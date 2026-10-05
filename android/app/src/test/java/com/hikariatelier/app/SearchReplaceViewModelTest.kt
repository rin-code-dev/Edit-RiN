package com.hikariatelier.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class SearchReplaceViewModelTest {
    @Test fun openingFromASelectedWordSeedsTheQueryAndReturnsToCurrentFile() {
        val vm = SearchReplaceViewModel().apply { searchWholeWork = true; searchQuery = "previous" }
        vm.openSearch(TextFieldValue("let color = 1;", TextRange(9, 4)))
        assertEquals("color", vm.searchQuery)
        assertFalse(vm.searchWholeWork)
        assertTrue(vm.showSearchDialog)
    }

    @Test fun openingWithoutASuitableSelectionRetainsTheLastQuery() {
        val vm = SearchReplaceViewModel().apply { searchQuery = "color" }
        vm.openSearch(TextFieldValue("plain text", TextRange(3)))
        assertEquals("color", vm.searchQuery)
        vm.openSearch(TextFieldValue("first\nsecond", TextRange(0, 12)))
        assertEquals("color", vm.searchQuery)
        vm.openSearch(TextFieldValue("x".repeat(501), TextRange(0, 501)))
        assertEquals("color", vm.searchQuery)
    }

    @Test fun navigationRevealsTheNearestMatchWithoutRewritingText() {
        val vm = SearchReplaceViewModel().apply { searchQuery = "red" }
        val source = "red + green + red + red"
        val middle = source.indexOf("green")
        val next = vm.selectSearchMatch(source, TextRange(middle), 1)!!
        val previous = vm.selectSearchMatch(source, TextRange(middle), -1)!!
        assertEquals(TextRange(source.indexOf("red", 1), source.indexOf("red", 1) + 3), next)
        assertEquals(TextRange(0, 3), previous)
        assertEquals("red", source.substring(next.min, next.max))
    }

    @Test fun replaceSelectedHonorsCaseAndReversedSelection() {
        val vm = SearchReplaceViewModel().apply {
            searchQuery = "red"
            replacementText = "blue"
            searchMatchCase = true
        }
        val source = TextFieldValue("RED red", TextRange(3, 0))
        assertFalse(vm.replaceSelectedMatch(source).second)
        vm.searchMatchCase = false
        val (replaced, changed) = vm.replaceSelectedMatch(source)
        assertTrue(changed)
        assertEquals("blue red", replaced.text)
        assertEquals(TextRange(4), replaced.selection)
    }

    @Test fun replaceAllKeepsAnUnmatchedSelectionInPlaceAfterLengthChanges() {
        val source = "left aa middle aa right"
        val selected = source.indexOf("middle")
        val value = TextFieldValue(source, TextRange(selected + 6, selected))
        val result = replaceFileMatches(value, findFileMatches(FileSearchRequest(source, "aa", true)), "long")
        assertEquals("left long middle long right", result.text)
        assertEquals(TextRange(selected + 8, selected + 2), result.selection)
        assertEquals("middle", result.text.substring(result.selection.min, result.selection.max))
    }

    @Test fun replaceAllPreservesCaretsBeforeAndAfterMatchesAndCollapsesDeletedMatches() {
        val source = "left aa middle aa right"
        val matches = findFileMatches(FileSearchRequest(source, "aa", true))
        assertEquals(TextRange(2), replaceFileMatches(TextFieldValue(source, TextRange(2)), matches, "long").selection)
        val after = source.indexOf("right")
        assertEquals(TextRange(after + 4), replaceFileMatches(TextFieldValue(source, TextRange(after)), matches, "long").selection)
        assertEquals(TextRange(5), replaceFileMatches(TextFieldValue(source, TextRange(6)), matches, "").selection)
    }

    @Test fun replaceAllRetainsTheSelectedReplacementRatherThanJumpingToTheStart() {
        val source = "a cat b cat c"
        val second = source.lastIndexOf("cat")
        val result = replaceFileMatches(
            TextFieldValue(source, TextRange(second + 3, second)),
            findFileMatches(FileSearchRequest(source, "cat", true)), "panther"
        )
        val replacedSecond = result.text.lastIndexOf("panther")
        assertEquals(TextRange(replacedSecond + 7, replacedSecond), result.selection)
        assertEquals("panther", result.text.substring(result.selection.min, result.selection.max))
    }

    @Test fun replaceAllWithNoMatchesRetainsSelectionAndComposition() {
        val value = TextFieldValue("unchanged", TextRange(5), TextRange(3, 6))
        assertSame(value, replaceFileMatches(value, emptyList(), "replacement"))
    }
}
