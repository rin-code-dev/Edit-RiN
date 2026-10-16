package com.hikariatelier.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * Practice screens for the Editor Practice Tour. All state is local to these composables:
 * they never modify real works, files, or persistent app settings.
 */

private val PracticeMockPaper = Color(0xFFF2EFE7)
private val PracticeMockInk = Color(0xFF2B2B2B)
private val PracticeMockInks = listOf(
    Color(0xFFD67856), // Terracotta
    Color(0xFF3F6F8F), // Indigo
    Color(0xFF4F7D5C), // Olive
    Color(0xFF8A5EA6)  // Violet
)

private fun Color.toHexRgb(): String {
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    return String.format("#%02X%02X%02X", r, g, b)
}

@Composable
internal fun EditorPracticeMock(step: Int, language: String, animate: Boolean) {
    when (step) {
        0 -> MockPracticeWatch(language, animate)
        1 -> MockPracticeInlineParameters(language, animate)
        2 -> MockPracticeRinParameters(language, animate)
        else -> MockPracticeGolf(language, animate)
    }
}

private object PracticeFullSpeedMotion : MotionDurationScale {
    override val scaleFactor: Float get() = 1f
}

@Composable
private fun rememberPracticeLoopPhase(enabled: Boolean, periodMillis: Int, reverse: Boolean, easing: Easing, idle: Float): Float {
    val phase = remember { Animatable(idle) }
    if (enabled) {
        LaunchedEffect(Unit) {
            withContext(PracticeFullSpeedMotion) {
                phase.snapTo(0f)
                phase.animateTo(
                    1f,
                    infiniteRepeatable(tween(periodMillis, easing = easing), if (reverse) RepeatMode.Reverse else RepeatMode.Restart)
                )
            }
        }
    }
    return phase.value
}

