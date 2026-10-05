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
import androidx.compose.ui.graphics.luminance
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
    onEditFile: (name: String, content: String) -> Unit,
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
                            IconButton(onClick = { onEditFile(name, content) }, modifier = Modifier.size(32.dp)) {
                                Icon(painterResource(R.drawable.ic_more_vertical),
                                    textTranslator("ファイル設定", emptyArray()), Modifier.size(18.dp))
                            }
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
    existingFileNames: Set<String> = emptySet(),
    onSave: (fileName: String, content: String) -> Unit,
    onDelete: (fileName: String) -> Unit,
    onDismiss: () -> Unit,
    textTranslator: (String, Array<out Any?>) -> String
) {
    var fileName by rememberSaveable(initialFileName) { mutableStateOf(initialFileName) }
    val fileContent = initialContent

    val normalizedName = fileName.trim()
    val syntax = projectTextSyntax(normalizedName)
    val nameExists = normalizedName != originalFileName && normalizedName in existingFileNames
    val validName = validProjectPath(normalizedName) && normalizedName != "sketch.js" && !nameExists
    val highlighter = editorHighlight(fileContent, colors.surface.luminance() < 0.5f, emptySet(), normalizedName)

    EditSettingsDialog(
        onDismissRequest = { if (!busy) onDismiss() },
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
                    onValueChange = { if (!busy) fileName = it },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(textTranslator("ファイル名・相対パス", emptyArray())) },
                    isError = fileName.isNotBlank() && !validName,
                    supportingText = if (nameExists) {
                        { Text(textTranslator("同じ名前のファイルが存在します", emptyArray())) }
                    } else null,
                    singleLine = true
                )
                OutlinedTextField(
                    value = fileContent,
                    onValueChange = { if (!busy) onContentChange(it) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    label = {
                        Text(
                            textTranslator(when (syntax) {
                                ProjectTextSyntax.JAVASCRIPT -> "JavaScriptコード"
                                ProjectTextSyntax.SHADER -> "シェーダーコード"
                                ProjectTextSyntax.HTML -> "HTML・マークアップ"
                                ProjectTextSyntax.CSS -> "CSSコード"
                                ProjectTextSyntax.JSON -> "JSONデータ"
                                ProjectTextSyntax.PLAIN -> "テキスト"
                            }, emptyArray())
                        )
                    },
                    textStyle = TextStyle(fontFeatureSettings = fontFeatures, fontFamily = codeFontFamily, fontSize = 13.sp),
                    visualTransformation = highlighter
                )
                Text(
                    textTranslator("index.html、style.css、.mjs、.json などのテキストと、scripts/main.js のような相対パスを使用できます。読み込み方法は実行環境で設定します。", emptyArray()),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                shape = ButtonDefaults.shape,
                onClick = { if (!busy && validName) onSave(normalizedName, fileContent) },
                enabled = validName && !busy
            ) {
                Text(textTranslator("保存", emptyArray()))
            }
        },
        dismissButton = {
            Row {
                originalFileName?.let { existingName ->
                    TextButton(
                        onClick = { if (!busy) onDelete(existingName) },
                        enabled = !busy
                    ) {
                        Text(textTranslator("削除", emptyArray()), color = colors.error)
                    }
                }
                TextButton(enabled = !busy, onClick = { if (!busy) onDismiss() }) {
                    Text(textTranslator("キャンセル", emptyArray()))
                }
            }
        }
    )
}
