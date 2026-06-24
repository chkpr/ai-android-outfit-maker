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
fun CreateOutfitScreen(onBack: () -> Unit, onOpenWardrobe: () -> Unit, preloadedImagePath: String? = null) {
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
    var selectedStyle by remember { mutableStateOf("Casual chic") }

    LaunchedEffect(preloadedImagePath) {
        preloadedImagePath?.let { path ->
            val file = java.io.File(path)
            if (file.exists()) {
                photoUri = android.net.Uri.fromFile(file)
            }
        }
    }

    val styles = listOf(
        "Classique", "Casual chic", "Sophistiqué", "Fashion", "Rock", "Streetwear",
        "Décalé", "Bohème", "Bureau", "Soirée", "Y2K", "Années 90", "Seventies"
    )
    var styleExpanded by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("Autres") }

    val categories = listOf(
        "Tops", "Jeans", "Pantalons", "Pulls", "Gilets",
        "Vestes", "Manteaux", "Jupes", "Robes",
        "Ceintures", "Chapeaux & Casquettes", "Foulards & Écharpes", "Autres"
    )

    LaunchedEffect(outfitSuggestion) {
        val type = outfitSuggestion
            ?.lines()
            ?.find { it.startsWith("TYPE:") }
            ?.removePrefix("TYPE:")
            ?.trim() ?: ""
        if (type.isNotEmpty()) {
            selectedCategory = mapTypeToCategory(type)
        }
    }

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

    // Modale de catégorie
    if (showCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("Choisir une catégorie") },
            text = {
                Column {
                    if (generatedBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = generatedBitmap!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    var catExpanded by remember { mutableStateOf(false) }
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
                Button(onClick = {
                    val item = if (generatedBitmap != null) {
                        val file = java.io.File(context.filesDir, "wardrobe_${UUID.randomUUID()}.jpg")
                        val outStream = java.io.FileOutputStream(file)
                        generatedBitmap!!.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outStream)
                        outStream.close()
                        ClothingItem(
                            id = UUID.randomUUID().toString(),
                            photoPath = file.absolutePath,
                            description = outfitSuggestion ?: "Vêtement sans description",
                            category = selectedCategory
                        )
                    } else {
                        ClothingItem(
                            id = UUID.randomUUID().toString(),
                            photoPath = photoUri.toString(),
                            description = outfitSuggestion ?: "Vêtement sans description",
                            category = selectedCategory
                        )
                    }
                    wardrobeStorage.saveItem(item)
                    savedToDressing = true
                    showCategoryDialog = false
                }) {
                    Text("Ajouter")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCategoryDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Créer un outfit") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Retour")
                    }
                },
                actions = {
                    TextButton(onClick = onOpenWardrobe) {
                        Text("👗 Dressing")
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

            if (photoUri != null) {
                if (generatedBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = generatedBitmap!!.asImageBitmap(),
                        contentDescription = "Vêtement isolé",
                        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                        contentScale = ContentScale.Fit
                    )
                } else if (waitingForTouch) {
                    Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
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
                            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(8.dp),
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
                        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                        contentScale = ContentScale.Fit
                    )
                }

                if (generatedBitmap == null && !waitingForTouch && preloadedImagePath == null) {
                    Button(
                        onClick = { waitingForTouch = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("✂️ Sélectionner le vêtement")
                    }
                }

                if (preloadedImagePath == null) {
                    if (savedToDressing) {
                        Text(
                            text = "✅ Ajouté au dressing !",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        OutlinedButton(
                            onClick = { showCategoryDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("👗 Ajouter au dressing")
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Aucune photo sélectionnée")
                    }
                }
            }

            if (preloadedImagePath == null) {
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
            }

            ExposedDropdownMenuBox(
                expanded = styleExpanded,
                onExpandedChange = { styleExpanded = !styleExpanded }
            ) {
                OutlinedTextField(
                    value = selectedStyle,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Style") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = styleExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = styleExpanded,
                    onDismissRequest = { styleExpanded = false }
                ) {
                    styles.forEach { style ->
                        DropdownMenuItem(
                            text = { Text(style) },
                            onClick = {
                                selectedStyle = style
                                styleExpanded = false
                            }
                        )
                    }
                }
            }

            if (photoUri != null) {
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            outfitSuggestion = geminiApi.generateOutfit(context, photoUri!!, selectedStyle, wardrobeStorage.getItems())

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

                            if (preloadedImagePath == null) {
                                val isolatedBitmap = imageGenService.generateCleanImage(
                                    context, photoUri!!, description ?: "",
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

            outfitSuggestion?.let { suggestion ->
                val outfitStorage = remember { OutfitStorage(context) }

                val outfitLines = suggestion.lines()
                val outfits = mutableListOf<Pair<String, String>>()
                var currentTitle = ""
                var currentDesc = StringBuilder()

                outfitLines.forEach { line ->
                    when {
                        line.startsWith("1.") -> {
                            currentTitle = "Outfit 1"
                            currentDesc = StringBuilder(line.removePrefix("1.").trim())
                        }
                        line.startsWith("2.") -> {
                            if (currentTitle.isNotEmpty()) outfits.add(Pair(currentTitle, currentDesc.toString()))
                            currentTitle = "Outfit 2"
                            currentDesc = StringBuilder(line.removePrefix("2.").trim())
                        }
                        line.startsWith("3.") -> {
                            if (currentTitle.isNotEmpty()) outfits.add(Pair(currentTitle, currentDesc.toString()))
                            currentTitle = "Outfit 3"
                            currentDesc = StringBuilder(line.removePrefix("3.").trim())
                        }
                        line.isNotBlank() && currentTitle.isNotEmpty() &&
                                !line.startsWith("DESCRIPTION:") && !line.startsWith("COULEUR") &&
                                !line.startsWith("TYPE") && !line.startsWith("A_MOTIF") &&
                                !line.startsWith("OUTFITS") -> {
                            currentDesc.append(" ").append(line.trim())
                        }
                    }
                }
                if (currentTitle.isNotEmpty()) outfits.add(Pair(currentTitle, currentDesc.toString()))

                val outfitEmojis = listOf("✨", "🌟", "💫")

                outfits.forEachIndexed { index, (title, desc) ->
                    var saved by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "${outfitEmojis.getOrElse(index) { "👗" }} $title",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (saved) {
                                Text(
                                    text = "✅ Sauvegardé !",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        outfitStorage.saveOutfit(
                                            SavedOutfit(
                                                id = java.util.UUID.randomUUID().toString(),
                                                title = title,
                                                description = desc,
                                                garmentDescription = suggestion
                                                    .lines()
                                                    .find { it.startsWith("DESCRIPTION:") }
                                                    ?.removePrefix("DESCRIPTION:")
                                                    ?.trim() ?: ""
                                            )
                                        )
                                        saved = true
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("🔖 Garder cet outfit")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}