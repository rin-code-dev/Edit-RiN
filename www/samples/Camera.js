// Camera — dot matrix video stream reflecting the device camera
const SIZE = 600;
const STEP = 12;

let capture;
let ready = false;

function setup() {
  createCanvas(SIZE, SIZE);
  capture = createCapture(VIDEO, () => {
    ready = true;
  });
  if (capture && capture.elt) {
    capture.elt.setAttribute('poster', 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7');
  }
  capture.size(SIZE / STEP, SIZE / STEP);
  capture.hide();
  noStroke();
}

function draw() {
  background(9, 9, 11);

  const videoReady = (ready || (capture && (capture.loadedmetadata || capture.width > 0))) &&
    Boolean(capture && capture.width > 0 && capture.height > 0);
  if (videoReady) {
    capture.loadPixels();
    const cols = capture.width;
    const rows = capture.height;

    if (cols > 0 && rows > 0 && capture.pixels && capture.pixels.length > 0) {
      for (let y = 0; y < rows; y++) {
        for (let x = 0; x < cols; x++) {
          // ミラー反転（インカメラ自然表示）
          const idx = ((y * cols) + (cols - 1 - x)) * 4;
          const r = capture.pixels[idx];
          const g = capture.pixels[idx + 1];
          const b = capture.pixels[idx + 2];
          const bright = (r * 0.299 + g * 0.587 + b * 0.114) / 255;

          if (bright > 0.08) {
            fill(168, 199, 250, bright * 255);
            const px = map(x, 0, cols, 0, width);
            const py = map(y, 0, rows, 0, height);
            const radius = bright * (STEP - 1);
            circle(px + STEP / 2, py + STEP / 2, radius);
          }
        }
      }
    }
  } else {
    fill(168, 199, 250, 160);
    textAlign(CENTER, CENTER);
    textSize(14);
    text('Waiting for camera...', width / 2, height / 2);
  }
}

function touchStarted() {
  if (capture && capture.elt && typeof capture.elt.play === 'function') {
    capture.elt.play();
  }
}
