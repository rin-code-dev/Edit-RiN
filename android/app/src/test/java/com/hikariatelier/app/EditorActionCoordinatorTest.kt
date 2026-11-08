package com.hikariatelier.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
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
    fun failedFlushDoesNotRunReadOnlyPreviewAction() = runTest {
        val session = EditorSessionViewModel()
        session.initialize(listOf(Work(id = "work", title = "Work", code = "draw()")), activeWorkId = "work")
        session.blockModeState("work").value = true
        session.blockEditorFlusher = { false }
        val coordinator = EditorActionCoordinator(session, this, { null }, {})
        var executed = false
        val result = coordinator.executeAction(ActionKind.RUN_SKETCH, requireEditable = false) {
            executed = true
            CoordinatorResult.Success(Unit)
        }
        assertTrue(result is CoordinatorResult.Failed)
        assertFalse(executed)
    }

    @Test
    fun concurrentActionsAreSerializedWithoutDroppingUndo() = runTest {
        val session = EditorSessionViewModel()
        session.initialize(listOf(Work(id = "work", title = "Work", code = "draw()")), activeWorkId = "work")
        val coordinator = EditorActionCoordinator(session, this, { null }, {})
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val first = async { coordinator.executeAction(ActionKind.UNDO, requireFlush = false) {
            events.add("first start"); release.await(); events.add("first end"); CoordinatorResult.Success(Unit)
        } }
        runCurrent()
        val second = async { coordinator.executeAction(ActionKind.UNDO, requireFlush = false) {
            events.add("second"); CoordinatorResult.Success(Unit)
        } }
        runCurrent()
        assertEquals(listOf("first start"), events)
        release.complete(Unit)
        first.await(); second.await()
        assertEquals(listOf("first start", "first end", "second"), events)
    }

    @Test
    fun queuedActionDoesNotFlushAfterWorkChanges() = runTest {
        val session = EditorSessionViewModel()
        session.initialize(listOf(Work(id = "a", title = "A", code = "a()"), Work(id = "b", title = "B", code = "b()")), activeWorkId = "a")
        val coordinator = EditorActionCoordinator(session, this, { null }, {})
        val release = CompletableDeferred<Unit>()
        val first = async { coordinator.executeAction(ActionKind.GENERAL, requireFlush = false) {
            release.await(); CoordinatorResult.Success(Unit)
        } }
        runCurrent()
        var flushed = false
        session.blockModeState("a").value = true
        session.blockEditorFlusher = { flushed = true; true }
        val second = async { coordinator.executeAction(ActionKind.RUN_SKETCH) { CoordinatorResult.Success(Unit) } }
        runCurrent()
        session.activeWorkIdState.value = "b"
        release.complete(Unit)
        first.await()
        assertEquals(CoordinatorResult.Superseded, second.await())
        assertFalse(flushed)
    }

    @Test
    fun returningToSameWorkDuringFlushDoesNotReviveOldAction() = runTest {
        val session = EditorSessionViewModel()
        session.initialize(listOf(Work(id = "a", title = "A", code = "a()"), Work(id = "b", title = "B", code = "b()")), activeWorkId = "a")
        session.blockModeState("a").value = true
        val release = CompletableDeferred<Unit>()
        session.blockEditorFlusher = { release.await(); true }
        val coordinator = EditorActionCoordinator(session, this, { null }, {})
        var executed = false
        val result = async { coordinator.executeAction(ActionKind.RUN_SKETCH) {
            executed = true; CoordinatorResult.Success(Unit)
        } }
        runCurrent()
        session.activateWorkEditor("b", "b()", false, setOf("a", "b"))
        session.activateWorkEditor("a", "a()", false, setOf("a", "b"))
        release.complete(Unit)
        assertTrue(result.await() !is CoordinatorResult.Success)
        assertFalse(executed)
    }

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
