package com.hikariatelier.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.os.ConfigurationCompat
import java.util.Locale

internal fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return if (digitGroups == 0) "$bytes B" else String.format(Locale.US, "%.1f %s", value, units[digitGroups])
}

@Composable
internal fun UpdateDialog(
    state: UpdateViewModel,
    text: (String) -> String,
    install: () -> Unit,
    openRelease: () -> Unit
) {
    val message = state.message ?: return
    val release = state.availableRelease
    val canDownload = release?.apk != null && !BuildConfig.DEBUG
    val colors = MaterialTheme.colorScheme

    val configuration = LocalConfiguration.current
    val deviceLanguage = ConfigurationCompat.getLocales(configuration)[0]?.language ?: "en"
    val isJa = deviceLanguage.startsWith("ja")

    // Find highlights for this release or fallback to the latest known release note
    val note = APP_RELEASE_NOTES.firstOrNull {
        val relTag = release?.tag?.removePrefix("v") ?: ""
        it.versionName == relTag || it.versionName == release?.tag
    } ?: APP_RELEASE_NOTES.firstOrNull()
    val highlights = note?.features?.take(3).orEmpty()

    Dialog(
        onDismissRequest = { if (!state.downloading) state.dismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = !state.downloading
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .border(1.dp, colors.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceContainer,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Header Icon
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(colors.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_system_update),
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // 2. Title & Version/Size Chips
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = text("新しいバージョンがあります"),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val tagText = release?.tag ?: "v${BuildConfig.VERSION_NAME}"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.secondaryContainer)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = tagText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onSecondaryContainer
                                )
                            }

                            val apkSize = release?.apk?.size ?: 0L
                            if (apkSize > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.secondaryContainer)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = formatFileSize(apkSize),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // 3. Feature Highlights (Rich Card feature)
                    if (highlights.isNotEmpty() && release != null) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = text("主な更新内容"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(start = 2.dp)
                            )

                            highlights.forEach { feature ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.surfaceContainerHigh)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.primary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(feature.iconRes),
                                            contentDescription = null,
                                            tint = colors.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isJa) feature.titleJa else feature.titleEn,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.onSurface
                                        )
                                        Text(
                                            text = if (isJa) feature.descJa else feature.descEn,
                                            fontSize = 11.sp,
                                            color = colors.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Download Progress / Status message
                    if (state.downloading) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { state.downloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = colors.primary,
                                trackColor = colors.surfaceContainerHighest
                            )
                            val totalBytes = release?.apk?.size ?: 0L
                            val progressPercent = (state.downloadProgress * 100).toInt()
                            val progressDetail = if (totalBytes > 0) {
                                val currentBytes = (totalBytes * state.downloadProgress).toLong()
                                "$progressPercent% (${formatFileSize(currentBytes)} / ${formatFileSize(totalBytes)})"
                            } else {
                                "$progressPercent%"
                            }
                            Text(
                                text = "${text("ダウンロード中…")} $progressDetail",
                                fontSize = 12.sp,
                                color = colors.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    } else if (state.readyApk != null) {
                        Text(
                            text = text("ダウンロードが完了しました。インストールして更新できます"),
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    } else if (canDownload) {
                        Text(
                            text = text("Androidの確認画面でインストールします。初回は、このアプリからのインストールを許可してください"),
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    } else if (release != null) {
                        Text(
                            text = text(
                                if (BuildConfig.DEBUG)
                                    "デバッグ版はアプリ内更新に対応していません。配布ページから正式版を入手してください"
                                else
                                    "このリリースのAPKが見つかりません。配布ページを確認してください"
                            ),
                            fontSize = 12.sp,
                            color = colors.error,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = text(message),
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    // 5. Actions
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!state.downloading) {
                            Button(
                                onClick = {
                                    when {
                                        state.readyApk != null -> install()
                                        canDownload -> state.download()
                                        release != null -> { openRelease(); state.dismiss() }
                                        else -> state.dismiss()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary,
                                    contentColor = colors.onPrimary
                                )
                            ) {
                                Text(
                                    text = text(
                                        when {
                                            state.readyApk != null -> "インストール"
                                            canDownload -> "ダウンロード"
                                            release != null -> "配布ページを開く"
                                            else -> "閉じる"
                                        }
                                    ),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Secondary actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.downloading) {
                                Spacer(modifier = Modifier.weight(1f))
                                TextButton(onClick = { state.cancelDownload() }) {
                                    Text(text("中止"), color = colors.error, fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.weight(1f))
                            } else {
                                if (canDownload && release != null) {
                                    TextButton(onClick = openRelease) {
                                        Text(text("配布ページを開く"), color = colors.onSurfaceVariant, fontSize = 14.sp)
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(1.dp))
                                }

                                TextButton(onClick = { state.dismiss() }) {
                                    Text(
                                        text = text(if (release != null) "後で" else "閉じる"),
                                        color = colors.onSurfaceVariant,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
