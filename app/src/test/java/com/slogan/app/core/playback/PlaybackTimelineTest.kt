package com.slogan.app.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTimelineTest {

    @Test
    fun computesTotalDurationForFiniteRepeats() {
        val timeline = PlaybackTimeline(clipDurationMs = 1000, intervalMs = 500, repeatCount = 3)

        assertFalse(timeline.isInfinite)
        assertEquals(3, timeline.plannedIterations)
        assertEquals(1000 + 500 + 1000 + 500 + 1000L, timeline.totalDurationMs())
    }

    @Test
    fun lastIterationHasNoGap() {
        val timeline = PlaybackTimeline(clipDurationMs = 800, intervalMs = 1200, repeatCount = 2)

        assertEquals(1200L, timeline.gapAfter(0))
        assertEquals(0L, timeline.gapAfter(1))
        assertTrue(timeline.isLastIteration(1))
        assertFalse(timeline.isLastIteration(0))
    }

    @Test
    fun singleRepeatNeverWaits() {
        val timeline = PlaybackTimeline(clipDurationMs = 2000, intervalMs = 5000, repeatCount = 1)

        assertEquals(2000L, timeline.totalDurationMs())
        assertEquals(0L, timeline.gapAfter(0))
    }

    @Test
    fun zeroOrNegativeRepeatMeansInfiniteLoop() {
        for (count in listOf(0, -1)) {
            val timeline = PlaybackTimeline(clipDurationMs = 1000, intervalMs = 300, repeatCount = count)

            assertTrue(timeline.isInfinite)
            assertEquals(Int.MAX_VALUE, timeline.plannedIterations)
            assertEquals(Long.MAX_VALUE, timeline.totalDurationMs())
            assertEquals(300L, timeline.gapAfter(999))
            assertFalse(timeline.isLastIteration(999))
        }
    }

    @Test
    fun zeroIntervalStillProducesCorrectTotal() {
        val timeline = PlaybackTimeline(clipDurationMs = 500, intervalMs = 0, repeatCount = 4)

        assertEquals(2000L, timeline.totalDurationMs())
        assertEquals(0L, timeline.gapAfter(0))
    }
}
