package com.chkip.aiandroidoutfitmaker

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.rememberAsyncImagePainter
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateOutfitScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    // Crée un fichier temporaire pour la photo
    val tempFile = remember {
        File.createTempFile("outfit_", ".jpg", context.cacheDir)
    }
    val fileUri = remember {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }

    // Launcher pour la caméra
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) photoUri = fileUri
    }

    // Launcher pour la permission caméra
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) cameraLauncher.launch(fileUri)
    }

    // Launcher pour la galerie
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { photoUri = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Créer un outfit") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Retour")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "📸 Prends ton vêtement en photo",
                style = MaterialTheme.typography.headlineSmall
            )

            // Affiche la photo si prise
            if (photoUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(photoUri),
                    contentDescription = "Photo du vêtement",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Placeholder
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Aucune photo sélectionnée")
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { permissionLauncher.launch(android.Manifest.permission.CAMERA) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("📷 Caméra")
                }
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🖼️ Galerie")
                }
            }

            if (photoUri != null) {
                Button(
                    onClick = { /* TODO: appel API Claude */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("✨ Générer un outfit avec l'IA")
                }
            }
        }
    }
}