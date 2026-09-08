package com.hikariatelier.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.util.AtomicFile
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.documentfile.provider.DocumentFile
import com.hikariatelier.app.ui.theme.AppTheme
import com.hikariatelier.app.ui.theme.AppThemeMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private enum class ConsoleLevel {
    LOG,
    WARNING,
    ERROR
}

private data class ConsoleEntry(
    val id: Long,
    val level: ConsoleLevel,
    val message: String,
    val line: Int? = null,
    val count: Int = 1
)

private data class DraftSnapshot(
    val workId: String,
    val code: String,
    val updatedAt: Long
)

private const val EDITOR_INDENT = "  "

private fun applyAutomaticIndent(
    previous: TextFieldValue,
    changed: TextFieldValue
): TextFieldValue {
    if (
        !previous.selection.collapsed ||
        changed.text.length != previous.text.length + 1 ||
        changed.selection.start != previous.selection.start + 1
    ) return changed

    val insertedAt = previous.selection.start
    if (
        insertedAt !in changed.text.indices ||
        changed.text[insertedAt] != '\n' ||
        changed.text.removeRange(insertedAt, insertedAt + 1) != previous.text
    ) return changed

    val before = previous.text.substring(0, insertedAt)
    val currentLine = before.substringAfterLast('\n')
    val baseIndent = currentLine.takeWhile { it == ' ' || it == '\t' }
    val opener = before.lastOrNull()
    val closer = previous.text.getOrNull(insertedAt)
    val matchingPair =
        (opener == '{' && closer == '}') ||
            (opener == '[' && closer == ']') ||
            (opener == '(' && closer == ')')
    val deeperIndent = if (opener == '{' || opener == '[' || opener == '(') {
        EDITOR_INDENT
    } else {
        ""
    }
    val insertion = if (matchingPair) {
        "\n$baseIndent$deeperIndent\n$baseIndent"
    } else {
        "\n$baseIndent$deeperIndent"
    }
    val newText = previous.text.replaceRange(insertedAt, insertedAt, insertion)
    val cursor = insertedAt + 1 + baseIndent.length + deeperIndent.length
    return TextFieldValue(newText, TextRange(cursor))
}

internal fun insertAtSelection(
    value: TextFieldValue,
    insertion: String,
    closing: String = ""
): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val selected = if (closing.isNotEmpty()) value.text.substring(start, end) else ""
    val replacement = insertion + selected + closing
    val cursor = if (start == end && closing.isNotEmpty()) {
        start + insertion.length
    } else {
        start + replacement.length
    }
    return TextFieldValue(
        value.text.replaceRange(start, end, replacement),
        TextRange(cursor)
    )
}

private fun moveCursorVertically(
    value: TextFieldValue,
    direction: Int
): TextFieldValue {
    val cursor = if (direction < 0) value.selection.min else value.selection.max
    val currentStart = if (cursor == 0) {
        0
    } else {
        value.text.lastIndexOf('\n', cursor - 1) + 1
    }
    val column = cursor - currentStart
    val targetStart: Int
    val targetEnd: Int
    if (direction < 0) {
        if (currentStart == 0) return value.copy(selection = TextRange(0))
        targetEnd = currentStart - 1
        targetStart = if (targetEnd == 0) {
            0
        } else {
            value.text.lastIndexOf('\n', targetEnd - 1) + 1
        }
    } else {
        val currentEnd = value.text.indexOf('\n', cursor).let {
            if (it < 0) value.text.length else it
        }
        if (currentEnd == value.text.length) {
            return value.copy(selection = TextRange(value.text.length))
        }
        targetStart = currentEnd + 1
        targetEnd = value.text.indexOf('\n', targetStart).let {
            if (it < 0) value.text.length else it
        }
    }
    return value.copy(
        selection = TextRange((targetStart + column).coerceAtMost(targetEnd))
    )
}

internal fun changeLineIndent(
    value: TextFieldValue,
    addIndent: Boolean
): TextFieldValue {
    if (addIndent && value.selection.collapsed) {
        return insertAtSelection(value, EDITOR_INDENT)
    }
    val firstLineStart = if (value.selection.min == 0) {
        0
    } else {
        value.text.lastIndexOf('\n', value.selection.min - 1) + 1
    }
    val starts = mutableListOf(firstLineStart)
    var newline = value.text.indexOf('\n', firstLineStart)
    while (newline >= 0 && newline + 1 < value.selection.max) {
        starts += newline + 1
        newline = value.text.indexOf('\n', newline + 1)
    }
    val builder = StringBuilder(value.text)
    var newStart = value.selection.min
    var newEnd = value.selection.max
    starts.asReversed().forEach { start ->
        if (addIndent) {
            builder.insert(start, EDITOR_INDENT)
            if (start <= newStart) newStart += EDITOR_INDENT.length
            if (start <= newEnd) newEnd += EDITOR_INDENT.length
        } else {
            val removeCount = when {
                builder.substring(start).startsWith("\t") -> 1
                builder.substring(start).startsWith(EDITOR_INDENT) -> EDITOR_INDENT.length
                builder.getOrNull(start) == ' ' -> 1
                else -> 0
            }
            if (removeCount > 0) {
                builder.delete(start, start + removeCount)
                if (start < newStart) newStart = (newStart - removeCount).coerceAtLeast(start)
                if (start < newEnd) newEnd = (newEnd - removeCount).coerceAtLeast(start)
            }
        }
    }
    return TextFieldValue(
        builder.toString(),
        TextRange(newStart, newEnd)
    )
}


private val PREVIEW_ASPECT_RATIOS = listOf("16:9", "4:3", "1:1", "9:16", "device")
private val LANDSCAPE_PREVIEW_SPLITS = listOf(0.35f, 0.5f, 0.65f)

internal fun normalizedPreviewAspectRatio(value: String?): String =
    value?.takeIf(PREVIEW_ASPECT_RATIOS::contains) ?: "1:1"

internal fun previewAspectRatioValue(value: String, deviceRatio: Float = 1f): Float = when (value) {
    "4:3" -> 4f / 3f
    "16:9" -> 16f / 9f
    "9:16" -> 9f / 16f
    "device" -> deviceRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    else -> 1f
}

private fun composeProjectSource(
    mainCode: String,
    files: Map<String, String>
): String = buildString {
    files.toSortedMap().forEach { (name, code) ->
        append(code)
        append("\n//# sourceURL=")
        append(name)
        append("\n")
    }
    append(mainCode)
    append("\n//# sourceURL=sketch.js")
}


class MainActivity : ComponentActivity() {

    private val sessionViewModel: EditorSessionViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()
    private val hideEditingPreviewKey = "hide_editing_preview"

    private val p5UsernameKey = "p5_web_editor_username"

    private val assetStorage by lazy { AssetStorage(File(filesDir, "project-assets")) }
    @Volatile private var previewAssets = PreviewAssets("initial", emptyMap())
    private var webView: WebView? = null
    private val unreadableWorkFolders get() = sessionViewModel.unreadableFolderUris

    @Volatile
    private var pendingSketchCode = ""
    @Volatile private var pendingP5Version = P5_VERSION_CURRENT
    @Volatile private var pendingP5SoundEnabled = false

    @Volatile
    private var pendingMainLineOffset = 0

    private val prefsName =
        "ugoku_atelier_prefs"

    private val preferences by lazy(LazyThreadSafetyMode.NONE) {
        getSharedPreferences(prefsName, MODE_PRIVATE)
    }

    private val autoRunKey =
        "setting_auto_run"

    private val compactPreviewKey =
        "setting_compact_preview"

    private val preserveExpandedPreviewKey =
        "setting_preserve_expanded_preview"

    private val landscapePreviewSplitKey =
        "setting_landscape_preview_split"

    private val resizeHandlesVisibleKey =
        "setting_resize_handles_visible"

    private val editorFontKey =
        "setting_editor_font"

    private val autoIndentKey =
        "setting_auto_indent"

    private val lineNumbersKey =
        "setting_line_numbers"

    private val editorAccessoryBarKey =
        "setting_editor_accessory_bar"

    private val codeCompletionKey =
        "setting_code_completion"

    private val accessoryNavigationKey =
        "setting_accessory_navigation"

    private val accessorySymbolsKey =
        "setting_accessory_symbols"

    private val compactAccessoryKeysKey =
        "setting_compact_accessory_keys"

    private val folderUriKey =
        "works_folder_uri"

    private val themeModeKey =
        "theme_mode"

    private val statusBarKey =
        "setting_status_bar"

    private val landscapeCutoutKey = "setting_landscape_use_cutout"

    private var appLanguage by mutableStateOf("system")
    private val appLanguageKey = "setting_app_language"

    private var customFontFamily by mutableStateOf<FontFamily?>(null)
    private var importedFontName by mutableStateOf("")
    private var fontImportBusy by mutableStateOf(false)
    private var fontLigatures by mutableStateOf(false)
    private val codeFontFamily: FontFamily get() = customFontFamily ?: FontFamily.Monospace
    private val fontFeatures: String get() = if (fontLigatures)
        "'liga' 1, 'clig' 1, 'calt' 1" else "'liga' 0, 'clig' 0, 'calt' 0"

    @Suppress("DEPRECATION")
    private fun uiText(source: String, vararg arguments: Any?): String {
        val text = translateUi(source,
            resolveUiLanguage(appLanguage, resources.configuration.locale.language))
        return if (arguments.isEmpty()) text
            else String.format(java.util.Locale.ROOT, text, *arguments)
    }

    private val manualRotationKey =
        "setting_manual_rotation"

    private val draftRecoveryKey =
        "setting_draft_recovery"

    private val worksFileName =
        "hikari_atelier_works.json"

    private val draftFileName =
        "editkiro_draft.json"

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        appLanguage = preferences.getString(appLanguageKey, "system")
            ?.takeIf { it in listOf("system", "ja", "en", "zh") } ?: "system"

        customFontFamily = loadImportedFont(File(filesDir, "fonts"),
            preferences.getString("custom_font_file", null))?.let { FontFamily(it) }
        importedFontName = preferences.getString("custom_font_name", "").orEmpty()
        fontLigatures = preferences.getBoolean("font_ligatures", false)

        updateViewModel.checkAtStartup()
        initializeSessionIfNeeded()
        enableEdgeToEdge()

