package com.hikariatelier.app

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
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
    var newCanvasModeName by rememberSaveable {
        mutableStateOf(CanvasSizingMode.FIXED.name)
    }
    val newCanvasMode = CanvasSizingMode.valueOf(newCanvasModeName)
    var kindName by rememberSaveable { mutableStateOf(WorkTemplateKind.BASIC_2D.name) }
    val kind = WorkTemplateKind.valueOf(kindName)
    val deviceTemplateScale = 960f / maxOf(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        1
    )
    val deviceTemplate = WorkTemplate(
        ratio = "device",
        width = (configuration.screenWidthDp * deviceTemplateScale).toInt().coerceAtLeast(1),
        height = (configuration.screenHeightDp * deviceTemplateScale).toInt().coerceAtLeast(1)
    )
    val maxSide = maxOf(configuration.screenWidthDp, configuration.screenHeightDp, 1)
    val minSide = minOf(configuration.screenWidthDp, configuration.screenHeightDp, 1)
    val deviceLandscapeTemplate = WorkTemplate(
        ratio = "device_landscape",
        width = (maxSide * deviceTemplateScale).toInt().coerceAtLeast(1),
        height = (minSide * deviceTemplateScale).toInt().coerceAtLeast(1)
    )
    val creationAspectOptions = listOf(deviceLandscapeTemplate) + workTemplates + listOf(deviceTemplate)
    val template = creationAspectOptions.firstOrNull { it.ratio == newRatio } ?: creationAspectOptions.first()

    WorkSheet(
        title = uiText("新しい作品"),
        subtitle = uiText("名前と描画比率を選んでスタート"),
        onDismiss = { if (!busy) onDismiss() },
        colors = colors,
        textTranslator = textTranslator,
        windowSetup = windowSetup
    ) {
        Column(
            Modifier.fillMaxWidth().weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                enabled = !busy,
                label = { Text(uiText("作品名")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !customTab, enabled = !busy, onClick = { customTab = false },
                    label = { Text(uiText("標準テンプレート")) })
                FilterChip(selected = customTab, enabled = !busy, onClick = { customTab = true },
                    label = { Text(uiText("自作テンプレート")) })
            }
            if (customTab) {
                UserTemplateChoices(
                    templates = userTemplates, selectedId = selectedTemplateId,
                    busy = busy, loadFailed = templateLoadFailed,
                    text = { uiText(it) }, onRetry = onRetryTemplates,
                    onSelect = { selectedTemplateId = it },
                    onDelete = { deletingTemplateId = it },
                    onManage = onManageTemplates
                )
            } else {
            Text(uiText("自分の作品を書き始めるためのテンプレートを選びます。"),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            listOf(false, true).forEach { advanced ->
                Text(uiText(if (advanced) "応用テンプレート" else "基本テンプレート"),
                    style = MaterialTheme.typography.labelLarge)
                WorkTemplateKind.entries.filter { it.advanced == advanced }.chunked(2).forEach { kinds ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        kinds.forEach { option ->
                            FilterChip(selected = kind == option, onClick = { kindName = option.name },
                                label = { Text(uiText(option.title)) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Text(uiText(kind.description), style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant)
            Text(
                uiText("キャンバスの動作"),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    CanvasSizingMode.FIXED to uiText("固定サイズ"),
                    CanvasSizingMode.RESPONSIVE to uiText("画面に合わせる")
                ).forEach { (mode, label) ->
                    val selected = mode == newCanvasMode
                    val enabled = mode != CanvasSizingMode.FIXED || newRatio != "device"
                    Surface(
                        onClick = { newCanvasModeName = mode.name },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) colors.primary.copy(alpha = 0.08f)
                        else colors.surface,
                        border = BorderStroke(
                            1.dp,
                            if (selected) colors.primary else colors.outlineVariant
                        )
                    ) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                label,
                                fontWeight = FontWeight.SemiBold,
                                color = if (!enabled) colors.onSurfaceVariant.copy(alpha = 0.45f)
                                else if (selected) colors.primary else colors.onSurface
                            )
                            Text(
                                uiText(
                                    if (mode == CanvasSizingMode.FIXED)
                                        "指定した描画サイズを維持し、表示だけを枠に合わせます"
                                    else "作業領域に合わせてresizeCanvas()を実行します"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Text(
                uiText("描画比率"),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
            val creationAspectColumns = when {
                configuration.fontScale > 1.5f || maxWidth < 280.dp -> 1
                maxWidth >= 540.dp -> 3
                else -> 2
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            creationAspectOptions.chunked(creationAspectColumns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { option ->
                        val selected = option.ratio == newRatio
                        Surface(
                            onClick = {
                                newRatio = option.ratio
                                if (option.ratio == "device" || option.ratio == "device_landscape") {
                                    newCanvasModeName = CanvasSizingMode.RESPONSIVE.name
                                }
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 96.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) colors.primary.copy(alpha = 0.08f) else colors.surface,
                            border = BorderStroke(
                                1.dp,
                                if (selected) colors.primary else colors.outlineVariant
                            )
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                    val size = fitPreviewSize(
                                        32f, 32f,
                                        option.width.toFloat() / option.height
                                    )
                                    Box(
                                        Modifier.size(size.width.dp, size.height.dp)
                                            .border(
                                                1.dp,
                                                if (selected) colors.primary else colors.onSurfaceVariant,
                                                RoundedCornerShape(4.dp)
                                            )
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        when (option.ratio) {
                                            "device_landscape" -> uiText("端末・横")
                                            "device" -> uiText("端末・縦")
                                            else -> option.ratio
                                        },
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected) colors.primary else colors.onSurface
                                    )
                                    Text(
                                        when (option.ratio) {
                                            "device_landscape" -> uiText("横向きの端末比率")
                                            "device" -> uiText("縦向きの端末比率")
                                            else -> "${option.width} × ${option.height}"
                                        },
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    repeat(creationAspectColumns - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceVariant
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        uiText(kind.title),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        if (newCanvasMode == CanvasSizingMode.FIXED)
                            "createCanvas(${template.width}, ${template.height});"
                        else "createCanvas(windowWidth, windowHeight);",
                        fontFamily = codeFontFamily,
                        style = MaterialTheme.typography.bodySmall, color = colors.primary
                    )
                    Text(
                        uiText("選択した比率を作品に保存します。現在の作品のコードはコピーしません。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            }
            Spacer(Modifier.height(4.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(uiText("キャンセル")) }
            Button(
                enabled = !busy && (!customTab || (selectedUserTemplate != null && !templateLoadFailed)),
                onClick = {
                    if (customTab) selectedUserTemplate?.let { onCreateFromTemplate(newTitle, it.id) }
                    else onCreate(newTitle, template.ratio, newCanvasMode, template.copy(kind = kind))
                },
                shape = RoundedCornerShape(16.dp)
            ) {
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
