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

    @Test fun parsesChoiceParametersAndHandlesDefaultsAndUpdates() {
        val parameters = workParameters(mapOf("sketch.js" to """
            // @rin choice shape "Shape" circle circle square triangle
            // @rin choice mode "Draw Mode" fast slow normal
            // @rin choice single "Single" only
            // @rin choice badToken "Bad" circle square#1
        """.trimIndent()))
        assertEquals(listOf("shape", "mode"), parameters.map { it.name })

        val shapeParam = parameters[0] as WorkParameter.Choice
        assertEquals("shape", shapeParam.name)
        assertEquals("Shape", shapeParam.label)
        assertEquals("circle", shapeParam.defaultValue)
        assertEquals(listOf("circle", "square", "triangle"), shapeParam.options)

        val modeParam = parameters[1] as WorkParameter.Choice
        assertEquals("mode", modeParam.name)
        assertEquals("Draw Mode", modeParam.label)
        assertEquals("fast", modeParam.defaultValue)
        assertEquals(listOf("fast", "slow", "normal"), modeParam.options)

        // Value lookup: valid vs invalid vs null
        assertEquals("square", parameterValue(shapeParam, "square"))
        assertEquals("circle", parameterValue(shapeParam, "hexagon"))
        assertEquals("circle", parameterValue(shapeParam, null))

        // JSON serialization
        val json = JSONObject(parameterValuesJson(parameters, mapOf("shape" to "triangle", "mode" to "slow")))
        assertEquals("triangle", json.getString("shape"))
        assertEquals("slow", json.getString("mode"))

        // Applying defaults rewrites source code and keeps remaining options
        val source = "// @rin choice shape \"Shape\" circle circle square triangle\n"
        val (rewritten1, changed1) = applyParameterDefaults(source, mapOf("shape" to "square"))!!
        assertEquals(setOf("shape"), changed1)
        val reparsed1 = workParameters(mapOf("sketch.js" to rewritten1)).single() as WorkParameter.Choice
        assertEquals("square", reparsed1.defaultValue)
        assertEquals(listOf("circle", "square", "triangle"), reparsed1.options)

        // Sequential rewrite on the same line preserves options without duplicate ballooning
        val (rewritten2, changed2) = applyParameterDefaults(rewritten1, mapOf("shape" to "triangle"))!!
        assertEquals(setOf("shape"), changed2)
        val reparsed2 = workParameters(mapOf("sketch.js" to rewritten2)).single() as WorkParameter.Choice
        assertEquals("triangle", reparsed2.defaultValue)
        assertEquals(listOf("circle", "square", "triangle"), reparsed2.options)

        // Clean syntax without duplicate default rewrites cleanly as well
        val sourceMode = "// @rin choice mode \"Draw Mode\" fast slow normal\n"
        val (rewrittenMode, changedMode) = applyParameterDefaults(sourceMode, mapOf("mode" to "slow"))!!
        assertEquals(setOf("mode"), changedMode)
        val reparsedMode = workParameters(mapOf("sketch.js" to rewrittenMode)).single() as WorkParameter.Choice
        assertEquals("slow", reparsedMode.defaultValue)
        assertEquals(listOf("fast", "slow", "normal"), reparsedMode.options)
    }

    @Test fun rejectsInvalidChoiceDeclarations() {
        val parameters = workParameters(mapOf("sketch.js" to """
            // @rin choice valid "Valid" a b
            // @rin choice onlyOne "One" only
            // @rin choice identical "Identical" dup dup
            // @rin choice tooLong "Long" valid ${"x".repeat(33)}
            // @rin choice hasSlash "Slash" a b/c
            // @rin choice hasHash "Hash" a #bc
        """.trimIndent()))
        assertEquals(listOf("valid"), parameters.map { it.name })
    }
}
