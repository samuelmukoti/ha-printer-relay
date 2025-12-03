package com.harelayprint.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.harelayprint.data.local.SettingsDataStore
import com.harelayprint.ui.navigation.AppNavigation
import com.harelayprint.ui.navigation.Screen
import com.harelayprint.ui.theme.HARelayPrintTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val navigateTo = intent.getStringExtra("navigate_to")

        setContent {
            HARelayPrintTheme {
                val navController = rememberNavController()
                val isConfigured by settingsDataStore.isConfigured.collectAsState(initial = null)

                // Wait for the configured state to be loaded
                isConfigured?.let { configured ->
                    val startDestination = if (configured) {
                        Screen.Dashboard.route
                    } else {
                        Screen.Setup.route
                    }

                    AppNavigation(
                        navController = navController,
                        startDestination = startDestination
                    )

                    // Handle navigation from print service settings
                    LaunchedEffect(navigateTo, configured) {
                        if (navigateTo == "settings" && configured) {
                            navController.navigate(Screen.Settings.route)
                        }
                    }
                }
            }
        }
    }
}
