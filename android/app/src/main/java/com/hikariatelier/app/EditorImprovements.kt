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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun editorHighlight(source: String, dark: Boolean, errors: Set<Int>): VisualTransformation {
    val immediate = remember(dark, errors) { JavaScriptHighlighter(dark, errors) }
    if (source.length < 8_000) return immediate
    val result by produceState<Pair<String, TransformedText>?>(null, source, dark, errors) {
        // Rapid typing supersedes this job before a full-document scan begins.
        delay(120)
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

/** Keep first-open parsing of a large sketch away from keyboard and sheet animations. */
@Composable
internal fun editorFoldRegions(source: String, documentKey: String): List<CodeFold>? {
    if (source.length < 8_000) return remember(source) { codeFolds(source) }
    var lastComplete by remember(documentKey) { mutableStateOf<Pair<String, List<CodeFold>>?>(null) }
    val result by produceState<Pair<String, List<CodeFold>>?>(null, source) {
        delay(120)
        value = withContext(Dispatchers.Default) { source to codeFolds(source) }
    }
    if (result?.first == source) {
        SideEffect { lastComplete = result }
        return result?.second
    }
    return lastComplete?.let { (oldSource, folds) -> rebaseCodeFoldRegions(oldSource, folds, source) }
}

internal fun revisionDifference(current: String, revision: String): String {
    val before = current.lines()
    val after = revision.lines()
    var prefix = 0
    while (prefix < minOf(before.size, after.size) && before[prefix] == after[prefix]) prefix++
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
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        names.forEach { name ->
            val ext = name.substringAfterLast('.', "").lowercase()
            val isShader = ext in setOf("frag", "vert", "glsl")
            FilterChip(
                selected = name == selected,
                onClick = { onSelect(name) },
                label = {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(name, maxLines = 1)
                        if (isShader) {
                            Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ) {
                                Text(
                                    ext.uppercase(),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}
