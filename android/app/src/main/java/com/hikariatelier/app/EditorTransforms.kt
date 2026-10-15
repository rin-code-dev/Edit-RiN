package com.hikariatelier.app

private const val FORMAT_INDENT = "  "

internal sealed interface FormatResult {
    data class Success(val code: String) : FormatResult
    object UnbalancedBrackets : FormatResult
    object UnfinishedString : FormatResult
}

private val REGEX_PREFIX_KEYWORDS = setOf(
    "return", "case", "throw", "yield", "await",
    "typeof", "void", "delete", "instanceof", "in", "do"
)

/**
 * Validate delimiters and reindent ordinary code, including division expressions.
 * This is not a JavaScript parser: preserve the whole source when it contains
 * templates, block comments, regex literals or continued strings. Their exact
 * whitespace and surrounding expression context must not change.
 */
internal fun formatJavaScriptDetailed(source: String): FormatResult {
    data class Line(val text: String, val leadingClosers: Int, val balance: Int)
    val lines = mutableListOf<Line>()
    val delimiters = ArrayDeque<Char>()
    var quote: Char? = null
    var blockComment = false
    var templateText = false
    var openTemplates = 0
    var preserveSource = false
    var lastChar: Char? = null
    var lastToken = ""

    for (rawLine in source.lines()) {
        var index = 0
        var balance = 0
        var leadingClosers = 0
        var onlyClosers = true
        var continuedString = false
        while (index < rawLine.length) {
            val char = rawLine[index]
            if (blockComment) {
                val end = rawLine.indexOf("*/", index)
                if (end < 0) break
                blockComment = false
                index = end + 2
                continue
            }
            if (quote != null) {
                if (char == '\\') {
                    if (index + 1 == rawLine.length) continuedString = true
                    index += 2
                    continue
                }
                if (char == quote) {
                    quote = null
                    lastChar = char
                    lastToken = "literal"
                }
                index++
                continue
            }
            if (templateText) {
                when {
                    char == '\\' -> index += 2
                    char == '`' -> {
                        openTemplates--
                        templateText = false
                        lastChar = '`'
                        lastToken = "literal"
                        index++
                    }
                    char == '$' && rawLine.getOrNull(index + 1) == '{' -> {
                        // A distinct delimiter returns to template text after interpolation.
                        delimiters.addLast('$')
                        templateText = false
                        lastChar = '{'
                        lastToken = "{"
                        index += 2
                    }
                    else -> index++
                }
                continue
            }
            if (char.isWhitespace()) { index++; continue }
            if (char == '/' && rawLine.getOrNull(index + 1) == '/') break
            if (char == '/' && rawLine.getOrNull(index + 1) == '*') {
                preserveSource = true
                blockComment = true
                index += 2
                continue
            }
            if (char == '\'' || char == '"') {
                quote = char
                onlyClosers = false
                index++
                continue
            }
            if (char == '`') {
                preserveSource = true
                openTemplates++
                templateText = true
                onlyClosers = false
                index++
                continue
            }
            if (char == '/') {
                if (isRegexStart(lastChar, lastToken)) {
                    val end = javaScriptRegexLiteralEnd(rawLine, index)
                        ?: return FormatResult.UnbalancedBrackets
                    preserveSource = true
                    index = end
                    lastChar = 'v'
                    lastToken = "literal"
                } else {
                    lastChar = '/'
                    lastToken = "/"
                    index++
                }
                onlyClosers = false
                continue
            }
            if (char == '{' || char == '[' || char == '(') {
                delimiters.addLast(char)
                balance++
                onlyClosers = false
            } else if (char == '}' || char == ']' || char == ')') {
                val expected = when (char) { '}' -> '{'; ']' -> '['; else -> '(' }
                val opener = delimiters.removeLastOrNull()
                if (char == '}' && opener == '$') {
                    templateText = true
                } else {
                    if (opener != expected) return FormatResult.UnbalancedBrackets
                    balance--
                    if (onlyClosers) leadingClosers++
                }
            } else if (char.isLetterOrDigit() || char == '_' || char == '$') {
                val start = index++
                while (index < rawLine.length && (rawLine[index].isLetterOrDigit() || rawLine[index] == '_' || rawLine[index] == '$')) index++
                lastToken = rawLine.substring(start, index)
                lastChar = rawLine[index - 1]
                onlyClosers = false
                continue
            } else {
                onlyClosers = false
                if ((char == '+' || char == '-') && rawLine.getOrNull(index + 1) == char) {
                    // Postfix operators can be followed by division, not a regex literal.
                    lastChar = 'v'
                    lastToken = "postfix"
                    index += 2
                    continue
                }
            }
            lastChar = char
            lastToken = char.toString()
            index++
        }
        if (quote != null) {
            if (!continuedString) return FormatResult.UnfinishedString
            preserveSource = true
        }
        lines += Line(rawLine.trim(), leadingClosers, balance)
    }
    if (quote != null || openTemplates != 0) return FormatResult.UnfinishedString
    if (blockComment || delimiters.isNotEmpty()) return FormatResult.UnbalancedBrackets
    if (preserveSource) return FormatResult.Success(source)

    var depth = 0
    val result = ArrayList<String>(lines.size)
    for (line in lines) {
        if (line.text.isEmpty()) {
            result += ""
        } else {
            result += FORMAT_INDENT.repeat((depth - line.leadingClosers).coerceAtLeast(0)) + line.text
            depth = (depth + line.balance).coerceAtLeast(0)
        }
    }
    val newline = if (source.contains("\r\n")) "\r\n" else "\n"
    return FormatResult.Success(result.joinToString(newline))
}

