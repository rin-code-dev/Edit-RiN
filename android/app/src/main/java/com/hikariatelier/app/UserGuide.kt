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
        title = "Getting Started",
        summary = "Edit:RiN is a p5.js creative coding editor designed for mobile creativity. Each work encapsulates your code, supporting scripts, media assets, and runtime configurations.",
        iconRes = R.drawable.ic_play,
        tag = "Basics",
        steps = listOf(
            "Work Picker: Tap the title bar to open the gallery, switch between saved sketches, create new works, or search and sort your library.",
            "Live Preview: Edit sketch.js and tap the Play button. Your generative artwork compiles and executes instantly in real time.",
            "Work Menu: Access the dropdown menu next to the title to rename, duplicate, export, delete, or configure aspect ratios (1:1, 4:3, 16:9, or responsive)."
        )
    ),
    GuideSection(
        title = "Live Parameters",
        summary = "Generate interactive sliders, color pickers, and toggle switches directly from comments in your code without building manual UI.",
        iconRes = R.drawable.ic_snippet,
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
            "Syntax: Declare parameters at the top of your scripts using // @rin followed by type (number, color, or boolean).",
            "Real-time Binding: Values are automatically mapped to rinParams.<name>. Adjusting parameters updates your canvas without restarting the sketch.",
            "Controls: Use the drawer for fine-tuning with +/- step buttons, reset to defaults individually or all at once, or copy parameter templates."
        )
    ),
    GuideSection(
        title = "Share Card & QR Web Play",
        summary = "Turn your sketches into stunning 1080p shareable artwork cards with scannable QR codes that run directly in any web browser.",
        iconRes = R.drawable.ic_share_card,
        tag = "New",
        steps = listOf(
            "Generate Card: Open Share Card from the preview toolbar. The app takes a crisp screenshot and embeds a high-resolution QR code.",
            "Customization: Choose from stylish card themes (Dark, Midnight, Cyber, Light) and add your author name or social handle (by @...).",
            "Instant Web Play: Anyone scanning the QR code with their phone camera can view and interact with your sketch in their web browser—no app installation required.",
            "Source Visibility: Toggle whether recipients can inspect your sketch's source code in the web viewer or keep it private."
        )
    ),
    GuideSection(
        title = "Code Editor",
        summary = "A powerful coding environment optimized for touchscreens, offering desktop-grade editor features.",
        iconRes = R.drawable.ic_code,
        tag = "Editor",
        steps = listOf(
            "File Tabs: Effortlessly switch between multiple JavaScript modules. Manage project files via Work settings → Project files.",
            "Toolbar Actions: Quick access to Undo/Redo, Find & Replace, Go to Line, Code Formatting (Prettier-style), and Block Folding.",
            "Intelligent Completion: Context-aware suggestions for p5.js functions, mathematical constants, and your custom identifiers.",
            "Interactive Console: Inspect logs and error traces. Tap any error message to jump directly to the offending file and line.",
            "Revision History: Browse past saves side-by-side with diff inspection to safely revert unwanted modifications."
        )
    ),
    GuideSection(
        title = "Assets & Media",
        summary = "Bundle images, audio, video, custom fonts (TTF/OTF), and datasets (JSON/CSV) directly inside each work.",
        iconRes = R.drawable.ic_folder_code,
        tag = "Assets",
        codeSnippet = """let img, snd;
function preload() {
  img = loadImage('assets/texture.png');
  snd = loadSound('assets/beat.mp3');
}""",
        steps = listOf(
            "Import Files: Open Work settings → Work assets to add files from your device storage. Tap any item to inspect and preview.",
            "One-tap Code Insert: Tap 'Insert loading code' in the preview to auto-generate the exact preload code at your editor cursor.",
            "Relative Paths: Reference assets easily using 'assets/<filename>' relative URLs in standard p5.js loaders.",
            "Capacity: Supports up to 50 MB per file, and up to 100 files / 200 MB total per individual work."
        )
    ),
    GuideSection(
        title = "Runtime & Libraries",
        summary = "Fine-tune the runtime engine and library ecosystem to match your creative needs.",
        iconRes = R.drawable.ic_settings,
        tag = "Runtimes",
        steps = listOf(
            "p5.js Versioning: Select between p5.js 2.3.3 (modern web standards and WebGL improvements) and 1.11.5 (legacy compatibility).",
            "p5.sound: Enable audio playback, audio synthesis, and FFT frequency analyzers (audio begins smoothly on first user touch).",
            "p5.brush: Includes the expressive p5.brush library for realistic watercolor, ink, and pencil strokes in WebGL mode."
        )
    ),
    GuideSection(
        title = "Capture & Recording",
        summary = "Capture high-fidelity still images or fluid video loops to showcase your creations.",
        iconRes = R.drawable.ic_camera,
        tag = "Media",
        steps = listOf(
            "Screenshots: Instantly save crystal-clear PNG snapshots to your device's Pictures folder.",
            "Video Recording: Capture animated loops as MP4 (up to 60s) or animated GIF (up to 15s) directly from the preview bar.",
            "Configuration: Tailor MP4 bitrates (up to 16 Mbps) and recording countdown timers under Settings → Save & backup.",
            "Quick Sharing: Send recorded media immediately to social media, messaging apps, or cloud storage via the system share sheet."
        )
    ),
    GuideSection(
        title = "Backup & Cloud Sync",
        summary = "Keep your sketches safe with robust local backups and cloud-friendly project management.",
        iconRes = R.drawable.ic_save,
        tag = "Storage",
        steps = listOf(
            "Single Work ZIP: Export or import an individual work with all its scripts, assets, and settings bundled together.",
            "Full Backup: Package all your works and preferences into a single archive before switching phones or factory resetting.",
            "External Storage Folder: Direct your work storage to a custom folder (e.g. Nextcloud, Google Drive, or SD card) for automatic synchronization.",
            "p5.js Web Editor Import: Import public sketches from any web editor username without needing login credentials."
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
                        contentDescription = "Close",
                        tint = colors.onSurface
                    )
                }
            }

            // Category Jump Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(sections) { index, section ->
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
                itemsIndexed(sections) { index, section ->
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
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (section.tag in listOf("New", "新機能")) colors.tertiaryContainer else colors.secondaryContainer
                                    ) {
                                        Text(
                                            text = section.tag,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (section.tag in listOf("New", "新機能")) colors.onTertiaryContainer else colors.onSecondaryContainer,
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
