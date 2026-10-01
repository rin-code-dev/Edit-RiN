package com.hikariatelier.app

import androidx.compose.runtime.mutableIntStateOf
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

internal const val P5_VERSION_LEGACY = "1.11.5"
internal const val P5_VERSION_CURRENT = "2.3.3"
internal val SUPPORTED_P5_VERSIONS = listOf(P5_VERSION_CURRENT, P5_VERSION_LEGACY)

internal fun normalizedP5Version(value: String?): String =
    value?.takeIf(SUPPORTED_P5_VERSIONS::contains) ?: P5_VERSION_LEGACY

class Work(
    val id: String,
    title: String,
    code: String,
    files: MutableMap<String, String> = mutableMapOf(),
    assets: Map<String, ProjectAsset> = emptyMap(),
    revisions: MutableList<WorkRevision> = mutableListOf(),
    previewAspectRatio: String = "1:1",
    p5Version: String = P5_VERSION_CURRENT,
    p5SoundEnabled: Boolean = false,
    libraries: Map<String, String> = emptyMap(),
    parameterValues: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    updatedAt: Long = System.currentTimeMillis(),
    isPinned: Boolean = false,
    tags: List<String> = emptyList()
) {
    /** Gallery metadata placeholders cannot be persisted as empty authored content. */
    internal var bodyLoaded: Boolean = true
    var title by mutableStateOf(title)
    var code by mutableStateOf(code)
    var previewAspectRatio by mutableStateOf(previewAspectRatio)
    var p5Version by mutableStateOf(normalizedP5Version(p5Version))
    var p5SoundEnabled by mutableStateOf(p5SoundEnabled)
    var libraries by mutableStateOf(normalizedWorkLibraries(libraries))
    val parameterValues = parameterValues.toList().toMutableStateMap()
    var updatedAt by mutableStateOf(updatedAt)
    var isPinned by mutableStateOf(isPinned)
    val tags = tags.distinct().filter { it.isNotBlank() }.toMutableStateList()
    val files = files.toList().toMutableStateMap()
    val revisions = revisions.toMutableStateList()
    val assets = assets.toList().toMutableStateMap()
}

data class WorkStore(
    val works: List<Work>,
    val activeWorkId: String
)

class EditorSessionViewModel : ViewModel() {
    internal class EditorScroll {
        val vertical = androidx.compose.foundation.ScrollState(0)
        val horizontal = androidx.compose.foundation.ScrollState(0)
    }
    private val editorScrolls = mutableMapOf<String, EditorScroll>()
    internal fun editorScroll(key: String): EditorScroll = editorScrolls.getOrPut(key) { EditorScroll() }
    var assetBusy by mutableStateOf(false)
    var editorInputLocked by mutableStateOf(false)
    var snapshotOperationWorkId by mutableStateOf<String?>(null)
    var snapshotRestoring by mutableStateOf(false)
    val fileDrafts = androidx.compose.runtime.mutableStateMapOf<String, String>()
    val fileEditorValues = mutableMapOf<String, androidx.compose.runtime.MutableState<TextFieldValue>>()
    internal val codeFoldStates = androidx.compose.runtime.mutableStateMapOf<String, CodeFoldState>()
    val fileUndoStacks = mutableMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<TextFieldValue>>()
    val fileRedoStacks = mutableMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<TextFieldValue>>()
    var auxiliaryEditorGeneration by mutableIntStateOf(0)
        private set

    /** Discard caches only when their saved source is replaced or deleted. */
    fun clearAuxiliaryEditors(workId: String? = null) {
        editorScrolls.keys.removeAll { workId == null || it.startsWith("$workId/") }
        val keys = (fileDrafts.keys + fileEditorValues.keys + fileUndoStacks.keys + fileRedoStacks.keys)
            .filter { workId == null || it.startsWith("$workId/") }
        keys.forEach { key ->
            fileDrafts.remove(key)
            fileEditorValues.remove(key)
            fileUndoStacks.remove(key)
            fileRedoStacks.remove(key)
        }
        codeFoldStates.keys.filter { workId == null || it.startsWith("$workId/") }
            .forEach { codeFoldStates.remove(it) }
        auxiliaryEditorGeneration++
    }

    /** Invalidate only a changed/deleted file after its save succeeds. */
    fun clearAuxiliaryEditor(workId: String, fileName: String) {
        val key = "$workId/$fileName"
        editorScrolls.remove(key)
        fileDrafts.remove(key)
        fileEditorValues.remove(key)
        fileUndoStacks.remove(key)
        fileRedoStacks.remove(key)
        codeFoldStates.remove(key)
        auxiliaryEditorGeneration++
    }

    var assetPreviewRevision by mutableStateOf(0)
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

    fun clearEditHistory() {
        undoStack.clear()
        redoStack.clear()
    }

    fun applyChange(
        currentValue: TextFieldValue,
        nextValue: TextFieldValue,
        targetUndoStack: androidx.compose.runtime.snapshots.SnapshotStateList<TextFieldValue> = undoStack,
        targetRedoStack: androidx.compose.runtime.snapshots.SnapshotStateList<TextFieldValue> = redoStack
    ) {
        if (nextValue.text != currentValue.text) {
            targetUndoStack.add(currentValue.copy(composition = null))
            while (targetUndoStack.size > 100 || (targetUndoStack.size > 1 && targetUndoStack.sumOf { it.text.length.toLong() } > 2_000_000)) {
                targetUndoStack.removeAt(0)
            }
            targetRedoStack.clear()
        }
    }

    fun undo(
        currentValue: TextFieldValue,
        targetUndoStack: androidx.compose.runtime.snapshots.SnapshotStateList<TextFieldValue> = undoStack,
        targetRedoStack: androidx.compose.runtime.snapshots.SnapshotStateList<TextFieldValue> = redoStack
    ): TextFieldValue? {
        if (targetUndoStack.isEmpty()) return null
        targetRedoStack.add(currentValue.copy(composition = null))
        return targetUndoStack.removeAt(targetUndoStack.lastIndex)
    }

    fun redo(
        currentValue: TextFieldValue,
        targetUndoStack: androidx.compose.runtime.snapshots.SnapshotStateList<TextFieldValue> = undoStack,
        targetRedoStack: androidx.compose.runtime.snapshots.SnapshotStateList<TextFieldValue> = redoStack
    ): TextFieldValue? {
        if (targetRedoStack.isEmpty()) return null
        targetUndoStack.add(currentValue.copy(composition = null))
        return targetRedoStack.removeAt(targetRedoStack.lastIndex)
    }
}
