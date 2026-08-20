package com.storybrain.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Performs a bounded full decode after pure header inspection and before publication. */
object CoverBitmapValidator {
    fun validate(file: File, inspected: InspectedCoverImage) {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: throw IllegalArgumentException("封面生成失败（图片无法解码）")
        try {
            require(bitmap.width == inspected.width && bitmap.height == inspected.height) {
                "封面生成失败（解码尺寸不一致）"
            }
            require(bitmap.width.toLong() * bitmap.height.toLong() <= CoverImageInspector.MAX_PIXELS) {
                "封面生成失败（解码图片过大）"
            }
        } finally {
            bitmap.recycle()
        }
    }
}
