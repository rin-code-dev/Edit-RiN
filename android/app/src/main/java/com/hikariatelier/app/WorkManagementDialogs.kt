package com.hikariatelier.app

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun AddWorkDialog(
    worksCount: Int,
    workTemplates: List<WorkTemplate>,
    wideWorkPanels: Boolean,
    colors: ColorScheme,
    codeFontFamily: FontFamily,
    configuration: Configuration,
    onDismiss: () -> Unit,
    onCreate: (title: String, ratio: String, sizingMode: CanvasSizingMode, template: WorkTemplate) -> Unit,
    userTemplates: List<Work>,
    busy: Boolean,
    templateLoadFailed: Boolean,
    onRetryTemplates: () -> Unit,
    onCreateFromTemplate: (String, String) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onManageTemplates: (() -> Unit)? = null,
    textTranslator: (String, Array<out Any?>) -> String,
    windowSetup: @Composable () -> Unit = {}
) {
    fun uiText(source: String, vararg arguments: Any?): String = textTranslator(source, arguments)

    var newTitle by rememberSaveable {
        mutableStateOf(uiText("新しい作品") + " ${worksCount + 1}")
    }
    var customTab by rememberSaveable { mutableStateOf(false) }
    var selectedTemplateId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingTemplateId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedUserTemplate = userTemplates.firstOrNull { it.id == selectedTemplateId }
    var newRatio by rememberSaveable { mutableStateOf("1:1") }
    var customWidth by rememberSaveable { mutableStateOf("800") }
    var customHeight by rememberSaveable { mutableStateOf("800") }
    var newCanvasModeName by rememberSaveable { mutableStateOf(CanvasSizingMode.FIXED.name) }
    val newCanvasMode = CanvasSizingMode.valueOf(newCanvasModeName)
    var kindName by rememberSaveable { mutableStateOf(WorkTemplateKind.BASIC_2D.name) }
    val kind = WorkTemplateKind.valueOf(kindName)
    val maxSide = maxOf(configuration.screenWidthDp, configuration.screenHeightDp, 1)
    val minSide = minOf(configuration.screenWidthDp, configuration.screenHeightDp, 1)
    val scale = 960f / maxSide
    val portrait = WorkTemplate("device", (minSide * scale).toInt().coerceAtLeast(1), (maxSide * scale).toInt().coerceAtLeast(1))
    val landscape = WorkTemplate("device_landscape", portrait.height, portrait.width)
    val options = listOf(landscape) + workTemplates + listOf(portrait)
    val width = customWidth.toIntOrNull()
    val height = customHeight.toIntOrNull()
    val validCustom = customCanvasDimensionsValid(width, height)
    val template = if (newRatio == "custom" && validCustom) {
        WorkTemplate(ratioFromDimensions(width!!, height!!), width, height, kind)
    } else (options.firstOrNull { it.ratio == newRatio } ?: workTemplates.first { it.ratio == "1:1" }).copy(kind = kind)

    WorkSheet(
        title = uiText("新しい作品"),
        subtitle = uiText("テンプレートとキャンバスを選択"),
        onDismiss = { if (!busy) onDismiss() },
        dismissEnabled = !busy,
        colors = colors,
        textTranslator = textTranslator,
        windowSetup = windowSetup
    ) {
        Column(
            Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, enabled = !busy,
                label = { Text(uiText("作品名")) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !customTab, enabled = !busy, onClick = { customTab = false }, label = { Text(uiText("標準テンプレート")) })
                FilterChip(selected = customTab, enabled = !busy, onClick = { customTab = true }, label = { Text(uiText("自作テンプレート")) })
            }
            if (customTab) {
                UserTemplateChoices(templates = userTemplates, selectedId = selectedTemplateId,
                    busy = busy, loadFailed = templateLoadFailed, text = { uiText(it) }, onRetry = onRetryTemplates,
                    onSelect = { selectedTemplateId = it }, onDelete = { deletingTemplateId = it }, onManage = onManageTemplates)
            } else {
                @Composable
                fun TemplateChoices(columns: Int) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(false, true).forEach { advanced ->
                            Text(uiText(if (advanced) "応用テンプレート" else "基本テンプレート"),
                                style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                            WorkTemplateKind.entries.filter { it.advanced == advanced }.chunked(columns).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { option ->
                                        val selected = kind == option
                                        Surface(onClick = { kindName = option.name }, enabled = !busy,
                                            modifier = Modifier.weight(1f).semantics { this.selected = selected; role = Role.RadioButton },
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
                                            border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant)) {
                                            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(uiText(option.title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                                                if (selected) Text("✓", color = colors.primary)
                                            }
                                        }
                                    }
                                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
                @Composable
                fun TemplateDetail() {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(uiText(kind.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(uiText(kind.description), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                            TemplateLivePreview(kind, colors) { uiText(it) }
                        }
                        Text(uiText("動作見本（作品のサイズとは異なります）"), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                }
                if (wideWorkPanels) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) { TemplateChoices(1) }
                        Column(Modifier.weight(1f)) { TemplateDetail() }
                    }
                } else {
                    TemplateDetail()
                    TemplateChoices(if (configuration.fontScale > 1.4f || configuration.screenWidthDp < 340) 1 else 2)
                }
                Text(uiText("キャンバスの動作"), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(CanvasSizingMode.FIXED, CanvasSizingMode.RESPONSIVE).forEach { mode ->
                        val selected = newCanvasMode == mode
                        val enabled = !busy && (mode != CanvasSizingMode.FIXED || newRatio != "device") &&
                            (mode != CanvasSizingMode.RESPONSIVE || newRatio != "custom")
                        Surface(onClick = { newCanvasModeName = mode.name }, enabled = enabled,
                            modifier = Modifier.weight(1f).semantics { this.selected = selected; role = Role.RadioButton },
                            shape = RoundedCornerShape(14.dp), color = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
                            border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(uiText(if (mode == CanvasSizingMode.FIXED) "固定サイズ" else "画面に合わせる"),
                                    fontWeight = FontWeight.SemiBold, color = if (enabled) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.5f))
                                Text(uiText(if (mode == CanvasSizingMode.FIXED) "指定した描画サイズを維持し、表示だけを枠に合わせます" else "作業領域に合わせてresizeCanvas()を実行します"),
                                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
                Text(uiText("プレビュー比率"), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = when { configuration.fontScale > 1.5f || maxWidth < 280.dp -> 1; maxWidth >= 540.dp -> 3; else -> 2 }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (options.map { it.ratio } + "custom").chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { ratio ->
                                    val selected = newRatio == ratio
                                    val option = options.firstOrNull { it.ratio == ratio }
                                    Surface(onClick = {
                                        newRatio = ratio
                                        if (ratio == "device" || ratio == "device_landscape") newCanvasModeName = CanvasSizingMode.RESPONSIVE.name
                                        if (ratio == "custom") newCanvasModeName = CanvasSizingMode.FIXED.name
                                    }, enabled = !busy, modifier = Modifier.weight(1f).fillMaxHeight().semantics { this.selected = selected; role = Role.RadioButton },
                                        shape = RoundedCornerShape(14.dp), color = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
                                        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant)) {
                                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                                                val aspect = option?.let { it.width.toFloat() / it.height } ?: 1f
                                                val size = fitPreviewSize(26f, 26f, aspect)
                                                Box(Modifier.size(size.width.dp, size.height.dp).border(1.dp, if (selected) colors.primary else colors.onSurfaceVariant, RoundedCornerShape(3.dp)))
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Text(when (ratio) { "device_landscape" -> uiText("端末・横"); "device" -> uiText("端末・縦"); "custom" -> uiText("自由指定"); else -> ratio }, style = MaterialTheme.typography.labelLarge)
                                                if (option != null && ratio != "device" && ratio != "device_landscape") Text("${option.width} × ${option.height}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                                            }
                                            if (selected) Text("✓", color = colors.primary)
                                        }
                                    }
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                if (newRatio == "custom") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = customWidth, onValueChange = { customWidth = it }, enabled = !busy,
                            label = { Text(uiText("幅（px）")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, isError = !validCustom, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = customHeight, onValueChange = { customHeight = it }, enabled = !busy,
                            label = { Text(uiText("高さ（px）")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, isError = !validCustom, modifier = Modifier.weight(1f))
                    }
                    Text(uiText(if (validCustom) "入力した幅と高さからプレビュー比率を設定します。" else "幅と高さは1〜8192の整数、合計1600万ピクセル以内にしてください。"),
                        style = MaterialTheme.typography.bodySmall, color = if (validCustom) colors.onSurfaceVariant else colors.error)
                }
                if (newCanvasMode == CanvasSizingMode.RESPONSIVE) {
                    Text(uiText("選んだ比率はプレビュー枠に適用されます。キャンバスは画面に合わせて変化します。"),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(14.dp), color = colors.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(uiText(kind.title) + " · " + if (newRatio == "custom" && !validCustom) "—" else previewAspectRatioLabel(template.ratio) { uiText(it) }, style = MaterialTheme.typography.labelLarge)
                        Text(if (newCanvasMode == CanvasSizingMode.FIXED) "createCanvas(${template.width}, ${template.height});" else "createCanvas(windowWidth, windowHeight);",
                            fontFamily = codeFontFamily, style = MaterialTheme.typography.bodySmall, color = colors.primary)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Row(Modifier.fillMaxWidth().imePadding().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(uiText("キャンセル")) }
            Button(enabled = !busy && (if (customTab) selectedUserTemplate != null && !templateLoadFailed else newRatio != "custom" || validCustom),
                onClick = { if (customTab) selectedUserTemplate?.let { onCreateFromTemplate(newTitle, it.id) } else onCreate(newTitle, template.ratio, newCanvasMode, template) },
                shape = RoundedCornerShape(16.dp)) {
                Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(uiText("作成"))
            }
        }
    }
    userTemplates.firstOrNull { it.id == deletingTemplateId }?.let { deleting ->
        AlertDialog(
            onDismissRequest = { if (!busy) deletingTemplateId = null },
            title = { Text(uiText("テンプレートを削除")) },
            text = { Text(uiText("「%s」を削除します。作成済みの作品には影響しません。", deleting.title)) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    onDeleteTemplate(deleting.id)
                    deletingTemplateId = null
                }) { Text(uiText("削除")) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { deletingTemplateId = null }) { Text(uiText("キャンセル")) } }
        )
    }
}

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
    AlertDialog(
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
                shape = ButtonDefaults.shape,
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
    AlertDialog(
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
                shape = ButtonDefaults.shape,
                onClick = onAddSamples
            ) {
                Text(textTranslator("作品一覧に追加", emptyArray()))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(textTranslator("あとで", emptyArray()))
            }
        }
    )
}
