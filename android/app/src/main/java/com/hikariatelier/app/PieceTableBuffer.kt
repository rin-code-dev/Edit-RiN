package com.hikariatelier.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Piece Table text buffer that avoids full-string copies on insert/delete.
 * Coordinates with [EditOperationLog] for compact delta-based Undo/Redo.
 */
internal class PieceTableBuffer(initialText: String = "") {
    private enum class BufferSource { ORIGINAL, ADD }

    private data class Piece(
        val source: BufferSource,
        val start: Int,
        val length: Int
    )

    private var originalBuffer: String = initialText
    private val addBuffer = StringBuilder()
    private val pieces = mutableListOf<Piece>().apply {
        if (initialText.isNotEmpty()) {
            add(Piece(BufferSource.ORIGINAL, 0, initialText.length))
        }
    }

    private var cachedSnapshot: String? = initialText

    val length: Int
        get() = pieces.sumOf { it.length }

    fun reset(newText: String) {
        originalBuffer = newText
        addBuffer.setLength(0)
        pieces.clear()
        if (newText.isNotEmpty()) {
            pieces.add(Piece(BufferSource.ORIGINAL, 0, newText.length))
        }
        cachedSnapshot = newText
    }

    fun replace(start: Int, endExclusive: Int, inserted: String) {
        val safeStart = start.coerceIn(0, length)
        val safeEnd = endExclusive.coerceIn(safeStart, length)
        if (safeStart == safeEnd && inserted.isEmpty()) return
        if (safeEnd > safeStart) {
            deleteRangeInternal(safeStart, safeEnd - safeStart)
        }
        if (inserted.isNotEmpty()) {
            insertInternal(safeStart, inserted)
        }
        cachedSnapshot = null
    }

    private fun insertInternal(offset: Int, text: String) {
        val addStart = addBuffer.length
        addBuffer.append(text)
        val newPiece = Piece(BufferSource.ADD, addStart, text.length)

        if (pieces.isEmpty()) {
            pieces.add(newPiece)
            return
        }

        var currentOffset = 0
        for (i in pieces.indices) {
            val piece = pieces[i]
            val pieceEnd = currentOffset + piece.length
            if (offset == currentOffset) {
                // Coalesce with preceding ADD piece if contiguous
                val prev = pieces.getOrNull(i - 1)
                if (prev != null && prev.source == BufferSource.ADD && prev.start + prev.length == addStart) {
                    pieces[i - 1] = prev.copy(length = prev.length + text.length)
                } else {
                    pieces.add(i, newPiece)
                }
                return
            } else if (offset < pieceEnd) {
                val splitOffset = offset - currentOffset
                val left = Piece(piece.source, piece.start, splitOffset)
                val right = Piece(piece.source, piece.start + splitOffset, piece.length - splitOffset)
                pieces[i] = left
                pieces.add(i + 1, newPiece)
                pieces.add(i + 2, right)
                return
            }
            currentOffset = pieceEnd
        }

        val last = pieces.last()
        if (last.source == BufferSource.ADD && last.start + last.length == addStart) {
            pieces[pieces.lastIndex] = last.copy(length = last.length + text.length)
        } else {
            pieces.add(newPiece)
        }
    }

    private fun deleteRangeInternal(offset: Int, deleteLength: Int) {
        if (deleteLength <= 0 || pieces.isEmpty()) return
        val deleteEnd = offset + deleteLength
        val updated = mutableListOf<Piece>()
        var currentOffset = 0

        for (piece in pieces) {
            val pieceEnd = currentOffset + piece.length
            if (pieceEnd <= offset || currentOffset >= deleteEnd) {
                updated.add(piece)
            } else {
                if (currentOffset < offset) {
                    val leftLen = offset - currentOffset
                    updated.add(Piece(piece.source, piece.start, leftLen))
                }
                if (pieceEnd > deleteEnd) {
                    val rightSkip = deleteEnd - currentOffset
                    updated.add(Piece(piece.source, piece.start + rightSkip, piece.length - rightSkip))
                }
            }
            currentOffset = pieceEnd
        }
        pieces.clear()
        pieces.addAll(updated)
    }

