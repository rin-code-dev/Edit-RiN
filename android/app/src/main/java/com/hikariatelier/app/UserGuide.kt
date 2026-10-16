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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalConfiguration
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
internal val userGuideSections by lazy { listOf(
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
) }

private const val GUIDE_LIST = -2
private const val GUIDE_PRACTICE = -1
private const val GUIDE_REFERENCE_BASE = 1000

private fun GuideReferenceEntry.matches(query: String): Boolean =
    query.isBlank() || (listOf(title, summary, group) + paragraphs + notes).any { it.contains(query.trim(), ignoreCase = true) }

private fun GuideSection.matches(query: String): Boolean =
    query.isBlank() || (listOf(title, summary, tag) + steps).any { it.contains(query.trim(), ignoreCase = true) }

@Composable
internal fun UserGuideScreen(
    language: String,
    onClose: () -> Unit,
    onStartPracticeTour: () -> Unit = {}
) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val sections = localizedUserGuide(language)
    val configuration = LocalConfiguration.current
    // Same rule as Settings: a sideways phone or a large font may not have room for two panes.
    val landscape = configuration.screenWidthDp >= 640 &&
        configuration.screenWidthDp / configuration.fontScale >= 560
    var selected by rememberSaveable { mutableIntStateOf(GUIDE_LIST) }
    var query by rememberSaveable { mutableStateOf("") }
    val current = if (landscape && selected == GUIDE_LIST) GUIDE_PRACTICE else selected
    val contentState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun open(target: Int) {
        selected = target
        scope.launch { contentState.scrollToItem(0) }
    }
    val showBack = !landscape && current != GUIDE_LIST
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(WindowInsets.safeDrawing.asPaddingValues())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showBack) {
                    IconButton({ selected = GUIDE_LIST }) { Icon(painterResource(R.drawable.ic_back), t("目次へ戻る", "Back to contents", "返回目录")) }
                } else {
                    Spacer(Modifier.width(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(t("使い方ガイド", "User Guide", "使用指南"), style = MaterialTheme.typography.headlineSmall)
                    Text(t("操作を試して、使い方を確かめる", "Practice controls and explore each feature", "试用操作，了解各项功能"), style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClose) { Icon(painterResource(R.drawable.ic_close), t("閉じる", "Close", "关闭")) }
            }
            HorizontalDivider()
            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(300.dp).fillMaxHeight()) {
                        GuideContents(language, sections, current, query, { query = it }, ::open, onStartPracticeTour)
                    }
                    VerticalDivider()
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        GuideBody(language, sections, current, contentState, ::open, maxContentWidth = 720)
                    }
                }
            } else if (current == GUIDE_LIST) {
                GuideContents(language, sections, current, query, { query = it }, ::open, onStartPracticeTour)
            } else {
                GuideBody(language, sections, current, contentState, ::open, maxContentWidth = 720)
            }
        }
    }
}

