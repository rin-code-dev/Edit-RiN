package com.hikariatelier.app

import androidx.compose.ui.text.input.TextFieldValue

private val P5_COMPLETIONS = listOf(
    "setup", "draw", "createCanvas", "createVector", "createGraphics", "createShader",
    "resizeCanvas", "windowWidth", "windowHeight", "pixelDensity", "frameRate", "noLoop", "loop", "redraw", "clear", "saveCanvas",
    "WEBGL", "WEBGPU", "HSB", "RGB", "HSL", "CENTER", "CORNER", "RADIUS", "CLOSE", "DEGREES", "RADIANS",
    "PI", "TWO_PI", "HALF_PI",
    "background", "fill", "stroke", "strokeWeight", "noStroke", "noFill", "colorMode", "color", "lerpColor", "blendMode",
    "circle", "ellipse", "rect", "square", "line", "point", "triangle", "quad", "arc",
    "beginShape", "vertex", "curveVertex", "bezierVertex", "endShape",
    "rectMode", "ellipseMode", "imageMode", "angleMode",
    "push", "pop", "translate", "rotate", "rotateX", "rotateY", "rotateZ", "scale",
    "text", "textSize", "textAlign", "textFont", "image", "loadImage", "loadSound", "loadFont", "loadJSON", "loadStrings", "loadShader", "userStartAudio",
    "orbitControl", "box", "sphere", "torus", "cone", "cylinder", "plane",
    "ambientLight", "directionalLight", "pointLight", "normalMaterial", "ambientMaterial", "specularMaterial", "texture", "shader",
    "random", "randomSeed", "noise", "noiseSeed", "map", "dist", "lerp", "constrain", "floor", "ceil", "round", "abs",
    "pow", "sqrt", "atan2", "min", "max", "sin", "cos", "tan",
    "mouseX", "mouseY", "frameCount", "deltaTime", "millis",
    "mousePressed", "mouseDragged", "mouseReleased", "touchStarted", "touchMoved", "touchEnded",
    "rotationX", "rotationY", "rotationZ", "pRotationX", "pRotationY", "pRotationZ",
    "accelerationX", "accelerationY", "accelerationZ", "pAccelerationX", "pAccelerationY", "pAccelerationZ",
    "deviceOrientation", "deviceMoved", "deviceTurned", "deviceShaken", "setMoveThreshold", "setShakeThreshold",
    "rinParams", "rinWatch", "brush", "Matter"
)

private val SHADER_COMPLETIONS = listOf(
    "precision", "mediump", "highp", "lowp", "uniform", "attribute", "varying", "const", "struct", "discard",
    "float", "vec2", "vec3", "vec4", "ivec2", "ivec3", "ivec4", "mat2", "mat3", "mat4", "sampler2D", "samplerCube",
    "gl_Position", "gl_FragColor", "gl_FragCoord", "gl_PointCoord",
    "mix", "smoothstep", "step", "clamp", "fract", "mod", "abs", "floor", "ceil", "min", "max",
    "length", "distance", "dot", "cross", "normalize", "reflect", "refract",
    "sin", "cos", "tan", "atan", "pow", "exp", "log", "sqrt", "texture2D", "texture"
)
private val DECLARATION_WORDS = setOf("function", "class", "const", "let", "var")
private val REGEX_PREFIXES = setOf("=", "(", "[", "{", ",", ":", ";", "return", "case", "!", "?")

internal fun completionPrefix(value: TextFieldValue): String {
    if (!value.selection.collapsed) return ""
    val end = value.selection.start.coerceIn(0, value.text.length)
    var start = end
    while (start > 0 && (value.text[start - 1].isLetterOrDigit() || value.text[start - 1] == '_' ||
        value.text[start - 1] == '$')) start--
    return value.text.substring(start, end)
}

internal data class ProjectSymbol(val name: String, val file: String)
internal data class CompletionCandidate(val name: String, val file: String? = null)

/** One work owns this cache; only changed JS files need their declarations scanned. */
internal class ProjectCompletionCache(
    private val parse: (String, String) -> List<ProjectSymbol> = { file, source -> projectSymbols(mapOf(file to source)) }
) {
    private data class Entry(val source: String, val symbols: List<ProjectSymbol>)
    private val entries = mutableMapOf<String, Entry>()

    @Synchronized fun symbols(sources: Map<String, String>, checkActive: () -> Unit = {}): List<ProjectSymbol> {
        val files = sources.filterKeys { isJavaScriptProjectFile(it) }
        checkActive()
        entries.keys.retainAll(files.keys)
        return buildList {
            files.forEach { (file, source) ->
                checkActive()
                val cached = entries[file]
                val entry = if (cached != null && cached.source == source) cached else Entry(source, parse(file, source)).also {
                    checkActive()
                    entries[file] = it
                }
                addAll(entry.symbols)
            }
        }
    }
}

