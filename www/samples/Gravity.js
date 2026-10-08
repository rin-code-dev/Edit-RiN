// Gravity — orbital ink
// Hold or drag to move the attractor.
// @rin number pull "Gravity" 0.2 2 0.8 0.1
// @rin color ink "Ink" #C8C2B8
// Particle count
const COUNT = 64;
let particles = [];
let t = 0;

function setup() {
  createCanvas(600, 600);
  pixelDensity(1);
  background(28, 27, 26);
  for (let i = 0; i < COUNT; i++) {
    const angle = (i * TWO_PI) / COUNT;
    const radius = min(width, height) * (0.19 + (i % 4) * 0.048);
    particles.push({
      x: width / 2 + cos(angle) * radius,
      y: height / 2 + sin(angle) * radius,
      vx: -sin(angle) * 1.6,
      vy: cos(angle) * 1.6,
    });
  }
}

function draw() {
  const parameters = typeof rinParams === 'undefined' ? {} : rinParams;
  const step = constrain(
    (typeof deltaTime === 'number' ? deltaTime : 16.67) / 16.67,
    0,
    2,
  );
  t += step / 60;
  background(28, 27, 26, 12);
  const centerX = mouseIsPressed ? constrain(mouseX, 0, width) : width / 2;
  const centerY = mouseIsPressed ? constrain(mouseY, 0, height) : height / 2;
  const pull = parameters.pull ?? 0.8;
  stroke(parameters.ink ?? '#C8C2B8');
  strokeWeight(1.1);
  for (const point of particles) {
    const deltaX = centerX - point.x,
      deltaY = centerY - point.y;
    // Softening prevents an infinite force at the center.
    const distance = Math.max(30, Math.hypot(deltaX, deltaY));
    const force = (600 * pull) / (distance * distance);
    point.vx = constrain(point.vx + (deltaX / distance) * force * step, -6, 6);
    point.vy = constrain(point.vy + (deltaY / distance) * force * step, -6, 6);
    const x = point.x,
      y = point.y;
    point.x += point.vx * step;
    point.y += point.vy * step;
    line(x, y, point.x, point.y);
    point.x = (point.x + width) % width;
    point.y = (point.y + height) % height;
  }
  noStroke();
  fill(28, 27, 26);
  circle(centerX, centerY, 36);
  fill(214, 120, 86);
  circle(centerX, centerY, 7);
}

function touchStarted() {
  return false;
}
function touchMoved() {
  return false;
}
