package com.storybrain.app.reader

import com.storybrain.app.data.ReaderTheme

enum class ReaderDisplayMode { PLAIN_TEXT, DIALOGUE }

data class ReaderPreferences(
    val fontSizeSp: Int = DEFAULT_FONT_SIZE_SP,
    val lineHeightSp: Int = DEFAULT_LINE_HEIGHT_SP,
    val horizontalPaddingDp: Int = DEFAULT_HORIZONTAL_PADDING_DP,
    val paragraphSpacingDp: Int = DEFAULT_PARAGRAPH_SPACING_DP,
    val displayMode: ReaderDisplayMode = ReaderDisplayMode.PLAIN_TEXT,
    val theme: ReaderTheme = ReaderTheme.PAPER,
    val serifFont: Boolean = false
) {
    fun normalized(): ReaderPreferences {
        val normalizedFontSize = fontSizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
        val minimumReadableLineHeight = normalizedFontSize + MIN_LINE_GAP_SP
        return copy(
            fontSizeSp = normalizedFontSize,
            lineHeightSp = lineHeightSp.coerceIn(
                maxOf(MIN_LINE_HEIGHT_SP, minimumReadableLineHeight),
                MAX_LINE_HEIGHT_SP
            ),
            horizontalPaddingDp = horizontalPaddingDp.coerceIn(
                MIN_HORIZONTAL_PADDING_DP,
                MAX_HORIZONTAL_PADDING_DP
            ),
            paragraphSpacingDp = paragraphSpacingDp.coerceIn(
                MIN_PARAGRAPH_SPACING_DP,
                MAX_PARAGRAPH_SPACING_DP
            )
        )
    }

    fun adjustFontSize(delta: Int): ReaderPreferences {
        val current = normalized()
        val adjustedFont = (current.fontSizeSp + delta).coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
        return current.copy(
            fontSizeSp = adjustedFont,
            lineHeightSp = current.lineHeightSp.coerceAtLeast(adjustedFont + MIN_LINE_GAP_SP)
        ).normalized()
    }

    fun adjustLineHeight(delta: Int): ReaderPreferences {
        val current = normalized()
        return current.copy(
            lineHeightSp = (current.lineHeightSp + delta).coerceIn(
                maxOf(MIN_LINE_HEIGHT_SP, current.fontSizeSp + MIN_LINE_GAP_SP),
                MAX_LINE_HEIGHT_SP
            )
        ).normalized()
    }

    fun adjustParagraphSpacing(delta: Int): ReaderPreferences = normalized().let { current ->
        current.copy(
            paragraphSpacingDp = (current.paragraphSpacingDp + delta)
                .coerceIn(MIN_PARAGRAPH_SPACING_DP, MAX_PARAGRAPH_SPACING_DP)
        ).normalized()
    }

    fun adjustHorizontalPadding(delta: Int): ReaderPreferences = normalized().let { current ->
        current.copy(
            horizontalPaddingDp = (current.horizontalPaddingDp + delta)
                .coerceIn(MIN_HORIZONTAL_PADDING_DP, MAX_HORIZONTAL_PADDING_DP)
        ).normalized()
    }

    fun withDisplayMode(mode: ReaderDisplayMode): ReaderPreferences =
        normalized().copy(displayMode = mode)

    fun withTheme(theme: ReaderTheme): ReaderPreferences = normalized().copy(theme = theme)

    fun withSerifFont(enabled: Boolean): ReaderPreferences = normalized().copy(serifFont = enabled)

    fun toMap(): Map<String, String> = normalized().let { value ->
        mapOf(
            KEY_FONT_SIZE to value.fontSizeSp.toString(),
            KEY_LINE_HEIGHT to value.lineHeightSp.toString(),
            KEY_HORIZONTAL_PADDING to value.horizontalPaddingDp.toString(),
            KEY_PARAGRAPH_SPACING to value.paragraphSpacingDp.toString(),
            KEY_DISPLAY_MODE to value.displayMode.name,
            KEY_THEME to value.theme.name,
            KEY_SERIF_FONT to value.serifFont.toString()
        )
    }

    companion object {
        const val DEFAULT_FONT_SIZE_SP = 17
        const val DEFAULT_LINE_HEIGHT_SP = 31
        const val DEFAULT_HORIZONTAL_PADDING_DP = 22
        const val DEFAULT_PARAGRAPH_SPACING_DP = 8
        const val MIN_FONT_SIZE_SP = 14
        const val MAX_FONT_SIZE_SP = 28
        const val MIN_LINE_HEIGHT_SP = 20
        const val MAX_LINE_HEIGHT_SP = 44
        const val MIN_HORIZONTAL_PADDING_DP = 12
        const val MAX_HORIZONTAL_PADDING_DP = 40
        const val MIN_PARAGRAPH_SPACING_DP = 0
        const val MAX_PARAGRAPH_SPACING_DP = 24
        const val MIN_LINE_GAP_SP = 6

        const val KEY_FONT_SIZE = "font_size_sp"
        const val KEY_LINE_HEIGHT = "line_height_sp"
        const val KEY_HORIZONTAL_PADDING = "horizontal_padding_dp"
        const val KEY_PARAGRAPH_SPACING = "paragraph_spacing_dp"
        const val KEY_DISPLAY_MODE = "display_mode"
        const val KEY_THEME = "theme"
        const val KEY_SERIF_FONT = "serif_font"

        fun fromMap(values: Map<String, String?>): ReaderPreferences = ReaderPreferences(
            fontSizeSp = values[KEY_FONT_SIZE]?.toIntOrNull() ?: DEFAULT_FONT_SIZE_SP,
            lineHeightSp = values[KEY_LINE_HEIGHT]?.toIntOrNull() ?: DEFAULT_LINE_HEIGHT_SP,
            horizontalPaddingDp = values[KEY_HORIZONTAL_PADDING]?.toIntOrNull()
                ?: DEFAULT_HORIZONTAL_PADDING_DP,
            paragraphSpacingDp = values[KEY_PARAGRAPH_SPACING]?.toIntOrNull()
                ?: DEFAULT_PARAGRAPH_SPACING_DP,
            displayMode = values[KEY_DISPLAY_MODE]
                ?.let { runCatching { ReaderDisplayMode.valueOf(it) }.getOrNull() }
                ?: ReaderDisplayMode.PLAIN_TEXT,
            theme = values[KEY_THEME]
                ?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() }
                ?: ReaderTheme.PAPER,
            serifFont = values[KEY_SERIF_FONT]?.toBooleanStrictOrNull() ?: false
        ).normalized()
    }
}
