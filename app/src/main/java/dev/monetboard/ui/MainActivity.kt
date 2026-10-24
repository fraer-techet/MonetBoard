package dev.monetboard.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import dev.monetboard.data.Prefs

class MainActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs.get(this)
        setContent {
            MonetTheme(prefs) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsApp(prefs, tick.intValue)
                }
            }
        }
    }

    // re-check "enabled / selected" status when coming back from system settings
    override fun onResume() {
        super.onResume()
        tick.intValue++
    }
}
