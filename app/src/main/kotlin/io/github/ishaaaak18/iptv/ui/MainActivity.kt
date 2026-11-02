package io.github.ishaaaak18.iptv.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.semantics
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.ishaaaak18.iptv.BuildConfig
import io.github.ishaaaak18.iptv.IptvApp
import io.github.ishaaaak18.iptv.player.PlayerQueue
import io.github.ishaaaak18.iptv.ui.add.AddPlaylistScreen
import io.github.ishaaaak18.iptv.ui.channels.ChannelsScreen
import io.github.ishaaaak18.iptv.ui.home.HomeScreen
import io.github.ishaaaak18.iptv.ui.theme.IptvTheme

@OptIn(ExperimentalComposeUiApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Debug builds only: UI tests can turn ads off, since an animated banner keeps
        // the screen from ever going idle for uiautomator.
        val adsOff = BuildConfig.DEBUG && intent.getBooleanExtra("disable_ads", false)
        if (!adsOff) IptvApp.instance.ads.gatherConsentAndInit(this)
        setContent {
            IptvTheme {
                Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.background) {
                    val nav = rememberNavController()
                    NavHost(nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                onAdd = {
                                    Log.i("IptvUi", "navigate add")
                                    nav.navigate("add")
                                },
                                onOpen = { id -> nav.navigate("channels/$id") },
                            )
                        }
                        composable("add") {
                            AddPlaylistScreen(
                                onDone = { id ->
                                    nav.popBackStack()
                                    nav.navigate("channels/$id")
                                },
                                onCancel = { nav.popBackStack() },
                            )
                        }
                        composable(
                            "channels/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.StringType }),
                        ) { entry ->
                            ChannelsScreen(
                                playlistId = entry.arguments?.getString("id").orEmpty(),
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Natural break: the user just closed the player. Ads never interrupt playback.
        if (PlayerQueue.playerJustClosed) {
            PlayerQueue.playerJustClosed = false
            IptvApp.instance.ads.maybeShowInterstitial(this)
        }
    }
}
