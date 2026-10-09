# Changelog

## 2.3.1 — 2026-10-09

### English

- Fixed code-folding arrows that could stop responding after loading or editing code, including in read-only samples.
- Fixed line numbers and folding arrows wrapping or becoming misaligned with some fonts and font sizes.
- Added automatic gallery thumbnail generation after installation or an update. Saved works and samples are rendered in the background without switching the current work. Thumbnail generation does not play sound or request camera or microphone access; sketches that require network access or permissions may not produce a thumbnail.
- Kept newer thumbnails captured from the visible preview when background generation overlaps with editing or running a work.

### 日本語

- コードの読み込み後や編集中に、折りたたみの矢印をタップしても反応しないことがある問題を修正しました。閲覧専用のサンプルも対象です。
- フォントや文字サイズによって、行番号や折りたたみの矢印が折り返され、コードの行とずれる問題を修正しました。
- インストール後や更新後に、保存済みの作品とサンプルのサムネイルをバックグラウンドで生成するようにしました。開いている作品は切り替わりません。生成中は音を再生せず、カメラやマイクの許可も求めません。通信や権限が必要な作品では、サムネイルを生成できない場合があります。
- サムネイルの生成中に、表示中のプレビューから新しい画像が保存された場合は、その画像を優先するようにしました。


## 2.3.0 — 2026-10-08

### English

- Added in-app updates for the release app: download the latest GitHub APK with progress and cancellation, then open Android’s installation confirmation. Package, version, and signing checks run before installation; save pending edits first. The debug app continues to use the release page.
- Added Palette, Ripples, and Sensor samples, and redesigned the existing samples. Explore touch, live parameters, sound, camera input, and device motion; copy a read-only sample to edit it.
- Added native device orientation and motion support for tilt and acceleration values and `deviceShaken()` on compatible devices.
- Added a searchable p5.js reference with function signatures, explanations, examples, and code insertion from the work menu or editing toolbar.
- Improved JavaScript formatting with selection preservation and checks for unmatched brackets or unfinished strings. Restored code folding in read-only samples.
- Simplified starter templates and revised the in-app guide in English, Japanese, and Simplified Chinese. Bundled sample comments follow the app language; Japanese interface text is easier to read.
- Added the Sumi theme with charcoal, warm paper colors, and terracotta accents. Update popups, reference examples, tag dialogs, and recording countdowns now follow the selected theme.
- Fixed gallery display and scrolling issues when switching between sample and user-work folders.
- On the first launch after this update or a new installation, Palette opens and the app switches to the light theme with code wrapping off. Existing user works are kept. These options can be changed in Settings, and later launches keep your choices.
- Temporarily removed the share-card button. PNG export, MP4/GIF recording, and sharing saved images and recordings remain available.

### 日本語

- 正式版でアプリ内更新に対応しました。GitHubの最新版APKをアプリ内でダウンロードし、Androidの確認画面からインストールできます。進捗表示と中止に対応し、インストール前にアプリID・バージョン・署名情報を確認します。未保存の編集がある場合は、先に保存してください。デバッグ版では配布ページを開きます。
- Palette・Ripples・Sensorを追加し、既存のサンプルも作り直しました。タッチ・ライブパラメータ・音・カメラ・端末の動きを試せます。閲覧専用のサンプルは、コピーして編集できます。
- 対応端末の傾きや加速度を取得し、`deviceShaken()`で端末を振る操作を使えるようにしました。
- p5.jsリファレンスを追加しました。作品メニューや編集キーから開き、関数の引数・説明・使用例を確認して、コードを挿入できます。
- JavaScriptの整形後も選択範囲を引き継ぐようにしました。閉じていない括弧や文字列も確認できます。閲覧専用サンプルでコードを折りたためない問題を修正しました。
- 書き始めやすいテンプレートに整理し、使い方ガイドを英語・日本語・簡体字中国語で更新しました。サンプルのコメントはアプリの表示言語に合わせ、日本語の説明文も読みやすくしました。
- 墨色と生成りに朱色を合わせたSumiテーマを追加しました。更新ポップアップ、リファレンスのコード例、タグ管理画面、録画カウントダウンにも、選んだテーマの色が反映されます。
- サンプルと自分の作品のフォルダーを切り替えるときの、一覧表示やスクロールの不具合を修正しました。
- 今回の更新後や新規インストール後の初回は、Paletteを開き、ライトテーマ・コードの折り返しオフに切り替えます。自分の作品は残ります。設定から変更でき、その後の起動では選んだ設定を引き継ぎます。
- シェアカードのボタンを一時的に外しました。PNGの書き出し、MP4・GIFの録画、保存した画像や録画の共有は引き続き使えます。


