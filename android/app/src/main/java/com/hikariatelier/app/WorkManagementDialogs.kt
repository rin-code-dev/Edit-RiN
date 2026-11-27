package com.hikariatelier.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun RenameWorkDialog(
    initialTitle: String,
    colors: ColorScheme,
    onDismiss: () -> Unit,
    onConfirmRename: (String) -> Unit,
    textTranslator: (String, Array<out Any?>) -> String,
    windowSetup: @Composable () -> Unit = {}
) {
    fun uiText(source: String, vararg arguments: Any?): String = textTranslator(source, arguments)

    var renamedTitle by rememberSaveable(initialTitle) { mutableStateOf(initialTitle) }

    EditSettingsDialog(
        onDismissRequest = onDismiss,
        title = {
            windowSetup()
            Text(uiText("作品名を変更"))
        },
        text = {
            OutlinedTextField(
                value = renamedTitle,
                onValueChange = { renamedTitle = it },
                label = { Text(uiText("作品名")) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmRename(renamedTitle.trim().ifBlank { initialTitle }) }
            ) {
                Text(uiText("変更"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(uiText("キャンセル"))
            }
        }
    )
}

@Composable
internal fun DeleteWorkDialog(
    workTitle: String?,
    colors: ColorScheme,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
    textTranslator: (String, Array<out Any?>) -> String,
    windowSetup: @Composable () -> Unit = {}
) {
    fun uiText(source: String, vararg arguments: Any?): String = textTranslator(source, arguments)

    EditSettingsDialog(
        onDismissRequest = onDismiss,
        title = {
            windowSetup()
            Text(uiText("作品を削除"))
        },
        text = {
            Text(uiText("「%s」を削除しますか？", workTitle))
        },
        confirmButton = {
            TextButton(onClick = onConfirmDelete) {
                Text(uiText("削除"), color = colors.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(uiText("キャンセル"))
            }
        }
    )
}

@Composable
internal fun FolderSelectionPromptDialog(
    colors: ColorScheme,
    onChooseFolder: () -> Unit,
    textTranslator: (String, Array<out Any?>) -> String,
    windowSetup: @Composable () -> Unit = {}
) {
    UnitAlertDialog(
        onDismissRequest = {},
        title = {
            windowSetup()
            Text(textTranslator("作品フォルダーを選択", emptyArray()))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    textTranslator(
                        "作品のコードや画像・音声素材を安全に保存するため、保存先フォルダーを選んでください。バックアップや、外部のファイル管理アプリとの連携に使います。",
                        emptyArray()
                    )
                )
                Text(
                    textTranslator(
                        "「Documents」などに「Edit-RiN」フォルダーを作成して選ぶのがおすすめです。以前の作品フォルダーがある場合は、そのフォルダーを選ぶと作品を復元できます。",
                        emptyArray()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                shape = UNIT_CONTROL_SHAPE,
                onClick = onChooseFolder
            ) {
                Text(textTranslator("フォルダーを選択", emptyArray()))
            }
        }
    )
}

@Composable
internal fun SampleUpdatePromptDialog(
    sampleNames: String,
    onAddSamples: () -> Unit,
    onDismiss: () -> Unit,
    textTranslator: (String, Array<out Any?>) -> String,
    windowSetup: @Composable () -> Unit = {}
) {
    UnitAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            windowSetup()
            Text(textTranslator("新しいサンプル作品の追加", emptyArray()))
        },
        text = {
            Text(
                textTranslator(
                    "新しく追加された公式サンプル（%s）を現在の作品フォルダーに追加しますか？\n\n※既存の作品はそのまま保持されます。",
                    arrayOf(sampleNames)
                )
            )
        },
        confirmButton = {
            Button(
                shape = UNIT_CONTROL_SHAPE,
                onClick = onAddSamples
            ) {
                Text(textTranslator("作品一覧に追加", emptyArray()))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = UNIT_CONTROL_SHAPE) {
                Text(textTranslator("あとで", emptyArray()))
            }
        }
    )
}
