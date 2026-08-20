package com.storybrain.app.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverGenerationCoordinatorTest {
    @Test
    fun serializesWorkForTheSameBook() {
        val coordinator = CoverGenerationCoordinator()
        val entered = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val firstEntered = CountDownLatch(1)
        val release = CountDownLatch(1)

        fun launchWorker(): Thread = Thread {
            runBlocking {
                coordinator.withBookLock("book") {
                    val active = entered.incrementAndGet()
                    peak.updateAndGet { maxOf(it, active) }
                    firstEntered.countDown()
                    release.await(2, TimeUnit.SECONDS)
                    entered.decrementAndGet()
                }
            }
        }.apply { start() }

        val first = launchWorker()
        assertTrue(firstEntered.await(1, TimeUnit.SECONDS))
        val second = launchWorker()
        Thread.sleep(100)
        assertEquals(1, peak.get())
        release.countDown()
        first.join(2_000)
        second.join(2_000)
        assertEquals(1, peak.get())
    }

    @Test
    fun oneBookAllowsOnlyOneActiveGenerationAndDeletionInvalidatesIt() {
        val coordinator = CoverGenerationCoordinator()

        val first = coordinator.beginGeneration("book")
        val duplicate = coordinator.beginGeneration("book")

        assertTrue(first != null)
        assertTrue(duplicate == null)
        assertTrue(coordinator.isCurrent("book", first!!))
        coordinator.markDeleting("book")
        assertEquals(false, coordinator.isCurrent("book", first))
        coordinator.clearDeleting("book")
        assertEquals(false, coordinator.finishGeneration("book", first))
        val current = coordinator.beginGeneration("book")!!
        assertTrue(coordinator.finishGeneration("book", current))
        assertTrue(coordinator.beginGeneration("book") != null)
    }

    @Test
    fun deletionFlagRejectsLatePublicationAndWaitsForInFlightGeneration() {
        val coordinator = CoverGenerationCoordinator()
        val generationEntered = CountDownLatch(1)
        val releaseGeneration = CountDownLatch(1)
        val deletionFinished = CountDownLatch(1)

        val generation = Thread {
            runBlocking {
                coordinator.withBookLock("book") {
                    generationEntered.countDown()
                    releaseGeneration.await(2, TimeUnit.SECONDS)
                }
            }
        }.apply { start() }
        assertTrue(generationEntered.await(1, TimeUnit.SECONDS))

        val deletion = Thread {
            coordinator.markDeleting("book")
            runBlocking {
                coordinator.withBookLock("book") { deletionFinished.countDown() }
            }
        }.apply { start() }

        Thread.sleep(100)
        assertTrue(coordinator.isDeleting("book"))
        assertEquals(false, deletionFinished.await(50, TimeUnit.MILLISECONDS))
        releaseGeneration.countDown()
        assertTrue(deletionFinished.await(1, TimeUnit.SECONDS))
        generation.join(2_000)
        deletion.join(2_000)
        coordinator.clearDeleting("book")
    }
}
