package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Test

class WorkTemplateTest {
    @Test fun allPresetDimensionsMatchTheirRatio() {
        assertEquals(listOf("16:9", "4:3", "1:1", "9:16"), workTemplates.map { it.ratio })
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
                WorkTemplateKind.WEBGL_3D, WorkTemplateKind.SHADER -> "createCanvas(800, 800, WEBGL)"
                WorkTemplateKind.WEBGPU -> "createCanvas(800, 800, navigator.gpu ? WEBGPU : WEBGL)"
                else -> "createCanvas(800, 800)"
            }
            assertTrue("$kind must keep its renderer and fixed dimensions", fixed.contains(expectedCanvas))
            assertFalse(fixed.contains("function windowResized()"))
            assertTrue(responsive.contains("resizeCanvas(windowWidth, windowHeight)"))
            assertEquals(if (kind == WorkTemplateKind.PHYSICS_MATTER) mapOf("matter-js" to "0.20.0") else emptyMap<String, String>(), template.libraries)
        }
    }

}
