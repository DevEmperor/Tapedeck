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
        vm.player.connect()
        vm.resumePendingDownloads()
    }

    override fun onStop() {
        if (!isChangingConfigurations) vm.player.disconnect()
        super.onStop()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: return
        vm.onShared(text)
        // A rotation must not hand the same link over twice.
        intent.action = null
    }
}
