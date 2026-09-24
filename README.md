# Edit:RiN

**Edit:RiN** is a mobile creative coding environment and p5.js editor designed for Android. Create, sketch, and experiment with generative art anywhere, directly on your device.

[日本語の案内はこちら](#日本語) · [Download](https://github.com/rin-code-dev/Edit-RiN/releases) · [Changelog](CHANGELOG.md) · [Issues](https://github.com/rin-code-dev/Edit-RiN/issues)

---

## Features

- **Live Code Editing & Preview**: Interactive p5.js canvas with split-view and fullscreen mode.
- **Live Parameters**: Tweak variables on the fly with dynamic sliders and color pickers generated directly from code comments.
- **Multiple p5.js Runtimes**: Switch between **p5.js 2.3.3** (modern, async setup) and **p5.js 1.11.5** (classic compatibility) per work.
- **Audio & Sound**: Built-in support for `p5.sound` for audio synthesis, playback, and FFT analysis.
- **Work & Asset Management**:
  - Store multiple works with revision history and ZIP backup/export.
  - Per-work asset support (images, audio, video, fonts, JSON/CSV) with instant loading-code insertion.
  - Single-work ZIP export and import for easy sharing.
  - Import public sketches directly from your p5.js Web Editor account.
- **Capture & Export**: Record animations (video/GIF) and capture high-resolution screenshots.
- **Customizable Environment**: Custom editor themes, fonts (TTF/OTF/TTC), ligature support, and canvas orientation toggle.
- **Multilingual Support**: Fully localized in English, Japanese (日本語), and Simplified Chinese (简体中文).
- **Privacy First**: Fully offline-capable, zero ads, no trackers or analytics.

---

## Live Parameters

Add `@rin` annotations to your JavaScript code to automatically create interactive controls in the preview panel. Values update in real time via the `rinParams` object and are saved with your sketch:

```javascript
// @rin number speed "Speed" 0 3 1 0.1
// @rin number strokeSize "Stroke size" 1 16 4 1
// @rin color ink "Color" #BA90E2

function setup() {
  createCanvas(400, 400);
}

function draw() {
  background(20);
  stroke(rinParams.ink);
  strokeWeight(rinParams.strokeSize);
  circle(width / 2, height / 2, 80 + sin(frameCount * 0.02 * rinParams.speed) * 40);
}
```

---

## Installation & Requirements

- **Supported OS**: Android 6.0 (API level 23) or later.
- **Download**: Get the latest signed APK from [GitHub Releases](https://github.com/rin-code-dev/Edit-RiN/releases).

---

## Building from Source

### Prerequisites
- JDK 25 (Java toolchain)
- Android SDK Platform 35
- Android SDK Build Tools 36.0.0
- Gradle 9.5.0 (via included wrapper)

### Build Commands
```sh
cd android
./gradlew assembleDebug
```

For signed release builds and configuration, refer to [RELEASE.md](RELEASE.md).

---

## Documentation

- [Asset Guide (素材ガイド)](ASSETS.md): Managing images, audio, fonts, and data files.
- [p5.js Runtime & Sound](P5_RUNTIME_AND_SOUND.md): Runtime versions and audio setup.
- [Language Support](LANGUAGE_SUPPORT.md): Localization details and supported languages.
- [Changelog](CHANGELOG.md): Release history and notes.
- [Contributing](CONTRIBUTING.md): Issues, feedback, and contribution guidelines.
- [Privacy Policy](PRIVACY.md): Data privacy and network behavior.
- [Third-Party Notices](THIRD_PARTY_LICENSES.md): Open-source licenses and acknowledgments.

---

## 日本語

**Edit:RiN** は、Android 端末で手軽に p5.js によるジェネラティブアートやクリエイティブ・コーディングを楽しめるエディタアプリです。

### 主な機能
- **ライブプレビュー**: コードを書きながらその場で動作確認。全画面表示、録画（MP4/GIF）、高解像度スクリーンショット撮影に対応。
- **ライブパラメータ**: コード内に `// @rin number ...` や `// @rin color ...` のように注釈を書くだけで、スライダーやカラーピッカーが自動生成され、リアルタイムに数値を調整可能。
- **ランタイム切り替え**: 作品ごとに `p5.js 2.3.3` と `1.11.5` を選択可能。`p5.sound` によるサウンドの再生・合成・解析にも対応。
- **素材（アセット）管理**: 画像・音声・フォント・JSON などを作品内に取り込み、ワンタップで読み込みコードを挿入。単一作品の ZIP 書き出し・取り込みによる共有も可能。
- **p5.js Web Editor 連携**: ユーザー名を入力するだけで公開作品を直接インポート。
- **プライバシー重視**: 完全オフライン動作。広告やトラッキングは一切ありません。

詳しい使い方については各ドキュメント（[素材ガイド](ASSETS.md) / [実行環境とサウンド](P5_RUNTIME_AND_SOUND.md) など）をご覧ください。

---

## Support & Tips

If you enjoy Edit:RiN and would like to support its ongoing development, optional tips are welcome on [OFUSE](https://ofuse.me/rincode).

---

## License

Modification, reuse, or redistribution requires prior contact, permission, and attribution to rin-code-dev. See [LICENSE](LICENSE) for details.

© 2026 rin-code-dev · Made with p5.js and Jetpack Compose.
