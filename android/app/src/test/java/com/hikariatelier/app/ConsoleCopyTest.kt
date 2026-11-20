package com.hikariatelier.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ConsoleCopyTest {
    @Test fun copyKeepsMultilineErrorsLocationAndRepeatCount() {
        val entry = ConsoleEntry(1, ConsoleLevel.ERROR, "ReferenceError: missing\n    at draw", 12, "sketch.js", count = 3)
        assertEquals("[ERROR] ReferenceError: missing\n    at draw  ×3  ·  sketch.js:12", entry.copyText())
    }
    @Test fun copyWorksWithoutSourceLocation() {
        assertEquals("[LOG] hello", ConsoleEntry(2, ConsoleLevel.LOG, "hello").copyText())
    }

    @Test fun parsesPreviewHudMetricsAndRejectsInvalidValues() {
        val parsed = parsePreviewHudMetrics("""{"fps":59.8,"frameCount":142,"width":400,"height":400,"paused":false}""")!!
        assertEquals(59.8f, parsed.fps, 0.01f)
        assertEquals(142, parsed.frameCount)
        assertEquals(400, parsed.width)
        assertEquals(400, parsed.height)
        assertEquals(false, parsed.paused)

        val negative = parsePreviewHudMetrics("""{"fps":-5,"frameCount":-10,"width":-1,"height":200,"paused":true}""")!!
        assertEquals(0f, negative.fps, 0.001f)
        assertEquals(0, negative.frameCount)
        assertEquals(0, negative.width)
        assertEquals(200, negative.height)
        assertEquals(true, negative.paused)

        assertEquals(null, parsePreviewHudMetrics("not-json"))
    }

    @Test fun replHistoryDeduplicatesConsecutiveEntriesAndBoundsSize() {
        val vm = ConsoleViewModel()
        vm.recordReplCommand("  frameCount  ")
        vm.recordReplCommand("frameCount")
        vm.recordReplCommand("width")
        assertEquals(listOf("frameCount", "width"), vm.replHistory.toList())
        repeat(40) { i -> vm.recordReplCommand("cmd_$i") }
        assertEquals(30, vm.replHistory.size)
        assertEquals("cmd_39", vm.replHistory.last())
    }
}
