# Changelog

## 4.2.0 — 2026-11-21

## English

Requires Android 11 or later.

- Parameter Presets, Morphing & 2D XY Pad: Save, switch, overwrite, rename, and delete live parameter presets; animate transitions smoothly over 650 ms or crossfade interactively with the A↔B blend slider; bind 2-axis controls with `@rin xy` / `@xy`; and edit parameters fullscreen in portrait alongside a floating PiP preview.
- Cross-Work Code Search & Custom Cover Thumbnails: Search code across works with `file:line` snippets, folder-scope toggle, and direct line jump; capture and lock custom cover thumbnails from any frame or revert to auto capture anytime.
- Inspection, Console REPL & Loop Recording Tools: Inspect sketches with a live FPS / `frameCount` / resolution HUD, 1-frame step (`+1`), and a 1-line console JavaScript REPL with command history; capture seamless loops with duration countdown presets and deterministic 30 fps frame-synced GIF export.
- Shaders, Completions, Outline & Block Mode Upgrades: Added dedicated GLSL syntax highlighting (`.frag`, `.vert`, `.glsl`, `.wgsl`), expanded p5/WEBGL/`rinParams`/`p5.brush`/Matter/GLSL completions, a Code Outline jump dialog, full-code preview highlighting with auto-scroll in Block Mode, and bundled Block Mode templates for parameters, `p5.brush`, and `matter-js`.
- Performance Architecture Overhaul & UI Fixes: Eliminated main-thread thumbnail I/O in the gallery, moved gallery transitions and high-frequency overlays off full-screen recomposition, kept the workspace mounted while pausing WebView under Settings, removed hot-path `BoxWithConstraints` passes, isolated per-card parameter recompositions and incremental block errors, auto-closed the camera actions tray in Block Mode / focused portrait editing, and wrapped fullscreen preview overlay actions into a bounded multi-row popup on compact screens.

Edit:RiN includes a visual JavaScript block editor built with Blockly and Acorn. Blockly is licensed under Apache-2.0, and Acorn under MIT. Third-party license texts are available in the app's License information and in the corresponding source repository.

## 日本語

Android 11以降に対応しています。

- パラメータのプリセット保存・補間・2D XYパッド: パラメータ値の組み合わせをプリセットとして保存・切替・上書き・名前変更・削除でき、650msのモーフィング補間やA↔Bブレンドスライダーで滑らかに変化させられます。`@rin xy` / `@xy` による2次元XYパッドと、縦画面全画面パラメータ編集時のPiPプレビュー表示にも対応しました。
- 作品横断のコード検索と手動サムネイル設定: ギャラリー検索から全作品（フォルダー横断切替対応）のソース内を `file:line` スニペット付きで検索し、該当行へ直接ジャンプできます。プレビューの任意のフレームを保護付きカスタムサムネイルとして保存・自動生成へリセットする機能も追加しました。
- 制作支援（1コマ送り・FPS HUD・REPL・フレーム同期録画）: プレビュー右上にFPS・フレーム数・解像度HUD、停止中の`+1`コマ送り、ログ画面に履歴付き1行JS評価（REPL）を追加しました。録画設定では自動停止プリセットや30fps確定ステップのフレーム同期GIF書き出しを利用できます。
- GLSL強調・補完拡充・アウトライン・ブロック拡張: シェーダー（`.frag` / `.vert` / `.glsl` / `.wgsl`）の専用シンタックス強調、p5 / WEBGL / `rinParams` / `p5.brush` / Matter / GLSL の補完拡充、コードアウトライン一覧ジャンプを追加しました。ブロックモードでは全体コードプレビューの強調・自動スクロールに加え、パラメータ・`p5.brush`・`matter-js` のテンプレートとサンプルを搭載しました。
- アプリ全体の高速化再設計とUI修正: ギャラリーのサムネイル判定I/Oの非同期キャッシュ化、遷移アニメや高頻度HUD/録画表示の再構成分離、設定画面の裏でのワークスペース保持（WebView一時停止付き）、ホットパス上の`BoxWithConstraints`撤去、パラメータカード単位のスキップ最適化、ブロックエラーの差分更新を実施しました。さらにブロックモード／縦画面コード入力時のカメラトレイ残留と、全画面プレビュー操作パネルの幅はみ出しを修正しました。