@Composable
private fun GuideContents(
    language: String,
    sections: List<GuideSection>,
    current: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: (Int) -> Unit,
    onStartPracticeTour: () -> Unit = {}
) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val matches = sections.withIndex().filter { it.value.matches(query) }
    val referenceMatches = guideReferenceEntries(language).withIndex().filter { it.value.matches(query) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("guide-chapters"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(t("ガイドを検索", "Search the guide", "搜索指南")) }
            )
        }
        val qLower = query.trim().lowercase()
        val practiceTourMatches = query.isNotBlank() && listOf(
            "?", "@slider", "@rin", "@int", "@color", "@toggle", "@choice",
            "tour", "golf", "ツアー", "実践", "体験", "練習", "practice",
            "つぶやき", "minify", "ウォッチ", "watch"
        ).any { it in qLower || qLower in it }

        if (query.isBlank()) {
            item {
                GuideContentsEntry(t("操作を試す", "Practice", "练习"),
                    t("練習用の画面でボタンの働きを確かめます", "Try the buttons on a practice screen", "在练习画面中试用按钮"),
                    current == GUIDE_PRACTICE) { onOpen(GUIDE_PRACTICE) }
            }
            item {
                GuideContentsEntry(
                    t("実際に触って体験する（エディター実践ツアー）", "Interactive Editor Practice Tour", "亲自体验（编辑器实践向导）"),
                    t("// ? や // @slider などのコメント機能を体験", "Experience comment features like // ? and // @slider", "体验 // ? 和 // @slider 等注释功能"),
                    false
                ) { onStartPracticeTour() }
            }
            item { Text(t("機能別の使い方", "Explore by task", "按功能查看"), style = MaterialTheme.typography.titleMedium) }
        } else if (practiceTourMatches) {
            item {
                GuideContentsEntry(
                    t("実際に触って体験する（エディター実践ツアー）", "Interactive Editor Practice Tour", "亲自体验（编辑器实践向导）"),
                    t("// ? や // @slider などのコメント機能を体験", "Experience comment features like // ? and // @slider", "体验 // ? 和 // @slider 等注释功能"),
                    false
                ) { onStartPracticeTour() }
            }
        }
        items(matches, key = { it.index }) { (index, section) ->
            GuideContentsEntry("${index + 1}. ${section.title}", section.summary, current == index) { onOpen(index) }
        }
        if (referenceMatches.isNotEmpty()) {
            item { Text(t("リファレンス", "Reference", "参考"), style = MaterialTheme.typography.titleMedium) }
            items(referenceMatches, key = { GUIDE_REFERENCE_BASE + it.index }) { (index, entry) ->
                GuideContentsEntry(entry.title, entry.summary, current == GUIDE_REFERENCE_BASE + index) {
                    onOpen(GUIDE_REFERENCE_BASE + index)
                }
            }
        }
        if (matches.isEmpty() && referenceMatches.isEmpty() && !practiceTourMatches) {
            item { Text(t("一致する項目がありません", "No matching topics", "没有匹配的项目"), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun GuideContentsEntry(title: String, summary: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        border = BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GuideBody(
    language: String,
    sections: List<GuideSection>,
    current: Int,
    state: androidx.compose.foundation.lazy.LazyListState,
    onOpen: (Int) -> Unit,
    maxContentWidth: Int
) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val context = LocalContext.current
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (current == GUIDE_PRACTICE) {
            item { Box(Modifier.widthIn(max = maxContentWidth.dp)) { GuideQuickStart(language) } }
        } else if (current >= GUIDE_REFERENCE_BASE) {
            guideReferenceEntries(language).getOrNull(current - GUIDE_REFERENCE_BASE)?.let { entry ->
                item(key = "reference-$current") {
                    GuideReferenceContent(language, entry, Modifier.widthIn(max = maxContentWidth.dp))
                }
            }
        } else if (current >= 0 && current <= sections.lastIndex) {
            val section = sections[current]
            item(key = "chapter-$current") {
                Column(Modifier.widthIn(max = maxContentWidth.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    FilledTonalButton(onClick = { onOpen(if (current < sections.lastIndex) current + 1 else GUIDE_PRACTICE) }) {
                        Text(if (current < sections.lastIndex) t("次の項目へ", "Next topic", "下一项") else t("操作の練習に戻る", "Back to practice", "返回练习"))
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideReferenceContent(language: String, entry: GuideReferenceEntry, modifier: Modifier) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(entry.group, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(entry.title, style = MaterialTheme.typography.headlineSmall)
        Text(entry.summary, style = MaterialTheme.typography.bodyLarge)
        entry.paragraphs.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        entry.code?.let { snippet ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Text(snippet, Modifier.padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("code", snippet))
                Toast.makeText(context, t("コードをコピーしました", "Code copied", "已复制代码"), Toast.LENGTH_SHORT).show()
            }) { Text(t("コードをコピー", "Copy code", "复制代码")) }
        }
        if (entry.notes.isNotEmpty()) {
            Text(t("注意", "Notes", "注意"), style = MaterialTheme.typography.titleMedium)
            entry.notes.forEach { note ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", color = MaterialTheme.colorScheme.primary)
                    Text(note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
