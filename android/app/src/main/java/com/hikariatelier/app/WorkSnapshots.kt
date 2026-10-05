package com.hikariatelier.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SnapshotSheet(
    snapshots: List<WorkSnapshot>,
    current: SnapshotContent,
    loading: Boolean,
    busy: Boolean,
    error: String?,
    onRetry: () -> Unit,
    codeFontFamily: FontFamily,
    onCreateSnapshot: (String?, String?) -> Unit,
    onUpdateSnapshotDetails: (WorkSnapshot, String?, String?) -> Unit,
    onRestoreSnapshot: (WorkSnapshot) -> Unit,
    onDeleteSnapshot: (WorkSnapshot) -> Unit,
    text: (String) -> String
) {
    val colors = MaterialTheme.colorScheme
    var diffTarget by remember { mutableStateOf<WorkSnapshot?>(null) }
    var snapshotToDelete by remember { mutableStateOf<WorkSnapshot?>(null) }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    var detailsTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsTitle by rememberSaveable { mutableStateOf("") }
    var detailsNote by rememberSaveable { mutableStateOf("") }
    var detailsPending by rememberSaveable { mutableStateOf(false) }
    var beforeCreateIds by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(snapshots, busy, detailsPending) {
        if (detailsPending && !busy) {
            val expected = runCatching { normalizedSnapshotDetails(detailsTitle, detailsNote) }.getOrNull()
            val saved = if (detailsTargetId == null) snapshots.firstOrNull { it.id !in beforeCreateIds }
                else snapshots.firstOrNull { it.id == detailsTargetId }
            if (expected != null && saved != null && saved.title == expected.first && saved.note == expected.second) {
                detailsOpen = false
                detailsPending = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        // Create Snapshot Action
        FilledTonalButton(
            onClick = {
                detailsTargetId = null; detailsTitle = ""; detailsNote = ""
                detailsPending = false; detailsOpen = true
            },
            enabled = !loading && !busy && error == null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_snapshot),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(text("現在の状態をスナップショット（保存）"), fontWeight = FontWeight.SemiBold)
        }

        if (loading || busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(text(if (loading) "スナップショットを読み込み中…" else "スナップショットを処理中…"),
                modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
        if (error != null) {
            Text(error, color = colors.error)
            TextButton(onClick = onRetry, enabled = !busy && !loading) { Text(text("再試行")) }
        }
        if (snapshots.isEmpty() && !loading && error == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text("保存されたスナップショットはありません"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(snapshots, key = { it.id }, contentType = { "snapshot_item" }) { snapshot ->
                    val isCurrent = snapshot.matches(current)
                    val lineCount = remember(snapshot.code) { snapshot.code.lines().size }
                    val dateFormatted = remember(snapshot.savedAt) {
                        android.text.format.DateFormat.format("MM/dd HH:mm", snapshot.savedAt).toString()
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surfaceContainerHigh,
                        border = BorderStroke(
                            1.dp,
                            if (isCurrent) colors.primary.copy(alpha = 0.5f) else colors.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            snapshot.title?.let { title ->
                                Text(title, style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dateFormatted,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onSurface
                                )
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = colors.surfaceVariant,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${lineCount} ${text("行")}" + if (snapshot.files.isNotEmpty()) " +${snapshot.files.size} ${text("ファイル")}" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (isCurrent) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = text("（現在）"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                IconButton(
                                    onClick = { snapshotToDelete = snapshot },
                                    enabled = !busy && !loading && error == null,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_delete),
                                        contentDescription = text("削除"),
                                        tint = colors.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(4.dp))
                            snapshot.note?.let { note ->
                                Text(note, style = MaterialTheme.typography.bodySmall, maxLines = 3,
                                    overflow = TextOverflow.Ellipsis, color = colors.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                            }
                            Text(
                                text = snapshot.code.lineSequence()
                                    .firstOrNull { it.isNotBlank() }
                                    ?.trim()
                                    ?.take(90)
                                    ?: text("空のコード"),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = codeFontFamily,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(Modifier.height(8.dp))
                            TextButton(enabled = !busy && !loading && error == null, onClick = {
                                detailsTargetId = snapshot.id
                                detailsTitle = snapshot.title.orEmpty(); detailsNote = snapshot.note.orEmpty()
                                detailsPending = false; detailsOpen = true
                            }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) {
                                Text(text("名前・メモを編集"), fontSize = 12.sp)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { diffTarget = snapshot },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(text("差分を確認"), fontSize = 12.sp)
                                }
                                Spacer(Modifier.width(6.dp))
                                Button(
                                    onClick = { onRestoreSnapshot(snapshot) },
                                    enabled = !isCurrent && !busy && !loading && error == null,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(text("復元"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (detailsOpen) {
        val target = snapshots.firstOrNull { it.id == detailsTargetId }
        val normalized = runCatching { normalizedSnapshotDetails(detailsTitle, detailsNote) }.getOrNull()
        val enabled = !busy && !loading && error == null && normalized != null &&
            (detailsTargetId == null || target != null)
        EditSettingsDialog(onDismissRequest = { if (!busy) { detailsOpen = false; detailsPending = false } },
            title = { Text(text(if (detailsTargetId == null) "スナップショットを記録" else "名前・メモを編集")) },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = detailsTitle, onValueChange = { detailsTitle = it },
                        enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text(text("名前（任意）")) },
                        supportingText = { Text("${detailsTitle.length}/$MAX_SNAPSHOT_TITLE") },
                        isError = detailsTitle.trim().length > MAX_SNAPSHOT_TITLE)
                    OutlinedTextField(value = detailsNote, onValueChange = { detailsNote = it },
                        enabled = !busy, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 5,
                        label = { Text(text("メモ（任意）")) },
                        supportingText = { Text("${detailsNote.length}/$MAX_SNAPSHOT_NOTE") },
                        isError = detailsNote.trim().length > MAX_SNAPSHOT_NOTE)
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(enabled = enabled, onClick = {
                val details = normalized ?: return@TextButton
                detailsPending = true
                if (detailsTargetId == null) {
                    beforeCreateIds = snapshots.map { it.id }.toSet()
                    onCreateSnapshot(details.first, details.second)
                } else target?.let {
                    if (it.title == details.first && it.note == details.second) {
                        detailsOpen = false; detailsPending = false
                    } else onUpdateSnapshotDetails(it, details.first, details.second)
                }
            }) { Text(text("保存")) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { detailsOpen = false; detailsPending = false }) {
                Text(text("キャンセル"))
            } })
    }

    diffTarget?.let { target ->
        val difference by produceState<String?>(null, target, current) {
            value = null
            value = withContext(Dispatchers.Default) { snapshotDifference(current, target) }
        }
        AlertDialog(
            onDismissRequest = { diffTarget = null },
            title = { Text(text("変更内容を確認")) },
            text = {
                Column {
                    Text(
                        text("− 現在のコード（削除） / + スナップショット（追加）"),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    val diff = difference
                    if (diff == null) {
                        Text(text("スナップショットを読み込み中…"))
                    } else {
                        val lines = remember(diff) { diff.lines() }
                        Column(Modifier.fillMaxWidth().heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())
                            .width(IntrinsicSize.Max)) {
                            lines.forEach { line ->
                                val kind = diffLineKind(line)
                                val background = when (kind) {
                                    DiffLineKind.ADDED -> androidx.compose.ui.graphics.Color(0xFF388E3C).copy(alpha = 0.16f)
                                    DiffLineKind.REMOVED -> colors.errorContainer.copy(alpha = 0.35f)
                                    DiffLineKind.HEADER -> colors.surfaceVariant
                                    DiffLineKind.CONTEXT -> androidx.compose.ui.graphics.Color.Transparent
                                }
                                val foreground = when (kind) {
                                    DiffLineKind.ADDED -> if (colors.surface.luminance() < 0.5f)
                                        androidx.compose.ui.graphics.Color(0xFFA5D6A7) else androidx.compose.ui.graphics.Color(0xFF1B5E20)
                                    DiffLineKind.REMOVED -> colors.error
                                    else -> colors.onSurface
                                }
                                Text(line.ifEmpty { " " }, Modifier.fillMaxWidth().background(background)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontFamily = codeFontFamily, fontSize = 11.sp, softWrap = false,
                                    color = foreground, fontWeight = if (kind == DiffLineKind.HEADER) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toRestore = target
                        diffTarget = null
                        onRestoreSnapshot(toRestore)
                    },
                    enabled = !target.matches(current) && !busy && !loading && error == null
                ) {
                    Text(text("この状態に復元"))
                }
            },
            dismissButton = {
                TextButton(onClick = { diffTarget = null }) {
                    Text(text("閉じる"))
                }
            }
        )
    }

    snapshotToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { snapshotToDelete = null },
            title = { Text(text("スナップショットを削除")) },
            text = { Text(text("このスナップショットを削除しますか？この操作は取り消せません。")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = target
                        snapshotToDelete = null
                        onDeleteSnapshot(toDelete)
                    },
                    enabled = !busy && !loading && error == null
                ) {
                    Text(text("削除"), color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { snapshotToDelete = null }) {
                    Text(text("キャンセル"))
                }
            }
        )
    }
}
