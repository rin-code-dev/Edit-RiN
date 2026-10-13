package com.hikariatelier.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withContext

/*
 * Practice screens for the first-run tour. All state is local to these composables: they never
 * touch works, files, the preview, recording or sharing, so nothing a user tries here is saved.
 */

private val MockPaper = Color(0xFFF2EFE7)
private val MockInk = Color(0xFF2B2B2B)
private val MockInks = listOf(Color(0xFFD67856), Color(0xFF3F6F8F), Color(0xFF4F7D5C))

@Composable
internal fun OnboardingMock(step: Int, language: String, animate: Boolean) {
    when (step) {
        0 -> MockGallery(language, animate)
        1 -> MockRun(language, animate)
        2 -> MockParameters(language)
        3 -> MockPauseHistory(language, animate)
        4 -> MockCapture(language, animate)
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
    val grow = remember { Animatable(0.3f) }
    LaunchedEffect(ran) {
        if (ran) withContext(FullSpeedMotion) {
            if (animate) grow.animateTo(0.8f, tween(450)) else grow.snapTo(0.8f)
        }
    }
    val fraction = grow.value
    MockFrame {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                "function draw() {\n  background('#F2EFE7');\n  circle(width / 2, height / 2, 160);\n}",
                Modifier.fillMaxWidth().padding(12.dp),
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall
            )
        }
        MockCanvas(fraction, MockInks[0])
        Button(onClick = { ran = true }, modifier = Modifier.fillMaxWidth().tapHint(!ran, animate)) {
            Icon(painterResource(R.drawable.ic_reload), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(t("実行", "Run", "运行"))
        }
        MockCaption(
            if (ran) t("プレビューが更新されました（練習用）", "The preview is updated (practice)", "预览已更新（练习）")
            else t("コードを変更しました。「実行」を押して反映しましょう", "Code edited. Press Run to see the update!", "代码已修改，点击“运行”查看效果")
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
        Text(t("直径  ${diameter.toInt()}", "Diameter  ${diameter.toInt()}", "直径  ${diameter.toInt()}"), style = MaterialTheme.typography.labelLarge)
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
    val pulse = rememberLoopPhase(animate && !paused, 1600, true, FastOutSlowInEasing, 0.5f)
    MockFrame {
        MockCanvas(0.3f + 0.4f * pulse, MockInks[1])
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = { paused = !paused },
                modifier = Modifier.tapHint(!paused && !menu && message == null, animate)
            ) {
                Icon(painterResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause),
                    contentDescription = if (paused) t("再開", "Resume", "继续") else t("一時停止", "Pause", "暂停"))
            }
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
