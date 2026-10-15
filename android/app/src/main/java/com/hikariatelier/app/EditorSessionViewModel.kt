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
internal const val P5_VERSION_CURRENT = "2.3.4"
internal val SUPPORTED_P5_VERSIONS = listOf(P5_VERSION_CURRENT, P5_VERSION_LEGACY)

internal fun normalizedP5Version(value: String?): String =
    if (value == "2.3.3") P5_VERSION_CURRENT else value?.takeIf(SUPPORTED_P5_VERSIONS::contains) ?: P5_VERSION_LEGACY

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
    tags: List<String> = emptyList(),
    folderName: String = "",
    val isSample: Boolean = false
) {
    /** Gallery metadata placeholders cannot be persisted as empty authored content. */
    internal var bodyLoaded: Boolean = true
    var folderName by mutableStateOf(folderName)
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
    private data class MainEditorResume(
        val value: TextFieldValue,
        val savedText: String,
        val undo: List<TextFieldValue>,
        val redo: List<TextFieldValue>
    ) {
        val size: Long get() = value.text.length.toLong() + undo.sumOf { it.text.length.toLong() } + redo.sumOf { it.text.length.toLong() }
    }
    private val mainEditorResumes = linkedMapOf<String, MainEditorResume>()
    private var skipCurrentEditorCapture = false
    private val mainEditorPositions = mutableMapOf<String, androidx.compose.ui.text.TextRange>()
    private val selectedFiles = mutableMapOf<String, androidx.compose.runtime.MutableState<String>>()
    internal fun editorFileState(workId: String) = selectedFiles.getOrPut(workId) { mutableStateOf("sketch.js") }

    /** Piece Table buffer and delta operation log for low-allocation editing. */
    internal val mainPieceTable = PieceTableBuffer()
    internal val mainDeltaLog = EditOperationLog()
    internal var autoRunEnabled by mutableStateOf(false)
    internal var pipModeEnabled by mutableStateOf(true)
    internal var showInspectorSheet by mutableStateOf(false)
    internal var pipSizePreset by mutableStateOf(PipSizePreset.SMALL)
    internal var pipTranslucent by mutableStateOf(false)
    internal var pipMinimized by mutableStateOf(false)
    internal var pipSnapCorner by mutableStateOf(PipCorner.TOP_END)
    internal var tweetModeEnabled by mutableStateOf(false)
    internal var tweetTargetLimit by mutableIntStateOf(140)
    internal var showMinifySheet by mutableStateOf(false)

    private var liveParameterDragInitial: TextFieldValue? = null

    fun applyLiveParameterToEditor(parameter: WorkParameter, newValue: String) {
        if (liveParameterDragInitial == null) {
            liveParameterDragInitial = editorValueState.value
        }
        val current = editorValueState.value
        val updatedText = updateParameterInSource(current.text, parameter, newValue) ?: return
        if (updatedText != current.text) {
            val updatedValue = current.copy(text = updatedText)
            editorValueState.value = updatedValue
            mainPieceTable.reset(updatedText)
        }
    }

    fun commitLiveParameterChange() {
        val initial = liveParameterDragInitial ?: return
        liveParameterDragInitial = null
        val current = editorValueState.value
        if (initial.text != current.text) {
            val prevCount = mainDeltaLog.undoCommandsCount
            mainDeltaLog.record(initial, current)
            if (mainDeltaLog.undoCommandsCount > prevCount || undoStack.isEmpty()) {
                undoStack.add(TextFieldValue("", initial.selection))
            }
            while (undoStack.size > 2000) {
                undoStack.removeAt(0)
            }
            redoStack.clear()
        }
    }

    /** Navigation keeps history only while its saved source still matches the work. */
    internal fun activateWorkEditor(workId: String, code: String, resume: Boolean, retainedIds: Set<String>) {
        val previousId = activeWorkIdState.value
        mainEditorResumes.keys.retainAll(retainedIds)
        mainEditorPositions.keys.retainAll(retainedIds)
        selectedFiles.keys.retainAll(retainedIds)
        if (!skipCurrentEditorCapture && previousId != workId && previousId in retainedIds) {
            mainEditorPositions[previousId] = editorValueState.value.selection
            mainEditorResumes.remove(previousId)
            mainEditorResumes[previousId] = MainEditorResume(editorValueState.value.copy(composition = null),
                lastSavedTextState.value, undoStack.toList(), redoStack.toList())
        }
        val cached = mainEditorResumes.remove(workId)?.takeIf { resume && it.savedText == code && it.value.text == code }
        skipCurrentEditorCapture = false
        activeWorkIdState.value = workId
        lastSavedTextState.value = code
        historyWorkId = workId
        val position = if (resume) mainEditorPositions[workId]?.let {
            androidx.compose.ui.text.TextRange(it.start.coerceIn(0, code.length), it.end.coerceIn(0, code.length))
        } else null
        val initialVal = cached?.value ?: TextFieldValue(code, position ?: androidx.compose.ui.text.TextRange.Zero)
        editorValueState.value = initialVal
        mainPieceTable.reset(initialVal.text)
        mainDeltaLog.clear()
        if (!resume) mainEditorPositions.remove(workId)
        clearEditHistory()
        cached?.let { undoStack.addAll(it.undo); redoStack.addAll(it.redo) }
        while (mainEditorResumes.size > 8 || mainEditorResumes.values.sumOf { it.size } > 8_000_000L) {
            mainEditorResumes.remove(mainEditorResumes.keys.first())
        }
    }
    internal class EditorScroll {
        val vertical = androidx.compose.foundation.ScrollState(0)
        val horizontal = androidx.compose.foundation.ScrollState(0)
    }
    private val editorScrolls = mutableMapOf<String, EditorScroll>()
    internal fun editorScroll(key: String): EditorScroll = editorScrolls.getOrPut(key) { EditorScroll() }
    var assetBusy by mutableStateOf(false)
    val sampleReadOnly: Boolean get() = worksState.value.any { it.id == activeWorkIdState.value && it.isSample }
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

    fun clearAuxiliaryEditors(workId: String? = null) {
        if (workId == null || workId == activeWorkIdState.value) skipCurrentEditorCapture = true
        if (workId == null) { mainEditorResumes.clear(); mainEditorPositions.clear(); selectedFiles.clear() }
        else { mainEditorResumes.remove(workId); mainEditorPositions.remove(workId); selectedFiles.remove(workId) }
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

    fun clearAuxiliaryEditor(workId: String, fileName: String) {
        val key = "$workId/$fileName"
        editorScrolls.remove(key)
        fileDrafts.remove(key)
        fileEditorValues.remove(key)
        fileUndoStacks.remove(key)
        fileRedoStacks.remove(key)
        codeFoldStates.remove(key)
        if (selectedFiles[workId]?.value == fileName) selectedFiles[workId]?.value = "sketch.js"
        auxiliaryEditorGeneration++
    }

    var assetPreviewRevision by mutableStateOf(0)
    val worksState = mutableStateOf<List<Work>>(emptyList())
    val activeWorkIdState = mutableStateOf("")
    val editorValueState = mutableStateOf(TextFieldValue(""))
    /** Deferred state provider to prevent parent recompositions during typing. */
    val editorValueProvider: () -> TextFieldValue = { editorValueState.value }
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
        val initialCode = editorTextOverride ?: works.find { it.id == resolvedActiveId }?.code.orEmpty()
        lastSavedTextState.value = works.find { it.id == resolvedActiveId }?.code.orEmpty()
        editorValueState.value = TextFieldValue(initialCode)
        mainPieceTable.reset(initialCode)
        mainDeltaLog.clear()
        initialized = true
    }

    fun clearEditHistory() {
        liveParameterDragInitial = null
        undoStack.clear()
        redoStack.clear()
        mainDeltaLog.clear()
    }

    fun applyChange(
        currentValue: TextFieldValue,
        nextValue: TextFieldValue,
        targetUndoStack: androidx.compose.runtime.snapshots.SnapshotStateList<TextFieldValue> = undoStack,
        targetRedoStack: androidx.compose.runtime.snapshots.SnapshotStateList<TextFieldValue> = redoStack
    ) {
        if (nextValue.text != currentValue.text) {
            if (targetUndoStack === undoStack) {
                val change = editorTextChange(currentValue, nextValue)
                mainPieceTable.replace(
                    change.start,
                    change.oldEnd,
                    nextValue.text.substring(change.start, change.newEnd)
                )
                val prevCount = mainDeltaLog.undoCommandsCount
                mainDeltaLog.record(currentValue, nextValue)
                if (mainDeltaLog.undoCommandsCount > prevCount || targetUndoStack.isEmpty()) {
                    targetUndoStack.add(TextFieldValue("", currentValue.selection))
                }
                while (targetUndoStack.size > 2000) {
                    targetUndoStack.removeAt(0)
                }
                targetRedoStack.clear()
                return
            }
            // Coalesce adjacent single-char typing entries in targetUndoStack to cut memory by >80%
            val lastEntry = targetUndoStack.lastOrNull()
            val deltaLen = kotlin.math.abs(nextValue.text.length - currentValue.text.length)
            val canCoalesce = lastEntry != null && targetUndoStack.size >= 2 &&
                deltaLen == 1 &&
                kotlin.math.abs(currentValue.text.length - lastEntry.text.length) in 1..24 &&
                !nextValue.text.endsWith('\n')
            if (!canCoalesce) {
                targetUndoStack.add(currentValue.copy(composition = null))
            }
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
        if (targetUndoStack === undoStack) {
            val restored = mainDeltaLog.applyUndo(currentValue)
            if (restored != null) {
                targetRedoStack.add(TextFieldValue("", currentValue.selection))
                if (targetUndoStack.isNotEmpty()) targetUndoStack.removeAt(targetUndoStack.lastIndex)
                mainPieceTable.reset(restored.text)
                return restored
            }
            if (targetUndoStack.isEmpty()) return null
            targetRedoStack.add(currentValue.copy(composition = null))
            return targetUndoStack.removeAt(targetUndoStack.lastIndex).also {
                mainPieceTable.reset(it.text)
            }
        }
        if (targetUndoStack.isEmpty()) return null
        targetRedoStack.add(currentValue.copy(composition = null))
        return targetUndoStack.removeAt(targetUndoStack.lastIndex)
    }

    fun redo(
        currentValue: TextFieldValue,
        targetUndoStack: androidx.compose.runtime.snapshots.SnapshotStateList<TextFieldValue> = undoStack,
        targetRedoStack: androidx.compose.runtime.snapshots.SnapshotStateList<TextFieldValue> = redoStack
    ): TextFieldValue? {
        if (targetUndoStack === undoStack) {
            val restored = mainDeltaLog.applyRedo(currentValue)
            if (restored != null) {
                targetUndoStack.add(TextFieldValue("", currentValue.selection))
                if (targetRedoStack.isNotEmpty()) targetRedoStack.removeAt(targetRedoStack.lastIndex)
                mainPieceTable.reset(restored.text)
                return restored
            }
            if (targetRedoStack.isEmpty()) return null
            targetUndoStack.add(currentValue.copy(composition = null))
            return targetRedoStack.removeAt(targetRedoStack.lastIndex).also {
                mainPieceTable.reset(it.text)
            }
        }
        if (targetRedoStack.isEmpty()) return null
        targetUndoStack.add(currentValue.copy(composition = null))
        return targetRedoStack.removeAt(targetRedoStack.lastIndex)
    }
}
