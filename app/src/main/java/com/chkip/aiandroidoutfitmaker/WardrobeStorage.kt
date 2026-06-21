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
    val category: String="Autres",
    val addedAt: Long = System.currentTimeMillis()
)

fun mapTypeToCategory(type: String): String {
    val typeLower = type.lowercase()
    return when {
        typeLower.contains("top") || typeLower.contains("tank") ||
                typeLower.contains("tshirt") || typeLower.contains("t-shirt") ||
                typeLower.contains("blouse") || typeLower.contains("shirt") -> "Tops"

        typeLower.contains("jean") || typeLower.contains("denim") -> "Jeans"

        typeLower.contains("pant") || typeLower.contains("trouser") ||
                typeLower.contains("legging") -> "Pantalons"

        typeLower.contains("pull") || typeLower.contains("sweater") ||
                typeLower.contains("knit") || typeLower.contains("jumper") -> "Pulls"

        typeLower.contains("cardigan") || typeLower.contains("gilet") ||
                typeLower.contains("vest") -> "Gilets"

        typeLower.contains("jacket") || typeLower.contains("blazer") ||
                typeLower.contains("veste") -> "Vestes"

        typeLower.contains("coat") || typeLower.contains("manteau") -> "Manteaux"

        typeLower.contains("skirt") || typeLower.contains("jupe") -> "Jupes"

        typeLower.contains("dress") || typeLower.contains("robe") -> "Robes"

        typeLower.contains("belt") || typeLower.contains("ceinture") -> "Ceintures"

        typeLower.contains("hat") || typeLower.contains("cap") ||
                typeLower.contains("chapeau") || typeLower.contains("casquette") -> "Chapeaux & Casquettes"

        typeLower.contains("scarf") || typeLower.contains("foulard") ||
                typeLower.contains("écharpe") -> "Foulards & Écharpes"

        typeLower.contains("top") || typeLower.contains("tank") ||
                typeLower.contains("tshirt") || typeLower.contains("t-shirt") ||
                typeLower.contains("blouse") || typeLower.contains("shirt") ||
                typeLower.contains("tee") -> "Tops"  // 👈 ajoute "tee"

        typeLower.contains("cardigan") || typeLower.contains("gilet") ||
                typeLower.contains("vest") || typeLower.contains("knit vest") -> "Gilets"  // 👈

        else -> "Autres"
    }
}
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

        fun recategorizeItems() {
            val items = getItems().map { item ->
                val type = item.description
                    .lines()
                    .find { it.startsWith("TYPE:") }
                    ?.removePrefix("TYPE:")
                    ?.trim() ?: ""
                val category = if (type.isNotEmpty()) mapTypeToCategory(type) else item.category
                item.copy(category = category)
            }
            prefs.edit().putString("items", json.encodeToString(items)).apply()
        }
    }

