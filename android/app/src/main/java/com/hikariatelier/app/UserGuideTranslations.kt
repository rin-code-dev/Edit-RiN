package com.hikariatelier.app

internal fun localizedUserGuide(language: String): List<GuideSection> = when (language) {
    "ja" -> japaneseUserGuide
    "zh" -> chineseUserGuide
    else -> userGuideSections
}

private val japaneseUserGuide by lazy { listOf(
    GuideSection(
        title = "サンプルを試す",
        summary = "用意された作品を動かして、コードと見た目の関係を確認します。",
        iconRes = R.drawable.ic_play,
        tag = "はじめに",
        steps = listOf(
            "作品選択の「サンプル」から Palette を開き、キャンバスをタップして配色の変化を確かめます。",
            "上部の作品名をタップすると作品一覧が開き、「サンプル」と「自分の作品」を切り替えられます。",
            "サンプルの原本は上書きできない仕組みですが、コードの選択やコピー、折りたたみ、パラメータ操作は試せます。",
            "「コピーして編集」を選び、名前を付けて複製すると、自分の作品として自由に書き換えられます。"
        )
    ),
    GuideSection(
        title = "自分の作品を作る",
        summary = "テンプレートとキャンバス比率を選んで、新しい作品を作り始めます。",
        iconRes = R.drawable.ic_snippet,
        tag = "作成",
        steps = listOf(
            "作品一覧の「＋」ボタンを押し、作品名と最初に使うテンプレートを選びます。",
            "2D・アニメーション・タッチ・パラメータといった基本テンプレートに加え、WebGL・シェーダー・物理演算・WebGPU も選べます。",
            "画面サイズに合わせる比率のほか、「自由指定」でピクセル単位の固定サイズも設定できます。",
            "作品メニューの「テンプレートとして保存」を使うと、コードや追加ファイル、素材、実行設定ごとひな形として残せます。"
        )
    ),
    GuideSection(
        title = "編集して実行する",
        summary = "折りたたみ、数値の調整、検索、エラー箇所への移動を使ってコードを編集します。",
        iconRes = R.drawable.ic_code,
        tag = "編集",
        steps = listOf(
            "元に戻す・やり直すや、キーボード上のカーソル移動・記号・スニペットバーを使ってコードを書けます。",
            "行番号横の三角をタップするとコードブロックを折りたためます。長い行を折り返すかどうかは設定のエディター欄で変更できます。",
            "作品内の文字列検索・置換やコード整形、p5.js リファレンスを利用でき、ログのエラーからは該当する行へ移動できます。",
            "sketch.js の上部バーから「コード」と「ブロック」を切り替えられます。どちらで編集しても元の JavaScript コードが基準になります。"
        )
    ),
    GuideSection(
        title = "パラメータを調整する",
        summary = "スライダーやカラーピッカーで、実行中の作品の見た目を変えられます。",
        iconRes = R.drawable.ic_tune,
        tag = "調整",
        codeSnippet = "// @rin number diameter \"Diameter\" 20 240 120 10\n// @rin color ink \"Ink\" #D67856\n\nfunction setup() {\n  createCanvas(600, 600);\n}\n\nfunction draw() {\n  background('#F2EFE7');\n  noStroke();\n  fill(rinParams.ink);\n  circle(width / 2, height / 2, rinParams.diameter);\n}",
        steps = listOf(
            "プレビュー操作バーのパラメータボタンを押すと、コードに書いた調整項目が開きます。",
            "コードに // @rin、// @slider、// @color などのコメントを書くか、「＋ パラメータ追加」から宣言を挿入できます。",
            "スライダーの移動や「−」「＋」ボタン、色選択がプレビューに反映されます。// @slider などはコード側の数値も書き換わります。",
            "調整した値は作品ごとに保存され、項目ごとまたは全体まとめて初期値へ戻せます。"
        )
    ),
    GuideSection(
        title = "ブロックで組み立てる",
        summary = "JavaScript のコードを保ったまま、ブロックを組み合わせて作品を編集します。",
        iconRes = R.drawable.ic_folder_code,
        tag = "ブロック",
        codeSnippet = "function setup() {\n  createCanvas(400, 400);\n}\n\nfunction draw() {\n  background(240);\n  circle(mouseX, mouseY, 80);\n}",
        steps = listOf(
            "sketch.js の上部バーで「ブロック」を選ぶと、広いブロック編集画面と小窓プレビューが表示されます。",
            "下部バーの「ブロックを追加」を押し、挿入位置を確かめて一覧から追加したいブロックを選びます。",
            "ブロック内の色や数値をタップして変更したり、ブロックをタップして複製・削除・コード確認を行えます。",
            "処理の一覧から目的の関数へ移動したり、下部バーや小窓のボタンから全画面プレビューを開いたりできます。"
        )
    ),
    GuideSection(
        title = "ファイル・素材・ライブラリ",
        summary = "複数ファイルの構成や、画像・音声素材、外部ライブラリを作品に組み込みます。",
        iconRes = R.drawable.ic_assets,
        tag = "構成",
        steps = listOf(
            "作品メニューの「プロジェクトファイル」で追加の JS・HTML・CSS ファイルを作成し、上部タブで切り替えられます。",
            "画像・音声・フォント・データを読み込み、素材画面のボタンから assets/ パス付きの読み込みコードを挿入できます。",
            "素材名を変更するときは、コード内の同じパスを検索して更新する箇所を選べます（上限は 1 ファイル 50 MiB、合計 200 MiB）。",
            "作品設定で p5.js 2.3.4 / 1.11.5 や p5.sound、外部ライブラリを設定できます。カメラやマイクは権限を許可すると動きます。"
        )
    ),
    GuideSection(
        title = "保存と復元",
        summary = "自動保存の履歴やスナップショット、下書き復元で作業内容を保護します。",
        iconRes = R.drawable.ic_history,
        tag = "保存",
        steps = listOf(
            "「保存」で作品の全ファイルを保存し、隣の「履歴」から自動保存履歴と名前付きスナップショットを確認できます。",
            "履歴画面では過去のコード内容や現在のコードとの一致を確認でき、メモ付きでスナップショットを残せます。",
            "設定で「アプリを離れるときに自動保存」や「未保存のコードを復元」をオンにしておくと、終了や中断のあとも復元できます。",
            "書き込みに失敗しても編集中のコードはそのまま残るため、再試行や別名での保護を選べます。"
        )
    ),
    GuideSection(
        title = "画像・録画を書き出す",
        summary = "高解像度の PNG 画像や MP4・GIF 動画を書き出して共有します。",
        iconRes = R.drawable.ic_camera,
        tag = "出力",
        steps = listOf(
            "カメラボタンから 1x・2x・4x（上限 1600 万画素）を選び、描き直した PNG 画像を保存できます。",
            "録画ボタンから MP4 または GIF 形式で保存でき、ビットレートや開始カウントダウンは設定から変更できます。",
            "保存後の確認画面から、作成した画像や動画をそのまま開いたり、他のアプリへ共有したりできます。",
            "書き込みに失敗したときは再試行や別の保存先の指定ができ、破棄を選ぶまで一時録画データは消えません。"
        )
    ),
    GuideSection(
        title = "整理とバックアップ",
        summary = "フォルダーやタグで作品を整理し、ZIP ファイルにまとめて保管します。",
        iconRes = R.drawable.ic_save,
        tag = "管理",
        steps = listOf(
            "作品一覧でフォルダー分け・タグ絞り込み・検索ができます。フォルダーを削除しても、中の作品は「未分類」として残ります。",
            "複数の作品を選んでまとめて移動・タグ付け・書き出しができます。作品ごとの ZIP にはコード・素材・設定が含まれます。",
            "設定の「保存とデータ」から、すべての作品・自作テンプレート・設定を 1 つの ZIP ファイルに保存・復元できます。",
            "保存先フォルダーを外部アプリと共有する場所にしておくと管理しやすくなります（自動でクラウドへ送信されることはありません）。"
        )
    )
) }