internal fun formatJavaScript(source: String): String = when (val result = formatJavaScriptDetailed(source)) {
    is FormatResult.Success -> result.code
    else -> source
}

private fun isRegexStart(lastChar: Char?, lastToken: String): Boolean {
    if (lastChar == null || lastToken in REGEX_PREFIX_KEYWORDS) return true
    return lastChar in "([{,;:?=+-*%&|^!~<>"
}

/** Line count is retained; move the cursor with the changed leading indentation. */
internal fun formattedJavaScriptOffset(source: String, formatted: String, offset: Int): Int {
    val cursor = offset.coerceIn(0, source.length)
    val line = source.take(cursor).count { it == '\n' }
    val sourceStart = if (cursor == 0) 0 else source.lastIndexOf('\n', cursor - 1) + 1
    val oldLine = source.substring(sourceStart, source.indexOf('\n', sourceStart).takeIf { it >= 0 } ?: source.length).trimEnd('\r')
    var newStart = 0
    repeat(line) {
        val newline = formatted.indexOf('\n', newStart)
        if (newline < 0) return formatted.length
        newStart = newline + 1
    }
    val newLine = formatted.substring(newStart, formatted.indexOf('\n', newStart).takeIf { it >= 0 } ?: formatted.length).trimEnd('\r')
    val oldIndent = oldLine.takeWhile { it == ' ' || it == '\t' }.length
    val newIndent = newLine.takeWhile { it == ' ' || it == '\t' }.length
    return newStart + (cursor - sourceStart + newIndent - oldIndent).coerceIn(0, newLine.length)
}

internal data class TweetGolfOptions(
    val embedHashtagInCall: Boolean = true,
    val preferNewlines: Boolean = true,
    val aggressiveGolf: Boolean = true
)

private val P5_AND_JS_RESERVED_WORDS = setOf(
    // JS keywords & globals
    "let", "const", "var", "function", "return", "if", "else", "for", "while", "do",
    "switch", "case", "break", "continue", "new", "true", "false", "null", "undefined",
    "Math", "Array", "Object", "Number", "String", "window", "document", "async", "await",
    // p5 lifecycle & constants
    "setup", "draw", "preload", "P2D", "WEBGL", "WEBGL2", "PI", "TWO_PI", "TAU", "HALF_PI", "QUARTER_PI",
    "HSB", "RGB", "HSL", "CENTER", "CORNER", "CORNERS", "RADIUS", "CLOSE", "OPEN", "CHORD", "PIE",
    "POINTS", "LINES", "TRIANGLES", "TRIANGLE_FAN", "TRIANGLE_STRIP", "QUAD_STRIP", "QUADS", "TESS",
    "ADD", "BLEND", "DARKEST", "LIGHTEST", "DIFFERENCE", "EXCLUSION", "MULTIPLY", "SCREEN", "REPLACE",
    "OVERLAY", "HARD_LIGHT", "SOFT_LIGHT", "DODGE", "BURN", "SUBTRACT",
    // p5 environment & globals
    "width", "height", "frameCount", "deltaTime", "mouseX", "mouseY", "pmouseX", "pmouseY",
    "mouseIsPressed", "keyIsPressed", "key", "keyCode", "touches", "pixelDensity",
    // p5 functions
    "createCanvas", "resizeCanvas", "createGraphics", "background", "clear", "colorMode",
    "fill", "noFill", "stroke", "noStroke", "strokeWeight", "strokeCap", "strokeJoin",
    "push", "pop", "translate", "rotate", "rotateX", "rotateY", "rotateZ", "scale", "shearX", "shearY",
    "applyMatrix", "resetMatrix", "beginShape", "endShape", "vertex", "curveVertex", "bezierVertex",
    "quadraticVertex", "normal", "circle", "ellipse", "arc", "line", "point", "quad", "rect", "square",
    "triangle", "bezier", "curve", "box", "sphere", "cylinder", "cone", "ellipsoid", "torus", "plane",
    "lights", "ambientLight", "directionalLight", "pointLight", "spotLight", "specularMaterial",
    "ambientMaterial", "emissiveMaterial", "normalMaterial", "shininess", "camera", "perspective", "ortho",
    "orbitControl", "sin", "cos", "tan", "asin", "acos", "atan", "atan2", "radians", "degrees",
    "abs", "ceil", "constrain", "dist", "exp", "floor", "lerp", "log", "mag", "map", "max", "min",
    "norm", "pow", "round", "sq", "sqrt", "fract", "noise", "noiseDetail", "noiseSeed",
    "random", "randomSeed", "randomGaussian", "color", "lerpColor", "red", "green", "blue", "alpha",
    "hue", "saturation", "brightness", "blendMode", "drawingContext", "filter", "BLUR", "POSTERIZE", "THRESHOLD", "INVERT"
)

