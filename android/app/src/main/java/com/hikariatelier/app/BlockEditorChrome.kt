package com.hikariatelier.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.util.UUID
import kotlin.math.round

internal data class BlockChromeState(
    val editable: Boolean = false,
    val busy: Boolean = true,
    val error: Boolean = false,
    val status: String = "",
    val dismissable: Boolean = false,
    val parts: Int = 0,
    val overlay: Boolean = false,
    val notice: Boolean = false,
    val palette: Boolean = false,
    val easy: Boolean = false
)

internal fun decodeBlockChromeState(value: JSONObject): BlockChromeState? {
    for (key in listOf("editable", "busy", "error", "dismissable", "overlay", "notice")) if (value.opt(key) !is Boolean) return null
    val status = value.opt("status") as? String ?: return null
    val count = value.opt("parts") as? Number ?: return null
    if (status.length > 2048 || count.toDouble() != count.toInt().toDouble() || count.toInt() !in 0..10000) return null
    val palette = value.optBoolean("palette", false)
    val easy = value.optBoolean("easy", false)
    return BlockChromeState(value.getBoolean("editable"), value.getBoolean("busy"), value.getBoolean("error"),
        status, value.getBoolean("dismissable"), count.toInt(), value.getBoolean("overlay"), value.getBoolean("notice"), palette, easy)
}

internal data class BlockSearchEntry(val id: String, val title: String, val detail: String)

/** Only view state. Search replies cannot edit source and must match the latest request and revision. */
internal class BlockEditorChromeModel {
    var state by mutableStateOf(BlockChromeState())
    var searchMode by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var entries by mutableStateOf<List<BlockSearchEntry>>(emptyList())
    var total by mutableStateOf(0)
    var searching by mutableStateOf(false)
    var showDisplay by mutableStateOf(false)
    var requestId: String? = null
        private set
    var resultRevision: String? = null
        private set

    fun openSearch(mode: String) {
        searchMode = mode; query = ""; invalidateSearch()
    }
    fun switchSearchMode(mode: String) {
        if (searchMode == mode) return
        searchMode = mode; invalidateSearch()
    }
    fun invalidateSearch() {
        requestId = null; resultRevision = null; entries = emptyList(); total = 0; searching = true
    }
    fun beginSearch(): String = UUID.randomUUID().toString().also { requestId = it; searching = true }
    fun closeSearch() {
        searchMode = null; invalidateSearch(); searching = false
    }
    fun acceptResults(value: JSONObject, revision: String): Boolean {
        if (searchMode == null || value.optString("requestId") != requestId || value.optString("expectedRevision") != revision) return false
        val rows = value.optJSONArray("entries") ?: return false
        val count = value.opt("total") as? Int ?: return false
        if (rows.length() > 100 || count < rows.length()) return false
        val decoded = mutableListOf<BlockSearchEntry>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: return false
            val id = row.opt("id") as? String ?: return false
            val title = row.opt("title") as? String ?: return false
            val detail = row.opt("detail") as? String ?: return false
            if (id.isBlank() || id.length > 256 || title.length > 240 || detail.length > 300 || decoded.any { it.id == id }) return false
            decoded.add(BlockSearchEntry(id, title, detail))
        }
        entries = decoded; total = count; resultRevision = revision; searching = false
        return true
    }
}

