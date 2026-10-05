# p5.js Runtime & Sound / 実行環境とサウンド

作品メニュー → **実行環境**（または作品設定）から、作品ごとに p5.js のバージョンや `p5.sound` の利用有無を設定できます。

---

## 実行環境の選択

- **p5.js 2.3.4**: 新規作成時のデフォルト環境です。モダンな JavaScript 構文や非同期読み込み（`async/await`）に対応しています。
- **p5.js 1.11.5**: 従来の p5.js スケッチや既存資産との互換性のための環境です。
- **p5.sound**: 音声の再生・合成・FFT 解析を行うためのライブラリです（初期状態はオフ）。p5.js 2.xでは公式0.4.1、1.11.5では同版に同梱された従来版を読み込みます。
  - 有効にすると、プレビュー画面をタップ（または操作）したタイミングで Web Audio コンテキストが開始されます。

---

## サウンドの利用コード例

音声ファイルは作品メニューの **作品の素材** から追加し、パスには `assets/ファイル名` を指定します。

### p5.js 1.x での利用例 (`preload`)

```javascript
let sound;

function preload() {
  sound = loadSound('assets/music.mp3');
}

function setup() {
  createCanvas(400, 400);
}

function mousePressed() {
  if (!sound.isPlaying()) {
    sound.play();
  }
}
```

### p5.js 2.x での利用例 (`async setup`)

```javascript
let sound;

async function setup() {
  createCanvas(400, 400);
  sound = await loadSound('assets/music.mp3');
}

function mousePressed() {
  if (sound && !sound.isPlaying()) {
    sound.play();
  }
}
```

> [!NOTE]
> `p5.sound 0.4.1` は従来版とAPIが異なります。`p5.Part`, `p5.Phrase`, `p5.PolySynth`, `p5.SoundRecorder`, `saveSound()` などを使う従来作品は、p5.js 1.11.5を選んでください。

---

## English

### Selecting the Runtime
Open the work menu → **Runtime** (or Work Settings) to configure p5.js settings per sketch:

- **p5.js 2.3.4**: Modern standard runtime supporting async/await and contemporary JavaScript APIs.
- **p5.js 1.11.5**: Legacy compatibility mode for standard 1.x sketches.
- **p5.sound**: Audio playback, synthesis, and analysis (disabled by default). The 2.x runtime uses official 0.4.1; the 1.11.5 runtime uses its bundled legacy addon, including legacy sequencing and sound recording APIs. Audio starts upon first user interaction with the preview.

Audio files should be placed in **Work assets** and referenced using `assets/filename.ext`. Use `preload()` in p5.js 1.x or `async setup()` with `await loadSound()` in p5.js 2.x.


For HTML projects, select core and sound scripts in your HTML. / HTML作品では、p5.jsとp5.soundをHTMLのscriptタグで指定します。

See [p5.js Compatibility / 互換機能](P5_COMPATIBILITY.md) for camera, microphone, saving, project files, and modules.
