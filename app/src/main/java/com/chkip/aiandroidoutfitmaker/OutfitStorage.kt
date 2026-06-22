package com.chkip.aiandroidoutfitmaker

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SavedOutfit(
    val id: String,
    val title: String,
    val description: String,
    val garmentDescription: String = "",
    val savedAt: Long = System.currentTimeMillis()
)

class OutfitStorage(context: Context) {
    private val prefs = context.getSharedPreferences("outfits", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun saveOutfit(outfit: SavedOutfit) {
        val outfits = getOutfits().toMutableList()
        outfits.add(outfit)
        prefs.edit().putString("outfits", json.encodeToString(outfits)).apply()
    }

    fun getOutfits(): List<SavedOutfit> {
        val raw = prefs.getString("outfits", null) ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun deleteOutfit(id: String) {
        val outfits = getOutfits().filter { it.id != id }
        prefs.edit().putString("outfits", json.encodeToString(outfits)).apply()
    }
}