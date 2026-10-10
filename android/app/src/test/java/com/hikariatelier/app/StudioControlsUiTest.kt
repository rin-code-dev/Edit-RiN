package com.hikariatelier.app

import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StudioControlsUiTest {
    @get:Rule(order = 0) val activityRegistration = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            shadowOf(context.packageManager).addActivityIfNotPresent(
                ComponentName(context, ComponentActivity::class.java)
            )
        }
    }
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun faderDragUpdatesValueAndCommitsOnce() {
        var value by mutableFloatStateOf(0.25f)
        var commits = 0
        compose.setContent {
            MaterialTheme {
                StudioSlider(value, { value = it },
                    modifier = Modifier.width(280.dp).testTag("fader"),
                    onValueChangeFinished = { commits++ })
            }
        }
        compose.onNodeWithTag("fader").performTouchInput {
            swipe(centerLeft + androidx.compose.ui.geometry.Offset(width * .25f, 0f),
                centerLeft + androidx.compose.ui.geometry.Offset(width * .8f, 0f))
        }
        compose.runOnIdle {
            assertTrue(value > .6f)
            assertEquals(1, commits)
        }
    }

    @Test fun disabledFaderDoesNotChangeValue() {
        var changes = 0
        compose.setContent {
            MaterialTheme {
                StudioSlider(.5f, { changes++ }, enabled = false,
                    modifier = Modifier.width(280.dp).testTag("fader"))
            }
        }
        compose.onNodeWithTag("fader").assertIsNotEnabled().performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(0, changes) }
    }

    @Test fun segmentedChoicesExposeAndUpdateSelection() {
        var selected by mutableIntStateOf(0)
        compose.setContent {
            MaterialTheme {
                ScrollableChoiceGroup(listOf("Light", "Dark", "Sumi"), selected, { selected = it })
            }
        }
        compose.onNodeWithText("Light").assertIsSelected()
        compose.onNodeWithText("Sumi").performClick().assertIsSelected()
        compose.onNodeWithText("Light").assertIsNotSelected()
        compose.runOnIdle { assertEquals(2, selected) }
    }

    @Test fun historyRequiresAnExplicitMenuChoiceAndSaveRemainsSeparate() {
        var snapshots = 0
        var restores = 0
        var saves = 0
        compose.setContent {
            MaterialTheme {
                SaveRestoreControls(true, MaterialTheme.colorScheme, 36.dp, 28.dp, 17.dp,
                    onSnapshot = { snapshots++ }, onRestore = { restores++ }, onSave = { saves++ },
                    textTranslator = { text, _ -> text })
            }
        }
        compose.onNodeWithContentDescription("履歴").performClick()
        compose.runOnIdle {
            assertEquals(0, snapshots)
            assertEquals(0, restores)
            assertEquals(0, saves)
        }
        compose.onNodeWithText("スナップショット").performClick()
        compose.runOnIdle { assertEquals(1, snapshots) }
        compose.onNodeWithContentDescription("履歴").performClick()
        compose.onNodeWithText("保存済み状態に戻す").performClick()
        compose.runOnIdle { assertEquals(1, restores) }
        compose.onNodeWithContentDescription("作品の全ファイルを保存").performClick()
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test fun guideParametersSaveAndRestoreOnlyPracticeState() {
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { GuideControlIllustration(R.drawable.ic_tune, "ja") } } }
        compose.onNodeWithText("+").performScrollTo().performClick()
        compose.onNodeWithTag("guide-diameter").assertTextEquals("直径 130")
        compose.onNodeWithContentDescription("作品の全ファイルを保存").performScrollTo().performClick()
        compose.onNodeWithText("+").performScrollTo().performClick()
        compose.onNodeWithTag("guide-diameter").assertTextEquals("直径 140")
        compose.onNodeWithContentDescription("履歴").performScrollTo().performClick()
        compose.onNodeWithText("保存済み状態に戻す").performClick()
        compose.onNodeWithText("復元", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("guide-diameter").assertTextEquals("直径 130")
        compose.onNodeWithContentDescription("パネルを閉じる").performScrollTo().performClick()
        compose.onNodeWithTag("guide-slider").assertDoesNotExist()
        compose.onNodeWithContentDescription("一時停止").performScrollTo().performClick()
        compose.onNodeWithText("一時停止中").assertExists()
        compose.onNodeWithContentDescription("再開").performScrollTo().performClick()
        java.io.FileOutputStream("/tmp/edit-rin-guide-interactive.png").use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
    }
    @Test fun guideSliderColorSnapshotAndCaptureRespondToInput() {
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { GuideControlIllustration(R.drawable.ic_tune, "ja") } } }
        compose.onNodeWithTag("guide-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(200f) }
        compose.onNodeWithTag("guide-diameter").assertTextEquals("直径 200")
        compose.onNodeWithText("青").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("guide-preview").assertContentDescriptionEquals("200 / 青")
        compose.onNodeWithContentDescription("履歴").performScrollTo().performClick()
        compose.onNodeWithText("スナップショット").performClick()
        compose.onNodeWithText("スナップショットを作成").performScrollTo().performClick()
        compose.onNodeWithContentDescription("パラメータ").performScrollTo().performClick()
        compose.onNodeWithText("初期値に戻す").performScrollTo().performClick()
        compose.onNodeWithContentDescription("履歴").performScrollTo().performClick()
        compose.onNodeWithText("スナップショット").performClick()
        compose.onNodeWithText("この状態を復元").performScrollTo().performClick()
        compose.onNodeWithTag("guide-preview").assertContentDescriptionEquals("200 / 青")
        compose.onNodeWithContentDescription("プレビュー操作").performScrollTo().performClick()
        compose.onNodeWithText("スクリーンショットを試す").performScrollTo().performClick()
        compose.onNodeWithText("撮影結果（練習）").assertExists()
        compose.onNodeWithText("録画を試す").performScrollTo().performClick()
        compose.onNodeWithText("録画を停止").performScrollTo().performClick()
        compose.onNodeWithText("録画を終了しました（練習）", substring = true).assertExists()
    }

    @Test fun guideChaptersOpenAndReturnToPractice() {
        compose.setContent { MaterialTheme { UserGuideScreen("ja", {}) } }
        compose.onNodeWithTag("guide-chapters").performScrollToIndex(4)
        compose.onAllNodesWithText("4. パラメータを調整する").onFirst().performClick()
        compose.onNodeWithTag("guide-slider").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("guide-chapters").performScrollToIndex(0)
        compose.onNodeWithText("操作を試す").performClick()
        compose.onNodeWithText("練習用プレビュー").assertExists()
    }

}
