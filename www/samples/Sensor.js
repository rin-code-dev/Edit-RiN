// Sensor — tilt marble
// Tap to calibrate; shake to reset.
// @rin number sensitivity "Sensitivity" 0.2 2 0.8 0.1
// @rin number bounce "Bounce" 0.3 0.95 0.75 0.05
// @rin color ballColor "Color" #D67856
let pos, vel;
let neutralX = 0,
  neutralY = 0;
let calibrated = false;
const RADIUS = 30;

function setup() {
  createCanvas(600, 600);
  pixelDensity(1);
  angleMode(DEGREES);
  pos = createVector(width / 2, height / 2);
  vel = createVector(0, 0);
  setShakeThreshold(35);
}

function calibrate() {
  neutralX =
    typeof rotationX === 'number' && Number.isFinite(rotationX) ? rotationX : 0;
  neutralY =
    typeof rotationY === 'number' && Number.isFinite(rotationY) ? rotationY : 0;
  calibrated = true;
}

function draw() {
  const parameters = typeof rinParams === 'undefined' ? {} : rinParams;
  const tiltX =
    typeof rotationX === 'number' && Number.isFinite(rotationX) ? rotationX : 0;
  const tiltY =
    typeof rotationY === 'number' && Number.isFinite(rotationY) ? rotationY : 0;
  if (!calibrated && (tiltX !== 0 || tiltY !== 0)) calibrate();
  const step = constrain(
    (typeof deltaTime === 'number' ? deltaTime : 16.67) / 16.67,
    0,
    2,
  );
  const strength = parameters.sensitivity ?? 0.8;
  vel.x += constrain(tiltY - neutralY, -45, 45) * strength * 0.018 * step;
  vel.y += constrain(tiltX - neutralX, -45, 45) * strength * 0.018 * step;
  // Drag also works without sensors.
  if (mouseIsPressed) {
    vel.x += (constrain(mouseX, 0, width) - pos.x) * 0.002 * step;
    vel.y += (constrain(mouseY, 0, height) - pos.y) * 0.002 * step;
  }
  vel.mult(Math.pow(0.96, step));
  pos.x += vel.x * step;
  pos.y += vel.y * step;
  const bounce = parameters.bounce ?? 0.75;
  if (pos.x < RADIUS || pos.x > width - RADIUS) {
    pos.x = constrain(pos.x, RADIUS, width - RADIUS);
    vel.x *= -bounce;
  }
  if (pos.y < 90 || pos.y > height - RADIUS) {
    pos.y = constrain(pos.y, 90, height - RADIUS);
    vel.y *= -bounce;
  }
  background(242, 239, 231);
  const unit = min(width, height);
  noFill();
  strokeWeight(0.8);
  for (let i = 0; i < 5; i++) {
    stroke(208 + i * 3, 205 + i * 3, 193 + i * 3);
    circle(width / 2, height / 2, unit * (0.5 + i * 0.04));
  }
  noStroke();
  // A soft shadow gives a single disc weight.
  for (let i = 10; i > 0; i--) {
    fill(43, 40, 37, 2);
    circle(pos.x + 10, pos.y + 14, RADIUS * 2 + i * 3);
  }
  fill(parameters.ballColor ?? '#D67856');
  circle(pos.x, pos.y, RADIUS * 2);
  fill(255, 246, 221, 55);
  circle(pos.x - 6, pos.y - 7, RADIUS * 1.15);
}

function mousePressed() {
  calibrate();
  return false;
}
function touchStarted() {
  calibrate();
  return false;
}
function touchMoved() {
  return false;
}
function deviceShaken() {
  pos.set(width / 2, height / 2);
  vel.set(0, 0);
}
