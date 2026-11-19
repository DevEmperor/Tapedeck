package net.devemperor.tapedeck

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.devemperor.tapedeck.data.Library
import net.devemperor.tapedeck.data.Settings
import net.devemperor.tapedeck.download.Downloads
import net.devemperor.tapedeck.download.NetworkMonitor
import net.devemperor.tapedeck.engine.AppUpdates
import net.devemperor.tapedeck.engine.Engine
import net.devemperor.tapedeck.engine.Preview
import net.devemperor.tapedeck.work.Maintenance
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Whether the activity is on screen; yt-dlp updates are only allowed then. */
object AppVisibility {
    @Volatile
    var foreground = false
}

class TapedeckApp : Application(), SingletonImageLoader.Factory {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settings: Settings
        private set
    lateinit var library: Library
        private set
    lateinit var downloads: Downloads
        private set
    lateinit var network: NetworkMonitor
        private set
    lateinit var preview: Preview
        private set
    lateinit var appUpdates: AppUpdates
        private set

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        library = Library(File(filesDir, "library.json"), scope)
        downloads = Downloads(this, scope)
        network = NetworkMonitor(this)
        preview = Preview(http)
        appUpdates = AppUpdates(http, settings)
        Notifications.createChannels(this)
        Maintenance.schedule(this)
        // Unpacking Python and FFmpeg only; updates happen when the activity opens (MainViewModel.onAppOpened).
        scope.launch(Dispatchers.IO) {
            runCatching { Engine.ensureInitialized(this@TapedeckApp) }
        }
        scope.launch(Dispatchers.IO) { Maintenance.cleanUp(this@TapedeckApp) }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            .build()
}
