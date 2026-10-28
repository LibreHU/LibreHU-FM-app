package org.librehu.fm

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.librehu.fm.ui.RadioScreen
import org.librehu.fm.ui.RadioTheme
import org.librehu.fm.ui.SettingsScreen
import org.librehu.fm.ui.ThemeFollower

class MainActivity : ComponentActivity() {
    /** A click on each tap, the sound of LibreHU Launcher's style (see KeyClick). */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        org.librehu.fm.ui.KeyClick.onTouch(this, ev, org.librehu.fm.ui.RetroLook.style)
        return super.dispatchTouchEvent(ev)
    }

    private val theme = lazy { ThemeFollower(this) }

    /** FM journal (Settings → Export the log) to the file the user picks. */
    private val logLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                val ok =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val text = FmLog.collect(this@MainActivity)
                            contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
                        }.getOrDefault(false)
                    }
                Toast.makeText(this@MainActivity, getString(if (ok) R.string.set_log_done else R.string.set_log_failed), Toast.LENGTH_LONG).show()
            }
        }

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
                var settings by rememberSaveable { mutableStateOf(false) }
                if (settings) {
                    SettingsScreen(onBack = { settings = false }, onExportLog = { logLauncher.launch(FmLog.fileName()) })
                } else {
                    RadioScreen(
                        send = { action, freq -> FmService.send(this, action, freq) },
                        onSettings = { settings = true },
                    )
                }
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
