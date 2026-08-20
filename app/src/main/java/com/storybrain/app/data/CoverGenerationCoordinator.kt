package com.storybrain.app.data

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coordinates cover generation and deletion per book without blocking unrelated books. */
class CoverGenerationCoordinator {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val states = ConcurrentHashMap<String, BookCoverState>()

    suspend fun <T> withBookLock(bookId: String, operation: suspend () -> T): T =
        locks.computeIfAbsent(bookId) { Mutex() }.withLock { operation() }

    fun beginGeneration(bookId: String): String? {
        val generationId = UUID.randomUUID().toString()
        return states.compute(bookId) { _, current ->
            when {
                current?.deleting == true -> current
                current?.generationId != null -> current
                else -> BookCoverState(generationId = generationId, deleting = false)
            }
        }?.generationId?.takeIf { it == generationId }
    }

    fun finishGeneration(bookId: String, generationId: String): Boolean {
        var wasCurrent = false
        states.computeIfPresent(bookId) { _, current ->
            if (current.generationId != generationId) current
            else {
                wasCurrent = !current.deleting
                current.copy(generationId = null).takeUnless { !it.deleting }
            }
        }
        return wasCurrent
    }

    fun markDeleting(bookId: String) {
        states.compute(bookId) { _, _ ->
            BookCoverState(generationId = null, deleting = true)
        }
    }

    fun clearDeleting(bookId: String) {
        states.computeIfPresent(bookId) { _, current ->
            current.copy(deleting = false).takeUnless { it.generationId == null }
        }
    }

    fun isDeleting(bookId: String): Boolean = states[bookId]?.deleting == true

    fun isCurrent(bookId: String, generationId: String): Boolean {
        val state = states[bookId]
        return state?.deleting == false && state.generationId == generationId
    }

    private data class BookCoverState(val generationId: String?, val deleting: Boolean)
}
