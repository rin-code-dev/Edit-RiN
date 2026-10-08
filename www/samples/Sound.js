// Sound — pocket instrument
// Hold and slide to change pitch.
// Short notes stop even when drawing is paused.
let audio, voice;
let active = false,
  starting = false;
let frequency = 220;
let lastNoteAt = -Infinity;
let status = 'TOUCH TO PLAY';

function setup() {
  createCanvas(600, 600);
  pixelDensity(1);
}

async function soundOn() {
  if (starting || active) return;
  starting = true;
  active = true;
  try {
    audio ??= new (window.AudioContext || window.webkitAudioContext)();
    await audio.resume();
    if (active) {
      status = 'SLIDE TO CHANGE PITCH';
      playNote();
    }
  } catch (_) {
    active = false;
    status = 'AUDIO UNAVAILABLE · TAP TO RETRY';
  } finally {
    starting = false;
  }
}

function playNote() {
  if (!audio || audio.state !== 'running') return;
  if (voice) {
    try {
      voice.stop();
    } catch (_) {}
  }
  const now = audio.currentTime;
  const oscillator = audio.createOscillator(),
    gain = audio.createGain();
  frequency = 110 * Math.pow(2, constrain(mouseX / width, 0, 1) * 2);
  oscillator.type = 'sine';
  oscillator.frequency.value = frequency;
  gain.gain.setValueAtTime(0, now);
  gain.gain.linearRampToValueAtTime(0.08, now + 0.015);
  gain.gain.exponentialRampToValueAtTime(0.001, now + 0.22);
  oscillator.connect(gain);
  gain.connect(audio.destination);
  oscillator.onended = () => {
    oscillator.disconnect();
    gain.disconnect();
    if (voice === oscillator) voice = null;
  };
  oscillator.start(now);
  oscillator.stop(now + 0.24);
  voice = oscillator;
  lastNoteAt = millis();
}

function soundOff() {
  active = false;
  if (voice) {
    try {
      voice.stop();
    } catch (_) {}
    voice = null;
  }
  status = 'TOUCH TO PLAY';
}

function draw() {
  if (active && millis() - lastNoteAt > 160) playNote();
  background(28, 27, 26);
  noFill();
  const unit = min(width, height),
    time = millis() / 1000;
  const energy = Math.max(0, 1 - (millis() - lastNoteAt) / 240);
  // Sound becomes a standing wave; idle motion stays subtle.
  for (let row = 0; row < 36; row++) {
    const rowPosition = row / 35;
    stroke(row === 18 ? '#D67856' : '#C8C2B8');
    strokeWeight(row === 18 ? 1.5 : 0.65);
    beginShape();
    for (let segment = 0; segment <= 80; segment++) {
      const progress = segment / 80,
        envelope = Math.pow(sin(progress * PI), 3) * sin(rowPosition * PI);
      const wave = sin(
        progress * TWO_PI * (2 + frequency / 160) - time * 1.2 + rowPosition * 3,
      );
      vertex(
        width / 2 + (progress - 0.5) * unit * 0.76,
        height / 2 +
          (rowPosition - 0.5) * unit * 0.37 +
          wave * envelope * unit * (0.018 + energy * 0.09),
      );
    }
    endShape();
  }
  noStroke();
  fill(200, 194, 184);
  textAlign(CENTER, CENTER);
  textSize(11);
  text(status, width / 2, height * 0.88);
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
function touchMoved() {
  return false;
}
window.addEventListener('pagehide', () => {
  soundOff();
  audio?.close().catch(() => {});
});
document.addEventListener('visibilitychange', () => {
  if (document.hidden) {
    soundOff();
    audio?.suspend().catch(() => {});
  }
});

// Preview pause/resume controls this audio context.
function getAudioContext() {
  audio ??= new (window.AudioContext || window.webkitAudioContext)();
  return audio;
}
