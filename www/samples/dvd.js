const logo = "EDIT:RiN";
const palette = [
  "#A8C7FA",
  "#FF8CB5",
  "#A8E6A3",
  "#FFD580",
  "#C3A6FF"
];

let x = 0;
let y = 0;
let directionX = 1;
let directionY = 1;
let colorIndex = 0;
let logoWidth = 0;
let logoHeight = 0;
let lastWidth = 0;
let lastHeight = 0;

let rollY = 0;

function setup() {
  createCanvas(windowWidth, windowHeight);
  pixelDensity(1);

  textFont("monospace");
  textStyle(BOLD);
  textAlign(LEFT, TOP);
  noStroke();

  measureLogo();

  x = (width - logoWidth) * 0.18;
  y = (height - logoHeight) * 0.27;
}

function measureLogo() {
  textSize(max(1, min(width * 0.10, height * 0.16)));

  const fit = min(
    1,
    width / max(1, textWidth(logo)),
    height / max(1, textAscent() + textDescent())
  );

  textSize(textSize() * fit * 0.92);

  logoWidth = min(width, textWidth(logo));
  logoHeight = min(height, textAscent() + textDescent());

  x = constrain(x, 0, max(0, width - logoWidth));
  y = constrain(y, 0, max(0, height - logoHeight));

  lastWidth = width;
  lastHeight = height;
}

function draw() {
  if (width !== lastWidth || height !== lastHeight) {
    measureLogo();
  }

  // わずかな明滅
  const flicker = random(-3, 3);
  background(9 + flicker, 9 + flicker, 11 + flicker);

  updateLogo();
  drawLogo();

  // CRTエフェクト
  drawRollingBand();
  drawScanlines();
  drawStaticNoise();
  drawVignette();
}

function updateLogo() {
  const step =
    min(deltaTime, 50) / 1000 *
    min(width, height) *
    0.24;

  const maxX = max(0, width - logoWidth);
  const maxY = max(0, height - logoHeight);

  x += directionX * step;
  y += directionY * step;

  let hit = false;

  if (x <= 0 || x >= maxX) {
    directionX = x <= 0 ? 1 : -1;
    x = constrain(x, 0, maxX);
    hit = true;
  }

  if (y <= 0 || y >= maxY) {
    directionY = y <= 0 ? 1 : -1;
    y = constrain(y, 0, maxY);
    hit = true;
  }

  if (hit) {
    colorIndex = (colorIndex + 1) % palette.length;
  }
}

function drawLogo() {
  // 一瞬だけ発生する水平同期の乱れ
  let glitchX = 0;

  if (random() < 0.025) {
    glitchX = random(-5, 5);
  }

  // 色ずれ
  blendMode(ADD);

  fill(255, 40, 70, 18);
  text(logo, x + glitchX - 1.5, y);

  fill(40, 180, 255, 18);
  text(logo, x + glitchX + 1.5, y);

  blendMode(BLEND);

  fill(palette[colorIndex]);
  text(logo, x + glitchX, y);
}

function drawScanlines() {
  strokeWeight(1);

  // 固定された細い走査線
  stroke(0, 55);

  for (let sy = 0; sy < height; sy += 4) {
    line(0, sy, width, sy);
  }

  noStroke();
}

function drawStaticNoise() {
  const amount = floor(width * height * 0.00045);

  strokeWeight(1);

  // 微細な砂嵐
  for (let i = 0; i < amount; i++) {
    const nx = random(width);
    const ny = random(height);
    const brightness = random() < 0.5 ? 255 : 0;

    stroke(brightness, random(10, 45));
    point(nx, ny);
  }

  // 短い横ノイズ
  const streaks = floor(random(2, 7));

  for (let i = 0; i < streaks; i++) {
    const sy = random(height);
    const sx = random(width);
    const length = random(10, width * 0.12);

    stroke(255, random(8, 25));
    line(sx, sy, min(width, sx + length), sy);
  }

  noStroke();
}

function drawRollingBand() {
  rollY += min(deltaTime, 50) * 0.055;

  if (rollY > height + 100) {
    rollY = -100;
  }

  const ctx = drawingContext;
  const bandHeight = max(40, height * 0.12);

  const gradient = ctx.createLinearGradient(
    0,
    rollY - bandHeight,
    0,
    rollY + bandHeight
  );

  gradient.addColorStop(0, "rgba(255,255,255,0)");
  gradient.addColorStop(0.5, "rgba(255,255,255,0.025)");
  gradient.addColorStop(1, "rgba(255,255,255,0)");

  ctx.save();
  ctx.fillStyle = gradient;
  ctx.fillRect(0, rollY - bandHeight, width, bandHeight * 2);
  ctx.restore();
}

function drawVignette() {
  const ctx = drawingContext;
  const radius = max(width, height) * 0.72;

  const gradient = ctx.createRadialGradient(
    width * 0.5,
    height * 0.5,
    radius * 0.15,
    width * 0.5,
    height * 0.5,
    radius
  );

  gradient.addColorStop(0, "rgba(0,0,0,0)");
  gradient.addColorStop(0.65, "rgba(0,0,0,0.05)");
  gradient.addColorStop(1, "rgba(0,0,0,0.72)");

  ctx.save();
  ctx.fillStyle = gradient;
  ctx.fillRect(0, 0, width, height);
  ctx.restore();
}

function windowResized() {
  resizeCanvas(windowWidth, windowHeight);
  measureLogo();
}
