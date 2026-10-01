package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Test

class ProjectUiSupportTest {
    @Test fun dirtyTabsIncludeChangedShaderAndMainButIgnoreRestoredAndOtherWorkDrafts() {
        val dirty = dirtyProjectFiles("work", "saved", "changed",
            mapOf("same.js" to "same", "shader.frag" to "old", "other.js" to "same"),
            mapOf("work/same.js" to "same", "work/shader.frag" to "new", "other/other.js" to "new"))
        assertEquals(setOf("sketch.js", "shader.frag"), dirty)
        assertTrue(dirtyProjectFiles("work", "saved", "saved", mapOf("same.js" to "same"),
            mapOf("work/same.js" to "same")).isEmpty())
    }

    @Test fun assetReferencesUseUnsavedAuxiliaryTextAndKeepExactCaseAndLocations() {
        val sources = projectSearchSources("work", "// main\nloadImage('assets/photo.png');",
            mapOf("helper.js" to "loadImage('assets/old.png');"),
            mapOf("work/helper.js" to "// pending\nloadImage('assets/photo.png');\nloadImage('assets/Photo.png');"))
        val references = assetReferenceCandidates(sources, "photo.png")
        assertEquals(listOf("sketch.js", "helper.js"), references.matches.map { it.file })
        assertEquals(listOf(2, 2), references.matches.map { it.line })
        assertFalse(references.truncated)
        assertTrue(assetReferenceCandidates(sources, "old.png").matches.isEmpty())
    }

    @Test fun referenceSummaryBoundsLargeResults() {
        val source = "loadImage('assets/a.png');\n".repeat(100)
        val result = assetReferenceCandidates(mapOf("sketch.js" to source), "a.png")
        assertEquals(50, result.matches.size)
        assertTrue(result.truncated)
        assertEquals(50, result.matches.last().line)
    }

    @Test fun templateSearchIgnoresCaseAndWhitespaceAndKeepsIdsAndOrder() {
        val templates = listOf(Work("a", "Canvas Basic", "a"), Work("b", "Audio", "b"), Work("c", "canvas WebGL", "c"))
        assertEquals(listOf("a", "c"), matchingUserTemplates(templates, " CANVAS ").map { it.id })
        assertSame(templates, matchingUserTemplates(templates, "  "))
        assertTrue(matchingUserTemplates(templates, "missing").isEmpty())
    }

    @Test fun consoleErrorFilterKeepsSourceAndCopyDetailsWithoutDiscardingLogs() {
        val entries = listOf(ConsoleEntry(1, ConsoleLevel.LOG, "hello"),
            ConsoleEntry(2, ConsoleLevel.ERROR, "failed", 4, "helper.js", "work", count = 3),
            ConsoleEntry(3, ConsoleLevel.WARNING, "warning"))
        val filtered = visibleConsoleEntries(entries, errorsOnly = true)
        assertEquals(listOf(2L), filtered.map { it.id })
        assertEquals("[ERROR] failed  ×3  ·  helper.js:4", filtered.single().copyText())
        assertSame(entries, visibleConsoleEntries(entries, errorsOnly = false))
        assertEquals(3, entries.size)
    }
}
