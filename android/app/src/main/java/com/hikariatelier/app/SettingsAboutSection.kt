package com.hikariatelier.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Update-check state is observed only by the About section. */
@Composable
internal fun SettingsAboutSection(
    updateViewModel: UpdateViewModel,
    onShowGuide: () -> Unit,
    onShowOnboarding: () -> Unit,
    onShowPracticeTour: () -> Unit = {},
    onShowLicenses: () -> Unit,
    onShowReleaseNotes: () -> Unit,
    openExternalUrl: (String) -> Unit,
    textTranslator: (String, Array<out Any?>) -> String
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    fun uiText(source: String, vararg arguments: Any?) = textTranslator(source, arguments)

    val webViewVersion = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { WebView.getCurrentWebViewPackage()?.versionName }.getOrNull()
        } else {
            null
        } ?: "System WebView"
    }

    val runtimeInfoSummary = remember(webViewVersion) {
        buildString {
            appendLine("Edit:RiN ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Package: ${BuildConfig.APPLICATION_ID}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("WebView: $webViewVersion")
            appendLine("p5.js: 2.3.4 / 1.11.5 (LGPL-2.1)")
            appendLine("p5.sound: 0.4.1 (LGPL-2.1) / 1.0.1 (MIT)")
            appendLine("Blockly: 13.4.0 (Apache-2.0)")
            appendLine("Acorn: 8.19.0 (MIT)")
            append("Matter.js: 0.20.0 (MIT)")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. アプリ情報 & ライブ更新ステータス
        SettingsSection(
            title = "Edit:RiN",
            description = uiText("アプリ情報とアップデート")
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_app_icon),
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(UNIT_CARD_SHAPE)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Edit:RiN",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = UNIT_COMPACT_CONTROL_SHAPE,
                                color = colors.secondaryContainer,
                                contentColor = colors.onSecondaryContainer
                            ) {
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            if (BuildConfig.DEBUG) {
                                Surface(
                                    shape = UNIT_COMPACT_CONTROL_SHAPE,
                                    color = colors.tertiaryContainer,
                                    contentColor = colors.onTertiaryContainer
                                ) {
                                    Text(
                                        text = "DEV",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Text(
                            text = uiText("Android向けクリエイティブコーディング環境"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.primary
                        )
                    }
                }

                // Update status banner
                val available = updateViewModel.availableRelease
                val statusMsg = updateViewModel.message
                if (updateViewModel.checking || updateViewModel.downloading || statusMsg != null || available != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = UNIT_CONTROL_SHAPE,
                        color = colors.secondaryContainer.copy(alpha = 0.65f),
                        contentColor = colors.onSecondaryContainer,
                        border = BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant.copy(alpha = 0.55f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (updateViewModel.checking) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = uiText("確認中…"),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else {
                                if (available != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = uiText("新しいバージョン: %s", available.tag),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (!updateViewModel.downloading && available.apk != null && !BuildConfig.DEBUG) {
                                            FilledTonalButton(
                                                onClick = { updateViewModel.download() },
                                                shape = UNIT_CONTROL_SHAPE,
                                                colors = unitTonalButtonColors(),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                            ) {
                                                Text(uiText("ダウンロード"), style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                                if (updateViewModel.downloading) {
                                    LinearProgressIndicator(
                                        progress = { updateViewModel.downloadProgress.coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (!statusMsg.isNullOrBlank()) {
                                    Text(
                                        text = statusMsg,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SettingsDivider()

            AboutActionRow(
                title = uiText("アップデートを確認"),
                subtitle = if (updateViewModel.checking) uiText("確認中…") else uiText("最新バージョンをチェック"),
                trailingText = if (updateViewModel.manualChecking) "…" else "›",
                enabled = !updateViewModel.checking && !updateViewModel.downloading,
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

        // 2. 同梱エンジンと実行環境
        SettingsSection(
            title = uiText("同梱エンジンと実行環境"),
            description = uiText("搭載ライブラリとWebView・ライセンス構成")
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EngineSpecRow("p5.js", "2.3.4 / 1.11.5 · LGPL-2.1")
                EngineSpecRow("p5.sound", "0.4.1 (p5 2.x: LGPL-2.1) / 1.0.1 (p5 1.x: MIT)")
                EngineSpecRow("Blockly", "13.4.0 · Apache-2.0")
                EngineSpecRow("Acorn", "8.19.0 · MIT")
                EngineSpecRow("Matter.js", "0.20.0 · MIT")
                EngineSpecRow("Android WebView", webViewVersion)
                EngineSpecRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Edit:RiN Runtime Info", runtimeInfoSummary))
                            Toast.makeText(context, uiText("環境情報をコピーしました"), Toast.LENGTH_SHORT).show()
                        },
                        shape = UNIT_CONTROL_SHAPE,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_copy),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        AutoFitSingleLineText(
                            text = uiText("環境情報をコピー"),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    FilledTonalButton(
                        onClick = onShowLicenses,
                        shape = UNIT_CONTROL_SHAPE,
                        colors = unitTonalButtonColors(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        AutoFitSingleLineText(
                            text = uiText("ライセンス情報"),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
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
                title = uiText("初回ツアーをもう一度見る"),
                subtitle = uiText("基本操作やブロックモード、// ?・// @slider などの使い方を試せます"),
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

            SettingsDivider()

            AboutActionRow(
                title = uiText("対応ソース (GitHub)"),
                subtitle = uiText("GPLv3 / LGPLv2.1 に基づき、完全な対応ソースコードを公開リポジトリで提供しています"),
                trailingText = "↗",
                onClick = { openExternalUrl(GITHUB_REPO_URL) }
            )
        }

        // 4. リンクとコミュニティ
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
    }
}

@Composable
private fun EngineSpecRow(name: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLow, UNIT_COMPACT_CONTROL_SHAPE)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = colors.onSurfaceVariant
        )
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
