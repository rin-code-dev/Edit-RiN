let t = 0;

const W = 600;
const N = 72;
const RANGE = 205;
const POINT_COUNT = N * N;

const pointR = new Float32Array(POINT_COUNT);
const pointA = new Float32Array(POINT_COUNT);
const pointNoise = new Float32Array(POINT_COUNT);

function setup() {
  createCanvas(W, W, WEBGL);

  pixelDensity(1);

  stroke(255, 180);
  strokeWeight(1.5);
  strokeCap(ROUND);

  noFill();

  let index = 0;

  // 動かない値は起動時に一度だけ計算する
  for (let j = 0; j < N; j++) {
    for (let i = 0; i < N; i++) {
      const x = map(i, 0, N - 1, -RANGE, RANGE);
      const y = map(j, 0, N - 1, -RANGE, RANGE);
      const r = sqrt(x * x + y * y);

      pointR[index] = r;
      pointA[index] = atan2(y, x);
      pointNoise[index] = noise(x * 0.012 + 20, y * 0.012 + 20);
      index++;
    }
  }
}

function draw() {
  background(5);

  rotateX(0.92);
  rotateZ(-0.22);

  translate(0, 18, 0);

  stroke(255, 185);

  strokeWeight(1.7);

  beginShape(POINTS);

  for (let i = 0; i < POINT_COUNT; i++) {
    const r = pointR[i];
    const a = pointA[i];
    const n = pointNoise[i];
    const gravity = exp(-r / 105);

    // 波
    const wave =
      sin(
        r * 0.055 -
        t * 4.2 +
        n * 2
      );

    // 渦
    const twist =
      gravity *
      (
        1.15 +
        wave * 0.18
      );

    const angle =
      a +
      twist;

    // 半径変形
    let radius =
      r +
      wave *
      16 *
      gravity;

    radius -=
      gravity *
      22 *
      (
        0.5 +
        0.5 *
        sin(t * 1.8)
      );

    const px =
      cos(angle) *
      radius;

    const py =
      sin(angle) *
      radius;

    // リング
    const ring =
      exp(
        -sq(r - 112) /
        1600
      );

    // 中央の落ち込み
    const pit =
      exp(
        -sq(r) /
        2300
      );

    // 微細な表面
    const texture =
      sin(
        n * 12 +
        t * 2
      ) *
      7;

    const z =
      wave *
      62 *
      gravity +
      ring *
      (
        27 +
        sin(
          t * 3 +
          a * 6
        ) * 9
      ) -
      pit * 72 +
      texture;

    vertex(
      px,
      py,
      z
    );
  }

  endShape();

  t += 0.012;
}
