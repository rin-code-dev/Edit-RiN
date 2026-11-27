package com.hikariatelier.app

internal enum class CanvasSizingMode { FIXED, RESPONSIVE }

internal enum class WorkTemplateCategory(val label: String, val description: String) {
    BASIC("基本", "最小構成・動き・入力・パラメータ"),
    BLOCKS("ブロックの例", "ブロックで組み立てやすい完成例"),
    ADVANCED("応用", "3D・シェーダー・物理・ブラシ・WebGPU"),
    USER("自作", "保存済みのマイテンプレートから作成")
}

internal enum class WorkTemplateKind(
    val title: String,
    val description: String,
    val category: WorkTemplateCategory,
    val badge: String,
    val blockFriendly: Boolean = true,
    val recommendedBlockMode: Boolean = false,
    val requiresSound: Boolean = false
) {
    BASIC_2D("2D基本", "円を1つ描く最小構成。色や大きさを変えて始めましょう。", WorkTemplateCategory.BASIC, "2D"),
    ANIMATION("アニメーション", "時間に合わせて動く円。速度や動き方を変えてみましょう。", WorkTemplateCategory.BASIC, "MOTION"),
    INPUT("マウス・タッチ", "マウスやタッチの位置に円を描く最小構成。", WorkTemplateCategory.BASIC, "INPUT"),
    PARAMETERS("パラメータ", "円の大きさと色をライブパラメータで変更できます。", WorkTemplateCategory.BASIC, "PARAM"),
    PATTERN("反復模様", "図形を繰り返して模様を描きます。", WorkTemplateCategory.BASIC, "LOOP"),
    PARTICLES("パーティクル", "小さな粒が流れるアニメーションです。", WorkTemplateCategory.BASIC, "MOTION"),
    BLOCK_FOLLOW("指についてくる円", "マウスや指の位置に円が付いてくる、ブロック入門に最適な最小例。", WorkTemplateCategory.BLOCKS, "2D", recommendedBlockMode = true),
    BLOCK_PAINT("指でお絵描き", "押している間だけキャンバスに描ける、条件分岐（if）の基本例。", WorkTemplateCategory.BLOCKS, "IF", recommendedBlockMode = true),
    BLOCK_PATTERN("回る模様", "原点の移動・回転と繰り返しループで模様を描きます。", WorkTemplateCategory.BLOCKS, "LOOP", recommendedBlockMode = true),
    BLOCK_SOUND("タップして音を鳴らす", "画面を押すと横位置に応じた高さの音が鳴ります（p5.sound 自動有効）。", WorkTemplateCategory.BLOCKS, "SOUND", recommendedBlockMode = true, requiresSound = true),
    BLOCK_THREE("回る3D立方体", "指で視点を回せる3Dキャンバスに回転する立方体を描きます。", WorkTemplateCategory.BLOCKS, "3D", recommendedBlockMode = true),
    BLOCK_PARAMETERS("スライダーとXYパッド", "2D XYパッド・スライダー・カラーで円を操作するブロック例。", WorkTemplateCategory.BLOCKS, "XY", recommendedBlockMode = true),
    WEBGL_3D("3D WebGL", "回転する立体から3D作品を作り始めます。", WorkTemplateCategory.ADVANCED, "WEBGL"),
    SHADER("シェーダー", "頂点シェーダーとフラグメントシェーダーを使って、色を描画します。", WorkTemplateCategory.ADVANCED, "GLSL", blockFriendly = false),
    PHYSICS_MATTER("物理演算", "Matter.jsを使って、落下や跳ね返りを試せます。", WorkTemplateCategory.ADVANCED, "MATTER", blockFriendly = false),
    BRUSH("水彩・ブラシ", "p5.brushを使って、水彩のにじみや鉛筆の質感を試せます。", WorkTemplateCategory.ADVANCED, "BRUSH"),
    WEBGPU("WebGPU", "WebGPUで3Dを描画します。非対応の環境ではWebGLに切り替えます。", WorkTemplateCategory.ADVANCED, "WEBGPU", blockFriendly = false);

    val advanced: Boolean get() = category == WorkTemplateCategory.ADVANCED
}

