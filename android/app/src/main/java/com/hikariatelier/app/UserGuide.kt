package com.hikariatelier.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal data class GuideSection(
    val title: String,
    val summary: String,
    val steps: List<String>
)

/** English is the source text for the in-app user guide. */
internal val userGuideSections = listOf(
    GuideSection("Getting Started", "Edit:RiN is a p5.js creative coding editor for Android. A work contains your sketch, additional JavaScript files, assets, and settings.", listOf(
        "Use the work picker to create a new work, switch between saved works, or browse the gallery with search and sorting.",
        "Edit sketch.js and tap Run to instantly see your generative art in action.",
        "Use the work menu to rename, duplicate, export, or delete works, or open Work settings to configure assets and runtimes."
    )),
    GuideSection("Code Editor", "The editor provides modern programming tools for sketch.js and all additional JavaScript files.", listOf(
        "Switch between files using the file tabs. Add or manage files in Work settings → Project files.",
        "Take advantage of undo/redo, search and replace, go to line, auto-formatting, and code folding directly from the editor bar.",
        "Use project search to find text across all scripts. Intelligent code completion suggests p5.js APIs and your own variables and functions.",
        "Check the interactive console for logs and errors. Tap an error message to jump directly to the relevant file and line number.",
        "Review previous saves and inspect side-by-side diffs before restoring earlier revisions."
    )),
    GuideSection("Run & Preview", "Run compiles and reloads your sketch in the live canvas. Changes take effect each time you run.", listOf(
        "In portrait mode, hide the preview while typing to maximize your coding space; showing it again preserves your running canvas.",
        "Use the preview overlay controls to toggle fullscreen, rotate canvas orientation, capture screenshots, record, or tweak parameters.",
        "Set your canvas aspect ratio (1:1, 4:3, 16:9, or responsive) from the work menu. Syntax and runtime errors appear in the console."
    )),
    GuideSection("Live Parameters", "Create dynamic UI sliders and color pickers directly from comments in your code, without manual UI coding.", listOf(
        "Numbers: // @rin number speed \"Speed\" 0 3 1 0.1 (name, label, min, max, default, step).",
        "Colors: // @rin color ink \"Ink Color\" #BA90E2 (name, label, default hex color).",
        "Access values in your sketch through rinParams.speed and rinParams.ink. Adjusting sliders updates the canvas in real time.",
        "Parameters are saved per work and included in backups. Up to 16 parameters can be displayed at once."
    )),
    GuideSection("Assets & Media", "Bundle images, audio, video, custom fonts, and data files directly with each work.", listOf(
        "Go to Work settings → Work assets to add files from your device. Tap any asset to preview it.",
        "Tap 'Insert loading code' in the asset preview to automatically place the required loading code at your editor cursor.",
        "Reference assets in your sketch using relative paths, such as loadImage('assets/photo.png').",
        "Files up to 50 MB each are supported, with up to 100 assets and 200 MB total per work."
    )),
    GuideSection("Runtime & Libraries", "Choose the right environment and libraries for your creative project.", listOf(
        "In Work settings → Runtime, switch between modern p5.js 2.3.3 (recommended for new works) and p5.js 1.11.5 (for legacy sketches).",
        "Enable p5.sound for audio playback, synthesis, and FFT frequency analysis. Audio starts upon first user interaction with the preview.",
        "The bundled p5.brush library is available for p5.js 2.3.3 to create expressive watercolor and sketch effects."
    )),
    GuideSection("Capture & Share", "Record animations or save high-resolution stills of your creations.", listOf(
        "Tap Screenshot to save a still image of the current canvas.",
        "Tap Record to capture animations as MP4 (up to 60 seconds) or animated GIF (up to 15 seconds).",
        "Configure MP4 bitrate and recording countdown timers in Settings → Save & backup.",
        "Once captured, use the share dialog to send your creations directly to social media or other apps."
    )),
    GuideSection("Sharing & Import", "Easily share sketches with friends or import projects from the web.", listOf(
        "Export work ZIP: Package your sketch, assets, and settings into a single file to share with other Edit:RiN users.",
        "Import work ZIP: Add a shared work ZIP as a new project without affecting your existing works.",
        "p5.js Web Editor: Import public sketches directly by entering any public username (no password needed).",
        "Single .js files can also be imported or exported via Settings → Save & backup."
    )),
    GuideSection("Backup & History", "Safeguard your creative work with flexible local and cloud-friendly backups.", listOf(
        "Every time you save, a revision is created so you can review changes and safely restore previous versions.",
        "Create a Full Backup ZIP from Settings to back up all works and editor configurations in a single file before switching devices.",
        "Choose an external folder in Settings → Save & backup if you prefer to sync your works to cloud storage."
    )),
    GuideSection("Settings & Customization", "Personalize the editor to match your creative workflow.", listOf(
        "Appearance: Customize themes (System, Dark, Light, Terminal), custom colors, app language, and custom editor fonts (TTF/OTF).",
        "Editor: Adjust typography, font sizes, line wrapping, code ligatures, and auto-indent behavior.",
        "About: Check the current version, view open-source licenses, and check for updates silently in the background."
    ))
)


@Composable
internal fun UserGuideScreen(language: String, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val sections = localizedUserGuide(language)
    val title = when (language) { "ja" -> "使い方ガイド"; "zh" -> "使用指南"; else -> "User Guide" }
    val close = when (language) { "ja" -> "閉じる"; "zh" -> "关闭"; else -> "Close" }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Surface(Modifier.fillMaxSize(), color = colors.background, contentColor = colors.onBackground) {
        Column(Modifier.fillMaxSize().padding(WindowInsets.safeDrawing.asPaddingValues())) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Edit:RiN", style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant)
                }
                TextButton(onClick = onClose) { Text(close) }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(sections) { index, section ->
                    TextButton(onClick = { scope.launch { state.animateScrollToItem(index) } }) {
                        Text(section.title)
                    }
                }
            }
            HorizontalDivider()
            LazyColumn(state = state, contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                itemsIndexed(sections) { _, section ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(section.title, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(section.summary, style = MaterialTheme.typography.bodyMedium)
                        section.steps.forEachIndexed { stepIndex, step ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${stepIndex + 1}.", color = colors.primary,
                                    style = MaterialTheme.typography.bodyMedium)
                                Text(step, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
