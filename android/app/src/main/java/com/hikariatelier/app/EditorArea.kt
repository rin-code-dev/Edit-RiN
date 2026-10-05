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
import androidx.compose.ui.text.input.VisualTransformation
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
    navigationTarget: Pair<String, Int>?,
    onClearNavigationTarget: () -> Unit,
    colors: ColorScheme,
    textTranslator: (String, Array<out Any?>) -> String,
    readOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val darkEditorTheme = colors.surface.luminance() < 0.5f
    val dirtyFiles = dirtyProjectFiles(activeWorkId, activeWork?.code.orEmpty(),
        sessionViewModel.editorValueState.value.text, activeWork?.files.orEmpty(), sessionViewModel.fileDrafts)
    LaunchedEffect(readOnly) { if (readOnly) focusManager.clearFocus(force = true) }

    val editorErrorLines by remember(editingFile, activeWorkId) {
        derivedStateOf {
            consoleEntries.asSequence()
                .filter { it.level == ConsoleLevel.ERROR && it.file == editingFile && it.workId == activeWorkId }
                .mapNotNull { it.line }
                .toSet()
        }
    }

    val javascriptHighlighter = editorHighlight(editingText, darkEditorTheme, editorErrorLines, editingFile)

    val parsedFoldRegions = if (isJavaScriptProjectFile(editingFile) ||
        projectTextSyntax(editingFile) == ProjectTextSyntax.SHADER) editorFoldRegions(editingText, editingKey) else emptyList()
    val foldRegions = parsedFoldRegions.orEmpty()
    val storedFolds = sessionViewModel.codeFoldStates[editingKey]
    val collapsedFolds = remember(storedFolds, editingText, parsedFoldRegions) {
        if (parsedFoldRegions == null) emptySet()
        else rebasedFolds(storedFolds, editingText).intersect(foldRegions.map { it.open }.toSet())
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
    val foldedHighlighter = remember(javascriptHighlighter, projection) {
        VisualTransformation { text -> projection.transform(javascriptHighlighter.filter(text).text) }
    }
    LaunchedEffect(editingKey, editingValue.selection, collapsedFolds) {
        val selection = editingValue.selection
        val reveal = foldRegions.filter { it.open in collapsedFolds &&
            (if (selection.collapsed) selection.start > it.open + 1 && selection.start < it.close
            else selection.min < it.close && selection.max > it.open + 1) }.map { it.open }.toSet()
        if (reveal.isNotEmpty()) sessionViewModel.codeFoldStates[editingKey] =
            CodeFoldState(editingText, collapsedFolds - reveal, foldRegions)
    }
    fun toggleFold(fold: CodeFold) {
        if (readOnly) return
        val next = if (fold.open in collapsedFolds) collapsedFolds - fold.open else collapsedFolds + fold.open
        if (fold.open !in collapsedFolds) {
            onUpdateEditingValue(editingValue.copy(selection = TextRange(fold.open), composition = null))
        }
        sessionViewModel.codeFoldStates[editingKey] = CodeFoldState(editingText, next, foldRegions)
    }
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
    val lineNumberDigits = maxOf(1, logicalLineStarts.size.toString().length)
    val hasFolds = foldRegions.isNotEmpty()

    val gutterText = remember(
        showLineNumbers,
        hasFolds,
        projection,
        foldsByLine,
        editorTextLayout,
        logicalLineStarts,
        lineNumberDigits,
        editorErrorLines,
        colors.error
    ) {
        val visualStarts = editorTextLayout?.takeIf {
            it.layoutInput.text.text == displayText
        }?.let { layout ->
            List(layout.lineCount) { visualLine ->
                projection.transformedToOriginal(layout.getLineStart(visualLine))
            }
        } ?: logicalLineStarts.filter { offset -> projection.hidden.none { offset > it.open && offset < it.close } }

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

    Card(
        modifier = modifier.border(
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    FileTabs(listOf("sketch.js") + activeWork?.files.orEmpty().keys.sorted(), selectedEditorFile,
                        dirtyFiles = dirtyFiles, unsavedDescription = textTranslator("未保存の変更あり", emptyArray()),
                        enabled = !readOnly) {
                        if (selectedEditorFile != it) {
                            focusManager.clearFocus(force = true)
                            onFocusChange(false)
                            onSelectEditorFile(it)
                        }
                    }
                }
                IconButton(
                    onClick = onOpenSnapshotSheet,
                    enabled = !readOnly,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_snapshot),
                        contentDescription = textTranslator("スナップショット", emptyArray()),
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val editorScrollState = remember(editingKey) { sessionViewModel.editorScroll(editingKey).vertical }
                val editorHorizontalScrollState = remember(editingKey) { sessionViewModel.editorScroll(editingKey).horizontal }
                LaunchedEffect(navigationSequence, editingKey, editorTextLayout, projection) {
                    val target = navigationTarget ?: return@LaunchedEffect
                    val layout = editorTextLayout ?: return@LaunchedEffect
                    if (target.first != editingFile || layout.layoutInput.text.text != displayText) return@LaunchedEffect
                    val offset = sourceLineOffset(editingText, target.second) ?: return@LaunchedEffect
                    val containing = foldRegions.filter { it.open in collapsedFolds && offset > it.open && offset < it.close }
                    if (containing.isNotEmpty()) {
                        sessionViewModel.codeFoldStates[editingKey] = CodeFoldState(
                            editingText,
                            collapsedFolds - containing.map { it.open }.toSet(),
                            foldRegions
                        )
                        return@LaunchedEffect
                    }
                    editorFocusRequester.requestFocus()
                    editorScrollState.scrollTo(layout.getLineTop(layout.getLineForOffset(projection.originalToTransformed(offset))).toInt())
                    onClearNavigationTarget()
                }
                val contentMinHeight = (maxHeight - 32.dp).coerceAtLeast(0.dp)
                val gutterChars = (if (hasFolds) 1 else 0) + (if (showLineNumbers) lineNumberDigits else 0)
                val gutterWidth = (
                    gutterChars * editorFontSize * LocalDensity.current.fontScale * 0.60f + 10f
                ).dp
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
                            text = gutterText,
                            onTextLayout = { if (!it.hasSameEditorLines(gutterLayout)) gutterLayout = it },
                            modifier = Modifier
                                .width(gutterWidth)
                                .pointerInput(projection, gutterLayout, editorTextLayout, foldsByLine) {
                                    detectTapGestures { position ->
                                        val gutter = gutterLayout ?: return@detectTapGestures
                                        val layout = editorTextLayout ?: return@detectTapGestures
                                        if (layout.layoutInput.text.text != displayText || position.y > gutter.size.height) return@detectTapGestures
                                        val visualLine = gutter.getLineForVerticalPosition(position.y)
                                        if (visualLine >= layout.lineCount) return@detectTapGestures
                                        val original = projection.transformedToOriginal(layout.getLineStart(visualLine))
                                        val line = logicalLineStarts.binarySearch(original)
                                        if (line >= 0) foldsByLine[line]?.let(::toggleFold)
                                    }
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
                            onValueChange = {
                                if (!readOnly) {
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
}
