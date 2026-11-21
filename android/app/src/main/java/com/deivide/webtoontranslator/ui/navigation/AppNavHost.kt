package com.deivide.webtoontranslator.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.deivide.webtoontranslator.ui.home.HomeScreen
import com.deivide.webtoontranslator.ui.settings.SettingsScreen

/** Telas do app. Todas na mesma janela (o voltar do Android volta pra anterior). */
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
