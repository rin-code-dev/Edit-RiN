# p5.js runtime and sound

作品画面のメニューから「実行環境」を開き、作品ごとに設定します。

- `p5.js 2.3.3`: 新しい作品の標準です。
- `p5.js 1.11.5`: 以前の作品との互換用です。
- `p5.sound`: 音声再生、音の合成、FFTなどの解析を使う場合にオンにします。

p5.soundをオンにした作品では、実行後にプレビューの「タップして音声を開始」を押してください。Androidの自動再生制限に合わせて、この操作で音声処理を開始します。

音声ファイルは「作品の素材」から追加し、コードでは`assets/ファイル名`を指定します。

```js
let sound;

function preload() {
  sound = loadSound('assets/music.mp3');
}

function setup() {
  createCanvas(400, 400);
}

function mousePressed() {
  if (!sound.isPlaying()) sound.play();
}
```

`works.json`の保存形式はversion 6です。より新しい正のversion番号を持つファイルも、ver1.0.1が理解できるフィールドを読み込みます。
