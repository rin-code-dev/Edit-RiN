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
