package com.hikariatelier.app

internal enum class CanvasSizingMode { FIXED, RESPONSIVE }

internal enum class WorkTemplateKind(val title: String) {
    BASIC_2D("2D基本"), WEBGL_3D("3D WebGL"), SHADER("シェーダー"), PHYSICS_MATTER("物理演算"), PARAMETERS("パラメータ"), WEBGPU("WebGPU")
}

internal data class WorkTemplate(val ratio: String, val width: Int, val height: Int,
    val kind: WorkTemplateKind = WorkTemplateKind.BASIC_2D) {
    val libraries: Map<String, String>
        get() = if (kind == WorkTemplateKind.PHYSICS_MATTER) mapOf("matter-js" to "0.20.0") else emptyMap()

    fun code(mode: CanvasSizingMode): String {
        val source = when (kind) {
            WorkTemplateKind.BASIC_2D -> CIRCLE_TEMPLATE
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
internal const val CIRCLE_TEMPLATE = "function setup() {\n  createCanvas(windowWidth, windowHeight);\n}\n\nfunction draw() {\n  background(9, 9, 11);\n  noStroke();\n  fill(168, 199, 250);\n  circle(width / 2, height / 2, min(width, height) * 0.3);\n}\n\nfunction windowResized() {\n  resizeCanvas(windowWidth, windowHeight);\n}\n"

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
// Edit:RiN Live Parameters Test
// Run the preview, then open Preview Actions -> Parameters.
// @rin number speed "Speed" 0 3 1 0.1
// @rin number lineWidth "Line Width" 1 16 4 1
// @rin color ink "Ink Color" #BA90E2

let phase = 0;

function setup() {
  createCanvas(windowWidth, windowHeight);
}

function draw() {
  background(12, 15, 24);
  phase += 0.025 * rinParams.speed;

  noFill();
  stroke(rinParams.ink);
  strokeWeight(rinParams.lineWidth);

  for (let band = 0; band < 5; band++) {
    beginShape();
    for (let x = 50; x <= 750; x += 8) {
      const wave = sin(x * 0.015 + phase + band * 0.55) * 55;
      const ripple = sin(x * 0.036 - phase * 0.7) * 15;
      vertex(x, 260 + band * 70 + wave + ripple);
    }
    endShape();
  }

  const orbitX = 400 + cos(phase) * 210;
  const orbitY = 400 + sin(phase * 1.3) * 170;
  fill(rinParams.ink);
  noStroke();
  circle(orbitX, orbitY, 20 + rinParams.lineWidth * 2);
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
