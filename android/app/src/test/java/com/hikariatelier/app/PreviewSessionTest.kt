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
}
