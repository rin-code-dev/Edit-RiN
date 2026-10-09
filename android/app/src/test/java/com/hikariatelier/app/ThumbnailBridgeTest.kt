package com.hikariatelier.app

import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ThumbnailBridgeTest {
    @Test fun captureCompletionAndErrorsBelongToTheOriginatingRun() {
        val work = Work("work", "Work", "function setup() {}")
        val run = preparePreviewRun(capturePreviewRun(work, work.code, work.files, work.assets, token = "current"))
        val ready = CompletableDeferred<Unit>(); val captured = CompletableDeferred<String?>()
        val bridge = ThumbnailBridge(run, ready, captured)
        bridge.onThumbnailReady("old", "stale")
        bridge.onError("old", "old error")
        assertFalse(captured.isCompleted); assertFalse(ready.isCompleted)
        bridge.onThumbnailReady("current", "png")
        assertEquals("png", kotlinx.coroutines.runBlocking { captured.await() })
        val failed = CompletableDeferred<String?>(); val failedReady = CompletableDeferred<Unit>()
        ThumbnailBridge(run, failedReady, failed).onRuntimeError("current", "broken", 2)
        assertTrue(failedReady.isCompleted)
        assertNull(kotlinx.coroutines.runBlocking { failed.await() })
        val empty = CompletableDeferred<String?>()
        ThumbnailBridge(run, CompletableDeferred(), empty).onThumbnailReady("current", "")
        assertNull(kotlinx.coroutines.runBlocking { empty.await() })
    }
    @Test fun isolatedBridgeRejectsOldTokensAndPreservesRunConfiguration() {
        val work = Work("sample", "Sample", "function setup() {}", p5SoundEnabled = true,
            files = mutableMapOf("helper.js" to "const n = 1;"))
        val prepared = preparePreviewRun(capturePreviewRun(work, work.code, work.files, work.assets, token = "current"))
        val ready = CompletableDeferred<Unit>()
        val bridge = ThumbnailBridge(prepared, ready)
        assertEquals("", bridge.getSketchCode("old"))
        assertEquals("{}", bridge.getProjectConfig("old"))
        assertFalse(bridge.isP5SoundEnabled("old"))
        bridge.onPreviewReady("old")
        assertFalse(ready.isCompleted)
        assertEquals(prepared.sketchCode, bridge.getSketchCode("current"))
        assertTrue(bridge.isP5SoundEnabled("current"))
        assertTrue(JSONObject(bridge.getProjectConfig("current")).getBoolean("thumbnailOnly"))
        assertFalse(JSONObject(prepared.projectConfig).has("thumbnailOnly"))
        bridge.onPreviewReady("current")
        assertTrue(ready.isCompleted)
    }
}