internal data class WorkTemplate(val ratio: String, val width: Int, val height: Int,
    val kind: WorkTemplateKind = WorkTemplateKind.BASIC_2D) {
    val libraries: Map<String, String>
        get() = when (kind) {
            WorkTemplateKind.PHYSICS_MATTER -> mapOf("matter-js" to "0.20.0")
            WorkTemplateKind.BRUSH -> mapOf("p5.brush" to "2.2.1")
            else -> emptyMap()
        }

    val requiresSound: Boolean
        get() = kind.requiresSound

    fun canvasCallPreview(mode: CanvasSizingMode): String {
        val dims = if (mode == CanvasSizingMode.FIXED) "$width, $height" else "windowWidth, windowHeight"
        return when (kind) {
            WorkTemplateKind.WEBGL_3D, WorkTemplateKind.SHADER, WorkTemplateKind.BLOCK_THREE, WorkTemplateKind.BRUSH ->
                "createCanvas($dims, WEBGL);"
            WorkTemplateKind.WEBGPU ->
                "await createCanvas($dims, renderer);"
            else -> "createCanvas($dims);"
        }
    }

    fun code(mode: CanvasSizingMode): String {
        val source = when (kind) {
            WorkTemplateKind.BASIC_2D -> CIRCLE_TEMPLATE
            WorkTemplateKind.ANIMATION -> ANIMATION_TEMPLATE
            WorkTemplateKind.INPUT -> INPUT_TEMPLATE
            WorkTemplateKind.PARAMETERS -> WAVE_PARAMETERS_TEMPLATE
            WorkTemplateKind.PATTERN -> PATTERN_TEMPLATE
            WorkTemplateKind.PARTICLES -> PARTICLES_TEMPLATE
            WorkTemplateKind.BLOCK_FOLLOW -> BLOCK_FOLLOW_TEMPLATE
            WorkTemplateKind.BLOCK_PAINT -> BLOCK_PAINT_TEMPLATE
            WorkTemplateKind.BLOCK_PATTERN -> BLOCK_PATTERN_TEMPLATE
            WorkTemplateKind.BLOCK_SOUND -> BLOCK_SOUND_TEMPLATE
            WorkTemplateKind.BLOCK_THREE -> BLOCK_THREE_TEMPLATE
            WorkTemplateKind.BLOCK_PARAMETERS -> BLOCK_PARAMETERS_TEMPLATE
            WorkTemplateKind.WEBGL_3D -> WEBGL_TEMPLATE
            WorkTemplateKind.SHADER -> SHADER_TEMPLATE
            WorkTemplateKind.PHYSICS_MATTER -> MATTER_TEMPLATE
            WorkTemplateKind.BRUSH -> BRUSH_TEMPLATE
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
    WorkTemplate("3:2", 900, 600),
    WorkTemplate("4:3", 800, 600),
    WorkTemplate("1:1", 800, 800),
    WorkTemplate("4:5", 640, 800),
    WorkTemplate("3:4", 600, 800),
    WorkTemplate("9:16", 540, 960)
)

internal val PATTERN_TEMPLATE = """
function setup() {
  createCanvas(windowWidth, windowHeight);
  noStroke();
}

function draw() {
  background(9, 9, 11);
  const spacing = min(width, height) / 8;
  for (let y = spacing / 2; y < height; y += spacing) {
    for (let x = spacing / 2; x < width; x += spacing) {
      fill(168, 199, 250, 180);
      circle(x, y, spacing * (0.3 + 0.2 * sin(millis() / 1000 + x / spacing)));
    }
  }
}

function windowResized() {
  resizeCanvas(windowWidth, windowHeight);
}
""".trimIndent() + "\n"

internal val PARTICLES_TEMPLATE = """
function setup() {
  createCanvas(windowWidth, windowHeight);
  noStroke();
}

function draw() {
  background(9, 9, 11);
  fill(168, 199, 250, 200);
  for (let i = 0; i < 48; i++) {
    const x = (i * 73 + millis() * (0.015 + (i % 4) * 0.01)) % width;
    const y = ((i * 137) % height + sin(millis() / 1000 + i) * 20 + height) % height;
    circle(x, y, 3 + i % 5);
  }
}

function windowResized() {
  resizeCanvas(windowWidth, windowHeight);
}
""".trimIndent() + "\n"

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
  let renderer = WEBGL;
  if (navigator.gpu) {
    try {
      if (await navigator.gpu.requestAdapter()) renderer = WEBGPU;
    } catch (_) {
      // Keep WebGL when a GPU adapter is unavailable.
    }
  }
  await createCanvas(windowWidth, windowHeight, renderer);
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

internal val BLOCK_FOLLOW_TEMPLATE = """
function setup() {
  createCanvas(windowWidth, windowHeight);
}

function draw() {
  background(10, 14, 26, 36);
  blendMode(ADD);
  noStroke();
  fill("#6c5ce7");
  circle(mouseX, mouseY, 80 + sin(frameCount * 0.06) * 24);
  blendMode(BLEND);
}

function windowResized() {
  resizeCanvas(windowWidth, windowHeight);
}
""".trimIndent() + "\n"

internal val BLOCK_PAINT_TEMPLATE = """
function setup() {
  createCanvas(windowWidth, windowHeight);
  background(12, 14, 24);
  noStroke();
  fill("#a8c7fa");
  circle(width / 2, height / 2, 24);
}

function draw() {
  if (mouseIsPressed) {
    noStroke();
    fill("hsl(" + floor((frameCount * 3 + 0) % 360) + ", 85%, 65%)");
    circle(mouseX, mouseY, 28);
  }
}

function windowResized() {
  resizeCanvas(windowWidth, windowHeight);
  background(12, 14, 24);
}
""".trimIndent() + "\n"

internal val BLOCK_PATTERN_TEMPLATE = """
function setup() {
  createCanvas(windowWidth, windowHeight);
}

function draw() {
  background(10, 14, 26, 48);
  noFill();
  stroke("hsl(" + floor((frameCount * 2 + 0) % 360) + ", 85%, 65%)");
  strokeWeight(2);
  push();
  translate(width / 2, height / 2);
  for (let i = 0; i < 12; i++) {
    rotate(TWO_PI / 12);
    circle(min(width, height) * 0.18, 0, min(width, height) * 0.24);
  }
  pop();
}

function windowResized() {
  resizeCanvas(windowWidth, windowHeight);
}
""".trimIndent() + "\n"

internal val BLOCK_SOUND_TEMPLATE = """
let tone;
let toneStarted = false;

function setup() {
  createCanvas(windowWidth, windowHeight);
  tone = new p5.Oscillator("sine");
  tone.amp(0);
}

function draw() {
  background("#17233d");
  fill("#a8c7fa");
  noStroke();
  circle(mouseX, mouseY, mouseIsPressed ? 100 : 50);
}

function mousePressed() {
  userStartAudio();
  if (!toneStarted) {
    tone.start();
    toneStarted = true;
  }
  tone.freq(map(mouseX, 0, width, 220, 880));
  tone.amp(0.1, 0.05);
}

function mouseReleased() {
  tone.amp(0, 0.1);
}

function windowResized() {
  resizeCanvas(windowWidth, windowHeight);
}
""".trimIndent() + "\n"

internal val BLOCK_THREE_TEMPLATE = """
function setup() {
  createCanvas(windowWidth, windowHeight, WEBGL);
}

function draw() {
  background(24);
  orbitControl();
  normalMaterial();
  rotateX(frameCount * 0.01);
  rotateY(frameCount * 0.02);
  box(min(width, height) * 0.3);
}

function windowResized() {
  resizeCanvas(windowWidth, windowHeight);
}
""".trimIndent() + "\n"

internal val BLOCK_PARAMETERS_TEMPLATE = """
let pos = { x: 0.5, y: 0.5 }; // @xy(0, 1) "Position"
let size = 90; // @slider(20, 200, 2) "Size"
let ink = "#BA90E2"; // @color "Ink"

function setup() {
  createCanvas(windowWidth, windowHeight);
}

function draw() {
  background(22);
  noStroke();
  fill(ink);
  circle(pos.x * width, pos.y * height, size);
}

function windowResized() {
  resizeCanvas(windowWidth, windowHeight);
}
""".trimIndent() + "\n"

internal val BRUSH_TEMPLATE = """
function drawArtwork() {
  background("#f2efe7");
  brush.pick("HB");
  brush.stroke("#2b2825");
  brush.strokeWeight(1.5);
  brush.fill("#d67856", 90);
  brush.fillBleed(0.25, "out");
  brush.circle(0, 0, min(width, height) * 0.24);
  brush.noFill();
  brush.line(-width * 0.25, height * 0.28, width * 0.25, height * 0.28);
}

function setup() {
  createCanvas(windowWidth, windowHeight, WEBGL);
  brush.load();
  drawArtwork();
}

function draw() {
}

function windowResized() {
  resizeCanvas(windowWidth, windowHeight);
  drawArtwork();
}
""".trimIndent() + "\n"
