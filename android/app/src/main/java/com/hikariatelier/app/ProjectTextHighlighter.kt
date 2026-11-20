package com.hikariatelier.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

internal enum class ProjectTextSyntax { JAVASCRIPT, HTML, CSS, JSON, SHADER, PLAIN }

internal fun projectTextSyntax(file: String): ProjectTextSyntax = when (file.substringAfterLast('.', "").lowercase()) {
    "js", "mjs", "cjs" -> ProjectTextSyntax.JAVASCRIPT
    "html", "htm", "svg", "xml" -> ProjectTextSyntax.HTML
    "css" -> ProjectTextSyntax.CSS
    "json" -> ProjectTextSyntax.JSON
    "frag", "vert", "glsl", "wgsl" -> ProjectTextSyntax.SHADER
    else -> ProjectTextSyntax.PLAIN
}

internal fun projectFileHighlighter(file: String, dark: Boolean, errors: Set<Int>): VisualTransformation =
    when (val syntax = projectTextSyntax(file)) {
        ProjectTextSyntax.JAVASCRIPT -> JavaScriptHighlighter(dark, errors)
        else -> ProjectTextHighlighter(syntax, dark, errors)
    }

private val SHADER_KEYWORDS = setOf(
    "attribute", "const", "uniform", "varying", "in", "out", "inout", "layout",
    "centroid", "flat", "smooth", "invariant", "precision", "lowp", "mediump", "highp",
    "struct", "void", "if", "else", "for", "while", "do", "switch", "case", "default",
    "break", "continue", "return", "discard", "true", "false",
    "fn", "let", "var"
)

private val SHADER_TYPES = setOf(
    "float", "double", "int", "uint", "bool",
    "vec2", "vec3", "vec4", "dvec2", "dvec3", "dvec4",
    "ivec2", "ivec3", "ivec4", "uvec2", "uvec3", "uvec4", "bvec2", "bvec3", "bvec4",
    "mat2", "mat3", "mat4", "mat2x2", "mat2x3", "mat2x4", "mat3x2", "mat3x3", "mat3x4", "mat4x2", "mat4x3", "mat4x4",
    "sampler2D", "sampler3D", "samplerCube", "sampler2DShadow", "isampler2D", "usampler2D",
    "f32", "i32", "u32"
)

private val SHADER_BUILTINS = setOf(
    "gl_Position", "gl_FragColor", "gl_FragCoord", "gl_PointSize", "gl_PointCoord",
    "gl_FrontFacing", "gl_VertexID", "gl_InstanceID",
    "radians", "degrees", "sin", "cos", "tan", "asin", "acos", "atan",
    "pow", "exp", "log", "exp2", "log2", "sqrt", "inversesqrt",
    "abs", "sign", "floor", "ceil", "fract", "mod", "min", "max", "clamp", "mix", "step", "smoothstep",
    "length", "distance", "dot", "cross", "normalize", "faceforward", "reflect", "refract",
    "matrixCompMult", "transpose", "inverse", "determinant",
    "lessThan", "lessThanEqual", "greaterThan", "greaterThanEqual", "equal", "notEqual", "any", "all", "not",
    "texture2D", "textureCube", "texture", "texelFetch", "textureSize", "dFdx", "dFdy", "fwidth"
)

