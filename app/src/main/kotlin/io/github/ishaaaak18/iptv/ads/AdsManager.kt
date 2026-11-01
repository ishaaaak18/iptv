package io.github.ishaaaak18.iptv.ads

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import io.github.ishaaaak18.iptv.BuildConfig
import io.github.ishaaaak18.iptv.IptvApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * All ad logic in one place.
 *
 * Rules (to keep users and stay inside AdMob policy):
 *  - Consent is collected with UMP before any ad request (required for EEA/UK users).
 *  - No ads on Android TV: AdMob does not support TV apps.
 *  - Never over video: banners only on list screens, interstitials only when the
 *    player is closed, at most one per [INTERSTITIAL_INTERVAL_MS] and never in the
 *    first [GRACE_PERIOD_MS] of a session.
 */
class AdsManager(private val context: Context) {

    private val initialized = AtomicBoolean(false)
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var interstitial: InterstitialAd? = null
    private var loadingInterstitial = false
    private val sessionStart = SystemClock.elapsedRealtime()
    private var lastInterstitialAt = 0L

    val isTv: Boolean
        get() = (context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager)
            .currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

    /** Call from the launcher activity's onCreate. */
    fun gatherConsentAndInit(activity: Activity) {
        if (isTv) return
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                Log.i(TAG, "consent status=${consent.consentStatus} canRequestAds=${consent.canRequestAds()}")
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) Log.w(TAG, "Consent form: ${error.message}")
                    if (consent.canRequestAds()) initSdk()
                }
            },
            { error -> Log.w(TAG, "Consent update: ${error.message}") },
        )
        // Consent from a previous session is enough to start right away.
        if (consent.canRequestAds()) initSdk()
    }

    private fun initSdk() {
        if (!initialized.compareAndSet(false, true)) return
        MobileAds.initialize(context) {
            _ready.value = true
            preloadInterstitial()
        }
    }

    private fun preloadInterstitial() {
        if (!_ready.value || interstitial != null || loadingInterstitial) return
        loadingInterstitial = true
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loadingInterstitial = false
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingInterstitial = false
                    Log.d(TAG, "Interstitial failed: ${error.message}")
                }
            },
        )
    }

    /** Shows an interstitial if the frequency cap allows it. Returns true when one was shown. */
    fun maybeShowInterstitial(activity: Activity): Boolean {
        val now = SystemClock.elapsedRealtime()
        val ad = interstitial
        val allowed = ad != null &&
            now - sessionStart >= GRACE_PERIOD_MS &&
            (lastInterstitialAt == 0L || now - lastInterstitialAt >= INTERSTITIAL_INTERVAL_MS)
        if (!allowed) {
            preloadInterstitial()
            return false
        }
        ad!!.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = preloadInterstitial()
        }
        interstitial = null
        lastInterstitialAt = now
        ad.show(activity)
        return true
    }

    companion object {
        private const val TAG = "AdsManager"
        const val INTERSTITIAL_INTERVAL_MS = 5 * 60 * 1000L
        const val GRACE_PERIOD_MS = 2 * 60 * 1000L
    }
}

/** Bottom banner for list screens. Renders nothing on TV or before consent/initialization. */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val ads = IptvApp.instance.ads
    val ready by ads.ready.collectAsState()
    if (!ready || ads.isTv) return
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BuildConfig.ADMOB_BANNER_ID
                adListener = object : AdListener() {}
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { it.destroy() },
    )
}
