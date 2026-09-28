# Changelog

## 2.0.6 — 2026-09-29

### English
- **Seamless Preview Ratio & UX Optimizations**:
  - Eliminated UI-blocking progress dialogs ("Saving...") when toggling preview aspect ratios, opening settings, or updating tags/pins. Changes now apply instantly (0ms latency) while saving persistently in the background.
  - Debounced in-flight ratio persistence requests to avoid redundant disk I/O during rapid switching.
- **Landscape Device Aspect Ratio (`device_landscape`)**:
  - Added full device landscape aspect ratio support (`max(w, h) / min(w, h)`), allowing generative sketches to utilize the full panoramic screen of modern mobile devices.
- **Reordered Aspect Ratio Hierarchy**:
  - Unified aspect ratio ordering across the ratio dialog, work creation templates, and preview badges:
    **Device Landscape → 16:9 → 4:3 → 1:1 → 9:16 → Device Portrait**.
  - Provides a natural progression from ultrawide horizontal to vertical portrait, creating balanced grid layouts with zero orphaned tiles.
- **UI & Performance Optimizations**:
  - Optimized scrolling behavior and layout responsiveness across the works gallery and menu dialogs.
- **User Guide Documentation & Cloud Sync Clarifications**:
  - Updated in-app User Guide across English, Japanese, and Simplified Chinese to accurately document aspect ratio options and cloud sync folder configurations.

### 日本語
- **プレビュー比率変更の非同期・即時反映（「保存中」ダイアログの解消）**:
  - プレビュー比率の切り替え時や設定画面の表示、ピン留め・タグ操作時に「保存中」ダイアログが割り込むのを解消。UIを即時（遅延0ms）に反映し、ファイル保存はバックグラウンドで非同期に完了する設計に刷新。
  - 短時間の連続操作に対しても不要な多重ディスク書き込みを防ぐデバウンス制御を導入。
- **端末・横（デバイス横向き比率）の追加**:
  - 従来の端末縦（全画面）に加え、端末の物理画面サイズに応じた「端末・横 (`device_landscape`)」比率を新設。超広角・パノラマ表示でのクリエイティブコーディングにネイティブ対応。
- **プレビュー比率順序の再編**:
  - 比率選択ダイアログ、新規作品テンプレート、プレビュー上の比率バッジにおける表示順序を統一：
    **端末横 → 16:9 → 4:3 → 1:1 → 9:16 → 端末縦**。
  - 横長パノラマから縦長への自然な遷移となり、2列グリッド（3行）および3列グリッド（2行）のいずれでも余りなく美しく並ぶレイアウトを実現。
- **UI動作・パフォーマンスの最適化**:
  - 作品ギャラリーやメニュー等のスクロール処理およびレイアウト動作を最適化し、操作時の安定性と快適性を向上。
- **ユーザーガイド拡充とクラウド同期仕様の明確化**:
  - アプリ内ユーザーガイド（日・英・中）の比率表記を更新。Google ドライブ連携フォルダの仕様や自動バックアップ環境構築に関する案内をより正確な記述にアップデート。

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
