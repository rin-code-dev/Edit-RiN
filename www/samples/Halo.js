const CANVAS_SIZE = 600;
const RING_COUNT = 36;

let time = 0;

function setup() {
  createCanvas(CANVAS_SIZE, CANVAS_SIZE);
  pixelDensity(1);
  colorMode(HSB, 360, 100, 100, 100);
  strokeWeight(1.5);
  noFill();
}

function draw() {
  background(0);
  translate(width * 0.5, height * 0.5);

  for (let i = 0; i < RING_COUNT; i++) {
    rotate(TWO_PI / RING_COUNT);

    const wobble = sin(time + i * 0.4) * 42;

    stroke(
      (i * 10 + time * 30) % 360,
      70,
      100,
      65
    );

    ellipse(wobble, 0, CANVAS_SIZE * 0.55, 55 + wobble);
  }

  time += 0.012;
}
