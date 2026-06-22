package com.chkip.aiandroidoutfitmaker

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onCreateOutfit: () -> Unit,
    onOpenWardrobe: () -> Unit,
    onOpenSavedOutfits: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "👗 Outfit Maker",
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Crée tes outfits avec l'IA",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onCreateOutfit) {
                Text("✨ Créer un outfit")
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onOpenWardrobe) {
                Text("👗 Mon dressing")
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onOpenSavedOutfits) {
                Text("🔖 Mes outfits")
            }
        }
    }
}