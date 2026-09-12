package com.hikariatelier.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun editorHighlight(source: String, dark: Boolean, errors: Set<Int>): VisualTransformation {
    val immediate = remember(dark, errors) { JavaScriptHighlighter(dark, errors) }
    if (source.length < 50_000) return immediate
    val result by produceState<Pair<String, TransformedText>?>(null, source, dark, errors) {
        value = withContext(Dispatchers.Default) {
            source to JavaScriptHighlighter(dark, errors).filter(AnnotatedString(source))
        }
    }
    return remember(source, result) {
        VisualTransformation { text ->
            result?.takeIf { it.first == text.text }?.second ?: TransformedText(text, OffsetMapping.Identity)
        }
    }
}

internal fun revisionDifference(current: String, revision: String): String {
    val before = current.lines()
    val after = revision.lines()
    val prefix = before.zip(after).takeWhile { it.first == it.second }.size
    var suffix = 0
    while (suffix < minOf(before.size, after.size) - prefix &&
        before[before.lastIndex - suffix] == after[after.lastIndex - suffix]) suffix++
    return buildString {
        appendLine("@@ ${prefix + 1} @@")
        before.subList(prefix, before.size - suffix).take(400).forEach { appendLine("− $it") }
        after.subList(prefix, after.size - suffix).take(400).forEach { appendLine("+ $it") }
        if (maxOf(before.size, after.size) - prefix - suffix > 400) appendLine("…")
    }
}

@Composable
internal fun FileTabs(names: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        names.forEach { name -> FilterChip(selected = name == selected,
            onClick = { onSelect(name) }, label = { Text(name, maxLines = 1) }) }
    }
}
