package com.hikariatelier.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Update-check state is observed only by the About section. */
@Composable
internal fun SettingsAboutSection(
    updateViewModel: UpdateViewModel,
    onShowGuide: () -> Unit,
    onShowOnboarding: () -> Unit,
    onShowLicenses: () -> Unit,
    onShowReleaseNotes: () -> Unit,
    openExternalUrl: (String) -> Unit,
    textTranslator: (String, Array<out Any?>) -> String
) {
    val colors = MaterialTheme.colorScheme
    fun uiText(source: String, vararg arguments: Any?) = textTranslator(source, arguments)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. アプリ情報 & アップデート
        SettingsSection(
            title = "Edit:RiN",
            description = uiText("アプリ情報とアップデート")
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher),
                        contentDescription = null,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Edit:RiN",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = uiText("バージョン %s", BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            text = uiText("Android向けクリエイティブコーディング環境"),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary
                        )
                    }
                }
            }

            SettingsDivider()

            AboutActionRow(
                title = uiText("アップデートを確認"),
                subtitle = if (updateViewModel.checking) uiText("確認中…") else uiText("最新バージョンをチェック"),
                trailingText = if (updateViewModel.manualChecking) "…" else "›",
                enabled = !updateViewModel.checking,
                onClick = { updateViewModel.checkManually() }
            )

            SettingsDivider()

            AboutActionRow(
                title = uiText("更新履歴 (What's New)"),
                subtitle = uiText("最近のアップデート内容と変更点"),
                trailingText = "›",
                onClick = onShowReleaseNotes
            )
        }

        // 2. リンクとコミュニティ
        SettingsSection(
            title = uiText("リンクとコミュニティ"),
            description = uiText("公式ウェブサイト・リポジトリ・SNS")
        ) {
            AboutActionRow(
                title = uiText("公式サイト"),
                subtitle = "rin-code-dev.github.io/Edit-RiN",
                trailingText = "↗",
                onClick = { openExternalUrl(OFFICIAL_WEBSITE_URL) }
            )

            SettingsDivider()

            AboutActionRow(
                title = "GitHub",
                subtitle = "rin-code-dev/Edit-RiN",
                trailingText = "↗",
                onClick = { openExternalUrl(GITHUB_REPO_URL) }
            )

            SettingsDivider()

            AboutActionRow(
                title = uiText("X (旧Twitter)"),
                subtitle = "@rincodedev",
                trailingText = "↗",
                onClick = { openExternalUrl(DEVELOPER_X_URL) }
            )

            SettingsDivider()

            AboutActionRow(
                title = "OFUSE",
                subtitle = uiText("開発を任意で支援できます"),
                trailingText = "↗",
                onClick = { openExternalUrl(SUPPORT_OFUSE_URL) }
            )
        }

        // 3. ヘルプとライセンス
        SettingsSection(
            title = uiText("ヘルプとライセンス"),
            description = uiText("使い方とオープンソース情報")
        ) {
            AboutActionRow(
                title = uiText("使い方ガイド"),
                subtitle = uiText("エディターの操作や各機能の解説"),
                trailingText = "›",
                onClick = onShowGuide
            )

            SettingsDivider()

            AboutActionRow(
                title = uiText("入門ツアーをもう一度見る"),
                subtitle = uiText("初回起動時のツアーを再開します"),
                trailingText = "›",
                onClick = onShowOnboarding
            )

            SettingsDivider()

            AboutActionRow(
                title = uiText("ライセンス情報"),
                subtitle = uiText("オープンソースソフトウェアのライセンス"),
                trailingText = "›",
                onClick = onShowLicenses
            )
        }
    }
}

@Composable
private fun AboutActionRow(
    title: String,
    subtitle: String? = null,
    trailingText: String = "›",
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        contentColor = colors.onSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f)
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
