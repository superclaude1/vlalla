package com.storybrain.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CoverImageInspectorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun acceptsARealPngHeaderAndReportsBoundedDimensions() {
        val file = write("valid.png", png(width = 768, height = 1024))

        val image = CoverImageInspector.inspect(file, "image/png")

        assertEquals(CoverImageFormat.PNG, image.format)
        assertEquals(768, image.width)
        assertEquals(1024, image.height)
    }

    @Test
    fun acceptsARealJpegHeaderAndReportsBoundedDimensions() {
        val file = write("valid.jpg", jpeg(width = 768, height = 1024))

        val image = CoverImageInspector.inspect(file, "image/jpeg")

        assertEquals(CoverImageFormat.JPEG, image.format)
        assertEquals(768, image.width)
        assertEquals(1024, image.height)
    }

    @Test
    fun acceptsAWebpExtendedHeaderAndReportsBoundedDimensions() {
        val file = write("valid.webp", webpExtended(width = 768, height = 1024))

        val image = CoverImageInspector.inspect(file, "image/webp")

        assertEquals(CoverImageFormat.WEBP, image.format)
        assertEquals(768, image.width)
        assertEquals(1024, image.height)
    }

    @Test
    fun rejectsAnimatedWebpBeforeAndroidDecode() {
        val file = write("animated.webp", webpExtended(width = 768, height = 1024, flags = 0x02))

        assertThrows(IllegalArgumentException::class.java) {
            CoverImageInspector.inspect(file, "image/webp")
        }
    }

    @Test
    fun acceptsAWebpLossyHeaderAndReportsBoundedDimensions() {
        val file = write("valid-lossy.webp", webpLossy(width = 768, height = 1024))

        val image = CoverImageInspector.inspect(file, "image/webp")

        assertEquals(CoverImageFormat.WEBP, image.format)
        assertEquals(768, image.width)
        assertEquals(1024, image.height)
    }

    @Test
    fun rejectsHtmlThatOnlyClaimsToBeAnImage() {
        val payload = "<html>not an image</html>".repeat(100).toByteArray()
        val file = write("fake.jpg", payload)

        assertThrows(IllegalArgumentException::class.java) {
            CoverImageInspector.inspect(file, "image/jpeg")
        }
    }

    @Test
    fun rejectsARealHeaderWhoseDeclaredMimeDoesNotMatch() {
        val file = write("mismatch.png", png(width = 768, height = 1024))

        assertThrows(IllegalArgumentException::class.java) {
            CoverImageInspector.inspect(file, "image/jpeg")
        }
    }

    @Test
    fun rejectsImagesWhoseDimensionsWouldAllocateTooMuchMemory() {
        val file = write("huge.png", png(width = 20_000, height = 20_000))

        assertThrows(IllegalArgumentException::class.java) {
            CoverImageInspector.inspect(file, "image/png")
        }
    }

    private fun write(name: String, bytes: ByteArray): File =
        temporaryFolder.newFile(name).apply { writeBytes(bytes) }

    private fun png(width: Int, height: Int): ByteArray {
        val result = ByteArray(CoverGenerationPolicy.MIN_RESPONSE_BYTES + 1)
        val signature = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        )
        signature.copyInto(result)
        result[8] = 0
        result[9] = 0
        result[10] = 0
        result[11] = 13
        "IHDR".toByteArray().copyInto(result, 12)
        writeInt(result, 16, width)
        writeInt(result, 20, height)
        return result
    }

    private fun jpeg(width: Int, height: Int): ByteArray {
        val result = ByteArray(CoverGenerationPolicy.MIN_RESPONSE_BYTES + 1)
        var index = 0
        fun byte(value: Int) { result[index++] = value.toByte() }
        byte(0xff); byte(0xd8)
        byte(0xff); byte(0xc0)
        byte(0x00); byte(0x11)
        byte(0x08)
        byte(height ushr 8); byte(height)
        byte(width ushr 8); byte(width)
        byte(0x03)
        repeat(9) { byte(0) }
        return result
    }

    private fun webpExtended(width: Int, height: Int, flags: Int = 0): ByteArray {
        val result = ByteArray(CoverGenerationPolicy.MIN_RESPONSE_BYTES + 1)
        "RIFF".toByteArray().copyInto(result, 0)
        "WEBP".toByteArray().copyInto(result, 8)
        "VP8X".toByteArray().copyInto(result, 12)
        result[16] = 10
        result[20] = flags.toByte()
        writeUInt24Le(result, 24, width - 1)
        writeUInt24Le(result, 27, height - 1)
        return result
    }

    private fun webpLossy(width: Int, height: Int): ByteArray {
        val result = ByteArray(CoverGenerationPolicy.MIN_RESPONSE_BYTES + 1)
        "RIFF".toByteArray().copyInto(result, 0)
        "WEBP".toByteArray().copyInto(result, 8)
        "VP8 ".toByteArray().copyInto(result, 12)
        result[16] = 10
        result[23] = 0x9d.toByte()
        result[24] = 0x01
        result[25] = 0x2a
        result[26] = width.toByte()
        result[27] = (width ushr 8).toByte()
        result[28] = height.toByte()
        result[29] = (height ushr 8).toByte()
        return result
    }

    private fun writeUInt24Le(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
        target[offset + 2] = (value ushr 16).toByte()
    }

    private fun writeInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }
}
