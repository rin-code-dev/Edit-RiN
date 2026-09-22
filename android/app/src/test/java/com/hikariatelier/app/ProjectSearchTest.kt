package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Test

class ProjectSearchTest {
    @Test fun searchesCurrentDraftsWithoutOtherWorksOrDeletedFiles() {
        val sources = projectSearchSources("work", "unsaved main", mapOf("b.js" to "saved", "a.js" to "saved"),
            mapOf("work/b.js" to "unsaved helper", "other/a.js" to "wrong work", "work/deleted.js" to "deleted"))
        assertEquals(listOf("sketch.js", "a.js", "b.js"), sources.keys.toList())
        assertEquals(listOf("sketch.js", "b.js"), searchProject(sources, "unsaved", false).matches.map { it.file })
        assertEquals("saved", sources["a.js"])
        assertFalse(sources.containsKey("deleted.js"))
    }

    @Test fun returnsExactSelectionsAndLinesForMultipleMatchesAndUnicode() {
        val code = "日本語\r\n\nlet Foo = foo + foo;"
        val matches = searchProject(mapOf("helper.js" to code), "foo", false).matches
        assertEquals(3, matches.size)
        assertTrue(matches.all { it.line == 3 && it.excerpt == "let Foo = foo + foo;" })
        assertEquals(listOf("Foo", "foo", "foo"), matches.map { code.substring(it.start, it.end) })
        assertEquals(2, searchProject(mapOf("helper.js" to code), "foo", true).matches.size)
    }

    @Test fun handlesEmptyQueriesMultilineMatchesAndResultLimit() {
        val sources = mapOf("sketch.js" to "a\na\na")
        assertTrue(searchProject(sources, "", false).matches.isEmpty())
        assertEquals(1, searchProject(sources, "a\na", false).matches.single().line)
        assertTrue(searchProject(sources, "a", false, 2).truncated)
        assertFalse(searchProject(sources, "a", false, 3).truncated)
        assertTrue(searchProject(sources, "absent", false).matches.isEmpty())
    }

    @Test fun excerptsStayBoundedAndIncludeMatchesFarAlongALine() {
        val code = "x".repeat(2000) + "needle" + "y".repeat(2000)
        val match = searchProject(mapOf("long.js" to code), "needle", true).matches.single()
        assertTrue(match.excerpt.contains("needle"))
        assertTrue(match.excerpt.length <= 202)
        assertEquals("needle", code.substring(match.start, match.end))
    }
}
