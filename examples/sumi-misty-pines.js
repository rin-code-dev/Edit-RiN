// 「松風・霧の渡し」— Edit:RiN用の水墨山水
// 作品設定 → 実行環境: p5.js 2.3.3 / p5.brush ON
// プレビュー比率 3:4。コード全文を sketch.js に貼り付けて実行。
// 外部画像・フォント不要。描画は段階的に進み、完成すると停止します。
const SUMI = {
  seed: 72831,        // 変えると岩肌・松葉の表情が変化
  width: 720,
  height: 960,
  paper: '#eee8d9',
  ink: '#252c29',
  pale: '#747e77',
  seal: '#984336'
};
let paintingStep = 0;

function setup() {
  pixelDensity(1);
  createCanvas(SUMI.width, SUMI.height, WEBGL);
  randomSeed(SUMI.seed);
  noiseSeed(SUMI.seed);
  brush.seed(SUMI.seed);
  brush.noiseSeed(SUMI.seed);
  frameRate(12);
}

function draw() {
  // 各フレームの末尾でp5.brushが描画を確定する。
  // background()は初回だけ。次の工程でも描き重ねを保持する。
  translate(-width / 2, -height / 2);
  brush.noField();
  brush.noHatch();
  brush.noStroke();
  brush.noFill();

  switch (paintingStep++) {
    case 0: paperSurface(); break;
    case 1:
      mountain([[40,342],[102,298],[156,203],[206,256],
        [259,163],[304,237],[366,289],[430,350]], 426, 17, 0);
      break;
    case 2:
      mountain([[276,417],[348,351],[392,268],[431,318],
        [473,232],[518,301],[564,341],[620,405]], 478, 26, 1);
      break;
    case 3:
      mountain([[46,474],[96,405],[135,338],[177,393],
        [212,324],[256,409],[305,476]], 522, 39, 2);
      break;
    case 4: waterAndBoat(); break;
    case 5: foregroundRock(); break;
    case 6: pineTrunk(); break;
    case 7: pineCrown(224, 651, 103, -1); break;
    case 8: pineCrown(209, 594, 100, 1); break;
    case 9: pineCrown(252, 545, 75, -1); break;
    case 10: pineCrown(278, 500, 58, 1); break;
    case 11: finishingMarks(); noLoop(); break;
  }
}

function paperSurface() {
  background(SUMI.paper);
  const paper = createGraphics(width, height);
  paper.pixelDensity(1);
  paper.clear();
  // 控えめな繊維。粒を濃くしすぎず、墨の階調を残す。
  for (let i = 0; i < 16000; i++) {
    const x = random(width), y = random(height);
    paper.stroke(112, 99, 75, random(3, 12));
    paper.strokeWeight(random(0.3, 0.8));
    paper.line(x, y, x + random(-1, 1), y + random(0.5, 2.8));
  }
  image(paper, 0, 0);
  paper.remove();
}

function wash(points, shade, opacity, bleed = 0.16) {
  brush.noStroke();
  brush.fill(shade, opacity);
  brush.fillBleed(bleed, 'out');
  brush.fillTexture(0.65, 0.22);
  brush.polygon(points);
  brush.noFill();
}

function inkPath(points, weight, shade = SUMI.ink, tool = '2B') {
  brush.noFill();
  brush.set(tool, shade, weight);
  // 第3要素は筆圧。線の入り・中腹・抜きを変化させる。
  brush.spline(points, 0.65);
}

function mountain(anchors, bottom, opacity, layer) {
  const ridge = [];
  for (let i = 0; i < anchors.length - 1; i++) {
    const a = anchors[i], b = anchors[i + 1];
    for (let j = 0; j < 7; j++) {
      const t = j / 7;
      ridge.push([lerp(a[0], b[0], t), lerp(a[1], b[1], t)
        + (noise(i * 2 + t * 3, layer * 11) - 0.5) * 19]);
    }
  }
  ridge.push(anchors[anchors.length - 1]);
  // 裾を明るく残すため、全体の淡墨と上部の濃墨を分ける。
  wash([...ridge, [ridge.at(-1)[0], bottom], [ridge[0][0], bottom]],
    SUMI.pale, opacity, 0.24);
  for (let pass = 0; pass < 2; pass++) {
    const lowerEdge = ridge.map(([x, y]) =>
      [x, lerp(y, bottom, 0.40 + pass * 0.20)]).reverse();
    wash([...ridge, ...lowerEdge], SUMI.ink, opacity * 0.48, 0.12);
  }
  // 皴法を思わせる、稜線から斜めに落ちる短い乾筆。
  const rockColor = ['#b6b9ad', '#969f94', '#7c887c'][layer];
  for (let i = 3; i < ridge.length - 3; i += 3) {
    const [x, y] = ridge[i];
    const length = min(random(26, 83), (bottom - y) * 0.60);
    inkPath([[x,y+6,0.15], [x-7,y+length*0.35,0.7],
      [x+random(-15,3),y+length,0.08]], random(0.7,1.5), rockColor, 'charcoal');
  }
}

