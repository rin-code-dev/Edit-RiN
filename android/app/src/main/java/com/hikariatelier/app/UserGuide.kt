package com.hikariatelier.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
        summary = "Run an example sketch and see how the code drives what appears on the canvas.",
        iconRes = R.drawable.ic_play,
        tag = "Start",
        steps = listOf(
            "Open Palette under Samples in the work list and tap the canvas to cycle through color palettes.",
            "Tap the work title at the top of the workspace to open the list and switch between Samples and My works.",
            "Original samples are read-only so they cannot be overwritten by mistake, while still allowing text copying, block folding, and parameter adjustments.",
            "Choose Copy to edit and give your copy a name to freely modify the code in My works."
        )
    ),
    GuideSection(
        title = "Create your work",
        summary = "Start a new sketch from a template and pick the canvas aspect ratio.",
        iconRes = R.drawable.ic_snippet,
        tag = "Create",
        steps = listOf(
            "Tap the plus button in the work list, enter a name, and choose a starting template.",
            "Choose from 2D, Animation, Touch, and Parameters, as well as WebGL, Shaders, Physics, and WebGPU templates.",
            "Pick a preview aspect ratio that scales with your screen, or select Custom for fixed pixel dimensions.",
            "Use Save as template in the work menu to reuse a sketch along with its files, assets, and runtime settings."
        )
    ),
    GuideSection(
        title = "Edit and run",
        summary = "Navigate and edit code with folding, number scrubbing, search, and error jumps.",
        iconRes = R.drawable.ic_code,
        tag = "Editor",
        steps = listOf(
            "Use Undo and Redo along with the bar above the keyboard for cursor movement, symbols, and snippets.",
            "Tap the triangle next to line numbers to fold code blocks, and toggle line wrapping in Editor settings.",
            "Search or replace text across the sketch, format code, browse the p5.js reference, and tap console errors to jump to the matching line.",
            "Switch sketch.js between Code and Blocks in the top bar while keeping JavaScript code as the single source of truth."
        )
    ),
    GuideSection(
        title = "Adjust live parameters",
        summary = "Control sketches while they run using sliders, toggles, and color pickers.",
        iconRes = R.drawable.ic_tune,
        tag = "Parameters",
        codeSnippet = "// @rin number diameter \"Diameter\" 20 240 120 10\n// @rin color ink \"Ink\" #D67856\n\nfunction setup() {\n  createCanvas(600, 600);\n}\n\nfunction draw() {\n  background('#F2EFE7');\n  noStroke();\n  fill(rinParams.ink);\n  circle(width / 2, height / 2, rinParams.diameter);\n}",
        steps = listOf(
            "Tap Parameters in the preview bar to open controls declared in your code.",
            "Write // @rin, // @slider, or // @color comments in the code, or use Add parameter to insert them.",
            "Drag sliders, tap step buttons, or pick colors to update the canvas immediately; // @slider lines also update numbers in your code.",
            "Parameter values are saved per work and can be reset individually or all together."
        )
    ),
    GuideSection(
        title = "Build with blocks",
        summary = "Arrange and edit JavaScript visually with blocks while keeping your code intact.",
        iconRes = R.drawable.ic_folder_code,
        tag = "Blocks",
        codeSnippet = "function setup() {\n  createCanvas(400, 400);\n}\n\nfunction draw() {\n  background(240);\n  circle(mouseX, mouseY, 80);\n}",
        steps = listOf(
            "Switch sketch.js to Blocks in the top bar to work on a wide block canvas alongside the floating preview.",
            "Tap Add block on the bottom dock, check where it will be inserted, and tap a block card to add it.",
            "Tap color swatches or value pills inside blocks to edit them directly, or tap a block to duplicate, remove, or view its code.",
            "Jump between functions using the list button, or switch between floating and full-screen preview."
        )
    ),
    GuideSection(
        title = "Files, assets, and libraries",
        summary = "Manage multi-file projects, images, audio, fonts, and p5.js libraries.",
        iconRes = R.drawable.ic_assets,
        tag = "Project",
        steps = listOf(
            "Create additional JS, HTML, and CSS files under Work settings → Project files and switch between them using the file tabs.",
            "Import images, audio, fonts, or data files, then insert loading code with assets/ paths from the asset details.",
            "When renaming an asset, choose which matching literal paths to update; limits are 50 MiB per file and 200 MiB per work.",
            "Select p5.js 2.3.4 or 1.11.5, enable p5.sound, or add external libraries in Work settings; camera and microphone work once permitted."
        )
    ),
    GuideSection(
        title = "Save and recover your work",
        summary = "Protect edits with save history, named snapshots, and draft recovery.",
        iconRes = R.drawable.ic_history,
        tag = "Save",
        steps = listOf(
            "Tap Save to write all files in the work, and tap History next to it to open both auto-save revisions and manual snapshots.",
            "Inspect past code in History, check whether it matches the current editor code, and create named snapshots with optional notes.",
            "Turn on Auto-save when leaving and Draft recovery in Settings to preserve edits when switching apps or restarting.",
            "If saving fails, your active editor code stays intact and retry or recovery options remain available."
        )
    ),
    GuideSection(
        title = "Export images and recordings",
        summary = "Save high-resolution PNG images or MP4 and GIF recordings of your canvas.",
        iconRes = R.drawable.ic_camera,
        tag = "Output",
        steps = listOf(
            "Tap the camera button to redraw the canvas and export a PNG at 1x, 2x, or 4x scale (up to 16 megapixels).",
            "Record animations as MP4 or GIF; bitrate and countdown duration can be changed in Settings.",
            "Open saved images or recordings from the completion dialog, or share them directly to other apps.",
            "If writing a recording fails, retry or pick another save location—pending captures remain safe until discarded."
        )
    ),
    GuideSection(
        title = "Organize and back up",
        summary = "Organize works with folders and tags, and create full ZIP backups.",
        iconRes = R.drawable.ic_save,
        tag = "Manage",
        steps = listOf(
            "Group works into folders, filter by tags, and search in the picker; deleting a folder moves its works to Unfiled.",
            "Select multiple works to move, tag, or export together; single-work ZIP archives bundle code, assets, and settings.",
            "Export and restore all works, custom templates, and editor preferences together as a ZIP in Storage settings.",
            "Point your work folder to an external directory if you sync files with another app (nothing is uploaded automatically)."
        )
    )
) }

