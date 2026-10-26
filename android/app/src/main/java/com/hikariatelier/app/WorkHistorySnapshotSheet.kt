package com.hikariatelier.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class HistoryItemType {
    REVISION,
    SNAPSHOT,
    LEGACY_SNAPSHOT
}

internal enum class HistoryFilter {
    ALL,
    REVISIONS,
    SNAPSHOTS
}

/**
 * Unified representation of a restore point for UI display and diffing.
 */
internal data class HistoryDisplayItem(
    val id: String,
    val workId: String,
    val type: HistoryItemType,
    val timestamp: Long,
    val label: String,
    val notes: String? = null,
    val restoreScopeSummary: String,
    val codePreview: String,
    val originalRevision: WorkRevision? = null,
    val originalSnapshot: WorkSnapshot? = null
)

/**
 * Builds unified history display items from revisions and snapshots, sorting by timestamp descending.
 */
internal fun buildHistoryDisplayItems(
    workId: String,
    revisions: List<WorkRevision>,
    snapshots: List<WorkSnapshot>,
    uiText: (String) -> String
): List<HistoryDisplayItem> {
    val items = mutableListOf<HistoryDisplayItem>()

    // Revisions
    revisions.forEachIndexed { index, rev ->
        val preview = rev.code.lines().take(4).joinToString("\n")
        items.add(
            HistoryDisplayItem(
                id = "rev_${rev.savedAt}_$index",
                workId = workId,
                type = HistoryItemType.REVISION,
                timestamp = rev.savedAt,
                label = uiText("自動保存"),
                notes = null,
                restoreScopeSummary = uiText("sketch.js 本文のみ復元"),
                codePreview = preview,
                originalRevision = rev
            )
        )
    }

    // Snapshots
    snapshots.forEach { snap ->
        val isLegacy = snap.mainCodeOnly || (snap.files.isEmpty() && snap.parameterValues.isEmpty() && snap.blockEditorState == null)
        val type = if (isLegacy) HistoryItemType.LEGACY_SNAPSHOT else HistoryItemType.SNAPSHOT
        val summary = if (isLegacy) {
            uiText("コードのみ復元（旧形式）")
        } else {
            val parts = mutableListOf(uiText("コード"))
            if (snap.files.isNotEmpty()) parts.add("${uiText("追加ファイル")} (${snap.files.size})")
            if (snap.parameterValues.isNotEmpty()) parts.add(uiText("パラメータ"))
            if (snap.blockEditorState != null) parts.add(uiText("ブロック配置"))
            String.format(java.util.Locale.ROOT, uiText("復元対象: %s"), parts.joinToString("・"))
        }

        items.add(
            HistoryDisplayItem(
                id = "snap_${snap.id}",
                workId = workId,
                type = type,
                timestamp = snap.savedAt,
                label = snap.title?.takeIf { it.isNotBlank() } ?: uiText("スナップショット"),
                notes = snap.note?.takeIf { it.isNotBlank() },
                restoreScopeSummary = summary,
                codePreview = snap.code.lines().take(4).joinToString("\n"),
                originalSnapshot = snap
            )
        )
    }

    return items.sortedByDescending { it.timestamp }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UnifiedHistorySheet(
    workId: String,
    workTitle: String,
    revisions: List<WorkRevision>,
    snapshots: List<WorkSnapshot>,
    currentCode: String,
    initialFilter: HistoryFilter = HistoryFilter.ALL,
    loading: Boolean = false,
    busy: Boolean = false,
    error: String? = null,
    codeFontFamily: FontFamily,
    colors: ColorScheme,
    onRestoreRevision: (WorkRevision) -> Unit,
    onRestoreSnapshot: (WorkSnapshot) -> Unit,
    onCreateSnapshot: (name: String?, notes: String?) -> Unit,
    onUpdateSnapshot: (WorkSnapshot, name: String?, notes: String?) -> Unit,
    onDeleteSnapshot: (WorkSnapshot) -> Unit,
    onRetryLoad: () -> Unit,
    onDismiss: () -> Unit,
    textTranslator: (String, Array<out Any?>) -> String
) {
    fun uiText(key: String, vararg args: Any?): String = textTranslator(key, args)

    var currentFilter by rememberSaveable { mutableStateOf(initialFilter) }
    var selectedItemForDiff by remember { mutableStateOf<HistoryDisplayItem?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val allItems = remember(workId, revisions, snapshots) {
        buildHistoryDisplayItems(workId, revisions, snapshots) { uiText(it) }
    }

    val filteredItems = remember(allItems, currentFilter) {
        when (currentFilter) {
            HistoryFilter.ALL -> allItems
            HistoryFilter.REVISIONS -> allItems.filter { it.type == HistoryItemType.REVISION }
            HistoryFilter.SNAPSHOTS -> allItems.filter { it.type != HistoryItemType.REVISION }
        }
    }

    WorkSheet(
        title = uiText("履歴と保護"),
        subtitle = workTitle,
        onDismiss = onDismiss,
        colors = colors,
        textTranslator = textTranslator,
        dismissEnabled = !busy
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Filter chips + Create Snapshot button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = currentFilter == HistoryFilter.ALL,
                        onClick = { currentFilter = HistoryFilter.ALL },
                        label = { Text("${uiText("すべて")} (${allItems.size})") }
                    )
                    FilterChip(
                        selected = currentFilter == HistoryFilter.REVISIONS,
                        onClick = { currentFilter = HistoryFilter.REVISIONS },
                        label = { Text("${uiText("自動保存")} (${revisions.size})") }
                    )
                    FilterChip(
                        selected = currentFilter == HistoryFilter.SNAPSHOTS,
                        onClick = { currentFilter = HistoryFilter.SNAPSHOTS },
                        label = { Text("${uiText("スナップショット")} (${snapshots.size})") }
                    )
                }

                Spacer(Modifier.width(8.dp))

                FilledTonalButton(
                    onClick = { showCreateDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_add), null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(uiText("スナップショット作成"), style = MaterialTheme.typography.labelMedium)
                }
            }

            if (error != null) {
                Surface(
                    color = colors.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            color = colors.onErrorContainer,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = onRetryLoad) {
                            Text(uiText("再試行"))
                        }
                    }
                }
            }

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiText("記録がありません"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        HistoryCard(
                            item = item,
                            colors = colors,
                            codeFontFamily = codeFontFamily,
                            uiText = { uiText(it) },
                            onClick = { selectedItemForDiff = item }
                        )
                    }
                }
            }
        }
    }

    // Diff / Confirmation dialog
    selectedItemForDiff?.let { item ->
        val itemCode = item.originalRevision?.code ?: item.originalSnapshot?.code.orEmpty()
        HistoryDiffDialog(
            item = item,
            currentCode = currentCode,
            itemCode = itemCode,
            colors = colors,
            codeFontFamily = codeFontFamily,
            uiText = { uiText(it) },
            onDismiss = { selectedItemForDiff = null },
            onConfirmRestore = {
                selectedItemForDiff = null
                onDismiss()
                when (item.type) {
                    HistoryItemType.REVISION -> item.originalRevision?.let(onRestoreRevision)
                    HistoryItemType.SNAPSHOT, HistoryItemType.LEGACY_SNAPSHOT -> item.originalSnapshot?.let(onRestoreSnapshot)
                }
            }
        )
    }

    // Create Snapshot Dialog
    if (showCreateDialog) {
        var newName by remember { mutableStateOf("") }
        var newNotes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(uiText("スナップショット作成")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        uiText("現在のコード・追加ファイル・ブロック配置を保護します。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(uiText("名前（省略可）")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newNotes,
                        onValueChange = { newNotes = it },
                        label = { Text(uiText("メモ（省略可）")) },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        onCreateSnapshot(newName.takeIf { it.isNotBlank() }, newNotes.takeIf { it.isNotBlank() })
                    }
                ) {
                    Text(uiText("作成"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(uiText("キャンセル"))
                }
            }
        )
    }
}

