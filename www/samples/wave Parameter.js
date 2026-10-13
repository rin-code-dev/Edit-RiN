// Weave — flowing threads
// Choose a weave and palette in Parameters.
// Weave: silk = flowing threads, braid = interlaced curves, ripple = waves.
// Palette: gradient, alternating colors, or mono.
// Turn Cross Weave on to overlay a second set of threads.
// @rin choice weave "Weave" silk silk braid ripple
// @rin choice palette "Palette" gradient gradient alternating mono
// @rin number speed "Speed" 0 3 0.7 0.1
// @rin number threads "Threads" 16 80 48 1
// @rin number amplitude "Amplitude" 0 1.5 0.8 0.05
// @rin number twist "Twist" 0 4 1.5 0.1
// @rin number thickness "Line Width" 0.5 3 1 0.5
// @rin number angle "Angle" -90 90 -20 1
// @rin boolean cross "Cross Weave" true
// @rin boolean animate "Animate" true
// @rin color ink "Thread Color 1" #167D9A
// @rin color accent "Thread Color 2" #E19879
// @rin color paper "Background" #F4EFE6

let weaveTime = 0;

function setup() {
  createCanvas(720, 720);
  pixelDensity(1);
}

function draw() {
  const p = typeof rinParams === 'undefined' ? {} : rinParams;
  if (p.animate ?? true) {
    weaveTime += constrain(deltaTime / 1000, 0, 0.05) * (p.speed ?? 0.7);
  }
  background(p.paper ?? '#F4EFE6');
  const count = round(p.threads ?? 48);
  const first = color(p.ink ?? '#167D9A');
  const second = color(p.accent ?? '#E19879');
  const mode = p.weave ?? 'silk';
  const palette = p.palette ?? 'gradient';
  const amplitude = p.amplitude ?? 0.8;
  const twist = p.twist ?? 1.5;
  const unit = min(width, height);

  push();
  translate(width / 2, height / 2);
  rotate(radians(p.angle ?? -20));
  noFill();
  strokeWeight(p.thickness ?? 1);

  const layers = (p.cross ?? true) ? 2 : 1;
  for (let layer = 0; layer < layers; layer++) {
    push();
    if (layer === 1) rotate(HALF_PI);
    for (let i = 0; i < count; i++) {
      const v = i / (count - 1);
      const lane = v * 2 - 1;
      const mix = palette === 'mono' ? 0
        : palette === 'alternating' ? (i + layer) % 2
        : layer === 0 ? v : 1 - v;
      const ink = lerpColor(first, second, mix);
      ink.setAlpha(layer === 0 ? 210 : 155);
      stroke(ink);
      beginShape();
      for (let j = 0; j <= 100; j++) {
        const u = j / 100;
        const x = (u - 0.5) * unit * 0.78;
        const envelope = pow(sin(PI * u), 1.3);
        const phase = u * TWO_PI * twist - weaveTime + layer * PI;
        let bend;
        if (mode === 'braid') {
          bend = sin(phase + lane * PI) * 0.115
            + cos(phase * 0.5 - lane * 2) * 0.025;
        } else if (mode === 'ripple') {
          bend = sin(phase + abs(lane) * 5) * 0.065
            + sin(u * TWO_PI * 3 + weaveTime * 0.7) * 0.025;
        } else {
          bend = sin(phase + lane * 1.8) * 0.085
            + cos(u * PI - weaveTime * 0.4) * lane * 0.04;
        }
        const y = unit * (lane * 0.19 * (0.3 + 0.7 * envelope)
          + bend * envelope * amplitude);
        vertex(x, y);
      }
      endShape();
    }
    pop();
  }
  pop();
}
