package com.hikariatelier.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** A bounded settings panel; actions remain reachable when content needs scrolling. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditSettingsDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            val availableHeight = maxHeight
            Box(Modifier.matchParentSize().pointerInput(onDismissRequest) {
                detectTapGestures(onTap = { onDismissRequest() })
            })
            Surface(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                    .heightIn(max = availableHeight),
                shape = RoundedCornerShape(20.dp),
                color = colors.surface,
                contentColor = colors.onSurface,
                border = BorderStroke(1.dp, colors.outlineVariant),
                tonalElevation = 0.dp
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (icon != null) Box(Modifier.size(22.dp)) { icon() }
                        Box(Modifier.weight(1f)) {
                            ProvideTextStyle(MaterialTheme.typography.titleMedium) { title() }
                        }
                    }
                    HorizontalDivider(color = colors.outlineVariant)
                    Box(
                        Modifier.weight(1f, fill = false).fillMaxWidth()
                            .verticalScroll(rememberScrollState()).padding(20.dp)
                    ) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                    }
                    HorizontalDivider(color = colors.outlineVariant)
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}
