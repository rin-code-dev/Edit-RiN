package com.hikariatelier.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
internal fun UpdateDialog(state: UpdateViewModel, text: (String) -> String, openRelease: () -> Unit) {
    val message = state.message ?: return
    AlertDialog(
        onDismissRequest = state::dismiss,
        title = { Text(text("アップデート")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text(message))
                state.availableRelease?.let { Text(it.tag) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (state.availableRelease != null) openRelease()
                state.dismiss()
            }) { Text(text(if (state.availableRelease != null) "配布ページを開く" else "閉じる")) }
        },
        dismissButton = {
            if (state.availableRelease != null) {
                TextButton(onClick = state::dismiss) { Text(text("閉じる")) }
            }
        }
    )
}