@Composable
internal fun BlockEditorFloatingStatusPill(
    model: BlockEditorChromeModel,
    ready: Boolean,
    text: (String) -> String,
    onCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = (model.state.error || model.state.dismissable || model.state.notice) && model.state.status.isNotBlank()
    val usable = ready && !model.state.busy && !model.state.overlay
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val colors = MaterialTheme.colorScheme
        val isError = model.state.error
        Surface(
            shape = UNIT_CARD_SHAPE,
            color = if (isError) colors.errorContainer.copy(alpha = 0.96f) else colors.surface.copy(alpha = 0.96f),
            contentColor = if (isError) colors.onErrorContainer else colors.onSurface,
            border = BorderStroke(UNIT_BORDER_WIDTH, if (isError) colors.error.copy(alpha = 0.55f) else colors.outlineVariant),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .clip(UNIT_CARD_SHAPE)
                    .clickable(enabled = usable && isError) { onCommand("status") }
                    .padding(start = 12.dp, end = if (model.state.dismissable) 4.dp else 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = model.state.status,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isError) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (model.state.dismissable) {
                    IconButton(
                        onClick = { onCommand("statusDismiss") },
                        enabled = usable,
                        shape = UNIT_COMPACT_CONTROL_SHAPE,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(painterResource(R.drawable.ic_close), text("閉じる"), Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun BlockEditorEasyModeHintBanner(
    visible: Boolean,
    text: (String) -> String,
    onSwitchToEasy: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val colors = MaterialTheme.colorScheme
        Surface(
            shape = UNIT_CARD_SHAPE,
            color = colors.surface.copy(alpha = 0.97f),
            contentColor = colors.onSurface,
            border = BorderStroke(UNIT_BORDER_WIDTH, colors.primary.copy(alpha = 0.45f)),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = text("かんたんブロックも使えます"),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Surface(
                    onClick = onSwitchToEasy,
                    shape = UNIT_COMPACT_CONTROL_SHAPE,
                    color = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer,
                    border = BorderStroke(UNIT_BORDER_WIDTH, colors.primary.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = text("かんたんに切替"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    shape = UNIT_COMPACT_CONTROL_SHAPE,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_close), text("案内を閉じる"), Modifier.size(15.dp))
                }
            }
        }
    }
}

@Composable
internal fun BlockEditorFloatingDock(
    model: BlockEditorChromeModel,
    ready: Boolean,
    fullscreen: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    text: (String) -> String,
    onCommand: (String) -> Unit,
    onSearch: (String) -> Unit,
    onDisplay: () -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenExamples: (() -> Unit)? = null,
    easyMode: Boolean = false,
    onToggleEasyMode: (() -> Unit)? = null
) {
    var menu by remember { mutableStateOf(false) }
    val usable = ready && !model.state.busy && !model.state.overlay
    val paletteOpen = model.state.palette
    LaunchedEffect(usable) { if (!usable) menu = false }
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.widthIn(max = 340.dp),
        shape = UNIT_CARD_SHAPE,
        color = colors.surface.copy(alpha = 0.96f),
        contentColor = colors.onSurface,
        border = BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 46.dp)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val primaryDesc = text(if (paletteOpen) "閉じる" else "ブロックを追加")
            val primaryLabel = text(if (paletteOpen) "閉じる" else "追加")
            Surface(
                onClick = {
                    if (paletteOpen) onCommand("dismiss") else onCommand("add")
                },
                enabled = if (paletteOpen) ready else (usable && model.state.editable),
                shape = UNIT_CONTROL_SHAPE,
                color = if (paletteOpen) colors.secondaryContainer else colors.primary,
                contentColor = if (paletteOpen) colors.onSecondaryContainer else colors.onPrimary,
                modifier = Modifier.semantics { contentDescription = primaryDesc }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        painter = painterResource(if (paletteOpen) R.drawable.ic_close else R.drawable.ic_add),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    AutoFitSingleLineText(
                        text = primaryLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        minFontSize = 11.sp
                    )
                }
            }

            Box(
                Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(colors.outlineVariant.copy(alpha = UNIT_HAIRLINE_ALPHA))
            )

            @Composable
            fun DockIconButton(
                iconRes: Int,
                contentDesc: String,
                enabled: Boolean = true,
                onClick: () -> Unit
            ) {
                Surface(
                    onClick = onClick,
                    enabled = enabled,
                    shape = UNIT_COMPACT_CONTROL_SHAPE,
                    color = if (enabled) colors.surfaceContainerLow else Color.Transparent,
                    contentColor = if (enabled) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.45f),
                    border = if (enabled) BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant.copy(alpha = 0.55f)) else null,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(iconRes), contentDesc, Modifier.size(16.dp))
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DockIconButton(
                    iconRes = R.drawable.ic_undo,
                    contentDesc = text("元に戻す"),
                    enabled = usable && canUndo,
                    onClick = { onCommand("undo") }
                )
                DockIconButton(
                    iconRes = R.drawable.ic_redo,
                    contentDesc = text("やり直す"),
                    enabled = usable && canRedo,
                    onClick = { onCommand("redo") }
                )
                Box {
                    DockIconButton(
                        iconRes = R.drawable.ic_more_vertical,
                        contentDesc = text("その他の操作"),
                        enabled = usable || fullscreen,
                        onClick = { menu = true }
                    )
                    UnitDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        @Composable fun Item(label: String, enabled: Boolean = true, action: () -> Unit) {
                            DropdownMenuItem(text = { Text(text(label)) }, enabled = enabled,
                                onClick = { menu = false; action() })
                        }
                        Item("作品内を探す", usable) { onSearch("search") }
                        if (onToggleEasyMode != null) {
                            Item(if (easyMode) "詳細ブロックに切り替え" else "かんたんブロックに切り替え", usable) {
                                onToggleEasyMode()
                            }
                        }
                        HorizontalDivider()
                        Item(if (fullscreen) "分割表示に戻る" else "全画面", fullscreen || usable) { onFullscreen() }
                        Item("全体を表示", usable) { onCommand("fit") }
                        Item("コードプレビュー", usable) { onCommand("codePreview") }
                        Item("表示設定", usable, action = onDisplay)
                    }
                }
            }
        }
    }
}

