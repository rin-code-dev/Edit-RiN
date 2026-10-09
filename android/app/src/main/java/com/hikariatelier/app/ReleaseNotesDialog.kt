package com.hikariatelier.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal data class ReleaseFeature(
    val iconRes: Int,
    val titleEn: String,
    val titleJa: String,
    val descEn: String,
    val descJa: String
)

internal data class ReleaseNote(
    val versionName: String,
    val versionCode: Int,
    val titleEn: String,
    val titleJa: String,
    val features: List<ReleaseFeature>
)

internal val APP_RELEASE_NOTES = listOf(
    ReleaseNote(
        versionName = "2.3.1",
        versionCode = 31,
        titleEn = "What's New in v2.3.1",
        titleJa = "v2.3.1 の更新内容",
        features = listOf(
            ReleaseFeature(
                iconRes = R.drawable.ic_code,
                titleEn = "Code Folding Fix",
                titleJa = "コードの折りたたみを修正",
                descEn = "Fixed folding arrows that could stop responding after loading or editing code. This also applies to read-only samples.",
                descJa = "コードの読み込み後や編集中に、矢印をタップしても折りたためないことがある問題を修正しました。閲覧専用のサンプルも対象です。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_tune,
                titleEn = "Aligned Line Numbers",
                titleJa = "行番号の表示ずれを修正",
                descEn = "Fixed line numbers and folding arrows wrapping or becoming misaligned with some fonts and font sizes.",
                descJa = "フォントや文字サイズによって、行番号や折りたたみの矢印が折り返され、コードの行とずれる問題を修正しました。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_camera,
                titleEn = "Automatic Gallery Thumbnails",
                titleJa = "作品のサムネイルを自動生成",
                descEn = "After installation or an update, saved works and samples get gallery thumbnails in the background without switching your current work. No sound plays and no camera or microphone permission is requested. Some sketches may not produce a thumbnail.",
                descJa = "インストール後や更新後に、保存済みの作品とサンプルのサムネイルを生成します。開いている作品は切り替わらず、音の再生やカメラ・マイクの許可要求もありません。作品によっては生成できない場合があります。"
            )
        )
    ),
    ReleaseNote(
        versionName = "2.3.0",
        versionCode = 30,
        titleEn = "What's New in v2.3.0",
        titleJa = "v2.3.0 の更新内容",
        features = listOf(
            ReleaseFeature(
                iconRes = R.drawable.ic_history,
                titleEn = "Update Within the App",
                titleJa = "アプリ内で更新",
                descEn = "Download future release updates within the app, then install through Android’s confirmation screen. Progress and cancellation are available. Save your edits before installing.",
                descJa = "今後の正式版の更新は、アプリ内でダウンロードし、Androidの確認画面からインストールできます。進捗の確認や中止もできます。インストール前に、編集中の作品を保存してください。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_play,
                titleEn = "Explore the New Samples",
                titleJa = "新しいサンプルを試す",
                descEn = "Try Palette, Ripples, and Sensor, alongside refreshed existing samples. Explore touch, sound, camera input, and device motion, then copy a sample to edit.",
                descJa = "Palette・Ripples・Sensorを追加し、既存のサンプルも作り直しました。タッチ・音・カメラ・端末の動きを試し、コピーして編集できます。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_code,
                titleEn = "Tools for Writing Code",
                titleJa = "コードを書きやすく",
                descEn = "Search the p5.js reference, copy examples, or insert code. Formatting preserves your selection, and code folding now works in read-only samples.",
                descJa = "p5.jsリファレンスで関数を検索し、例をコピーしたり、コードを挿入したりできます。コード整形後も選択範囲を引き継ぎ、閲覧専用サンプルの折りたたみも修正しました。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_snippet,
                titleEn = "Templates and a Clearer Guide",
                titleJa = "テンプレートと案内を見直し",
                descEn = "Start with small templates and follow the revised guide from editing to export. Sample comments follow the app language, and Japanese interface text is clearer.",
                descJa = "小さなテンプレートと使い方ガイドで、編集から書き出しまで確認できます。サンプルのコメントは表示言語に合わせ、日本語の説明文も読みやすくしました。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_tune,
                titleEn = "Sumi Theme",
                titleJa = "Sumiテーマを追加",
                descEn = "Choose Sumi in Settings for charcoal tones, warm paper colors, and terracotta accents.",
                descJa = "墨色と生成りに朱色を合わせたSumiテーマを追加しました。設定から選べます。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_history,
                titleEn = "First Launch After Updating",
                titleJa = "更新後の初回起動について",
                descEn = "This update opens Palette and switches to the light theme with code wrapping off once. Your works are kept. Change these options in Settings; later launches keep your choices.",
                descJa = "今回の更新後は、初回だけPaletteを開き、ライトテーマ・コードの折り返しオフに切り替えます。自分の作品は残ります。設定から変更でき、その後は選んだ設定を引き継ぎます。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_camera,
                titleEn = "Capture and Share",
                titleJa = "画像・録画の共有について",
                descEn = "The share-card button is temporarily unavailable. PNG export, MP4/GIF recording, and sharing saved media remain available.",
                descJa = "シェアカードのボタンを一時的に外しました。PNGの書き出し、MP4・GIFの録画、保存した画像や録画の共有は引き続き使えます。"
            )
        )
    ),
    ReleaseNote(
        versionName = "2.2.2",
        versionCode = 29,
        titleEn = "What's New in v2.2.2",
        titleJa = "v2.2.2 の新機能",
        features = listOf(
            ReleaseFeature(
                iconRes = R.drawable.ic_tune,
                titleEn = "Live Parameters Button",
                titleJa = "パラメータボタンの独立",
                descEn = "Access sketch parameters directly from the preview bar with a live count badge indicating adjustable inputs.",
                descJa = "プレビューバーからパラメータ調整を直接開けます。パラメータ数もバッジに表示され、その場で更新されます。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_error_jump,
                titleEn = "Gutter Error Details",
                titleJa = "行番号エラー表示 & 詳細確認",
                descEn = "Error lines are highlighted in the gutter. Tap any line number with an error to immediately inspect the issue.",
                descJa = "エラーがある行の行番号を強調表示します。行番号をタップすると、エラーメッセージの詳細をその場で確認できます。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_code,
                titleEn = "Smoother Editor Highlighting",
                titleJa = "高速・安定したコードハイライト",
                descEn = "Enjoy flicker-free editing with incremental syntax caching and responsive spring-based panel animations.",
                descJa = "キャッシュを使って、入力中のハイライトのちらつきを抑えました。各種パネルの開閉も、滑らかなスプリングアニメーションに変更しました。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_camera,
                titleEn = "WebView & Media Tuning",
                titleJa = "動画・キャンバス描画の最適化",
                descEn = "Resolved WebView video poster issues and accelerated canvas pixel operations for camera and generative media.",
                descJa = "WebViewの動画エラーを防ぐようにし、カメラや画像処理を使う作品の描画性能を改善しました。"
            )
        )
    ),
    ReleaseNote(
        versionName = "2.2.1",
        versionCode = 28,
        titleEn = "What's New in v2.2.1",
        titleJa = "v2.2.1 の新機能",
        features = listOf(
            ReleaseFeature(
                iconRes = R.drawable.ic_folder_code,
                titleEn = "Folders & Organization",
                titleJa = "作品フォルダー機能",
                descEn = "Organize user works with 1-level folders. Create, rename, move, and drag to reorder folders effortlessly.",
                descJa = "1階層のフォルダーで作品を分類・整理できます。フォルダーの作成・名前変更・移動や、ドラッグでの並べ替えに対応しました。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_tune,
                titleEn = "Protected Official Samples",
                titleJa = "公式サンプルの保護・刷新",
                descEn = "Official samples are kept safe in a read-only section. Freely test parameters and copy anytime to edit.",
                descJa = "公式サンプルの原本を保護しました。パラメータを自由に調整して試せます。コピーすれば、自分用に編集できます。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_search,
                titleEn = "Inline Search & Replace",
                titleJa = "行内 検索・置換バー",
                descEn = "A compact search and replace bar directly above the keyboard with match count and highlighting.",
                descJa = "キーボードのすぐ上に小さな検索バーを表示します。一致件数の表示・強調表示・置換に対応しました。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_rename,
                titleEn = "Asset Rename & Refactoring",
                titleJa = "素材名と参照コードの一括置換",
                descEn = "Rename project assets with automatic updates to code references across your project files.",
                descJa = "画像・音声などの素材名を変更するときに、コード内で参照しているパスの文字列も、安全にまとめて置換できます。"
            )
        )
    ),
    ReleaseNote(
        versionName = "2.2.0",
        versionCode = 27,
        titleEn = "What's New in v2.2.0",
        titleJa = "v2.2.0 の新機能",
        features = listOf(
            ReleaseFeature(
                iconRes = R.drawable.ic_code,
                titleEn = "WebGPU Support",
                titleJa = "WebGPU サポート",
                descEn = "Experience next-generation high-performance graphics on compatible devices with p5.js WebGPU mode.",
                descJa = "p5.jsのWebGPUモードに対応しました。対応端末では、高性能なグラフィックス描画を楽しめます。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_camera,
                titleEn = "Device Camera & Microphone",
                titleJa = "カメラ・マイク対応",
                descEn = "Build reactive sketches using live camera video feeds and real-time audio analysis with new Camera & Microphone samples.",
                descJa = "端末のカメラ映像やマイク入力と連動する作品を作れるようになりました。Camera・Microphoneサンプルも追加しました。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_sort,
                titleEn = "Refreshed Selection UI",
                titleJa = "選択UIの刷新",
                descEn = "A cleaner work gallery layout, revamped sort menus, and smoother dialog interactions for effortless navigation.",
                descJa = "作品一覧やソートメニューのレイアウトを刷新し、操作性と視認性が向上しました。"
            ),
            ReleaseFeature(
                iconRes = R.drawable.ic_tune,
                titleEn = "Stabilized Audio Analysis",
                titleJa = "オーディオ解析の安定化",
                descEn = "Added new sample sketches to explore and showcase newly introduced features.",
                descJa = "新機能を試せるサンプル作品を追加しました。"
            )
        )
    )
)

@Composable
internal fun ReleaseNotesDialog(
    note: ReleaseNote = APP_RELEASE_NOTES.first(),
    language: String = "system",
    onDismiss: () -> Unit,
    windowSetup: @Composable () -> Unit = {}
) {
    windowSetup()
    val colors = MaterialTheme.colorScheme
    val systemLanguage = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]?.language
    val isJa = language == "ja" || (language == "system" && systemLanguage == "ja")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
            color = colors.surface,
            contentColor = colors.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_history),
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isJa) note.titleJa else note.titleEn,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            text = if (isJa) "最新のアップデートと新機能" else "Latest updates and enhancements",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "Close",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = colors.outlineVariant, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Feature List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(note.features) { feature ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceContainerHigh)
                                .padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(feature.iconRes),
                                    contentDescription = null,
                                    tint = colors.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isJa) feature.titleJa else feature.titleEn,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isJa) feature.descJa else feature.descEn,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer Action
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    )
                ) {
                    Text(
                        text = if (isJa) "はじめる" else "Get Started",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
