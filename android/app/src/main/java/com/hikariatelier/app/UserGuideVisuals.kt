package com.hikariatelier.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import kotlin.math.sin
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    Column(verticalArrangement = Arrangement.spacedBy(if (compactHeight) 6.dp else 12.dp)) {
        Text(
            t("操作を試す", "Try the controls", "试用操作"),
            style = if (compactHeight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
        )
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
            Canvas(Modifier.fillMaxWidth().height(if (LocalConfiguration.current.screenHeightDp < 480) 110.dp else 160.dp).testTag("guide-preview")
                .semantics { contentDescription = "${diameter.toInt()} / ${names[ink]}" }
                .clickable { ink = (ink + 1) % palette.size }) {
                drawRect(Color(0xFFF2EFE7))
                drawCircle(palette[ink], size.minDimension * diameter / 600f,
                    Offset(size.width / 2 + sin(phase) * size.width * .12f, size.height / 2))
            }
            Text(if (paused) t("一時停止中", "Paused", "已暂停") else t("実行中 · 円をタップすると色が変わります", "Running · tap the circle to change color", "运行中 · 点击圆更改颜色"), style = MaterialTheme.typography.bodySmall)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                val colors = MaterialTheme.colorScheme
                val controlTranslator: (String, Array<out Any?>) -> String = { source, _ ->
                    when (source) {
                        "再生" -> t("再開", "Resume", "继续")
                        "一時停止" -> t("一時停止", "Pause", "暂停")
                        "再読み込み", "変更を実行" -> t("変更を実行", "Run changes", "运行更改")
                        "ログ" -> t("ログ", "Logs", "日志")
                        "パラメータ" -> t("パラメータ", "Parameters", "参数")
                        "プレビュー操作" -> t("プレビュー操作", "Preview actions", "预览操作")
                        "履歴" -> t(source, "History", "历史")
                        "スナップショット" -> t(source, "Snapshots", "快照")
                        "保存済み状態に戻す" -> t(source, "Revert to saved", "恢复到已保存状态")
                        else -> t(source, "Save all files", "保存所有文件")
                    }
                }
                RunStatusControls(
                    isError = false,
                    isPaused = paused,
                    colors = colors,
                    height = 38.dp,
                    buttonSize = 32.dp,
                    iconSize = 17.dp,
                    onTogglePause = { paused = !paused },
                    onReload = {
                        phase = 0f
                        paused = false
                        message = t("再実行しました。", "Restarted.", "已重新运行。")
                    },
                    textTranslator = controlTranslator
                )
                ConsoleButton(
                    hasError = false,
                    entryCount = 2,
                    colors = colors,
                    height = 38.dp,
                    minWidth = 38.dp,
                    iconSize = 17.dp,
                    onClick = { toggle("logs") },
                    textTranslator = controlTranslator
                )
                ParameterButton(
                    parameterCount = 2,
                    colors = colors,
                    height = 38.dp,
                    minWidth = 38.dp,
                    iconSize = 17.dp,
                    onClick = { toggle("parameters") },
                    textTranslator = controlTranslator,
                    active = panel == "parameters"
                )
                PreviewActionsToggleButton(
                    isRecordingOrCountingDown = recording,
                    previewActionsExpanded = panel == "capture",
                    isLandscape = false,
                    colors = colors,
                    onClick = { toggle("capture") },
                    textTranslator = controlTranslator
                )
                SaveRestoreControls(
                    hasUnsavedChanges = diameter != savedDiameter || ink != savedInk,
                    colors = colors,
                    height = 38.dp,
                    buttonSize = 32.dp,
                    iconSize = 18.dp,
                    onSnapshot = { panel = "snapshots" },
                    onRestore = { confirmRestore = true },
                    onSave = {
                        savedDiameter = diameter
                        savedInk = ink
                        message = t("練習用の状態を保存しました。", "Practice state saved.", "已保存练习状态。")
                    },
                    textTranslator = controlTranslator
                )
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

@Composable
internal fun GuideBlockModeIllustration(language: String) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val colors = MaterialTheme.colorScheme
    var showingBlocks by rememberSaveable { mutableStateOf(true) }
    var autoRun by rememberSaveable { mutableStateOf(true) }
    var paletteOpen by rememberSaveable { mutableStateOf(false) }
    var fullPreviewOpen by rememberSaveable { mutableStateOf(false) }
    var inkIndex by rememberSaveable { mutableIntStateOf(0) }
    var circleSize by rememberSaveable { mutableIntStateOf(120) }
    var hasExtraRing by rememberSaveable { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf("") }

    val inks = listOf(Color(0xFFD67856), Color(0xFF3F6F8F), Color(0xFF4F7D5C), Color(0xFFE5A93B))
    val inkHexes = listOf("#D67856", "#3F6F8F", "#4F7D5C", "#E5A93B")

    val chromeModel = remember(paletteOpen) {
        BlockEditorChromeModel().apply {
            state = BlockChromeState(editable = true, busy = false, palette = paletteOpen)
        }
    }
    val uiTranslator: (String, Array<out Any?>) -> String = { source, args ->
        val translated = translateUi(source, language)
        if (args.isEmpty()) translated else String.format(java.util.Locale.ROOT, translated, *args)
    }

    Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EditorDocumentBar(
                files = listOf("sketch.js"),
                selectedFile = "sketch.js",
                dirtyFiles = if (hasExtraRing || inkIndex != 0 || circleSize != 120) setOf("sketch.js") else emptySet(),
                showingBlocks = showingBlocks,
                allowBlocks = true,
                enabled = true,
                readOnly = false,
                autoRunEnabled = autoRun,
                previewHasChanges = !autoRun,
                onSelectFile = {},
                onSelectBlocks = { next ->
                    showingBlocks = next
                    feedback = if (next) {
                        t("ブロックモードへ切り替えました。", "Switched to Block mode.", "已切换到积木模式。")
                    } else {
                        t("コード表示へ切り替えました。元のコードがそのまま保たれます。", "Switched to Code view. Your source code stays intact.", "已切换到代码视图，原始代码保持完整。")
                    }
                },
                onAddFile = null,
                onToggleAutoRun = { autoRun = !autoRun },
                onRunChanges = { feedback = t("変更を実行しました。", "Ran changes.", "已运行更改。") },
                textTranslator = uiTranslator,
                onOpenFullPreview = {
                    fullPreviewOpen = true
                    feedback = t("全画面プレビューを開きました。", "Opened full-screen preview.", "已打开全屏预览。")
                }
            )

            if (fullPreviewOpen) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF2EFE7),
                    border = BorderStroke(1.dp, colors.outlineVariant),
                    modifier = Modifier.fillMaxWidth().height(210.dp).testTag("guide-block-full-preview")
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(inks[inkIndex], radius = size.minDimension * (circleSize / 320f))
                            if (hasExtraRing) {
                                drawCircle(
                                    color = inks[(inkIndex + 1) % inks.size],
                                    radius = size.minDimension * 0.42f,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = { fullPreviewOpen = false },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        ) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(t("全画面表示を閉じる", "Exit full screen", "退出全屏"))
                        }
                    }
                }
            } else if (!showingBlocks) {
                val codeText = buildString {
                    append("function setup() {\n  createCanvas(400, 400);\n}\n\nfunction draw() {\n  background('#F2EFE7');\n")
                    append("  fill('${inkHexes[inkIndex]}');\n")
                    append("  circle(200, 200, $circleSize);\n")
                    if (hasExtraRing) append("  circle(200, 200, 220);\n")
                    append("}")
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = codeText,
                        modifier = Modifier.padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceContainer)
                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        // Event block header: function draw()
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFD97724),
                            contentColor = Color.White
                        ) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("function draw()", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                // fill('#...') statement block with inline color swatch
                                Surface(
                                    onClick = {
                                        inkIndex = (inkIndex + 1) % inks.size
                                        feedback = t("スウォッチをタップして色を変更しました。", "Tapped the inline swatch to change color.", "已点击行内色块切换颜色。")
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF8B5CF6),
                                    contentColor = Color.White
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(t("塗り色", "fill", "填充色"), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        Box(
                                            Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(inks[inkIndex])
                                                .border(1.5.dp, Color.White, CircleShape)
                                        )
                                        Text(inkHexes[inkIndex], style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                    }
                                }
                                // circle(200, 200, size) block with clickable capsule
                                Surface(
                                    onClick = {
                                        circleSize = if (circleSize >= 160) 80 else circleSize + 40
                                        feedback = t("カプセルをタップして直径を $circleSize に変更しました。", "Tapped capsule to set diameter to $circleSize.", "已点击数值胶囊将直径设为 $circleSize。")
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF2E8B8B),
                                    contentColor = Color.White
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(t("円を描く", "circle", "绘制圆"), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        Surface(shape = RoundedCornerShape(999.dp), color = Color.White.copy(alpha = 0.22f)) {
                                            Text("200, 200", Modifier.padding(horizontal = 7.dp, vertical = 2.dp), fontSize = 11.sp, color = Color.White)
                                        }
                                        Surface(shape = RoundedCornerShape(999.dp), color = Color.White.copy(alpha = 0.32f)) {
                                            Text("$circleSize", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                                if (hasExtraRing) {
                                    Surface(
                                        onClick = { hasExtraRing = false },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF2E8B8B),
                                        contentColor = Color.White
                                    ) {
                                        Row(
                                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(t("外側のリング (タップで削除)", "Outer ring (tap to remove)", "外圈圆环 (点击移除)"), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        if (paletteOpen) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colors.surface,
                                border = BorderStroke(1.dp, colors.outlineVariant),
                                shadowElevation = 4.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        t("挿入先: draw() の最後 · タップで1タップ挿入", "Insert at: end of draw() · 1-tap to insert", "插入位置：draw() 末尾 · 单击直接插入"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    OutlinedCard(
                                        onClick = {
                                            hasExtraRing = true
                                            paletteOpen = false
                                            feedback = t("ブロックを追加しました！", "Block inserted!", "已添加积木！")
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(t("外側のリングを追加", "Add outer ring", "添加外圈圆环"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                                Text("circle(200, 200, 220)", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = colors.onSurfaceVariant)
                                            }
                                            Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Mini PiP preview in top-end corner of the block workspace
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.outlineVariant),
                        shadowElevation = 4.dp,
                        modifier = Modifier.align(Alignment.TopEnd).size(width = 104.dp, height = 88.dp)
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            Row(
                                Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("AUTO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                                Icon(
                                    painter = painterResource(R.drawable.ic_fullscreen),
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp).clickable { fullPreviewOpen = true }
                                )
                            }
                            Canvas(Modifier.fillMaxWidth().weight(1f).background(Color(0xFFF2EFE7))) {
                                drawCircle(inks[inkIndex], radius = size.minDimension * (circleSize / 320f))
                                if (hasExtraRing) {
                                    drawCircle(
                                        color = inks[(inkIndex + 1) % inks.size],
                                        radius = size.minDimension * 0.42f,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }

                BlockEditorFloatingDock(
                    model = chromeModel,
                    ready = true,
                    fullscreen = true,
                    canUndo = hasExtraRing || inkIndex != 0 || circleSize != 120,
                    canRedo = false,
                    text = { uiTranslator(it, emptyArray()) },
                    onCommand = { cmd ->
                        when (cmd) {
                            "add" -> paletteOpen = true
                            "dismiss" -> paletteOpen = false
                            "undo" -> {
                                hasExtraRing = false
                                inkIndex = 0
                                circleSize = 120
                                feedback = t("元に戻しました。", "Undone.", "已撤销。")
                            }
                        }
                    },
                    onSearch = {},
                    onDisplay = {},
                    onFullscreen = { fullPreviewOpen = !fullPreviewOpen }
                )
            }

            if (feedback.isNotEmpty()) {
                Text(feedback, style = MaterialTheme.typography.bodySmall, color = colors.primary)
            }
        }
    }
}
