package io.github.ishaaaak18.iptv

import android.app.Application
import io.github.ishaaaak18.iptv.ads.AdsManager
import io.github.ishaaaak18.iptv.core.PlaylistLoader
import io.github.ishaaaak18.iptv.core.xtream.XtreamApi
import io.github.ishaaaak18.iptv.data.LibraryRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class IptvApp : Application() {

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
    val xtreamApi by lazy { XtreamApi(httpClient) }
    val repository by lazy { LibraryRepository(filesDir, PlaylistLoader(httpClient)) }
    val ads by lazy { AdsManager(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: IptvApp
            private set
    }
}
