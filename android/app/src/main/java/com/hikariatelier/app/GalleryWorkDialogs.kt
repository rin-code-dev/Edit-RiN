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

/** Dialogs use work IDs rather than switching the active editor to the target work. */
@Composable
internal fun GalleryWorkDialogs(
    model: WorkManagementViewModel,
    works: List<Work>,
    busy: Boolean,
    text: (String) -> String,
    windowSetup: @Composable () -> Unit = {}
) {
    val renameWork = works.firstOrNull { it.id == model.galleryRenameWorkId }
    if (renameWork != null) {
        var title by rememberSaveable(renameWork.id) { mutableStateOf(renameWork.title) }
        EditSettingsDialog(onDismissRequest = { if (!busy) model.galleryRenameWorkId = null },
            title = { Text(text("名前変更")) },
            text = {
                windowSetup()
                OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true,
                    enabled = !busy, label = { Text(text("作品名")) })
            },
            confirmButton = {
                TextButton(enabled = !busy && title.isNotBlank(),
                    onClick = { model.renameWork(renameWork.id, title.trim()) }) { Text(text("保存")) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { model.galleryRenameWorkId = null }) { Text(text("キャンセル")) }
            })
    }
    val deleted = works.filter { it.id in model.galleryDeleteIds }
    if (deleted.isNotEmpty()) {
        EditSettingsDialog(onDismissRequest = { if (!busy) model.galleryDeleteIds = emptySet() },
            title = { Text(text("選択した作品を削除")) },
            text = {
                windowSetup()
                Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    Text(text("次の作品を削除します。削除後に「元に戻す」で取り消せます。"))
                    Spacer(Modifier.height(12.dp))
                    deleted.forEach { Text(it.title) }
                    if (deleted.size >= works.size) {
                        Spacer(Modifier.height(12.dp))
                        Text(text("最後の1作品は削除できません"))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && deleted.size < works.size,
                    onClick = { model.deleteGalleryWorks(deleted.map { it.id }.toSet()) }) { Text(text("削除")) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { model.galleryDeleteIds = emptySet() }) { Text(text("キャンセル")) }
            })
    }
    val tagged = works.filter { it.id in model.galleryTagIds }
    if (tagged.isNotEmpty()) {
        var input by rememberSaveable(model.galleryTagIds) { mutableStateOf("") }
        val tag = input.trim().removePrefix("#").trim()
        val canRemove = tagged.any { work -> work.tags.any { it.equals(tag, true) } }
        val known = works.flatMap { it.tags }.distinct().sorted()
        EditSettingsDialog(onDismissRequest = { if (!busy) model.galleryTagIds = emptySet() },
            title = { Text(text("選択した作品にタグ付け")) },
            text = {
                windowSetup()
                Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    Text("${tagged.size} · ${text("選択中")}")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = input, onValueChange = { input = it }, singleLine = true,
                        enabled = !busy, label = { Text(text("新しいタグを入力")) })
                    if (known.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        known.forEach { name ->
                            SuggestionChip(enabled = !busy, onClick = { input = name }, label = { Text("#$name") })
                        }
                    }
                    TextButton(enabled = !busy && tag.isNotEmpty() && canRemove,
                        onClick = { model.tagGalleryWorks(tagged.map { it.id }.toSet(), tag, remove = true) }) {
                        Text(text("選択した作品からタグを外す"))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && tag.isNotEmpty(),
                    onClick = { model.tagGalleryWorks(tagged.map { it.id }.toSet(), tag) }) { Text(text("タグを追加")) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { model.galleryTagIds = emptySet() }) { Text(text("キャンセル")) }
            })
    }
}
