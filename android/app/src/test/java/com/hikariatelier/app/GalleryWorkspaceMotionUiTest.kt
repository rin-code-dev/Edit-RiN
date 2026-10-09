package com.hikariatelier.app

import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
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
class GalleryWorkspaceMotionUiTest {
    @get:Rule(order = 0) val activityRegistration = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            shadowOf(context.packageManager).addActivityIfNotPresent(ComponentName(context, ComponentActivity::class.java))
        }
    }
    @get:Rule(order = 1) val compose = createComposeRule()

    private fun state(progress: Float, landscape: Boolean, left: Boolean) = EditorWorkspaceState(
        isLandscape = landscape, editorFocused = false, keyboardVisible = false,
        showResizeHandles = false, showConsole = false, showEditorAccessoryBar = false,
        compactPreview = false, hideEditingPreview = false, themeMode = AppThemeMode.LIGHT,
        workPreviewRatio = 1f, animatedLandscapePreviewFraction = 0.5f, landscapeEditorOnLeft = left,
        previewActionsExpanded = false, landscapePreviewFraction = 0.5f, showLandscapeSplitLabel = false,
        activeWorkId = "work", previewRatioSelection = "1:1", portraitRatioDragging = false,
        codeFontFamily = FontFamily.Monospace, hasVisibleCompletions = false,
        galleryVisible = progress > 0f, galleryProgress = progress)

    private fun verifyReveal(landscape: Boolean, left: Boolean) {
        var progress by mutableFloatStateOf(0f)
        var editorSize = IntSize.Zero
        var previewSize = IntSize.Zero
        compose.setContent {
            MaterialTheme {
                EditorWorkspaceLayout(state(progress, landscape, left),
                    EditorWorkspaceActions({}, {}, {}, {}, {}, {}),
                    EditorWorkspaceSlots(
                        workBar = { Box(Modifier.fillMaxWidth().height(40.dp)) },
                        landscapeBar = { Box(Modifier.fillMaxWidth().height(40.dp)) },
                        controlBar = { Box(Modifier.fillMaxWidth().height(40.dp)) },
                        console = {}, previewActions = {}, completions = {}, accessory = {},
                        editor = { Box(it.testTag("editor").background(Color.Red).onSizeChanged { editorSize = it }) },
                        preview = { Box(it.testTag("preview").background(Color.Green).onSizeChanged { previewSize = it }) },
                        gallery = { Box(it.background(Color.Blue)) }),
                    textTranslator = { text, _ -> text }, modifier = Modifier.testTag("workspace"))
            }
        }
        compose.waitForIdle()
        val bounds: Rect = compose.onNodeWithTag("editor").fetchSemanticsNode().boundsInRoot
        val origin = compose.onNodeWithTag("workspace").fetchSemanticsNode().boundsInRoot.topLeft
        val initialEditorSize = editorSize
        val initialPreviewSize = previewSize
        assertTrue(initialEditorSize.width > 0 && initialEditorSize.height > 0)
        // Visit both endpoints, mid-reveal and a reversal. The actual workspace must expose
        // the editor before completion while keeping its and the preview's measurements.
        for (fraction in listOf(1f, 0.45f, 0.7f, 0.45f, 0f)) {
            compose.runOnIdle { progress = fraction }
            val pixels = compose.onNodeWithTag("workspace").captureToImage().toPixelMap()
            if (fraction == 0.45f) {
                val shownX = if (landscape) bounds.left + bounds.width * (if (left) 0.8f else 0.2f) else bounds.center.x
                val hiddenX = if (landscape) bounds.left + bounds.width * (if (left) 0.05f else 0.95f) else bounds.center.x
                val shownY = if (landscape) bounds.center.y else bounds.top + bounds.height * 0.2f
                val hiddenY = if (landscape) bounds.center.y else bounds.top + bounds.height * 0.95f
                val shown = pixels[(shownX - origin.x).toInt(), (shownY - origin.y).toInt()]
                val hidden = pixels[(hiddenX - origin.x).toInt(), (hiddenY - origin.y).toInt()]
                assertTrue("Editor reveal must be visible at midpoint: $shown", shown.red > 0.9f && shown.green < 0.4f)
                assertTrue("Editor must still be unfolding: $hidden", hidden.green > 0.6f)
            }
            compose.runOnIdle {
                assertEquals(initialEditorSize, editorSize)
                assertEquals(initialPreviewSize, previewSize)
            }
        }
    }
    @Test fun portraitShowsEditorRevealBeforeTheGalleryTransitionEnds() = verifyReveal(false, true)
    @Test fun landscapeLeftEditorOpensFromThePreviewEdge() = verifyReveal(true, true)
    @Test fun landscapeRightEditorOpensFromThePreviewEdge() = verifyReveal(true, false)
}
