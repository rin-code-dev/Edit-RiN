package com.hikariatelier.app

import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.hikariatelier.app.ui.theme.AppThemeMode
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
        compose.onNodeWithContentDescription("目次へ戻る").performClick()
        compose.onNodeWithTag("guide-chapters").performScrollToIndex(0)
        compose.onNodeWithText("操作を試す").performClick()
        compose.onNodeWithText("練習用プレビュー").assertExists()
    }

    @Test fun choiceParameterPanelRendersChipsAndUpdatesSelection() {
        val choiceParam = WorkParameter.Choice(
            name = "mode",
            label = "Draw Mode",
            defaultValue = "circle",
            options = listOf("circle", "square", "triangle")
        )
        var currentValues by mutableStateOf(mapOf("mode" to "circle"))
        var commitCount by mutableIntStateOf(0)
        var resetParam: WorkParameter? by mutableStateOf(null)

        compose.setContent {
            MaterialTheme {
                WorkParameterPanel(
                    parameters = listOf(choiceParam),
                    values = currentValues,
                    onChange = { param, nextVal -> currentValues = currentValues + (param.name to nextVal) },
                    onCommit = { commitCount++ },
                    text = { it },
                    onResetParameter = { resetParam = it }
                )
            }
        }

        compose.onNodeWithText("Draw Mode").assertIsDisplayed()
        compose.onNodeWithText("rinParams.mode").assertIsDisplayed()
        // The option chip (which has click action) should be selected
        compose.onNode(hasClickAction() and hasText("circle")).assertIsSelected()
        compose.onNode(hasClickAction() and hasText("square")).assertIsNotSelected()

        // Click square
        compose.onNode(hasClickAction() and hasText("square")).performClick()
        compose.runOnIdle {
            assertEquals("square", currentValues["mode"])
            assertEquals(1, commitCount)
        }

        // Now that it is modified, reset button and initial value hint should be displayed
        compose.onNodeWithText("初期値: circle").assertIsDisplayed()
        compose.onNodeWithContentDescription("初期値に戻す").performClick()
        compose.runOnIdle {
            assertEquals(choiceParam, resetParam)
        }
    }

    @Test fun fullscreenOverlayControlsWrapIntoPopupOnCompactPortraitAndStayWithinBounds() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(390.dp, 720.dp).testTag("preview-host")) {
                    PreviewOverlayControls(
                        fullscreen = true,
                        showExpandControl = false,
                        isLandscape = false,
                        isRecordingOrCountingDown = false,
                        recordingCountdownRemaining = 0,
                        pendingRecordingFormat = null,
                        isPreviewRecording = false,
                        isRecordingSaving = false,
                        recordingFormatLabel = "MP4",
                        recordingLimitMillis = 0L,
                        previewActionsExpanded = true,
                        onTogglePreviewActions = {},
                        onClosePreviewActions = {},
                        onRotate = {},
                        onScreenshot = {},
                        onShareCard = {},
                        onOpenParameters = {},
                        onToggleRecording = {},
                        onCancelCountdown = {},
                        onToggleFullscreen = {},
                        onStopRecording = {},
                        colors = MaterialTheme.colorScheme,
                        textTranslator = { s, _ -> s },
                        showHud = true,
                        hudMetricsProvider = { PreviewHudMetrics(fps = 60f, frameCount = 120, width = 800, height = 800, paused = true) },
                        isPaused = true,
                        onToggleHud = {},
                        onStepFrame = {},
                        onTogglePause = {}
                    )
                }
            }
        }
        compose.waitForIdle()
        val hostBounds = compose.onNodeWithTag("preview-host").fetchSemanticsNode().boundsInRoot
        val hudBounds = compose.onNodeWithText("PAUSE · #120 · 800×800").fetchSemanticsNode().boundsInRoot
        val toggleNode = compose.onNodeWithContentDescription("プレビュー操作を閉じる").assertIsDisplayed()
        val toggleBounds = toggleNode.fetchSemanticsNode().boundsInRoot
        assertTrue("Toggle button must stay inside host bounds", toggleBounds.right <= hostBounds.right + 1f)
        assertTrue("Toggle button must not overlap HUD pill", toggleBounds.left >= hudBounds.right)

        val closeBtn = compose.onNodeWithContentDescription("全画面表示を閉じる").assertIsDisplayed()
        val shotBtn = compose.onNodeWithContentDescription("プレビューをスクリーンショット").assertIsDisplayed()
        compose.onNodeWithContentDescription("FPS HUD").assertIsDisplayed()
        compose.onNodeWithContentDescription("1コマ送り").assertIsDisplayed()
        compose.onNodeWithContentDescription("再生").assertIsDisplayed()
        compose.onNodeWithContentDescription("パラメータ").assertIsDisplayed()

        // FlowRow wraps into multiple rows in the popup instead of overflowing a single wide row.
        val pauseBounds = compose.onNodeWithContentDescription("再生").fetchSemanticsNode().boundsInRoot
        val closeBounds = closeBtn.fetchSemanticsNode().boundsInRoot
        val shotBounds = shotBtn.fetchSemanticsNode().boundsInRoot
        assertTrue("Wrapped buttons should fit compact popup width", closeBounds.right <= hostBounds.width)
        assertTrue("Popup FlowRow must wrap buttons onto a second row", closeBounds.top > pauseBounds.bottom || shotBounds.top > pauseBounds.bottom)
    }

    @Test fun portraitWorkspaceHidesPreviewActionsTrayInBlockModeAndFocusedEditor() {
        var blockFullscreen by mutableStateOf(false)
        var editorFocused by mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                EditorWorkspaceLayout(
                    state = EditorWorkspaceState(
                        isLandscape = false,
                        editorFocused = editorFocused,
                        keyboardVisible = false,
                        showResizeHandles = false,
                        showConsole = false,
                        showEditorAccessoryBar = false,
                        themeMode = AppThemeMode.LIGHT,
                        workPreviewRatio = 1f,
                        animatedLandscapePreviewFraction = 0.5f,
                        landscapeEditorOnLeft = true,
                        previewActionsExpanded = true,
                        landscapePreviewFraction = 0.5f,
                        showLandscapeSplitLabel = false,
                        activeWorkId = "work-1",
                        previewRatioSelection = "1:1",
                        portraitRatioDragging = false,
                        codeFontFamily = FontFamily.Monospace,
                        hasVisibleCompletions = false,
                        blockModeFullscreen = blockFullscreen
                    ),
                    actions = EditorWorkspaceActions({}, {}, {}, {}, {}, {}),
                    slots = EditorWorkspaceSlots(
                        workBar = { Box(Modifier.fillMaxWidth().height(36.dp)) },
                        landscapeBar = {},
                        controlBar = { Box(Modifier.fillMaxWidth().height(36.dp)) },
                        console = {},
                        previewActions = { Box(it.fillMaxWidth().height(40.dp).testTag("preview-actions-tray")) },
                        editor = { Box(it.fillMaxSize()) },
                        completions = {},
                        accessory = {},
                        preview = { Box(it.fillMaxSize()) }
                    ),
                    textTranslator = { s, _ -> s }
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("preview-actions-tray").assertIsDisplayed()

        compose.runOnIdle { blockFullscreen = true }
        compose.waitForIdle()
        compose.onNodeWithTag("preview-actions-tray").assertDoesNotExist()

        compose.runOnIdle {
            blockFullscreen = false
            editorFocused = true
        }
        compose.waitForIdle()
        compose.onNodeWithTag("preview-actions-tray").assertDoesNotExist()
    }
}
