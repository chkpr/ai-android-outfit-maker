package com.chkip.aiandroidoutfitmaker

import ai.onnxruntime.*
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import java.nio.FloatBuffer

object MobileSAMSegmentation {

    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()

    fun initialize(context: Context) {
        if (encoderSession != null) return

        android.util.Log.d("MobileSAM", "Initializing...")

        copyAssetToFile(context, "mobile_sam_encoder.onnx")
        copyAssetToFile(context, "mobile_sam_encoder.onnx.data")
        copyAssetToFile(context, "mobile_sam_decoder_hf.onnx")

        val encoderPath = "${context.filesDir}/mobile_sam_encoder.onnx"
        val decoderPath = "${context.filesDir}/mobile_sam_decoder_hf.onnx"

        encoderSession = env.createSession(encoderPath)
        decoderSession = env.createSession(decoderPath)

        android.util.Log.d("MobileSAM", "Initialized!")
    }

    private fun copyAssetToFile(context: Context, filename: String) {
        val outFile = java.io.File(context.filesDir, filename)
        outFile.delete()
        context.assets.open(filename).use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        android.util.Log.d("MobileSAM", "Copied $filename")
    }

    fun isolateGarment(
        bitmap: Bitmap,
        touchX: Float,
        touchY: Float,
        imageWidth: Float,
        imageHeight: Float
    ): Bitmap {
        val encoder = encoderSession ?: return bitmap
        val decoder = decoderSession ?: return bitmap

        // Redimensionne en gardant le ratio avec padding noir
        val resized = resizeWithPadding(bitmap, 1024)

        val imageData = bitmapToFloatArray(resized)
        val imageTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(imageData),
            longArrayOf(1, 3, 1024, 1024)
        )

        // Encode l'image
        val encoderInputs = mapOf("image" to imageTensor)
        val encoderOutputs = encoder.run(encoderInputs)
        val embeddingsTensor = encoderOutputs.get("image_embeddings").get() as OnnxTensor

        // Convertit le point de touch en coordonnées 1024x1024
        val scale = 1024f / maxOf(bitmap.width, bitmap.height)
        val scaleX = scale * bitmap.width / imageWidth
        val scaleY = scale * bitmap.height / imageHeight
        val pointX = touchX * scaleX
        val pointY = touchY * scaleY

        android.util.Log.d("MobileSAM", "Point: $pointX, $pointY")
        android.util.Log.d("MobileSAM", "Bitmap size: ${bitmap.width}x${bitmap.height}")

        // Point touché = foreground (1)
// Points aux coins = background (0)
        val topY = bitmap.height * 0.05f * scale
        val bottomY = bitmap.height * 0.95f * scale
        val leftX = bitmap.width * 0.05f * scale
        val rightX = bitmap.width * 0.95f * scale

        val centerX = pointX
        val centerY = pointY
        val offsetX = bitmap.width * 0.1f * scale
        val offsetY = bitmap.height * 0.1f * scale

        val midY = bitmap.height * 0.5f * scale

        val pointCoords = floatArrayOf(
            pointX, pointY,                    // point touché = vêtement
            centerX + offsetX, centerY,        // hint positif droite
            centerX - offsetX, centerY,        // hint positif gauche
            centerX, centerY - offsetY,        // hint positif haut
            leftX, topY,                       // coin haut gauche = fond
            rightX, topY,                      // coin haut droit = fond
            leftX, bottomY,                    // coin bas gauche = fond
            rightX, bottomY                    // coin bas droit = fond
        )
        val pointLabels = floatArrayOf(1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f)

        val maskInput = FloatArray(1 * 1 * 256 * 256) { 0f }
        val hasMaskInput = floatArrayOf(0f)
        val origImSize = floatArrayOf(bitmap.height.toFloat(), bitmap.width.toFloat())

        val pointCoordsTensor = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(pointCoords), longArrayOf(1, 8, 2)
        )
        val pointLabelsTensor = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(pointLabels), longArrayOf(1, 8)
        )
        val maskInputTensor = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(maskInput), longArrayOf(1, 1, 256, 256)
        )
        val hasMaskInputTensor = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(hasMaskInput), longArrayOf(1)
        )
        val origImSizeTensor = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(origImSize), longArrayOf(2)
        )

        val decoderInputs = mapOf(
            "image_embeddings" to embeddingsTensor,
            "point_coords" to pointCoordsTensor,
            "point_labels" to pointLabelsTensor,
            "mask_input" to maskInputTensor,
            "has_mask_input" to hasMaskInputTensor,
            "orig_im_size" to origImSizeTensor
        )

        val decoderOutputs = decoder.run(decoderInputs)
        val masks = decoderOutputs.get("masks").get().value as Array<*>

        val rawMasks = decoderOutputs.get("masks").get().value
        android.util.Log.d("MobileSAM", "masks type: ${rawMasks?.javaClass}")
        val l1 = rawMasks as Array<*>
        android.util.Log.d("MobileSAM", "l1 size: ${l1.size}, type: ${l1[0]?.javaClass}")
        val l2 = l1[0] as Array<*>
        android.util.Log.d("MobileSAM", "l2 size: ${l2.size}, type: ${l2[0]?.javaClass}")
        val l3 = l2[0] as Array<*>
        android.util.Log.d("MobileSAM", "l3 size: ${l3.size}, type: ${l3[0]?.javaClass}")
        val l4 = l3[0] as FloatArray
        android.util.Log.d("MobileSAM", "l4 size: ${l4.size}")



        return applyMask(bitmap, masks, imageWidth, imageHeight)
    }

    private fun bitmapToFloatArray(bitmap: Bitmap): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)

        val result = FloatArray(3 * width * height)
        for (i in pixels.indices) {
            result[i] = (Color.red(pixels[i]) / 255f - mean[0]) / std[0]
            result[width * height + i] = (Color.green(pixels[i]) / 255f - mean[1]) / std[1]
            result[2 * width * height + i] = (Color.blue(pixels[i]) / 255f - mean[2]) / std[2]
        }
        return result
    }

    private fun applyMask(
        bitmap: Bitmap,
        masks: Array<*>,
        imageWidth: Float,
        imageHeight: Float
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val result = IntArray(width * height) { Color.WHITE }

        val maskRows = ((masks[0] as Array<*>)[0] as Array<*>)
        val maskH = maskRows.size
        val maskW = (maskRows[0] as FloatArray).size

        android.util.Log.d("MobileSAM", "Bitmap: ${width}x${height}, Mask: ${maskH}x${maskW}")

        for (y in 0 until height) {
            for (x in 0 until width) {
                // Mapping direct proportionnel
                val maskX = (x.toFloat() * maskW / width).toInt().coerceIn(0, maskW - 1)
                val maskY = (y.toFloat() * maskH / height).toInt().coerceIn(0, maskH - 1)
                val maskVal = (maskRows[maskY] as FloatArray)[maskX]
                if (maskVal > 0f) {
                    result[y * width + x] = pixels[y * width + x]
                }
            }
        }

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(result, 0, width, 0, 0, width, height)
        return resultBitmap
    }

    private fun resizeWithPadding(bitmap: Bitmap, targetSize: Int): Bitmap {
        val scale = targetSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        val newW = (bitmap.width * scale).toInt()
        val newH = (bitmap.height * scale).toInt()

        val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        val result = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(scaled, 0f, 0f, null)
        return result
    }
}