function waterAndBoat() {
  // 水面を埋めず、わずかな水平線で奥行きを示す。
  for (let i = 0; i < 18; i++) {
    const y = random(540, 742), x = random(300, 606);
    const length = random(12, 53);
    inkPath([[x,y,0.1],[x+length*0.5,y-0.8,0.45],
      [x+length,y,0.05]], 0.7, '#b4b7a9', 'HB');
  }
  wash([[450,619],[495,619],[484,627],[461,627]], SUMI.ink, 165, 0.07);
  inkPath([[449,618,0.2],[472,623,1],[497,617,0.1]], 1.6);
  inkPath([[472,618,0.7],[474,607,1],[480,610,0.15]], 1.4);
  brush.noStroke();
  brush.fill(SUMI.ink, 170);
  brush.circle(475, 604, 2.6);
  brush.noFill();
  inkPath([[477,611,0.3],[500,635,0.08]], 0.9);
  inkPath([[454,636,0.08],[480,637,0.3],[505,635,0.05]], 0.7, '#8e988c');
}

function foregroundRock() {
  const rock = [[45,820],[70,792],[110,784],[148,754],
    [182,773],[218,797],[253,808],[282,845],[303,866],[45,866]];
  wash(rock, '#414b42', 76, 0.17);
  wash([[69,811],[117,793],[148,765],[155,811],[233,842],
    [259,863],[54,863]], '#29352e', 73, 0.13);
  for (let i = 0; i < 36; i++) {
    const x = random(66, 240), y = random(817, 855);
    inkPath([[x,y,0.15],[x-7,y+random(4,12),0.9],
      [x+random(4,17),y+random(12,20),0.1]], random(1,2.4), '#596252', 'charcoal');
  }
}

function pineTrunk() {
  // 扁平な一本線にせず、濃淡の異なる幹を少しずらして重ねる。
  const trunk = [[126,810,1.4],[156,747,1.25],[186,676,1],
    [199,614,0.85],[241,555,0.65],[266,519,0.4],[286,481,0.06]];
  inkPath(trunk, 12, '#646b59', 'charcoal');
  inkPath(trunk.map(([x,y,p])=>[x-3,y,p]), 5.5, '#303a30', '2B');
  inkPath(trunk.map(([x,y,p])=>[x+4,y,p*0.5]), 2, '#a4a58c', 'HB');
  inkPath([[163,735,0.8],[130,690,0.5],[95,672,0.05]], 4, '#465140');
  inkPath([[195,665,0.8],[238,642,0.5],[286,645,0.04]], 3.6);
}

function pineCrown(x, y, spread, side) {
  const tipX = x + side * spread;
  inkPath([[x-12,y+36,0.8],[x,y+14,0.7],
    [lerp(x,tipX,0.6),y+5,0.4],[tipX,y-6,0.03]], 3.6);
  for (let i = 0; i < 7; i++) {
    const t = i / 6;
    const cx = lerp(x, tipX, t) + random(-7,7);
    const cy = y - sin(t*PI)*14 + random(-5,5);
    wash([[cx-22,cy+6],[cx-13,cy-9],[cx+5,cy-15],
      [cx+24,cy-1],[cx+18,cy+9]], '#394638', 46, 0.12);
    for (let j = 0; j < 10; j++) {
      const a = random(PI*1.06, PI*1.94);
      const length = random(11,28);
      inkPath([[cx,cy+8,0.6],
        [cx+cos(a)*length*0.6,cy+sin(a)*length*0.6,0.9],
        [cx+cos(a)*length,cy+sin(a)*length,0.03]],
        random(0.75,1.3), random(['#303d30','#44513e','#616b50']), 'HB');
    }
  }
}

function finishingMarks() {
  // ごく小さい遠鳥。右上の余白を残す。
  for (const [x,y,size] of [[517,180,7],[541,171,5],[558,184,4]]) {
    inkPath([[x-size,y,0.1],[x,y+3,0.7],[x+size,y-1,0.05]], 0.85, '#777f73');
  }
  // 架空の朱印。文字フォントに依存しない線刻模様。
  wash([[612,805],[635,806],[634,835],[612,833]], SUMI.seal, 190, 0.08);
  for (const points of [
    [[617,811,1],[629,811,1],[629,820,1]],
    [[617,815,1],[617,829,1],[629,829,1]],
    [[622,811,1],[622,826,1]],
    [[617,822,1],[630,822,1]]
  ]) inkPath(points, 1.4, SUMI.paper, 'HB');
}
