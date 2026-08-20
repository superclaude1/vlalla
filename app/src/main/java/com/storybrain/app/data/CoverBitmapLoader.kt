package com.storybrain.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

object CoverBitmapLoader {
    fun decodeSampled(path: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        if (targetWidth <= 0 || targetHeight <= 0) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        if (bounds.outWidth > CoverImageInspector.MAX_DIMENSION ||
            bounds.outHeight > CoverImageInspector.MAX_DIMENSION ||
            bounds.outWidth.toLong() * bounds.outHeight.toLong() > CoverImageInspector.MAX_PIXELS
        ) return null

        var sampleSize = 1
        while (
            bounds.outWidth / (sampleSize * 2) >= targetWidth &&
            bounds.outHeight / (sampleSize * 2) >= targetHeight
        ) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        })
    }
}
