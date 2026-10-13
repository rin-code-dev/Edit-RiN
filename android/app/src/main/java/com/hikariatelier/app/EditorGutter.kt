package com.hikariatelier.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class EditorGutterRow(val visualLine: Int, val logicalLine: Int, val label: String, val fold: CodeFold?)

internal fun editorGutterRow(visual: Int, offset: Int, starts: List<Int>, folds: Map<Int, CodeFold>,
                            collapsed: Set<Int>, numbers: Boolean, digits: Int): EditorGutterRow? {
    val line = starts.binarySearch(offset)
    if (line < 0) return null // Continuation of a wrapped logical line.
    val fold = folds[line]
    val label = (if (folds.isEmpty()) "" else when {
        fold == null -> " "
        fold.open in collapsed -> "▸"
        else -> "▾"
    }) + if (numbers) (line + 1).toString().padStart(digits) else ""
    return EditorGutterRow(visual, line + 1, label, fold)
}

/** Draw/measure only visible row labels; the body layout remains the authority for wrapped baselines. */
@Composable
internal fun EditorGutter(
    documentKey: String, layout: TextLayoutResult?, displayText: String, projection: FoldProjection,
    starts: List<Int>, folds: Map<Int, CodeFold>, collapsed: Set<Int>, errors: Set<Int>,
    numbers: Boolean, digits: Int, width: Dp, minimumHeight: Dp, viewportHeight: Float,
    scroll: ScrollState, style: TextStyle, errorColor: androidx.compose.ui.graphics.Color,
    dividerColor: androidx.compose.ui.graphics.Color, onFold: (CodeFold) -> Unit, onError: (Int) -> Unit,
    foldDescription: (Boolean, Int) -> String
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 256)
    val currentLayout = layout?.takeIf { it.layoutInput.text.text == displayText }
    val rows by remember(currentLayout, projection, starts, folds, collapsed, numbers, digits, viewportHeight, scroll) {
        derivedStateOf {
            val textLayout = currentLayout ?: return@derivedStateOf emptyList<EditorGutterRow>()
            val top = (scroll.value - with(density) { 16.dp.toPx() }).coerceAtLeast(0f)
            val first = (textLayout.getLineForVerticalPosition(top) - 1).coerceAtLeast(0)
            val last = (textLayout.getLineForVerticalPosition(top + viewportHeight) + 1).coerceAtMost(textLayout.lineCount - 1)
            (first..last).mapNotNull { visual -> editorGutterRow(visual,
                projection.transformedToOriginal(textLayout.getLineStart(visual)), starts, folds, collapsed, numbers, digits) }
        }
    }
    val measured = remember(rows, style, errors, errorColor, measurer) {
        rows.map { row -> row to measurer.measure(AnnotatedString(row.label), style =
            if (row.logicalLine in errors) style.copy(color = errorColor, fontWeight = FontWeight.Bold) else style,
            softWrap = false) }
    }
    val currentTap by rememberUpdatedState<(Offset) -> Unit> { position ->
        val textLayout = currentLayout
        if (textLayout != null && position.y >= 0 && position.y < textLayout.size.height) {
            val visual = textLayout.getLineForVerticalPosition(position.y)
            val item = measured.firstOrNull { it.first.visualLine == visual }
            if (item != null) {
                val (row, label) = item
                val left = with(density) { width.toPx() - 5.dp.toPx() } - label.size.width
                val arrowRight = if (row.fold != null) left + label.getBoundingBox(0).right else -1f
                when (editorGutterAction(row.fold != null, row.logicalLine in errors, position.x <= arrowRight)) {
                    EditorGutterAction.FOLD -> row.fold?.let(onFold)
                    EditorGutterAction.ERROR -> onError(row.logicalLine)
                    null -> Unit
                }
            }
        }
    }
    val height = with(density) { (layout?.size?.height ?: 0).toDp() }.coerceAtLeast(minimumHeight)
    Canvas(Modifier.width(width).height(height).pointerInput(documentKey) {
        detectTapGestures { currentTap(it) }
    }.semantics {
        text = AnnotatedString(rows.joinToString("\n") { it.label })
        customActions = rows.mapNotNull { row -> row.fold?.let { fold ->
            CustomAccessibilityAction(foldDescription(fold.open in collapsed, row.logicalLine)) { onFold(fold); true }
        } }
    }) {
        drawLine(dividerColor, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
        val textLayout = currentLayout ?: return@Canvas
        measured.forEach { (row, label) ->
            drawText(label, topLeft = Offset(size.width - 5.dp.toPx() - label.size.width,
                textLayout.getLineBaseline(row.visualLine) - label.firstBaseline))
        }
    }
}
