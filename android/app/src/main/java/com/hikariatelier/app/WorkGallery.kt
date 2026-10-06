package com.hikariatelier.app

import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import java.util.Collections
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONTokener

/** Disposable UI cache; never part of works.json or exported work data. */
internal fun workPreviewFile(cacheDir: File, id: String): File {
    val key = MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
        .joinToString("") { "%02x".format(it) }
    return File(cacheDir, "work-previews/$key.png")
}

internal fun captureWorkPreview(view: WebView, onResult: (String) -> Unit) {
    if (previewCaptures.put(view, true) != null) return
    // Copy the canvas without resizing it or changing its drawing/animation state.
    view.evaluateJavascript("""
        (() => {
            try {
                const source = document.querySelector('canvas.p5Canvas') || document.querySelector('canvas');
                if (!source || !source.width || !source.height) return null;
                const scale = Math.min(1, 480 / Math.max(source.width, source.height));
                const copy = document.createElement('canvas');
                copy.width = Math.max(1, Math.round(source.width * scale));
                copy.height = Math.max(1, Math.round(source.height * scale));
                copy.getContext('2d').drawImage(source, 0, 0, copy.width, copy.height);
                return copy.toDataURL('image/png');
            } catch (_) { return null; }
        })()
    """.trimIndent()) { result ->
        previewCaptures.remove(view)
        val data = runCatching { JSONTokener(result).nextValue() as? String }.getOrNull()
        if (data != null && data.startsWith("data:image/png;base64,")) onResult(data.substringAfter(','))
    }
}

private val previewCaptures = java.util.WeakHashMap<WebView, Boolean>()
private val previewWriteMutex = Mutex()
private val previewBitmaps = object : android.util.LruCache<String, android.graphics.Bitmap>(12 * 1024 * 1024) {
    override fun sizeOf(key: String, value: android.graphics.Bitmap) = value.allocationByteCount
}

internal suspend fun storeWorkPreview(file: File, encoded: String): Boolean = withContext(Dispatchers.IO) {
    previewWriteMutex.withLock { runCatching {
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        file.parentFile?.mkdirs()
        val atomic = android.util.AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
            previewBitmaps.remove(file.path)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
        // Only disposable thumbnails in this dedicated directory are eligible.
        val files = file.parentFile?.listFiles { candidate -> candidate.name.matches(Regex("[a-f0-9]{64}\\.png")) }
            .orEmpty().sortedByDescending { it.lastModified() }
        var total = 0L
        files.forEachIndexed { index, candidate ->
            total += candidate.length()
            if (index >= 120 || total > 32L * 1024 * 1024) {
                candidate.delete()
                previewBitmaps.remove(candidate.path)
            }
        }
    }.isSuccess }
}

/** Interaction state stays with the workspace while either orientation changes surfaces. */
@Stable
internal class WorkGalleryState internal constructor(
    searchingState: MutableState<Boolean>,
    queryState: MutableState<String>,
    selectedTagState: MutableState<String?>,
    val gridState: LazyGridState,
    selectingState: MutableState<Boolean>,
    selectedIdsState: MutableState<Set<String>>,
    selectedFolderState: MutableState<String?> = mutableStateOf(null)
) {
    var searching by searchingState
    var query by queryState
    var selectedTag by selectedTagState
    var selecting by selectingState
    var selectedIds by selectedIdsState
    var selectedFolder by selectedFolderState
    fun inFolder(work: Work): Boolean = if (selectedFolder == SAMPLE_FOLDER) work.isSample
        else !work.isSample && (selectedFolder == null || work.folderName == selectedFolder)
    fun changeFolder(folder: String?) {
        selectedFolder = folder; selectedTag = null; cardMenuWorkId = null; finishSelection()
    }
    var jumpWorkId by mutableStateOf<String?>(null)
    var jumpSequence by mutableIntStateOf(0)

    fun startSelection(id: String) {
        selecting = true
        selectedIds = setOf(id)
        cardMenuWorkId = null
    }
    fun toggleSelected(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }
    fun finishSelection() { selecting = false; selectedIds = emptySet() }
    fun jumpToWork(id: String) {
        closeSearch()
        selectedTag = null
        jumpWorkId = id
        jumpSequence++
    }

    var sorting by mutableStateOf(false)
    var cardMenuWorkId by mutableStateOf<String?>(null)
    var showManageAllTagsDialog by mutableStateOf(false)

    fun closeSearch() {
        searching = false
        query = ""
    }
}

