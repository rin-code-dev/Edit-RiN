package com.hikariatelier.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ProjectFilesDialog(
    activeWorkFiles: Map<String, String>,
    colors: ColorScheme,
    codeFontFamily: FontFamily,
    onSelectFile: (name: String, content: String) -> Unit,
    onAddNewFile: () -> Unit,
    onDismiss: () -> Unit,
    textTranslator: (String, Array<out Any?>) -> String
) {
    EditSettingsDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(painterResource(R.drawable.ic_folder_code), contentDescription = null)
        },
        title = { Text(textTranslator("プロジェクトファイル", emptyArray())) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = colors.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_code),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("sketch.js", fontFamily = codeFontFamily)
                        Spacer(Modifier.weight(1f))
                        Text("MAIN", style = MaterialTheme.typography.labelSmall)
                    }
                }
                activeWorkFiles.toSortedMap().forEach { (name, content) ->
                    Surface(
                        onClick = {
                            onSelectFile(name, content)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.outlineVariant),
                        color = colors.surface,
                        contentColor = colors.onSurface
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_code),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(name, modifier = Modifier.weight(1f), fontFamily = codeFontFamily)
                            Spacer(Modifier.width(8.dp))
                            Text(textTranslator("編集", emptyArray()), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAddNewFile) {
                Icon(
                    painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(textTranslator("ファイルを追加", emptyArray()))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(textTranslator("閉じる", emptyArray()))
            }
        }
    )
}

@Composable
internal fun AuxiliaryFileEditorDialog(
    originalFileName: String?,
    initialFileName: String,
    initialContent: String,
    onContentChange: (String) -> Unit,
    fontFeatures: String,
    codeFontFamily: FontFamily,
    colors: ColorScheme,
    busy: Boolean,
    onSave: (fileName: String, content: String) -> Unit,
    onDelete: (fileName: String) -> Unit,
    onDismiss: () -> Unit,
    textTranslator: (String, Array<out Any?>) -> String
) {
    var fileName by rememberSaveable(initialFileName) { mutableStateOf(initialFileName) }
    val fileContent = initialContent

    val normalizedName = fileName.trim()
    val isShader = normalizedName.endsWith(".frag", ignoreCase = true) ||
        normalizedName.endsWith(".vert", ignoreCase = true) ||
        normalizedName.endsWith(".glsl", ignoreCase = true)
    val validName = normalizedName.matches(Regex("[A-Za-z0-9._-]+\\.(js|frag|vert|glsl)", RegexOption.IGNORE_CASE)) &&
        normalizedName != "sketch.js"

    EditSettingsDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (originalFileName == null) textTranslator("ファイルを追加", emptyArray())
                else textTranslator("ファイルを編集", emptyArray())
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(textTranslator("ファイル名（.js / .frag / .vert）", emptyArray())) },
                    isError = fileName.isNotBlank() && !validName,
                    singleLine = true
                )
                OutlinedTextField(
                    value = fileContent,
                    onValueChange = onContentChange,
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    label = {
                        Text(
                            if (isShader) textTranslator("シェーダーコード", emptyArray())
                            else textTranslator("JavaScriptコード", emptyArray())
                        )
                    },
                    textStyle = TextStyle(fontFeatureSettings = fontFeatures, fontFamily = codeFontFamily, fontSize = 13.sp)
                )
                Text(
                    if (isShader) textTranslator("シェーダーは loadShader() や rinShaders で読み込み可能です。", emptyArray())
                    else textTranslator("追加ファイルは名前順にsketch.jsより先に実行されます。", emptyArray()),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                shape = ButtonDefaults.shape,
                onClick = { onSave(normalizedName, fileContent) },
                enabled = validName && !busy
            ) {
                Text(textTranslator("保存", emptyArray()))
            }
        },
        dismissButton = {
            Row {
                originalFileName?.let { existingName ->
                    TextButton(
                        onClick = { onDelete(existingName) },
                        enabled = !busy
                    ) {
                        Text(textTranslator("削除", emptyArray()), color = colors.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(textTranslator("キャンセル", emptyArray()))
                }
            }
        }
    )
}
