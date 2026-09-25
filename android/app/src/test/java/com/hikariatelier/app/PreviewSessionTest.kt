package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Test

class PreviewSessionTest {
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
