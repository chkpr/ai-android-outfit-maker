package com.chkip.aiandroidoutfitmaker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeScreen(onBack: () -> Unit, onCreateOutfit: (String) -> Unit = {}) {
    val context = LocalContext.current
    val wardrobeStorage = remember { WardrobeStorage(context) }
    var selectedItem by remember { mutableStateOf<ClothingItem?>(null) }
    var showItemDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("Autres") }

    var items by remember { mutableStateOf(wardrobeStorage.getItems()) }
    LaunchedEffect(Unit) {
        wardrobeStorage.recategorizeItems()
        items = wardrobeStorage.getItems()
    }

    items.forEach { item ->
        android.util.Log.d("WARDROBE", "Category: ${item.category}, Description: ${item.description.take(100)}")
    }



    // Groupe les vêtements par catégorie
    val categories = listOf(
        "Tops", "Jeans", "Pantalons", "Pulls", "Gilets",
        "Vestes", "Manteaux", "Jupes", "Robes",
        "Ceintures", "Chapeaux & Casquettes", "Foulards & Écharpes", "Autres"
    )

    if (showItemDialog && selectedItem != null) {
        val categories = listOf(
            "Tops", "Jeans", "Pantalons", "Pulls", "Gilets",
            "Vestes", "Manteaux", "Jupes", "Robes",
            "Ceintures", "Chapeaux & Casquettes", "Foulards & Écharpes", "Autres"
        )
        var catExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showItemDialog = false },
            title = { Text("Modifier le vêtement") },
            text = {
                Column {
                    AsyncImage(
                        model = selectedItem!!.photoPath.toUri(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    ExposedDropdownMenuBox(
                        expanded = catExpanded,
                        onExpandedChange = { catExpanded = !catExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Catégorie") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = catExpanded,
                            onDismissRequest = { catExpanded = false }
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        selectedCategory = cat
                                        catExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onCreateOutfit(selectedItem!!.photoPath)
                            showItemDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("✨ Générer un outfit")
                    }
                    Button(
                        onClick = {
                            selectedItem?.let { item ->
                                wardrobeStorage.deleteItem(item.id)
                                wardrobeStorage.saveItem(item.copy(category = selectedCategory))
                                items = wardrobeStorage.getItems()
                            }
                            showItemDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Confirmer la catégorie")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showItemDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("👗 Mon Dressing") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Retour")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Ton dressing est vide !\nAjoute des vêtements depuis l'écran de création.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(8.dp)
            ) {
                categories.forEach { category ->
                    val categoryItems = items.filter { it.category == category }
                    if (categoryItems.isNotEmpty()) {
                        item {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                            )
                        }
                        item {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 600.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(categoryItems) { item ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedItem = item
                                                selectedCategory = item.category
                                                showItemDialog = true
                                            },
                                        colors = CardDefaults.cardColors(containerColor = Color.White)
                                    ) {
                                        Column {
                                            AsyncImage(
                                                model = item.photoPath.toUri(),
                                                contentDescription = "Vêtement",
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(150.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                            TextButton(
                                                onClick = {
                                                    wardrobeStorage.deleteItem(item.id)
                                                    items = wardrobeStorage.getItems()
                                                },
                                                modifier = Modifier.align(Alignment.End)
                                            ) {
                                                Text("🗑️", color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }


}