/** Collect declarations without treating words in comments and literals as code. */
internal fun projectSymbols(sources: Map<String, String>): List<ProjectSymbol> = buildList {
    val seen = HashSet<Pair<String, String>>()
    for ((file, source) in sources.filterKeys { isJavaScriptProjectFile(it) }) {
        var index = 0
        var expectedName = false
        var previousToken = ""
        while (index < source.length) {
            val character = source[index]
            val next = source.getOrNull(index + 1)
            when {
                character.isWhitespace() -> index++
                character == '/' && next == '/' -> {
                    index += 2
                    while (index < source.length && source[index] != '\n') index++
                }
                character == '/' && next == '*' -> {
                    val end = source.indexOf("*/", index + 2)
                    index = if (end < 0) source.length else end + 2
                }
                character == '/' && previousToken in REGEX_PREFIXES -> {
                    val end = javaScriptRegexLiteralEnd(source, index)
                    index = end ?: index + 1
                    expectedName = false
                    previousToken = if (end == null) "/" else "literal"
                }
                character == '\'' || character == '"' || character == '`' -> {
                    val quote = character
                    index++
                    while (index < source.length) {
                        val current = source[index++]
                        if (current == '\\') index = (index + 1).coerceAtMost(source.length)
                        else if (current == quote) break
                    }
                    expectedName = false
                    previousToken = "literal"
                }
                character.isLetter() || character == '_' || character == '$' -> {
                    val start = index++
                    while (index < source.length &&
                        (source[index].isLetterOrDigit() || source[index] == '_' || source[index] == '$')) index++
                    val word = source.substring(start, index)
                    if (expectedName) {
                        if (seen.add(file to word)) add(ProjectSymbol(word, file))
                        expectedName = false
                    } else {
                        expectedName = previousToken != "." && word in DECLARATION_WORDS
                    }
                    previousToken = word
                }
                else -> {
                    if (character != '*' || previousToken != "function") expectedName = false
                    previousToken = character.toString()
                    index++
                }
            }
        }
    }
}

internal fun projectCompletions(
    value: TextFieldValue,
    symbols: List<ProjectSymbol>,
    currentFile: String
): List<CompletionCandidate> {
    val syntax = projectTextSyntax(currentFile)
    if (syntax != ProjectTextSyntax.JAVASCRIPT && syntax != ProjectTextSyntax.SHADER) return emptyList()
    val prefix = completionPrefix(value)
    if (prefix.length < 2) return emptyList()
    val result = ArrayList<CompletionCandidate>(8)
    val seen = HashSet<String>()
    if (syntax == ProjectTextSyntax.SHADER) {
        for (name in SHADER_COMPLETIONS) {
            if (name.startsWith(prefix, ignoreCase = true) && name != prefix && seen.add(name)) {
                result.add(CompletionCandidate(name))
            }
            if (result.size == 8) break
        }
        return result
    }
    for (symbol in symbols.asSequence().filter { it.file == currentFile } +
        symbols.asSequence().filter { it.file != currentFile }) {
        if (symbol.name.startsWith(prefix, ignoreCase = true) && symbol.name != prefix &&
            seen.add(symbol.name)) result.add(CompletionCandidate(symbol.name, symbol.file))
        if (result.size == 8) return result
    }
    for (name in P5_COMPLETIONS) {
        if (name.startsWith(prefix, ignoreCase = true) && name != prefix && seen.add(name)) {
            result.add(CompletionCandidate(name))
        }
        if (result.size == 8) break
    }
    return result
}

internal fun completionHelp(name: String, text: (String) -> String): String {
    val detail = when (name) {
        "circle" -> "(x, y, d)" to "円を描画"
        "rect" -> "(x, y, w, h)" to "四角形を描画"
        "square" -> "(x, y, s)" to "正方形を描画"
        "ellipse" -> "(x, y, w, h)" to "楕円を描画"
        "line" -> "(x1, y1, x2, y2)" to "線を描画"
        "arc" -> "(x, y, w, h, start, stop)" to "円弧を描画"
        "createCanvas", "resizeCanvas" -> "(width, height)" to "キャンバスの大きさ"
        "createVector" -> "(x, y, z)" to "ベクトルを作成"
        "createGraphics" -> "(w, h)" to "オフスクリーン描画"
        "createShader" -> "(vert, frag)" to "シェーダーを作成"
        "loadShader" -> "(vertPath, fragPath)" to "シェーダーを読み込む"
        "background", "fill", "stroke" -> "(color)" to "色を指定"
        "colorMode" -> "(mode, max)" to "カラーモードを設定"
        "lerpColor" -> "(c1, c2, amt)" to "2つの色を補間"
        "blendMode" -> "(mode)" to "合成モードを設定"
        "loadImage" -> "(path)" to "画像を読み込む"
        "loadSound" -> "(path)" to "音声を読み込む"
        "image" -> "(img, x, y)" to "画像を描画"
        "random" -> "(min, max)" to "乱数を生成"
        "noise" -> "(x, y, z)" to "パーリンノイズ"
        "map" -> "(value, a, b, c, d)" to "数値の範囲を変換"
        "lerp" -> "(start, stop, amt)" to "数値を補間"
        "constrain" -> "(n, low, high)" to "数値を範囲内に制限"
        "translate" -> "(x, y)" to "座標を移動"
        "rotate" -> "(angle)" to "座標を回転"
        "text" -> "(str, x, y)" to "文字を描画"
        "orbitControl" -> "()" to "3Dカメラを操作"
        "box" -> "(w, h, d)" to "3Dの箱を描画"
        "sphere" -> "(radius)" to "3Dの球を描画"
        "torus" -> "(radius, tube)" to "3Dのドーナツを描画"
        "shader" -> "(s)" to "シェーダーを適用"
        "rinParams" -> ".name" to "ライブパラメータ値"
        "rinWatch" -> "(label, value)" to "コンソールで値を監視"
        "brush" -> ".pick(name)" to "p5.brush 描画ツール"
        "Matter" -> ".Engine" to "Matter.js 物理演算"
        "smoothstep" -> "(edge0, edge1, x)" to "GLSL 滑らかな補間"
        "mix" -> "(x, y, a)" to "GLSL 線形補間"
        "texture2D", "texture" -> "(sampler, uv)" to "テクスチャを参照"
        else -> return name
    }
    return name + detail.first + " · " + text(detail.second)
}
