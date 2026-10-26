package com.hikariatelier.app

import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.test.core.app.ApplicationProvider
import com.hikariatelier.app.ui.theme.AppThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
class EditorWorkspaceLifetimeTest {
    @get:Rule(order = 0) val activityRegistration = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            shadowOf(context.packageManager).addActivityIfNotPresent(ComponentName(context, ComponentActivity::class.java))
        }
    }
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun editorSurvivesFullSplitAndOrientationChangesWithLatestCallbacks() {
        val initial = EditorWorkspaceState(
            isLandscape = true, editorFocused = false, keyboardVisible = false,
            showResizeHandles = false, showConsole = false, showEditorAccessoryBar = false,
            themeMode = AppThemeMode.SYSTEM, workPreviewRatio = 1f,
            animatedLandscapePreviewFraction = .5f, landscapeEditorOnLeft = false,
            previewActionsExpanded = false, landscapePreviewFraction = .5f, showLandscapeSplitLabel = false,
            activeWorkId = "one", previewRatioSelection = "1:1", portraitRatioDragging = false,
            codeFontFamily = FontFamily.Monospace, hasVisibleCompletions = false, blockModeFullscreen = true
        )
        val state = mutableStateOf(initial)
        var created = 0
        var disposed = 0
        var owner: Any? = null
        var latestMode = false
        compose.setContent {
            MaterialTheme {
                val current = state.value
                EditorWorkspaceLayout(
                    state = current,
                    actions = EditorWorkspaceActions({}, {}, {}, {}, {}, {}),
                    slots = EditorWorkspaceSlots(
                        workBar = {}, landscapeBar = {}, controlBar = {}, console = {}, previewActions = {},
                        editor = { modifier ->
                            val identity = remember { Any().also { created++ } }
                            owner = identity
                            latestMode = current.blockModeFullscreen
                            DisposableEffect(identity) { onDispose { disposed++ } }
                            Box(modifier)
                        },
                        completions = {}, accessory = {}, preview = {}
                    ),
                    textTranslator = { source, _ -> source }
                )
            }
        }
        compose.waitForIdle()
        val firstOwner = owner
        for (next in listOf(
            initial.copy(blockModeFullscreen = false),
            initial.copy(blockModeFullscreen = false, landscapeEditorOnLeft = true),
            initial,
            initial.copy(isLandscape = false),
            initial.copy(isLandscape = false, blockModeFullscreen = false),
            initial
        )) {
            compose.runOnIdle { state.value = next }
            compose.waitForIdle()
            assertSame(firstOwner, owner)
            assertEquals(next.blockModeFullscreen, latestMode)
            assertEquals(1, created)
            assertEquals(0, disposed)
        }
    }
}
