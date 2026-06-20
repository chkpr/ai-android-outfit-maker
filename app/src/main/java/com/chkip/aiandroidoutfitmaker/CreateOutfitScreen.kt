package com.chkip.aiandroidoutfitmaker

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import android.graphics.Bitmap




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateOutfitScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var outfitSuggestion by remember { mutableStateOf<String?>(null) }
    val wardrobeStorage = remember { WardrobeStorage(context) }
    var savedToDressing by remember { mutableStateOf(false) }
    var generatedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val imageGenService = remember { ImageGenerationService() }
    val geminiApi = remember { GeminiApiService() }
    var processedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var waitingForTouch by remember { mutableStateOf(false) }
    var imageSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    var lastTouchY by remember { mutableStateOf(0f) }
    var lastTouchX by remember { mutableStateOf(0f) }

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

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri = fileUri
            outfitSuggestion = null
            generatedBitmap = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) cameraLauncher.launch(fileUri)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            photoUri = it
            outfitSuggestion = null
            generatedBitmap = null
        }
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "📸 Prends ton vêtement en photo",
                style = MaterialTheme.typography.headlineSmall
            )

            // Affichage photo ou image générée
            if (photoUri != null) {
                if (generatedBitmap != null ) {
                    androidx.compose.foundation.Image(
                        bitmap = generatedBitmap!!.asImageBitmap(),
                        contentDescription = "Vêtement isolé",
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        contentScale = ContentScale.Fit
                    )
                } else if (waitingForTouch) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        AsyncImage(
                            model = photoUri,
                            contentDescription = "Photo du vêtement",
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .onGloballyPositioned { coordinates ->
                                    imageSize = androidx.compose.ui.geometry.Size(
                                        coordinates.size.width.toFloat(),
                                        coordinates.size.height.toFloat()
                                    )
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        android.util.Log.d("TOUCH", "Touch at: ${offset.x}, ${offset.y}")
                                        android.util.Log.d("TOUCH", "Image size: ${imageSize.width}x${imageSize.height}")
                                        waitingForTouch = false
                                        lastTouchX = offset.x
                                        lastTouchY = offset.y
                                        scope.launch {
                                            val sourceBitmap = BitmapUtils.loadCorrectlyOrientedBitmap(context, photoUri!!)
                                                ?: return@launch

                                            val maxSize = 800
                                            val scale = minOf(maxSize.toFloat() / sourceBitmap.width, maxSize.toFloat() / sourceBitmap.height)
                                            val scaledBitmap = Bitmap.createScaledBitmap(
                                                sourceBitmap,
                                                (sourceBitmap.width * scale).toInt(),
                                                (sourceBitmap.height * scale).toInt(),
                                                true
                                            )

                                            generatedBitmap = GrabCutSegmentation.isolateGarment(
                                                scaledBitmap,
                                                offset.x,
                                                offset.y,
                                                imageSize.width,
                                                imageSize.height
                                            )
                                        }
                                    }
                                },
                            contentScale = ContentScale.Fit
                        )
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                text = "👆 Touche le vêtement sur la photo",
                                modifier = Modifier.padding(8.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "Photo du vêtement",
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        contentScale = ContentScale.Fit
                    )
                }

                if (generatedBitmap == null && !waitingForTouch) {
                    Button(
                        onClick = { waitingForTouch = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("✂️ Sélectionner le vêtement")
                    }
                }

                // Bouton ajouter au dressing
                if (savedToDressing) {
                    Text(
                        text = "✅ Ajouté au dressing !",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    OutlinedButton(
                        onClick = {
                            val item = if (generatedBitmap != null) {
                                // Sauvegarde l'image détourée
                                val file = java.io.File(context.filesDir, "wardrobe_${UUID.randomUUID()}.jpg")
                                val outStream = java.io.FileOutputStream(file)
                                generatedBitmap!!.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outStream)
                                outStream.close()
                                ClothingItem(
                                    id = UUID.randomUUID().toString(),
                                    photoPath = file.absolutePath,
                                    description = outfitSuggestion ?: "Vêtement sans description"
                                )
                            } else {
                                ClothingItem(
                                    id = UUID.randomUUID().toString(),
                                    photoPath = photoUri.toString(),
                                    description = outfitSuggestion ?: "Vêtement sans description"
                                )
                            }
                            wardrobeStorage.saveItem(item)
                            savedToDressing = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("👗 Ajouter au dressing")
                    }
                }
            } else {
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

            // Boutons caméra et galerie
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            // Bouton générer
            if (photoUri != null) {
                Button(
                    onClick = {
                        scope.launch {
                            android.util.Log.d("OUTFIT", "Bouton cliqué !")
                            isLoading = true
                            outfitSuggestion = geminiApi.generateOutfit(context, photoUri!!)
                            android.util.Log.d("OUTFIT", "Suggestion complète: $outfitSuggestion")

                            val description = outfitSuggestion
                                ?.lines()
                                ?.find { it.startsWith("DESCRIPTION:") }
                                ?.removePrefix("DESCRIPTION:")
                                ?.trim()

                            val mainColor = outfitSuggestion
                                ?.lines()
                                ?.find { it.startsWith("COULEUR_PRINCIPALE:") }
                                ?.removePrefix("COULEUR_PRINCIPALE:")
                                ?.trim()

                            val secondaryColors = outfitSuggestion
                                ?.lines()
                                ?.find { it.startsWith("COULEURS_SECONDAIRES:") }
                                ?.removePrefix("COULEURS_SECONDAIRES:")
                                ?.trim()
                                ?.split(",")
                                ?.map { it.trim() }
                                ?: emptyList()

                            val hasPattern = outfitSuggestion
                                ?.lines()
                                ?.find { it.startsWith("A_MOTIF:") }
                                ?.removePrefix("A_MOTIF:")
                                ?.trim() == "true"

                            android.util.Log.d("OUTFIT", "Couleur principale: $mainColor, Motif: $hasPattern, Couleurs: $secondaryColors")


                            if (description != null) {
                                val isolatedBitmap = imageGenService.generateCleanImage(
                                    context, photoUri!!, description,
                                    lastTouchX, lastTouchY,
                                    imageSize.width, imageSize.height
                                )
                                if (isolatedBitmap != null) generatedBitmap = isolatedBitmap
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyse en cours...")
                    } else {
                        Text("✨ Générer un outfit avec l'IA")
                    }
                }
            }
            // Suggestions d'outfits
            outfitSuggestion?.let { suggestion ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        text = suggestion,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}