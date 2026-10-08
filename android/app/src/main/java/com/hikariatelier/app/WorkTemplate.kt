package com.hikariatelier.app

internal enum class CanvasSizingMode { FIXED, RESPONSIVE }

internal enum class WorkTemplateKind(val title: String, val description: String, val advanced: Boolean) {
    BASIC_2D("2D基本", "円を1つ描く最小構成。色や大きさを変えて始めましょう。", false),
    ANIMATION("アニメーション", "時間に合わせて動く円。速度や動き方を変えてみましょう。", false),
    INPUT("マウス・タッチ", "マウスやタッチの位置に円を描く最小構成。", false),
    PARAMETERS("パラメータ", "円の大きさと色をライブパラメータで変更できます。", false),
    WEBGL_3D("3D WebGL", "回転する立体から3D作品を作り始めます。", true),
    SHADER("シェーダー", "頂点シェーダーとフラグメントシェーダーを使って、色を描画します。", true),
    PHYSICS_MATTER("物理演算", "Matter.jsを使って、落下や跳ね返りを試せます。", true),
    WEBGPU("WebGPU", "WebGPUで3Dを描画します。非対応の環境ではWebGLに切り替えます。", true)
}

internal data class WorkTemplate(val ratio: String, val width: Int, val height: Int,
    val kind: WorkTemplateKind = WorkTemplateKind.BASIC_2D) {
    val libraries: Map<String, String>
        get() = if (kind == WorkTemplateKind.PHYSICS_MATTER) mapOf("matter-js" to "0.20.0") else emptyMap()

    fun code(mode: CanvasSizingMode): String {
        val source = when (kind) {
            WorkTemplateKind.BASIC_2D -> CIRCLE_TEMPLATE
            WorkTemplateKind.ANIMATION -> ANIMATION_TEMPLATE
            WorkTemplateKind.INPUT -> INPUT_TEMPLATE
            WorkTemplateKind.WEBGL_3D -> WEBGL_TEMPLATE
            WorkTemplateKind.SHADER -> SHADER_TEMPLATE
            WorkTemplateKind.PHYSICS_MATTER -> MATTER_TEMPLATE
            WorkTemplateKind.PARAMETERS -> WAVE_PARAMETERS_TEMPLATE
            WorkTemplateKind.WEBGPU -> WEBGPU_TEMPLATE
        }
        if (mode == CanvasSizingMode.RESPONSIVE) return source
        return source.substringBefore("\nfunction windowResized()")
            .trimEnd().replace("createCanvas(windowWidth, windowHeight", "createCanvas($width, $height") + "\n"
    }
}

// Supplied Template.js; only canvas sizing is adapted for fixed-size new works.
internal const val CIRCLE_TEMPLATE = "// 色や円の大きさを変えてみましょう。 / Try changing the color or circle size.\nfunction setup() {\n  createCanvas(windowWidth, windowHeight);\n}\n\nfunction draw() {\n  background(9, 9, 11);\n  noStroke();\n  fill(168, 199, 250);\n  circle(width / 2, height / 2, min(width, height) * 0.3);\n}\n\nfunction windowResized() {\n  resizeCanvas(windowWidth, windowHeight);\n}\n"

internal val workTemplates = listOf(
    WorkTemplate("16:9", 960, 540),
    WorkTemplate("4:3", 800, 600),
    WorkTemplate("1:1", 800, 800),
    WorkTemplate("9:16", 540, 960)
)

internal val WEBGL_TEMPLATE = """
function setup() {
  createCanvas(windowWidth, windowHeight, WEBGL);
}

function draw() {
  background(9, 9, 11);
  noStroke();
  ambientLight(100);
  directionalLight(255, 255, 255, 0.5, 1, -1);
  rotateX(frameCount * 0.01);
  rotateY(frameCount * 0.015);
  normalMaterial();
  torus(min(width, height) * 0.22, min(width, height) * 0.07);
}

function windowResized() {
  resizeCanvas(windowWidth, windowHeight);
}
""".trimIndent() + "\n"

