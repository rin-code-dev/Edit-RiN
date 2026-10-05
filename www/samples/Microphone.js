// Microphone — car audio style graphic equalizer reactive to voice
const SIZE = 600;
const BANDS = 24;
const ROWS = 20;

let mic;
let fft;
let peaks = new Array(BANDS).fill(0);
let peakWait = new Array(BANDS).fill(0);
let started = false;

function setup() {
  createCanvas(SIZE, SIZE);
  mic = new p5.AudioIn();
  fft = new p5.FFT(0.7, 64);
  textAlign(CENTER, CENTER);
}

function draw() {
  background(9, 9, 11);

  // カーオーディオのフレーム/ヘッダー情報
  stroke(168, 199, 250, 50);
  strokeWeight(1);
  line(40, 70, width - 40, 70);
  line(40, height - 70, width - 40, height - 70);

  noStroke();
  fill(168, 199, 250, 200);
  textSize(12);
  textAlign(LEFT, CENTER);
  text('CAR AUDIO EQ  //  STEREO SPECTRUM', 42, 52);
  textAlign(RIGHT, CENTER);
  text(started ? 'MIC: ACTIVE' : 'MIC: STANDBY', width - 42, 52);

  const spectrum = started ? fft.analyze() : [];

  const eqLeft = 50;
  const eqRight = width - 50;
  const eqTop = 90;
  const eqBottom = height - 90;
  const eqHeight = eqBottom - eqTop;
  const barWidth = (eqRight - eqLeft) / BANDS;
  const blockGapY = 3;
  const blockHeight = (eqHeight - (ROWS - 1) * blockGapY) / ROWS;

  for (let i = 0; i < BANDS; i++) {
    const val = started && spectrum[i] ? spectrum[i] / 255 : 0;
    const activeBlocks = floor(val * ROWS);

    // ピークホールド更新
    if (activeBlocks >= peaks[i]) {
      peaks[i] = activeBlocks;
      peakWait[i] = 12;
    } else {
      if (peakWait[i] > 0) {
        peakWait[i]--;
      } else if (peaks[i] > 0) {
        peaks[i] -= 0.3;
      }
    }

    const bx = eqLeft + i * barWidth + 3;
    const bw = barWidth - 6;

    // LEDセグメント描画（下から上へ）
    for (let r = 0; r < ROWS; r++) {
      const by = eqBottom - (r + 1) * (blockHeight + blockGapY);
      const isLit = r < activeBlocks;
      const isPeak = floor(peaks[i]) === r && r > 0;

      if (isPeak) {
        fill(255, 255, 255); // ピークLEDは白発光
      } else if (isLit) {
        fill(168, 199, 250); // 点灯ブロック
      } else {
        fill(168, 199, 250, 25); // 消灯セグメント（グリッド状の陰影）
      }
      rect(bx, by, bw, blockHeight, 1);
    }
  }

  // フッターの周波数ラベル
  fill(168, 199, 250, 140);
  textAlign(CENTER, CENTER);
  textSize(10);
  const labels = ['31', '63', '125', '250', '500', '1k', '2k', '4k', '8k', '16k'];
  for (let k = 0; k < labels.length; k++) {
    const lx = map(k, 0, labels.length - 1, eqLeft + 15, eqRight - 15);
    text(labels[k], lx, height - 52);
  }

  if (!started) {
    fill(9, 9, 11, 200);
    rect(width / 2 - 120, height / 2 - 25, 240, 50, 8);
    stroke(168, 199, 250);
    strokeWeight(1);
    noFill();
    rect(width / 2 - 120, height / 2 - 25, 240, 50, 8);
    noStroke();
    fill(168, 199, 250);
    textSize(14);
    text('Touch to start Mic', width / 2, height / 2);
  }
}

function touchStarted() {
  if (!started) {
    userStartAudio().then(() => {
      mic.start(() => {
        fft.setInput(mic);
        started = true;
      });
    });
  }
}
