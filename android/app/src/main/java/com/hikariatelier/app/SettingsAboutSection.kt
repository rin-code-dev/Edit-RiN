package com.hikariatelier.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Update-check state is observed only by the About section. */
@Composable
internal fun SettingsAboutSection(
    updateViewModel: UpdateViewModel,
    onShowGuide: () -> Unit,
    onShowLicenses: () -> Unit,
    onShowReleaseNotes: () -> Unit,
    openExternalUrl: (String) -> Unit,
    textTranslator: (String, Array<out Any?>) -> String
) {
    val colors = MaterialTheme.colorScheme
    fun uiText(source: String, vararg arguments: Any?) = textTranslator(source, arguments)
    SettingsSection(
        title = "About",
        description = uiText("アプリ情報とリンク")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = uiText("アプリ情報"),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant
            )
            Text(
                text = "Edit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = uiText("バージョン %s", BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            TextButton(
                enabled = !updateViewModel.checking,
                onClick = { updateViewModel.checkManually() },
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                Text(uiText(if (updateViewModel.manualChecking) "確認中…" else "アップデートを確認"))
            }
            TextButton(
                onClick = onShowReleaseNotes,
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                Text(uiText("更新履歴 (What's New)"))
            }
            TextButton(
                onClick = onShowGuide,
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                Text(uiText("使い方ガイド"))
            }
            TextButton(
                onClick = onShowLicenses,
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                Text(uiText("ライセンス情報"))
            }
        }

        SettingsDivider()

        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = uiText("開発者"),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant
            )
            Surface(
                onClick = { openExternalUrl(DEVELOPER_X_URL) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color.Transparent,
                contentColor = colors.primary
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text("X")
                    Text(
                        text = "@rincodedev",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        SettingsDivider()

        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = uiText("サポート"),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant
            )
            Text(
                text = uiText("開発を任意で支援できます"),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            TextButton(
                onClick = { openExternalUrl(SUPPORT_OFUSE_URL) },
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                Text("OFUSE (Tip)")
            }
        }
    }
}
