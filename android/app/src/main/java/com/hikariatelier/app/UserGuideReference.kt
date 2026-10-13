package com.hikariatelier.app

/**
 * Reference topics for the in-app guide. Japanese is the source text; English and Chinese are
 * written to be short and clear rather than translated word for word. All languages must keep
 * the same topics in the same order (see UserGuideTest).
 * Facts follow `P5_COMPATIBILITY.md`, `P5_RUNTIME_AND_SOUND.md`, and `WorkParameters.kt`.
 */
internal data class GuideReferenceEntry(
    val title: String,
    val summary: String,
    val group: String,
    val paragraphs: List<String>,
    val code: String? = null,
    val notes: List<String> = emptyList()
)

internal fun guideReferenceEntries(language: String): List<GuideReferenceEntry> = when (language) {
    "ja" -> japaneseGuideReference
    "zh" -> chineseGuideReference
    else -> englishGuideReference
}

private val japaneseGuideReference = listOf(
    GuideReferenceEntry(
        title = "パラメータ宣言（// @rin）",
        summary = "コメント1行で、スライダーや色などの操作項目を作ります。",
        group = "パラメータ",
        paragraphs = listOf(
            "書式は // @rin 種類 名前 \"表示名\" 値 です。名前は英字・_・\$で始まる識別子で、コードからは rinParams.名前 で読み取ります。表示名は1〜40文字で、二重引用符で囲みます。",
            "number: 最小 最大 初期値 刻み を空白で区切ります。最小は最大より小さく、初期値は範囲内、刻みは0より大きくします。",
            "color: #RRGGBB の6桁を1つ書きます。",
            "boolean: true か false を1つ書きます。",
            "choice: 初期値のあとに選択肢を並べます。初期値は選択肢の1つで、選択肢は2つ以上必要です。各選択肢は32文字以内の英数字・_・.・- です。"
        ), code = "// @rin number size \"Size\" 20 240 120 10\n// @rin color ink \"Ink\" #D67856\n// @rin boolean fillOn \"Fill\" true\n// @rin choice shape \"Shape\" circle circle square triangle\n\nfunction draw() {\n  background('#F2EFE7');\n  fill(rinParams.ink);\n  if (rinParams.shape === 'circle') circle(width / 2, height / 2, rinParams.size);\n}", notes = listOf("宣言は16個までです。同じ名前は、最初の1つだけが使われます。", "書式に合わない行は、エラーにならず無視されます。", "宣言はコメントなので、そのまま実行できます。値は作品ごとに保存され、コピーやバックアップにも含まれます。")
    ),
    GuideReferenceEntry(
        title = "HTML作品",
        summary = "HTMLを入口に、自分でページを組み立てます。",
        group = "作品の構成と実行",
        paragraphs = listOf(
            "作品メニューの「プロジェクトファイル」で、index.html、style.css、lib/math.mjs などを追加・編集できます。フォルダー付きの名前も使え、相対パスやimportはその構造のまま動きます。",
            "実行方式が自動のときは、index.html があればそれを実行します。",
            "HTML作品では、scriptタグがp5.jsのバージョン、ライブラリ、読み込み順を決めます。実行環境ダイアログの設定は、JavaScript・モジュール作品に使われます。",
            "内蔵版p5のCDN URLは、端末内の資産につながります。独自版のURLや、CSPを指定したHTMLのURLは、そのまま使われます。"
        ), notes = listOf("CSPがプレビュー用の補助スクリプトを禁止すると、プレビュー操作の一部が使えないことがあります。", "撮影・録画の対象はキャンバスです。ボタンなどのDOM要素は含まれません。複数のキャンバスは合成されます。", "一時停止の対象は、p5、HTMLのアニメーションフレーム、CSSアニメーションです。通常のタイマーは止まりません。")
    ),
    GuideReferenceEntry(
        title = "実行方式と開始ファイル",
        summary = "自動・通常のJS・モジュール・HTMLの違いと、読み込み設定です。",
        group = "作品の構成と実行",
        paragraphs = listOf(
            "実行環境ダイアログの「プロジェクトの読み込み設定」で、実行方式（自動・通常のJavaScript・モジュール・HTML）と開始ファイルを選べます。",
            "自動では、index.html があればHTMLを実行します。メインコードに静的な import / export があれば、モジュールとして扱います。.mjs が1つだけのときは、メインコードが空で通常のJSファイルがない場合に限り、自動で選ばれます。",
            "通常のJavaScriptは、読み込み順を指定できます。空のままなら、従来どおりまとめた順で読み込みます。",
            "モジュールでは、setup / draw / 入力コールバックを宣言・export するか、new p5(...) を使います。npmのパッケージ名は解決されないため、URLかHTMLのimport mapを使います。",
            "設定は作品の edit-rin.json に保存され、バックアップにも含まれます。"
        ), code = "{\n  \"executionMode\": \"module\",\n  \"moduleEntry\": \"main.mjs\",\n  \"libraries\": [],\n  \"p5Url\": null\n}"
    ),
    GuideReferenceEntry(
        title = "p5.jsのバージョンとp5.sound",
        summary = "2.3.4と1.11.5の違い、音声ライブラリの設定です。",
        group = "作品の構成と実行",
        paragraphs = listOf(
            "p5.js 2.3.4 は新規作成時の標準で、async/await に対応します。1.11.5 は従来のスケッチ向けです。以前の2.3.3の設定は、2.3.4として読み込まれます。",
            "p5.sound は音声の再生・合成・解析のライブラリで、初期状態はオフです。2.xでは公式の0.4.1、1.11.5では同梱の従来版を読み込みます。",
            "p5.Part、p5.Phrase、p5.PolySynth、SoundRecorder、saveSound などの従来のAPIを使う作品は、1.11.5を選びます。",
            "Processing / Java は内蔵していません。"
        )
    ),
    GuideReferenceEntry(
        title = "外部ライブラリとHTTPS URL",
        summary = "追加ライブラリの読み込み方と、読み込み順です。",
        group = "作品の構成と実行",
        paragraphs = listOf(
            "外部のHTTPSライブラリは、通常のスクリプトかモジュールとして登録できます。通常のスクリプトは、登録順に作品コードより先に実行されます。モジュールは、ブラウザーのモジュールの規則に従います。",
            "p5.js本体も、独自のHTTPS URLで指定できます。外部URLの利用には、インターネット接続が必要です。"
        ), notes = listOf("CORSなど、ブラウザーと同じ制約があります。")
    ),
    GuideReferenceEntry(
        title = "素材（画像・音声・フォント・データ）",
        summary = "素材の読み込み方と、容量などの上限です。",
        group = "作品の構成と実行",
        paragraphs = listOf(
            "作品メニューの「作品の素材」で取り込み、コードからは assets/ファイル名 で読み込みます。素材のインスペクターから、読み込みコードを挿入できます。",
            "素材名を変えるときは、一致するパスを確認し、更新する箇所を選べます。",
            "上限は、1ファイル50 MiB、1作品100ファイル・合計200 MiBです。プロジェクトファイル（テキスト）は、新しい作品で500ファイル・合計16 MiBまでが実行の対象です。"
        ), code = "let img;\n\nfunction preload() {\n  img = loadImage('assets/photo.png');\n}"
    ),
    GuideReferenceEntry(
        title = "サウンドの再生",
        summary = "音声の読み込み方と、再生が始まる条件です。",
        group = "端末機能と入出力",
        paragraphs = listOf(
            "音声ファイルは「作品の素材」に追加し、assets/ファイル名 で指定します。p5.js 1.xでは preload() で loadSound を呼び、2.xでは async setup() で await loadSound() を使います。",
            "再生には、画面のタップなどユーザーの操作が必要です。p5.soundを有効にすると、プレビューをタップしたときに音声の準備が始まります。マイク入力には、Androidの権限も必要です。"
        ), code = "let sound;\n\nasync function setup() {\n  createCanvas(400, 400);\n  sound = await loadSound('assets/music.mp3'); // p5.js 2.x\n}\n\nfunction mousePressed() {\n  if (sound && !sound.isPlaying()) sound.play();\n}"
    ),
    GuideReferenceEntry(
        title = "カメラ・マイク・ファイル選択・センサー",
        summary = "端末の機能を使う関数と、必要な権限です。",
        group = "端末機能と入出力",
        paragraphs = listOf(
            "createCapture() やマイク入力を使うと、Androidのカメラ・音声の権限を要求します。拒否すると、通常のブラウザーの権限エラーになります。",
            "createFileInput() は、Androidのファイル選択画面を開きます。入力が複数選択を指定した場合は、複数選択もできます。",
            "傾きと加速度（rotationX/Y/Z、accelerationX/Y/Z、deviceMoved()、deviceTurned()、deviceShaken()）に対応します。センサーは、作品が使うときだけ有効になります。",
            "fullscreen(true) は、Androidの全画面表示になります。ブラウザーと同様に、ユーザーの操作が必要です。"
        ), notes = listOf("WebGPU・センサー・全画面・ファイル選択は、端末とWebViewによって動作が変わります。")
    ),
    GuideReferenceEntry(
        title = "保存・ダウンロード・撮影・録画",
        summary = "p5.jsの保存関数と、画像・動画の書き出しです。",
        group = "端末機能と入出力",
        paragraphs = listOf(
            "saveCanvas、saveJSON、saveStrings、saveTable、saveFrames、saveGif、従来のsaveSound、HTMLのダウンロードリンクは、Androidに保存されます。出力できる形式は、p5.jsとWebViewの対応に従います。",
            "保存先は、通常 Download/Edit-RiN です。Android 6〜9でストレージ権限がない場合は、保存先を選べます。",
            "カメラボタンのPNG書き出しは、1倍・2倍・4倍を選べます。倍率を上げると、高い解像度で描き直します。上限は1600万画素です。",
            "録画はMP4とGIFを選べます。設定で、カウントダウンとMP4のビットレートを変えられます。"
        )
    ),
    GuideReferenceEntry(
        title = "WebGPU",
        summary = "公式のWebGPUアドオンを使う条件です。",
        group = "端末機能と入出力",
        paragraphs = listOf(
            "p5.js 2.3.4の公式WebGPUアドオンを内蔵しています。",
            "WEBGPU を使うには、端末のGPUとAndroid WebViewが対応している必要があります。"
        )
    ),
    GuideReferenceEntry(
        title = "動かないとき・保存できないとき",
        summary = "よくある原因と、確認する場所です。",
        group = "困ったとき",
        paragraphs = listOf(
            "プレビューが古いときは、「変更を実行」を押します。ログのエラー表示から、該当するコードの位置へ移動できます。",
            "パラメータが出ないときは、// @rin の書式（種類、引用符、数値の範囲）と、宣言が16個を超えていないかを確認します。",
            "音が鳴らないときは、p5.soundが有効か、バージョンに合った読み込み方か、画面を一度タップしたかを確認します。",
            "npmのパッケージ名のimportが失敗するときは、URLかHTMLのimport mapに置き換えます。",
            "HTML作品で一部のプレビュー操作が使えないときは、CSPが補助スクリプトを禁止していないかを確認します。",
            "保存に失敗しても、編集内容はそのまま残ります。表示される再試行や復旧の選択肢を使ってください。"
        )
    )
)

