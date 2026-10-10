package com.hikariatelier.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun editorHighlight(
    source: String, dark: Boolean, errors: Set<Int>, file: String = "sketch.js", documentKey: String = file
): VisualTransformation {
    val immediate = remember(file, dark) { projectFileHighlighter(file, dark, emptySet()) }
    val cache = remember(documentKey, file, dark) { EditorHighlightCache() }
    val result = if (source.length < 500) null else key(documentKey, file, dark) {
        produceState<AnnotatedString?>(null, source) {
            // Rapid typing supersedes this job before a full-document scan begins.
            delay(120)
            value = withContext(Dispatchers.Default) {
                projectFileHighlighter(file, dark, emptySet()).filter(AnnotatedString(source)).text
            }
        }.value
    }
    return remember(source, result, immediate, cache, dark, errors) {
        VisualTransformation { text ->
            val completed = if (text.length < 500) immediate.filter(text).text else result
            val syntax = cache.highlight(text, completed)
            TransformedText(withEditorErrorLines(syntax, dark, errors), OffsetMapping.Identity)
        }
    }
}

internal const val EDITOR_FOLD_IDLE_MS = 350L

/** All full scans wait for idle input and run off the UI thread, including small files. */
@Composable
internal fun editorFoldRegions(
    editingValue: androidx.compose.ui.text.input.TextFieldValue,
    documentKey: String,
    parse: (String) -> List<CodeFold> = ::codeFolds
): EditorFoldSnapshot = key(documentKey) {
    val analysis = remember { EditorFoldAnalysis() }
    val source = editingValue.text
    val result by produceState<Pair<String, List<CodeFold>>?>(null, source) {
        delay(EDITOR_FOLD_IDLE_MS)
        value = withContext(Dispatchers.Default) { source to parse(source) }
    }
    analysis.update(editingValue, result)
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
internal fun FileTabs(
    names: List<String>, selected: String, dirtyFiles: Set<String> = emptySet(),
    unsavedDescription: String = "未保存の変更あり", enabled: Boolean = true, onSelect: (String) -> Unit
) {
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
                enabled = enabled,
                onClick = { onSelect(name) },
                modifier = Modifier.semantics { if (name in dirtyFiles) stateDescription = unsavedDescription },
                label = {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(name, maxLines = 1)
                        if (name in dirtyFiles) {
                            Box(Modifier.size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        }
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