Edit:RiNは、BlocklyとAcornを使用したブロック形式のJavaScript編集機能を備えています。BlocklyはApache-2.0、AcornはMITライセンスで提供されています。第三者ライセンスの全文は、アプリのライセンス情報と対応ソースのリポジトリで確認できます。

## 中文

需要 Android 11 或更高版本。

- 参数预设、平滑插值与二维 XY 触控板: 支持保存、切换、覆盖、重命名和删除参数预设，通过 650 毫秒插值动画或 A↔B 混合滑块平滑过渡；支持 `@rin xy` / `@xy` 二维调节板，并在竖屏全屏参数面板下保留浮动 PiP 预览。
- 跨作品代码搜索与自定义封面缩略图: 在作品库中跨文件夹搜索源码并显示 `file:line` 片段及行级跳转；支持将当前画面帧锁定为手动封面缩略图或随时恢复自动生成。
- 逐帧步进、FPS HUD、控制台 REPL 与帧同步录制: 新增实时 FPS／帧数／分辨率 HUD、暂停 `+1` 逐帧步进及带历史记录的单行 JavaScript 控制台求值（REPL）；录制选项新增定时自动停止预设与 30fps 确定性帧同步 GIF 导出。
- 着色器高亮、代码补全、大纲跳转与积木扩展: 新增 `.frag` / `.vert` / `.glsl` / `.wgsl` 语法高亮，扩展 p5 / WEBGL / `rinParams` / `p5.brush` / Matter / GLSL 自动补全，增加代码大纲跳转弹窗，并在积木模式加入全码预览高亮自动滚动以及参数、`p5.brush`、`matter-js` 模板与示例。
- 全局架构性能优化与界面修复: 将作品库缩略图标记检查迁出主线程并加入缓存，使画廊过渡动画与高频状态更新脱离整页重组，在打开设置时保留后台工作区并暂停 WebView，移除热点路径的 `BoxWithConstraints`，实现参数卡片独立跳过与积木错误增量更新，并修复了切换至积木模式或聚焦编辑时顶部操作栏残留以及全屏预览操作按钮栏溢出的问题。

Edit:RiN 内置基于 Blockly 与 Acorn 构建的可视化 JavaScript 积木编辑器。Blockly 采用 Apache-2.0 许可证，Acorn 采用 MIT 许可证。第三方许可证全文可在应用的许可证信息及对应源码仓库中查看。


## 4.1.0 — 2026-11-20

## English

Requires Android 11 or later.

- Landscape Workspace Redesign: Replaced the landscape top bar with an evenly spaced vertical tool rail, balanced 50/50 editor and 1:1 preview geometry, and preserved the seamless editor file/mode header with a bordered work title button.
- Landscape Work Gallery Redesign: Added a minimal collapsible sidebar Unit for folders, tags, and work creation alongside a 1:1 square artwork grid and uniform-height horizontal cards for bundled sample works with full descriptions.
- Floating Unit Modals & Unified Controls: Styled work menus, inspectors, and dialogs as floating bordered Units in both orientations, refined light-mode surfaces, and unified button/icon touch highlight shapes to match each control's rounded corners.
- Compact Portrait Editor Overlay: Replaced the portrait code top bar with a floating mode/file capsule that omits `sketch.js` on single-file works and hides while typing.
- Runtime, Navigation & Guide Improvements: Added smooth configuration-change retention on rotation, background audio suspend/resume handling in WebView, richer block-editor error localization, and updated settings/onboarding/user guide visuals.

Edit:RiN includes a visual JavaScript block editor built with Blockly and Acorn. Blockly is licensed under Apache-2.0, and Acorn under MIT. Third-party license texts are available in the app's License information and in the corresponding source repository.

## 日本語

Android 11以降に対応しています。

