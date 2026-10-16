package com.hikariatelier.app

import android.view.inputmethod.EditorInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Forces Android IMEs (Gboard, Samsung Keyboard, Simeji, etc.) to NEVER enter the full-screen
 * white "Extract UI" box in landscape orientation by injecting [EditorInfo.IME_FLAG_NO_EXTRACT_UI]
 * and [EditorInfo.IME_FLAG_NO_FULLSCREEN] into every text input connection inside [content].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DisableExtractUiInputWrapper(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            val modifiedRequest = PlatformTextInputMethodRequest { outAttrs ->
                val connection = request.createInputConnection(outAttrs)
                outAttrs.imeOptions = outAttrs.imeOptions or
                    EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                    EditorInfo.IME_FLAG_NO_FULLSCREEN
                connection
            }
            nextHandler.startInputMethod(modifiedRequest)
        },
        content = content
    )
}

private val PRELOAD_DECLARATION_REGEX = Regex("""\bfunction\s+preload\s*\(""")
private val P5_ASYNC_LOADER_REGEX = Regex("""\b(loadImage|loadSound|loadFont|loadJSON|loadTable|loadStrings|loadBytes|loadXML|loadModel|loadShader)\s*\(""")

internal fun p5ErrorDiagnosticHint(errorMessage: String, sourceCode: String, p5Version: String?): String? {
    if (p5Version != null && p5Version != P5_VERSION_CURRENT) return null
    if (PRELOAD_DECLARATION_REGEX.containsMatchIn(sourceCode)) {
        return "ヒント: p5.js 2.x では preload() は廃止されました。async function setup() 内で await loadImage() などを使用するか、実行環境で p5.js 1.11.5 を選択してください。"
    }
    val lower = errorMessage.lowercase()
    val looksLikePromiseIssue = lower.contains("promise") ||
        lower.contains("is not a function") ||
        lower.contains("cannot read properties of undefined")
    if (looksLikePromiseIssue && P5_ASYNC_LOADER_REGEX.containsMatchIn(sourceCode) && !sourceCode.contains("await ")) {
        return "ヒント: p5.js 2.x の読み込み関数は Promise を返します。async function setup() 内で await を付けて呼び出してください。"
    }
    return null
}

