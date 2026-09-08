# 作品の素材

作品メニュー → **作品の素材** → **素材を追加** で、端末のファイルを選択します。
画像・GIF・音声・動画・フォント・JSON・CSV・テキスト・3Dモデル・シェーダーなどを
作品にまとめられます。複数選択に対応し、同じファイル名は `photo-2.png` のように別名で追加します。

一覧の **パスをコピー** で、コードに貼り付けるパスをコピーできます。
名前を変更した場合はコード内のパスも変更してください。

素材は1ファイル50MBまで、1作品あたり200MB・100ファイルまでです。
保存先を選んでいない場合も端末内に保存します。作品を複製すると素材も引き継ぎます。
設定のZIPバックアップには素材も含まれます（展開後の合計512MBまで）。
外部の作品フォルダーを移す場合は `works.json` と `assets` フォルダーを一緒に移してください。
デバッグ版は独立したアプリです。既存アプリの作品はZIPバックアップを復元して取り込めます。
旧形式のバックアップも復元できます。素材を含む形式は、この版以降で開いてください。

## 画像

`photo.png` を追加して実行します。

```javascript
let photo;
function preload() {
  photo = loadImage('assets/photo.png');
}
function setup() {
  createCanvas(400, 400);
  image(photo, 0, 0, width, height);
}
```

## フォント

```javascript
let font;
function preload() {
  font = loadFont('assets/MyFont.ttf');
}
function setup() {
  createCanvas(400, 400);
  background(25);
  fill(255);
  textFont(font);
  textSize(40);
  text('Hello!', 30, 100);
}
```

## データ

```javascript
let data;
function preload() {
  data = loadJSON('assets/data.json');
}
function setup() {
  createCanvas(400, 400);
  print(data);
}
```

## 音声

p5.js本体の `createAudio()` を使う例です。画面をタップすると再生します。
`loadSound()` や `p5.FFT` を使うには別途p5.soundが必要です。

```javascript
let music;
function setup() {
  createCanvas(400, 400);
  background(30);
  music = createAudio('assets/music.mp3');
  music.hide();
}
function mousePressed() {
  music.play();
}
```

## 動画

```javascript
let movie;
function setup() {
  createCanvas(400, 400);
  movie = createVideo('assets/movie.mp4');
  movie.hide();
}
function mousePressed() {
  movie.loop();
}
function draw() {
  background(0);
  image(movie, 0, 0, width, height);
}
```

## English

Open the work menu → **Work assets** → **Add assets**. Use **Copy path** to insert
an `assets/name.ext` path into your sketch. Renaming an asset also requires updating
your code. Assets are included when duplicating works and exporting a ZIP backup.
The debug app is named **Edit:KIRO Dev** and uses a separate application ID.

## 中文

打开作品菜单 → **作品素材** → **添加素材**。点击 **复制路径**，在代码中使用
`assets/文件名`。重命名素材后，请同时更新代码中的路径。复制作品和导出 ZIP 备份时
会包含素材。调试版的应用名称为 **Edit:KIRO Dev**，可单独安装。
