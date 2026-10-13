package com.hikariatelier.app

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Raise this only when the tour is redesigned and should be shown again to everyone. */
internal const val ONBOARDING_VERSION = 1

internal const val ONBOARDING_STEP_COUNT = 6

internal data class OnboardingStep(val title: String, val body: String)

/** Tour copy is kept here, in the same ja/en/zh form as the user guide. */
internal fun onboardingSteps(language: String): List<OnboardingStep> {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    return listOf(
        OnboardingStep(
            t("作品を開く", "Open a work", "打开作品"),
            t("ギャラリーでサムネイルをタップすると作品が開きます。まずは動くサンプルを選んでみましょう。",
                "Tap a thumbnail in the gallery to open a work. Start by choosing a running sample.",
                "在作品库中点击缩略图即可打开作品。先从正在运行的示例开始吧。")
        ),
        OnboardingStep(
            t("書いて、動かす", "Write and run", "编写并运行"),
            t("コードを編集したら、「実行」を押してプレビューに変更を反映しましょう。",
                "Edit the code and press Run to see your changes in the preview.",
                "编辑代码后，点击“运行”即可把更改应用到预览。")
        ),
        OnboardingStep(
            t("パラメータで調整", "Tune with parameters", "实时参数调整"),
            t("コードに宣言した数値や色を、スライダーを使ってリアルタイムに調整できます。",
                "Adjust numbers and colors declared in your code in real time using sliders.",
                "作品运行时，可以通过滑块实时调整代码中声明的数值和颜色。")
        ),
        OnboardingStep(
            t("一時停止と履歴", "Pause and History", "暂停与恢复"),
            t("作品の動きを一時停止したり、「履歴」からスナップショット作成や保存済み状態への復元ができます。",
                "Pause the artwork at any time. From History, you can take snapshots or revert to your saved work.",
                "可以随时暂停作品。在“历史”中可以创建快照，或恢复到已保存的状态。")
        ),
        OnboardingStep(
            t("記録して、共有", "Capture and share", "导出与分享"),
            t("画像の書き出しや、MP4・GIFの録画ができます。保存した作品は、ほかのアプリへすぐに共有できます。",
                "Export images or record MP4 and GIF files, then share your creations through other apps.",
                "可以导出图片，或录制 MP4 和 GIF。保存后，可通过其他应用分享。")
        ),
        OnboardingStep(
            t("さあ、始めましょう", "Make it yours", "开始创作吧"),
            t("このツアーは設定からいつでも再確認できます。詳しい使い方は「使い方ガイド」をご覧ください。",
                "You can revisit this tour from Settings at any time. The User Guide explains each feature in detail.",
                "您可以随时在设置中再次查看此引导。各项功能的详细说明请参阅“使用指南”。")
        )
    )
}

/**
 * True when the first-run tour is due. Uses the same gate as the release notes prompt so the tour
 * is never shown over a load failure, a pending draft recovery or the folder selection prompt.
 */
internal fun onboardingDue(
    storedVersion: Int, loadFailed: Boolean, hasPendingDraft: Boolean, hasStorageFolder: Boolean
): Boolean = storedVersion < ONBOARDING_VERSION && !loadFailed && !hasPendingDraft && hasStorageFolder

/**
 * Full-screen first-run tour. Every interaction lives in the mock screens of [OnboardingMocks];
 * nothing here reads or writes works, files or settings. Hint and demo animations always play,
 * even when the system animation scale is off; [animate] exists so tests can hold them still.
 */
@Composable
internal fun OnboardingTour(
    language: String,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    onOpenDetailedGuide: () -> Unit,
    animate: Boolean = true
) {
    fun t(ja: String, en: String, zh: String) = guideText(language, ja, en, zh)
    val steps = remember(language) { onboardingSteps(language) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    val last = step == steps.lastIndex
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    BackHandler { if (step > 0) step-- else onSkip() }

    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(steps.size) { index ->
                    Box(
                        Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (index <= step) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f))
                    )
                }
            }
            TextButton(onClick = onSkip, modifier = Modifier.testTag("onboarding-skip")) {
                Text(t("スキップ", "Skip", "跳过"))
            }
        }
    }
    val mock: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().semantics { contentDescription = t("練習用の画面", "Practice screen", "练习画面") },
            contentAlignment = Alignment.Center) {
            key(step) { OnboardingMock(step, language, animate) }
        }
    }
    val text: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                t("ステップ ${step + 1} / ${steps.size}", "Step ${step + 1} of ${steps.size}", "第 ${step + 1} 步，共 ${steps.size} 步"),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(steps[step].title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("onboarding-title"))
            Text(steps[step].body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val buttons: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step > 0) {
                    OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("onboarding-back")) {
                        Text(t("戻る", "Back", "返回"))
                    }
                }
                Button(
                    onClick = { if (last) onFinish() else step++ },
                    modifier = Modifier.weight(if (step > 0) 2f else 1f).heightIn(min = 48.dp).testTag("onboarding-next")
                ) { Text(if (last) t("始める", "Start Creating", "开始创作") else t("次へ", "Next", "下一步")) }
            }
            if (last) {
                TextButton(onClick = onOpenDetailedGuide, modifier = Modifier.fillMaxWidth().testTag("onboarding-guide")) {
                    Text(t("詳しい使い方を見る", "Open the User Guide", "查看使用指南"))
                }
            }
        }
    }

    Surface(Modifier.fillMaxSize().testTag("onboarding-tour"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(WindowInsets.safeDrawing.asPaddingValues()).padding(horizontal = 20.dp, vertical = 12.dp)) {
            header()
            Spacer(Modifier.height(12.dp))
            if (landscape) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight()) { mock() }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                        text()
                        buttons()
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) { mock() }
                Spacer(Modifier.height(12.dp))
                text()
                Spacer(Modifier.height(16.dp))
                buttons()
            }
        }
    }
}
