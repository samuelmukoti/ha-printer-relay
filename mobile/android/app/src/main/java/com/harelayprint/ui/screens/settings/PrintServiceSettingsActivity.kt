package com.harelayprint.ui.screens.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.harelayprint.app.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity launched when user clicks "Settings" in the system Print Service settings.
 * Simply redirects to the main app settings.
 */
@AndroidEntryPoint
class PrintServiceSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Launch main activity which will show settings
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "settings")
        }
        startActivity(intent)
        finish()
    }
}
