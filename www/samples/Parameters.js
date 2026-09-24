// Live Parameters Showcase
// Edit:RiN supports live real-time parameter tweaking.
// Open "Parameters" from the work menu to adjust values without reloading!

// @rin number speed "Speed" 0.2 4.0 1.0 0.1
// @rin number petals "Petals" 3 16 8 1
// @rin color theme "Color" #00E5FF
// @rin color bg "Background" #0F111A
// @rin boolean glow "Glow Mode" true
// @rin boolean filled "Fill Shapes" false

let angle = 0;

function setup() {
  createCanvas(600, 600);
}

function draw() {
  // Read live parameters from Edit:RiN runtime with fallbacks
  const p = (typeof rinParams !== 'undefined') ? rinParams : {};
  const speed = Number(p.speed ?? 1.0);
  const petalCount = Math.round(Number(p.petals ?? 8));
  const themeColor = p.theme || '#00E5FF';
  const bgColor = p.bg || '#0F111A';
  const glow = Boolean(p.glow ?? true);
  const fillShape = Boolean(p.filled ?? false);

  background(bgColor);
  translate(width * 0.5, height * 0.5);

  const mainColor = color(themeColor);
  const r = red(mainColor);
  const g = green(mainColor);
  const b = blue(mainColor);

  angle += 0.015 * speed;

  // Glow layer
  if (glow) {
    noFill();
    for (let glowPass = 3; glowPass >= 1; glowPass--) {
      strokeWeight(glowPass * 5);
      stroke(r, g, b, 25 / glowPass);
      drawMandala(petalCount, angle, false);
    }
  }

  // Core mandala
  if (fillShape) {
    fill(r, g, b, 50);
  } else {
    noFill();
  }
  strokeWeight(2);
  stroke(r, g, b, 230);
  drawMandala(petalCount, angle, fillShape);

  // Center core
  noStroke();
  fill(r, g, b, 200);
  circle(0, 0, 12 + sin(angle * 3) * 4);
}

function drawMandala(petals, t, isFilled) {
  const step = TWO_PI / petals;
  for (let i = 0; i < petals; i++) {
    push();
    rotate(i * step + t * 0.3);

    const len = 160 + sin(t * 2 + i) * 35;
    const w = 50 + cos(t * 1.5 + i) * 20;

    beginShape();
    vertex(0, 0);
    bezierVertex(w, len * 0.35, w * 0.8, len * 0.75, 0, len);
    bezierVertex(-w * 0.8, len * 0.75, -w, len * 0.35, 0, 0);
    endShape(CLOSE);

    // Inner orbital rings
    if (!isFilled) {
      const orbitDist = len * 0.6;
      circle(0, orbitDist, w * 0.4);
    }

    pop();
  }
}