@Composable
internal fun BlockEditorSearchDialog(
    model: BlockEditorChromeModel,
    text: (String) -> String,
    onQuery: (String) -> Unit,
    onLocate: (BlockSearchEntry) -> Unit
) {
    if (model.searchMode == null) return
    val focus = remember { FocusRequester() }
    val colors = MaterialTheme.colorScheme
    UnitAlertDialog(
        onDismissRequest = model::closeSearch,
        title = { Text(text("作品内を探す")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = listOf(
                        "search" to text("すべて"),
                        "functions" to text("処理の一覧"),
                        "parts" to if (model.state.parts > 0) "${text("未接続")} (${model.state.parts})" else text("未接続")
                    )
                    for ((modeKey, label) in tabs) {
                        val active = model.searchMode == modeKey
                        Surface(
                            onClick = { model.switchSearchMode(modeKey) },
                            shape = UNIT_COMPACT_CONTROL_SHAPE,
                            color = if (active) colors.primaryContainer else colors.surfaceContainerLow,
                            contentColor = if (active) colors.onPrimaryContainer else colors.onSurfaceVariant,
                            border = BorderStroke(
                                UNIT_BORDER_WIDTH,
                                if (active) colors.primary.copy(alpha = 0.55f) else colors.outlineVariant.copy(alpha = 0.65f)
                            )
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(value = model.query, onValueChange = onQuery,
                    label = { Text(text("名前・関数・コードで検索")) }, singleLine = true,
                    shape = UNIT_CONTROL_SHAPE,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus))
                if (model.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                else Text(text(if (model.total == 0) "一致するブロックがありません" else if (model.total > 100) "100件を表示しています。検索して絞り込めます" else "選ぶとその場所へ移動します"),
                    style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(model.entries, key = { it.id }) { entry ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(UNIT_COMPACT_CONTROL_SHAPE)
                                .clickable(enabled = !model.searching) { onLocate(entry) }
                                .padding(horizontal = 4.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                            Text(entry.detail, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = UNIT_HAIRLINE_ALPHA))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = model::closeSearch, shape = UNIT_CONTROL_SHAPE) { Text(text("閉じる")) } }
    )
    LaunchedEffect(Unit) { if (model.searchMode == "search") focus.requestFocus() }
}

@Composable
internal fun BlockEditorDisplayDialog(
    preferences: BlockEditorDisplayPreferences,
    text: (String) -> String,
    onDismiss: () -> Unit,
    onApply: (BlockEditorDisplayPreferences) -> Unit
) {
    var size by remember(preferences) { mutableStateOf(preferences.fontSize.toFloat()) }
    var roomy by remember(preferences) { mutableStateOf(preferences.density == "comfortable") }
    UnitAlertDialog(onDismissRequest = onDismiss, title = { Text(text("表示設定")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text("文字サイズ"), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = size.toString().removeSuffix(".0"),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            StudioSlider(
                value = size,
                onValueChange = { size = round(it * 2) / 2 },
                valueRange = 12f..22f,
                steps = 19,
                defaultValue = 12.5f,
                modifier = Modifier.semantics { contentDescription = text("文字サイズ") }
            )
            Row(Modifier.fillMaxWidth().toggleable(value = roomy, role = Role.Checkbox, onValueChange = { roomy = it }), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = roomy, onCheckedChange = null)
                Text(text("ゆったり表示（タッチ向け）"))
            }
            Text(text("表示だけを変更します。作品のコードは変わりません"), style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { onApply(BlockEditorDisplayPreferences(size.toDouble(), if (roomy) "comfortable" else "standard")) }, shape = UNIT_CONTROL_SHAPE) { Text(text("適用")) } },
        dismissButton = { TextButton(onClick = onDismiss, shape = UNIT_CONTROL_SHAPE) { Text(text("キャンセル")) } })
}
