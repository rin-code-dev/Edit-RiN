// WebGPU — a lit torus and cube (falls back to WebGL)
// @rin number speed "Speed" 0.2 3 1 0.1
// @rin number size "Size" 0.5 2 1 0.1
// @rin color accent "Color" #A8C7FA

let t = 0;

async function setup() {
  await createCanvas(600, 600, navigator.gpu ? WEBGPU : WEBGL);
  noStroke();
}

function draw() {
  background(9, 9, 11);
  t += 0.015 * rinParams.speed;

  ambientLight(60);
  directionalLight(255, 255, 255, 0.5, 1, -0.8);
  rotateX(t * 0.8);
  rotateY(t);

  const s = 120 * rinParams.size;
  fill(rinParams.accent);
  torus(s, s * 0.12, 36, 24);
  box(s * 0.7);
}