- 横画面ワークスペースの再設計: 均等間隔の縦ツールレールを新設し、1:1作品のプレビューとエディターが50:50で揃うレイアウトへ刷新しました。エディター上部は枠線付き作品名ボタンを保ちつつシームレスなデザインを維持しています。
- 横画面の作品選択画面を再設計: フォルダー・タグ・作品追加をまとめた折りたたみ対応のサイドバーユニットと、1:1正方形サムネイルのギャラリーへ刷新しました。サンプル一覧は最大カード高さに揃えた横型カードで説明文を全文表示します。
- 浮遊ユニットUIと操作パーツの統一: 作品メニュー・作品設定・各種ダイアログを縦横共通の枠付き浮遊ユニットへ統一し、ライトモード配色と各種ボタン／アイコンのタッチ時ハイライト形状を角丸に合わせて揃えました。
- 縦画面エディターの省スペース化: 縦画面コード編集時の上部バーをフロートカプセル化し、単一ファイルでは `sketch.js` 表示を省略、入力中は自動で非表示にしてコード領域を広げました。
- 実行安定性・ガイド改善: 画面回転時の状態保持、バックグラウンド移行時のWebView音声一時停止／復帰制御、ブロック編集エラーの多言語案内強化、設定画面・入門ツアー・使い方ガイドの表示改善を行いました。

Edit:RiNは、BlocklyとAcornを使用したブロック形式のJavaScript編集機能を備えています。BlocklyはApache-2.0、AcornはMITライセンスで提供されています。第三者ライセンスの全文は、アプリのライセンス情報と対応ソースのリポジトリで確認できます。

## 中文

需要 Android 11 或更高版本。

- 横屏工作区重构: 新增等间距垂直工具栏，优化 1:1 作品预览与编辑器的 50:50 平衡布局，并保留带边框作品名按钮的无缝编辑器顶部栏。
- 横屏作品库重构: 新增可折叠的极简侧边栏单元（文件夹、标签与新建作品），采用 1:1 正方形缩略图网格，并为内置示例作品提供统一高度的横向卡片与完整说明。
- 悬浮单元界面与控件统一: 将作品菜单、属性面板与对话框统一为竖横屏一致的带边框悬浮单元，优化浅色模式配色并统一各类按钮与图标的点击反馈圆角形状。
- 竖屏编辑器紧凑悬浮胶囊: 在竖屏代码编辑时使用右上角悬浮胶囊切换模式与文件，单文件作品隐藏冗余的 `sketch.js` 标签，输入时自动隐藏以扩大代码视野。
- 运行稳定性与指南优化: 增强旋转时的状态保留、后台 WebView 音频暂停与恢复管理、积木编辑错误多语言提示以及设置页与使用指南界面。

Edit:RiN 内置基于 Blockly 与 Acorn 构建的可视化 JavaScript 积木编辑器。Blockly 采用 Apache-2.0 许可证，Acorn 采用 MIT 许可证。第三方许可证全文可在应用的许可证信息及对应源码仓库中查看。


## 4.0.1 — 2026-11-08

## English

Requires Android 11 or later.

- Standalone HTML Export: Preserves external URLs, code, and comments by resolving only actual tag references.
- Safe JS Import: Enforces a 16 MiB limit during stream reading, shows localized explanations, and preserves unsaved work.
- Audio Asset Lifecycle: Pauses playback on backgrounding without resuming on restore, and safely unregisters listeners on disposal.

### Details
- Standalone HTML Export: Replaced regular-expression replacements with token-aware tag/attribute scanning, ensuring embedded scripts, external CDN URLs, and HTML comments remain intact during export.
- Safe JS Import: Stream reading now checks `MAX_PROJECT_TEXT_BYTES` (16 MiB) on the fly. If an imported sketch exceeds the limit, a clear message is displayed in English, Japanese, or Chinese, leaving existing editor edits untouched.
- Audio Asset Lifecycle: `AssetPreviewDialog` binds to the view lifecycle to pause audio when backgrounded, prevents unintended auto-playback upon return, and cleanly detaches media listeners on dialog disposal.

Edit:RiN includes a visual JavaScript block editor built with Blockly and Acorn. Blockly is licensed under Apache-2.0, and Acorn under MIT. Third-party license texts are available in the app's License information and in the corresponding source repository.

## 日本語

Android 11以降に対応しています。

- HTML書き出しの安全性向上: 実際のタグ参照先のみを処理し、外部URL・コード・コメントの破損を防止。
- JS取り込みの安全性向上: ストリーム読み込み中に16 MiB制限を適用し、超過理由を多言語で表示して現在の編集を保持。
- 音声素材のライフサイクル管理: アプリ背景移行時に一時停止して復帰時の自動再生を防止し、終了時のリスナー解放を追加。

