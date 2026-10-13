package com.hikariatelier.app

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.pow

internal data class EditorNumberTarget(
    val range: IntRange,
    val rawText: String,
    val value: Double,
    val decimals: Int,
    val step: Double
)

private val NUMBER_LITERAL_REGEX = Regex("""-?\d+(?:\.\d+)?""")

internal fun findNumberAtSelection(text: String, selection: TextRange): EditorNumberTarget? {
    if (text.isEmpty()) return null
    val cursor = selection.min.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
    if (!selection.collapsed && selection.max > lineEnd) return null
    val line = text.substring(lineStart, lineEnd)
    val commentIdx = line.indexOf("//")
    if (commentIdx >= 0 && cursor - lineStart > commentIdx) return null

    for (match in NUMBER_LITERAL_REGEX.findAll(line)) {
        val absStart = lineStart + match.range.first
        val absEndExclusive = lineStart + match.range.last + 1
        val touchesSelection = if (selection.collapsed) {
            cursor in absStart..absEndExclusive
        } else {
            selection.min >= absStart && selection.max <= absEndExclusive
        }
        if (!touchesSelection) continue
        val prevChar = text.getOrNull(absStart - 1)
        val nextChar = text.getOrNull(absEndExclusive)
        if (prevChar != null && (prevChar.isLetterOrDigit() || prevChar == '_' || prevChar == '$' || prevChar == '#' || prevChar == '.')) continue
        if (nextChar != null && (nextChar.isLetterOrDigit() || nextChar == '_' || nextChar == '$' || nextChar == '.')) continue

        val raw = match.value
        val parsed = raw.toDoubleOrNull() ?: continue
        if (!parsed.isFinite()) continue
        val dotIndex = raw.indexOf('.')
        val decimals = if (dotIndex >= 0) (raw.length - dotIndex - 1).coerceIn(0, 6) else 0
        val step = if (decimals > 0) 10.0.pow(-decimals.toDouble()) else 1.0
        return EditorNumberTarget(absStart until absEndExclusive, raw, parsed, decimals, step)
    }
    return null
}

