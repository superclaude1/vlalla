package com.storybrain.app.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CoverFileLifecycleTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun stagingMovesManagedCoverOutOfItsPublishedLocationAndRestorePutsItBack() {
        val filesDir = temporaryFolder.newFolder("files")
        val cover = File(filesDir, "covers/cover.jpg").apply {
            parentFile?.mkdirs()
            writeText("cover")
        }

        val stage = CoverFileLifecycle.stageDeletion(filesDir, cover.absolutePath)

        assertFalse(cover.exists())
        assertTrue(stage != null && stage.staged.exists())
        CoverFileLifecycle.restoreDeletion(stage!!)
        assertTrue(cover.exists())
    }

    @Test
    fun stagingNeverMovesAnUnmanagedPath() {
        val filesDir = temporaryFolder.newFolder("files")
        val outside = temporaryFolder.newFile("outside.jpg").apply { writeText("cover") }

        val stage = CoverFileLifecycle.stageDeletion(filesDir, outside.absolutePath)

        assertTrue(stage == null)
        assertTrue(outside.exists())
    }

    @Test
    fun replacingDeletesPreviousManagedCoverButNeverExternalFiles() {
        val filesDir = temporaryFolder.newFolder("files")
        val previous = File(filesDir, "covers/old.jpg").apply {
            parentFile?.mkdirs()
            writeText("old")
        }
        val external = temporaryFolder.newFile("external.jpg").apply { writeText("external") }

        CoverFileLifecycle.deleteReplaced(filesDir, previous.absolutePath, "new")
        CoverFileLifecycle.deleteReplaced(filesDir, external.absolutePath, "new")

        assertFalse(previous.exists())
        assertTrue(external.exists())
    }

    @Test
    fun startupRecoveryRestoresReferencedTrashAndDeletesOrphansAndPartials() {
        val filesDir = temporaryFolder.newFolder("files")
        val covers = File(filesDir, "covers").apply { mkdirs() }
        val referenced = File(covers, "referenced.jpg").apply { writeText("keep") }
        val stagedDirectory = File(covers, ".trash-test").apply { mkdirs() }
        val stagedReferenced = File(stagedDirectory, "staged.jpg").apply { writeText("restore") }
        val stagedDestination = File(covers, "staged.jpg")
        val orphan = File(covers, "orphan.jpg").apply { writeText("delete") }
        val partial = File(covers, ".download.partial").apply { writeText("delete") }

        CoverFileLifecycle.recoverAndCleanup(
            filesDir,
            setOf(referenced.absolutePath, stagedDestination.absolutePath),
            nowMillis = System.currentTimeMillis(),
            orphanGraceMillis = 0L
        )

        assertTrue(referenced.exists())
        assertTrue(stagedDestination.exists())
        assertFalse(stagedReferenced.exists())
        assertFalse(stagedDirectory.exists())
        assertFalse(orphan.exists())
        assertFalse(partial.exists())
    }

    @Test
    fun startupRecoveryLeavesFreshTrashForInFlightDeletion() {
        val filesDir = temporaryFolder.newFolder("files")
        val covers = File(filesDir, "covers").apply { mkdirs() }
        val stagedDirectory = File(covers, ".trash-fresh").apply { mkdirs() }
        File(stagedDirectory, "cover.jpg").writeText("deleting")
        val now = stagedDirectory.lastModified()

        CoverFileLifecycle.recoverAndCleanup(
            filesDir,
            emptySet(),
            nowMillis = now,
            orphanGraceMillis = 60_000L
        )

        assertTrue(stagedDirectory.exists())
    }

    @Test
    fun startupRecoveryLeavesFreshUnreferencedFilesForInFlightGeneration() {
        val filesDir = temporaryFolder.newFolder("files")
        val covers = File(filesDir, "covers").apply { mkdirs() }
        val fresh = File(covers, "fresh.jpg").apply { writeText("in flight") }
        val now = fresh.lastModified()

        CoverFileLifecycle.recoverAndCleanup(
            filesDir,
            emptySet(),
            nowMillis = now,
            orphanGraceMillis = 60_000L
        )

        assertTrue(fresh.exists())
    }

    @Test
    fun uniqueTemporaryFilesDoNotCollideForOneBook() {
        val filesDir = temporaryFolder.newFolder("files")

        val first = CoverFileLifecycle.newTemporaryFile(filesDir, "book")
        val second = CoverFileLifecycle.newTemporaryFile(filesDir, "book")

        assertFalse(first == second)
        assertTrue(first.parentFile == second.parentFile)
        assertTrue(first.name.endsWith(".partial"))
        assertTrue(second.name.endsWith(".partial"))
    }
}