### 詳細
- HTML書き出し: 正規表現による一括置換からHTMLトークナイザー（タグ・属性走査）へ移行し、コメントやスクリプト内の誤検知を解消。外部CDNやインラインコードを保持したまま安全に書き出せるように改善しました。
- JS取り込み: 外部プロバイダからのストリーム読み込み中に上限チェック（16 MiB）を実施。超過時は現在の編集データを破壊せず、日本語・英語・中国語で分かりやすい理由を表示します。
- 音声素材プレビュー: ライフサイクル監視により、アプリを背景（バックグラウンド）へ移した際の一時停止と、画面復帰時の意図しない自動再生の防止を徹底。ダイアログ破棄時のリスナー解放により安定性を向上させました。

Edit:RiNは、BlocklyとAcornを使用したブロック形式のJavaScript編集機能を備えています。BlocklyはApache-2.0、AcornはMITライセンスで提供されています。第三者ライセンスの全文は、アプリのライセンス情報と対応ソースのリポジトリで確認できます。

## 中文

需要 Android 11 或更高版本。

- 独立 HTML 导出安全性提升: 仅解析实际标签的引用目标，防止外部链接、代码和注释受损。
- JS 导入安全性提升: 在流式读取过程中应用 16 MiB 限制，以多语言提示超限原因并保护当前编辑内容。
- 音频素材生命周期管理: 应用切换到后台时自动暂停且返回时不自动播放，并在销毁时注销监听器。

### 详细说明
- 独立 HTML 导出: 从正则替换改用 HTML 词法分析器，保留注释与内联脚本，安全处理外部 CDN 链接。
- JS 导入: 在读取流的同时检查 16 MiB 上限，超限时保护当前正在编辑的内容并以多语言提示。
- 音频素材预览: 监听应用生命周期，在进入后台时暂停播放且恢复时不自动重播，并在对话框关闭时安全注销监听器。


## 4.0.0 — 2026-10-27

## English

Requires Android 11 or later.

- JavaScript Block Mode: Build and edit p5.js code visually with blocks while keeping `sketch.js` as the single source of truth, with direct color/number capsule controls, a bottom block dock, function navigation, and floating PiP preview.
- Unified History & Snapshots: Browse auto-save history and named snapshots in a single sheet with category filters, restoration scope badges (code only vs. extra files, parameters, and block layout), and a preview of the code to restore with an indication of whether it matches the current code.
- Unified Inspector & Safe Action Guards: Select all 6 preview aspect ratios directly in the project inspector, return to the inspector from sub-dialogs, and automatically flush pending block edits before saving, running, or switching files.
- Coordinated Preview Execution: Track runtime and visual readiness and superseded runs with execution tokens, and prevent sketch reruns during recording or recording export.
- Expanded Onboarding & User Guide: Added an interactive Block Mode step to the 9-step onboarding tour and updated the built-in user guide for block editing and unified history.
- Third-Party Libraries: Bundled Blockly 13.4.0 (Apache-2.0) and Acorn 8.19.0 (MIT) for the block editor, with corresponding licenses and checksums.

Block Mode edits JavaScript source ranges directly. Unsupported syntax is preserved as code-holding blocks, and disconnected block parts are stored in project metadata without running until connected to a function or top-level flow.

## 日本語

Android 11以降に対応しています。

- ブロックモードの新設: `sketch.js` を唯一の正本（Source of truth）として保ったまま、ブロックでJavaScriptを組み立て・編集できるモードを追加しました。色スウォッチや数値カプセルの直接操作、下部ブロックドック、関数一覧・検索、PiP小窓プレビューに対応しています。
- 保存履歴とスナップショットの統合: 自動保存履歴と名前付きスナップショットを1つのシートに統合し、種別フィルター、復元対象範囲（コードのみ／追加ファイル・パラメータ・ブロック配置を含む）の表示、復元するコードの表示と、現在のコードとの一致判定を追加しました。
- 作品設定と操作前確認の共通化: 作品設定シートで全6種類のプレビュー比率（正方形・横長・縦長・4:3・端末横・端末縦）を直接切り替えられるようにし、子画面から元の設定シートへ戻る階層遷移と、保存・実行・ファイル切替時のブロック編集確定処理を共通化しました。
- プレビュー実行状態の管理: 実行トークンを用いて実行環境・描画の準備完了と新しい実行への切り替えを管理し、録画中や録画の保存中は再実行を制限します。
- 入門ツアーと使い方ガイドの拡充: 初回入門ツアー（全9ステップ）とアプリ内の使い方ガイドに、ブロックモードの模擬操作と統合履歴シートの説明を追加しました。
- 第三者ライブラリの同梱: ブロック編集基盤として Blockly 13.4.0（Apache-2.0）および Acorn 8.19.0（MIT）を同梱し、対応するライセンス表記とチェックサムを追加しました。

