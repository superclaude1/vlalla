package com.storybrain.app.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPreferencesTest {
    @Test
    fun defaultsPreserveExistingReaderTypography() {
        val preferences = ReaderPreferences()

        assertEquals(17, preferences.fontSizeSp)
        assertEquals(31, preferences.lineHeightSp)
        assertEquals(22, preferences.horizontalPaddingDp)
        assertEquals(ReaderDisplayMode.PLAIN_TEXT, preferences.displayMode)
    }

    @Test
    fun valuesClampToReadableAndLayoutSafeBounds() {
        assertEquals(
            ReaderPreferences.MIN_FONT_SIZE_SP,
            ReaderPreferences(fontSizeSp = -1).normalized().fontSizeSp
        )
        assertEquals(
            ReaderPreferences.MAX_FONT_SIZE_SP,
            ReaderPreferences(fontSizeSp = 200).normalized().fontSizeSp
        )
        assertEquals(
            ReaderPreferences.DEFAULT_FONT_SIZE_SP + ReaderPreferences.MIN_LINE_GAP_SP,
            ReaderPreferences(lineHeightSp = 1).normalized().lineHeightSp
        )
        assertEquals(
            ReaderPreferences.MAX_HORIZONTAL_PADDING_DP,
            ReaderPreferences(horizontalPaddingDp = 200).normalized().horizontalPaddingDp
        )
    }

    @Test
    fun fontAdjustmentsKeepLineHeightReadable() {
        val increased = ReaderPreferences(fontSizeSp = 17, lineHeightSp = 25).adjustFontSize(2)
        val decreased = increased.adjustFontSize(-20)

        assertEquals(19, increased.fontSizeSp)
        assertEquals(25, increased.lineHeightSp)
        assertEquals(ReaderPreferences.MIN_FONT_SIZE_SP, decreased.fontSizeSp)
        assertEquals(25, decreased.lineHeightSp)
    }

    @Test
    fun stableValuesRoundTripThroughStorageMap() {
        val original = ReaderPreferences(
            fontSizeSp = 21,
            lineHeightSp = 34,
            horizontalPaddingDp = 28,
            displayMode = ReaderDisplayMode.DIALOGUE
        )

        assertEquals(original, ReaderPreferences.fromMap(original.toMap()))
    }

    @Test
    fun malformedStoredValuesFallBackSafely() {
        val restored = ReaderPreferences.fromMap(
            mapOf(
                ReaderPreferences.KEY_FONT_SIZE to "not-a-number",
                ReaderPreferences.KEY_LINE_HEIGHT to "-50",
                ReaderPreferences.KEY_HORIZONTAL_PADDING to "999",
                ReaderPreferences.KEY_DISPLAY_MODE to "UNKNOWN"
            )
        )

        assertEquals(ReaderPreferences.DEFAULT_FONT_SIZE_SP, restored.fontSizeSp)
        assertEquals(
            ReaderPreferences.DEFAULT_FONT_SIZE_SP + ReaderPreferences.MIN_LINE_GAP_SP,
            restored.lineHeightSp
        )
        assertEquals(ReaderPreferences.MAX_HORIZONTAL_PADDING_DP, restored.horizontalPaddingDp)
        assertEquals(ReaderDisplayMode.PLAIN_TEXT, restored.displayMode)
    }
}
