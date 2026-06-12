package com.smarthome.navigation

import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smarthome.data.AuthPreferences
import com.smarthome.data.AuthRepository
import com.smarthome.ui.auth.LoginScreen
import com.smarthome.ui.dashboard.DashboardScreen
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Dashboard : Screen("dashboard")
}

@Composable
fun AppNavigation(
    authPreferences: AuthPreferences,
    authRepository: AuthRepository,
    sensorRepository: com.smarthome.data.SensorRepository
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    
    // Collect the login state from DataStore
    val isLoggedIn by authPreferences.isLoggedIn.collectAsState(initial = null)

    // Wait for the initial load from DataStore
    if (isLoggedIn == null) {
        // You could show a splash screen here
        return
    }

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn == true) Screen.Dashboard.route else Screen.Auth.route
    ) {
        composable(Screen.Auth.route) {
            LoginScreen(
                authRepository = authRepository,
                onLoginSuccess = {
                    scope.launch {
                        authPreferences.setLoggedIn(true)
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Auth.route) { inclusive = true }
                        }
                    }
                }
            )
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                sensorRepository = sensorRepository,
                onLogout = {
                    scope.launch {
                        authPreferences.setLoggedIn(false)
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = true }
                        }
                    }
                }
            )
        }
    }
}
