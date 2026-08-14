package com.storybrain.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderPositionCodecTest {
    @Test
    fun roundTripsIdentifiersWithoutEmbeddingXmlUnsafeControlCharacters() {
        val position = ReaderPosition(
            bookId = "book\u0000长篇",
            chapterId = "chapter\n42",
            displayMode = ReaderDisplayMode.DIALOGUE,
            itemIndex = 8,
            itemOffsetPx = 17
        )

        val encoded = ReaderPositionCodec.encode(position)

        assertEquals(position, ReaderPositionCodec.decode(encoded))
        assertEquals(false, encoded.contains('\u0000'))
    }

    @Test
    fun rejectsCorruptedPayloadsWithoutPartialRestore() {
        assertNull(ReaderPositionCodec.decode("not-a-position"))
        assertNull(ReaderPositionCodec.decode("v1|broken|payload"))
    }
}