    override fun toString(): String {
        cachedSnapshot?.let { return it }
        val sb = StringBuilder(length)
        for (piece in pieces) {
            when (piece.source) {
                BufferSource.ORIGINAL -> sb.append(originalBuffer, piece.start, piece.start + piece.length)
                BufferSource.ADD -> sb.append(addBuffer, piece.start, piece.start + piece.length)
            }
        }
        return sb.toString().also { cachedSnapshot = it }
    }

    fun applyUndoDelta(cmd: EditDeltaCommand): String {
        replace(cmd.start, cmd.start + cmd.insertedText.length, cmd.deletedText)
        return toString()
    }

    fun applyRedoDelta(cmd: EditDeltaCommand): String {
        replace(cmd.start, cmd.start + cmd.deletedText.length, cmd.insertedText)
        return toString()
    }
}

/**
 * Compact delta command for Undo/Redo history instead of storing full-document copies per keystroke.
 */
internal data class EditDeltaCommand(
    val start: Int,
    val deletedText: String,
    val insertedText: String,
    val selectionBefore: TextRange,
    val selectionAfter: TextRange,
    val timestampMs: Long = System.currentTimeMillis()
) {
    val memoryWeightBytes: Long
        get() = (deletedText.length + insertedText.length) * 2L + 48L

    fun canCoalesceWith(next: EditDeltaCommand, maxGapMs: Long = 350L): Boolean {
        if (next.timestampMs - timestampMs > maxGapMs) return false
        // Merge contiguous single-line typing insertions
        if (deletedText.isEmpty() && next.deletedText.isEmpty() &&
            !insertedText.contains('\n') && !next.insertedText.contains('\n') &&
            start + insertedText.length == next.start &&
            insertedText.length + next.insertedText.length <= 64
        ) {
            return true
        }
        // Merge contiguous backspace deletions
        if (insertedText.isEmpty() && next.insertedText.isEmpty() &&
            !deletedText.contains('\n') && !next.deletedText.contains('\n') &&
            next.start + next.deletedText.length == start &&
            deletedText.length + next.deletedText.length <= 64
        ) {
            return true
        }
        return false
    }

    fun coalesce(next: EditDeltaCommand): EditDeltaCommand {
        return if (deletedText.isEmpty() && next.deletedText.isEmpty()) {
            copy(
                insertedText = insertedText + next.insertedText,
                selectionAfter = next.selectionAfter,
                timestampMs = next.timestampMs
            )
        } else {
            copy(
                start = next.start,
                deletedText = next.deletedText + deletedText,
                selectionAfter = next.selectionAfter,
                timestampMs = next.timestampMs
            )
        }
    }
}

internal class EditOperationLog(
    private val maxCommands: Int = 2000,
    private val maxMemoryBytes: Long = 5_000_000L
) {
    private val undoCommands = ArrayDeque<EditDeltaCommand>()
    private val redoCommands = ArrayDeque<EditDeltaCommand>()

    val canUndo: Boolean get() = undoCommands.isNotEmpty()
    val canRedo: Boolean get() = redoCommands.isNotEmpty()
    val undoCommandsCount: Int get() = undoCommands.size
    val redoCommandsCount: Int get() = redoCommands.size

    fun clear() {
        undoCommands.clear()
        redoCommands.clear()
    }

    fun record(before: TextFieldValue, after: TextFieldValue) {
        if (before.text == after.text) return
        val change = editorTextChange(before, after)
        val deleted = before.text.substring(change.start, change.oldEnd)
        val inserted = after.text.substring(change.start, change.newEnd)
        val command = EditDeltaCommand(
            start = change.start,
            deletedText = deleted,
            insertedText = inserted,
            selectionBefore = before.selection,
            selectionAfter = after.selection
        )
        val previous = undoCommands.lastOrNull()
        if (previous != null && previous.canCoalesceWith(command)) {
            undoCommands.removeLast()
            undoCommands.addLast(previous.coalesce(command))
        } else {
            undoCommands.addLast(command)
        }
        redoCommands.clear()
        trimBudget()
    }

    fun applyUndo(current: TextFieldValue): TextFieldValue? {
        val cmd = undoCommands.removeLastOrNull() ?: return null
        val end = (cmd.start + cmd.insertedText.length).coerceAtMost(current.text.length)
        val restored = current.text.replaceRange(cmd.start.coerceAtMost(end), end, cmd.deletedText)
        redoCommands.addLast(cmd)
        val safeSelection = TextRange(
            cmd.selectionBefore.start.coerceIn(0, restored.length),
            cmd.selectionBefore.end.coerceIn(0, restored.length)
        )
        return TextFieldValue(restored, safeSelection)
    }

    fun applyRedo(current: TextFieldValue): TextFieldValue? {
        val cmd = redoCommands.removeLastOrNull() ?: return null
        val end = (cmd.start + cmd.deletedText.length).coerceAtMost(current.text.length)
        val reapplied = current.text.replaceRange(cmd.start.coerceAtMost(end), end, cmd.insertedText)
        undoCommands.addLast(cmd)
        val safeSelection = TextRange(
            cmd.selectionAfter.start.coerceIn(0, reapplied.length),
            cmd.selectionAfter.end.coerceIn(0, reapplied.length)
        )
        return TextFieldValue(reapplied, safeSelection)
    }

    private fun trimBudget() {
        var totalBytes = undoCommands.sumOf { it.memoryWeightBytes }
        while (undoCommands.size > maxCommands || (undoCommands.size > 1 && totalBytes > maxMemoryBytes)) {
            val removed = undoCommands.removeFirst()
            totalBytes -= removed.memoryWeightBytes
        }
    }
}

