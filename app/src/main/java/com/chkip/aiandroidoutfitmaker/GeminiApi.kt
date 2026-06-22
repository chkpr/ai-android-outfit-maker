package com.chkip.aiandroidoutfitmaker

import android.R
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

    suspend fun generateOutfit(context: Context, imageUri: Uri, style: String = "Casual chic", wardrobeItems: List<ClothingItem> = emptyList()): String {
        val imageBytes = context.contentResolver.openInputStream(imageUri)?.readBytes()
            ?: return "Erreur : impossible de lire l'image"
        val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

        // Construit la liste des items du dressing si disponible
        val wardrobeContext = if (wardrobeItems.isNotEmpty()) {
            val itemsList = wardrobeItems.mapNotNull { item ->
                item.description
                    .lines()
                    .find { it.startsWith("DESCRIPTION:") }
                    ?.removePrefix("DESCRIPTION:")
                    ?.trim()
            }.joinToString("\n- ", prefix = "- ")

            "\n\nIMPORTANT: Crée les outfits en utilisant EN PRIORITÉ les vêtements suivants déjà disponibles dans le dressing :\n$itemsList\n\nQuand tu mentionnes ces vêtements dans les outfits, traduis leur nom en français de façon courte et naturelle (ex: 'votre top col V imprimé tropical', 'votre jean taille haute', etc.). N'suggère de nouveaux achats qu'en dernier recours."
        } else ""

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
                            text = """Tu es un styliste de mode créatif et audacieux, expert en tendances actuelles.
                            Analyse ce vêtement et réponds EXACTEMENT dans ce format :

                            DESCRIPTION: [description courte du vêtement en anglais]
                            COULEUR_PRINCIPALE: [couleur principale en anglais]
                            COULEURS_SECONDAIRES: [autres couleurs en anglais, séparées par des virgules, ou "none"]
                            TYPE: [type de vêtement en anglais]
                            A_MOTIF: [true ou false]
                            $wardrobeContext

                            OUTFITS:
                            1. [outfit dans le style $style - sois créatif, précis et inspirant. Cite des pièces spécifiques, des matières, des couleurs exactes]
                            2. [deuxième outfit ${style} - propose quelque chose d'inattendu et original]
                            3. [troisième outfit $style - ose une combinaison surprenante mais cohérente]
    
                            Pour chaque outfit, mentionne : les pièces du bas, les chaussures, les accessoires clés.
                            Réponds en français pour les outfits, en anglais pour le reste."""
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
