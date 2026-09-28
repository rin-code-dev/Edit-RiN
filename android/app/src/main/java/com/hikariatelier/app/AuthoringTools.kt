package com.hikariatelier.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal const val PARAMETER_SAMPLE = "// @rin number speed \"Speed\" 0 3 1 0.1\n// @rin number lineWidth \"Line Width\" 1 16 4 1\n// @rin color ink \"Ink Color\" #BA90E2\n"

internal fun prependDeclarations(value: TextFieldValue, declarations: String): TextFieldValue {
    val prefix = declarations.trimEnd() + "\n"
    return TextFieldValue(prefix + value.text,
        TextRange(value.selection.start + prefix.length, value.selection.end + prefix.length))
}

internal enum class ParameterKind(val title: String, val token: String) {
    NUMBER("数値", "number"), COLOR("色", "color"), BOOLEAN("真偽値", "boolean")
}

/** Reuse the runtime parser so the dialog can never emit a declaration the panel ignores. */
internal fun parameterDeclaration(kind: ParameterKind, name: String, label: String, initial: String,
    min: String, max: String, step: String, existingNames: Set<String>, count: Int): String? {
    if (count >= 16 || !Regex("[A-Za-z][A-Za-z0-9_]*").matches(name) || name in existingNames ||
        name in setOf("__proto__", "prototype", "constructor") ||
        label.isBlank() || label.length > 40 || label.any { it == '"' || it == '\n' || it == '\r' }) return null
    if (listOf(initial, min, max, step).any { field -> field.any { it.isWhitespace() } }) return null
    val tail = if (kind == ParameterKind.NUMBER) "$min $max $initial $step" else initial
    val line = "// @rin ${kind.token} $name \"$label\" $tail\n"
    return line.takeIf { workParameters(mapOf("sketch.js" to line)).size == 1 }
}

internal data class CodeSnippet(val category: String, val title: String, val placement: String, val code: String)
internal val codeSnippets = listOf(
    CodeSnippet("アニメーション・数学", "sin/cos周期運動", "draw() 内に挿入", """
        const orbitAngle = frameCount * 0.02;
        const orbitX = width / 2 + cos(orbitAngle) * width * 0.25;
        const orbitY = height / 2 + sin(orbitAngle) * height * 0.25;
        circle(orbitX, orbitY, 24);
    """.trimIndent()),
    CodeSnippet("アニメーション・数学", "lerp補間", "draw() 内に挿入", """
        const easedX = lerp(width / 2, mouseX, 0.1);
        const easedY = lerp(height / 2, mouseY, 0.1);
        circle(easedX, easedY, 24);
    """.trimIndent()),
    CodeSnippet("アニメーション・数学", "map値マッピング", "draw() 内に挿入", """
        const mappedSize = map(mouseX, 0, width, 10, 100, true);
        circle(width / 2, height / 2, mappedSize);
    """.trimIndent()),
    CodeSnippet("カラー", "HSBカラーモード設定", "setup() 内に挿入", "colorMode(HSB, 360, 100, 100, 1);"),
    CodeSnippet("カラー", "グラデーション背景", "draw() 内に挿入", """
        push();
        colorMode(RGB, 255);
        strokeWeight(1);
        const gradientStart = color(20, 30, 80);
        const gradientEnd = color(220, 100, 180);
        for (let y = 0; y < height; y++) {
          stroke(lerpColor(gradientStart, gradientEnd, y / max(1, height - 1)));
          line(0, y, width, y);
        }
        pop();
    """.trimIndent()),
    CodeSnippet("構造・クラス", "パーティクルクラス", "関数の外に挿入", """
        class Particle {
          constructor(x, y) {
            this.position = createVector(x, y);
            this.velocity = p5.Vector.random2D().mult(2);
            this.life = 255;
          }
          update() {
            this.position.add(this.velocity);
            this.life -= 2;
          }
          display() {
            push();
            colorMode(RGB, 255);
            noStroke();
            fill(168, 199, 250, max(0, this.life));
            circle(this.position.x, this.position.y, 8);
            pop();
          }
        }
        const particles = [];
    """.trimIndent()),
    CodeSnippet("構造・クラス", "配列ループ更新", "draw() 内に挿入（Particleが必要）", """
        if (particles.length < 200) particles.push(new Particle(width / 2, height / 2));
        for (let i = particles.length - 1; i >= 0; i--) {
          particles[i].update();
          particles[i].display();
          if (particles[i].life <= 0) particles.splice(i, 1);
        }
    """.trimIndent()),
    CodeSnippet("インタラクション", "マウス・タッチ追従", "draw() 内に挿入", """
        const pointerX = touches.length ? touches[0].x : mouseX;
        const pointerY = touches.length ? touches[0].y : mouseY;
        circle(pointerX, pointerY, 32);
    """.trimIndent()),
    CodeSnippet("インタラクション", "タッチ押し込み判定", "draw() 内に挿入", """
        if (mouseIsPressed || touches.length > 0) {
          fill(255, 100, 180);
          circle(width / 2, height / 2, 80);
        }
    """.trimIndent())
)

internal enum class DiffLineKind { ADDED, REMOVED, HEADER, CONTEXT }
internal fun diffLineKind(line: String): DiffLineKind = when {
    line.startsWith("@@") || line.startsWith("+++") || line.startsWith("---") -> DiffLineKind.HEADER
    line.startsWith("+") -> DiffLineKind.ADDED
    line.startsWith("−") || line.startsWith("-") -> DiffLineKind.REMOVED
    line == "sketch.js" || line.startsWith("rinParams.") || Regex("[^\\s]+\\.(js|frag|vert|glsl)").matches(line) -> DiffLineKind.HEADER
    else -> DiffLineKind.CONTEXT
}
