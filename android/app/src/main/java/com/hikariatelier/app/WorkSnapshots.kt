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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class WorkSnapshot(
    val id: String = UUID.randomUUID().toString(),
    val savedAt: Long = System.currentTimeMillis(),
    val code: String,
    val files: Map<String, String> = emptyMap(),
    val parameterValues: Map<String, String> = emptyMap(),
    val note: String? = null
)

internal object WorkSnapshotStore {
    private fun snapshotFile(filesDir: File, workId: String): File {
        val dir = File(filesDir, "snapshots").apply { if (!exists()) mkdirs() }
        return File(dir, "$workId.json")
    }

    fun loadSnapshots(
        filesDir: File,
        workId: String,
        fallbackRevisions: List<WorkRevision> = emptyList()
    ): List<WorkSnapshot> {
        if (workId.isBlank()) return emptyList()
        val file = snapshotFile(filesDir, workId)
        if (file.exists()) {
            return runCatching {
                val array = JSONArray(file.readText())
                val list = mutableListOf<WorkSnapshot>()
                for (idx in 0 until array.length()) {
                    val obj = array.getJSONObject(idx)
                    val filesObj = obj.optJSONObject("files")
                    val filesMap = filesObj?.let { fo ->
                        fo.keys().asSequence().associateWith { fo.getString(it) }
                    } ?: emptyMap()
                    val paramsObj = obj.optJSONObject("parameterValues")
                    val paramsMap = paramsObj?.let { po ->
                        po.keys().asSequence().associateWith { po.getString(it) }
                    } ?: emptyMap()
                    list.add(
                        WorkSnapshot(
                            id = obj.optString("id", idx.toString()),
                            savedAt = obj.optLong("savedAt", System.currentTimeMillis()),
                            code = obj.getString("code"),
                            files = filesMap,
                            parameterValues = paramsMap,
                            note = obj.optString("note").takeIf { !it.isNullOrBlank() }
                        )
                    )
                }
                list.sortedByDescending { it.savedAt }
            }.getOrDefault(emptyList())
        }

        // Migrate from fallbackRevisions if available
        if (fallbackRevisions.isNotEmpty()) {
            val migrated = fallbackRevisions.takeLast(10).mapIndexed { idx, rev ->
                WorkSnapshot(
                    id = "migrated_$idx",
                    savedAt = rev.savedAt,
                    code = rev.code
                )
            }.sortedByDescending { it.savedAt }
            saveSnapshots(filesDir, workId, migrated)
            return migrated
        }

        return emptyList()
    }

    fun saveSnapshots(filesDir: File, workId: String, snapshots: List<WorkSnapshot>) {
        if (workId.isBlank()) return
        val file = snapshotFile(filesDir, workId)
        runCatching {
            val array = JSONArray()
            // Keep up to 15 snapshots per work
            snapshots.take(15).reversed().forEach { snapshot ->
                array.put(JSONObject().apply {
                    put("id", snapshot.id)
                    put("savedAt", snapshot.savedAt)
                    put("code", snapshot.code)
                    put("files", JSONObject(snapshot.files))
                    put("parameterValues", JSONObject(snapshot.parameterValues))
                    if (!snapshot.note.isNullOrBlank()) {
                        put("note", snapshot.note)
                    }
                })
            }
            file.writeText(array.toString(2))
        }
    }

    fun addSnapshot(
        filesDir: File,
        workId: String,
        code: String,
        files: Map<String, String>,
        parameterValues: Map<String, String>,
        note: String? = null
    ): List<WorkSnapshot> {
        val current = loadSnapshots(filesDir, workId)
        val newSnapshot = WorkSnapshot(
            code = code,
            files = files,
            parameterValues = parameterValues,
            note = note
        )
        val updated = (listOf(newSnapshot) + current).take(15)
        saveSnapshots(filesDir, workId, updated)
        return updated
    }

    fun deleteSnapshot(filesDir: File, workId: String, snapshotId: String): List<WorkSnapshot> {
        val current = loadSnapshots(filesDir, workId)
        val updated = current.filterNot { it.id == snapshotId }
        saveSnapshots(filesDir, workId, updated)
        return updated
    }
}

@Composable
internal fun SnapshotSheet(
    snapshots: List<WorkSnapshot>,
    currentCode: String,
    codeFontFamily: FontFamily,
    onCreateSnapshot: () -> Unit,
    onRestoreSnapshot: (WorkSnapshot) -> Unit,
    onDeleteSnapshot: (WorkSnapshot) -> Unit,
    text: (String) -> String
) {
    val colors = MaterialTheme.colorScheme
    var diffTarget by remember { mutableStateOf<WorkSnapshot?>(null) }
    var snapshotToDelete by remember { mutableStateOf<WorkSnapshot?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        // Create Snapshot Action
        FilledTonalButton(
            onClick = onCreateSnapshot,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_restore),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(text("現在の状態をスナップショット（保存）"), fontWeight = FontWeight.SemiBold)
        }

        if (snapshots.isEmpty()) {
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
                items(snapshots, key = { it.id }) { snapshot ->
                    val isCurrent = snapshot.code == currentCode
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
                                    enabled = !isCurrent,
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

    diffTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { diffTarget = null },
            title = { Text(text("変更内容を確認")) },
            text = {
                Column {
                    Text(
                        text("− 現在のコード / + スナップショットのコード"),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = revisionDifference(currentCode, target.code),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState()),
                        fontFamily = codeFontFamily,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toRestore = target
                        diffTarget = null
                        onRestoreSnapshot(toRestore)
                    },
                    enabled = target.code != currentCode
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
                    }
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
