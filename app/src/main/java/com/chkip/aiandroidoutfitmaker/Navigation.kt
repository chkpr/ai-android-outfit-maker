package com.chkip.aiandroidoutfitmaker

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                onCreateOutfit = { navController.navigate("create_outfit") }
            )
        }
        composable("create_outfit") {
            CreateOutfitScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}