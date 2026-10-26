package com.hikariatelier.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withContext

/*
 * Practice screens for the first-run tour. All state is local to these composables: they never
 * touch works, files, the preview, recording or sharing, so nothing a user tries here is saved.
 */

private val MockPaper = Color(0xFFF2EFE7)
private val MockInk = Color(0xFF2B2B2B)
private val MockInks = listOf(Color(0xFFD67856), Color(0xFF3F6F8F), Color(0xFF4F7D5C))

private fun mockUiTranslator(language: String): (String, Array<out Any?>) -> String = { source, args ->
    val translated = translateUi(source, language)
    if (args.isEmpty()) translated else String.format(java.util.Locale.ROOT, translated, *args)
}

@Composable
internal fun OnboardingMock(step: Int, language: String, animate: Boolean) {
    when (step) {
        0 -> MockGallery(language, animate)
        1 -> MockRun(language, animate)
        2 -> MockBlockMode(language, animate)
        3 -> MockEditorTools(language, animate)
        4 -> MockPipPreview(language, animate)
        5 -> MockParameters(language)
        6 -> MockPauseHistory(language, animate)
        7 -> MockCapture(language, animate)
        else -> MockFinish()
    }
}

/**
 * Compose reads the system animation scale from the coroutine context and finishes animations
 * immediately when it is 0. The tour is a demonstration, so it always runs at normal speed.
 */
private object FullSpeedMotion : MotionDurationScale {
    override val scaleFactor: Float get() = 1f
}

/** Repeating 0..1 value that keeps playing at normal speed. It holds its last value while [enabled] is false ([idle] before the first start). */
@Composable
private fun rememberLoopPhase(enabled: Boolean, periodMillis: Int, reverse: Boolean, easing: Easing, idle: Float): Float {
    val phase = remember { Animatable(idle) }
    if (enabled) {
        LaunchedEffect(Unit) {
            withContext(FullSpeedMotion) {
                phase.snapTo(0f)
                phase.animateTo(1f, infiniteRepeatable(tween(periodMillis, easing = easing),
                    if (reverse) RepeatMode.Reverse else RepeatMode.Restart))
            }
        }
    }
    return phase.value
}

/** Pulsing outline that points at the control to try next. */
@Composable
private fun Modifier.tapHint(active: Boolean, animate: Boolean): Modifier {
    if (!active) return this
    val color = MaterialTheme.colorScheme.primary
    val phase = rememberLoopPhase(animate, 1400, false, LinearEasing, 0.35f)
    return drawBehind {
        val grow = 10.dp.toPx() * phase
        drawRoundRect(
            color = color.copy(alpha = 0.32f * (1f - phase)),
            topLeft = Offset(-grow, -grow),
            size = Size(size.width + grow * 2, size.height + grow * 2),
            cornerRadius = CornerRadius(16.dp.toPx() + grow)
        )
    }
}

@Composable
private fun MockFrame(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth().heightIn(max = 480.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

/** A small artwork preview: one circle on paper. [fraction] is the circle diameter relative to the height. */
@Composable
private fun MockCanvas(fraction: Float, ink: Color, modifier: Modifier = Modifier, overlay: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier.fillMaxWidth().aspectRatio(1.9f).clip(RoundedCornerShape(14.dp)).background(MockPaper)) {
        Canvas(Modifier.fillMaxSize()) { drawCircle(ink, radius = size.height * fraction.coerceIn(0.05f, 0.95f) / 2f) }
        overlay()
    }
}

@Composable
private fun MockCaption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MockGallery(language: String, animate: Boolean) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val names = listOf("Palette", "Halo", "Wave", "Gravity")
    var opened by remember { mutableIntStateOf(-1) }
    val colors = MaterialTheme.colorScheme
    MockFrame {
        Text(t("ギャラリー", "Gallery", "作品库"), style = MaterialTheme.typography.titleSmall)
        for (row in 0..1) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (col in 0..1) {
                    val index = row * 2 + col
                    val selected = opened == index
                    Surface(
                        onClick = { opened = index },
                        modifier = Modifier.weight(1f).tapHint(opened < 0 && index == 0, animate),
                        shape = RoundedCornerShape(14.dp),
                        color = MockPaper,
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant)
                    ) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Canvas(Modifier.fillMaxWidth().aspectRatio(1.7f)) {
                                drawCircle(MockInks[index % MockInks.size], radius = size.height * 0.32f)
                            }
                            Text(names[index], style = MaterialTheme.typography.labelMedium, color = MockInk)
                        }
                    }
                }
            }
        }
        MockCaption(
            if (opened >= 0) t("「${names[opened]}」を開きました（練習用）", "Opened “${names[opened]}” (practice)", "已打开“${names[opened]}”（练习）")
            else t("サムネイルをタップしてみましょう", "Tap a thumbnail to try it", "试着点击缩略图")
        )
    }
}

