package com.hikariatelier.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorActionCoordinatorTest {

    @Test
    fun sampleReadOnlyRejectsEditableActions() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val session = EditorSessionViewModel()
        var sampleCopyRequested = false

        session.initialize(
            listOf(Work(id = "sample1", title = "Sample", code = "setup()", isSample = true)),
            activeWorkId = "sample1"
        )

        val coordinator = EditorActionCoordinator(
            session = session,
            scope = testScope,
            folderUriProvider = { null },
            onSampleCopyRequested = { sampleCopyRequested = true }
        )

        val result = coordinator.executeAction<String>(
            kind = ActionKind.UNDO,
            targetWorkId = "sample1",
            requireEditable = true,
            requireFlush = false
        ) {
            CoordinatorResult.Success("Executed")
        }

        assertTrue(result is CoordinatorResult.Rejected)
        assertTrue(sampleCopyRequested)
    }

    @Test
    fun workSwitchDuringFlushMarksActionSuperseded() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val session = EditorSessionViewModel()

        session.initialize(
            listOf(
                Work(id = "workA", title = "Work A", code = "setupA()"),
                Work(id = "workB", title = "Work B", code = "setupB()")
            ),
            activeWorkId = "workA"
        )

        // Mock flush that switches active work before returning
        session.blockEditorFlusher = { _ ->
            session.activeWorkIdState.value = "workB"
            true
        }
        session.blockModeState("workA").value = true

        val coordinator = EditorActionCoordinator(
            session = session,
            scope = testScope,
            folderUriProvider = { null },
            onSampleCopyRequested = {}
        )

        val result = coordinator.executeAction<String>(
            kind = ActionKind.SAVE,
            targetWorkId = "workA",
            requireEditable = false,
            requireFlush = true
        ) {
            CoordinatorResult.Success("Saved")
        }

        assertEquals(CoordinatorResult.Superseded, result)
    }

    @Test
    fun failedFlushRejectsEditableOperation() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val session = EditorSessionViewModel()

        session.initialize(
            listOf(Work(id = "work1", title = "Work 1", code = "setup()")),
            activeWorkId = "work1"
        )

        session.blockEditorFlusher = { _ -> false }
        session.blockModeState("work1").value = true

        val coordinator = EditorActionCoordinator(
            session = session,
            scope = testScope,
            folderUriProvider = { null },
            onSampleCopyRequested = {}
        )

        val result = coordinator.executeAction<String>(
            kind = ActionKind.UNDO,
            targetWorkId = "work1",
            requireEditable = true,
            requireFlush = true
        ) {
            CoordinatorResult.Success("Undone")
        }

        assertTrue(result is CoordinatorResult.Failed)
    }

    @Test
    fun validActionExecutesWithCurrentContext() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val session = EditorSessionViewModel()

        session.initialize(
            listOf(Work(id = "work1", title = "Work 1", code = "line1\nline2")),
            activeWorkId = "work1"
        )

        val coordinator = EditorActionCoordinator(
            session = session,
            scope = testScope,
            folderUriProvider = { null },
            onSampleCopyRequested = {}
        )

        var contextReceived: ActionContext? = null

        val result = coordinator.executeAction<Int>(
            kind = ActionKind.RUN_SKETCH,
            targetWorkId = "work1",
            requireEditable = false,
            requireFlush = false
        ) { ctx ->
            contextReceived = ctx
            CoordinatorResult.Success(ctx.currentCode.length)
        }

        assertTrue(result is CoordinatorResult.Success)
        assertEquals("work1", contextReceived?.workId)
        assertEquals("line1\nline2", contextReceived?.currentCode)
        assertFalse(contextReceived?.isSample ?: true)
    }
}
