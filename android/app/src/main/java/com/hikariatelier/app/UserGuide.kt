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
        title = "Try a sample",
        summary = "Start with a finished artwork and explore how it responds.",
        iconRes = R.drawable.ic_play,
        tag = "Start",
        steps = listOf(
            "On the first launch of v2.3.0, Palette opens with the light theme and code wrapping off. Tap its canvas to change the palette.",
            "Tap the work title to open the work picker. The Samples tab contains finished examples; My works contains your own creations.",
            "Sample originals are read-only. You can select and copy text, fold code blocks, and try available live parameters.",
            "Choose Copy to edit, enter a name, and save an independent copy in My works before changing the code."
        )
    ),
    GuideSection(
        title = "Create your work",
        summary = "Templates provide a small starting point for your own code.",
        iconRes = R.drawable.ic_snippet,
        tag = "Create",
        steps = listOf(
            "Use the plus button in the work picker to create a work. Give it a name and choose a starting template.",
            "Begin with 2D, Animation, Mouse / Touch, or Parameters. Templates are starting points; samples are finished artworks to explore.",
            "Advanced templates cover WebGL, shaders, physics, and WebGPU. Their required bundled libraries are configured automatically.",
            "Save as template in the work menu stores current code, supporting files, assets, and runtime settings. Choose My templates when creating another work."
        )
    ),
    GuideSection(
        title = "Edit and run",
        summary = "Edit your own work, run changes, and inspect errors.",
        iconRes = R.drawable.ic_code,
        tag = "Editor",
        steps = listOf(
            "Tap the code to edit. Undo and Redo are available. The editing toolbar provides navigation keys, symbols, and code snippets.",
            "Tap the triangle beside a line number to fold or expand a code block. Folding changes the display without deleting any source code.",
            "With code wrapping off, long source lines scroll horizontally. Settings → Editor → Wrap code at screen width enables wrapping without changing source line breaks.",
            "Use search to find text, formatting to organize supported code, and the p5.js reference to inspect APIs. Run changes when the preview is outdated; use console errors to jump to their source."
        )
    ),
    GuideSection(
        title = "Adjust live parameters",
        summary = "Sliders and color controls can change a work while it runs.",
        iconRes = R.drawable.ic_tune,
        tag = "Parameters",
        codeSnippet = "// @rin number diameter \"Diameter\" 20 240 120 10\n// @rin color ink \"Ink\" #D67856\n\nfunction setup() {\n  createCanvas(600, 600);\n}\n\nfunction draw() {\n  background('#F2EFE7');\n  noStroke();\n  fill(rinParams.ink);\n  circle(width / 2, height / 2, rinParams.diameter);\n}",
        steps = listOf(
            "Open Parameters from the preview controls. Available controls depend on declarations in the current code.",
            "Declare a number, color, or boolean using a // @rin comment, or use Add parameter in the panel. Read the value through rinParams.<name>.",
            "Move sliders or choose colors to update the artwork. Number controls also have minus and plus buttons for small adjustments.",
            "Reset individual controls or all controls to code defaults. Parameter values belong to each work and are included when copying or backing it up."
        )
    ),
    GuideSection(
        title = "Files, assets, and libraries",
        summary = "Add resources when your work needs more than one script.",
        iconRes = R.drawable.ic_assets,
        tag = "Project",
        steps = listOf(
            "Work settings → Project files manages supporting JavaScript, HTML, and CSS files. Use editor tabs to switch between files.",
            "Import images, audio, fonts, or data under Work assets. Insert loading code from the asset inspector to use the correct path.",
            "When renaming an asset, inspect matching literal paths and choose which references to update. Asset limits are 50 MiB per file and 100 files / 200 MiB per work.",
            "Select the p5.js version and libraries in Work settings. Camera and microphone works require permissions; sound usually starts after a canvas tap. WebGPU availability depends on the device."
        )
    ),
    GuideSection(
        title = "Save and recover your work",
        summary = "Keep working copies, snapshots, and backups for different needs.",
        iconRes = R.drawable.ic_history,
        tag = "Save",
        steps = listOf(
            "Auto-save when leaving the app is available in Settings. Check the save result before leaving a work when a storage error occurs.",
            "Use snapshots to keep earlier versions. Add names or notes, inspect differences, and restore a chosen version when needed.",
            "Draft recovery can restore compatible unsaved edits or offer a recovery prompt. Confirm the work and content before applying a recovered draft.",
            "If saving fails, keep the current editor content and use the retry or recovery options shown. Avoid clearing app data while unsaved work is waiting."
        )
    ),
    GuideSection(
        title = "Export images and recordings",
        summary = "Save what you see in the canvas and share the saved media.",
        iconRes = R.drawable.ic_camera,
        tag = "Output",
        steps = listOf(
            "Use the camera button to export a PNG at 1x, 2x, or 4x. Higher scales redraw the work at higher resolution, up to 16 megapixels.",
            "Use the recording controls for MP4 or GIF. Settings provides recording countdown and MP4 bitrate options.",
            "After saving, open the result or share it through another app. These actions use the saved image or recording.",
            "If a recording cannot be saved, use Retry saving or Choose another destination. Discard removes the pending recording only when you choose it."
        )
    ),
    GuideSection(
        title = "Organize and back up",
        summary = "Keep your collection organized and make a copy before moving devices.",
        iconRes = R.drawable.ic_save,
        tag = "Manage",
        steps = listOf(
            "Use folders, tags, and search in the work picker. Deleting a folder moves its works to Unfiled instead of deleting the works.",
            "Select multiple user works for move, tag, ZIP export, or deletion. Export a single work as ZIP to include its code, files, assets, and settings.",
            "Use full backup to export your works, custom templates, and settings before changing devices or reinstalling. Check the backup file and choose it when restoring.",
            "An external storage folder changes where works are saved. Cloud synchronization requires a separate tool that synchronizes that folder; the app does not upload works to cloud storage automatically."
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
        "ja" -> "Edit:RiN の操作と機能を紹介します"
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