@Composable
private fun MockRun(language: String, animate: Boolean) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    var ran by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    val grow = remember { Animatable(0.3f) }
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val uiTranslator = remember(language) { mockUiTranslator(language) }
    LaunchedEffect(ran) {
        if (ran) withContext(FullSpeedMotion) {
            if (animate) grow.animateTo(0.8f, tween(450)) else grow.snapTo(0.8f)
        }
    }
    val fraction = grow.value
    val codeSource = "function draw() {\n  background('#F2EFE7');\n  circle(width / 2, height / 2, 160);\n}"
    val highlightedCode = remember(codeSource, dark) {
        JavaScriptHighlighter(dark).filter(AnnotatedString(codeSource)).text
    }
    MockFrame {
        EditorDocumentBar(
            files = listOf("sketch.js"),
            selectedFile = "sketch.js",
            dirtyFiles = if (!ran) setOf("sketch.js") else emptySet(),
            showingBlocks = false,
            allowBlocks = true,
            enabled = true,
            readOnly = false,
            autoRunEnabled = false,
            previewHasChanges = !ran,
            onSelectFile = {},
            onSelectBlocks = {},
            onAddFile = null,
            onToggleAutoRun = {},
            onRunChanges = { ran = true },
            textTranslator = uiTranslator
        )
        Surface(shape = RoundedCornerShape(12.dp), color = colors.surfaceContainerHigh) {
            Text(
                highlightedCode,
                Modifier.fillMaxWidth().padding(12.dp),
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall
            )
        }
        MockCanvas(fraction, MockInks[0])
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RunStatusControls(
                isError = false,
                isPaused = paused,
                hasPendingChanges = !ran,
                colors = colors,
                height = 38.dp,
                buttonSize = 32.dp,
                iconSize = 17.dp,
                onTogglePause = { paused = !paused },
                onReload = { ran = true },
                textTranslator = uiTranslator
            )
            Button(onClick = { ran = true }, modifier = Modifier.weight(1f).tapHint(!ran, animate)) {
                Icon(painterResource(R.drawable.ic_reload), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(t("実行", "Run", "运行"))
            }
        }
        MockCaption(
            if (ran) t("プレビューが更新されました（練習用）", "The preview is updated (practice)", "预览已更新（练习）")
            else t("コードを変更しました。「実行」を押して反映しましょう", "Code edited. Press Run to see the update!", "代码已修改，点击“运行”查看效果")
        )
    }
}

