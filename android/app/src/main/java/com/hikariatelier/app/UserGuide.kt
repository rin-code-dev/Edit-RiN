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
    GuideSection("Start a work", "Edit:RiN is a p5.js editor for Android. A work holds its sketch, additional JavaScript files, assets and runtime settings.", listOf(
        "Open the work selector to create a work, choose a saved work, or browse the gallery. Search and sort the gallery to find a work.",
        "Edit sketch.js and tap Run to see the result. Save your changes when you want to keep a revision.",
        "Open Work settings for files, assets and runtime options. The work menu contains rename, duplicate, export and delete actions."
    )),
    GuideSection("Edit code", "The editor uses the same editing tools for sketch.js and additional .js files.", listOf(
        "Use the file tabs to switch JavaScript files. Add or remove files from Work settings → Project files.",
        "Use Undo and Redo, search and replace, go to line, code formatting, and code folding from the editor controls.",
        "Project search finds text across the work's JavaScript files. Completion suggests p5.js names and names declared in your work; the current file is shown first.",
        "The console shows runtime messages. Tap a linked error to open its source file and line.",
        "Saved revisions can be compared with the current code before you restore one."
    )),
    GuideSection("Run and preview", "Run reloads the work in the preview. Changes to code take effect after you run it again.", listOf(
        "The portrait editor can hide the preview while you type. Show it again to inspect the work; hiding it does not discard the running WebView.",
        "Use the preview controls for fullscreen, orientation, screenshots, recording and parameters. Fullscreen keeps the work's logical canvas size.",
        "Set the preview aspect ratio from the work menu. A syntax or runtime error appears in the console."
    )),
    GuideSection("Live parameters", "Declare controls in JavaScript comments, then run the work and open Parameters from the preview controls.", listOf(
        "Number: // @rin number speed \"Speed\" 0 3 1 0.1 — name, label, minimum, maximum, initial value and step.",
        "Color: // @rin color ink \"Color\" #BA90E2 — name, label and initial six-digit color.",
        "Read values in your sketch as rinParams.speed or rinParams.ink. Controls update the running preview without editing your source code.",
        "Values are saved per work and included in work and full backups. Up to 16 distinct parameters are shown. Run again after changing a declaration."
    )),
    GuideSection("Files and assets", "Each work can contain additional JavaScript files and its own images, audio, video, fonts and data files.", listOf(
        "Open Work settings → Project files to manage .js files. Additional files run with sketch.js and use the same editor tools.",
        "Open Work settings → Work assets to add, preview, rename or remove assets. You can insert a loading statement into the editor.",
        "Use the displayed assets/ path in your sketch, for example loadImage('assets/photo.png'). Keep code paths in sync when you rename an asset.",
        "A single asset can be up to 50 MB; a work can hold up to 100 assets and 200 MB of assets."
    )),
    GuideSection("p5.js and libraries", "Runtime choices belong to each work.", listOf(
        "In Work settings → Runtime, choose p5.js 2.3.3 or 1.11.5. Existing works retain their saved choice.",
        "Enable p5.sound when a work uses audio playback, synthesis or analysis. Audio starts after the first interaction with the preview.",
        "The bundled p5.brush library is available for p5.js 2.3.3 works. Choose it in the work's library settings before running the sketch."
    )),
    GuideSection("Capture and share", "The preview controls can save a still image or record the running work.", listOf(
        "Use Screenshot for a still image. Recording offers MP4 and GIF; MP4 can record up to 60 seconds and GIF up to 15 seconds.",
        "Open Settings → Save & backup to set MP4 bitrate, a recording countdown and optional X share text.",
        "After saving a recording, use the share action to send that saved file to another app. Check the selected format before sharing."
    )),
    GuideSection("Import and export", "Choose the format that matches what you want to move.", listOf(
        "Use Settings → Save & backup to import a .js file as a work or export the current code as .js.",
        "Use the work menu to export one work as a ZIP, including its JavaScript files, assets and runtime settings. Import a work ZIP to add it as a separate work.",
        "Use Full backup to save or restore all works and editor settings in one ZIP. Keep a copy outside the device before replacing or resetting it.",
        "The p5.js Web Editor import can fetch public works by account name. It does not use your account password."
    )),
    GuideSection("Save and restore", "Local works are stored on the device. You can also choose an external work folder in Settings → Save & backup.", listOf(
        "Save the current work to record a revision. Review changes before restoring an older revision.",
        "If an external folder becomes unavailable, reconnect or reselect it before saving. Keep a full backup for device moves.",
        "A full backup includes works and editor settings. A single-work ZIP is intended for one work and its files."
    )),
    GuideSection("Appearance and editor settings", "Settings are grouped into Appearance, Editor, Save & backup, and About.", listOf(
        "Appearance controls language, theme, custom background and accent colors, and imported interface fonts.",
        "Editor controls text appearance and editing behavior, including preview visibility while editing.",
        "About shows the app version, update check, license notices and developer links. Automatic update checks are silent until a newer stable release is found."
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
