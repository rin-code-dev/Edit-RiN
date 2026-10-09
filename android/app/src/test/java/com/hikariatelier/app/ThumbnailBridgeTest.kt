package com.hikariatelier.app

import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ThumbnailBridgeTest {
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
