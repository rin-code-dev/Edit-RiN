package com.hikariatelier.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class AssetRenameTest {
    @Test fun onlyCompleteStaticReferencesOutsideCommentsAreOffered() {
        val code = """
            // loadImage("assets/photo.png");
            /* 'assets/photo.png' */
            loadImage("assets/photo.png");
            loadImage('./assets/photo.png');
            const longer = "assets/photo.png.bak";
            const prefix = "assets/photo.png" + suffix;
            const dynamic = prefix + /* comment */ "assets/photo.png";
            const concat = "assets/photo.png".concat(suffix);
            const template = `assets/photo.png`;
            const escaped = "assets\/photo.png";
            const expression = /"assets\/photo.png"/;
        """.trimIndent()
        val sources = mapOf("sketch.js" to code, "notes.txt" to "\"assets/photo.png\"")
        val found = assetRenameCandidates(sources, "photo.png")
        assertEquals(2, found.size)
        assertEquals(listOf(3, 4), found.map { it.line })
        assertEquals(listOf("assets/photo.png", "./assets/photo.png"), found.map { it.original })
    }

    @Test fun selectedReferencesAreUpdatedAcrossUnsavedSourcesWithoutChangingOtherOccurrences() {
        val sources = mapOf(
            "sketch.js" to "const a = loadImage('assets/old.png'); const b = loadImage('assets/old.png');",
            "helper.js" to "const unsavedImage = loadImage(\"./assets/old.png\");"
        )
        val candidates = assetRenameCandidates(sources, "old.png")
        val request = AssetRenameRequest("old.png", "new'wide.png", listOf(candidates[0], candidates[2]))
        val result = applyAssetRename(sources, request)
        assertEquals("const a = loadImage('assets/new\\'wide.png'); const b = loadImage('assets/old.png');", result["sketch.js"])
        assertEquals("const unsavedImage = loadImage(\"./assets/new'wide.png\");", result["helper.js"])
        assertTrue(sources.getValue("helper.js").contains("old.png"))
    }

    @Test fun htmlAttributesAndCssUrlsUseTheirOwnEscapingAndExcludeCommentsAndInlineCode() {
        val sources = mapOf(
            "index.html" to """<!-- <img src="assets/old.png"> --><img src='assets/old.png'><script>if(x<y)img="assets/old.png";</script>""",
            "style.css" to """/* url('assets/old.png') */ body { background: url("assets/old.png"); content: "assets/old.png"; }""",
            "data.json" to """{"image":"assets/old.png"}"""
        )
        val candidates = assetRenameCandidates(sources, "old.png")
        assertEquals(3, candidates.size)
        val result = applyAssetRename(sources, AssetRenameRequest("old.png", "a'&b.png", candidates))
        assertTrue(result.getValue("index.html").contains("src='assets/a&#39;&amp;b.png'"))
        assertTrue(result.getValue("index.html").contains("img=\"assets/old.png\""))
        assertTrue(result.getValue("style.css").contains("url(\"assets/a'&b.png\")"))
        assertTrue(result.getValue("style.css").contains("content: \"assets/old.png\""))
        assertEquals("""{"image":"assets/a'&b.png"}""", result.getValue("data.json"))
    }

    @Test fun staleDuplicateAndForgedSelectionsFailBeforeAnySourceIsChanged() {
        val original = mapOf("sketch.js" to "loadImage('assets/old.png');")
        val candidate = assetRenameCandidates(original, "old.png").single()
        val changed = mapOf("sketch.js" to "// edited\n" + original.getValue("sketch.js"))
        assertTrue(runCatching { applyAssetRename(changed, AssetRenameRequest("old.png", "new.png", listOf(candidate))) }.isFailure)
        assertTrue(runCatching { applyAssetRename(original, AssetRenameRequest("old.png", "new.png", listOf(candidate, candidate))) }.isFailure)
        assertTrue(runCatching { applyAssetRename(original, AssetRenameRequest("old.png", "new.png", listOf(candidate.copy(start = candidate.start + 1)))) }.isFailure)
        assertEquals("loadImage('assets/old.png');", original["sketch.js"])
        assertEquals("// edited\nloadImage('assets/old.png');", changed["sketch.js"])
    }

    @Test fun noSelectedReferencesLeavesAllCodeUntouched() {
        val original = mapOf("sketch.js" to "loadImage('assets/old.png');")
        assertEquals(original, applyAssetRename(original, AssetRenameRequest("old.png", "new.png")))
    }

    @Test fun nestedTemplateBodiesAndExpressionsNeverBecomeRenameCandidates() {
        val code = """const t = `outer ${'$'}{`nested "assets/old.png"`} ${'$'}{"assets/old.png"}`;
            loadImage("assets/old.png");"""
        val found = assetRenameCandidates(mapOf("sketch.js" to code), "old.png")
        assertEquals(1, found.size)
        assertEquals(2, found.single().line)
    }

    @Test fun editorSelectionMovesForEveryReplacementAndRetainsSelectionDirection() {
        val source = "loadImage('assets/old.png'); loadImage('assets/old.png'); end;"
        val candidates = assetRenameCandidates(mapOf("sketch.js" to source), "old.png")
        val before = TextFieldValue(source, TextRange(source.length, candidates[0].start + 2))
        val request = AssetRenameRequest("old.png", "long'new.png", candidates)
        val after = assetRenameEditorValue(before, "sketch.js", request)
        assertEquals(applyAssetRename(mapOf("sketch.js" to source), request)["sketch.js"], after.text)
        assertEquals(after.text.length, after.selection.start)
        assertEquals(candidates[0].start + 2, after.selection.end)
        assertTrue(after.selection.reversed)
        assertNull(after.composition)
    }

    @Test fun replacementBoundaryAndFollowingFileSelectionUseExactEncodedLength() {
        val source = "loadImage('assets/old.png');"
        val candidate = assetRenameCandidates(mapOf("helper.js" to source), "old.png").single()
        val request = AssetRenameRequest("old.png", "a'b.png", listOf(candidate))
        val after = assetRenameEditorValue(TextFieldValue(source, TextRange(candidate.end)), "helper.js", request)
        assertEquals(candidate.start + "assets/a\\'b.png".length, after.selection.start)
        val untouched = TextFieldValue("const v = 1;", TextRange(2))
        assertSame(untouched, assetRenameEditorValue(untouched, "other.js", request))
    }
}
