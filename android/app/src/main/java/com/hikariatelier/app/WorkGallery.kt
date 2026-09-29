package com.hikariatelier.app

import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.WebView
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.hikariatelier.app.ui.theme.LocalCustomTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun WorkGallery(
    works: List<Work>, activeId: String, unsaved: Boolean,
    sort: String, cacheDir: File, previewRevision: Int,
    updatedPreviewId: String?,
    text: (String) -> String,
    onSort: (String) -> Unit,
    onOpen: (Work, Boolean) -> Unit, onAdd: () -> Unit, onDismiss: () -> Unit,
    onTogglePin: ((Work) -> Unit)? = null,
    onEditTags: ((Work) -> Unit)? = null,
    onDeleteGlobalTag: ((String) -> Unit)? = null,
    windowSetup: @Composable () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val background = if (LocalCustomTheme.current) colors.background
        else if (colors.surface.luminance() < 0.5f) Color(0xFF0D0D10) else colors.surface
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sorting by remember { mutableStateOf(false) }
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var cardMenuWorkId by remember { mutableStateOf<String?>(null) }
    var showManageAllTagsDialog by remember { mutableStateOf(false) }

    val allTags = remember(works) {
        works.flatMap { it.tags }.distinct().sorted()
    }

    val visibleWorks = remember(works, sort, query, selectedTag) {
        val trimmed = query.trim()
        val byTag = if (selectedTag != null) {
            works.filter { work -> work.tags.any { it.equals(selectedTag, ignoreCase = true) } }
        } else works
        val filtered = if (trimmed.isEmpty()) byTag else byTag.filter { work ->
            work.title.contains(trimmed, ignoreCase = true) ||
            work.tags.any { it.contains(trimmed.removePrefix("#"), ignoreCase = true) }
        }
        val baseComparator: Comparator<Work> = if (sort == "名前順") {
            Comparator { a, b -> a.title.lowercase().compareTo(b.title.lowercase()) }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = false,
        containerColor = background, contentColor = colors.onSurface,
        tonalElevation = 0.dp, dragHandle = null,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        windowSetup()
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Works", fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Text(works.size.toString(), color = colors.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showManageAllTagsDialog = true }) {
                    Icon(painterResource(R.drawable.ic_tag), text("タグの管理"), Modifier.size(20.dp))
                }
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                    Icon(painterResource(R.drawable.ic_search), text("作品名で検索"), Modifier.size(20.dp))
                }
                Box {
                    IconButton(onClick = { sorting = true }) {
                        Icon(painterResource(R.drawable.ic_more_vertical), text("並び替え"), Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = sorting, onDismissRequest = { sorting = false }) {
                        listOf("更新順", "名前順").forEach { value ->
                            DropdownMenuItem(text = { Text(text(value)) },
                                modifier = Modifier.semantics { selected = sort == value },
                                trailingIcon = { if (sort == value) Text("✓") },
                                onClick = { onSort(value); sorting = false })
                        }
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(painterResource(R.drawable.ic_close), text("閉じる"), Modifier.size(20.dp))
                }
            }
            if (searching) {
                TextField(value = query, onValueChange = { query = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text(text("作品名で検索")) },
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent),
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Icon(painterResource(R.drawable.ic_close), text("検索をクリア"), Modifier.size(18.dp))
                    } })
            }
            if (allTags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedTag == null,
                        onClick = { selectedTag = null },
                        label = { Text(text("すべて"), fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.primaryContainer,
                            selectedLabelColor = colors.onPrimaryContainer
                        )
                    )
                    allTags.forEach { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { selectedTag = if (selectedTag == tag) null else tag },
                            label = { Text("#$tag", fontSize = 12.sp) },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primaryContainer,
                                selectedLabelColor = colors.onPrimaryContainer
                            )
                        )
                    }
                    AssistChip(
                        onClick = { showManageAllTagsDialog = true },
                        label = { Text(text("タグの管理"), fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(painterResource(R.drawable.ic_tag), null, Modifier.size(13.dp), tint = colors.primary)
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (visibleWorks.isEmpty()) {
                    Text(text("一致する作品がありません"), Modifier.align(Alignment.Center).padding(24.dp),
                        color = colors.onSurfaceVariant)
                }
                LazyVerticalGrid(
                    columns = if (landscape || configuration.fontScale > 1.3f)
                        GridCells.Adaptive(168.dp) else GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(visibleWorks, key = { it.id }, contentType = { "work_card" }) { work ->
                        val isSelected = work.id == activeId
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
                        Column(Modifier.clip(RoundedCornerShape(6.dp))
                            .combinedClickable(
                                onClick = { onOpen(work, false) },
                                onLongClickLabel = text("作品メニュー"),
                                onLongClick = { cardMenuWorkId = work.id }
                            )
                            .semantics { selected = isSelected }) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (colors.surface.luminance() < 0.5f) Color(0xFF17171B) else colors.surfaceContainer)
                                .then(if (isSelected) Modifier.border(1.dp, colors.primary.copy(alpha = 0.65f),
                                    RoundedCornerShape(6.dp)) else Modifier), contentAlignment = Alignment.Center) {
                                bitmap?.let {
                                    Image(it.asImageBitmap(), text("作品プレビュー"),
                                        Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                } ?: Text(text("プレビュー未作成"), fontSize = 11.sp,
                                    color = colors.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                                if (work.isPinned) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color.Black.copy(alpha = 0.65f),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(40.dp)
                                            .then(if (onTogglePin != null) Modifier.clickable { onTogglePin(work) } else Modifier)
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
                                        onClick = { cardMenuWorkId = work.id },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_more_vertical),
                                            contentDescription = text("作品メニュー"),
                                            modifier = Modifier.size(15.dp),
                                            tint = colors.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = cardMenuWorkId == work.id,
                                        onDismissRequest = { cardMenuWorkId = null }
                                    ) {
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
                                                cardMenuWorkId = null
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
                                                cardMenuWorkId = null
                                                onEditTags?.invoke(work)
                                            }
                                        )
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
                                                cardMenuWorkId = null
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
                                                cardMenuWorkId = null
                                                onOpen(work, true)
                                            }
                                        )
                                    }
                                }
                            }
                            if (work.tags.isNotEmpty()) {
                                Text(
                                    text = work.tags.joinToString(" ") { "#$it" },
                                    color = colors.primary.copy(alpha = 0.9f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { onEditTags?.invoke(work) }
                                        .padding(vertical = 8.dp)
                                )
                            } else {
                                Text(
                                    text = "+ " + text("タグを追加"),
                                    color = colors.onSurfaceVariant.copy(alpha = 0.45f),
                                    fontSize = 10.sp,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { onEditTags?.invoke(work) }
                                        .padding(vertical = 8.dp)
                                )
                            }
                            val info = buildList {
                                add(if (work.p5Version == P5_VERSION_LEGACY) "1.x" else "2.x")
                                add("${1 + work.files.keys.count { it.endsWith(".js", ignoreCase = true) }} JS")
                                val shaderCount = work.files.keys.count { !it.endsWith(".js", ignoreCase = true) }
                                if (shaderCount > 0) add("$shaderCount SHADER")
                                if (work.p5SoundEnabled) add("SOUND")
                                if (work.libraries.containsKey("matter-js")) add("PHYSICS")
                            }.joinToString(" · ")
                            Text(info, color = colors.onSurfaceVariant, fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace)
                            if (isSelected && unsaved) Text(text("未保存の変更あり"),
                                color = colors.onSurfaceVariant, fontSize = 10.sp)
                        }
                    }
                }
                Surface(onClick = onAdd, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(48.dp),
                    shape = RoundedCornerShape(14.dp), color = colors.onSurface, contentColor = background,
                    border = BorderStroke(1.dp, colors.outlineVariant)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_add), text("作品を追加"), Modifier.size(24.dp))
                    }
                }
            }
        }
        if (showManageAllTagsDialog) {
            val tagCounts = remember(works) {
                works.flatMap { it.tags }.groupingBy { it }.eachCount()
            }
            AllTagsManageDialog(
                tagCounts = tagCounts,
                text = text,
                onDeleteTag = { tag ->
                    onDeleteGlobalTag?.invoke(tag)
                },
                onDismiss = { showManageAllTagsDialog = false }
            )
        }
    }
}