/**
 * Extreme code golfer for #つぶやきProcessing.
 * Produces ultra-short p5.js code using idiomatic tweet-coding tricks:
 * - Inlines constants (`const SIZE = 600` -> `600`)
 * - Merges `setup()` into `draw=_=>{t||createCanvas(...)`
 * - Embeds `#つぶやきProcessing` as tagged template literal `noStroke`#つぶやきProcessing``
 * - Converts `for(i=0;i<N;i++)` into `for(i=N;i--;)` and folds `pop(drawCall())` into loop header
 * - Renames multi-char user variables to single-letter identifiers
 */
internal fun minifyJavaScriptForTweet(
    source: String,
    options: TweetGolfOptions = TweetGolfOptions()
): String {
    if (source.isBlank()) return ""

    // 0. Strip existing #つぶやきProcessing header comment if present
    var code = source.replace(
        Regex("""^\s*//\s*#つぶやきProcessing[^\n\r]*[\r\n]*"""),
        ""
    )

    // 1. Strip all comments while keeping literals intact
    code = stripCommentsPreservingLiterals(code)

    if (options.aggressiveGolf) {
        // 2. Inline constant declarations (e.g., const CANVAS_SIZE = 600; const RING_COUNT = 36;)
        code = inlineConstantDeclarations(code)

        // 3. Remove non-essential setup calls like pixelDensity(...)
        code = code.replace(Regex("""\bpixelDensity\s*\([^)]*\)\s*;?"""), "")
    }

    // 4. Extract setup() and draw() and merge into `t=0\ndraw=_=>{...}`
    code = mergeSetupAndDrawForTweet(code, options.aggressiveGolf)

    // 5. Core golf transformations (canvas folding, math constants, loops, variable renaming)
    code = applyExtremeGolfTransforms(code, options)

    // 6. Embed `#つぶやきProcessing` tag via template literal or header
    code = injectTweetHashtag(code, options.embedHashtagInCall)

    // 7. Final token compaction
    return compactTokensForTweet(code, preferNewlines = options.preferNewlines)
}

private fun inlineConstantDeclarations(source: String): String {
    var code = source
    val constRegex = Regex("""^\s*const\s+([A-Za-z_$][\w$]*)\s*=\s*(-?\d+(?:\.\d+)?)\s*;?\s*$""", RegexOption.MULTILINE)
    val constants = mutableMapOf<String, String>()

    for (match in constRegex.findAll(code)) {
        val name = match.groupValues[1]
        val value = match.groupValues[2]
        // Do not inline the frame counter `t` or `time` if it is mutated later
        val isMutated = Regex("""\b${Regex.escape(name)}\s*(?:\+\+|--|[+\-*/%&|^]=|=\s*[^=])""")
            .findAll(code)
            .count() > 1
        if (!isMutated && name != "t" && name != "time") {
            constants[name] = value
        }
    }

    if (constants.isEmpty()) return code

    // Remove the declaration lines of inlined constants
    code = code.lines().filterNot { line ->
        val m = constRegex.matchEntire(line)
        m != null && m.groupValues[1] in constants
    }.joinToString("\n")

    // Replace all identifier occurrences with their literal values
    for ((name, value) in constants) {
        code = code.replace(Regex("""\b${Regex.escape(name)}\b"""), value)
    }
    return code
}

