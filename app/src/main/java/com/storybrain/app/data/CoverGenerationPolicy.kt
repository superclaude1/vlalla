package com.storybrain.app.data

import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

object CoverGenerationPolicy {
    const val MAX_TITLE_LENGTH = 120
    const val MIN_RESPONSE_BYTES = 1_024
    const val MAX_RESPONSE_BYTES = 10 * 1024 * 1024

    private val allowedContentTypes = setOf("image/jpeg", "image/png", "image/webp")

    fun sanitizeTitle(title: String): String = title
        .filterNot(Char::isISOControl)
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_TITLE_LENGTH)
        .ifBlank { "未命名小说" }

    fun isAcceptableImage(contentType: String?, bytes: ByteArray): Boolean =
        isAcceptableImage(contentType, bytes.size.toLong())

    fun isAcceptableImage(contentType: String?, contentLength: Long): Boolean {
        val normalizedType = contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
        return normalizedType in allowedContentTypes &&
            (contentLength == -1L || contentLength > MIN_RESPONSE_BYTES) &&
            contentLength <= MAX_RESPONSE_BYTES
    }

    fun extensionFor(contentType: String?): String = when (
        contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
    ) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }

    fun fileName(bookId: String, extension: String = "jpg", generationId: String? = null): String {
        val identity = if (generationId == null) bookId else "$bookId\u0000$generationId"
        return sha256(identity).take(32) + "." + extension.filter(Char::isLetterOrDigit).lowercase(Locale.ROOT)
    }

    fun isManagedPath(filesDir: File, path: String): Boolean = runCatching {
        val coversDir = File(filesDir, "covers").canonicalFile
        val candidate = File(path).canonicalFile
        candidate.parentFile == coversDir && candidate.name.isNotBlank()
    }.getOrDefault(false)

    fun stableSeed(bookId: String): Int = MessageDigest.getInstance("SHA-256")
        .digest(bookId.toByteArray(StandardCharsets.UTF_8))
        .fold(0) { acc, byte -> acc * 31 + byte }
        .and(0x7fffffff)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(Locale.ROOT, it) }
}
