package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Test

class WorkTemplateTest {
    @Test fun allPresetDimensionsMatchTheirRatio() {
        assertEquals(listOf("16:9", "3:2", "4:3", "1:1", "4:5", "3:4", "9:16"), workTemplates.map { it.ratio })
        workTemplates.forEach {
            val parts = it.ratio.split(":").map(String::toInt)
            assertEquals(parts[0] * it.height, parts[1] * it.width)
        }
    }

    @Test fun templatesUseSelectedDimensionsAndResponsiveDrawing() {
        workTemplates.forEach {
            val code = it.code(CanvasSizingMode.FIXED)
            assertTrue(code.contains("createCanvas(${it.width}, ${it.height});"))
            assertTrue(code.contains("circle(width / 2, height / 2, min(width, height) * 0.3);"))
            assertTrue(code.contains("background(9, 9, 11);"))
            assertTrue(code.contains("function setup()"))
            assertTrue(code.contains("function draw()"))
            assertFalse(code.contains("resizeCanvas"))
        }
    }

    @Test fun responsiveTemplatesExplicitlyOwnViewportResizing() {
        workTemplates.forEach {
            val code = it.code(CanvasSizingMode.RESPONSIVE)
            assertTrue(code.contains("createCanvas(windowWidth, windowHeight);"))
            assertTrue(code.contains("function windowResized()"))
            assertTrue(code.contains("resizeCanvas(windowWidth, windowHeight);"))
        }
    }
    @Test fun allKindsKeepCanvasSizingAndLibraryConfiguration() {
        WorkTemplateKind.entries.forEach { kind ->
            val template = WorkTemplate("1:1", 800, 800, kind)
            val fixed = template.code(CanvasSizingMode.FIXED)
            val responsive = template.code(CanvasSizingMode.RESPONSIVE)
            val expectedCanvas = when (kind) {
                WorkTemplateKind.WEBGL_3D, WorkTemplateKind.SHADER, WorkTemplateKind.BLOCK_THREE, WorkTemplateKind.BRUSH ->
                    "createCanvas(800, 800, WEBGL)"
                WorkTemplateKind.WEBGPU -> "createCanvas(800, 800, renderer)"
                else -> "createCanvas(800, 800)"
            }
            assertTrue("$kind must keep its renderer and fixed dimensions", fixed.contains(expectedCanvas))
            assertFalse(fixed.contains("function windowResized()"))
            assertTrue(responsive.contains("resizeCanvas(windowWidth, windowHeight)"))
            if (kind == WorkTemplateKind.WEBGPU) assertTrue(fixed.contains("navigator.gpu.requestAdapter()"))
            val expectedLibraries = when (kind) {
                WorkTemplateKind.PHYSICS_MATTER -> mapOf("matter-js" to "0.20.0")
                WorkTemplateKind.BRUSH -> mapOf("p5.brush" to "2.2.1")
                else -> emptyMap()
            }
            assertEquals(expectedLibraries, template.libraries)
            assertEquals(kind == WorkTemplateKind.BLOCK_SOUND, template.requiresSound)
            assertEquals(
                when (kind) {
                    WorkTemplateKind.WEBGL_3D, WorkTemplateKind.SHADER, WorkTemplateKind.BLOCK_THREE, WorkTemplateKind.BRUSH ->
                        "createCanvas(800, 800, WEBGL);"
                    WorkTemplateKind.WEBGPU -> "await createCanvas(800, 800, renderer);"
                    else -> "createCanvas(800, 800);"
                },
                template.canvasCallPreview(CanvasSizingMode.FIXED)
            )
            assertEquals(
                when (kind) {
                    WorkTemplateKind.WEBGL_3D, WorkTemplateKind.SHADER, WorkTemplateKind.BLOCK_THREE, WorkTemplateKind.BRUSH ->
                        "createCanvas(windowWidth, windowHeight, WEBGL);"
                    WorkTemplateKind.WEBGPU -> "await createCanvas(windowWidth, windowHeight, renderer);"
                    else -> "createCanvas(windowWidth, windowHeight);"
                },
                template.canvasCallPreview(CanvasSizingMode.RESPONSIVE)
            )
        }
    }

    @Test fun basicTemplatesStaySmallAndParameterLayoutUsesCanvasDimensions() {
        assertEquals(6, WorkTemplateKind.entries.count { it.category == WorkTemplateCategory.BASIC })
        assertEquals(6, WorkTemplateKind.entries.count { it.category == WorkTemplateCategory.BLOCKS })
        assertEquals(5, WorkTemplateKind.entries.count { it.category == WorkTemplateCategory.ADVANCED })
        assertEquals(5, WorkTemplateKind.entries.count { it.advanced })
        listOf(WorkTemplate("9:16", 540, 960), WorkTemplate("16:9", 960, 540)).forEach { size ->
            val code = size.copy(kind = WorkTemplateKind.PARAMETERS).code(CanvasSizingMode.FIXED)
            assertTrue(code.contains("min(width, height) * rinParams.size"))
            assertTrue(code.contains("circle(width / 2, height / 2"))
            assertFalse(code.contains("750"))
            assertFalse(code.contains("400"))
            assertTrue(code.lines().size < 30)
        }
    }

    @Test fun sampleNotesNeverLeakIntoUserWorksWithTheSameId() {
        val user = Work("halo", "Halo", "my own code")
        val sample = Work("builtin-sample:halo", "Halo", "sample", isSample = true)
        assertNull(sampleGuide(user))
        assertNotNull(sampleGuide(sample))
        assertNotNull(sampleGuide(Work("builtin-sample:shapes", "Shapes", "", isSample = true)))
    }
}
