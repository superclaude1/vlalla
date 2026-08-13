package com.storybrain.app.reader

enum class ReaderDisplayMode { PLAIN_TEXT, DIALOGUE }

data class ReaderPreferences(
    val fontSizeSp: Int = DEFAULT_FONT_SIZE_SP,
    val lineHeightSp: Int = DEFAULT_LINE_HEIGHT_SP,
    val horizontalPaddingDp: Int = DEFAULT_HORIZONTAL_PADDING_DP,
    val displayMode: ReaderDisplayMode = ReaderDisplayMode.PLAIN_TEXT
) {
    fun normalized(): ReaderPreferences = copy(
        fontSizeSp = fontSizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP),
        lineHeightSp = lineHeightSp.coerceIn(MIN_LINE_HEIGHT_SP, MAX_LINE_HEIGHT_SP),
        horizontalPaddingDp = horizontalPaddingDp.coerceIn(
            MIN_HORIZONTAL_PADDING_DP,
            MAX_HORIZONTAL_PADDING_DP
        )
    )

    fun adjustFontSize(delta: Int): ReaderPreferences {
        val current = normalized()
        val adjustedFont = (current.fontSizeSp + delta).coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
        return current.copy(
            fontSizeSp = adjustedFont,
            lineHeightSp = current.lineHeightSp.coerceAtLeast(adjustedFont + MIN_LINE_GAP_SP)
        ).normalized()
    }

    fun withDisplayMode(mode: ReaderDisplayMode): ReaderPreferences =
        normalized().copy(displayMode = mode)

    fun toMap(): Map<String, String> = normalized().let { value ->
        mapOf(
            KEY_FONT_SIZE to value.fontSizeSp.toString(),
            KEY_LINE_HEIGHT to value.lineHeightSp.toString(),
            KEY_HORIZONTAL_PADDING to value.horizontalPaddingDp.toString(),
            KEY_DISPLAY_MODE to value.displayMode.name
        )
    }

    companion object {
        const val DEFAULT_FONT_SIZE_SP = 17
        const val DEFAULT_LINE_HEIGHT_SP = 31
        const val DEFAULT_HORIZONTAL_PADDING_DP = 22
        const val MIN_FONT_SIZE_SP = 14
        const val MAX_FONT_SIZE_SP = 28
        const val MIN_LINE_HEIGHT_SP = 20
        const val MAX_LINE_HEIGHT_SP = 44
        const val MIN_HORIZONTAL_PADDING_DP = 12
        const val MAX_HORIZONTAL_PADDING_DP = 40
        const val MIN_LINE_GAP_SP = 6

        const val KEY_FONT_SIZE = "font_size_sp"
        const val KEY_LINE_HEIGHT = "line_height_sp"
        const val KEY_HORIZONTAL_PADDING = "horizontal_padding_dp"
        const val KEY_DISPLAY_MODE = "display_mode"

        fun fromMap(values: Map<String, String?>): ReaderPreferences = ReaderPreferences(
            fontSizeSp = values[KEY_FONT_SIZE]?.toIntOrNull() ?: DEFAULT_FONT_SIZE_SP,
            lineHeightSp = values[KEY_LINE_HEIGHT]?.toIntOrNull() ?: DEFAULT_LINE_HEIGHT_SP,
            horizontalPaddingDp = values[KEY_HORIZONTAL_PADDING]?.toIntOrNull()
                ?: DEFAULT_HORIZONTAL_PADDING_DP,
            displayMode = values[KEY_DISPLAY_MODE]
                ?.let { runCatching { ReaderDisplayMode.valueOf(it) }.getOrNull() }
                ?: ReaderDisplayMode.PLAIN_TEXT
        ).normalized()
    }
}
