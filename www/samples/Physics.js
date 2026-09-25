// Kinetic Neon Physics Sandbox
// Powered by matter.js 0.20.0 + p5.js
// Works with Edit:RiN Live Parameters!
// Requires: matter-js enabled in Work Settings -> Libraries

// @rin number gravity "Gravity" -0.5 2.0 0.8 0.1
// @rin number bounciness "Bounciness" 0.2 1.0 0.85 0.05
// @rin number spinnerSpeed "Spinner Speed" 0.0 4.0 1.5 0.1
// @rin color ballColor "Ball Color" #FF2A85
// @rin color obstacleColor "Obstacle Color" #00E5FF
// @rin boolean trail "Motion Blur" true

let engine;
let world;
let balls = [];
let staticPegs = [];
let spinners = [];

function hexToRgb(hex) {
  if (typeof hex !== 'string') return [255, 42, 133];
  const c = hex.replace('#', '');
  const n = parseInt(c.length === 3 ? c.split('').map(x => x + x).join('') : c, 16);
  if (isNaN(n)) return [255, 42, 133];
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function setup() {
  createCanvas(400, 400);

  if (typeof Matter === 'undefined') {
    console.error('matter.js is not loaded. Enable matter-js in Work Settings -> Libraries');
    return;
  }

  const { Engine, World, Bodies, Composite } = Matter;
  engine = Engine.create();
  world = engine.world;

  // Boundary walls
  const wallOpts = { isStatic: true, friction: 0.1, restitution: 0.8 };
  const leftWall = Bodies.rectangle(-10, height / 2, 30, height * 2, wallOpts);
  const rightWall = Bodies.rectangle(width + 10, height / 2, 30, height * 2, wallOpts);
  const bottomWall = Bodies.rectangle(width / 2, height + 10, width * 2, 30, wallOpts);
  Composite.add(world, [leftWall, rightWall, bottomWall]);

  // Triangular pins / Galton board pegs
  const rows = 4;
  for (let r = 0; r < rows; r++) {
    const cols = r + 3;
    const y = 80 + r * 45;
    for (let c = 0; c < cols; c++) {
      const x = width / 2 + (c - (cols - 1) / 2) * 44;
      const peg = Bodies.circle(x, y, 7, { isStatic: true, restitution: 0.9 });
      staticPegs.push(peg);
      Composite.add(world, peg);
    }
  }

  // Spinning cross obstacles
  const s1 = Bodies.rectangle(width * 0.3, height * 0.72, 80, 10, { isStatic: true });
  const s2 = Bodies.rectangle(width * 0.7, height * 0.72, 80, 10, { isStatic: true });
  spinners.push(s1, s2);
  Composite.add(world, [s1, s2]);
}

function spawnBall(x, y, radius = 10) {
  if (!world || typeof Matter === 'undefined') return;
  const p = (typeof rinParams !== 'undefined') ? rinParams : {};
  const rest = Number(p.bounciness ?? 0.85);

  const ball = Matter.Bodies.circle(x, y, radius, {
    restitution: rest,
    friction: 0.05,
    density: 0.04
  });
  balls.push(ball);
  Matter.Composite.add(world, ball);

  // Keep max balls capped for smooth performance
  if (balls.length > 45) {
    const old = balls.shift();
    Matter.Composite.remove(world, old);
  }
}

function draw() {
  const p = (typeof rinParams !== 'undefined') ? rinParams : {};
  const grav = Number(p.gravity ?? 0.8);
  const bounciness = Number(p.bounciness ?? 0.85);
  const spinSpeed = Number(p.spinnerSpeed ?? 1.5);
  const [bR, bG, bB] = hexToRgb(p.ballColor || '#FF2A85');
  const [oR, oG, oB] = hexToRgb(p.obstacleColor || '#00E5FF');
  const useTrail = Boolean(p.trail ?? true);

  if (useTrail) {
    background(10, 13, 20, 50);
  } else {
    background(10, 13, 20);
  }

  if (typeof Matter === 'undefined') {
    fill(255);
    textAlign(CENTER, CENTER);
    textSize(14);
    text('Please enable "matter-js" in\nWork Settings -> Libraries', width / 2, height / 2);
    return;
  }

  // Update physics engine
  world.gravity.y = grav;
  Matter.Engine.update(engine, 1000 / 60);

  // Auto drop balls periodically
  if (frameCount % 45 === 0 && balls.length < 30) {
    spawnBall(random(width * 0.35, width * 0.65), 10, random(8, 14));
  }

  // Rotate spinners
  spinners.forEach((spinner, i) => {
    const dir = i % 2 === 0 ? 1 : -1;
    Matter.Body.rotate(spinner, 0.02 * spinSpeed * dir);

    push();
    translate(spinner.position.x, spinner.position.y);
    rotate(spinner.angle);
    rectMode(CENTER);
    noStroke();
    fill(oR, oG, oB, 40);
    rect(0, 0, 88, 16, 6);
    fill(oR, oG, oB);
    rect(0, 0, 80, 10, 4);
    circle(0, 0, 12);
    pop();
  });

  // Draw static pegs
  staticPegs.forEach(peg => {
    noStroke();
    fill(oR, oG, oB, 50);
    circle(peg.position.x, peg.position.y, 18);
    fill(oR, oG, oB, 220);
    circle(peg.position.x, peg.position.y, 10);
  });

  // Draw balls
  balls.forEach(ball => {
    ball.restitution = bounciness;
    push();
    translate(ball.position.x, ball.position.y);
    rotate(ball.angle);
    const r = ball.circleRadius;

    // Outer glow
    noStroke();
    fill(bR, bG, bB, 40);
    circle(0, 0, r * 2.8);

    // Inner core
    fill(bR, bG, bB, 230);
    circle(0, 0, r * 2);

    // Rotation indicator notch
    stroke(255, 200);
    strokeWeight(1.5);
    line(0, 0, r * 0.7, 0);
    pop();
  });
}

function touchStarted() {
  spawnBall(mouseX, mouseY, random(9, 15));
  return false;
}

function touchMoved() {
  if (frameCount % 4 === 0) {
    spawnBall(mouseX, mouseY, random(8, 12));
  }
  return false;
}