@Composable
internal fun rememberWorkGalleryState(): WorkGalleryState {
    val searching = rememberSaveable { mutableStateOf(false) }
    val query = rememberSaveable { mutableStateOf("") }
    val selectedTag = rememberSaveable { mutableStateOf<String?>(null) }
    val grid = rememberLazyGridState()
    val selecting = rememberSaveable { mutableStateOf(false) }
    val selectedIds = rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    val selectedFolder = rememberSaveable { mutableStateOf<String?>(null) }
    return remember { WorkGalleryState(searching, query, selectedTag, grid, selecting, selectedIds, selectedFolder) }
}

/** Shared actions with compact portrait and larger landscape touch targets. */
@Composable
internal fun WorkGalleryActions(
    state: WorkGalleryState,
    onDismiss: () -> Unit,
    text: (String) -> String,
    modifier: Modifier = Modifier,
    sort: String = "更新順",
    onSort: (String) -> Unit = {},
    buttonSize: Dp = 38.dp,
    busy: Boolean = false,
    canDeleteSelection: Boolean = true,
    onTagSelection: () -> Unit = {},
    onExportSelection: () -> Unit = {},
    onDeleteSelection: () -> Unit = {},
    onMoveSelection: () -> Unit = {}
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.selecting) {
            val enabled = state.selectedIds.isNotEmpty() && !busy
            IconButton(onClick = onMoveSelection, enabled = enabled, modifier = Modifier.size(buttonSize)) {
                Icon(painterResource(R.drawable.ic_folder_code), text("フォルダーへ移動"), Modifier.size(20.dp))
            }
            IconButton(onClick = onTagSelection, enabled = enabled, modifier = Modifier.size(buttonSize)) {
                Icon(painterResource(R.drawable.ic_tag), text("選択した作品にタグ付け"), Modifier.size(20.dp))
            }
            IconButton(onClick = onExportSelection, enabled = enabled, modifier = Modifier.size(buttonSize)) {
                Icon(painterResource(R.drawable.ic_save), text("選択した作品を書き出す"), Modifier.size(20.dp))
            }
            IconButton(onClick = onDeleteSelection, enabled = enabled && canDeleteSelection,
                modifier = Modifier.size(buttonSize)) {
                Icon(painterResource(R.drawable.ic_delete), text("選択した作品を削除"), Modifier.size(20.dp))
            }
            IconButton(onClick = { state.finishSelection() }, enabled = !busy, modifier = Modifier.size(buttonSize)) {
                Icon(painterResource(R.drawable.ic_close), text("選択を終了"), Modifier.size(20.dp))
            }
        } else WorkGalleryActionButtons(state, onDismiss, text, sort, onSort, Modifier.size(buttonSize))
    }
}

@Composable
private fun WorkGalleryActionButtons(
    state: WorkGalleryState,
    onDismiss: () -> Unit,
    text: (String) -> String,
    sort: String,
    onSort: (String) -> Unit,
    buttonModifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    IconButton(onClick = { state.showManageAllTagsDialog = true }, modifier = buttonModifier) {
        Icon(painterResource(R.drawable.ic_tag), text("タグの管理"), Modifier.size(20.dp))
    }
    IconButton(onClick = {
        if (state.searching) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            state.closeSearch()
        } else state.searching = true
    },
        modifier = buttonModifier) {
        Icon(painterResource(R.drawable.ic_search), text("作品名で検索"), Modifier.size(20.dp))
    }
    Box {
        IconButton(onClick = { state.sorting = true }, modifier = buttonModifier) {
            Icon(painterResource(R.drawable.ic_sort),
                text("並び替え"), Modifier.size(20.dp))
        }
        DropdownMenu(expanded = state.sorting, onDismissRequest = { state.sorting = false }) {
            listOf("更新順", "最近開いた順", "名前順").forEach { value ->
                DropdownMenuItem(text = { Text(text(value)) },
                    modifier = Modifier.semantics { selected = sort == value },
                    trailingIcon = { if (sort == value) Text("✓") },
                    onClick = { onSort(value); state.sorting = false })
            }
        }
    }
    IconButton(onClick = onDismiss, modifier = buttonModifier) {
        Icon(painterResource(R.drawable.ic_close), text("閉じる"), Modifier.size(20.dp))
    }
}