@Composable
private fun Modifier.practiceTapHint(active: Boolean, animate: Boolean): Modifier {
    if (!active) return this
    val color = MaterialTheme.colorScheme.primary
    val phase = rememberPracticeLoopPhase(animate, 1400, false, LinearEasing, 0.35f)
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
private fun PracticeMockFrame(content: @Composable ColumnScope.() -> Unit) {
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

@Composable
private fun PracticeMockCaption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Step 0: リアルタイム変数ウォッチ (`// ?`) */
@Composable
private fun MockPracticeWatch(language: String, animate: Boolean) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    var watched by remember { mutableStateOf(false) }
    var frameCountVal by remember { mutableIntStateOf(128) }

    LaunchedEffect(watched, animate) {
        if (watched) {
            if (!animate) {
                frameCountVal = 128
            } else {
                while (true) {
                    delay(100)
                    frameCountVal = (frameCountVal + 1) % 999
                }
            }
        }
    }

    PracticeMockFrame {
        Text(t("リアルタイム変数ウォッチ", "Live Variable Watch", "实时变量监听"), style = MaterialTheme.typography.titleSmall)

        // Mock Code Block
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "function draw() {\n  background('#F2EFE7');",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        if (watched) "  let frame = frameCount; // ?" else "  let frame = frameCount;",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (watched) FontWeight.Bold else FontWeight.Normal,
                        color = if (watched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (watched) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.testTag("practice-watch-badge")
                        ) {
                            Text(
                                "⇒ frame = $frameCountVal",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Text(
                    "  circle(200, 200, (frame % 80) + 40);\n}",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Mock Canvas (Preview)
        Box(
            Modifier.fillMaxWidth().aspectRatio(2.2f).clip(RoundedCornerShape(14.dp)).background(PracticeMockPaper),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = if (watched) {
                    ((frameCountVal % 80) + 40).dp.toPx() / 2f
                } else {
                    50.dp.toPx()
                }
                drawCircle(PracticeMockInks[0], radius = radius.coerceIn(10f, size.height * 0.45f))
            }
        }

        // Mock Accessory Keyboard Bar
        Text(t("キーボード補助バー:", "Accessory Bar:", "辅助输入栏:"), style = MaterialTheme.typography.labelSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f).height(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("()", style = MaterialTheme.typography.labelMedium)
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f).height(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("{}", style = MaterialTheme.typography.labelMedium)
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f).height(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(";", style = MaterialTheme.typography.labelMedium)
                }
            }
            Button(
                onClick = { watched = !watched },
                modifier = Modifier.weight(1.5f).height(38.dp).practiceTapHint(!watched, animate).testTag("practice-watch-toggle"),
                contentPadding = PaddingValues(horizontal = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (watched) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                    contentColor = if (watched) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("// ?", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        PracticeMockCaption(
            if (watched) t("行末に「// ?」を付けると、毎フレームの値がリアルタイムに表示されます！",
                "Adding “// ?” displays live values on every frame in real-time!",
                "行末添加“// ?”即可实时显示每帧的数值！")
            else t("補助バーの「// ?」をタップしてウォッチを追加してみましょう",
                "Tap “// ?” on the accessory bar to add a live watch",
                "点击辅助栏上的“// ?”添加监听")
        )
    }
}

/** Step 1: インラインGUIパラメータ (`// @slider`, `// @color`) */
@Composable
private fun MockPracticeInlineParameters(language: String, animate: Boolean) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    var sizeVal by remember { mutableFloatStateOf(120f) }
    var selectedColor by remember { mutableStateOf(PracticeMockInks[0]) }
    var userInteracted by remember { mutableStateOf(false) }

    PracticeMockFrame {
        Text(t("インラインパラメータ", "Inline Parameters", "行内实时参数"), style = MaterialTheme.typography.titleSmall)

        // Mock Code Block
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "let size = ${sizeVal.toInt()}; // @slider(40, 200, 10)",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "let ink = \"${selectedColor.toHexRgb()}\"; // @color",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = selectedColor,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "fill(ink);\ncircle(200, 200, size);",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Mock Canvas
        Box(
            Modifier.fillMaxWidth().aspectRatio(2.2f).clip(RoundedCornerShape(14.dp)).background(PracticeMockPaper),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = (sizeVal / 200f) * (size.height * 0.45f)
                drawCircle(selectedColor, radius = r)
            }
        }

        // Interactive Parameter Controls
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().practiceTapHint(!userInteracted, animate)
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("size: ${sizeVal.toInt()}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(72.dp))
                    Slider(
                        value = sizeVal,
                        onValueChange = {
                            sizeVal = it
                            userInteracted = true
                        },
                        valueRange = 40f..200f,
                        steps = 15,
                        modifier = Modifier.weight(1f).testTag("practice-inline-slider")
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ink:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp))
                    PracticeMockInks.forEachIndexed { index, color ->
                        val selected = color == selectedColor
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable {
                                    selectedColor = color
                                    userInteracted = true
                                }
                                .then(
                                    if (selected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .testTag("practice-color-chip-$index")
                        )
                    }
                }
            }
        }

        PracticeMockCaption(
            t("スライダーを動かすと、プレビューとコード内の数値が同時に書き換わります！",
                "Dragging the slider updates both the preview and code literals at the same time!",
                "拖动滑块时，预览图形与代码中的数值会同步更新！")
        )
    }
}

/** Step 2: 独立パラメータ (`// @rin`) */
@Composable
private fun MockPracticeRinParameters(language: String, animate: Boolean) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    var shape by remember { mutableStateOf("circle") }
    var shapeSize by remember { mutableFloatStateOf(100f) }
    var userInteracted by remember { mutableStateOf(false) }

    PracticeMockFrame {
        Text(t("作品設定パネル (// @rin)", "Custom Settings Panel (// @rin)", "作品设置面板 (// @rin)"), style = MaterialTheme.typography.titleSmall)

        // Mock Code Block
        val shapeLabel = t("形状", "Shape", "图形")
        val sizeLabel = t("サイズ", "Size", "大小")
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "// @rin choice shape \"$shapeLabel\" circle square triangle\n// @rin number size \"$sizeLabel\" 60 160 100 1",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "function draw() {\n  background('#F2EFE7');\n  fill('#D67856');\n  drawShape(rinParams.shape, rinParams.size);\n}",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Mock Canvas
        Box(
            Modifier.fillMaxWidth().aspectRatio(2.2f).clip(RoundedCornerShape(14.dp)).background(PracticeMockPaper),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val s = (shapeSize / 160f) * (size.height * 0.75f)
                when (shape) {
                    "square" -> {
                        drawRoundRect(
                            color = PracticeMockInks[0],
                            topLeft = Offset((size.width - s) / 2f, (size.height - s) / 2f),
                            size = Size(s, s),
                            cornerRadius = CornerRadius(12.dp.toPx())
                        )
                    }
                    "triangle" -> {
                        val path = Path().apply {
                            moveTo(size.width / 2f, (size.height - s) / 2f)
                            lineTo((size.width + s) / 2f, (size.height + s) / 2f)
                            lineTo((size.width - s) / 2f, (size.height + s) / 2f)
                            close()
                        }
                        drawPath(path, color = PracticeMockInks[0])
                    }
                    else -> {
                        drawCircle(PracticeMockInks[0], radius = s / 2f)
                    }
                }
            }
        }

        // Mock Settings Panel
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().practiceTapHint(!userInteracted, animate)
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    t("作品パラメータ (rinParams)", "Sketch Parameters (rinParams)", "作品参数 (rinParams)"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                // Shape choice chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val shapes = listOf(
                        "circle" to t("○ 円", "○ Circle", "○ 圆形"),
                        "square" to t("□ 四角", "□ Square", "□ 正方形"),
                        "triangle" to t("△ 三角", "△ Triangle", "△ 三角形")
                    )
                    shapes.forEach { (key, label) ->
                        val selected = shape == key
                        FilterChip(
                            selected = selected,
                            onClick = {
                                shape = key
                                userInteracted = true
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f).testTag("practice-rin-shape-$key")
                        )
                    }
                }

                // Size slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$sizeLabel: ${shapeSize.toInt()}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(64.dp))
                    Slider(
                        value = shapeSize,
                        onValueChange = {
                            shapeSize = it
                            userInteracted = true
                        },
                        valueRange = 60f..160f,
                        modifier = Modifier.weight(1f).testTag("practice-rin-slider")
                    )
                }
            }
        }

        PracticeMockCaption(
            t("コード冒頭の // @rin 宣言が、自動的に作品設定パネルになります。値は作品に保存されます。",
                "Declarations of // @rin at the top automatically create a settings panel saved with the work.",
                "代码开头的 // @rin 声明会自动生成设置面板，数值随作品保存。")
        )
    }
}

