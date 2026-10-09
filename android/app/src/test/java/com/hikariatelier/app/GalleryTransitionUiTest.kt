package com.hikariatelier.app

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GalleryTransitionUiTest {
    @get:Rule(order = 0) val activityRegistration = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            shadowOf(context.packageManager).addActivityIfNotPresent(ComponentName(context, ComponentActivity::class.java))
        }
    }
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun tappingAnotherWorkPinsItsActualImageAndBoundsBeforeNavigation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = Files.createTempDirectory("gallery-tap").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val images = PreviewImageRepository(directory, context.assets, scope)
        val calls = mutableListOf<String>()
        var chosenBitmap: Bitmap? = null
        var chosenBounds: Rect? = null
        try {
            val red = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
            images.file("other").also { it.parentFile!!.mkdirs() }.outputStream().use { red.compress(Bitmap.CompressFormat.PNG, 100, it) }
            compose.setContent {
                MaterialTheme {
                    InlineWorkGallery(listOf(Work("current", "Current", ""), Work("other", "Other", "")),
                        activeId = "current", unsaved = false, sort = "名前順", cacheDir = directory,
                        previewRevision = 0, updatedPreviewId = null, text = { it },
                        onOpen = { work, _ ->
                            assertEquals("other", work.id)
                            assertEquals(listOf("prepare"), calls)
                            calls += "open"
                        }, onAdd = {}, state = rememberWorkGalleryState(), images = images,
                        modifier = Modifier.fillMaxSize(), morphProgress = 1f,
                        onPrepareOpen = { work, bitmap, bounds ->
                            assertEquals("other", work.id)
                            chosenBitmap = bitmap; chosenBounds = bounds; calls += "prepare"
                        })
                }
            }
            compose.waitUntil(10_000) { images.peek("other") != null }
            compose.waitForIdle()
            compose.onNodeWithText("Other").performClick()
            compose.runOnIdle {
                assertEquals(listOf("prepare", "open"), calls)
                assertEquals(android.graphics.Color.RED, chosenBitmap!!.getPixel(8, 8))
                assertTrue(chosenBounds!!.usableGalleryBounds())
            }
        } finally { images.close(); scope.cancel(); directory.deleteRecursively() }
    }

    @Test fun morphImageStaysOpaqueOverAWhiteWorkspaceInBothDirections() {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        var progress by mutableFloatStateOf(0.5f)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(200.dp).background(Color.White).testTag("transition")) {
                    WorkGalleryPreview(bitmap, Rect(20f, 20f, 120f, 120f), Rect(20f, 20f, 120f, 120f), progress, Modifier.fillMaxSize(), visible = true)
                }
            }
        }
        for (fraction in listOf(0f, 0.01f, 0.5f, 0.99f, 1f, 0.5f, 0.01f, 0f)) {
            compose.runOnIdle { progress = fraction }
            val pixel = compose.onNodeWithTag("transition").captureToImage().toPixelMap()[70, 70]
            assertEquals(1f, pixel.red, 0.01f)
            assertEquals(0f, pixel.green, 0.01f)
            assertEquals(0f, pixel.blue, 0.01f)
        }
    }
    @Test fun foregroundControlsRemainAboveTheImageAtTheFinalHandoff() {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        var progress by mutableFloatStateOf(0.01f)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(200.dp).background(Color.White).testTag("handoff")) {
                    val bounds = Rect(20f, 20f, 120f, 120f)
                    WorkGalleryPreview(bitmap, bounds, bounds, progress, Modifier.fillMaxSize(), visible = true)
                    WorkGalleryPreviewChrome(bounds, Modifier.fillMaxSize(), visible = true) {
                        Box(Modifier.size(30.dp).background(Color.Green))
                    }
                }
            }
        }
        for (fraction in listOf(0.01f, 0f, 0.01f, 0f)) {
            compose.runOnIdle { progress = fraction }
            val pixels = compose.onNodeWithTag("handoff").captureToImage().toPixelMap()
            assertEquals(1f, pixels[35, 35].green, 0.01f)
            assertEquals(0f, pixels[35, 35].red, 0.01f)
            assertEquals(1f, pixels[90, 90].red, 0.01f)
            assertEquals(0f, pixels[90, 90].green, 0.01f)
        }
    }

    @Test fun toolbarScalesInPlaceAndItsButtonsArriveInOrder() {
        var progress by mutableFloatStateOf(1f)
        compose.setContent {
            Row(Modifier.size(160.dp, 50.dp).background(Color.White).testTag("toolbar")) {
                Box(Modifier.size(40.dp).galleryChrome(progress, GalleryPart.TITLE).background(Color.Red))
                Box(Modifier.size(40.dp).galleryChrome(progress, GalleryPart.PRIMARY, 0).background(Color.Red))
                Box(Modifier.size(40.dp).galleryChrome(progress, GalleryPart.PRIMARY, 3).background(Color.Red))
            }
        }
        compose.runOnIdle { progress = 0.5f }
        val pixels = compose.onNodeWithTag("toolbar").captureToImage().toPixelMap()
        // Smaller green values mean more of each red control has appeared over white.
        assertTrue(pixels[20, 20].green < pixels[60, 20].green)
        assertTrue(pixels[60, 20].green < pixels[100, 20].green)
        compose.runOnIdle { progress = 0f }
        val finalPixels = compose.onNodeWithTag("toolbar").captureToImage().toPixelMap()
        for (x in listOf(20, 60, 100)) assertEquals(0f, finalPixels[x, 20].green, 0.01f)
    }

}
