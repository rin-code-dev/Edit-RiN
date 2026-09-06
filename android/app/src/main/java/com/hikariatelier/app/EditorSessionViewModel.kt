package com.hikariatelier.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.toMutableStateMap
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel

data class WorkRevision(
    val code: String,
    val savedAt: Long
)

class Work(
    val id: String,
    title: String,
    code: String,
    files: MutableMap<String, String> = mutableMapOf(),
    revisions: MutableList<WorkRevision> = mutableListOf(),
    previewAspectRatio: String = "1:1",
    val createdAt: Long = System.currentTimeMillis(),
    updatedAt: Long = System.currentTimeMillis()
) {
    var title by mutableStateOf(title)
    var code by mutableStateOf(code)
    var previewAspectRatio by mutableStateOf(previewAspectRatio)
    var updatedAt by mutableStateOf(updatedAt)
    val files = files.toList().toMutableStateMap()
    val revisions = revisions.toMutableStateList()
}

data class WorkStore(
    val works: List<Work>,
    val activeWorkId: String
)

class EditorSessionViewModel : ViewModel() {
    val worksState = mutableStateOf<List<Work>>(emptyList())
    val activeWorkIdState = mutableStateOf("")
    val editorValueState = mutableStateOf(TextFieldValue(""))
    val lastSavedTextState = mutableStateOf("")
    val undoStack = mutableStateListOf<TextFieldValue>()
    val redoStack = mutableStateListOf<TextFieldValue>()
    var historyWorkId = ""
    val unreadableFolderUris = mutableSetOf<String>()

    var initialized = false
        private set

    fun initialize(
        works: List<Work>,
        activeWorkId: String,
        editorTextOverride: String? = null
    ) {
        if (initialized) return

        val resolvedActiveId =
            activeWorkId.takeIf { id ->
                works.any { it.id == id }
            } ?: works.firstOrNull()?.id.orEmpty()

        worksState.value = works
        activeWorkIdState.value = resolvedActiveId
        historyWorkId = resolvedActiveId
        lastSavedTextState.value = works.find { it.id == resolvedActiveId }?.code.orEmpty()
        editorValueState.value =
            TextFieldValue(
                editorTextOverride
                    ?: works.find { it.id == resolvedActiveId }?.code.orEmpty()
            )
        initialized = true
    }
}