private fun mergeSetupAndDrawForTweet(source: String, aggressive: Boolean): String {
    val extracted = extractP5Structure(source)
    if (!extracted.hasSetupOrDraw) return source

    var globals = extracted.globals.trim()
    var setupBody = extracted.setupBody.trim()
    var drawBody = extracted.drawBody.trim()

    // Normalize `time` to `t`
    if (Regex("""\btime\b""").containsMatchIn(source) && !Regex("""\bt\b""").containsMatchIn(source)) {
        globals = globals.replace(Regex("""\btime\b"""), "t")
        setupBody = setupBody.replace(Regex("""\btime\b"""), "t")
        drawBody = drawBody.replace(Regex("""\btime\b"""), "t")
    }

    // Remove `let/const/var` from globals
    globals = globals.replace(Regex("""\b(?:let|const|var)\s+"""), "").trim()

    val hasTimeVar = Regex("""\bt\s*=""").containsMatchIn(globals) ||
        Regex("""\bt\b""").containsMatchIn(drawBody)

    // Ensure `t=0` is at the top if `t` is used or setup needs `t||createCanvas`
    if (hasTimeVar && !Regex("""\bt\s*=""").containsMatchIn(globals)) {
        globals = if (globals.isEmpty()) "t=0" else "t=0\n$globals"
    }

    // Extract createCanvas(...) from setupBody if present
    val canvasRegex = Regex("""\bcreateCanvas\s*\([^)]*\)\s*;?""")
    val canvasCall = canvasRegex.find(setupBody)?.value?.trim()?.removeSuffix(";")
    if (canvasCall != null) {
        setupBody = setupBody.replace(canvasRegex, "").trim()
    }

    return buildString {
        if (globals.isNotBlank()) {
            append(globals).append("\n")
        } else if (canvasCall != null && aggressive) {
            append("t=0\n")
        }

        append("draw=_=>{")
        if (canvasCall != null) {
            if (aggressive && (hasTimeVar || Regex("""\bt\b""").containsMatchIn(drawBody))) {
                append("t||").append(canvasCall).append("\n")
            } else {
                append(canvasCall).append("\n")
            }
        }
        if (setupBody.isNotBlank()) {
            append(setupBody).append("\n")
        }
        if (drawBody.isNotBlank()) {
            append(drawBody)
        }
        if (aggressive && canvasCall != null && !Regex("""\bt\s*(?:\+\+|\+=|=\s*\(?t)""").containsMatchIn(drawBody)) {
            append("\n++t")
        }
        append("}")
        if (extracted.others.isNotBlank()) {
            append("\n").append(extracted.others.trim())
        }
    }
}