/** Search shares focus/IME handling, whether it sits in the body or the landscape header. */
@Composable
internal fun WorkGallerySearch(
    state: WorkGalleryState,
    text: (String) -> String,
    interactive: Boolean,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(interactive) {
        if (interactive) {
            withFrameNanos { }
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    TextField(value = state.query, onValueChange = { state.query = it }, singleLine = true,
        modifier = modifier.focusRequester(focusRequester),
        placeholder = { Text(text("作品名で検索"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }),
        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent),
        trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = { state.query = "" }) {
            Icon(painterResource(R.drawable.ic_close), text("検索をクリア"), Modifier.size(18.dp))
        } })
}

/** Gallery body; actions live in the workspace toolbar for each orientation. */
@Composable
internal fun InlineWorkGallery(
    works: List<Work>, activeId: String, unsaved: Boolean,
    sort: String, cacheDir: File, previewRevision: Int,
    updatedPreviewId: String?,
    text: (String) -> String,
    onOpen: (Work, Boolean) -> Unit, onAdd: () -> Unit,
    state: WorkGalleryState,
    modifier: Modifier = Modifier,
    onTogglePin: ((Work) -> Unit)? = null,
    onEditTags: ((Work) -> Unit)? = null,
    onDeleteGlobalTag: ((String) -> Unit)? = null,
    busy: Boolean = false,
    onRename: (Work) -> Unit = {},
    onDuplicate: (Work) -> Unit = {},
    onDelete: (Work) -> Unit = {},
    onActiveThumbnailBounds: (Rect?) -> Unit = {},
    morphWorkId: String? = activeId,
    suppressMorphThumbnail: Boolean = false,
    morphProgress: Float = 0f,
    folders: List<String> = emptyList(),
    tabs: List<String> = emptyList(),
    onManageFolders: () -> Unit = {},
    onMove: (Work) -> Unit = {},
    onReorderTabs: (List<String>) -> Unit = {}
) {
    WorkGalleryContent(works, activeId, unsaved, sort, cacheDir, previewRevision, updatedPreviewId,
        text, onOpen, onAdd, state, modifier, MaterialTheme.colorScheme.background, onTogglePin,
        onEditTags, onDeleteGlobalTag, busy, onRename, onDuplicate, onDelete,
        morphWorkId, onActiveThumbnailBounds, suppressMorphThumbnail, morphProgress, folders, tabs, onManageFolders, onMove, onReorderTabs)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkGalleryContent(
    works: List<Work>, activeId: String, unsaved: Boolean,
    sort: String, cacheDir: File, previewRevision: Int,
    updatedPreviewId: String?,
    text: (String) -> String,
    onOpen: (Work, Boolean) -> Unit, onAdd: () -> Unit,
    state: WorkGalleryState,
    modifier: Modifier,
    background: Color,
    onTogglePin: ((Work) -> Unit)?,
    onEditTags: ((Work) -> Unit)?,
    onDeleteGlobalTag: ((String) -> Unit)?,
    busy: Boolean,
    onRename: (Work) -> Unit,
    onDuplicate: (Work) -> Unit,
    onDelete: (Work) -> Unit,
    morphWorkId: String?,
    onActiveThumbnailBounds: (Rect?) -> Unit,
    suppressMorphThumbnail: Boolean = false,
    morphProgress: Float = 0f,
    folders: List<String> = emptyList(),
    tabs: List<String> = emptyList(),
    onManageFolders: () -> Unit = {},
    onMove: (Work) -> Unit = {},
    onReorderTabs: (List<String>) -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val interactive = morphProgress >= 1f

    fun finishSearchInput() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    BackHandler(enabled = interactive && state.searching && !keyboardVisible) {
        finishSearchInput()
        state.closeSearch()
    }
    BackHandler(enabled = interactive && state.selecting && !keyboardVisible) {
        if (!busy) state.finishSelection()
    }
    LaunchedEffect(works) {
        state.selectedIds = state.selectedIds.intersect(works.filterNot { it.isSample }.map { it.id }.toSet())
    }
    val currentBoundsCallback by rememberUpdatedState(onActiveThumbnailBounds)
    LaunchedEffect(state.selectedFolder, folders) {
        if (state.selectedFolder != null && state.selectedFolder != SAMPLE_FOLDER &&
            state.selectedFolder != "" && state.selectedFolder !in folders) state.changeFolder(null)
        state.gridState.scrollToItem(0)
    }
    val allTags = remember(works, state.selectedFolder) {
        works.filter(state::inFolder).flatMap { it.tags }.distinct().sorted()
    }

    val galleryContext = androidx.compose.ui.platform.LocalContext.current
    val recentRevision = RecentWorks.revision
    val visibleWorks = remember(works, sort, state.query, state.selectedTag, state.selectedFolder, recentRevision) {
        val trimmed = state.query.trim()
        val scoped = works.filter(state::inFolder)
        val byTag = if (state.selectedTag != null) {
            scoped.filter { work -> work.tags.any { it.equals(state.selectedTag, ignoreCase = true) } }
        } else scoped
        val filtered = if (trimmed.isEmpty()) byTag else byTag.filter { work ->
            work.title.contains(trimmed, ignoreCase = true) ||
            work.tags.any { it.contains(trimmed.removePrefix("#"), ignoreCase = true) }
        }
        val baseComparator: Comparator<Work> = if (sort == "名前順") {
            Comparator { a, b -> a.title.lowercase().compareTo(b.title.lowercase()) }
        } else if (sort == "最近開いた順") {
            Comparator { a, b ->
                val opened = RecentWorks.lastOpened(galleryContext, b.id).compareTo(RecentWorks.lastOpened(galleryContext, a.id))
                if (opened != 0) opened else b.updatedAt.compareTo(a.updatedAt)
            }
        } else {
            Comparator { a, b -> b.updatedAt.compareTo(a.updatedAt) }
        }
        filtered.sortedWith(Comparator { a, b ->
            if (a.isPinned != b.isPinned) {
                if (a.isPinned) -1 else 1
            } else {
                baseComparator.compare(a, b)
            }
        })
    }

    LaunchedEffect(state.jumpSequence, visibleWorks) {
        val index = visibleWorks.indexOfFirst { it.id == state.jumpWorkId }
        if (state.jumpSequence > 0 && index >= 0) {
            state.gridState.animateScrollToItem(index)
            state.jumpWorkId = null
        }
    }
    LaunchedEffect(allTags, state.selectedTag) {
        val selected = state.selectedTag
        if (selected != null && allTags.none { it.equals(selected, ignoreCase = true) }) state.selectedTag = null
    }
    if (morphWorkId != null) {
        DisposableEffect(morphWorkId, state) {
            onDispose { currentBoundsCallback(null) }
        }
        LaunchedEffect(morphWorkId, state.gridState) {
            snapshotFlow { state.gridState.layoutInfo.visibleItemsInfo.any { it.key == morphWorkId } }
                .collect { visible -> if (!visible) currentBoundsCallback(null) }
        }
    }
    Column(modifier) {
        val density = LocalDensity.current
        val haptic = LocalHapticFeedback.current
        val effectiveTabs = remember(tabs, folders) {
            if (tabs.isNotEmpty()) tabs else (listOf(SAMPLE_FOLDER) + (if (folders.isNotEmpty()) listOf("") + folders else emptyList()))
        }
        var currentTabs by remember(effectiveTabs) { mutableStateOf(effectiveTabs) }
        var draggingTab by remember { mutableStateOf<String?>(null) }
        var dragOffset by remember { mutableFloatStateOf(0f) }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            fun choose(folder: String?) { finishSearchInput(); state.changeFolder(folder) }
            FilterChip(selected = state.selectedFolder == null, enabled = !busy,
                onClick = { choose(null) }, label = { Text(text("自分の作品")) })

            currentTabs.forEach { tab ->
                val isDragging = draggingTab == tab
                val chipModifier = Modifier
                    .graphicsLayer {
                        if (isDragging) {
                            translationX = dragOffset
                            scaleX = 1.08f
                            scaleY = 1.08f
                            shadowElevation = 8f
                        }
                    }
                    .zIndex(if (isDragging) 1f else 0f)
                    .pointerInput(tab, currentTabs, busy) {
                        if (!busy) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingTab = tab
                                    dragOffset = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.x
                                    val threshold = with(density) { 54.dp.toPx() }
                                    val currentIndex = currentTabs.indexOf(tab)
                                    if (currentIndex != -1) {
                                        if (dragOffset > threshold && currentIndex < currentTabs.lastIndex) {
                                            val next = currentTabs.toMutableList()
                                            Collections.swap(next, currentIndex, currentIndex + 1)
                                            currentTabs = next
                                            dragOffset -= threshold
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        } else if (dragOffset < -threshold && currentIndex > 0) {
                                            val next = currentTabs.toMutableList()
                                            Collections.swap(next, currentIndex, currentIndex - 1)
                                            currentTabs = next
                                            dragOffset += threshold
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    draggingTab = null
                                    dragOffset = 0f
                                    if (currentTabs != effectiveTabs) {
                                        onReorderTabs(currentTabs)
                                    }
                                },
                                onDragCancel = {
                                    draggingTab = null
                                    dragOffset = 0f
                                    if (currentTabs != effectiveTabs) {
                                        onReorderTabs(currentTabs)
                                    }
                                }
                            )
                        }
                    }

                val labelText = when (tab) {
                    SAMPLE_FOLDER -> text("サンプル・閲覧専用")
                    "" -> text("未分類")
                    else -> tab
                }
                FilterChip(
                    selected = state.selectedFolder == tab,
                    enabled = !busy,
                    onClick = { choose(tab) },
                    label = { Text(labelText) },
                    modifier = chipModifier
                )
            }

            IconButton(onClick = onManageFolders, enabled = !busy) {
                Icon(painterResource(R.drawable.ic_folder_code), text("フォルダー管理"), Modifier.size(20.dp))
            }
        }
        if (state.searching && !landscape) {
            WorkGallerySearch(state, text, interactive,
                Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
        if (allTags.isNotEmpty() && !(landscape && keyboardVisible)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = state.selectedTag == null,
                    onClick = { state.selectedTag = null },
                    label = { Text(text("すべて"), fontSize = 12.sp) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.surfaceContainerHighest,
                        selectedLabelColor = colors.onSurface,
                        labelColor = colors.onSurfaceVariant
                    )
                )
                allTags.forEach { tag ->
                    FilterChip(
                        selected = state.selectedTag == tag,
                        onClick = { state.selectedTag = if (state.selectedTag == tag) null else tag },
                        label = { Text("#$tag", fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.surfaceContainerHighest,
                            selectedLabelColor = colors.onSurface,
                            labelColor = colors.onSurfaceVariant
                        )
                    )
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val columnCount = galleryColumnCount(maxWidth.value, configuration.fontScale, landscape)
            val cardWidth = ((maxWidth - 32.dp - 14.dp * (columnCount - 1)) / columnCount).coerceAtLeast(1.dp)
            // Leave a readable title/menu row even on a short viewport with the IME open.
            val landscapeThumbnailHeight = minOf(cardWidth,
                (maxHeight - 96.dp).coerceIn(48.dp, if (keyboardVisible) 88.dp else 180.dp))
            if (visibleWorks.isEmpty()) {
                Text(text("一致する作品がありません"), Modifier.align(Alignment.Center).padding(24.dp),
                    color = colors.onSurfaceVariant)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                state = state.gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(if (landscape) 16.dp else 20.dp)) {
                items(visibleWorks, key = { it.id }, contentType = { "work_card" }) { work ->
                    val isSelected = if (state.selecting) work.id in state.selectedIds else work.id == activeId
                    val bitmap by produceState<android.graphics.Bitmap?>(null, work.id,
                        if (work.id == updatedPreviewId) previewRevision else 0) {
                        value = withContext(Dispatchers.IO) {
                            ensureActive()
                            val file = workPreviewFile(cacheDir, work.id)
                            previewBitmaps.get(file.path) ?: runCatching {
                                val decoded = BitmapFactory.decodeFile(file.path)
                                ensureActive()
                                decoded?.also { previewBitmaps.put(file.path, it) }
                            }.getOrElse {
                                if (it is kotlinx.coroutines.CancellationException) throw it
                                null
                            }
                        }
                    }
                    Column(Modifier.animateItem().clip(RoundedCornerShape(6.dp))
                        .combinedClickable(
                            onClick = { if (!busy) { if (state.selecting) state.toggleSelected(work.id) else onOpen(work, false) } },
                            onLongClickLabel = text("作品メニュー"),
                            onLongClick = { if (!busy) { if (state.selecting) state.toggleSelected(work.id) else state.cardMenuWorkId = work.id } }
                        )
                        .semantics { selected = isSelected }) {
                        Box(Modifier.fillMaxWidth()
                            .then(if (landscape) Modifier.height(landscapeThumbnailHeight) else Modifier.aspectRatio(1f))
                            .graphicsLayer {
                                alpha = if (suppressMorphThumbnail && work.id == morphWorkId)
                                    (1f - (1f - morphProgress) / GALLERY_EDGE_FADE).coerceIn(0f, 1f)
                                else 1f
                            }
                            .then(if (work.id == morphWorkId) Modifier.onGloballyPositioned { coordinates ->
                                val visible = state.gridState.layoutInfo.visibleItemsInfo.any { it.key == work.id }
                                val bounds = coordinates.boundsInRoot()
                                // A clipped card is a fade destination, not a smaller morph destination.
                                val fullyVisible = bounds.width >= coordinates.size.width - 1f &&
                                    bounds.height >= coordinates.size.height - 1f
                                currentBoundsCallback(if (visible && fullyVisible && bounds.width > 0f &&
                                    bounds.height > 0f) bounds else null)
                            } else Modifier)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (colors.surface.luminance() < 0.5f) Color(0xFF17171B) else colors.surfaceContainer)
                            .then(if (isSelected) Modifier.border(1.dp, colors.primary.copy(alpha = 0.65f),
                                RoundedCornerShape(6.dp)) else Modifier), contentAlignment = Alignment.Center) {
                            bitmap?.let {
                                Image(it.asImageBitmap(), text("作品プレビュー"),
                                    Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            } ?: Text(text("プレビュー未作成"), fontSize = 11.sp,
                                color = colors.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                            if (state.selecting) {
                                Checkbox(checked = isSelected,
                                    onCheckedChange = { if (!busy) state.toggleSelected(work.id) },
                                    enabled = !busy,
                                    modifier = Modifier.align(Alignment.TopStart).size(48.dp)
                                        .semantics { contentDescription = work.title })
                            }
                            if (work.isPinned && !state.selecting) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(40.dp)
                                        .then(if (onTogglePin != null) Modifier.clickable(enabled = !busy) {
                                            if (state.selecting) state.toggleSelected(work.id) else onTogglePin(work)
                                        } else Modifier)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painterResource(R.drawable.ic_pin_filled),
                                            text("ピン留め済み"),
                                            tint = colors.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                work.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Box {
                                IconButton(
                                    onClick = { state.cardMenuWorkId = work.id },
                                    enabled = !busy && !state.selecting,
                                    modifier = Modifier.size(if (landscape) 48.dp else 40.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_more_vertical),
                                        contentDescription = text("作品メニュー"),
                                        modifier = Modifier.size(15.dp),
                                        tint = colors.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                DropdownMenu(
                                    expanded = state.cardMenuWorkId == work.id,
                                    onDismissRequest = { state.cardMenuWorkId = null }
                                ) {
                                    Text(workGalleryDetails(work),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        color = colors.onSurfaceVariant, fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace)
                                    HorizontalDivider()
                                    if (work.isSample) {
                                        DropdownMenuItem(text = { Text(text("コピーして編集")) },
                                            leadingIcon = { Icon(painterResource(R.drawable.ic_duplicate), null, Modifier.size(18.dp)) },
                                            onClick = { state.cardMenuWorkId = null; onDuplicate(work) })
                                    } else {
                                    DropdownMenuItem(text = { Text(text("選択")) },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_select), null, Modifier.size(18.dp)) },
                                        onClick = { finishSearchInput(); state.startSelection(work.id) })
                                    DropdownMenuItem(text = { Text(text("名前変更")) },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_rename), null, Modifier.size(18.dp)) },
                                        onClick = { finishSearchInput(); state.cardMenuWorkId = null; onRename(work) })
                                    DropdownMenuItem(text = { Text(text("複製")) },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_duplicate), null, Modifier.size(18.dp)) },
                                        onClick = { state.cardMenuWorkId = null; onDuplicate(work) })
                                    DropdownMenuItem(text = { Text(text("削除")) }, enabled = works.size > 1,
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_delete), null, Modifier.size(18.dp)) },
                                        onClick = { finishSearchInput(); state.cardMenuWorkId = null; onDelete(work) })
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text(if (work.isPinned) text("ピン留め解除") else text("ピン留め")) },
                                        leadingIcon = {
                                            Icon(
                                                painterResource(if (work.isPinned) R.drawable.ic_pin_filled else R.drawable.ic_pin),
                                                null,
                                                tint = colors.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            state.cardMenuWorkId = null
                                            onTogglePin?.invoke(work)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(text("タグを編集")) },
                                        leadingIcon = {
                                            Icon(
                                                painterResource(R.drawable.ic_tag),
                                                null,
                                                tint = colors.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            state.cardMenuWorkId = null
                                            onEditTags?.invoke(work)
                                        }
                                    )
                                    DropdownMenuItem(text = { Text(text("フォルダーへ移動")) },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_folder_code), null, Modifier.size(18.dp)) },
                                        onClick = { state.cardMenuWorkId = null; onMove(work) })
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text(text("開く")) },
                                        leadingIcon = {
                                            Icon(
                                                painterResource(R.drawable.ic_code),
                                                null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            state.cardMenuWorkId = null
                                            onOpen(work, false)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(text("作品メニュー")) },
                                        leadingIcon = {
                                            Icon(
                                                painterResource(R.drawable.ic_settings),
                                                null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            state.cardMenuWorkId = null
                                            onOpen(work, true)
                                        }
                                    )
                                }
                            }
                        }
                        if (work.tags.isNotEmpty() && !(landscape && keyboardVisible)) {
                            Text(
                                text = work.tags.joinToString(" ") { "#$it" },
                                color = colors.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(enabled = !busy && !state.selecting) { onEditTags?.invoke(work) }
                                    .padding(vertical = 8.dp)
                            )
                        }
                        if (work.id == activeId && unsaved) Text(text("未保存の変更あり"),
                            color = colors.onSurfaceVariant, fontSize = 10.sp)
                    }
                }
            }
            if (!state.selecting) Surface(onClick = onAdd, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(48.dp),
                shape = RoundedCornerShape(14.dp), color = colors.onSurface, contentColor = background,
                border = BorderStroke(1.dp, colors.outlineVariant)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_add), text("作品を追加"), Modifier.size(24.dp))
                }
            }
        }
    }
    if (state.showManageAllTagsDialog) {
        val tagCounts = remember(works) {
            works.flatMap { it.tags }.groupingBy { it }.eachCount()
        }
        AllTagsManageDialog(
            tagCounts = tagCounts,
            text = text,
            onDeleteTag = { tag ->
                onDeleteGlobalTag?.invoke(tag)
            },
            onDismiss = { state.showManageAllTagsDialog = false }
        )
    }
}

/** Keep technical details in the card menu so titles remain easy to scan. */
private fun workGalleryDetails(work: Work): String = buildList {
    add(if (work.p5Version == P5_VERSION_LEGACY) "1.x" else "2.x")
    add("${1 + work.files.keys.count { it.endsWith(".js", ignoreCase = true) || it.endsWith(".mjs", ignoreCase = true) }} JS")
    val shaderCount = work.files.keys.count {
        when (it.substringAfterLast('.').lowercase(java.util.Locale.ROOT)) {
            "vert", "frag", "glsl", "wgsl" -> true
            else -> false
        }
    }
    if (shaderCount > 0) add("$shaderCount SHADER")
    if (work.p5SoundEnabled) add("SOUND")
    if (work.libraries.containsKey("matter-js")) add("PHYSICS")
}.joinToString(" · ")
