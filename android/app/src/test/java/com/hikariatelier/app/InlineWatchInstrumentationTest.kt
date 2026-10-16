package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Test

class InlineWatchInstrumentationTest {
    @Test fun preservesDeclarationKindAndLineNumbers() {
        for (kind in listOf("var", "let", "const")) {
            val source = "if (true) {\n  $kind x = 1; // ?\n}\n"
            val result = instrumentInlineWatchComments(source)
            assertTrue(result.contains("  $kind x = window.rinWatch"))
            assertEquals(source.count { it == '\n' }, result.count { it == '\n' })
        }
    }
}
