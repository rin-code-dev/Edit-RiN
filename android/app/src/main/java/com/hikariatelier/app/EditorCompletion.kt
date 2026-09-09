package com.hikariatelier.app

import androidx.compose.ui.text.input.TextFieldValue

private val P5_COMPLETIONS = listOf(
    "setup", "draw", "createCanvas", "resizeCanvas", "windowWidth", "windowHeight",
    "background", "fill", "stroke", "strokeWeight", "noStroke", "noFill", "colorMode",
    "circle", "ellipse", "rect", "line", "triangle", "beginShape", "vertex", "endShape",
    "push", "pop", "translate", "rotate", "scale", "text", "textSize", "image", "loadImage",
    "random", "noise", "map", "dist", "lerp", "constrain", "floor", "ceil", "round", "abs",
    "sin", "cos", "tan", "mouseX", "mouseY", "frameCount", "deltaTime", "millis",
    "mousePressed", "mouseDragged", "mouseReleased", "touchStarted", "touchMoved", "touchEnded"
)

internal fun completionPrefix(value: TextFieldValue): String {
    if (!value.selection.collapsed) return ""
    val end = value.selection.start.coerceIn(0, value.text.length)
    var start = end
    while (start > 0 && (value.text[start - 1].isLetterOrDigit() || value.text[start - 1] == '_')) start--
    return value.text.substring(start, end)
}

internal fun editorCompletions(value: TextFieldValue): List<String> {
    val prefix = completionPrefix(value)
    if (prefix.length < 2) return emptyList()
    return P5_COMPLETIONS.asSequence()
        .filter { it.startsWith(prefix, ignoreCase = true) && it != prefix }
        .take(8).toList()
}
