package com.storybrain.app.reader

import android.content.Context

class ReaderPositionStore(context: Context) {
    private val storage = context.getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE)

    fun read(bookId: String, chapterId: String, displayMode: ReaderDisplayMode): ReaderPosition? =
        readByKey(ReaderPositionPolicy.storageKey(bookId, chapterId, displayMode))

    @Synchronized
    fun save(next: ReaderPosition) {
        val normalized = next.copy(
            itemIndex = next.itemIndex.coerceAtLeast(0),
            itemOffsetPx = next.itemOffsetPx.coerceAtLeast(0)
        )
        val key = ReaderPositionPolicy.storageKey(
            normalized.bookId,
            normalized.chapterId,
            normalized.displayMode
        )
        if (!ReaderPositionPolicy.shouldPersist(readByKey(key), normalized)) return
        storage.edit()
            .putString(key, encode(normalized))
            .apply()
    }

    private fun readByKey(key: String): ReaderPosition? = runCatching {
        storage.getString(key, null)?.let(ReaderPositionCodec::decode)
    }.getOrNull()

    private fun encode(position: ReaderPosition): String = ReaderPositionCodec.encode(position)

    private companion object {
        const val STORAGE_NAME = "reader_positions_v2"
    }
}
