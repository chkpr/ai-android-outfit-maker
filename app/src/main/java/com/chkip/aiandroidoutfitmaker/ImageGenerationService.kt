package com.chkip.aiandroidoutfitmaker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable
data class ReplicateRequest(
    val version: String,
    val input: ReplicateInput
)

@Serializable
data class ReplicateInput(
    val prompt: String,
    @SerialName("negative_prompt")
    val negativePrompt: String = "wrinkled, messy, person, background, hanger, crumpled, dirty",
    @SerialName("num_inference_steps")
    val numInferenceSteps: Int = 30,
    @SerialName("guidance_scale")
    val guidanceScale: Double = 7.5
)

class ImageGenerationService {
    private val apiKey = BuildConfig.REPLICATE_API_KEY
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }
        engine {
            connectTimeout = 60_000
            socketTimeout = 60_000
        }
    }

    suspend fun generateCleanImage(context: Context, imageUri: Uri, clothingDescription: String): Bitmap? {
        val prompt = "Professional product photo of $clothingDescription, flat lay on pure white background, perfectly smooth and neat, studio lighting, high quality, e-commerce style"

        android.util.Log.d("ImageGen", "API Key: ${apiKey.take(10)}...")

        val requestBody = ReplicateRequest(
            version = "ac732df83cea7fff18b8472768c88ad041fa750ff7682a21affe81863cbe77e4",
            input = ReplicateInput(prompt = prompt)
        )
        android.util.Log.d("ImageGen", "Request body: ${json.encodeToString(requestBody)}")

        return try {
            val rawResponse = client.post("https://api.replicate.com/v1/predictions") {
                header("Authorization", "Bearer $apiKey")
                header("Prefer", "wait")
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.bodyAsText()

            android.util.Log.d("ImageGen", "Raw response: $rawResponse")

            val jsonResponse = Json.parseToJsonElement(rawResponse).jsonObject
            val predictionId = jsonResponse["id"]?.jsonPrimitive?.contentOrNull
            val status = jsonResponse["status"]?.jsonPrimitive?.contentOrNull

            android.util.Log.d("ImageGen", "ID: $predictionId, Status: $status")

            if (predictionId == null) {
                android.util.Log.e("ImageGen", "No prediction ID")
                return null
            }

            var currentStatus = status
            var output: String? = null
            var attempts = 0

            while (currentStatus != "succeeded" && currentStatus != "failed" && attempts < 30) {
                delay(2000)
                val pollRaw = client.get("https://api.replicate.com/v1/predictions/$predictionId") {
                    header("Authorization", "Bearer $apiKey")
                }.bodyAsText()
                android.util.Log.d("ImageGen", "Poll: $pollRaw")
                val pollJson = Json.parseToJsonElement(pollRaw).jsonObject
                currentStatus = pollJson["status"]?.jsonPrimitive?.contentOrNull
                output = pollJson["output"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull
                attempts++
            }

            if (output != null) {
                android.util.Log.d("ImageGen", "Image URL: $output")
                val bytes: ByteArray = client.get(output).body()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else {
                android.util.Log.e("ImageGen", "No output after $attempts attempts")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageGen", "Error: ${e.message}")
            null
        }
    }
}