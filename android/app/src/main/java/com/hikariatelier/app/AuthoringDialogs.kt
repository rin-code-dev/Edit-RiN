package com.hikariatelier.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
internal fun AddParameterDialog(parameters: List<WorkParameter>, text: (String) -> String,
    onAdd: (String) -> Boolean, onDismiss: () -> Unit) {
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
    val initial = when (kind) { ParameterKind.NUMBER -> number; ParameterKind.COLOR -> color; ParameterKind.BOOLEAN -> boolean.toString() }
    val declaration = parameterDeclaration(kind, name.trim(), label.trim(), initial.trim(), min.trim(),
        max.trim(), step.trim(), parameters.map { it.name }.toSet(), parameters.size)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(text("パラメータ追加")) }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ParameterKind.entries.forEach { option ->
                    FilterChip(selected = kind == option, onClick = { kindName = option.name }, label = { Text(text(option.title)) })
                }
            }
            OutlinedTextField(name, { name = it; failed = false }, label = { Text(text("変数名（英字で開始）")) }, singleLine = true)
            OutlinedTextField(label, { label = it; failed = false }, label = { Text(text("表示ラベル")) }, singleLine = true)
            when (kind) {
                ParameterKind.NUMBER -> {
                    OutlinedTextField(number, { number = it }, label = { Text(text("初期値")) }, singleLine = true)
                    OutlinedTextField(min, { min = it }, label = { Text(text("最小値")) }, singleLine = true)
                    OutlinedTextField(max, { max = it }, label = { Text(text("最大値")) }, singleLine = true)
                    OutlinedTextField(step, { step = it }, label = { Text(text("ステップ")) }, singleLine = true)
                }
                ParameterKind.COLOR -> OutlinedTextField(color, { color = it }, label = { Text(text("6桁のカラーコード")) }, singleLine = true)
                ParameterKind.BOOLEAN -> Row { Checkbox(boolean, { boolean = it }); Text(text("初期値") + ": $boolean") }
            }
            Text(text("変数名は半角英字で始め、半角英数字と_を使ってください。ラベルは40文字以内です。作品ごとに最大16個まで追加できます。"),
                style = MaterialTheme.typography.bodySmall)
            declaration?.let { Text(it.trimEnd(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            if (failed) Text(text("パラメータを追加できませんでした。入力値を確認してください"), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = {
        TextButton(enabled = declaration != null, onClick = {
            if (declaration != null && onAdd(declaration)) onDismiss() else failed = true
        }) { Text(text("追加")) }
    }, dismissButton = { TextButton(onDismiss) { Text(text("キャンセル")) } })
}

@Composable
internal fun SnippetDialog(text: (String) -> String, onInsert: (CodeSnippet) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(text("スニペット")) }, text = {
        Column {
            Text(text("スニペットを選ぶと、カーソルの位置に挿入します。コードを選択中の場合は、その部分を置き換えます。"), style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                codeSnippets.groupBy { it.category }.forEach { (category, snippets) ->
                    item { Text(text(category), modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                    snippets.forEach { snippet ->
                        item {
                            ListItem(headlineContent = { Text(text(snippet.title)) },
                                supportingContent = { Text(text(snippet.placement)) },
                                modifier = Modifier.clickable { onInsert(snippet) })
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onDismiss) { Text(text("閉じる")) } })
}

@Composable
internal fun ScreenshotScaleDialog(text: (String) -> String, onCapture: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(text("PNG画像書き出し")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text("画面外で高解像度に再描画し、PNG画像を保存します。上限は1600万画素です。アニメーションや乱数によって、フレームが進む場合があります。"),
                style = MaterialTheme.typography.bodySmall)
            listOf(1 to "1x（通常）", 2 to "2x（高精細）", 4 to "4x（超高精細）").forEach { (scale, label) ->
                OutlinedButton(onClick = { onCapture(scale) }, modifier = Modifier.fillMaxWidth()) { Text(text(label)) }
            }
        }
    }, confirmButton = { TextButton(onDismiss) { Text(text("キャンセル")) } })
}
