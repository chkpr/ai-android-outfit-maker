package com.chkip.aiandroidoutfitmaker

import android.graphics.Bitmap
import android.graphics.Color
import java.util.LinkedList

object FloodFill {

    fun isolateGarment(
        bitmap: Bitmap,
        touchX: Float,
        touchY: Float,
        imageWidth: Float,
        imageHeight: Float,
        tolerance: Int = 30
    ): Bitmap {
        // Convertit les coordonnées écran en coordonnées bitmap
        val scaleX = bitmap.width.toFloat() / imageWidth
        val scaleY = bitmap.height.toFloat() / imageHeight
        val startX = (touchX * scaleX).toInt().coerceIn(0, bitmap.width - 1)
        val startY = (touchY * scaleY).toInt().coerceIn(0, bitmap.height - 1)

        // Copie le bitmap en ARGB
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

// Calcule la tolérance automatiquement
        val autoTolerance = calculateTolerance(mutableBitmap, startX, startY)
        android.util.Log.d("FLOODFILL", "Bitmap: ${bitmap.width}x${bitmap.height}, Touch: $startX, $startY, Auto tolerance: $autoTolerance")
        val width = mutableBitmap.width
        val height = mutableBitmap.height

        // Lit tous les pixels
        val pixels = IntArray(width * height)
        mutableBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val targetColor = pixels[startY * width + startX]
        android.util.Log.d("FLOODFILL", "Target color: $targetColor")

        // Masque des pixels sélectionnés
        val selected = BooleanArray(width * height)
        val queue = LinkedList<Int>()
        queue.add(startY * width + startX)
        selected[startY * width + startX] = true

        // Flood fill
        while (queue.isNotEmpty()) {
            val idx = queue.poll()!!
            val x = idx % width
            val y = idx / width

            // Vérifie les 4 voisins
            val neighbors = listOf(
                Pair(x - 1, y), Pair(x + 1, y),
                Pair(x, y - 1), Pair(x, y + 1)
            )

            for ((nx, ny) in neighbors) {
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue
                val nIdx = ny * width + nx
                if (selected[nIdx]) continue
                if (colorSimilar(pixels[nIdx], targetColor, autoTolerance)) {
                    selected[nIdx] = true
                    queue.add(nIdx)
                }
            }
        }

        // Crée l'image résultat : sélection sur fond blanc
        val result = IntArray(width * height)
        for (i in result.indices) {
            result[i] = if (selected[i]) pixels[i] else Color.WHITE
        }

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(result, 0, width, 0, 0, width, height)
        return resultBitmap
    }

    fun calculateTolerance(bitmap: Bitmap, touchX: Int, touchY: Int): Int {
        val pixel = bitmap.getPixel(touchX, touchY)
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)

        // Luminosité du pixel touché (0-255)
        val luminosity = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

        android.util.Log.d("FLOODFILL", "Luminosity: $luminosity")

        return when {
            luminosity < 80 -> 60   // Très sombre (noir) → tolérance plus haute
            luminosity < 150 -> 40  // Couleurs moyennes
            luminosity > 200 -> 50  // Très clair (blanc) → tolérance haute
            else -> 45              // Standard
        }
    }

    private fun colorSimilar(c1: Int, c2: Int, tolerance: Int): Boolean {
        val dr = Math.abs(Color.red(c1) - Color.red(c2))
        val dg = Math.abs(Color.green(c1) - Color.green(c2))
        val db = Math.abs(Color.blue(c1) - Color.blue(c2))
        return dr <= tolerance && dg <= tolerance && db <= tolerance
    }
}