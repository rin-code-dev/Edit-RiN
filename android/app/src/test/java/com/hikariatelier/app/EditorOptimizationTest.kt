package com.hikariatelier.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class EditorOptimizationTest {
    @Test fun incrementalLineNumbersMatchFullScanAcrossRandomEdits() {
        val cache = EditorLineIndex()
        val random = Random(91)
        var text = "function draw() {\n  background(0);\n}\n"
        repeat(2000) {
            val expected = listOf(0) + text.indices.filter { text[it] == '\n' }.map { it + 1 }
            assertEquals(expected, cache.update(text))
            val start = random.nextInt(text.length + 1)
            val end = random.nextInt(start, text.length + 1)
            val inserted = List(random.nextInt(15)) { "ab\n\r{}"[random.nextInt(6)] }.joinToString("")
            text = text.replaceRange(start, end, inserted)
        }
    }

    @Test fun replacementPreservesUnmatchedTextAndHandlesDifferentLengths() {
        val source = TextFieldValue("Aa aa\nAA end")
        val matches = findFileMatches(FileSearchRequest(source.text, "aa", false))
        assertEquals("long long\nlong end", replaceFileMatches(source, matches, "long").text)
        assertEquals(" \n end", replaceFileMatches(source, matches, "").text)
        assertEquals(source, replaceFileMatches(source, emptyList(), "x"))
        assertEquals(listOf(3..4), findFileMatches(FileSearchRequest(source.text, "aa", true)))
    }

    @Test fun cachedSearchInvalidatesForTextQueryAndCaseAndNavigationWraps() {
        val vm = SearchReplaceViewModel().apply { searchQuery = "a" }
        val first = vm.searchMatches("a A a")
        assertSame(first, vm.searchMatches("a A a"))
        assertEquals(TextRange(4, 5), vm.selectSearchMatch("a A a", TextRange(0, 1), -1, first))
        assertEquals(TextRange(0, 1), vm.selectSearchMatch("a A a", TextRange(4, 5), 1, first))
        vm.searchMatchCase = true
        assertEquals(listOf(0..0, 4..4), vm.searchMatches("a A a"))
        vm.searchQuery = "A"
        assertEquals(listOf(2..2), vm.searchMatches("a A a"))
        assertTrue(vm.searchMatches("bbb").isEmpty())
    }

    @Test fun cachedFoldRegionsPreserveAnUntouchedBlockWhenAnotherLineChanges() {
        val source = "// title\nfunction draw() {\n  circle(1, 2, 3);\n}\n"
        val folds = codeFolds(source)
        val state = CodeFoldState(source, setOf(folds.single().open), folds)
        val next = "// new title" + source.removePrefix("// title")
        assertEquals(setOf(codeFolds(next).single().open), rebasedFolds(state, next))
        assertTrue(rebasedFolds(state, source.replace("circle", "ellipse")).isEmpty())
    }

    @Test fun consoleBatchesRepeatedLogsButPublishesErrorsImmediately() = runBlocking {
        val vm = ConsoleViewModel(this)
        repeat(1000) { vm.append(ConsoleLevel.LOG, "frame") }
        assertTrue(vm.entries.isEmpty())
        vm.append(ConsoleLevel.ERROR, "broken", line = 3, file = "sketch.js", workId = "one")
        assertEquals(2, vm.entries.size)
        assertEquals(1000, vm.entries.first().count)
        assertEquals("broken", vm.entries.last().message)
        assertEquals(1, vm.errorCount)
        vm.append(ConsoleLevel.LOG, "old run")
        vm.clear()
        delay(150)
        assertTrue(vm.entries.isEmpty())
    }

    @Test fun consoleFlushIsBoundedAndRetainsLatestEntriesInOrder() = runBlocking {
        val vm = ConsoleViewModel(this)
        repeat(500) { vm.append(ConsoleLevel.LOG, "$it") }
        delay(150)
        assertEquals(200, vm.entries.size)
        assertEquals("300", vm.entries.first().message)
        assertEquals("499", vm.entries.last().message)
    }
}
