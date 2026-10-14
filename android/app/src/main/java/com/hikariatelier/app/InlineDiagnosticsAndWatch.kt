package com.hikariatelier.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Automatic Quick Fix transformations for common p5.js 2.x migration & syntax issues.
 */
internal data class EditorQuickFix(
    val label: String,
    val transformedCode: String? = null,
    val switchP5Version: String? = null
)

private val PRELOAD_BLOCK_REGEX = Regex("""\bfunction\s+preload\s*\(\s*\)\s*\{([\s\S]*?)\}""")
private val SETUP_HEADER_REGEX = Regex("""\b(async\s+)?function\s+setup\s*\(\s*\)\s*\{""")
private val UNWAITED_LOADER_REGEX = Regex("""(?<!await\s)\b(loadImage|loadSound|loadFont|loadJSON|loadTable|loadStrings|loadBytes|loadXML|loadModel|loadShader)\s*\(""")

internal fun buildEditorQuickFixes(
    errorMessage: String,
    sourceCode: String,
    p5Version: String?,
    textTranslator: (String, Array<out Any?>) -> String
): List<EditorQuickFix> {
    if (p5Version != null && p5Version != P5_VERSION_CURRENT) return emptyList()
    val fixes = mutableListOf<EditorQuickFix>()
    val hasPreload = PRELOAD_BLOCK_REGEX.containsMatchIn(sourceCode)
    val hasUnwaitedLoader = UNWAITED_LOADER_REGEX.containsMatchIn(sourceCode)
    val lower = errorMessage.lowercase()
    val looksLikeAsyncLoaderError = hasPreload || (
        (lower.contains("promise") || lower.contains("is not a function") || lower.contains("undefined")) &&
            hasUnwaitedLoader
        )

    if (looksLikeAsyncLoaderError) {
        val migrated = migratePreloadToAsyncSetup(sourceCode)
        if (migrated != sourceCode) {
            fixes.add(
                EditorQuickFix(
                    label = textTranslator("async setup() + await に自動変換", emptyArray()),
                    transformedCode = migrated
                )
            )
        }
        fixes.add(
            EditorQuickFix(
                label = textTranslator("p5.js 1.11.5 に切替", emptyArray()),
                switchP5Version = P5_VERSION_LEGACY
            )
        )
    }
    return fixes
}

internal fun migratePreloadToAsyncSetup(sourceCode: String): String {
    var updated = sourceCode
    val preloadMatch = PRELOAD_BLOCK_REGEX.find(updated)
    val preloadBody = preloadMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
    if (preloadMatch != null) {
        updated = updated.removeRange(preloadMatch.range).trimStart()
    }
    updated = UNWAITED_LOADER_REGEX.replace(updated) { match ->
        "await ${match.value}"
    }
    val setupMatch = SETUP_HEADER_REGEX.find(updated)
    if (setupMatch != null) {
        val replacement = buildString {
            append("async function setup() {")
            if (preloadBody.isNotEmpty()) {
                val awaitedPreload = UNWAITED_LOADER_REGEX.replace(preloadBody) { "await ${it.value}" }
                append("\n  ")
                append(awaitedPreload)
            }
        }
        updated = updated.replaceRange(setupMatch.range, replacement)
    }
    return updated
}

/**
 * Inline diagnostic card with one-tap Quick Fix actions and jump-to-line support.
 */