ブロックモードはJavaScriptの該当範囲を直接更新します。未対応の構文はコードを保持するブロックとして保護され、関数やトップレベルに接続していないブロック部品は作品の補助メタデータに保存され、接続するまで実行されません。

## 中文

需要 Android 11 或更高版本。

- JavaScript 积木模式: 在保持 `sketch.js` 为唯一真实来源（Source of truth）的同时，通过积木可视化构建和编辑代码，支持颜色与数值胶囊直接调节、底部积木栏、函数导航以及浮动小窗预览。
- 历史记录与快照整合: 将自动保存历史与命名快照整合到同一面板，提供分类筛选、恢复范围标识（仅代码或包含附加文件、参数与积木布局）以及待恢复代码预览和与当前代码是否一致的提示。
- 作品设置与操作前检查统一: 支持在作品设置面板直接切换全部 6 种预览比例，从子对话框返回原设置面板，并在保存、运行或切换文件前自动提交未完成的积木编辑。
- 预览运行状态管理: 通过运行令牌跟踪运行环境与画面的就绪状态以及新运行的替换，并在录制或保存录制结果期间限制重新运行。
- 入门导览与使用指南扩展: 在 9 步入门导览和内置使用指南中新增了积木模式互动演示与整合历史面板说明。
- 第三方库集成: 内置 Blockly 13.4.0（Apache-2.0）与 Acorn 8.19.0（MIT）用于积木编辑，并附上对应的许可证与校验和。

积木模式直接更新对应的 JavaScript 源码范围。暂不支持的语法会作为保留代码的积木块受到保护；未连接到函数或顶层流程的积木部件会保存在作品附加元数据中，连接前不会执行。


## 3.2.1 — 2026-10-17

## English

Requires Android 11 or later.

- Tweet code golf: Shorten recursive helpers and repeated p5 calls while preserving strings, templates, and regex literals.
- Live editing fixes: Keep variable-watch declaration scopes and update annotated constants in classic scripts and modules.
- HTML export fixes: Include saved parameter values, use the selected module entry, and embed relative module dependencies.
- Update highlights now recognize exact language headings, retain nested sections, and skip fenced code examples.
- Legacy HTML exports use p5.js 1.11.5, matching the app runtime.

Code golf favors shorter formulas over identical execution. Scope, loop order, and setup timing can change; try the result before applying or sharing it. HTML exports may still need network access for libraries and separate asset files.

## 日本語

Android 11以降に対応しています。

- つぶやきProcessing用のコード圧縮: 再帰関数のアロー式化やp5関数の別名化を追加しました。文字列・テンプレート・正規表現の内容は保持します。
- ライブ編集の修正: 変数ウォッチで宣言の有効範囲が変わる問題を直しました。注釈付きconst変数のパラメータ操作は、通常JSとモジュールの両方で実変数に反映します。
- HTML書き出しの修正: 保存したパラメータ値を初期化し、指定した開始モジュールと相対インポート先を埋め込みます。
- 更新内容は言語見出しを完全一致で判定し、小見出しの先も読み取ります。コード例の箇条書きは表示対象から外しました。
- 旧バージョン用のHTML出力は、アプリと同じp5.js 1.11.5を使うように直しました。

コード圧縮は、同じ動作の維持より式の短さを優先します。変数の有効範囲、ループの順序、初期化のタイミングが変わるため、本文への反映や共有の前に結果を試してください。HTML出力では、ライブラリ取得のための通信や素材の別ファイルが必要な場合があります。

## 中文

需要 Android 11 或更高版本。

- 推文代码压缩: 将递归辅助函数改写为箭头表达式，为重复的p5函数调用使用短别名，同时保留字符串、模板和正则表达式的内容。
- 实时编辑修复: 保留变量监视声明的作用域，让带注释的const参数在普通脚本和模块中都能更新实际变量。
- HTML导出修复: 初始化已保存的参数值，使用指定的入口模块，并嵌入相对导入的模块文件。
- 更新内容按完整的语言标题匹配，保留子标题下的内容，并跳过代码示例中的列表项。
- 旧版本HTML导出改用与应用一致的p5.js 1.11.5。

