package com.hikariatelier.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class WorkParametersTest {
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
