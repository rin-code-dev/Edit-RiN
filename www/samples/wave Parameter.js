// Weave — flowing threads
// Change speed, thickness and color in Parameters.
// @rin number speed "Speed" 0 3 1 0.1
// @rin number lineWidth "Line Width" 0.5 4 1 0.5
// @rin color ink "Ink Color" #2B2825
let phase = 0;

function setup() {
  createCanvas(600, 600);
  pixelDensity(1);
}

function draw() {
  const parameters = typeof rinParams === 'undefined' ? {} : rinParams;
  const seconds = constrain(
    (typeof deltaTime === 'number' ? deltaTime : 16.67) / 1000,
    0,
    0.05,
  );
  phase += seconds * (parameters.speed ?? 1);
  background(242, 239, 231);
  noFill();
  stroke(parameters.ink ?? '#2B2825');
  strokeWeight(parameters.lineWidth ?? 1);
  // Parallel threads bend into one floating lens.
  const unit = min(width, height);
  for (let thread = 0; thread < 42; thread++) {
    const threadPosition = thread / 41;
    beginShape();
    for (let segment = 0; segment <= 64; segment++) {
      const progress = segment / 64,
        envelope = sin(progress * PI);
      const bulge = sin(threadPosition * PI) * envelope * envelope;
      const bend =
        sin(progress * TWO_PI - phase * 0.45 + threadPosition * 2.2) * bulge * 0.09;
      vertex(
        width / 2 + (progress - 0.5) * unit * 0.73,
        height / 2 + (threadPosition - 0.5) * unit * 0.46 * envelope + bend * unit,
      );
    }
    endShape();
  }
}
