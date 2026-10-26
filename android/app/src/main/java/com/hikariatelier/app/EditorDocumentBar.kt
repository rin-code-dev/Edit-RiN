package com.hikariatelier.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
    onOpenFullPreview: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    var showFiles by remember(files, selectedFile) { mutableStateOf(false) }
    var showModes by remember(selectedFile) { mutableStateOf(false) }
    val unsaved = textTranslator("未保存の変更あり", emptyArray())
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            TextButton(
                onClick = { showFiles = true },
                enabled = enabled,
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
            DropdownMenu(showFiles, { showFiles = false }, Modifier.heightIn(max = 320.dp).widthIn(max = 320.dp)) {
                files.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        onClick = { showFiles = false; onSelectFile(name) },
                        enabled = enabled,
                        modifier = Modifier.semantics {
                            selected = name == selectedFile
                            if (name in dirtyFiles) stateDescription = unsaved
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        onClick = { showFiles = false; onAddFile() },
                        enabled = enabled
                    )
                }
            }
        }
        if (allowBlocks) Box {
            val mode = textTranslator(if (showingBlocks) "ブロック" else "コード", emptyArray())
            TextButton(
                onClick = { showModes = true }, enabled = enabled,
                modifier = Modifier.semantics {
                    contentDescription = textTranslator("編集モード", emptyArray()) + ": " + mode
                },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(mode, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(16.dp))
            }
            DropdownMenu(showModes, { showModes = false }) {
                listOf(false, true).forEach { blocks ->
                    DropdownMenuItem(
                        text = { Text(textTranslator(if (blocks) "ブロック" else "コード", emptyArray())) },
                        onClick = { showModes = false; onSelectBlocks(blocks) },
                        enabled = enabled,
                        modifier = Modifier.semantics { selected = showingBlocks == blocks },
                        trailingIcon = { if (showingBlocks == blocks) Text("✓") }
                    )
                }
            }
        }
        if (showingBlocks && !readOnly) {
            IconToggleButton(checked = autoRunEnabled, onCheckedChange = { onToggleAutoRun() }, enabled = enabled) {
                Icon(painterResource(R.drawable.ic_auto_run), textTranslator("自動実行を切り替え", emptyArray()),
                    Modifier.size(18.dp))
            }
            if (previewHasChanges) IconButton(onClick = onRunChanges, enabled = enabled) {
                Icon(painterResource(R.drawable.ic_play), textTranslator("変更を実行", emptyArray()), Modifier.size(18.dp))
            }
        }
        if (showingBlocks && onOpenFullPreview != null) {
            IconButton(onClick = onOpenFullPreview, enabled = enabled) {
                Icon(
                    painterResource(R.drawable.ic_fullscreen),
                    textTranslator("プレビューを全画面表示", emptyArray()),
                    Modifier.size(18.dp)
                )
            }
        }
    }
}
