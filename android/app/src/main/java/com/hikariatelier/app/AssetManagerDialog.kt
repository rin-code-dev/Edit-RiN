package com.hikariatelier.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AssetManagerDialog(
    assets: Map<String, ProjectAsset>, busy: Boolean, text: (String) -> String,
    onAdd: () -> Unit, onRename: (String, String) -> Unit, onDelete: (String) -> Unit, onClose: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var renaming by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(text("作品の素材")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text("画像・音声・動画・フォント・データを追加できます"))
                Text(text("1ファイル50MBまで、1作品200MB・100ファイルまで"), style = MaterialTheme.typography.bodySmall)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (assets.isEmpty()) Text(text("素材を追加すると、ここに表示されます"))
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(assets.toSortedMap().entries.toList(), key = { it.key }) { (name, asset) ->
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(name, style = MaterialTheme.typography.titleSmall)
                                Text("assets/$name", style = MaterialTheme.typography.bodySmall)
                                Text(android.text.format.Formatter.formatShortFileSize(androidx.compose.ui.platform.LocalContext.current, asset.size), style = MaterialTheme.typography.bodySmall)
                                FlowRow {
                                    TextButton(enabled = !busy, onClick = { clipboard.setText(AnnotatedString("assets/$name")) }) { Text(text("パスをコピー")) }
                                    TextButton(enabled = !busy, onClick = { renaming = name; newName = name }) { Text(text("名前を変更")) }
                                    TextButton(enabled = !busy, onClick = { deleting = name }) { Text(text("削除")) }
                                }
                            }
                        }
                    }
                }
                Text("loadImage(\"assets/photo.png\")", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onAdd) { Text(text("素材を追加")) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(text("閉じる")) } }
    )
    renaming?.let { old ->
        val valid = validAssetName(newName) && (newName == old || newName !in assets)
        AlertDialog(onDismissRequest = { renaming = null }, title = { Text(text("名前を変更")) },
            text = { Column {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true,
                    label = { Text(text("ファイル名")) }, isError = !valid)
                Text(text("コード内のパスも新しい名前に変更してください"), style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(enabled = valid, onClick = { onRename(old, newName); renaming = null }) { Text(text("保存")) } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(text("閉じる")) } })
    }
    deleting?.let { name ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text(text("素材を削除")) },
            text = { Text(name) },
            confirmButton = { TextButton(onClick = { onDelete(name); deleting = null }) { Text(text("削除")) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(text("閉じる")) } })
    }
}