private val englishGuideReference = listOf(
    GuideReferenceEntry(
        title = "Parameter declarations (// @rin)",
        summary = "Add sliders, colors, and other controls with one comment line.",
        group = "Parameters",
        paragraphs = listOf(
            "Write: // @rin kind name \"Label\" values. The name is an identifier; read it in code as rinParams.name. The label is 1–40 characters in double quotes.",
            "number: min max initial step. min < max, initial within range, step > 0.",
            "color: one #RRGGBB value.",
            "boolean: true or false.",
            "choice: the initial value, then the options. At least 2 options; the initial value must be one of them. Each option is up to 32 letters, digits, _ . or -."
        ), code = "// @rin number size \"Size\" 20 240 120 10\n// @rin color ink \"Ink\" #D67856\n// @rin boolean fillOn \"Fill\" true\n// @rin choice shape \"Shape\" circle circle square triangle\n\nfunction draw() {\n  background('#F2EFE7');\n  fill(rinParams.ink);\n  if (rinParams.shape === 'circle') circle(width / 2, height / 2, rinParams.size);\n}", notes = listOf("Up to 16 declarations. For a repeated name, only the first is used.", "Lines in the wrong format are ignored without an error.", "Declarations are comments, so the sketch runs as is. Values are saved per work and included in copies and backups.")
    ),
    GuideReferenceEntry(
        title = "HTML works",
        summary = "Build your own page with HTML as the entry point.",
        group = "Project and runtime",
        paragraphs = listOf(
            "In Work menu → Project files, add and edit files such as index.html, style.css, and lib/math.mjs. Names can include folders; relative paths and imports keep that structure.",
            "With automatic execution, index.html runs if it exists.",
            "In HTML works, script tags choose the p5.js version, libraries, and load order. The Runtime dialog settings apply to JavaScript and module works.",
            "Bundled p5 CDN URLs resolve to local assets. Other versions, and URLs in HTML with a CSP, are used as written."
        ), notes = listOf("A CSP that blocks the preview helper script can disable some preview actions.", "Capture and recording include canvases only, not DOM elements such as buttons. Multiple canvases are combined.", "Pause affects p5, HTML animation frames, and CSS animations. Plain timers keep running.")
    ),
    GuideReferenceEntry(
        title = "Execution mode and entry file",
        summary = "Automatic, classic JavaScript, module, and HTML, plus loading settings.",
        group = "Project and runtime",
        paragraphs = listOf(
            "In Runtime → Project loading settings, choose the execution mode (automatic, classic JavaScript, module, or HTML) and an entry file.",
            "Automatic runs index.html if it exists. Main code with static import/export runs as a module. A single .mjs is selected automatically only when the main code is empty and there are no classic JS files.",
            "Classic JavaScript can use an explicit load order. If empty, files load in the earlier combined order.",
            "A module can declare or export setup, draw, and input callbacks, or use new p5(...). npm package names are not resolved; use a URL or an HTML import map.",
            "Settings are stored in the work's edit-rin.json and included in backups."
        ), code = "{\n  \"executionMode\": \"module\",\n  \"moduleEntry\": \"main.mjs\",\n  \"libraries\": [],\n  \"p5Url\": null\n}"
    ),
    GuideReferenceEntry(
        title = "p5.js version and p5.sound",
        summary = "2.3.4 vs. 1.11.5, and the sound library.",
        group = "Project and runtime",
        paragraphs = listOf(
            "p5.js 2.3.4 is the default for new works and supports async/await. 1.11.5 is for older sketches. Earlier 2.3.3 settings load as 2.3.4.",
            "p5.sound handles audio playback, synthesis, and analysis, and is off by default. 2.x uses the official 0.4.1; 1.11.5 uses its bundled legacy version.",
            "Works that use legacy APIs such as p5.Part, p5.Phrase, p5.PolySynth, SoundRecorder, or saveSound need 1.11.5.",
            "Processing/Java is not included."
        )
    ),
    GuideReferenceEntry(
        title = "External libraries and HTTPS URLs",
        summary = "How extra libraries load, and in what order.",
        group = "Project and runtime",
        paragraphs = listOf(
            "External HTTPS libraries can be registered as classic scripts or modules. Classic scripts run in registered order before your code. Modules follow browser module rules.",
            "You can also set a custom HTTPS URL for p5.js itself. External URLs need an internet connection."
        ), notes = listOf("Browser limits such as CORS still apply.")
    ),
    GuideReferenceEntry(
        title = "Assets (images, audio, fonts, data)",
        summary = "How to load assets, and the limits.",
        group = "Project and runtime",
        paragraphs = listOf(
            "Import files under Work assets and load them in code as assets/filename. The asset inspector can insert loading code.",
            "When renaming an asset, you can review matching paths and choose which to update.",
            "Limits: 50 MiB per file, 100 files and 200 MiB per work. For project text files in new works, execution is limited to 500 files and 16 MiB total."
        ), code = "let img;\n\nfunction preload() {\n  img = loadImage('assets/photo.png');\n}"
    ),
    GuideReferenceEntry(
        title = "Playing sound",
        summary = "How to load audio and when it can start.",
        group = "Device and output",
        paragraphs = listOf(
            "Add audio under Work assets and use assets/filename. In p5.js 1.x, call loadSound in preload(); in 2.x, use await loadSound() in async setup().",
            "Playback needs a user action such as a tap. With p5.sound on, audio is prepared when you tap the preview. Microphone input also needs the Android permission."
        ), code = "let sound;\n\nasync function setup() {\n  createCanvas(400, 400);\n  sound = await loadSound('assets/music.mp3'); // p5.js 2.x\n}\n\nfunction mousePressed() {\n  if (sound && !sound.isPlaying()) sound.play();\n}"
    ),
    GuideReferenceEntry(
        title = "Camera, microphone, file input, sensors",
        summary = "Functions that use device features, and the permissions they need.",
        group = "Device and output",
        paragraphs = listOf(
            "createCapture() and microphone input request the Android camera or audio permission. If denied, you get a normal browser permission error.",
            "createFileInput() opens the Android file picker, with multiple selection if the input asks for it.",
            "Tilt and acceleration are supported: rotationX/Y/Z, accelerationX/Y/Z, deviceMoved(), deviceTurned(), deviceShaken(). Sensors turn on only while the work uses them.",
            "fullscreen(true) uses Android fullscreen. As in browsers, it needs a user action."
        ), notes = listOf("WebGPU, sensors, fullscreen, and file selection depend on the device and WebView.")
    ),
    GuideReferenceEntry(
        title = "Saving, downloads, capture, recording",
        summary = "p5.js save functions and image/video export.",
        group = "Device and output",
        paragraphs = listOf(
            "saveCanvas, saveJSON, saveStrings, saveTable, saveFrames, saveGif, legacy saveSound, and HTML download links save through Android. Supported formats follow p5.js and WebView.",
            "Files go to Download/Edit-RiN. On Android 6–9 without storage permission, you can pick a destination.",
            "PNG export from the camera button offers 1x, 2x, and 4x. Higher scales redraw at higher resolution, up to 16 megapixels.",
            "Recording supports MP4 and GIF. Settings has the countdown and MP4 bitrate."
        )
    ),
    GuideReferenceEntry(
        title = "WebGPU",
        summary = "What a WebGPU work needs.",
        group = "Device and output",
        paragraphs = listOf(
            "The official p5.js 2.3.4 WebGPU addon is bundled.",
            "WEBGPU needs support from the device GPU and the installed Android WebView."
        )
    ),
    GuideReferenceEntry(
        title = "When something does not work",
        summary = "Common causes and where to check.",
        group = "Troubleshooting",
        paragraphs = listOf(
            "If the preview is outdated, press Run changes. Use the console errors to jump to the code.",
            "If parameters do not appear, check the // @rin format (kind, quotes, number ranges) and that there are 16 or fewer.",
            "If there is no sound, check that p5.sound is on, that you used the loading style for your version, and that you tapped the screen once.",
            "If an npm-style import fails, replace it with a URL or an HTML import map.",
            "If some preview actions fail in an HTML work, check whether the CSP blocks the helper script.",
            "If saving fails, your edits stay in the editor. Use the retry or recovery options shown."
        )
    )
)

