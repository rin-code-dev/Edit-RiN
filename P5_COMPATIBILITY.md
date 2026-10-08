# p5.js compatibility / p5.js互換機能

## English

The Android preview runs the bundled upstream p5.js **2.3.4** or **1.11.5** in WebView. Existing 2.3.3 work settings select 2.3.4. Processing/Java is not included.

- `createCapture()` and microphone input request Android camera/audio permissions when used. Denial remains a normal browser permission error.
- `createFileInput()` opens the Android file picker, including multiple selection when requested by the input.
- Standard p5 downloads and HTML download links save through Android. This includes `saveCanvas`, `saveJSON`, `saveStrings`, `saveTable`, `saveFrames`, `saveGif`, and legacy `saveSound` when their upstream implementation is available. Output format support follows p5.js and WebView. Files go to `Download/Edit-RiN`; Android 6–9 can use a destination picker if storage permission is denied.
- `fullscreen(true)` uses the native fullscreen view. Browser rules still require user interaction.
- Global and instance sketches, multiple canvases, HTML/CSS pages, and ES modules are supported. Pause/resume controls p5 instances, HTML animation frames, and CSS animations. Each instance's original `noLoop()` state is retained. Plain timers and unrelated asynchronous tasks continue normally.
- PNG capture and video/GIF recording combine multiple visible canvases. Canvas export cannot include arbitrary DOM elements. HTML controls/CSS retain their authored layout.
- The official p5.js 2.3.4 WebGPU addon is bundled. `WEBGPU` needs support from the device GPU and installed Android WebView.
- Device orientation and motion sensors (`rotationX`/`Y`/`Z`, `accelerationX`/`Y`/`Z`, `deviceMoved()`, `deviceTurned()`, and `deviceShaken()`) are supported via high-sampling native Android bridge and standard Web APIs. Sensors activate on-demand when used by the sketch to conserve battery.
- HTTPS links opened by a user go to the system browser; links to another HTML file in the work stay in the preview.

### Project files and loading

Use **Work menu → Project files** for HTML, CSS, JavaScript, modules, JSON, shaders, and other text files. Names can include folders, such as `lib/math.mjs` or `pages/index.html`; relative imports and asset paths keep that structure. Imports from p5.js Web Editor preserve its project files and folders.

Use **Runtime → Project loading settings** to select automatic, classic JavaScript, module, or HTML execution. Automatic mode prefers `index.html`; main code with static `import`/`export` is a module. A sole `.mjs` is selected automatically only when the main code is empty and there are no classic JS files. You can select an explicit entry file.

Classic JavaScript can use an explicit file order. An empty order keeps the earlier combined-source behavior. External HTTPS libraries can be classic or module scripts; classic scripts execute in registered order before the sketch. Module scripts follow browser module loading rules. A custom HTTPS p5.js URL is also available. Internet access is required for external URLs.

In **HTML mode**, script tags select the p5.js version, sound library, other libraries, and script order. Runtime core/library switches apply to JavaScript and module mode. Recognized exact bundled p5 CDN versions are redirected to local assets; other versions remain external. Author HTML with a Content Security Policy keeps its external URLs and policy. A policy that blocks the injected preview helper also limits preview controls and native downloads.

Loading settings are stored in `edit-rin.json`, included in work backups:

```json
{
  "executionMode": "module",
  "moduleEntry": "main.mjs",
  "libraries": [],
  "p5Url": null
}
```

A module entry can declare/export `setup`, `draw`, and input callbacks, or create `new p5(...)` instances. Imports need browser-resolvable URLs or a declared HTML import map; npm package names are not automatically installed or bundled.

### Sound and platform limits

p5.js 2.x uses official `p5.sound 0.4.1`. For the older sequencing, synthesis, `SoundRecorder`, and `saveSound` APIs, choose 1.11.5 with its matching legacy sound addon. Audio playback must be unlocked by a user action. Microphone input additionally requires Android permission.

Browser CORS, secure-context, media codec, and user-gesture restrictions still apply. WebGPU, hardware input, fullscreen, and file providers depend on the Android device/WebView. New project file sets and preview execution are bounded to 500 text files/16 MiB total; older stored works remain readable and portable in backups. Assets to 100 files/50 MiB each/200 MiB total. The `__edit-rin__/` folder is reserved for runtime helpers. Downloads are bounded to 256 MiB per file, with bounded queues; excessive batches report an error. These limits protect the editor process on a phone.

## 日本語

AndroidのWebViewで本家p5.js **2.3.4 / 1.11.5**を実行します。以前の2.3.3設定は2.3.4として読み込みます。Processing/Javaは内蔵していません。

- カメラ・マイク：作品が使用するときにAndroidの権限を要求します。
- `createFileInput()`：Androidのファイル選択画面を開きます。
- 標準保存関数・HTMLのダウンロードリンク：Androidへ保存します。通常は `Download/Edit-RiN`、Android 6〜9でストレージ権限がない場合は保存先を選択します。
- `fullscreen(true)`：Androidの全画面表示につなぎます。ブラウザーと同様にユーザー操作が必要です。
- インスタンスモード・複数キャンバス：停止・再開、合成PNG撮影、動画/GIF録画に対応します。HTML内のレイアウトは作品のCSSを維持します。撮影対象はキャンバスで、任意のDOM要素は含みません。
- HTML/CSS・ESモジュール・フォルダー付きの相対パス・任意のHTTPSライブラリに対応します。
- 公式WebGPUアドオンを内蔵します。動作にはGPUとWebViewの対応が必要です。
- 端末センサー（傾き・加速度）：`rotationX`/`Y`/`Z`、`accelerationX`/`Y`/`Z`、`deviceMoved()`、`deviceTurned()`、`deviceShaken()` に対応します。作品内でセンサーが使用されている場合にのみオンデマンドで高精度ネイティブ連携が作動し、バッテリーを保護します。

**作品メニュー → プロジェクトファイル**で `index.html`、`style.css`、`lib/math.mjs` などを追加・編集できます。p5.js Web Editorからの取り込みでもHTML/CSSやフォルダーを保持します。

**実行環境 → プロジェクトの読み込み設定**で実行方式、開始ファイル、通常JSの読み込み順、外部ライブラリ、p5.jsのHTTPS URLを指定します。自動モードは `index.html` を優先し、メインコードの静的 `import` / `export` をモジュールとして扱います。`.mjs` が1個の場合は、メインコードが空で通常JSがないときだけ自動選択します。設定は作品内の `edit-rin.json` に保存します。

HTML作品ではHTMLの `script` タグがバージョン・ライブラリ・読み込み順を決めます。JavaScript/モジュール作品では実行環境ダイアログの設定を使います。既知の内蔵版CDN URLはローカルへ接続しますが、独自版やCSP指定があるHTMLのURLは維持します。CSPが補助スクリプトを禁止する場合、プレビュー操作やAndroidへの保存も制限されます。

モジュールからは `setup` / `draw` / 入力コールバックの宣言・export、または `new p5(...)` を使えます。npmパッケージ名は自動解決されないため、URLやHTMLのimport mapを指定してください。

従来の `p5.Part`、`p5.Phrase`、`p5.PolySynth`、`SoundRecorder`、`saveSound` などは1.11.5と従来版soundを選びます。2.xでは公式sound 0.4.1のAPIを使います。

CORS、ユーザー操作、音声・動画コーデック、端末機能などのブラウザー制約は残ります。通常のタイマーは停止ボタンでは止まりません。容量・保存キューには上記の上限があります。カメラ、マイク、ファイル選択、全画面、WebGPUの最終確認には実機が必要です。