internal val SHADER_TEMPLATE = """
let gradientShader;
const vertexSource = `
  precision highp float;
  attribute vec3 aPosition;
  uniform mat4 uModelViewMatrix;
  uniform mat4 uProjectionMatrix;
  void main() {
    gl_Position = uProjectionMatrix * uModelViewMatrix * vec4(aPosition, 1.0);
  }
`;
const fragmentSource = `
  precision highp float;
  uniform vec2 uResolution;
  uniform float uTime;
  void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    vec3 color = 0.5 + 0.5 * cos(uTime + uv.xyx + vec3(0.0, 2.0, 4.0));
    gl_FragColor = vec4(color, 1.0);
  }
`;

function setup() {
  createCanvas(windowWidth, windowHeight, WEBGL);
  gradientShader = createShader(vertexSource, fragmentSource);
  noStroke();
}

function draw() {
  shader(gradientShader);
  gradientShader.setUniform('uResolution', [width * pixelDensity(), height * pixelDensity()]);
  gradientShader.setUniform('uTime', millis() / 1000);
  rect(-width / 2, -height / 2, width, height);
}

function windowResized() {
  resizeCanvas(windowWidth, windowHeight);
}
""".trimIndent() + "\n"

internal val MATTER_TEMPLATE = """
let engine, ball, ground;

function rebuildGround() {
  if (ground) Matter.Composite.remove(engine.world, ground);
  ground = Matter.Bodies.rectangle(width / 2, height + 20, width * 3, 60, { isStatic: true });
  Matter.Composite.add(engine.world, ground);
}

function setup() {
  createCanvas(windowWidth, windowHeight);
  engine = Matter.Engine.create();
  ball = Matter.Bodies.circle(width / 2, 40, 24, { restitution: 0.85 });
  Matter.Composite.add(engine.world, ball);
  rebuildGround();
}

function draw() {
  Matter.Engine.update(engine, min(deltaTime, 1000 / 60));
  background(9, 9, 11);
  noStroke();
  fill(168, 199, 250);
  circle(ball.position.x, ball.position.y, 48);
  fill(100);
  rect(0, height - 10, width, 10);
}

function windowResized() {
  resizeCanvas(windowWidth, windowHeight);
  rebuildGround();
}
""".trimIndent() + "\n"

internal val WAVE_PARAMETERS_TEMPLATE = """
// プレビューのパラメータボタンで円の大きさと色を変更します。
// Open Parameters in the preview to change the circle's size and color.
// @rin number size "Size" 0.1 0.8 0.3 0.05
// @rin color ink "Color" #A8C7FA

function setup() {
  createCanvas(windowWidth, windowHeight);
}

function draw() {
  background(9, 9, 11);
  noStroke();
  fill(rinParams.ink);
  circle(width / 2, height / 2, min(width, height) * rinParams.size);
}

function windowResized() {
  resizeCanvas(windowWidth, windowHeight);
}
""".trimIndent() + "\n"

internal val WEBGPU_TEMPLATE = """
// WebGPU — a lit torus and cube (falls back to WebGL)
let t = 0;

async function setup() {
  await createCanvas(windowWidth, windowHeight, navigator.gpu ? WEBGPU : WEBGL);
  noStroke();
}

function draw() {
  background(9, 9, 11);
  t += 0.015;

  ambientLight(60);
  directionalLight(255, 255, 255, 0.5, 1, -0.8);
  rotateX(t * 0.8);
  rotateY(t);

  const s = min(width, height) * 0.2;
  fill(168, 199, 250);
  torus(s, s * 0.12, 36, 24);
  box(s * 0.7);
}

function windowResized() {
  resizeCanvas(windowWidth, windowHeight);
}
""".trimIndent() + "\n"

internal val ANIMATION_TEMPLATE = """
// speedを変えると動く速さが変わります。 / Change speed to adjust the motion.
const speed = 1;

function setup() {
  createCanvas(windowWidth, windowHeight);
}

function draw() {
  background(9, 9, 11);
  noStroke();
  fill(168, 199, 250);
  const x = width / 2 + sin(millis() / 1000 * speed) * width * 0.3;
  circle(x, height / 2, min(width, height) * 0.15);
}

function windowResized() {
  resizeCanvas(windowWidth, windowHeight);
}
""".trimIndent() + "\n"

internal val INPUT_TEMPLATE = """
// マウスを動かすか画面をタッチします。 / Move the mouse or touch the canvas.
function setup() {
  createCanvas(windowWidth, windowHeight);
}

function draw() {
  background(9, 9, 11);
  noStroke();
  fill(168, 199, 250);
  circle(mouseX, mouseY, min(width, height) * 0.15);
}

function touchStarted() {
  return false;
}

function touchMoved() {
  return false;
}

function windowResized() {
  resizeCanvas(windowWidth, windowHeight);
}
""".trimIndent() + "\n"
