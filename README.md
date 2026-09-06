# Edit:KIRO

**Write code. Make art. Wherever inspiration finds you.**  
**ひらめいた場所を、アトリエに。**

A p5.js creative-coding editor for Android. Write JavaScript, see your artwork come alive, and keep experimenting—all on your phone.

Androidで、コードを書いてアートをつくる。Edit:KIROは、編集とプレビューをひとつの画面で行えるp5.js向けクリエイティブコーディングアプリです。

Created by **rin-code-dev** · Source available under the [Edit:KIRO License](LICENSE)

[English](#english) · [日本語](#日本語)

**1.0.0-beta.3** — Android beta release / Androidベータ版  
Release instructions: [RELEASE.md](RELEASE.md) · [Privacy / プライバシー](PRIVACY.md) · [Changes / 変更点](BETA_NOTES.md) · [Contributing / フィードバック](CONTRIBUTING.md)

---

## English

### Your pocket-sized creative workspace

Edit:KIRO brings a code editor and a live preview together in a minimal interface. Explore generative art, sketch an animation, or refine an idea in portrait or landscape.

The p5.js runtime is bundled with the app, so sketches that use only local resources can run offline. Sketches that load external resources may need an internet connection.

### Features

- **Edit and run:** JavaScript syntax highlighting, auto-indent, undo/redo, find and replace, and jump to line.
- **Made for touch:** An editor toolbar with navigation, Tab, and symbol keys. Line numbers remain separate from selectable code, including wrapped lines.
- **A flexible preview:** Per-work aspect ratios, including device aspect ratio, adjustable workspace proportions, and fullscreen preview.
- **Two canvas approaches:** New works can use fixed canvas dimensions or responsive sizing. Ordinary preview resizing scales the presentation without rewriting your canvas dimensions.
- **Capture your work:** Canvas screenshots and video recording, with controls tucked into an expandable menu.
- **Keep creating:** Work management, revision history, draft recovery, and backup import/export.
- **Make it yours:** Dark, light, and system theme options; custom font import; ligature controls; and individually configurable editor features.
- **Multiple languages:** Japanese, English, Simplified Chinese, and system default. Unsupported system languages fall back to English.

### Start creating

1. Create a new work and choose its canvas aspect ratio and sizing mode. Device aspect ratio uses responsive sizing.
2. Start with the simple circle template, or replace it with your own p5.js code. Bundled works include Axis, Halo and Gravity (1:1), plus dvd (4:3).
3. Edit the source and run it. Enable automatic execution if it suits your workflow.
4. Open the preview menu to enter fullscreen, take a screenshot, or record the canvas.
5. Save your work and export backups regularly.

Fresh installs use the dark theme. Landscape cutout usage and ligatures are off; other settings switches are on. Existing saved works and preferences are preserved when upgrading.

### Canvas behavior

**Fixed size** is useful when you want a consistent composition: a canvas created with `createCanvas(800, 800)` keeps that drawing size as you adjust the workspace. The app fits the canvas into the available preview area.

**Responsive size** lets your sketch respond to its viewport through `windowWidth`, `windowHeight`, and `windowResized()`. The generated responsive template includes a `resizeCanvas()` handler.

In fullscreen, the orientation button exchanges canvas width and height—for example, 960 × 540 becomes 540 × 960. Closing fullscreen restores the previous dimensions without changing the saved source or work aspect ratio. Sketches with hard-coded coordinates or custom WebGL cameras may need their own resize handling. Stop recording before switching orientation.

### Custom fonts

Import a **TTF, OTF, or TTC** file, up to **20 MB**, from the appearance settings. The font applies to the app interface and editor, not to the artwork or system keyboard. You can return to the default font at any time.

Ligatures affect appearance only; the underlying code and copied text remain unchanged. The selected font must support the relevant ligatures. Imported fonts are stored locally and are not included in the app's work/settings ZIP backups—reimport them on another installation.

### Requirements and limitations

- Android 6.0 / API 23 or later is the configured minimum. Performance and feature availability depend on the device and Android WebView.
- Recording requires WebView support for canvas capture and MediaRecorder, and stops automatically after 60 seconds.
- Screenshots and recordings capture the canvas, not the full app interface. External resources can restrict capture.
- This is a p5.js sketch environment, not a full desktop IDE or a Node.js runtime.
- Run only code and external resources you trust.

### Build from source

Use **JDK 17** and **Android SDK Platform 35**. Open the `android` directory in Android Studio, or use the included Gradle wrapper:

```sh
cd android
./gradlew testDebugUnitTest assembleDebug lintDebug
```

Configure your Android SDK location in Android Studio, `local.properties`, or `ANDROID_HOME`. The first build needs access to the configured dependency repositories; add `--offline` only when the dependencies are already cached.

The debug APK is generated at `android/app/build/outputs/apk/debug/app-debug.apk`, relative to the project root. Web assets are copied automatically during the build; no npm installation is required for this workflow.

Run the JavaScript runner tests from the project root with Node.js:

```sh
node tests/runner.test.cjs
```

For a signed, optimized beta APK, copy `android/release-signing.properties.example` to `android/release-signing.properties`, supply your private signing details, then run `./gradlew testReleaseUnitTest assembleRelease lintRelease` from `android`. See [RELEASE.md](RELEASE.md). Never publish the signing file or private key.

### Feedback

Please use [GitHub Issues](../../issues) for bug reports and feature requests. Include your device, Android and WebView versions, app version, steps to reproduce, and a small sketch that demonstrates the issue. Screenshots or a short screen recording are helpful. Remove personal information before sharing.

### Project licensing and credits

Copyright © 2026 rin-code-dev. The source is available for review under the [Edit:KIRO Source-Available License](LICENSE). Modification, reuse, or distribution requires prior contact through GitHub Issues, explicit written permission from rin-code-dev, and visible attribution to rin-code-dev.

Edit:KIRO uses p5.js and Android Jetpack Compose. All interface and launcher icons are original artwork created for Edit:KIRO. See [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) for notices, bundled license texts, and the corresponding p5.js source.

---

## 日本語

### ポケットの中の制作環境

Edit:KIROは、コードエディターとライブプレビューをミニマルな画面にまとめたAndroidアプリです。ジェネラティブアートを試す、アニメーションを描く、思いついた表現を磨く。縦画面でも横画面でも、スマートフォンで制作を続けられます。

p5.jsの実行環境を同梱しているため、ローカルの素材だけを使う作品はオフラインでも実行できます。外部の画像やデータなどを読み込む作品には、インターネット接続が必要になる場合があります。

### 主な機能

- **コードの編集と実行：** JavaScriptのシンタックスハイライト、自動インデント、元に戻す・やり直す、検索・置換、指定行への移動。
- **タッチ操作に合わせた編集：** カーソル移動、Tab、記号入力の操作パネル。行番号はコードとは独立したUIで、折り返し行にも対応。
- **調整できるプレビュー：** 端末比率を含む作品ごとのアスペクト比率、作業領域の割合調整、全画面表示。
- **2種類の描画サイズ：** 新規作成時に固定サイズとレスポンシブを選択。通常のプレビューサイズ変更では、キャンバスの描画サイズを書き換えずに表示を拡大・縮小。
- **作品のキャプチャ：** キャンバスのスクリーンショットと動画録画。操作ボタンは展開式メニューに集約。
- **制作を続けるための機能：** 作品管理、変更履歴、下書き復元、バックアップの書き出し・読み込み。
- **好みに合わせた設定：** ダーク・ライト・システムテーマ、フォントのインポート、リガチャ切替、エディター機能ごとのオン・オフ。
- **多言語対応：** 日本語、英語、簡体字中国語、システムデフォルト。未対応のシステム言語では英語を使用。

### はじめかた

1. 作品を新規作成し、描画比率とサイズの扱いを選びます。端末比率ではレスポンシブを使用します。
2. シンプルな円のテンプレートから始めるか、自分のp5.jsコードに書き換えます。同梱作品はAxis・Halo・Gravity（1:1）とdvd（4:3）です。
3. コードを編集して実行します。必要に応じて自動実行を有効にできます。
4. プレビューの操作メニューから、全画面表示・スクリーンショット・録画を利用できます。
5. 作品を保存し、大切な作品は定期的にバックアップを書き出してください。

新規インストール時はダークテーマです。「横画面でノッチ部分まで利用」と「リガチャ」のみOFF、その他の設定スイッチはONです。更新時には既存の保存作品・設定を保持します。

### 描画サイズについて

**固定サイズ**は、作品の構図を統一したい場合に適しています。たとえば`createCanvas(800, 800)`で作成したキャンバスは、作業領域の割合を変更しても800 × 800の描画サイズを維持し、プレビュー枠に収まるように表示されます。

**レスポンシブ**は、`windowWidth`、`windowHeight`、`windowResized()`を使って、作品側で表示領域の変化に対応する方式です。レスポンシブのテンプレートには、`resizeCanvas()`を呼び出す処理が含まれます。

全画面表示中の縦横切替ボタンでは、キャンバスの幅と高さを入れ替えます。たとえば960 × 540は540 × 960になります。全画面を閉じると元のサイズに戻り、保存済みのコードや作品の比率は変更しません。固定座標や独自のWebGLカメラを使う作品には、作品側でのリサイズ対応が必要になる場合があります。録画中は停止してから切り替えてください。

### フォントのカスタマイズ

外観設定から、**20 MB以下のTTF・OTF・TTC**をインポートできます。アプリのUIとエディターに反映され、いつでも標準フォントに戻せます。作品内の文字や端末のキーボードのフォントは変更しません。

リガチャは、対応フォントでのみ表示に反映されます。コードそのものやコピーした文字列は変わりません。取り込んだフォントは端末内に保存されますが、作品・設定のZIPバックアップには含まれないため、別の環境では再インポートしてください。

### 動作環境と制限

- 設定上の最低対応環境はAndroid 6.0 / API 23です。動作速度や利用できる機能は、端末とAndroid WebViewによって異なります。
- 録画にはWebViewのキャンバスキャプチャとMediaRecorderへの対応が必要です。録画は60秒で自動停止します。
- スクリーンショットと録画の対象はキャンバスです。アプリ全体は含まれません。外部素材の読み込みによってキャプチャが制限される場合があります。
- p5.jsの作品を編集・実行する環境であり、デスクトップIDEやNode.jsの実行環境ではありません。
- 信頼できるコードと外部素材のみを実行してください。

### ソースコードからのビルド

**JDK 17**と**Android SDK Platform 35**を用意し、Android Studioで`android`フォルダーを開いてください。付属のGradle Wrapperからもビルドできます。

```sh
cd android
./gradlew testDebugUnitTest assembleDebug lintDebug
```

Android SDKの場所は、Android Studio、`local.properties`、または`ANDROID_HOME`で指定します。初回は依存関係を取得するためのネットワーク接続が必要です。取得済みの場合のみ、`--offline`を追加できます。

デバッグAPKは、プロジェクトのルートから見て`android/app/build/outputs/apk/debug/app-debug.apk`に生成されます。Webアセットはビルド時に自動コピーされるため、この手順にnpmのインストール作業は不要です。

JavaScript実行部分のテストは、Node.jsを使ってプロジェクトのルートで実行します。

```sh
node tests/runner.test.cjs
```

署名・最適化済みのベータAPKを作る場合は、`android/release-signing.properties.example`を`android/release-signing.properties`へコピーし、自分の署名情報を設定します。続いて`android`内で`./gradlew testReleaseUnitTest assembleRelease lintRelease`を実行してください。詳しくは[RELEASE.md](RELEASE.md)を参照してください。署名設定と秘密鍵は公開しないでください。

### 不具合報告・フィードバック

不具合報告や機能の提案は[GitHub Issues](../../issues)へお願いします。端末名、Android・WebView・アプリのバージョン、再現手順、問題を再現できる短いコードを添えてください。スクリーンショットや短い録画も役立ちます。共有前に個人情報が含まれていないことを確認してください。

### ライセンスとクレジット

Copyright © 2026 rin-code-dev. ソースコードは[Edit:KIRO Source-Available License](LICENSE)で公開します。ソースコードまたはその一部を改変・転用・再配布する場合は、GitHub Issuesでの事前連絡、rin-code-devからの明示的な許可、見える場所へのrin-code-devの名前表記が必要です。

Edit:KIROはp5.jsとAndroid Jetpack Composeを使用しています。UIおよびランチャーアイコンは、すべてEdit:KIROのために制作したオリジナルです。ライセンス原文とp5.js対応ソースを同梱しています。詳細は[THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)を参照してください。
