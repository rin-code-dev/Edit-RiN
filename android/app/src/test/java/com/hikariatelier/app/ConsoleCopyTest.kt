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
}
