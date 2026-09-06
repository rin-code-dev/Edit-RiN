let t = 0;

const CANVAS_SIZE = 600;
const LINE_COUNT = 18;

function setup() {
  createCanvas(CANVAS_SIZE, CANVAS_SIZE);
  pixelDensity(1);
  strokeCap(SQUARE);
  rectMode(CENTER);
}

function draw() {
  background(245);

  translate(width / 2, height / 2);

  // outer frame
  noFill();
  stroke(20);
  strokeWeight(1);
  rect(0, 0, 360, 360);

  // rotating minimal lines
  for (let i = 0; i < LINE_COUNT; i++) {
    push();

    const a = TWO_PI * i / LINE_COUNT + t * 0.15;
    rotate(a);

    const wave = sin(t + i * 0.45) * 32;
    const len = 80 + cos(t * 0.7 + i) * 35;

    stroke(20, 170);
    strokeWeight(i % 3 === 0 ? 2 : 1);

    line(
      70 + wave,
      0,
      70 + wave + len,
      0
    );

    pop();
  }

  // center circle
  noStroke();
  fill(20);
  circle(0, 0, 10);

  // orbiting point
  const r = 105 + sin(t * 1.3) * 18;

  fill(20);
  circle(
    cos(t) * r,
    sin(t) * r,
    6
  );

  // crosshair
  stroke(20, 80);
  strokeWeight(1);

  line(-12, 0, 12, 0);
  line(0, -12, 0, 12);

  t += 0.015;
}
