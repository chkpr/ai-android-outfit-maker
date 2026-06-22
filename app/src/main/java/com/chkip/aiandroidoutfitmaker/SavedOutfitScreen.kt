package com.chkip.aiandroidoutfitmaker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedOutfitsScreen(
    onBack: () -> Unit,
    onCreateOutfit: () -> Unit = {},
    garmentDescription: String? = null
) {
    val context = LocalContext.current
    val outfitStorage = remember { OutfitStorage(context) }
    var outfits by remember {
        mutableStateOf(
            if (garmentDescription != null) {
                if (garmentDescription.isEmpty()) {
                    emptyList() // description vide = aucun outfit
                } else {
                    val keywords = garmentDescription.split(" ")
                        .filter { it.length > 4 }
                        .take(3)
                    outfitStorage.getOutfits().filter { outfit ->
                        keywords.any { keyword ->
                            outfit.garmentDescription.contains(keyword, ignoreCase = true) ||
                                    outfit.description.contains(keyword, ignoreCase = true)
                        }
                    }
                }
            } else {
                outfitStorage.getOutfits()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (garmentDescription != null)
                            "🔖 Outfits générés à partir de ce vêtement"
                        else
                            "🔖 Mes outfits sauvegardés"
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Retour")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (outfits.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (garmentDescription != null)
                            "Aucun outfit sauvegardé avec ce vêtement encore !"
                        else
                            "Aucun outfit sauvegardé pour l'instant !",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (garmentDescription != null) {
                        Button(onClick = onCreateOutfit) {
                            Text("✨ Créer un outfit avec ce vêtement")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(outfits) { outfit ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = outfit.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (outfit.garmentDescription.isNotEmpty() && garmentDescription == null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "👗 ${outfit.garmentDescription}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = outfit.description,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    outfitStorage.deleteOutfit(outfit.id)
                                    outfits = outfitStorage.getOutfits()
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("🗑️ Supprimer", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}