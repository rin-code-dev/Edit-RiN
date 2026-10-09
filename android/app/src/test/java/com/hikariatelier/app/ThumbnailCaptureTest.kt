package com.hikariatelier.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class ThumbnailCaptureTest {
    @Test fun stateChangeInterruptsCaptureWithoutPolling() = runBlocking {
        val stop = MutableStateFlow(false); val started = CompletableDeferred<Unit>()
        var cleaned = false
        val capture = async {
            try {
                captureThumbnailWhileAllowed(stop) {
                    try { started.complete(Unit); awaitCancellation() } finally { cleaned = true }
                }
            } catch (_: ThumbnailInterruptedException) { "interrupted" }
        }
        started.await(); stop.value = true
        assertEquals("interrupted", withTimeout(1000) { capture.await() })
        assertTrue(cleaned); assertTrue(currentCoroutineContext().isActive)
    }
    @Test fun foregroundInterruptionStopsTheRendererButAllowsLaterJobs() = runBlocking {
        var cleanedUp = false
        try {
            withTimeout(2000) {
                captureThumbnailWhileAllowed(MutableStateFlow(true)) {
                    try { awaitCancellation() } finally { cleanedUp = true }
                }
            }
            fail("Interrupted capture must not be marked as a broken sketch")
        } catch (_: ThumbnailInterruptedException) {}
        assertTrue(cleanedUp)
        assertTrue(currentCoroutineContext().isActive)
        assertEquals("next", captureThumbnailWhileAllowed(MutableStateFlow(false)) { "next" })
    }
    @Test fun lifecycleCancellationCleansUpTheCapture() = runBlocking {
        val started = CompletableDeferred<Unit>(); var cleanedUp = false
        val job = launch {
            captureThumbnailWhileAllowed(MutableStateFlow(false)) {
                try { started.complete(Unit); awaitCancellation() } finally { cleanedUp = true }
            }
        }
        started.await(); job.cancelAndJoin()
        assertTrue(cleanedUp)
    }
}
