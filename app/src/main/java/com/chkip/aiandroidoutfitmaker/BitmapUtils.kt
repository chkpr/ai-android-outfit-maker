package com.chkip.aiandroidoutfitmaker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

object BitmapUtils {
    fun loadCorrectlyOrientedBitmap(context: Context, uri: Uri): Bitmap? {
        val bitmap = android.graphics.BitmapFactory.decodeStream(
            context.contentResolver.openInputStream(uri)
        ) ?: return null

        val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
        val exif = ExifInterface(inputStream)
        val rotation = when (exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        return if (rotation != 0f) {
            val matrix = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else bitmap
    }
}