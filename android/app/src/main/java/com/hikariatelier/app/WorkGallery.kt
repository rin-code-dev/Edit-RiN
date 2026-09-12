package com.hikariatelier.app

import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.contentDescription
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
import org.json.JSONTokener

/** Disposable UI cache; never part of works.json or exported work data. */
internal fun workPreviewFile(cacheDir: File, id: String): File {
    val key = MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
        .joinToString("") { "%02x".format(it) }
    return File(cacheDir, "work-previews/$key.png")
}

internal fun captureWorkPreview(view: WebView, onResult: (String) -> Unit) {
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
        val data = runCatching { JSONTokener(result).nextValue() as? String }.getOrNull()
        if (data != null && data.startsWith("data:image/png;base64,")) onResult(data.substringAfter(','))
    }
}

internal suspend fun storeWorkPreview(file: File, encoded: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        file.parentFile?.mkdirs()
        val atomic = android.util.AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
    }.isSuccess
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun WorkGallery(
    works: List<Work>, activeId: String, unsaved: Boolean,
    sort: String, favorites: Set<String>, cacheDir: File, previewRevision: Int,
    updatedPreviewId: String?,
    text: (String) -> String,
    onSort: (String) -> Unit, onFavorite: (String) -> Unit,
    onOpen: (Work, Boolean) -> Unit, onAdd: () -> Unit, onDismiss: () -> Unit,
    windowSetup: @Composable () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val background = if (colors.surface.luminance() < 0.5f) Color(0xFF0D0D10) else colors.surface
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sorting by remember { mutableStateOf(false) }
    val visibleWorks by remember(works, sort, favorites) { derivedStateOf {
        works.filter { it.title.contains(query.trim(), ignoreCase = true) }
        .let { list -> if (sort == "名前順") list.sortedBy { it.title.lowercase() } else list.sortedByDescending { it.updatedAt } }
        .sortedByDescending { it.id in favorites }
    } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (visibleWorks.isEmpty()) {
                    Text(text("一致する作品がありません"), Modifier.align(Alignment.Center).padding(24.dp),
                        color = colors.onSurfaceVariant)
                }
                LazyVerticalGrid(
                    columns = if (landscape) GridCells.Adaptive(168.dp) else GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(visibleWorks, key = { it.id }) { work ->
                        val isSelected = work.id == activeId
                        val bitmap by produceState<android.graphics.Bitmap?>(null, work.id,
                            if (work.id == updatedPreviewId) previewRevision else 0) {
                            value = withContext(Dispatchers.IO) {
                                runCatching { BitmapFactory.decodeFile(workPreviewFile(cacheDir, work.id).path) }.getOrNull()
                            }
                        }
                        Column(Modifier.clip(RoundedCornerShape(6.dp))
                            .combinedClickable(onClick = { onOpen(work, false) },
                                onLongClickLabel = text("作品メニュー"), onLongClick = { onOpen(work, true) })
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
                                IconToggleButton(checked = work.id in favorites,
                                    onCheckedChange = { onFavorite(work.id) },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)
                                        .semantics { contentDescription = text("お気に入り") }) {
                                    Surface(shape = RoundedCornerShape(20.dp), color = background.copy(alpha = 0.8f)) {
                                        Text(if (work.id in favorites) "★" else "☆",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = colors.onSurface, fontSize = 16.sp)
                                    }
                                }
                            }
                            Text(work.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp), fontSize = 14.sp)
                            val info = buildList {
                                add(if (work.p5Version == P5_VERSION_LEGACY) "1.x" else "2.x")
                                add("${1 + work.files.keys.count { it.endsWith(".js", ignoreCase = true) }} JS")
                                if (work.p5SoundEnabled) add("SOUND")
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
    }
}
