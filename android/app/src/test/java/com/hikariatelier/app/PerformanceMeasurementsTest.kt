package com.hikariatelier.app

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PerformanceMeasurementsTest {
    @Test fun framesRemainSeparatedByScrollingAndOnlyPrimitivesAreStored() {
        val metrics = PerformanceMeasurements(true)
        metrics.frame(10_000_000, false, false)
        metrics.frame(30_000_000, true, true)
        metrics.frame(20_000_000, false, true)
        assertEquals(1L, metrics.frameSummary(false).timing.count)
        assertEquals(2L, metrics.frameSummary(true).timing.count)
        assertEquals(1L, metrics.frameSummary(true).janky)
        assertEquals(30_000_000L, metrics.frameSummary(true).timing.maxNanos)
        assertTrue(metrics.report().contains("avgMs=25"))
    }
    @Test fun disabledMeasurementsHaveNoRecords() = runBlocking {
        val metrics = PerformanceMeasurements(false)
        assertEquals("value", metrics.measure(PerformanceOperation.IMAGE_READ) { "value" })
        metrics.frame(10, true, true)
        assertEquals("", metrics.report())
    }
    @Test fun cancellationIsTimedAndPropagates() = runBlocking {
        val metrics = PerformanceMeasurements(true)
        val start = CompletableDeferred<Unit>()
        val job = launch { metrics.measure(PerformanceOperation.THUMBNAIL) { start.complete(Unit); awaitCancellation() } }
        start.await(); job.cancelAndJoin()
        assertEquals(1L, metrics.operation(PerformanceOperation.THUMBNAIL).count)
    }
    @Test fun differentiatedMeasurementSeparatesInterruptionAndFailure() = runBlocking {
        val metrics = PerformanceMeasurements(true)
        val caughtInterruption = runCatching {
            metrics.measureDifferentiated(
                PerformanceOperation.THUMBNAIL,
                PerformanceOperation.THUMBNAIL_INTERRUPTED,
                PerformanceOperation.THUMBNAIL_FAILED
            ) { throw ThumbnailInterruptedException() }
        }
        assertTrue(caughtInterruption.exceptionOrNull() is ThumbnailInterruptedException)
        assertEquals(1L, metrics.operation(PerformanceOperation.THUMBNAIL_INTERRUPTED).count)
        assertEquals(0L, metrics.operation(PerformanceOperation.THUMBNAIL).count)

        val caughtFailure = runCatching {
            metrics.measureDifferentiated(
                PerformanceOperation.THUMBNAIL,
                PerformanceOperation.THUMBNAIL_INTERRUPTED,
                PerformanceOperation.THUMBNAIL_FAILED
            ) { throw IllegalStateException("boom") }
        }
        assertTrue(caughtFailure.exceptionOrNull() is IllegalStateException)
        assertEquals(1L, metrics.operation(PerformanceOperation.THUMBNAIL_FAILED).count)
    }
    @Test fun jankBurstFlagsLoadStateAndRecoversAfterCleanFrames() {
        val metrics = PerformanceMeasurements(enabled = false, trackLoadState = true)
        assertFalse(metrics.loadState.jankActive)
        assertFalse(metrics.loadState.heavyLoad)

        // Inject 6 jank frames in a 24 frame window
        repeat(6) { metrics.frame(30_000_000, true, false) }
        repeat(18) { metrics.frame(16_000_000, false, false) }
        assertTrue(metrics.loadState.jankActive)
        assertFalse(metrics.loadState.heavyLoad)

        // Inject heavy load (>= 12 jank frames)
        repeat(12) { metrics.frame(40_000_000, true, false) }
        assertTrue(metrics.loadState.heavyLoad)

        // Recovery requires 30 consecutive clean frames
        repeat(29) { metrics.frame(16_000_000, false, false) }
        assertTrue(metrics.loadState.jankActive)
        metrics.frame(16_000_000, false, false)
        assertFalse(metrics.loadState.jankActive)
        assertFalse(metrics.loadState.heavyLoad)
    }
    @Test fun nullCaptureIsFailureAndHeavyLoadStaysLatchedUntilStable() = runBlocking {
        val metrics = PerformanceMeasurements(true)
        metrics.measureDifferentiated<String?>(PerformanceOperation.THUMBNAIL,
            PerformanceOperation.THUMBNAIL_INTERRUPTED, PerformanceOperation.THUMBNAIL_FAILED,
            isSuccess = { it != null }) { null }
        assertEquals(0L, metrics.operation(PerformanceOperation.THUMBNAIL).count)
        assertEquals(1L, metrics.operation(PerformanceOperation.THUMBNAIL_FAILED).count)
        repeat(12) { metrics.frame(40_000_000, true, false) }
        repeat(29) { metrics.frame(16_000_000, false, false) }
        assertTrue(metrics.loadState.heavyLoad)
        metrics.frame(16_000_000, false, false)
        assertFalse(metrics.loadState.heavyLoad)
    }

}
