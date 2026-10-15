package com.hikariatelier.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AssetManagerDialog(
    assets: Map<String, ProjectAsset>,
    busy: Boolean,
    text: (String) -> String,
    onAdd: () -> Unit,
    onRename: (AssetRenameRequest) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
    onPreview: (String, ProjectAsset) -> Unit,
    onInsert: (String, ProjectAsset) -> Unit,
    referenceSources: Map<String, String> = emptyMap(),
    onDeleteMultiple: ((Set<String>) -> Unit)? = null
) {
    val copyToClipboard = rememberClipboardCopy()
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var renaming by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    var confirmDeleteUnused by remember { mutableStateOf(false) }
    var selectedReferences by remember { mutableStateOf<Set<AssetRenameCandidate>>(emptySet()) }
    var renameCandidates by remember { mutableStateOf<List<AssetRenameCandidate>?>(null) }
    var renamePending by remember { mutableStateOf(false) }

    val unusedAssets = remember(assets, referenceSources) {
        if (referenceSources.isEmpty()) emptySet<String>()
        else {
            val sources = referenceSources.values
            assets.keys.filter { name ->
                val query = "assets/$name"
                sources.none { code -> code.contains(query) }
            }.toSet()
        }
    }

    LaunchedEffect(assets, renamePending, renaming, newName) {
        if (renamePending && renaming !in assets && newName in assets) {
            renaming = null
            renamePending = false
        }
    }

    EditSettingsDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(text("作品の素材")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text("画像・音声・動画・フォント・データを追加できます"))
                Text(
                    text("1ファイル50MBまで、1作品200MB・100ファイルまで"),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (assets.isEmpty()) {
                    Text(
                        text("素材を追加すると、ここに表示されます"),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                if (unusedAssets.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${text("未使用素材")}: ${unusedAssets.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant
                        )
                        TextButton(
                            enabled = !busy,
                            onClick = { confirmDeleteUnused = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = colors.error
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = text("未使用素材を一括削除"),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.error
                            )
                        }
                    }
                }
                LazyColumn(
                    Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(assets.toSortedMap().entries.toList(), key = { it.key }, contentType = { "asset_item" }) { (name, asset) ->
                        val isAudio = asset.mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in setOf("mp3", "wav", "ogg", "m4a", "aac", "flac")
                        val isUnused = name in unusedAssets
                        Surface(
                            onClick = { if (!busy) onPreview(name, asset) },
                            enabled = !busy,
                            color = colors.surface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.75f))
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isAudio) {
                                                Surface(
                                                    color = colors.secondaryContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = text("音声"),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = colors.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            if (isUnused) {
                                                Surface(
                                                    color = colors.errorContainer.copy(alpha = 0.8f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = text("未使用"),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = colors.onErrorContainer,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "assets/$name · ${android.text.format.Formatter.formatShortFileSize(context, asset.size)}",
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            IconButton(
                                                enabled = !busy,
                                                modifier = Modifier.size(32.dp),
                                                onClick = { copyToClipboard("assets/$name") }
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_copy),
                                                    contentDescription = text("パスをコピー"),
                                                    modifier = Modifier.size(16.dp),
                                                    tint = colors.onSurfaceVariant
                                                )
                                            }
                                            IconButton(
                                                enabled = !busy,
                                                modifier = Modifier.size(32.dp),
                                                onClick = {
                                                    selectedReferences = emptySet()
                                                    renameCandidates = null
                                                    renamePending = false
                                                    renaming = name
                                                    newName = name
                                                }
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_rename),
                                                    contentDescription = text("名前を変更"),
                                                    modifier = Modifier.size(16.dp),
                                                    tint = colors.onSurfaceVariant
                                                )
                                            }
                                            IconButton(
                                                enabled = !busy,
                                                modifier = Modifier.size(32.dp),
                                                onClick = { deleting = name }
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_delete),
                                                    contentDescription = text("削除"),
                                                    modifier = Modifier.size(16.dp),
                                                    tint = colors.error.copy(alpha = 0.85f)
                                                )
                                            }
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        enabled = !busy,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(30.dp),
                                        onClick = { onInsert(name, asset) }
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_code),
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(text("読み込みコードを挿入"), style = MaterialTheme.typography.labelMedium)
                                    }
                                    TextButton(
                                        enabled = !busy,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(30.dp),
                                        onClick = { onPreview(name, asset) }
                                    ) {
                                        Text(text("プレビュー"), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    "loadImage(\"assets/photo.png\")",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onAdd) { Text(text("素材を追加")) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(text("閉じる")) } }
    )
    renaming?.let { old ->
        val valid = validAssetName(newName) && (newName == old || newName !in assets)
        LaunchedEffect(referenceSources, old) {
            renameCandidates = null
            selectedReferences = emptySet()
            renameCandidates = withContext(Dispatchers.Default) { assetRenameCandidates(referenceSources, old) }
        }
        EditSettingsDialog(
            onDismissRequest = { if (!busy) renaming = null },
            title = { Text(text("名前を変更")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        enabled = !busy,
                        singleLine = true,
                        label = { Text(text("ファイル名")) },
                        isError = !valid,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text("素材名と一緒に変更する参照パスを選んでください。選ばなかった箇所のコードは変わりません。"),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text("素材のパスと完全に一致する文字列だけを表示します。動的に組み立てたパスやテンプレート文字列は対象外です。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val candidates = renameCandidates
                    when {
                        candidates == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        candidates.isEmpty() -> Text(text("変更できる参照パスはありません"), style = MaterialTheme.typography.bodySmall)
                        else -> {
                            Text(text("コード内の参照候補"), style = MaterialTheme.typography.labelLarge)
                            LazyColumn(
                                Modifier.fillMaxWidth().heightIn(max = 240.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(candidates, key = { "${it.file}:${it.start}" }) { candidate ->
                                    val checked = candidate in selectedReferences
                                    fun toggle() {
                                        selectedReferences = if (checked) selectedReferences - candidate else selectedReferences + candidate
                                    }
                                    Row(Modifier.fillMaxWidth().clickable(enabled = !busy, onClick = ::toggle)) {
                                        Checkbox(checked = checked, enabled = !busy, onCheckedChange = { toggle() })
                                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                                            Text(
                                                "${candidate.file}:${candidate.line}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                candidate.excerpt,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                fontFamily = FontFamily.Monospace,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            if (valid) Text(
                                                "${candidate.original} → " +
                                                    (if (candidate.original.startsWith("./")) "./" else "") + "assets/$newName",
                                                fontFamily = FontFamily.Monospace,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = valid && !busy && renameCandidates != null,
                    onClick = {
                        if (old == newName) {
                            renaming = null
                            renamePending = false
                        } else {
                            renamePending = true
                            onRename(AssetRenameRequest(old, newName, selectedReferences.toList()))
                        }
                    }
                ) { Text(text("保存")) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { renaming = null }) { Text(text("閉じる")) } }
        )
    }
    deleting?.let { name ->
        EditSettingsDialog(
            onDismissRequest = { deleting = null },
            title = { Text(text("素材を削除")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name)
                    Text(text("削除してもコードは変更されません。"), style = MaterialTheme.typography.bodySmall)
                    AssetReferenceSummary(referenceSources, name, text)
                }
            },
            confirmButton = { TextButton(onClick = { onDelete(name); deleting = null }) { Text(text("削除")) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(text("閉じる")) } }
        )
    }
    if (confirmDeleteUnused) {
        EditSettingsDialog(
            onDismissRequest = { confirmDeleteUnused = false },
            title = { Text(text("未使用素材を一括削除")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(unusedAssets.sorted().joinToString(", "))
                    Text(text("削除してもコードは変更されません。"), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteUnused = false
                        if (onDeleteMultiple != null) {
                            onDeleteMultiple(unusedAssets)
                        } else {
                            unusedAssets.forEach { onDelete(it) }
                        }
                    }
                ) { Text(text("削除")) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteUnused = false }) { Text(text("閉じる")) } }
        )
    }
}

@Composable
private fun AssetReferenceSummary(sources: Map<String, String>, name: String, text: (String) -> String) {
    val result by produceState<ProjectSearchResults?>(null, sources, name) {
        value = withContext(Dispatchers.Default) { assetReferenceCandidates(sources, name) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text("コード内の参照候補"), style = MaterialTheme.typography.labelLarge)
        Text(
            text("素材のパスと一致する文字列を表示します。動的に組み立てたパスは検出できません。"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val found = result
        when {
            found == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            found.matches.isEmpty() -> Text(text("一致する参照候補はありません"), style = MaterialTheme.typography.bodySmall)
            else -> {
                if (found.truncated) Text(text("先頭50件を表示"), style = MaterialTheme.typography.labelSmall)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 160.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(found.matches, key = { "${it.file}:${it.start}" }) { match ->
                        Column {
                            Text(
                                "${match.file}:${match.line}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                match.excerpt,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
