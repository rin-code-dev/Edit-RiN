package com.hikariatelier.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Files and editing mode share one row, regardless of the number of project files. */
@Composable
internal fun EditorDocumentBar(
    files: List<String>,
    selectedFile: String,
    dirtyFiles: Set<String>,
    showingBlocks: Boolean,
    allowBlocks: Boolean,
    enabled: Boolean,
    readOnly: Boolean,
    autoRunEnabled: Boolean,
    previewHasChanges: Boolean,
    onSelectFile: (String) -> Unit,
    onSelectBlocks: (Boolean) -> Unit,
    onAddFile: (() -> Unit)?,
    onToggleAutoRun: () -> Unit,
    onRunChanges: () -> Unit,
    textTranslator: (String, Array<out Any?>) -> String,
    modifier: Modifier = Modifier,
    onOpenFullPreview: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    compact: Boolean = false,
    showBlockPreviewControls: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    var showFiles by remember(files, selectedFile) { mutableStateOf(false) }
    var showModes by remember(selectedFile) { mutableStateOf(false) }
    val unsaved = textTranslator("未保存の変更あり", emptyArray())

    if (compact) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = colors.surface,
            tonalElevation = 0.dp
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    leadingContent?.invoke()
                    Box(Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = enabled) { showFiles = true }
                                .semantics {
                                    contentDescription = textTranslator("ファイルを切り替え", emptyArray()) + ": " + selectedFile
                                    if (selectedFile in dirtyFiles) stateDescription = unsaved
                                }
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                selectedFile,
                                Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = colors.onSurface
                            )
                            if (selectedFile in dirtyFiles) Box(
                                Modifier.padding(start = 4.dp).size(5.dp).clip(CircleShape).background(colors.primary)
                            )
                            Icon(
                                painterResource(R.drawable.ic_chevron_down),
                                null,
                                Modifier.padding(start = 2.dp).size(14.dp),
                                tint = colors.onSurfaceVariant
                            )
                        }
                        FileSwitcherDropdown(
                            expanded = showFiles,
                            onDismiss = { showFiles = false },
                            files = files,
                            selectedFile = selectedFile,
                            dirtyFiles = dirtyFiles,
                            enabled = enabled,
                            readOnly = readOnly,
                            unsaved = unsaved,
                            onSelectFile = onSelectFile,
                            onAddFile = onAddFile,
                            textTranslator = textTranslator
                        )
                    }
                    if (allowBlocks) Box {
                        val mode = textTranslator(if (showingBlocks) "ブロック" else "コード", emptyArray())
                        val pillShape = RoundedCornerShape(7.dp)
                        Row(
                            modifier = Modifier
                                .height(24.dp)
                                .clip(pillShape)
                                .background(colors.primary.copy(alpha = 0.10f))
                                .border(1.dp, colors.primary.copy(alpha = 0.45f), pillShape)
                                .clickable(enabled = enabled) { showModes = true }
                                .semantics {
                                    contentDescription = textTranslator("編集モード", emptyArray()) + ": " + mode
                                }
                                .padding(horizontal = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                mode,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.primary,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                painterResource(R.drawable.ic_chevron_down),
                                null,
                                Modifier.padding(start = 2.dp).size(13.dp),
                                tint = colors.primary
                            )
                        }
                        ModeSwitcherDropdown(
                            expanded = showModes,
                            onDismiss = { showModes = false },
                            showingBlocks = showingBlocks,
                            enabled = enabled,
                            onSelectBlocks = onSelectBlocks,
                            textTranslator = textTranslator
                        )
                    }
                    if (showingBlocks && showBlockPreviewControls && !readOnly) {
                        IconToggleButton(
                            checked = autoRunEnabled,
                            onCheckedChange = { onToggleAutoRun() },
                            enabled = enabled,
                            shape = UNIT_COMPACT_CONTROL_SHAPE,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_auto_run),
                                textTranslator("自動実行を切り替え", emptyArray()),
                                Modifier.size(16.dp)
                            )
                        }
                        if (previewHasChanges) IconButton(
                            onClick = onRunChanges,
                            enabled = enabled,
                            shape = UNIT_COMPACT_CONTROL_SHAPE,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_play),
                                textTranslator("変更を実行", emptyArray()),
                                Modifier.size(16.dp)
                            )
                        }
                    }
                    if (showingBlocks && showBlockPreviewControls && onOpenFullPreview != null) {
                        IconButton(
                            onClick = onOpenFullPreview,
                            enabled = enabled,
                            shape = UNIT_COMPACT_CONTROL_SHAPE,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_fullscreen),
                                textTranslator("プレビューを全画面表示", emptyArray()),
                                Modifier.size(16.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.45f))
            }
        }
        return
    }

    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leadingContent?.invoke()
        Box(Modifier.weight(1f)) {
            TextButton(
                onClick = { showFiles = true },
                enabled = enabled,
                shape = UNIT_CONTROL_SHAPE,
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = textTranslator("ファイルを切り替え", emptyArray()) + ": " + selectedFile
                    if (selectedFile in dirtyFiles) stateDescription = unsaved
                },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(selectedFile, Modifier.weight(1f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                if (selectedFile in dirtyFiles) Box(
                    Modifier.padding(horizontal = 4.dp).size(5.dp).clip(CircleShape).background(colors.primary)
                )
                Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(16.dp))
            }
            FileSwitcherDropdown(
                expanded = showFiles,
                onDismiss = { showFiles = false },
                files = files,
                selectedFile = selectedFile,
                dirtyFiles = dirtyFiles,
                enabled = enabled,
                readOnly = readOnly,
                unsaved = unsaved,
                onSelectFile = onSelectFile,
                onAddFile = onAddFile,
                textTranslator = textTranslator
            )
        }
        if (allowBlocks) Box {
            val mode = textTranslator(if (showingBlocks) "ブロック" else "コード", emptyArray())
            TextButton(
                onClick = { showModes = true }, enabled = enabled,
                shape = UNIT_CONTROL_SHAPE,
                modifier = Modifier.semantics {
                    contentDescription = textTranslator("編集モード", emptyArray()) + ": " + mode
                },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(mode, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(16.dp))
            }
            ModeSwitcherDropdown(
                expanded = showModes,
                onDismiss = { showModes = false },
                showingBlocks = showingBlocks,
                enabled = enabled,
                onSelectBlocks = onSelectBlocks,
                textTranslator = textTranslator
            )
        }
        if (showingBlocks && showBlockPreviewControls && !readOnly) {
            IconToggleButton(
                checked = autoRunEnabled,
                onCheckedChange = { onToggleAutoRun() },
                enabled = enabled,
                shape = UNIT_CONTROL_SHAPE
            ) {
                Icon(painterResource(R.drawable.ic_auto_run), textTranslator("自動実行を切り替え", emptyArray()),
                    Modifier.size(18.dp))
            }
            if (previewHasChanges) IconButton(onClick = onRunChanges, enabled = enabled, shape = UNIT_CONTROL_SHAPE) {
                Icon(painterResource(R.drawable.ic_play), textTranslator("変更を実行", emptyArray()), Modifier.size(18.dp))
            }
        }
        if (showingBlocks && showBlockPreviewControls && onOpenFullPreview != null) {
            IconButton(onClick = onOpenFullPreview, enabled = enabled, shape = UNIT_CONTROL_SHAPE) {
                Icon(
                    painterResource(R.drawable.ic_fullscreen),
                    textTranslator("プレビューを全画面表示", emptyArray()),
                    Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Floating top-right mini capsule used in portrait code mode (B+C hybrid):
 * omits "sketch.js" for single-file works, displays the active file name for multi-file works,
 * and leaves 0dp of dedicated header chrome above the code area.
 */
@Composable
internal fun EditorDocumentOverlayCapsule(
    files: List<String>,
    selectedFile: String,
    dirtyFiles: Set<String>,
    showingBlocks: Boolean,
    allowBlocks: Boolean,
    enabled: Boolean,
    readOnly: Boolean,
    showFileName: Boolean,
    onSelectFile: (String) -> Unit,
    onSelectBlocks: (Boolean) -> Unit,
    onAddFile: (() -> Unit)?,
    textTranslator: (String, Array<out Any?>) -> String,
    modifier: Modifier = Modifier
) {
    val showAddAction = !showFileName && !readOnly && onAddFile != null
    if (!allowBlocks && !showFileName && !showAddAction) return

    val colors = MaterialTheme.colorScheme
    var showFiles by remember(files, selectedFile) { mutableStateOf(false) }
    var showModes by remember(selectedFile) { mutableStateOf(false) }
    val unsaved = textTranslator("未保存の変更あり", emptyArray())

    Surface(
        modifier = modifier.height(28.dp),
        shape = UNIT_CONTROL_SHAPE,
        color = colors.surface,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (showFileName) {
                Box {
                    Row(
                        modifier = Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .clickable(enabled = enabled) { showFiles = true }
                            .semantics {
                                contentDescription = textTranslator("ファイルを切り替え", emptyArray()) + ": " + selectedFile
                                if (selectedFile in dirtyFiles) stateDescription = unsaved
                            }
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            selectedFile,
                            modifier = Modifier.widthIn(max = 120.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.onSurface
                        )
                        if (selectedFile in dirtyFiles) Box(
                            Modifier.padding(start = 3.dp).size(5.dp).clip(CircleShape).background(colors.primary)
                        )
                        Icon(
                            painterResource(R.drawable.ic_chevron_down),
                            null,
                            Modifier.padding(start = 1.dp).size(13.dp),
                            tint = colors.onSurfaceVariant
                        )
                    }
                    FileSwitcherDropdown(
                        expanded = showFiles,
                        onDismiss = { showFiles = false },
                        files = files,
                        selectedFile = selectedFile,
                        dirtyFiles = dirtyFiles,
                        enabled = enabled,
                        readOnly = readOnly,
                        unsaved = unsaved,
                        onSelectFile = onSelectFile,
                        onAddFile = onAddFile,
                        textTranslator = textTranslator
                    )
                }
            } else if (showAddAction) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .clickable(enabled = enabled) { showFiles = true }
                            .semantics {
                                contentDescription = textTranslator("ファイルを切り替え", emptyArray()) + ": " + selectedFile
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_add),
                            textTranslator("ファイルを追加", emptyArray()),
                            Modifier.size(14.dp),
                            tint = colors.onSurfaceVariant
                        )
                    }
                    FileSwitcherDropdown(
                        expanded = showFiles,
                        onDismiss = { showFiles = false },
                        files = files,
                        selectedFile = selectedFile,
                        dirtyFiles = dirtyFiles,
                        enabled = enabled,
                        readOnly = readOnly,
                        unsaved = unsaved,
                        onSelectFile = onSelectFile,
                        onAddFile = onAddFile,
                        textTranslator = textTranslator
                    )
                }
            }
            if (allowBlocks && (showFileName || showAddAction)) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(14.dp)
                        .background(colors.outlineVariant)
                )
            }
            if (allowBlocks) {
                Box {
                    val mode = textTranslator(if (showingBlocks) "ブロック" else "コード", emptyArray())
                    Row(
                        modifier = Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .clickable(enabled = enabled) { showModes = true }
                            .semantics {
                                contentDescription = textTranslator("編集モード", emptyArray()) + ": " + mode
                            }
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            mode,
                            maxLines = 1,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary
                        )
                        Icon(
                            painterResource(R.drawable.ic_chevron_down),
                            null,
                            Modifier.padding(start = 1.dp).size(13.dp),
                            tint = colors.primary
                        )
                    }
                    ModeSwitcherDropdown(
                        expanded = showModes,
                        onDismiss = { showModes = false },
                        showingBlocks = showingBlocks,
                        enabled = enabled,
                        onSelectBlocks = onSelectBlocks,
                        textTranslator = textTranslator
                    )
                }
            }
        }
    }
}

