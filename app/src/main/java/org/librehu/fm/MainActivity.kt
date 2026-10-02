package org.librehu.fm

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.librehu.fm.ui.RadioScreen
import org.librehu.fm.ui.RadioTheme
import org.librehu.fm.ui.ThemeFollower

class MainActivity : ComponentActivity() {
    private val theme = lazy { ThemeFollower(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        theme.value.start()
        enableEdgeToEdge()
        // The FM capture source needs RECORD_AUDIO on top of the privileged CAPTURE_AUDIO_OUTPUT.
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        setContent {
            RadioTheme {
                RadioScreen(send = { action, freq -> FmService.send(this, action, freq) })
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        theme.value.refresh()
    }

    override fun onDestroy() {
        theme.value.stop()
        super.onDestroy()
    }
}
