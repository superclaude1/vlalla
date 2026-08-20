package com.storybrain.app.data

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CoverPublicationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun staleGenerationDeletesTemporaryFileWithoutPublishing() = runBlocking {
        val filesDir = temporaryFolder.newFolder("files")
        val temporary = temporary(filesDir)
        val coordinator = CoverGenerationCoordinator()
        val generationId = coordinator.beginGeneration("book")!!
        coordinator.markDeleting("book")

        val published = CoverPublication.publishIfCurrent(
            filesDir = filesDir,
            bookId = "book",
            generationId = generationId,
            artifact = GeneratedCoverArtifact(temporary, CoverImageFormat.JPEG),
            coordinator = coordinator,
            readBook = { ExistingBookCover(exists = true, path = null) },
            updatePath = { 1 }
        )

        assertFalse(published)
        assertFalse(temporary.exists())
        assertTrue(CoverFileLifecycle.coversDirectory(filesDir).listFiles().orEmpty().none { !it.name.startsWith(".") })
    }

    @Test
    fun successfulPublicationUpdatesDatabaseAndDeletesPreviousManagedCover() = runBlocking {
        val filesDir = temporaryFolder.newFolder("files")
        val old = File(filesDir, "covers/old.jpg").apply {
            parentFile?.mkdirs()
            writeText("old")
        }
        val temporary = temporary(filesDir)
        val coordinator = CoverGenerationCoordinator()
        val generationId = coordinator.beginGeneration("book")!!
        var updatedPath: String? = null

        val published = CoverPublication.publishIfCurrent(
            filesDir = filesDir,
            bookId = "book",
            generationId = generationId,
            artifact = GeneratedCoverArtifact(temporary, CoverImageFormat.JPEG),
            coordinator = coordinator,
            readBook = { ExistingBookCover(exists = true, path = old.absolutePath) },
            updatePath = { path -> updatedPath = path; 1 }
        )

        assertTrue(published)
        assertFalse(temporary.exists())
        assertFalse(old.exists())
        assertTrue(updatedPath != null && File(updatedPath!!).exists())
    }

    @Test
    fun zeroRowUpdateDeletesPublishedFile() = runBlocking {
        val filesDir = temporaryFolder.newFolder("files")
        val temporary = temporary(filesDir)
        val coordinator = CoverGenerationCoordinator()
        val generationId = coordinator.beginGeneration("book")!!

        val published = CoverPublication.publishIfCurrent(
            filesDir = filesDir,
            bookId = "book",
            generationId = generationId,
            artifact = GeneratedCoverArtifact(temporary, CoverImageFormat.PNG),
            coordinator = coordinator,
            readBook = { ExistingBookCover(exists = true, path = null) },
            updatePath = { 0 }
        )

        assertFalse(published)
        assertTrue(CoverFileLifecycle.coversDirectory(filesDir).listFiles().orEmpty().none { !it.name.startsWith(".") })
    }

    @Test
    fun databaseFailureDeletesPublishedFileAndPropagates() {
        val filesDir = temporaryFolder.newFolder("files")
        val temporary = temporary(filesDir)
        val coordinator = CoverGenerationCoordinator()
        val generationId = coordinator.beginGeneration("book")!!

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                CoverPublication.publishIfCurrent(
                    filesDir = filesDir,
                    bookId = "book",
                    generationId = generationId,
                    artifact = GeneratedCoverArtifact(temporary, CoverImageFormat.WEBP),
                    coordinator = coordinator,
                    readBook = { ExistingBookCover(exists = true, path = null) },
                    updatePath = { error("database failed") }
                )
            }
        }
        assertTrue(CoverFileLifecycle.coversDirectory(filesDir).listFiles().orEmpty().none { !it.name.startsWith(".") })
    }

    private fun temporary(filesDir: File): File =
        CoverFileLifecycle.newTemporaryFile(filesDir, "book").apply { writeText("validated") }
}
