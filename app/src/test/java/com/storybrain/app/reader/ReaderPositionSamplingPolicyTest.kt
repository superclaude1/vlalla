package com.storybrain.app.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPositionSamplingPolicyTest {
    @Test
    fun persistsItemChangesAndMeaningfulOffsetMovement() {
        val previous = ReaderViewport(itemIndex = 4, itemOffsetPx = 100)

        assertTrue(
            ReaderPositionSamplingPolicy.shouldPersist(
                previous,
                ReaderViewport(itemIndex = 5, itemOffsetPx = 0)
            )
        )
        assertTrue(
            ReaderPositionSamplingPolicy.shouldPersist(
                previous,
                ReaderViewport(itemIndex = 4, itemOffsetPx = 180)
            )
        )
    }

    @Test
    fun skipsMinorPixelNoiseButAlwaysCapturesFirstViewport() {
        val previous = ReaderViewport(itemIndex = 4, itemOffsetPx = 100)

        assertFalse(
            ReaderPositionSamplingPolicy.shouldPersist(
                previous,
                ReaderViewport(itemIndex = 4, itemOffsetPx = 120)
            )
        )
        assertTrue(ReaderPositionSamplingPolicy.shouldPersist(null, previous))
    }
}