## 2.2.2 — 2026-10-06

### English
- Dedicated Parameters button directly in the preview bar with a live count badge indicating active sketch parameters.
- Interactive error gutter that highlights erroneous lines and displays error details upon tap.
- Incremental syntax highlighting cache to eliminate typing flicker during background re-parsing.
- Compact preview control bar sizing and unified spring animations for panels and sheets.
- Android WebView video poster fix and offscreen 2D canvas `willReadFrequently` acceleration to eliminate GPU stalls.
- Refreshed settings icon to a standard gear design.
- Accurate search match navigation and restored selection offsets.

### 日本語
- プレビューバーにパラメータボタンを独立配置し、作品内のパラメータ数をリアルタイムにバッジ表示。
- 行番号（ガター）のエラー行強調表示およびタップ時のエラー詳細ダイアログ表示。
- 入力中の再解析時にも既存ハイライトを再利用するキャッシュにより、エディタ編集時のチラつきを解消。
- プレビュー操作バーのサイズ・余白をコンパクト化し、パネル開閉を滑らかなスプリングアニメーションに統一。
- Android WebViewでの動画ポスターエラーを修正し、オフスクリーン描画の `willReadFrequently` 最適化によりGPUストールを抑止。
- 設定アイコンを直感的に分かりやすい歯車アイコンへ刷新。
- 検索マッチ位置へのスクロール・選択範囲の復元精度を向上。

## 2.2.1 — 2026-10-05

### English
- Separate bundled read-only samples from all existing user works. Copy a sample with its trial parameters before editing.
- Organize user works in one-level folders, with creation, renaming, moves and deletion that keeps the contained works.
- Replace Sound with the supplied MONO SYNTH SCOPE sketch and use the p5.js 2.x sound runtime.
- Restore the original Halo and Gravity samples, and replace Parameters and Wave with the supplied wave Parameter sketch.
- Keep the last file, cursor, scrolling and Undo/Redo when returning to a work during the editing session. Restored or replaced content invalidates incompatible history.
- Show when edits have not reached the running preview, with a Run changes action and an indication that existing errors belong to the previous run.
- Retain a recording after a save failure. Retry saving, choose another destination, or explicitly discard it.
- Search and replace the current file in a compact editor bar, with match highlighting and preserved cursor position after replacement. Whole-work search remains available.
- Rename an asset together with selected literal references, preview the updates, and preserve edits if the combined save fails.
- Name snapshots, add notes, and edit their details without changing the captured content.
- Open or share PNG images directly from the save result screen.
- Reorder gallery folders by long-pressing and dragging to swap with adjacent tabs, keeping My works fixed at the start.
- Rewrite the comprehensive in-app User Guide across English, Japanese, and Chinese with all new features and workflows.