private val chineseUserGuide by lazy { listOf(
    GuideSection(
        title = "体验示例",
        summary = "运行内置示例，了解画面效果与代码之间的对应关系。",
        iconRes = R.drawable.ic_play,
        tag = "入门",
        steps = listOf(
            "在作品列表的“示例”中打开 Palette，点击画布即可切换配色。",
            "点击顶部作品名称可打开列表，在“示例”与“我的作品”之间切换。",
            "示例原件为只读状态以防误改，但可以复制代码、折叠代码块，以及调整参数。",
            "选择“复制后编辑”并输入名称，即可作为自己的作品自由修改。"
        )
    ),
    GuideSection(
        title = "创建自己的作品",
        summary = "选择模板和画布比例，开始编写新的作品。",
        iconRes = R.drawable.ic_snippet,
        tag = "创建",
        steps = listOf(
            "在作品列表点击“＋”按钮，填写名称并挑选起始模板。",
            "除了 2D、动画、触摸和参数模板外，还提供 WebGL、着色器、物理模拟和 WebGPU 模板。",
            "可选择随屏幕缩放的预览比例，或使用“自定义”指定固定像素尺寸。",
            "通过作品菜单中的“保存为模板”，可将代码、附加文件、素材和运行配置保存起来供下次复用。"
        )
    ),
    GuideSection(
        title = "编辑并运行",
        summary = "使用代码折叠、数值微调、查找与错误定位等功能编写代码。",
        iconRes = R.drawable.ic_code,
        tag = "编辑",
        steps = listOf(
            "使用撤销/重做以及键盘上方的光标、符号与代码片段栏输入和调整代码。",
            "点击行号旁的三角图标可折叠代码块；可在“设置 → 编辑器”切换长行是否自动换行。",
            "支持作品内搜索与替换、代码格式化以及查阅 p5.js 参考；点击日志中的报错可直接跳转到对应行。",
            "在 sketch.js 顶部栏可随时切换“代码”和“积木”，始终以 JavaScript 源码为准。"
        )
    ),
    GuideSection(
        title = "调整实时参数",
        summary = "通过滑块与取色器，在运行过程中修改画面的参数。",
        iconRes = R.drawable.ic_tune,
        tag = "参数",
        codeSnippet = "// @rin number diameter \"Diameter\" 20 240 120 10\n// @rin color ink \"Ink\" #D67856\n\nfunction setup() {\n  createCanvas(600, 600);\n}\n\nfunction draw() {\n  background('#F2EFE7');\n  noStroke();\n  fill(rinParams.ink);\n  circle(width / 2, height / 2, rinParams.diameter);\n}",
        steps = listOf(
            "点击预览操作栏的参数按钮，打开代码中定义的参数控制面板。",
            "在代码中写入 // @rin、// @slider 或 // @color 注释，或使用“＋ 添加参数”自动插入。",
            "拖动滑块、点击加减按钮或选色时画面会即时更新；// @slider 注释还会同步修改代码里的数值。",
            "参数值会随作品单独保存，支持恢复单个参数或全部恢复默认值。"
        )
    ),
    GuideSection(
        title = "用积木搭建",
        summary = "保留原有 JavaScript 代码的同时，通过积木界面编排逻辑。",
        iconRes = R.drawable.ic_folder_code,
        tag = "积木",
        codeSnippet = "function setup() {\n  createCanvas(400, 400);\n}\n\nfunction draw() {\n  background(240);\n  circle(mouseX, mouseY, 80);\n}",
        steps = listOf(
            "在 sketch.js 顶部栏切换到“积木”，即可在较宽的工作区搭配悬浮预览小窗进行编辑。",
            "点击底部栏的“添加积木”，确认插入位置后从面板中选择需要的积木。",
            "点击积木中的色块或数值即可直接修改，点击积木本体可进行复制、删除或查看对应代码。",
            "可通过函数列表跳转到指定函数，或从底部栏和小窗按钮切换到全屏预览。"
        )
    ),
    GuideSection(
        title = "文件、素材和库",
        summary = "管理多文件项目、图片与音频素材以及 p5.js 扩展库。",
        iconRes = R.drawable.ic_assets,
        tag = "项目",
        steps = listOf(
            "在“作品菜单 → 项目文件”中添加额外的 JS、HTML 和 CSS 文件，并通过顶部标签切换。",
            "导入图片、音频、字体或数据文件后，可在素材详情页插入带有 assets/ 路径的加载代码。",
            "重命名素材时，可核对并更新代码中匹配的固定路径；单个素材上限为 50 MiB，每个作品总计 200 MiB。",
            "在作品设置中选择 p5.js 2.3.4 或 1.11.5、启用 p5.sound 或外部库；摄像头与麦克风需授予权限后使用。"
        )
    ),
    GuideSection(
        title = "保存与恢复",
        summary = "通过自动保存记录、命名快照与草稿恢复功能保护编辑内容。",
        iconRes = R.drawable.ic_history,
        tag = "保存",
        steps = listOf(
            "点击“保存”会写入当前作品的所有文件，点击旁的“历史”可查看自动保存记录与手动快照。",
            "在历史记录中可以预览过去的代码、确认是否与当前编辑区一致，并可创建附带备注的快照。",
            "在设置中开启“离开应用时自动保存”与“恢复未保存的编辑”，可在切换应用或意外退出后保留进度。",
            "即便保存失败，编辑区中的代码也不会丢失，可选择重试或另行恢复。"
        )
    ),
    GuideSection(
        title = "导出图片与录制",
        summary = "将作品导出为高分辨率 PNG 图像或 MP4 / GIF 视频，并分享到其他应用。",
        iconRes = R.drawable.ic_camera,
        tag = "导出",
        steps = listOf(
            "点击相机按钮可重新绘制并保存 1x、2x 或 4x（最高 1600 万像素）的 PNG 图像。",
            "可通过录制按钮录制 MP4 或 GIF 动画，比特率与倒计时秒数可在设置中调整。",
            "导出完成后可直接在提示框中打开查看，或分享至其他应用。",
            "若写入目标目录失败，可重试或更改保存位置；在主动丢弃之前，待保存的录制内容会一直保留。"
        )
    ),
    GuideSection(
        title = "整理与备份",
        summary = "使用文件夹和标签整理作品库，并导出完整的 ZIP 备份包。",
        iconRes = R.drawable.ic_save,
        tag = "管理",
        steps = listOf(
            "可在作品库中使用文件夹分类、按标签筛选及搜索；删除文件夹时，其中的作品会移至未分类，不会被删除。",
            "支持多选作品以统一移动、贴标签或导出；单个作品的 ZIP 包内包含源码、素材和运行配置。",
            "在“设置 → 保存与数据”中可将全部作品、自定义模板与编辑器设置打包为 ZIP，方便迁移或恢复。",
            "将保存位置设在外部工具可访问的目录即可自行同步文件，应用不会在后台自动上传数据。"
        )
    )
) }
