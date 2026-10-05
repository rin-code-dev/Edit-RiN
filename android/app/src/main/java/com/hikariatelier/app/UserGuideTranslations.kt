package com.hikariatelier.app

internal fun localizedUserGuide(language: String): List<GuideSection> = listOf(authoringUserGuide(language)) + when (language) {
    "ja" -> japaneseUserGuide
    "zh" -> chineseUserGuide
    else -> userGuideSections
}

private val japaneseUserGuide = listOf(
    GuideSection(
        title = "はじめの一歩とフォルダー管理",
        summary = "Edit:RiN は Android 上で手軽に p5.js のアートを作成・実行できるエディタです。フォルダーによる作品整理や公式サンプルの閲覧専用保護、リアルタイム実行に対応しています。",
        iconRes = R.drawable.ic_play,
        tag = "基本",
        steps = listOf(
            "作品の切り替えと作成: 画面上部のタイトル部分をタップするとギャラリーが開き、作品の新規作成や検索・タグ絞り込みができます。",
            "フォルダー整理と並び替え: ユーザー作品を1階層のフォルダーで整理できます。フォルダーのチップを長押しして左右にドラッグすると隣のタブと位置を入れ替えられます（「自分の作品」は常に先頭固定）。フォルダーを削除しても作品は未分類に残る安心設計です。",
            "公式サンプルの保護とコピー編集: 同梱サンプルは閲覧専用の原本として保護されています。ライブパラメータを動かして試したあと、「コピーして編集」を押すと試した設定を引き継いで自分の作品として安全に複製・編集できます。",
            "一括操作: 複数作品を選択し、一括でフォルダーへ移動、タグ付け・解除、ZIP書き出し、削除が行えます。",
            "キャンバス比率と作品メニュー: タイトル横のメニューからキャンバス比率（端末横、16:9、4:3、1:1、9:16、端末縦）の変更、作品の複製・書き出し・設定変更が可能です。"
        )
    ),
    GuideSection(
        title = "コード編集と便利ツール",
        summary = "作品を行き来しても編集状態を保持するセッション保護、コンパクトな検索・置換バー、プレビュー変更未反映検知などを備えています。",
        iconRes = R.drawable.ic_code,
        tag = "エディタ",
        steps = listOf(
            "編集セッションの保持: 別の作品に切り替えて戻っても、直前に編集していたファイル、カーソル位置、スクロール位置、元に戻す（Undo）・やり直し（Redo）の履歴をそのまま引き継ぎます。",
            "コンパクト検索・置換バー: エディタ上部に固定される省スペースなバーで現在のファイルを検索・置換できます。一致箇所のハイライト表示や置換後のカーソル位置保持に対応し、作品全体の検索も引き続き利用できます。",
            "プレビュー変更未反映の検知: コードを編集したあと実行していない場合、「変更未反映」と表示されます。「変更を実行」を押すと即座に反映され、過去の実行エラーと区別して表示されます。",
            "マルチファイル管理: 複数ファイルのスクリプトやHTML/CSSをタブで快適に切り替えて編集できます。追加は「作品設定 → プロジェクトファイル」から行えます。",
            "編集補助ツール: Prettierスタイルの自動整形、コード折りたたみ、行ジャンプ、p5.js APIのインテリジェント入力補完、エラー行へのワンタップジャンプを完備しています。"
        )
    ),
    GuideSection(
        title = "ライブパラメータ",
        summary = "コードにコメントを追加するだけで、スライダーやカラーパレット、スイッチなどの調整UIを自動生成できます。コードを書き換えずに数値を微調整できます。",
        iconRes = R.drawable.ic_tune,
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
        title = "素材と参照パス連携",
        summary = "画像、音声、フォント、データを取り込み、素材名変更時にはコード内のパス参照も一括で安全に同期できます。",
        iconRes = R.drawable.ic_assets,
        tag = "素材",
        codeSnippet = """let img, snd;
function preload() {
  img = loadImage('assets/texture.png');
  snd = loadSound('assets/beat.mp3');
}""",
        steps = listOf(
            "素材の追加: 「作品設定 → 作品の素材」から端末内のファイルを取り込みます。画像や音声のプレビューをその場で確認できます。",
            "読み込みコードの自動挿入: 素材プレビュー画面の「読み込みコードを挿入」を押すと、エディタのカーソル位置に必要な preload コードが自動挿入されます。",
            "素材名とコード参照の一括更新: 素材の名前を変更する際、コード内の固定パス文字列を検出してまとめて更新できます。変更箇所を事前プレビューでき、保存失敗時も編集内容が安全に保護されます。",
            "保存容量: 1ファイルあたり最大50MB、1作品につき最大100ファイル・合計200MBまで保存可能です。"
        )
    ),
    GuideSection(
        title = "実行環境・WebGPU・ハードウェア",
        summary = "p5.js のバージョン選択に加え、最新のWebGPU描画、端末カメラ・マイク入力によるインタラクティブ表現に対応しています。",
        iconRes = R.drawable.ic_terminal,
        tag = "環境",
        steps = listOf(
            "p5.js バージョン選択: モダンな WebGL や最新機能に対応した「p5.js 2.3.4」と、軽量描画向けの「p5.js 1.11.5」を切り替えられます。",
            "次世代 WebGPU 対応: WebGPU を指定してハードウェア描画を実行できます（非対応端末では自動的に WebGL へ安全にフォールバック）。",
            "カメラ＆マイク対応: スマートフォンの実機カメラ（createCapture）やマイク入力（p5.AudioIn）をスケッチ内でリアルタイムに活用できます。",
            "オーディオ＆物理演算: p5.js 2.x の最新サウンド環境（MONO SYNTH SCOPE スケッチ等）に対応し、初回タップで安全に音声を起動。Matter.js や p5.brush ライブラリもワンタップで利用可能です。"
        )
    ),
    GuideSection(
        title = "録画・撮影とリカバリー保護",
        summary = "高解像度静止画やなめらかなループ動画を撮影でき、万一の保存失敗時も録画データを安全に保持・救出できます。",
        iconRes = R.drawable.ic_camera,
        tag = "メディア",
        steps = listOf(
            "高解像度PNG撮影: プレビュー画面から1x・2x・4x（最大1600万画素）でオフスクリーン描画保存。保存完了シートから直接画像を開く・共有が可能です。",
            "MP4動画・GIF録画: アニメーションを MP4 動画（最大60秒）または ループGIF（最大15秒）としてキャンバスから直接録画できます。",
            "録画リカバリー機能: 保存に失敗した場合でも録画データを一時領域に保持。「再保存」「別の保存先を選ぶ」「明示的に破棄」を選択して確実にデータを救出できます。",
            "画質とカウントダウン設定: 「設定」から MP4 のビットレート（最大16Mbps）や録画開始前のカウントダウン秒数を変更できます。"
        )
    ),
    GuideSection(
        title = "シェアカード＆QR共有",
        summary = "作品の美しいスクリーンショットにQRコードを組み合わせた1080pの高画質シェアカードを生成し、Webブラウザ上で誰にでも動かせる作品として共有できます。",
        iconRes = R.drawable.ic_share_card,
        tag = "共有",
        steps = listOf(
            "カード生成: プレビュー画面のツールバーから「シェアカード」を開くと、現在のキャンバスを取り込んだカード画像が生成されます。",
            "テーマと作者クレジット: ダーク、ミッドナイト、サイバー、ライトの4種のカラーテーマを選べ、作者名やSNSアカウント名（by @...）を印字できます。",
            "ブラウザ直接再生: 生成されたQRコードをスマホのカメラでスキャンするだけで、アプリをインストールしていない人でもWeb上でそのままインタラクティブに作品を体験できます。",
            "ソースコードの公開設定: QRアクセス時に「Web上でソースコードの閲覧を許可するかどうか」をスイッチ1つで自由に選択できます。"
        )
    ),
    GuideSection(
        title = "バックアップと安心保存",
        summary = "万一の端末故障や機種変更に備え、複数の方法で作品データを確実に保護・移行できます。",
        iconRes = R.drawable.ic_save,
        tag = "安心機能",
        steps = listOf(
            "作品ZIPの書き出し・取り込み: 単一の作品をコード・素材・設定まるごとZIPで書き出し、他の端末や友人と受け渡しができます。",
            "全体バックアップ: すべての作品、自作テンプレート、エディタ設定を1つのZIPにまとめて保存・復元できます。機種変更前のバックアップに最適です。",
            "外部フォルダ指定と自動同期: 端末内の特定フォルダやSDカードを作品保存先に指定できます。同期アプリ（FolderSyncやNextcloud等）と連携した端末内フォルダを指定することで、クラウドへの自動バックアップ環境を構築できます（※Androidの仕様上、Google Drive等をフォルダ選択で直接指定することはできません）。",
            "p5.js Web Editor からのインポート: 公開されている p5.js Web Editor のユーザー名を入力するだけで、公開スケッチをパスワード不要でインポートできます。"
        )
    )
)