/**
 * Incremental line-state lexer for JavaScript / GLSL.
 * Re-tokenizes only edited lines and stops as soon as end-of-line lexer state converges.
 */
internal enum class LineLexerState {
    NORMAL,
    IN_BLOCK_COMMENT,
    IN_TEMPLATE_STRING
}

internal data class CachedLineHighlight(
    val lineText: String,
    val startState: LineLexerState,
    val endState: LineLexerState,
    val spans: List<AnnotatedString.Range<SpanStyle>>
)

internal class IncrementalLineLexer(private val darkTheme: Boolean) {
    private val keywordStyle = SpanStyle(color = if (darkTheme) Color(0xFFC792EA) else Color(0xFF7B1FA2))
    private val functionStyle = SpanStyle(color = if (darkTheme) Color(0xFF82AAFF) else Color(0xFF1565C0))
    private val stringStyle = SpanStyle(color = if (darkTheme) Color(0xFFC3E88D) else Color(0xFF2E7D32))
    private val numberStyle = SpanStyle(color = if (darkTheme) Color(0xFFF78C6C) else Color(0xFFD84315))
    private val commentStyle = SpanStyle(color = if (darkTheme) Color(0xFF78909C) else Color(0xFF607D8B))

    private var cachedLines = mutableListOf<CachedLineHighlight>()

    fun updateAndHighlightLine(
        lineIndex: Int,
        lineText: String,
        prevEndState: LineLexerState
    ): CachedLineHighlight {
        val existing = cachedLines.getOrNull(lineIndex)
        if (existing != null && existing.lineText == lineText && existing.startState == prevEndState) {
            return existing
        }
        val (endState, spans) = lexSingleLine(lineText, prevEndState)
        val entry = CachedLineHighlight(lineText, prevEndState, endState, spans)
        if (lineIndex < cachedLines.size) {
            cachedLines[lineIndex] = entry
        } else {
            cachedLines.add(entry)
        }
        return entry
    }

