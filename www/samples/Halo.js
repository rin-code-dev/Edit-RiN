// Halo — quiet orbital light
// Repeating ellipses create one slowly breathing halo.
// @rin number speed "Speed" 0 2 0.6 0.1
const RING_COUNT = 36;
let time = 0;

function setup() {
  createCanvas(600, 600);
  pixelDensity(1);
}

function draw() {
  const parameters = typeof rinParams === 'undefined' ? {} : rinParams;
  const seconds = constrain(deltaTime / 1000, 0, 0.05);
  time += seconds * (parameters.speed ?? 0.6);

  background(28, 27, 26);
  translate(width / 2, height / 2);
  rotate(time * 0.08);
  noFill();

  const unit = min(width, height);
  // Each ring follows the same rule with a small phase offset.
  for (let ring = 0; ring < RING_COUNT; ring++) {
    push();
    rotate((ring * TWO_PI) / RING_COUNT);
    const breath = sin(time * 0.7 + (ring * TWO_PI) / RING_COUNT);
    const isAccent = ring % 12 === 0;
    stroke(isAccent ? '#D67856' : '#F2EFE7');
    strokeWeight(isAccent ? 1.25 : 0.65);
    ellipse(unit * 0.018 * breath, 0, unit * 0.61, unit * (0.19 + breath * 0.025));
    pop();
  }
}