private val chineseUserGuide = listOf(
    GuideSection(
        title = "新手入门与文件夹管理",
        summary = "Edit:RiN 是一款专为移动端创意编程打造的 p5.js 编辑器。支持文件夹整理、内置示例只读保护与实时创作运行。",
        iconRes = R.drawable.ic_play,
        tag = "基础",
        steps = listOf(
            "作品选择与新建：点击顶部标题栏即可打开画廊，在已有作品间切换、搜索标签或创建全新作品。",
            "文件夹整理与排序：支持通过单层文件夹分类整理作品。长按文件夹标签并左右拖动，即可与相邻标签调换顺序（“我的作品”始终固定在首位）。删除文件夹时，其中的作品会自动保留在“未分类”中，安全无忧。",
            "内置示例只读保护与复制：官方示例作为只读范本受到保护。自由调节参数体验后，点击“复制后编辑”即可保留当前试调的参数并复制为个人作品。",
            "批量管理操作：支持多选作品，一并移入文件夹、添加/移除标签、导出为 ZIP 或批量删除。",
            "画布比例与作品菜单：点击标题旁菜单可自由切换画布宽高比（设备横屏、16:9、4:3、1:1、9:16、设备竖屏），并进行复制、导出与参数配置。"
        )
    ),
    GuideSection(
        title = "代码编辑与智能工具",
        summary = "具备跨作品切换保留光标与历史记录的会话保持机制、紧凑型搜索替换栏以及运行状态实时同步。",
        iconRes = R.drawable.ic_code,
        tag = "编辑",
        steps = listOf(
            "编辑会话状态保持：在多个作品间切换后返回，自动恢复上次编辑的文件、精准光标位置、滚动距离以及撤销/重做（Undo/Redo）历史，多任务编辑流畅自如。",
            "紧凑型查找与替换：编辑器顶部内置紧凑工具栏，支持高亮匹配项并保持替换后的光标位置，同时保留全局作品搜索能力。",
            "运行状态未应用提示：当代码改动尚未同步至画布时会显示“更改未应用”，点击“运行更改”即可即时执行，清晰区分当前修改与上次运行的错误。",
            "多文件模块管理：在顶部标签栏轻松切换 JavaScript 脚本或 HTML/CSS 文件，可在“作品设置 → 项目文件”中增删管理。",
            "智能辅助工具：具备 Prettier 风格一键自动格式化、代码折叠、行跳转、智能 API 补全以及点击错误信息直达出错行功能。"
        )
    ),
    GuideSection(
        title = "实时参数",
        summary = "只需在代码中编写简单注释，即可自动生成滑块、调色板和布尔开关，无需手动编写复杂的界面代码。",
        iconRes = R.drawable.ic_tune,
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
            "参数面板：提供 +/- 步长微调、单项或全部恢复默认值，以及一键复制参数模板功能。"
        )
    ),
    GuideSection(
        title = "素材管理与引用同步",
        summary = "在每个作品中独立管理图像、音频、字体和数据文件，重命名素材时自动同步代码中的路径引用。",
        iconRes = R.drawable.ic_assets,
        tag = "素材",
        codeSnippet = """let img, snd;
function preload() {
  img = loadImage('assets/texture.png');
  snd = loadSound('assets/beat.mp3');
}""",
        steps = listOf(
            "添加文件：通过“作品设置 → 作品素材”从设备中导入文件，轻触素材即可快速预览画面与音频。",
            "自动插入加载代码：在素材预览界面点击“插入加载代码”，即可在当前光标处自动生成 preload 代码。",
            "素材重命名联动更新：修改素材名称时，系统会自动扫描项目代码中完全匹配的路径字符串并批量替换，提供更新预览且在保存失败时安全保留修改。",
            "容量支持：单文件最大支持 50MB，单个作品最多容纳 100 个素材、总容量 200MB。"
        )
    ),
    GuideSection(
        title = "运行环境、WebGPU与硬件",
        summary = "自由选择 p5.js 版本，支持新一代 WebGPU 硬件渲染，以及移动端相机与麦克风输入互动。",
        iconRes = R.drawable.ic_terminal,
        tag = "环境",
        steps = listOf(
            "版本选择：支持现代 WebGL 与最新特性的「p5.js 2.3.4」与轻量稳定的兼容版本「p5.js 1.11.5」。",
            "新一代 WebGPU 支持：支持开启 WebGPU 硬件渲染模式（在不支持的设备上自动安全降级至 WebGL）。",
            "实机相机与麦克风：可在作品中直接调用移动设备摄像头（createCapture）与麦克风音频流（p5.AudioIn）进行互动。",
            "音频引擎与着色器：支持 p5.js 2.x 最新音频环境（包含 MONO SYNTH SCOPE 范例），轻触画布平滑启动音频，并内置 Matter.js 物理库与 p5.brush 笔触库。"
        )
    ),
    GuideSection(
        title = "录制、截图与安全恢复",
        summary = "轻松记录高分辨率静态画面与动态循环动画，具备录制失败安全保留与恢复机制。",
        iconRes = R.drawable.ic_camera,
        tag = "媒体",
        steps = listOf(
            "高分辨率截图：支持 1x、2x、4x（最高 1600 万像素）离屏渲染保存无损 PNG，保存成功后可直接打开或一键分享。",
            "视频与 GIF 录制：直接从画布录制 MP4 视频（最长 60 秒）或 GIF 动图（最长 15 秒）。",
            "录制防丢恢复：若保存过程中发生异常，录制文件将保存在临时存储中，支持“重新保存”、“选择其他位置”或明示丢弃，防止创作成果丢失。",
            "画质与倒计时：在“设置”中自由调节 MP4 录制码率（最高 16Mbps）与录制开始前倒计时秒数。"
        )
    ),
    GuideSection(
        title = "分享卡片与网页播放",
        summary = "一键生成嵌入高清二维码的 1080p 精美分享卡片，任何人只需扫码即可在手机或电脑浏览器中直接运行作品。",
        iconRes = R.drawable.ic_share_card,
        tag = "分享",
        steps = listOf(
            "生成卡片：在预览工具栏中点击“分享卡片”，应用会自动截取画布并生成高分辨率专属二维码。",
            "个性化样式：支持深色、午夜、赛博、浅色四款主题配色，并可自由署名作者信息（by @...）。",
            "免安装网页运行：他人使用手机相机或扫码工具扫描二维码，即可在浏览器中免安装流畅体验动态作品。",
            "代码公开控制：扫码访问时，可自由设置是否允许他人查看作品的源码。"
        )
    ),
    GuideSection(
        title = "备份与云同步",
        summary = "全方位的本地与云端备份策略，全面守护您的创作成果。",
        iconRes = R.drawable.ic_save,
        tag = "安全",
        steps = listOf(
            "单作品 ZIP：将当前作品的代码、素材与配置打包导出，方便与好友交换。",
            "完整备份：换机或重置手机前，一键打包所有作品、自定义模板与设置偏好为一个完整 ZIP 归档。",
            "外部文件夹指定与云同步：可将作品保存路径指定为设备本地文件夹或 SD 卡。搭配第三方同步工具（如 FolderSync、Nextcloud 等）管理的本地文件夹，即可实现作品自动同步至云端（注：受 Android 系统限制，无法直接在目录选择器中选中 Google Drive 等云端根目录）。",
            "Web Editor 导入：输入公开用户名即可免密导入 p5.js Web Editor 上的公开作品。"
        )
    )
)

