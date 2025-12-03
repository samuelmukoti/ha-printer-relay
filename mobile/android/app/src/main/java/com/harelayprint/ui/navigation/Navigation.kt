package com.harelayprint.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.harelayprint.ui.screens.dashboard.DashboardScreen
import com.harelayprint.ui.screens.printers.PrinterListScreen
import com.harelayprint.ui.screens.queue.PrintQueueScreen
import com.harelayprint.ui.screens.settings.SettingsScreen
import com.harelayprint.ui.screens.setup.SetupScreen

sealed class Screen(val route: String) {
    data object Setup : Screen("setup")
    data object Dashboard : Screen("dashboard")
    data object PrinterList : Screen("printers")
    data object PrintQueue : Screen("queue")
    data object Settings : Screen("settings")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToPrinters = { navController.navigate(Screen.PrinterList.route) },
                onNavigateToQueue = { navController.navigate(Screen.PrintQueue.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.PrinterList.route) {
            PrinterListScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.PrintQueue.route) {
            PrintQueueScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.Setup.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