        setContent {

            var themeMode by remember {
                mutableStateOf(
                    runCatching {
                        AppThemeMode.valueOf(
                            preferences.getString(
                                themeModeKey,
                                AppThemeMode.DARK.name
                            )
                                ?: AppThemeMode.DARK.name
                        )
                    }.getOrDefault(
                        AppThemeMode.DARK
                    )
                )
            }

            AppTheme(
                themeMode = themeMode,
                customFont = customFontFamily,
                ligatures = fontLigatures
            ) {

                UpdateDialog(updateViewModel, ::uiText) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)))
                    } catch (_: android.content.ActivityNotFoundException) {
                        Toast.makeText(this@MainActivity, uiText("ブラウザーを開けませんでした"), Toast.LENGTH_SHORT).show()
                    }
                }
                MainScreen(
                    themeMode = themeMode,

                    onThemeModeChange = {

                        themeMode = it

                        preferences.edit()
                            .putString(
                                themeModeKey,
                                it.name
                            )
                            .apply()
                    }
                )
            }
        }
    }

    private fun initializeSessionIfNeeded() {
        if (sessionViewModel.initialized) return

        val folderUri =
            preferences.getString(folderUriKey, null)?.let { value ->
                runCatching { Uri.parse(value) }.getOrNull()
            }

        val store = if (folderUri != null) loadWorkStore(folderUri) else loadLocalWorkStore()
        val initialWorks =
            store?.works?.takeIf { it.isNotEmpty() } ?: defaultWorks()
        val initialActiveId =
            store?.activeWorkId?.takeIf { id ->
                initialWorks.any { it.id == id }
            } ?: initialWorks.firstOrNull()?.id.orEmpty()

        val draft =
            if (
                preferences.getBoolean(
                    draftRecoveryKey,
                    true
                )
            ) {
                loadDraftSnapshot()
            } else {
                null
            }

        val draftText =
            draft
                ?.takeIf {
                    it.workId == initialActiveId &&
                        initialWorks.any { work ->
                            work.id == initialActiveId &&
                                work.code != it.code
                        }
                }
                ?.code

        sessionViewModel.initialize(
            works = initialWorks,
            activeWorkId = initialActiveId,
            editorTextOverride = draftText
        )
    }

    private fun loadDraftSnapshot():
            DraftSnapshot? {

        return runCatching {

            val file =
                getFileStreamPath(
                    draftFileName
                )

            if (!file.exists() && !File(file.path + ".bak").exists()) {
                return null
            }

            val root =
                JSONObject(
                    AtomicFile(file).openRead().bufferedReader().use { it.readText() }
                )

            DraftSnapshot(
                workId =
                    root.optString(
                        "workId"
                    ),
                code =
                    root.optString(
                        "code"
                    ),
                updatedAt =
                    root.optLong(
                        "updatedAt"
                    )
            )
        }.getOrNull()
    }

    private fun saveDraftSnapshot(
        workId: String,
        code: String
    ) {

        if (workId.isBlank()) {
            return
        }

        runCatching {

            val root =
                JSONObject()
                    .put(
                        "workId",
                        workId
                    )
                    .put(
                        "code",
                        code
                    )
                    .put(
                        "updatedAt",
                        System.currentTimeMillis()
                    )

            val atomicFile = AtomicFile(getFileStreamPath(draftFileName))
            val output = atomicFile.startWrite()
            try {
                output.write(root.toString().toByteArray(Charsets.UTF_8))
                atomicFile.finishWrite(output)
            } catch (error: Exception) {
                atomicFile.failWrite(output)
                throw error
            }
        }.onFailure {
            Log.w(
                "EditKIRO",
                "Draft save failed",
                it
            )
        }
    }

    private fun clearDraftSnapshot() {

        runCatching {
            deleteFile(
                draftFileName
            )
        }
    }

    private fun displayNameForUri(
        uri: Uri
    ): String? {

        return runCatching {

            contentResolver
                .query(
                    uri,
                    arrayOf(
                        OpenableColumns
                            .DISPLAY_NAME
                    ),
                    null,
                    null,
                    null
                )
                ?.use { cursor ->

                    if (
                        cursor.moveToFirst()
                    ) {

                        val index =
                            cursor.getColumnIndex(
                                OpenableColumns
                                    .DISPLAY_NAME
                            )

                        if (index >= 0) {
                            cursor.getString(
                                index
                            )
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
        }.getOrNull()
    }

    private fun safeJsFileName(
        title: String
    ): String {

        val base =
            title
                .trim()
                .ifBlank {
                    "sketch"
                }
                .replace(
                    Regex(
                        """[\\/:*?"<>|]+"""
                    ),
                    "_"
                )

        return if (
            base.endsWith(
                ".js",
                ignoreCase = true
            )
        ) {
            base
        } else {
            "$base.js"
        }
    }

    override fun onPause() {
        if (sessionViewModel.initialized && preferences.getBoolean(draftRecoveryKey, true)) {
            saveDraftSnapshot(sessionViewModel.activeWorkIdState.value, sessionViewModel.editorValueState.value.text)
        }
        if (sessionViewModel.initialized && !sessionViewModel.assetBusy && preferences.getString(folderUriKey, null) == null) {
            saveWorkStore(null, sessionViewModel.worksState.value, sessionViewModel.activeWorkIdState.value)
        }
        webView?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onDestroy() {
        webView?.let(::disposeWebView)
        webView = null
        super.onDestroy()
    }

    private fun disposeWebView(view: WebView) {
        view.stopLoading()
        view.removeJavascriptInterface("Android")
        view.loadUrl("about:blank")
        view.clearHistory()
        view.removeAllViews()
        view.destroy()
    }

    private fun savePreviewMedia(
        dataUrl: String,
        mimeType: String,
        displayName: String,
        video: Boolean
    ): Uri? = runCatching {
        val encoded = dataUrl.substringAfter(',', dataUrl)
        val bytes = Base64.decode(encoded, Base64.DEFAULT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (video) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val values = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    if (video) "Movies/EditKIRO" else "Pictures/EditKIRO"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(collection, values)
                ?: error(uiText("保存先を作成できませんでした"))
            try {
                contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                    ?: error(uiText("保存先を開けませんでした"))
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                check(contentResolver.update(uri, values, null, null) > 0)
            } catch (error: Exception) {
                // Only clean up the pending item created by this capture attempt.
                runCatching { contentResolver.delete(uri, null, null) }
                throw error
            }
            uri
        } else {
            val parent = getExternalFilesDir(
                if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            ) ?: error(uiText("保存先を利用できません"))
            val directory = File(parent, "EditKIRO").apply { mkdirs() }
            val file = File(directory, displayName)
            file.outputStream().use { it.write(bytes) }
            MediaScannerConnection.scanFile(
                this,
                arrayOf(file.absolutePath),
                arrayOf(mimeType),
                null
            )
            Uri.fromFile(file)
        }
    }.getOrNull()

    @Composable
    private fun KeepLandscapeDialogImmersive(
        enabled: Boolean
    ) {

        val localView =
            LocalView.current

        DisposableEffect(
            localView,
            enabled
        ) {

            val dialogWindow =
                if (
                    enabled &&
                    !localView.isInEditMode
                ) {
                    (localView.parent as?
                        DialogWindowProvider)
                        ?.window
                } else {
                    null
                }

            if (dialogWindow == null) {
                onDispose {}
            } else {

                fun applyImmersiveMode() {

                    WindowCompat
                        .setDecorFitsSystemWindows(
                            dialogWindow,
                            false
                        )

                    dialogWindow.navigationBarColor =
                        android.graphics.Color.TRANSPARENT

                    dialogWindow.statusBarColor =
                        android.graphics.Color.TRANSPARENT

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        dialogWindow.attributes = dialogWindow.attributes.apply {
                            layoutInDisplayCutoutMode =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                                } else {
                                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                                }
                        }
                    }

                    if (
                        Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.Q
                    ) {
                        dialogWindow
                            .isNavigationBarContrastEnforced =
                            false
                    }

                    val controller =
                        WindowCompat
                            .getInsetsController(
                                dialogWindow,
                                dialogWindow.decorView
                            )

                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat
                            .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                    controller.hide(
                        WindowInsetsCompat
                            .Type
                            .systemBars()
                    )
                }

                val focusListener =
                    android.view.ViewTreeObserver
                        .OnWindowFocusChangeListener {
                            hasFocus ->

                            if (hasFocus) {
                                applyImmersiveMode()
                            }
                        }

                val observer =
                    dialogWindow.decorView
                        .viewTreeObserver

                observer.addOnWindowFocusChangeListener(
                    focusListener
                )

                applyImmersiveMode()

                onDispose {
                    val currentObserver =
                        dialogWindow.decorView
                            .viewTreeObserver

                    if (currentObserver.isAlive) {
                        currentObserver
                            .removeOnWindowFocusChangeListener(
                                focusListener
                            )
                    }
                }

            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainScreen(
        themeMode: AppThemeMode,
        onThemeModeChange:
            (AppThemeMode) -> Unit
    ) {

        val focusManager =
            LocalFocusManager.current

        val configuration =
            LocalConfiguration.current

        val view =
            LocalView.current

        val isLandscape =
            configuration.orientation ==
                    Configuration.ORIENTATION_LANDSCAPE

        val colors =
            MaterialTheme.colorScheme

        val hapticFeedback =
            LocalHapticFeedback.current

        val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null && !fontImportBusy) {
                fontImportBusy = true
                lifecycleScope.launch {
                    try {
                        val (imported, name) = withContext(Dispatchers.IO) {
                            val name = contentResolver.query(uri,
                                arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) cursor.getString(0) else null
                            } ?: "Imported font"
                            val stream = contentResolver.openInputStream(uri) ?: error("Cannot open font")
                            importFont(File(filesDir, "fonts"), stream) to name
                        }
                        preferences.edit().putString("custom_font_file", imported.fileName)
                            .putString("custom_font_name", name).apply()
                        customFontFamily = FontFamily(imported.typeface)
                        importedFontName = name
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Toast.makeText(this@MainActivity,
                            uiText("フォントを読み込めません。20MB以下のTTF・OTF・TTCを選んでください"),
                            Toast.LENGTH_LONG).show()
                    } finally {
                        fontImportBusy = false
                    }
                }
            }
        }

        var showStatusBar by remember {
            mutableStateOf(
                preferences.getBoolean(
                    statusBarKey,
                    true
                )
            )
        }

        var landscapeUseCutout by remember {
            mutableStateOf(preferences.getBoolean(landscapeCutoutKey, false))
        }
        // Own cutout avoidance in Compose, including when system bars are hidden.
        val appInsets = WindowInsets.statusBars.union(WindowInsets.navigationBars).union(
            if (isLandscape && landscapeUseCutout) WindowInsets(0, 0, 0, 0)
            else WindowInsets.displayCutout
        )

        var manualRotation by remember {
            mutableStateOf(
                preferences.getBoolean(
                    manualRotationKey,
                    true
                )
            )
        }

        LaunchedEffect(
            view,
            manualRotation
        ) {

            if (!view.isInEditMode) {

                val activity =
                    view.context as Activity

                activity.requestedOrientation =
                    if (manualRotation) {

                        if (isLandscape) {
                            ActivityInfo
                                .SCREEN_ORIENTATION_LANDSCAPE
                        } else {
                            ActivityInfo
                                .SCREEN_ORIENTATION_PORTRAIT
                        }

                    } else {
                        ActivityInfo
                            .SCREEN_ORIENTATION_UNSPECIFIED
                    }
            }
        }

        LaunchedEffect(
            view,
            isLandscape,
            showStatusBar
        ) {

            if (!view.isInEditMode) {

                val window =
                    (view.context as Activity).window

                window.navigationBarColor =
                    android.graphics.Color.TRANSPARENT

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                        else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }

                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q
                ) {

                    window.isNavigationBarContrastEnforced =
                        false
                }

                val controller =
                    WindowCompat.getInsetsController(
                        window,
                        view
                    )

                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                if (isLandscape) {

                    controller.hide(
                        WindowInsetsCompat
                            .Type
                            .statusBars()
                    )

                    controller.hide(
                        WindowInsetsCompat
                            .Type
                            .navigationBars()
                    )

                } else {

                    if (showStatusBar) {

                        controller.show(
                            WindowInsetsCompat
                                .Type
                                .statusBars()
                        )

                    } else {

                        controller.hide(
                            WindowInsetsCompat
                                .Type
                                .statusBars()
                        )
                    }

                    controller.show(
                        WindowInsetsCompat
                            .Type
                            .navigationBars()
                    )
                }
            }
        }

        DisposableEffect(
            view,
            isLandscape
        ) {

            if (
                view.isInEditMode ||
                !isLandscape
            ) {
                onDispose {}
            } else {

                val activityWindow =
                    (view.context as Activity).window

                fun restoreLandscapeImmersive() {

                    val controller =
                        WindowCompat
                            .getInsetsController(
                                activityWindow,
                                activityWindow.decorView
                            )

                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat
                            .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                    controller.hide(
                        WindowInsetsCompat
                            .Type
                            .systemBars()
                    )
                }

                val focusListener =
                    android.view.ViewTreeObserver
                        .OnWindowFocusChangeListener {
                            hasFocus ->

                            if (hasFocus) {
                                restoreLandscapeImmersive()
                            }
                        }

                val observer =
                    activityWindow.decorView
                        .viewTreeObserver

                observer.addOnWindowFocusChangeListener(
                    focusListener
                )

                restoreLandscapeImmersive()

                onDispose {
                    val currentObserver =
                        activityWindow.decorView
                            .viewTreeObserver

                    if (currentObserver.isAlive) {
                        currentObserver
                            .removeOnWindowFocusChangeListener(
                                focusListener
                            )
                    }
                }
            }
        }

        var selectedFolderUri by remember {
            mutableStateOf(
                preferences.getString(
                    folderUriKey,
                    null
                )?.let {

                    runCatching {
                        Uri.parse(it)
                    }.getOrNull()
                }
            )
        }

        var works by sessionViewModel.worksState
        var activeWorkId by sessionViewModel.activeWorkIdState
        var editorValue by sessionViewModel.editorValueState
        val editorText =
            editorValue.text

        val activeWork =
            works.find {
                it.id == activeWorkId
            } ?: works.firstOrNull()

        var isError by remember {
            mutableStateOf(false)
        }

        var isPaused by remember {
            mutableStateOf(false)
        }

        var editorFocused by remember {
            mutableStateOf(false)
        }

        var showSettings by rememberSaveable {
            mutableStateOf(false)
        }

        var showAddDialog by rememberSaveable {
            mutableStateOf(false)
        }

        var showDeleteDialog by remember {
            mutableStateOf(false)
        }

        var showRenameDialog by remember {
            mutableStateOf(false)
        }

        var workMenuExpanded by rememberSaveable {
            mutableStateOf(false)
        }

        var workActionsMenuExpanded by rememberSaveable {
            mutableStateOf(false)
        }

        var showConsole by remember {
            mutableStateOf(false)
        }

        var showSearchDialog by remember {
            mutableStateOf(false)
        }

        val assetScope = sessionViewModel.viewModelScope
        var showAssets by rememberSaveable { mutableStateOf(false) }
        var assetBusy by sessionViewModel::assetBusy
        var assetTargetId by rememberSaveable { mutableStateOf<String?>(null) }

        var showP5Import by rememberSaveable { mutableStateOf(false) }
        var p5Username by remember {
            mutableStateOf(preferences.getString(p5UsernameKey, "").orEmpty())
        }
        var p5Sketches by remember { mutableStateOf<List<P5Sketch>>(emptyList()) }
        var p5Busy by remember { mutableStateOf(false) }
        var p5Error by remember { mutableStateOf<String?>(null) }

        var showProjectFilesDialog by remember {
            mutableStateOf(false)
        }

        var showHistoryDialog by remember {
            mutableStateOf(false)
        }

        var showAspectRatioDialog by remember {
            mutableStateOf(false)
        }

        var showRuntimeDialog by remember {
            mutableStateOf(false)
        }
        var runtimeP5Version by remember(activeWorkId) {
            mutableStateOf(normalizedP5Version(activeWork?.p5Version))
        }
        var runtimeSoundEnabled by remember(activeWorkId) {
            mutableStateOf(activeWork?.p5SoundEnabled == true)
        }

        val previewRatioSelection = normalizedPreviewAspectRatio(activeWork?.previewAspectRatio)
        val currentPreviewRatio = rememberUpdatedState(previewRatioSelection)
        val devicePreviewRatio = remember(
            configuration.screenWidthDp,
            configuration.screenHeightDp
        ) {
            configuration.screenWidthDp.toFloat() /
                configuration.screenHeightDp.coerceAtLeast(1).toFloat()
        }

        var portraitRatioDragging by remember {
            mutableStateOf(false)
        }

        var landscapePreviewFraction by remember {
            mutableFloatStateOf(
                preferences.getFloat(landscapePreviewSplitKey, 0.5f)
                    .let { saved ->
                        LANDSCAPE_PREVIEW_SPLITS.minByOrNull {
                            kotlin.math.abs(it - saved)
                        } ?: 0.5f
                    }
            )
        }

        var showLandscapeSplitLabel by remember {
            mutableStateOf(false)
        }

        val currentLandscapePreviewFraction = rememberUpdatedState(landscapePreviewFraction)

        var showExpandedPreview by remember {
            mutableStateOf(false)
        }
        var expandedCanvasSwapped by remember { mutableStateOf(false) }

        var isPreviewRecording by remember {
            mutableStateOf(false)
        }

        var previewActionsExpanded by remember {
            mutableStateOf(false)
        }

        LaunchedEffect(showExpandedPreview) {
            previewActionsExpanded = false
            if (!showExpandedPreview && expandedCanvasSwapped) {
                webView?.evaluateJavascript("window.__editKiroSetCanvasSwapped?.(false)", null)
                expandedCanvasSwapped = false
            }
        }

        var normalPreviewSize by remember {
            mutableStateOf(IntSize.Zero)
        }

        var showAuxiliaryFileEditor by remember {
            mutableStateOf(false)
        }

        var auxiliaryFileName by remember {
            mutableStateOf("")
        }

        var originalAuxiliaryFileName by remember {
            mutableStateOf<String?>(null)
        }

        var auxiliaryFileContent by remember {
            mutableStateOf("")
        }

        var searchQuery by remember {
            mutableStateOf("")
        }

        var replacementText by remember {
            mutableStateOf("")
        }

        var searchMatchCase by remember {
            mutableStateOf(false)
        }

        var goToLineText by remember {
            mutableStateOf("")
        }

        val consoleEntries =
            remember {
                mutableStateListOf<ConsoleEntry>()
            }

        var consoleSequence by remember {
            mutableLongStateOf(0L)
        }

        val editorFocusRequester =
            remember {
                FocusRequester()
            }

        val undoStack = sessionViewModel.undoStack
        val redoStack = sessionViewModel.redoStack
        var lastSavedText by sessionViewModel.lastSavedTextState

        val hasUnsavedChanges =
            editorText != lastSavedText

        fun clearEditHistory() {
            undoStack.clear()
            redoStack.clear()
        }

        fun applyEditorChange(
            nextValue: TextFieldValue
        ) {
            if (nextValue.text != editorValue.text) {
                undoStack.add(editorValue.copy(composition = null))
                while (undoStack.size > 100) {
                    undoStack.removeAt(0)
                }
                redoStack.clear()
            }
            editorValue = nextValue
        }

        fun undoEditorChange() {
            if (undoStack.isEmpty()) return
            redoStack.add(editorValue.copy(composition = null))
            editorValue = undoStack.removeAt(undoStack.lastIndex)
            editorFocusRequester.requestFocus()
        }

        fun redoEditorChange() {
            if (redoStack.isEmpty()) return
            undoStack.add(editorValue.copy(composition = null))
            editorValue = redoStack.removeAt(redoStack.lastIndex)
            editorFocusRequester.requestFocus()
        }

        LaunchedEffect(activeWorkId) {
            if (sessionViewModel.historyWorkId != activeWorkId) {
                sessionViewModel.historyWorkId = activeWorkId
                clearEditHistory()
                lastSavedText = works.find { it.id == activeWorkId }?.code.orEmpty()
            }
        }

        BackHandler(enabled = editorFocused && !showSettings) { focusManager.clearFocus(force = true) }

        BackHandler(enabled = showSettings) { showSettings = false }

        var autoRun by remember {
            mutableStateOf(
                preferences.getBoolean(
                    autoRunKey,
                    true
                )
            )
        }

        var hideEditingPreview by remember {
            mutableStateOf(preferences.getBoolean(hideEditingPreviewKey, true))
        }

        val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        var keyboardWasVisible by remember { mutableStateOf(false) }
        LaunchedEffect(keyboardVisible) {
            if (keyboardWasVisible && !keyboardVisible && hideEditingPreview && !isLandscape) {
                focusManager.clearFocus(force = true)
            }
            keyboardWasVisible = keyboardVisible
        }

        var compactPreview by remember {
            mutableStateOf(
                preferences.getBoolean(
                    compactPreviewKey,
                    true
                )
            )
        }

        var showResizeHandles by remember {
            mutableStateOf(
                preferences.getBoolean(
                    resizeHandlesVisibleKey,
                    true
                )
            )
        }

        var preserveExpandedPreview by remember {
            mutableStateOf(
                preferences.getBoolean(
                    preserveExpandedPreviewKey,
                    true
                )
            )
        }

        var editorFontSize by remember {
            mutableFloatStateOf(
                preferences.getFloat(
                    editorFontKey,
                    14f
                )
            )
        }

        var autoIndent by remember {
            mutableStateOf(
                preferences.getBoolean(
                    autoIndentKey,
                    true
                )
            )
        }

        var showLineNumbers by remember {
            mutableStateOf(
                preferences.getBoolean(
                    lineNumbersKey,
                    true
                )
            )
        }

        var showEditorAccessoryBar by remember {
            mutableStateOf(
                preferences.getBoolean(
                    editorAccessoryBarKey,
                    true
                )
            )
        }

        var codeCompletion by remember {
            mutableStateOf(preferences.getBoolean(codeCompletionKey, true))
        }

        var showAccessoryNavigation by remember {
            mutableStateOf(preferences.getBoolean(accessoryNavigationKey, true))
        }

        var showAccessorySymbols by remember {
            mutableStateOf(preferences.getBoolean(accessorySymbolsKey, true))
        }

        var compactAccessoryKeys by remember {
            mutableStateOf(preferences.getBoolean(compactAccessoryKeysKey, true))
        }

        var draftRecovery by remember {
            mutableStateOf(
                preferences.getBoolean(
                    draftRecoveryKey,
                    true
                )
            )
        }

        LaunchedEffect(
            editorText,
            activeWorkId,
            draftRecovery
        ) {

            if (
                !draftRecovery ||
                activeWorkId.isBlank()
            ) {
                return@LaunchedEffect
            }

            kotlinx.coroutines.delay(
                700L
            )

            saveDraftSnapshot(
                workId =
                    activeWorkId,
                code =
                    editorText
            )
        }

        fun appendConsole(
            level: ConsoleLevel,
            message: String,
            line: Int? = null
        ) {

            val normalized =
                message.trim()

            if (normalized.isBlank()) {
                return
            }

            val normalizedLine =
                line?.takeIf {
                    it > 0
                }

            val previous =
                consoleEntries
                    .lastOrNull()

            if (
                previous?.level == level &&
                previous.message == normalized &&
                previous.line == normalizedLine
            ) {

                val lastIndex =
                    consoleEntries
                        .lastIndex

                if (lastIndex >= 0) {
                    consoleEntries[lastIndex] =
                        previous.copy(
                            count =
                                previous.count + 1
                        )
                }

                return
            }

            consoleSequence += 1L

            consoleEntries.add(
                ConsoleEntry(
                    id =
                        consoleSequence,
                    level =
                        level,
                    message =
                        normalized,
                    line =
                        normalizedLine
                )
            )

            while (
                consoleEntries.size >
                200
            ) {
                consoleEntries.removeAt(
                    0
                )
            }
        }

        fun jumpToLine(
            line: Int
        ) {

            if (line <= 0) {
                return
            }

            var currentLine =
                1

            var offset =
                0

            while (
                currentLine < line &&
                offset < editorText.length
            ) {

                if (
                    editorText[offset] ==
                    '\n'
                ) {
                    currentLine += 1
                }

                offset += 1
            }

            editorValue =
                editorValue.copy(
                    selection =
                        TextRange(
                            offset.coerceIn(
                                0,
                                editorText.length
                            )
                        )
                )

            showConsole =
                false

            editorFocusRequester
                .requestFocus()
        }

        fun searchMatches(): List<IntRange> {
            if (searchQuery.isEmpty()) return emptyList()
            val source = editorText
            val needle = searchQuery
            val matches = mutableListOf<IntRange>()
            var fromIndex = 0
            while (fromIndex <= source.length - needle.length) {
                val start = source.indexOf(needle, fromIndex, ignoreCase = !searchMatchCase)
                if (start < 0) break
                matches += start until (start + needle.length)
                fromIndex = start + needle.length.coerceAtLeast(1)
            }
            return matches
        }

        fun selectSearchMatch(
            direction: Int
        ) {
            val matches = searchMatches()
            if (matches.isEmpty()) return
            val selectionStart = editorValue.selection.min
            val selectionEnd = editorValue.selection.max
            val currentIndex = matches.indexOfFirst {
                it.first == selectionStart && it.last + 1 == selectionEnd
            }
            val targetIndex = if (direction >= 0) {
                if (currentIndex >= 0) {
                    (currentIndex + 1) % matches.size
                } else {
                    matches.indexOfFirst { it.first >= selectionEnd }
                        .takeIf { it >= 0 }
                        ?: 0
                }
            } else {
                if (currentIndex >= 0) {
                    (currentIndex - 1 + matches.size) % matches.size
                } else {
                    matches.indexOfLast { it.last + 1 <= selectionStart }
                        .takeIf { it >= 0 }
                        ?: matches.lastIndex
                }
            }
            val target = matches[targetIndex]
            editorValue = editorValue.copy(
                selection = TextRange(target.first, target.last + 1)
            )
            editorFocusRequester.requestFocus()
        }

        fun replaceSelectedMatch() {
            if (searchQuery.isEmpty()) return
            val start = editorValue.selection.min
            val end = editorValue.selection.max
            val selected = editorText.substring(start, end)
            val matches = if (searchMatchCase) {
                selected == searchQuery
            } else {
                selected.equals(searchQuery, ignoreCase = true)
            }
            if (!matches) {
                selectSearchMatch(1)
                return
            }
            applyEditorChange(
                TextFieldValue(
                    editorText.replaceRange(start, end, replacementText),
                    TextRange(start + replacementText.length)
                )
            )
        }

        fun replaceAllMatches() {
            val matches = searchMatches()
            if (matches.isEmpty()) return
            val builder = StringBuilder(editorText)
            matches.asReversed().forEach { range ->
                builder.replace(range.first, range.last + 1, replacementText)
            }
            applyEditorChange(
                TextFieldValue(
                    builder.toString(),
                    TextRange(0)
                )
            )
        }

        fun saveStore(
            targetWorks: List<Work> =
                works,

            targetActiveWorkId: String =
                activeWorkId
        ): Boolean {

            val uri = selectedFolderUri

            return saveWorkStore(
                folderUri =
                    uri,

                works =
                    targetWorks,

                activeWorkId =
                    targetActiveWorkId
            )
        }

        fun updateCurrentWork() {

            activeWork?.let {

                if (
                    it.code !=
                    editorText
                ) {

                    it.code =
                        editorText

                    it.updatedAt =
                        System.currentTimeMillis()
                }
            }
        }

        fun persistWorkChange(targetWorks: List<Work>, targetId: String): Boolean {
            if (saveStore(targetWorks, targetId)) return true
            Toast.makeText(this@MainActivity,
                uiText("保存できませんでした。保存先を確認して再試行してください"),
                Toast.LENGTH_LONG).show()
            return false
        }

        fun updateActiveWorkPreviewRatio(value: String) {
            val normalized = normalizedPreviewAspectRatio(value)
            val work = works.find { it.id == activeWorkId } ?: return
            if (work.previewAspectRatio == normalized) return
            val previousRatio = work.previewAspectRatio
            val previousUpdatedAt = work.updatedAt
            work.previewAspectRatio = normalized
            work.updatedAt = System.currentTimeMillis()
            if (selectedFolderUri != null && !saveStore()) {
                work.previewAspectRatio = previousRatio
                work.updatedAt = previousUpdatedAt
                Toast.makeText(this@MainActivity, uiText("比率を保存できませんでした。保存先を確認してください"), Toast.LENGTH_SHORT).show()
            }
        }

        val currentRatioChange = rememberUpdatedState<(String) -> Unit>(::updateActiveWorkPreviewRatio)

        fun runSketch(
            source: String = editorText,
            supportingFiles: Map<String, String> = activeWork?.files.orEmpty(),
            sourceAssets: Map<String, ProjectAsset> = sessionViewModel.worksState.value
                .find { it.id == sessionViewModel.activeWorkIdState.value }?.assets.orEmpty()
        ) {

            if (isPreviewRecording) {
                Toast.makeText(this@MainActivity, uiText("録画を停止してから再実行してください"), Toast.LENGTH_SHORT).show()
                return
            }

            previewAssets = PreviewAssets(java.util.UUID.randomUUID().toString(), sourceAssets.toMap())
            val runtimeWork = sessionViewModel.worksState.value
                .find { it.id == sessionViewModel.activeWorkIdState.value }
            pendingP5Version = normalizedP5Version(runtimeWork?.p5Version)
            pendingP5SoundEnabled = runtimeWork?.p5SoundEnabled == true
            pendingSketchCode =
                composeProjectSource(source, supportingFiles)

            pendingMainLineOffset =
                supportingFiles.values.sumOf { code ->
                    code.count { it == '\n' } + 2
                }

            isPaused =
                false

            isError =
                false

            consoleEntries.clear()

            webView?.loadUrl(
                previewUrl(previewAssets.token)
            )
        }

        LaunchedEffect(sessionViewModel.assetPreviewRevision) {
            if (sessionViewModel.assetPreviewRevision > 0) runSketch()
        }

        fun restoreCurrentWork() {

            val stored =
                selectedFolderUri
                    ?.let {
                        loadWorkStore(it)
                    }

            val savedWork =
                stored
                    ?.works
                    ?.find {
                        it.id ==
                                activeWorkId
                    }

            if (savedWork != null) {

                editorValue =
                    TextFieldValue(
                        savedWork.code
                    )

                lastSavedText =
                    savedWork.code

                clearEditHistory()

                works = works.map { if (it.id == savedWork.id) savedWork else it }

                isError =
                    false

                clearDraftSnapshot()

                if (autoRun) {

                    runSketch(
                        savedWork.code,
                        savedWork.files
                    )
                }
            }
        }

        fun saveCurrentWork() {
            val current = works.find { it.id == activeWorkId } ?: return
            val revisions = current.revisions.toMutableList()
            if (lastSavedText != editorText) {
                revisions.add(WorkRevision(lastSavedText, current.updatedAt))
            }
            val saved = Work(
                id = current.id, title = current.title, code = editorText,
                files = current.files.toMutableMap(),
                assets = current.assets.toMap(),
                revisions = revisions.takeLast(30).toMutableList(),
                previewAspectRatio = current.previewAspectRatio,
                p5Version = current.p5Version,
                p5SoundEnabled = current.p5SoundEnabled,
                createdAt = current.createdAt, updatedAt = System.currentTimeMillis()
            )
            val updatedWorks = works.map { if (it.id == current.id) saved else it }
            if (saveStore(updatedWorks)) {
                works = updatedWorks
                isError = false
                lastSavedText = editorText
                clearDraftSnapshot()
            } else {
                isError = true
                Toast.makeText(this@MainActivity, uiText("保存できませんでした。保存先フォルダーを確認してください"), Toast.LENGTH_LONG).show()
            }
        }

        val folderLauncher =
            rememberLauncherForActivityResult(
                contract =
                    ActivityResultContracts
                        .OpenDocumentTree()
            ) { uri ->

                if (uri != null) {

                    try {

                        contentResolver
                            .takePersistableUriPermission(
                                uri,
                                Intent
                                    .FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent
                                            .FLAG_GRANT_WRITE_URI_PERMISSION
                            )

                    } catch (
                        e: Exception
                    ) {

                        Log.w(
                            "EditKIRO",
                            "Persistable permission failed",
                            e
                        )
                    }

                    val stored = loadWorkStore(uri)
                    if (uri.toString() in unreadableWorkFolders) {
                        Toast.makeText(this@MainActivity, uiText("作品ファイルを読み込めません。元のファイルは上書きしていません"), Toast.LENGTH_LONG).show()
                        return@rememberLauncherForActivityResult
                    }

                    preferences.edit()
                        .putString(
                            folderUriKey,
                            uri.toString()
                        )
                        .apply()

                    selectedFolderUri =
                        uri

                    if (
                        stored != null &&
                        stored.works.isNotEmpty()
                    ) {

                        works =
                            stored.works

                        activeWorkId =
                            if (
                                stored.works.any {
                                    it.id ==
                                            stored.activeWorkId
                                }
                            ) {

                                stored.activeWorkId

                            } else {

                                stored
                                    .works
                                    .first()
                                    .id
                            }

                        editorValue =
                            TextFieldValue(
                                stored.works
                                    .find { it.id == activeWorkId }
                                    ?.code
                                    .orEmpty()
                            )

                        lastSavedText =
                            editorValue.text

                        clearEditHistory()

                        isError =
                            false

                    } else {

                        saveWorkStore(
                            folderUri =
                                uri,

                            works =
                                works,

                            activeWorkId =
                                activeWorkId
                        )

                        isError =
                            false
                    }

                    clearDraftSnapshot()
                }
            }

        val importJsLauncher =
            rememberLauncherForActivityResult(
                contract =
                    ActivityResultContracts
                        .OpenDocument()
            ) { uri ->

                if (uri != null) {

                    val importedCode =
                        runCatching {

                            contentResolver
                                .openInputStream(
                                    uri
                                )
                                ?.bufferedReader()
                                ?.use {
                                    it.readText()
                                }
                        }.getOrNull()

                    if (importedCode != null) {

                        updateCurrentWork()
                        if (saveStore()) {
                            lastSavedText = editorText
                        }

                        val now =
                            System.currentTimeMillis()

                        val importedTitle =
                            displayNameForUri(
                                uri
                            )
                                ?.removeSuffix(
                                    ".js"
                                )
                                ?.removeSuffix(
                                    ".JS"
                                )
                                ?.ifBlank {
                                    null
                                }
                                ?: "Imported sketch"

                        val newWork =
                            Work(
                                id =
                                    now.toString(),
                                title =
                                    importedTitle,
                                code =
                                    importedCode,
                                createdAt =
                                    now,
                                updatedAt =
                                    now
                            )

                        val newWorks =
                            works +
                                newWork

                        works =
                            newWorks

                        activeWorkId =
                            newWork.id

                        editorValue =
                            TextFieldValue(
                                importedCode
                            )

                        saveWorkStore(
                            folderUri =
                                selectedFolderUri,
                            works =
                                newWorks,
                            activeWorkId =
                                newWork.id
                        )

                        clearDraftSnapshot()

                        isError =
                            false

                        if (autoRun) {
                            runSketch(
                                importedCode,
                                emptyMap()
                            )
                        }

                    } else {

                        isError =
                            true

                        appendConsole(
                            level =
                                ConsoleLevel.ERROR,
                            message =
                                uiText("JSファイルを読み込めませんでした")
                        )

                        showConsole =
                            true
                    }
                }
            }

        fun changeAssets(workId: String, updated: Map<String, ProjectAsset>) {
            if (assetBusy) return
            val target = works.find { it.id == workId } ?: return
            validateAssetSet(updated)
            val replacement = snapshotWork(target, updated)
            val nextWorks = works.map { if (it.id == workId) replacement else snapshotWork(it) }
            val folder = selectedFolderUri
            val selectedId = activeWorkId
            assetBusy = true
            assetScope.launch {
                val saved = withContext(Dispatchers.IO) { saveWorkStore(folder, nextWorks, selectedId) }
                if (saved) {
                    target.assets.clear()
                    target.assets.putAll(updated)
                    target.updatedAt = replacement.updatedAt
                    if (activeWorkId == workId) sessionViewModel.assetPreviewRevision++
                } else Toast.makeText(this@MainActivity, uiText("素材を保存できませんでした"), Toast.LENGTH_LONG).show()
                assetBusy = false
            }
        }

        val assetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            val targetId = assetTargetId
            assetTargetId = null
            val target = works.find { it.id == targetId }
            if (target != null && uris.isNotEmpty() && !assetBusy) {
                val existing = target.assets.toMap()
                val folder = selectedFolderUri
                val selectedId = activeWorkId
                val snapshots = works.map { snapshotWork(it) }
                assetBusy = true
                assetScope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            pruneUnusedAssets(snapshots)
                            val updated = existing.toMutableMap()
                            require(updated.size + uris.size <= 100)
                            uris.forEach { uri ->
                                val original = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                                    if (it.moveToFirst()) it.getString(0) else null
                                } ?: "asset"
                                val name = uniqueAssetName(original, updated.keys)
                                val mime = assetMimeType(name, contentResolver.getType(uri))
                                val asset = contentResolver.openInputStream(uri)?.use { assetStorage.put(it, mime) }
                                    ?: error("Cannot open asset")
                                updated[name] = asset
                                validateAssetSet(updated)
                            }
                            val nextWorks = snapshots.map { if (it.id == target.id) snapshotWork(it, updated) else it }
                            check(saveWorkStore(folder, nextWorks, selectedId))
                            updated.toMap()
                        }
                    }
                    result.onSuccess { updated ->
                        target.assets.clear(); target.assets.putAll(updated)
                        target.updatedAt = System.currentTimeMillis()
                        if (activeWorkId == target.id) sessionViewModel.assetPreviewRevision++
                    }.onFailure {
                        if (it is kotlinx.coroutines.CancellationException) throw it
                        Toast.makeText(this@MainActivity, uiText("素材を追加できませんでした。ファイル名・サイズ・保存先を確認してください"), Toast.LENGTH_LONG).show()
                    }
                    assetBusy = false
                }
            }
        }

        fun loadP5Account() {
            val username = p5Username.trim()
            if (p5Busy || !validP5Username(username)) return
            p5Busy = true
            p5Error = null
            p5Sketches = emptyList()
            assetScope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { fetchP5Sketches(username) }
                }
                result.onSuccess { sketches ->
                    p5Sketches = sketches
                    preferences.edit().putString(p5UsernameKey, username).apply()
                    p5Username = username
                    if (sketches.isEmpty()) p5Error = "公開作品がありません"
                }.onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    p5Error = "p5.jsの作品を取得できませんでした"
                }
                p5Busy = false
            }
        }

        fun importFromP5(sketch: P5Sketch) {
            if (p5Busy) return
            updateCurrentWork()
            val currentWorks = works.map { snapshotWork(it) }
            val folder = selectedFolderUri
            p5Busy = true
            p5Error = null
            assetScope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        pruneUnusedAssets(currentWorks)
                        val imported = importP5Sketch(sketch, assetStorage)
                        val now = System.currentTimeMillis()
                        val work = Work(
                            id = "$now-${sketch.id}",
                            title = imported.name,
                            code = imported.code,
                            files = imported.supportingFiles.toMutableMap(),
                            assets = imported.assets,
                            p5Version = imported.p5Version,
                            p5SoundEnabled = imported.p5SoundEnabled,
                            createdAt = now,
                            updatedAt = now
                        )
                        val nextWorks = currentWorks + work
                        check(saveWorkStore(folder, nextWorks, work.id))
                        nextWorks to work
                    }
                }
                result.onSuccess { (nextWorks, work) ->
                    works = nextWorks
                    activeWorkId = work.id
                    editorValue = TextFieldValue(work.code)
                    lastSavedText = work.code
                    clearEditHistory()
                    clearDraftSnapshot()
                    sessionViewModel.assetPreviewRevision++
                    showP5Import = false
                    if (autoRun) runSketch(work.code, work.files)
                    Toast.makeText(this@MainActivity, uiText("p5.jsの作品を取り込みました"), Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    p5Error = "作品を取り込めませんでした。ファイル数・容量・通信環境を確認してください"
                }
                p5Busy = false
            }
        }

        val exportJsLauncher =
            rememberLauncherForActivityResult(
                contract =
                    ActivityResultContracts
                        .CreateDocument(
                            "application/javascript"
                        )
            ) { uri ->

                if (uri != null) {

                    val exported =
                        runCatching {

                            val stream =
                                contentResolver
                                    .openOutputStream(
                                        uri,
                                        "wt"
                                    )
                                    ?: return@runCatching false

                            stream
                                .bufferedWriter()
                                .use {
                                    it.write(
                                        editorText
                                    )
                                }

                            true
                        }.getOrDefault(
                            false
                        )

                    if (!exported) {

                        isError =
                            true

                        appendConsole(
                            level =
                                ConsoleLevel.ERROR,
                            message =
                                uiText("JSファイルを書き出せませんでした")
                        )

                        showConsole =
                            true
                    }
                }
            }

        val exportBackupLauncher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/zip")
            ) { uri ->
                if (uri != null && !assetBusy) {
                    val backupWorks = works.map { snapshotWork(it) }
                    val backupActiveId = activeWorkId
                    val settings = JSONObject()
                        .put("themeMode", themeMode.name)
                        .put("autoRun", autoRun)
                        .put("compactPreview", compactPreview)
                        .put("hideEditingPreview", hideEditingPreview)
                        .put("p5Username", p5Username)
                        .put("landscapeUseCutout", landscapeUseCutout)
                        .put("appLanguage", appLanguage)
                        .put("preserveExpandedPreview", preserveExpandedPreview)
                        .put("landscapePreviewSplit", landscapePreviewFraction.toDouble())
                        .put("resizeHandlesVisible", showResizeHandles)
                        .put("editorFontSize", editorFontSize.toDouble())
                        .put("autoIndent", autoIndent)
                        .put("lineNumbers", showLineNumbers)
                        .put("accessoryBar", showEditorAccessoryBar)
                        .put("codeCompletion", codeCompletion)
                        .put("accessoryNavigation", showAccessoryNavigation)
                        .put("accessorySymbols", showAccessorySymbols)
                        .put("compactAccessoryKeys", compactAccessoryKeys)
                        .put("draftRecovery", draftRecovery)
                    assetScope.launch {
                        assetBusy = true
                        val exported = withContext(Dispatchers.IO) {
                            runCatching {
                                val output = contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open backup")
                                writeAssetBackup(output, backupWorks, backupActiveId, settings.toString(2), assetStorage)
                            }.isSuccess
                        }
                        if (!exported) {
                            appendConsole(ConsoleLevel.ERROR, uiText("バックアップを書き出せませんでした"))
                            showConsole = true
                        }
                        assetBusy = false
                    }
                }
            }

        val importBackupLauncher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null && !assetBusy) {
                    assetScope.launch {
                        assetBusy = true
                        val restored = runCatching {
                            val backup = withContext(Dispatchers.IO) {
                                contentResolver.openInputStream(uri)?.let { readAssetBackup(it, assetStorage) }
                                    ?: error("Cannot open backup")
                            }
                            val store = backup.store
                            val settingsJson = backup.settings
                            // Validate settings and complete persistence before replacing the open session.
                            val restoredSettings = settingsJson?.let(::JSONObject)
                            val restoredActiveId = store.activeWorkId.takeIf { id ->
                                store.works.any { it.id == id }
                            } ?: store.works.first().id
                            val restoredFolder = selectedFolderUri
                            if (!withContext(Dispatchers.IO) { saveWorkStore(restoredFolder, store.works, restoredActiveId) }) {
                                return@runCatching false
                            }
                            works = store.works
                            activeWorkId = restoredActiveId
                            editorValue = TextFieldValue(
                                store.works.find { it.id == activeWorkId }?.code.orEmpty()
                            )
                            lastSavedText = editorValue.text
                            clearEditHistory()
                            restoredSettings?.let { settings ->
                                runCatching {
                                    AppThemeMode.valueOf(settings.optString("themeMode"))
                                }.getOrNull()?.let(onThemeModeChange)
                                autoRun = settings.optBoolean("autoRun", autoRun)
                                hideEditingPreview = settings.optBoolean("hideEditingPreview", hideEditingPreview)
                                p5Username = settings.optString("p5Username", p5Username)
                                    .takeIf(::validP5Username).orEmpty()
                                compactPreview = settings.optBoolean("compactPreview", compactPreview)
                                landscapeUseCutout = settings.optBoolean("landscapeUseCutout", landscapeUseCutout)
                                appLanguage = settings.optString("appLanguage", appLanguage)
                                    .takeIf { it in listOf("system", "ja", "en", "zh") } ?: "system"
                                preserveExpandedPreview = settings.optBoolean(
                                    "preserveExpandedPreview",
                                    preserveExpandedPreview
                                )
                                landscapePreviewFraction = settings.optDouble(
                                    "landscapePreviewSplit",
                                    landscapePreviewFraction.toDouble()
                                ).toFloat().let { restored ->
                                    LANDSCAPE_PREVIEW_SPLITS.minByOrNull {
                                        kotlin.math.abs(it - restored)
                                    } ?: 0.5f
                                }
                                showResizeHandles = settings.optBoolean(
                                    "resizeHandlesVisible",
                                    showResizeHandles
                                )
                                editorFontSize = settings.optDouble("editorFontSize", editorFontSize.toDouble())
                                    .toFloat().takeIf { it.isFinite() }?.coerceIn(12f, 20f) ?: 14f
                                autoIndent = settings.optBoolean("autoIndent", autoIndent)
                                showLineNumbers = settings.optBoolean("lineNumbers", showLineNumbers)
                                showEditorAccessoryBar = settings.optBoolean("accessoryBar", showEditorAccessoryBar)
                                codeCompletion = settings.optBoolean("codeCompletion", codeCompletion)
                                showAccessoryNavigation = settings.optBoolean("accessoryNavigation", showAccessoryNavigation)
                                showAccessorySymbols = settings.optBoolean("accessorySymbols", showAccessorySymbols)
                                compactAccessoryKeys = settings.optBoolean("compactAccessoryKeys", compactAccessoryKeys)
                                draftRecovery = settings.optBoolean("draftRecovery", draftRecovery)
                                preferences.edit()
                                    .putBoolean(autoRunKey, autoRun)
                                    .putBoolean(compactPreviewKey, compactPreview)
                                    .putBoolean(hideEditingPreviewKey, hideEditingPreview)
                                    .putString(p5UsernameKey, p5Username)
                                    .putBoolean(landscapeCutoutKey, landscapeUseCutout)
                                    .putString(appLanguageKey, appLanguage)
                                    .putBoolean(preserveExpandedPreviewKey, preserveExpandedPreview)
                                    .putFloat(landscapePreviewSplitKey, landscapePreviewFraction)
                                    .putBoolean(resizeHandlesVisibleKey, showResizeHandles)
                                    .putFloat(editorFontKey, editorFontSize)
                                    .putBoolean(autoIndentKey, autoIndent)
                                    .putBoolean(lineNumbersKey, showLineNumbers)
                                    .putBoolean(editorAccessoryBarKey, showEditorAccessoryBar)
                                    .putBoolean(codeCompletionKey, codeCompletion)
                                    .putBoolean(accessoryNavigationKey, showAccessoryNavigation)
                                    .putBoolean(accessorySymbolsKey, showAccessorySymbols)
                                    .putBoolean(compactAccessoryKeysKey, compactAccessoryKeys)
                                    .putBoolean(draftRecoveryKey, draftRecovery)
                                    .apply()
                            }
                            clearDraftSnapshot()
                            if (autoRun) {
                                val restoredWork = store.works.first { it.id == restoredActiveId }
                                runSketch(restoredWork.code, restoredWork.files)
                            }
                            true
                        }.getOrDefault(false)
                        if (!restored) {
                            appendConsole(ConsoleLevel.ERROR, uiText("バックアップを復元できませんでした"))
                            showConsole = true
                        }
                        assetBusy = false
                    }
                }
            }

        val workPreviewRatio = previewAspectRatioValue(previewRatioSelection, devicePreviewRatio)


        val animatedLandscapePreviewFraction by animateFloatAsState(
            targetValue = landscapePreviewFraction,
            animationSpec = tween(
                durationMillis = 260,
                easing = FastOutSlowInEasing
            ),
            label = "landscapePreviewSplit"
        )

        @Composable
        fun WorkSheet(
            title: String,
            subtitle: String,
            onDismiss: () -> Unit,
            headerAction: @Composable () -> Unit = {},
            content: @Composable ColumnScope.() -> Unit
        ) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = sheetState,
                containerColor = colors.surface,
                contentColor = colors.onSurface,
                tonalElevation = 0.dp,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                dragHandle = null
            ) {
                KeepLandscapeDialogImmersive(enabled = isLandscape)
                Column(
                    Modifier
                        .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                        headerAction()
                        IconButton(onClick = onDismiss) {
                            Icon(painterResource(R.drawable.ic_close), uiText("閉じる"),
                                Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
                    Column(Modifier.weight(1f, fill = false).padding(top = 12.dp)) {
                        content()
                    }
                }
            }
        }

        @Composable
        fun WorkSelector(modifier: Modifier = Modifier) {
            Surface(
                onClick = { workMenuExpanded = true },
                modifier = modifier,
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outlineVariant)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_folder_code), null,
                        Modifier.size(20.dp), tint = colors.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .widthIn(max = if (manualRotation) 128.dp else 180.dp)
                    ) {
                        Text(
                            if (hasUnsavedChanges) uiText("作品・未保存") else uiText("作品"),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasUnsavedChanges) colors.tertiary else colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(activeWork?.title ?: uiText("作品を選択"), color = colors.onSurface,
                            fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(painterResource(R.drawable.ic_chevron_down), uiText("作品一覧を開く"),
                        Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                }
            }

            if (workMenuExpanded) {
                var query by rememberSaveable { mutableStateOf("") }
                val filteredWorks = works.filter { it.title.contains(query.trim(), ignoreCase = true) }
                WorkSheet(
                    title = uiText("作品を選択"),
                    subtitle = uiText("%s作品", works.size),
                    onDismiss = { workMenuExpanded = false },
                    headerAction = {
                        OutlinedButton(
                            onClick = { workMenuExpanded = false; showAddDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(uiText("追加"))
                        }
                    }
                ) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(uiText("作品名で検索")) },
                        leadingIcon = {
                            Icon(painterResource(R.drawable.ic_search), null, Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                                Icon(painterResource(R.drawable.ic_close), uiText("検索をクリア"),
                                    Modifier.size(18.dp))
                            }
                        },
                        singleLine = true, shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        if (filteredWorks.isEmpty()) item {
                            Text(uiText("一致する作品がありません"),
                                Modifier.padding(vertical = 24.dp),
                                color = colors.onSurfaceVariant)
                        }
                        items(filteredWorks, key = { it.id }) { work ->
                            val selected = work.id == activeWorkId
                            Surface(
                                onClick = selectWork@{
                                    if (work.id == activeWorkId) {
                                        workMenuExpanded = false
                                        return@selectWork
                                    }
                                    updateCurrentWork()
                                    if (!persistWorkChange(works, work.id)) return@selectWork
                                    activeWorkId = work.id
                                    editorValue = TextFieldValue(work.code)
                                    clearDraftSnapshot()
                                    workMenuExpanded = false
                                    if (autoRun) runSketch(work.code, work.files)
                                },
                                modifier = Modifier.fillMaxWidth().semantics {
                                    contentDescription = work.title + if (selected) uiText("、選択中") else ""
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = if (selected) colors.primary.copy(alpha = 0.08f) else colors.surface,
                                border = BorderStroke(1.dp,
                                    if (selected) colors.primary.copy(alpha = 0.7f) else colors.outlineVariant)
                            ) {
                                Row(Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(12.dp),
                                        color = colors.surfaceVariant) {
                                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                            Icon(painterResource(R.drawable.ic_folder_code), null,
                                                Modifier.size(22.dp),
                                                tint = if (selected) colors.primary else colors.onSurfaceVariant)
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(work.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.Medium)
                                        Text((if (work.previewAspectRatio == "device") uiText("端末")
                                            else work.previewAspectRatio) + "  ·  " +
                                            if (selected && hasUnsavedChanges) uiText("未保存の変更あり")
                                            else if (selected) uiText("選択中") else uiText("タップして開く"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected && hasUnsavedChanges)
                                                colors.tertiary else colors.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun WorkActions() {

            @Composable
            fun ActionRow(
                iconRes: Int,
                title: String,
                subtitle: String,
                enabled: Boolean = true,
                destructive: Boolean = false,
                onClick: () -> Unit
            ) {
                val tint = when {
                    !enabled -> colors.onSurface.copy(alpha = 0.38f)
                    destructive -> colors.error
                    else -> colors.onSurface
                }
                Surface(
                    onClick = onClick, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = if (destructive) colors.error.copy(alpha = 0.04f) else colors.surface,
                    border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = if (enabled) 0.7f else 0.35f))
                ) {
                    Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(iconRes), null, Modifier.size(22.dp), tint = tint)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, color = tint, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium)
                            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                                color = tint.copy(alpha = if (enabled) 0.7f else 0.6f))
                        }
                    }
                }
            }

            @Composable
            fun SectionLabel(
                text: String
            ) {

                Text(
                    text =
                        text,
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,
                    color =
                        colors
                            .onSurfaceVariant,
                    letterSpacing =
                        0.8.sp,
                    modifier =
                        Modifier.padding(
                            start =
                                if (isLandscape) {
                                    10.dp
                                } else {
                                    12.dp
                                },
                            bottom =
                                4.dp
                        )
                )
            }

            @Composable
            fun EditActions() {

                SectionLabel(
                    text =
                        uiText("編集・作品")
                )

                ActionRow(
                    iconRes = R.drawable.ic_undo,
                    title = uiText("元に戻す"),
                    subtitle = uiText("直前の編集を取り消す"),
                    enabled = undoStack.isNotEmpty(),
                    onClick = {
                        workActionsMenuExpanded = false
                        undoEditorChange()
                    }
                )

                ActionRow(
                    iconRes = R.drawable.ic_redo,
                    title = uiText("やり直す"),
                    subtitle = uiText("取り消した編集をやり直す"),
                    enabled = redoStack.isNotEmpty(),
                    onClick = {
                        workActionsMenuExpanded = false
                        redoEditorChange()
                    }
                )

                ActionRow(
                    iconRes = R.drawable.ic_search,
                    title = uiText("検索・置換"),
                    subtitle = uiText("文字列検索、置換、指定行へ移動"),
                    onClick = {
                        workActionsMenuExpanded = false
                        showSearchDialog = true
                    }
                )

                ActionRow(
                    iconRes = R.drawable.ic_format,
                    title = uiText("コードを整形"),
                    subtitle = uiText("インデントと空行を整理"),
                    onClick = {
                        workActionsMenuExpanded = false
                        val formatted = formatJavaScript(editorText)
                        if (formatted != editorText) {
                            applyEditorChange(TextFieldValue(formatted, TextRange(0)))
                        }
                        editorFocusRequester.requestFocus()
                    }
                )

                ActionRow(
                    iconRes = R.drawable.ic_folder_code,
                    title = uiText("プロジェクトファイル"),
                    subtitle = uiText("追加JavaScriptファイルを管理"),
                    onClick = {
                        workActionsMenuExpanded = false
                        showProjectFilesDialog = true
                    }
                )

                ActionRow(
                    iconRes = R.drawable.ic_folder_code,
                    title = uiText("作品の素材"),
                    subtitle = uiText("画像・音声・フォントなどを管理"),
                    onClick = { workActionsMenuExpanded = false; focusManager.clearFocus(force = true); showAssets = true }
                )

                ActionRow(
                    iconRes = R.drawable.ic_restore,
                    title = uiText("変更履歴"),
                    subtitle = uiText("過去30回の保存状態を表示・復元"),
                    enabled = activeWork?.revisions?.isNotEmpty() == true,
                    onClick = {
                        workActionsMenuExpanded = false
                        showHistoryDialog = true
                    }
                )

                ActionRow(
                    iconRes = R.drawable.ic_fullscreen,
                    title = uiText("プレビュー比率"),
                    subtitle = uiText("作品ごとにキャンバスの縦横比を設定"),
                    onClick = {
                        workActionsMenuExpanded = false
                        showAspectRatioDialog = true
                    }
                )

                ActionRow(
                    iconRes = R.drawable.ic_code,
                    title = uiText("実行環境"),
                    subtitle = uiText("p5.jsとp5.soundを作品ごとに設定"),
                    onClick = {
                        workActionsMenuExpanded = false
                        runtimeP5Version = normalizedP5Version(activeWork?.p5Version)
                        runtimeSoundEnabled = activeWork?.p5SoundEnabled == true
                        showRuntimeDialog = true
                    }
                )

                ActionRow(
                    iconRes =
                        R.drawable.ic_rename,
                    title =
                        uiText("名前を変更"),
                    subtitle =
                        uiText("作品タイトルを編集"),
                    onClick = {
                        workActionsMenuExpanded =
                            false
                        showRenameDialog =
                            true
                    }
                )

                ActionRow(
                    iconRes =
                        R.drawable.ic_duplicate,
                    title =
                        uiText("複製"),
                    subtitle =
                        uiText("現在のコードからコピーを作成"),
                    onClick = duplicateWork@{

                        workActionsMenuExpanded =
                            false

                        val current =
                            activeWork

                        if (current != null) {

                            val now =
                                System.currentTimeMillis()

                            val duplicate =
                                Work(
                                    id =
                                        java.util.UUID.randomUUID().toString(),
                                    title =
                                        "${current.title} copy",
                                    code =
                                        editorText,
                                    files =
                                        current.files.toMutableMap(),
                                    assets = current.assets.toMap(),
                                    previewAspectRatio =
                                        current.previewAspectRatio,
                                    p5Version = current.p5Version,
                                    p5SoundEnabled = current.p5SoundEnabled,
                                    createdAt =
                                        now,
                                    updatedAt =
                                        now
                                )

                            val newWorks =
                                works +
                                    duplicate

                            updateCurrentWork()
                            if (!persistWorkChange(newWorks, duplicate.id)) return@duplicateWork

                            works =
                                newWorks

                            activeWorkId =
                                duplicate.id

                            editorValue =
                                TextFieldValue(
                                    duplicate.code
                                )

                            clearDraftSnapshot()

                            isError =
                                false

                            if (autoRun) {
                                runSketch(
                                    duplicate.code,
                                    duplicate.files
                                )
                            }
                        }
                    }
                )
            }

            @Composable
            fun FileActions() {

                SectionLabel(
                    text =
                        uiText("ファイル")
                )

                ActionRow(
                    iconRes =
                        R.drawable.ic_import_js,
                    title =
                        uiText("JSをインポート"),
                    subtitle =
                        uiText("外部のJavaScriptファイルを読み込む"),
                    onClick = {
                        workActionsMenuExpanded =
                            false

                        importJsLauncher.launch(
                            arrayOf(
                                "application/javascript",
                                "text/javascript",
                                "text/plain",
                                "application/octet-stream"
                            )
                        )
                    }
                )

                ActionRow(
                    iconRes =
                        R.drawable.ic_export_js,
                    title =
                        uiText("JSを書き出す"),
                    subtitle =
                        uiText("現在の作品を.jsファイルとして保存"),
                    onClick = {
                        workActionsMenuExpanded =
                            false

                        exportJsLauncher.launch(
                            safeJsFileName(
                                activeWork
                                    ?.title
                                    ?: "sketch"
                            )
                        )
                    }
                )
            }

            @Composable
            fun DeleteAction() {

                ActionRow(
                    iconRes =
                        R.drawable.ic_delete,
                    title =
                        uiText("削除"),
                    subtitle =
                        if (works.size > 1) {
                            uiText("この作品を削除")
                        } else {
                            uiText("最後の1作品は削除できません")
                        },
                    enabled =
                        works.size > 1,
                    destructive =
                        true,
                    onClick = {
                        workActionsMenuExpanded =
                            false
                        showDeleteDialog =
                            true
                    }
                )
            }

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                if (manualRotation) {

                    IconButton(
                        onClick = {

                            val activity =
                                view.context as Activity

                            activity.requestedOrientation =
                                if (isLandscape) {
                                    ActivityInfo
                                        .SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    ActivityInfo
                                        .SCREEN_ORIENTATION_LANDSCAPE
                                }
                        }
                    ) {

                        Icon(
                            painter =
                                painterResource(
                                    R.drawable.ic_rotate
                                ),
                            contentDescription =
                                uiText("画面を回転"),
                            tint =
                                colors.onSurface,
                            modifier =
                                Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {

                        updateCurrentWork()
                        if (saveStore()) {
                            lastSavedText = editorText
                        }
                        clearDraftSnapshot()

                        showSettings =
                            true
                    }
                ) {

                    Icon(
                        painter =
                            painterResource(
                                R.drawable.ic_settings
                            ),
                        contentDescription =
                            uiText("設定"),
                        tint =
                            colors.onSurface,
                        modifier =
                            Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = {
                        showAddDialog =
                            true
                    }
                ) {

                    Icon(
                        painter =
                            painterResource(
                                R.drawable.ic_add
                            ),
                        contentDescription =
                            uiText("作品を追加"),
                        tint =
                            colors.onSurface,
                        modifier =
                            Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = {
                        workActionsMenuExpanded =
                            true
                    }
                ) {

                    Icon(
                        painter =
                            painterResource(
                                R.drawable.ic_more_vertical
                            ),
                        contentDescription =
                            uiText("作品メニュー"),
                        tint =
                            colors.onSurface,
                        modifier =
                            Modifier.size(20.dp)
                    )
                }
            }

            if (workActionsMenuExpanded) {
                WorkSheet(
                    title = uiText("作品メニュー"),
                    subtitle = (activeWork?.title ?: uiText("作品未選択")) +
                        if (hasUnsavedChanges) uiText(" · 未保存") else "",
                    onDismiss = { workActionsMenuExpanded = false }
                ) {
                    // One scroll area keeps every command reachable on small screens
                    // and with large accessibility fonts, in either orientation.
                    Row(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            EditActions()
                            if (!isLandscape) {
                                Spacer(Modifier.height(12.dp))
                                FileActions()
                                Spacer(Modifier.height(12.dp))
                                SectionLabel(uiText("管理"))
                                DeleteAction()
                            }
                        }
                        if (isLandscape) {
                            Column(Modifier.weight(1f)) {
                                FileActions()
                                Spacer(Modifier.height(12.dp))
                                SectionLabel(uiText("管理"))
                                DeleteAction()
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun WorkBar() {

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal =
                                12.dp,

                            vertical =
                                2.dp
                        ),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                ) {
                    WorkSelector()
                }

                WorkActions()
            }
        }

        @Composable
        fun PreviewOverlayButton(
            iconRes: Int,
            description: String,
            active: Boolean = false,
            modifier: Modifier = Modifier,
            onClick: () -> Unit
        ) {
            Surface(
                onClick = onClick,
                modifier = modifier.size(40.dp),
                shape = RoundedCornerShape(11.dp),
                color = if (active) {
                    colors.errorContainer.copy(alpha = 0.96f)
                } else {
                    colors.surface.copy(alpha = 0.9f)
                },
                contentColor = if (active) colors.onErrorContainer else colors.onSurface,
                border = BorderStroke(
                    1.dp,
                    if (active) colors.error else colors.outlineVariant
                ),
                shadowElevation = 3.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = description,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        @Composable
        fun PreviewArea(
            modifier: Modifier,
            showExpandControl: Boolean = true,
            logicalSize: IntSize? = null,
            fullscreen: Boolean = false
        ) {

            Card(
                modifier =
                    if (fullscreen) {
                        modifier
                    } else {
                        modifier.border(
                            width =
                                1.dp,

                            color =
                                if (isError) {
                                    colors.error
                                } else {
                                    colors
                                        .outlineVariant
                                },

                            shape =
                                RoundedCornerShape(
                                    18.dp
                                )
                        )
                    },

                shape =
                    if (fullscreen) RectangleShape else RoundedCornerShape(18.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Color.Black
                    ),

                elevation =
                    CardDefaults.cardElevation(
                        defaultElevation =
                            0.dp
                    )
            ) {

                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {

                val density = LocalDensity.current
                AndroidView(
                    factory = {
                            context ->

                        val preview = webView?.also { retainedView ->
                            (retainedView.parent as? ViewGroup)?.removeView(retainedView)
                            retainedView.onResume()
                            retainedView.post { retainedView.onResume() }
                        } ?: WebView(context)
                            .apply {

                                settings
                                    .javaScriptEnabled =
                                    true

                                settings
                                    .domStorageEnabled =
                                    true

                                settings
                                    .allowFileAccess =
                                    false

                                settings
                                    .allowContentAccess =
                                    false

                                setBackgroundColor(
                                    android.graphics
                                        .Color.BLACK
                                )

                                addJavascriptInterface(
                                    object {

                                        @JavascriptInterface
                                        fun getSketchCode():
                                                String {

                                            return pendingSketchCode
                                        }

                                        @JavascriptInterface
                                        fun getP5Version(): String = pendingP5Version

                                        @JavascriptInterface
                                        fun isP5SoundEnabled(): Boolean = pendingP5SoundEnabled

                                        @JavascriptInterface
                                        fun onError(
                                            message:
                                            String
                                        ) {

                                            Log.e(
                                                "P5JS",
                                                message
                                            )

                                            runOnUiThread {

                                                isError =
                                                    true

                                                appendConsole(
                                                    level =
                                                        ConsoleLevel.ERROR,
                                                    message =
                                                        message
                                                )

                                                showConsole =
                                                    true
                                            }
                                        }

                                        @JavascriptInterface
                                        fun onRuntimeError(
                                            message:
                                            String,
                                            line:
                                            Int
                                        ) {

                                            Log.e(
                                                "P5JS",
                                                "$message ($line)"
                                            )

                                            runOnUiThread {

                                                isError =
                                                    true

                                                appendConsole(
                                                    level =
                                                        ConsoleLevel.ERROR,
                                                    message =
                                                        message,
                                                    line =
                                                        (line - pendingMainLineOffset)
                                                            .takeIf { it > 0 }
                                                )

                                                showConsole =
                                                    true
                                            }
                                        }

                                        @JavascriptInterface
                                        fun onStatusChanged(
                                            status:
                                            String
                                        ) {

                                            runOnUiThread {

                                                if (status == "実行中") isPaused = false
                                                if (status == "一時停止中") isPaused = true
                                            }
                                        }

                                        @JavascriptInterface
                                        fun onScreenshotReady(dataUrl: String) {
                                            Thread {
                                                val saved = savePreviewMedia(
                                                    dataUrl = dataUrl,
                                                    mimeType = "image/png",
                                                    displayName = "EditKIRO_${System.currentTimeMillis()}.png",
                                                    video = false
                                                )
                                                runOnUiThread {
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        if (saved != null) uiText("スクリーンショットをPictures/EditKIROへ保存しました")
                                                        else uiText("スクリーンショットを保存できませんでした"),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }.start()
                                        }

                                        @JavascriptInterface
                                        fun onRecordingStatusChanged(recording: Boolean) {
                                            runOnUiThread {
                                                isPreviewRecording = recording
                                            }
                                        }

                                        @JavascriptInterface
                                        fun onRecordingReady(dataUrl: String, mimeType: String) {
                                            runOnUiThread { isPreviewRecording = false }
                                            Thread {
                                                val saved = savePreviewMedia(
                                                    dataUrl = dataUrl,
                                                    mimeType = mimeType.ifBlank { "video/webm" },
                                                    displayName = "EditKIRO_${System.currentTimeMillis()}.webm",
                                                    video = true
                                                )
                                                runOnUiThread {
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        if (saved != null) uiText("録画をMovies/EditKIROへ保存しました")
                                                        else uiText("録画を保存できませんでした"),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }.start()
                                        }

                                        @JavascriptInterface
                                        fun onCaptureError(message: String) {
                                            runOnUiThread {
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    message,
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    },

                                    "Android"
                                )

                                webChromeClient =
                                    object :
                                        WebChromeClient() {

                                        override fun onConsoleMessage(
                                            message:
                                            ConsoleMessage?
                                        ): Boolean {

                                            message
                                                ?.let {

                                                    val text =
                                                        it
                                                            .message()
                                                            .orEmpty()

                                                    val ignored =
                                                        text.contains(
                                                            "Ignored attempt to cancel a touchmove event",
                                                            ignoreCase =
                                                                true
                                                        )

                                                    if (!ignored) {

                                                        val level =
                                                            when (
                                                                it.messageLevel()
                                                            ) {

                                                                ConsoleMessage
                                                                    .MessageLevel
                                                                    .ERROR ->
                                                                    ConsoleLevel.ERROR

                                                                ConsoleMessage
                                                                    .MessageLevel
                                                                    .WARNING ->
                                                                    ConsoleLevel.WARNING

                                                                else ->
                                                                    ConsoleLevel.LOG
                                                            }

                                                        val line =
                                                            it.lineNumber()
                                                                .minus(pendingMainLineOffset)
                                                                .takeIf {
                                                                        number ->
                                                                    number > 0 &&
                                                                        it.sourceId()
                                                                            .contains(
                                                                                "sketch.js",
                                                                                ignoreCase =
                                                                                    true
                                                                            )
                                                                }

                                                        runOnUiThread {

                                                            appendConsole(
                                                                level =
                                                                    level,
                                                                message =
                                                                    text,
                                                                line =
                                                                    line
                                                            )

                                                            if (
                                                                level ==
                                                                ConsoleLevel.ERROR
                                                            ) {
                                                                isError =
                                                                    true

                                                                showConsole =
                                                                    true
                                                            }
                                                        }
                                                    }
                                                }

                                            return true
                                        }
                                    }

                                webViewClient = AssetWebClient(assets, assetStorage) { previewAssets }

                                setOnTouchListener {
                                        currentView,
                                        event ->

                                    if (
                                        event.actionMasked ==
                                        MotionEvent.ACTION_DOWN
                                    ) {

                                        focusManager
                                            .clearFocus(
                                                force =
                                                    true
                                            )

                                        currentView
                                            .requestFocus()
                                    }

                                    false
                                }

                                previewAssets = PreviewAssets(java.util.UUID.randomUUID().toString(), activeWork?.assets?.toMap().orEmpty())
                                pendingP5Version = normalizedP5Version(activeWork?.p5Version)
                                pendingP5SoundEnabled = activeWork?.p5SoundEnabled == true
                                pendingSketchCode =
                                    composeProjectSource(editorText, activeWork?.files.orEmpty())

                                pendingMainLineOffset =
                                    activeWork?.files?.values.orEmpty().sumOf { code ->
                                        code.count { it == '\n' } + 2
                                    }

                                loadUrl(
                                    previewUrl(previewAssets.token)
                                )

                                webView =
                                    this
                            }
                        PreviewWebViewHost(context, preview)
                    },

                    modifier = Modifier.fillMaxSize(),
                    update = { host -> host.setLogicalSize(logicalSize?.width, logicalSize?.height) },

                    onRelease = { releasedView ->
                        releasedView.preview.onPause()
                    }
                )

                    @Composable
                    fun PreviewActionButtons() {
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                if (fullscreen) {
                                    PreviewOverlayButton(
                                        iconRes = R.drawable.ic_rotate,
                                        description = uiText("描画の縦横を切り替える"),
                                        active = expandedCanvasSwapped,
                                        onClick = {
                                            if (isPreviewRecording) {
                                                Toast.makeText(this@MainActivity,
                                                    uiText("録画を停止してから描画の向きを変更してください"),
                                                    Toast.LENGTH_SHORT).show()
                                            } else {
                                                val swapped = !expandedCanvasSwapped
                                                webView?.evaluateJavascript(
                                                    "window.__editKiroSetCanvasSwapped?.($swapped)"
                                                ) { result ->
                                                    if (result == "true") expandedCanvasSwapped = swapped
                                                    else Toast.makeText(this@MainActivity,
                                                        uiText("描画の向きを変更できませんでした"),
                                                        Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            previewActionsExpanded = false
                                        }
                                    )
                                }
                                PreviewOverlayButton(
                                    iconRes = R.drawable.ic_camera,
                                    description = uiText("プレビューをスクリーンショット"),
                                    onClick = {
                                        webView?.evaluateJavascript(
                                            "window.__editKiroCaptureScreenshot?.()",
                                            null
                                        )
                                        previewActionsExpanded = false
                                    }
                                )

                                PreviewOverlayButton(
                                    iconRes = if (isPreviewRecording) {
                                        R.drawable.ic_stop
                                    } else {
                                        R.drawable.ic_record
                                    },
                                    description = if (isPreviewRecording) uiText("録画を停止") else uiText("プレビューを録画"),
                                    active = isPreviewRecording,
                                    onClick = {
                                        webView?.evaluateJavascript(
                                            if (isPreviewRecording) {
                                                "window.__editKiroStopRecording?.()"
                                            } else {
                                                "window.__editKiroStartRecording?.()"
                                            },
                                            null
                                        )
                                        previewActionsExpanded = false
                                    }
                                )

                                if (showExpandControl) {
                                    PreviewOverlayButton(
                                        iconRes = R.drawable.ic_fullscreen,
                                        description = uiText("プレビューを全画面表示"),
                                        onClick = {
                                            previewActionsExpanded = false
                                            focusManager.clearFocus(force = true)
                                            showExpandedPreview = true
                                        }
                                    )
                                } else {
                                    PreviewOverlayButton(
                                        iconRes = R.drawable.ic_close,
                                        description = uiText("全画面表示を閉じる"),
                                        onClick = {
                                            previewActionsExpanded = false
                                            if (isPreviewRecording) {
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    uiText("録画を停止してから全画面表示を閉じてください"),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                showExpandedPreview = false
                                            }
                                        }
                                    )
                                }
                            }
                    }
                    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
                    val availableWidth = maxWidth - if (fullscreen) with(density) {
                        (WindowInsets.safeDrawing.getLeft(density, direction) +
                            WindowInsets.safeDrawing.getRight(density, direction)).toDp()
                    } else 0.dp
                    // A separate popup can extend outside a narrow preview without squeezing buttons.
                    val usePopup = availableWidth < (if (fullscreen) 248.dp else 201.dp) || maxHeight < 60.dp
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .then(
                                if (fullscreen) {
                                    Modifier.windowInsetsPadding(
                                        WindowInsets.safeDrawing.only(
                                            WindowInsetsSides.Top +
                                                WindowInsetsSides.Horizontal
                                        )
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        AnimatedVisibility(
                            visible = previewActionsExpanded && !usePopup,
                            enter = fadeIn(tween(130)) + expandHorizontally(
                                animationSpec = tween(210, easing = FastOutSlowInEasing),
                                expandFrom = Alignment.End
                            ),
                            exit = fadeOut(tween(100)) + shrinkHorizontally(
                                animationSpec = tween(170, easing = FastOutSlowInEasing),
                                shrinkTowards = Alignment.End
                            )
                        ) {
                            PreviewActionButtons()
                        }

                        if (previewActionsExpanded && usePopup) {
                            androidx.compose.ui.window.Popup(
                                alignment = Alignment.TopEnd,
                                offset = androidx.compose.ui.unit.IntOffset(0, with(density) { 48.dp.roundToPx() }),
                                onDismissRequest = { previewActionsExpanded = false },
                                properties = androidx.compose.ui.window.PopupProperties(focusable = true)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = colors.surface,
                                    border = BorderStroke(1.dp, colors.outlineVariant),
                                    shadowElevation = 6.dp,
                                    modifier = Modifier.widthIn(max = 300.dp)
                                ) {
                                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp)) {
                                        PreviewActionButtons()
                                    }
                                }
                            }
                        }

                        PreviewOverlayButton(
                            iconRes = R.drawable.ic_more_horizontal,
                            description = if (previewActionsExpanded) uiText("プレビュー操作を閉じる") else uiText("プレビュー操作を開く"),
                            active = isPreviewRecording,
                            onClick = {
                                previewActionsExpanded = !previewActionsExpanded
                            }
                        )
                    }
                }
            }
        }

        @Composable
        fun MainPreviewArea(
            modifier: Modifier
        ) {
            if (showExpandedPreview) {
                Box(modifier = modifier)
            } else {
                PreviewArea(
                    modifier = modifier.onSizeChanged { size ->
                        if (size.width > 0 && size.height > 0) {
                            normalPreviewSize = size
                        }
                    }
                )
            }
        }

        @Composable
        fun ControlBar() {

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(
                            50.dp
                        )
                        .padding(
                            vertical =
                                6.dp
                        ),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Row(
                    modifier =
                        Modifier
                            .height(
                                38.dp
                            )
                            .border(
                                width =
                                    1.dp,

                                color =
                                    if (isError) {
                                        colors.error
                                    } else {
                                        colors
                                            .outlineVariant
                                    },

                                shape =
                                    RoundedCornerShape(
                                        12.dp
                                    )
                            )
                            .padding(
                                start =
                                    10.dp,

                                end =
                                    2.dp
                            ),

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Box(
                        modifier =
                            Modifier
                                .size(
                                    6.dp
                                )
                                .clip(
                                    CircleShape
                                )
                                .background(
                                    when {

                                        isError ->
                                            colors.error

                                        isPaused ->
                                            colors.outline

                                        else ->
                                            colors.primary
                                    }
                                )
                    )

                    Spacer(
                        Modifier.width(
                            6.dp
                        )
                    )

                    Text(
                        text =
                            when {

                                isError ->
                                    "ERR"

                                isPaused ->
                                    "PAUSE"

                                else ->
                                    "RUN"
                            },

                        fontSize =
                            10.sp,

                        fontWeight =
                            FontWeight.SemiBold,

                        color =
                            if (isError) {
                                colors.error
                            } else {
                                colors
                                    .onSurfaceVariant
                            },

                        letterSpacing =
                            0.6.sp
                    )

                    IconButton(
                        onClick = {

                            isPaused =
                                !isPaused

                            webView
                                ?.evaluateJavascript(
                                    if (isPaused) {
                                        "pauseSketch()"
                                    } else {
                                        "resumeSketch()"
                                    },
                                    null
                                )
                        },

                        modifier =
                            Modifier.size(
                                34.dp
                            )
                    ) {

                        if (isPaused) {

                            Icon(
                                painter =
                                    painterResource(
                                        R.drawable.ic_play
                                    ),
                                contentDescription =
                                    uiText("再生"),
                                tint =
                                    colors.onSurface,
                                modifier =
                                    Modifier.size(
                                        19.dp
                                    )
                            )

                        } else {

                            Icon(
                                painter =
                                    painterResource(
                                        R.drawable.ic_pause
                                    ),
                                contentDescription =
                                    uiText("一時停止"),
                                tint =
                                    colors.onSurface,
                                modifier =
                                    Modifier.size(
                                        19.dp
                                    )
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            runSketch()
                        },

                        modifier =
                            Modifier.size(
                                34.dp
                            )
                    ) {

                        Icon(
                            painter =
                                painterResource(
                                    R.drawable.ic_reload
                                ),
                            contentDescription =
                                uiText("再読み込み"),
                            tint =
                                colors.onSurface,
                            modifier =
                                Modifier.size(
                                    20.dp
                                )
                        )
                    }
                }

                Spacer(
                    Modifier.width(
                        6.dp
                    )
                )

                val consoleHasError =
                    consoleEntries.any {
                        it.level ==
                            ConsoleLevel.ERROR
                    }

                Box(
                    modifier =
                        Modifier
                            .height(
                                34.dp
                            )
                            .widthIn(
                                min =
                                    44.dp
                            )
                            .clip(
                                RoundedCornerShape(
                                    10.dp
                                )
                            )
                            .border(
                                width =
                                    1.dp,
                                color =
                                    if (consoleHasError) {
                                        colors.error
                                    } else {
                                        colors.outlineVariant
                                    },
                                shape =
                                    RoundedCornerShape(
                                        10.dp
                                    )
                            )
                            .clickable {
                                showConsole =
                                    !showConsole
                            }
                            .padding(
                                horizontal =
                                    8.dp
                            ),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.Center
                    ) {

                        Icon(
                            painter =
                                painterResource(
                                    R.drawable.ic_log
                                ),
                            contentDescription =
                                uiText("ログ"),
                            tint =
                                if (consoleHasError) {
                                    colors.error
                                } else {
                                    colors.onSurfaceVariant
                                },
                            modifier =
                                Modifier.size(
                                    17.dp
                                )
                        )

                        if (
                            consoleEntries.isNotEmpty()
                        ) {

                            Spacer(
                                Modifier.width(
                                    5.dp
                                )
                            )

                            Text(
                                text =
                                    if (
                                        consoleEntries.size >
                                        99
                                    ) {
                                        "99+"
                                    } else {
                                        consoleEntries
                                            .size
                                            .toString()
                                    },
                                fontSize =
                                    9.sp,
                                fontWeight =
                                    FontWeight.Medium,
                                color =
                                    if (consoleHasError) {
                                        colors.error
                                    } else {
                                        colors.outline
                                    }
                            )
                        }
                    }
                }

                Spacer(
                    Modifier.weight(
                        1f
                    )
                )

                Row(
                    modifier =
                        Modifier
                            .height(
                                38.dp
                            )
                            .border(
                                width =
                                    1.dp,

                                color =
                                    colors
                                        .outlineVariant,

                                shape =
                                    RoundedCornerShape(
                                        12.dp
                                    )
                            )
                            .padding(
                                horizontal =
                                    2.dp
                            ),

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    IconButton(
                        onClick = {
                            restoreCurrentWork()
                        },

                        modifier =
                            Modifier.size(
                                34.dp
                            )
                    ) {

                        Icon(
                            painter =
                                painterResource(
                                    R.drawable.ic_restore
                                ),
                            contentDescription =
                                uiText("保存済み状態に戻す"),
                            tint =
                                colors.onSurface,
                            modifier =
                                Modifier.size(
                                    20.dp
                                )
                        )
                    }

                    IconButton(
                        onClick = {
                            saveCurrentWork()
                        },

                        modifier =
                            Modifier.size(
                                34.dp
                            )
                    ) {

                        Icon(
                            painter =
                                painterResource(
                                    id =
                                        R.drawable.ic_save
                                ),

                            contentDescription =
                                uiText("作品を保存"),

                            tint =
                                if (hasUnsavedChanges) {
                                    colors.primary
                                } else {
                                    colors.onSurface
                                },

                            modifier =
                                Modifier.size(
                                    20.dp
                                )
                        )
                    }
                }
            }
        }

        @Composable
        fun ConsolePanel(
            modifier: Modifier =
                Modifier
        ) {

            Card(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .heightIn(
                            min =
                                96.dp,
                            max =
                                if (isLandscape) {
                                    150.dp
                                } else {
                                    170.dp
                                }
                        )
                        .border(
                            width =
                                1.dp,
                            color =
                                colors.outlineVariant,
                            shape =
                                RoundedCornerShape(
                                    16.dp
                                )
                        ),
                shape =
                    RoundedCornerShape(
                        16.dp
                    ),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            colors.surface
                    ),
                elevation =
                    CardDefaults.cardElevation(
                        defaultElevation =
                            0.dp
                    )
            ) {

                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(
                                    38.dp
                                )
                                .padding(
                                    start =
                                        12.dp,
                                    end =
                                        4.dp
                                ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Text(
                            text =
                                "CONSOLE",
                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall,
                            fontWeight =
                                FontWeight.SemiBold,
                            letterSpacing =
                                0.7.sp,
                            color =
                                colors.onSurfaceVariant
                        )

                        val errorCount =
                            consoleEntries.count {
                                it.level ==
                                    ConsoleLevel.ERROR
                            }

                        if (errorCount > 0) {

                            Spacer(
                                Modifier.width(
                                    8.dp
                                )
                            )

                            Text(
                                text =
                                    "$errorCount ERR",
                                style =
                                    MaterialTheme
                                        .typography
                                        .labelSmall,
                                color =
                                    colors.error,
                                fontFamily =
                                    codeFontFamily
                            )
                        }

                        Spacer(
                            Modifier.weight(
                                1f
                            )
                        )

                        TextButton(
                            onClick = {
                                consoleEntries.clear()
                            },
                            contentPadding =
                                PaddingValues(
                                    horizontal =
                                        8.dp,
                                    vertical =
                                        0.dp
                                )
                        ) {

                            Text(
                                text =
                                    "CLEAR",
                                fontSize =
                                    10.sp
                            )
                        }

                        TextButton(
                            onClick = {
                                showConsole =
                                    false
                            },
                            contentPadding =
                                PaddingValues(
                                    horizontal =
                                        8.dp,
                                    vertical =
                                        0.dp
                                )
                        ) {

                            Text(
                                text =
                                    "CLOSE",
                                fontSize =
                                    10.sp
                            )
                        }
                    }

                    HorizontalDivider(
                        color =
                            colors.outlineVariant
                    )

                    if (
                        consoleEntries.isEmpty()
                    ) {

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(
                                        58.dp
                                    ),
                            contentAlignment =
                                Alignment.CenterStart
                        ) {

                            Text(
                                text =
                                    uiText("ログはありません"),
                                modifier =
                                    Modifier.padding(
                                        horizontal =
                                            12.dp
                                    ),
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall,
                                color =
                                    colors.onSurfaceVariant,
                                fontFamily =
                                    codeFontFamily
                            )
                        }

                    } else {

                        LazyColumn(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(
                                        max =
                                            if (isLandscape) {
                                                112.dp
                                            } else {
                                                132.dp
                                            }
                                    ),
                            contentPadding =
                                PaddingValues(
                                    vertical =
                                        4.dp
                                )
                        ) {

                            items(
                                items =
                                    consoleEntries,
                                key = {
                                    it.id
                                }
                            ) { entry ->

                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                enabled =
                                                    entry.line != null
                                            ) {
                                                entry.line
                                                    ?.let {
                                                        jumpToLine(
                                                            it
                                                        )
                                                    }
                                            }
                                            .padding(
                                                horizontal =
                                                    12.dp,
                                                vertical =
                                                    6.dp
                                            ),
                                    verticalAlignment =
                                        Alignment.Top
                                ) {

                                    Text(
                                        text =
                                            when (
                                                entry.level
                                            ) {
                                                ConsoleLevel.ERROR ->
                                                    "ERR"

                                                ConsoleLevel.WARNING ->
                                                    "WARN"

                                                ConsoleLevel.LOG ->
                                                    ">"
                                            },
                                        modifier =
                                            Modifier.width(
                                                38.dp
                                            ),
                                        fontFamily =
                                            codeFontFamily,
                                        fontSize =
                                            11.sp,
                                        fontWeight =
                                            FontWeight.SemiBold,
                                        color =
                                            when (
                                                entry.level
                                            ) {
                                                ConsoleLevel.ERROR ->
                                                    colors.error

                                                ConsoleLevel.WARNING ->
                                                    colors.tertiary

                                                ConsoleLevel.LOG ->
                                                    colors.onSurfaceVariant
                                            }
                                    )

                                    Text(
                                        text =
                                            buildString {
                                                append(
                                                    entry.message
                                                )

                                                if (
                                                    entry.count > 1
                                                ) {
                                                    append(
                                                        "  ×${entry.count}"
                                                    )
                                                }

                                                entry.line
                                                    ?.let {
                                                        append(
                                                            "  ·  L$it"
                                                        )
                                                    }
                                            },
                                        modifier =
                                            Modifier.weight(
                                                1f
                                            ),
                                        fontFamily =
                                            codeFontFamily,
                                        fontSize =
                                            11.sp,
                                        lineHeight =
                                            15.sp,
                                        color =
                                            colors.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun EditorAccessoryBar(
            modifier: Modifier = Modifier
        ) {
            fun applyEdit(value: TextFieldValue) {
                applyEditorChange(value)
                editorFocusRequester.requestFocus()
            }

            @Composable
            fun AccessoryKey(
                label: String,
                description: String,
                width: Int = 40,
                command: Boolean = false,
                enabled: Boolean = true,
                onClick: () -> Unit
            ) {
                Surface(
                    onClick = onClick,
                    enabled = enabled,
                    modifier = Modifier
                        .width(
                            (if (compactAccessoryKeys) {
                                (width - 6).coerceAtLeast(34)
                            } else {
                                width
                            }).dp
                        )
                        .height(if (compactAccessoryKeys) 32.dp else 38.dp)
                        .semantics {
                            contentDescription = description
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = if (command) {
                        colors.secondaryContainer
                    } else {
                        colors.surface
                    },
                    contentColor = if (command) {
                        colors.onSecondaryContainer.copy(
                            alpha = if (enabled) 1f else 0.38f
                        )
                    } else {
                        colors.onSurface.copy(
                            alpha = if (enabled) 1f else 0.38f
                        )
                    },
                    border = BorderStroke(
                        1.dp,
                        if (command) colors.secondary.copy(alpha = 0.42f)
                        else colors.outlineVariant
                    ),
                    shadowElevation = 1.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontFamily = codeFontFamily,
                            fontSize = if (compactAccessoryKeys) {
                                if (label.length > 3) 10.sp else 12.sp
                            } else if (label.length > 3) 11.sp else 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            @Composable
            fun KeyDivider() {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .width(1.dp)
                        .height(26.dp)
                        .background(colors.outlineVariant)
                )
            }

            val moveLeft = {
                    val target = if (!editorValue.selection.collapsed) {
                        editorValue.selection.min
                    } else {
                        (editorValue.selection.start - 1).coerceAtLeast(0)
                    }
                    applyEdit(editorValue.copy(selection = TextRange(target)))
                }
            val moveRight = {
                    val target = if (!editorValue.selection.collapsed) {
                        editorValue.selection.max
                    } else {
                        (editorValue.selection.end + 1).coerceAtMost(editorValue.text.length)
                    }
                    applyEdit(editorValue.copy(selection = TextRange(target)))
                }

            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                color = colors.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(
                            horizontal = 8.dp,
                            vertical = if (compactAccessoryKeys) 5.dp else 7.dp
                        ),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(if (compactAccessoryKeys) 32.dp else 38.dp),
                        shape = RoundedCornerShape(9.dp),
                        color = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_keyboard_symbols),
                                contentDescription = uiText("編集キー"),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    KeyDivider()
                    AccessoryKey("↶", uiText("元に戻す"), command = true, enabled = undoStack.isNotEmpty()) {
                        undoEditorChange()
                    }
                    AccessoryKey("↷", uiText("やり直す"), command = true, enabled = redoStack.isNotEmpty()) {
                        redoEditorChange()
                    }
                    AccessoryKey("⌕", uiText("検索と置換"), command = true) {
                        showSearchDialog = true
                    }
                    AccessoryKey("≡", uiText("コードを整形"), command = true) {
                        val formatted = formatJavaScript(editorText)
                        if (formatted != editorText) {
                            applyEdit(TextFieldValue(formatted, TextRange(0)))
                        }
                    }

                    if (showAccessoryNavigation) {
                        KeyDivider()
                        AccessoryKey("TAB", uiText("インデント"), 54, true) {
                            applyEdit(changeLineIndent(editorValue, true))
                        }
                        AccessoryKey("⇤", uiText("インデントを戻す"), command = true) {
                            applyEdit(changeLineIndent(editorValue, false))
                        }
                        AccessoryKey("←", uiText("左へ移動"), onClick = moveLeft)
                        AccessoryKey("↑", uiText("上へ移動")) {
                            applyEdit(moveCursorVertically(editorValue, -1))
                        }
                        AccessoryKey("↓", uiText("下へ移動")) {
                            applyEdit(moveCursorVertically(editorValue, 1))
                        }
                        AccessoryKey("→", uiText("右へ移動"), onClick = moveRight)
                    }

                    if (showAccessorySymbols) {
                        KeyDivider()
                        listOf(
                            Triple("{}", "{", "}"), Triple("()", "(", ")"),
                            Triple("[]", "[", "]"), Triple("\"\"", "\"", "\""),
                            Triple("''", "'", "'")
                        ).forEach { (label, opening, closing) ->
                            AccessoryKey(label, uiText("%s を入力", label)) {
                                applyEdit(insertAtSelection(editorValue, opening, closing))
                            }
                        }
                        listOf(";", "=", ",", ".").forEach { symbol ->
                            AccessoryKey(symbol, uiText("%s を入力", symbol)) {
                                applyEdit(insertAtSelection(editorValue, symbol))
                            }
                        }
                        AccessoryKey("//", uiText("コメントを入力"), 44) {
                            applyEdit(insertAtSelection(editorValue, "// "))
                        }
                    }
                }
            }
        }

        val editorSuggestions = remember(editorValue.text, editorValue.selection, editorFocused, codeCompletion) {
            if (!editorFocused || !codeCompletion) emptyList() else editorCompletions(editorValue)
        }

        @Composable
        fun CompletionBar(
            modifier: Modifier = Modifier
        ) {
            val suggestions = editorSuggestions
            AnimatedVisibility(
                visible = editorFocused && codeCompletion && suggestions.isNotEmpty(),
                modifier = modifier,
                enter = fadeIn(tween(100)) + expandVertically(tween(130)),
                exit = fadeOut(tween(80)) + shrinkVertically(tween(110))
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surfaceContainer,
                    border = BorderStroke(1.dp, colors.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        suggestions.forEach { suggestion ->
                            SuggestionChip(
                                onClick = {
                                    val end = editorValue.selection.start
                                    val start = (end - completionPrefix(editorValue).length).coerceAtLeast(0)
                                    applyEditorChange(TextFieldValue(
                                        editorText.replaceRange(start, end, suggestion),
                                        TextRange(start + suggestion.length)
                                    ))
                                    editorFocusRequester.requestFocus()
                                },
                                label = {
                                    Text(
                                        suggestion,
                                        fontFamily = codeFontFamily,
                                        fontSize = 12.sp
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        @Composable
        fun EditorArea(
            modifier: Modifier
        ) {

            val darkEditorTheme =
                colors.surface.luminance() < 0.5f

            val editorErrorLines by remember {
                derivedStateOf {
                    consoleEntries.asSequence()
                        .filter { it.level == ConsoleLevel.ERROR }
                        .mapNotNull { it.line }
                        .toSet()
                }
            }

            val javascriptHighlighter =
                remember(darkEditorTheme, editorErrorLines) {
                    JavaScriptHighlighter(
                        darkTheme = darkEditorTheme,
                        errorLines = editorErrorLines
                    )
                }

            var editorTextLayout by remember {
                mutableStateOf<TextLayoutResult?>(null)
            }

            val logicalLineStarts = remember(editorText) {
                buildList {
                    add(0)
                    editorText.forEachIndexed { index, character ->
                        if (character == '\n') add(index + 1)
                    }
                }
            }

            val lineNumberDigits = logicalLineStarts.size.toString().length

            val gutterText = remember(
                editorTextLayout,
                logicalLineStarts,
                lineNumberDigits,
                editorErrorLines,
                colors.error
            ) {
                val visualStarts = editorTextLayout?.takeIf {
                    it.layoutInput.text.text == editorText
                }?.let { layout ->
                    List(layout.lineCount) { visualLine ->
                        layout.getLineStart(visualLine)
                    }
                } ?: logicalLineStarts

                AnnotatedString.Builder().apply {
                    visualStarts.forEachIndexed { visualIndex, offset ->
                        val logicalIndex = logicalLineStarts.binarySearch(offset).let {
                            if (it >= 0) it else -it - 2
                        }.coerceAtLeast(0)
                        val startsLogicalLine =
                            logicalLineStarts.getOrNull(logicalIndex) == offset

                        if (startsLogicalLine) {
                            val numberStart = length
                            append(
                                (logicalIndex + 1)
                                    .toString()
                                    .padStart(lineNumberDigits)
                            )
                            if (logicalIndex + 1 in editorErrorLines) {
                                addStyle(
                                    SpanStyle(
                                        color = colors.error,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    numberStart,
                                    length
                                )
                            }
                        } else {
                            append(" ".repeat(lineNumberDigits))
                        }

                        if (visualIndex < visualStarts.lastIndex) {
                            append('\n')
                        }
                    }
                }.toAnnotatedString()
            }

            Card(
                modifier =
                    modifier
                        .border(
                            width =
                                1.dp,

                            color =
                                if (
                                    editorFocused
                                ) {
                                    colors.primary
                                } else {
                                    colors
                                        .outlineVariant
                                },

                            shape =
                                RoundedCornerShape(
                                    18.dp
                                )
                        ),

                shape =
                    RoundedCornerShape(
                        18.dp
                    ),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            colors.surface
                    ),

                elevation =
                    CardDefaults.cardElevation(
                        defaultElevation =
                            0.dp
                    )
            ) {

                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val editorScrollState = rememberScrollState()
                    val contentMinHeight = (maxHeight - 32.dp).coerceAtLeast(0.dp)
                    val gutterWidth = (
                        lineNumberDigits * editorFontSize * LocalDensity.current.fontScale * 0.72f + 18f
                    ).dp
                    val gutterDividerColor = colors.outlineVariant

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(editorScrollState)
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        if (showLineNumbers) {
                            Text(
                                text = gutterText,
                                modifier = Modifier
                                    .width(gutterWidth)
                                    .clearAndSetSemantics { }
                                    .heightIn(min = contentMinHeight)
                                    .drawBehind {
                                        drawLine(
                                            color = gutterDividerColor,
                                            start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                            end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }
                                    .padding(end = 9.dp),
                                color = colors.onSurfaceVariant.copy(alpha = 0.72f),
                                fontFamily = codeFontFamily,
                                fontSize = editorFontSize.sp,
                                lineHeight = (editorFontSize * 1.55f).sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        }

                        BasicTextField(
                            value = editorValue,
                            onValueChange = {
                                applyEditorChange(
                                    if (autoIndent && editorValue.composition == null && it.composition == null) {
                                        applyAutomaticIndent(editorValue, it)
                                    } else {
                                        it
                                    }
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = contentMinHeight)
                                .padding(
                                    start = if (showLineNumbers) 12.dp else 16.dp,
                                    end = 16.dp
                                )
                                .focusRequester(editorFocusRequester)
                                .onFocusChanged {
                                    editorFocused = it.isFocused
                                },
                            textStyle = TextStyle(fontFeatureSettings = fontFeatures, 
                                fontFamily = codeFontFamily,
                                fontSize = editorFontSize.sp,
                                lineHeight = (editorFontSize * 1.55f).sp,
                                color = colors.onSurface
                            ),
                            visualTransformation = javascriptHighlighter,
                            onTextLayout = { editorTextLayout = it },
                            cursorBrush = SolidColor(colors.primary)
                        )
                    }
                }
            }
        }

        val selectedFolderName =
            remember(
                selectedFolderUri,
                appLanguage,
                showSettings
            ) {
                if (showSettings) {
                    getFolderName(selectedFolderUri)
                } else {
                    ""
                }
            }

        Scaffold(
            containerColor =
                colors.background,

            contentWindowInsets =
                appInsets
        ) { innerPadding ->

            if (showSettings) {

                SettingsScreen(
                    onImportFont = { fontPicker.launch(arrayOf("*/*")) },
                    modifier =
                        Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),

                    onBack = {
                        showSettings =
                            false
                    },

                    themeMode =
                        themeMode,

                    onThemeModeChange =
                        onThemeModeChange,

                    folderName =
                        selectedFolderName,

                    onChooseFolder = {

                        updateCurrentWork()
                        if (saveStore()) {
                            lastSavedText = editorText
                        }

                        folderLauncher.launch(
                            selectedFolderUri
                        )
                    },

                    autoRun =
                        autoRun,

                    onAutoRunChange = {

                        autoRun =
                            it

                        preferences.edit()
                            .putBoolean(
                                autoRunKey,
                                it
                            )
                            .apply()
                    },

                    hideEditingPreview = hideEditingPreview,
                    onHideEditingPreviewChange = {
                        hideEditingPreview = it
                        preferences.edit().putBoolean(hideEditingPreviewKey, it).apply()
                    },
                    compactPreview =
                        compactPreview,

                    onCompactPreviewChange = {

                        compactPreview =
                            it

                        preferences.edit()
                            .putBoolean(
                                compactPreviewKey,
                                it
                            )
                            .apply()
                    },

                    showResizeHandles =
                        showResizeHandles,

                    onShowResizeHandlesChange = {
                        showResizeHandles = it
                        preferences.edit()
                            .putBoolean(resizeHandlesVisibleKey, it)
                            .apply()
                    },

                    landscapePreviewFraction =
                        landscapePreviewFraction,

                    onLandscapePreviewFractionChange = { fraction ->
                        val normalized = LANDSCAPE_PREVIEW_SPLITS.minByOrNull {
                            kotlin.math.abs(it - fraction)
                        } ?: 0.5f
                        if (normalized != landscapePreviewFraction) {
                            landscapePreviewFraction = normalized
                            preferences.edit()
                                .putFloat(landscapePreviewSplitKey, normalized)
                                .apply()
                            hapticFeedback.performHapticFeedback(
                                HapticFeedbackType.LongPress
                            )
                        }
                    },

                    preserveExpandedPreview =
                        preserveExpandedPreview,

                    onPreserveExpandedPreviewChange = {

                        preserveExpandedPreview =
                            it

                        preferences.edit()
                            .putBoolean(
                                preserveExpandedPreviewKey,
                                it
                            )
                            .apply()
                    },

                    showStatusBar =
                        showStatusBar,

                    statusBarForcedHidden =
                        isLandscape,

                    landscapeUseCutout = landscapeUseCutout,
                    onLandscapeUseCutoutChange = {
                        landscapeUseCutout = it
                        preferences.edit().putBoolean(landscapeCutoutKey, it).apply()
                    },

                    onShowStatusBarChange = {

                        showStatusBar =
                            it

                        preferences.edit()
                            .putBoolean(
                                statusBarKey,
                                it
                            )
                            .apply()
                    },

                    manualRotation =
                        manualRotation,

                    onManualRotationChange = {

                        manualRotation =
                            it

                        preferences.edit()
                            .putBoolean(
                                manualRotationKey,
                                it
                            )
                            .apply()
                    },

                    editorFontSize =
                        editorFontSize,

                    onEditorFontSizeChange = {

                        editorFontSize =
                            it

                        preferences.edit()
                            .putFloat(
                                editorFontKey,
                                it
                            )
                            .apply()
                    },

                    autoIndent = autoIndent,

                    onAutoIndentChange = {
                        autoIndent = it
                        preferences.edit()
                            .putBoolean(autoIndentKey, it)
                            .apply()
                    },

                    showLineNumbers = showLineNumbers,

                    onShowLineNumbersChange = {
                        showLineNumbers = it
                        preferences.edit()
                            .putBoolean(lineNumbersKey, it)
                            .apply()
                    },

                    showEditorAccessoryBar = showEditorAccessoryBar,

                    onShowEditorAccessoryBarChange = {
                        showEditorAccessoryBar = it
                        preferences.edit()
                            .putBoolean(editorAccessoryBarKey, it)
                            .apply()
                    },

                    codeCompletion = codeCompletion,
                    onCodeCompletionChange = {
                        codeCompletion = it
                        preferences.edit().putBoolean(codeCompletionKey, it).apply()
                    },

                    showAccessoryNavigation = showAccessoryNavigation,
                    onShowAccessoryNavigationChange = {
                        showAccessoryNavigation = it
                        preferences.edit().putBoolean(accessoryNavigationKey, it).apply()
                    },

                    showAccessorySymbols = showAccessorySymbols,
                    onShowAccessorySymbolsChange = {
                        showAccessorySymbols = it
                        preferences.edit().putBoolean(accessorySymbolsKey, it).apply()
                    },

                    compactAccessoryKeys = compactAccessoryKeys,
                    onCompactAccessoryKeysChange = {
                        compactAccessoryKeys = it
                        preferences.edit().putBoolean(compactAccessoryKeysKey, it).apply()
                    },

                    draftRecovery =
                        draftRecovery,

                    onDraftRecoveryChange = {

                        draftRecovery =
                            it

                        preferences.edit()
                            .putBoolean(
                                draftRecoveryKey,
                                it
                            )
                            .apply()

                        if (!it) {
                            clearDraftSnapshot()
                        }
                    },

                    p5Username = p5Username,
                    onImportP5 = {
                        p5Error = null
                        showP5Import = true
                    },

                    onImportJs = {

                        importJsLauncher.launch(
                            arrayOf(
                                "application/javascript",
                                "text/javascript",
                                "text/plain",
                                "application/octet-stream"
                            )
                        )
                    },

                    onExportJs = {

                        exportJsLauncher.launch(
                            safeJsFileName(
                                activeWork
                                    ?.title
                                    ?: "sketch"
                            )
                        )
                    },

                    onExportBackup = {
                        updateCurrentWork()
                        exportBackupLauncher.launch("Edit-KIRO-backup.zip")
                    },

                    onImportBackup = {
                        importBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    }
                )

            } else {

                BoxWithConstraints(
                    Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
                        .fillMaxSize().background(colors.background)
                        .imePadding()
                ) {
                // Reserve editing space even for tall works and when the keyboard is open.
                val hasVisibleCompletions = editorSuggestions.isNotEmpty()
                val chromeHeight = (if (editorFocused) 0.dp else 108.dp) +
                    (if (showResizeHandles && !editorFocused) 28.dp else 0.dp) +
                    (if (showConsole) 176.dp else 0.dp) +
                    (if (editorFocused && showEditorAccessoryBar) 60.dp else 8.dp) +
                    (if (hasVisibleCompletions) 48.dp else 0.dp)
                val availableForEditing = (maxHeight - chromeHeight).coerceAtLeast(0.dp)
                // Editing uses a shallow, full-width viewport, without changing the saved work ratio.
                val useWideEditingPreview = editorFocused && compactPreview
                val previewHeightLimit = if (editorFocused && compactPreview) {
                    availableForEditing * 0.5f
                } else {
                    availableForEditing * 0.65f
                }
                val availablePreviewWidth = (maxWidth - 24.dp).value
                val portraitHeight by animateFloatAsState(
                    targetValue = portraitPreviewHeight(
                        availablePreviewWidth, previewHeightLimit.value, workPreviewRatio,
                        hidden = hideEditingPreview && editorFocused && !isLandscape,
                        compact = useWideEditingPreview
                    ),
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                    label = "boundedPortraitPreview"
                )
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        colors.background,
                                        // Match the inset background in explicit dark mode.
                                        // Keep the existing light / Material You gradient.
                                        if (themeMode == AppThemeMode.DARK) colors.background
                                        else colors.surface
                                    )
                                )
                            )
                ) {

                    AnimatedVisibility(
                        visible =
                            !isLandscape &&
                                    !editorFocused,

                        enter =
                            fadeIn(
                                tween(180)
                            ) +
                                    expandVertically(
                                        animationSpec =
                                            tween(
                                                220,
                                                easing =
                                                    FastOutSlowInEasing
                                            ),

                                        expandFrom =
                                            Alignment.Top
                                    ),

                        exit =
                            fadeOut(
                                tween(140)
                            ) +
                                    shrinkVertically(
                                        animationSpec =
                                            tween(
                                                220,
                                                easing =
                                                    FastOutSlowInEasing
                                            ),

                                        shrinkTowards =
                                            Alignment.Top
                                    )
                    ) {

                        WorkBar()
                    }

                    if (isLandscape) {

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(
                                        start =
                                            12.dp,

                                        end =
                                            12.dp,

                                        top =
                                            8.dp,

                                        bottom =
                                            8.dp
                                    ),

                            horizontalArrangement = Arrangement.Start
                        ) {

                            BoxWithConstraints(
                                modifier =
                                    Modifier
                                        .weight(
                                            animatedLandscapePreviewFraction
                                        )
                                        .fillMaxHeight()
                            ) {

                                val previewSize = fitPreviewSize(maxWidth.value, maxHeight.value, workPreviewRatio)

                                MainPreviewArea(
                                    modifier =
                                        Modifier
                                            .size(previewSize.width.dp, previewSize.height.dp)
                                            .align(
                                                Alignment.Center
                                            )
                                )
                            }

                            if (showResizeHandles) {
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .fillMaxHeight()
                                    .pointerInput(Unit) {
                                        var accumulated = 0f
                                        var splitIndex = 1
                                        val threshold = 44.dp.toPx()
                                        detectHorizontalDragGestures(
                                            onDragStart = {
                                                accumulated = 0f
                                                splitIndex = LANDSCAPE_PREVIEW_SPLITS
                                                    .indices
                                                    .minByOrNull { index ->
                                                        kotlin.math.abs(
                                                            LANDSCAPE_PREVIEW_SPLITS[index] -
                                                                currentLandscapePreviewFraction.value
                                                        )
                                                    } ?: 1
                                                showLandscapeSplitLabel = true
                                            },
                                            onDragEnd = {
                                                showLandscapeSplitLabel = false
                                                preferences.edit()
                                                    .putFloat(
                                                        landscapePreviewSplitKey,
                                                        currentLandscapePreviewFraction.value
                                                    )
                                                    .apply()
                                            },
                                            onDragCancel = {
                                                showLandscapeSplitLabel = false
                                            },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                accumulated += dragAmount
                                                if (kotlin.math.abs(accumulated) >= threshold) {
                                                    val nextIndex = (
                                                        splitIndex + if (accumulated > 0f) 1 else -1
                                                    ).coerceIn(0, LANDSCAPE_PREVIEW_SPLITS.lastIndex)
                                                    if (nextIndex != splitIndex) {
                                                        splitIndex = nextIndex
                                                        landscapePreviewFraction =
                                                            LANDSCAPE_PREVIEW_SPLITS[splitIndex]
                                                        preferences.edit()
                                                            .putFloat(
                                                                landscapePreviewSplitKey,
                                                                LANDSCAPE_PREVIEW_SPLITS[splitIndex]
                                                            )
                                                            .apply()
                                                        hapticFeedback.performHapticFeedback(
                                                            HapticFeedbackType.LongPress
                                                        )
                                                    }
                                                    accumulated = 0f
                                                }
                                            }
                                        )
                                    }
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                landscapePreviewFraction = 0.5f
                                                preferences.edit()
                                                    .putFloat(landscapePreviewSplitKey, 0.5f)
                                                    .apply()
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.LongPress
                                                )
                                            }
                                        )
                                    }
                                    .semantics {
                                        contentDescription = uiText("プレビューとエディターの分割を調整")
                                        customActions = listOf(
                                            CustomAccessibilityAction(uiText("プレビューを広げる")) {
                                                val index = LANDSCAPE_PREVIEW_SPLITS.indexOf(landscapePreviewFraction)
                                                val next = LANDSCAPE_PREVIEW_SPLITS[(index + 1).coerceIn(0, 2)]
                                                landscapePreviewFraction = next
                                                preferences.edit().putFloat(landscapePreviewSplitKey, next).apply()
                                                true
                                            },
                                            CustomAccessibilityAction(uiText("エディターを広げる")) {
                                                val index = LANDSCAPE_PREVIEW_SPLITS.indexOf(landscapePreviewFraction)
                                                val next = LANDSCAPE_PREVIEW_SPLITS[(index - 1).coerceIn(0, 2)]
                                                landscapePreviewFraction = next
                                                preferences.edit().putFloat(landscapePreviewSplitKey, next).apply()
                                                true
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .width(if (showLandscapeSplitLabel) 12.dp else 2.dp)
                                            .height(if (showLandscapeSplitLabel) 64.dp else 48.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (showLandscapeSplitLabel) {
                                            colors.primaryContainer
                                        } else {
                                            colors.outlineVariant
                                        },
                                        border = if (showLandscapeSplitLabel) {
                                            BorderStroke(
                                                1.dp,
                                                colors.primary.copy(alpha = 0.6f)
                                            )
                                        } else null,
                                        tonalElevation = if (showLandscapeSplitLabel) 2.dp else 0.dp
                                    ) {
                                        if (showLandscapeSplitLabel) {
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(
                                                    4.dp,
                                                    Alignment.CenterVertically
                                                )
                                            ) {
                                                repeat(3) {
                                                    Box(
                                                        Modifier
                                                            .width(6.dp)
                                                            .height(1.dp)
                                                            .background(
                                                                colors.onPrimaryContainer,
                                                                RoundedCornerShape(1.dp)
                                                            )
                                                    )
                                                }
                                            }
                                        } else {
                                            Box(Modifier.fillMaxSize())
                                        }
                                    }
                                }
                            }
                            } else {
                                Spacer(Modifier.width(8.dp))
                            }

                            Column(
                                modifier =
                                    Modifier
                                        .weight(
                                            1f - animatedLandscapePreviewFraction
                                        )
                                        .fillMaxHeight()
                            ) {

                                AnimatedVisibility(
                                    visible =
                                        !editorFocused,

                                    enter =
                                        fadeIn(
                                            tween(180)
                                        ) +
                                                expandVertically(
                                                    animationSpec =
                                                        tween(
                                                            220,
                                                            easing =
                                                                FastOutSlowInEasing
                                                        ),

                                                    expandFrom =
                                                        Alignment.Top
                                                ),

                                    exit =
                                        fadeOut(
                                            tween(140)
                                        ) +
                                                shrinkVertically(
                                                    animationSpec =
                                                        tween(
                                                            220,
                                                            easing =
                                                                FastOutSlowInEasing
                                                        ),

                                                    shrinkTowards =
                                                        Alignment.Top
                                                )
                                ) {

                                    Column {

                                        Row(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(
                                                        48.dp
                                                    ),

                                            verticalAlignment =
                                                Alignment.CenterVertically
                                        ) {

                                            Box(
                                                modifier =
                                                    Modifier
                                                        .weight(1f)
                                                        .padding(end = 8.dp)
                                            ) {
                                                WorkSelector()
                                            }

                                            WorkActions()
                                        }

                                        ControlBar()
                                    }
                                }

                                AnimatedVisibility(
                                    visible =
                                        showConsole,
                                    enter =
                                        fadeIn(
                                            tween(140)
                                        ) +
                                            expandVertically(
                                                tween(180)
                                            ),
                                    exit =
                                        fadeOut(
                                            tween(110)
                                        ) +
                                            shrinkVertically(
                                                tween(160)
                                            )
                                ) {

                                    ConsolePanel(
                                        modifier =
                                            Modifier.padding(
                                                bottom =
                                                    6.dp
                                            )
                                    )
                                }

                                EditorArea(
                                    modifier =
                                        Modifier
                                            .weight(
                                                1f
                                            )
                                            .fillMaxWidth()
                                )

                                CompletionBar(
                                    modifier = Modifier.padding(top = 6.dp)
                                )

                                AnimatedVisibility(
                                    visible = editorFocused && showEditorAccessoryBar,
                                    enter = fadeIn(tween(120)) + expandVertically(tween(160)),
                                    exit = fadeOut(tween(90)) + shrinkVertically(tween(130))
                                ) {
                                    EditorAccessoryBar(
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }

                    } else {

                        Box(
                            Modifier.fillMaxWidth().height(portraitHeight.coerceAtMost(previewHeightLimit.value).dp).clipToBounds(),
                            contentAlignment = Alignment.Center
                        ) {
                            val fitted = fitPreviewSize(
                                availablePreviewWidth,
                                portraitHeight.coerceAtMost(previewHeightLimit.value),
                                workPreviewRatio
                            )
                            MainPreviewArea(Modifier.size(
                                if (useWideEditingPreview) availablePreviewWidth.coerceAtLeast(0f).dp else fitted.width.dp,
                                if (useWideEditingPreview) portraitHeight.coerceAtMost(previewHeightLimit.value).dp else fitted.height.dp
                            ))
                        }

                        if (showResizeHandles && !editorFocused) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .pointerInput(activeWorkId) {
                                    var accumulated = 0f
                                    var ratioIndex = PREVIEW_ASPECT_RATIOS
                                        .indexOf(currentPreviewRatio.value)
                                        .coerceAtLeast(0)
                                    val threshold = 38.dp.toPx()
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            accumulated = 0f
                                            ratioIndex = PREVIEW_ASPECT_RATIOS
                                                .indexOf(currentPreviewRatio.value)
                                                .coerceAtLeast(0)
                                            portraitRatioDragging = true
                                        },
                                        onDragEnd = {
                                            portraitRatioDragging = false
                                        },
                                        onDragCancel = {
                                            portraitRatioDragging = false
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            accumulated += dragAmount
                                            if (kotlin.math.abs(accumulated) >= threshold) {
                                                val nextIndex = (
                                                    ratioIndex + if (accumulated > 0f) 1 else -1
                                                ).coerceIn(0, PREVIEW_ASPECT_RATIOS.lastIndex)
                                                if (nextIndex != ratioIndex) {
                                                    ratioIndex = nextIndex
                                                    val selectedRatio = PREVIEW_ASPECT_RATIOS[ratioIndex]
                                                    currentRatioChange.value(selectedRatio)
                                                    hapticFeedback.performHapticFeedback(
                                                        HapticFeedbackType.LongPress
                                                    )
                                                }
                                                accumulated = 0f
                                            }
                                        }
                                    )
                                }
                                .semantics {
                                    contentDescription = uiText(
                                        "プレビュー比率を変更: %s",
                                        if (previewRatioSelection == "device") uiText("端末")
                                        else previewRatioSelection
                                    )
                                    customActions = PREVIEW_ASPECT_RATIOS.map { ratio ->
                                        CustomAccessibilityAction(
                                            if (ratio == "device") uiText("端末") else ratio
                                        ) {
                                            currentRatioChange.value(ratio)
                                            true
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                modifier = Modifier
                                    .width(if (portraitRatioDragging) 82.dp else 48.dp)
                                    .height(if (portraitRatioDragging) 24.dp else 2.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = if (portraitRatioDragging) {
                                    colors.primaryContainer
                                } else {
                                    colors.outlineVariant
                                },
                                border = if (portraitRatioDragging) {
                                    BorderStroke(
                                        1.dp,
                                        colors.primary.copy(alpha = 0.6f)
                                    )
                                } else null,
                                tonalElevation = if (portraitRatioDragging) 2.dp else 0.dp
                            ) {
                                if (portraitRatioDragging) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (previewRatioSelection == "device") uiText("端末")
                                            else previewRatioSelection,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = codeFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (portraitRatioDragging) {
                                            colors.onPrimaryContainer
                                        } else {
                                            colors.onSurfaceVariant
                                        }
                                    )
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        repeat(2) {
                                            Box(
                                                Modifier
                                                    .width(14.dp)
                                                    .height(1.dp)
                                                    .background(
                                                        if (portraitRatioDragging) {
                                                            colors.onPrimaryContainer
                                                        } else {
                                                            colors.onSurfaceVariant
                                                        },
                                                        RoundedCornerShape(1.dp)
                                                    )
                                            )
                                        }
                                    }
                                }
                                } else {
                                    Box(Modifier.fillMaxSize())
                                }
                            }
                        }
                        }

                        AnimatedVisibility(
                            visible =
                                !editorFocused,

                            enter =
                                fadeIn(
                                    tween(180)
                                ) +
                                        expandVertically(
                                            tween(220)
                                        ),

                            exit =
                                fadeOut(
                                    tween(140)
                                ) +
                                        shrinkVertically(
                                            tween(220)
                                        )
                        ) {

                            Box(
                                modifier =
                                    Modifier.padding(
                                        horizontal =
                                            12.dp
                                    )
                            ) {

                                ControlBar()
                            }
                        }

                        AnimatedVisibility(
                            visible =
                                showConsole,
                            enter =
                                fadeIn(
                                    tween(140)
                                ) +
                                    expandVertically(
                                        tween(180)
                                    ),
                            exit =
                                fadeOut(
                                    tween(110)
                                ) +
                                    shrinkVertically(
                                        tween(160)
                                    )
                        ) {

                            ConsolePanel(
                                modifier =
                                    Modifier
                                        .padding(
                                            horizontal =
                                                12.dp
                                        )
                                        .padding(
                                            bottom =
                                                6.dp
                                        )
                            )
                        }

                        EditorArea(
                            modifier =
                                Modifier
                                    .weight(
                                        1f
                                    )
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal =
                                            12.dp
                                    )
                                    .padding(
                                        bottom =
                                            if (editorFocused && showEditorAccessoryBar) 0.dp else 8.dp
                                    )
                        )

                        CompletionBar(
                            modifier = Modifier.padding(
                                start = 12.dp,
                                end = 12.dp,
                                top = 6.dp
                            )
                        )

                        AnimatedVisibility(
                            visible = editorFocused && showEditorAccessoryBar,
                            enter = fadeIn(tween(120)) + expandVertically(tween(160)),
                            exit = fadeOut(tween(90)) + shrinkVertically(tween(130))
                        ) {
                            EditorAccessoryBar(
                                modifier = Modifier.padding(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = 6.dp,
                                    bottom = 2.dp
                                )
                            )
                        }
                    }
                }
            }
        }

        }

        if (showExpandedPreview) {
            Dialog(
                onDismissRequest = {
                    if (!isPreviewRecording) showExpandedPreview = false
                },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                KeepLandscapeDialogImmersive(enabled = true)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    PreviewArea(
                        modifier = Modifier
                            .fillMaxSize(),
                        showExpandControl = false,
                        logicalSize = (if (expandedCanvasSwapped)
                            IntSize(normalPreviewSize.height, normalPreviewSize.width)
                        else normalPreviewSize).takeIf {
                            preserveExpandedPreview
                        },
                        fullscreen = true
                    )
                }
            }
        }

        if (showHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showHistoryDialog = false },
                icon = {
                    Icon(painterResource(R.drawable.ic_restore), contentDescription = null)
                },
                title = { Text(uiText("変更履歴")) },
                text = {
                    val revisions = activeWork?.revisions.orEmpty().asReversed()
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(revisions) { revision ->
                            Surface(
                                onClick = {
                                    val work = activeWork ?: return@Surface
                                    if (editorText != revision.code) {
                                        work.revisions.add(
                                            WorkRevision(editorText, System.currentTimeMillis())
                                        )
                                    }
                                    while (work.revisions.size > 30) work.revisions.removeAt(0)
                                    work.code = revision.code
                                    work.updatedAt = System.currentTimeMillis()
                                    editorValue = TextFieldValue(revision.code)
                                    lastSavedText = revision.code
                                    clearEditHistory()
                                    saveWorkStore(selectedFolderUri, works, activeWorkId)
                                    clearDraftSnapshot()
                                    showHistoryDialog = false
                                    if (autoRun) runSketch(revision.code, work.files)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = colors.surfaceContainerHigh,
                                border = BorderStroke(1.dp, colors.outlineVariant)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        text = android.text.format.DateFormat.format(
                                            "yyyy/MM/dd HH:mm",
                                            revision.savedAt
                                        ).toString(),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = revision.code.lineSequence()
                                            .firstOrNull { it.isNotBlank() }
                                            ?.trim()
                                            ?.take(80)
                                            ?: uiText("空のコード"),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = codeFontFamily,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHistoryDialog = false }) { Text(uiText("閉じる")) }
                }
            )
        }

        if (showRuntimeDialog) {
            AlertDialog(
                onDismissRequest = { showRuntimeDialog = false },
                icon = { Icon(painterResource(R.drawable.ic_code), contentDescription = null) },
                title = { Text(uiText("実行環境")) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(uiText("p5.jsバージョン"), style = MaterialTheme.typography.labelLarge)
                        listOf(
                            P5_VERSION_CURRENT to uiText("現在の標準"),
                            P5_VERSION_LEGACY to uiText("旧作品向け")
                        ).forEach { (version, description) ->
                            val selected = runtimeP5Version == version
                            Surface(
                                onClick = { runtimeP5Version = version },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) colors.primary else colors.outlineVariant
                                ),
                                color = if (selected) colors.primaryContainer else Color.Transparent
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("p5.js $version", modifier = Modifier.weight(1f))
                                    Text(
                                        description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("p5.sound", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    uiText("音声再生・合成・解析を有効にします"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = runtimeSoundEnabled,
                                onCheckedChange = { runtimeSoundEnabled = it }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = saveRuntime@{
                        val work = activeWork ?: return@saveRuntime
                        val previousVersion = work.p5Version
                        val previousSound = work.p5SoundEnabled
                        val previousUpdatedAt = work.updatedAt
                        work.p5Version = runtimeP5Version
                        work.p5SoundEnabled = runtimeSoundEnabled
                        work.updatedAt = System.currentTimeMillis()
                        if (selectedFolderUri != null && !saveStore()) {
                            work.p5Version = previousVersion
                            work.p5SoundEnabled = previousSound
                            work.updatedAt = previousUpdatedAt
                            Toast.makeText(this@MainActivity, uiText("実行環境を保存できませんでした"), Toast.LENGTH_SHORT).show()
                            return@saveRuntime
                        }
                        showRuntimeDialog = false
                        runSketch()
                    }) { Text(uiText("保存")) }
                },
                dismissButton = {
                    TextButton(onClick = { showRuntimeDialog = false }) { Text(uiText("キャンセル")) }
                }
            )
        }

        if (showAspectRatioDialog) {
            val aspectColumns = if (isLandscape) 3 else 2
            val deviceRatioText = if (devicePreviewRatio >= 1f) {
                String.format(java.util.Locale.ROOT, "%.2f:1", devicePreviewRatio)
            } else {
                String.format(java.util.Locale.ROOT, "1:%.2f", 1f / devicePreviewRatio)
            }
            WorkSheet(
                title = uiText("プレビュー比率"),
                subtitle = uiText("作品の表示枠を選択"),
                onDismiss = { showAspectRatioDialog = false }
            ) {
                Column(
                    Modifier.fillMaxWidth().weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PREVIEW_ASPECT_RATIOS.chunked(aspectColumns).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { ratio ->
                                val selected = previewRatioSelection == ratio
                                val ratioValue = previewAspectRatioValue(ratio, devicePreviewRatio)
                                Surface(
                                    onClick = {
                                        updateActiveWorkPreviewRatio(ratio)
                                        showAspectRatioDialog = false
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 84.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) colors.primary.copy(alpha = 0.08f)
                                        else colors.surface,
                                    border = BorderStroke(1.dp,
                                        if (selected) colors.primary else colors.outlineVariant)
                                ) {
                                    Column(
                                        Modifier.fillMaxWidth().padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            Modifier.fillMaxWidth().height(28.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            val previewShape = fitPreviewSize(42f, 26f, ratioValue)
                                            Box(
                                                Modifier.size(previewShape.width.dp, previewShape.height.dp)
                                                    .border(
                                                        1.dp,
                                                        if (selected) colors.primary
                                                        else colors.onSurfaceVariant,
                                                        RoundedCornerShape(4.dp)
                                                    )
                                            )
                                        }
                                        Text(
                                            if (ratio == "device") uiText("端末") else ratio,
                                            fontFamily = codeFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selected) colors.primary else colors.onSurface
                                        )
                                        Text(
                                            if (ratio == "device") deviceRatioText else when (ratio) {
                                                "1:1" -> uiText("正方形")
                                                "4:3" -> uiText("標準・横")
                                                "16:9" -> uiText("ワイド")
                                                else -> uiText("縦長")
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            repeat(aspectColumns - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    Text(
                        uiText("端末の向きに合わせて比率が切り替わります"),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
            }
        }

        if (assetBusy && !showAssets) {
            AlertDialog(onDismissRequest = {}, title = { Text(uiText("ファイルを処理中…")) },
                text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }, confirmButton = {})
        }

        if (showP5Import) {
            P5AccountImportDialog(
                username = p5Username,
                sketches = p5Sketches,
                busy = p5Busy,
                error = p5Error,
                text = { uiText(it) },
                onUsernameChange = { value ->
                    p5Username = value.take(64)
                    p5Sketches = emptyList()
                    p5Error = null
                },
                onLoad = ::loadP5Account,
                onImport = ::importFromP5,
                onDismiss = { showP5Import = false }
            )
        }

        if (showAssets) {
            AssetManagerDialog(
                assets = activeWork?.assets.orEmpty(), busy = assetBusy, text = ::uiText,
                onAdd = {
                    assetTargetId = activeWorkId
                    assetPicker.launch(arrayOf("*/*"))
                },
                onRename = { old, new ->
                    activeWork?.let { work ->
                        val updated = work.assets.toMutableMap()
                        updated.remove(old)?.let { updated[new] = it }
                        changeAssets(work.id, updated)
                    }
                },
                onDelete = { name -> activeWork?.let { changeAssets(it.id, it.assets.toMap() - name) } },
                onClose = { showAssets = false }
            )
        }

        if (showProjectFilesDialog) {
            AlertDialog(
                onDismissRequest = { showProjectFilesDialog = false },
                icon = {
                    Icon(painterResource(R.drawable.ic_folder_code), contentDescription = null)
                },
                title = { Text(uiText("プロジェクトファイル")) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = colors.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_code),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("sketch.js", fontFamily = codeFontFamily)
                                Spacer(Modifier.weight(1f))
                                Text("MAIN", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        activeWork?.files?.toSortedMap()?.forEach { (name, content) ->
                            Surface(
                                onClick = {
                                    originalAuxiliaryFileName = name
                                    auxiliaryFileName = name
                                    auxiliaryFileContent = content
                                    showProjectFilesDialog = false
                                    showAuxiliaryFileEditor = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, colors.outlineVariant),
                                color = Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_code),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(name, fontFamily = codeFontFamily)
                                    Spacer(Modifier.weight(1f))
                                    Text(uiText("編集"), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        originalAuxiliaryFileName = null
                        auxiliaryFileName = "library.js"
                        auxiliaryFileContent = ""
                        showProjectFilesDialog = false
                        showAuxiliaryFileEditor = true
                    }) {
                        Icon(
                            painterResource(R.drawable.ic_add),
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(uiText("ファイルを追加"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showProjectFilesDialog = false }) {
                        Text(uiText("閉じる"))
                    }
                }
            )
        }

        if (showAuxiliaryFileEditor) {
            val normalizedName = auxiliaryFileName.trim()
            val validName = normalizedName.matches(Regex("[A-Za-z0-9._-]+\\.js")) &&
                normalizedName != "sketch.js"
            AlertDialog(
                onDismissRequest = { showAuxiliaryFileEditor = false },
                title = { Text(if (originalAuxiliaryFileName == null) uiText("JSファイルを追加") else uiText("JSファイルを編集")) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = auxiliaryFileName,
                            onValueChange = { auxiliaryFileName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(uiText("ファイル名（.js）")) },
                            isError = auxiliaryFileName.isNotBlank() && !validName,
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = auxiliaryFileContent,
                            onValueChange = { auxiliaryFileContent = it },
                            modifier = Modifier.fillMaxWidth().height(260.dp),
                            label = { Text(uiText("JavaScriptコード")) },
                            textStyle = TextStyle(fontFeatureSettings = fontFeatures, fontFamily = codeFontFamily, fontSize = 13.sp)
                        )
                        Text(
                            uiText("追加ファイルは名前順にsketch.jsより先に実行されます。"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            updateCurrentWork()
                            activeWork?.let { work ->
                                originalAuxiliaryFileName?.takeIf { it != normalizedName }
                                    ?.let(work.files::remove)
                                work.files[normalizedName] = auxiliaryFileContent
                                work.updatedAt = System.currentTimeMillis()
                                works = works.toList()
                                if (saveWorkStore(selectedFolderUri, works, activeWorkId)) {
                                    lastSavedText = editorText
                                }
                                runSketch(editorText, work.files)
                            }
                            showAuxiliaryFileEditor = false
                        },
                        enabled = validName
                    ) { Text(uiText("保存")) }
                },
                dismissButton = {
                    Row {
                        originalAuxiliaryFileName?.let { existingName ->
                            TextButton(onClick = {
                                updateCurrentWork()
                                activeWork?.let { work ->
                                    work.files.remove(existingName)
                                    works = works.toList()
                                    if (saveWorkStore(selectedFolderUri, works, activeWorkId)) {
                                        lastSavedText = editorText
                                    }
                                    runSketch(editorText, work.files)
                                }
                                showAuxiliaryFileEditor = false
                            }) {
                                Text(uiText("削除"), color = colors.error)
                            }
                        }
                        TextButton(onClick = { showAuxiliaryFileEditor = false }) {
                            Text(uiText("キャンセル"))
                        }
                    }
                }
            )
        }

        if (showSearchDialog) {

            val matches = searchMatches()
            val selectedMatchIndex = matches.indexOfFirst {
                it.first == editorValue.selection.min &&
                    it.last + 1 == editorValue.selection.max
            }

            AlertDialog(
                onDismissRequest = {
                    showSearchDialog = false
                },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null
                    )
                },
                title = {
                    Text(uiText("検索・置換"))
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(uiText("検索する文字列")) },
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = searchMatchCase,
                                onCheckedChange = { searchMatchCase = it }
                            )
                            Text(
                                text = uiText("大文字・小文字を区別"),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = if (matches.isEmpty()) {
                                    uiText("0件")
                                } else if (selectedMatchIndex >= 0) {
                                    "${selectedMatchIndex + 1} / ${matches.size}"
                                } else {
                                    uiText("%s件", matches.size)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { selectSearchMatch(-1) },
                                enabled = matches.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(uiText("前へ"))
                            }
                            Button(
                                onClick = { selectSearchMatch(1) },
                                enabled = matches.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(uiText("次へ"))
                            }
                        }

                        HorizontalDivider(color = colors.outlineVariant)

                        OutlinedTextField(
                            value = replacementText,
                            onValueChange = { replacementText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(uiText("置換後の文字列")) },
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { replaceSelectedMatch() },
                                enabled = matches.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(uiText("置換"))
                            }
                            OutlinedButton(
                                onClick = { replaceAllMatches() },
                                enabled = matches.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(uiText("すべて置換"))
                            }
                        }

                        HorizontalDivider(color = colors.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = goToLineText,
                                onValueChange = {
                                    goToLineText = it.filter(Char::isDigit)
                                },
                                modifier = Modifier.weight(1f),
                                label = { Text(uiText("行番号")) },
                                singleLine = true
                            )
                            FilledTonalButton(
                                onClick = {
                                    goToLineText.toIntOrNull()?.let(::jumpToLine)
                                    showSearchDialog = false
                                },
                                enabled = goToLineText.toIntOrNull()?.let { it > 0 } == true
                            ) {
                                Text(uiText("移動"))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSearchDialog = false
                            editorFocusRequester.requestFocus()
                        }
                    ) {
                        Text(uiText("閉じる"))
                    }
                }
            )
        }

        if (
            selectedFolderUri ==
            null
        ) {

            AlertDialog(
                onDismissRequest = {},

                title = {

                    KeepLandscapeDialogImmersive(
                        enabled =
                            isLandscape
                    )

                    Text(
                        uiText("作品フォルダーを選択")
                    )
                },

                text = {

                    Text(
                        uiText("作品は選択したフォルダーに保存されます。再インストール後も同じフォルダーを選択すれば復元できます。")
                    )
                },

                confirmButton = {

                    Button(
                        onClick = {

                            folderLauncher.launch(
                                null
                            )
                        }
                    ) {

                        Text(
                            uiText("フォルダーを選択")
                        )
                    }
                }
            )
        }

        if (showAddDialog) {
            var newTitle by rememberSaveable {
                mutableStateOf(uiText("新しい作品") + " ${works.size + 1}")
            }
            var newRatio by rememberSaveable { mutableStateOf("1:1") }
            var newCanvasModeName by rememberSaveable {
                mutableStateOf(CanvasSizingMode.FIXED.name)
            }
            val newCanvasMode = CanvasSizingMode.valueOf(newCanvasModeName)
            val deviceTemplateScale = 960f / maxOf(
                configuration.screenWidthDp,
                configuration.screenHeightDp,
                1
            )
            val deviceTemplate = WorkTemplate(
                ratio = "device",
                width = (configuration.screenWidthDp * deviceTemplateScale).toInt().coerceAtLeast(1),
                height = (configuration.screenHeightDp * deviceTemplateScale).toInt().coerceAtLeast(1)
            )
            val creationAspectOptions = workTemplates + deviceTemplate
            val template = creationAspectOptions.first { it.ratio == newRatio }
            WorkSheet(
                title = uiText("新しい作品"),
                subtitle = uiText("名前と描画比率を選んでスタート"),
                onDismiss = { showAddDialog = false }
            ) {
                Column(
                    Modifier.fillMaxWidth().weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text(uiText("作品名")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                    Text(uiText("キャンバスの動作"), style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            CanvasSizingMode.FIXED to uiText("固定サイズ"),
                            CanvasSizingMode.RESPONSIVE to uiText("画面に合わせる")
                        ).forEach { (mode, label) ->
                            val selected = mode == newCanvasMode
                            val enabled = mode != CanvasSizingMode.FIXED || newRatio != "device"
                            Surface(
                                onClick = { newCanvasModeName = mode.name },
                                enabled = enabled,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) colors.primary.copy(alpha = 0.08f)
                                    else colors.surface,
                                border = BorderStroke(1.dp,
                                    if (selected) colors.primary else colors.outlineVariant)
                            ) {
                                Column(Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(label, fontWeight = FontWeight.SemiBold,
                                        color = if (!enabled) colors.onSurfaceVariant.copy(alpha = 0.45f)
                                            else if (selected) colors.primary else colors.onSurface)
                                    Text(
                                        uiText(if (mode == CanvasSizingMode.FIXED)
                                            "指定した描画サイズを維持し、表示だけを枠に合わせます"
                                        else "作業領域に合わせてresizeCanvas()を実行します"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Text(uiText("描画比率"), style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant)
                    val creationAspectColumns = if (isLandscape) 3 else 2
                    creationAspectOptions.chunked(creationAspectColumns).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { option ->
                                val selected = option.ratio == newRatio
                                Surface(
                                    onClick = {
                                        newRatio = option.ratio
                                        if (option.ratio == "device") {
                                            newCanvasModeName = CanvasSizingMode.RESPONSIVE.name
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) colors.primary.copy(alpha = 0.08f) else colors.surface,
                                    border = BorderStroke(1.dp,
                                        if (selected) colors.primary else colors.outlineVariant)
                                ) {
                                    Row(Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                            val size = fitPreviewSize(32f, 32f,
                                                option.width.toFloat() / option.height)
                                            Box(Modifier.size(size.width.dp, size.height.dp)
                                                .border(1.dp, if (selected) colors.primary else colors.onSurfaceVariant,
                                                    RoundedCornerShape(4.dp)))
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(if (option.ratio == "device") uiText("端末") else option.ratio,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (selected) colors.primary else colors.onSurface)
                                            Text(if (selected) uiText("選択中") else if (option.ratio == "device")
                                                uiText("端末の画面比率") else "${option.width} × ${option.height}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            repeat(creationAspectColumns - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceVariant
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(uiText("シンプルな円のテンプレート"),
                                style = MaterialTheme.typography.labelLarge)
                            Text(if (newCanvasMode == CanvasSizingMode.FIXED)
                                "createCanvas(${template.width}, ${template.height});"
                            else "createCanvas(windowWidth, windowHeight);",
                                fontFamily = codeFontFamily,
                                style = MaterialTheme.typography.bodySmall, color = colors.primary)
                            Text(uiText("選択した比率を作品に保存します。現在の作品のコードはコピーしません。"),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showAddDialog = false }) { Text(uiText("キャンセル")) }
                    Button(
                        onClick = {
                            updateCurrentWork()
                            val now = System.currentTimeMillis()
                            val newWork = Work(
                                id = java.util.UUID.randomUUID().toString(),
                                title = newTitle.trim().ifBlank { uiText("新しい作品") },
                                code = template.code(newCanvasMode),
                                previewAspectRatio = template.ratio,
                                createdAt = now,
                                updatedAt = now
                            )
                            val newWorks = works + newWork
                            // Do not switch away or dismiss the form when persistent creation fails.
                            if (selectedFolderUri != null &&
                                !saveWorkStore(selectedFolderUri, newWorks, newWork.id)) {
                                Toast.makeText(this@MainActivity, uiText("作品を保存できませんでした。保存先を確認してください"),
                                    Toast.LENGTH_LONG).show()
                            } else {
                                works = newWorks
                                activeWorkId = newWork.id
                                editorValue = TextFieldValue(newWork.code)
                                clearDraftSnapshot()
                                showAddDialog = false
                                isError = false
                                if (autoRun) runSketch(newWork.code, newWork.files)
                            }
                        },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(uiText("作成"))
                    }
                }
            }
        }

        if (
            showRenameDialog &&
            activeWork != null
        ) {

            var renamedTitle by
            remember(
                activeWorkId
            ) {
                mutableStateOf(
                    activeWork.title
                )
            }

            AlertDialog(
                onDismissRequest = {
                    showRenameDialog =
                        false
                },

                title = {

                    KeepLandscapeDialogImmersive(
                        enabled =
                            isLandscape
                    )

                    Text(
                        uiText("作品名を変更")
                    )
                },

                text = {

                    OutlinedTextField(
                        value =
                            renamedTitle,

                        onValueChange = {
                            renamedTitle =
                                it
                        },

                        label = {
                            Text(
                                uiText("作品名")
                            )
                        },

                        singleLine =
                            true
                    )
                },

                confirmButton = {

                    TextButton(
                        onClick = {

                            val title =
                                renamedTitle
                                    .trim()
                                    .ifBlank {
                                        activeWork.title
                                    }

                            val previousTitle = activeWork.title
                            val previousUpdatedAt = activeWork.updatedAt
                            activeWork.title =
                                title

                            activeWork.updatedAt =
                                System.currentTimeMillis()

                            works =
                                works.toList()

                            if (persistWorkChange(works, activeWorkId)) {
                                showRenameDialog = false
                            } else {
                                activeWork.title = previousTitle
                                activeWork.updatedAt = previousUpdatedAt
                            }
                        }
                    ) {

                        Text(
                            uiText("変更")
                        )
                    }
                },

                dismissButton = {

                    TextButton(
                        onClick = {
                            showRenameDialog =
                                false
                        }
                    ) {

                        Text(
                            uiText("キャンセル")
                        )
                    }
                }
            )
        }

        if (showDeleteDialog) {

            AlertDialog(
                onDismissRequest = {
                    showDeleteDialog =
                        false
                },

                title = {

                    KeepLandscapeDialogImmersive(
                        enabled =
                            isLandscape
                    )

                    Text(
                        uiText("作品を削除")
                    )
                },

                text = {

                    Text(
                        uiText("「%s」を削除しますか？", activeWork?.title)
                    )
                },

                confirmButton = {

                    TextButton(
                        onClick = deleteWork@{

                            if (
                                works.size <=
                                1
                            ) {

                                isError =
                                    true

                            } else {

                                val remaining =
                                    works.filter {
                                        it.id !=
                                                activeWorkId
                                    }

                                val newActiveId =
                                    remaining
                                        .first()
                                        .id

                                if (!persistWorkChange(remaining, newActiveId)) return@deleteWork

                                works =
                                    remaining

                                activeWorkId =
                                    newActiveId

                                editorValue =
                                    TextFieldValue(
                                        remaining.first().code
                                    )

                                clearDraftSnapshot()

                                isError =
                                    false

                                if (autoRun) {

                                    runSketch(
                                        remaining
                                            .first()
                                            .code,
                                        remaining.first().files
                                    )
                                }
                            }

                            showDeleteDialog =
                                false
                        }
                    ) {

                        Text(
                            text =
                                uiText("削除"),

                            color =
                                colors.error
                        )
                    }
                },

                dismissButton = {

                    TextButton(
                        onClick = {
                            showDeleteDialog =
                                false
                        }
                    ) {

                        Text(
                            uiText("キャンセル")
                        )
                    }
                }
            )
        }
    }

    @Composable
    private fun SettingsScreen(
        onImportFont: () -> Unit,
        modifier: Modifier,
        onBack: () -> Unit,
        themeMode: AppThemeMode,
        onThemeModeChange:
            (AppThemeMode) -> Unit,
        folderName: String,
        onChooseFolder: () -> Unit,
        autoRun: Boolean,
        onAutoRunChange:
            (Boolean) -> Unit,
        hideEditingPreview: Boolean,
        onHideEditingPreviewChange: (Boolean) -> Unit,
        compactPreview: Boolean,
        onCompactPreviewChange:
            (Boolean) -> Unit,
        showResizeHandles: Boolean,
        onShowResizeHandlesChange:
            (Boolean) -> Unit,
        landscapePreviewFraction: Float,
        onLandscapePreviewFractionChange:
            (Float) -> Unit,
        preserveExpandedPreview: Boolean,
        onPreserveExpandedPreviewChange:
            (Boolean) -> Unit,
        showStatusBar: Boolean,
        statusBarForcedHidden: Boolean,
        landscapeUseCutout: Boolean,
        onLandscapeUseCutoutChange: (Boolean) -> Unit,
        onShowStatusBarChange:
            (Boolean) -> Unit,
        manualRotation: Boolean,
        onManualRotationChange:
            (Boolean) -> Unit,
        editorFontSize: Float,
        onEditorFontSizeChange:
            (Float) -> Unit,
        autoIndent: Boolean,
        onAutoIndentChange:
            (Boolean) -> Unit,
        showLineNumbers: Boolean,
        onShowLineNumbersChange:
            (Boolean) -> Unit,
        showEditorAccessoryBar: Boolean,
        onShowEditorAccessoryBarChange:
            (Boolean) -> Unit,
        codeCompletion: Boolean,
        onCodeCompletionChange:
            (Boolean) -> Unit,
        showAccessoryNavigation: Boolean,
        onShowAccessoryNavigationChange:
            (Boolean) -> Unit,
        showAccessorySymbols: Boolean,
        onShowAccessorySymbolsChange:
            (Boolean) -> Unit,
        compactAccessoryKeys: Boolean,
        onCompactAccessoryKeysChange:
            (Boolean) -> Unit,
        draftRecovery: Boolean,
        onDraftRecoveryChange:
            (Boolean) -> Unit,
        p5Username: String,
        onImportP5: () -> Unit,
        onImportJs: () -> Unit,
        onExportJs: () -> Unit,
        onExportBackup: () -> Unit,
        onImportBackup: () -> Unit
    ) {

        val colors =
            MaterialTheme.colorScheme

        val settingsLandscape =
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

        var settingsTab by rememberSaveable { mutableStateOf(0) }
        var showLicenses by remember { mutableStateOf(false) }
        if (showLicenses) {
            val paragraphs = remember {
                assets.open("licenses/THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() }
                    .split("\n\n")
            }
            AlertDialog(
                onDismissRequest = { showLicenses = false },
                title = { Text(uiText("ライセンス情報")) },
                text = {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(paragraphs) { paragraph ->
                            Text(paragraph, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLicenses = false }) { Text(uiText("閉じる")) }
                }
            )
        }
        val settingsScroll = remember(settingsTab, settingsLandscape) {
            androidx.compose.foundation.ScrollState(0)
        }

        Row(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(colors.background)
                    .padding(if (settingsLandscape) 8.dp else 0.dp)
        ) {

            if (settingsLandscape) {
                Surface(
                    modifier = Modifier.width(148.dp).fillMaxHeight(),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outlineVariant),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_back), uiText("戻る"), Modifier.size(20.dp))
                        }
                        Text(uiText("設定"), Modifier.padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        listOf(
                            R.drawable.ic_settings to uiText("外観"),
                            R.drawable.ic_code to uiText("エディター"),
                            R.drawable.ic_folder_code to uiText("保存とバックアップ")
                        ).forEachIndexed { index, (icon, label) ->
                            Surface(
                                onClick = { settingsTab = index },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = if (settingsTab == index) colors.primary.copy(alpha = 0.1f)
                                    else Color.Transparent,
                                border = if (settingsTab == index) BorderStroke(1.dp, colors.primary)
                                    else null
                            ) {
                                Row(Modifier.heightIn(min = 48.dp).padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painterResource(icon), null, Modifier.size(18.dp),
                                        tint = if (settingsTab == index) colors.primary else colors.onSurfaceVariant)
                                    Spacer(Modifier.width(8.dp))
                                    Text(label, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(settingsScroll)
                    .padding(
                        start = if (settingsLandscape) 12.dp else 20.dp,
                        top = 8.dp,
                        end = if (settingsLandscape) 8.dp else 20.dp,
                        bottom = 16.dp
                    ),

            verticalArrangement =
                Arrangement.spacedBy(
                    22.dp
                )
        ) {

            if (!settingsLandscape) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                IconButton(
                    onClick =
                        onBack
                ) {

                    Icon(
                        painter =
                            painterResource(
                                R.drawable.ic_back
                            ),
                        contentDescription =
                            uiText("戻る"),
                        tint =
                            colors.onSurface,
                        modifier =
                            Modifier.size(22.dp)
                    )
                }

                Spacer(
                    Modifier.width(
                        6.dp
                    )
                )

                Column {

                    Text(
                        text = uiText("設定"),
                        style =
                            MaterialTheme
                                .typography
                                .titleLarge,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    Text(
                        text = "Edit:KIRO",
                        style =
                            MaterialTheme
                                .typography
                                .labelMedium,
                        color =
                            colors.onSurfaceVariant
                    )
                }
            }
            }

            if (!settingsLandscape || settingsTab == 0) {
            SettingsSection(
                title = uiText("外観"),
                description = uiText("テーマとシステムUI")
            ) {

                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(uiText("言語"), style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    Text(uiText("対応していない端末言語の場合は英語を使用します"),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    listOf("system" to uiText("システムデフォルト"), "ja" to "日本語",
                        "en" to "English", "zh" to "中文（简体）").chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (code, label) ->
                                FilterChip(
                                    selected = appLanguage == code,
                                    onClick = {
                                        appLanguage = code
                                        preferences.edit().putString(appLanguageKey, code).apply()
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                SettingsDivider()

                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp)
                ) {
                    Text(
                        text = uiText("p5.js Web Editor"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = uiText("アカウントの公開作品を選んで取り込みます"),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    if (p5Username.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "@$p5Username",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.primary
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onImportP5
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_import_js),
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(uiText("p5.jsから作品を取り込む"))
                    }
                }

                SettingsDivider()

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 18.dp,
                                vertical = 16.dp
                            )
                ) {

                    Text(
                        text = uiText("テーマ"),
                        style =
                            MaterialTheme
                                .typography
                                .titleSmall,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    Spacer(
                        Modifier.height(
                            4.dp
                        )
                    )

                    Text(
                        text =
                            when (themeMode) {
                                AppThemeMode.SYSTEM ->
                                    uiText("端末の設定とMaterial Youに合わせます")

                                AppThemeMode.DARK ->
                                    uiText("黒を基調とした表示")

                                AppThemeMode.LIGHT ->
                                    uiText("白を基調とした表示")
                            },
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                        color =
                            colors.onSurfaceVariant
                    )

                    Spacer(
                        Modifier.height(
                            14.dp
                        )
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {

                        ThemeChip(
                            modifier =
                                Modifier.weight(1f),
                            label = uiText("自動"),
                            selected =
                                themeMode ==
                                    AppThemeMode.SYSTEM,
                            onClick = {
                                onThemeModeChange(
                                    AppThemeMode.SYSTEM
                                )
                            }
                        )

                        ThemeChip(
                            modifier =
                                Modifier.weight(1f),
                            label = uiText("ダーク"),
                            selected =
                                themeMode ==
                                    AppThemeMode.DARK,
                            onClick = {
                                onThemeModeChange(
                                    AppThemeMode.DARK
                                )
                            }
                        )

                        ThemeChip(
                            modifier =
                                Modifier.weight(1f),
                            label = uiText("ライト"),
                            selected =
                                themeMode ==
                                    AppThemeMode.LIGHT,
                            onClick = {
                                onThemeModeChange(
                                    AppThemeMode.LIGHT
                                )
                            }
                        )
                    }
                }

                SettingsDivider()

                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(uiText("アプリのフォント"), fontWeight = FontWeight.SemiBold)
                    Text(if (customFontFamily == null) uiText("標準フォント") else importedFontName,
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Text(uiText("TTF・OTF・TTCをインポート。設定画面とエディターに反映します"),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onImportFont, enabled = !fontImportBusy,
                            modifier = Modifier.weight(1f)) {
                            Text(uiText(if (fontImportBusy) "読み込み中" else "フォントをインポート"))
                        }
                        TextButton(onClick = {
                            customFontFamily = null
                            preferences.edit().remove("custom_font_file").remove("custom_font_name").apply()
                        }, enabled = customFontFamily != null && !fontImportBusy,
                            modifier = Modifier.weight(1f)) { Text(uiText("標準に戻す")) }
                    }
                    Text("EDIT:KIRO  Aa 0123  日本語 中文\n->  =>  !=  ===  <=  >=",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = codeFontFamily, fontFeatureSettings = fontFeatures,
                            letterSpacing = 0.sp),
                        modifier = Modifier.fillMaxWidth().background(colors.surface,
                            RoundedCornerShape(12.dp)).padding(12.dp))
                }
                SettingSwitchRow(
                    title = uiText("フォントの連字"),
                    description = uiText("対応フォントの連字を有効にします。コードの文字列は変わりません"),
                    checked = fontLigatures,
                    onCheckedChange = {
                        fontLigatures = it
                        preferences.edit().putBoolean("font_ligatures", it).apply()
                    }
                )
                SettingsDivider()

                SettingSwitchRow(
                    title =
                        uiText("ステータスバー"),
                    description =
                        if (statusBarForcedHidden) {
                            uiText("横画面では自動的に非表示になります")
                        } else {
                            uiText("時刻や通知アイコンを表示します")
                        },
                    checked =
                        showStatusBar &&
                            !statusBarForcedHidden,
                    enabled =
                        !statusBarForcedHidden,
                    onCheckedChange =
                        onShowStatusBarChange
                )

                SettingsDivider()

                SettingSwitchRow(
                    title = uiText("横画面でノッチ部分まで使用"),
                    description = if (landscapeUseCutout)
                        uiText("余白なしで表示します。ノッチの位置は内容が隠れる場合があります")
                    else uiText("ノッチを避ける余白を確保します"),
                    checked = landscapeUseCutout,
                    onCheckedChange = onLandscapeUseCutoutChange
                )

                SettingsDivider()

                SettingSwitchRow(
                    title =
                        uiText("画面の向きを固定"),
                    description =
                        if (manualRotation) {
                            uiText("自動回転を停止し、上部の↻ボタンで縦横を切り替えます")
                        } else {
                            uiText("端末の向きに合わせて画面を回転します")
                        },
                    checked =
                        manualRotation,
                    onCheckedChange =
                        onManualRotationChange
                )
            }

            }

            if (!settingsLandscape || settingsTab == 1) {
            SettingsSection(
                title = uiText("エディター"),
                description = uiText("編集とプレビューの動作")
            ) {

                SettingSwitchRow(
                    title =
                        uiText("作品切替時に自動実行"),
                    description =
                        uiText("作品を選択するとプレビューを更新します"),
                    checked =
                        autoRun,
                    onCheckedChange =
                        onAutoRunChange
                )

                SettingsDivider()

                SettingSwitchRow(
                    title = uiText("編集時にプレビューを隠す"),
                    description = uiText("縦画面でコード欄を選ぶと非表示になり、編集を終えると戻ります"),
                    checked = hideEditingPreview,
                    onCheckedChange = onHideEditingPreviewChange
                )

                SettingsDivider()

                SettingSwitchRow(
                    enabled = !hideEditingPreview,
                    title =
                        uiText("編集時にプレビューを縮小"),
                    description =
                        if (hideEditingPreview) uiText("プレビューを隠す設定が優先されます")
                        else uiText("縦画面の編集中だけプレビューを低く表示します"),
                    checked =
                        compactPreview,
                    onCheckedChange =
                        onCompactPreviewChange
                )

                SettingsDivider()

                SettingSwitchRow(
                    title = uiText("サイズ調整バー"),
                    description = if (showResizeHandles) {
                        uiText("プレビューとエディターの間にドラッグ操作を表示します")
                    } else {
                        uiText("境界線とドラッグ操作を非表示にします")
                    },
                    checked = showResizeHandles,
                    onCheckedChange = onShowResizeHandlesChange
                )

                if (settingsLandscape) {
                    SettingsDivider()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = uiText("横画面のプレビュー幅"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = uiText("プレビューに使う横幅を調整します"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LANDSCAPE_PREVIEW_SPLITS.forEach { fraction ->
                                ThemeChip(
                                    modifier = Modifier.weight(1f),
                                    label = "${kotlin.math.round(fraction * 100).toInt()}%",
                                    selected = kotlin.math.abs(
                                        landscapePreviewFraction - fraction
                                    ) < 0.01f,
                                    onClick = {
                                        onLandscapePreviewFractionChange(fraction)
                                    }
                                )
                            }
                        }
                    }
                }

                SettingsDivider()

                SettingSwitchRow(
                    title = uiText("全画面でも描画サイズを維持"),
                    description = uiText("通常プレビューと同じ座標・縦横比で実行し、表示だけを拡大します"),
                    checked = preserveExpandedPreview,
                    onCheckedChange = onPreserveExpandedPreviewChange
                )

                SettingsDivider()

                SettingSwitchRow(
                    title = uiText("自動インデント"),
                    description = uiText("改行時に現在の字下げを引き継ぎ、括弧内を一段下げます"),
                    checked = autoIndent,
                    onCheckedChange = onAutoIndentChange
                )

                SettingsDivider()

                SettingSwitchRow(
                    title = uiText("行番号"),
                    description = uiText("コードの各行に番号を表示します"),
                    checked = showLineNumbers,
                    onCheckedChange = onShowLineNumbersChange
                )

                SettingsDivider()

                SettingSwitchRow(
                    title = uiText("編集キー"),
                    description = uiText("編集中にTAB、カーソル移動、記号ボタンを表示します"),
                    checked = showEditorAccessoryBar,
                    onCheckedChange = onShowEditorAccessoryBarChange
                )

                SettingsDivider()

                SettingSwitchRow(
                    title = uiText("入力候補"),
                    description = uiText("p5.jsの関数名や変数名の候補を表示します"),
                    checked = codeCompletion,
                    onCheckedChange = onCodeCompletionChange
                )

                SettingsDivider()

                SettingSwitchRow(
                    title = uiText("移動キーを表示"),
                    description = uiText("操作パネルにTABとカーソルキーを表示します"),
                    checked = showAccessoryNavigation,
                    enabled = showEditorAccessoryBar,
                    onCheckedChange = onShowAccessoryNavigationChange
                )

                SettingsDivider()

                SettingSwitchRow(
                    title = uiText("記号キーを表示"),
                    description = uiText("操作パネルに括弧や記号キーを表示します"),
                    checked = showAccessorySymbols,
                    enabled = showEditorAccessoryBar,
                    onCheckedChange = onShowAccessorySymbolsChange
                )

                SettingsDivider()

                SettingSwitchRow(
                    title = uiText("編集キーを小さくする"),
                    description = uiText("操作パネルの高さとキー幅を小さくします"),
                    checked = compactAccessoryKeys,
                    enabled = showEditorAccessoryBar,
                    onCheckedChange = onCompactAccessoryKeysChange
                )

                SettingsDivider()

                SettingSwitchRow(
                    title =
                        uiText("未保存のコードを復元"),
                    description =
                        uiText("未保存の変更を一時保存し、次回起動時に復元します"),
                    checked =
                        draftRecovery,
                    onCheckedChange =
                        onDraftRecoveryChange
                )

                SettingsDivider()

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 18.dp,
                                vertical = 16.dp
                            )
                ) {

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Column(
                            modifier =
                                Modifier.weight(1f)
                        ) {

                            Text(
                                text = uiText("文字サイズ"),
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleSmall,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Text(
                                text =
                                    uiText("コードエディターの文字サイズ"),
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall,
                                color =
                                    colors.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape =
                                RoundedCornerShape(
                                    10.dp
                                ),
                            color =
                                colors.secondaryContainer
                        ) {

                            Text(
                                text =
                                    "${editorFontSize.toInt()} sp",
                                modifier =
                                    Modifier.padding(
                                        horizontal = 10.dp,
                                        vertical = 6.dp
                                    ),
                                style =
                                    MaterialTheme
                                        .typography
                                        .labelLarge,
                                color =
                                    colors.onSecondaryContainer,
                                fontFamily =
                                    codeFontFamily
                            )
                        }
                    }

                    Spacer(
                        Modifier.height(
                            8.dp
                        )
                    )

                    Slider(
                        value =
                            editorFontSize,
                        onValueChange =
                            onEditorFontSizeChange,
                        valueRange =
                            12f..20f,
                        steps =
                            7
                    )
                }
            }

            }

            if (!settingsLandscape || settingsTab == 2) {
            SettingsSection(
                title = uiText("保存とバックアップ"),
                description = uiText("保存先とファイル入出力")
            ) {

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                18.dp
                            )
                ) {

                    Text(
                        text = uiText("作品フォルダー"),
                        style =
                            MaterialTheme
                                .typography
                                .titleSmall,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    Spacer(
                        Modifier.height(
                            6.dp
                        )
                    )

                    Surface(
                        modifier =
                            Modifier.fillMaxWidth(),
                        shape =
                            RoundedCornerShape(
                                12.dp
                            ),
                        color =
                            colors.surface
                    ) {

                        Text(
                            text =
                                folderName,
                            modifier =
                                Modifier.padding(
                                    horizontal = 14.dp,
                                    vertical = 12.dp
                                ),
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium,
                            color =
                                colors.onSurfaceVariant,
                            fontFamily =
                                codeFontFamily
                        )
                    }

                    Spacer(
                        Modifier.height(
                            12.dp
                        )
                    )

                    FilledTonalButton(
                        modifier =
                            Modifier.fillMaxWidth(),
                        onClick =
                            onChooseFolder
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    R.drawable.ic_folder_code
                                ),
                            contentDescription =
                                null,
                            modifier =
                                Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            uiText("保存先を変更")
                        )
                    }
                }

                SettingsDivider()

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                18.dp
                            )
                ) {

                    Text(
                        text =
                            uiText("JavaScriptファイル"),
                        style =
                            MaterialTheme
                                .typography
                                .titleSmall,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    Spacer(
                        Modifier.height(
                            4.dp
                        )
                    )

                    Text(
                        text =
                            uiText(".js を作品として読み込む、または現在のコードを書き出します"),
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                        color =
                            colors.onSurfaceVariant
                    )

                    Spacer(
                        Modifier.height(
                            12.dp
                        )
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {

                        OutlinedButton(
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            onClick =
                                onImportJs
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        R.drawable.ic_import_js
                                    ),
                                contentDescription =
                                    null,
                                modifier =
                                    Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Import .js"
                            )
                        }

                        FilledTonalButton(
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            onClick =
                                onExportJs
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        R.drawable.ic_export_js
                                    ),
                                contentDescription =
                                    null,
                                modifier =
                                    Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Export .js"
                            )
                        }
                    }
                }

                SettingsDivider()

                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp)
                ) {
                    Text(
                        text = uiText("作品と設定のバックアップ"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = uiText("すべての作品とエディター設定をZIPで保存・復元します"),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onImportBackup,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_restore),
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(uiText("復元"))
                        }
                        FilledTonalButton(
                            onClick = onExportBackup,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_save),
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(uiText("ZIP保存"))
                        }
                    }
                }
            }

            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Edit:KIRO ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
                Text("rin-code-dev", style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
                TextButton(
                    enabled = !updateViewModel.checking,
                    onClick = { updateViewModel.checkManually() }
                ) { Text(uiText(if (updateViewModel.manualChecking) "確認中…" else "アップデートを確認")) }
                TextButton(onClick = { showLicenses = true }) {
                    Text(uiText("ライセンス情報"))
                }
            }
            Text(
                text = "Edit:KIRO  •  p5.js editor",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 2.dp
                        ),
                style =
                    MaterialTheme
                        .typography
                        .labelSmall,
                color =
                    colors.onSurfaceVariant.copy(
                        alpha = 0.65f
                    )
            )
        }
    }
    }

    @Composable
    private fun SettingsSection(
        title: String,
        description: String,
        content:
            @Composable ColumnScope.() -> Unit
    ) {

        val colors =
            MaterialTheme.colorScheme

        Column(
            verticalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 4.dp
                        ),
                verticalAlignment =
                    Alignment.Bottom
            ) {

                Text(
                    text =
                        title,
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Spacer(
                    Modifier.width(
                        8.dp
                    )
                )

                Text(
                    text =
                        description,
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,
                    color =
                        colors.onSurfaceVariant
                )
            }

            Surface(
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(
                        22.dp
                    ),
                color =
                    colors.surfaceVariant.copy(
                        alpha = 0.58f
                    ),
                border =
                    BorderStroke(
                        1.dp,
                        colors.outlineVariant.copy(
                            alpha = 0.65f
                        )
                    ),
                content = {
                    Column(
                        content =
                            content
                    )
                }
            )
        }
    }

    @Composable
    private fun ThemeChip(
        modifier: Modifier,
        label: String,
        selected: Boolean,
        onClick: () -> Unit
    ) {

        FilterChip(
            modifier =
                modifier,
            selected =
                selected,
            onClick =
                onClick,
            label = {
                Text(
                    text =
                        label,
                    modifier =
                        Modifier.fillMaxWidth(),
                    textAlign =
                        androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        )
    }

    @Composable
    private fun SettingsDivider() {

        HorizontalDivider(
            modifier =
                Modifier.padding(
                    horizontal = 18.dp
                ),
            color =
                MaterialTheme
                    .colorScheme
                    .outlineVariant
                    .copy(
                        alpha = 0.7f
                    )
        )
    }

    @Composable
    private fun SettingSwitchRow(
        title: String,
        description: String,
        checked: Boolean,
        enabled: Boolean = true,
        onCheckedChange:
            (Boolean) -> Unit
    ) {

        val colors =
            MaterialTheme.colorScheme

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                    .padding(
                        horizontal = 18.dp,
                        vertical = 14.dp
                    ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    text =
                        title,
                    style =
                        MaterialTheme
                            .typography
                            .titleSmall,
                    fontWeight =
                        FontWeight.SemiBold,
                    color =
                        if (enabled) {
                            colors.onSurface
                        } else {
                            colors.onSurfaceVariant.copy(
                                alpha = 0.55f
                            )
                        }
                )

                Spacer(
                    Modifier.height(
                        3.dp
                    )
                )

                Text(
                    text =
                        description,
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                    color =
                        colors.onSurfaceVariant.copy(
                            alpha =
                                if (enabled) {
                                    1f
                                } else {
                                    0.55f
                                }
                        )
                )
            }

            Spacer(
                Modifier.width(
                    14.dp
                )
            )

            Switch(
                checked =
                    checked,
                enabled =
                    enabled,
                onCheckedChange = null
            )
        }
    }


    private fun pruneUnusedAssets(works: List<Work>) {
        val local = AtomicFile(File(filesDir, "local-works.json"))
        val localWorks = if (local.baseFile.exists() || File(local.baseFile.path + ".bak").exists()) {
            runCatching { local.openRead().bufferedReader().use { parseWorkStoreJson(it.readText()) } }.getOrNull()?.works
                ?: return
        } else emptyList()
        val keep = (works + localWorks).flatMap { it.assets.values }.map { it.hash }.toSet() +
            previewAssets.assets.values.map { it.hash }
        assetStorage.prune(keep)
    }

    private fun loadLocalWorkStore(): WorkStore? = try {
        val local = AtomicFile(File(filesDir, "local-works.json"))
        if (!local.baseFile.exists() && !File(local.baseFile.path + ".bak").exists()) null else {
            val store = local.openRead().bufferedReader().use { parseWorkStoreJson(it.readText()) }
                ?: error("Invalid local works")
            check(store.works.flatMap { it.assets.values }.all(assetStorage::contains))
            store
        }
    } catch (_: Exception) { unreadableWorkFolders.add("local"); null }

    @Synchronized
    private fun saveLocalWorkStore(works: List<Work>, activeId: String): Boolean {
        if ("local" in unreadableWorkFolders) return false
        return runCatching {
            val json = serializeWorkStore(works, activeId)
            val atomic = AtomicFile(File(filesDir, "local-works.json"))
            val output = atomic.startWrite()
            try { output.write(json.toByteArray(Charsets.UTF_8)); atomic.finishWrite(output) }
            catch (error: Exception) { atomic.failWrite(output); throw error }
        }.isSuccess
    }

    private fun saveFolderAssets(directory: DocumentFile, works: List<Work>) {
        val references = works.flatMap { it.assets.values }.distinctBy { it.hash }
        if (references.isEmpty()) return
        val folder = directory.findFile("assets") ?: directory.createDirectory("assets") ?: error("Cannot create assets")
        references.forEach { asset ->
            check(assetStorage.contains(asset))
            val existing = folder.findFile(asset.hash)
            if (existing == null || existing.length() != asset.size) {
                val document = existing ?: folder.createFile("application/octet-stream", asset.hash) ?: error("Cannot save asset")
                contentResolver.openOutputStream(document.uri, "wt")?.use { output ->
                    assetStorage.file(asset).inputStream().use { copyBounded(it, output, MAX_ASSET_BYTES) }
                } ?: error("Cannot save asset")
            }
        }
    }

    private fun restoreFolderAssets(directory: DocumentFile, works: List<Work>) {
        val references = works.flatMap { it.assets.values }.distinctBy { it.hash }
        if (references.isEmpty()) return
        val folder = directory.findFile("assets") ?: error("Missing assets")
        references.forEach { asset ->
            if (!assetStorage.contains(asset)) {
                val document = folder.findFile(asset.hash) ?: error("Missing asset")
                val loaded = contentResolver.openInputStream(document.uri)?.use { assetStorage.put(it, asset.mime, asset.hash) }
                check(loaded?.size == asset.size)
            }
        }
    }

    private fun loadWorkStore(folderUri: Uri): WorkStore? {
        return try {
            val directory = DocumentFile.fromTreeUri(this, folderUri)
                ?: error(uiText("保存先フォルダーにアクセスできません"))
            check(directory.exists() && directory.canRead()) { uiText("保存先フォルダーを読み込めません") }
            val file = directory.findFile(worksFileName)
            if (file == null) {
                unreadableWorkFolders.remove(folderUri.toString())
                return null
            }
            val json = contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
                ?: error(uiText("作品ファイルを読み込めません"))
            val store = parseWorkStoreJson(json) ?: error(uiText("作品ファイルの形式を読み取れません"))
            check(store.works.isNotEmpty()) { uiText("作品ファイルが空です") }
            restoreFolderAssets(directory, store.works)
            unreadableWorkFolders.remove(folderUri.toString())
            store
        } catch (error: Exception) {
            unreadableWorkFolders.add(folderUri.toString())
            Log.e("EditKIRO", "Failed to load work store; refusing overwrite", error)
            null
        }
    }

    private fun saveWorkStore(
        folderUri: Uri?,
        works: List<Work>,
        activeWorkId: String
    ): Boolean {
        if (folderUri == null) return saveLocalWorkStore(works, activeWorkId)
        if (folderUri.toString() in unreadableWorkFolders) return false
        return try {
            // Serialize before opening the provider's truncating stream.
            val json = serializeWorkStore(works, activeWorkId)
            val directory = DocumentFile.fromTreeUri(this, folderUri) ?: return false
            saveFolderAssets(directory, works)
            val file = directory.findFile(worksFileName)
                ?: directory.createFile("application/json", worksFileName)
                ?: return false
            contentResolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use {
                it.write(json)
            } ?: return false
            true
        } catch (error: Exception) {
            Log.e("EditKIRO", "Failed to save work store", error)
            false
        }
    }

    private fun getFolderName(
        uri: Uri?
    ): String {

        if (uri == null) {
            return uiText("端末内")
        }

        return try {

            DocumentFile
                .fromTreeUri(
                    this,
                    uri
                )
                ?.name
                ?: uiText("選択済みフォルダー")

        } catch (
            _: Exception
        ) {

            uiText("選択済みフォルダー")
        }
    }

    private fun defaultWorks(): List<Work> {
        val now = System.currentTimeMillis()
        return listOf(
            Triple("axis", "Axis", "1:1"),
            Triple("halo", "Halo", "1:1"),
            Triple("gravity", "Gravity", "1:1"),
            Triple("dvd", "dvd", "4:3")
        ).map { (id, title, ratio) ->
            Work(
                id = id,
                title = title,
                code = assets.open("public/samples/$title.js").bufferedReader().use { it.readText() },
                createdAt = now,
                updatedAt = now,
                previewAspectRatio = ratio
            )
        }
    }
}
