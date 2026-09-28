package com.hikariatelier.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel

class SearchReplaceViewModel : ViewModel() {
    var searchQuery by mutableStateOf("")
    var replacementText by mutableStateOf("")
    var searchWholeWork by mutableStateOf(false)
    var searchMatchCase by mutableStateOf(false)
    var goToLineText by mutableStateOf("")
    var showSearchDialog by mutableStateOf(false)

    fun searchMatches(source: String): List<IntRange> {
        if (searchQuery.isEmpty()) return emptyList()
        val needle = searchQuery
        val matches = mutableListOf<IntRange>()
        var fromIndex = 0
        while (fromIndex <= source.length - needle.length) {
            val start = source.indexOf(needle, fromIndex, ignoreCase = !searchMatchCase)
            if (start < 0) break
            matches += start until (start + needle.length)
            fromIndex = start + needle.length.coerceAtLeast(1)
        }
        return matches
    }

    fun selectSearchMatch(
        source: String,
        currentSelection: TextRange,
        direction: Int
    ): TextRange? {
        val matches = searchMatches(source)
        if (matches.isEmpty()) return null
        val selectionStart = currentSelection.min
        val selectionEnd = currentSelection.max
        val currentIndex = matches.indexOfFirst {
            it.first == selectionStart && it.last + 1 == selectionEnd
        }
        val targetIndex = if (direction >= 0) {
            if (currentIndex >= 0) {
                (currentIndex + 1) % matches.size
            } else {
                matches.indexOfFirst { it.first >= selectionEnd }
                    .takeIf { it >= 0 }
                    ?: 0
            }
        } else {
            if (currentIndex >= 0) {
                (currentIndex - 1 + matches.size) % matches.size
            } else {
                matches.indexOfLast { it.last + 1 <= selectionStart }
                    .takeIf { it >= 0 }
                    ?: matches.lastIndex
            }
        }
        val target = matches[targetIndex]
        return TextRange(target.first, target.last + 1)
    }

    fun replaceSelectedMatch(
        currentValue: TextFieldValue
    ): Pair<TextFieldValue, Boolean> {
        if (searchQuery.isEmpty()) return currentValue to false
        val source = currentValue.text
        val start = currentValue.selection.min
        val end = currentValue.selection.max
        if (start < 0 || end > source.length || start >= end) {
            return currentValue to false
        }
        val selected = source.substring(start, end)
        val matches = if (searchMatchCase) {
            selected == searchQuery
        } else {
            selected.equals(searchQuery, ignoreCase = true)
        }
        if (!matches) {
            return currentValue to false
        }
        val nextText = source.replaceRange(start, end, replacementText)
        val nextValue = TextFieldValue(
            nextText,
            TextRange(start + replacementText.length)
        )
        return nextValue to true
    }

    fun replaceAllMatches(
        currentValue: TextFieldValue
    ): TextFieldValue {
        val source = currentValue.text
        val matches = searchMatches(source)
        if (matches.isEmpty()) return currentValue
        val builder = StringBuilder(source)
        matches.asReversed().forEach { range ->
            builder.replace(range.first, range.last + 1, replacementText)
        }
        return TextFieldValue(builder.toString(), TextRange(0))
    }
}