@Composable
private fun MockBlockMode(language: String, animate: Boolean) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val uiTranslator = remember(language) { mockUiTranslator(language) }
    var showingBlocks by remember { mutableStateOf(true) }
    var autoRun by remember { mutableStateOf(true) }
    var paletteOpen by remember { mutableStateOf(false) }
    var fullPreviewOpen by remember { mutableStateOf(false) }
    var inkIndex by remember { mutableIntStateOf(0) }
    var circleSize by remember { mutableIntStateOf(120) }
    var hasExtraRing by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    val swatchColors = remember {
        listOf(Color(0xFFD67856), Color(0xFF3F6F8F), Color(0xFF4F7D5C), Color(0xFFE5A93B))
    }
    val swatchHexes = remember {
        listOf("#D67856", "#3F6F8F", "#4F7D5C", "#E5A93B")
    }
    val chromeModel = remember(paletteOpen) {
        BlockEditorChromeModel().apply {
            state = BlockChromeState(editable = true, busy = false, palette = paletteOpen)
        }
    }

    MockFrame {
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
                note = if (next) {
                    t("ブロックモードへ切り替えました（練習用）", "Switched to Block mode (practice)", "已切换到积木模式（练习）")
                } else {
                    t("コード表示へ切り替えました（練習用）", "Switched to Code view (practice)", "已切换到代码视图（练习）")
                }
            },
            onAddFile = null,
            onToggleAutoRun = { autoRun = !autoRun },
            onRunChanges = { note = t("変更を実行しました（練習用）", "Ran changes (practice)", "已运行更改（练习）") },
            textTranslator = uiTranslator,
            onOpenFullPreview = {
                fullPreviewOpen = true
                note = t("全画面プレビューを開きました（練習用）", "Opened full-screen preview (practice)", "已打开全屏预览（练习）")
            }
        )

        if (fullPreviewOpen) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MockPaper,
                border = BorderStroke(1.dp, colors.outlineVariant),
                modifier = Modifier.fillMaxWidth().height(190.dp).testTag("onboarding-block-full-preview")
            ) {
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(swatchColors[inkIndex], radius = size.minDimension * (circleSize / 320f))
                        if (hasExtraRing) {
                            drawCircle(
                                color = swatchColors[(inkIndex + 1) % swatchColors.size],
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
            val codeSource = buildString {
                append("function draw() {\n  background('#F2EFE7');\n")
                append("  fill('${swatchHexes[inkIndex]}');\n")
                append("  circle(200, 200, $circleSize);\n")
                if (hasExtraRing) append("  circle(200, 200, 220);\n")
                append("}")
            }
            val highlighted = remember(codeSource, dark) {
                JavaScriptHighlighter(dark).filter(AnnotatedString(codeSource)).text
            }
            Surface(shape = RoundedCornerShape(12.dp), color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Text(highlighted, Modifier.padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceContainerHigh)
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFD97724),
                        contentColor = Color.White
                    ) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("function draw()", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Surface(
                                onClick = {
                                    inkIndex = (inkIndex + 1) % swatchColors.size
                                    note = t("ブロックの色スウォッチを変更しました（練習用）", "Changed block color swatch (practice)", "已修改积木颜色色块（练习）")
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF8B5CF6),
                                contentColor = Color.White,
                                modifier = Modifier.semantics {
                                    contentDescription = t("ブロックの色を変更", "Change block color", "修改积木颜色")
                                }
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("fill", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Box(
                                        Modifier
                                            .size(15.dp)
                                            .clip(CircleShape)
                                            .background(swatchColors[inkIndex])
                                            .border(1.5.dp, Color.White, CircleShape)
                                    )
                                    Text(swatchHexes[inkIndex], style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                }
                            }
                            Surface(
                                onClick = {
                                    circleSize = if (circleSize >= 160) 80 else circleSize + 40
                                    note = t("直径カプセルを $circleSize に変更しました（練習用）", "Updated circle size to $circleSize (practice)", "已将直径胶囊更新为 $circleSize（练习）")
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
                                    Text("circle", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
                                    Text(
                                        t("外側のリング", "Outer ring", "外圈圆环"),
                                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
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
                                    t("挿入先: draw() の最後 · タップで追加", "Insert at: end of draw() · Tap to insert", "插入位置：draw() 末尾 · 点击插入"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                OutlinedCard(
                                    onClick = {
                                        hasExtraRing = true
                                        paletteOpen = false
                                        note = t("ブロックを1タップで追加しました（練習用）", "Inserted block in one tap (practice)", "已一键插入积木（练习）")
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("onboarding-insert-ring-block")
                                ) {
                                    Row(
                                        Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(t("外側のリングを追加", "Add outer ring", "添加外圈圆环"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.primary)
                                    }
                                }
                            }
                        }
                    }
                }

                // Floating PiP preview window in top-right corner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outlineVariant),
                    shadowElevation = 4.dp,
                    modifier = Modifier.align(Alignment.TopEnd).size(width = 100.dp, height = 84.dp)
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
                        Canvas(Modifier.fillMaxWidth().weight(1f).background(MockPaper)) {
                            drawCircle(swatchColors[inkIndex], radius = size.minDimension * (circleSize / 320f))
                            if (hasExtraRing) {
                                drawCircle(
                                    color = swatchColors[(inkIndex + 1) % swatchColors.size],
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
                        }
                    }
                },
                onSearch = {},
                onDisplay = {},
                onFullscreen = { fullPreviewOpen = !fullPreviewOpen },
                modifier = Modifier.tapHint(note == null && !paletteOpen, animate)
            )
        }

        MockCaption(
            note ?: t(
                "「＋ ブロックを追加」やブロック内の色・数値、全画面ボタンをタップしてみましょう",
                "Try tapping + Add block, the inline color swatch, or the full-screen preview button",
                "试试点击“＋ 添加积木”、积木内的色块或全屏预览按钮"
            )
        )
    }
}

@Composable
private fun MockEditorTools(language: String, animate: Boolean) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    var diameter by remember { mutableFloatStateOf(80f) }
    var inkIndex by remember { mutableIntStateOf(0) }
    var lastAction by remember { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.colorScheme
    val swatchColors = remember {
        listOf(
            Color(0xFFD67856),
            Color(0xFF3F6F8F),
            Color(0xFF4F7D5C),
            Color(0xFFE5A93B)
        )
    }

    val colorHex = when (inkIndex) {
        0 -> "#D67856"
        1 -> "#3F6F8F"
        2 -> "#4F7D5C"
        else -> "#E5A93B"
    }

    MockFrame {
        MockCanvas(diameter / 220f, swatchColors[inkIndex])

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.fillMaxWidth()) {
                EditorDocumentBar(
                    files = listOf("sketch.js"),
                    selectedFile = "sketch.js",
                    dirtyFiles = if (lastAction != null) setOf("sketch.js") else emptySet(),
                    showingBlocks = false,
                    allowBlocks = true,
                    enabled = true,
                    readOnly = false,
                    autoRunEnabled = true,
                    previewHasChanges = false,
                    onSelectFile = {},
                    onSelectBlocks = {},
                    onAddFile = null,
                    onToggleAutoRun = {},
                    onRunChanges = {},
                    textTranslator = { s, _ -> translateUi(s, language) }
                )
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        JavaScriptHighlighter(false).filter(
                            AnnotatedString("function draw() {\n  background('#F2EFE7');\n  fill('$colorHex');")
                        ).text,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                    ) {
                        Text(
                            JavaScriptHighlighter(false).filter(AnnotatedString("  circle(width / 2, height / 2, ")).text,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Surface(
                            onClick = {
                                val next = if (diameter >= 160f) 50f else diameter + 30f
                                diameter = next
                                lastAction = t("数値を変更: ${next.toInt()}", "Changed value: ${next.toInt()}", "数值已修改: ${next.toInt()}")
                            },
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                            modifier = Modifier.tapHint(lastAction == null && diameter == 80f, animate)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_scrub),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    "${diameter.toInt()}",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            ");",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        "}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Text(
            t("数値スクラブ (直径: ${diameter.toInt()})", "Number scrubber (diameter: ${diameter.toInt()})", "数值微调 (直径: ${diameter.toInt()})"),
            style = MaterialTheme.typography.labelMedium
        )
        StudioSlider(
            value = diameter,
            onValueChange = {
                diameter = it
                lastAction = t("数値を調整中: ${it.toInt()}", "Scrubbing value: ${it.toInt()}", "正在微调数值: ${it.toInt()}")
            },
            valueRange = 20f..200f
        )

        Text(t("記号バー", "Symbol bar", "符号辅助栏"), style = MaterialTheme.typography.labelMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            val symbols = listOf("{ }", "( )", "[ ]", ";", "=", "//")
            symbols.forEach { sym ->
                Surface(
                    onClick = {
                        lastAction = t("「${sym}」を入力（練習用）", "Entered “${sym}” (practice)", "输入了“${sym}”（练习）")
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Text(
                            sym,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Text(t("クイックカラースウォッチ", "Color swatches", "快速颜色色板"), style = MaterialTheme.typography.labelMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            swatchColors.forEachIndexed { index, color ->
                val selected = inkIndex == index
                Surface(
                    onClick = {
                        inkIndex = index
                        lastAction = t("色を変更しました（練習用）", "Color changed (practice)", "已切换颜色（练习）")
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .semantics { contentDescription = t("色 ${index + 1}", "Color ${index + 1}", "颜色 ${index + 1}") },
                    shape = RoundedCornerShape(8.dp),
                    color = color,
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant)
                ) {}
            }
        }

        MockCaption(
            lastAction ?: t(
                "コード内の数値をタップ・ドラッグしたり、記号や色を選んでみましょう",
                "Try dragging or tapping the number, or tap symbols and colors",
                "试着拖动或点击代码中的数值，或选择符号与颜色"
            )
        )
    }
}

@Composable
private fun MockPipPreview(language: String, animate: Boolean) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    var corner by remember { mutableStateOf(PipCorner.TOP_END) }
    var translucent by remember { mutableStateOf(false) }
    var sizePreset by remember { mutableStateOf(PipSizePreset.SMALL) }
    var minimized by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.colorScheme

    val alignment = when (corner) {
        PipCorner.TOP_START -> Alignment.TopStart
        PipCorner.TOP_END -> Alignment.TopEnd
        PipCorner.BOTTOM_START -> Alignment.BottomStart
        PipCorner.BOTTOM_END -> Alignment.BottomEnd
    }

    val targetWidth = when (sizePreset) {
        PipSizePreset.SMALL -> 118.dp
        PipSizePreset.MEDIUM -> 152.dp
        PipSizePreset.LARGE -> 188.dp
    }
    val targetHeight = if (minimized) 30.dp else when (sizePreset) {
        PipSizePreset.SMALL -> 84.dp
        PipSizePreset.MEDIUM -> 106.dp
        PipSizePreset.LARGE -> 130.dp
    }
    val animWidth by animateDpAsState(targetWidth, label = "mockPipWidth")
    val animHeight by animateDpAsState(targetHeight, label = "mockPipHeight")

    MockFrame {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(216.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
        ) {
            Column(Modifier.fillMaxSize()) {
                EditorDocumentBar(
                    files = listOf("sketch.js"),
                    selectedFile = "sketch.js",
                    dirtyFiles = emptySet(),
                    showingBlocks = false,
                    allowBlocks = true,
                    enabled = true,
                    readOnly = false,
                    autoRunEnabled = true,
                    previewHasChanges = false,
                    onSelectFile = {},
                    onSelectBlocks = {},
                    onAddFile = null,
                    onToggleAutoRun = {},
                    onRunChanges = {},
                    textTranslator = { s, _ -> translateUi(s, language) }
                )
                Column(Modifier.fillMaxSize().padding(10.dp)) {
                    Text(
                        JavaScriptHighlighter(false).filter(
                            AnnotatedString("// Fullscreen Code\nfunction setup() {\n  createCanvas(400, 400);\n}\nfunction draw() {\n  background('#F2EFE7');\n  circle(200, 200, 80);\n}")
                        ).text,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .align(alignment)
                        .size(animWidth, animHeight)
                        .tapHint(note == null, animate),
                    shape = RoundedCornerShape(10.dp),
                    color = if (translucent) colors.surface.copy(alpha = 0.65f) else colors.surface,
                    border = BorderStroke(1.dp, if (translucent) colors.primary.copy(alpha = 0.65f) else colors.outlineVariant),
                    shadowElevation = 4.dp
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp)
                                .background(colors.surfaceContainerLow)
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(colors.primary))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "PiP",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface,
                                modifier = Modifier.weight(1f)
                            )

                            if (!minimized) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (translucent) colors.primary.copy(alpha = 0.15f) else Color.Transparent)
                                        .then(if (translucent) Modifier.border(1.dp, colors.primary.copy(alpha = 0.8f), RoundedCornerShape(6.dp)) else Modifier)
                                        .clickable {
                                            translucent = !translucent
                                            note = if (translucent) {
                                                t("小窓を半透明にしました（コードが透けます）", "Window set to translucent", "小窗已设为半透明（代码透视）")
                                            } else {
                                                t("小窓を不透明に戻しました", "Window set to opaque", "小窗已恢复不透明")
                                            }
                                        }
                                        .semantics { contentDescription = t("半透明切替", "Toggle opacity", "半透明切换") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_pip_opacity),
                                        contentDescription = null,
                                        tint = if (translucent) colors.primary else colors.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }

                                Spacer(Modifier.width(2.dp))

                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            sizePreset = when (sizePreset) {
                                                PipSizePreset.SMALL -> PipSizePreset.MEDIUM
                                                PipSizePreset.MEDIUM -> PipSizePreset.LARGE
                                                PipSizePreset.LARGE -> PipSizePreset.SMALL
                                            }
                                            note = t("サイズを変更: ${sizePreset.shortLabel}", "Resized to ${sizePreset.shortLabel}", "尺寸切换为: ${sizePreset.shortLabel}")
                                        }
                                        .semantics { contentDescription = t("サイズ切替", "Toggle size", "尺寸切换") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        sizePreset.shortLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primary
                                    )
                                }

                                Spacer(Modifier.width(2.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (minimized) colors.primary.copy(alpha = 0.15f) else Color.Transparent)
                                    .then(if (minimized) Modifier.border(1.dp, colors.primary.copy(alpha = 0.8f), RoundedCornerShape(6.dp)) else Modifier)
                                    .clickable {
                                        minimized = !minimized
                                        note = if (minimized) {
                                            t("小窓を最小化しました", "Window minimized", "小窗已最小化")
                                        } else {
                                            t("小窓を展開しました", "Window expanded", "小窗已展开")
                                        }
                                    }
                                    .semantics { contentDescription = t("最小化切替", "Toggle minimize", "最小化切换") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painterResource(if (minimized) R.drawable.ic_pip_expand else R.drawable.ic_pip_minimize),
                                    contentDescription = null,
                                    tint = if (minimized) colors.primary else colors.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        if (!minimized) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(MockPaper)
                                    .clickable {
                                        corner = when (corner) {
                                            PipCorner.TOP_END -> PipCorner.BOTTOM_END
                                            PipCorner.BOTTOM_END -> PipCorner.BOTTOM_START
                                            PipCorner.BOTTOM_START -> PipCorner.TOP_START
                                            PipCorner.TOP_START -> PipCorner.TOP_END
                                        }
                                        note = t("小窓をタップして移動しました", "Tapped PiP window to move", "点击小窗完成移动")
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(Modifier.fillMaxSize()) {
                                    drawCircle(MockInks[1], radius = size.height * 0.32f)
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(t("4隅スナップ移動", "Corner snap", "四角吸附移动"), style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val cornerOptions = listOf(
                Pair(PipCorner.TOP_START, t("左上", "Top-L", "左上")),
                Pair(PipCorner.TOP_END, t("右上", "Top-R", "右上")),
                Pair(PipCorner.BOTTOM_START, t("左下", "Btm-L", "左下")),
                Pair(PipCorner.BOTTOM_END, t("右下", "Btm-R", "右下"))
            )
            cornerOptions.forEach { (c, label) ->
                val selected = corner == c
                Surface(
                    onClick = {
                        corner = c
                        note = t("「${label}」へスナップしました", "Snapped to $label", "已吸附到 $label")
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) colors.primaryContainer else colors.surfaceContainerHighest,
                    border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant)
                ) {
                    Box(Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) colors.onPrimaryContainer else colors.onSurface
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(t("サイズ:", "Size:", "尺寸:"), style = MaterialTheme.typography.labelSmall)
            listOf(PipSizePreset.SMALL, PipSizePreset.MEDIUM, PipSizePreset.LARGE).forEach { preset ->
                val active = sizePreset == preset && !minimized
                Surface(
                    onClick = {
                        sizePreset = preset
                        minimized = false
                        note = t("サイズを変更: ${preset.shortLabel}", "Resized to ${preset.shortLabel}", "尺寸切换为: ${preset.shortLabel}")
                    },
                    shape = RoundedCornerShape(6.dp),
                    color = if (active) colors.primaryContainer else colors.surfaceContainerHighest,
                    border = BorderStroke(1.dp, if (active) colors.primary else colors.outlineVariant)
                ) {
                    Text(
                        preset.shortLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) colors.onPrimaryContainer else colors.onSurface
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Surface(
                onClick = {
                    translucent = !translucent
                    note = if (translucent) {
                        t("小窓を半透明にしました", "Window translucent", "小窗半透明")
                    } else {
                        t("小窓を不透明に戻しました", "Window opaque", "小窗不透明")
                    }
                },
                shape = RoundedCornerShape(6.dp),
                color = if (translucent) colors.primaryContainer else colors.surfaceContainerHighest,
                border = BorderStroke(1.dp, if (translucent) colors.primary else colors.outlineVariant)
            ) {
                Text(
                    t("半透明", "Opacity", "半透明"),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (translucent) colors.onPrimaryContainer else colors.onSurface
                )
            }
        }

        MockCaption(
            note ?: t(
                "小窓のボタンや4隅ボタンで、位置・サイズ・透過を試せます（練習用）",
                "Try changing position, size and opacity using the buttons (practice)",
                "可使用小窗按键或四角按键体验位置、尺寸与半透明（练习）"
            )
        )
    }
}

@Composable
private fun MockParameters(language: String) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    var diameter by remember { mutableFloatStateOf(120f) }
    var ink by remember { mutableIntStateOf(0) }
    val colors = MaterialTheme.colorScheme
    MockFrame {
        MockCanvas(diameter / 240f, MockInks[ink])
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ParameterButton(
                parameterCount = 2,
                colors = colors,
                height = 36.dp,
                minWidth = 36.dp,
                iconSize = 18.dp,
                onClick = {},
                textTranslator = { s, _ -> translateUi(s, language) },
                active = true
            )
            Text(t("直径  ${diameter.toInt()}", "Diameter  ${diameter.toInt()}", "直径  ${diameter.toInt()}"), style = MaterialTheme.typography.labelLarge)
        }
        StudioSlider(value = diameter, onValueChange = { diameter = it }, valueRange = 20f..240f)
        Text(t("インク", "Ink", "颜色"), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MockInks.forEachIndexed { index, color ->
                Surface(
                    onClick = { ink = index },
                    modifier = Modifier.size(40.dp).semantics { contentDescription = t("色 ${index + 1}", "Color ${index + 1}", "颜色 ${index + 1}") },
                    shape = CircleShape, color = color,
                    border = BorderStroke(if (ink == index) 3.dp else 1.dp, if (ink == index) colors.primary else colors.outlineVariant)
                ) {}
            }
        }
        MockCaption(t("練習用の画面です。自分の作品には影響しません", "This is a practice screen. It does not affect your works", "这是练习画面，不会影响您的作品"))
    }
}

@Composable
private fun MockPauseHistory(language: String, animate: Boolean) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    var paused by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.colorScheme
    val pulse = rememberLoopPhase(animate && !paused, 1600, true, FastOutSlowInEasing, 0.5f)
    MockFrame {
        MockCanvas(0.3f + 0.4f * pulse, MockInks[1])
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RunStatusControls(
                isError = false,
                isPaused = paused,
                colors = colors,
                height = 38.dp,
                buttonSize = 32.dp,
                iconSize = 17.dp,
                onTogglePause = { paused = !paused },
                onReload = { paused = false },
                textTranslator = { source, _ ->
                    when (source) {
                        "再生" -> t("再開", "Resume", "继续")
                        "一時停止" -> t("一時停止", "Pause", "暂停")
                        else -> translateUi(source, language)
                    }
                },
                modifier = Modifier.tapHint(!paused && !menu && message == null, animate)
            )
            FilledTonalIconButton(onClick = { menu = !menu }) {
                Icon(painterResource(R.drawable.ic_history), contentDescription = t("履歴", "History", "历史"))
            }
            Text(
                if (paused) t("一時停止中", "Paused", "已暂停") else t("実行中", "Running", "运行中"),
                style = MaterialTheme.typography.labelLarge
            )
        }
        if (menu) {
            OutlinedCard {
                Column {
                    TextButton(onClick = {
                        message = t("スナップショットを作成しました（練習用）", "Snapshot created (practice)", "已创建快照（练习）"); menu = false
                    }, modifier = Modifier.fillMaxWidth()) { Text(t("スナップショットを作成", "Create snapshot", "创建快照")) }
                    TextButton(onClick = {
                        message = t("保存済みの状態に戻しました（練習用）", "Reverted to the saved work (practice)", "已恢复到已保存状态（练习）"); menu = false
                    }, modifier = Modifier.fillMaxWidth()) { Text(t("保存済みの状態に戻す", "Revert to saved", "恢复到已保存状态")) }
                }
            }
        }
        MockCaption(message ?: t("一時停止を押してから、履歴を開いてみましょう", "Press Pause, then open History", "先点击暂停，再打开历史"))
    }
}

@Composable
private fun MockCapture(language: String, animate: Boolean) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    var captured by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.colorScheme
    MockFrame {
        MockCanvas(0.55f, MockInks[0]) {
            if (recording) {
                Row(Modifier.align(Alignment.TopStart).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFD93025)))
                    Spacer(Modifier.width(6.dp))
                    Text("REC", style = MaterialTheme.typography.labelMedium, color = MockInk)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PreviewActionsToggleButton(
                isRecordingOrCountingDown = recording,
                previewActionsExpanded = true,
                isLandscape = false,
                colors = colors,
                onClick = {},
                textTranslator = { s, _ -> translateUi(s, language) }
            )
            FilledTonalIconButton(
                onClick = {
                    captured = true
                    note = t("画像を書き出しました（練習用：端末には保存されません）", "Image exported (Practice mode: not saved to device)", "已导出图片（练习模式：不会保存到设备）")
                },
                modifier = Modifier.tapHint(note == null, animate)
            ) { Icon(painterResource(R.drawable.ic_camera), contentDescription = t("画像を書き出す", "Export image", "导出图片")) }
            FilledTonalIconButton(onClick = {
                recording = !recording
                if (recording) note = t("録画を開始しました", "Recording started", "已开始录制")
                else { captured = true; note = t("録画を止めました（練習用：端末には保存されません）", "Recording stopped (Practice mode: not saved to device)", "已停止录制（练习模式：不会保存到设备）") }
            }) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(Color(0xFFD93025)))
            }
            FilledTonalButton(
                onClick = { note = t("保存した画像や録画は、ここからほかのアプリで共有できます（練習用）", "Saved images and recordings can be shared from here through another app (practice)", "已保存的图片和录制内容可在这里通过其他应用分享（练习）") },
                enabled = captured
            ) { Text(t("共有", "Share", "分享")) }
        }
        MockCaption(note ?: t("画像の書き出しや録画を試してみましょう", "Try exporting an image or recording", "试试导出图片或录制"))
    }
}

@Composable
private fun MockFinish() {
    val colors = MaterialTheme.colorScheme
    MockFrame {
        Box(Modifier.fillMaxWidth().aspectRatio(1.9f).clip(RoundedCornerShape(14.dp)).background(MockPaper), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.height * 0.26f
                val cy = size.height / 2f
                MockInks.forEachIndexed { index, color ->
                    drawCircle(color.copy(alpha = 0.78f), radius = r, center = Offset(size.width / 2f + (index - 1) * r * 0.9f, cy))
                }
            }
        }
        Text("Edit:RiN", style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
    }
}
