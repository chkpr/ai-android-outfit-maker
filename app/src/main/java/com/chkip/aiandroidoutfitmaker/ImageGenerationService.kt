package com.chkip.aiandroidoutfitmaker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*

class ImageGenerationService {
    private val replicateKey = BuildConfig.REPLICATE_API_KEY
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

    suspend fun isolateGarment(
        context: Context,
        imageUri: Uri,
        touchX: Float,
        touchY: Float,
        imageWidth: Float,
        imageHeight: Float
    ): Bitmap? {
        val imageBytes = context.contentResolver.openInputStream(imageUri)?.readBytes()
            ?: return null

        // Charge le bitmap correctement orienté pour avoir les bonnes dimensions
        val bitmap = BitmapUtils.loadCorrectlyOrientedBitmap(context, imageUri) ?: return null

        // Convertit les coordonnées écran en coordonnées image réelles
        val scaleX = bitmap.width.toFloat() / imageWidth
        val scaleY = bitmap.height.toFloat() / imageHeight
        val pointX = (touchX * scaleX).toInt().coerceIn(0, bitmap.width - 1)
        val pointY = (touchY * scaleY).toInt().coerceIn(0, bitmap.height - 1)

        android.util.Log.d("SAM", "Point: $pointX, $pointY in bitmap ${bitmap.width}x${bitmap.height}")

        val base64Image = "data:image/jpeg;base64," + Base64.encodeToString(imageBytes, Base64.NO_WRAP)

        val requestBody = JsonObject(mapOf(
            "version" to JsonPrimitive("b28e02c3844df2c44dcb2cb96ba2496435681bf88878e3bd0ab6b401a971d79e"),
            "input" to JsonObject(mapOf(
                "image" to JsonPrimitive(base64Image),
                "mask_limit" to JsonPrimitive(10),
                "mask_only" to JsonPrimitive(true)
            ))
        ))

        return try {
            val rawResponse = client.post("https://api.replicate.com/v1/predictions") {
                header("Authorization", "Bearer $replicateKey")
                header("Prefer", "wait")
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.bodyAsText()

            android.util.Log.d("SAM", "Response: ${rawResponse.take(300)}")

            val jsonResponse = Json.parseToJsonElement(rawResponse).jsonObject
            val predictionId = jsonResponse["id"]?.jsonPrimitive?.contentOrNull ?: return null

            var output: String? = jsonResponse["output"]?.jsonPrimitive?.contentOrNull
            var currentStatus = jsonResponse["status"]?.jsonPrimitive?.contentOrNull
            var attempts = 0

            while (output == null && currentStatus != "failed" && attempts < 30) {
                delay(2000)
                val pollRaw = client.get("https://api.replicate.com/v1/predictions/$predictionId") {
                    header("Authorization", "Bearer $replicateKey")
                }.bodyAsText()
                val pollJson = Json.parseToJsonElement(pollRaw).jsonObject
                currentStatus = pollJson["status"]?.jsonPrimitive?.contentOrNull
                output = pollJson["output"]?.jsonPrimitive?.contentOrNull
                android.util.Log.d("SAM", "Status: $currentStatus")
                attempts++
            }

            if (output == null) return null

            android.util.Log.d("SAM", "Mask URL: $output")

            // Télécharge le masque
            val maskBytes: ByteArray = client.get(output).body()
            val maskBitmap = BitmapFactory.decodeByteArray(maskBytes, 0, maskBytes.size) ?: return null

            // Applique le masque sur l'image originale
            applyMask(bitmap, maskBitmap)

        } catch (e: Exception) {
            android.util.Log.e("SAM", "Error: ${e.message}")
            null
        }
    }

    private fun applyMask(original: Bitmap, mask: Bitmap): Bitmap {
        val scaledMask = Bitmap.createScaledBitmap(mask, original.width, original.height, true)
        val result = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val originalPixels = IntArray(original.width * original.height)
        val maskPixels = IntArray(scaledMask.width * scaledMask.height)
        original.getPixels(originalPixels, 0, original.width, 0, 0, original.width, original.height)
        scaledMask.getPixels(maskPixels, 0, scaledMask.width, 0, 0, scaledMask.width, scaledMask.height)

        val resultPixels = IntArray(original.width * original.height)
        for (i in resultPixels.indices) {
            // Si le masque est blanc/clair = garder le pixel original, sinon blanc
            val maskBrightness = Color.red(maskPixels[i])
            resultPixels[i] = if (maskBrightness > 128) originalPixels[i] else Color.WHITE
        }

        result.setPixels(resultPixels, 0, original.width, 0, 0, original.width, original.height)
        return result
    }

    // Garde la méthode existante pour la compatibilité
    suspend fun generateCleanImage(context: Context, imageUri: Uri, clothingDescription: String): Bitmap? {
        return isolateGarment(context, imageUri, 0f, 0f, 1f, 1f)
    }
}