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

    @Test fun parsesInlineCommentParametersAndExtractsDefaults() {
        val code = """
            let speed = 2.5; // @slider(0.1, 10.0, 0.1)
            let autoStep = 5.0; // @slider(0.0, 100.0)
            const count = 64; // @int(1, 256)
            var baseColor = "#FF3366"; // @color
            let blend = true; // @toggle
            let mode = "sin"; // @choice(sin, cos, noise)
            let customLabel = 1.0; // @slider(0.0, 5.0, 0.5) "Custom Speed"
        """.trimIndent()

        val parameters = workParameters(mapOf("sketch.js" to code))
        assertEquals(listOf("speed", "autoStep", "count", "baseColor", "blend", "mode", "customLabel"), parameters.map { it.name })

        val speedParam = parameters[0] as WorkParameter.Number
        assertEquals("speed", speedParam.name)
        assertEquals("2.5", speedParam.defaultValue)
        assertEquals(0.1f, speedParam.min, 0.001f)
        assertEquals(10.0f, speedParam.max, 0.001f)
        assertEquals(0.1f, speedParam.step, 0.001f)

        val autoStepParam = parameters[1] as WorkParameter.Number
        assertEquals(1.0f, autoStepParam.step, 0.001f)

        val countParam = parameters[2] as WorkParameter.Number
        assertEquals("64", countParam.defaultValue)
        assertEquals(1f, countParam.min, 0.001f)
        assertEquals(256f, countParam.max, 0.001f)
        assertEquals(1f, countParam.step, 0.001f)

        val colorParam = parameters[3] as WorkParameter.Color
        assertEquals("#FF3366", colorParam.defaultValue)

        val boolParam = parameters[4] as WorkParameter.Boolean
        assertEquals("true", boolParam.defaultValue)

        val choiceParam = parameters[5] as WorkParameter.Choice
        assertEquals("sin", choiceParam.defaultValue)
        assertEquals(listOf("sin", "cos", "noise"), choiceParam.options)

        val customParam = parameters[6] as WorkParameter.Number
        assertEquals("Custom Speed", customParam.label)

        // Line lookup
        assertEquals("sketch.js" to 1, findParameterDeclarationLine(mapOf("sketch.js" to code), "speed"))
        assertEquals("sketch.js" to 4, findParameterDeclarationLine(mapOf("sketch.js" to code), "baseColor"))
    }

    @Test fun updatesInlineParametersInSourceCode() {
        val code = """
            let speed = 2.5; // @slider(0.1, 10.0, 0.1)
            let baseColor = "#FF3366"; // @color
            let blend = true; // @toggle
            let mode = "sin"; // @choice(sin, cos, noise)
        """.trimIndent()

        val params = workParameters(mapOf("sketch.js" to code))
        val speedParam = params.find { it.name == "speed" }!!
        val updated1 = updateParameterInSource(code, speedParam, "4.8")!!
        assertTrue(updated1.contains("let speed = 4.8; // @slider(0.1, 10.0, 0.1)"))

        val colorParam = params.find { it.name == "baseColor" }!!
        val updated2 = updateParameterInSource(updated1, colorParam, "#00AABB")!!
        assertTrue(updated2.contains("let baseColor = \"#00AABB\"; // @color"))

        val blendParam = params.find { it.name == "blend" }!!
        val updated3 = updateParameterInSource(updated2, blendParam, "false")!!
        assertTrue(updated3.contains("let blend = false; // @toggle"))

        val modeParam = params.find { it.name == "mode" }!!
        val updated4 = updateParameterInSource(updated3, modeParam, "noise")!!
        assertTrue(updated4.contains("let mode = \"noise\"; // @choice(sin, cos, noise)"))
    }
    @Test fun runtimeParametersKeepAuthoredConstButExposeAMutableBinding() {
        val source = "const speed = 1; // @slider(0, 3, 0.1)"
        val runtime = prepareLiveParameterSource(source)
        assertTrue(runtime.startsWith("let speed = 1;"))
        assertTrue(runtime.contains("__editRinParameterBindings"))
        assertTrue(runtime.contains("speed = v"))
        assertEquals(0, runtime.count { it == '\n' })
        val declaration = workParameters(mapOf("sketch.js" to source)).single()
        assertTrue(updateParameterInSource(source, declaration, "2")!!.startsWith("const speed = 2"))
        assertEquals("const speed = 1;", prepareLiveParameterSource("const speed = 1;"))
    }

    @Test fun parsesPoint2DParametersBothLegacyAndInlineAndSerializesCorrectly() {
        val code = """
            // @rin xy origin "Origin" 0 1 0.5 0 2 1.0 0.05
            let pos = { x: 0.25, y: 0.75 }; // @xy(0, 1) "Position"
            const vec = [-10, 20]; // @xy(-100, 100, -50, 50, 1) "Vector"
            let bad = { x: 0, y: 0 }; // @xy(5, 1)
        """.trimIndent()
        val params = workParameters(mapOf("sketch.js" to code))
        assertEquals(listOf("origin", "pos", "vec"), params.map { it.name })

        val origin = params[0] as WorkParameter.Point2D
        assertEquals("0.50,1.00", origin.defaultValue)
        assertEquals(false, origin.arrayStyle)

        val pos = params[1] as WorkParameter.Point2D
        assertEquals("Position", pos.label)
        assertEquals("0.25,0.75", pos.defaultValue)
        assertEquals(false, pos.arrayStyle)

        val vec = params[2] as WorkParameter.Point2D
        assertEquals("-10,20", vec.defaultValue)
        assertEquals(true, vec.arrayStyle)

        val json = JSONObject(parameterValuesJson(params, mapOf("origin" to "0.25,1.50", "vec" to "15,-25")))
        assertEquals(0.25, json.getJSONObject("origin").getDouble("x"), 0.001)
        assertEquals(1.50, json.getJSONObject("origin").getDouble("y"), 0.001)
        assertEquals(15.0, json.getJSONArray("vec").getDouble(0), 0.001)
        assertEquals(-25.0, json.getJSONArray("vec").getDouble(1), 0.001)

        val updatedPos = updateParameterInSource(code, pos, "0.80,0.20")!!
        assertTrue(updatedPos.contains("let pos = { x: 0.80, y: 0.20 }; // @xy(0, 1) \"Position\""))

        val updatedVec = updateParameterInSource(updatedPos, vec, "30,-40")!!
        assertTrue(updatedVec.contains("const vec = [30, -40]; // @xy(-100, 100, -50, 50, 1) \"Vector\""))

        val updatedOrigin = updateParameterInSource(updatedVec, origin, "0.10,1.90")!!
        assertTrue(updatedOrigin.contains("// @rin xy origin \"Origin\" 0 1 0.10 0 2 1.90 0.05"))

        val runtime = prepareLiveParameterSource("const vec = [-10, 20]; // @xy(-100, 100, -50, 50, 1)")
        assertTrue(runtime.startsWith("let vec = [-10, 20];"))
        assertTrue(runtime.contains("__editRinParameterBindings"))
    }

    @Test fun interpolatesAllParameterTypesAndRespectsLocks() {
        val code = """
            // @rin number speed "Speed" 0 10 0 0.5
            // @rin color ink "Ink" #000000
            // @rin boolean glow "Glow" false
            // @rin choice shape "Shape" circle circle square
            // @rin xy pos "Pos" 0 10 0 0 10 0 0.5
        """.trimIndent()
        val params = workParameters(mapOf("sketch.js" to code))
        val from = mapOf("speed" to "0", "ink" to "#000000", "glow" to "false", "shape" to "circle", "pos" to "0.0,0.0")
        val to = mapOf("speed" to "10", "ink" to "#FF8040", "glow" to "true", "shape" to "square", "pos" to "10.0,4.0")

        val mid = interpolateWorkParameters(params, from, to, 0.5f, lockedNames = setOf("glow"))
        assertEquals("5.0", mid["speed"])
        assertEquals("#804020", mid["ink"])
        assertEquals("false", mid["glow"]) // locked!
        assertEquals("square", mid["shape"]) // threshold >= 0.5
        assertEquals("5.0,2.0", mid["pos"])

        val early = interpolateWorkParameters(params, from, to, 0.25f)
        assertEquals("2.5", early["speed"])
        assertEquals("false", early["glow"])
        assertEquals("circle", early["shape"])
        assertEquals("2.5,1.0", early["pos"])

        val end = interpolateWorkParameters(params, from, to, 1f)
        assertEquals("10.0", end["speed"])
        assertEquals("#FF8040", end["ink"])
        assertEquals("true", end["glow"])
        assertEquals("square", end["shape"])
        assertEquals("10.0,4.0", end["pos"])
    }

    @Test fun parameterPresetsRoundTripThroughWorkStoreCodecWithLimitAndBackwardCompat() {
        val presets = List(MAX_PARAMETER_PRESETS + 3) { i ->
            WorkParameterPreset(id = "p-$i", name = "Look ${i + 1}", values = mapOf("speed" to "$i", "pos" to "0.5,0.5"))
        }
        val work = Work("w1", "Preset Work", "", parameterPresets = presets)
        assertEquals(MAX_PARAMETER_PRESETS, work.parameterPresets.size)

        val serialized = serializeWorkStore(listOf(work), work.id)
        val restored = parseWorkStoreJson(serialized)!!.works.single()
        assertEquals(MAX_PARAMETER_PRESETS, restored.parameterPresets.size)
        assertEquals("Look 1", restored.parameterPresets.first().name)
        assertEquals("0.5,0.5", restored.parameterPresets.first().values["pos"])

        val noPresetsWork = Work("w2", "Plain", "")
        val plainJson = serializeWorkStore(listOf(noPresetsWork), noPresetsWork.id)
        assertTrue(!plainJson.contains("parameterPresets"))
        assertTrue(parseWorkStoreJson(plainJson)!!.works.single().parameterPresets.isEmpty())
    }
}
