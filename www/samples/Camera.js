// Camera — halftone mirror
// Tap to allow the camera; tap again to stop.
const COLS = 64,
  ROWS = 48;
let video, stream, buffer, pixels;
let pending = false,
  started = false;
let status = 'TAP TO START CAMERA';
let request = 0;

function setup() {
  createCanvas(600, 600);
  pixelDensity(1);
  noStroke();
  buffer = document.createElement('canvas');
  buffer.width = COLS;
  buffer.height = ROWS;
  pixels = buffer.getContext('2d', { willReadFrequently: true });
}

async function startCamera() {
  if (pending) return;
  if (started) {
    stopCamera();
    return;
  }
  const token = ++request;
  pending = true;
  status = 'WAITING FOR PERMISSION';
  try {
    const incoming = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'user', width: { ideal: 640 }, height: { ideal: 480 } },
      audio: false,
    });
    if (token !== request) {
      incoming.getTracks().forEach((track) => track.stop());
      return;
    }
    stream = incoming;
    video = document.createElement('video');
    video.muted = true;
    video.playsInline = true;
    video.srcObject = stream;
    await video.play();
    if (token !== request) return;
    started = true;
    status = 'TAP TO STOP CAMERA';
  } catch (_) {
    if (token === request) {
      stopCamera();
      status = 'CAMERA UNAVAILABLE · TAP TO RETRY';
    }
  } finally {
    if (token === request) pending = false;
  }
}

function stopCamera() {
  request++;
  pending = false;
  started = false;
  stream?.getTracks().forEach((track) => track.stop());
  if (video) {
    video.pause();
    video.srcObject = null;
  }
  video = stream = null;
  status = 'TAP TO START CAMERA';
}

function draw() {
  background(242, 239, 231);
  if (
    started &&
    video.readyState >= 2 &&
    video.videoWidth > 0 &&
    video.videoHeight > 0
  ) {
    // Center crop before sampling, preserving the source aspect ratio.
    const ratio = COLS / ROWS;
    const cropWidth = Math.min(video.videoWidth, video.videoHeight * ratio);
    const cropHeight = cropWidth / ratio;
    pixels.drawImage(
      video,
      (video.videoWidth - cropWidth) / 2,
      (video.videoHeight - cropHeight) / 2,
      cropWidth,
      cropHeight,
      0,
      0,
      COLS,
      ROWS,
    );
    const data = pixels.getImageData(0, 0, COLS, ROWS).data;
    const cell = Math.min((width * 0.8) / COLS, (height * 0.65) / ROWS);
    const left = (width - cell * COLS) / 2,
      top = (height - cell * ROWS) / 2;
    for (let y = 0; y < ROWS; y++) {
      for (let x = 0; x < COLS; x++) {
        const pixelIndex = (y * COLS + COLS - 1 - x) * 4;
        const luminance =
          (data[pixelIndex] * 0.2126 +
            data[pixelIndex + 1] * 0.7152 +
            data[pixelIndex + 2] * 0.0722) /
          255;
        fill(43, 40, 37);
        circle(
          left + (x + 0.5) * cell,
          top + (y + 0.5) * cell,
          cell * Math.sqrt(1 - luminance) * 0.96,
        );
      }
    }
  }
  if (!started) {
    noFill();
    stroke(193, 189, 177);
    strokeWeight(0.8);
    const unit = min(width, height);
    circle(width / 2, height / 2, unit * 0.4);
    circle(width / 2, height / 2, unit * 0.32);
    noStroke();
    fill(214, 120, 86);
    circle(width / 2, height / 2, unit * 0.04);
  }
  noStroke();
  fill(97, 89, 81);
  textAlign(CENTER, CENTER);
  textSize(11);
  text(status, width / 2, height * 0.88);
}

function mousePressed() {
  startCamera();
  return false;
}
window.addEventListener('pagehide', stopCamera);
document.addEventListener('visibilitychange', () => {
  if (document.hidden) stopCamera();
});
