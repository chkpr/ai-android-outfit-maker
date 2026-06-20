package com.chkip.aiandroidoutfitmaker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*

class ImageGenerationService {
    private val replicateKey = BuildConfig.REPLICATE_API_KEY
    private val removeBgKey = BuildConfig.REMOVE_BG_API_KEY
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }
        engine {
            connectTimeout = 60_000
            socketTimeout = 120_000
        }
    }

    private suspend fun callRemoveBg(imageBytes: ByteArray, type: String = "auto", roi: String? = null): Bitmap? {
        return try {
            val responseBytes: ByteArray = client.post("https://api.remove.bg/v1.0/removebg") {
                header("X-Api-Key", removeBgKey)
                setBody(MultiPartFormDataContent(
                    formData {
                        append("image_file", imageBytes, Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=image.jpg")
                        })
                        append("size", "auto")
                        append("type", type)
                        append("crop", "true")

                    }
                ))
            }.body()

            val jsonString = responseBytes.toString(Charsets.UTF_8)
            android.util.Log.d("RemoveBg", "Response preview: ${jsonString.take(200)}")

            val jsonResponse = Json.parseToJsonElement(jsonString).jsonObject
            val base64Result = jsonResponse["data"]?.jsonObject?.get("result_b64")?.jsonPrimitive?.content
            android.util.Log.d("RemoveBg", "Base64 null: ${base64Result == null}")
            base64Result ?: return null

            val imageBytes2 = Base64.decode(base64Result, Base64.DEFAULT)
            val tempFile = java.io.File.createTempFile("removebg_", ".png")
            tempFile.writeBytes(imageBytes2)

            val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(tempFile)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                BitmapFactory.decodeFile(tempFile.absolutePath)
            }

            android.util.Log.d("RemoveBg", "Bitmap decoded: ${bitmap?.width}x${bitmap?.height}")
            if (bitmap == null) {
                android.util.Log.e("RemoveBg", "Bitmap is null!")
                return null
            }
            val result = Bitmap.createBitmap(bitmap!!.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(result)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            result

        } catch (e: Exception) {
            android.util.Log.e("RemoveBg", "Error: ${e.message}")
            null
        }
    }

    private fun bitmapToBytes(bitmap: Bitmap): ByteArray {
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        return outputStream.toByteArray()
    }

    suspend fun generateCleanImage(
        context: Context,
        imageUri: Uri,
        description: String,
        touchX: Float = 0f,
        touchY: Float = 0f,
        imageWidth: Float = 0f,
        imageHeight: Float = 0f
    ): Bitmap? {
        val imageBytes = context.contentResolver.openInputStream(imageUri)?.readBytes()
            ?: return null

        android.util.Log.d("ImageGen", "Step 1: Remove background")
        val step1 = callRemoveBg(imageBytes, "auto") ?: return null

        android.util.Log.d("ImageGen", "Step 2: Replace white with green")
        val greenBg = replaceWhiteWithGreen(step1)

        android.util.Log.d("ImageGen", "Step 3: GrabCut on green background")
        val maxSize = 800
        val scale = minOf(maxSize.toFloat() / greenBg.width, maxSize.toFloat() / greenBg.height)
        val scaledBitmap = Bitmap.createScaledBitmap(
            greenBg,
            (greenBg.width * scale).toInt(),
            (greenBg.height * scale).toInt(),
            true
        )

        val grabCutResult = GrabCutSegmentation.isolateGarment(
            scaledBitmap,
            touchX,
            touchY,
            imageWidth,
            imageHeight
        )

        android.util.Log.d("ImageGen", "Step 4: Replace remaining green with white")
        return replaceGreenWithWhite(grabCutResult)
    }

    fun paintSkinGreen(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val result = pixels.copyOf()
        for (i in result.indices) {
            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])

            val isSkin = r > 150 &&
                    g > 80 && g < 180 &&
                    b > 50 && b < 140 &&
                    r > g && r > b &&
                    (r - g) > 20 &&
                    (r - b) > 40 &&
                    r < 240

            if (isSkin) result[i] = Color.rgb(0, 255, 0) // vert vif
        }

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(result, 0, width, 0, 0, width, height)
        return resultBitmap
    }

    suspend fun reimagineGarment(context: Context, imageUri: Uri, prompt: String): Bitmap? {
        val imageBytes = context.contentResolver.openInputStream(imageUri)?.readBytes()
            ?: return null

        val base64Image = "data:image/jpeg;base64," + Base64.encodeToString(imageBytes, Base64.NO_WRAP)

        val requestBody = JsonObject(mapOf(
            "version" to JsonPrimitive("ac732df83cea7fff18b8472768c88ad041fa750ff7682a21affe81863cbe77e4"),
            "input" to JsonObject(mapOf(
                "prompt" to JsonPrimitive(prompt),
                "image" to JsonPrimitive(base64Image),
                "prompt_strength" to JsonPrimitive(0.3),
                "negative_prompt" to JsonPrimitive("person, mannequin, background, wrinkled"),
                "num_inference_steps" to JsonPrimitive(30)
            ))
        ))

        return try {
            val rawResponse = client.post("https://api.replicate.com/v1/predictions") {
                header("Authorization", "Bearer $replicateKey")
                header("Prefer", "wait")
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.bodyAsText()

            android.util.Log.d("Reimagine", "Response: ${rawResponse.take(300)}")

            val jsonResponse = Json.parseToJsonElement(rawResponse).jsonObject
            val predictionId = jsonResponse["id"]?.jsonPrimitive?.contentOrNull ?: return null
            var output = jsonResponse["output"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull
            var currentStatus = jsonResponse["status"]?.jsonPrimitive?.contentOrNull
            var attempts = 0

            while (output == null && currentStatus != "failed" && attempts < 30) {
                delay(2000)
                val pollRaw = client.get("https://api.replicate.com/v1/predictions/$predictionId") {
                    header("Authorization", "Bearer $replicateKey")
                }.bodyAsText()
                val pollJson = Json.parseToJsonElement(pollRaw).jsonObject
                currentStatus = pollJson["status"]?.jsonPrimitive?.contentOrNull
                output = pollJson["output"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull
                android.util.Log.d("Reimagine", "Status: $currentStatus")
                attempts++
            }

            if (output != null) {
                val bytes: ByteArray = client.get(output).body()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else null

        } catch (e: Exception) {
            android.util.Log.e("Reimagine", "Error: ${e.message}")
            null
        }
    }

    suspend fun isolateGarment(
        context: Context,
        imageUri: Uri,
        touchX: Float,
        touchY: Float,
        imageWidth: Float,
        imageHeight: Float
    ): Bitmap? {
        return generateCleanImage(context, imageUri, "")
    }

    fun cropGarment(bitmap: Bitmap, touchY: Float, imageHeight: Float): Bitmap {
        // Convertit la coordonnée du touch en coordonnée bitmap
        val scaleY = bitmap.height.toFloat() / imageHeight
        val centerY = (touchY * scaleY).toInt()

        // Estime la hauteur du vêtement (40% de la hauteur totale)
        val estimatedHeight = (bitmap.height * 0.4f).toInt()

        val top = (centerY - estimatedHeight / 2).coerceIn(0, bitmap.height)
        val bottom = (centerY + estimatedHeight / 2).coerceIn(0, bitmap.height)

        android.util.Log.d("Crop", "Center: $centerY, Top: $top, Bottom: $bottom")

        return Bitmap.createBitmap(bitmap, 0, top, bitmap.width, bottom - top)
    }

    fun fillHoles(bitmap: Bitmap): Bitmap {

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val whiteCount = pixels.count { isWhite(it) }
        val totalCount = pixels.size
        android.util.Log.d("FillHoles", "White pixels: $whiteCount / $totalCount (${whiteCount * 100 / totalCount}%)")



        // Marque tous les pixels blancs connectés aux bords comme "fond réel"
        val isTrueBg = BooleanArray(width * height) { false }
        val queue = java.util.LinkedList<Int>()

        // Ajoute tous les pixels blancs des bords
        for (x in 0 until width) {
            if (isWhite(pixels[x])) { isTrueBg[x] = true; queue.add(x) }
            val bottom = (height - 1) * width + x
            if (isWhite(pixels[bottom])) { isTrueBg[bottom] = true; queue.add(bottom) }
        }
        for (y in 0 until height) {
            if (isWhite(pixels[y * width])) { isTrueBg[y * width] = true; queue.add(y * width) }
            val right = y * width + width - 1
            if (isWhite(pixels[right])) { isTrueBg[right] = true; queue.add(right) }
        }

        // Flood fill depuis les bords
        while (queue.isNotEmpty()) {
            val idx = queue.poll()!!
            val x = idx % width
            val y = idx / width

            val neighbors = listOf(
                Pair(x-1, y), Pair(x+1, y),
                Pair(x, y-1), Pair(x, y+1)
            )
            for ((nx, ny) in neighbors) {
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue
                val nIdx = ny * width + nx
                if (!isTrueBg[nIdx] && isWhite(pixels[nIdx])) {
                    isTrueBg[nIdx] = true
                    queue.add(nIdx)
                }
            }
        }

        // Les pixels blancs non connectés aux bords = trous → on les colorie
        val result = pixels.copyOf()
        for (i in result.indices) {
            if (isWhite(pixels[i]) && !isTrueBg[i]) {
                // Cherche la couleur du voisin non blanc le plus proche
                val x = i % width
                val y = i / width
                result[i] = findNearestColor(pixels, x, y, width, height)
            }
        }

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(result, 0, width, 0, 0, width, height)
        return resultBitmap
    }

    private fun isWhite(color: Int): Boolean {
        return Color.red(color) > 240 && Color.green(color) > 240 && Color.blue(color) > 240
    }

    private fun findNearestColor(pixels: IntArray, x: Int, y: Int, width: Int, height: Int): Int {
        for (radius in 1..20) {
            for (dx in -radius..radius) {
                for (dy in -radius..radius) {
                    val nx = (x + dx).coerceIn(0, width - 1)
                    val ny = (y + dy).coerceIn(0, height - 1)
                    val pixel = pixels[ny * width + nx]
                    if (!isWhite(pixel)) return pixel
                }
            }
        }
        return Color.WHITE
    }
    fun removeSkin(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val result = pixels.copyOf()
        for (i in result.indices) {
            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])

            // Détection peau plus stricte
            val isSkin = r > 150 &&      // assez rouge
                    g > 80 && g < 180 && // vert moyen
                    b > 50 && b < 140 && // bleu faible
                    r > g && r > b &&
                    (r - g) > 20 &&
                    (r - b) > 40 &&      // plus strict sur le bleu
                    r < 240              // évite le blanc

            if (isSkin) result[i] = Color.WHITE
        }

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(result, 0, width, 0, 0, width, height)
        return resultBitmap
    }

    fun keepLargestRegion(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Identifie les pixels non-blancs
        val visited = BooleanArray(width * height)
        val regions = mutableListOf<Set<Int>>()

        for (i in pixels.indices) {
            if (!isWhite(pixels[i]) && !visited[i]) {
                // Flood fill pour trouver tous les pixels connectés
                val region = mutableSetOf<Int>()
                val queue = java.util.LinkedList<Int>()
                queue.add(i)
                visited[i] = true

                while (queue.isNotEmpty()) {
                    val idx = queue.poll()!!
                    region.add(idx)
                    val x = idx % width
                    val y = idx / width

                    val neighbors = listOf(
                        Pair(x-1, y), Pair(x+1, y),
                        Pair(x, y-1), Pair(x, y+1)
                    )
                    for ((nx, ny) in neighbors) {
                        if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue
                        val nIdx = ny * width + nx
                        if (!visited[nIdx] && !isWhite(pixels[nIdx])) {
                            visited[nIdx] = true
                            queue.add(nIdx)
                        }
                    }
                }
                regions.add(region)
            }
        }

        android.util.Log.d("ImageGen", "Found ${regions.size} regions")

        regions.forEachIndexed { index, region ->
            android.util.Log.d("ImageGen", "Region $index: ${region.size} pixels")
        }

        // Garde uniquement la plus grande région
        val largestRegion = regions.maxByOrNull { it.size } ?: return bitmap
        android.util.Log.d("ImageGen", "Largest region: ${largestRegion.size} pixels")

        val result = IntArray(width * height) { Color.WHITE }
        for (idx in largestRegion) {
            result[idx] = pixels[idx]
        }

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(result, 0, width, 0, 0, width, height)
        return resultBitmap
    }

    fun removeByTexture(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val result = pixels.copyOf()
        val windowSize = 8 // fenêtre d'analyse

        for (y in windowSize until height - windowSize) {
            for (x in windowSize until width - windowSize) {
                val idx = y * width + x
                if (isWhite(pixels[idx])) continue // déjà fond blanc

                // Calcule la variance locale des pixels voisins
                val neighbors = mutableListOf<Int>()
                for (dy in -windowSize..windowSize) {
                    for (dx in -windowSize..windowSize) {
                        val nIdx = (y + dy) * width + (x + dx)
                        if (!isWhite(pixels[nIdx])) {
                            neighbors.add(pixels[nIdx])
                        }
                    }
                }

                if (neighbors.size < 5) {
                    result[idx] = Color.WHITE
                    continue
                }

                // Calcule la variance des couleurs
                val avgR = neighbors.map { Color.red(it) }.average()
                val avgG = neighbors.map { Color.green(it) }.average()
                val avgB = neighbors.map { Color.blue(it) }.average()

                val variance = neighbors.map { c ->
                    val dr = Color.red(c) - avgR
                    val dg = Color.green(c) - avgG
                    val db = Color.blue(c) - avgB
                    dr * dr + dg * dg + db * db
                }.average()

                // Faible variance = texture uniforme = peau → blanc
                // Haute variance = texture riche = vêtement → garder
                if (variance < 100) {
                    result[idx] = Color.WHITE
                }
            }
        }

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(result, 0, width, 0, 0, width, height)
        return resultBitmap
    }

    fun erodeContours(bitmap: Bitmap, iterations: Int = 3): Bitmap {
        var current = bitmap
        repeat(iterations) {
            val width = current.width
            val height = current.height
            val pixels = IntArray(width * height)
            current.getPixels(pixels, 0, width, 0, 0, width, height)
            val result = pixels.copyOf()

            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val idx = y * width + x
                    if (!isWhite(pixels[idx])) {
                        // Si un voisin est blanc, ce pixel devient blanc
                        val hasWhiteNeighbor = listOf(
                            pixels[(y-1) * width + x],
                            pixels[(y+1) * width + x],
                            pixels[y * width + (x-1)],
                            pixels[y * width + (x+1)]
                        ).any { isWhite(it) }

                        if (hasWhiteNeighbor) result[idx] = Color.WHITE
                    }
                }
            }
            val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            resultBitmap.setPixels(result, 0, width, 0, 0, width, height)
            current = resultBitmap
        }
        return current
    }

    fun replaceWhiteWithGreen(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val result = pixels.copyOf()
        for (i in result.indices) {
            if (isWhite(pixels[i])) {
                result[i] = Color.rgb(0, 255, 0) // vert vif
            }
        }

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(result, 0, width, 0, 0, width, height)
        return resultBitmap
    }

    fun replaceGreenWithWhite(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val result = pixels.copyOf()
        for (i in result.indices) {
            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])
            if (g > 200 && r < 50 && b < 50) {
                result[i] = Color.WHITE
            }
        }

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(result, 0, width, 0, 0, width, height)
        return resultBitmap
    }
}