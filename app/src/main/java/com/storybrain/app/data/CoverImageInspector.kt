package com.storybrain.app.data

import java.io.File
import java.io.RandomAccessFile

enum class CoverImageFormat(val mimeType: String, val extension: String) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp")
}

data class InspectedCoverImage(
    val format: CoverImageFormat,
    val width: Int,
    val height: Int
)

/** Header-only validation performed before Android decodes any untrusted cover. */
object CoverImageInspector {
    const val MAX_DIMENSION = 2_048
    const val MAX_PIXELS = 4_000_000L

    fun inspect(file: File, declaredContentType: String?): InspectedCoverImage {
        require(file.length() in (CoverGenerationPolicy.MIN_RESPONSE_BYTES + 1L)..CoverGenerationPolicy.MAX_RESPONSE_BYTES) {
            "封面生成失败（图片大小异常）"
        }
        val expectedMime = declaredContentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?: throw IllegalArgumentException("封面生成失败（缺少图片类型）")
        val inspected = RandomAccessFile(file, "r").use { source ->
            when {
                isPng(source) -> inspectPng(source)
                isJpeg(source) -> inspectJpeg(source)
                isWebp(source) -> inspectWebp(source)
                else -> throw IllegalArgumentException("封面生成失败（不是有效图片）")
            }
        }
        require(inspected.format.mimeType == expectedMime) { "封面生成失败（图片格式与响应类型不匹配）" }
        require(inspected.width in 1..MAX_DIMENSION && inspected.height in 1..MAX_DIMENSION) {
            "封面生成失败（图片尺寸异常）"
        }
        require(inspected.width.toLong() * inspected.height.toLong() <= MAX_PIXELS) {
            "封面生成失败（图片像素过大）"
        }
        return inspected
    }

    private fun isPng(source: RandomAccessFile): Boolean {
        source.seek(0)
        val signature = ByteArray(8)
        if (source.read(signature) != signature.size) return false
        return signature.contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        )
    }

    private fun inspectPng(source: RandomAccessFile): InspectedCoverImage {
        source.seek(8)
        require(source.readInt() == 13 && source.readInt() == 0x49484452) { "封面生成失败（PNG 头无效）" }
        return InspectedCoverImage(CoverImageFormat.PNG, source.readInt(), source.readInt())
    }

    private fun isJpeg(source: RandomAccessFile): Boolean {
        source.seek(0)
        return source.readUnsignedShort() == 0xffd8
    }

    private fun inspectJpeg(source: RandomAccessFile): InspectedCoverImage {
        source.seek(2)
        while (source.filePointer + 4 <= source.length()) {
            var markerPrefix = source.readUnsignedByte()
            while (markerPrefix != 0xff && source.filePointer < source.length()) {
                markerPrefix = source.readUnsignedByte()
            }
            var marker = source.readUnsignedByte()
            while (marker == 0xff) marker = source.readUnsignedByte()
            if (marker == 0xd9 || marker == 0xda) break
            if (marker == 0x01 || marker in 0xd0..0xd7) continue
            val length = source.readUnsignedShort()
            require(length >= 2 && source.filePointer + length - 2 <= source.length()) { "封面生成失败（JPEG 头无效）" }
            if (marker in setOf(0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf)) {
                require(length >= 7) { "封面生成失败（JPEG 尺寸头无效）" }
                source.readUnsignedByte()
                val height = source.readUnsignedShort()
                val width = source.readUnsignedShort()
                return InspectedCoverImage(CoverImageFormat.JPEG, width, height)
            }
            source.seek(source.filePointer + length - 2)
        }
        throw IllegalArgumentException("封面生成失败（JPEG 缺少尺寸信息）")
    }

    private fun isWebp(source: RandomAccessFile): Boolean {
        source.seek(0)
        val header = ByteArray(12)
        if (source.read(header) != header.size) return false
        return header.copyOfRange(0, 4).decodeToString() == "RIFF" &&
            header.copyOfRange(8, 12).decodeToString() == "WEBP"
    }

    private fun inspectWebp(source: RandomAccessFile): InspectedCoverImage {
        source.seek(12)
        val kindBytes = ByteArray(4)
        require(source.read(kindBytes) == 4) { "封面生成失败（WebP 头无效）" }
        val kind = kindBytes.decodeToString()
        source.skipBytes(4)
        return when (kind) {
            "VP8X" -> {
                val flags = source.readUnsignedByte()
                require(flags and 0x02 == 0) { "封面生成失败（不支持动画 WebP）" }
                source.skipBytes(3)
                val width = readUInt24Le(source) + 1
                val height = readUInt24Le(source) + 1
                InspectedCoverImage(CoverImageFormat.WEBP, width, height)
            }
            "VP8L" -> {
                require(source.readUnsignedByte() == 0x2f) { "封面生成失败（WebP 无损头无效）" }
                val packed = readIntLe(source)
                val width = (packed and 0x3fff) + 1
                val height = ((packed ushr 14) and 0x3fff) + 1
                InspectedCoverImage(CoverImageFormat.WEBP, width, height)
            }
            "VP8 " -> {
                source.skipBytes(3)
                require(
                    source.readUnsignedByte() == 0x9d &&
                        source.readUnsignedByte() == 0x01 &&
                        source.readUnsignedByte() == 0x2a
                ) { "封面生成失败（WebP 有损头无效）" }
                val width = readUInt16Le(source) and 0x3fff
                val height = readUInt16Le(source) and 0x3fff
                InspectedCoverImage(CoverImageFormat.WEBP, width, height)
            }
            else -> throw IllegalArgumentException("封面生成失败（不支持的 WebP 编码）")
        }
    }

    private fun readUInt24Le(source: RandomAccessFile): Int =
        source.readUnsignedByte() or
            (source.readUnsignedByte() shl 8) or
            (source.readUnsignedByte() shl 16)

    private fun readUInt16Le(source: RandomAccessFile): Int =
        source.readUnsignedByte() or (source.readUnsignedByte() shl 8)

    private fun readIntLe(source: RandomAccessFile): Int =
        source.readUnsignedByte() or
            (source.readUnsignedByte() shl 8) or
            (source.readUnsignedByte() shl 16) or
            (source.readUnsignedByte() shl 24)
}
