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

    private var cachedRequest: FileSearchRequest? = null
    private var cachedMatches: List<IntRange> = emptyList()

    fun searchMatches(source: String): List<IntRange> {
        val request = FileSearchRequest(source, searchQuery, searchMatchCase)
        if (request != cachedRequest) {
            cachedMatches = findFileMatches(request)
            cachedRequest = request
        }
        return cachedMatches
    }

    fun selectSearchMatch(
        source: String,
        currentSelection: TextRange,
        direction: Int,
        matches: List<IntRange> = searchMatches(source)
    ): TextRange? {
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
        return replaceFileMatches(currentValue, matches, replacementText)
    }
}

internal data class FileSearchRequest(val source: String, val query: String, val matchCase: Boolean)

internal fun findFileMatches(request: FileSearchRequest): List<IntRange> {
    val (source, query, matchCase) = request
    if (query.isEmpty()) return emptyList()
    return buildList {
        var from = 0
        while (from <= source.length - query.length) {
            val start = source.indexOf(query, from, ignoreCase = !matchCase)
            if (start < 0) break
            add(start until start + query.length)
            from = start + query.length
        }
    }
}

/** Append each untouched span once; replacement length does not multiply copy cost. */
internal fun replaceFileMatches(value: TextFieldValue, matches: List<IntRange>, replacement: String): TextFieldValue {
    if (matches.isEmpty()) return value
    val source = value.text
    val result = buildString {
        var from = 0
        for (range in matches) {
            append(source, from, range.first)
            append(replacement)
            from = range.last + 1
        }
        append(source, from, source.length)
    }
    return TextFieldValue(result, TextRange(0))
}
