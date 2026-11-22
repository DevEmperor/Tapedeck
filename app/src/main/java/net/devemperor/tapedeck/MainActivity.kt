package net.devemperor.tapedeck

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import net.devemperor.tapedeck.ui.MainViewModel
import net.devemperor.tapedeck.ui.TapedeckRoot
import net.devemperor.tapedeck.ui.theme.TapedeckTheme
import net.devemperor.tapedeck.work.Subscriptions

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            TapedeckTheme {
                // The surface sets the content colour (light text on dark, and the reverse) for everything below.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TapedeckRoot(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.foreground = true
        vm.player.connect()
        vm.resumePendingDownloads()
        vm.refreshJustUpdated()
        vm.onAppOpened()
        // The installer's "Update?" dialog, if it arrived while Tapedeck was in the background.
        vm.takePendingInstallConfirmation()?.let(::startActivity)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) vm.checkClipboard(this)
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            AppVisibility.foreground = false
            vm.player.disconnect()
        }
        super.onStop()
    }

    private fun handleIntent(intent: Intent?) {
        // Emulator tests: Genymotion mirrors its clipboard to the host's, so tests hand a "copied" link in here.
        if (BuildConfig.DEBUG) intent?.getStringExtra(DEBUG_CLIP)?.let(vm::considerClipText)
        // Emulator tests: a subscription look in so many seconds, by when the app is in the background.
        if (BuildConfig.DEBUG) intent?.getIntExtra(DEBUG_CHECK, 0)?.takeIf { it > 0 }?.let { Subscriptions.checkSoon(this, it.toLong()) }
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: return
        vm.onShared(text)
        // A rotation must not hand the same link over twice.
        intent.action = null
    }

    private companion object {
        const val DEBUG_CLIP = "tapedeck.debug.clip"
        const val DEBUG_CHECK = "tapedeck.debug.check"
    }
}