@Composable
private fun HistoryCard(
    item: HistoryDisplayItem,
    colors: ColorScheme,
    codeFontFamily: FontFamily,
    uiText: (String) -> String,
    onClick: () -> Unit
) {
    val dateText = remember(item.timestamp) {
        val date = java.util.Date(item.timestamp)
        java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss", java.util.Locale.getDefault()).format(date)
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceContainerHigh,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (item.type == HistoryItemType.REVISION) colors.secondaryContainer else colors.tertiaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (item.type == HistoryItemType.REVISION) uiText("自動保存") else uiText("スナップショット"),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.type == HistoryItemType.REVISION) colors.onSecondaryContainer else colors.onTertiaryContainer
                    )
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    text = item.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = dateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }

            Text(
                text = item.restoreScopeSummary,
                style = MaterialTheme.typography.bodySmall,
                color = colors.primary
            )

            if (item.notes != null) {
                Text(
                    text = item.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                color = colors.surface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.5.dp, colors.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.codePreview,
                    fontFamily = codeFontFamily,
                    fontSize = 10.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp),
                    color = colors.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun HistoryDiffDialog(
    item: HistoryDisplayItem,
    currentCode: String,
    itemCode: String,
    colors: ColorScheme,
    codeFontFamily: FontFamily,
    uiText: (String) -> String,
    onDismiss: () -> Unit,
    onConfirmRestore: () -> Unit
) {
    val isSame = currentCode == itemCode

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(String.format(java.util.Locale.ROOT, uiText("復元の確認: %s"), item.label))
                Text(
                    item.restoreScopeSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isSame) {
                    Surface(
                        color = colors.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiText("コード本文は現在の内容と同一です。"),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                } else {
                    Text(
                        text = uiText("この記録の内容に復元します（現在の未保存編集は上書きされます）。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }

                Text(
                    text = uiText("復元するコード（プレビュー）:"),
                    style = MaterialTheme.typography.labelMedium
                )

                Surface(
                    color = colors.surfaceContainerHighest,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    Text(
                        text = itemCode,
                        fontFamily = codeFontFamily,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmRestore,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Text(uiText("復元する"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(uiText("キャンセル"))
            }
        }
    )
}
