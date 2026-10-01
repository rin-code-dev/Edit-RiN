package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Test

class PreviewSessionTest {
    @Test fun onlyLatestPreparationCanPublishAndOldBridgeReadsAreRejected() {
        val session = PreviewSession()
        val old = capturePreviewRun(Work("one", "One", "old"), "old", emptyMap(), emptyMap(), token = "old")
        val latest = capturePreviewRun(Work("two", "Two", "new"), "new", emptyMap(), emptyMap(), token = "new")
        session.request(old.token)
        assertTrue(session.publish(preparePreviewRun(old)))
        assertNotNull(session.snapshotFor(old.token))
        session.request(latest.token)
        assertNull(session.snapshotFor(old.token))
        assertFalse(session.publish(preparePreviewRun(old)))
        assertTrue(session.publish(preparePreviewRun(latest)))
        assertEquals("two", session.workId)
        assertTrue(session.sketchCode.startsWith("new"))
        assertNull(session.snapshotFor(old.token))
        session.invalidate()
        assertNull(session.snapshotFor(latest.token))
    }

    @Test fun capturedInputDoesNotObserveSubsequentWorkOrDraftEdits() {
        val work = Work("one", "One", "main", files = mutableMapOf("helper.js" to "saved"))
        val drafts = mutableMapOf("one/helper.js" to "draft")
        val input = capturePreviewRun(work, "main", work.files, work.assets, drafts)
        work.p5SoundEnabled = true
        work.files["helper.js"] = "new saved"
        drafts["one/helper.js"] = "new draft"
        val prepared = preparePreviewRun(input)
        assertFalse(prepared.soundEnabled)
        assertEquals("draft", prepared.assets.virtualFiles["helper.js"])
    }

    @Test fun independentFilesHaveSafeStatementBoundariesAndMatchingLineOffsets() {
        val files = linkedMapOf("a.js" to "const n = 1", "b.js" to "(function(){})() // end")
        val source = composeProjectSource("[1].forEach(() => {})", files)
        assertTrue(source.contains("const n = 1\n;\n(function()"))
        assertTrue(source.contains("// end\n;\n[1]"))
        val locations = previewSourceFiles("[1].forEach(() => {})", files)
        locations.forEach { file -> assertEquals(file.file, previewSourceLocation(locations, file.startLine)?.file) }
        assertEquals("(function(){})() // end", source.lines()[locations[1].startLine - 1])
        assertEquals("[1].forEach(() => {})", source.lines()[locations[2].startLine - 1])
    }

    @Test fun prepareUsesCurrentDraftsAndCreatesNewAssetScope() {
        val work = Work("work", "A", "saved", files = mutableMapOf("helper.js" to "saved helper"))
        val session = PreviewSession()
        val first = session.prepare(work, "main", work.files, emptyMap(), mapOf("work/helper.js" to "draft helper"))
        assertTrue(session.sketchCode.contains("draft helper"))
        assertFalse(session.sketchCode.contains("saved helper"))
        assertEquals("work", session.workId)
        assertEquals(P5_VERSION_CURRENT, session.p5Version)
        assertNotEquals(first, session.prepare(work, "main", work.files, emptyMap()))
    }

    @Test fun prepareSeparatesShadersFromExecutableScript() {
        val work = Work("work", "ShaderWork", "void main() { ... }", files = mutableMapOf(
            "helper.js" to "function helper() {}",
            "effect.frag" to "precision mediump float;\nvoid main() { gl_FragColor = vec4(1.0); }",
            "effect.vert" to "attribute vec3 aPosition;\nvoid main() { gl_Position = vec4(aPosition, 1.0); }"
        ))
        val session = PreviewSession()
        session.prepare(work, "function setup() {}", work.files, emptyMap())
        assertTrue(session.sketchCode.contains("function helper() {}"))
        assertFalse(session.sketchCode.contains("gl_FragColor"))
        assertTrue(session.shaders.contains("effect.frag"))
        assertTrue(session.shaders.contains("effect.vert"))
        assertEquals("precision mediump float;\nvoid main() { gl_FragColor = vec4(1.0); }", session.assets.virtualFiles["effect.frag"])
    }
}
