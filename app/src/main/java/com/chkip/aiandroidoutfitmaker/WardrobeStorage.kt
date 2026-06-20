package com.chkip.aiandroidoutfitmaker

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ClothingItem(
    val id: String,
    val photoPath: String,
    val description: String,
    val addedAt: Long = System.currentTimeMillis()
)

class WardrobeStorage(context: Context) {
    private val prefs = context.getSharedPreferences("wardrobe", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun saveItem(item: ClothingItem) {
        val items = getItems().toMutableList()
        items.add(item)
        prefs.edit().putString("items", json.encodeToString(items)).apply()
    }

    fun getItems(): List<ClothingItem> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun deleteItem(id: String) {
        val items = getItems().filter { it.id != id }
        prefs.edit().putString("items", json.encodeToString(items)).apply()
    }
}