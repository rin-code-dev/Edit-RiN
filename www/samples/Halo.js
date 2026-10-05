// Halo — rotating ellipses around one center
const SIZE = 600;
const COUNT = 36;

let t = 0;

function setup() {
  createCanvas(SIZE, SIZE);
  noFill();
  stroke(168, 199, 250, 90);
}

function draw() {
  background(9, 9, 11);
  translate(width / 2, height / 2);

  for (let i = 0; i < COUNT; i++) {
    rotate(TWO_PI / COUNT);
    const wobble = sin(t + i * 0.4) * 42;
    ellipse(wobble, 0, SIZE * 0.55, 55 + wobble);
  }

  t += 0.012;
}
