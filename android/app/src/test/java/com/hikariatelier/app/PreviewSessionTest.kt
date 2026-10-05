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

    @Test fun classicCjsSupportingFilesExecuteInTheSameOrderAsTheirSourceMappings() {
        val files = mapOf("src/a.cjs" to "const a = 1;\nconst b = 2;", "src/b.js" to "function helper() {}",
            "module.mjs" to "export const ignored = 3;")
        val source = composeProjectSource("function setup() {}", files)
        val locations = previewSourceFiles("function setup() {}", files)
        assertEquals(listOf("src/a.cjs", "src/b.js", "sketch.js"), locations.map { it.file })
        assertEquals("const b = 2;", source.lines()[locations.first().startLine])
        assertEquals("function helper() {}", source.lines()[locations[1].startLine - 1])
        assertEquals("function setup() {}", source.lines()[locations[2].startLine - 1])
        assertFalse(source.contains("export const ignored"))
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

    @Test fun htmlPreviewUsesOriginalDocumentDirectoryAndServesMainAsItsOwnFile() {
        val html = "<html><head><script src='../sketch.js'></script><link rel='stylesheet' href='../styles/style.css'></head></html>"
        val work = Work("html", "Html", "function setup() {}", files = mutableMapOf("pages/index.html" to html, "styles/style.css" to "canvas { width: 40%; }"))
        val run = preparePreviewRun(capturePreviewRun(work, work.code, work.files, work.assets, token = "run"))
        assertEquals("$PREVIEW_ORIGIN/project/run/pages/index.html", previewUrl(run.assets))
        assertTrue(org.json.JSONObject(run.projectConfig).getBoolean("documentMode"))
        assertEquals(work.code, run.assets.virtualFiles["sketch.js"])
        assertTrue(run.assets.virtualFiles.getValue("pages/index.html").contains("src='../sketch.js'"))
        assertEquals(html, work.files["pages/index.html"])
        assertTrue(run.sourceFiles.isEmpty())
    }

    @Test fun moduleRegistrationIsAddedOnlyToEntryResponseAndNeverPersistedOrConcatenated() {
        val entry = "import { paint } from './helpers/paint.mjs'; export function setup() { paint(); }"
        val helper = "export function paint() {}"
        val files = withProjectDocumentConfig(mapOf("src/entry.mjs" to entry, "src/helpers/paint.mjs" to helper),
            ProjectDocumentConfig(moduleEntry = "src/entry.mjs", executionMode = "module"))
        val work = Work("module", "Module", "", files = files.toMutableMap())
        val run = preparePreviewRun(capturePreviewRun(work, "", work.files, work.assets, token = "run"))
        assertTrue(run.assets.virtualFiles.getValue("src/entry.mjs").startsWith(entry + "\n"))
        assertTrue(run.assets.virtualFiles.getValue("src/entry.mjs").contains("__editRinRegisterModuleCallbacks"))
        assertEquals(helper, run.assets.virtualFiles["src/helpers/paint.mjs"])
        assertEquals(entry, work.files["src/entry.mjs"])
        assertFalse(run.shaders.contains("entry.mjs"))
        assertFalse(run.shaders.contains(PROJECT_CONFIG_FILE))
        assertTrue(run.sourceFiles.isEmpty())
        assertEquals(PreviewSourceLocation("src/helpers/paint.mjs", 1),
            previewProjectSourceLocation(run.assets, "$PREVIEW_ORIGIN/project/run/src/helpers/paint.mjs", 1))
    }

    @Test fun configuredClassicOrderRunsIndividualFilesAndKeepsLegacyCombinedCodeForShareCards() {
        val files = withProjectDocumentConfig(mapOf("src/helper.cjs" to "function helper() {}", "index.html" to "<p>Ignored in classic mode</p>"),
            ProjectDocumentConfig(executionMode = "classic", classicScriptOrder = listOf("sketch.js", "src/helper.cjs")))
        val work = Work("classic", "Classic", "function setup() {}", files = files.toMutableMap())
        val run = preparePreviewRun(capturePreviewRun(work, work.code, work.files, work.assets, token = "ordered"))
        val runtime = org.json.JSONObject(run.projectConfig)
        assertFalse(runtime.getBoolean("documentMode"))
        assertFalse(runtime.getBoolean("moduleMode"))
        assertEquals("$PREVIEW_ORIGIN/project/ordered/sketch.js", runtime.getJSONArray("classicScripts").getString(0))
        assertEquals("$PREVIEW_ORIGIN/project/ordered/src/helper.cjs", runtime.getJSONArray("classicScripts").getString(1))
        assertEquals(work.code, run.assets.virtualFiles["sketch.js"])
        assertTrue(run.sourceFiles.isEmpty())
        assertTrue(run.sketchCode.contains("function helper() {}"))
    }
}