/** Step 3: つぶやきGolf ＆ 完了 (`Golf`) */
@Composable
private fun MockPracticeGolf(language: String, animate: Boolean) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    var golfed by remember { mutableStateOf(false) }

    PracticeMockFrame {
        Text(t("つぶやきGolf 圧縮", "Tweet Golf Minification", "推文 Golf 极简压缩"), style = MaterialTheme.typography.titleSmall)

        // Mock Code Block
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (golfed) {
                    Text(
                        "// #つぶやきProcessing\ndraw=_=>{createCanvas(W=400,W);background(0);circle(200,200,sin(frameCount*.05)*150)}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        "// #つぶやきProcessing\nfunction setup() {\n  createCanvas(400, 400);\n}\nfunction draw() {\n  background(0);\n  circle(200, 200, sin(frameCount * 0.05) * 150);\n}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (golfed) t("88 / 280 文字", "88 / 280 chars", "88 / 280 字符")
                        else t("158 / 280 文字", "158 / 280 chars", "158 / 280 字符"),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (golfed) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (golfed) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFE8F5E9)
                        ) {
                            Text(
                                t("-70文字 (圧縮成功!)", "-70 chars (Compressed!)", "-70 字符 (压缩成功!)"),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }
            }
        }

        // Mock Golf Compression Action Button
        Button(
            onClick = { golfed = !golfed },
            modifier = Modifier.fillMaxWidth().practiceTapHint(!golfed, animate).testTag("practice-golf-button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (golfed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                contentColor = if (golfed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_format),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(if (golfed) t("元のコードに戻す", "Revert to Original", "还原为原始代码")
                 else t("Golf 圧縮を実行 (1行に短縮)", "Run Golf Minify", "执行 Golf 极简压缩"))
        }

        PracticeMockCaption(
            if (golfed) t("コードが極限まで圧縮されました！ハッシュタグを保持したままX (Twitter) へ投稿できます。",
                "Code compressed to the limit! Ready to tweet on X with hashtags preserved.",
                "代码已被极致压缩！保留标签的同时非常适合在 X (Twitter) 上发布。")
            else t("「Golf 圧縮を実行」をタップしてコードの自動短縮を試してみましょう",
                "Tap “Run Golf Minify” to test smart code compression",
                "点击“执行 Golf 极简压缩”体验智能代码压缩")
        )
    }
}
