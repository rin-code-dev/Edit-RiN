package com.hikariatelier.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal data class EditorNavigationTarget(val file: String, val line: Int, val selection: TextRange? = null) {
    fun sourceOffset(source: String): Int? = selection?.takeIf {
        it.min >= 0 && it.max <= source.length
    }?.min ?: sourceLineOffset(source, line)
}

internal enum class EditorGutterAction { FOLD, ERROR }

internal fun editorGutterAction(hasFold: Boolean, hasError: Boolean, onFoldControl: Boolean): EditorGutterAction? = when {
    hasFold && (onFoldControl || !hasError) -> EditorGutterAction.FOLD
    hasError -> EditorGutterAction.ERROR
    else -> null
}

/** Read-only text still supports selection and copying; text changes take the editing path. */
internal fun dispatchEditorValueChange(
    current: TextFieldValue,
    next: TextFieldValue,
    readOnly: Boolean,
    onSelectionChange: (TextFieldValue) -> Unit,
    onTextChange: (TextFieldValue) -> Unit
) {
    if (next.text == current.text) onSelectionChange(current.copy(selection = next.selection, composition = next.composition))
    else if (!readOnly) onTextChange(next)
}

internal data class EditorFoldToggle(val state: CodeFoldState, val value: TextFieldValue)

/** Folding changes only presentation and selection, including for read-only samples. */
internal fun toggleEditorFold(
    source: String,
    value: TextFieldValue,
    collapsed: Set<Int>,
    regions: List<CodeFold>,
    fold: CodeFold
): EditorFoldToggle? {
    if (value.text != source || fold !in regions) return null
    val closing = fold.open !in collapsed
    val next = if (closing) collapsed + fold.open else collapsed - fold.open
    // Put the caret outside the hidden range so automatic navigation does not reopen it.
    val selection = if (closing) value.copy(selection = TextRange(fold.open), composition = null) else value
    return EditorFoldToggle(CodeFoldState(source, next, regions), selection)
}
