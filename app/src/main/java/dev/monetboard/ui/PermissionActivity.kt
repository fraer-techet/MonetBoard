package dev.monetboard.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** Invisible activity: an IME can't show permission dialogs itself, so it bounces through here. */
class PermissionActivity : ComponentActivity() {
    private val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}
