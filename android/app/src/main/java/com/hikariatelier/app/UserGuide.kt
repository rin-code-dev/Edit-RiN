package com.hikariatelier.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

internal data class GuideSection(
    val title: String,
    val summary: String,
    val iconRes: Int = 0,
    val tag: String = "",
    val codeSnippet: String? = null,
    val steps: List<String>
)

/** English is the source text for the in-app user guide. */
internal val userGuideSections = listOf(
    GuideSection(
        title = "Getting Started & Folders",
        summary = "Edit:RiN is a p5.js creative coding environment designed for touch devices. Organize works in folders, explore read-only bundled samples, and execute sketches in real time.",
        iconRes = R.drawable.ic_play,
        tag = "Basics",
        steps = listOf(
            "Work Picker: Tap the title bar to open the gallery, switch between sketches, create new works, or search and tag your collection.",
            "Folder Organization: Group user works into custom one-level folders. Long-press any folder chip and drag horizontally to swap and reorder with adjacent tabs (My works is always fixed at the start). Deleting a folder keeps all contained works safely in Unfiled.",
            "Bundled Samples Protection: Official bundled samples are read-only originals. Experiment freely with live parameters, then tap Copy to edit to duplicate into your personal works before modifying code.",
            "Batch Operations: Select multiple works to move them into a folder, apply or remove tags, export as ZIP, or delete together.",
            "Canvas Sizing & Menu: Customize canvas aspect ratios (Device landscape, 16:9, 4:3, 1:1, 9:16, Device portrait) and access duplicate, export, or settings from the title menu."
        )
    ),
    GuideSection(
        title = "Code Editor & Smart Tools",
        summary = "A touchscreen-optimized development environment featuring session persistence, smart inline search, and live preview state synchronization.",
        iconRes = R.drawable.ic_code,
        tag = "Editor",
        steps = listOf(
            "Session State Preservation: Switching between works retains your active file, exact cursor position, scroll offset, and Undo/Redo history for smooth multitasking.",
            "Compact Search & Replace: Use the streamlined editor bar to search and replace text with match highlighting and preserved cursor position. Whole-work search remains available.",
            "Stale Preview Indication: An unapplied changes indicator appears when edits have not reached the running canvas. Tap Run changes to execute, with clear separation between current edits and previous errors.",
            "Multi-File Tabs: Switch effortlessly between JavaScript modules and HTML/CSS files configured under Work settings → Project files.",
            "Editing Utilities: Enjoy Prettier-style automatic formatting, bracket and block folding, intelligent p5.js autocompletion, and jump-to-line navigation."
        )
    ),
    GuideSection(
        title = "Live Parameters",
        summary = "Generate interactive sliders, color pickers, and toggle switches directly from code comments without writing custom UI widgets.",
        iconRes = R.drawable.ic_tune,
        tag = "Interactive",
        codeSnippet = """// @rin number speed "Speed" 0 3 1 0.1
// @rin color ink "Ink Color" #BA90E2
// @rin boolean glow "Glow" true

function draw() {
  background(20);
  fill(rinParams.ink);
  if (rinParams.glow) drawingContext.shadowBlur = 15;
  circle(width/2, height/2, 60 * rinParams.speed);
}""",
        steps = listOf(
            "Syntax: Declare parameters at the top of your scripts using // @rin followed by type (number, color, or boolean), name, title, and initial value.",
            "Real-time Binding: Access values instantly in code via rinParams.<name>. Adjusting sliders dynamically alters your canvas without restarting.",
            "Controls & Fine-Tuning: Use +/- step buttons, reset to defaults individually or all at once, or copy parameter templates from the panel."
        )
    ),
    GuideSection(
        title = "Assets & Path Sync",
        summary = "Bundle images, audio, video, fonts, and data files, and keep code references automatically synchronized when renaming.",
        iconRes = R.drawable.ic_assets,
        tag = "Assets",
        codeSnippet = """let img, snd;
function preload() {
  img = loadImage('assets/texture.png');
  snd = loadSound('assets/beat.mp3');
}""",
        steps = listOf(
            "Import Files: Add assets via Work settings → Work assets from device storage, with instant visual and audio previews.",
            "One-tap Code Insert: Tap 'Insert loading code' in the asset inspector to generate the exact preload loader at your cursor.",
            "Synchronized Asset Rename: Renaming an asset scans your project files and lets you update matching literal paths together with preview verification and safe rollback.",
            "Capacity: Supports up to 50 MB per file, and up to 100 files / 200 MB total per individual work."
        )
    ),
    GuideSection(
        title = "Runtime, WebGPU & Hardware",
        summary = "Choose between modern and legacy p5.js engines, next-generation WebGPU, and integrate device camera and microphone inputs.",
        iconRes = R.drawable.ic_terminal,
        tag = "Runtimes",
        steps = listOf(
            "p5.js Versioning: Select between p5.js 2.3.4 (modern web standards and WebGL/WebGPU) and 1.11.5 (legacy compatibility).",
            "Next-Gen WebGPU: Leverage WebGPU hardware rendering with automatic fallback to WebGL on unsupported hardware.",
            "Device Camera & Microphone: Capture live mobile camera video (createCapture) and live audio input (p5.AudioIn) directly in your sketches.",
            "Sound & Shaders: Audio synthesis (including MONO SYNTH SCOPE) starts smoothly on first touch, and bundled libraries like Matter.js and p5.brush provide rich physical and expressive brush strokes."
        )
    ),
    GuideSection(
        title = "Capture, Video & Recovery",
        summary = "Export high-resolution still images or smooth animated loops, backed by crash-proof recording recovery.",
        iconRes = R.drawable.ic_camera,
        tag = "Media",
        steps = listOf(
            "High-Resolution PNG: Capture crisp screenshots with 1x, 2x, or 4x offscreen rendering (up to 16 MP), with direct Open and Share buttons on save completion.",
            "Video & GIF Recording: Record fluid MP4 video (up to 60s) or animated GIFs (up to 15s) directly from the running preview canvas.",
            "Recording Recovery: If a recording save fails, the captured data is preserved in temporary storage so you can retry, select an alternate folder, or explicitly discard.",
            "Quality Settings: Adjust MP4 bitrate (up to 16 Mbps) and recording countdown delays in Settings."
        )
    ),
    GuideSection(
        title = "Share Card & QR Web Play",
        summary = "Turn sketches into 1080p shareable artwork cards with scannable QR codes that run instantly in any mobile or desktop web browser.",
        iconRes = R.drawable.ic_share_card,
        tag = "Share",
        steps = listOf(
            "Generate Card: Open Share Card from the preview toolbar to produce a high-resolution card featuring your canvas artwork and a QR code.",
            "Card Customization: Pick from four sleek themes (Dark, Midnight, Cyber, Light) and add your author name or handle (by @...).",
            "Instant Web Play: Anyone scanning the QR code with their phone camera can play and interact with your sketch in a browser without installing the app.",
            "Source Visibility: Choose whether viewers can inspect the sketch's JavaScript source code in the web player or keep it hidden."
        )
    ),
    GuideSection(
        title = "Backup & Cloud Sync",
        summary = "Safeguard your creative portfolio with versatile backup tools and automated external sync support.",
        iconRes = R.drawable.ic_save,
        tag = "Storage",
        steps = listOf(
            "Single Work ZIP: Export or import an individual sketch with all scripts, assets, and metadata bundled together.",
            "Full Backup Archive: Package all works, custom templates, and settings into a single backup file before device migration.",
            "External Storage Folder: Direct your work storage to an external folder or SD card. By pointing this to a folder monitored by third-party sync apps (like FolderSync or Nextcloud), projects sync continuously to cloud storage.",
            "p5.js Web Editor Import: Import public sketches from any p5.js Web Editor username without requiring credentials."
        )
    )
)

