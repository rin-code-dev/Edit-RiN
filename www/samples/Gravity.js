// Gravity — a point grid bending around a sinkhole
const SIZE = 600;
const N = 64;
const RANGE = 205;

let t = 0;

function setup() {
  createCanvas(SIZE, SIZE, WEBGL);
  stroke(168, 199, 250, 190);
  strokeWeight(1.6);
}

function draw() {
  background(9, 9, 11);
  rotateX(0.92);
  rotateZ(-0.22);

  beginShape(POINTS);
  for (let j = 0; j < N; j++) {
    for (let i = 0; i < N; i++) {
      const x = map(i, 0, N - 1, -RANGE, RANGE);
      const y = map(j, 0, N - 1, -RANGE, RANGE);
      const r = sqrt(x * x + y * y);
      const pull = exp(-r / 105);
      const wave = sin(r * 0.055 - t * 4);
      const angle = atan2(y, x) + pull * (1.15 + wave * 0.18);
      const radius = r + wave * 16 * pull;
      const depth = wave * 62 * pull - exp(-sq(r) / 2300) * 72;
      vertex(cos(angle) * radius, sin(angle) * radius, depth);
    }
  }
  endShape();

  t += 0.012;
}