private fun applyExtremeGolfTransforms(source: String, options: TweetGolfOptions): String {
    var code = source

    // 1. Remove all `let`, `const`, `var`
    code = code.replace(Regex("""\b(?:let|const|var)\s+"""), "")

    // 2. Replace Math.* and p5 angle constants
    code = code.replace(Regex("""\bTWO_PI\b"""), "TAU")
    code = code.replace(Regex("""\bMath\.PI\s*\*\s*2\b"""), "TAU")
    code = code.replace(Regex("""\b2\s*\*\s*Math\.PI\b"""), "TAU")
    code = code.replace(Regex("""\b2\s*\*\s*PI\b"""), "TAU")
    code = code.replace(Regex("""\bPI\s*\*\s*2\b"""), "TAU")
    code = code.replace(Regex("""\bHALF_PI\b"""), "PI/2")
    code = code.replace(Regex("""\bQUARTER_PI\b"""), "PI/4")
    code = code.replace(Regex("""\bMath\.(sin|cos|tan|atan2|sqrt|abs|min|max|floor|ceil|round|pow|random|PI)\b"""), "$1")

    // 3. Fold createCanvas(N, N[, MODE]) -> createCanvas(W=N,W[,MODE]) or createCanvas(W=(w=N/2)*2,W)
    val canvasMatch = Regex("""\bcreateCanvas\s*\(\s*(\d+)\s*,\s*\1\s*(?:,\s*([A-Za-z0-9_]+))?\s*\)""").find(code)
    if (canvasMatch != null) {
        val fullSize = canvasMatch.groupValues[1].toIntOrNull()
        val modeArg = canvasMatch.groupValues[2].takeIf { it.isNotEmpty() }
        if (fullSize != null && fullSize >= 100 && !Regex("""\bW\b""").containsMatchIn(code)) {
            val halfSize = fullSize / 2
            val hasHalfLiteral = fullSize % 2 == 0 &&
                Regex("""\b$halfSize\b""").findAll(code).count() >= 1 &&
                !Regex("""\bw\b""").containsMatchIn(code)
            val modeSuffix = if (modeArg != null) ",$modeArg" else ""

            if (hasHalfLiteral) {
                code = code.replaceRange(
                    canvasMatch.range,
                    "createCanvas(W=(w=$halfSize)*2,W$modeSuffix)"
                )
                code = code.replace(Regex("""\b$fullSize\b"""), "W")
                code = code.replace(Regex("""\b$halfSize\b"""), "w")
                code = code.replace(Regex("""\b(?:width|height)\s*\*\s*0?\.5\b"""), "w")
                code = code.replace(Regex("""\b(?:width|height)\s*/\s*2\b"""), "w")
                code = code.replace(Regex("""\b(?:width|height)\b"""), "W")
                code = code.replace("createCanvas(W=(w=w)*2,W$modeSuffix)", "createCanvas(W=(w=$halfSize)*2,W$modeSuffix)")
            } else {
                code = code.replaceRange(
                    canvasMatch.range,
                    "createCanvas(W=$fullSize,W$modeSuffix)"
                )
                code = code.replace(Regex("""\b$fullSize\b"""), "W")
                code = code.replace(Regex("""\b(?:width|height)\s*\*\s*0?\.5\b"""), "W/2")
                code = code.replace(Regex("""\b(?:width|height)\s*/\s*2\b"""), "W/2")
                code = code.replace(Regex("""\b(?:width|height)\b"""), "W")
                code = code.replace("createCanvas(W=W,W$modeSuffix)", "createCanvas(W=$fullSize,W$modeSuffix)")
            }
        }
    }

    // 4. Shorten `colorMode(HSB, 360, 100, 100, 100)` or `colorMode(HSB, 360, 100, 100)` when standard
    code = code.replace(Regex("""\bcolorMode\s*\(\s*HSB\s*,\s*360\s*,\s*100\s*,\s*100(?:\s*,\s*100)?\s*\)"""), "colorMode(HSB)")

    // 5. Convert counting loops `for(i=0;i<N;i++)` into `for(i=N;i--;)` when safe
    if (options.aggressiveGolf) {
        code = convertForLoopsToCountdown(code)
    }

    // 6. Flatten control blocks `{ stmt1; stmt2; }` into comma-chained expressions & fold `pop(drawCall())`
    code = flattenControlBlocksWithCommas(code, options.aggressiveGolf)

    // 7. Shorten multi-character user variables into single letters
    if (options.aggressiveGolf) {
        code = renameUserVariablesToSingleChars(code)
    }

    // 8. Standalone post-increment / += 1 -> pre-increment ++t
    code = code.replace(Regex("""(?<=^|[\n;{,])[ \t]*([A-Za-z_$][\w$]*)[ \t]*(?:\+\+|\+=\s*1)[ \t]*(?=$|[\n;}])"""), "++$1")

    // 9. Simplify `t = t + 0.02` -> `t += .02`
    code = code.replace(Regex("""\b([A-Za-z_$][\w$]*)\s*=\s*\1\s*([+\-*/%])\s*([^\n;}]+)(?=$|[\n;}])""")) { m ->
        val v = m.groupValues[1]
        val op = m.groupValues[2]
        val rhs = m.groupValues[3].trim()
        if (op in listOf("+", "-") && rhs.contains('%')) "${v}=${v}${op}${rhs}" else "${v}${op}=${rhs}"
    }

    // 10. Shorten leading-zero decimals: `0.5` -> `.5`, `-0.5` -> `-.5`
    code = code.replace(Regex("""(?<![\w.])0\.(\d+)"""), ".$1")

    // 11. Trim trailing `.0` on floats: `1.0` -> `1`
    code = code.replace(Regex("""(?<!\.)\b(\d+)\.0\b"""), "$1")

    return code
}

private fun convertForLoopsToCountdown(source: String): String {
    // Matches `for (i = 0; i < N; i++)` or `for (i = 0; i < N; ++i)`
    val forRegex = Regex("""\bfor\s*\(\s*([A-Za-z_$][\w$]*)\s*=\s*0\s*;\s*\1\s*<\s*([A-Za-z0-9_$.]+)\s*;\s*(?:\1\s*\+\+|\+\+\s*\1|\1\s*\+=\s*1)\s*\)""")
    return forRegex.replace(source) { match ->
        val iterVar = match.groupValues[1]
        val limitExpr = match.groupValues[2]
        "for($iterVar=$limitExpr;$iterVar--;)"
    }
}

