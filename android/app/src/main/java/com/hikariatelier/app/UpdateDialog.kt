package com.hikariatelier.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
internal fun UpdateDialog(state: UpdateViewModel, text: (String) -> String,
                          install: () -> Unit, openRelease: () -> Unit) {
    val message = state.message ?: return
    val release = state.availableRelease
    val canDownload = release?.apk != null && !BuildConfig.DEBUG
    AlertDialog(
        onDismissRequest = { if (!state.downloading) state.dismiss() },
        title = { Text(text("アップデート")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text(message))
                release?.let { Text(it.tag) }
                if (state.downloading) {
                    LinearProgressIndicator(progress = { state.downloadProgress })
                    Text("${(state.downloadProgress * 100).toInt()}%")
                } else if (canDownload) {
                    Text(text("Androidの確認画面でインストールします。初回は、このアプリからのインストールを許可してください"))
                } else if (release != null) {
                    Text(text(if (BuildConfig.DEBUG)
                        "デバッグ版はアプリ内更新に対応していません。配布ページから正式版を入手してください"
                        else "このリリースのAPKが見つかりません。配布ページを確認してください"))
                }
            }
        },
        confirmButton = {
            if (!state.downloading) TextButton(onClick = {
                when {
                    state.readyApk != null -> install()
                    canDownload -> state.download()
                    release != null -> { openRelease(); state.dismiss() }
                    else -> state.dismiss()
                }
            }) { Text(text(when {
                state.readyApk != null -> "インストール"
                canDownload -> "ダウンロード"
                release != null -> "配布ページを開く"
                else -> "閉じる"
            })) }
        },
        dismissButton = {
            if (release != null) Column {
                if (!state.downloading && canDownload) {
                    TextButton(onClick = openRelease) { Text(text("配布ページを開く")) }
                }
                TextButton(onClick = { if (state.downloading) state.cancelDownload() else state.dismiss() }) {
                    Text(text(if (state.downloading) "中止" else "閉じる"))
                }
            }
        }
    )
}
