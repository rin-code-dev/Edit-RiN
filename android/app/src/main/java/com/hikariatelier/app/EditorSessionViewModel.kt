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
    internal var pipAutoGhosted by mutableStateOf(false)
    internal var tweetModeEnabled by mutableStateOf(false)
    internal var tweetTargetLimit by mutableIntStateOf(140)
    internal var showMinifySheet by mutableStateOf(false)

    internal var suppressAutoRunOnce by mutableStateOf(false)
    private var liveParameterDragInitial: TextFieldValue? = null
    private var liveParameterSubFileInitial: Pair<String, String>? = null

    internal fun applyLiveParameterToEditor(parameter: WorkParameter, newValue: String) {
        suppressAutoRunOnce = true
        val targetFile = parameter.sourceFile
        if (targetFile == "sketch.js" || targetFile.isBlank()) {
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
        } else {
            val workId = activeWorkIdState.value
            val key = "$workId/$targetFile"
            val currentText = fileEditorValues[key]?.value?.text
                ?: fileDrafts[key]
                ?: worksState.value.firstOrNull { it.id == workId }?.files?.get(targetFile).orEmpty()
            if (liveParameterSubFileInitial == null) {
                liveParameterSubFileInitial = key to currentText
            }
            val updatedText = updateParameterInSource(currentText, parameter, newValue) ?: return
            if (updatedText != currentText) {
                fileDrafts[key] = updatedText
                fileEditorValues[key]?.let { it.value = it.value.copy(text = updatedText) }
            }
        }
    }

    internal fun commitLiveParameterChange() {
        val initial = liveParameterDragInitial
        liveParameterDragInitial = null
        val subFileInitial = liveParameterSubFileInitial
        liveParameterSubFileInitial = null
        if (initial != null) {
            val current = editorValueState.value
            if (initial.text != current.text) {
                undoStack.add(initial.copy(composition = null))
                while (undoStack.size > 100 || (undoStack.size > 1 && undoStack.sumOf { it.text.length.toLong() } > 2_000_000)) {
                    undoStack.removeAt(0)
                }
                redoStack.clear()
            }
        }
        if (subFileInitial != null) {
            val (key, initialText) = subFileInitial
            val currentText = fileEditorValues[key]?.value?.text ?: fileDrafts[key] ?: ""
            if (initialText != currentText) {
                val subUndo = fileUndoStacks.getOrPut(key) { androidx.compose.runtime.mutableStateListOf() }
                subUndo.add(TextFieldValue(initialText))
                while (subUndo.size > 100 || (subUndo.size > 1 && subUndo.sumOf { it.text.length.toLong() } > 2_000_000)) {
                    subUndo.removeAt(0)
                }
                fileRedoStacks[key]?.clear()
            }
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

    private var lastEditPosition: Int = -1
    private var lastEditTimestamp: Long = 0L

    fun clearEditHistory() {
        liveParameterDragInitial = null
        liveParameterSubFileInitial = null
        undoStack.clear()
        redoStack.clear()
        mainDeltaLog.clear()
        lastEditPosition = -1
        lastEditTimestamp = 0L
    }

    fun applyChange(
        currentValue: TextFieldValue,
        nextValue: TextFieldValue,
        targetUndoStack: androidx.compose.runtime.snapshots.SnapshotStateList<TextFieldValue> = undoStack,
        targetRedoStack: androidx.compose.runtime.snapshots.SnapshotStateList<TextFieldValue> = redoStack
    ) {
        if (nextValue.text != currentValue.text) {
            val change = editorTextChange(currentValue, nextValue)
            if (targetUndoStack === undoStack) {
                mainPieceTable.replace(
                    change.start,
                    change.oldEnd,
                    nextValue.text.substring(change.start, change.newEnd)
                )
                mainDeltaLog.record(currentValue, nextValue)
            }
            // Coalesce adjacent single-char typing entries in targetUndoStack to cut memory by >80%
            val lastEntry = targetUndoStack.lastOrNull()
            val deltaLen = kotlin.math.abs(nextValue.text.length - currentValue.text.length)
            val inserted = if (change.newEnd > change.start && change.newEnd <= nextValue.text.length) {
                nextValue.text.substring(change.start, change.newEnd)
            } else ""
            val deleted = if (change.oldEnd > change.start && change.oldEnd <= currentValue.text.length) {
                currentValue.text.substring(change.start, change.oldEnd)
            } else ""
            val isInsertion = nextValue.text.length > currentValue.text.length
            val lastWasInsertion = lastEntry != null && currentValue.text.length > lastEntry.text.length
            val now = android.os.SystemClock.uptimeMillis()
            val timeCoalesce = (now - lastEditTimestamp) < 1500L
            val positionCoalesce = lastEditPosition == -1 || kotlin.math.abs(change.start - lastEditPosition) <= 2
            val sameDirection = (lastEntry == null) || (isInsertion == lastWasInsertion)
            val canCoalesce = lastEntry != null && targetUndoStack.size >= 2 &&
                deltaLen == 1 &&
                !inserted.contains('\n') &&
                !deleted.contains('\n') &&
                sameDirection &&
                positionCoalesce &&
                timeCoalesce &&
                kotlin.math.abs(currentValue.text.length - lastEntry.text.length) in 1..24
            if (!canCoalesce) {
                targetUndoStack.add(currentValue.copy(composition = null))
            }
            lastEditPosition = change.start
            lastEditTimestamp = now
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
        return targetUndoStack.removeAt(targetUndoStack.lastIndex).also {
            if (targetUndoStack === undoStack) mainPieceTable.reset(it.text)
        }
    }

    fun redo(
        currentValue: TextFieldValue,
        targetUndoStack: androidx.compose.runtime.snapshots.SnapshotStateList<TextFieldValue> = undoStack,
        targetRedoStack: androidx.compose.runtime.snapshots.SnapshotStateList<TextFieldValue> = redoStack
    ): TextFieldValue? {
        if (targetRedoStack.isEmpty()) return null
        targetUndoStack.add(currentValue.copy(composition = null))
        return targetRedoStack.removeAt(targetRedoStack.lastIndex).also {
            if (targetUndoStack === undoStack) mainPieceTable.reset(it.text)
        }
    }
}
