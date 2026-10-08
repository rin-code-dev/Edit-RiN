// WebGPU — kinetic sculpture
// @rin number speed "Speed" 0.2 3 1 0.1
// @rin number size "Size" 0.5 2 1 0.1
// @rin color accent "Color" #D67856
let t = 0;

async function setup() {
  let supported = false;
  try {
    supported =
      typeof WEBGPU !== 'undefined' && Boolean(await navigator.gpu?.requestAdapter());
  } catch (_) {
    // WebGL remains available.
  }
  try {
    await createCanvas(600, 600, supported ? WEBGPU : WEBGL);
  } catch (error) {
    if (!supported) throw error;
    await createCanvas(600, 600, WEBGL);
  }
  pixelDensity(1);
  noStroke();
}

function draw() {
  const parameters = typeof rinParams === 'undefined' ? {} : rinParams;
  t +=
    constrain((typeof deltaTime === 'number' ? deltaTime : 16.67) / 1000, 0, 0.05) *
    (parameters.speed ?? 1);
  background(232, 230, 222);
  ambientLight(155);
  directionalLight(255, 250, 238, -0.6, 0.8, -1);
  rotateX(-0.36);
  rotateY(-0.55);
  const unit = min(width, height),
    scale = parameters.size ?? 1;
  // Eighteen thin slabs slowly twist around one axis.
  for (let i = 0; i < 18; i++) {
    push();
    translate(0, (i - 8.5) * unit * 0.019 * scale, 0);
    rotateY(sin(t * 0.35 + i * 0.12) * 0.65);
    fill(i === 0 ? (parameters.accent ?? '#D67856') : '#F4F0E7');
    box(unit * 0.34 * scale, unit * 0.009 * scale, unit * 0.26 * scale);
    pop();
  }
}
