# 作品の素材 / Work Assets

作品メニュー → **作品の素材** から、作品で使用する画像・音声・フォント・データなどを管理できます。

---

## 素材の追加と基本操作

- **素材の追加**: **素材を追加** をタップして端末内のファイルを選択します（画像、GIF、音声、動画、フォント、JSON、CSV、テキスト、3Dモデル、シェーダー等に対応）。複数選択も可能です。
- **プレビューとコード挿入**: 一覧で素材をタップするとプレビューを確認でき、**読み込みコードを挿入** でエディタのカーソル位置に該当アセットの読み込みコードを自動挿入できます。
- **パスのコピー**: **パスをコピー** を押すと、`assets/ファイル名` の形式でクリップボードにコピーされます。
- **容量と制限**:
  - 1ファイルあたり最大 **50MB**
  - 1作品あたり合計 **200MB / 最大100ファイル**
  - 全体バックアップ（ZIP）展開時の上限は **512MB**

---

## 作品の共有（単一作品 ZIP）

作品メニューの **作品ZIPを書き出す** を使うと、JavaScriptコード、追加した素材、p5.js設定をまとめた単一のZIPファイルを出力できます。
受け取った側は作品メニューの **作品ZIPを追加** から取り込むことで、既存の作品や設定に影響を与えることなく新しい作品として追加できます。

---

## コード例

### 画像 (Image)

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

### フォント (Font)

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

### データ (JSON)

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

### 音声 (Audio)

`createAudio()` を使用する例（画面タップで再生）：

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

> [!NOTE]
> `loadSound()` や `p5.FFT` を使用する場合は、作品設定で **p5.sound** を有効にしてください。

### 動画 (Video)

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

---

## English

### Managing Work Assets
Open the work menu → **Work assets** → **Add assets** to select files from your device. Edit:RiN supports images, audio, video, custom fonts, JSON/CSV data, 3D models, shaders, and more.

- **Insert Loading Code**: Tap an asset to open its preview, then tap **Insert loading code** to automatically insert loading code at your editor cursor.
- **Copy Path**: Tap **Copy path** to copy the relative path (`assets/filename.ext`) to your clipboard.
- **Limits**: Up to 50 MB per file, and up to 200 MB / 100 files per work.

### Sharing Individual Works
Use **Export work ZIP** in the work menu to export the current sketch, including its code, assets, and settings. Another user can use **Import work ZIP** to import it as a new work without modifying existing sketches.

---

## 中文

### 作品素材管理
打开作品菜单 → **作品素材** → **添加素材**，选择设备中的文件。支持图片、音频、视频、字体、JSON/CSV数据、3D模型及着色器等。

- **插入加载代码**：点击素材即可预览，点击 **插入加载代码** 可在编辑器光标处自动插入加载代码。
- **复制路径**：点击 **复制路径** 可复制 `assets/文件名` 格式的相对路径。
- **限制**：单文件最大 50 MB，单作品上限 200 MB / 100 个文件。

### 单个作品分享
使用作品菜单中的 **导出作品 ZIP**，可将当前作品（包含代码、素材及设置）打包分享。通过 **导入作品 ZIP** 可将其添加为新作品，不影响已有内容。