@Composable
private fun FileSwitcherDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    files: List<String>,
    selectedFile: String,
    dirtyFiles: Set<String>,
    enabled: Boolean,
    readOnly: Boolean,
    unsaved: String,
    onSelectFile: (String) -> Unit,
    onAddFile: (() -> Unit)?,
    textTranslator: (String, Array<out Any?>) -> String
) {
    val colors = MaterialTheme.colorScheme
    UnitDropdownMenu(expanded, onDismiss, Modifier.heightIn(max = 320.dp).widthIn(max = 320.dp)) {
        files.forEach { name ->
            DropdownMenuItem(
                text = { Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                onClick = { onDismiss(); onSelectFile(name) },
                enabled = enabled,
                modifier = Modifier.semantics {
                    selected = name == selectedFile
                    if (name in dirtyFiles) stateDescription = unsaved
                },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (name in dirtyFiles) Box(Modifier.size(5.dp).clip(CircleShape).background(colors.primary))
                        if (name == selectedFile) Text("✓")
                    }
                }
            )
        }
        if (!readOnly && onAddFile != null) {
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(textTranslator("ファイルを追加", emptyArray())) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp)) },
                onClick = { onDismiss(); onAddFile() },
                enabled = enabled
            )
        }
    }
}

@Composable
private fun ModeSwitcherDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    showingBlocks: Boolean,
    enabled: Boolean,
    onSelectBlocks: (Boolean) -> Unit,
    textTranslator: (String, Array<out Any?>) -> String
) {
    UnitDropdownMenu(expanded, onDismiss) {
        listOf(false, true).forEach { blocks ->
            DropdownMenuItem(
                text = { Text(textTranslator(if (blocks) "ブロック" else "コード", emptyArray())) },
                onClick = { onDismiss(); onSelectBlocks(blocks) },
                enabled = enabled,
                modifier = Modifier.semantics { selected = showingBlocks == blocks },
                trailingIcon = { if (showingBlocks == blocks) Text("✓") }
            )
        }
    }
}