代码压缩优先缩短公式，可能改变作用域、循环顺序和初始化时机。应用或分享前请先试运行。导出的HTML可能仍需联网加载库，或需要额外的素材文件。


## 3.2.0 — 2026-10-16

### English

- Added an interactive 4-step Editor Practice Tour accessible from Settings, User Guide, and Onboarding completion to explore live watch badges, parameters, and code compression in a safe sandbox.
- Implemented startup tour automatic dispatch: fresh installations launch the Onboarding Tour, while updating from earlier versions prioritizes the new Editor Practice Tour.
- Redesigned the update dialog with modern card styling, clear download progress details, and automatic language-based feature highlights extracted dynamically from GitHub Releases.
- Added live watch auto-ghosting and direct touch pass-through when cursor lines hover over inline watch comments.

### 日本語

- 変数ウォッチ、スライダー、コード圧縮を安全なサンドボックスで実際に体験できる「エディター実践ツアー」（全4ステップ）を新設しました（初回ツアー完了画面、設定画面、使い方ガイドからいつでも起動可能）。
- 起動時ツアーの自動振り分けを実装しました。新規インストール時は入門ツアー、過去バージョンからの更新時はエディター実践ツアーを優先して自動案内します。
- アプリ更新画面をモダンなカードデザインに刷新し、詳細な進捗表示とGitHub Releasesからの多言語ハイライト自動抽出に対応しました。
- 編集中のカーソル行と重なる変数ウォッチバッジの自動半透明化（オートゴースト）および直接タッチ編集に対応しました。

## 3.1.0 — 2026-10-16

### English

- Added comment-driven bidirectional live parameters: declare variables with inline comments like `// @slider(min, max, step)`, `// @int(min, max)`, `// @color`, `// @toggle`, and `// @choice(...)` to automatically generate parameter controls that sync bidirectionally with running preview and editor code with grouped undo.
- Added `#つぶやきProcessing` support mode featuring a slim 1-line HUD character gauge (140/280 chars), a compact modal share sheet, and smart code compression to fit creations cleanly into social posts.
- Implemented PiP auto-ghosting and direct touch editing in portrait mode: floating preview automatically drops opacity to 25% when hovering over active cursor lines or inline watch values; tapping directly on preview focuses the editor to edit code underneath.
- Added dynamic bottom content padding in portrait editor, expanding scrollable space when PiP is docked to bottom corners so code is never hidden beneath preview.
- Added URL-based asset hot-swap helpers. Normal asset changes still reload the preview. Asset size limits apply; memory usage depends on the device and content.
- Optimized app memory footprint with lazy-loaded reference databases and translation catalogs.

### 日本語

- コメント駆動の双方向ライブパラメータを導入しました。コード内に `// @slider(min, max, step)`, `// @int(min, max)`, `// @color`, `// @toggle`, `// @choice(...)` などのインラインコメントを書くだけで操作UIが自動生成され、スライダー操作によるプレビュー即時反映とコード数値の自動置換が連動します（1操作ごとのUndo集約対応）。
- `#つぶやきProcessing` 支援モードを追加しました。画面上部に常時表示される1行スリムHUD文字数カウンター（140/280字）、専用シェアシート、余分な記述のスリム化や定数置換・変数名整理を行うコード自動圧縮機能を搭載しました。
- 縦画面PiP（小窓プレビュー）に「オートゴースト」と「直接タッチ編集」を導入しました。編集中のカーソル行やインラインウォッチと小窓が重なると自動で半透明化（不透明度25%）し、小窓の上から背後にあるコードを直接タップしてそのまま編集を続けられます。
- 縦画面エディターに動的ボトムパディングを追加し、小窓が下隅にある際も最下行のコードが小窓の下に隠れないようスクロール余白を自動調整します。
- URL参照で素材を差し替える内部処理を追加しました。通常の素材変更ではプレビューを読み込み直します。素材には容量制限があり、メモリ使用量は端末や内容によって異なります。
- p5.jsリファレンスデータおよび多言語翻訳カタログの遅延展開（Lazy Loading）を導入し、アプリ起動時のメモリ消費を削減しました。

## 3.0.0 — 2026-10-15

### English

