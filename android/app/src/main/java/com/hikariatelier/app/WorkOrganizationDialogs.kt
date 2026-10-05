package com.hikariatelier.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun WorkOrganizationDialogs(model: WorkManagementViewModel, works: List<Work>,
    busy: Boolean, text: (String) -> String, windowSetup: @Composable () -> Unit = {}) {
    val sample = works.firstOrNull { it.id == model.copySampleId && it.isSample }
    val moving = model.galleryMoveIds.isNotEmpty()
    if (sample != null || moving) {
        var title by rememberSaveable(sample?.id, moving) { mutableStateOf(sample?.let { "${it.title} copy" }.orEmpty()) }
        var folder by rememberSaveable(sample?.id, moving) { mutableStateOf("") }
        fun dismiss() { model.copySampleId = null; model.galleryMoveIds = emptySet() }
        EditSettingsDialog(onDismissRequest = { if (!busy) dismiss() },
            title = { Text(text(if (sample != null) "コピーして編集" else "フォルダーへ移動")) },
            text = {
                windowSetup()
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    if (sample != null) {
                        Text(text("サンプル原本は変更せず、自分の作品にコピーします。"))
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(value = title, onValueChange = { title = it.take(120) },
                            enabled = !busy, singleLine = true, label = { Text(text("作品名")) })
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(text("保存先フォルダー"), style = MaterialTheme.typography.labelLarge)
                    (listOf("") + model.galleryFolders).forEach { name ->
                        FilterChip(selected = folder == name, enabled = !busy,
                            onClick = { folder = name }, label = { Text(if (name.isEmpty()) text("未分類") else name) })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && (sample == null || title.isNotBlank()) &&
                    (folder.isEmpty() || folder in model.galleryFolders), onClick = {
                    if (sample != null) model.copySample(title, folder)
                    else model.moveGalleryWorks(model.galleryMoveIds, folder)
                }) { Text(text(if (sample != null) "コピーして編集" else "移動")) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = ::dismiss) { Text(text("キャンセル")) } })
    }
    if (model.showGalleryFolders) {
        var selected by rememberSaveable { mutableStateOf<String?>(null) }
        var name by rememberSaveable { mutableStateOf("") }
        var deleting by rememberSaveable { mutableStateOf<String?>(null) }
        val normalized = name.trim()
        LaunchedEffect(model.galleryFolders, busy) {
            if (!busy && normalized in model.galleryFolders) selected = normalized
            else if (!busy && selected != null && selected !in model.galleryFolders) { selected = null; name = "" }
        }
        val valid = validGalleryFolder(normalized) && (normalized == selected || normalized !in model.galleryFolders)
        EditSettingsDialog(onDismissRequest = { if (!busy) model.showGalleryFolders = false },
            title = { Text(text("フォルダー管理")) }, text = {
                windowSetup()
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(text("フォルダーを削除しても作品は未分類に残ります。"))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = selected == null, enabled = !busy,
                            onClick = { selected = null; name = "" }, label = { Text(text("新規フォルダー")) })
                        model.galleryFolders.forEach { item ->
                            FilterChip(selected = selected == item, enabled = !busy,
                                onClick = { selected = item; name = item }, label = { Text(item) })
                        }
                    }
                    OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, singleLine = true,
                        enabled = !busy, label = { Text(text("フォルダー名")) },
                        isError = name.isNotBlank() && !valid)
                    Row {
                        TextButton(enabled = !busy && valid, onClick = {
                            val old = selected
                            if (old == null) model.createGalleryFolder(normalized) else model.renameGalleryFolder(old, normalized)
                        }) { Text(text(if (selected == null) "作成" else "名前変更")) }
                        TextButton(enabled = !busy && selected in model.galleryFolders, onClick = { deleting = selected }) {
                            Text(text("削除"))
                        }
                    }
                }
            }, confirmButton = {
                TextButton(enabled = !busy, onClick = { model.showGalleryFolders = false }) { Text(text("閉じる")) }
            })
        deleting?.let { target ->
            EditSettingsDialog(onDismissRequest = { if (!busy) deleting = null },
                title = { Text(target) }, text = { Text(text("フォルダーを削除しても作品は未分類に残ります。")) },
                confirmButton = { TextButton(enabled = !busy, onClick = {
                    model.deleteGalleryFolder(target); deleting = null; selected = null; name = ""
                }) { Text(text("削除")) } },
                dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null }) { Text(text("キャンセル")) } })
        }
    }
}
