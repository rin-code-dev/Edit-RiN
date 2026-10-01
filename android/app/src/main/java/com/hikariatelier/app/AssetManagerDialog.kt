package com.hikariatelier.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AssetManagerDialog(
    assets: Map<String, ProjectAsset>, busy: Boolean, text: (String) -> String,
    onAdd: () -> Unit, onRename: (String, String) -> Unit, onDelete: (String) -> Unit, onClose: () -> Unit,
    onPreview: (String, ProjectAsset) -> Unit, onInsert: (String, ProjectAsset) -> Unit,
    referenceSources: Map<String, String> = emptyMap()
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var renaming by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    EditSettingsDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(text("作品の素材")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text("画像・音声・動画・フォント・データを追加できます"))
                Text(text("1ファイル50MBまで、1作品200MB・100ファイルまで"), style = MaterialTheme.typography.bodySmall)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (assets.isEmpty()) Text(text("素材を追加すると、ここに表示されます"))
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(assets.toSortedMap().entries.toList(), key = { it.key }, contentType = { "asset_item" }) { (name, asset) ->
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(name, style = MaterialTheme.typography.titleSmall)
                                Text("assets/$name", style = MaterialTheme.typography.bodySmall)
                                Text(android.text.format.Formatter.formatShortFileSize(androidx.compose.ui.platform.LocalContext.current, asset.size), style = MaterialTheme.typography.bodySmall)
                                FlowRow {
                                    TextButton(enabled = !busy, onClick = { onPreview(name, asset) }) { Text(text("プレビュー")) }
                                    TextButton(enabled = !busy, onClick = { onInsert(name, asset) }) { Text(text("読み込みコードを挿入")) }
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
        EditSettingsDialog(onDismissRequest = { renaming = null }, title = { Text(text("名前を変更")) },
            text = { Column {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true,
                    label = { Text(text("ファイル名")) }, isError = !valid)
                Text(text("コード内のパスも新しい名前に変更してください"), style = MaterialTheme.typography.bodySmall)
                AssetReferenceSummary(referenceSources, old, text)
            } },
            confirmButton = { TextButton(enabled = valid, onClick = { onRename(old, newName); renaming = null }) { Text(text("保存")) } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(text("閉じる")) } })
    }
    deleting?.let { name ->
        EditSettingsDialog(onDismissRequest = { deleting = null }, title = { Text(text("素材を削除")) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name)
                Text(text("削除してもコードは変更されません。"), style = MaterialTheme.typography.bodySmall)
                AssetReferenceSummary(referenceSources, name, text)
            } },
            confirmButton = { TextButton(onClick = { onDelete(name); deleting = null }) { Text(text("削除")) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(text("閉じる")) } })
    }
}

@Composable
private fun AssetReferenceSummary(sources: Map<String, String>, name: String, text: (String) -> String) {
    val result by produceState<ProjectSearchResults?>(null, sources, name) {
        value = withContext(Dispatchers.Default) { assetReferenceCandidates(sources, name) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text("コード内の参照候補"), style = MaterialTheme.typography.labelLarge)
        Text(text("文字列が一致する箇所を表示します。動的なパスは検出できません。"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val found = result
        when {
            found == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            found.matches.isEmpty() -> Text(text("一致する参照候補はありません"), style = MaterialTheme.typography.bodySmall)
            else -> {
                if (found.truncated) Text(text("先頭50件を表示"), style = MaterialTheme.typography.labelSmall)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 160.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(found.matches, key = { "${it.file}:${it.start}" }) { match ->
                        Column {
                            Text("${match.file}:${match.line}", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            Text(match.excerpt, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
