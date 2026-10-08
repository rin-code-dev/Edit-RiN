package com.hikariatelier.app

internal data class P5ReferenceItem(
    val name: String,
    val signature: String,
    val categoryJa: String,
    val categoryEn: String,
    val descriptionJa: String,
    val descriptionEn: String,
    val example: String,
    val snippet: String = signature
)

internal val p5ReferenceCategories = listOf(
    "すべて" to "All",
    "図形" to "Shape",
    "色・線" to "Color & Style",
    "座標・変形" to "Transform",
    "数学・計算" to "Math",
    "入力・センサー" to "Input & Sensors",
    "環境・キャンバス" to "Environment",
    "テキスト・画像" to "Text & Image",
    "サウンド" to "Sound"
)

internal val p5ReferenceItems = listOf(
    // --- 図形 (Shape) ---
    P5ReferenceItem(
        name = "circle",
        signature = "circle(x, y, d)",
        categoryJa = "図形",
        categoryEn = "Shape",
        descriptionJa = "円を描画します。x, y は中心座標、d は直径です。",
        descriptionEn = "Draws a circle at (x, y) with diameter d.",
        example = "circle(width / 2, height / 2, 80);",
        snippet = "circle(x, y, d);"
    ),
    P5ReferenceItem(
        name = "rect",
        signature = "rect(x, y, w, h, [tl, tr, br, bl])",
        categoryJa = "図形",
        categoryEn = "Shape",
        descriptionJa = "四角形を描画します。角丸の半径も指定できます。",
        descriptionEn = "Draws a rectangle with width w and height h. Optional corner radii.",
        example = "rect(40, 40, 120, 80, 8);",
        snippet = "rect(x, y, w, h);"
    ),
    P5ReferenceItem(
        name = "ellipse",
        signature = "ellipse(x, y, w, [h])",
        categoryJa = "図形",
        categoryEn = "Shape",
        descriptionJa = "楕円を描画します。w は横幅、h は縦幅です。",
        descriptionEn = "Draws an ellipse at (x, y) with width w and height h.",
        example = "ellipse(width / 2, height / 2, 120, 60);",
        snippet = "ellipse(x, y, w, h);"
    ),
    P5ReferenceItem(
        name = "line",
        signature = "line(x1, y1, x2, y2)",
        categoryJa = "図形",
        categoryEn = "Shape",
        descriptionJa = "2点間に直線を引きます。",
        descriptionEn = "Draws a line segment between two points (x1, y1) and (x2, y2).",
        example = "line(0, 0, width, height);",
        snippet = "line(x1, y1, x2, y2);"
    ),
    P5ReferenceItem(
        name = "point",
        signature = "point(x, y, [z])",
        categoryJa = "図形",
        categoryEn = "Shape",
        descriptionJa = "座標に点を描画します。太さは strokeWeight で設定します。",
        descriptionEn = "Draws a single point. Thickness depends on strokeWeight().",
        example = "strokeWeight(6);\npoint(width / 2, height / 2);",
        snippet = "point(x, y);"
    ),
    P5ReferenceItem(
        name = "triangle",
        signature = "triangle(x1, y1, x2, y2, x3, y3)",
        categoryJa = "図形",
        categoryEn = "Shape",
        descriptionJa = "3つの頂点を結ぶ三角形を描画します。",
        descriptionEn = "Draws a triangle defined by three vertices.",
        example = "triangle(300, 100, 200, 300, 400, 300);",
        snippet = "triangle(x1, y1, x2, y2, x3, y3);"
    ),
    P5ReferenceItem(
        name = "arc",
        signature = "arc(x, y, w, h, start, stop, [mode], [detail])",
        categoryJa = "図形",
        categoryEn = "Shape",
        descriptionJa = "円弧（パイや扇形）を描画します。mode に PIE, OPEN, CHORD を指定可能。",
        descriptionEn = "Draws an arc from angle start to stop. mode can be PIE, OPEN, or CHORD.",
        example = "arc(width / 2, height / 2, 100, 100, 0, PI + HALF_PI, PIE);",
        snippet = "arc(x, y, w, h, start, stop, PIE);"
    ),
    P5ReferenceItem(
        name = "beginShape",
        signature = "beginShape([kind])",
        categoryJa = "図形",
        categoryEn = "Shape",
        descriptionJa = "カスタム多角形や連続曲線の描画を開始します。kind に POINTS, LINES, TRIANGLES 等を指定可能。",
        descriptionEn = "Begins recording vertices for a custom shape.",
        example = "beginShape();\nvertex(100, 100);\nvertex(150, 50);\nvertex(200, 100);\nendShape(CLOSE);",
        snippet = "beginShape();\nvertex(x, y);\nendShape(CLOSE);"
    ),
    P5ReferenceItem(
        name = "vertex",
        signature = "vertex(x, y, [z])",
        categoryJa = "図形",
        categoryEn = "Shape",
        descriptionJa = "beginShape と endShape の間で図形の頂点座標を追加します。",
        descriptionEn = "Specifies a vertex coordinates for custom shapes.",
        example = "vertex(width / 2, height / 2);",
        snippet = "vertex(x, y);"
    ),
    P5ReferenceItem(
        name = "curveVertex",
        signature = "curveVertex(x, y, [z])",
        categoryJa = "図形",
        categoryEn = "Shape",
        descriptionJa = "Catmull-Rom スプライン曲線の頂点を追加します。最初と最後の頂点は制御点として機能します。",
        descriptionEn = "Specifies coordinates for Catmull-Rom spline vertices.",
        example = "beginShape();\ncurveVertex(80, 80);\ncurveVertex(120, 40);\ncurveVertex(180, 120);\ncurveVertex(180, 120);\nendShape();",
        snippet = "curveVertex(x, y);"
    ),
    P5ReferenceItem(
        name = "bezierVertex",
        signature = "bezierVertex(x2, y2, x3, y3, x4, y4)",
        categoryJa = "図形",
        categoryEn = "Shape",
        descriptionJa = "3次ベジェ曲線の頂点を追加します（制御点2つと終点1つ）。",
        descriptionEn = "Specifies coordinates for a cubic Bezier curve vertex.",
        example = "beginShape();\nvertex(30, 20);\nbezierVertex(80, 0, 80, 75, 30, 75);\nendShape();",
        snippet = "bezierVertex(cx1, cy1, cx2, cy2, x, y);"
    ),
    P5ReferenceItem(
        name = "endShape",
        signature = "endShape([mode])",
        categoryJa = "図形",
        categoryEn = "Shape",
        descriptionJa = "カスタム図形の定義を終了します。mode に CLOSE を指定すると始点と終点を閉じます。",
        descriptionEn = "Finishes the shape definition. Use CLOSE to connect back to the first vertex.",
        example = "endShape(CLOSE);",
        snippet = "endShape(CLOSE);"
    ),
    P5ReferenceItem(
        name = "rectMode",
        signature = "rectMode(mode)",
        categoryJa = "図形",
        categoryEn = "Shape",
        descriptionJa = "rect() の描画基準点を設定します（CORNER, CORNERS, CENTER, RADIUS）。",
        descriptionEn = "Modifies the location from which rectangles are drawn (CORNER, CENTER, etc.).",
        example = "rectMode(CENTER);\nrect(width / 2, height / 2, 100, 100);",
        snippet = "rectMode(CENTER);"
    ),
    P5ReferenceItem(
        name = "ellipseMode",
        signature = "ellipseMode(mode)",
        categoryJa = "図形",
        categoryEn = "Shape",
        descriptionJa = "ellipse() の描画基準点を設定します（CENTER, RADIUS, CORNER, CORNERS）。",
        descriptionEn = "Modifies the location from which ellipses are drawn (CENTER, CORNER, etc.).",
        example = "ellipseMode(CENTER);",
        snippet = "ellipseMode(CENTER);"
    ),

    // --- 色・線 (Color & Style) ---
    P5ReferenceItem(
        name = "background",
        signature = "background(color, [a])",
        categoryJa = "色・線",
        categoryEn = "Color & Style",
        descriptionJa = "キャンバス全体を指定色で塗りつぶします。",
        descriptionEn = "Sets the color used for the background of the canvas.",
        example = "background(15, 20, 30);\n// 半透明残像: background(15, 20, 30, 20);",
        snippet = "background(15, 20, 30);"
    ),
    P5ReferenceItem(
        name = "fill",
        signature = "fill(v1, [v2, v3, a])",
        categoryJa = "色・線",
        categoryEn = "Color & Style",
        descriptionJa = "図形の塗りつぶし色を設定します。グレースケール、RGB、カラー名、#16進数に対応。",
        descriptionEn = "Sets the color used to fill shapes. Supports grayscale, RGB, hex, or color names.",
        example = "fill('#64B5F6');\n// または: fill(100, 180, 255, 180);",
        snippet = "fill(r, g, b);"
    ),
    P5ReferenceItem(
        name = "noFill",
        signature = "noFill()",
        categoryJa = "色・線",
        categoryEn = "Color & Style",
        descriptionJa = "図形の塗りつぶしを無効にします（枠線のみ描画）。",
        descriptionEn = "Disables filling geometry so shapes are drawn as outlines only.",
        example = "noFill();\nstroke(255);\ncircle(width / 2, height / 2, 80);",
        snippet = "noFill();"
    ),
    P5ReferenceItem(
        name = "stroke",
        signature = "stroke(v1, [v2, v3, a])",
        categoryJa = "色・線",
        categoryEn = "Color & Style",
        descriptionJa = "線や輪郭の色を設定します。",
        descriptionEn = "Sets the color used to draw lines and borders around shapes.",
        example = "stroke(255, 100, 150);\nstrokeWeight(3);",
        snippet = "stroke(r, g, b);"
    ),
    P5ReferenceItem(
        name = "noStroke",
        signature = "noStroke()",
        categoryJa = "色・線",
        categoryEn = "Color & Style",
        descriptionJa = "線や輪郭の描画を無効にします。",
        descriptionEn = "Disables drawing the stroke (outline) around shapes.",
        example = "noStroke();\nfill(255, 0, 100);",
        snippet = "noStroke();"
    ),
    P5ReferenceItem(
        name = "strokeWeight",
        signature = "strokeWeight(weight)",
        categoryJa = "色・線",
        categoryEn = "Color & Style",
        descriptionJa = "線や輪郭の太さ（ピクセル）を設定します。",
        descriptionEn = "Sets the width of the stroke used for lines, points, and shape borders.",
        example = "strokeWeight(4);",
        snippet = "strokeWeight(weight);"
    ),
    P5ReferenceItem(
        name = "strokeCap",
        signature = "strokeCap(cap)",
        categoryJa = "色・線",
        categoryEn = "Color & Style",
        descriptionJa = "線の端点の形状を設定します（ROUND, SQUARE, PROJECT）。",
        descriptionEn = "Sets the style for line endings: ROUND, SQUARE, or PROJECT.",
        example = "strokeCap(ROUND);",
        snippet = "strokeCap(ROUND);"
    ),
    P5ReferenceItem(
        name = "strokeJoin",
        signature = "strokeJoin(join)",
        categoryJa = "色・線",
        categoryEn = "Color & Style",
        descriptionJa = "線と線が交差する角の形状を設定します（MITER, BEVEL, ROUND）。",
        descriptionEn = "Sets the style of joints which connect line segments (MITER, BEVEL, ROUND).",
        example = "strokeJoin(ROUND);",
        snippet = "strokeJoin(ROUND);"
    ),
    P5ReferenceItem(
        name = "colorMode",
        signature = "colorMode(mode, [max1, max2, max3, maxA])",
        categoryJa = "色・線",
        categoryEn = "Color & Style",
        descriptionJa = "カラー解釈モードを変更します（RGB, HSB, HSL）。HSBモードは虹色やグラデーションに便利です。",
        descriptionEn = "Changes the way color values are interpreted (RGB, HSB, or HSL).",
        example = "colorMode(HSB, 360, 100, 100, 1);\nfill(frameCount % 360, 80, 95);",
        snippet = "colorMode(HSB, 360, 100, 100, 1);"
    ),
    P5ReferenceItem(
        name = "color",
        signature = "color(v1, [v2, v3, a])",
        categoryJa = "色・線",
        categoryEn = "Color & Style",
        descriptionJa = "p5.Color オブジェクトを生成します。色の保存や比較、lerpColorに利用できます。",
        descriptionEn = "Creates a p5.Color object from values, hex strings, or color names.",
        example = "const c = color('#64B5F6');\nfill(c);",
        snippet = "const c = color(r, g, b);"
    ),
    P5ReferenceItem(
        name = "lerpColor",
        signature = "lerpColor(c1, c2, amt)",
        categoryJa = "色・線",
        categoryEn = "Color & Style",
        descriptionJa = "2つの色を指定した割合（amt: 0.0〜1.0）で線形補間した色を返します。",
        descriptionEn = "Blends two colors to find a third color between them (amt from 0.0 to 1.0).",
        example = "const start = color(255, 0, 100);\nconst end = color(0, 150, 255);\nconst mid = lerpColor(start, end, 0.5);",
        snippet = "lerpColor(c1, c2, amt);"
    ),
    P5ReferenceItem(
        name = "blendMode",
        signature = "blendMode(mode)",
        categoryJa = "色・線",
        categoryEn = "Color & Style",
        descriptionJa = "ピクセルの合成モードを設定します（BLEND, ADD, MULTIPLY, SCREEN, DIFFERENCE等）。光の表現には ADD が最適です。",
        descriptionEn = "Sets the blend mode for rendering (BLEND, ADD, MULTIPLY, SCREEN, etc.).",
        example = "blendMode(ADD);\n// 描画後に元に戻す: blendMode(BLEND);",
        snippet = "blendMode(ADD);"
    ),

    // --- 座標・変形 (Transform) ---
    P5ReferenceItem(
        name = "push",
        signature = "push()",
        categoryJa = "座標・変形",
        categoryEn = "Transform",
        descriptionJa = "現在の描画スタイル（fill, stroke）と座標変換（translate, rotate）の状態を保存します。pop() とペアで使用。",
        descriptionEn = "Saves current drawing style settings and transformations. Paired with pop().",
        example = "push();\ntranslate(width / 2, height / 2);\nrotate(frameCount * 0.02);\nrect(0, 0, 60, 60);\npop();",
        snippet = "push();\n// style / transform\npop();"
    ),
    P5ReferenceItem(
        name = "pop",
        signature = "pop()",
        categoryJa = "座標・変形",
        categoryEn = "Transform",
        descriptionJa = "直前の push() で保存した描画設定と座標系を復元します。",
        descriptionEn = "Restores previous drawing style settings and transformations saved by push().",
        example = "pop();",
        snippet = "pop();"
    ),
    P5ReferenceItem(
        name = "translate",
        signature = "translate(x, y, [z])",
        categoryJa = "座標・変形",
        categoryEn = "Transform",
        descriptionJa = "座標系の原点 (0, 0) を指定した距離だけ平行移動します。",
        descriptionEn = "Specifies an amount to displace objects within the display window.",
        example = "translate(width / 2, height / 2);",
        snippet = "translate(x, y);"
    ),
    P5ReferenceItem(
        name = "rotate",
        signature = "rotate(angle)",
        categoryJa = "座標・変形",
        categoryEn = "Transform",
        descriptionJa = "原点 (0, 0) を中心に座標系を回転します。単位は angleMode（デフォルトはラジアン）に従います。",
        descriptionEn = "Rotates shape by the specified angle around the current origin.",
        example = "rotate(frameCount * 0.05);",
        snippet = "rotate(angle);"
    ),
    P5ReferenceItem(
        name = "scale",
        signature = "scale(s, [y, z])",
        categoryJa = "座標・変形",
        categoryEn = "Transform",
        descriptionJa = "座標系を指定した倍率で拡大・縮小します。負の値を指定すると反転します。",
        descriptionEn = "Increases or decreases the size of shapes by expanding/contracting vertices.",
        example = "scale(1.5);\n// 左右反転: scale(-1, 1);",
        snippet = "scale(s);"
    ),
    P5ReferenceItem(
        name = "resetMatrix",
        signature = "resetMatrix()",
        categoryJa = "座標・変形",
        categoryEn = "Transform",
        descriptionJa = "すべての座標変換（translate, rotate, scale）をリセットし、初期状態の単位行列に戻します。",
        descriptionEn = "Replaces the current transformation matrix with the identity matrix.",
        example = "resetMatrix();",
        snippet = "resetMatrix();"
    ),
    P5ReferenceItem(
        name = "angleMode",
        signature = "angleMode(mode)",
        categoryJa = "座標・変形",
        categoryEn = "Transform",
        descriptionJa = "角度の解釈モードを設定します（RADIANS または DEGREES）。度数法で直感的に扱いたい時に便利です。",
        descriptionEn = "Sets the current mode for interpreting angles: RADIANS (default) or DEGREES.",
        example = "angleMode(DEGREES);\nrotate(45);",
        snippet = "angleMode(DEGREES);"
    ),

    // --- 数学・計算 (Math) ---
    P5ReferenceItem(
        name = "map",
        signature = "map(value, start1, stop1, start2, stop2, [withinBounds])",
        categoryJa = "数学・計算",
        categoryEn = "Math",
        descriptionJa = "ある範囲の数値を別の範囲に線形換算します。withinBounds を true にすると変換後の範囲内に収めます。",
        descriptionEn = "Re-maps a number from one range to another.",
        example = "const radius = map(mouseX, 0, width, 10, 100, true);",
        snippet = "map(value, 0, width, 0, 100, true);"
    ),
    P5ReferenceItem(
        name = "constrain",
        signature = "constrain(n, low, high)",
        categoryJa = "数学・計算",
        categoryEn = "Math",
        descriptionJa = "数値を指定した最小値 low と最大値 high の範囲内に収めます。",
        descriptionEn = "Constrains a value to not exceed a minimum and maximum value.",
        example = "const clampedX = constrain(mouseX, 40, width - 40);",
        snippet = "constrain(n, low, high);"
    ),
    P5ReferenceItem(
        name = "lerp",
        signature = "lerp(start, stop, amt)",
        categoryJa = "数学・計算",
        categoryEn = "Math",
        descriptionJa = "2つの数値の間を線形補間します。amt（0.0〜1.0）は滑らかな追従アニメーションに多用されます。",
        descriptionEn = "Calculates a number between two numbers at a specific increment.",
        example = "x = lerp(x, targetX, 0.1);",
        snippet = "lerp(current, target, 0.1);"
    ),
    P5ReferenceItem(
        name = "dist",
        signature = "dist(x1, y1, [z1], x2, y2, [z2])",
        categoryJa = "数学・計算",
        categoryEn = "Math",
        descriptionJa = "2点間のユークリッド距離を計算します。",
        descriptionEn = "Calculates the distance between two points.",
        example = "const d = dist(x, y, mouseX, mouseY);",
        snippet = "dist(x1, y1, x2, y2);"
    ),
    P5ReferenceItem(
        name = "random",
        signature = "random([min], [max])",
        categoryJa = "数学・計算",
        categoryEn = "Math",
        descriptionJa = "一様乱数を生成します。引数が配列の場合はその要素をランダムに選びます。",
        descriptionEn = "Generates random numbers or picks a random element from an array.",
        example = "const r = random(10, 50);\nconst col = random(['#F44336', '#2196F3', '#4CAF50']);",
        snippet = "random(min, max);"
    ),
    P5ReferenceItem(
        name = "noise",
        signature = "noise(x, [y], [z])",
        categoryJa = "数学・計算",
        categoryEn = "Math",
        descriptionJa = "パーリンノイズ（滑らかで自然な連続乱数、0.0〜1.0）を返します。波や煙、地形生成に不可欠。",
        descriptionEn = "Returns the Perlin noise value at specified coordinates (returns 0.0 to 1.0).",
        example = "const n = noise(frameCount * 0.01) * width;",
        snippet = "noise(x * 0.01);"
    ),
    P5ReferenceItem(
        name = "sin / cos",
        signature = "sin(angle) / cos(angle)",
        categoryJa = "数学・計算",
        categoryEn = "Math",
        descriptionJa = "三角関数の正弦（sin）と余弦（cos）を計算します。円運動や波の生成に利用します。",
        descriptionEn = "Calculates sine and cosine of an angle.",
        example = "const x = width / 2 + cos(angle) * radius;\nconst y = height / 2 + sin(angle) * radius;",
        snippet = "cos(angle);"
    ),
    P5ReferenceItem(
        name = "atan2",
        signature = "atan2(y, x)",
        categoryJa = "数学・計算",
        categoryEn = "Math",
        descriptionJa = "指定された点 (x, y) と原点との間の角度をアークタンジェントで計算します。",
        descriptionEn = "Calculates the angle from the specified point to the coordinate origin.",
        example = "const angle = atan2(mouseY - y, mouseX - x);",
        snippet = "atan2(y, x);"
    ),
    P5ReferenceItem(
        name = "createVector",
        signature = "createVector([x], [y], [z])",
        categoryJa = "数学・計算",
        categoryEn = "Math",
        descriptionJa = "2Dまたは3Dの p5.Vector オブジェクトを生成します。位置・速度・力の計算に便利です。",
        descriptionEn = "Creates a new 2D or 3D p5.Vector object.",
        example = "const pos = createVector(width / 2, height / 2);\nconst vel = createVector(2, -1);\npos.add(vel);",
        snippet = "createVector(x, y);"
    ),
    P5ReferenceItem(
        name = "p5.Vector",
        signature = "p5.Vector.sub(v1, v2) / dist / lerp",
        categoryJa = "数学・計算",
        categoryEn = "Math",
        descriptionJa = "ベクトルの静的計算メソッド群。引き算（sub）、加算（add）、距離（dist）、正規化（normalize）など。",
        descriptionEn = "Static methods on p5.Vector for vector arithmetic.",
        example = "const force = p5.Vector.sub(target, pos).normalize().mult(5);",
        snippet = "p5.Vector.sub(v1, v2);"
    ),

    // --- 入力・センサー (Input & Sensors) ---
    P5ReferenceItem(
        name = "mouseX / mouseY",
        signature = "mouseX, mouseY",
        categoryJa = "入力・センサー",
        categoryEn = "Input & Sensors",
        descriptionJa = "現在のマウスまたは指のタッチ座標（水平・垂直ピクセル位置）を保持するシステム変数。",
        descriptionEn = "System variables that always contain the current horizontal and vertical mouse/touch position.",
        example = "circle(mouseX, mouseY, 30);",
        snippet = "mouseX"
    ),
    P5ReferenceItem(
        name = "pmouseX / pmouseY",
        signature = "pmouseX, pmouseY",
        categoryJa = "入力・センサー",
        categoryEn = "Input & Sensors",
        descriptionJa = "直前のフレームでのマウス・タッチ座標。線の描画（お絵描き）に便利です。",
        descriptionEn = "System variables containing the mouse/touch position from the previous frame.",
        example = "if (mouseIsPressed) line(mouseX, mouseY, pmouseX, pmouseY);",
        snippet = "line(mouseX, mouseY, pmouseX, pmouseY);"
    ),
    P5ReferenceItem(
        name = "mouseIsPressed",
        signature = "mouseIsPressed",
        categoryJa = "入力・センサー",
        categoryEn = "Input & Sensors",
        descriptionJa = "画面が現在タップされているか（マウスが押されているか）を示す真偽値（true / false）。",
        descriptionEn = "Boolean system variable that is true if mouse is pressed or screen is touched.",
        example = "if (mouseIsPressed) fill('#FF4081'); else fill('#64B5F6');",
        snippet = "if (mouseIsPressed) {\n  \n}"
    ),
    P5ReferenceItem(
        name = "touchStarted",
        signature = "touchStarted([event])",
        categoryJa = "入力・センサー",
        categoryEn = "Input & Sensors",
        descriptionJa = "画面に指が触れた瞬間に一度だけ自動呼び出しされる関数。false を返すと既定スクロールを防止します。",
        descriptionEn = "Called once every time a touch is registered.",
        example = "function touchStarted() {\n  circle(mouseX, mouseY, 50);\n  return false;\n}",
        snippet = "function touchStarted() {\n  \n  return false;\n}"
    ),
    P5ReferenceItem(
        name = "touchMoved",
        signature = "touchMoved([event])",
        categoryJa = "入力・センサー",
        categoryEn = "Input & Sensors",
        descriptionJa = "画面上で指がドラッグされた時に連続して呼ばれる関数。",
        descriptionEn = "Called every time a touch moves.",
        example = "function touchMoved() {\n  line(mouseX, mouseY, pmouseX, pmouseY);\n  return false;\n}",
        snippet = "function touchMoved() {\n  \n  return false;\n}"
    ),
    P5ReferenceItem(
        name = "touches",
        signature = "touches",
        categoryJa = "入力・センサー",
        categoryEn = "Input & Sensors",
        descriptionJa = "現在画面に触れているすべての指のタッチ情報配列 [{x, y, id}, ...]。",
        descriptionEn = "Array containing information for all current touch points.",
        example = "for (const t of touches) {\n  circle(t.x, t.y, 40);\n}",
        snippet = "for (const t of touches) {\n  circle(t.x, t.y, 40);\n}"
    ),
    P5ReferenceItem(
        name = "rotationX / rotationY",
        signature = "rotationX, rotationY",
        categoryJa = "入力・センサー",
        categoryEn = "Input & Sensors",
        descriptionJa = "端末の前後傾き（rotationX）および左右傾き（rotationY）。angleMode(DEGREES) で -180〜180° / -90〜90°。",
        descriptionEn = "Device tilt angles around X (front-to-back) and Y (left-to-right) axes. In degrees if angleMode(DEGREES).",
        example = "angleMode(DEGREES);\nconst tiltX = rotationY;\nconst tiltY = rotationX - 35;",
        snippet = "rotationX"
    ),
    P5ReferenceItem(
        name = "accelerationX / Y",
        signature = "accelerationX, accelerationY",
        categoryJa = "入力・センサー",
        categoryEn = "Input & Sensors",
        descriptionJa = "端末にかかる直線加速度（m/s²）。端末を急に動かした時の検知に使います。",
        descriptionEn = "Linear acceleration along the X and Y axes in m/s².",
        example = "if (Math.abs(accelerationX) > 1.5) {\n  // 激しく動いた時の処理\n}",
        snippet = "accelerationX"
    ),
    P5ReferenceItem(
        name = "deviceShaken",
        signature = "deviceShaken()",
        categoryJa = "入力・センサー",
        categoryEn = "Input & Sensors",
        descriptionJa = "端末が振られた（シェイクされた）時に一度だけ自動呼び出しされる関数。",
        descriptionEn = "Called when the device is shaken above the shake threshold.",
        example = "function deviceShaken() {\n  background(20);\n  resetParticles();\n}",
        snippet = "function deviceShaken() {\n  \n}"
    ),
    P5ReferenceItem(
        name = "setShakeThreshold",
        signature = "setShakeThreshold(value)",
        categoryJa = "入力・センサー",
        categoryEn = "Input & Sensors",
        descriptionJa = "deviceShaken() が反応するシェイクの感度閾値を設定します（デフォルトは約 30）。",
        descriptionEn = "Sets the sensitivity threshold for deviceShaken(). Default is around 30.",
        example = "setShakeThreshold(35);",
        snippet = "setShakeThreshold(35);"
    ),

    // --- 環境・キャンバス (Environment) ---
    P5ReferenceItem(
        name = "createCanvas",
        signature = "createCanvas(w, h, [renderer])",
        categoryJa = "環境・キャンバス",
        categoryEn = "Environment",
        descriptionJa = "スケッチの描画キャンバスを生成します。renderer に WEBGL または WEBGPU を指定可能。",
        descriptionEn = "Creates a canvas element with specified dimensions and renderer (P2D, WEBGL, WEBGPU).",
        example = "createCanvas(600, 600);\n// 3Dの場合: createCanvas(600, 600, WEBGL);",
        snippet = "createCanvas(600, 600);"
    ),
    P5ReferenceItem(
        name = "resizeCanvas",
        signature = "resizeCanvas(w, h, [noRedraw])",
        categoryJa = "環境・キャンバス",
        categoryEn = "Environment",
        descriptionJa = "キャンバスのサイズを動的に変更します。windowResized イベント内でよく使われます。",
        descriptionEn = "Resizes the canvas to given width and height.",
        example = "function windowResized() {\n  resizeCanvas(windowWidth, windowHeight);\n}",
        snippet = "resizeCanvas(width, height);"
    ),
    P5ReferenceItem(
        name = "width / height",
        signature = "width, height",
        categoryJa = "環境・キャンバス",
        categoryEn = "Environment",
        descriptionJa = "現在のキャンバスの幅と高さを保持するシステム変数。",
        descriptionEn = "System variables holding the width and height of the canvas.",
        example = "circle(width / 2, height / 2, width * 0.5);",
        snippet = "width"
    ),
    P5ReferenceItem(
        name = "frameCount",
        signature = "frameCount",
        categoryJa = "環境・キャンバス",
        categoryEn = "Environment",
        descriptionJa = "プログラム開始から描画されたフレーム総数を保持するシステム変数。時間経過アニメーションの基準に。",
        descriptionEn = "System variable containing the number of frames that have been displayed.",
        example = "const angle = frameCount * 0.02;",
        snippet = "frameCount"
    ),
    P5ReferenceItem(
        name = "deltaTime",
        signature = "deltaTime",
        categoryJa = "環境・キャンバス",
        categoryEn = "Environment",
        descriptionJa = "直前のフレームからの経過時間（ミリ秒）。可変フレームレートでも一定速度で動かしたい物理演算に。",
        descriptionEn = "Contains the time difference between current and previous frame in milliseconds.",
        example = "x += speed * (deltaTime / 1000);",
        snippet = "deltaTime"
    ),
    P5ReferenceItem(
        name = "frameRate",
        signature = "frameRate([fps])",
        categoryJa = "環境・キャンバス",
        categoryEn = "Environment",
        descriptionJa = "目標フレームレートを設定、または現在の実測フレームレートを取得します。",
        descriptionEn = "Sets or gets the target frame rate.",
        example = "frameRate(60);",
        snippet = "frameRate(60);"
    ),
    P5ReferenceItem(
        name = "noLoop / loop",
        signature = "noLoop() / loop()",
        categoryJa = "環境・キャンバス",
        categoryEn = "Environment",
        descriptionJa = "draw() の連続描画ループを停止（noLoop）または再開（loop）します。静止画ジェネラティブアートでは noLoop() が定番。",
        descriptionEn = "Stops or resumes p5.js from continuously executing draw().",
        example = "function setup() {\n  createCanvas(600, 600);\n  noLoop(); // 1回だけ描画\n}",
        snippet = "noLoop();"
    ),
    P5ReferenceItem(
        name = "pixelDensity",
        signature = "pixelDensity([val])",
        categoryJa = "環境・キャンバス",
        categoryEn = "Environment",
        descriptionJa = "高解像度ディスプレイ（Retina等）のピクセル密度を設定・取得します。1に固定すると処理負荷を軽減できます。",
        descriptionEn = "Sets the pixel scaling for high pixel density displays.",
        example = "pixelDensity(1); // 負荷軽減",
        snippet = "pixelDensity(1);"
    ),

    // --- テキスト・画像 (Text & Image) ---
    P5ReferenceItem(
        name = "text",
        signature = "text(str, x, y, [x2, y2])",
        categoryJa = "テキスト・画像",
        categoryEn = "Text & Image",
        descriptionJa = "キャンバスに文字列を描画します。",
        descriptionEn = "Draws text to the screen.",
        example = "fill(255);\ntextSize(24);\ntextAlign(CENTER, CENTER);\ntext('Hello Edit:RiN', width / 2, height / 2);",
        snippet = "text('Hello', x, y);"
    ),
    P5ReferenceItem(
        name = "textSize",
        signature = "textSize(size)",
        categoryJa = "テキスト・画像",
        categoryEn = "Text & Image",
        descriptionJa = "描画するテキストの文字サイズ（ピクセル）を設定します。",
        descriptionEn = "Sets the current font size.",
        example = "textSize(20);",
        snippet = "textSize(20);"
    ),
    P5ReferenceItem(
        name = "textAlign",
        signature = "textAlign(horiz, [vert])",
        categoryJa = "テキスト・画像",
        categoryEn = "Text & Image",
        descriptionJa = "テキストの揃え位置を設定します（horiz: LEFT, CENTER, RIGHT / vert: TOP, BOTTOM, CENTER）。",
        descriptionEn = "Sets current alignment for drawing text (LEFT, CENTER, RIGHT, etc.).",
        example = "textAlign(CENTER, CENTER);",
        snippet = "textAlign(CENTER, CENTER);"
    ),
    P5ReferenceItem(
        name = "textFont",
        signature = "textFont(font, [size])",
        categoryJa = "テキスト・画像",
        categoryEn = "Text & Image",
        descriptionJa = "テキスト描画に使用するフォント（loadFont で読み込んだフォントまたはシステムフォント名）を設定します。",
        descriptionEn = "Sets the current font used for text().",
        example = "textFont('monospace', 16);",
        snippet = "textFont('monospace');"
    ),
    P5ReferenceItem(
        name = "loadImage / image",
        signature = "image(img, x, y, [w, h])",
        categoryJa = "テキスト・画像",
        categoryEn = "Text & Image",
        descriptionJa = "画像をキャンバスに描画します。通常は preload() で loadImage('assets/...') を実行してから使用します。",
        descriptionEn = "Draws an image onto the canvas.",
        example = "let img;\nfunction preload() { img = loadImage('assets/photo.png'); }\nfunction draw() { image(img, 0, 0, width, height); }",
        snippet = "image(img, x, y, w, h);"
    ),

    // --- サウンド (Sound) ---
    P5ReferenceItem(
        name = "userStartAudio",
        signature = "userStartAudio()",
        categoryJa = "サウンド",
        categoryEn = "Sound",
        descriptionJa = "ブラウザのオーディオコンテキストのロックを解除します。タップやクリックイベント（touchStarted）内で呼び出します。",
        descriptionEn = "Enables audio in response to a user gesture to unblock browser AudioContext.",
        example = "function touchStarted() {\n  userStartAudio();\n}",
        snippet = "userStartAudio();"
    ),
    P5ReferenceItem(
        name = "p5.Oscillator",
        signature = "new p5.Oscillator([freq], [type])",
        categoryJa = "サウンド",
        categoryEn = "Sound",
        descriptionJa = "シンセサイザーの波形発振器を作成します（sine, triangle, sawtooth, square）。start() で再生、freq() で周波数を変更。",
        descriptionEn = "Creates a sound oscillator with waveform type (sine, triangle, sawtooth, square).",
        example = "const osc = new p5.Oscillator(440, 'sine');\nosc.start();\nosc.amp(0.5);",
        snippet = "const osc = new p5.Oscillator(440, 'sine');\nosc.start();"
    ),
    P5ReferenceItem(
        name = "p5.AudioIn",
        signature = "new p5.AudioIn()",
        categoryJa = "サウンド",
        categoryEn = "Sound",
        descriptionJa = "マイクからの音声入力を取得します。start() で音声の取得を開始します（Android権限ダイアログが表示されます）。",
        descriptionEn = "Captures audio input from microphone. Call start() to begin streaming.",
        example = "const mic = new p5.AudioIn();\nmic.start();\nconst level = mic.getLevel();",
        snippet = "const mic = new p5.AudioIn();\nmic.start();"
    ),
    P5ReferenceItem(
        name = "p5.FFT",
        signature = "new p5.FFT([smoothing], [bins])",
        categoryJa = "サウンド",
        categoryEn = "Sound",
        descriptionJa = "高速フーリエ変換により音声を周波数スペクトル（analyze()）や波形データ（waveform()）に解析します。",
        descriptionEn = "Analyzes frequency spectrum and waveform of audio.",
        example = "const fft = new p5.FFT();\nconst spectrum = fft.analyze();\nconst waveform = fft.waveform();",
        snippet = "const fft = new p5.FFT();\nconst spectrum = fft.analyze();"
    )
)
