package io.github.ishaaaak18.iptv.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import io.github.ishaaaak18.iptv.IptvApp
import io.github.ishaaaak18.iptv.R
import io.github.ishaaaak18.iptv.core.model.Channel
import io.github.ishaaaak18.iptv.core.xtream.XtreamApi
import kotlinx.coroutines.launch

/** Hands the channel list to the player in memory: a big playlist does not fit in an Intent. */
object PlayerQueue {
    var playlistId: String = ""
    var channels: List<Channel> = emptyList()
    var index: Int = 0

    /** Set when the player closes, so the list screen may show a (frequency-capped) interstitial. */
    var playerJustClosed = false
}

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var overlay: TextView
    private var player: ExoPlayer? = null
    private val http = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)

    private val handler = Handler(Looper.getMainLooper())
    private var retries = 0
    private val hideOverlay = Runnable { overlay.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (PlayerQueue.channels.isEmpty()) {
            // Process was recreated and the in-memory queue is gone.
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        playerView = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            keepScreenOn = true
        }
        overlay = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            setBackgroundColor(0x99000000.toInt())
            setPadding(32, 16, 32, 16)
            visibility = View.GONE
        }
        setContentView(FrameLayout(this).apply {
            addView(playerView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(
                overlay,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START)
                    .apply { setMargins(48, 48, 48, 48) },
            )
        })
    }

    override fun onStart() {
        super.onStart()
        if (PlayerQueue.channels.isEmpty()) return
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
        exo.addListener(listener)
        exo.playWhenReady = true
        player = exo
        playerView.player = exo
        play(PlayerQueue.index)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }

    override fun finish() {
        PlayerQueue.playerJustClosed = true
        super.finish()
    }

    private fun play(index: Int) {
        val list = PlayerQueue.channels
        if (list.isEmpty()) return
        val i = ((index % list.size) + list.size) % list.size
        PlayerQueue.index = i
        val channel = list[i]
        retries = 0

        http.setUserAgent(channel.userAgent.ifBlank { XtreamApi.USER_AGENT })
        http.setDefaultRequestProperties(
            if (channel.referrer.isNotBlank()) mapOf("Referer" to channel.referrer) else emptyMap(),
        )
        val item = MediaItem.Builder()
            .setUri(channel.url)
            .apply { if (channel.url.contains(".m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        player?.run {
            setMediaItem(item)
            prepare()
        }
        showOverlay("${i + 1}. ${channel.name}")
        lifecycleScope.launch { IptvApp.instance.repository.recordPlay(PlayerQueue.playlistId, channel) }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) retries = 0
        }

        override fun onPlayerError(error: PlaybackException) {
            val p = player ?: return
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                p.seekToDefaultPosition()
                p.prepare()
                return
            }
            if (retries >= MAX_RETRIES) {
                showOverlay(getString(R.string.player_failed), sticky = true)
                return
            }
            retries++
            showOverlay(getString(R.string.player_reconnecting, retries), sticky = true)
            // Back off 1s, 2s, 4s, 8s, 16s: live servers often drop briefly.
            handler.postDelayed({ player?.prepare() }, 1000L shl (retries - 1))
        }
    }

    private fun showOverlay(text: String, sticky: Boolean = false) {
        handler.removeCallbacks(hideOverlay)
        overlay.text = text
        overlay.visibility = View.VISIBLE
        if (!sticky) handler.postDelayed(hideOverlay, 3000)
    }

    /** Channel up/down with the remote (or D-pad while the controls are hidden). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val controlsHidden = !playerView.isControllerFullyVisible
            when (event.keyCode) {
                KeyEvent.KEYCODE_CHANNEL_UP -> { play(PlayerQueue.index - 1); return true }
                KeyEvent.KEYCODE_CHANNEL_DOWN -> { play(PlayerQueue.index + 1); return true }
                KeyEvent.KEYCODE_DPAD_UP -> if (controlsHidden) { play(PlayerQueue.index - 1); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> if (controlsHidden) { play(PlayerQueue.index + 1); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature("android.software.picture_in_picture") &&
            player?.isPlaying == true
        ) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerView.useController = !isInPictureInPictureMode
        if (isInPictureInPictureMode) overlay.visibility = View.GONE
    }

    companion object {
        private const val MAX_RETRIES = 5

        fun intent(context: Context, playlistId: String, channels: List<Channel>, index: Int): Intent {
            PlayerQueue.playlistId = playlistId
            PlayerQueue.channels = channels.toList()
            PlayerQueue.index = index
            return Intent(context, PlayerActivity::class.java)
        }
    }
}
