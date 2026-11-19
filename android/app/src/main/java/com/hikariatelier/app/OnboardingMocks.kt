package com.hikariatelier.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Shared interactive studio mocks for the 5-step unified onboarding tour.
 * All state lives in OnboardingStudioState so one generative artwork evolves across Steps 0..4
 * without reading or writing real user works, files, or persistent settings.
 */

private val StudioPaper = Color(0xFFF2EFE7)
private val StudioInkDark = Color(0xFF2B2B2B)
private val StudioInks = listOf(
    Color(0xFFD67856), // Terracotta
    Color(0xFF3F6F8F), // Indigo
    Color(0xFF4F7D5C), // Olive
    Color(0xFF8A5EA6)  // Violet
)
private val StudioInkHexes = listOf("#D67856", "#3F6F8F", "#4F7D5C", "#8A5EA6")

internal class OnboardingStudioState {
    var selectedSample by mutableIntStateOf(0) // 0=Orbit, 1=Palette, 2=Wave
    var ran by mutableStateOf(false)
    var paused by mutableStateOf(false)
    var inkIndex by mutableIntStateOf(0)
    var orbitRadius by mutableFloatStateOf(100f)
    var particleCount by mutableIntStateOf(6)
    var hasOuterRing by mutableStateOf(false)
    var shapeKind by mutableStateOf("circle") // "circle", "square", "triangle"
    var watched by mutableStateOf(false)
    var compressed by mutableStateOf(false)
    var snapshotSaved by mutableStateOf(false)
    var snapshotShape by mutableStateOf("circle")
    var snapshotInk by mutableIntStateOf(0)
    var snapshotRadius by mutableFloatStateOf(100f)
    var exportedScale by mutableIntStateOf(0)
    var recording by mutableStateOf(false)
    var pipCorner by mutableStateOf(PipCorner.TOP_END)
    var pipSizePreset by mutableStateOf(PipSizePreset.SMALL)
    var pipTranslucent by mutableStateOf(false)
    var pipMinimized by mutableStateOf(false)
}

private fun mockUiTranslator(language: String): (String, Array<out Any?>) -> String = { source, args ->
    val translated = translateUi(source, language)
    if (args.isEmpty()) translated else String.format(java.util.Locale.ROOT, translated, *args)
}

@Composable
internal fun OnboardingMock(
    step: Int,
    language: String,
    animate: Boolean,
    studioState: OnboardingStudioState = remember { OnboardingStudioState() }
) {
    when (step) {
        0 -> MockStudioRunAndPip(language, animate, studioState)
        1 -> MockStudioBlockMode(language, animate, studioState)
        2 -> MockStudioLiveTuning(language, animate, studioState)
        3 -> MockStudioHistoryExportGolf(language, animate, studioState)
        else -> MockStudioFinish(language, animate, studioState)
    }
}

private object FullSpeedMotion : MotionDurationScale {
    override val scaleFactor: Float get() = 1f
}

