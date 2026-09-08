package com.hikariatelier.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkStoreCodecTest {
    @Test
    fun futureFormatsKeepKnownFieldsAndMalformedOptionalDataAreRejected() {
        val work = """{"id":"a","title":"A","code":""}"""
        val future = parseWorkStoreJson("""{"version":999,"works":[$work],"futureField":true}""")
        assertEquals("a", future!!.works.single().id)
        for (field in listOf("\"files\":[]", "\"revisions\":{}", "\"files\":{\"bad.js\":42}")) {
            val broken = work.dropLast(1) + "," + field + "}"
            assertNull(parseWorkStoreJson("""{"works":[$broken]}"""))
        }
    }

    @Test
    fun missingActiveWorkFallsBackToExistingWork() {
        val parsed = parseWorkStoreJson("""{"activeWorkId":"missing","works":[{"id":"a","title":"A","code":""}]}""")
        assertEquals("a", parsed!!.activeWorkId)
    }

    @Test
    fun serializationBoundsHistoryWithoutMutatingSession() {
        val work = Work("a", "A", "", revisions = MutableList(50) { WorkRevision("code $it", it.toLong()) })
        val parsed = parseWorkStoreJson(serializeWorkStore(listOf(work), "a"))!!
        assertEquals(30, parsed.works.single().revisions.size)
        assertEquals("code 20", parsed.works.single().revisions.first().code)
        assertEquals(50, work.revisions.size)
    }

    @Test
    fun roundTripRetainsEachWorkRatioAndProjectContents() {
        val ratios = listOf("16:9", "4:3", "1:1", "9:16", "device")
        val originals = ratios.mapIndexed { index, ratio ->
            Work(
                id = "work-$index",
                title = "作品 $index / $ratio",
                code = "const label = \"作品 $index\";\nfunction setup() { createCanvas(320, 240); }",
                files = linkedMapOf(
                    "palette.js" to "const palette = [\"#123456\", \"#abcdef\"];",
                    "drawing.js" to "function drawLabel() {\n  text(label, 10, 20);\n}"
                ),
                revisions = mutableListOf(
                    WorkRevision("// first revision $index", 1_000L + index),
                    WorkRevision("// second revision $index\nbackground(0);", 2_000L + index)
                ),
                previewAspectRatio = ratio,
                p5Version = if (index % 2 == 0) P5_VERSION_CURRENT else P5_VERSION_LEGACY,
                p5SoundEnabled = index % 2 == 0,
                createdAt = 3_000L + index,
                updatedAt = 4_000L + index
            )
        }

        val restored = parseWorkStoreJson(serializeWorkStore(originals, "work-4"))
        assertNotNull(restored)
        restored!!
        assertEquals("work-4", restored.activeWorkId)
        assertEquals(originals.size, restored.works.size)
        originals.zip(restored.works).forEach { (original, actual) ->
            assertEquals(original.id, actual.id)
            assertEquals(original.title, actual.title)
            assertEquals(original.code, actual.code)
            assertEquals(original.previewAspectRatio, actual.previewAspectRatio)
            assertEquals(original.p5Version, actual.p5Version)
            assertEquals(original.p5SoundEnabled, actual.p5SoundEnabled)
            assertEquals(original.files.toMap(), actual.files.toMap())
            assertEquals(original.revisions.toList(), actual.revisions.toList())
            assertEquals(original.createdAt, actual.createdAt)
            assertEquals(original.updatedAt, actual.updatedAt)
        }
    }

    @Test
    fun legacyVersionTwoUsesSquareAndEmptyOptionalProjectData() {
        val restored = parseWorkStoreJson(
            """
            {
              "format": "hikari-atelier",
              "version": 2,
              "works": [
                {"id": "legacy-work", "title": "旧作品", "code": "function draw() {}"}
              ]
            }
            """.trimIndent()
        )

        assertNotNull(restored)
        restored!!
        assertEquals("legacy-work", restored.activeWorkId)
        val work = restored.works.single()
        assertEquals("旧作品", work.title)
        assertEquals("function draw() {}", work.code)
        assertEquals("1:1", work.previewAspectRatio)
        assertEquals(P5_VERSION_LEGACY, work.p5Version)
        assertFalse(work.p5SoundEnabled)
        assertTrue(work.files.isEmpty())
        assertTrue(work.revisions.isEmpty())
        assertTrue(work.createdAt > 0L)
        assertTrue(work.updatedAt > 0L)
    }

    @Test
    fun unsupportedLegacyRatioFallsBackToSquareWithoutChangingCode() {
        val restored = parseWorkStoreJson(
            """
            {
              "format": "hikari-atelier",
              "version": 2,
              "activeWorkId": "legacy-work",
              "works": [
                {
                  "id": "legacy-work",
                  "title": "旧比率",
                  "code": "background(42);",
                  "previewAspectRatio": "invalid"
                }
              ]
            }
            """.trimIndent()
        )

        assertNotNull(restored)
        val work = restored!!.works.single()
        assertEquals("1:1", work.previewAspectRatio)
        assertEquals("background(42);", work.code)
    }

    @Test
    fun blankMissingOrDuplicateWorkIdsRejectTheEntireStore() {
        val invalidWorkArrays = listOf(
            """[{"id":"","title":"Blank","code":""}]""",
            """[{"id":"   ","title":"Whitespace","code":""}]""",
            """[{"title":"Missing ID","code":""}]""",
            """[
                {"id":"same-id","title":"First","code":"background(0);"},
                {"id":"same-id","title":"Second","code":"background(255);"}
            ]"""
        )
        for (works in invalidWorkArrays) {
            val json = """{"format":"hikari-atelier","version":4,"works":$works}"""
            assertNull("Invalid work IDs must not silently merge or replace works", parseWorkStoreJson(json))
        }
    }

    @Test
    fun malformedStoreIsNotTreatedAsAnEmptyValidStore() {
        assertNull(parseWorkStoreJson("{broken"))
        assertNull(parseWorkStoreJson("""{"format":"hikari-atelier","version":4}"""))
        assertNull(parseWorkStoreJson("""{"works":"not an array"}"""))
    }
}
