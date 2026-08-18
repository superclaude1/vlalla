package com.storybrain.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverGenerationPolicyTest {
    @Test
    fun sanitizesTitleForPromptWithoutControlCharactersOrUnboundedLength() {
        val sanitized = CoverGenerationPolicy.sanitizeTitle("  书\n名\u0000  ".repeat(40))

        assertTrue(sanitized.length <= CoverGenerationPolicy.MAX_TITLE_LENGTH)
        assertFalse(sanitized.any(Char::isISOControl))
        assertFalse(sanitized.startsWith(" "))
        assertFalse(sanitized.endsWith(" "))
    }

    @Test
    fun acceptsOnlyBoundedImageResponses() {
        val bytes = ByteArray(CoverGenerationPolicy.MIN_RESPONSE_BYTES + 1)

        assertTrue(CoverGenerationPolicy.isAcceptableImage("image/jpeg", bytes))
        assertTrue(CoverGenerationPolicy.isAcceptableImage("image/jpeg", -1L))
        assertTrue(CoverGenerationPolicy.isAcceptableImage("image/png; charset=binary", bytes))
        assertFalse(CoverGenerationPolicy.isAcceptableImage("text/html", bytes))
        assertFalse(
            CoverGenerationPolicy.isAcceptableImage(
                "image/jpeg",
                ByteArray(CoverGenerationPolicy.MIN_RESPONSE_BYTES)
            )
        )
        assertFalse(
            CoverGenerationPolicy.isAcceptableImage(
                "image/jpeg",
                ByteArray(CoverGenerationPolicy.MAX_RESPONSE_BYTES + 1)
            )
        )
    }

    @Test
    fun generatedFileNameDoesNotTreatBookIdAsAPath() {
        val fileName = CoverGenerationPolicy.fileName("../../outside/book")

        assertEquals(".jpg", fileName.takeLast(4))
        assertFalse(fileName.contains(".."))
        assertFalse(fileName.contains(File.separator))
    }

    @Test
    fun onlyFilesInsideManagedCoverDirectoryCanBeDeleted() {
        val filesDir = File("/data/user/0/com.storybrain.app/files")
        val managed = File(filesDir, "covers/a.jpg").path
        val outside = File(filesDir, "../shared/a.jpg").path

        assertTrue(CoverGenerationPolicy.isManagedPath(filesDir, managed))
        assertFalse(CoverGenerationPolicy.isManagedPath(filesDir, outside))
    }
}