@Composable
private fun rememberLoopPhase(enabled: Boolean, periodMillis: Int, reverse: Boolean, easing: Easing, idle: Float): Float {
    val phase = remember { Animatable(idle) }
    if (enabled) {
        LaunchedEffect(Unit) {
            withContext(FullSpeedMotion) {
                phase.snapTo(0f)
                phase.animateTo(
                    1f,
                    infiniteRepeatable(
                        tween(periodMillis, easing = easing),
                        if (reverse) RepeatMode.Reverse else RepeatMode.Restart
                    )
                )
            }
        }
    }
    return phase.value
}

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
        modifier = Modifier.widthIn(max = 390.dp).fillMaxWidth().heightIn(max = 500.dp),
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
private fun MockCaption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Shared generative artwork canvas that reflects every customization made across tour steps. */
@Composable
internal fun GenerativeStudioCanvas(
    studio: OnboardingStudioState,
    phase: Float,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val primary = StudioInks[studio.inkIndex % StudioInks.size]
    val secondary = StudioInks[(studio.inkIndex + 1) % StudioInks.size]
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(StudioPaper)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val baseScale = size.minDimension / 240f
            val orbitR = (studio.orbitRadius * 0.62f * baseScale).coerceIn(14f, size.minDimension * 0.43f)
            val coreR = (size.minDimension * 0.16f).coerceIn(10f, 36f)
            val angleOffset = (phase * 2.0 * PI) + if (studio.ran) 0.5 else 0.0

            // Subtle guide orbit ring
            drawCircle(
                color = StudioInkDark.copy(alpha = 0.12f),
                radius = orbitR,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Outer ring block addition
            if (studio.hasOuterRing) {
                val pulseR = orbitR * (1.22f + 0.06f * sin(angleOffset).toFloat())
                drawCircle(
                    color = secondary.copy(alpha = 0.72f),
                    radius = pulseR.coerceAtMost(size.minDimension * 0.47f),
                    center = Offset(cx, cy),
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // Sample-specific wave or palette arc motif
            if (studio.selectedSample == 2) {
                val wavePath = Path()
                val steps = 20
                for (i in 0..steps) {
                    val t = i / steps.toFloat()
                    val x = size.width * (0.1f + 0.8f * t)
                    val y = cy + sin(t * 4.0 * PI + angleOffset).toFloat() * (orbitR * 0.45f)
                    if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                }
                drawPath(wavePath, color = secondary.copy(alpha = 0.45f), style = Stroke(width = 2.dp.toPx()))
            }

            // Center shape (circle / square / triangle)
            when (studio.shapeKind) {
                "square" -> {
                    val side = coreR * 1.8f
                    drawRoundRect(
                        color = primary,
                        topLeft = Offset(cx - side / 2f, cy - side / 2f),
                        size = Size(side, side),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
                "triangle" -> {
                    val p = Path().apply {
                        moveTo(cx, cy - coreR * 1.15f)
                        lineTo(cx + coreR * 1.05f, cy + coreR * 0.9f)
                        lineTo(cx - coreR * 1.05f, cy + coreR * 0.9f)
                        close()
                    }
                    drawPath(p, color = primary)
                }
                else -> {
                    drawCircle(color = primary, radius = coreR, center = Offset(cx, cy))
                }
            }

            // Orbiting satellite particles
            val count = studio.particleCount.coerceIn(3, 12)
            for (i in 0 until count) {
                val theta = angleOffset + (i * 2.0 * PI / count)
                val px = cx + cos(theta).toFloat() * orbitR
                val py = cy + sin(theta).toFloat() * orbitR
                val dotR = (4.5.dp.toPx() * (if (i % 2 == 0) 1f else 0.72f)).coerceAtMost(coreR * 0.45f)
                drawCircle(
                    color = if (i % 2 == 0) primary.copy(alpha = 0.88f) else secondary.copy(alpha = 0.82f),
                    radius = dotR,
                    center = Offset(px, py)
                )
            }
        }
        overlay()
    }
}

/** Step 0: Write, Run & Floating PiP */
@Composable
private fun MockStudioRunAndPip(language: String, animate: Boolean, studio: OnboardingStudioState) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val uiTranslator = remember(language) { mockUiTranslator(language) }
    val phase = rememberLoopPhase(animate && !studio.paused, 3200, false, LinearEasing, 0.25f)
    var note by remember { mutableStateOf<String?>(null) }

    val sampleNames = listOf("Orbit", "Palette", "Wave")
    val sampleCode = remember(studio.selectedSample, studio.inkIndex) {
        when (studio.selectedSample) {
            1 -> "function draw() {\n  background('#F2EFE7');\n  orbitSketch('${StudioInkHexes[studio.inkIndex]}', 8);\n}"
            2 -> "function draw() {\n  background('#F2EFE7');\n  waveSketch('${StudioInkHexes[studio.inkIndex]}');\n}"
            else -> "function draw() {\n  background('#F2EFE7');\n  circle(width / 2, height / 2, 120);\n}"
        }
    }
    val highlighted = remember(sampleCode, dark) {
        JavaScriptHighlighter(dark).filter(AnnotatedString(sampleCode)).text
    }

    val pipAlign = when (studio.pipCorner) {
        PipCorner.TOP_START -> Alignment.TopStart
        PipCorner.TOP_END -> Alignment.TopEnd
        PipCorner.BOTTOM_START -> Alignment.BottomStart
        PipCorner.BOTTOM_END -> Alignment.BottomEnd
    }
    val targetWidth = when (studio.pipSizePreset) {
        PipSizePreset.SMALL -> 108.dp
        PipSizePreset.MEDIUM -> 132.dp
        PipSizePreset.LARGE -> 156.dp
    }
    val targetHeight = if (studio.pipMinimized) 28.dp else targetWidth
    val animW by animateDpAsState(targetWidth, label = "step0PipW")
    val animH by animateDpAsState(targetHeight, label = "step0PipH")

    MockFrame {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            sampleNames.forEachIndexed { index, name ->
                FilterChip(
                    selected = studio.selectedSample == index,
                    onClick = {
                        studio.selectedSample = index
                        studio.inkIndex = index
                        studio.particleCount = when (index) {
                            1 -> 8
                            2 -> 10
                            else -> 6
                        }
                        studio.ran = true
                        note = t("サンプル「$name」を実行しました", "Ran sample “$name”", "已运行示例“$name”")
                    },
                    label = { Text(name, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Virtual Editor + Floating PiP Preview overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceContainerHigh)
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp))
        ) {
            Column(Modifier.fillMaxSize()) {
                EditorDocumentBar(
                    files = listOf("sketch.js"),
                    selectedFile = "sketch.js",
                    dirtyFiles = if (!studio.ran) setOf("sketch.js") else emptySet(),
                    showingBlocks = false,
                    allowBlocks = true,
                    enabled = true,
                    readOnly = false,
                    autoRunEnabled = false,
                    previewHasChanges = !studio.ran,
                    onSelectFile = {},
                    onSelectBlocks = {},
                    onAddFile = null,
                    onToggleAutoRun = {},
                    onRunChanges = { studio.ran = true },
                    textTranslator = uiTranslator
                )
                Text(
                    highlighted,
                    Modifier.fillMaxWidth().padding(10.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Surface(
                modifier = Modifier
                    .align(pipAlign)
                    .padding(6.dp)
                    .size(animW, animH),
                shape = RoundedCornerShape(10.dp),
                color = if (studio.pipTranslucent) colors.surface.copy(alpha = 0.65f) else colors.surface,
                border = BorderStroke(1.dp, if (studio.pipTranslucent) colors.primary else colors.outlineVariant),
                shadowElevation = 4.dp
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().height(26.dp).background(colors.surfaceContainerLow).padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("PiP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (!studio.pipMinimized) {
                            Icon(
                                painterResource(R.drawable.ic_pip_opacity),
                                contentDescription = t("半透明切替", "Toggle opacity", "半透明切换"),
                                tint = if (studio.pipTranslucent) colors.primary else colors.onSurfaceVariant,
                                modifier = Modifier.size(16.dp).clickable {
                                    studio.pipTranslucent = !studio.pipTranslucent
                                }
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                studio.pipSizePreset.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    studio.pipSizePreset = when (studio.pipSizePreset) {
                                        PipSizePreset.SMALL -> PipSizePreset.MEDIUM
                                        PipSizePreset.MEDIUM -> PipSizePreset.LARGE
                                        PipSizePreset.LARGE -> PipSizePreset.SMALL
                                    }
                                }
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { studio.pipMinimized = !studio.pipMinimized }
                                .semantics { contentDescription = t("最小化切替", "Toggle minimize", "最小化切换") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painterResource(if (studio.pipMinimized) R.drawable.ic_pip_expand else R.drawable.ic_pip_minimize),
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    if (!studio.pipMinimized) {
                        GenerativeStudioCanvas(
                            studio = studio,
                            phase = phase,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(2.dp)
                        )
                    }
                }
            }
        }

        // Corner & size quick controls
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                PipCorner.TOP_START to t("左上", "Top-L", "左上"),
                PipCorner.TOP_END to t("右上", "Top-R", "右上"),
                PipCorner.BOTTOM_START to t("左下", "Btm-L", "左下"),
                PipCorner.BOTTOM_END to t("右下", "Btm-R", "右下")
            ).forEach { (corner, label) ->
                val selected = studio.pipCorner == corner
                Surface(
                    onClick = { studio.pipCorner = corner },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) colors.primaryContainer else colors.surfaceContainerHighest
                ) {
                    Box(Modifier.padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RunStatusControls(
                isError = false,
                isPaused = studio.paused,
                hasPendingChanges = !studio.ran,
                colors = colors,
                height = 38.dp,
                buttonSize = 32.dp,
                iconSize = 17.dp,
                onTogglePause = { studio.paused = !studio.paused },
                onReload = { studio.ran = true; studio.orbitRadius = 120f },
                textTranslator = uiTranslator
            )
            Button(
                onClick = {
                    studio.ran = true
                    studio.orbitRadius = 120f
                    note = t("作品を実行し、プレビューを更新しました", "Updated preview", "已运行并更新画面")
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f).tapHint(!studio.ran, animate)
            ) {
                Icon(painterResource(R.drawable.ic_reload), contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(t("実行", "Run", "运行"), maxLines = 1)
            }
        }

        MockCaption(note ?: t("サンプル切替・PiP小窓移動・実行を試してみましょう", "Try switching samples, moving PiP, or pressing Run", "试试切换示例、移动小窗或点击运行"))
    }
}

/** Step 1: Two-Way Block & Code Editing */
@Composable
private fun MockStudioBlockMode(language: String, animate: Boolean, studio: OnboardingStudioState) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val uiTranslator = remember(language) { mockUiTranslator(language) }
    val phase = rememberLoopPhase(animate && !studio.paused, 3200, false, LinearEasing, 0.35f)
    var showingBlocks by remember { mutableStateOf(true) }
    var paletteOpen by remember { mutableStateOf(false) }
    var fullPreviewOpen by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    val chromeModel = remember(paletteOpen) {
        BlockEditorChromeModel().apply {
            state = BlockChromeState(editable = true, busy = false, palette = paletteOpen)
        }
    }

    MockFrame {
        EditorDocumentBar(
            files = listOf("sketch.js"),
            selectedFile = "sketch.js",
            dirtyFiles = setOf("sketch.js"),
            showingBlocks = showingBlocks,
            allowBlocks = true,
            enabled = true,
            readOnly = false,
            autoRunEnabled = true,
            previewHasChanges = false,
            onSelectFile = {},
            onSelectBlocks = { showingBlocks = it },
            onAddFile = null,
            onToggleAutoRun = {},
            onRunChanges = {},
            textTranslator = uiTranslator,
            onOpenFullPreview = { fullPreviewOpen = true }
        )

        if (fullPreviewOpen) {
            Box(Modifier.fillMaxWidth().height(190.dp).testTag("onboarding-block-full-preview")) {
                GenerativeStudioCanvas(studio, phase, Modifier.fillMaxSize())
                FilledTonalButton(
                    onClick = { fullPreviewOpen = false },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(t("閉じる", "Close", "关闭"), maxLines = 1)
                }
            }
        } else if (!showingBlocks) {
            val codeText = buildString {
                append("function draw() {\n  background('#F2EFE7');\n")
                append("  fill('${StudioInkHexes[studio.inkIndex]}');\n")
                append("  orbit(${studio.orbitRadius.toInt()}, ${studio.particleCount});\n")
                if (studio.hasOuterRing) append("  outerRing();\n")
                append("}")
            }
            val highlighted = remember(codeText, dark) {
                JavaScriptHighlighter(dark).filter(AnnotatedString(codeText)).text
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
                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFD97724), contentColor = Color.White) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("function draw()", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Surface(
                                onClick = {
                                    studio.inkIndex = (studio.inkIndex + 1) % StudioInks.size
                                    note = t("ブロックの色スウォッチを更新しました", "Updated block color swatch", "已更新积木色块颜色")
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF8B5CF6),
                                contentColor = Color.White,
                                modifier = Modifier.semantics {
                                    contentDescription = t("ブロックの色を変更", "Change block color", "修改积木颜色")
                                }
                            ) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("fill", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Box(Modifier.size(14.dp).clip(CircleShape).background(StudioInks[studio.inkIndex]).border(1.5.dp, Color.White, CircleShape))
                                    Text(StudioInkHexes[studio.inkIndex], style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                }
                            }
                            Surface(
                                onClick = {
                                    studio.orbitRadius = if (studio.orbitRadius >= 160f) 80f else studio.orbitRadius + 30f
                                    note = t("軌道半径を ${studio.orbitRadius.toInt()} に変更しました", "Updated orbit radius to ${studio.orbitRadius.toInt()}", "轨道半径已更改为 ${studio.orbitRadius.toInt()}")
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF2E8B8B),
                                contentColor = Color.White
                            ) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("orbit", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Surface(shape = RoundedCornerShape(999.dp), color = Color.White.copy(alpha = 0.28f)) {
                                        Text("${studio.orbitRadius.toInt()}", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                            if (studio.hasOuterRing) {
                                Surface(
                                    onClick = { studio.hasOuterRing = false },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF2E8B8B),
                                    contentColor = Color.White
                                ) {
                                    Text(t("外周リング (タップで削除)", "outerRing (tap to remove)", "外圈波纹（点击移除）"), Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (paletteOpen) {
                        OutlinedCard(
                            onClick = {
                                studio.hasOuterRing = true
                                paletteOpen = false
                                note = t("外周リングを追加しました", "Added outer ring block", "已添加外圈积木")
                            },
                            modifier = Modifier.fillMaxWidth().testTag("onboarding-insert-ring-block")
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(t("外周リングを追加", "Add outer ring", "添加外圈波纹"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("outerRing()", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = colors.onSurfaceVariant)
                                }
                                Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.primary)
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outlineVariant),
                    shadowElevation = 4.dp,
                    modifier = Modifier.align(Alignment.TopEnd).size(width = 96.dp, height = 84.dp)
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("AUTO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                            Icon(
                                painterResource(R.drawable.ic_fullscreen),
                                contentDescription = null,
                                modifier = Modifier.size(13.dp).clickable { fullPreviewOpen = true }
                            )
                        }
                        GenerativeStudioCanvas(studio, phase, Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }

            BlockEditorFloatingDock(
                model = chromeModel,
                ready = true,
                fullscreen = true,
                canUndo = studio.hasOuterRing,
                canRedo = false,
                text = { uiTranslator(it, emptyArray()) },
                onCommand = { cmd ->
                    when (cmd) {
                        "add" -> paletteOpen = true
                        "dismiss" -> paletteOpen = false
                        "undo" -> studio.hasOuterRing = false
                    }
                },
                onSearch = {},
                onDisplay = {},
                onFullscreen = { fullPreviewOpen = !fullPreviewOpen },
                modifier = Modifier.tapHint(!studio.hasOuterRing && !paletteOpen, animate)
            )
        }

        MockCaption(note ?: t("ブロックの色・半径をタップしたり、「＋ ブロックを追加」を試してみましょう", "Tap color or orbit on blocks, or add an outer ring from the dock", "点击积木颜色/半径，或从底部栏添加新积木"))
    }
}

/** Step 2: Live Tuning with // ?, // @slider, // @color, // @rin */
@Composable
private fun MockStudioLiveTuning(language: String, animate: Boolean, studio: OnboardingStudioState) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val colors = MaterialTheme.colorScheme
    val phase = rememberLoopPhase(animate && !studio.paused, 3200, false, LinearEasing, 0.45f)
    val frameNumber = ((phase * 360).toInt())

    MockFrame {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GenerativeStudioCanvas(
                studio = studio,
                phase = phase,
                modifier = Modifier.size(104.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("実行中の値とスライダー", "Live values & sliders", "运行值与滑块"), style = MaterialTheme.typography.titleSmall)
                FilterChip(
                    selected = studio.watched,
                    onClick = { studio.watched = !studio.watched },
                    label = { Text(if (studio.watched) "// ? ON" else "// ? (Watch)") },
                    modifier = Modifier.testTag("onboarding-watch-toggle")
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colors.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "// @rin choice shape \"Shape\" circle square triangle",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Text(
                        if (studio.watched) "let f = frameCount; // ?" else "let f = frameCount;",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (studio.watched) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.primaryContainer,
                            modifier = Modifier.testTag("onboarding-watch-badge")
                        ) {
                            Text(
                                "⇒ f = $frameNumber",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = colors.onPrimaryContainer
                            )
                        }
                    }
                }
                Text(
                    "let radius = ${studio.orbitRadius.toInt()}; // @slider(60, 180, 10)",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primary
                )
            }
        }

        Text(
            t("軌道半径 (radius): ${studio.orbitRadius.toInt()}", "Orbit radius: ${studio.orbitRadius.toInt()}", "轨道半径: ${studio.orbitRadius.toInt()}"),
            style = MaterialTheme.typography.labelMedium
        )
        StudioSlider(
            value = studio.orbitRadius,
            onValueChange = { studio.orbitRadius = it },
            valueRange = 60f..180f,
            modifier = Modifier.testTag("onboarding-live-slider")
        )

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("circle" to "circle", "square" to "square", "triangle" to "triangle").forEach { (kind, label) ->
                FilterChip(
                    selected = studio.shapeKind == kind,
                    onClick = { studio.shapeKind = kind },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StudioInks.forEachIndexed { index, color ->
                Surface(
                    onClick = { studio.inkIndex = index },
                    modifier = Modifier
                        .size(34.dp)
                        .semantics { contentDescription = t("色 ${index + 1}", "Color ${index + 1}", "颜色 ${index + 1}") },
                    shape = CircleShape,
                    color = color,
                    border = BorderStroke(if (studio.inkIndex == index) 3.dp else 1.dp, if (studio.inkIndex == index) colors.primary else colors.outlineVariant)
                ) {}
            }
        }

        MockCaption(t("変数の値チェックやスライダー・形状変更がそのまま作品に反映されます", "Watch values update live as you adjust sliders, shape, and color", "调节滑块、形状与颜色时，代码和画面会同步更新"))
    }
}

/** Step 3: Unified History, Export & Social Code Compression */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MockStudioHistoryExportGolf(language: String, animate: Boolean, studio: OnboardingStudioState) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val colors = MaterialTheme.colorScheme
    val uiTranslator = remember(language) { mockUiTranslator(language) }
    val phase = rememberLoopPhase(animate && !studio.paused, 3200, false, LinearEasing, 0.6f)
    var historyMenu by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    MockFrame {
        GenerativeStudioCanvas(
            studio = studio,
            phase = phase,
            modifier = Modifier.fillMaxWidth().aspectRatio(2.2f)
        ) {
            if (studio.recording) {
                Row(Modifier.align(Alignment.TopStart).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFD93025)))
                    Spacer(Modifier.width(5.dp))
                    Text("REC", style = MaterialTheme.typography.labelSmall, color = StudioInkDark, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Row 1: Playback, Unified History, Export & Share
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            RunStatusControls(
                isError = false,
                isPaused = studio.paused,
                colors = colors,
                height = 36.dp,
                buttonSize = 30.dp,
                iconSize = 16.dp,
                onTogglePause = { studio.paused = !studio.paused },
                onReload = { studio.paused = false },
                textTranslator = uiTranslator
            )
            FilledTonalIconButton(onClick = { historyMenu = !historyMenu }) {
                Icon(painterResource(R.drawable.ic_history), contentDescription = t("履歴", "History", "历史"))
            }
            FilledTonalIconButton(
                onClick = {
                    studio.exportedScale = when (studio.exportedScale) {
                        1 -> 2
                        2 -> 4
                        else -> 1
                    }
                    note = t("PNG (${studio.exportedScale}x) を書き出しました（練習用）", "Exported ${studio.exportedScale}x PNG (practice)", "已导出 ${studio.exportedScale}x PNG（练习）")
                }
            ) {
                Icon(painterResource(R.drawable.ic_camera), contentDescription = t("画像を書き出す", "Export image", "导出图片"))
            }
            FilledTonalIconButton(
                onClick = {
                    studio.recording = !studio.recording
                    note = if (studio.recording) t("録画を開始しました", "Recording started", "开始录制")
                    else {
                        if (studio.exportedScale == 0) studio.exportedScale = 1
                        t("MP4/GIF録画を停止しました（練習用）", "Recording stopped (practice)", "已停止录制（练习）")
                    }
                }
            ) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(Color(0xFFD93025)))
            }
            FilledTonalButton(
                onClick = { note = t("保存した画像や動画を他アプリへ共有できます", "Saved media can be shared right from here", "可在此直接分享已保存的作品") },
                enabled = studio.exportedScale > 0,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(t("共有", "Share", "分享"), maxLines = 1)
            }
        }

        if (historyMenu) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            studio.snapshotSaved = true
                            studio.snapshotShape = studio.shapeKind
                            studio.snapshotInk = studio.inkIndex
                            studio.snapshotRadius = studio.orbitRadius
                            historyMenu = false
                            note = t("スナップショットを保存しました", "Snapshot created", "已保存快照")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(t("スナップショットを作成", "Create snapshot", "创建快照"), maxLines = 1) }
                    if (studio.snapshotSaved) {
                        TextButton(
                            onClick = {
                                studio.shapeKind = studio.snapshotShape
                                studio.inkIndex = studio.snapshotInk
                                studio.orbitRadius = studio.snapshotRadius
                                historyMenu = false
                                note = t("スナップショットへ復元しました", "Restored snapshot", "已恢复快照")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(t("復元", "Restore", "恢复"), maxLines = 1) }
                    }
                }
            }
        }

        // Social Code Golf Minify Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colors.surfaceContainerHigh,
            border = BorderStroke(1.dp, if (studio.compressed) colors.primary else colors.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("#つぶやきProcessing", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (studio.compressed) t("88 / 280 字（圧縮済み）", "88 / 280 chars (Compressed)", "88 / 280 字符（已压缩）")
                        else t("158 / 280 字", "158 / 280 chars", "158 / 280 字符"),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (studio.compressed) colors.primary else colors.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = { studio.compressed = !studio.compressed },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("onboarding-golf-button")
                ) {
                    Text(if (studio.compressed) t("元に戻す", "Undo", "还原") else t("コードを圧縮", "Minify", "压缩代码"), maxLines = 1)
                }
            }
        }

        MockCaption(note ?: t("スナップショット作成・PNG書き出し・SNSコード圧縮を試してみましょう", "Try saving a snapshot, exporting media, or minifying code", "试试创建快照、导出媒体或一键压缩代码"))
    }
}

/** Step 4: Finished Custom Generative Artwork Summary */
@Composable
private fun MockStudioFinish(language: String, animate: Boolean, studio: OnboardingStudioState) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val colors = MaterialTheme.colorScheme
    val phase = rememberLoopPhase(animate && !studio.paused, 3200, false, LinearEasing, 0.7f)

    MockFrame {
        GenerativeStudioCanvas(
            studio = studio,
            phase = phase,
            modifier = Modifier.fillMaxWidth().aspectRatio(1.75f)
        )
        Text("Edit:RiN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.onSurface)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = {}, label = { Text(studio.shapeKind) })
            AssistChip(onClick = {}, label = { Text("r = ${studio.orbitRadius.toInt()}") })
            if (studio.hasOuterRing) AssistChip(onClick = {}, label = { Text("outerRing") })
            if (studio.watched) AssistChip(onClick = {}, label = { Text("// ?") })
            if (studio.compressed) AssistChip(onClick = {}, label = { Text("#つぶやき") })
        }
        MockCaption(t("準備が整いました。下のボタンから始められます", "Ready to go. Choose how you want to start below.", "已准备就绪，请从下方选择开始方式。"))
    }
}
