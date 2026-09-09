package com.hikariatelier.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun P5AccountImportDialog(
    username: String,
    sketches: List<P5Sketch>,
    busy: Boolean,
    error: String?,
    text: (String) -> String,
    onUsernameChange: (String) -> Unit,
    onLoad: () -> Unit,
    onImport: (P5Sketch) -> Unit,
    onDismiss: () -> Unit
) {
    EditSettingsDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(text("p5.jsアカウント")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text("p5.js Web Editorのユーザー名から公開作品を取り込みます。パスワードは使用しません。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(text("ユーザー名")) },
                        singleLine = true,
                        enabled = !busy,
                        isError = username.isNotBlank() && !validP5Username(username.trim())
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onLoad,
                        enabled = !busy && validP5Username(username.trim())
                    ) {
                        Text(text("作品を取得"))
                    }
                }
                if (busy) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(text("読み込み中"), style = MaterialTheme.typography.bodySmall)
                    }
                }
                error?.let {
                    Text(text(it), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                if (!busy && sketches.isEmpty() && error == null) {
                    Text(
                        text("ユーザー名を入力して公開作品を取得してください"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (sketches.isNotEmpty()) {
                    Text(
                        text("%s件の公開作品").replace("%s", sketches.size.toString()),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(sketches, key = { it.id }) { sketch ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                    onImport(sketch)
                                },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(sketch.name, maxLines = 2, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text("タップして取り込む"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(text("閉じる")) }
        }
    )
}
