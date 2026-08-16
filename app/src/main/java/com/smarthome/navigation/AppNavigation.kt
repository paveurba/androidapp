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
    sensorRepository: com.smarthome.data.SensorRepository,
    notificationRepository: com.smarthome.data.NotificationRepository,
    alarmSensorRepository: com.smarthome.data.AlarmSensorRepository,
    agentStatusRepository: com.smarthome.data.AgentStatusRepository,
    pairingRepository: com.smarthome.data.PairingRepository
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
                authPreferences = authPreferences,
                authRepository = authRepository,
                onLoginSuccess = { serialNumber, otp ->
                    scope.launch {
                        authPreferences.saveCredentials(serialNumber, otp)
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
                authPreferences = authPreferences,
                sensorRepository = sensorRepository,
                notificationRepository = notificationRepository,
                alarmSensorRepository = alarmSensorRepository,
                agentStatusRepository = agentStatusRepository,
                pairingRepository = pairingRepository,
                onLogout = {
                    scope.launch {
                        // Must clear local schedule configs and both
                        // dashboards' tile layouts too, not just auth state -
                        // none of these are scoped to the logged-in account,
                        // so leftovers would otherwise show up (phantom
                        // disabled schedules, someone else's tile layout)
                        // under whichever account logs in next.
                        sensorRepository.clearLocalScheduleConfigs()
                        sensorRepository.clearSensorTileLayout()
                        alarmSensorRepository.clearAlarmSensorTileLayout()
                        authPreferences.clear()
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = true }
                        }
                    }
                }
            )
        }
    }
}
