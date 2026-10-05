package com.hikariatelier.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SearchReplaceBar(
    viewModel: SearchReplaceViewModel,
    editingKey: String,
    editingValue: TextFieldValue,
    onApplyChange: (TextFieldValue) -> Unit,
    onJumpToLine: (Int) -> Unit,
    editorFocusRequester: FocusRequester,
    codeFontFamily: FontFamily,
    textTranslator: (String, Array<out Any?>) -> String,
    modifier: Modifier = Modifier,
    onSearchMatchesChanged: (String, List<IntRange>) -> Unit = { _, _ -> },
    onRevealMatch: (TextRange) -> Unit = {}
) {
    if (!viewModel.showSearchDialog || viewModel.searchWholeWork) return
    // A switch cancels replacements prepared for the previous file, even if both texts are identical.
    key(editingKey) {
        FileSearchBar(
            viewModel, editingValue, onApplyChange, onJumpToLine, editorFocusRequester,
            codeFontFamily, textTranslator, modifier, onSearchMatchesChanged, onRevealMatch
        )
    }
}

@Composable
private fun FileSearchBar(
    viewModel: SearchReplaceViewModel,
    editingValue: TextFieldValue,
    onApplyChange: (TextFieldValue) -> Unit,
    onJumpToLine: (Int) -> Unit,
    editorFocusRequester: FocusRequester,
    codeFontFamily: FontFamily,
    textTranslator: (String, Array<out Any?>) -> String,
    modifier: Modifier,
    onSearchMatchesChanged: (String, List<IntRange>) -> Unit,
    onRevealMatch: (TextRange) -> Unit
) {
    fun uiText(source: String, vararg arguments: Any?): String = textTranslator(source, arguments)
    val colors = MaterialTheme.colorScheme
    val request = FileSearchRequest(editingValue.text, viewModel.searchQuery, viewModel.searchMatchCase)
    val searchResult by produceState<Pair<FileSearchRequest, List<IntRange>>?>(null, request) {
        value = null
        if (request.source.length >= 8_000) delay(120)
        value = request to withContext(Dispatchers.Default) { findFileMatches(request) }
    }
    val searching = searchResult?.first != request
    val matches = searchResult?.takeIf { it.first == request }?.second.orEmpty()
    val currentValue by rememberUpdatedState(editingValue)
    val scope = rememberCoroutineScope()
    val queryFocusRequester = remember { FocusRequester() }
    var replaceExpanded by rememberSaveable { mutableStateOf(false) }
    var lineExpanded by rememberSaveable { mutableStateOf(false) }
    var replacing by remember { mutableStateOf(false) }
    val selectedMatchIndex = matches.indexOfFirst {
        it.first == editingValue.selection.min && it.last + 1 == editingValue.selection.max
    }
    LaunchedEffect(Unit) { queryFocusRequester.requestFocus() }
    LaunchedEffect(request, searchResult) { onSearchMatchesChanged(request.source, matches) }

    fun selectMatch(direction: Int) {
        if (searching || replacing) return
        viewModel.selectSearchMatch(editingValue.text, editingValue.selection, direction, matches)?.let { selection ->
            onApplyChange(editingValue.copy(selection = selection, composition = null))
            onRevealMatch(selection)
        }
    }
    fun closeSearch() {
        viewModel.showSearchDialog = false
        onSearchMatchesChanged(request.source, emptyList())
        editorFocusRequester.requestFocus()
    }
    BackHandler { closeSearch() }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    OutlinedTextField(
                        value = viewModel.searchQuery,
                        onValueChange = { viewModel.searchQuery = it },
                        modifier = Modifier.weight(1f).focusRequester(queryFocusRequester),
                        label = { Text(uiText("検索する文字列"), style = MaterialTheme.typography.labelSmall) },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = codeFontFamily),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { selectMatch(1) })
                    )
                    IconButton(onClick = { selectMatch(-1) }, enabled = !searching && !replacing && matches.isNotEmpty(), modifier = Modifier.size(36.dp)) {
                        Icon(painterResource(R.drawable.ic_chevron_down), uiText("前へ"), Modifier.size(18.dp).rotate(180f))
                    }
                    IconButton(onClick = { selectMatch(1) }, enabled = !searching && !replacing && matches.isNotEmpty(), modifier = Modifier.size(36.dp)) {
                        Icon(painterResource(R.drawable.ic_chevron_down), uiText("次へ"), Modifier.size(18.dp))
                    }
                    IconButton(onClick = { closeSearch() }, modifier = Modifier.size(36.dp)) {
                        Icon(painterResource(R.drawable.ic_close), uiText("閉じる"), Modifier.size(18.dp))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (searching || replacing) uiText("検索中…")
                        else if (selectedMatchIndex >= 0) "${selectedMatchIndex + 1} / ${matches.size}"
                        else uiText("%s件", matches.size),
                        modifier = Modifier.padding(end = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                    TextButton(onClick = {
                        replaceExpanded = !replaceExpanded
                        if (replaceExpanded) lineExpanded = false
                    }, modifier = Modifier.height(36.dp).semantics { selected = replaceExpanded }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(uiText("置換"), style = MaterialTheme.typography.labelSmall)
                        Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(14.dp).rotate(if (replaceExpanded) 180f else 0f))
                    }
                    TextButton(
                        onClick = { viewModel.searchMatchCase = !viewModel.searchMatchCase },
                        modifier = Modifier.height(36.dp).semantics {
                            selected = viewModel.searchMatchCase
                            contentDescription = uiText("大文字・小文字を区別")
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = if (viewModel.searchMatchCase) colors.primary else colors.onSurfaceVariant)
                    ) { Text("Aa", style = MaterialTheme.typography.labelSmall) }
                    TextButton(onClick = { viewModel.searchWholeWork = true }, modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(uiText("作品全体"), style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = {
                        lineExpanded = !lineExpanded
                        if (lineExpanded) replaceExpanded = false
                    }, modifier = Modifier.height(36.dp).semantics { selected = lineExpanded }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(uiText("行番号"), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (replaceExpanded) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = viewModel.replacementText,
                            onValueChange = { viewModel.replacementText = it },
                            modifier = Modifier.weight(1f),
                            label = { Text(uiText("置換後の文字列"), style = MaterialTheme.typography.labelSmall) },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = codeFontFamily),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        TextButton(
                            onClick = {
                                val (nextValue, replaced) = viewModel.replaceSelectedMatch(editingValue)
                                if (replaced) {
                                    onApplyChange(nextValue)
                                    onRevealMatch(nextValue.selection)
                                } else selectMatch(1)
                            },
                            enabled = !searching && !replacing && matches.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text(uiText("置換"), style = MaterialTheme.typography.labelSmall) }
                        TextButton(
                            onClick = {
                                val before = editingValue
                                val replacement = viewModel.replacementText
                                replacing = true
                                scope.launch {
                                    try {
                                        val next = withContext(Dispatchers.Default) { replaceFileMatches(before, matches, replacement) }
                                        // Retain edits made while the replacement was prepared.
                                        if (currentValue == before && viewModel.searchQuery == request.query &&
                                            viewModel.searchMatchCase == request.matchCase && viewModel.replacementText == replacement) {
                                            onApplyChange(next)
                                            onRevealMatch(next.selection)
                                        }
                                    } finally { replacing = false }
                                }
                            },
                            enabled = !searching && !replacing && matches.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text(uiText("すべて置換"), style = MaterialTheme.typography.labelSmall) }
                    }
                }
                if (lineExpanded) {
                    val lineCount = remember(editingValue.text) { editingValue.text.count { it == '\n' } + 1 }
                    val targetLine = viewModel.goToLineText.toIntOrNull()?.takeIf { it in 1..lineCount }
                    fun jumpToLine() {
                        targetLine?.let {
                            viewModel.showSearchDialog = false
                            onSearchMatchesChanged(request.source, emptyList())
                            onJumpToLine(it)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = viewModel.goToLineText,
                            onValueChange = { viewModel.goToLineText = it.filter(Char::isDigit) },
                            modifier = Modifier.weight(1f),
                            label = { Text(uiText("行番号"), style = MaterialTheme.typography.labelSmall) },
                            placeholder = { Text("1–$lineCount") },
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { jumpToLine() })
                        )
                        TextButton(onClick = { jumpToLine() }, enabled = targetLine != null) {
                            Text(uiText("移動"), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
