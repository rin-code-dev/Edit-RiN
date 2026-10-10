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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
            "Open Palette from Samples and tap its canvas to change the palette. Try the practice controls on the first page of this guide before editing your own work.",
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
            "Save and History share one control. Save stores all work files; History opens Snapshots and Revert to saved. Auto-save when leaving the app is available in Settings.",
            "In History, choose Snapshots to keep earlier versions, add names or notes, inspect differences, or restore a version. Revert to saved returns to your last save; check the confirmation before discarding unsaved edits.",
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
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val context = LocalContext.current
    val sections = localizedUserGuide(language)
    var chapter by rememberSaveable { mutableIntStateOf(-1) }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(WindowInsets.safeDrawing.asPaddingValues())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("使い方ガイド", "User Guide", "使用指南"), style = MaterialTheme.typography.headlineSmall)
                    Text(t("操作を試して、使い方を確かめる", "Practice controls and explore each feature", "试用操作，了解各项功能"), style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClose) { Icon(painterResource(R.drawable.ic_close), t("閉じる", "Close", "关闭")) }
            }
            LazyRow(modifier = Modifier.testTag("guide-chapters"), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(chapter == -1, { chapter = -1; scope.launch { state.scrollToItem(0) } },
                        label = { Text(t("操作を試す", "Practice", "练习")) })
                }
                itemsIndexed(sections) { index, section ->
                    FilterChip(chapter == index, { chapter = index; scope.launch { state.scrollToItem(0) } },
                        label = { Text("${index + 1}. ${section.title}") })
                }
            }
            HorizontalDivider()
            LazyColumn(state = state, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (chapter == -1) {
                    item { GuideQuickStart(language) }
                    item { Text(t("機能別の使い方", "Explore by task", "按功能查看"), style = MaterialTheme.typography.titleMedium) }
                    itemsIndexed(sections) { index, section ->
                        OutlinedCard(onClick = { chapter = index; scope.launch { state.scrollToItem(0) } }) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${index + 1}. ${section.title}", style = MaterialTheme.typography.titleMedium)
                                Text(section.summary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                } else {
                    val section = sections[chapter]
                    item(key = "chapter-$chapter") {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(section.title, style = MaterialTheme.typography.headlineSmall)
                            Text(section.summary, style = MaterialTheme.typography.bodyLarge)
                            if (guideHighlightedControls(section.iconRes).isNotEmpty()) {
                                Text(t("ここで操作を試せます。変更は練習用の画面だけに反映されます。", "Try the controls here. Changes only affect this practice screen.", "在这里试用操作，修改仅影响练习画面。"), style = MaterialTheme.typography.bodySmall)
                                GuideControlIllustration(section.iconRes, language)
                            }
                            section.steps.forEachIndexed { index, step ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("${index + 1}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                                    Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                }
                            }
                            section.codeSnippet?.let { snippet ->
                                Text(t("試せるコード", "Code to try", "示例代码"), style = MaterialTheme.typography.titleMedium)
                                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Text(snippet, Modifier.padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                }
                                OutlinedButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("code", snippet))
                                    Toast.makeText(context, t("コードをコピーしました", "Code copied", "已复制代码"), Toast.LENGTH_SHORT).show()
                                }) { Text(t("コードをコピー", "Copy code", "复制代码")) }
                            }
                            FilledTonalButton(onClick = { chapter = if (chapter < sections.lastIndex) chapter + 1 else -1; scope.launch { state.scrollToItem(0) } }) {
                                Text(if (chapter < sections.lastIndex) t("次の項目へ", "Next topic", "下一项") else t("操作の練習に戻る", "Back to practice", "返回练习"))
                            }
                        }
                    }
                }
            }
        }
    }
}
