package com.hikariatelier.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    var xyInitX by rememberSaveable { mutableStateOf("0.5") }
    var xyMinX by rememberSaveable { mutableStateOf("0") }
    var xyMaxX by rememberSaveable { mutableStateOf("1") }
    var xyInitY by rememberSaveable { mutableStateOf("0.5") }
    var xyMinY by rememberSaveable { mutableStateOf("0") }
    var xyMaxY by rememberSaveable { mutableStateOf("1") }
    var xyStep by rememberSaveable { mutableStateOf("0.01") }
    var failed by rememberSaveable { mutableStateOf(false) }
    val initial = when (kind) {
        ParameterKind.NUMBER -> number
        ParameterKind.COLOR -> color
        ParameterKind.BOOLEAN -> boolean.toString()
        ParameterKind.XY -> xyInitX
    }
    val declaration = if (kind == ParameterKind.XY) {
        parameterDeclaration(
            kind, name.trim(), label.trim(), xyInitX.trim(), xyMinX.trim(),
            xyMaxX.trim(), xyStep.trim(), parameters.map { it.name }.toSet(), parameters.size,
            minY = xyMinY.trim(), maxY = xyMaxY.trim(), initialY = xyInitY.trim(), stepY = xyStep.trim()
        )
    } else {
        parameterDeclaration(
            kind, name.trim(), label.trim(), initial.trim(), min.trim(),
            max.trim(), step.trim(), parameters.map { it.name }.toSet(), parameters.size
        )
    }
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
                    ParameterKind.XY -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(xyInitX, { xyInitX = it }, label = { Text("X " + text("初期値")) }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(xyInitY, { xyInitY = it }, label = { Text("Y " + text("初期値")) }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(xyMinX, { xyMinX = it }, label = { Text("X " + text("最小値")) }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(xyMaxX, { xyMaxX = it }, label = { Text("X " + text("最大値")) }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(xyMinY, { xyMinY = it }, label = { Text("Y " + text("最小値")) }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(xyMaxY, { xyMaxY = it }, label = { Text("Y " + text("最大値")) }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(xyStep, { xyStep = it }, label = { Text(text("ステップ")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
                                    modifier = Modifier
                                        .clip(UNIT_COMPACT_CONTROL_SHAPE)
                                        .clickable { onInsert(snippet) }
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
    onDismiss: () -> Unit,
    onSetAsThumbnail: (() -> Unit)? = null,
    hasManualThumbnail: Boolean = false,
    onResetThumbnail: (() -> Unit)? = null
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
                                    text = text(fullLabel).substringAfter("（").substringAfter("(").removeSuffix("）").removeSuffix(")"),
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
                if (onSetAsThumbnail != null) {
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = text("作品のサムネイル"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        FilledTonalButton(
                            onClick = onSetAsThumbnail,
                            shape = UNIT_CONTROL_SHAPE,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_camera),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(text("今のフレームをサムネイルに設定"))
                        }
                        if (hasManualThumbnail && onResetThumbnail != null) {
                            OutlinedButton(
                                onClick = onResetThumbnail,
                                shape = UNIT_CONTROL_SHAPE,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_restore),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(text("自動サムネイルに戻す"))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(text("キャンセル")) } }
    )
}

@Composable
internal fun CodeOutlineDialog(
    file: String,
    source: String,
    codeFontFamily: FontFamily,
    text: (String) -> String,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val allItems = remember(file, source) { extractCodeOutline(source, file) }
    var filter by rememberSaveable(file) { mutableStateOf("") }
    val filtered = remember(allItems, filter) {
        val query = filter.trim()
        if (query.isEmpty()) allItems
        else allItems.filter { it.name.contains(query, ignoreCase = true) }
    }
    val colors = MaterialTheme.colorScheme

    EditSettingsDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text("アウトライン"))
                Text(
                    file,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = codeFontFamily,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (allItems.size > 6) {
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(text("関数・セクションを絞り込む")) },
                        shape = UNIT_CONTROL_SHAPE
                    )
                }
                if (filtered.isEmpty()) {
                    Text(
                        text("関数や // --- セクション見出しが見つかりません"),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filtered, key = { "${it.kind.name}:${it.line}:${it.name}" }) { item ->
                            val badge = when (item.kind) {
                                CodeOutlineKind.FUNCTION -> "fn"
                                CodeOutlineKind.CLASS -> "cls"
                                CodeOutlineKind.SECTION -> "§"
                            }
                            val badgeColor = when (item.kind) {
                                CodeOutlineKind.FUNCTION -> colors.primary
                                CodeOutlineKind.CLASS -> colors.tertiary
                                CodeOutlineKind.SECTION -> colors.secondary
                            }
                            Surface(
                                shape = UNIT_CONTROL_SHAPE,
                                color = colors.surfaceVariant.copy(alpha = 0.42f),
                                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(UNIT_CONTROL_SHAPE)
                                    .clickable {
                                        onJump(item.line)
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = badgeColor.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = badge,
                                            fontFamily = codeFontFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = item.name,
                                        fontFamily = if (item.kind == CodeOutlineKind.SECTION) FontFamily.Default else codeFontFamily,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (item.kind == CodeOutlineKind.SECTION) FontWeight.SemiBold else FontWeight.Medium,
                                        color = colors.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "L${item.line}",
                                        fontFamily = codeFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(text("閉じる")) } }
    )
}
