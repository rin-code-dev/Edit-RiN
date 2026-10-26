package com.hikariatelier.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreviewExecutionCoordinatorTest {

    @Test
    fun newerTokenSupersedesOlderPendingExecution() = runTest {
        val coordinator = PreviewExecutionCoordinator()

        coordinator.registerRequest(token = "tokenA", workId = "work1")
        coordinator.registerRequest(token = "tokenB", workId = "work1")

        // Awaiting tokenA should immediately yield Superseded
        val resultA = coordinator.awaitStage(token = "tokenA", targetStage = PreviewStage.RUNTIME_READY, timeoutMillis = 500L)
        assertTrue(resultA is PreviewExecutionResult.Superseded)

        // tokenB receives ready
        coordinator.notifyRuntimeReady("tokenB")
        val resultB = coordinator.awaitStage(token = "tokenB", targetStage = PreviewStage.RUNTIME_READY, timeoutMillis = 500L)
        assertTrue(resultB is PreviewExecutionResult.RuntimeReady)
        assertEquals("tokenB", (resultB as PreviewExecutionResult.RuntimeReady).token)
    }

    @Test
    fun errorCompletesWaitersWithFailure() = runTest {
        val coordinator = PreviewExecutionCoordinator()

        coordinator.registerRequest(token = "tokenErr", workId = "work1")

        launch {
            coordinator.notifyError("tokenErr", "Syntax error on line 3")
        }

        val result = coordinator.awaitStage(token = "tokenErr", targetStage = PreviewStage.RUNTIME_READY, timeoutMillis = 500L)
        assertTrue(result is PreviewExecutionResult.Failed)
        assertEquals("Syntax error on line 3", (result as PreviewExecutionResult.Failed).message)
    }

    @Test
    fun visualReadyFulfillsBothRuntimeAndVisualWaiters() = runTest {
        val coordinator = PreviewExecutionCoordinator()

        coordinator.registerRequest(token = "tokenVis", workId = "work1")

        coordinator.notifyVisualReady("tokenVis")

        val visualResult = coordinator.awaitStage(token = "tokenVis", targetStage = PreviewStage.VISUAL_READY, timeoutMillis = 500L)
        assertTrue(visualResult is PreviewExecutionResult.VisualReady)
    }

    @Test
    fun timeoutReturnsTimedOutResult() = runTest {
        val coordinator = PreviewExecutionCoordinator()

        coordinator.registerRequest(token = "tokenSlow", workId = "work1")

        // No event dispatched -> timeout
        val result = coordinator.awaitStage(token = "tokenSlow", targetStage = PreviewStage.RUNTIME_READY, timeoutMillis = 50L)
        assertTrue(result is PreviewExecutionResult.TimedOut)
        assertEquals(PreviewStage.RUNTIME_READY, (result as PreviewExecutionResult.TimedOut).stage)
    }
}
