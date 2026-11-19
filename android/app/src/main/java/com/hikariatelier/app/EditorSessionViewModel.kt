package com.hikariatelier.app

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.toMutableStateMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
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
    val isSample: Boolean = false,
    blockEditorState: String? = null
) {
    /** Gallery metadata placeholders cannot be persisted as empty authored content. */
    internal var bodyLoaded: Boolean = true
    var folderName by mutableStateOf(folderName)
    var title by mutableStateOf(title)
    var code by mutableStateOf(code)
    /** Opaque authoring metadata. It never participates in preview execution. */
    var blockEditorState by mutableStateOf(blockEditorState)
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
        val redo: List<TextFieldValue>,
        val blockEditorState: String?,
        val undoBlockStates: List<String?>,
        val redoBlockStates: List<String?>
    ) {
        val size: Long get() = value.text.length.toLong() + undo.sumOf { it.text.length.toLong() } + redo.sumOf { it.text.length.toLong() } +
            (blockEditorState?.length ?: 0) + undoBlockStates.sumOf { (it?.length ?: 0).toLong() } + redoBlockStates.sumOf { (it?.length ?: 0).toLong() }
    }
    private val mainEditorResumes = linkedMapOf<String, MainEditorResume>()
    private var skipCurrentEditorCapture = false
    private val mainEditorPositions = mutableMapOf<String, androidx.compose.ui.text.TextRange>()
    private val selectedFiles = mutableMapOf<String, androidx.compose.runtime.MutableState<String>>()
    private val blockModes = mutableMapOf<String, androidx.compose.runtime.MutableState<Boolean>>()
    internal var blockEditorSearch: (() -> Unit)? = null
    var blockSplitView by mutableStateOf(false)
    private val blockEditorViewports = mutableMapOf<String, BlockEditorViewport>()
    internal fun blockEditorViewport(workId: String): BlockEditorViewport? = blockEditorViewports[workId]
    internal fun rememberBlockEditorViewport(workId: String, viewport: BlockEditorViewport): Boolean {
        if (activeWorkIdState.value != workId) return false
        blockEditorViewports[workId] = viewport
        return true
    }
    private data class BlockDocumentContent(
        val source: String, val metadata: String?, val files: Map<String, String>,
        val p5Version: String?, val soundEnabled: Boolean?, val libraries: Map<String, String>, val assets: Map<String, ProjectAsset>
    )
    private data class BlockDocumentRevision(val content: BlockDocumentContent, val token: String)
    private val blockRevisionSession = java.util.UUID.randomUUID().toString()
    private var blockRevisionSequence = 0L
    private val blockDocumentRevisions = mutableMapOf<String, BlockDocumentRevision>()
    internal var blockEditorActivationGeneration: Long = 0L
        private set

    // Invalidation is O(1) per keypress. Environment comparison is deferred until a block client asks.
    private fun invalidateBlockDocumentRevision(workId: String) { blockDocumentRevisions.remove(workId) }

    internal fun currentBlockDocumentRevision(workId: String): String {
        val work = worksState.value.firstOrNull { it.id == workId }
        val content = BlockDocumentContent(
            if (activeWorkIdState.value == workId) editorValueState.value.text else work?.code.orEmpty(),
            currentBlockEditorState(workId), effectiveProjectFiles(workId, work?.files.orEmpty(), fileDrafts),
            work?.p5Version, work?.p5SoundEnabled, work?.libraries.orEmpty().toMap(), work?.assets.orEmpty().toMap())
        blockDocumentRevisions[workId]?.takeIf { it.content == content }?.let { return it.token }
        return "$blockRevisionSession:${++blockRevisionSequence}".also { token ->
            blockDocumentRevisions[workId] = BlockDocumentRevision(content, token)
        }
    }

    /** Recheck after suspension; a same-ID replacement or a trip away and back is a new editor owner. */
    internal fun canApplyBlockAction(workId: String, activation: Long, expectedRevision: String? = null,
                                     requireEditable: Boolean = true): Boolean {
        if (activeWorkIdState.value != workId || blockEditorActivationGeneration != activation ||
            editorInputLocked || snapshotRestoring) return false
        val work = worksState.value.firstOrNull { it.id == workId } ?: return false
        if (!work.bodyLoaded || (requireEditable && work.isSample)) return false
        return expectedRevision == null || currentBlockDocumentRevision(workId) == expectedRevision
    }
    internal val blockEditorDrafts = androidx.compose.runtime.mutableStateMapOf<String, String?>()
    /** Installed by the visible native host; it must acknowledge pending browser edits before returning. */
    internal var blockEditorFlusher: (suspend (hideChaff: Boolean) -> Boolean)? = null

    internal var blockEditorInputGeneration by mutableStateOf(0L)
        private set
    internal var blockEditorHasPendingInput by mutableStateOf(false)
        private set
    private data class BlockInputDraft(val workId: String, val revision: String, val source: String, val metadata: String?)
    private var blockInputDraft: BlockInputDraft? = null

    internal fun clearBlockInputDraft() {
        blockInputDraft = null
        blockEditorHasPendingInput = false
    }

    internal fun notifyBlockInputChanged() {
        // Keep the last validated draft while the user types an incomplete value.
        blockEditorHasPendingInput = true
        blockEditorInputGeneration++
    }

    /** An open field belongs in recovery storage, without committing it to code or Undo. */
    internal fun stageBlockInputDraft(transaction: BlockEditTransaction): Boolean {
        val revision = transaction.expectedRevision ?: return false
        if (!canApplyBlockAction(transaction.workId, blockEditorActivationGeneration, revision) ||
            transaction.expectedSource != editorValueState.value.text) return false
        val source = applySourcePatches(transaction.expectedSource, transaction.patches) ?: return false
        blockInputDraft = BlockInputDraft(transaction.workId, revision, source, transaction.blockEditorState)
        blockEditorHasPendingInput = source != editorValueState.value.text || transaction.blockEditorState != currentBlockEditorState(transaction.workId)
        return true
    }

    internal fun currentBlockInputDraft(workId: String): Pair<String, String?>? = blockInputDraft?.takeIf {
        it.workId == workId && it.revision == currentBlockDocumentRevision(workId)
    }?.let { it.source to it.metadata }

    internal suspend fun flushBlockEditor(hideChaff: Boolean = true): Boolean {
        val flush = blockEditorFlusher ?: return true
        val owner = activeWorkIdState.value
        val activation = blockEditorActivationGeneration
        return try {
            flush(hideChaff) && activeWorkIdState.value == owner && blockEditorActivationGeneration == activation
        }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }
    private val blockEditorValues = androidx.compose.runtime.mutableStateMapOf<String, String?>()
    private val mainHistoryBlockStates = java.util.IdentityHashMap<TextFieldValue, String?>()

    internal fun blockModeState(workId: String): androidx.compose.runtime.MutableState<Boolean> =
        blockModes.getOrPut(workId) { mutableStateOf(false) }

    internal fun currentBlockEditorState(workId: String): String? =
        if (blockEditorValues.containsKey(workId)) blockEditorValues[workId]
        else if (blockEditorDrafts.containsKey(workId)) blockEditorDrafts[workId]
        else worksState.value.firstOrNull { it.id == workId }?.blockEditorState

    private fun setBlockEditorState(workId: String, state: String?) {
        if (currentBlockEditorState(workId) != state) invalidateBlockDocumentRevision(workId)
        blockEditorValues[workId] = state
        rebaseBlockEditorDraft(workId)
    }

    /** Saving can advance the baseline while a later Undo returns to the old saved state. */
    internal fun rebaseBlockEditorDraft(workId: String) {
        val state = currentBlockEditorState(workId)
        val saved = worksState.value.firstOrNull { it.id == workId }?.blockEditorState
        if (state == saved) blockEditorDrafts.remove(workId) else blockEditorDrafts[workId] = state
    }

    /** Existing toolbar stacks stay compatible; each main-file entry also captures its block state. */
    private fun historyValue(value: TextFieldValue, state: String? = currentBlockEditorState(activeWorkIdState.value)): TextFieldValue =
        value.copy(composition = null).also { mainHistoryBlockStates[it] = state }

    private fun clearMainRedoHistory() {
        redoStack.forEach { mainHistoryBlockStates.remove(it) }
        redoStack.clear()
    }

    private fun trimMainHistory() {
        while (undoStack.size > 100) {
            mainHistoryBlockStates.remove(undoStack.removeAt(0))
        }
        if (undoStack.size > 1) {
            var total = 0L
            var pruneFrom = -1
            for (i in undoStack.lastIndex downTo 0) {
                val item = undoStack[i]
                total += item.text.length.toLong() + (mainHistoryBlockStates[item]?.length ?: 0)
                if (total > 2_000_000L) {
                    pruneFrom = i
                    break
                }
            }
            if (pruneFrom >= 0) {
                for (i in 0..pruneFrom) {
                    mainHistoryBlockStates.remove(undoStack.removeAt(0))
                }
            }
        }
    }

    /** Applies source and authoring metadata together. A stale or invalid action has no effects. */
    internal fun applyBlockEdit(transaction: BlockEditTransaction): Boolean {
        val workId = activeWorkIdState.value
        val work = worksState.value.firstOrNull { it.id == workId } ?: return false
        if (transaction.workId != workId || work.isSample || !work.bodyLoaded || editorInputLocked || snapshotRestoring) return false
        val current = editorValueState.value
        if (transaction.expectedSource != current.text ||
            (transaction.expectedRevision != null && transaction.expectedRevision != currentBlockDocumentRevision(workId))) return false
        val nextText = applySourcePatches(current.text, transaction.patches) ?: return false
        if (nextText == current.text && transaction.blockEditorState == currentBlockEditorState(workId)) return true
        clearBlockInputDraft()
        commitLiveParameterChange()
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            undoStack.add(historyValue(current))
            clearMainRedoHistory()
            setBlockEditorState(workId, transaction.blockEditorState)
            if (nextText != current.text) {
                val next = TextFieldValue(nextText, androidx.compose.ui.text.TextRange(
                    current.selection.start.coerceIn(0, nextText.length), current.selection.end.coerceIn(0, nextText.length)))
                editorValueState.value = next
            }
            trimMainHistory()
            lastEditPosition = -1
            lastEditTimestamp = 0L
        }
        return true
    }
    internal fun editorFileState(workId: String) = selectedFiles.getOrPut(workId) { mutableStateOf("sketch.js") }

    internal var autoRunEnabled by mutableStateOf(false)
    internal var pipModeEnabled by mutableStateOf(true)
    internal var showInspectorSheet by mutableStateOf(false)
    internal var pipSizePreset by mutableStateOf(PipSizePreset.SMALL)
    internal var pipTranslucent by mutableStateOf(false)
    internal var pipMinimized by mutableStateOf(true)
    internal var pipSnapCorner by mutableStateOf(PipCorner.TOP_END)
    internal var pipAutoGhosted by mutableStateOf(false)
    internal var pipCanvasTapHandler: ((LayoutCoordinates, Offset) -> Unit)? by mutableStateOf(null)
    internal var tweetModeEnabled by mutableStateOf(false)
    internal var tweetTargetLimit by mutableIntStateOf(140)
    internal var showMinifySheet by mutableStateOf(false)

    internal var suppressAutoRunOnce by mutableStateOf(false)
    private var liveParameterDragInitial: TextFieldValue? = null
    private var liveParameterBlockInitial: String? = null
    private var liveParameterSubFileInitial: Pair<String, String>? = null

    internal fun applyLiveParameterToEditor(parameter: WorkParameter, newValue: String) {
        suppressAutoRunOnce = true
        val targetFile = parameter.sourceFile
        if (targetFile == "sketch.js" || targetFile.isBlank()) {
            if (liveParameterDragInitial == null) {
                liveParameterDragInitial = editorValueState.value
                liveParameterBlockInitial = currentBlockEditorState(activeWorkIdState.value)
            }
            val current = editorValueState.value
            val updatedText = updateParameterInSource(current.text, parameter, newValue) ?: return
            if (updatedText != current.text) {
                val updatedValue = current.copy(text = updatedText)
                editorValueState.value = updatedValue
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
                undoStack.add(historyValue(initial, liveParameterBlockInitial))
                clearMainRedoHistory()
                trimMainHistory()
                lastEditPosition = -1
                lastEditTimestamp = 0L
            }
        }
        liveParameterBlockInitial = null
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
        blockModes.keys.retainAll(retainedIds)
        blockEditorViewports.keys.retainAll(retainedIds)
        blockDocumentRevisions.keys.retainAll(retainedIds)
        blockEditorActivationGeneration++
        clearBlockInputDraft()
        invalidateBlockDocumentRevision(workId)
        blockEditorDrafts.keys.retainAll(retainedIds)
        blockEditorValues.keys.retainAll(retainedIds)
        if (!skipCurrentEditorCapture && previousId != workId && previousId in retainedIds) {
            mainEditorPositions[previousId] = editorValueState.value.selection
            mainEditorResumes.remove(previousId)
            mainEditorResumes[previousId] = MainEditorResume(editorValueState.value.copy(composition = null),
                lastSavedTextState.value, undoStack.toList(), redoStack.toList(), currentBlockEditorState(previousId),
                undoStack.map { if (mainHistoryBlockStates.containsKey(it)) mainHistoryBlockStates[it] else currentBlockEditorState(previousId) },
                redoStack.map { if (mainHistoryBlockStates.containsKey(it)) mainHistoryBlockStates[it] else currentBlockEditorState(previousId) })
        }
        val cached = mainEditorResumes.remove(workId)?.takeIf { resume && it.savedText == code && it.value.text == code &&
            it.blockEditorState == currentBlockEditorState(workId) }
        skipCurrentEditorCapture = false
        activeWorkIdState.value = workId
        lastSavedTextState.value = code
        historyWorkId = workId
        val position = if (resume) mainEditorPositions[workId]?.let {
            androidx.compose.ui.text.TextRange(it.start.coerceIn(0, code.length), it.end.coerceIn(0, code.length))
        } else null
        val initialVal = cached?.value ?: TextFieldValue(code, position ?: androidx.compose.ui.text.TextRange.Zero)
        editorValueState.value = initialVal
        if (!resume) mainEditorPositions.remove(workId)
        clearEditHistory()
        cached?.let { saved ->
            undoStack.addAll(saved.undo); redoStack.addAll(saved.redo)
            saved.undo.zip(saved.undoBlockStates).forEach { (value, state) -> mainHistoryBlockStates[value] = state }
            saved.redo.zip(saved.redoBlockStates).forEach { (value, state) -> mainHistoryBlockStates[value] = state }
        }
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
        if (workId == null || workId == activeWorkIdState.value) {
            clearBlockInputDraft()
            skipCurrentEditorCapture = true
            blockEditorActivationGeneration++
        }
        if (workId == null) blockDocumentRevisions.clear() else invalidateBlockDocumentRevision(workId)
        if (workId == null) {
            mainEditorResumes.clear(); mainEditorPositions.clear(); selectedFiles.clear()
            blockModes.clear(); blockEditorDrafts.clear(); blockEditorValues.clear()
        } else {
            mainEditorResumes.remove(workId); mainEditorPositions.remove(workId); selectedFiles.remove(workId)
            blockModes.remove(workId); blockEditorDrafts.remove(workId); blockEditorValues.remove(workId)
        }
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
    private val mainEditorValue = mutableStateOf(TextFieldValue(""))
    val editorValueState: androidx.compose.runtime.MutableState<TextFieldValue> = object : androidx.compose.runtime.MutableState<TextFieldValue> {
        override var value: TextFieldValue
            get() = mainEditorValue.value
            set(next) {
                if (mainEditorValue.value.text != next.text) invalidateBlockDocumentRevision(activeWorkIdState.value)
                mainEditorValue.value = next
            }
        override fun component1(): TextFieldValue = value
        override fun component2(): (TextFieldValue) -> Unit = { value = it }
    }
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
        initialized = true
    }

    private var lastEditPosition: Int = -1
    private var lastEditTimestamp: Long = 0L

    fun clearEditHistory() {
        liveParameterDragInitial = null
        liveParameterBlockInitial = null
        liveParameterSubFileInitial = null
        undoStack.clear()
        mainHistoryBlockStates.clear()
        redoStack.clear()
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
                invalidateBlockDocumentRevision(activeWorkIdState.value)
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
            // A monotonic JVM clock also keeps editor actions testable without Android stubs.
            val now = System.nanoTime() / 1_000_000L
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
                targetUndoStack.add(if (targetUndoStack === undoStack) historyValue(currentValue) else currentValue.copy(composition = null))
            }
            lastEditPosition = change.start
            lastEditTimestamp = now
            if (targetUndoStack === undoStack) {
                trimMainHistory()
            } else {
                while (targetUndoStack.size > 100 || (targetUndoStack.size > 1 && targetUndoStack.sumOf { it.text.length.toLong() } > 2_000_000)) {
                    targetUndoStack.removeAt(0)
                }
            }
            if (targetRedoStack === redoStack) clearMainRedoHistory() else targetRedoStack.clear()
        }
    }

    fun undo(
        currentValue: TextFieldValue,
        targetUndoStack: androidx.compose.runtime.snapshots.SnapshotStateList<TextFieldValue> = undoStack,
        targetRedoStack: androidx.compose.runtime.snapshots.SnapshotStateList<TextFieldValue> = redoStack
    ): TextFieldValue? {
        if (targetUndoStack.isEmpty()) return null
        val main = targetUndoStack === undoStack
        targetRedoStack.add(if (main) historyValue(currentValue) else currentValue.copy(composition = null))
        return targetUndoStack.removeAt(targetUndoStack.lastIndex).also {
            if (main) {
                invalidateBlockDocumentRevision(activeWorkIdState.value)
                if (mainHistoryBlockStates.containsKey(it)) setBlockEditorState(activeWorkIdState.value, mainHistoryBlockStates.remove(it))
                lastEditPosition = -1; lastEditTimestamp = 0L
            }
        }
    }

    fun redo(
        currentValue: TextFieldValue,
        targetUndoStack: androidx.compose.runtime.snapshots.SnapshotStateList<TextFieldValue> = undoStack,
        targetRedoStack: androidx.compose.runtime.snapshots.SnapshotStateList<TextFieldValue> = redoStack
    ): TextFieldValue? {
        if (targetRedoStack.isEmpty()) return null
        val main = targetUndoStack === undoStack
        targetUndoStack.add(if (main) historyValue(currentValue) else currentValue.copy(composition = null))
        return targetRedoStack.removeAt(targetRedoStack.lastIndex).also {
            if (main) {
                invalidateBlockDocumentRevision(activeWorkIdState.value)
                if (mainHistoryBlockStates.containsKey(it)) setBlockEditorState(activeWorkIdState.value, mainHistoryBlockStates.remove(it))
                lastEditPosition = -1; lastEditTimestamp = 0L
            }
        }
    }
}
