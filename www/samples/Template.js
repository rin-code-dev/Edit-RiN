function setup() {
  createCanvas(windowWidth, windowHeight);
}

function draw() {
  background(9, 9, 11);
  noStroke();
  fill(168, 199, 250);
  circle(width / 2, height / 2, min(width, height) * 0.3);
}

function windowResized() {
  resizeCanvas(windowWidth, windowHeight);
}
