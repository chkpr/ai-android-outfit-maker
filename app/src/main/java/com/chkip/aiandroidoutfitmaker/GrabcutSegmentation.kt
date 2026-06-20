package com.chkip.aiandroidoutfitmaker

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

object GrabCutSegmentation {

    fun isolateGarment(
        bitmap: Bitmap,
        touchX: Float,
        touchY: Float,
        imageWidth: Float,
        imageHeight: Float
    ): Bitmap {
        val scaleX = bitmap.width.toFloat() / imageWidth
        val scaleY = bitmap.height.toFloat() / imageHeight
        val centerX = (touchX * scaleX).toInt()
        val centerY = (touchY * scaleY).toInt()

        android.util.Log.d("GrabCut", "Center: $centerX, $centerY in ${bitmap.width}x${bitmap.height}")

        // Convertit en Mat OpenCV
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)
        Imgproc.cvtColor(src, src, Imgproc.COLOR_RGBA2RGB)

        // Rectangle large couvrant toute l'image
        val rect = Rect(
            (bitmap.width * 0.02f).toInt(),
            (bitmap.height * 0.02f).toInt(),
            (bitmap.width * 0.96f).toInt(),
            (bitmap.height * 0.96f).toInt()
        )

        // GrabCut initial avec rectangle
        val mask = Mat.zeros(src.size(), CvType.CV_8UC1)
        val bgModel = Mat.zeros(1, 65, CvType.CV_64F)
        val fgModel = Mat.zeros(1, 65, CvType.CV_64F)

        Imgproc.grabCut(src, mask, rect, bgModel, fgModel, 3, Imgproc.GC_INIT_WITH_RECT)

        // Ajoute le hint : le point touché est certainement le vêtement
        val hintRadius = (bitmap.width * 0.05f).toInt()
        for (dy in -hintRadius..hintRadius) {
            for (dx in -hintRadius..hintRadius) {
                val nx = (centerX + dx).coerceIn(0, bitmap.width - 1)
                val ny = (centerY + dy).coerceIn(0, bitmap.height - 1)
                mask.put(ny, nx, Imgproc.GC_FGD.toDouble())
            }
        }

        // Hint négatif : le bas de l'image est le fond
        val bottomMargin = (bitmap.height * 0.15f).toInt()
        for (y in bitmap.height - bottomMargin until bitmap.height) {
            for (x in 0 until bitmap.width) {
                mask.put(y, x, Imgproc.GC_BGD.toDouble())
            }
        }

        // Hint négatif : le haut de l'image est le fond
        val topMargin = (bitmap.height * 0.08f).toInt()
        for (y in 0 until topMargin) {
            for (x in 0 until bitmap.width) {
                mask.put(y, x, Imgproc.GC_BGD.toDouble())
            }
        }

        // Relance GrabCut avec le hint
        Imgproc.grabCut(src, mask, rect, bgModel, fgModel, 3, Imgproc.GC_INIT_WITH_MASK)

        // Masque final
        val fgMask = Mat()
        val probFgMask = Mat()
        Core.compare(mask, Scalar(Imgproc.GC_FGD.toDouble()), fgMask, Core.CMP_EQ)
        Core.compare(mask, Scalar(Imgproc.GC_PR_FGD.toDouble()), probFgMask, Core.CMP_EQ)
        Core.add(fgMask, probFgMask, fgMask)



        // Lisse les bords du masque
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
        Imgproc.morphologyEx(fgMask, fgMask, Imgproc.MORPH_CLOSE, kernel)
        Imgproc.morphologyEx(fgMask, fgMask, Imgproc.MORPH_DILATE, kernel)

// Applique le masque lissé
        val srcRgba = Mat()
        Utils.bitmapToMat(bitmap, srcRgba)
        val white = Mat(srcRgba.size(), srcRgba.type(), Scalar(255.0, 255.0, 255.0, 255.0))
        val result = white.clone()
        srcRgba.copyTo(result, fgMask)

        val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(result, resultBitmap)

        android.util.Log.d("GrabCut", "Done!")
        return resultBitmap
    }
}