private const val GUIDE_LIST = -2
private const val GUIDE_PRACTICE = -1
private const val GUIDE_REFERENCE_BASE = 1000
private const val GUIDE_CHEATSHEET = 2000

private const val GUIDE_TAB_ALL = 0
private const val GUIDE_TAB_PLAYGROUND = 1
private const val GUIDE_TAB_REFERENCE = 2

private fun GuideReferenceEntry.matches(query: String): Boolean =
    query.isBlank() || (listOf(title, summary, group) + paragraphs + notes).any { it.contains(query.trim(), ignoreCase = true) }

private fun GuideSection.matches(query: String): Boolean =
    query.isBlank() || (listOf(title, summary, tag) + steps).any { it.contains(query.trim(), ignoreCase = true) }

@Composable
internal fun UserGuideScreen(
    language: String,
    onClose: () -> Unit,
    onStartPracticeTour: () -> Unit = {},
    onTryCodeInEditor: ((title: String, code: String) -> Unit)? = null
) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val sections = localizedUserGuide(language)
    val configuration = LocalConfiguration.current
    // Same rule as Settings: a sideways phone or a large font may not have room for two panes.
    val landscape = configuration.screenWidthDp >= 640 &&
        configuration.screenWidthDp / configuration.fontScale >= 560
    var activeTab by rememberSaveable { mutableIntStateOf(GUIDE_TAB_ALL) }
    var selected by rememberSaveable { mutableIntStateOf(GUIDE_LIST) }
    var query by rememberSaveable { mutableStateOf("") }
    var refGroupFilter by rememberSaveable { mutableStateOf("") }

    val current = when {
        activeTab == GUIDE_TAB_PLAYGROUND -> GUIDE_PRACTICE
        landscape && selected == GUIDE_LIST -> if (activeTab == GUIDE_TAB_REFERENCE) GUIDE_CHEATSHEET else GUIDE_PRACTICE
        else -> selected
    }
    val contentState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun open(target: Int) {
        selected = target
        if (target == GUIDE_PRACTICE && activeTab == GUIDE_TAB_REFERENCE) {
            activeTab = GUIDE_TAB_PLAYGROUND
        } else if (target >= 0 && activeTab == GUIDE_TAB_PLAYGROUND) {
            activeTab = GUIDE_TAB_ALL
        }
        scope.launch { contentState.scrollToItem(0) }
    }
    val showBack = !landscape && (current != GUIDE_LIST || activeTab != GUIDE_TAB_ALL)
    // The guide opens in a dialog window, which has no enter animation of its own.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(180)) }

    Surface(
        Modifier.fillMaxSize().graphicsLayer { alpha = enter.value },
        color = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.fillMaxSize().padding(WindowInsets.safeDrawing.asPaddingValues())) {
            val compactHeight = configuration.screenHeightDp < 480
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = if (compactHeight) 0.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBack) {
                    IconButton(
                        onClick = {
                            if (activeTab == GUIDE_TAB_PLAYGROUND) activeTab = GUIDE_TAB_ALL
                            selected = GUIDE_LIST
                        },
                        modifier = Modifier.then(if (compactHeight) Modifier.size(40.dp) else Modifier)
                    ) {
                        Icon(painterResource(R.drawable.ic_back), t("目次へ戻る", "Back to contents", "返回目录"))
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }
                if (compactHeight) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(t("使い方ガイド", "User Guide", "使用指南"), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            t("操作の練習と機能別の説明", "Practice controls and browse topics", "练习操作与查看功能说明"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Column(Modifier.weight(1f)) {
                        Text(t("使い方ガイド", "User Guide", "使用指南"), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            t("操作の練習と機能別の説明", "Practice controls and browse topics", "练习操作与查看功能说明"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClose, Modifier.then(if (compactHeight) Modifier.size(40.dp) else Modifier)) {
                    Icon(painterResource(R.drawable.ic_close), t("閉じる", "Close", "关闭"))
                }
            }

            // 3-tab selector bar
            val tabs = listOf(
                GUIDE_TAB_ALL to t("目次", "Contents", "目录"),
                GUIDE_TAB_PLAYGROUND to t("操作の練習", "Playground", "操作练习"),
                GUIDE_TAB_REFERENCE to t("記法と仕様", "Reference", "语法与说明")
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEach { (tabId, label) ->
                    val isSelected = activeTab == tabId
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            activeTab = tabId
                            selected = when (tabId) {
                                GUIDE_TAB_PLAYGROUND -> GUIDE_PRACTICE
                                GUIDE_TAB_REFERENCE -> if (landscape) GUIDE_CHEATSHEET else GUIDE_LIST
                                else -> GUIDE_LIST
                            }
                        },
                        label = { Text(label, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) }
                    )
                }
            }

            HorizontalDivider()

            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(310.dp).fillMaxHeight()) {
                        GuideContents(
                            language = language,
                            sections = sections,
                            current = current,
                            activeTab = activeTab,
                            query = query,
                            refGroupFilter = refGroupFilter,
                            onQueryChange = { query = it },
                            onRefGroupChange = { refGroupFilter = it },
                            onOpen = ::open,
                            onStartPracticeTour = onStartPracticeTour
                        )
                    }
                    VerticalDivider()
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        GuideBody(
                            language = language,
                            sections = sections,
                            current = current,
                            state = contentState,
                            onOpen = ::open,
                            maxContentWidth = 720,
                            onTryCodeInEditor = onTryCodeInEditor
                        )
                    }
                }
            } else {
                // Contents <-> detail view cross-fade
                val showListPane = current == GUIDE_LIST
                Crossfade(targetState = showListPane, animationSpec = tween(160), label = "guidePane") { showList ->
                    if (showList) {
                        GuideContents(
                            language = language,
                            sections = sections,
                            current = current,
                            activeTab = activeTab,
                            query = query,
                            refGroupFilter = refGroupFilter,
                            onQueryChange = { query = it },
                            onRefGroupChange = { refGroupFilter = it },
                            onOpen = ::open,
                            onStartPracticeTour = onStartPracticeTour
                        )
                    } else {
                        GuideBody(
                            language = language,
                            sections = sections,
                            current = current,
                            state = contentState,
                            onOpen = ::open,
                            maxContentWidth = 720,
                            onTryCodeInEditor = onTryCodeInEditor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideContents(
    language: String,
    sections: List<GuideSection>,
    current: Int,
    activeTab: Int,
    query: String,
    refGroupFilter: String,
    onQueryChange: (String) -> Unit,
    onRefGroupChange: (String) -> Unit,
    onOpen: (Int) -> Unit,
    onStartPracticeTour: () -> Unit = {}
) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val allRefs = guideReferenceEntries(language)
    val refGroups = remember(allRefs) { allRefs.map { it.group }.distinct() }
    val matches = sections.withIndex().filter { it.value.matches(query) }
    val referenceMatches = allRefs.withIndex().filter { (_, entry) ->
        (activeTab != GUIDE_TAB_REFERENCE || refGroupFilter.isBlank() || entry.group == refGroupFilter) &&
            entry.matches(query)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("guide-chapters"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Keep index ordering in default tab for test/user predictability:
        // 0: Search field, 1: Practice entry, 2: Interactive tour entry, 3: Section heading, 4+: Chapters
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(t("ガイドを検索", "Search the guide", "搜索指南")) },
                leadingIcon = {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
        }

        if (activeTab == GUIDE_TAB_REFERENCE) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = refGroupFilter.isBlank(),
                        onClick = { onRefGroupChange("") },
                        label = { Text(t("すべて", "All", "全部")) }
                    )
                    refGroups.forEach { group ->
                        FilterChip(
                            selected = refGroupFilter == group,
                            onClick = { onRefGroupChange(if (refGroupFilter == group) "" else group) },
                            label = { Text(group) }
                        )
                    }
                }
            }
            item {
                GuideContentsEntry(
                    title = t("コメント記法の一覧", "Comment directives", "注释语法一览"),
                    summary = t("// ? や // @slider・// @rin の構文一覧", "Syntax summary for // ?, // @slider, and // @rin", "// ?、// @slider 与 // @rin 语法一览"),
                    selected = current == GUIDE_CHEATSHEET,
                    iconRes = R.drawable.ic_tune
                ) { onOpen(GUIDE_CHEATSHEET) }
            }
            items(referenceMatches, key = { GUIDE_REFERENCE_BASE + it.index }) { (index, entry) ->
                GuideContentsEntry(
                    title = entry.title,
                    summary = entry.summary,
                    selected = current == GUIDE_REFERENCE_BASE + index,
                    badge = entry.group
                ) { onOpen(GUIDE_REFERENCE_BASE + index) }
            }
        } else {
            val qLower = query.trim().lowercase()
            val practiceTourMatches = query.isNotBlank() && listOf(
                "?", "@slider", "@rin", "@int", "@color", "@toggle", "@choice",
                "tour", "golf", "ツアー", "実践", "体験", "練習", "practice",
                "つぶやき", "minify", "ウォッチ", "watch"
            ).any { it in qLower || qLower in it }

            if (query.isBlank()) {
                item {
                    GuideContentsEntry(
                        title = t("操作を試す", "Practice", "练习"),
                        summary = t("練習用の画面でプレビュー操作やパラメータ、ブロックを試せます", "Try preview controls, parameters, and blocks in a practice view", "在练习画面中试用预览操作、参数与积木"),
                        selected = current == GUIDE_PRACTICE,
                        iconRes = R.drawable.ic_play
                    ) { onOpen(GUIDE_PRACTICE) }
                }
                item {
                    GuideContentsEntry(
                        title = t("初回ツアーをもう一度見る", "Replay the tour", "重新查看初始向导"),
                        summary = t("基本操作やブロックモード、コメント記法の使い方を順番に試せます", "Walk through basic controls, Block mode, and comment syntax", "按顺序试用基础操作、积木模式与注释语法"),
                        selected = false,
                        iconRes = R.drawable.ic_reload
                    ) { onStartPracticeTour() }
                }
                item { Text(t("機能別の使い方", "Explore by task", "按功能查看"), style = MaterialTheme.typography.titleMedium) }
            } else if (practiceTourMatches) {
                item {
                    GuideContentsEntry(
                        title = t("初回ツアーをもう一度見る", "Replay the tour", "重新查看初始向导"),
                        summary = t("基本操作やブロックモード、コメント記法の使い方を順番に試せます", "Walk through basic controls, Block mode, and comment syntax", "按顺序试用基础操作、积木模式与注释语法"),
                        selected = false,
                        iconRes = R.drawable.ic_reload
                    ) { onStartPracticeTour() }
                }
            }
            items(matches, key = { it.index }) { (index, section) ->
                GuideContentsEntry(
                    title = "${index + 1}. ${section.title}",
                    summary = section.summary,
                    selected = current == index,
                    iconRes = section.iconRes,
                    badge = section.tag.ifBlank { null }
                ) { onOpen(index) }
            }
            if (referenceMatches.isNotEmpty()) {
                item { Text(t("リファレンス", "Reference", "参考"), style = MaterialTheme.typography.titleMedium) }
                items(referenceMatches, key = { GUIDE_REFERENCE_BASE + it.index }) { (index, entry) ->
                    GuideContentsEntry(
                        title = entry.title,
                        summary = entry.summary,
                        selected = current == GUIDE_REFERENCE_BASE + index,
                        badge = entry.group
                    ) { onOpen(GUIDE_REFERENCE_BASE + index) }
                }
            }
            if (matches.isEmpty() && referenceMatches.isEmpty() && !practiceTourMatches) {
                item { Text(t("一致する項目がありません", "No matching topics", "没有匹配的项目"), style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun GuideContentsEntry(
    title: String,
    summary: String,
    selected: Boolean,
    iconRes: Int = 0,
    badge: String? = null,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        onClick = onClick,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) colors.primary else colors.outlineVariant
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (iconRes != 0) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) colors.primaryContainer else colors.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(iconRes),
                        contentDescription = null,
                        tint = if (selected) colors.onPrimaryContainer else colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                    if (!badge.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.secondaryContainer,
                            contentColor = colors.onSecondaryContainer
                        ) {
                            Text(
                                badge,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                Text(summary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
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
    maxContentWidth: Int,
    onTryCodeInEditor: ((title: String, code: String) -> Unit)? = null
) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    val headingStyle = if (compactHeight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall

    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(if (compactHeight) 12.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (compactHeight) 12.dp else 16.dp)
    ) {
        if (current == GUIDE_PRACTICE) {
            item { Box(Modifier.widthIn(max = maxContentWidth.dp)) { GuideQuickStart(language, onTryCodeInEditor) } }
        } else if (current == GUIDE_CHEATSHEET) {
            item(key = "guide-cheatsheet") {
                Box(Modifier.widthIn(max = maxContentWidth.dp)) {
                    GuideCommentCheatSheet(language, onTryCodeInEditor)
                }
            }
        } else if (current >= GUIDE_REFERENCE_BASE) {
            guideReferenceEntries(language).getOrNull(current - GUIDE_REFERENCE_BASE)?.let { entry ->
                item(key = "reference-$current") {
                    GuideReferenceContent(language, entry, Modifier.widthIn(max = maxContentWidth.dp), onTryCodeInEditor)
                }
            }
        } else if (current >= 0 && current <= sections.lastIndex) {
            val section = sections[current]
            item(key = "chapter-$current") {
                Column(Modifier.widthIn(max = maxContentWidth.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (section.tag.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.primaryContainer,
                            contentColor = colors.onPrimaryContainer
                        ) {
                            Text(
                                section.tag,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Text(section.title, style = headingStyle, fontWeight = FontWeight.Bold)
                    Text(section.summary, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)

                    Text(
                        t("図解で操作を試せます（自分の作品には影響しません）", "Interactive illustration (practice only — does not alter your works)", "互动图解（仅供练习，不影响您的作品）"),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary
                    )
                    GuideChapterVisual(current, section.iconRes, language)

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        section.steps.forEachIndexed { index, step ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colors.surfaceContainerLow,
                                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(colors.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "${index + 1}",
                                            color = colors.onPrimaryContainer,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        step,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    section.codeSnippet?.let { snippet ->
                        Text(t("試せるコード", "Code to try", "示例代码"), style = MaterialTheme.typography.titleMedium)
                        Surface(shape = RoundedCornerShape(12.dp), color = colors.surfaceContainerLow) {
                            Text(snippet, Modifier.padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("code", snippet))
                                    Toast.makeText(context, t("コードをコピーしました", "Code copied", "已复制代码"), Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) { Text(t("コードをコピー", "Copy code", "复制代码"), maxLines = 1) }
                            if (onTryCodeInEditor != null && !snippet.trimStart().startsWith("{")) {
                                FilledTonalButton(
                                    onClick = { onTryCodeInEditor(section.title, snippet) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(t("エディターで試す", "Try in Editor", "在编辑器中尝试"), maxLines = 1)
                                }
                            }
                        }
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
private fun GuideReferenceContent(
    language: String,
    entry: GuideReferenceEntry,
    modifier: Modifier,
    onTryCodeInEditor: ((title: String, code: String) -> Unit)? = null
) {
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("code", snippet))
                        Toast.makeText(context, t("コードをコピーしました", "Code copied", "已复制代码"), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) { Text(t("コードをコピー", "Copy code", "复制代码"), maxLines = 1) }
                if (onTryCodeInEditor != null && !snippet.trimStart().startsWith("{")) {
                    FilledTonalButton(
                        onClick = { onTryCodeInEditor(entry.title, snippet) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(t("エディターで試す", "Try in Editor", "在编辑器中尝试"), maxLines = 1)
                    }
                }
            }
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