@Composable
internal fun UserGuideScreen(language: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val sections = localizedUserGuide(language)
    val title = when (language) { "ja" -> "使い方ガイド"; "zh" -> "使用指南"; else -> "User Guide" }
    val subtitle = when (language) {
        "ja" -> "Edit:RiN を使いこなすための機能ガイド"
        "zh" -> "Edit:RiN 创意编程完整功能指南"
        else -> "Comprehensive guide to mastering Edit:RiN"
    }
    val copyText = when (language) { "ja" -> "コードをコピー"; "zh" -> "复制代码"; else -> "Copy Code" }
    val copiedText = when (language) { "ja" -> "クリップボードにコピーしました"; "zh" -> "已复制到剪贴板"; else -> "Copied to clipboard" }
    val closeText = when (language) { "ja" -> "閉じる"; "zh" -> "关闭"; else -> "Close" }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.background,
        contentColor = colors.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }

                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = closeText,
                        tint = colors.onSurface
                    )
                }
            }

            // Category Jump Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(sections, key = { _, s -> s.title }, contentType = { _, _ -> "guide_chip" }) { index, section ->
                    SuggestionChip(
                        onClick = {
                            scope.launch { state.animateScrollToItem(index) }
                        },
                        label = {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        icon = if (section.iconRes != 0) {
                            {
                                Icon(
                                    painter = painterResource(section.iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = colors.primary
                                )
                            }
                        } else null,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            HorizontalDivider(
                color = colors.outlineVariant.copy(alpha = 0.35f),
                modifier = Modifier.padding(top = 4.dp)
            )

            // Section Cards List
            LazyColumn(
                state = state,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                itemsIndexed(sections, key = { _, s -> s.title }, contentType = { _, _ -> "guide_section" }) { index, section ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = colors.surfaceContainerLow
                        ),
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Card Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (section.iconRes != 0) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = colors.primaryContainer,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    painter = painterResource(section.iconRes),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = colors.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = section.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.onSurface
                                        )
                                    }
                                }

                                if (section.tag.isNotEmpty()) {
                                    val isNewTag = section.tag == "New" || section.tag == "新機能" || section.tag == "新特性"
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isNewTag) colors.tertiaryContainer else colors.secondaryContainer
                                    ) {
                                        Text(
                                            text = section.tag,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isNewTag) colors.onTertiaryContainer else colors.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            // Summary Text
                            Text(
                                text = section.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                lineHeight = 20.sp
                            )

                            // Code Snippet Box (if provided)
                            section.codeSnippet?.let { snippet ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = colors.surfaceContainerLowest,
                                    border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.3f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = snippet,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                            color = colors.onSurface
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("code", snippet))
                                                Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.align(Alignment.End),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_snippet),
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(copyText, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            // Steps Checklist
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                section.steps.forEachIndexed { stepIndex, step ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = colors.primary.copy(alpha = 0.12f),
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${stepIndex + 1}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.primary
                                                )
                                            }
                                        }
                                        Text(
                                            text = step,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurface,
                                            lineHeight = 18.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
