// p5.js 2.3.3 / p5.brush ON / プレビュー比率 1:1
// SEEDを変えて再実行すると、山並みと葉の配置が変わります。
const SEED = 28;

function setup() {
  createCanvas(600, 600, WEBGL);
  pixelDensity(1);
  randomSeed(SEED);
  noiseSeed(SEED);
  noLoop();
}

function draw() {
  background('#f3efe3');
  // WEBGLの中央原点を左上へ移動。
  translate(-width / 2, -height / 2);

  // 淡い墨を重ねた遠山。
  brush.noStroke();
  for (let layer = 0; layer < 3; layer++) {
    const points = [];
    for (let x = -30; x <= 630; x += 22) {
      const y = 245 + layer * 58
        - noise(x * 0.006, layer * 3) * 145;
      points.push([x, y]);
    }
    points.push([630, 420], [-30, 420]);
    brush.fill('#4b5551', 12 + layer * 6);
    brush.polygon(points);
  }

  // 余白に浮かぶ淡い月。
  brush.fill('#aaa89b', 22);
  brush.circle(440, 98, 48);

  // 手前の竹。節ごとに少しずつ曲げる。
  bamboo(125, 555, 7, -5);
  bamboo(193, 575, 8, 4);
  bamboo(265, 562, 6, 9);

  // 小さな朱印。
  brush.noStroke();
  brush.fill('#a34335', 150);
  brush.rect(510, 506, 23, 28);
  brush.noFill();
  brush.set('HB', '#f3efe3', 0.7);
  brush.line(516, 512, 526, 512);
  brush.line(521, 512, 521, 527);
  brush.line(515, 521, 527, 521);
}

function bamboo(x, y, segments, lean) {
  for (let i = 0; i < segments; i++) {
    const nx = x + lean + random(-3, 3);
    const ny = y - 51;
    brush.noFill();
    brush.set('HB', '#303b32', 3.5);
    brush.line(x, y, nx, ny + 4);
    brush.set('HB', '#18271e', 1.2);
    brush.line(nx - 5, ny + 3, nx + 6, ny + 1);

    if (i > 1 && i % 2 === 0) {
      const side = i % 4 === 0 ? -1 : 1;
      const tipX = nx + side * 70;
      const tipY = ny - 27;
      brush.line(nx, ny, tipX, tipY);
      for (let j = 0; j < 5; j++) {
        const t = (j + 1) / 6;
        const bx = lerp(nx, tipX, t);
        const by = lerp(ny, tipY, t);
        const dx = side * random(18, 35);
        const dy = (j % 2 ? 1 : -1) * random(20, 38);
        brush.noStroke();
        brush.fill('#203329', random(100, 170));
        brush.polygon([
          [bx, by],
          [bx + dx * 0.4 - 4, by + dy * 0.5],
          [bx + dx, by + dy],
          [bx + dx * 0.4 + 4, by + dy * 0.5]
        ]);
      }
    }
    x = nx;
    y = ny;
  }
}
