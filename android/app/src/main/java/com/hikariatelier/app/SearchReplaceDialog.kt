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
    if (!viewModel.showSearchDialog) return

    val colors = MaterialTheme.colorScheme
    fun uiText(source: String, vararg arguments: Any?): String =
        textTranslator(source, arguments)
    val matches = if (viewModel.searchWholeWork) emptyList() else viewModel.searchMatches(editingValue.text)
    val selectedMatchIndex = matches.indexOfFirst {
        it.first == editingValue.selection.min && it.last + 1 == editingValue.selection.max
    }

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
            Text(uiText("検索・置換"))
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
                    Spacer(Modifier.weight(1f))
                    if (!viewModel.searchWholeWork) {
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
                }

                if (viewModel.searchWholeWork) {
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
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            shape = ButtonDefaults.outlinedShape,
                            onClick = {
                                viewModel.selectSearchMatch(editingValue.text, editingValue.selection, -1)?.let {
                                    onApplyChange(editingValue.copy(selection = it))
                                    editorFocusRequester.requestFocus()
                                }
                            },
                            enabled = matches.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(uiText("前へ"))
                        }
                        Button(
                            shape = ButtonDefaults.shape,
                            onClick = {
                                viewModel.selectSearchMatch(editingValue.text, editingValue.selection, 1)?.let {
                                    onApplyChange(editingValue.copy(selection = it))
                                    editorFocusRequester.requestFocus()
                                }
                            },
                            enabled = matches.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(uiText("次へ"))
                        }
                    }

                    HorizontalDivider(color = colors.outlineVariant)

                    OutlinedTextField(
                        value = viewModel.replacementText,
                        onValueChange = { viewModel.replacementText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(uiText("置換後の文字列")) },
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            shape = ButtonDefaults.outlinedShape,
                            onClick = {
                                val (nextValue, replaced) = viewModel.replaceSelectedMatch(editingValue)
                                if (replaced) {
                                    onApplyChange(nextValue)
                                } else {
                                    viewModel.selectSearchMatch(editingValue.text, editingValue.selection, 1)?.let {
                                        onApplyChange(editingValue.copy(selection = it))
                                        editorFocusRequester.requestFocus()
                                    }
                                }
                            },
                            enabled = matches.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(uiText("置換"))
                        }
                        OutlinedButton(
                            shape = ButtonDefaults.outlinedShape,
                            onClick = {
                                val nextValue = viewModel.replaceAllMatches(editingValue)
                                onApplyChange(nextValue)
                            },
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
                            value = viewModel.goToLineText,
                            onValueChange = {
                                viewModel.goToLineText = it.filter(Char::isDigit)
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text(uiText("行番号")) },
                            singleLine = true
                        )
                        FilledTonalButton(
                            shape = ButtonDefaults.filledTonalShape,
                            onClick = {
                                viewModel.goToLineText.toIntOrNull()?.let(onJumpToLine)
                                viewModel.showSearchDialog = false
                            },
                            enabled = viewModel.goToLineText.toIntOrNull()?.let { it > 0 } == true
                        ) {
                            Text(uiText("移動"))
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
