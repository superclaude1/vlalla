package com.storybrain.app.reader

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

data class ReaderViewport(
    val itemIndex: Int,
    val itemOffsetPx: Int
)

data class ReaderPosition(
    val bookId: String,
    val chapterId: String,
    val displayMode: ReaderDisplayMode,
    val itemIndex: Int,
    val itemOffsetPx: Int
) {
    fun toMap(): Map<String, String> = mapOf(
        KEY_BOOK_ID to bookId,
        KEY_CHAPTER_ID to chapterId,
        KEY_DISPLAY_MODE to displayMode.name,
        KEY_ITEM_INDEX to itemIndex.toString(),
        KEY_ITEM_OFFSET to itemOffsetPx.toString()
    )

    companion object {
        const val KEY_BOOK_ID = "book_id"
        const val KEY_CHAPTER_ID = "chapter_id"
        const val KEY_DISPLAY_MODE = "display_mode"
        const val KEY_ITEM_INDEX = "item_index"
        const val KEY_ITEM_OFFSET = "item_offset_px"

        fun fromMap(values: Map<String, String?>): ReaderPosition? {
            val bookId = values[KEY_BOOK_ID]?.takeIf(String::isNotBlank) ?: return null
            val chapterId = values[KEY_CHAPTER_ID]?.takeIf(String::isNotBlank) ?: return null
            val mode = values[KEY_DISPLAY_MODE]
                ?.let { runCatching { ReaderDisplayMode.valueOf(it) }.getOrNull() }
                ?: return null
            val itemIndex = values[KEY_ITEM_INDEX]?.toIntOrNull() ?: return null
            val itemOffset = values[KEY_ITEM_OFFSET]?.toIntOrNull() ?: return null
            return ReaderPosition(bookId, chapterId, mode, itemIndex, itemOffset)
        }
    }
}

object ReaderPositionCodec {
    private const val VERSION = "v1"
    private const val SEPARATOR = "|"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(position: ReaderPosition): String = listOf(
        VERSION,
        encodeText(position.bookId),
        encodeText(position.chapterId),
        position.displayMode.name,
        position.itemIndex.toString(),
        position.itemOffsetPx.toString()
    ).joinToString(SEPARATOR)

    fun decode(payload: String): ReaderPosition? = runCatching {
        val fields = payload.split(SEPARATOR)
        if (fields.size != 6 || fields[0] != VERSION) return null
        ReaderPosition.fromMap(
            mapOf(
                ReaderPosition.KEY_BOOK_ID to decodeText(fields[1]),
                ReaderPosition.KEY_CHAPTER_ID to decodeText(fields[2]),
                ReaderPosition.KEY_DISPLAY_MODE to fields[3],
                ReaderPosition.KEY_ITEM_INDEX to fields[4],
                ReaderPosition.KEY_ITEM_OFFSET to fields[5]
            )
        )
    }.getOrNull()

    private fun encodeText(value: String): String =
        encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String =
        decoder.decode(value).toString(StandardCharsets.UTF_8)
}

object ReaderPositionSamplingPolicy {
    const val MIN_OFFSET_DELTA_PX = 64

    fun shouldPersist(previous: ReaderViewport?, next: ReaderViewport): Boolean =
        previous == null ||
            previous.itemIndex != next.itemIndex ||
            kotlin.math.abs(previous.itemOffsetPx - next.itemOffsetPx) >= MIN_OFFSET_DELTA_PX
}

object ReaderPositionPolicy {
    fun storageKey(bookId: String, chapterId: String, displayMode: ReaderDisplayMode): String {
        val source = "$bookId\u0000$chapterId\u0000${displayMode.name}"
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun restore(
        saved: ReaderPosition?,
        bookId: String,
        chapterId: String,
        displayMode: ReaderDisplayMode,
        itemCount: Int
    ): ReaderViewport? {
        if (itemCount <= 0 || saved == null) return null
        if (saved.bookId != bookId || saved.chapterId != chapterId || saved.displayMode != displayMode) return null
        return ReaderViewport(
            itemIndex = saved.itemIndex.coerceIn(0, itemCount - 1),
            itemOffsetPx = saved.itemOffsetPx.coerceAtLeast(0)
        )
    }

    fun shouldPersist(previous: ReaderPosition?, next: ReaderPosition): Boolean = previous != next
}
