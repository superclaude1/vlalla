package com.storybrain.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BookCoverStore(
    context: Context,
    private val repository: StoryRepository
) {
    private val appContext = context.applicationContext

    suspend fun import(bookId: String, uri: Uri): String = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: error("无法读取封面图片")
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法识别封面图片" }
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_DECODE_EDGE * 2) sample *= 2
        val bitmap = appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("封面图片解码失败")
        val scale = (MAX_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
        val outputBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        val directory = File(appContext.filesDir, "covers").apply { mkdirs() }
        val destination = File(directory, "$bookId.webp")
        val temporary = File(directory, "$bookId.webp.part")
        try {
            FileOutputStream(temporary).use { stream ->
                require(outputBitmap.compress(Bitmap.CompressFormat.WEBP, 86, stream)) { "封面压缩失败" }
            }
            require(temporary.length() > 0) { "封面文件为空" }
            if (destination.exists()) destination.delete()
            require(temporary.renameTo(destination)) { "封面保存失败" }
            val relative = "covers/${destination.name}"
            repository.updateBookCoverPath(bookId, relative)
            relative
        } finally {
            temporary.delete()
            if (outputBitmap !== bitmap) outputBitmap.recycle()
            bitmap.recycle()
        }
    }

    suspend fun restoreDefault(bookId: String, currentPath: String?) = withContext(Dispatchers.IO) {
        currentPath?.let { File(appContext.filesDir, it).takeIf(File::isFile)?.delete() }
        repository.updateBookCoverPath(bookId, null)
    }

    private companion object {
        const val MAX_EDGE = 1200
        const val MAX_DECODE_EDGE = 1200
    }
}