- Added internal WebMessagePort hot-swap helpers. Normal code reruns reload the preview; reload-free editing and uninterrupted audio are not guaranteed.
- Added floating PiP (Picture-in-Picture) preview in portrait mode: canvas docks to screen corners during editing with S/M/L presets, smooth corner snapping, customizable translucency, and session-persistent layout state.
- Added live parameter auto-run option that automatically updates sketches when typing pauses.
- Introduced 2-axis numeric scrubbing (horizontal drag adjusts value, vertical drag scales step increment) and inline color swatch palette for instant hex color tweaking.
- Added dedicated symbol accessory bar on mobile keyboard with quick-insert for common coding punctuation, brackets, operators, and indentation tools.
- Added a Piece Table text buffer and viewport virtualization to reduce editing and rendering work for large sketches. Performance depends on the device and sketch; no fixed frame rate is guaranteed.
- Expanded the interactive onboarding tour to 8 steps with dedicated interactive previews for editor helper tools and PiP preview mode.
- Streamlined fresh install and update flows into an orderly sequence: Onboarding Tour, followed by What's New highlights, followed by workspace folder selection.
- Enforced complete tour completion on initial runs and major updates while keeping instant skipping available when replaying from Settings.

### 日本語

- WebMessagePortを使うHot-Swap用の内部処理を追加しました。通常のコード再実行ではプレビューを読み込み直します。編集時のゼロリロードや音声の継続を保証するものではありません。
- 縦画面の入力時に作品が画面の四隅にスナップする「フローティングPiP（小窓）プレビュー」を追加しました。S/M/Lの3段階サイズ切替、4隅ドラッグスナップ、透過度調整、最小化に対応し、配置やサイズの状態はセッション内で安全に記憶されます。
- 文字入力停止時に自動でプレビューを更新する「Auto-Run」機能を追加しました。
- 2軸ベロシティ・スクラブ（左右ドラッグで数値増減、上下ドラッグで桁倍率変更）と、コード内のカラーコードをタップして直接色を選べるインラインカラーパレットを追加しました。
- キーボード上部に記号入力バーを追加し、波括弧やセミコロン、比較演算子、インデント調整をワンタップで素早く入力できるようにしました。
- エディターにPiece Tableテキストバッファと表示領域のみを描画する処理を導入し、大規模コードの編集・描画の負荷を減らしました。動作速度は端末や作品によって異なり、一定のフレームレートを保証するものではありません。
- 対話型入門ツアーを全8ステップに拡充し、エディター便利機能（記号バー・スクラブ・カラー）とPiP小窓プレビューの模擬操作ガイドを追加しました。
- 新規インストールおよびアップデート時の案内順序を「入門ツアー → 更新内容 → フォルダー選択」の順に整理し、重複表示なくスムーズに案内されるように直列化しました。
- 強制表示時のツアーではスキップを不可（設定画面からのリプレイ時のみスキップ可能）とし、主要な機能の理解をサポートします。


## 2.5.0 — 2026-10-13

### English

- Added an adaptive parameter layout: in landscape orientation, parameters dock as an inline side panel beside the canvas, while portrait orientation maintains the modal bottom sheet.
- Fixed editor overflow when opening read-only sample code, expanding the editor smoothly upward while preserving bottom canvas margins.
- Redesigned the About section into three structured cards (App Info, Official Links & Community, Support & Legal) with external links to the official website and GitHub repository.
- Replaced the adaptive launcher icon in the About screen with a vector resource to eliminate an Android 13+ rendering crash, and unified app branding to "Edit:RiN".
- Featured the "Weave" generative art sample for fresh installations and updates, while safely preserving existing user works and unsaved drafts.
- Added an interactive onboarding tour for new users to explore essential editor actions.
- Added standalone single-file HTML sketch export for viewing and sharing creations directly in web browsers.

### 日本語

- 画面の向きに応じたパラメータ操作に対応しました。横画面ではプレビュー横のインラインパネルでリアルタイムに調整でき、縦画面では使い慣れたボトムシートを維持します。
- 閲覧専用サンプルのコードを開いた際にエディター下部が画面外にはみ出る問題を修正し、下部余白を維持したまま上方向へ自然に展開するように改善しました。
- アプリ情報（About）画面を「アプリ情報」「公式リンク・コミュニティ」「サポート・法的情報」の3カードに再編成し、公式WebサイトやGitHubリポジトリへのリンクを追加しました。
- Android 13以降でアプリアイコン読み込み時に発生していたクラッシュを解消し、アプリアイコンをベクター描画で安定化して表記を「Edit:RiN」に統一しました。
- 初期起動およびアップデート時の体験作品として、波形生成サンプル「Weave」を採用しました（既存の作品や下書きは安全に保持されます）。
- 初めての利用時に主要な機能を案内する対話型オンボーディングツアーを追加しました。
- ブラウザでそのまま実行できる単一HTMLファイル書き出しに対応しました。


