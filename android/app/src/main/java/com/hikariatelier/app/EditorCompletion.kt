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

internal fun completionHelp(name: String, text: (String) -> String): String {
    val detail = when (name) {
        "circle" -> "(x, y, d)" to "円を描画"
        "rect" -> "(x, y, w, h)" to "四角形を描画"
        "ellipse" -> "(x, y, w, h)" to "楕円を描画"
        "line" -> "(x1, y1, x2, y2)" to "線を描画"
        "createCanvas", "resizeCanvas" -> "(width, height)" to "キャンバスの大きさ"
        "background", "fill", "stroke" -> "(color)" to "色を指定"
        "loadImage" -> "(path)" to "画像を読み込む"
        "image" -> "(img, x, y)" to "画像を描画"
        "random" -> "(min, max)" to "乱数を生成"
        "map" -> "(value, a, b, c, d)" to "数値の範囲を変換"
        "translate" -> "(x, y)" to "座標を移動"
        "rotate" -> "(angle)" to "座標を回転"
        "text" -> "(str, x, y)" to "文字を描画"
        else -> return name
    }
    return name + detail.first + "\n" + text(detail.second)
}