private fun authoringUserGuide(language: String): GuideSection = when (language) {
    "ja" -> GuideSection("制作ツールと高解像度出力", "多彩な新規テンプレート、自作テンプレート、GUIパラメータ作成、定番コードスニペット、スナップショット命名とメモ編集、高画質PNG書き出しを活用して作品を仕上げます。", R.drawable.ic_snippet, "制作", steps = listOf(
        "新規テンプレート: 2D基本、3D WebGL、カスタムシェーダー、物理演算（Matter.js）、パラメータからベースを選んで新規作成できます。必要なライブラリは自動で設定されます。",
        "自作テンプレート: 作品メニューの「テンプレートとして保存」で現在の編集内容・補助ファイル・素材・実行設定をこの端末のアプリ内に登録できます。新規作成の「自作テンプレート」で選択して作成。元作品の削除後も利用でき、テンプレートの削除は作成済み作品に影響しません。",
        "ライブパラメータ: パラメータ画面から「コードに直接挿入」でサンプルを追加したり、「＋ パラメータ追加」でスライダー・カラー・スイッチを直感的に定義できます。コードからは rinParams.<名前> でリアルタイムに参照されます（最大16個）。",
        "コードスニペット: 作品メニューやキーボード上の {…} から、周期運動やパーティクルなどの定番コードをカーソル位置へワンタップで挿入できます。",
        "スナップショットの命名とメモ: 履歴画面で各スナップショットにタイトルやメモを付け、記録内容を変えずに後から編集できます。Diff画面では追加（緑）と削除（赤）を視覚的に比較可能です。",
        "高解像度PNG書き出し: プレビュー画面のカメラアイコンから1x・2x・4xを選択して保存できます。オフスクリーンで高精細に再描画し、Pictures/Edit-RiN フォルダへ美しく保存。結果画面から直接画像を開く・共有が可能です（最大1600万画素）。"
    ))
    "zh" -> GuideSection("创作工具与高分辨率导出", "利用全新创作模板、自定义模板、GUI交互参数、常用代码片段、快照命名与备注编辑以及高分辨率PNG导出，全面提升创作效率与画质。", R.drawable.ic_snippet, "创作", steps = listOf(
        "新建模板：创建作品时可自由选择基础2D、3D WebGL、自定义着色器、Matter.js物理模拟或参数模板，所需扩展库将自动启用配置。",
        "自定义模板：在作品菜单中选择“保存为模板”，将当前编辑内容、辅助文件、素材和运行设置保存到本设备的应用内。新建作品时从“自定义模板”选择使用。删除原作品后仍可使用，删除模板不影响已创建的作品。",
        "实时参数：在参数面板中点击“直接插入代码”或“＋ 添加参数”，即可可视化创建滑块、调色盘和切换开关。代码中直接通过 rinParams.<名称> 实时读取（最多支持16个）。",
        "代码片段：通过作品菜单或键盘辅助栏的 {…} 按钮，一键在光标处插入周期运动、粒子系统等常用结构。",
        "快照命名与备注编辑：在历史记录中为快照添加自定义标题和备注说明，无需修改代码即可随时编辑详情，并支持直观比对新增（绿色）与删除（红色）差异。",
        "高分辨率PNG导出：点击预览工具栏相机图标，可选择1x、2x或4x倍率重新渲染并保存至 Pictures/Edit-RiN（最高1600万像素），保存完成后可直接打开或分享。"
    ))
    else -> GuideSection("Authoring Tools & High-Res Export", "Accelerate your creative coding with templates, custom templates, interactive GUI parameters, code snippets, snapshot naming & notes, and high-resolution PNG export.", R.drawable.ic_snippet, "Create", steps = listOf(
        "Creative Templates: Choose from 2D Basics, 3D WebGL, Custom Shaders, Matter.js Physics, or Live Parameters when creating a work. Required libraries configure automatically.",
        "My Templates: Use Save as template in the work menu to store current edits, supporting files, assets and runtime settings in this app on this device. Select My templates when creating a work. Templates survive deletion of their source work; deleting a template keeps existing works.",
        "Live Parameters: Tap \"Insert into code\" for instant samples, or \"＋ Add parameter\" to define sliders, color pickers, and toggles without writing comments manually. Access in code via rinParams.<name> (up to 16 parameters).",
        "Code Snippets: Insert motion loops, particles, and interaction patterns directly at the cursor from the work menu or the {…} keyboard button.",
        "Snapshot Naming & Notes: Assign titles and notes to historical snapshots and edit their details without altering code. Review changes with color-coded additions (green) and deletions (red).",
        "High-Res PNG Export: Tap the camera button on the preview toolbar to export in 1x, 2x, or 4x (up to 16 MP). Directly open or share images from the save result screen."
    ))
}
