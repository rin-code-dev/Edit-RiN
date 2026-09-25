package com.hikariatelier.app

internal fun localizedUserGuide(language: String): List<GuideSection> = when (language) {
    "ja" -> japaneseUserGuide
    "zh" -> chineseUserGuide
    else -> userGuideSections
}

private val japaneseUserGuide = listOf(
    GuideSection(
        title = "はじめの一歩",
        summary = "Edit:RiN は Android 上で手軽に p5.js のアートを作成・実行できるエディタです。1つの作品にはメインコード、追加スクリプト、画像・音声素材、各種設定がすべて安全に保存されます。",
        iconRes = R.drawable.ic_play,
        tag = "基本",
        steps = listOf(
            "作品の切り替えと作成: 画面上部のタイトル部分をタップするとギャラリーが開き、作品の新規作成や検索・並び替えによる選択ができます。",
            "リアルタイム実行: sketch.js を編集して「実行」ボタンを押すと、キャンバスが即座に更新されジェネラティブアートが動き出します。",
            "作品メニュー: タイトル横のメニューから作品の名前変更、複製、ZIP書き出し、削除、キャンバス比率（1:1、4:3、16:9、可変）の変更が可能です。"
        )
    ),
    GuideSection(
        title = "ライブパラメータ",
        summary = "コードにコメントを追加するだけで、スライダーやカラーパレット、スイッチなどの調整UIを自動生成できます。コードを書き換えずに数値を微調整できます。",
        iconRes = R.drawable.ic_snippet,
        tag = "おすすめ",
        codeSnippet = """// @rin number speed "速さ" 0 3 1 0.1
// @rin color ink "インク色" #BA90E2
// @rin boolean glow "発光効果" true

function draw() {
  background(20);
  fill(rinParams.ink);
  if (rinParams.glow) drawingContext.shadowBlur = 15;
  circle(width/2, height/2, 60 * rinParams.speed);
}""",
        steps = listOf(
            "宣言の書き方: コードの先頭に // @rin に続けて型（number, color, boolean）、変数名、ラベル名、初期値を記述します。",
            "コードからの参照: 宣言したパラメータは自動的に rinParams.<変数名> で取得できます。スライダーを動かすと描画がリアルタイムに変化します。",
            "操作パネル: 「−」「＋」ボタンでの微調整、個別または一括での「初期値に戻す」、コメントテンプレートのコピー機能が利用できます。"
        )
    ),
    GuideSection(
        title = "シェアカード＆QR共有",
        summary = "作品の美しいスクリーンショットにQRコードを組み合わせた1080pの高画質シェアカードを生成し、Webブラウザ上で誰にでも動かせる作品として共有できます。",
        iconRes = R.drawable.ic_share_card,
        tag = "新機能",
        steps = listOf(
            "カード生成: プレビュー画面のツールバーから「シェアカード」を開くと、現在のキャンバスを取り込んだカード画像が生成されます。",
            "テーマと作者クレジット: ダーク、ミッドナイト、サイバー、ライトの4種のカラーテーマを選べ、作者名やSNSアカウント名（by @...）を印字できます。",
            "ブラウザ直接再生: 生成されたQRコードをスマホのカメラでスキャンするだけで、アプリをインストールしていない人でもWeb上でそのままインタラクティブに作品を体験できます。",
            "ソースコードの公開設定: QRアクセス時に「Web上でソースコードの閲覧を許可するかどうか」をスイッチ1つで自由に選択できます。"
        )
    ),
    GuideSection(
        title = "コードの編集",
        summary = "スマートフォンやタブレットでの快適なタイピングを追求した、本格的なエディタ機能を搭載しています。",
        iconRes = R.drawable.ic_code,
        tag = "エディタ",
        steps = listOf(
            "ファイルタブ切り替え: 複数ファイルのスクリプトを素早く切り替えて編集できます。ファイルの追加は「作品設定 → プロジェクトファイル」から行えます。",
            "ツールバー機能: 元に戻す（Undo）・やり直し（Redo）、検索・置換、行ジャンプ、Prettierスタイルのコード自動整形、コード折りたたみが手元で行えます。",
            "インテリジェント入力補完: p5.js の標準APIや数学定数、コード内で定義した変数・関数名をすばやく候補表示します。",
            "インタラクティブコンソール: 実行ログやエラーを確認できます。エラー行をタップすると該当のコード位置へ直接ジャンプします。",
            "変更履歴と差分復元: 保存履歴から過去の版との差分（Diff）を並べて確認し、安全に以前の状態へ復元できます。"
        )
    ),
    GuideSection(
        title = "素材（アセット）の活用",
        summary = "画像（PNG/JPG/SVG/GIF）、音声（MP3/WAV）、フォント（TTF/OTF）、データ（JSON/CSV）を作品ごとに直接取り込んで呼び出せます。",
        iconRes = R.drawable.ic_folder_code,
        tag = "素材",
        codeSnippet = """let img, snd;
function preload() {
  img = loadImage('assets/texture.png');
  snd = loadSound('assets/beat.mp3');
}""",
        steps = listOf(
            "素材の追加: 「作品設定 → 作品の素材」から端末内のファイルを取り込みます。タップすると画像や音声のプレビューを確認できます。",
            "読み込みコードの自動挿入: 素材プレビュー画面の「読み込みコードを挿入」を押すと、エディタのカーソル位置に必要な preload コードが自動挿入されます。",
            "相対パスでの参照: コードからは 'assets/ファイル名' の相対パスで標準の p5.js ローダー関数から読み込めます。",
            "保存容量: 1ファイルあたり最大50MB、1作品につき最大100ファイル・合計200MBまで保存可能です。"
        )
    ),
    GuideSection(
        title = "実行環境とライブラリ",
        summary = "作品の特性に合わせて、p5.js のバージョンや拡張ライブラリを作品ごとに個別に設定できます。",
        iconRes = R.drawable.ic_settings,
        tag = "環境",
        steps = listOf(
            "p5.js バージョン選択: モダンな WebGL や最新機能に対応した「p5.js 2.3.3」と、従来の作品や軽量描画向けの「p5.js 1.11.5」を切り替えられます。",
            "p5.sound サポート: 音声再生、シンセサイザー合成、FFT周波数解析を行う場合はONにします（画面を初回タップした際に安全にオーディオが開始します）。",
            "p5.brush ライブラリ: 水彩画、鉛筆スケッチ、インクのにじみなど表情豊かなストロークを表現できる p5.brush（p5.js 2.3.3 専用）をワンタップで有効化できます。"
        )
    ),
    GuideSection(
        title = "録画とスクリーンショット",
        summary = "完成したジェネラティブアートの高解像度静止画や、なめらかなループアニメーションを簡単に保存・シェアできます。",
        iconRes = R.drawable.ic_camera,
        tag = "メディア",
        steps = listOf(
            "スクリーンショット: プレビュー上のカメラボタンを押すだけで、現在のキャンバスを高画質PNGとして端末のピクチャフォルダに保存します。",
            "アニメーション録画: アニメーションを MP4 動画（最大60秒）または ループGIF（最大15秒）としてキャンバスから直接録画できます。",
            "画質とカウントダウン設定: 「設定 → 保存とバックアップ」から MP4 のビットレート（最大16Mbps）や録画開始前のカウントダウン秒数を変更できます。",
            "即時シェア: 保存完了後、表示されるダイアログから X（旧Twitter）や各種SNS、共有シートへ直接送信できます。"
        )
    ),
    GuideSection(
        title = "バックアップと移行",
        summary = "万一の端末故障や機種変更に備え、複数の方法で作品データを確実に保護・移行できます。",
        iconRes = R.drawable.ic_save,
        tag = "安心機能",
        steps = listOf(
            "作品ZIPの書き出し・取り込み: 単一の作品をコード・素材・設定まるごとZIPで書き出し、他の端末や友人と受け渡しができます。",
            "全体バックアップ: すべての作品とエディタ設定を1つのZIPにまとめて保存・復元できます。機種変更前のバックアップに最適です。",
            "外部フォルダ指定: クラウド同期フォルダ（Google DriveやNextcloud、SDカード等）を作品保存先に指定し、自動バックアップ環境を構築できます。",
            "p5.js Web Editor からのインポート: 公開されている p5.js Web Editor のユーザー名を入力するだけで、公開スケッチをパスワード不要でインポートできます。"
        )
    )
)

