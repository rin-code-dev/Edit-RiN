// Microphone — voice bloom
// Tap to allow the microphone; tap again to stop.
let audio, stream, source, analyser, samples;
let started = false,
  pending = false;
let level = 0;
let status = 'TAP TO START MICROPHONE';
let request = 0;

function setup() {
  createCanvas(600, 600);
  pixelDensity(1);
}

async function startMicrophone() {
  if (pending) return;
  if (started) {
    stopMicrophone();
    return;
  }
  const token = ++request;
  pending = true;
  status = 'WAITING FOR PERMISSION';
  try {
    audio ??= new (window.AudioContext || window.webkitAudioContext)();
    await audio.resume();
    if (token !== request) return;
    const incoming = await navigator.mediaDevices.getUserMedia({
      audio: true,
      video: false,
    });
    if (token !== request) {
      incoming.getTracks().forEach((track) => track.stop());
      return;
    }
    stream = incoming;
    source = audio.createMediaStreamSource(stream);
    analyser = audio.createAnalyser();
    analyser.fftSize = 256;
    samples = new Uint8Array(analyser.fftSize);
    // No speaker connection: avoids feedback.
    source.connect(analyser);
    started = true;
    status = 'SPEAK · TAP TO STOP';
  } catch (_) {
    if (token === request) {
      stopMicrophone();
      status = 'MICROPHONE UNAVAILABLE · TAP TO RETRY';
    }
  } finally {
    if (token === request) pending = false;
  }
}

function stopMicrophone() {
  request++;
  pending = false;
  started = false;
  stream?.getTracks().forEach((track) => track.stop());
  source?.disconnect();
  analyser?.disconnect();
  stream = source = analyser = samples = null;
  audio?.suspend().catch(() => {});
  status = 'TAP TO START MICROPHONE';
}

function draw() {
  let volume = 0;
  if (started) {
    analyser.getByteTimeDomainData(samples);
    let sum = 0;
    for (const sample of samples) sum += Math.pow((sample - 128) / 128, 2);
    volume = constrain(Math.sqrt(sum / samples.length) * 5, 0, 1);
  }
  const seconds = constrain(
    (typeof deltaTime === 'number' ? deltaTime : 16.67) / 1000,
    0,
    0.05,
  );
  level = lerp(level, volume, 1 - Math.exp(-seconds * 12));
  background(242, 239, 231);
  translate(width / 2, height / 2);
  const unit = min(width, height),
    time = millis() / 1000;
  noFill();
  stroke(43, 40, 37);
  strokeWeight(0.8);
  // A fine radial aperture opens with the voice.
  for (let petal = 0; petal < 64; petal++) {
    push();
    rotate((petal * TWO_PI) / 64 + time * 0.025);
    const inner = unit * (0.12 - level * 0.045);
    const outer = unit * (0.27 + level * 0.075);
    beginShape();
    for (let i = 0; i <= 24; i++) {
      const progress = i / 24;
      vertex(
        inner + (outer - inner) * progress,
        sin(progress * PI) * unit * (0.075 + level * 0.06),
      );
    }
    endShape();
    pop();
  }
  noStroke();
  fill(214, 120, 86);
  circle(0, 0, unit * 0.095);
  fill(97, 89, 81);
  textAlign(CENTER, CENTER);
  textSize(11);
  text(status, 0, height * 0.38);
}

function mousePressed() {
  startMicrophone();
  return false;
}
window.addEventListener('pagehide', () => {
  stopMicrophone();
  audio?.close().catch(() => {});
});
document.addEventListener('visibilitychange', () => {
  if (document.hidden) stopMicrophone();
});

// Preview pause/resume controls this audio context.
function getAudioContext() {
  audio ??= new (window.AudioContext || window.webkitAudioContext)();
  return audio;
}
