// WebGPU — kinetic sculpture
// Change motion, palette and slab dimensions in Parameters.
// Motion: wave = traveling twist, reverse = opposite direction, sway = move together.
// Palette: original = one accent slab; alternating, gradient and mono are also available.
// The default settings create eighteen slowly twisting slabs.
// @rin choice motion "Motion" wave wave reverse sway
// @rin choice palette "Palette" original original alternating gradient mono
// @rin number speed "Speed" 0 3 1 0.1
// @rin number size "Size" 0.5 2 1 0.1
// @rin number count "Slabs" 8 32 18 1
// @rin number spacing "Spacing" 0.7 1.5 1 0.05
// @rin number thickness "Thickness" 0.5 1.4 1 0.1
// @rin number twist "Twist Amount" 0 1.5 1 0.05
// @rin boolean animate "Animate" true
// @rin color accent "Accent Color" #D67856
// @rin color porcelain "Slab Color" #F4F0E7
// @rin color paper "Background" #E8E6DE

let t = 0;
let sculptureReady = false;

async function setup() {
  let supported = false;
  try {
    supported = typeof WEBGPU !== 'undefined'
      && Boolean(await navigator.gpu?.requestAdapter());
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
  sculptureReady = true;
}

function draw() {
  if (!sculptureReady) return;
  const p = typeof rinParams === 'undefined' ? {} : rinParams;
  if (p.animate ?? true) {
    t += constrain(
      (typeof deltaTime === 'number' ? deltaTime : 16.67) / 1000, 0, 0.05,
    ) * (p.speed ?? 1);
  }
  background(p.paper ?? '#E8E6DE');
  ambientLight(155);
  directionalLight(255, 250, 238, -0.6, 0.8, -1);

  const unit = min(width, height);
  const size = p.size ?? 1;
  const count = round(p.count ?? 18);
  const spacing = p.spacing ?? 1;
  const thickness = p.thickness ?? 1;
  const twist = p.twist ?? 1;
  const motion = p.motion ?? 'wave';
  const palette = p.palette ?? 'original';
  const accent = p.accent ?? '#D67856';
  const porcelain = p.porcelain ?? '#F4F0E7';
  const accentColor = color(accent);
  const porcelainColor = color(porcelain);

  push();
  rotateX(-0.36);
  rotateY(-0.55);
  for (let i = 0; i < count; i++) {
    push();
    translate(0, (i - (count - 1) / 2) * unit * 0.019 * size * spacing, 0);
    const phase = motion === 'reverse' ? -t * 0.35 + i * 0.12
      : motion === 'sway' ? t * 0.35
      : t * 0.35 + i * 0.12;
    rotateY(sin(phase) * 0.65 * twist);

    if (palette === 'alternating') {
      fill(i % 2 === 0 ? accent : porcelain);
    } else if (palette === 'gradient') {
      fill(lerpColor(accentColor, porcelainColor, i / (count - 1)));
    } else if (palette === 'mono') {
      fill(porcelain);
    } else {
      fill(i === 0 ? accent : porcelain);
    }
    box(unit * 0.34 * size, unit * 0.009 * size * thickness, unit * 0.26 * size);
    pop();
  }
  pop();
}