private fun flattenControlBlocksWithCommas(source: String, foldPopIntoFor: Boolean): String {
    val controlRegex = Regex("""\b(for|if|while)\s*\(""")
    var current = source

    // Repeat inside-out so nested `for` loops are flattened from innermost to outermost
    repeat(4) {
        var changed = false
        var searchIndex = 0
        while (searchIndex < current.length) {
            val match = controlRegex.find(current, searchIndex) ?: break
            val keyword = match.groupValues[1]
            val openParen = match.range.last
            val closeParen = findMatchingParen(current, openParen)
            if (closeParen < 0) {
                searchIndex = openParen + 1
                continue
            }
            var afterParen = closeParen + 1
            while (afterParen < current.length && current[afterParen].isWhitespace()) afterParen++
            if (afterParen >= current.length || current[afterParen] != '{') {
                searchIndex = closeParen + 1
                continue
            }
            val closeBrace = findMatchingBrace(current, afterParen)
            if (closeBrace < 0) {
                searchIndex = afterParen + 1
                continue
            }

            val body = current.substring(afterParen + 1, closeBrace).trim()
            val statements = splitTopLevelStatements(body).toMutableList()
            val hasNestedBraces = statements.any { it.contains('{') || it.contains('}') }
            val hasDisallowedKeywords = if (statements.size > 1) {
                statements.any {
                    it.contains(Regex("""\b(for|if|while|switch|return|break|continue|function)\b"""))
                }
            } else {
                false
            }

            if (statements.isNotEmpty() && !hasNestedBraces && !hasDisallowedKeywords) {
                var headerContent = current.substring(openParen + 1, closeParen)

                // Special trick: if `for(...;x--;)` ends with `drawCall(...), pop()`, fold `pop(drawCall(...))` into 3rd clause of `for`
                if (foldPopIntoFor && keyword == "for" && headerContent.trimEnd().endsWith(";") && statements.size >= 2) {
                    val lastStmt = statements.last().trim().removeSuffix(";")
                    val prevStmt = statements[statements.size - 2].trim().removeSuffix(";")
                    if (lastStmt == "pop()" && !prevStmt.startsWith("for") && !prevStmt.contains('=')) {
                        statements.removeAt(statements.size - 1)
                        statements.removeAt(statements.size - 1)
                        headerContent = headerContent.trimEnd() + "pop($prevStmt)"
                    }
                }

                val joined = statements.joinToString(",") { it.trim().removeSuffix(";") }
                val newControl = "$keyword($headerContent)$joined"
                current = current.substring(0, match.range.first) + newControl + current.substring(closeBrace + 1)
                searchIndex = match.range.first + newControl.length
                changed = true
            } else {
                searchIndex = openParen + 1
            }
        }
        if (!changed) return@repeat
    }
    return current
}

private fun renameUserVariablesToSingleChars(source: String): String {
    var code = source

    // 1. Canonical p5 tweet-coding variable names first
    val preferredMappings = listOf(
        "angle" to "T",
        "theta" to "T",
        "delta" to "D",
        "step" to "D",
        "time" to "t",
        "radius" to "r"
    )
    for ((longName, shortName) in preferredMappings) {
        if (Regex("""\b$longName\b""").containsMatchIn(code) &&
            !Regex("""\b$shortName\b""").containsMatchIn(code)
        ) {
            code = code.replace(Regex("""\b$longName\b"""), shortName)
        }
    }

    // 2. Find all remaining multi-character user variables that are assigned to (`ident = ...`)
    val assignedVarRegex = Regex("""\b([A-Za-z_$][\w$]{1,})\s*=(?!=)""")
    val allIdentifiers = Regex("""\b([A-Za-z_$][\w$]*)\b""").findAll(code).map { it.groupValues[1] }.toSet()

    val candidates = assignedVarRegex.findAll(code)
        .map { it.groupValues[1] }
        .filter { it.length >= 2 && it !in P5_AND_JS_RESERVED_WORDS }
        .distinct()
        .toList()

    if (candidates.isEmpty()) return code

    val pool = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVXYZ"
        .map { it.toString() }
        .filter { it !in allIdentifiers && it != "W" && it != "w" && it != "t" }
        .toMutableList()

    for (longVar in candidates) {
        if (pool.isEmpty()) break
        val shortVar = pool.removeAt(0)
        code = code.replace(Regex("""\b${Regex.escape(longVar)}\b"""), shortVar)
    }

    return code
}

private fun injectTweetHashtag(source: String, embedInCall: Boolean): String {
    if (source.contains("#つぶやきProcessing")) return source

    if (embedInCall) {
        // Look for a zero-argument p5 function call like `noStroke()`, `noFill()`, `clear()`
        val zeroArgCallRegex = Regex("""\b(noStroke|noFill|clear|lights|ortho|perspective)\s*\(\s*\)""")
        val match = zeroArgCallRegex.find(source)
        if (match != null) {
            val fnName = match.groupValues[1]
            return source.replaceRange(match.range, "$fnName`#つぶやきProcessing`")
        }
    }

    return "//#つぶやきProcessing #p5js\n$source"
}

private data class P5ExtractedStructure(
    val hasSetupOrDraw: Boolean,
    val globals: String,
    val setupBody: String,
    val drawBody: String,
    val others: String
)