/** Highlights text without rewriting it or applying JavaScript tokens to markup, styles, and shaders. */
internal class ProjectTextHighlighter(
    private val syntax: ProjectTextSyntax,
    dark: Boolean,
    private val errorLines: Set<Int> = emptySet()
) : VisualTransformation {
    private val keyword = SpanStyle(color = if (dark) Color(0xFFC792EA) else Color(0xFF7B1FA2))
    private val shaderType = SpanStyle(color = if (dark) Color(0xFF82AAFF) else Color(0xFF1565C0))
    private val shaderBuiltin = SpanStyle(color = if (dark) Color(0xFF80CBC4) else Color(0xFF00695C))
    private val preprocessor = SpanStyle(color = if (dark) Color(0xFFFFCB6B) else Color(0xFF9A5B00))
    private val string = SpanStyle(color = if (dark) Color(0xFFC3E88D) else Color(0xFF2E7D32))
    private val number = SpanStyle(color = if (dark) Color(0xFFF78C6C) else Color(0xFFD84315))
    private val comment = SpanStyle(color = if (dark) Color(0xFF78909C) else Color(0xFF607D8B))
    private val error = SpanStyle(background = if (dark) Color(0x334F1118) else Color(0x22B3261E))

    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        val result = AnnotatedString.Builder(text)
        var index = 0
        var inTag = false
        while (index < source.length && syntax != ProjectTextSyntax.PLAIN) {
            val start = index
            val character = source[index]
            val style = when {
                syntax == ProjectTextSyntax.SHADER && source.startsWith("//", index) -> {
                    index = source.indexOf('\n', index + 2).let { if (it < 0) source.length else it }
                    comment
                }
                syntax == ProjectTextSyntax.SHADER && source.startsWith("/*", index) -> {
                    index = source.indexOf("*/", index + 2).let { if (it < 0) source.length else it + 2 }
                    comment
                }
                syntax == ProjectTextSyntax.SHADER && character == '#' -> {
                    index++
                    while (index < source.length && source[index] in " \t") index++
                    while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_')) index++
                    preprocessor
                }
                syntax == ProjectTextSyntax.SHADER && (character.isDigit() ||
                    (character == '.' && source.getOrNull(index + 1)?.isDigit() == true)) -> {
                    index++
                    while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '.')) index++
                    number
                }
                syntax == ProjectTextSyntax.SHADER && (character.isLetter() || character == '_') -> {
                    index++
                    while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_')) index++
                    when (source.substring(start, index)) {
                        in SHADER_KEYWORDS -> keyword
                        in SHADER_TYPES -> shaderType
                        in SHADER_BUILTINS -> shaderBuiltin
                        else -> null
                    }
                }
                syntax == ProjectTextSyntax.SHADER -> { index++; null }
                syntax == ProjectTextSyntax.HTML && source.startsWith("<!--", index) -> {
                    index = source.indexOf("-->", index + 4).let { if (it < 0) source.length else it + 3 }
                    comment
                }
                syntax == ProjectTextSyntax.CSS && source.startsWith("/*", index) -> {
                    index = source.indexOf("*/", index + 2).let { if (it < 0) source.length else it + 2 }
                    comment
                }
                syntax == ProjectTextSyntax.HTML && character == '<' -> {
                    index++
                    if (source.getOrNull(index) in listOf('/', '!', '?')) index++
                    while (index < source.length && (source[index].isLetterOrDigit() || source[index] in "_:-")) index++
                    inTag = true
                    keyword
                }
                syntax == ProjectTextSyntax.HTML && character == '>' -> {
                    index++
                    inTag = false
                    keyword
                }
                (syntax != ProjectTextSyntax.HTML || inTag) && (character == '"' || character == '\'') -> {
                    index++
                    while (index < source.length) {
                        val current = source[index++]
                        if (current == '\\' && syntax != ProjectTextSyntax.HTML) index = (index + 1).coerceAtMost(source.length)
                        else if (current == character) break
                    }
                    string
                }
                syntax == ProjectTextSyntax.CSS && character == '#' -> {
                    index++
                    while (index < source.length && (source[index].isLetterOrDigit() || source[index] in "_-")) index++
                    number
                }
                syntax != ProjectTextSyntax.HTML && (character.isDigit() || character == '-' && source.getOrNull(index + 1)?.isDigit() == true) -> {
                    index++
                    while (index < source.length && (source[index].isLetterOrDigit() || source[index] in ".%+-")) index++
                    number
                }
                character.isLetter() || character in "_@-" -> {
                    index++
                    while (index < source.length && (source[index].isLetterOrDigit() || source[index] in "_-" ||
                        syntax == ProjectTextSyntax.HTML && source[index] == ':')) index++
                    val word = source.substring(start, index)
                    var afterWord = index
                    while (source.getOrNull(afterWord)?.isWhitespace() == true) afterWord++
                    when {
                        syntax == ProjectTextSyntax.HTML && inTag -> keyword
                        syntax == ProjectTextSyntax.JSON && word in setOf("true", "false", "null") -> keyword
                        syntax == ProjectTextSyntax.CSS && (word.startsWith('@') || source.getOrNull(afterWord) == ':') -> keyword
                        else -> null
                    }
                }
                else -> { index++; null }
            }
            if (style != null) result.addStyle(style, start, index)
        }
        var line = 1
        var start = 0
        while (errorLines.isNotEmpty() && start < source.length) {
            val end = source.indexOf('\n', start).let { if (it < 0) source.length else it }
            if (line in errorLines) result.addStyle(error, start, maxOf(start + 1, end).coerceAtMost(source.length))
            start = end + 1
            line++
        }
        return TransformedText(result.toAnnotatedString(), OffsetMapping.Identity)
    }
}
