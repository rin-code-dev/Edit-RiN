package com.hikariatelier.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

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
    onOpenProjectFiles: (() -> Unit)? = null
) {
    val copyToClipboard = rememberClipboardCopy()
    val currentFontSize = rememberUpdatedState(editorFontSize)
    val currentFontSizeChange = rememberUpdatedState(onEditorFontSizeChange)
    var pinchFontSize by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(editorFontSize) {
        pinchFontSize = null
    }
    val effectiveFontSize = pinchFontSize ?: editorFontSize

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
    var errorBannerExpanded by remember(activeWorkId, errorsCurrent) { mutableStateOf(false) }
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
        Column(Modifier.fillMaxSize()) {
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
                        IconButton(
                            onClick = onOpenProjectFiles,
                            enabled = !sessionViewModel.editorInputLocked,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = textTranslator("ファイルを追加", emptyArray()),
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
                val errorFile = latestError.file ?: editingFile
                val errorLine = latestError.line
                val hintKey = remember(latestError.message, editingText, activeWork?.p5Version) {
                    p5ErrorDiagnosticHint(latestError.message, editingText, activeWork?.p5Version)
                }
                Surface(
                    onClick = {
                        if (errorLine != null) onJumpToSource(errorFile, errorLine)
                        else errorBannerExpanded = !errorBannerExpanded
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = colors.errorContainer,
                    contentColor = colors.onErrorContainer
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = (if (errorLine != null) "${errorFile}:$errorLine  " else "") +
                                    latestError.message.lineSequence().firstOrNull().orEmpty(),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = if (errorBannerExpanded) 6 else 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (errorLine != null) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    textTranslator("移動", emptyArray()),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = { errorBannerExpanded = !errorBannerExpanded },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Text(
                                    text = if (errorBannerExpanded) "▴" else "▾",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        AnimatedVisibility(visible = errorBannerExpanded || hintKey != null) {
                            Column(
                                modifier = Modifier.padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (errorBannerExpanded && latestError.message.lines().size > 1) {
                                    Text(
                                        text = latestError.message,
                                        fontFamily = codeFontFamily,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (hintKey != null) {
                                    Text(
                                        text = textTranslator(hintKey, emptyArray()),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                if (errorBannerExpanded) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = { copyToClipboard(latestError.copyText()) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text(
                                                textTranslator("エラーをコピー", emptyArray()),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.onErrorContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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
                val containerMaxHeight = maxHeight
                val contentMinHeight = (containerMaxHeight - 32.dp).coerceAtLeast(0.dp)

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
                            viewportHeight = with(localDensity) { containerMaxHeight.toPx() },
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
                                    bottom = 28.dp
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

                // Bottom-Right Minimal Floating Status & Run Changes Pill (Zero layout shift)
                val showRunAction = !readOnly && previewHasChanges
                val statsText = "${editingText.length}c · ${logicalLineStarts.size}L"
                if (showRunAction || editorFocused) {
                    Surface(
                        onClick = { if (showRunAction) onRunChanges() },
                        enabled = showRunAction,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 10.dp, bottom = 8.dp)
                            .semantics {
                                if (showRunAction) contentDescription = textTranslator("変更を実行", emptyArray())
                            },
                        shape = RoundedCornerShape(999.dp),
                        color = if (showRunAction) colors.tertiaryContainer else colors.surface.copy(alpha = 0.85f),
                        contentColor = if (showRunAction) colors.onTertiaryContainer else colors.onSurfaceVariant.copy(alpha = 0.65f),
                        border = BorderStroke(
                            1.dp,
                            if (showRunAction) colors.tertiary.copy(alpha = 0.5f) else colors.outlineVariant.copy(alpha = 0.5f)
                        ),
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (showRunAction) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_play),
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = textTranslator("変更を実行", emptyArray()),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "· $statsText",
                                    fontFamily = codeFontFamily,
                                    fontSize = 10.sp,
                                    color = colors.onTertiaryContainer.copy(alpha = 0.75f)
                                )
                            } else {
                                Text(
                                    text = statsText,
                                    fontFamily = codeFontFamily,
                                    fontSize = 10.sp
                                )
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