private val chineseGuideReference = listOf(
    GuideReferenceEntry(
        title = "参数声明（// @rin）",
        summary = "用一行注释添加滑块、颜色等控件。",
        group = "参数",
        paragraphs = listOf(
            "格式：// @rin 类型 名称 \"显示名\" 值。名称是标识符，在代码中用 rinParams.名称 读取。显示名为 1–40 个字符，用双引号括起。",
            "number：最小 最大 初始值 步长。最小值须小于最大值，初始值在范围内，步长大于 0。",
            "color：一个 #RRGGBB 值。",
            "boolean：true 或 false。",
            "choice：先写初始值，再列出选项。至少 2 个选项，初始值须是其中之一。每个选项为不超过 32 个的字母、数字或 _ . -。"
        ), code = "// @rin number size \"Size\" 20 240 120 10\n// @rin color ink \"Ink\" #D67856\n// @rin boolean fillOn \"Fill\" true\n// @rin choice shape \"Shape\" circle circle square triangle\n\nfunction draw() {\n  background('#F2EFE7');\n  fill(rinParams.ink);\n  if (rinParams.shape === 'circle') circle(width / 2, height / 2, rinParams.size);\n}", notes = listOf("最多 16 个声明。名称重复时只使用第一个。", "格式不符的行会被忽略，不会报错。", "声明是注释，代码可直接运行。参数值按作品保存，复制和备份时也会包含。")
    ),
    GuideReferenceEntry(
        title = "HTML 作品",
        summary = "以 HTML 为入口，自行搭建页面。",
        group = "作品结构与运行",
        paragraphs = listOf(
            "在作品菜单的「项目文件」中，可添加、编辑 index.html、style.css、lib/math.mjs 等文件。名称可含文件夹，相对路径和 import 保持该结构。",
            "运行方式为自动时，若有 index.html 则运行它。",
            "HTML 作品由 script 标签决定 p5.js 版本、库和加载顺序。运行环境对话框的设置用于 JavaScript 和模块作品。",
            "内置 p5 的 CDN 地址会指向本地资源。其他版本和带 CSP 的 HTML 中的地址保持原样。"
        ), notes = listOf("若 CSP 禁止预览辅助脚本，部分预览操作可能无法使用。", "截图和录制只包含画布，不含按钮等 DOM 元素。多个画布会合成。", "暂停对 p5、HTML 动画帧和 CSS 动画有效，普通计时器不会停止。")
    ),
    GuideReferenceEntry(
        title = "运行方式与入口文件",
        summary = "自动、普通 JS、模块、HTML 的区别与加载设置。",
        group = "作品结构与运行",
        paragraphs = listOf(
            "在「运行环境 → 项目加载设置」中，可选择运行方式（自动、普通 JavaScript、模块、HTML）和入口文件。",
            "自动模式下，有 index.html 就运行 HTML。主代码含静态 import/export 时按模块处理。只有一个 .mjs 时，仅当主代码为空且没有普通 JS 文件才会自动选中。",
            "普通 JavaScript 可指定加载顺序；留空则按以往合并的顺序加载。",
            "模块可声明或导出 setup、draw 和输入回调，也可使用 new p5(...)。npm 包名不会被解析，请使用 URL 或 HTML 的 import map。",
            "设置保存在作品的 edit-rin.json 中，备份时也会包含。"
        ), code = "{\n  \"executionMode\": \"module\",\n  \"moduleEntry\": \"main.mjs\",\n  \"libraries\": [],\n  \"p5Url\": null\n}"
    ),
    GuideReferenceEntry(
        title = "p5.js 版本与 p5.sound",
        summary = "2.3.4 与 1.11.5 的区别，以及音频库设置。",
        group = "作品结构与运行",
        paragraphs = listOf(
            "p5.js 2.3.4 是新建作品的默认版本，支持 async/await。1.11.5 用于旧草图。以前的 2.3.3 设置会按 2.3.4 加载。",
            "p5.sound 提供音频播放、合成和分析，默认关闭。2.x 使用官方 0.4.1，1.11.5 使用内置的旧版。",
            "使用 p5.Part、p5.Phrase、p5.PolySynth、SoundRecorder、saveSound 等旧 API 的作品，请选择 1.11.5。",
            "不内置 Processing / Java。"
        )
    ),
    GuideReferenceEntry(
        title = "外部库与 HTTPS 地址",
        summary = "额外库的加载方式与顺序。",
        group = "作品结构与运行",
        paragraphs = listOf(
            "外部 HTTPS 库可注册为普通脚本或模块。普通脚本按注册顺序在作品代码之前执行，模块遵循浏览器的模块规则。",
            "p5.js 本体也可指定自己的 HTTPS 地址。使用外部地址需要联网。"
        ), notes = listOf("CORS 等浏览器限制同样适用。")
    ),
    GuideReferenceEntry(
        title = "素材（图片、音频、字体、数据）",
        summary = "素材的加载方式与上限。",
        group = "作品结构与运行",
        paragraphs = listOf(
            "在作品菜单的「作品素材」中导入，代码里用 assets/文件名 加载。素材检查器可以插入加载代码。",
            "重命名素材时，可查看匹配的路径并选择要更新的位置。",
            "上限：单个文件 50 MiB，每个作品 100 个文件、共 200 MiB。新作品的项目文本文件，运行时限 500 个文件、共 16 MiB。"
        ), code = "let img;\n\nfunction preload() {\n  img = loadImage('assets/photo.png');\n}"
    ),
    GuideReferenceEntry(
        title = "播放声音",
        summary = "音频的加载方式与开始播放的条件。",
        group = "设备与输出",
        paragraphs = listOf(
            "把音频添加到「作品素材」，用 assets/文件名 指定。p5.js 1.x 在 preload() 中调用 loadSound，2.x 在 async setup() 中使用 await loadSound()。",
            "播放需要点击屏幕等用户操作。启用 p5.sound 后，点击预览时开始准备音频。麦克风输入还需要 Android 权限。"
        ), code = "let sound;\n\nasync function setup() {\n  createCanvas(400, 400);\n  sound = await loadSound('assets/music.mp3'); // p5.js 2.x\n}\n\nfunction mousePressed() {\n  if (sound && !sound.isPlaying()) sound.play();\n}"
    ),
    GuideReferenceEntry(
        title = "摄像头、麦克风、文件选择、传感器",
        summary = "使用设备功能的函数及所需权限。",
        group = "设备与输出",
        paragraphs = listOf(
            "使用 createCapture() 或麦克风输入时，会请求 Android 的摄像头或音频权限。拒绝后会出现普通的浏览器权限错误。",
            "createFileInput() 会打开 Android 文件选择界面；输入要求多选时也可多选。",
            "支持倾斜与加速度：rotationX/Y/Z、accelerationX/Y/Z、deviceMoved()、deviceTurned()、deviceShaken()。传感器只在作品使用时开启。",
            "fullscreen(true) 使用 Android 全屏显示。与浏览器一样，需要用户操作。"
        ), notes = listOf("WebGPU、传感器、全屏和文件选择的表现取决于设备和 WebView。")
    ),
    GuideReferenceEntry(
        title = "保存、下载、截图、录制",
        summary = "p5.js 保存函数与图片、视频导出。",
        group = "设备与输出",
        paragraphs = listOf(
            "saveCanvas、saveJSON、saveStrings、saveTable、saveFrames、saveGif、旧版 saveSound 以及 HTML 下载链接，都会通过 Android 保存。可输出的格式取决于 p5.js 和 WebView。",
            "文件通常保存到 Download/Edit-RiN。Android 6–9 没有存储权限时，可选择保存位置。",
            "相机按钮的 PNG 导出可选 1 倍、2 倍、4 倍。倍率越高，重绘的分辨率越高，上限为 1600 万像素。",
            "录制支持 MP4 和 GIF。可在设置中调整倒计时和 MP4 码率。"
        )
    ),
    GuideReferenceEntry(
        title = "WebGPU",
        summary = "WebGPU 作品的运行条件。",
        group = "设备与输出",
        paragraphs = listOf(
            "内置 p5.js 2.3.4 的官方 WebGPU 插件。",
            "使用 WEBGPU 需要设备 GPU 和已安装的 Android WebView 支持。"
        )
    ),
    GuideReferenceEntry(
        title = "无法运行或保存时",
        summary = "常见原因与检查位置。",
        group = "遇到问题时",
        paragraphs = listOf(
            "预览不是最新时，请点击「运行更改」。可从日志中的错误跳转到对应代码。",
            "参数没有出现时，请检查 // @rin 的格式（类型、引号、数值范围）以及声明是否不超过 16 个。",
            "没有声音时，请检查 p5.sound 是否启用、加载方式是否与版本匹配，以及是否点击过屏幕。",
            "npm 包名形式的 import 失败时，请改用 URL 或 HTML 的 import map。",
            "HTML 作品中部分预览操作无法使用时，请检查 CSP 是否禁止了辅助脚本。",
            "保存失败时，编辑内容会保留。请使用显示的重试或恢复选项。"
        )
    )
)
