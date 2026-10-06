package com.hikariatelier.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class WorkParametersTest {
    @Test fun auxiliaryDraftsInvalidateObservedParameterSourcesAndSnapshotsStayImmutable() {
        val files = androidx.compose.runtime.mutableStateMapOf("helper.js" to "// helper")
        val drafts = androidx.compose.runtime.mutableStateMapOf<String, String>()
        val observer = androidx.compose.runtime.snapshots.SnapshotStateObserver { it() }
        var invalidations = 0
        var sources = emptyMap<String, String>()
        observer.start()
        try {
            observer.observeReads(Unit, { invalidations++ }) {
                sources = projectSearchSources("work", "// main", files, drafts)
            }
            val before = sources
            drafts["work/helper.js"] = "// @rin boolean glow \"Glow\" true"
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            assertTrue(invalidations > 0)
            assertTrue(workParameters(before).isEmpty())
            sources = projectSearchSources("work", "// main", files, drafts)
            assertEquals(listOf("glow"), workParameters(sources).map { it.name })
            files.remove("helper.js")
            assertTrue(workParameters(projectSearchSources("work", "// main", files, drafts)).isEmpty())
            assertTrue(workParameters(projectSearchSources("other", "// main", mapOf("helper.js" to ""), drafts)).isEmpty())
        } finally {
            observer.stop()
            observer.clear()
        }
    }

    @Test fun parsesDeclarationsAndRejectsInvalidRanges() {
        val parameters = workParameters(mapOf("sketch.js" to """
            // @rin number speed "Speed" 0 3 1 0.1
            // @rin color ink "Ink" #BA90E2
            // @rin boolean glow "Glow" true
            // @rin boolean trail "Trail" false
            // @rin boolean invalidBool "Invalid" maybe
            // @rin number invalid "Invalid" 3 0 1 0.1
        """.trimIndent()))
        assertEquals(listOf("speed", "ink", "glow", "trail"), parameters.map { it.name })
        val values = JSONObject(parameterValuesJson(parameters, mapOf("speed" to "2", "ink" to "#00ff00", "glow" to "false", "trail" to "true")))
        assertEquals(2.0, values.getDouble("speed"), 0.001)
        assertEquals("#00FF00", values.getString("ink"))
        assertEquals(false, values.getBoolean("glow"))
        assertEquals(true, values.getBoolean("trail"))
    }

    @Test fun storesValuesWithWorkAndFallsBackForOlderWorks() {
        val work = Work("one", "One", "", parameterValues = mapOf("speed" to "2.5"))
        val restored = parseWorkStoreJson(serializeWorkStore(listOf(work), work.id))!!.works.single()
        assertEquals("2.5", restored.parameterValues["speed"])
        val older = parseWorkStoreJson(serializeWorkStore(listOf(Work("old", "Old", "")), "old"))!!.works.single()
        assertTrue(older.parameterValues.isEmpty())
    }
}
