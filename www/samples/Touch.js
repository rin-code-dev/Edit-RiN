// Ripples — touch and time
// Tap or drag to paint.
const LIMIT = 40;
let ripples = [];
let lastRippleAt = -Infinity;

function setup() {
  createCanvas(600, 600);
  pixelDensity(1);
  addRipple(width / 2, height / 2);
}

function addRipple(x, y) {
  if (ripples.length >= LIMIT) ripples.shift();
  ripples.push({ x: constrain(x, 0, width), y: constrain(y, 0, height), age: 0 });
  lastRippleAt = millis();
}

function draw() {
  const seconds = constrain(
    (typeof deltaTime === 'number' ? deltaTime : 16.67) / 1000,
    0,
    0.05,
  );
  background(242, 239, 231);
  noFill();
  const unit = min(width, height),
    time = millis() / 1000;
  // A quiet pool remains visible before and after interaction.
  for (let i = 0; i < 24; i++) {
    const u = i / 23;
    stroke(43, 40, 37, 50 + 75 * (1 - u));
    strokeWeight(0.8);
    const radius = unit * (0.15 + u * 0.23 + sin(time * 0.55 + u * 4) * 0.012);
    ellipse(width / 2, height / 2, radius * 2, radius * 1.34);
  }
  for (const ripple of ripples) {
    ripple.age += seconds;
    const fade = Math.max(0, 1 - ripple.age / 2);
    stroke(214, 120, 86, 160 * fade);
    strokeWeight(1.2);
    for (let ring = 0; ring < 3; ring++) {
      const size = 12 + ripple.age * unit * 0.3 + ring * 14;
      ellipse(ripple.x, ripple.y, size, size * 0.67);
    }
  }
  ripples = ripples.filter((ripple) => ripple.age < 2);
}

function mousePressed() {
  addRipple(mouseX, mouseY);
  return false;
}
function mouseDragged() {
  if (millis() - lastRippleAt > 60) addRipple(mouseX, mouseY);
  return false;
}
function touchStarted() {
  addRipple(mouseX, mouseY);
  return false;
}
function touchMoved() {
  if (millis() - lastRippleAt > 60) addRipple(mouseX, mouseY);
  return false;
}
