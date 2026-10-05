package com.hikariatelier.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun WorkSnapshotSheet(
    title: String,
    subtitle: String,
    snapshots: List<WorkSnapshot>,
    current: SnapshotContent,
    loading: Boolean,
    busy: Boolean,
    error: String?,
    codeFontFamily: FontFamily,
    colors: ColorScheme,
    isLandscape: Boolean,
    dismissEnabled: Boolean,
    onRetry: () -> Unit,
    onCreateSnapshot: (String?, String?) -> Unit,
    onUpdateSnapshotDetails: (WorkSnapshot, String?, String?) -> Unit,
    onRestoreSnapshot: (WorkSnapshot) -> Unit,
    onDeleteSnapshot: (WorkSnapshot) -> Unit,
    onDismiss: () -> Unit,
    textTranslator: (String, Array<out Any?>) -> String,
    windowSetup: @Composable () -> Unit = {}
) {
    WorkSheet(
        title = title,
        subtitle = subtitle,
        onDismiss = onDismiss,
        colors = colors,
        textTranslator = textTranslator,
        dismissEnabled = dismissEnabled,
        windowSetup = windowSetup
    ) {
        SnapshotSheet(
            snapshots = snapshots,
            current = current,
            loading = loading,
            busy = busy,
            error = error,
            onRetry = onRetry,
            codeFontFamily = codeFontFamily,
            onCreateSnapshot = onCreateSnapshot,
            onUpdateSnapshotDetails = onUpdateSnapshotDetails,
            onRestoreSnapshot = onRestoreSnapshot,
            onDeleteSnapshot = onDeleteSnapshot,
            text = { s -> textTranslator(s, emptyArray()) }
        )
    }
}

@Composable
internal fun WorkHistoryDialog(
    revisions: List<WorkRevision>,
    colors: ColorScheme,
    codeFontFamily: FontFamily,
    onSelectRevision: (WorkRevision) -> Unit,
    onDismiss: () -> Unit,
    textTranslator: (String, Array<out Any?>) -> String
) {
    EditSettingsDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(painterResource(R.drawable.ic_history), contentDescription = null)
        },
        title = { Text(textTranslator("変更履歴", emptyArray())) },
        text = {
            val reversed = revisions.asReversed()
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(reversed, key = { it.savedAt }, contentType = { "revision_item" }) { revision ->
                    Surface(
                        onClick = { onSelectRevision(revision) },
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
                                    ?: textTranslator("空のコード", emptyArray()),
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
            TextButton(onClick = onDismiss) {
                Text(textTranslator("閉じる", emptyArray()))
            }
        }
    )
}

@Composable
internal fun WorkRevisionDiffDialog(
    revision: WorkRevision,
    editorText: String,
    codeFontFamily: FontFamily,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
    textTranslator: (String, Array<out Any?>) -> String
) {
    EditSettingsDialog(
        onDismissRequest = onDismiss,
        title = { Text(textTranslator("変更内容を確認", emptyArray())) },
        text = {
            Column {
                Text(textTranslator("− 現在のコード / + 復元するコード", emptyArray()))
                Text(
                    text = remember(editorText, revision) { revisionDifference(editorText, revision.code) },
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                    fontFamily = codeFontFamily
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onRestore) {
                Text(textTranslator("復元", emptyArray()))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(textTranslator("キャンセル", emptyArray()))
            }
        }
    )
}
