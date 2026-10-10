package com.hikariatelier.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    onJumpToSource: (String, Int) -> Unit = { _, _ -> }
) {
    val currentFontSize = rememberUpdatedState(editorFontSize)
    val currentFontSizeChange = rememberUpdatedState(onEditorFontSizeChange)
    val darkEditorTheme = colors.surface.luminance() < 0.5f
    val dirtyFiles by remember(activeWorkId, activeWork?.code, activeWork?.files, sessionViewModel) {
        derivedStateOf {
            dirtyProjectFiles(activeWorkId, activeWork?.code.orEmpty(),
                sessionViewModel.editorValueState.value.text, activeWork?.files.orEmpty(), sessionViewModel.fileDrafts)
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

    val currentEditorErrorLines by rememberUpdatedState(editorErrorLines)
    val javascriptHighlighter = editorHighlight(editingText, darkEditorTheme, editorErrorLines, editingFile, editingKey)

    val foldSnapshot = if (isJavaScriptProjectFile(editingFile) ||
        projectTextSyntax(editingFile) == ProjectTextSyntax.SHADER) editorFoldRegions(editingValue, editingKey)
        else EditorFoldSnapshot(emptyList(), null, null)
    val parsedFoldRegions = foldSnapshot.regions
    val foldRegions = parsedFoldRegions.orEmpty()
    val storedFolds = sessionViewModel.codeFoldStates[editingKey]
    val collapsedFolds = remember(storedFolds, editingText, parsedFoldRegions) {
        if (parsedFoldRegions == null) emptySet()
        else {
            val retained = if (storedFolds != null && storedFolds.source == foldSnapshot.previousSource &&
                foldSnapshot.change != null && storedFolds.collapsed.isNotEmpty()) {
                shiftedFoldRegions(storedFolds.regions.orEmpty().filter { it.open in storedFolds.collapsed },
                    foldSnapshot.change).map { it.open }.toSet()
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
        val reveal = foldRegions.filter { it.open in collapsedFolds &&
            (if (selection.collapsed) selection.start > it.open + 1 && selection.start < it.close
            else selection.min < it.close && selection.max > it.open + 1) }.map { it.open }.toSet()
        if (reveal.isNotEmpty()) sessionViewModel.codeFoldStates[editingKey] =
            CodeFoldState(editingText, collapsedFolds - reveal, foldRegions)
    }
    // A local function reference (::toggleFold) compares equal across recompositions,
    // so rememberUpdatedState can retain the initial source/regions/collapsed set.
    // Use a capturing lambda whose identity follows those changing values instead.
    val toggleFold: (CodeFold) -> Unit = { fold ->
        toggleEditorFold(editingText, editingValue, collapsedFolds, foldRegions, fold)?.let { toggle ->
            if (toggle.value != editingValue) onUpdateEditingValue(toggle.value)
            sessionViewModel.codeFoldStates[editingKey] = toggle.state
        }
    }
    val currentToggleFold by rememberUpdatedState(toggleFold)
    var gutterLayout by remember(editingKey) { mutableStateOf<TextLayoutResult?>(null) }
    var editorTextLayout by remember(editingKey) {
        mutableStateOf<TextLayoutResult?>(null)
    }

    val lineIndex = remember(editingKey) { EditorLineIndex() }
    val logicalLineStarts = remember(editingText, lineIndex) { lineIndex.update(editingText) }

    val foldsByLine = remember(foldRegions, logicalLineStarts) {
        foldRegions.groupBy { fold ->
            logicalLineStarts.binarySearch(fold.open).let { if (it >= 0) it else -it - 2 }
        }.mapValues { (_, regions) -> regions.first() }
    }
    // Keep the gesture detector alive while text/layout/highlighting updates arrive.
    // Restarting pointerInput between down/up silently cancels the user's tap.
    val currentGutterTap by rememberUpdatedState<(androidx.compose.ui.geometry.Offset) -> Unit>({ position ->
        val gutter = gutterLayout
        val layout = editorTextLayout
        if (gutter != null && layout != null && layout.layoutInput.text.text == displayText &&
            position.y >= 0 && position.y < gutter.getLineBottom(gutter.lineCount - 1)) {
            val visualLine = gutter.getLineForVerticalPosition(position.y)
            if (visualLine < layout.lineCount) {
                val original = projection.transformedToOriginal(layout.getLineStart(visualLine))
                val line = logicalLineStarts.binarySearch(original)
                if (line >= 0) {
                    val fold = foldsByLine[line]
                    val start = gutter.getLineStart(visualLine)
                    val marker = gutter.layoutInput.text.text.getOrNull(start)
                    val onArrow = fold != null && (marker == '▸' || marker == '▾') &&
                        position.x <= gutter.getBoundingBox(start).right
                    when (editorGutterAction(fold != null, (line + 1) in currentEditorErrorLines, onArrow)) {
                        EditorGutterAction.FOLD -> fold?.let(currentToggleFold)
                        EditorGutterAction.ERROR -> errorTooltipLine = line + 1
                        null -> Unit
                    }
                }
            }
        }
    })
    val lineNumberDigits = maxOf(1, logicalLineStarts.size.toString().length)
    val hasFolds = foldRegions.isNotEmpty()

    // Capture one layout for this source; the worker must not read changing Compose state.
    val gutterSourceLayout = editorTextLayout
    val gutterTextState = key(editingKey) {
        produceState(
            initialValue = AnnotatedString(""),
            showLineNumbers,
            hasFolds,
            projection,
            foldsByLine,
            gutterSourceLayout,
            logicalLineStarts,
            lineNumberDigits,
            editorErrorLines,
            colors.error
        ) {
            // While BasicTextField measures the edited source, keep the last visual rows.
            // Falling back to logical rows here shrinks wrapped gutters out of the viewport.
            if (gutterSourceLayout != null && gutterSourceLayout.layoutInput.text.text != displayText) {
                return@produceState
            }
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val visualStarts = gutterSourceLayout?.let { layout ->
                    List(layout.lineCount) { visualLine ->
                        projection.transformedToOriginal(layout.getLineStart(visualLine))
                    }
                } ?: logicalLineStarts.filterNot(projection::isHidden)

                AnnotatedString.Builder().apply {
                    visualStarts.forEachIndexed { visualIndex, offset ->
                        val logicalIndex = logicalLineStarts.binarySearch(offset).let {
                            if (it >= 0) it else -it - 2
                        }.coerceAtLeast(0)
                        val startsLogicalLine =
                            logicalLineStarts.getOrNull(logicalIndex) == offset

                        if (startsLogicalLine) {
                            if (hasFolds) {
                                val fold = foldsByLine[logicalIndex]
                                append(when {
                                    fold == null -> " "
                                    fold.open in collapsedFolds -> "▸"
                                    else -> "▾"
                                })
                            }
                            val numberStart = length
                            if (showLineNumbers) {
                                append((logicalIndex + 1).toString().padStart(lineNumberDigits))
                            }
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
                            append(" ".repeat((if (hasFolds) 1 else 0) + (if (showLineNumbers) lineNumberDigits else 0)))
                        }

                        if (visualIndex < visualStarts.lastIndex) {
                            append('\n')
                        }
                    }
                }.toAnnotatedString()
            }
        }
    }

    Card(
        modifier = modifier.pointerInput(Unit) {
            // Two-finger pinch resizes the code; single-finger scrolling and selection are untouched.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var startDistance = 0f
                var startSize = currentFontSize.value
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.size >= 2) {
                        val distance = (pressed[0].position - pressed[1].position).getDistance()
                        if (startDistance <= 0f) { startDistance = distance; startSize = currentFontSize.value }
                        else if (distance > 0f) {
                            val next = (startSize * distance / startDistance).coerceIn(12f, 28f)
                            if (kotlin.math.abs(next - currentFontSize.value) >= 0.5f) {
                                currentFontSizeChange.value(next.roundToInt().toFloat())
                            }
                        }
                        event.changes.forEach { it.consume() }
                    } else startDistance = 0f
                } while (event.changes.any { it.pressed })
            }
        }.border(
            width = 1.dp,
            color = if (editorFocused) {
                colors.primary
            } else {
                colors.outlineVariant
            },
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
            if (activeWork?.isSample == true) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(textTranslator("サンプル・閲覧専用", emptyArray()),
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onCopySample, enabled = !sessionViewModel.assetBusy) {
                    Text(textTranslator("コピーして編集", emptyArray()))
                }
            }
            val projectFiles = listOf("sketch.js") + activeWork?.files.orEmpty().keys.sorted()
            val hasMultipleFiles = projectFiles.size > 1
            if (hasMultipleFiles) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        FileTabs(projectFiles, selectedEditorFile,
                            dirtyFiles = dirtyFiles, unsavedDescription = textTranslator("未保存の変更あり", emptyArray()),
                            enabled = !sessionViewModel.editorInputLocked) {
                            if (selectedEditorFile != it) {
                                focusManager.clearFocus(force = true)
                                onFocusChange(false)
                                onSelectEditorFile(it)
                            }
                        }
                    }
                    if (editorFocused && previewHasChanges) {
                        TextButton(onClick = onRunChanges, enabled = !readOnly,
                            contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text(textTranslator("変更を実行", emptyArray()), style = MaterialTheme.typography.labelSmall,
                                color = colors.tertiary)
                        }
                    }
                }
            } else if (editorFocused && previewHasChanges) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onRunChanges, enabled = !readOnly,
                        contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text(textTranslator("変更を実行", emptyArray()), style = MaterialTheme.typography.labelSmall,
                            color = colors.tertiary)
                    }
                }
            }
            inlineSearch()
            if (!errorsCurrent && consoleEntries.any { it.level == ConsoleLevel.ERROR && it.workId == activeWorkId }) {
                Text(textTranslator("前回の実行時に発生したエラーです", emptyArray()),
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall, color = colors.tertiary)
            }
            val latestError = if (showErrorBanner && errorsCurrent) {
                consoleEntries.lastOrNull { it.level == ConsoleLevel.ERROR && it.workId == activeWorkId }
            } else null
            if (latestError != null) {
                val errorFile = latestError.file ?: editingFile
                val errorLine = latestError.line
                Surface(
                    onClick = { if (errorLine != null) onJumpToSource(errorFile, errorLine) },
                    enabled = errorLine != null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = colors.errorContainer,
                    contentColor = colors.onErrorContainer
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = (if (errorLine != null) "${errorFile}:$errorLine  " else "") +
                                latestError.message.lineSequence().firstOrNull().orEmpty(),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (errorLine != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(textTranslator("移動", emptyArray()),
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
                val gutterPixels = remember(gutterMeasurer, hasFolds, showLineNumbers, lineNumberDigits,
                    codeFontFamily, editorFontSize, localDensity) {
                    ('0'..'9').maxOf { digit ->
                        gutterMeasurer.measure(
                            AnnotatedString((if (hasFolds) "▾" else "") +
                                (if (showLineNumbers) digit.toString().repeat(lineNumberDigits) else "")),
                            style = TextStyle(fontFamily = codeFontFamily, fontSize = editorFontSize.sp,
                                fontWeight = FontWeight.Bold), softWrap = false
                        ).size.width
                    }
                }
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
                val contentMinHeight = (maxHeight - 32.dp).coerceAtLeast(0.dp)
                val gutterDividerColor = colors.outlineVariant

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(editorScrollState)
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    if (showLineNumbers || foldRegions.isNotEmpty()) {
                        Text(
                            text = gutterTextState.value,
                            softWrap = false,
                            onTextLayout = { if (!it.hasSameEditorLines(gutterLayout)) gutterLayout = it },
                            modifier = Modifier
                                .width(gutterWidth)
                                .pointerInput(editingKey) {
                                    detectTapGestures { position -> currentGutterTap(position) }
                                }
                                .semantics {
                                    customActions = foldsByLine.asSequence().mapNotNull { (line, fold) ->
                                        if (projection.hidden.any { fold.open > it.open && fold.close <= it.close }) null
                                        else CustomAccessibilityAction(textTranslator(
                                            if (fold.open in collapsedFolds) "%s行目を展開" else "%s行目を折りたたむ",
                                            arrayOf(line + 1)
                                        )) {
                                            toggleFold(fold); true
                                        }
                                    }.take(24).toList()
                                }
                                .heightIn(min = contentMinHeight)
                                .drawBehind {
                                    drawLine(
                                        color = gutterDividerColor,
                                        start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                        end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }
                                .padding(end = 5.dp),
                            color = colors.onSurfaceVariant.copy(alpha = 0.72f),
                            fontFamily = codeFontFamily,
                            fontSize = editorFontSize.sp,
                            lineHeight = (editorFontSize * 1.55f).sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
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
                                    start = if (showLineNumbers || foldRegions.isNotEmpty()) 6.dp else 12.dp,
                                    end = 16.dp
                                )
                                .focusRequester(editorFocusRequester)
                                .onFocusChanged {
                                    onFocusChange(it.isFocused)
                                },
                            textStyle = TextStyle(
                                fontFeatureSettings = fontFeatures,
                                fontFamily = codeFontFamily,
                                fontSize = editorFontSize.sp,
                                lineHeight = (editorFontSize * 1.55f).sp,
                                color = colors.onSurface
                            ),
                            visualTransformation = foldedHighlighter,
                            onTextLayout = { if (!it.hasSameEditorLines(editorTextLayout)) editorTextLayout = it },
                            cursorBrush = SolidColor(colors.primary)
                        )
                    }
                }
            }
        }
    }
    if (errorsCurrent && errorTooltipLine != null) {
        val errorDetail = consoleEntries.lastOrNull { it.level == ConsoleLevel.ERROR && it.file == editingFile && it.workId == activeWorkId && it.line == errorTooltipLine }
        if (errorDetail != null) {
            AlertDialog(
                onDismissRequest = { errorTooltipLine = null },
                title = { Text(textTranslator("エラー詳細", emptyArray())) },
                text = { Text(errorDetail.message) },
                confirmButton = {
                    TextButton(onClick = { errorTooltipLine = null }) {
                        Text(textTranslator("閉じる", emptyArray()))
                    }
                },
                containerColor = colors.errorContainer,
                titleContentColor = colors.onErrorContainer,
                textContentColor = colors.onErrorContainer
            )
        }
    }
}
