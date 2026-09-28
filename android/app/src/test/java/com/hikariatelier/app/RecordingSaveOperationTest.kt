package com.hikariatelier.app

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class RecordingSaveOperationTest {
    @Test fun successReleasesSavingState() = runBlocking {
        val states = mutableListOf<Boolean>()
        assertEquals("saved", completeRecordingSave({ states.add(it) }) { "saved" })
        assertEquals(listOf(true, false), states)
    }
    @Test fun exceptionReleasesSavingState() = runBlocking {
        val states = mutableListOf<Boolean>()
        try { completeRecordingSave({ states.add(it) }) { error("storage full") }; fail("Expected failure") }
        catch (_: IllegalStateException) { }
        assertEquals(listOf(true, false), states)
    }
    @Test fun cancellationReleasesSavingState() = runBlocking {
        val states = mutableListOf<Boolean>()
        val started = CompletableDeferred<Unit>()
        val job = launch {
            completeRecordingSave({ states.add(it) }) { started.complete(Unit); awaitCancellation() }
        }
        started.await(); job.cancelAndJoin()
        assertEquals(listOf(true, false), states)
    }
}
