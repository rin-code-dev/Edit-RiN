// Sound Synthesizer & FFT Visualizer
// Edit:RiN sample work with p5.sound

let osc;
let fft;
let isPlaying = false;

function setup() {
  createCanvas(600, 600);
  colorMode(HSB, 360, 100, 100, 100);

  osc = new p5.Oscillator('sine');
  osc.amp(0);
  osc.start();

  fft = new p5.FFT(0.8, 128);
}

function draw() {
  background(225, 40, 12, 35);

  const touching = mouseIsPressed && mouseX >= 0 && mouseX <= width && mouseY >= 0 && mouseY <= height;

  if (touching) {
    if (!isPlaying) {
      if (typeof userStartAudio === 'function') {
        userStartAudio();
      }
      osc.amp(0.35, 0.05);
      isPlaying = true;
    }
    // Pentatonic scale note mapping
    const pentatonic = [0, 2, 4, 7, 9];
    const step = floor(map(mouseX, 0, width, 0, 20, true));
    const octave = floor(step / 5);
    const noteInScale = pentatonic[step % 5];
    const midi = 48 + octave * 12 + noteInScale; // C3 to C7
    const freq = midiToFreq(midi);
    osc.freq(freq, 0.04);
  } else if (isPlaying) {
    osc.amp(0, 0.15);
    isPlaying = false;
  }

  // Audio visualization
  const waveform = fft.waveform();
  const spectrum = fft.analyze();

  // Center coordinate
  push();
  translate(width * 0.5, height * 0.5);

  // Frequency spectrum bars in circle
  const barCount = min(spectrum.length, 64);
  noStroke();
  for (let i = 0; i < barCount; i++) {
    const angle = map(i, 0, barCount, 0, TWO_PI);
    const amp = spectrum[i];
    const r = map(amp, 0, 255, 60, 220);
    const hue = (i * 5 + frameCount * 0.5) % 360;

    fill(hue, 80, 95, 60);
    const x1 = cos(angle) * 60;
    const y1 = sin(angle) * 60;
    const x2 = cos(angle) * r;
    const y2 = sin(angle) * r;
    strokeWeight(3);
    stroke(hue, 80, 95, 70);
    line(x1, y1, x2, y2);
  }

  // Circular waveform
  noFill();
  strokeWeight(2.5);
  stroke(185, 90, 100, 90);
  beginShape();
  for (let i = 0; i < waveform.length; i++) {
    const angle = map(i, 0, waveform.length, 0, TWO_PI);
    const wave = waveform[i];
    const r = 100 + wave * 50;
    const x = cos(angle) * r;
    const y = sin(angle) * r;
    curveVertex(x, y);
  }
  endShape(CLOSE);

  pop();

  // Touch hint
  if (!touching) {
    noStroke();
    fill(0, 0, 90, 75);
    textAlign(CENTER, CENTER);
    textSize(18);
    text('Tap & drag to play synth', width * 0.5, height * 0.88);
  }
}

function touchStarted() {
  if (typeof userStartAudio === 'function') {
    userStartAudio();
  }
}
