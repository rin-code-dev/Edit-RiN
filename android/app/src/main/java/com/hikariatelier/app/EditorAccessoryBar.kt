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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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

private val QUICK_SWATCH_HEXES = listOf(
    "#FF5252", "#FF9800", "#FFEB3B", "#4CAF50",
    "#00BCD4", "#448AFF", "#B388FF", "#F8BBD0",
    "#FFFFFF", "#121318"
)

/**
 * Enhanced EditorAccessoryBar (v3):
 * - 2-Axis Velocity Scrubber: horizontal drag changes value; vertical drag switches gear (×10 / ×1 / ×0.1 / ×0.01)
 *   with haptic feedback and triggers [onLiveScrub] for immediate 60fps canvas updates.
 * - Inline Color Quick Swatch Bar: when the cursor is on a color literal, presents an inline quick palette
 *   above the accessory bar so users can tweak colors live without a modal dialog blocking the canvas.
 */
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
    modifier: Modifier = Modifier,
    onLiveScrub: (() -> Unit)? = null
) {
    fun uiText(source: String, vararg arguments: Any?): String = textTranslator(source, arguments)
    val keyHeight = if (compactAccessoryKeys) 30.dp else 34.dp
    val iconSize = if (compactAccessoryKeys) 16.dp else 18.dp
    val haptic = LocalHapticFeedback.current

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
    val currentOnLiveScrub by rememberUpdatedState(onLiveScrub)
    val density = LocalDensity.current
    val scrubStepPx = with(density) { 8.dp.toPx().coerceAtLeast(4f) }
    val gearStepPx = with(density) { 26.dp.toPx().coerceAtLeast(14f) }

    val detectedColorTarget = remember(editingValue.text, editingValue.selection) {
        findColorAtSelection(editingValue.text, editingValue.selection)
    }
    val detectedNumberTarget = remember(editingValue.text, editingValue.selection, detectedColorTarget) {
        if (detectedColorTarget != null) null else findNumberAtSelection(editingValue.text, editingValue.selection)
    }

    var activeGearLabel by remember { mutableStateOf<String?>(null) }
    var showInlineColorStrip by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Inline Color Quick Popover Strip (Non-modal live color manipulation)
        AnimatedVisibility(
            visible = detectedColorTarget != null && showInlineColorStrip,
            enter = fadeIn(tween(100)) + expandVertically(tween(120)),
            exit = fadeOut(tween(80)) + shrinkVertically(tween(100))
        ) {
            val target = detectedColorTarget
            if (target != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surfaceContainerHigh,
                    border = BorderStroke(1.dp, colors.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        QUICK_SWATCH_HEXES.forEach { hex ->
                            val swatch = parseHexColor(hex) ?: Color.White
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(1.dp, colors.outlineVariant, CircleShape)
                                    .clickable {
                                        val r = target.range
                                        if (r.first >= 0 && r.last < editingValue.text.length) {
                                            val replacement = formatColorReplacement(target, swatch)
                                            onApplyEdit(
                                                TextFieldValue(
                                                    editingValue.text.replaceRange(r.first, r.last + 1, replacement),
                                                    TextRange(r.first + replacement.length)
                                                )
                                            )
                                            currentOnLiveScrub?.invoke()
                                        }
                                    }
                            )
                        }
                        TextButton(
                            onClick = { onOpenColorPicker(target, target.color) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text(uiText("詳細"), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
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
                // Left Pinned Zone: Undo, Redo, Inline Color Swatch, and 2-Axis Live Number Scrubber
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlatIconKey(R.drawable.ic_undo, uiText("元に戻す"), enabled = undoAvailable, onClick = onUndo)
                    FlatIconKey(R.drawable.ic_redo, uiText("やり直す"), enabled = redoAvailable, onClick = onRedo)

                    val swatchColor = detectedColorTarget?.color ?: lastColorPickerColor
                    Surface(
                        onClick = {
                            if (detectedColorTarget != null) {
                                showInlineColorStrip = !showInlineColorStrip
                            } else {
                                onOpenColorPicker(null, swatchColor)
                            }
                        },
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
                                    currentOnLiveScrub?.invoke()
                                }
                            },
                            modifier = Modifier
                                .widthIn(min = if (compactAccessoryKeys) 56.dp else 64.dp)
                                .height(keyHeight)
                                .semantics {
                                    contentDescription = uiText("左右にドラッグして数値を調整")
                                }
                                .pointerInput(scrubStepPx, gearStepPx) {
                                    var activeRange: IntRange? = null
                                    var currentVal = 0.0
                                    var baseStep = 1.0
                                    var baseDecimals = 0
                                    var gearExponent = 0
                                    var accumulatedDx = 0f
                                    var accumulatedDy = 0f
                                    detectDragGestures(
                                        onDragStart = {
                                            val target = findNumberAtSelection(
                                                currentEditingValue.text,
                                                currentEditingValue.selection
                                            )
                                            activeRange = target?.range
                                            currentVal = target?.value ?: 0.0
                                            baseStep = target?.step ?: 1.0
                                            baseDecimals = target?.decimals ?: 0
                                            gearExponent = 0
                                            accumulatedDx = 0f
                                            accumulatedDy = 0f
                                            activeGearLabel = "×1"
                                        },
                                        onDrag = { change, dragAmount ->
                                            val range = activeRange ?: return@detectDragGestures
                                            change.consume()
                                            accumulatedDx += dragAmount.x
                                            accumulatedDy -= dragAmount.y // Up = coarser (×10), Down = finer (×0.1)
                                            val nextGear = (accumulatedDy / gearStepPx).toInt().coerceIn(-2, 2)
                                            if (nextGear != gearExponent) {
                                                gearExponent = nextGear
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                activeGearLabel = when (gearExponent) {
                                                    2 -> "×100"
                                                    1 -> "×10"
                                                    0 -> "×1"
                                                    -1 -> "×0.1"
                                                    else -> "×0.01"
                                                }
                                            }
                                            val deltaSteps = (accumulatedDx / scrubStepPx).toInt()
                                            if (deltaSteps != 0) {
                                                accumulatedDx -= deltaSteps * scrubStepPx
                                                val effectiveStep = baseStep * 10.0.pow(gearExponent.toDouble())
                                                val effectiveDecimals = (baseDecimals - gearExponent).coerceIn(0, 4)
                                                currentVal += deltaSteps * effectiveStep
                                                val formatted = formatScrubbedNumber(currentVal, effectiveDecimals)
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
                                                        currentOnLiveScrub?.invoke()
                                                    }
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            activeRange = null
                                            activeGearLabel = null
                                        },
                                        onDragCancel = {
                                            activeRange = null
                                            activeGearLabel = null
                                        }
                                    )
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = colors.primary.copy(alpha = 0.12f),
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
                                    text = detectedNumberTarget.rawText + (activeGearLabel?.let { " ($it)" } ?: ""),
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
}

private fun formatColorReplacement(target: EditorColorTarget, color: Color): String {
    val argb = color.toArgb()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val raw = target.rawText.trim()
    return when {
        raw.startsWith("color(", ignoreCase = true) -> "color($r, $g, $b)"
        raw.startsWith("rgb(", ignoreCase = true) -> "rgb($r, $g, $b)"
        raw.startsWith("'") -> String.format(Locale.ROOT, "'#%02X%02X%02X'", r, g, b)
        raw.startsWith("\"") -> String.format(Locale.ROOT, "\"#%02X%02X%02X\"", r, g, b)
        else -> String.format(Locale.ROOT, "#%02X%02X%02X", r, g, b)
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
