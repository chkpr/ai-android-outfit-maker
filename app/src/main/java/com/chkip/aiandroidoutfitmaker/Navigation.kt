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
                onCreateOutfit = { navController.navigate("create_outfit") },
                onOpenWardrobe = { navController.navigate("wardrobe") },
                onOpenSavedOutfits = { navController.navigate("saved_outfits") }
            )
        }
        composable("create_outfit?itemPath={itemPath}") { backStackEntry ->
            val itemPath = backStackEntry.arguments?.getString("itemPath")
            CreateOutfitScreen(
                onBack = { navController.popBackStack() },
                onOpenWardrobe = { navController.navigate("wardrobe") },
                preloadedImagePath = itemPath
            )
        }
        composable("wardrobe") {
            WardrobeScreen(
                onBack = { navController.popBackStack() },
                onCreateOutfit = { itemPath ->
                    navController.navigate("create_outfit?itemPath=${itemPath}")
                }
            )
        }

        composable("saved_outfits") {
            SavedOutfitsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}