internal fun formatScrubbedNumber(value: Double, decimals: Int): String {
    if (!value.isFinite()) return "0"
    val bd = BigDecimal.valueOf(value).setScale(decimals.coerceIn(0, 6), RoundingMode.HALF_UP)
    val normalized = if (bd.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO.setScale(decimals.coerceIn(0, 6)) else bd
    return normalized.toPlainString()
}

private val RIN_PARAMS_USAGE_REGEX = Regex("""\brinParams\.([A-Za-z][A-Za-z0-9_]*)$""")

internal fun findUndeclaredRinParamAtCursor(text: String, selection: TextRange): String? {
    if (!selection.collapsed) return null
    val cursor = selection.start.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val prefix = text.substring(lineStart, cursor)
    val match = RIN_PARAMS_USAGE_REGEX.find(prefix) ?: return null
    val name = match.groupValues[1]
    val nextChar = text.getOrNull(cursor)
    if (nextChar != null && (nextChar.isLetterOrDigit() || nextChar == '_' || nextChar == '$')) return null
    val existing = workParameters(mapOf("sketch.js" to text))
    if (existing.size >= 16 || existing.any { it.name == name }) return null
    return name
}

internal fun defaultDeclarationForParamName(name: String, existing: List<WorkParameter>): String? {
    val lower = name.lowercase(Locale.ROOT)
    val existingNames = existing.map { it.name }.toSet()
    val label = name.replaceFirstChar { it.uppercase() }.take(40)
    return when {
        lower.contains("color") || lower.contains("ink") || lower.contains("bg") || lower.contains("tint") ->
            parameterDeclaration(ParameterKind.COLOR, name, label, "#BA90E2", "0", "1", "0.1", existingNames, existing.size)
        lower.startsWith("is") || lower.startsWith("has") || lower.startsWith("show") || lower.startsWith("use") || lower.startsWith("enable") ->
            parameterDeclaration(ParameterKind.BOOLEAN, name, label, "true", "0", "1", "0.1", existingNames, existing.size)
        lower.contains("speed") || lower.contains("scale") || lower.contains("rate") || lower.contains("alpha") ->
            parameterDeclaration(ParameterKind.NUMBER, name, label, "1", "0", "5", "0.1", existingNames, existing.size)
        else ->
            parameterDeclaration(ParameterKind.NUMBER, name, label, "50", "0", "200", "1", existingNames, existing.size)
    }
}

@Composable
internal fun EditorAccessoryBar(
    editingValue: TextFieldValue,
    compactAccessoryKeys: Boolean,
    colors: ColorScheme,
    codeFontFamily: FontFamily,
    undoAvailable: Boolean,
    redoAvailable: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSearch: () -> Unit,
    onFormat: () -> Unit,
    onSnippets: () -> Unit,
    onReference: () -> Unit,
    onApplyEdit: (TextFieldValue) -> Unit,
    lastColorPickerColor: Color,
    onOpenColorPicker: (EditorColorTarget?, Color) -> Unit,
    showAccessoryNavigation: Boolean,
    showAccessorySymbols: Boolean,
    textTranslator: (String, Array<out Any?>) -> String,
    modifier: Modifier = Modifier
) {
    fun uiText(source: String, vararg arguments: Any?): String = textTranslator(source, arguments)
    val keyHeight = if (compactAccessoryKeys) 30.dp else 34.dp
    val iconSize = if (compactAccessoryKeys) 16.dp else 18.dp

    @Composable
    fun FlatIconKey(
        @DrawableRes iconRes: Int,
        description: String,
        enabled: Boolean = true,
        onClick: () -> Unit
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(width = if (compactAccessoryKeys) 32.dp else 36.dp, height = keyHeight)
                .semantics { contentDescription = description },
            shape = RoundedCornerShape(8.dp),
            color = Color.Transparent,
            contentColor = colors.onSurface.copy(alpha = if (enabled) 0.88f else 0.32f)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }

    @Composable
    fun FlatTextKey(
        label: String,
        description: String,
        width: Int = 34,
        enabled: Boolean = true,
        onClick: () -> Unit
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .width((if (compactAccessoryKeys) (width - 4).coerceAtLeast(28) else width).dp)
                .height(keyHeight)
                .semantics { contentDescription = description },
            shape = RoundedCornerShape(8.dp),
            color = Color.Transparent,
            contentColor = colors.onSurface.copy(alpha = if (enabled) 0.9f else 0.32f)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    fontFamily = codeFontFamily,
                    fontSize = if (compactAccessoryKeys) 12.sp else 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }

    @Composable
    fun KeyDivider() {
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .width(1.dp)
                .height(18.dp)
                .background(colors.outlineVariant.copy(alpha = 0.65f))
        )
    }

    val moveLeft = {
        val target = if (!editingValue.selection.collapsed) {
            editingValue.selection.min
        } else {
            (editingValue.selection.start - 1).coerceAtLeast(0)
        }
        onApplyEdit(editingValue.copy(selection = TextRange(target)))
    }
    val moveRight = {
        val target = if (!editingValue.selection.collapsed) {
            editingValue.selection.max
        } else {
            (editingValue.selection.end + 1).coerceAtMost(editingValue.text.length)
        }
        onApplyEdit(editingValue.copy(selection = TextRange(target)))
    }

    val currentEditingValue by rememberUpdatedState(editingValue)
    val currentOnApplyEdit by rememberUpdatedState(onApplyEdit)
    val scrubStepPx = with(LocalDensity.current) { 8.dp.toPx().coerceAtLeast(4f) }

    val detectedColorTarget = remember(editingValue.text, editingValue.selection) {
        findColorAtSelection(editingValue.text, editingValue.selection)
    }
    val detectedNumberTarget = remember(editingValue.text, editingValue.selection, detectedColorTarget) {
        if (detectedColorTarget != null) null else findNumberAtSelection(editingValue.text, editingValue.selection)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.75f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = if (compactAccessoryKeys) 3.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Pinned Zone: Undo, Redo, Minimal Color Dot, and Live Number Scrub
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlatIconKey(R.drawable.ic_undo, uiText("元に戻す"), enabled = undoAvailable, onClick = onUndo)
                FlatIconKey(R.drawable.ic_redo, uiText("やり直す"), enabled = redoAvailable, onClick = onRedo)

                val swatchColor = detectedColorTarget?.color ?: lastColorPickerColor
                Surface(
                    onClick = { onOpenColorPicker(detectedColorTarget, swatchColor) },
                    modifier = Modifier
                        .size(width = if (compactAccessoryKeys) 32.dp else 36.dp, height = keyHeight)
                        .semantics {
                            contentDescription = uiText(if (detectedColorTarget != null) "カラーを編集" else "カラーピッカー")
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = if (detectedColorTarget != null) colors.surfaceVariant.copy(alpha = 0.55f) else Color.Transparent
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(if (detectedColorTarget != null) 14.dp else 11.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    width = if (detectedColorTarget != null) 1.5.dp else 1.dp,
                                    color = if (detectedColorTarget != null) colors.primary else colors.outlineVariant,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                if (detectedNumberTarget != null) {
                    Surface(
                        onClick = {
                            val nextVal = detectedNumberTarget.value + detectedNumberTarget.step
                            val formatted = formatScrubbedNumber(nextVal, detectedNumberTarget.decimals)
                            val r = detectedNumberTarget.range
                            if (r.first >= 0 && r.last < editingValue.text.length) {
                                onApplyEdit(
                                    TextFieldValue(
                                        editingValue.text.replaceRange(r.first, r.last + 1, formatted),
                                        TextRange(r.first + formatted.length)
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .widthIn(min = if (compactAccessoryKeys) 52.dp else 60.dp)
                            .height(keyHeight)
                            .semantics {
                                contentDescription = uiText("左右にドラッグして数値を調整")
                            }
                            .pointerInput(scrubStepPx) {
                                var activeRange: IntRange? = null
                                var startNumber = 0.0
                                var step = 1.0
                                var decimals = 0
                                var accumulatedDx = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        val target = findNumberAtSelection(
                                            currentEditingValue.text,
                                            currentEditingValue.selection
                                        )
                                        activeRange = target?.range
                                        startNumber = target?.value ?: 0.0
                                        step = target?.step ?: 1.0
                                        decimals = target?.decimals ?: 0
                                        accumulatedDx = 0f
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        val range = activeRange ?: return@detectHorizontalDragGestures
                                        change.consume()
                                        accumulatedDx += dragAmount
                                        val deltaSteps = (accumulatedDx / scrubStepPx).toInt()
                                        val nextVal = startNumber + deltaSteps * step
                                        val formatted = formatScrubbedNumber(nextVal, decimals)
                                        val latestText = currentEditingValue.text
                                        if (range.first >= 0 && range.last < latestText.length) {
                                            val currentSlice = latestText.substring(range.first, range.last + 1)
                                            if (currentSlice != formatted) {
                                                val updatedText = latestText.replaceRange(range.first, range.last + 1, formatted)
                                                val newEndExclusive = range.first + formatted.length
                                                activeRange = range.first until newEndExclusive
                                                currentOnApplyEdit(
                                                    TextFieldValue(updatedText, TextRange(newEndExclusive))
                                                )
                                            }
                                        }
                                    },
                                    onDragEnd = { activeRange = null },
                                    onDragCancel = { activeRange = null }
                                )
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = colors.primary.copy(alpha = 0.1f),
                        contentColor = colors.primary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_scrub),
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = detectedNumberTarget.rawText,
                                fontFamily = codeFontFamily,
                                fontSize = if (compactAccessoryKeys) 11.sp else 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            KeyDivider()

            // Right Scrollable Zone: Tools, Navigation, and Symbols
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlatIconKey(R.drawable.ic_search, uiText("検索と置換"), onClick = onSearch)
                FlatIconKey(R.drawable.ic_snippet, uiText("スニペット"), onClick = onSnippets)
                FlatIconKey(R.drawable.ic_help, uiText("p5.jsリファレンス"), onClick = onReference)
                FlatIconKey(R.drawable.ic_format, uiText("コードを整形"), onClick = onFormat)

                if (showAccessoryNavigation) {
                    KeyDivider()
                    FlatIconKey(R.drawable.ic_indent, uiText("インデント")) {
                        onApplyEdit(changeLineIndent(editingValue, true))
                    }
                    FlatIconKey(R.drawable.ic_outdent, uiText("インデントを戻す")) {
                        onApplyEdit(changeLineIndent(editingValue, false))
                    }
                    FlatTextKey("←", uiText("左へ移動"), onClick = moveLeft)
                    FlatTextKey("↑", uiText("上へ移動")) {
                        onApplyEdit(moveCursorVertically(editingValue, -1))
                    }
                    FlatTextKey("↓", uiText("下へ移動")) {
                        onApplyEdit(moveCursorVertically(editingValue, 1))
                    }
                    FlatTextKey("→", uiText("右へ移動"), onClick = moveRight)
                }

                if (showAccessorySymbols) {
                    KeyDivider()
                    listOf(
                        Triple("{}", "{", "}"), Triple("()", "(", ")"),
                        Triple("[]", "[", "]"), Triple("\"\"", "\"", "\""),
                        Triple("''", "'", "'")
                    ).forEach { (label, opening, closing) ->
                        FlatTextKey(label, uiText("%s を入力", label)) {
                            onApplyEdit(insertAtSelection(editingValue, opening, closing))
                        }
                    }
                    listOf(";", "=", ",", ".").forEach { symbol ->
                        FlatTextKey(symbol, uiText("%s を入力", symbol), width = 30) {
                            onApplyEdit(insertAtSelection(editingValue, symbol))
                        }
                    }
                    FlatIconKey(R.drawable.ic_comment, uiText("コメントを入力")) {
                        onApplyEdit(insertAtSelection(editingValue, "// "))
                    }
                }
            }
        }
    }
}

@Composable
internal fun CompletionBar(
    suggestions: List<CompletionCandidate>,
    editingValue: TextFieldValue,
    editingText: String,
    visible: Boolean,
    codeFontFamily: FontFamily,
    colors: ColorScheme,
    textTranslator: (String, Array<out Any?>) -> String,
    onApplyCompletion: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    val undeclaredParam = remember(editingText, editingValue.selection) {
        findUndeclaredRinParamAtCursor(editingText, editingValue.selection)
    }

    AnimatedVisibility(
        visible = visible && (suggestions.isNotEmpty() || undeclaredParam != null),
        modifier = modifier,
        enter = fadeIn(tween(100)) + expandVertically(tween(130)),
        exit = fadeOut(tween(80)) + shrinkVertically(tween(110))
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.75f))
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (undeclaredParam != null) {
                    AssistChip(
                        onClick = {
                            val existing = workParameters(mapOf("sketch.js" to editingText))
                            val declaration = defaultDeclarationForParamName(undeclaredParam, existing)
                            if (declaration != null) {
                                onApplyCompletion(prependDeclarations(editingValue, declaration))
                            }
                        },
                        label = {
                            Text(
                                textTranslator("＋ @rin %s を宣言", arrayOf(undeclaredParam)),
                                fontFamily = codeFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    )
                }
                suggestions.forEach { suggestion ->
                    SuggestionChip(
                        onClick = {
                            val end = editingValue.selection.start
                            val start = (end - completionPrefix(editingValue).length).coerceAtLeast(0)
                            onApplyCompletion(
                                TextFieldValue(
                                    editingText.replaceRange(start, end, suggestion.name),
                                    TextRange(start + suggestion.name.length)
                                )
                            )
                        },
                        label = {
                            Text(
                                suggestion.file?.let { "${suggestion.name} · $it" }
                                    ?: completionHelp(suggestion.name) { s -> textTranslator(s, emptyArray()) },
                                fontFamily = codeFontFamily,
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }
        }
    }
}