@Composable
internal fun EditorArea(
    activeWork: Work?,
    activeWorkId: String,
    selectedEditorFile: String,
    onSelectEditorFile: (String) -> Unit,
    onOpenSnapshotSheet: () -> Unit,
    editingFile: String,
    editingKey: String,
    editingText: String,
    editingValue: TextFieldValue,
    onUpdateEditingValue: (TextFieldValue) -> Unit,
    onApplyEditorChange: (TextFieldValue) -> Unit,
    editorFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    editorFocusRequester: FocusRequester,
    focusManager: FocusManager,
    consoleEntries: List<ConsoleEntry>,
    sessionViewModel: EditorSessionViewModel,
    codeFontFamily: FontFamily,
    editorFontSize: Float,
    editorWordWrap: Boolean,
    autoIndent: Boolean,
    showLineNumbers: Boolean,
    fontFeatures: String,
    navigationSequence: Int,
    navigationTarget: EditorNavigationTarget?,
    onClearNavigationTarget: () -> Unit,
    colors: ColorScheme,
    textTranslator: (String, Array<out Any?>) -> String,
    readOnly: Boolean = false,
    modifier: Modifier = Modifier,
    errorsCurrent: Boolean = true,
    searchMatches: List<IntRange> = emptyList(),
    inlineSearch: @Composable () -> Unit = {},
    navigationRequestsFocus: Boolean = true,
    previewHasChanges: Boolean = false,
    onRunChanges: () -> Unit = {},
    onCopySample: () -> Unit = {},
    onEditorFontSizeChange: (Float) -> Unit = {},
    showErrorBanner: Boolean = true,
    onJumpToSource: (String, Int) -> Unit = { _, _ -> },
    onOpenProjectFiles: (() -> Unit)? = null,
    watchValues: Map<Int, String> = emptyMap(),
    onSwitchRuntimeVersion: ((String) -> Unit)? = null,
    devicePreviewRatio: Float = 1f
) {
    val copyToClipboard = rememberClipboardCopy()
    val currentFontSize = rememberUpdatedState(editorFontSize)
    val currentFontSizeChange = rememberUpdatedState(onEditorFontSizeChange)
    var pinchFontSize by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(editorFontSize) {
        pinchFontSize = null
    }
    val effectiveFontSize = pinchFontSize ?: editorFontSize

    // Auto-Run debounced trigger when enabled in EditorSessionViewModel
    AutoRunHotSwapEffect(
        enabled = sessionViewModel.autoRunEnabled,
        editingText = editingText,
        previewHasChanges = previewHasChanges,
        readOnly = readOnly,
        onRunChanges = onRunChanges
    )

    val darkEditorTheme = colors.surface.luminance() < 0.5f
    val dirtyFiles by remember(activeWorkId, activeWork?.code, activeWork?.files, sessionViewModel) {
        derivedStateOf {
            dirtyProjectFiles(
                activeWorkId,
                activeWork?.code.orEmpty(),
                sessionViewModel.editorValueState.value.text,
                activeWork?.files.orEmpty(),
                sessionViewModel.fileDrafts
            )
        }
    }
    LaunchedEffect(readOnly) { if (readOnly) focusManager.clearFocus(force = true) }

    var errorTooltipLine by remember(editingKey, errorsCurrent) { mutableStateOf<Int?>(null) }
    val editorErrorLines by remember(editingFile, activeWorkId, errorsCurrent, consoleEntries) {
        derivedStateOf {
            if (!errorsCurrent) emptySet() else consoleEntries.asSequence()
                .filter { it.level == ConsoleLevel.ERROR && it.file == editingFile && it.workId == activeWorkId }
                .mapNotNull { it.line }
                .toSet()
        }
    }

    val changes = remember(editingKey) { EditorChangeTracker() }
    val sharedChange = changes.update(editingValue)
    val javascriptHighlighter = editorHighlight(editingText, darkEditorTheme, editorErrorLines, editingFile, editingKey, sharedChange)

    val foldSnapshot = if (isJavaScriptProjectFile(editingFile) ||
        projectTextSyntax(editingFile) == ProjectTextSyntax.SHADER) editorFoldRegions(editingValue, editingKey, change = sharedChange)
    else EditorFoldSnapshot(emptyList(), null, null)
    val parsedFoldRegions = foldSnapshot.regions
    val foldRegions = parsedFoldRegions.orEmpty()
    val hasFolds = foldRegions.isNotEmpty()
    val storedFolds = sessionViewModel.codeFoldStates[editingKey]
    val collapsedFolds = remember(storedFolds, editingText, parsedFoldRegions) {
        if (parsedFoldRegions == null) emptySet()
        else {
            val retained = if (storedFolds != null && storedFolds.source == foldSnapshot.previousSource &&
                foldSnapshot.change != null && storedFolds.collapsed.isNotEmpty()) {
                shiftedFoldRegions(
                    storedFolds.regions.orEmpty().filter { it.open in storedFolds.collapsed },
                    foldSnapshot.change
                ).map { it.open }.toSet()
            } else rebasedFolds(storedFolds, editingText)
            if (retained.isEmpty()) emptySet() else retained.intersect(foldRegions.map { it.open }.toSet())
        }
    }
    SideEffect {
        if (parsedFoldRegions != null && storedFolds != null &&
            (storedFolds.source != editingText || storedFolds.collapsed != collapsedFolds || storedFolds.regions != foldRegions)) {
            sessionViewModel.codeFoldStates[editingKey] = CodeFoldState(editingText, collapsedFolds, foldRegions)
        }
    }
    val projection = remember(editingText, foldRegions, collapsedFolds) {
        FoldProjection(editingText, foldRegions, collapsedFolds)
    }
    val displayText = remember(projection) { projection.transform(AnnotatedString(editingText)).text.text }
    val searchColor = colors.primary.copy(alpha = 0.18f)
    val visibleSearchMatches = remember(searchMatches, editingValue.selection) {
        if (searchMatches.size <= 2000) searchMatches else {
            val found = searchMatches.binarySearch { it.first.compareTo(editingValue.selection.min) }
            val anchor = if (found >= 0) found else -found - 1
            val start = (anchor - 1000).coerceIn(0, searchMatches.size - 2000)
            searchMatches.subList(start, start + 2000)
        }
    }
    val foldedHighlighter = remember(javascriptHighlighter, projection, visibleSearchMatches, searchColor) {
        CachedEditorTransformation { text ->
            val syntax = javascriptHighlighter.filter(text).text
            val highlighted = if (visibleSearchMatches.isEmpty()) syntax else AnnotatedString.Builder(syntax).apply {
                visibleSearchMatches.forEach { range ->
                    if (range.first >= 0 && range.last < text.length && !range.isEmpty())
                        addStyle(SpanStyle(background = searchColor), range.first, range.last + 1)
                }
            }.toAnnotatedString()
            projection.transform(highlighted)
        }
    }
    LaunchedEffect(editingKey, editingValue.selection, collapsedFolds) {
        val selection = editingValue.selection
        val reveal = foldRegions.filter {
            it.open in collapsedFolds &&
                (if (selection.collapsed) selection.start > it.open + 1 && selection.start < it.close
                else selection.min < it.close && selection.max > it.open + 1)
        }.map { it.open }.toSet()
        if (reveal.isNotEmpty()) sessionViewModel.codeFoldStates[editingKey] =
            CodeFoldState(editingText, collapsedFolds - reveal, foldRegions)
    }
    val toggleFold: (CodeFold) -> Unit = { fold ->
        toggleEditorFold(editingText, editingValue, collapsedFolds, foldRegions, fold)?.let { toggle ->
            if (toggle.value != editingValue) onUpdateEditingValue(toggle.value)
            sessionViewModel.codeFoldStates[editingKey] = toggle.state
        }
    }
    val currentToggleFold by rememberUpdatedState(toggleFold)
    var editorTextLayout by remember(editingKey) {
        mutableStateOf<TextLayoutResult?>(null)
    }

    val lineIndex = remember(editingKey) { EditorLineIndex() }
    val logicalLineStarts = remember(editingText, lineIndex) { lineIndex.update(editingText, sharedChange) }
    val activeLineNumber = remember(editingValue.selection.start, logicalLineStarts, editorFocused) {
        if (!editorFocused) -1
        else {
            val pos = editingValue.selection.start.coerceIn(0, editingText.length)
            val idx = logicalLineStarts.binarySearch(pos)
            (if (idx >= 0) idx else -idx - 2).coerceAtLeast(0) + 1
        }
    }

    val foldsByLine = remember(foldRegions, logicalLineStarts) {
        foldRegions.groupBy { fold ->
            logicalLineStarts.binarySearch(fold.open).let { if (it >= 0) it else -it - 2 }
        }.mapValues { (_, regions) -> regions.first() }
    }
    val lineNumberDigits = maxOf(1, logicalLineStarts.size.toString().length)

    Card(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var startDistance = 0f
                var startSize = currentFontSize.value
                var pendingSize: Float? = null
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.size >= 2) {
                        val distance = (pressed[0].position - pressed[1].position).getDistance()
                        if (startDistance <= 0f) {
                            startDistance = distance
                            startSize = pinchFontSize ?: currentFontSize.value
                        } else if (distance > 0f) {
                            val next = (startSize * distance / startDistance).coerceIn(12f, 28f).roundToInt().toFloat()
                            if (next != (pinchFontSize ?: currentFontSize.value)) {
                                pinchFontSize = next
                                pendingSize = next
                            }
                        }
                        event.changes.forEach { it.consume() }
                    } else {
                        startDistance = 0f
                    }
                } while (event.changes.any { it.pressed })
                val committed = pendingSize
                if (committed != null && committed != currentFontSize.value) {
                    currentFontSizeChange.value(committed)
                }
            }
        }.border(
            width = 1.dp,
            color = colors.outlineVariant,
            shape = RoundedCornerShape(18.dp)
        ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        DisableExtractUiInputWrapper {
            Column(Modifier.fillMaxSize()) {
                if (sessionViewModel.tweetModeEnabled) {
                    TweetModeStatusBar(
                        sourceText = editingText,
                        targetLimit = sessionViewModel.tweetTargetLimit,
                        codeFontFamily = codeFontFamily,
                        onTargetLimitChange = { sessionViewModel.tweetTargetLimit = it },
                        onOpenMinifySheet = { sessionViewModel.showMinifySheet = true },
                        onClose = { sessionViewModel.tweetModeEnabled = false },
                        colors = colors,
                        textTranslator = textTranslator
                    )
                }
                if (activeWork?.isSample == true) Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        textTranslator("サンプル・閲覧専用", emptyArray()),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onCopySample, enabled = !sessionViewModel.assetBusy) {
                        Text(textTranslator("コピーして編集", emptyArray()))
                    }
                }
                val projectFiles = listOf("sketch.js") + activeWork?.files.orEmpty().keys.sorted()
                val hasMultipleFiles = projectFiles.size > 1
                if (hasMultipleFiles) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            FileTabs(
                                projectFiles,
                                selectedEditorFile,
                                dirtyFiles = dirtyFiles,
                                unsavedDescription = textTranslator("未保存の変更あり", emptyArray()),
                                enabled = !sessionViewModel.editorInputLocked
                            ) {
                                if (selectedEditorFile != it) {
                                    focusManager.clearFocus(force = true)
                                    onFocusChange(false)
                                    onSelectEditorFile(it)
                                }
                            }
                        }
                        if (!readOnly && onOpenProjectFiles != null) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !sessionViewModel.editorInputLocked) { onOpenProjectFiles() }
                                    .semantics { contentDescription = textTranslator("ファイルを追加", emptyArray()) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_add),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                inlineSearch()
                if (!errorsCurrent && consoleEntries.any { it.level == ConsoleLevel.ERROR && it.workId == activeWorkId }) {
                    Text(
                        textTranslator("前回の実行時に発生したエラーです", emptyArray()),
                        Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.tertiary
                    )
                }
                val latestError = if (showErrorBanner && errorsCurrent) {
                    consoleEntries.lastOrNull { it.level == ConsoleLevel.ERROR && it.workId == activeWorkId }
                } else null
                if (latestError != null) {
                    InlineDiagnosticBanner(
                        latestError = latestError,
                        editingFile = editingFile,
                        editingValue = editingValue,
                        p5Version = activeWork?.p5Version,
                        codeFontFamily = codeFontFamily,
                        colors = colors,
                        textTranslator = textTranslator,
                        onJumpToSource = onJumpToSource,
                        onApplyQuickFixCode = { fixedValue ->
                            onApplyEditorChange(fixedValue)
                            onRunChanges()
                        },
                        onSwitchRuntimeVersion = onSwitchRuntimeVersion,
                        onCopyError = { copyToClipboard(it) }
                    )
                }
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val editorScrollState = remember(editingKey) { sessionViewModel.editorScroll(editingKey).vertical }
                    val editorHorizontalScrollState = remember(editingKey) { sessionViewModel.editorScroll(editingKey).horizontal }
                    val localDensity = LocalDensity.current
                    val gutterMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
                    val singleDigitAndFoldWidth = remember(gutterMeasurer, codeFontFamily, effectiveFontSize, localDensity) {
                        val style = TextStyle(
                            fontFamily = codeFontFamily,
                            fontSize = effectiveFontSize.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val maxDigit = ('0'..'9').maxOf { digit ->
                            gutterMeasurer.measure(AnnotatedString(digit.toString()), style = style, softWrap = false).size.width
                        }
                        val foldArrow = gutterMeasurer.measure(AnnotatedString("▾"), style = style, softWrap = false).size.width
                        maxDigit to foldArrow
                    }
                    val gutterPixels = (if (hasFolds) singleDigitAndFoldWidth.second else 0) +
                        (if (showLineNumbers) singleDigitAndFoldWidth.first * lineNumberDigits else 0)
                    val gutterWidth = with(localDensity) { gutterPixels.toDp() } + 10.dp
                    val viewportWidth = with(localDensity) { (maxWidth - gutterWidth - 30.dp).toPx().coerceAtLeast(24.dp.toPx()) }
                    val viewportHeightPx = with(localDensity) { maxHeight.toPx() }

                    LaunchedEffect(navigationSequence, editingKey, editorTextLayout, projection) {
                        val target = navigationTarget ?: return@LaunchedEffect
                        val layout = editorTextLayout ?: return@LaunchedEffect
                        if (target.file != editingFile || layout.layoutInput.text.text != displayText) return@LaunchedEffect
                        val offset = target.sourceOffset(editingText) ?: return@LaunchedEffect
                        val containing = foldRegions.filter { it.open in collapsedFolds && offset > it.open && offset < it.close }
                        if (containing.isNotEmpty()) {
                            sessionViewModel.codeFoldStates[editingKey] = CodeFoldState(
                                editingText,
                                collapsedFolds - containing.map { it.open }.toSet(),
                                foldRegions
                            )
                            return@LaunchedEffect
                        }
                        if (navigationRequestsFocus) editorFocusRequester.requestFocus()
                        editorScrollState.scrollTo(layout.getLineTop(layout.getLineForOffset(projection.originalToTransformed(offset))).toInt())
                        if (!editorWordWrap) {
                            val cursor = layout.getCursorRect(projection.originalToTransformed(offset))
                            val left = editorHorizontalScrollState.value
                            if (cursor.right > left + viewportWidth) {
                                editorHorizontalScrollState.scrollTo((cursor.right - viewportWidth).toInt().coerceAtLeast(0))
                            } else if (cursor.left < left) {
                                editorHorizontalScrollState.scrollTo(cursor.left.toInt().coerceAtLeast(0))
                            }
                        }
                        onClearNavigationTarget()
                    }
                    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    val density = LocalDensity.current
                    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
                    val useFloatingPip = !isLandscape && sessionViewModel.pipModeEnabled && (editorFocused || keyboardVisible)
                    val isPipAtBottom = sessionViewModel.pipSnapCorner == PipCorner.BOTTOM_START ||
                        sessionViewModel.pipSnapCorner == PipCorner.BOTTOM_END

                    val pipSafeRatio = previewAspectRatioValue(activeWork?.previewAspectRatio ?: "1:1", devicePreviewRatio).coerceIn(0.45f, 2.2f)
                    val pipBaseSize = sessionViewModel.pipSizePreset.baseDp
                    val pipCanvasHeightDp = if (pipSafeRatio >= 1f) (pipBaseSize / pipSafeRatio) else pipBaseSize.toFloat()
                    val pipCardHeightDp = if (sessionViewModel.pipMinimized) 32.dp else (pipCanvasHeightDp.dp + 32.dp)

                    val pipBottomPadding = if (useFloatingPip && isPipAtBottom) {
                        pipCardHeightDp + 16.dp
                    } else 0.dp

                    DisposableEffect(useFloatingPip) {
                        onDispose {
                            sessionViewModel.pipAutoGhosted = false
                        }
                    }

                    // Synchronous auto-ghost evaluation: reactive to scroll, cursor, focus, and watch value changes
                    val isOverlapping = remember(
                        useFloatingPip,
                        sessionViewModel.pipSnapCorner,
                        sessionViewModel.pipSizePreset,
                        sessionViewModel.pipMinimized,
                        editorScrollState.value,
                        editorHorizontalScrollState.value,
                        editingValue.selection,
                        editorFocused,
                        watchValues.toMap(),
                        editorTextLayout,
                        maxWidth,
                        maxHeight
                    ) {
                        if (!useFloatingPip) return@remember false

                        val containerWidthPx = with(localDensity) { maxWidth.toPx() }
                        val containerHeightPx = with(localDensity) { maxHeight.toPx() }
                        val pipCanvasWidthDp = if (pipSafeRatio >= 1f) pipBaseSize.dp else (pipBaseSize * pipSafeRatio).dp
                        val pipWidthDp = if (sessionViewModel.pipMinimized) 138.dp else maxOf(138.dp, pipCanvasWidthDp)
                        val pipWidthPx = with(localDensity) { pipWidthDp.toPx() }
                        val pipHeightPx = with(localDensity) { pipCardHeightDp.toPx() }
                        val marginPx = with(localDensity) { 8.dp.toPx() }

                        val pipLeft = when (sessionViewModel.pipSnapCorner) {
                            PipCorner.TOP_START, PipCorner.BOTTOM_START -> marginPx
                            PipCorner.TOP_END, PipCorner.BOTTOM_END -> (containerWidthPx - pipWidthPx - marginPx).coerceAtLeast(0f)
                        }
                        val pipTop = when (sessionViewModel.pipSnapCorner) {
                            PipCorner.TOP_START, PipCorner.TOP_END -> marginPx
                            PipCorner.BOTTOM_START, PipCorner.BOTTOM_END -> (containerHeightPx - pipHeightPx - marginPx).coerceAtLeast(0f)
                        }
                        val pipRight = pipLeft + pipWidthPx
                        val pipBottom = pipTop + pipHeightPx

                        var overlapping = false
                        val layout = editorTextLayout

                        // Check watch badges overlap (badges are on the right edge)
                        val isRightEdgePip = sessionViewModel.pipSnapCorner == PipCorner.TOP_END ||
                            sessionViewModel.pipSnapCorner == PipCorner.BOTTOM_END
                        if (isRightEdgePip && watchValues.isNotEmpty() && layout != null) {
                            val badgePad = with(localDensity) { 16.dp.toPx() }
                            val badgeH = with(localDensity) { 24.dp.toPx() }
                            for ((lineNum, _) in watchValues) {
                                val lineIdx = lineNum - 1
                                val origOffset = logicalLineStarts.getOrNull(lineIdx) ?: continue
                                if (projection.isHidden(origOffset)) continue
                                val transOffset = projection.originalToTransformed(origOffset)
                                    .coerceIn(0, layout.layoutInput.text.length)
                                val visualLine = layout.getLineForOffset(transOffset)
                                val badgeTop = layout.getLineTop(visualLine) - editorScrollState.value + badgePad
                                val badgeBottom = badgeTop + badgeH
                                if (badgeBottom >= pipTop && badgeTop <= pipBottom) {
                                    overlapping = true
                                    break
                                }
                            }
                        }

                        // Check cursor overlap with text padding factored in
                        if (!overlapping && layout != null && editorFocused) {
                            val pad = with(localDensity) { 16.dp.toPx() }
                            val textStartPad = with(localDensity) { (if (showLineNumbers || hasFolds) 8.dp else 14.dp).toPx() }
                            val gutterPx = with(localDensity) { gutterWidth.toPx() } + textStartPad
                            val cursorW = with(localDensity) { 24.dp.toPx() }
                            val cursorOffset = projection.originalToTransformed(editingValue.selection.end.coerceIn(0, editingText.length))
                                .coerceIn(0, layout.layoutInput.text.length)
                            val cursorVisualLine = layout.getLineForOffset(cursorOffset)
                            val cursorLineTop = layout.getLineTop(cursorVisualLine) - editorScrollState.value + pad
                            val cursorLineBottom = layout.getLineBottom(cursorVisualLine) - editorScrollState.value + pad
                            val cursorLeft = gutterPx + layout.getHorizontalPosition(cursorOffset, true) - editorHorizontalScrollState.value
                            val cursorRight = cursorLeft + cursorW

                            val vOverlap = cursorLineBottom >= pipTop && cursorLineTop <= pipBottom
                            val hOverlap = cursorRight >= pipLeft && cursorLeft <= pipRight
                            if (vOverlap && hOverlap) {
                                overlapping = true
                            }
                        }

                        overlapping
                    }

                    SideEffect {
                        if (sessionViewModel.pipAutoGhosted != isOverlapping) {
                            sessionViewModel.pipAutoGhosted = isOverlapping
                        }
                    }

                    val containerMaxHeight = maxHeight
                    val contentMinHeight = (containerMaxHeight - 32.dp + pipBottomPadding).coerceAtLeast(0.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(editorScrollState)
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        if (showLineNumbers || hasFolds) {
                            EditorGutter(
                                documentKey = editingKey,
                                layout = editorTextLayout,
                                displayText = displayText,
                                projection = projection,
                                starts = logicalLineStarts,
                                folds = foldsByLine,
                                collapsed = collapsedFolds,
                                errors = editorErrorLines,
                                numbers = showLineNumbers,
                                digits = lineNumberDigits,
                                width = gutterWidth,
                                minimumHeight = contentMinHeight,
                                viewportHeight = viewportHeightPx,
                                scroll = editorScrollState,
                                style = TextStyle(
                                    fontFamily = codeFontFamily,
                                    fontSize = effectiveFontSize.sp,
                                    lineHeight = (effectiveFontSize * 1.55f).sp,
                                    color = colors.onSurfaceVariant.copy(alpha = 0.38f)
                                ),
                                errorColor = colors.error,
                                dividerColor = Color.Transparent,
                                activeLine = activeLineNumber,
                                activeColor = colors.primary,
                                onFold = currentToggleFold,
                                onError = { errorTooltipLine = it },
                                foldDescription = { closed, line ->
                                    textTranslator(if (closed) "%s行目を展開" else "%s行目を折りたたむ", arrayOf(line))
                                }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (!editorWordWrap) Modifier.horizontalScroll(editorHorizontalScrollState)
                                    else Modifier
                                )
                        ) {
                            BasicTextField(
                                value = editingValue,
                                readOnly = readOnly,
                                onValueChange = { next ->
                                    dispatchEditorValueChange(editingValue, next, readOnly, onUpdateEditingValue) {
                                        if (editingValue.selection.collapsed &&
                                            deletesFoldedCode(editingText, it.text, projection.hidden)) {
                                            sessionViewModel.codeFoldStates[editingKey] = CodeFoldState(editingText, emptySet())
                                        } else onApplyEditorChange(
                                            if (autoIndent && (isJavaScriptProjectFile(editingFile) ||
                                                    projectTextSyntax(editingFile) == ProjectTextSyntax.SHADER) &&
                                                editingValue.composition == null && it.composition == null) {
                                                applyAutomaticIndent(editingValue, it)
                                            } else {
                                                it
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .then(
                                        if (editorWordWrap) Modifier.fillMaxWidth()
                                        else Modifier.wrapContentWidth()
                                    )
                                    .heightIn(min = contentMinHeight)
                                    .padding(
                                        start = if (showLineNumbers || hasFolds) 8.dp else 14.dp,
                                        end = 16.dp,
                                        bottom = 28.dp + pipBottomPadding
                                    )
                                    .focusRequester(editorFocusRequester)
                                    .onFocusChanged {
                                        onFocusChange(it.isFocused)
                                    },
                                textStyle = TextStyle(
                                    fontFeatureSettings = fontFeatures,
                                    fontFamily = codeFontFamily,
                                    fontSize = effectiveFontSize.sp,
                                    lineHeight = (effectiveFontSize * 1.55f).sp,
                                    color = colors.onSurface
                                ),
                                visualTransformation = foldedHighlighter,
                                onTextLayout = { if (!it.hasSameEditorLines(editorTextLayout)) editorTextLayout = it },
                                cursorBrush = SolidColor(colors.primary)
                            )
                        }
                    }

                    // Live Watch Overlay Badges (`// ?` or `rinWatch`)
                    LiveWatchOverlayColumn(
                        watchValues = watchValues,
                        logicalLineStarts = logicalLineStarts,
                        projection = projection,
                        textLayout = editorTextLayout,
                        scrollValue = editorScrollState.value,
                        viewportHeightPx = viewportHeightPx,
                        codeFontFamily = codeFontFamily,
                        colors = colors
                    )

                    // Bottom-Right Flat Status, Auto-Run & Run Changes Bar (Unified RoundedCornerShape(10.dp))
                    val showRunAction = !readOnly && previewHasChanges
                    val statsText = "${editingText.length}c · ${logicalLineStarts.size}L"
                    if (showRunAction || editorFocused || sessionViewModel.autoRunEnabled) {
                        val itemShape = RoundedCornerShape(10.dp)
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 10.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!readOnly) {
                                val autoActive = sessionViewModel.autoRunEnabled
                                Box(
                                    modifier = Modifier
                                        .height(28.dp)
                                        .clip(itemShape)
                                        .background(
                                            if (autoActive) colors.primary.copy(alpha = 0.14f)
                                            else colors.surface.copy(alpha = 0.90f)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (autoActive) colors.primary else colors.outlineVariant.copy(alpha = 0.7f),
                                            shape = itemShape
                                        )
                                        .clickable { sessionViewModel.autoRunEnabled = !sessionViewModel.autoRunEnabled }
                                        .padding(horizontal = 8.dp)
                                        .semantics { contentDescription = textTranslator("自動実行を切り替え", emptyArray()) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_auto_run),
                                            contentDescription = null,
                                            tint = if (autoActive) colors.primary else colors.onSurfaceVariant,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        if (autoActive) {
                                            Text(
                                                text = "AUTO",
                                                fontFamily = codeFontFamily,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.primary
                                            )
                                        }
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .height(28.dp)
                                    .clip(itemShape)
                                    .background(
                                        if (showRunAction) colors.tertiary.copy(alpha = 0.14f)
                                        else colors.surface.copy(alpha = 0.90f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (showRunAction) colors.tertiary else colors.outlineVariant.copy(alpha = 0.7f),
                                        shape = itemShape
                                    )
                                    .then(
                                        if (showRunAction) Modifier.clickable { onRunChanges() } else Modifier
                                    )
                                    .padding(horizontal = 9.dp)
                                    .semantics {
                                        if (showRunAction) contentDescription = textTranslator("変更を実行", emptyArray())
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    if (showRunAction) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_play),
                                            contentDescription = null,
                                            tint = colors.tertiary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = textTranslator("変更を実行", emptyArray()),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.tertiary
                                        )
                                        Text(
                                            text = "· $statsText",
                                            fontFamily = codeFontFamily,
                                            fontSize = 10.sp,
                                            color = colors.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    } else {
                                        Text(
                                            text = statsText,
                                            fontFamily = codeFontFamily,
                                            fontSize = 10.sp,
                                            color = colors.onSurfaceVariant.copy(alpha = 0.75f)
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
    if (errorsCurrent && errorTooltipLine != null) {
        val errorDetail = consoleEntries.lastOrNull {
            it.level == ConsoleLevel.ERROR && it.file == editingFile && it.workId == activeWorkId && it.line == errorTooltipLine
        }
        if (errorDetail != null) {
            val hintKey = p5ErrorDiagnosticHint(errorDetail.message, editingText, activeWork?.p5Version)
            EditSettingsDialog(
                onDismissRequest = { errorTooltipLine = null },
                title = { Text(textTranslator("エラー詳細", emptyArray())) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(errorDetail.message, fontFamily = codeFontFamily, style = MaterialTheme.typography.bodySmall)
                        if (hintKey != null) {
                            Text(
                                text = textTranslator(hintKey, emptyArray()),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { errorTooltipLine = null }) {
                        Text(textTranslator("閉じる", emptyArray()))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        copyToClipboard(errorDetail.copyText())
                        errorTooltipLine = null
                    }) {
                        Text(textTranslator("エラーをコピー", emptyArray()))
                    }
                }
            )
        }
    }
}

@Composable
internal fun TweetModeStatusBar(
    sourceText: String,
    targetLimit: Int,
    codeFontFamily: FontFamily,
    onTargetLimitChange: (Int) -> Unit,
    onOpenMinifySheet: () -> Unit,
    onClose: () -> Unit,
    colors: ColorScheme,
    textTranslator: (String, Array<out Any?>) -> String,
    modifier: Modifier = Modifier
) {
    fun uiText(source: String, vararg args: Any?) = textTranslator(source, args)

    val golfedCount by remember(sourceText) {
        derivedStateOf { minifyJavaScriptForTweet(sourceText).length }
    }

    val isOver = golfedCount > targetLimit
    val isWarning = !isOver && golfedCount > targetLimit - 15
    val delta = targetLimit - golfedCount

    val accentColor = when {
        isOver -> colors.error
        isWarning -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    val progressFraction = (golfedCount.toFloat() / targetLimit.toFloat()).coerceIn(0f, 1f)
    val pillShape = RoundedCornerShape(8.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Ring + Counter Pill (Tap to toggle 140 / 280)
            Row(
                modifier = Modifier
                    .clip(pillShape)
                    .clickable { onTargetLimitChange(if (targetLimit == 140) 280 else 140) }
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier.size(15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        color = colors.outlineVariant.copy(alpha = 0.35f),
                        strokeWidth = 2.dp
                    )
                    CircularProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier.fillMaxSize(),
                        color = accentColor,
                        strokeWidth = 2.dp
                    )
                }

                Text(
                    text = "$golfedCount/$targetLimit",
                    fontFamily = codeFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isOver) colors.error else colors.onSurface,
                    maxLines = 1,
                    softWrap = false
                )

                Text(
                    text = if (delta >= 0) "(-$delta)" else "(+${-delta})",
                    fontFamily = codeFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Right: Compact "Golf" Action + Close Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .clip(pillShape)
                        .background(colors.primary.copy(alpha = 0.14f))
                        .border(1.dp, colors.primary.copy(alpha = 0.65f), pillShape)
                        .clickable { onOpenMinifySheet() }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Golf",
                        fontFamily = codeFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onClose() }
                        .semantics { contentDescription = uiText("閉じる") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = null,
                        tint = colors.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // 1.5dp bottom progress hairline
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(colors.outlineVariant.copy(alpha = 0.22f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progressFraction)
                    .background(accentColor)
            )
        }
    }
}
