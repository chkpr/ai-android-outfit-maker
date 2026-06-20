package com.chkip.aiandroidoutfitmaker

import android.content.Context
import android.net.Uri
import android.util.Base64
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val inline_data: GeminiInlineData? = null
)

@Serializable
data class GeminiInlineData(
    val mime_type: String,
    val data: String
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val promptFeedback: JsonObject? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent
)

class GeminiApiService {
    private val apiKey = BuildConfig.GEMINI_API_KEY // 👈 mets ta clé ici
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun generateOutfit(context: Context, imageUri: Uri): String {
        val imageBytes = context.contentResolver.openInputStream(imageUri)?.readBytes()
            ?: return "Erreur : impossible de lire l'image"
        val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(
                            inline_data = GeminiInlineData(
                                mime_type = "image/jpeg",
                                data = base64Image
                            )
                        ),
                        GeminiPart(
                            text = """Tu es un expert en mode et stylisme.
                                    Analyse ce vêtement et réponds EXACTEMENT dans ce format :

                                    DESCRIPTION: [description courte du vêtement en anglais, ex: "white cotton t-shirt with round neck"]
        
                                     OUTFITS:
                                        1. [premier outfit suggéré]
                                        2. [deuxième outfit suggéré]  
                                        3. [troisième outfit suggéré]
        
                                    Réponds en français pour les outfits, en anglais uniquement pour la DESCRIPTION."""
                        )
                    )
                )
            )
        )

        return try {
            val rawResponse = client.post(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            ) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.bodyAsText() // 👈 texte brut pour déboguer

            android.util.Log.d("GEMINI", rawResponse) // 👈 log dans Logcat

            val response = Json { ignoreUnknownKeys = true }.decodeFromString<GeminiResponse>(rawResponse)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Aucune suggestion générée"
        } catch (e: Exception) {
            "Erreur : ${e.message}"
        }
        }
    }