## 2.4.0 — 2026-10-10

### English

- Added an interactive practice sandbox and redesigned chapters to the User Guide. Try controls, sliders, parameter changes, pause/resume, and snapshot restoration directly within the guide without affecting user works.
- Introduced StudioSlider with a slim rail and rectangular fader inspired by the official website, unified across parameters, font size settings, and custom color controls.
- Streamlined preview controls by consolidating snapshot and revert actions into a single History dropdown menu.
- Updated theme selection in Settings to a smooth horizontal scrolling chip group.
- Refined typography animation and easing on the official website, with rotation transformations and persistent hint dismissal.

### 日本語

- ユーザーガイドを再構成し、アプリ内で操作を体験できる練習用サンドボックスを追加しました。実際の作品に影響を与えることなく、スライダーやパラメータ変更、一時停止・再開、スナップショットと復元などの操作をその場で試せます。
- 公式Webサイトのデザインに合わせた薄型レールと長方形フェーダーのStudioSliderコンポーネントを導入し、パラメータ調整、フォントサイズ設定、カスタムカラー設定のスライダーを統一しました。
- プレビュー操作ツールの「スナップショット」と「保存済み状態に戻す」を「履歴」ドロップダウンメニューに統合し、ツールバーの配置を整理しました。
- 設定画面のテーマ選択を、横スクロール可能なチップグループに変更しました。
- 公式WebサイトのRiNタイポグラフィアニメーションのイージングと回転表現を向上させ、操作ヒントの非表示記憶などに対応しました。


## 2.3.2 — 2026-10-09

### English

- Changed the minimum supported OS to Android 11 (API 30). Android 6–10 devices cannot install v2.3.2; the previously published v2.3.1 supports Android 6.0 or later.
- Added bundled thumbnails for supported samples. Gallery thumbnail generation now prioritizes visible works and pauses during scrolling, saving, and recording. Background work also adjusts to device load, available memory, power saving, and temperature.
- Updated thumbnail caching to detect changes to code, assets, and parameters and avoid regenerating unchanged images. Newer images captured from the visible preview take priority over background captures.
- Revised transitions between the gallery and editor, including the animation from a selected thumbnail to the preview. The selected work’s thumbnail stays visible while its first preview frame loads.
- Improved work switching during saves. Switching waits for pending saves and uses the most recently selected destination; a failed save cancels the switch and keeps the current edits.
- Moved draft checks and image processing off the main UI thread and prioritized opening and saving works over background thumbnail reads.

### 日本語

- 対応OSをAndroid 11（API 30）以降に変更しました。Android 6〜10の端末にはv2.3.2をインストールできません。公開済みのv2.3.1はAndroid 6.0以降に対応しています。
- 一部のサンプルにサムネイル画像を同梱しました。作品のサムネイルは、一覧で見えている作品から優先して生成し、スクロール・保存・録画中は生成を一時停止します。端末の負荷、空きメモリ、省電力設定、温度に応じて生成処理も調整するようにしました。
- コード・素材・パラメータの変更を確認し、変わっていないサムネイルを再生成しないようにしました。表示中のプレビューから保存した新しい画像は、バックグラウンドで生成した画像より優先されます。
- 作品一覧とエディタの切り替えを見直し、選んだサムネイルからプレビューへつながるアニメーションに変更しました。プレビューの最初の描画が完了するまでは、選んだ作品のサムネイルを表示します。
- 保存中に別の作品を選ぶと、保存完了後に切り替わるようにしました。続けて複数の作品を選んだ場合は、最後に選んだ作品へ移ります。保存に失敗した場合は切り替えを中止し、現在の編集内容を残します。
- 下書きの確認や画像処理を画面操作とは別の処理に移しました。バックグラウンドのサムネイル用読み込みより、作品を開く操作や保存を優先するようにしました。


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
