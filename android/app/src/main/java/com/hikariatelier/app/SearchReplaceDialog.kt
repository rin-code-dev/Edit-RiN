package com.hikariatelier.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun SearchReplaceDialog(
    viewModel: SearchReplaceViewModel,
    activeWorkId: String,
    activeWorkFiles: Map<String, String>,
    editorText: String,
    fileDrafts: Map<String, String>,
    editingValue: TextFieldValue,
    onApplyChange: (TextFieldValue) -> Unit,
    onNavigateToSource: (String, Int, TextRange?) -> Unit,
    onJumpToLine: (Int) -> Unit,
    editorFocusRequester: FocusRequester,
    codeFontFamily: FontFamily,
    textTranslator: (String, Array<out Any?>) -> String
) {
    if (!viewModel.showSearchDialog || !viewModel.searchWholeWork) return

    val colors = MaterialTheme.colorScheme
    fun uiText(source: String, vararg arguments: Any?): String =
        textTranslator(source, arguments)
    AlertDialog(
        onDismissRequest = {
            viewModel.showSearchDialog = false
        },
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null
            )
        },
        title = {
            Text(uiText("作品全体"))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !viewModel.searchWholeWork,
                        onClick = { viewModel.searchWholeWork = false },
                        label = { Text(uiText("現在のファイル")) }
                    )
                    FilterChip(
                        selected = viewModel.searchWholeWork,
                        onClick = { viewModel.searchWholeWork = true },
                        label = { Text(uiText("作品全体")) }
                    )
                }

                OutlinedTextField(
                    value = viewModel.searchQuery,
                    onValueChange = { viewModel.searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(uiText("検索する文字列")) },
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = viewModel.searchMatchCase,
                        onCheckedChange = { viewModel.searchMatchCase = it }
                    )
                    Text(
                        text = uiText("大文字・小文字を区別"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                val sources = projectSearchSources(activeWorkId, editorText, activeWorkFiles, fileDrafts)
                key(sources, viewModel.searchQuery, viewModel.searchMatchCase) {
                    val results by produceState<ProjectSearchResults?>(null) {
                        delay(150)
                        value = withContext(Dispatchers.Default) {
                            searchProject(sources, viewModel.searchQuery, viewModel.searchMatchCase)
                        }
                    }
                    val found = results
                    Text(
                        text = if (found == null) uiText("検索中…")
                        else if (found.truncated) uiText("先頭%s件を表示", found.matches.size)
                        else uiText("%s件", found.matches.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant
                    )
                    if (found != null && found.matches.isNotEmpty()) {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                            items(found.matches, key = { "${it.file}:${it.start}" }, contentType = { "search_match" }) { match ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.showSearchDialog = false
                                            onNavigateToSource(match.file, match.line, TextRange(match.start, match.end))
                                        }
                                        .padding(vertical = 10.dp)
                                ) {
                                    Text(
                                        "${match.file}:${match.line}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.primary
                                    )
                                    Text(
                                        match.excerpt,
                                        fontFamily = codeFontFamily,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.showSearchDialog = false
                    editorFocusRequester.requestFocus()
                }
            ) {
                Text(uiText("閉じる"))
            }
        }
    )
}
