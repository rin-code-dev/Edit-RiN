// Parameters — controls generated from "@rin" comments
// Open Parameters from the work menu and move the controls.
// @rin number speed "Speed" 0.2 4 1 0.1
// @rin number petals "Petals" 3 16 8 1
// @rin color accent "Color" #A8C7FA
// @rin boolean filled "Fill" false

let t = 0;

function setup() {
  createCanvas(600, 600);
  strokeWeight(2);
}

function draw() {
  background(9, 9, 11);
  translate(width / 2, height / 2);
  t += 0.02 * rinParams.speed;

  stroke(rinParams.accent);
  if (rinParams.filled) fill(rinParams.accent);
  else noFill();

  const count = round(rinParams.petals);
  for (let i = 0; i < count; i++) {
    push();
    rotate(TWO_PI * i / count + t);
    ellipse(110, 0, 160, 60);
    pop();
  }
}