### 日本語
- 同梱サンプル原本を閲覧専用で別管理。既存作品はすべてユーザー作品として保持し、サンプルは試したパラメータとともにコピーして編集。
- ユーザー作品を1階層のフォルダーで分類。作成・名前変更・移動に対応し、フォルダー削除時も中の作品を保持。
- Soundを指定コードのMONO SYNTH SCOPEへ変更し、p5.js 2.xのサウンド実行環境に設定。
- HaloとGravityを元の作品へ復元し、ParametersとWaveを指定コードの「wave Parameter」に統合。
- 編集セッション中に作品へ戻ると、最後のファイル・カーソル・スクロール位置・Undo/Redoを引き継ぐよう改善。復元や内容の置き換えでは不整合な履歴を無効化。
- 編集内容がプレビューへ未反映の場合に表示し、「変更を実行」から再実行可能に。以前の実行に対するエラーも区別。
- 保存に失敗した録画を保持し、再保存・別の保存先・明示的な破棄に対応。
- 現在のファイルの検索・置換をコンパクトな編集バーへ変更。検索箇所の強調表示と置換後のカーソル保持に対応し、作品全体の検索も維持。
- 素材名と選択した固定パスの参照をまとめて変更。更新箇所を確認でき、保存失敗時は元の編集内容を保持。
- スナップショットに名前・メモを付け、記録内容を変えずに編集可能に。
- PNG保存後の結果画面から画像を直接開く・共有する操作に対応。
- 作品選択のフォルダーを長押しドラッグで左右のタブと入れ替えて並び替え（「自分の作品」は先頭固定）。
- 最新機能・ワークフローに対応し、アプリ内の使い方ガイド（日英中）を全面刷新。

## 2.2.0 — 2026-10-05

### English
- **Refreshed Selection UI & Work Gallery**:
  - Overhauled the work picker and gallery layout for improved visual clarity and faster navigation across large sketch collections.
  - Redesigned work sort options, modal sheets, and dialog interactions to provide a cleaner and more responsive mobile editing experience.
- **Android Device Features (Live Camera & Microphone)**:
  - Added full hardware camera and audio recording support via Android WebView media permissions.
  - Introduced bundled **Camera** (dot matrix video reflection) and **Microphone** (car audio graphic equalizer with peak hold) samples to jumpstart reactive multimedia coding.
- **WebGPU Graphics Acceleration**:
  - Added native WebGPU support with p5.js 2.x, enabling modern next-generation compute and graphics shaders on supported devices.
  - Bundled high-performance **WebGPU** sample sketch with automatic fallback to WebGL for older hardware.
- **Stabilized Audio Analysis & New Feature Samples**:
  - Resolved `p5.FFT` initialization errors and improved audio spectrum stability.
  - Added new bundled sample sketches to easily explore and verify newly introduced features.
- **In-App Release Notes ("What's New")**:
  - Added an automatic "What's New" popup after app updates so users can immediately discover newly added features.
  - Available anytime from **Settings → About → What's New**.

### 日本語
- **選択UIの刷新・作品ギャラリーの再設計**:
  - 作品選択画面（ギャラリー）のレイアウトを全面的に刷新し、作品数が増えても一覧性・視認性を維持できる洗練されたデザインへ強化。
  - ソートメニューや各種ダイアログの操作性を最適化し、モバイルでの制作作業をより軽快かつ直感的に改善。
- **Android端末機能の対応（カメラ・マイク入力）**:
  - Android ネイティブのメディア権限ハンドリングに対応し、端末のカメラ映像およびマイク音声のリアルタイム取得が可能に。
  - 端末機能をすぐに試せる公式サンプル **Camera**（ドットマトリクス映像）と **Microphone**（ピークホールド付きカーオーディオ風グラフィックEQ）を新規収録。
- **WebGPU 次世代グラフィックス対応**:
  - p5.js 2.x に対応し、対応端末において WebGPU による高性能なグラフィックス描画・シェーダー演算が利用可能に。
  - WebGPU 非対応端末でも自動的に WebGL へフォールバックする公式サンプル **WebGPU** を同梱。
- **オーディオ解析の安定化・新機能サンプル作品の追加**:
  - `p5.FFT` の初期化エラーを解消し、オーディオ解析の安定性を向上。
  - 新機能を確認出来るサンプル作品を追加。
