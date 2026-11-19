package com.hikariatelier.app

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.util.Collections

internal val GALLERY_SIDE_PANEL_EXPANDED_WIDTH = 188.dp
internal val GALLERY_SIDE_PANEL_COLLAPSED_WIDTH = 50.dp

/** Display label shared by the side panel and the collapsed folder menu. */
internal fun galleryFolderLabel(folder: String?, text: (String) -> String): String = when (folder) {
    null -> text("自分の作品")
    SAMPLE_FOLDER -> text("サンプル・閲覧専用")
    "" -> text("未分類")
    else -> folder
}

/**
 * Minimal atelier sidebar Unit for the landscape gallery.
 * Spans the full height of the screen beside the artwork grid so collections,
 * tags, and creation stay accessible without crowding the artworks.
 */
@Composable
internal fun WorkGallerySidePanel(
    state: WorkGalleryState,
    totalWorksCount: Int,
    tabs: List<String>,
    folderCounts: Map<String?, Int>,
    tags: List<String>,
    tagCounts: Map<String, Int>,
    busy: Boolean,
    text: (String) -> String,
    onChooseFolder: (String?) -> Unit,
    onReorderTabs: (List<String>) -> Unit,
    onManageFolders: () -> Unit,
    onManageTags: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val expanded = state.sidePanelExpanded
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
            .width(if (expanded) GALLERY_SIDE_PANEL_EXPANDED_WIDTH else GALLERY_SIDE_PANEL_COLLAPSED_WIDTH),
        shape = RoundedCornerShape(UNIT_CORNER_RADIUS),
        color = colors.surface,
        border = BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant)
    ) {
        if (expanded) {
            ExpandedSidePanel(
                state, totalWorksCount, tabs, folderCounts, tags, tagCounts,
                busy, text, onChooseFolder, onReorderTabs, onManageFolders, onManageTags, onAdd
            )
        } else {
            CollapsedSidePanel(
                state, tabs, tags, busy, text, onChooseFolder, onManageFolders, onManageTags, onAdd
            )
        }
    }
}