    private fun lexSingleLine(
        line: String,
        initialState: LineLexerState
    ): Pair<LineLexerState, List<AnnotatedString.Range<SpanStyle>>> {
        val spans = mutableListOf<AnnotatedString.Range<SpanStyle>>()
        var state = initialState
        var i = 0
        val len = line.length

        while (i < len) {
            when (state) {
                LineLexerState.IN_BLOCK_COMMENT -> {
                    val close = line.indexOf("*/", i)
                    if (close < 0) {
                        spans.add(AnnotatedString.Range(commentStyle, i, len))
                        return LineLexerState.IN_BLOCK_COMMENT to spans
                    } else {
                        spans.add(AnnotatedString.Range(commentStyle, i, close + 2))
                        i = close + 2
                        state = LineLexerState.NORMAL
                    }
                }
                LineLexerState.IN_TEMPLATE_STRING -> {
                    val start = i
                    while (i < len) {
                        val c = line[i]
                        if (c == '\\') {
                            i = (i + 2).coerceAtMost(len)
                        } else if (c == '`') {
                            i++
                            spans.add(AnnotatedString.Range(stringStyle, start, i))
                            state = LineLexerState.NORMAL
                            break
                        } else {
                            i++
                        }
                    }
                    if (state == LineLexerState.IN_TEMPLATE_STRING && i > start) {
                        spans.add(AnnotatedString.Range(stringStyle, start, i))
                    }
                }
                LineLexerState.NORMAL -> {
                    val c = line[i]
                    val next = line.getOrNull(i + 1)
                    when {
                        c == '/' && next == '/' -> {
                            spans.add(AnnotatedString.Range(commentStyle, i, len))
                            return LineLexerState.NORMAL to spans
                        }
                        c == '/' && next == '*' -> {
                            val close = line.indexOf("*/", i + 2)
                            if (close < 0) {
                                spans.add(AnnotatedString.Range(commentStyle, i, len))
                                return LineLexerState.IN_BLOCK_COMMENT to spans
                            } else {
                                spans.add(AnnotatedString.Range(commentStyle, i, close + 2))
                                i = close + 2
                            }
                        }
                        c == '`' -> {
                            val start = i
                            i++
                            var closed = false
                            while (i < len) {
                                if (line[i] == '\\') i = (i + 2).coerceAtMost(len)
                                else if (line[i] == '`') {
                                    i++
                                    closed = true
                                    break
                                } else i++
                            }
                            spans.add(AnnotatedString.Range(stringStyle, start, i))
                            if (!closed) state = LineLexerState.IN_TEMPLATE_STRING
                        }
                        c == '\'' || c == '"' -> {
                            val start = i
                            val contentStart = i + 1
                            i++
                            while (i < len) {
                                if (line[i] == '\\') i = (i + 2).coerceAtMost(len)
                                else if (line[i] == c) {
                                    val content = line.substring(contentStart, i)
                                    i++
                                    if (content.startsWith('#') && content.length in setOf(4, 5, 7, 9)) {
                                        parseHexColor(content)?.let { hexCol ->
                                            spans.add(
                                                AnnotatedString.Range(
                                                    SpanStyle(background = hexCol.copy(alpha = 0.28f)),
                                                    start,
                                                    i
                                                )
                                            )
                                        }
                                    }
                                    break
                                } else i++
                            }
                            spans.add(AnnotatedString.Range(stringStyle, start, i))
                        }
                        c.isLetter() || c == '_' || c == '$' -> {
                            val start = i
                            i++
                            while (i < len && (line[i].isLetterOrDigit() || line[i] == '_' || line[i] == '$')) i++
                            when (line.substring(start, i)) {
                                in LEXER_KEYWORDS -> spans.add(AnnotatedString.Range(keywordStyle, start, i))
                                in LEXER_P5_FUNCTIONS -> spans.add(AnnotatedString.Range(functionStyle, start, i))
                            }
                        }
                        c in '0'..'9' || (c == '.' && next in '0'..'9') -> {
                            val start = i
                            i++
                            while (i < len && (line[i].isLetterOrDigit() || line[i] == '.' || line[i] == '_')) i++
                            spans.add(AnnotatedString.Range(numberStyle, start, i))
                        }
                        else -> i++
                    }
                }
            }
        }
        return state to spans
    }

    companion object {
        private val LEXER_KEYWORDS = setOf(
            "function", "let", "var", "const", "if", "else", "for", "while",
            "return", "true", "false", "new", "this", "class", "extends", "async",
            "await", "switch", "case", "break", "continue", "try", "catch", "finally",
            "throw", "typeof", "instanceof", "null", "undefined", "do", "in",
            "of", "delete", "void", "yield", "import", "export", "default", "super"
        )
        private val LEXER_P5_FUNCTIONS = setOf(
            "setup", "draw", "createCanvas", "resizeCanvas", "background", "fill",
            "noStroke", "circle", "rect", "line", "ellipse", "triangle", "stroke",
            "strokeWeight", "noFill", "translate", "rotate", "push", "pop", "sin",
            "cos", "tan", "random", "noise", "map", "dist", "floor", "ceil", "min",
            "max", "abs", "colorMode", "blendMode", "strokeCap", "touchStarted",
            "touchMoved", "touchEnded", "mousePressed", "mouseDragged", "mouseReleased",
            "rinWatch"
        )
    }
}
