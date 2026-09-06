package com.hikariatelier.app

internal enum class CanvasSizingMode { FIXED, RESPONSIVE }

internal data class WorkTemplate(val ratio: String, val width: Int, val height: Int) {
    fun code(mode: CanvasSizingMode): String {
        if (mode == CanvasSizingMode.RESPONSIVE) return CIRCLE_TEMPLATE
        return CIRCLE_TEMPLATE
            .substringBefore("\nfunction windowResized()")
            .trimEnd()
            .replace("createCanvas(windowWidth, windowHeight);", "createCanvas($width, $height);") + "\n"
    }
}

// Supplied Template.js; only canvas sizing is adapted for fixed-size new works.
internal const val CIRCLE_TEMPLATE = "function setup() {\n  createCanvas(windowWidth, windowHeight);\n}\n\nfunction draw() {\n  background(9, 9, 11);\n  noStroke();\n  fill(168, 199, 250);\n  circle(width / 2, height / 2, min(width, height) * 0.3);\n}\n\nfunction windowResized() {\n  resizeCanvas(windowWidth, windowHeight);\n}\n"

internal val workTemplates = listOf(
    WorkTemplate("16:9", 960, 540),
    WorkTemplate("4:3", 800, 600),
    WorkTemplate("1:1", 800, 800),
    WorkTemplate("9:16", 540, 960)
)
