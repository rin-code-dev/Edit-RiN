// p5.js 2.3.3 + p5.sound
// MONO SYNTH SCOPE

let osc;
let filter;
let fft;
let env;

let active = false;
let freq = 220;

function setup() {
  createCanvas(windowWidth, windowHeight);

  // Oscillator
  osc = new p5.Oscillator(220, "sine");

  // Filter
  filter = new p5.LowPass();

  osc.disconnect();
  osc.connect(filter);

  // Envelope
  env = new p5.Envelope();
  env.setADSR(0.03, 0.1, 0.35, 0.2);

  // Envelope経由で出力
  filter.disconnect();
  filter.connect(env);

  // FFT
  fft = new p5.FFT(512);
  env.connect(fft);

  osc.start();

  textFont("monospace");
  strokeCap(ROUND);
}

function draw() {
  background(8);

  if (active) {
    // 横方向 = 音程
    const targetFreq = map(
      constrain(mouseX, 0, width),
      0,
      width,
      80,
      600
    );

    freq = lerp(freq, targetFreq, 0.08);

    osc.freq(freq);

    // 縦方向 = フィルター
    const cutoff = map(
      constrain(mouseY, 0, height),
      height,
      0,
      180,
      2200
    );

    filter.freq(cutoff);
    filter.res(1.5);
  }

  drawGrid();
  drawWave();
  drawCursor();
  drawUI();
}

function drawGrid() {
  strokeWeight(1);
  stroke(255, 15);

  const gap = 40;

  for (let x = gap; x < width; x += gap) {
    line(x, 0, x, height);
  }

  for (let y = gap; y < height; y += gap) {
    line(0, y, width, y);
  }

  stroke(255, 40);
  line(0, height / 2, width, height / 2);
}

function drawWave() {
  const wave = fft.waveform();

  noFill();

  // glow
  stroke(255, 30);
  strokeWeight(5);

  beginShape();

  for (let i = 0; i < wave.length; i++) {
    const x = map(i, 0, wave.length - 1, 0, width);
    const y =
      height / 2 +
      wave[i] * height * 0.3;

    vertex(x, y);
  }

  endShape();

  // main waveform
  stroke(255, 230);
  strokeWeight(1.5);

  beginShape();

  for (let i = 0; i < wave.length; i++) {
    const x = map(i, 0, wave.length - 1, 0, width);
    const y =
      height / 2 +
      wave[i] * height * 0.3;

    vertex(x, y);
  }

  endShape();
}

function drawCursor() {
  if (!active) return;

  stroke(255, 80);
  strokeWeight(1);

  line(mouseX, 0, mouseX, height);
  line(0, mouseY, width, mouseY);

  noFill();

  stroke(255);
  strokeWeight(1.5);

  circle(mouseX, mouseY, 20);

  fill(255);
  noStroke();

  circle(mouseX, mouseY, 3);
}

function drawUI() {
  noStroke();

  fill(255);
  textSize(11);

  text("MONO SYNTH SCOPE", 18, 25);

  fill(255, 100);

  if (active) {
    text(
      freq.toFixed(1) + " Hz",
      18,
      height - 22
    );
  } else {
    text(
      "TOUCH + DRAG",
      18,
      height - 22
    );
  }
}

function soundOn() {
  userStartAudio();

  if (!active) {
    active = true;
    env.triggerAttack(0.18);
  }
}

function soundOff() {
  if (active) {
    active = false;
    env.triggerRelease();
  }
}

function mousePressed() {
  soundOn();
  return false;
}

function mouseReleased() {
  soundOff();
  return false;
}

function touchStarted() {
  soundOn();
  return false;
}

function touchEnded() {
  soundOff();
  return false;
}

function windowResized() {
  resizeCanvas(windowWidth, windowHeight);
}
