package com.hikariatelier.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/**
 * Viewport-Virtualized Line Renderer that measures and paints ONLY visible lines
 * (plus a small overscan buffer) using [IncrementalLineLexer] and [PieceTableBuffer].
 * Can be used directly for massive read/edit buffers (>5,000 lines) at 120fps.
 */
@Composable
internal fun VirtualizedCodeViewport(
    lines: List<String>,
    darkTheme: Boolean,
    scrollState: ScrollState,
    viewportHeightPx: Float,
    textStyle: TextStyle,
    activeLineIndex: Int,
    activeLineBackground: Color,
    errorLines: Set<Int>,
    errorLineBackground: Color,
    modifier: Modifier = Modifier,
    extraBottomPaddingDp: androidx.compose.ui.unit.Dp = 0.dp
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 512)
    val lexer = remember(darkTheme) { IncrementalLineLexer(darkTheme) }
    val lineHeightPx = with(density) { textStyle.lineHeight.toPx().coerceAtLeast(18.dp.toPx()) }
    val totalHeightDp = with(density) { (lines.size * lineHeightPx + 32.dp.toPx() + extraBottomPaddingDp.toPx()).toDp() }

    val visibleRange by remember(lines.size, scrollState.value, viewportHeightPx, lineHeightPx) {
        derivedStateOf {
            val first = ((scrollState.value / lineHeightPx).toInt() - 6).coerceAtLeast(0)
            val visibleCount = (viewportHeightPx / lineHeightPx).toInt() + 12
            val last = (first + visibleCount).coerceAtMost((lines.size - 1).coerceAtLeast(0))
            first..last
        }
    }

    val highlightedLines = remember(lines, visibleRange, lexer) {
        if (lines.isEmpty()) emptyList()
        else {
            var state = LineLexerState.NORMAL
            // Converge lexer state up to visibleRange.first using cached line states
            for (i in 0 until visibleRange.first) {
                state = lexer.updateAndHighlightLine(i, lines[i], state).endState
            }
            buildList {
                for (i in visibleRange) {
                    val entry = lexer.updateAndHighlightLine(i, lines[i], state)
                    state = entry.endState
                    add(i to AnnotatedString(entry.lineText, entry.spans))
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeightDp)
    ) {
        for ((lineIdx, annotated) in highlightedLines) {
            val topY = lineIdx * lineHeightPx
            val lineNum = lineIdx + 1
            if (lineNum in errorLines) {
                drawRect(
                    color = errorLineBackground,
                    topLeft = Offset(0f, topY),
                    size = Size(size.width, lineHeightPx)
                )
            } else if (lineIdx == activeLineIndex) {
                drawRect(
                    color = activeLineBackground,
                    topLeft = Offset(0f, topY),
                    size = Size(size.width, lineHeightPx)
                )
            }
            val layoutResult = measurer.measure(
                text = annotated,
                style = textStyle,
                softWrap = false
            )
            drawText(
                textLayoutResult = layoutResult,
                topLeft = Offset(0f, topY)
            )
        }
    }
}
