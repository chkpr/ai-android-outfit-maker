package com.chkip.aiandroidoutfitmaker

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

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
                onOpenSavedOutfits = { navController.navigate("saved_outfits?garmentDesc=") }
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
                },
                onOpenSavedOutfits = { garmentDesc ->
                    navController.navigate("saved_outfits?garmentDesc=${garmentDesc ?: ""}")
                }
            )
        }
        composable(
            "saved_outfits?garmentDesc={garmentDesc}",
            arguments = listOf(navArgument("garmentDesc") {
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val garmentDesc = backStackEntry.arguments?.getString("garmentDesc")
            SavedOutfitsScreen(
                onBack = { navController.popBackStack() },
                onCreateOutfit = { navController.navigate("create_outfit") },
                garmentDescription = garmentDesc
            )
        }
    }
}