private fun stripCommentsPreservingLiterals(source: String): String {
    val out = StringBuilder(source.length)
    var i = 0
    val len = source.length
    var lastChar: Char? = null
    var lastToken = ""

    fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'

    while (i < len) {
        val c = source[i]
        val next = source.getOrNull(i + 1)

        if (c == '/' && next == '/') {
            i += 2
            while (i < len && source[i] != '\n' && source[i] != '\r') i++
            continue
        }
        if (c == '/' && next == '*') {
            i += 2
            val end = source.indexOf("*/", i)
            i = if (end >= 0) end + 2 else len
            continue
        }
        if (c == '\'' || c == '"' || c == '`') {
            val quote = c
            val start = i++
            while (i < len) {
                val sc = source[i]
                if (sc == '\\') i += 2
                else if (sc == quote) { i++; break }
                else i++
            }
            val lit = source.substring(start, i.coerceAtMost(len))
            out.append(lit)
            lastChar = lit.lastOrNull()
            lastToken = "literal"
            continue
        }
        if (c == '/' && isRegexStart(lastChar, lastToken)) {
            val end = javaScriptRegexLiteralEnd(source, i)
            if (end != null) {
                out.append(source, i, end)
                i = end
                lastChar = 'v'
                lastToken = "literal"
                continue
            }
        }
        if (isWordChar(c)) {
            val start = i
            while (i < len && isWordChar(source[i])) i++
            val word = source.substring(start, i)
            out.append(word)
            lastChar = word.last()
            lastToken = word
            continue
        }
        out.append(c)
        if (!c.isWhitespace()) {
            lastChar = c
            lastToken = c.toString()
        }
        i++
    }
    return out.toString()
}

private fun extractP5Structure(source: String): P5ExtractedStructure {
    val setupRegex = Regex("""(?:async\s+)?function\s+setup\s*\([^)]*\)\s*\{|(?:(?:let|const|var)\s+)?setup\s*=\s*(?:async\s*)?(?:\([^)]*\)|[A-Za-z_$][\w$]*)\s*=>\s*\{""")
    val drawRegex = Regex("""(?:async\s+)?function\s+draw\s*\([^)]*\)\s*\{|(?:(?:let|const|var)\s+)?draw\s*=\s*(?:async\s*)?(?:\([^)]*\)|[A-Za-z_$][\w$]*)\s*=>\s*\{""")

    var setupRange: IntRange? = null
    var setupBody = ""
    setupRegex.find(source)?.let { match ->
        val openBrace = match.range.last
        val closeBrace = findMatchingBrace(source, openBrace)
        if (closeBrace > openBrace) {
            setupRange = match.range.first..closeBrace
            setupBody = source.substring(openBrace + 1, closeBrace)
        }
    }

    var drawRange: IntRange? = null
    var drawBody = ""
    drawRegex.findAll(source).firstOrNull { m ->
        setupRange?.let { m.range.first !in it } ?: true
    }?.let { match ->
        val openBrace = match.range.last
        val closeBrace = findMatchingBrace(source, openBrace)
        if (closeBrace > openBrace) {
            drawRange = match.range.first..closeBrace
            drawBody = source.substring(openBrace + 1, closeBrace)
        }
    }

    if (setupRange == null && drawRange == null) {
        return P5ExtractedStructure(false, source, "", "", "")
    }

    val excluded = listOfNotNull(setupRange, drawRange).sortedBy { it.first }
    val globalsBuilder = StringBuilder()
    var cursor = 0
    for (range in excluded) {
        if (cursor < range.first) {
            globalsBuilder.append(source.substring(cursor, range.first)).append("\n")
        }
        cursor = range.last + 1
    }
    if (cursor < source.length) {
        globalsBuilder.append(source.substring(cursor))
    }

    return P5ExtractedStructure(
        hasSetupOrDraw = true,
        globals = globalsBuilder.toString(),
        setupBody = setupBody,
        drawBody = drawBody,
        others = ""
    )
}

private fun findMatchingBrace(source: String, openIndex: Int): Int {
    var depth = 0
    var i = openIndex
    val len = source.length
    while (i < len) {
        val c = source[i]
        if (c == '\'' || c == '"' || c == '`') {
            val q = c
            i++
            while (i < len) {
                if (source[i] == '\\') i += 2
                else if (source[i] == q) { i++; break }
                else i++
            }
            continue
        }
        if (c == '{') depth++
        else if (c == '}') {
            depth--
            if (depth == 0) return i
        }
        i++
    }
    return -1
}

