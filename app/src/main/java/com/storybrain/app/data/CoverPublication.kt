package com.storybrain.app.data

import java.io.File

data class GeneratedCoverArtifact(
    val temporary: File,
    val format: CoverImageFormat
)

data class ExistingBookCover(
    val exists: Boolean,
    val path: String?
)

/** Owns the only transition from validated temporary cover to published/database-backed cover. */
object CoverPublication {
    suspend fun publishIfCurrent(
        filesDir: File,
        bookId: String,
        generationId: String,
        artifact: GeneratedCoverArtifact,
        coordinator: CoverGenerationCoordinator,
        readBook: suspend () -> ExistingBookCover,
        updatePath: suspend (String) -> Int
    ): Boolean = coordinator.withBookLock(bookId) {
        if (!coordinator.isCurrent(bookId, generationId)) {
            artifact.temporary.delete()
            return@withBookLock false
        }
        val existing = readBook()
        if (!existing.exists) {
            artifact.temporary.delete()
            return@withBookLock false
        }
        val destination = File(
            CoverFileLifecycle.coversDirectory(filesDir),
            CoverGenerationPolicy.fileName(bookId, artifact.format.extension, generationId)
        )
        try {
            CoverFileLifecycle.publish(artifact.temporary, destination)
            if (updatePath(destination.absolutePath) != 1) {
                destination.delete()
                return@withBookLock false
            }
            // Consume the generation atomically after the DB row points to the new artifact.
            if (!coordinator.finishGeneration(bookId, generationId)) {
                return@withBookLock true
            }
            CoverFileLifecycle.deleteReplaced(filesDir, existing.path, destination.absolutePath)
            true
        } catch (error: Throwable) {
            artifact.temporary.delete()
            // The row may already have committed before a cancellation/error was delivered.
            // Preserve a committed destination; startup reconciliation can clean unknown files.
            if (runCatching { readBook().path != destination.absolutePath }.getOrDefault(true)) {
                destination.delete()
            }
            throw error
        }
    }
}
