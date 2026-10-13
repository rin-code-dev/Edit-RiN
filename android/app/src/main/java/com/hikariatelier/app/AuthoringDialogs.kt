package com.hikariatelier.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun AddParameterDialog(
    parameters: List<WorkParameter>,
    text: (String) -> String,
    onAdd: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    var kindName by rememberSaveable { mutableStateOf(ParameterKind.NUMBER.name) }
    val kind = ParameterKind.valueOf(kindName)
    var name by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("1") }
    var color by rememberSaveable { mutableStateOf("#BA90E2") }
    var boolean by rememberSaveable { mutableStateOf(true) }
    var min by rememberSaveable { mutableStateOf("0") }
    var max by rememberSaveable { mutableStateOf("3") }
    var step by rememberSaveable { mutableStateOf("0.1") }
    var failed by rememberSaveable { mutableStateOf(false) }
    val initial = when (kind) {
        ParameterKind.NUMBER -> number
        ParameterKind.COLOR -> color
        ParameterKind.BOOLEAN -> boolean.toString()
    }
    val declaration = parameterDeclaration(
        kind, name.trim(), label.trim(), initial.trim(), min.trim(),
        max.trim(), step.trim(), parameters.map { it.name }.toSet(), parameters.size
    )
    EditSettingsDialog(
        onDismissRequest = onDismiss,
        title = { Text(text("パラメータ追加")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ParameterKind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kindName = option.name },
                            label = { Text(text(option.title)) }
                        )
                    }
                }
                OutlinedTextField(name, { name = it; failed = false }, label = { Text(text("変数名（英字で開始）")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(label, { label = it; failed = false }, label = { Text(text("表示ラベル")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                when (kind) {
                    ParameterKind.NUMBER -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(number, { number = it }, label = { Text(text("初期値")) }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(step, { step = it }, label = { Text(text("ステップ")) }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(min, { min = it }, label = { Text(text("最小値")) }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(max, { max = it }, label = { Text(text("最大値")) }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                    ParameterKind.COLOR -> OutlinedTextField(color, { color = it }, label = { Text(text("6桁のカラーコード")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    ParameterKind.BOOLEAN -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(boolean, { boolean = it })
                        Text(text("初期値") + ": $boolean")
                    }
                }
                Text(
                    text("変数名は半角英字で始め、半角英数字と_を使ってください。ラベルは40文字以内です。作品ごとに最大16個まで追加できます。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                declaration?.let {
                    Text(it.trimEnd(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                if (failed) Text(text("パラメータを追加できませんでした。入力値を確認してください"), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = declaration != null,
                onClick = {
                    if (declaration != null && onAdd(declaration)) onDismiss() else failed = true
                }
            ) { Text(text("追加")) }
        },
        dismissButton = { TextButton(onDismiss) { Text(text("キャンセル")) } }
    )
}

@Composable
internal fun SnippetDialog(
    text: (String) -> String,
    onInsert: (CodeSnippet) -> Unit,
    onDismiss: () -> Unit
) {
    EditSettingsDialog(
        onDismissRequest = onDismiss,
        title = { Text(text("スニペット")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text("スニペットを選ぶと、カーソルの位置に挿入します。コードを選択中の場合は、その部分を置き換えます。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    codeSnippets.groupBy { it.category }.forEach { (category, snippets) ->
                        item {
                            Text(
                                text(category),
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        snippets.forEach { snippet ->
                            item {
                                ListItem(
                                    headlineContent = { Text(text(snippet.title)) },
                                    supportingContent = { Text(text(snippet.placement)) },
                                    modifier = Modifier.clickable { onInsert(snippet) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(text("閉じる")) } }
    )
}

@Composable
internal fun ScreenshotScaleDialog(
    text: (String) -> String,
    onCapture: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    EditSettingsDialog(
        onDismissRequest = onDismiss,
        title = { Text(text("PNG画像書き出し")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple(1, "1x", "1x（通常）"),
                        Triple(2, "2x", "2x（高精細）"),
                        Triple(4, "4x", "4x（超高精細）")
                    ).forEach { (scale, shortLabel, fullLabel) ->
                        Surface(
                            onClick = { onCapture(scale) },
                            modifier = Modifier.weight(1f).heightIn(min = 68.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = shortLabel,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primary
                                )
                                Text(
                                    text = text(fullLabel).substringAfter("（").removeSuffix("）").removeSuffix(")"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                Text(
                    text("画面外で高解像度に再描画し、PNG画像を保存します。上限は1600万画素です。アニメーションや乱数によって、フレームが進む場合があります。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(text("キャンセル")) } }
    )
}