@Composable
internal fun InlineDiagnosticBanner(
    latestError: ConsoleEntry,
    editingFile: String,
    editingValue: TextFieldValue,
    p5Version: String?,
    codeFontFamily: FontFamily,
    colors: ColorScheme,
    textTranslator: (String, Array<out Any?>) -> String,
    onJumpToSource: (String, Int) -> Unit,
    onApplyQuickFixCode: (TextFieldValue) -> Unit,
    onSwitchRuntimeVersion: ((String) -> Unit)? = null,
    onCopyError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember(latestError.id) { mutableStateOf(false) }
    val errorFile = latestError.file ?: editingFile
    val errorLine = latestError.line
    val hint = remember(latestError.message, editingValue.text, p5Version) {
        p5ErrorDiagnosticHint(latestError.message, editingValue.text, p5Version)
    }
    val quickFixes = remember(latestError.message, editingValue.text, p5Version) {
        buildEditorQuickFixes(latestError.message, editingValue.text, p5Version, textTranslator)
    }

    Surface(
        onClick = {
            if (errorLine != null) onJumpToSource(errorFile, errorLine)
            else expanded = !expanded
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.45f))
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_error_jump),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = colors.error
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = (if (errorLine != null) "$errorFile:$errorLine · " else "") +
                        latestError.message.lineSequence().firstOrNull().orEmpty(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = codeFontFamily,
                    maxLines = if (expanded) 6 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (errorLine != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        textTranslator("行へ移動", emptyArray()),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Text(if (expanded) "▴" else "▾", style = MaterialTheme.typography.labelMedium)
                }
            }

            AnimatedVisibility(visible = expanded || hint != null || quickFixes.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (hint != null) {
                        Text(
                            text = textTranslator(hint, emptyArray()),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (quickFixes.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            quickFixes.forEach { fix ->
                                AssistChip(
                                    onClick = {
                                        if (fix.transformedCode != null) {
                                            onApplyQuickFixCode(
                                                TextFieldValue(
                                                    fix.transformedCode,
                                                    TextRange(
                                                        editingValue.selection.start.coerceAtMost(fix.transformedCode.length)
                                                    )
                                                )
                                            )
                                        } else if (fix.switchP5Version != null) {
                                            onSwitchRuntimeVersion?.invoke(fix.switchP5Version)
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = "⚡ ${fix.label}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = colors.surface.copy(alpha = 0.9f),
                                        labelColor = colors.primary
                                    )
                                )
                            }
                        }
                    }
                    if (expanded) {
                        if (latestError.message.lines().size > 1) {
                            Text(
                                text = latestError.message,
                                fontFamily = codeFontFamily,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { onCopyError(latestError.copyText()) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text(
                                    textTranslator("エラーをコピー", emptyArray()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Live Watch inline badges: displays real-time values next to lines containing `// ?` or `rinWatch(...)`.
 */
@Composable
internal fun LiveWatchOverlayColumn(
    watchValues: Map<Int, String>,
    logicalLineStarts: List<Int>,
    projection: FoldProjection,
    textLayout: androidx.compose.ui.text.TextLayoutResult?,
    scrollValue: Int,
    viewportHeightPx: Float,
    codeFontFamily: FontFamily,
    colors: ColorScheme,
    modifier: Modifier = Modifier
) {
    if (watchValues.isEmpty() || textLayout == null) return
    val density = androidx.compose.ui.platform.LocalDensity.current

    Box(modifier = modifier.fillMaxSize()) {
        watchValues.forEach { (logicalLine, formattedValue) ->
            val lineIdx = logicalLine - 1
            if (lineIdx in logicalLineStarts.indices) {
                val originalOffset = logicalLineStarts[lineIdx]
                if (!projection.isHidden(originalOffset)) {
                    val transformedOffset = projection.originalToTransformed(originalOffset)
                        .coerceIn(0, textLayout.layoutInput.text.length)
                    val visualLine = textLayout.getLineForOffset(transformedOffset)
                    val lineTopPx = textLayout.getLineTop(visualLine) - scrollValue
                    if (lineTopPx >= -24f && lineTopPx <= viewportHeightPx + 24f) {
                        val topDp = with(density) { lineTopPx.toDp() } + 16.dp
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = topDp.coerceAtLeast(0.dp), end = 12.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = colors.secondaryContainer.copy(alpha = 0.9f),
                            contentColor = colors.onSecondaryContainer,
                            border = BorderStroke(0.5.dp, colors.secondary.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "⇒ $formattedValue",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                fontFamily = codeFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private val INLINE_WATCH_COMMENT_REGEX = Regex(
    """^(\s*)(?:(?:let|const|var)\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*=\s*([^;\n]+);?|([A-Za-z_$][A-Za-z0-9_$.]*)\s*=\s*([^;\n]+);?|([^/\n]+?))\s*//\s*\?\s*$""",
    RegexOption.MULTILINE
)

/**
 * Instruments lines ending with `// ?` so the runtime reports their live values via `window.rinWatch(line, label, val)`.
 * Preserves exact line count so stack traces and line numbers remain 1:1.
 */
internal fun instrumentInlineWatchComments(source: String): String {
    if (!source.contains("//?") && !source.contains("// ?")) return source
    val lines = source.split('\n')
    var changed = false
    val transformed = lines.mapIndexed { idx, line ->
        val trimmed = line.trimEnd()
        if (!trimmed.endsWith("//?") && !trimmed.endsWith("// ?")) {
            line
        } else {
            val match = INLINE_WATCH_COMMENT_REGEX.find(line)
            if (match == null) {
                line
            } else {
                changed = true
                val indent = match.groupValues[1]
                val declVar = match.groupValues[2]
                val declExpr = match.groupValues[3]
                val assignVar = match.groupValues[4]
                val assignExpr = match.groupValues[5]
                val bareExpr = match.groupValues[6].trim().removeSuffix(";")
                val lineNumber = idx + 1
                when {
                    declVar.isNotEmpty() ->
                        "${indent}let $declVar = window.rinWatch ? window.rinWatch($lineNumber, ${org.json.JSONObject.quote(declVar)}, ($declExpr)) : ($declExpr); // ?"
                    assignVar.isNotEmpty() ->
                        "$indent$assignVar = window.rinWatch ? window.rinWatch($lineNumber, ${org.json.JSONObject.quote(assignVar)}, ($assignExpr)) : ($assignExpr); // ?"
                    bareExpr.isNotEmpty() ->
                        "${indent}window.rinWatch?.($lineNumber, ${org.json.JSONObject.quote(bareExpr.take(24))}, ($bareExpr)); // ?"
                    else -> line
                }
            }
        }
    }
    return if (changed) transformed.joinToString("\n") else source
}