private val chineseUserGuide = listOf(
    GuideSection(
        title = "新手入门",
        summary = "Edit:RiN 是一款专为移动端创意编程打造的 p5.js 编辑器。每个作品均完整包含主脚本、附加模块、媒体素材与运行配置。",
        iconRes = R.drawable.ic_play,
        tag = "基础",
        steps = listOf(
            "作品选择与新建：点击顶部标题栏即可打开画廊，在已有作品间切换、搜索排序或创建全新作品。",
            "实时预览：编辑 sketch.js 后点击“运行”按钮，生成的创意图形即刻在画布中实时动态呈现。",
            "作品菜单：点击标题旁菜单可重命名、复制、导出为 ZIP、删除作品，或自由切换画布宽高比（1:1、4:3、16:9、响应式）。"
        )
    ),
    GuideSection(
        title = "实时参数",
        summary = "只需在代码中编写简单注释，即可自动生成滑块、调色板和布尔开关，无需手动编写复杂的界面代码。",
        iconRes = R.drawable.ic_snippet,
        tag = "互动",
        codeSnippet = """// @rin number speed "速度" 0 3 1 0.1
// @rin color ink "墨水颜色" #BA90E2
// @rin boolean glow "发光效果" true

function draw() {
  background(20);
  fill(rinParams.ink);
  if (rinParams.glow) drawingContext.shadowBlur = 15;
  circle(width/2, height/2, 60 * rinParams.speed);
}""",
        steps = listOf(
            "声明语法：在代码开头编写 // @rin，后接类型（number、color、boolean）、变量名、标签和默认值。",
            "代码调用：通过 rinParams.<变量名> 随时获取参数值，调节滑块时画面即刻响应，无需重新加载。",
            "调节抽屉：提供 +/- 步长微调、单项或全部恢复默认值，以及一键复制参数模板功能。"
        )
    ),
    GuideSection(
        title = "分享卡片与网页播放",
        summary = "一键生成嵌入高清二维码的 1080p 精美分享卡片，任何人只需扫码即可在手机或电脑浏览器中直接运行作品。",
        iconRes = R.drawable.ic_share_card,
        tag = "新特性",
        steps = listOf(
            "生成卡片：在预览工具栏中点击“分享卡片”，应用会自动截取画布并生成高分辨率专属二维码。",
            "个性化样式：支持深色、午夜、赛博、浅色四款主题配色，并可自由署名作者信息（by @...）。",
            "免安装网页运行：他人使用手机相机或扫码工具扫描二维码，即可在浏览器中免安装流畅体验动态作品。",
            "代码公开控制：扫码访问时，可自由设置是否允许他人查看作品的源码。"
        )
    ),
    GuideSection(
        title = "代码编辑",
        summary = "针对触控设备深度优化的专业级移动代码编辑环境。",
        iconRes = R.drawable.ic_code,
        tag = "编辑",
        steps = listOf(
            "多文件标签：轻松在多个 JavaScript 模块间切换，可在“作品设置 → 项目文件”中增删管理。",
            "工具栏辅助：快捷撤销/重做、查找与替换、行跳转、Prettier 风格一键格式化与代码块折叠。",
            "智能补全：精准推荐 p5.js API、数学常量以及脚本中自定义的变量与函数名。",
            "交互式控制台：实时查看日志与异常堆栈，轻触报错信息即可直达错误代码行。",
            "版本比对与还原：并排对比历史保存记录的 Diff 差异，随时安全还原早期版本。"
        )
    ),
    GuideSection(
        title = "素材管理",
        summary = "在每个作品中独立管理图像（PNG/JPG/SVG/GIF）、音频、字体（TTF/OTF）和数据文件（JSON/CSV）。",
        iconRes = R.drawable.ic_folder_code,
        tag = "素材",
        codeSnippet = """let img, snd;
function preload() {
  img = loadImage('assets/texture.png');
  snd = loadSound('assets/beat.mp3');
}""",
        steps = listOf(
            "添加文件：通过“作品设置 → 作品素材”从设备中导入文件，轻触素材即可快速预览效果。",
            "自动插入加载代码：在素材预览界面点击“插入加载代码”，即可在当前光标处自动生成 preload 代码。",
            "相对路径引用：代码中直接使用 'assets/<文件名>' 即可通过标准 p5.js 接口加载。",
            "容量支持：单文件最大支持 50MB，单个作品最多容纳 100 个素材、总容量 200MB。"
        )
    ),
    GuideSection(
        title = "运行环境与库",
        summary = "根据作品需求自由选择最适宜的 p5.js 版本与内置图形扩展库。",
        iconRes = R.drawable.ic_settings,
        tag = "环境",
        steps = listOf(
            "版本选择：支持现代 WebGL 与最新特性的「p5.js 2.3.3」与轻量稳定的兼容版本「p5.js 1.11.5」。",
            "p5.sound 音频：开启后支持音频合成、分析与回放（首次轻触画布时启动音频引擎）。",
            "p5.brush 水彩笔触：在 p5.js 2.3.3 作品中可一键启用水彩与素描表现力极佳的 p5.brush 库。"
        )
    ),
    GuideSection(
        title = "录制与截图",
        summary = "轻松记录高分辨率静态画面与动态循环动画，分享至社交网络。",
        iconRes = R.drawable.ic_camera,
        tag = "媒体",
        steps = listOf(
            "截图保存：轻触预览工具栏的相机图标，即可将画布即时保存为无损 PNG 图像至相册。",
            "视频录制：直接从画布录制 MP4 视频（最长 60 秒）或 GIF 动图（最长 15 秒）。",
            "码率与倒计时：在“设置 → 保存与备份”中调节录制画质码率（最高 16Mbps）与倒计时秒数。",
            "一键分享：录制完成后可通过系统分享面板直接发送到其他社交应用。"
        )
    ),
    GuideSection(
        title = "备份与云同步",
        summary = "全方位的本地与云端备份策略，全面守护您的创作成果。",
        iconRes = R.drawable.ic_save,
        tag = "安全",
        steps = listOf(
            "单作品 ZIP：将当前作品的代码、素材与配置打包导出，方便与好友交换。",
            "完整备份：换机或重置手机前，一键打包所有作品与设置偏好为一个完整 ZIP 归档。",
            "外部存储目录：将作品存储路径指定为云同步文件夹（如 Google Drive 或 SD 卡），实现自动同步。",
            "Web Editor 导入：输入公开用户名即可免密导入 p5.js Web Editor 上的公开作品。"
        )
    )
)
