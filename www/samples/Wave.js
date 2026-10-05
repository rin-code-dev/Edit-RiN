// Wave — layered sine lines
// @rin number speed "Speed" 0 3 1 0.1
// @rin number lineWidth "Line Width" 1 16 4 1
// @rin color ink "Ink Color" #A8C7FA

let t = 0;

function setup() {
  createCanvas(600, 600);
  noFill();
}

function draw() {
  background(9, 9, 11);
  t += 0.025 * rinParams.speed;

  stroke(rinParams.ink);
  strokeWeight(rinParams.lineWidth);

  for (let band = 0; band < 5; band++) {
    beginShape();
    for (let x = 50; x <= 550; x += 8) {
      vertex(x, 180 + band * 60 + sin(x * 0.015 + t + band * 0.55) * 40);
    }
    endShape();
  }
}
