package com.hikariatelier.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hikariatelier.app.ui.theme.LocalCustomTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkTagsDialog(
    workTitle: String,
    currentTags: List<String>,
    allKnownTags: List<String>,
    text: (String) -> String,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val background = if (LocalCustomTheme.current) colors.background
    else colors.surfaceContainerHigh
    var newTagInput by rememberSaveable { mutableStateOf("") }

    val cleanInput = newTagInput.trim().removePrefix("#").trim()
    val canAdd = cleanInput.isNotEmpty() && !currentTags.any { it.equals(cleanInput, ignoreCase = true) }

    fun commitAdd() {
        if (canAdd) {
            onAddTag(cleanInput)
        }
    }

    LaunchedEffect(currentTags) {
        if (cleanInput.isNotEmpty() && currentTags.any { it.equals(cleanInput, ignoreCase = true) }) newTagInput = ""
    }

    val availableSuggestions = remember(currentTags, allKnownTags) {
        allKnownTags.filter { known ->
            !currentTags.any { it.equals(known, ignoreCase = true) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = background,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tag),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colors.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = text("タグを編集"),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface
                        )
                        Text(
                            text = workTitle,
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = text("閉じる"),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Current Tags Flow
                Text(
                    text = text("設定中のタグ"),
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))

                if (currentTags.isEmpty()) {
                    Text(
                        text = text("タグが設定されていません"),
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        currentTags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.primaryContainer.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "#$tag",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.onPrimaryContainer
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .clickable { onRemoveTag(tag) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_close),
                                            contentDescription = text("タグを削除: %s").format(tag),
                                            modifier = Modifier.size(12.dp),
                                            tint = colors.onPrimaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Tag input field
                OutlinedTextField(
                    value = newTagInput,
                    onValueChange = { if (it.length <= 24 && !it.contains('\n')) newTagInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    placeholder = { Text(text("新しいタグを入力"), fontSize = 13.sp) },
                    prefix = { Text("#", color = colors.primary, fontWeight = FontWeight.Bold) },
                    trailingIcon = {
                        if (canAdd) {
                            IconButton(onClick = ::commitAdd) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_add),
                                    contentDescription = text("タグを追加"),
                                    tint = colors.primary
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitAdd() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.outlineVariant
                    )
                )

                // Suggestions from other works
                if (availableSuggestions.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = text("タグ候補"),
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableSuggestions.forEach { suggestion ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.surfaceContainerHighest.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable { onAddTag(suggestion) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("+", fontSize = 11.sp, color = colors.primary, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = suggestion,
                                        fontSize = 12.sp,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Footer button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text("閉じる"))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AllTagsManageDialog(
    tagCounts: Map<String, Int>,
    text: (String) -> String,
    onDeleteTag: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val background = if (LocalCustomTheme.current) colors.background
    else colors.surfaceContainerHigh
    var tagToDelete by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = background,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tag),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colors.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = text("タグの管理"),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface
                        )
                        Text(
                            text = text("全作品のタグを整理"),
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = text("閉じる"),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (tagCounts.isEmpty()) {
                    Text(
                        text = text("登録されているタグはありません"),
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tagCounts.toSortedMap().forEach { (tag, count) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.surfaceContainerHighest.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "#$tag",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.onSurface
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "($count)",
                                        fontSize = 11.sp,
                                        color = colors.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .clickable { tagToDelete = tag },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_delete),
                                            contentDescription = text("タグを削除"),
                                            modifier = Modifier.size(13.dp),
                                            tint = colors.error.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text("閉じる"))
                    }
                }
            }
        }
    }

    tagToDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { tagToDelete = null },
            title = { Text(text("全作品からタグを削除")) },
            text = { Text(text("「#%s」をすべての作品から削除しますか？").format(tag)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTag(tag)
                        tagToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.error)
                ) {
                    Text(text("削除"))
                }
            },
            dismissButton = {
                TextButton(onClick = { tagToDelete = null }) {
                    Text(text("キャンセル"))
                }
            }
        )
    }
}