private fun findMatchingParen(source: String, openIndex: Int): Int {
    var depth = 0
    var i = openIndex
    val len = source.length
    while (i < len) {
        val c = source[i]
        if (c == '\'' || c == '"' || c == '`') {
            val q = c
            i++
            while (i < len) {
                if (source[i] == '\\') i += 2
                else if (source[i] == q) { i++; break }
                else i++
            }
            continue
        }
        if (c == '(') depth++
        else if (c == ')') {
            depth--
            if (depth == 0) return i
        }
        i++
    }
    return -1
}

private fun splitTopLevelStatements(body: String): List<String> {
    val result = mutableListOf<String>()
    val buf = StringBuilder()
    var parenDepth = 0
    var bracketDepth = 0
    var braceDepth = 0
    var i = 0
    val len = body.length

    while (i < len) {
        val c = body[i]
        if (c == '\'' || c == '"' || c == '`') {
            val q = c
            buf.append(c)
            i++
            while (i < len) {
                val sc = body[i]
                buf.append(sc)
                if (sc == '\\' && i + 1 < len) {
                    buf.append(body[i + 1])
                    i += 2
                } else if (sc == q) {
                    i++
                    break
                } else {
                    i++
                }
            }
            continue
        }
        when (c) {
            '(' -> { parenDepth++; buf.append(c) }
            ')' -> { parenDepth = (parenDepth - 1).coerceAtLeast(0); buf.append(c) }
            '[' -> { bracketDepth++; buf.append(c) }
            ']' -> { bracketDepth = (bracketDepth - 1).coerceAtLeast(0); buf.append(c) }
            '{' -> { braceDepth++; buf.append(c) }
            '}' -> { braceDepth = (braceDepth - 1).coerceAtLeast(0); buf.append(c) }
            ';', '\n' -> {
                if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                    val stmt = buf.toString().trim()
                    if (stmt.isNotEmpty()) result.add(stmt)
                    buf.setLength(0)
                } else {
                    buf.append(c)
                }
            }
            else -> buf.append(c)
        }
        i++
    }
    val tail = buf.toString().trim()
    if (tail.isNotEmpty()) result.add(tail)
    return result
}

private fun compactTokensForTweet(source: String, preferNewlines: Boolean): String {
    val out = StringBuilder(source.length)
    var i = 0
    val len = source.length
    var lastChar: Char? = null
    var parenDepth = 0

    fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'

    // Preserve first line if it is a `//#...` comment
    if (source.startsWith("//")) {
        val nl = source.indexOf('\n')
        if (nl >= 0) {
            out.append(source.substring(0, nl + 1))
            i = nl + 1
            lastChar = '\n'
        }
    }

    while (i < len) {
        val c = source[i]

        if (c == '\'' || c == '"' || c == '`') {
            val quote = c
            val start = i++
            while (i < len) {
                val sc = source[i]
                if (sc == '\\') i += 2
                else if (sc == quote) { i++; break }
                else i++
            }
            val lit = source.substring(start, i.coerceAtMost(len))
            out.append(lit)
            lastChar = lit.lastOrNull()
            continue
        }

        if (c == '(') parenDepth++
        if (c == ')') parenDepth = (parenDepth - 1).coerceAtLeast(0)

        if (c == ';') {
            var lookahead = i + 1
            var hasNewline = false
            while (lookahead < len && source[lookahead].isWhitespace()) {
                if (source[lookahead] == '\n' || source[lookahead] == '\r') hasNewline = true
                lookahead++
            }
            val nextNonWs = source.getOrNull(lookahead)
            if (nextNonWs == null || nextNonWs == '}' || (preferNewlines && hasNewline && parenDepth == 0)) {
                i++
                continue
            }
        }

        if (c.isWhitespace()) {
            var seenNewline = false
            while (i < len && source[i].isWhitespace()) {
                if (source[i] == '\n' || source[i] == '\r') seenNewline = true
                i++
            }
            if (i >= len) break
            val nextChar = source[i]
            val prev = lastChar

            if (seenNewline && parenDepth == 0) {
                if (nextChar == '}') {
                    continue
                }
                if (preferNewlines) {
                    if (prev != null && prev != '\n' && prev != '{') {
                        out.append('\n')
                        lastChar = '\n'
                    }
                } else {
                    if (prev != null && prev != ';' && prev != '{' && prev != '}' && prev != ',') {
                        out.append(';')
                        lastChar = ';'
                    }
                }
            } else {
                if (prev != null && isWordChar(prev) && isWordChar(nextChar)) {
                    out.append(' ')
                    lastChar = ' '
                }
            }
            continue
        }

        if (c == '}') {
            while (out.isNotEmpty() && (out.last() == ';' || out.last() == '\n' || out.last() == ' ')) {
                out.deleteCharAt(out.length - 1)
            }
        }

        out.append(c)
        lastChar = c
        i++
    }

    return out.toString().trim()
}