- **アプリ内「更新内容（What's New）」表示機能**:
  - アップデート後の初回起動時に新機能をフワッと確認できるダイアログを新設。
  - 設定画面の **About →「更新履歴 (What's New)」** からいつでも再確認可能。

## 2.0.6 — 2026-09-29

### English
- **Landscape Device Aspect Ratio (`device_landscape`)**:
  - Added full device landscape aspect ratio support (`max(w, h) / min(w, h)`), allowing generative sketches to utilize the full panoramic screen of modern mobile devices.
- **Reordered Aspect Ratio Hierarchy**:
  - Unified aspect ratio ordering across all dialogs, templates, and badges:
    **Device Landscape → 16:9 → 4:3 → 1:1 → 9:16 → Device Portrait**.
  - Provides a natural progression from horizontal to vertical, creating balanced grid layouts.
- **Performance & UI Optimizations**:
  - Optimized responsiveness across preview ratio switching, settings, and tag operations by moving save operations to the background.
  - Improved scrolling behavior and layout performance across the works gallery and menu dialogs.
- **User Guide Updates**:
  - Updated in-app User Guide across English, Japanese, and Simplified Chinese to accurately document aspect ratio options and clarify Google Drive cloud sync configurations.

### 日本語
- **端末・横（デバイス横向き比率）の追加**:
  - 従来の端末縦（全画面）に加え、端末の物理画面サイズに応じた「端末・横 (`device_landscape`)」比率を新設。パノラマ表示でのクリエイティブコーディングに対応。
- **プレビュー比率順序の再編**:
  - 比率選択ダイアログ、新規作品テンプレート、プレビュー上の比率バッジにおける表示順序を統一：
    **端末横 → 16:9 → 4:3 → 1:1 → 9:16 → 端末縦**。
  - 横長から縦長への自然な遷移となり、グリッド表示のバランスを向上。
- **動作・UIの最適化**:
  - プレビュー比率の切り替えや設定・タグ操作時の保存処理をバックグラウンド化し、UIの応答性を向上。
  - 作品ギャラリーやメニュー等のスクロール処理およびレイアウト動作を最適化。
- **ユーザーガイドの更新**:
  - アプリ内ユーザーガイド（日・英・中）の比率表記を更新し、クラウド同期等の仕様案内を最新化。

## 2.0.5 — 2026-09-28

### English
- **Complete MVVM Architecture & MainActivity Modularization**:
  - Refactored `MainActivity.kt` from ~9,900 lines down to 120 lines, fully transitioning to a clean MVVM architecture with strict separation of concerns.
  - Introduced dedicated ViewModels and Repositories for all domains: Settings (`SettingsViewModel`, `SettingsRepository`), Work Management (`WorkManagementViewModel`, `WorkStoreRepository`, `WorkFolderRepository`), Snapshots (`WorkSnapshotViewModel`), Search & Replace (`SearchReplaceViewModel`), Console & Logging (`ConsoleViewModel`), Recording & Media (`RecordingViewModel`, `PreviewMediaRepository`), and Preview Controller (`PreviewController`).
  - Extracted modular Compose UI screens and layout components: `EditorScreen`, `EditorWorkspaceLayout`, `EditorWindowEffects`, `EditorWorkDialogs`, `PreviewSurface`, and `LiveParameterSheet`.
  - Added comprehensive automated unit test suites covering ViewModels, repositories, and persistence logic.
- **Enhanced Data Persistence & State Preservation**:
  - Implemented `WorkPersistence` interface with atomic writes and active work selection remembrance across SAF folders and local storage.
  - Improved dialog input preservation (`rememberSaveable`) across configuration and orientation changes.
  - Added responsive `WorkParameterBottomSheet` with native drawer presentation in landscape mode.

### 日本語
- **完全な MVVM アーキテクチャ移行・MainActivity のモジュール分離**:
  - 約9,900行に肥大化していた `MainActivity.kt` を 120行 までスリム化し、責務分離を徹底したクリーンな MVVM 構成へ完全移行。
  - 各ドメインごとに ViewModel と Repository を独立新設（設定、作品管理、スナップショット、検索・置換、ログ/コンソール、録画/メディア、プレビュー制御）。
  - Compose UI 画面・レイアウトコンポーネントを独立分離（`EditorScreen`、`EditorWorkspaceLayout`、`EditorWindowEffects`、`EditorWorkDialogs`、`PreviewSurface`、`LiveParameterSheet` など）。
  - 各 ViewModel や永続化層に対する包括的な自動単体テスト（Unit Tests）を整備・拡充。
- **データ永続化と状態保持の安定化**:
  - SAFフォルダおよびローカル保存における選択中作品の自動記憶とアトミック保存を行う `WorkPersistence` を導入。
  - 画面回転や設定変更時に入力状態を保持する `rememberSaveable` 対応をダイアログ群に適用。
  - 横画面でのドロワー表示に対応したレスポンシブな `WorkParameterBottomSheet` を追加。

## 2.0.4 — 2026-09-27

### English
- **Work Pinning & Tag Management**:
  - Pin important works to the top of the gallery with a persistent indicator and quick toggle.
  - Add, edit, and filter works by tags (`#tag`) with tag suggestions and an inline tag editor.
  - Perform quick actions (pinning, editing tags, opening) directly from the Works gallery without leaving the view.
  - Introduce an All-Tags management dialog to review tag usage counts and delete unused tags across all works.
  - Fully integrated with work backups and ZIP import/export to preserve pins and tags across devices.
- **Fullscreen Screen Rotation Fix**:
  - Fixed an issue where the rotation button in fullscreen mode did not rotate the screen orientation. It now properly toggles between portrait and landscape modes.
  - Retain fullscreen preview state seamlessly across device and screen orientation changes.

### 日本語
- **作品のピン留め・タグ付け・タグ管理**:
  - 重要な作品を一覧の最上部に固定できる「ピン留め」機能を新設。プレビュー上のピンバッジやメニューから手軽に切り替え可能。
  - 作品の分類タグ（`#tag`）に対応。タグ編集ダイアログ、候補サジェスト、ギャラリー上部のチップバーによる絞り込みを搭載。
  - 作品選択画面（Works ギャラリー）を開いたまま、カードごとの「︙」メニューや長押し、タグ部分のタップからその場でピン付け・タグ編集・オープンが可能。
  - 全作品で使用されているタグの使用数確認や一括削除ができる「タグの管理」ダイアログを新設。
  - 単体・全体の作品 ZIP 書き出し・取り込みに対応し、他端末への移行時もピン留めやタグ情報を保持。
- **フルスクリーン時の画面回転修正**:
  - フルスクリーン表示時にローテーションボタンを押しても画面が回転しなかった不具合を修正。画面全体の縦横（ポートレート／ランドスケープ）を確実にトグル切り替え可能に。
  - 画面回転に伴う Activity 再生成時も、フルスクリーン表示が勝手に閉じずに維持されるよう状態保持を改善。

## 2.0.3 — 2026-09-26

- **スナップショットの信頼性・データ保護の強化 (Snapshot Reliability & Data Protection)**:
  - 未保存の追加ファイル（シェーダーや別JSファイル）の下書き編集もスナップショットに確実に含めて記録するよう改善。
  - 復元処理の安全性を徹底。作品データのストレージ保存が正常に完了したことを確認してからエディターの状態を切り替えることで、書き込み失敗時でも作業中のコードが失われないよう保護。
  - スナップショットの読み込み、保存、削除、差分生成をすべてバックグラウンド処理（非同期）に移行し、UIのフリーズやカクつきを解消。
  - アトミックファイル書き込み（同期書き込みとバックアップ退避）を導入し、OSの強制終了や中断時でも履歴データの破損を防止。
  - 差分確認画面および復元判定を、メインコード（`sketch.js`）だけでなく追加ファイルや `rinParams` パラメーター全体に拡張。
- **バックアップZIPへのスナップショット収録 (Snapshot Backup Integration)**:
  - 全体バックアップZIPおよび単体作品ZIPにスナップショット履歴（`snapshots.json`）を同梱。他端末や別環境への移行時にも履歴を引き継げるように対応。

## 2.0.2 — 2026-09-26

- **インライン・カラーピッカー (Inline Color Picker)**:
  - コード内のカラーコード（`"#ff0055"` など）に背景ティントを自動表示し、どの色がどこにあるか直感的に把握可能に。
  - キーボード上のアクセサリーバーに動的カラースウォッチを新設。カーソル位置の色をリアルタイムに反映し、タップでカラー編集ダイアログを起動。
  - HSV / RGB スライダー、アルファ（不透明度）、16進数手入力、プリセットパレットを備えた Material 3 カラーピッカーダイアログを搭載。
- **エディター行番号幅の圧縮 (Compact Line Number Gutter)**:
  - ガター幅の計算式と余白を見直し、横幅を従来の約半分（約30dp削減）にスリム化。モバイル画面でコードをより広く表示。

## 2.0.1 — 2026-09-26

- Introduce **Snapshots (Checkpoints)**: Quick-save intentional code states with one tap right from the editor, compare diffs, and restore full multi-file projects and parameters anytime.
- Storage Optimization: Drastically reduce `works.json` size and eliminate work switching delays by isolating snapshots into dedicated storage and pruning legacy revision bloat.
- Landscape Customization: Add setting to swap left/right placement of the editor and preview in landscape mode, with matching parameter sheets and unified toolbar button styling.
- Editor Customization: Add setting to toggle between soft word-wrapping and single-line display with horizontal scrolling.
- Experimental Multi-file Shaders & Physics: Support editing supporting `.frag` and `.vert` shader files alongside JavaScript, with bundled Matter.js 2D physics engine.
- Complete full internationalization audit across all preview controls, editor options, and system alerts.

## 2.0.0 — 2026-09-25

- Introduce **Share Cards**: Generate sleek, social-ready shareable image cards featuring live sketch preview snapshots, optional customizable code snippets, and QR codes that run instantly on the Web Player.
- Introduce **Web Player**: Run and play shared sketches directly in any web browser without server storage, powered by client-side URL fragment decompression.
- Fix MIME type handling when sharing captured images and recordings directly to X (Twitter).
- Fix Jetpack Compose crash on large sketches by capping custom accessibility actions.
- Redesign User Guide with modern theme cards, interactive code snippet copying, and comprehensive Japanese, English, and Simplified Chinese translations.
- Complete full internationalization across all Share Card configuration dialogues and preview controls.

## 1.1.0 — 2026-09-25

- Expand Live Parameters to support boolean toggle switches declared with `// @rin boolean`.
- Add interactive `Sound` synthesizer sample work utilizing `p5.sound` with audio-reactive circular FFT visualizer.
- Add `Parameters` generative art showcase work demonstrating real-time numeric, color, and boolean live parameter adjustments.
- Remove legacy `Axis` and `dvd` samples and standardize sample comments in English.
- Modularize and optimize editor UI dialogs and components.
- Add works folder onboarding prompt when launching without an active storage folder.
- Add update prompt and Settings action to import new official sample works into existing work folders.
- Default `Gravity`, `Parameters`, and `Sound` samples to p5.js 1.11.5 and auto-repair existing `Gravity` works.
- Fix audio oscillator frequency calculation in the `Sound` sample sketch.

## 1.0.9 — 2026-09-24

- Complete names declared in the current work's JavaScript files, with the current file first.
- Defer full-document highlighting, folding and project symbol scans during continuous typing in large works.
- Add per-work live numeric and color parameters declared in JavaScript comments, with saved values and backup support.
- Add an in-app user guide for the complete workflow in English, Japanese and Simplified Chinese.

## 1.0.8 — 2026-09-22

- Add offline p5.brush support with per-work selection and backup support.
- Add a Custom theme with adjustable background and accent colors.
- Open Work settings from the toolbar to manage files, assets, and runtime options.
- Search across all JavaScript files in a work and jump to matching code.
- Jump from console errors to the corresponding JavaScript file and line.
- Fold and unfold multiline JavaScript blocks in the editor.
- Improve editor responsiveness, transitions, and support for high-refresh-rate displays.
- Reduce memory use when importing assets and improve saving and restoring works.
- Fix JavaScript tabs after restoring works and support reloading locally saved works.
- Protect external-folder work files with a verified pending copy and a recoverable previous copy.
- Validate backup assets in temporary storage before adding them to the app's asset library.
- Separate work persistence and preview run state from the main activity.

## 1.0.7 — 2026-09-13

- Add an About section for app information, update checks, developer links and optional support.
- Remove favorites from the work-selection gallery.
- Reduce recording transfer memory use and improve recording settings, progress and saved-media sharing.
- Limit thumbnail caches and improve layouts for landscape and larger text.
- Share a distinct recording attachment with X and validate it before opening the app.

## 1.0.6 — 2026-09-13

- Improve MP4/GIF recording and add configurable capture and sharing options.
- Add the corrected monochrome `/R\_` icon and ensure X shares the latest recording.

## 1.0.5 — 2026-09-13

- Browse works in a thumbnail gallery with search, sorting and favorites.
- Edit multiple JavaScript files using tabs.
- Export and import individual works with their assets and runtime settings.
- Preview assets and insert loading code into the editor.
- View changes before restoring a saved revision.
- Resize the console and see parameter hints for p5.js code completion.
- Improve landscape layouts and responsiveness.

## 1.0.4 — 2026-09-10

- Migrate to Gradle 9.5.0 and Android Gradle Plugin 9.3.2 with built-in Kotlin and Compose compiler 2.2.10.
- Update license generation for Gradle 9's Groovy XML package.

- Unify settings and work panels with the editor's outlined surfaces and compact headers.
- Add category navigation in portrait settings and adapt wide layouts to available width and font size.
- Keep work-setting dialog content scrollable with visible actions and explicit theme colors.
- Adapt runtime selection, asset management, and account import to short screens and larger text.

## 1.0.3 — 2026-09-09

- Renamed the app and project from Edit:KIRO to Edit:RiN.
- Kept the Android application ID and signing identity so existing installations can update normally.
- Fixed missing text in the runtime settings dialog in landscape orientation.
- Added scrolling and explicit theme colors to the runtime settings dialog.
- Updated the backup filename, bundled sample, documentation, update checker, and GitHub links for Edit:RiN.

## 1.0.2 — 2026-09-09

- Fixed preview startup with p5.js 2.x.
- Kept p5.sound off by default and started audio on the first preview interaction when enabled.
- Removed the visible audio-start prompt so the behavior works consistently in every language.
- Fixed fullscreen touch coordinates while preserving the logical canvas size.
- Added compatibility coverage for p5.js 1.x and 2.x, p5.sound, 2D, WebGL, and resized touch input.

## 1.0.1 — 2026-09-08

- Added per-work p5.js 2.3.3 and 1.11.5 selection.
- Added p5.sound 0.4.1, audio assets, synthesis, and analysis.
- Added public-work import from p5.js Web Editor accounts.
- Detected p5.js and p5.sound settings from imported projects.
- Fixed loading `works.json` files with newer positive format version numbers.

## 1.0.0 — 2026-09-08

- First stable release.
- Added code editing, live preview, saved works, backups, screenshots, and recording.
- Hid the portrait preview while editing by default.
- Added silent startup update checks that appear only when a newer stable release exists.
- Added Japanese, English, and Chinese interfaces.

## Beta history

### 1.0.0-beta.4

- Added update checks in Settings.
- Simplified the README and consolidated update history.

### 1.0.0-beta.3

- Updated the four bundled sketches and the simple circle starter.
- Set dark mode as the default and tidied Settings.
- Fixed controls in narrow previews.

### 1.0.0-beta.2

- Updated author credits and licensing.
- Improved icons and title layout.
- Prepared distribution builds and license notices.
