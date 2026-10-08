package com.hikariatelier.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun UserTemplateChoices(
    templates: List<Work>, selectedId: String?, busy: Boolean, loadFailed: Boolean,
    text: (String) -> String, onRetry: () -> Unit, onSelect: (String) -> Unit, onDelete: (String) -> Unit,
    onManage: (() -> Unit)? = null
) {
    if (loadFailed) {
        Text(text("テンプレートを読み込めませんでした。保存データは変更していません。"), color = MaterialTheme.colorScheme.error)
        TextButton(enabled = !busy, onClick = onRetry) { Text(text("再試行")) }
        return
    }
    if (templates.isEmpty()) {
        Text(text("作品メニューの「テンプレートとして保存」から登録できます。"), style = MaterialTheme.typography.bodyMedium)
    } else {
        Text(text("コード・補助ファイル・素材・実行設定をそのまま引き継ぎます。"), style = MaterialTheme.typography.bodySmall)
        templates.forEach { template ->
            Column(Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = selectedId == template.id, enabled = !busy,
                    onClick = { onSelect(template.id) }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(template.title, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                )
                UserTemplateMetadata(template, text)
            }
        }
    }
    onManage?.let { action -> TextButton(enabled = !busy, onClick = action) { Text(text("テンプレート管理")) } }
}

@Composable
private fun UserTemplateMetadata(template: Work, text: (String) -> String) {
    Text("${text("ファイル")} ${1 + template.files.size} · ${text("素材")} ${template.assets.size} · p5.js ${template.p5Version}",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UserTemplateManagerSheet(
    templates: List<Work>, currentWorkTitle: String?, busy: Boolean, loadFailed: Boolean,
    text: (String) -> String, onRetry: () -> Unit, onRename: (String, String) -> Unit,
    onUpdate: (String) -> Unit, onDelete: (String) -> Unit, onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameTitle by rememberSaveable { mutableStateOf("") }
    var updatingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val visible = remember(templates, query) { matchingUserTemplates(templates, query) }
    val colors = MaterialTheme.colorScheme
    WorkSheet(title = text("テンプレート管理"), subtitle = "${templates.size}",
        onDismiss = { if (!busy) onDismiss() }, dismissEnabled = !busy, colors = colors,
        textTranslator = { source, _ -> text(source) }, windowSetup = { KeepLandscapeDialogImmersive() }) {
        OutlinedTextField(value = query, onValueChange = { query = it }, enabled = !busy,
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(text("テンプレート名で検索")) })
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        if (loadFailed) {
            Text(text("テンプレートを読み込めませんでした。保存データは変更していません。"),
                modifier = Modifier.padding(top = 8.dp), color = colors.error)
            TextButton(enabled = !busy, onClick = onRetry) { Text(text("再試行")) }
        } else {
            if (visible.isEmpty()) Text(text(if (templates.isEmpty())
                "作品メニューの「テンプレートとして保存」から登録できます。" else "一致するテンプレートはありません"),
                modifier = Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyMedium)
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visible, key = { it.id }, contentType = { "user_template" }) { template ->
                    Surface(color = colors.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(template.title, style = MaterialTheme.typography.titleSmall)
                            UserTemplateMetadata(template, text)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(enabled = !busy, onClick = {
                                    renamingId = template.id; renameTitle = template.title
                                }) { Text(text("名前を変更")) }
                                TextButton(enabled = !busy && currentWorkTitle != null, onClick = {
                                    updatingId = template.id
                                }) { Text(text("現在の作品から更新")) }
                                TextButton(enabled = !busy, onClick = { deletingId = template.id }) {
                                    Text(text("削除"), color = colors.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    templates.firstOrNull { it.id == renamingId }?.let { template ->
        EditSettingsDialog(onDismissRequest = { if (!busy) renamingId = null },
            title = { KeepLandscapeDialogImmersive(); Text(text("テンプレート名を変更")) },
            text = { OutlinedTextField(value = renameTitle, onValueChange = { renameTitle = it },
                enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(text("テンプレート名")) }) },
            confirmButton = { TextButton(enabled = !busy && renameTitle.isNotBlank(), onClick = {
                onRename(template.id, renameTitle.trim()); renamingId = null
            }) { Text(text("変更")) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { renamingId = null }) { Text(text("キャンセル")) } })
    }
    templates.firstOrNull { it.id == updatingId }?.let { template ->
        AlertDialog(onDismissRequest = { if (!busy) updatingId = null },
            title = { KeepLandscapeDialogImmersive(); Text(text("テンプレートを更新")) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(template.title)
                Text(text("現在の作品の編集内容・素材・実行設定で、テンプレートを上書きします。このテンプレートから作成済みの作品は変わりません。"))
                currentWorkTitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            } },
            confirmButton = { TextButton(enabled = !busy && currentWorkTitle != null, onClick = {
                onUpdate(template.id); updatingId = null
            }) { Text(text("更新")) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { updatingId = null }) { Text(text("キャンセル")) } })
    }
    templates.firstOrNull { it.id == deletingId }?.let { template ->
        AlertDialog(onDismissRequest = { if (!busy) deletingId = null },
            title = { KeepLandscapeDialogImmersive(); Text(text("テンプレートを削除")) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(template.title)
                Text(text("削除しても作成済みの作品には影響しません。"))
            } },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                onDelete(template.id); deletingId = null
            }) { Text(text("削除"), color = colors.error) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { deletingId = null }) { Text(text("キャンセル")) } })
    }
}

@Composable
internal fun SaveUserTemplateDialog(
    initialTitle: String, busy: Boolean, loadFailed: Boolean, text: (String) -> String,
    onRetry: () -> Unit, onSave: (String) -> Unit, onDismiss: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { KeepLandscapeDialogImmersive(); Text(text("テンプレートとして保存")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text("現在の編集内容・補助ファイル・素材・実行設定を保存します。"), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = title, onValueChange = { title = it }, enabled = !busy,
                    label = { Text(text("テンプレート名")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (loadFailed) {
                    Text(text("テンプレートを読み込めませんでした。保存データは変更していません。"), color = MaterialTheme.colorScheme.error)
                    TextButton(enabled = !busy, onClick = onRetry) { Text(text("再試行")) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && !loadFailed && title.isNotBlank(), onClick = { onSave(title.trim()) }) {
                Text(text("保存"))
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(text("キャンセル")) } }
    )
}
