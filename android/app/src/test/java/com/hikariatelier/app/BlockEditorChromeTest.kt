package com.hikariatelier.app

import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
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
class BlockEditorChromeTest {
    @get:Rule(order = 0) val activityRegistration = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            shadowOf(context.packageManager).addActivityIfNotPresent(ComponentName(context, ComponentActivity::class.java))
        }
    }
    @get:Rule(order = 1) val compose = createComposeRule()

    private fun results(request: String, revision: String, id: String = "bound_circle") = JSONObject()
        .put("requestId", request).put("expectedRevision", revision).put("total", 1)
        .put("entries", JSONArray().put(JSONObject().put("id", id).put("title", "円").put("detail", "circle(10,20,30)")))

    @Test fun searchResultsCannotReviveClosedQueriesOrAnOlderRevision() {
        val model = BlockEditorChromeModel()
        model.openSearch("search")
        val first = model.beginSearch()
        val second = model.beginSearch()
        assertFalse(model.acceptResults(results(first, "r2"), "r2"))
        assertFalse(model.acceptResults(results(second, "r1"), "r2"))
        assertTrue(model.acceptResults(results(second, "r2"), "r2"))
        assertEquals("bound_circle", model.entries.single().id)
        assertEquals("r2", model.resultRevision)
        model.closeSearch()
        assertFalse(model.acceptResults(results(second, "r2"), "r2"))
        assertTrue(model.entries.isEmpty())
        model.openSearch("parts")
        assertFalse(model.acceptResults(results(second, "r2"), "r2"))
    }

    @Test fun malformedBridgeRowsAndChromeCannotBecomeNativeControls() {
        val state = JSONObject().put("editable", true).put("busy", false).put("error", false)
            .put("status", "ready").put("dismissable", false).put("overlay", false).put("notice", false).put("parts", 0)
        assertNotNull(decodeBlockChromeState(state))
        assertNull(decodeBlockChromeState(JSONObject(state.toString()).put("parts", 0.5)))
        assertNull(decodeBlockChromeState(JSONObject(state.toString()).put("parts", "0")))
        assertNull(decodeBlockChromeState(JSONObject(state.toString()).put("busy", "false")))
        val model = BlockEditorChromeModel().apply { openSearch("search") }
        val request = model.beginSearch()
        assertFalse(model.acceptResults(results(request, "r1", "x".repeat(257)), "r1"))
        val duplicate = results(request, "r1")
        duplicate.getJSONArray("entries").put(duplicate.getJSONArray("entries").getJSONObject(0))
        duplicate.put("total", 2)
        assertFalse(model.acceptResults(duplicate, "r1"))
        assertTrue(model.entries.isEmpty())
    }

    @Test fun narrowNativeToolbarHasOneSetOfActionsAndFullScreenEscapeSurvivesFailure() {
        val model = BlockEditorChromeModel().apply { state = BlockChromeState(editable = true, busy = false) }
        var action = ""
        var exited = false
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(320.dp)) {
                    BlockEditorFloatingDock(model, ready = false, fullscreen = true, canUndo = true, canRedo = true,
                        text = { it }, onCommand = { action = it }, onSearch = { action = it }, onDisplay = {}, onFullscreen = { exited = true })
                }
            }
        }
        compose.onNodeWithContentDescription("ブロックを追加").assertIsNotEnabled()
        compose.onNodeWithContentDescription("その他の操作").assertIsDisplayed().performClick()
        compose.onNodeWithText("分割表示に戻る").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(exited); assertEquals("", action) }
    }

    @Test fun nativeSearchSelectsOnlyTheVisibleResult() {
        val model = BlockEditorChromeModel().apply {
            openSearch("search")
            val request = beginSearch()
            acceptResults(results(request, "r1"), "r1")
        }
        var located: String? = null
        compose.setContent {
            MaterialTheme { BlockEditorSearchDialog(model, { it }, onQuery = { model.query = it }) { located = it.id } }
        }
        compose.onNodeWithText("円").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("bound_circle", located) }
        compose.onNodeWithText("閉じる").performClick()
        compose.runOnIdle { assertNull(model.searchMode) }
    }

    @Test fun nativeToolbarFits320AndUsesTheAppsMenuForSettings() {
        val model = BlockEditorChromeModel().apply { state = BlockChromeState(editable = true, busy = false) }
        var displayOpened = false
        var fullPreviewOpened = false
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(320.dp).testTag("header")) {
                    EditorDocumentBar(files = listOf("sketch.js"), selectedFile = "sketch.js", dirtyFiles = emptySet(),
                        showingBlocks = true, allowBlocks = true, enabled = true, readOnly = false,
                        autoRunEnabled = true, previewHasChanges = false, onSelectFile = {}, onSelectBlocks = {},
                        onAddFile = null, onToggleAutoRun = {}, onRunChanges = {}, textTranslator = { value, _ -> value },
                        onOpenFullPreview = { fullPreviewOpened = true })
                    BlockEditorFloatingDock(model, ready = true, fullscreen = false, canUndo = true, canRedo = true,
                        text = { it }, onCommand = {}, onSearch = {}, onDisplay = { displayOpened = true }, onFullscreen = {})
                }
            }
        }
        val header = compose.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot
        for (label in listOf("プレビューを全画面表示", "ブロックを追加", "元に戻す", "やり直す", "作品内を探す", "その他の操作")) {
            val button = compose.onNodeWithContentDescription(label).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("$label: $button outside $header", button.left >= header.left && button.right <= header.right)
        }
        compose.onNodeWithContentDescription("プレビューを全画面表示").performClick()
        compose.runOnIdle { assertTrue(fullPreviewOpened) }
        java.io.FileOutputStream("/tmp/edit-rin-native-block-header.png").use { output ->
            compose.onNodeWithTag("header").captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
        compose.onNodeWithContentDescription("その他の操作").performClick()
        compose.onNodeWithText("処理の一覧").assertIsDisplayed()
        compose.onNodeWithText("表示設定").performClick()
        compose.runOnIdle { assertTrue(displayOpened) }
    }

    @Test fun nativeDisplayChangesAreAppliedOnlyWhenConfirmed() {
        var applied: BlockEditorDisplayPreferences? = null
        compose.setContent {
            MaterialTheme { BlockEditorDisplayDialog(BlockEditorDisplayPreferences(), { it }, onDismiss = {}, onApply = { applied = it }) }
        }
        compose.onNodeWithContentDescription("文字サイズ").performSemanticsAction(SemanticsActions.SetProgress) { it(18.5f) }
        compose.onNodeWithText("ゆったり表示（タッチ向け）").performClick()
        compose.runOnIdle { assertNull(applied) }
        compose.onNodeWithText("適用").performClick()
        compose.runOnIdle { assertEquals(BlockEditorDisplayPreferences(18.5, "comfortable"), applied) }
    }
}
