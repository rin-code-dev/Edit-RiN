// Edit:RiN Live Parameters Test
// Run the preview, then open Preview Actions -> Parameters.
// @rin number speed "Speed" 0 3 1 0.1
// @rin number lineWidth "Line Width" 1 16 4 1
// @rin color ink "Ink Color" #BA90E2

let phase = 0;

function setup() {
  createCanvas(800, 800);
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
