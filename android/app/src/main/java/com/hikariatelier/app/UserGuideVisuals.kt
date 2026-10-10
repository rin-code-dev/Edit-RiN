package com.hikariatelier.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import kotlin.math.sin
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

internal fun guideText(language: String, ja: String, en: String, zh: String): String =
    when (language) { "ja" -> ja; "zh" -> zh; else -> en }

internal data class GuideAction(val icon: Int, val label: String, val result: String)

internal fun guideControlActions(language: String): List<GuideAction> {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    return listOf(
        GuideAction(R.drawable.ic_pause, t("一時停止", "Pause", "暂停"),
            t("作品の動きを止めます。もう一度押すと再開します。", "Pause the artwork; press again to resume.", "暂停作品；再次点击即可继续。")),
        GuideAction(R.drawable.ic_reload, t("変更を実行", "Run changes", "运行更改"),
            t("編集したコードを実行してプレビューへ反映します。", "Run edited code to update the preview.", "运行编辑后的代码并更新预览。")),
        GuideAction(R.drawable.ic_log, t("ログ", "Logs", "日志"),
            t("実行中のメッセージやエラーを確認します。", "Inspect runtime messages and errors.", "查看运行消息和错误。")),
        GuideAction(R.drawable.ic_tune, t("パラメータ", "Parameters", "参数"),
            t("コードに宣言された数値・色などを調整します。", "Adjust numbers and colors declared in the code.", "调整代码中声明的数值和颜色。")),
        GuideAction(R.drawable.ic_camera, t("プレビュー操作", "Preview actions", "预览操作"),
            t("スクリーンショットや録画などの操作を開きます。", "Open screenshot and recording actions.", "打开截图和录制操作。")),
        GuideAction(R.drawable.ic_history, t("履歴", "History", "历史"),
            t("スナップショットの作成や、保存済み状態への復元を選びます。", "Choose snapshots or revert to the saved work.", "选择快照或恢复到已保存状态。")),
        GuideAction(R.drawable.ic_save, t("保存", "Save", "保存"),
            t("作品の全ファイルを保存します。", "Save all files in the work.", "保存作品的所有文件。"))
    )
}

// Explicit chapter identity; captions and assets are shared across all translations.
internal fun guideHighlightedControls(sectionIcon: Int): Set<Int> = when (sectionIcon) {
    R.drawable.ic_code -> setOf(R.drawable.ic_pause, R.drawable.ic_reload, R.drawable.ic_log)
    R.drawable.ic_tune -> setOf(R.drawable.ic_tune)
    R.drawable.ic_history -> setOf(R.drawable.ic_history, R.drawable.ic_save)
    R.drawable.ic_camera -> setOf(R.drawable.ic_camera)
    else -> emptySet()
}