@Composable
private fun SidePanelToggle(state: WorkGalleryState, text: (String) -> String, size: Dp = 32.dp) {
    val label = if (state.sidePanelExpanded) text("サイドパネルを閉じる") else text("サイドパネルを開く")
    TooltipIconButton(
        label = label,
        onClick = { state.sidePanelExpanded = !state.sidePanelExpanded },
        shape = if (size <= 32.dp) UNIT_COMPACT_CONTROL_SHAPE else UNIT_CONTROL_SHAPE,
        modifier = Modifier.size(size)
    ) {
        Icon(
            painterResource(R.drawable.ic_back),
            label,
            Modifier.size(16.dp).rotate(if (state.sidePanelExpanded) 0f else 180f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpandedSidePanel(
    state: WorkGalleryState,
    totalWorksCount: Int,
    tabs: List<String>,
    folderCounts: Map<String?, Int>,
    tags: List<String>,
    tagCounts: Map<String, Int>,
    busy: Boolean,
    text: (String) -> String,
    onChooseFolder: (String?) -> Unit,
    onReorderTabs: (List<String>) -> Unit,
    onManageFolders: () -> Unit,
    onManageTags: () -> Unit,
    onAdd: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var currentTabs by remember(tabs) { mutableStateOf(tabs) }
    val latestTabs by rememberUpdatedState(currentTabs)
    val latestReorder by rememberUpdatedState(onReorderTabs)
    val latestSourceTabs by rememberUpdatedState(tabs)
    var draggingTab by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(Modifier.fillMaxSize()) {
        // Minimal editorial header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text("作品"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 1
            )
            Text(
                totalWorksCount.toString(),
                Modifier.padding(start = 6.dp).weight(1f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = colors.outline,
                maxLines = 1
            )
            SidePanelToggle(state, text)
        }

        HorizontalDivider(
            Modifier.padding(horizontal = 12.dp),
            color = colors.outlineVariant.copy(alpha = 0.45f)
        )

        // Scrollable collections & tag pills
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text("フォルダー"),
                    Modifier.weight(1f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.8.sp,
                    color = colors.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TooltipIconButton(
                    label = text("フォルダー管理"),
                    onClick = onManageFolders,
                    enabled = !busy,
                    shape = UNIT_COMPACT_CONTROL_SHAPE,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_folder_code),
                        text("フォルダー管理"),
                        Modifier.size(15.dp),
                        tint = colors.onSurfaceVariant
                    )
                }
            }

            SidePanelItem(
                label = galleryFolderLabel(null, text),
                count = folderCounts[null],
                selected = state.selectedFolder == null,
                enabled = !busy,
                onClick = { onChooseFolder(null) }
            )

            currentTabs.forEach { tab ->
                key(tab) {
                    val isDragging = draggingTab == tab
                    SidePanelItem(
                        label = galleryFolderLabel(tab, text),
                        count = folderCounts[tab],
                        selected = state.selectedFolder == tab,
                        enabled = !busy,
                        onClick = { onChooseFolder(tab) },
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                if (isDragging) {
                                    translationY = dragOffset
                                    scaleX = 1.03f; scaleY = 1.03f
                                    shadowElevation = 6f
                                }
                            }
                            .pointerInput(tab, busy) {
                                if (busy) return@pointerInput
                                fun finish() {
                                    draggingTab = null
                                    dragOffset = 0f
                                    if (latestTabs != latestSourceTabs) latestReorder(latestTabs)
                                }
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingTab = tab
                                        dragOffset = 0f
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val threshold = with(density) { 36.dp.toPx() }
                                        val index = latestTabs.indexOf(tab)
                                        if (index == -1) return@detectDragGesturesAfterLongPress
                                        if (dragOffset > threshold && index < latestTabs.lastIndex) {
                                            currentTabs = latestTabs.toMutableList().also { Collections.swap(it, index, index + 1) }
                                            dragOffset -= threshold
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        } else if (dragOffset < -threshold && index > 0) {
                                            currentTabs = latestTabs.toMutableList().also { Collections.swap(it, index, index - 1) }
                                            dragOffset += threshold
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                    onDragEnd = { finish() },
                                    onDragCancel = { finish() }
                                )
                            }
                    )
                }
            }

            HorizontalDivider(
                Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                color = colors.outlineVariant.copy(alpha = 0.45f)
            )

            Row(
                Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text("タグ"),
                    Modifier.weight(1f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.8.sp,
                    color = colors.outline,
                    maxLines = 1
                )
                TooltipIconButton(
                    label = text("タグの管理"),
                    onClick = onManageTags,
                    shape = UNIT_COMPACT_CONTROL_SHAPE,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_tag),
                        text("タグの管理"),
                        Modifier.size(15.dp),
                        tint = colors.onSurfaceVariant
                    )
                }
            }

            if (tags.isEmpty()) {
                Text(
                    text("タグはまだありません"),
                    Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    fontSize = 11.sp,
                    color = colors.outline
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    SidePanelTagPill(
                        label = text("すべて"),
                        selected = state.selectedTag == null,
                        onClick = { state.selectedTag = null }
                    )
                    tags.forEach { tag ->
                        val count = tagCounts[tag]
                        SidePanelTagPill(
                            label = if (count != null) "#$tag · $count" else "#$tag",
                            selected = state.selectedTag == tag,
                            onClick = { state.selectedTag = if (state.selectedTag == tag) null else tag }
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        // Pinned studio creation CTA
        if (!state.selecting) {
            HorizontalDivider(
                Modifier.padding(horizontal = 12.dp),
                color = colors.outlineVariant.copy(alpha = 0.45f)
            )
            Surface(
                onClick = onAdd,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .height(34.dp),
                shape = UNIT_CONTROL_SHAPE,
                color = colors.onSurface,
                contentColor = colors.surface
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(painterResource(R.drawable.ic_add), null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text("作品を追加"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SidePanelItem(
    label: String,
    count: Int?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp)
            .clip(UNIT_CONTROL_SHAPE)
            .background(if (selected) colors.primary.copy(alpha = 0.09f) else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(start = 6.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(CircleShape)
                .background(if (selected) colors.primary else Color.Transparent)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                !enabled -> colors.onSurface.copy(alpha = 0.38f)
                selected -> colors.primary
                else -> colors.onSurface
            }
        )
        if (count != null) {
            Text(
                if (count > 999) "999+" else count.toString(),
                Modifier.padding(start = 6.dp),
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                color = if (selected) colors.primary.copy(alpha = 0.85f) else colors.outline
            )
        }
    }
}

@Composable
private fun SidePanelTagPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val pillShape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .height(25.dp)
            .clip(pillShape)
            .background(if (selected) colors.primary.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) colors.primary else colors.outlineVariant.copy(alpha = 0.75f),
                pillShape
            )
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CollapsedSidePanel(
    state: WorkGalleryState,
    tabs: List<String>,
    tags: List<String>,
    busy: Boolean,
    text: (String) -> String,
    onChooseFolder: (String?) -> Unit,
    onManageFolders: () -> Unit,
    onManageTags: () -> Unit,
    onAdd: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var folderMenu by remember { mutableStateOf(false) }
    var tagMenu by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SidePanelToggle(state, text, size = 38.dp)
        Spacer(Modifier.height(4.dp))
        Box {
            val folderLabel = text("フォルダー") + ": " + galleryFolderLabel(state.selectedFolder, text)
            TooltipIconButton(
                label = folderLabel,
                onClick = { folderMenu = true },
                enabled = !busy,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    painterResource(R.drawable.ic_folder_code),
                    folderLabel,
                    Modifier.size(18.dp),
                    tint = if (state.selectedFolder != null) colors.primary else colors.onSurface
                )
            }
            DropdownMenu(folderMenu, { folderMenu = false }, Modifier.heightIn(max = 320.dp)) {
                (listOf<String?>(null) + tabs).forEach { folder ->
                    DropdownMenuItem(
                        text = { Text(galleryFolderLabel(folder, text), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.semantics { selected = state.selectedFolder == folder },
                        trailingIcon = { if (state.selectedFolder == folder) Text("✓") },
                        onClick = { folderMenu = false; onChooseFolder(folder) }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(text("フォルダー管理")) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_folder_code), null, Modifier.size(18.dp)) },
                    onClick = { folderMenu = false; onManageFolders() }
                )
            }
        }
        Box {
            val tagLabel = text("タグ") + ": " + (state.selectedTag?.let { "#$it" } ?: text("すべて"))
            TooltipIconButton(
                label = tagLabel,
                onClick = { tagMenu = true },
                modifier = Modifier.size(38.dp)
            ) {
                Box {
                    Icon(
                        painterResource(R.drawable.ic_tag),
                        tagLabel,
                        Modifier.size(18.dp),
                        tint = if (state.selectedTag != null) colors.primary else colors.onSurface
                    )
                    if (state.selectedTag != null) Box(
                        Modifier.align(Alignment.TopEnd).size(6.dp).clip(CircleShape).background(colors.primary)
                    )
                }
            }
            DropdownMenu(tagMenu, { tagMenu = false }, Modifier.heightIn(max = 320.dp)) {
                if (tags.isNotEmpty()) {
                    (listOf<String?>(null) + tags).forEach { tag ->
                        DropdownMenuItem(
                            text = { Text(tag?.let { "#$it" } ?: text("すべて"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.semantics { selected = state.selectedTag == tag },
                            trailingIcon = { if (state.selectedTag == tag) Text("✓") },
                            onClick = { tagMenu = false; state.selectedTag = tag }
                        )
                    }
                    HorizontalDivider()
                }
                DropdownMenuItem(
                    text = { Text(text("タグの管理")) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_tag), null, Modifier.size(18.dp)) },
                    onClick = { tagMenu = false; onManageTags() }
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (!state.selecting) {
            Surface(
                onClick = onAdd,
                enabled = !busy,
                modifier = Modifier.size(34.dp),
                shape = UNIT_CONTROL_SHAPE,
                color = colors.onSurface,
                contentColor = colors.surface
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_add), text("作品を追加"), Modifier.size(18.dp))
                }
            }
        }
    }
}
