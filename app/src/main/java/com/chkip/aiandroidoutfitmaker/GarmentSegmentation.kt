package com.chkip.aiandroidoutfitmaker

import ai.onnxruntime.*
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import java.nio.FloatBuffer

object GarmentSegmentation {

    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()

    fun initialize(context: Context) {
        if (session != null) return
        android.util.Log.d("GarmentSeg", "Initializing...")
        copyAssetToFile(context, "garment_segmentation.onnx")
        copyAssetToFile(context, "garment_segmentation.onnx.data")
        val modelPath = "${context.filesDir}/garment_segmentation.onnx"
        session = env.createSession(modelPath)
        android.util.Log.d("GarmentSeg", "Initialized!")
    }

    private fun copyAssetToFile(context: Context, filename: String) {
        val outFile = java.io.File(context.filesDir, filename)
        outFile.delete()
        context.assets.open(filename).use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        android.util.Log.d("GarmentSeg", "Copied $filename")
    }

    fun isolateGarment(bitmap: Bitmap): Bitmap {
        val sess = session ?: return bitmap

        // Redimensionne à 768x768
        val resized = Bitmap.createScaledBitmap(bitmap, 768, 768, true)

        // Normalisation ImageNet
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        val pixels = IntArray(768 * 768)
        resized.getPixels(pixels, 0, 768, 0, 0, 768, 768)

        val inputData = FloatArray(3 * 768 * 768)
        for (i in pixels.indices) {
            inputData[i] = (Color.red(pixels[i]) / 255f - mean[0]) / std[0]
            inputData[768 * 768 + i] = (Color.green(pixels[i]) / 255f - mean[1]) / std[1]
            inputData[2 * 768 * 768 + i] = (Color.blue(pixels[i]) / 255f - mean[2]) / std[2]
        }

        val tensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(inputData),
            longArrayOf(1, 3, 768, 768)
        )

        val outputs = sess.run(mapOf("image" to tensor))
        val maskRaw = outputs.get("mask").get().value as Array<*>

        // Applique le masque sur le bitmap original
        return applyMask(bitmap, maskRaw)
    }

    private fun applyMask(bitmap: Bitmap, maskRaw: Array<*>): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val result = IntArray(width * height) { Color.WHITE }

        val maskData = ((maskRaw[0] as Array<*>)[0] as Array<*>)
        val maskH = maskData.size
        val maskW = (maskData[0] as FloatArray).size

        android.util.Log.d("GarmentSeg", "Mask: ${maskH}x${maskW}")

        val firstRow = (maskData[0] as FloatArray)
        android.util.Log.d("GarmentSeg", "Mask values min/max: ${firstRow.min()} / ${firstRow.max()}")

        for (y in 0 until height) {
            for (x in 0 until width) {
                val maskX = (x * maskW.toFloat() / width).toInt().coerceIn(0, maskW - 1)
                val maskY = (y * maskH.toFloat() / height).toInt().coerceIn(0, maskH - 1)
                val maskVal = (maskData[maskY] as FloatArray)[maskX]
                if (maskVal > 0f) {
                    result[y * width + x] = pixels[y * width + x]
                }
            }
        }

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(result, 0, width, 0, 0, width, height)
        return resultBitmap
    }
}