/** All practice state stays in the guide; no work repository or export API is used. */
@Composable
internal fun GuideQuickStart(language: String) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t("操作を試す", "Try the controls", "试用操作"), style = MaterialTheme.typography.titleLarge)
        Text(t("下のボタンをタップして試してみてください。パラメータを変えると円の大きさや色が変わります。保存や復元の操作も体験できます。", "Try the buttons below. Change the circle's size and color, then try saving and restoring it.", "点击下方按钮，调整圆的大小和颜色，再试试保存和恢复。"))
        Text(t("練習用の画面です。自分の作品には影響しません。画像や録画は端末に保存されません。", "This practice screen does not affect your works. Images and recordings are not saved to your device.", "这是练习画面，不会影响您的作品，也不会在设备上保存图片或录制内容。"), style = MaterialTheme.typography.bodySmall)
        GuideControlIllustration(R.drawable.ic_play, language)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GuideControlIllustration(sectionIcon: Int, language: String) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    var diameter by rememberSaveable { mutableFloatStateOf(120f) }
    var ink by rememberSaveable { mutableIntStateOf(0) }
    var savedDiameter by rememberSaveable { mutableFloatStateOf(120f) }
    var savedInk by rememberSaveable { mutableIntStateOf(0) }
    var snapshotDiameter by rememberSaveable { mutableFloatStateOf(120f) }
    var snapshotInk by rememberSaveable { mutableIntStateOf(0) }
    var hasSnapshot by rememberSaveable { mutableStateOf(false) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf(if (sectionIcon == R.drawable.ic_tune) "parameters" else "") }
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    var recording by rememberSaveable { mutableStateOf(false) }
    var recordingSeconds by rememberSaveable { mutableIntStateOf(0) }
    var recordedSeconds by rememberSaveable { mutableIntStateOf(-1) }
    var captured by rememberSaveable { mutableStateOf(false) }
    var captureDiameter by rememberSaveable { mutableFloatStateOf(120f) }
    var captureInk by rememberSaveable { mutableIntStateOf(0) }
    var message by rememberSaveable { mutableStateOf("") }
    var phase by remember { mutableFloatStateOf(0f) }
    val palette = listOf(Color(0xFFD67856), Color(0xFF507FAD), Color(0xFF558567))
    val names = listOf(t("朱色", "Orange", "橙色"), t("青", "Blue", "蓝色"), t("緑", "Green", "绿色"))
    val actions = guideControlActions(language)
    LaunchedEffect(paused) {
        while (!paused) { delay(32); phase += .04f }
    }
    LaunchedEffect(recording) {
        while (recording) { delay(1000); recordingSeconds++ }
    }
    fun toggle(value: String) { panel = if (panel == value) "" else value }
    fun restore() { diameter = savedDiameter; ink = savedInk; confirmRestore = false; message = t("保存した状態に戻しました。", "Saved state restored.", "已恢复保存的状态。") }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("練習用プレビュー", "Practice preview", "练习预览"), style = MaterialTheme.typography.titleSmall)
            Canvas(Modifier.fillMaxWidth().height(160.dp).testTag("guide-preview")
                .semantics { contentDescription = "${diameter.toInt()} / ${names[ink]}" }
                .clickable { ink = (ink + 1) % palette.size }) {
                drawRect(Color(0xFFF2EFE7))
                drawCircle(palette[ink], size.minDimension * diameter / 600f,
                    Offset(size.width / 2 + sin(phase) * size.width * .12f, size.height / 2))
            }
            Text(if (paused) t("一時停止中", "Paused", "已暂停") else t("実行中 · 円をタップすると色が変わります", "Running · tap the circle to change color", "运行中 · 点击圆更改颜色"), style = MaterialTheme.typography.bodySmall)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                actions.take(5).forEach { action ->
                    val label = if (action.icon == R.drawable.ic_pause && paused) t("再開", "Resume", "继续") else action.label
                    TooltipIconButton(label = label, onClick = {
                        when (action.icon) {
                            R.drawable.ic_pause -> paused = !paused
                            R.drawable.ic_reload -> { phase = 0f; paused = false; message = t("再実行しました。", "Restarted.", "已重新运行。") }
                            R.drawable.ic_log -> toggle("logs")
                            R.drawable.ic_tune -> toggle("parameters")
                            R.drawable.ic_camera -> toggle("capture")
                        }
                    }) { Icon(painterResource(if (action.icon == R.drawable.ic_pause && paused) R.drawable.ic_play else action.icon), label, Modifier.size(22.dp)) }
                }
                SaveRestoreControls(diameter != savedDiameter || ink != savedInk, MaterialTheme.colorScheme,
                    48.dp, 48.dp, 22.dp,
                    onSnapshot = { panel = "snapshots" },
                    onRestore = { confirmRestore = true },
                    onSave = { savedDiameter = diameter; savedInk = ink; message = t("練習用の状態を保存しました。", "Practice state saved.", "已保存练习状态。") },
                    textTranslator = { source, _ -> when (source) {
                        "履歴" -> t(source, "History", "历史")
                        "スナップショット" -> t(source, "Snapshots", "快照")
                        "保存済み状態に戻す" -> t(source, "Revert to saved", "恢复到已保存状态")
                        else -> t(source, "Save all files", "保存所有文件")
                    } })
            }
            if (message.isNotEmpty()) Text(message, Modifier.testTag("guide-feedback"), style = MaterialTheme.typography.bodySmall)
            if (panel.isNotEmpty()) {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(when (panel) {
                        "parameters" -> t("パラメータ", "Parameters", "参数")
                        "logs" -> t("ログ", "Logs", "日志")
                        "snapshots" -> t("スナップショット", "Snapshots", "快照")
                        else -> t("プレビュー操作", "Preview actions", "预览操作")
                    }, style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { panel = "" }) { Icon(painterResource(R.drawable.ic_close), t("パネルを閉じる", "Close panel", "关闭面板")) }
                }
                when (panel) {
                    "parameters" -> {
                        Text(t("直径", "Diameter", "直径") + " ${diameter.toInt()}", Modifier.testTag("guide-diameter"))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { diameter = (diameter - 10).coerceAtLeast(20f) }) { Text("−") }
                            StudioSlider(diameter, { diameter = it }, Modifier.weight(1f).testTag("guide-slider"), steps = 21, valueRange = 20f..240f)
                            TextButton(onClick = { diameter = (diameter + 10).coerceAtMost(240f) }) { Text("+") }
                        }
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            names.forEachIndexed { index, name -> FilterChip(ink == index, { ink = index }, label = { Text(name) }) }
                        }
                        TextButton(onClick = { diameter = 120f; ink = 0 }) { Text(t("初期値に戻す", "Reset parameters", "重置参数")) }
                    }
                    "logs" -> Text("[demo] rinParams.diameter = ${diameter.toInt()}\n[demo] rinParams.ink = ${names[ink]}")
                    "snapshots" -> {
                        TextButton(onClick = { snapshotDiameter = diameter; snapshotInk = ink; hasSnapshot = true }) { Text(t("スナップショットを作成", "Create snapshot", "创建快照")) }
                        if (hasSnapshot) {
                            Text("${snapshotDiameter.toInt()} / ${names[snapshotInk]}")
                            TextButton(onClick = { diameter = snapshotDiameter; ink = snapshotInk; message = t("スナップショットを復元しました。", "Snapshot restored.", "已恢复快照。") }) { Text(t("この状態を復元", "Restore snapshot", "恢复此状态")) }
                        } else Text(t("まだスナップショットはありません。", "No snapshots yet.", "暂无快照。"))
                    }
                    "capture" -> {
                        TextButton(onClick = { captured = true; captureDiameter = diameter; captureInk = ink }) { Text(t("スクリーンショットを試す", "Try screenshot", "试用截图")) }
                        TextButton(onClick = {
                            if (recording) recordedSeconds = recordingSeconds else recordingSeconds = 0
                            recording = !recording
                        }) { Text(if (recording) t("録画を停止", "Stop recording", "停止录制") else t("録画を試す", "Try recording", "试用录制")) }
                        if (recording) Text(t("録画中（練習）", "Recording (practice)", "录制中（练习）") + " ${recordingSeconds}s")
                        if (!recording && recordedSeconds >= 0) Text(t("録画を終了しました（練習）", "Recording finished (practice)", "录制结束（练习）") + " ${recordedSeconds}s")
                        if (captured) {
                            Text(t("撮影結果（練習）", "Captured image (practice)", "截图结果（练习）"))
                            Canvas(Modifier.fillMaxWidth().height(80.dp)) { drawRect(Color(0xFFF2EFE7)); drawCircle(palette[captureInk], size.minDimension * captureDiameter / 600f) }
                        }
                    }
                }
            }
        }
    }
    if (confirmRestore) AlertDialog(onDismissRequest = { confirmRestore = false },
        title = { Text(t("保存した状態に戻しますか？", "Restore saved state?", "恢复保存的状态？")) },
        text = { Text(t("練習中の変更を、最後に保存した大きさと色に戻します。", "Replace practice changes with the last saved size and color.", "将练习中的修改恢复为上次保存的大小和颜色。")) },
        confirmButton = { TextButton(onClick = { restore() }) { Text(t("復元", "Restore", "恢复")) } },
        dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text(t("キャンセル", "Cancel", "取消")) } })
}
