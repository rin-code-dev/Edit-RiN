// Sound — touch to play tones; an oscilloscope sweeps the screen
const SIZE = 600;
let osc;
let fft;
let t = 0;

function setup() {
  createCanvas(SIZE, SIZE);
  osc = new p5.Oscillator('triangle');
  osc.amp(0);
  osc.start();
  fft = new p5.FFT(0.8, 1024);
}

function draw() {
  t += 0.02;
  background(9, 9, 11);

  if (mouseIsPressed) {
    const midi = floor(map(mouseX, 0, width, 45, 81, true));
    osc.freq(440 * pow(2, (midi - 69) / 12), 0.04);
    osc.amp(map(mouseY, height, 0, 0.1, 0.4, true), 0.05);
  } else {
    osc.amp(0, 0.15);
  }

  const wave = fft.waveform();

  // 背景の淡い余韻波
  noFill();
  stroke(168, 199, 250, 40);
  strokeWeight(1);
  beginShape();
  for (let i = 0; i < wave.length; i += 4) {
    const x = map(i, 0, wave.length, 0, width);
    const y = height / 2 + wave[i] * 180 + sin(t + i * 0.02) * 15;
    vertex(x, y);
  }
  endShape();

  // メインのオシロスコープ波形（画面全体を横断）
  stroke(168, 199, 250);
  strokeWeight(3);
  beginShape();
  for (let i = 0; i < wave.length; i += 2) {
    const x = map(i, 0, wave.length, 0, width);
    const idle = !mouseIsPressed ? sin(t * 2 + (x / width) * TWO_PI * 2) * 8 : 0;
    const y = height / 2 + wave[i] * 220 + idle;
    vertex(x, y);
  }
  endShape();

  if (!mouseIsPressed) {
    noStroke();
    fill(168, 199, 250, 160);
    textAlign(CENTER, CENTER);
    text('Touch to play', width / 2, height - 40);
  }
}

function touchStarted() {
  userStartAudio();
}
