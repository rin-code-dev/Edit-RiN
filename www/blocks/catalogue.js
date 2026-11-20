/* Edit:RiN block vocabulary. Blockly is only the visual editor; JavaScript keeps its semantics. */
(function (root) {
  'use strict';
  const words = (ja, en, zh) => ({ja, en, zh});
  const P5_CALLS = Object.create(null);
  const add = (name, category, label, args, source) => { P5_CALLS[name] = {label, category, args, source}; };
  add('createCanvas', 'drawing', words('キャンバスを作る', 'Create canvas', '创建画布'), ['width', 'height'], 'createCanvas(400, 400);');
  add('background', 'drawing', words('背景', 'Background', '背景'), ['color'], 'background(240);');
  add('clear', 'drawing', words('透明にする', 'Clear canvas', '清空画布'), [], 'clear();');
  add('circle', 'drawing', words('円', 'Circle', '圆'), ['x', 'y', 'diameter'], 'circle(200, 200, 80);');
  add('ellipse', 'drawing', words('楕円', 'Ellipse', '椭圆'), ['x', 'y', 'width', 'height'], 'ellipse(200, 200, 120, 80);');
  add('rect', 'drawing', words('四角', 'Rectangle', '矩形'), ['x', 'y', 'width', 'height'], 'rect(100, 100, 120, 80);');
  add('line', 'drawing', words('線', 'Line', '线'), ['x1', 'y1', 'x2', 'y2'], 'line(100, 100, 300, 300);');
  add('point', 'drawing', words('点', 'Point', '点'), ['x', 'y'], 'point(200, 200);');
  add('triangle', 'drawing', words('三角', 'Triangle', '三角形'), ['x1', 'y1', 'x2', 'y2', 'x3', 'y3'], 'triangle(200, 80, 80, 300, 320, 300);');
  add('text', 'drawing', words('文字を描く', 'Draw text', '绘制文字'), ['text', 'x', 'y'], 'text("Hello!", 100, 200);');
  add('image', 'assets', words('画像を描く', 'Draw image', '绘制图像'), ['image', 'x', 'y'], 'image(img, 0, 0);');
  add('loadImage', 'assets', words('画像を読み込む', 'Load image', '加载图像'), ['file'], 'loadImage("assets/image.png")');
  add('fill', 'style', words('塗りの色', 'Fill color', '填充颜色'), ['color'], 'fill("#6c5ce7");');
  add('noFill', 'style', words('塗りなし', 'No fill', '无填充'), [], 'noFill();');
  add('stroke', 'style', words('線の色', 'Stroke color', '描边颜色'), ['color'], 'stroke("#222222");');
  add('noStroke', 'style', words('線なし', 'No stroke', '无描边'), [], 'noStroke();');
  add('strokeWeight', 'style', words('線の太さ', 'Stroke weight', '描边宽度'), ['weight'], 'strokeWeight(3);');
  add('textSize', 'style', words('文字の大きさ', 'Text size', '文字大小'), ['size'], 'textSize(32);');
  add('push', 'transform', words('描画設定を保存', 'Save drawing state', '保存绘图状态'), [], 'push();');
  add('pop', 'transform', words('描画設定を戻す', 'Restore drawing state', '恢复绘图状态'), [], 'pop();');
  add('translate', 'transform', words('原点を移動', 'Translate', '平移'), ['x', 'y'], 'translate(200, 200);');
  add('rotate', 'transform', words('回転', 'Rotate', '旋转'), ['angle'], 'rotate(0.1);');
  add('scale', 'transform', words('拡大・縮小', 'Scale', '缩放'), ['factor'], 'scale(1.2);');
  add('random', 'math', words('乱数', 'Random', '随机数'), ['minimum', 'maximum'], 'random(0, 100)');
  add('noise', 'math', words('ノイズ', 'Noise', '噪声'), ['value'], 'noise(frameCount * 0.01)');
  add('map', 'math', words('値の範囲を変える', 'Map range', '映射范围'), ['value', 'fromMin', 'fromMax', 'toMin', 'toMax'], 'map(mouseX, 0, width, 0, 255)');
  add('sin', 'math', words('サイン', 'Sine', '正弦'), ['angle'], 'sin(frameCount * 0.05)');
  add('constrain', 'math', words('範囲内に収める', 'Constrain', '限制范围'), ['value', 'minimum', 'maximum'], 'constrain(mouseX, 0, width)');
  add('dist', 'math', words('2点間の距離 dist', 'Distance between 2 points · dist', '两点间距离 dist'), ['x1', 'y1', 'x2', 'y2'], 'dist(0, 0, mouseX, mouseY)');
  add('lerp', 'math', words('滑らかな移動補間 lerp', 'Linear interpolation · lerp', '线性插值 lerp'), ['start', 'stop', 'amount'], 'lerp(0, width, 0.1)');
  add('abs', 'math', words('絶対値 abs', 'Absolute value · abs', '绝对值 abs'), ['value'], 'abs(-10)');
  add('floor', 'math', words('切り捨て floor', 'Floor (round down) · floor', '向下取整 floor'), ['value'], 'floor(random(0, 10))');
  add('round', 'math', words('四捨五入 round', 'Round · round', '四舍五入 round'), ['value'], 'round(random(0, 10))');
  add('ceil', 'math', words('切り上げ ceil', 'Ceil (round up) · ceil', '向上取整 ceil'), ['value'], 'ceil(random(0, 10))');
  add('min', 'math', words('最小値 min', 'Minimum · min', '最小值 min'), ['a', 'b'], 'min(mouseX, 200)');
  add('max', 'math', words('最大値 max', 'Maximum · max', '最大值 max'), ['a', 'b'], 'max(mouseX, 50)');
  add('frameRate', 'control', words('毎秒のフレーム数', 'Frame rate', '帧率'), ['fps'], 'frameRate(30);');
  add('noLoop', 'control', words('繰り返し描画を止める', 'Stop looping', '停止循环'), [], 'noLoop();');
  add('loop', 'control', words('繰り返し描画を再開', 'Resume looping', '继续循环'), [], 'loop();');
  add('userStartAudio', 'sound', words('音声を開始する', 'Start audio', '启动音频'), [], 'userStartAudio();');
  add('loadSound', 'sound', words('音を読み込む', 'Load sound', '加载声音'), ['file'], 'loadSound("assets/sound.mp3")');
  add('normalMaterial', 'three', words('法線色の材質', 'Normal material', '法线材质'), [], 'normalMaterial();');
  add('ambientLight', 'three', words('環境光', 'Ambient light', '环境光'), ['brightness'], 'ambientLight(160);');
  add('directionalLight', 'three', words('平行光', 'Directional light', '平行光'), ['red', 'green', 'blue', 'x', 'y', 'z'], 'directionalLight(255, 255, 255, 0, 0, -1);');
  add('rotateX', 'three', words('X軸回転', 'Rotate X', '绕 X 轴旋转'), ['angle'], 'rotateX(frameCount * 0.01);');
  add('rotateY', 'three', words('Y軸回転', 'Rotate Y', '绕 Y 轴旋转'), ['angle'], 'rotateY(frameCount * 0.01);');
  add('box', 'three', words('立方体', 'Box', '立方体'), ['size'], 'box(100);');
  add('sphere', 'three', words('球', 'Sphere', '球体'), ['radius'], 'sphere(80);');
  add('torus', 'three', words('ドーナツ', 'Torus', '圆环'), ['radius', 'tubeRadius'], 'torus(80, 24);');
  add('orbitControl', 'three', words('指で視点を動かす', 'Orbit controls', '拖动视角'), [], 'orbitControl();');
  add('resizeCanvas', 'drawing', words('キャンバスのサイズを変える', 'Resize canvas', '调整画布大小'), ['width','height'], 'resizeCanvas(windowWidth, windowHeight);');
  add('square', 'drawing', words('正方形', 'Square', '正方形'), ['x','y','size'], 'square(100, 100, 80);');
  add('arc', 'drawing', words('円弧', 'Arc', '圆弧'), ['x','y','width','height','start','stop'], 'arc(200, 200, 120, 120, 0, PI);');
  add('beginShape', 'drawing', words('形を描き始める', 'Begin shape', '开始形状'), [], 'beginShape();');
  add('vertex', 'drawing', words('頂点', 'Vertex', '顶点'), ['x','y'], 'vertex(100, 100);');
  add('endShape', 'drawing', words('形を閉じる', 'End shape', '结束形状'), ['mode'], 'endShape(CLOSE);');
  add('textAlign', 'style', words('文字の位置合わせ', 'Text alignment', '文字对齐'), ['horizontal','vertical'], 'textAlign(CENTER, CENTER);');
  add('rectMode', 'style', words('四角の基準位置', 'Rectangle mode', '矩形模式'), ['mode'], 'rectMode(CENTER);');
  add('ellipseMode', 'style', words('楕円の基準位置', 'Ellipse mode', '椭圆模式'), ['mode'], 'ellipseMode(CENTER);');
  add('imageMode', 'style', words('画像の基準位置', 'Image mode', '图像模式'), ['mode'], 'imageMode(CENTER);');
  add('colorMode', 'style', words('色の指定方法', 'Color mode', '颜色模式'), ['mode','maximum'], 'colorMode(HSB, 360, 100, 100);');
  add('blendMode', 'style', words('色の合成方法', 'Blend mode', '混合模式'), ['mode'], 'blendMode(ADD);');
  add('angleMode', 'transform', words('角度の単位', 'Angle units', '角度单位'), ['mode'], 'angleMode(DEGREES);');
  add('millis', 'input', words('開始からのミリ秒', 'Milliseconds since start', '启动后的毫秒数'), [], 'millis()');
  add('second', 'input', words('現在の秒', 'Current second', '当前秒'), [], 'second()');
  add('minute', 'input', words('現在の分', 'Current minute', '当前分钟'), [], 'minute()');
  add('hour', 'input', words('現在の時', 'Current hour', '当前小时'), [], 'hour()');
  add('keyIsDown', 'input', words('キーが押されている', 'Key is down', '按键状态'), ['key'], 'keyIsDown(LEFT_ARROW)');
  add('pointLight', 'three', words('点光源', 'Point light', '点光源'), ['red','green','blue','x','y','z'], 'pointLight(255, 255, 255, 0, 0, 200);');
  add('lights', 'three', words('標準のライト', 'Default lights', '默认光照'), [], 'lights();');
  add('ambientMaterial', 'three', words('環境光の材質', 'Ambient material', '环境光材质'), ['color'], 'ambientMaterial("#6c5ce7");');
  add('specularMaterial', 'three', words('光沢のある材質', 'Specular material', '高光材质'), ['color'], 'specularMaterial("#ffffff");');
  add('shininess', 'three', words('光沢の強さ', 'Shininess', '光泽度'), ['amount'], 'shininess(40);');
  add('texture', 'three', words('画像を表面に貼る', 'Apply texture', '应用纹理'), ['image'], 'texture(img);');
  add('camera', 'three', words('カメラの位置', 'Camera position', '相机位置'), ['eyeX','eyeY','eyeZ','centerX','centerY','centerZ','upX','upY','upZ'], 'camera(0, 0, 600, 0, 0, 0, 0, 1, 0);');
  add('perspective', 'three', words('透視投影', 'Perspective', '透视投影'), [], 'perspective();');
  const TEMPLATES = [
    {id:'setup', category:'events', label:words('最初に一度 setup', 'Once at start · setup', '启动时 · setup'), source:'function setup() {\n  createCanvas(400, 400);\n}\n'},
    {id:'draw', category:'events', label:words('毎フレーム draw', 'Every frame · draw', '每一帧 · draw'), source:'function draw() {\n  background(240);\n}\n'},
    {id:'mousePressed', category:'events', label:words('押したとき mousePressed', 'When pressed · mousePressed', '按下时 · mousePressed'), source:'function mousePressed() {\n  circle(mouseX, mouseY, 40);\n}\n'},
    {id:'touchStarted', category:'events', label:words('指が触れたとき', 'When touched', '触摸时'), source:'function touchStarted() {\n  return false;\n}\n'},
    {id:'touchMoved', category:'events', label:words('指を動かしたとき', 'When touch moves', '触摸移动时'), source:'function touchMoved() {\n  return false;\n}\n'},
    {id:'touchEnded', category:'events', label:words('指を離したとき', 'When touch ends', '触摸结束时'), source:'function touchEnded() {\n  return false;\n}\n'},
    {id:'mouseDragged', category:'events', label:words('押したまま動かしたとき', 'When dragged', '拖动时'), source:'function mouseDragged() {\n}\n'},
    {id:'mouseReleased', category:'events', label:words('離したとき', 'When released', '松开时'), source:'function mouseReleased() {\n}\n'},
    {id:'keyPressed', category:'events', label:words('キーを押したとき', 'When a key is pressed', '按键时'), source:'function keyPressed() {\n}\n'},
    {id:'keyReleased', category:'events', label:words('キーを離したとき', 'When a key is released', '松开按键时'), source:'function keyReleased() {\n}\n'},
    {id:'windowResized', category:'events', label:words('画面サイズが変わったとき', 'When the window resizes', '窗口大小改变时'), source:'function windowResized() {\n  resizeCanvas(windowWidth, windowHeight);\n}\n'},
    {id:'deviceMoved', category:'events', label:words('端末を動かしたとき', 'When the device moves', '设备移动时'), source:'function deviceMoved() {\n}\n'},
    {id:'deviceShaken', category:'events', label:words('端末を振ったとき', 'When the device is shaken', '摇动设备时'), source:'function deviceShaken() {\n}\n'},
    {id:'if', category:'control', label:words('もし〜なら', 'If', '如果'), source:'if (mouseIsPressed) {\n  circle(mouseX, mouseY, 40);\n}\n'},
    {id:'ifelse', category:'control', label:words('もし〜なら／そうでなければ', 'If / else', '如果 / 否则'), source:'if (mouseIsPressed) {\n  fill(255);\n} else {\n  fill(0);\n}\n'},
    {id:'for', category:'control', label:words('回数を決めて繰り返す', 'Repeat with counter', '计数循环'), source:'for (let i = 0; i < 10; i++) {\n  circle(i * 40, 200, 20);\n}\n'},
    {id:'let', category:'variables', label:words('変数を作る let', 'Declare variable · let', '声明变量 · let'), source:'let size = 80;'},
    {id:'const', category:'variables', label:words('定数を作る const', 'Declare constant · const', '声明常量 · const'), source:'const speed = 2;'},
    {id:'assignment', category:'variables', label:words('値を代入', 'Assign a value', '赋值'), source:'size = 100;'},
    {id:'update', category:'variables', label:words('値を1増やす', 'Increment by one', '加一'), source:'size++;'},
    {id:'function', category:'functions', label:words('自分の関数', 'Custom function', '自定义函数'), source:'function drawShape(x, y) {\n  circle(x, y, 40);\n}\n'},
    {id:'return', category:'functions', label:words('値を返す', 'Return value', '返回值'), source:'return 0;'},
    {id:'number', category:'math', label:words('数値', 'Number', '数字'), source:'80'},
    {id:'binary', category:'math', label:words('計算', 'Calculation', '计算'), source:'10 + 20'},
    {id:'string', category:'text', label:words('文字列', 'Text', '文本'), source:'"Hello!"'},
    {id:'boolean', category:'control', label:words('真偽値', 'Boolean', '布尔值'), source:'true'},
    {id:'array', category:'variables', label:words('配列（0から数える）', 'Array · index starts at 0', '数组 · 索引从 0 开始'), source:'[10, 20, 30]'},
    ...['mouseX','mouseY','mouseIsPressed','width','height','frameCount','PI','TWO_PI'].map(name => ({id:name, category:'input', label:words(name, name, name), source:name})),
    {id:'accelerationX', category:'input', label:words('端末の横の加速度 accelerationX', 'Device acceleration X', '设备横向加速度'), source:'accelerationX'},
    {id:'accelerationY', category:'input', label:words('端末の縦の加速度 accelerationY', 'Device acceleration Y', '设备纵向加速度'), source:'accelerationY'},
    {id:'accelerationZ', category:'input', label:words('端末の前後の加速度 accelerationZ', 'Device acceleration Z', '设备前后加速度'), source:'accelerationZ'},
    {id:'rotationX', category:'input', label:words('端末の前後傾き rotationX', 'Device tilt X (pitch)', '设备前后倾斜 rotationX'), source:'rotationX'},
    {id:'rotationY', category:'input', label:words('端末の左右傾き rotationY', 'Device tilt Y (roll)', '设备左右倾斜 rotationY'), source:'rotationY'},
    {id:'rotationZ', category:'input', label:words('端末の回転 rotationZ', 'Device rotation Z (yaw)', '设备偏航旋转 rotationZ'), source:'rotationZ'},
    {id:'touches', category:'input', label:words('触れている指の配列 touches', 'Active touches array', '触摸点数组 touches'), source:'touches'},
    ...Object.entries(P5_CALLS).map(([id, value]) => ({id, ...value})),
    {id:'oscillator', category:'sound', label:words('音を鳴らす（操作イベント内）', 'Play tone · in an input event', '播放音调 · 在输入事件中'), source:'const tone = new p5.Oscillator("sine");\ntone.freq(440);\ntone.amp(0.1);\ntone.start();\n'},
    {id:'forOf',category:'control',label:words('配列の要素ごとに繰り返す','For each array item','遍历数组元素'),source:'for (const item of values) {\n  circle(item, 200, 20);\n}\n'},
    {id:'comparison',category:'control',label:words('値を比べる','Compare values','比较值'),source:'mouseX > width / 2'},
    {id:'logical',category:'control',label:words('両方の条件を満たす','Both conditions','同时满足条件'),source:'mouseIsPressed && mouseX > 100'},
    {id:'not',category:'control',label:words('条件を反転','Negate condition','条件取反'),source:'!mouseIsPressed'},
    {id:'arrayAccess',category:'variables',label:words('配列の要素（0から数える）','Array item · zero-based index','数组元素 · 索引从 0 开始'),source:'values[0]'},
    {id:'arrayLength',category:'variables',label:words('配列の長さ','Array length','数组长度'),source:'values.length'},
    {id:'arrayPush',category:'variables',label:words('配列の最後に追加','Append to array','添加到数组末尾'),source:'values.push(40);'},
    {id:'arrayPop',category:'variables',label:words('配列の最後を取り出す','Remove last from array','从数组末尾取出'),source:'values.pop();'},
    {id:'functionCall',category:'functions',label:words('自分の関数を呼ぶ','Call custom function','调用自定义函数'),source:'drawShape(100, 100);'},
    {id:'imageLoader',category:'assets',label:words('画像を読み込む（setup 内）','Load image · in setup','加载图像 · 在 setup 内'),source:'const img = await loadImage("assets/image.png");',sourceV1:'const img = loadImage("assets/image.png");'},
    {id:'asyncSetup',category:'assets',label:words('画像用の非同期 setup（2.x）','Async setup for images · 2.x','异步 setup 加载图像 · 2.x'),source:'async function setup() {\n  createCanvas(400, 400);\n  const img = await loadImage("assets/image.png");\n  image(img, 0, 0);\n}\n',sourceV1:'let img;\nfunction preload() {\n  img = loadImage("assets/image.png");\n}\nfunction setup() {\n  createCanvas(400, 400);\n  image(img, 0, 0);\n}\n'},
    {id:'soundLoader',category:'sound',label:words('音声ファイルを読み込む（setup 内）','Load audio file · in setup','加载音频文件 · 在 setup 内'),source:'const sound = await loadSound("assets/sound.mp3");',sourceV1:'const sound = loadSound("assets/sound.mp3");'},
    {id:'soundPlay',category:'sound',label:words('音声ファイルを再生（操作イベント内）','Play audio · in an input event','播放音频 · 在输入事件中'),source:'userStartAudio();\nsound.play();'},
    {id:'soundStop',category:'sound',label:words('音声ファイルを停止','Stop audio','停止音频'),source:'sound.stop();'},
    {id:'soundLoop',category:'sound',label:words('音声ファイルを繰り返す','Loop audio','循环音频'),source:'sound.loop();\nsound.play();',sourceV1:'sound.loop();'},
    {id:'soundVolume',category:'sound',label:words('音量','Audio volume','音量'),source:'sound.amp(0.5);',sourceV1:'sound.setVolume(0.5);'},
    {id:'soundRate',category:'sound',label:words('再生速度','Playback rate','播放速度'),source:'sound.rate(1);'},
    {id:'soundPan',category:'sound',label:words('音の左右の位置','Audio pan','声像'),source:'sound.disconnect();\nsound.connect(new p5.Panner(0));',sourceV1:'sound.pan(0);'},
    {id:'paramSlider',category:'parameters',label:words('スライダー変数 @slider','Slider parameter · @slider','滑块参数 · @slider'),source:'let speed = 1; // @slider(0, 5, 0.1) "Speed"'},
    {id:'paramColor',category:'parameters',label:words('カラー変数 @color','Color parameter · @color','颜色参数 · @color'),source:'let ink = "#BA90E2"; // @color "Ink"'},
    {id:'paramToggle',category:'parameters',label:words('スイッチ変数 @toggle','Toggle parameter · @toggle','开关参数 · @toggle'),source:'let active = true; // @toggle "Active"'},
    {id:'paramChoice',category:'parameters',label:words('選択変数 @choice','Choice parameter · @choice','选项参数 · @choice'),source:'let shape = "circle"; // @choice(circle, square, triangle) "Shape"'},
    {id:'paramXY',category:'parameters',label:words('2D座標変数 @xy','2D XY pad · @xy','2D 坐标板 · @xy'),source:'let pos = { x: 0.5, y: 0.5 }; // @xy(0, 1) "Position"'},
    {id:'brushPick',category:'brush',label:words('ブラシを選ぶ brush.pick','Pick brush · brush.pick','选择画笔 · brush.pick'),source:'brush.pick("watercolor");'},
    {id:'brushStroke',category:'brush',label:words('ブラシの色 brush.stroke','Brush stroke · brush.stroke','画笔颜色 · brush.stroke'),source:'brush.stroke("#2c3e50");'},
    {id:'brushWeight',category:'brush',label:words('ブラシの太さ brush.strokeWeight','Brush weight · brush.strokeWeight','画笔粗细 · brush.strokeWeight'),source:'brush.strokeWeight(2);'},
    {id:'brushFill',category:'brush',label:words('水彩塗り brush.fill','Watercolor fill · brush.fill','水彩填充 · brush.fill'),source:'brush.fill("#e17055", 90);'},
    {id:'brushBleed',category:'brush',label:words('にじみ brush.bleed','Watercolor bleed · brush.bleed','晕染效果 · brush.bleed'),source:'brush.bleed(0.25, "out");'},
    {id:'brushLine',category:'brush',label:words('ブラシで線を描く brush.line','Brush line · brush.line','画笔直线 · brush.line'),source:'brush.line(-120, 0, 120, 0);'},
    {id:'brushCircle',category:'brush',label:words('ブラシで円を描く brush.circle','Brush circle · brush.circle','画笔圆形 · brush.circle'),source:'brush.circle(0, 0, 80);'},
    {id:'brushRect',category:'brush',label:words('ブラシで四角を描く brush.rect','Brush rectangle · brush.rect','画笔矩形 · brush.rect'),source:'brush.rect(-60, -40, 120, 80);'},
    {id:'brushHatch',category:'brush',label:words('ハッチング brush.hatch','Hatching · brush.hatch','交叉阴影 · brush.hatch'),source:'brush.hatch(8, 45);'},
    {id:'matterEngine',category:'physics',label:words('物理エンジンを作る Matter.Engine','Create physics engine · Matter.Engine','创建物理引擎 · Matter.Engine'),source:'const engine = Matter.Engine.create();'},
    {id:'matterCircle',category:'physics',label:words('物理の円を作る Bodies.circle','Physics circle · Bodies.circle','物理圆形 · Bodies.circle'),source:'const ball = Matter.Bodies.circle(200, 60, 24, { restitution: 0.7 });'},
    {id:'matterBox',category:'physics',label:words('物理の床・箱を作る Bodies.rectangle','Physics box/ground · Bodies.rectangle','物理方块/地面 · Bodies.rectangle'),source:'const ground = Matter.Bodies.rectangle(200, 380, 380, 24, { isStatic: true });'},
    {id:'matterAdd',category:'physics',label:words('物理ワールドへ追加 Composite.add','Add to physics world · Composite.add','添加到物理世界 · Composite.add'),source:'Matter.Composite.add(engine.world, [ball, ground]);'},
    {id:'matterUpdate',category:'physics',label:words('物理を1ステップ進める Engine.update','Step physics · Engine.update','更新物理模拟 · Engine.update'),source:'Matter.Engine.update(engine);'},
  ];
  // Guided actions make complete source edits; placeholder snippets are never inserted directly.
  const eventLabels = {
    setup:words('最初に一度 setup','Once at start · setup','启动时 · setup'),
    draw:words('毎フレーム draw','Every frame · draw','每一帧 · draw'),
    preload:words('先に素材を読み込む preload（1.x）','Load assets first · preload (1.x)','预加载素材 · preload（1.x）'),
    mousePressed:words('押したとき mousePressed','When pressed · mousePressed','按下时 · mousePressed'),
    mouseReleased:words('離したとき mouseReleased','When released · mouseReleased','松开时 · mouseReleased'),
    mouseClicked:words('クリックしたとき','When clicked','点击时'),
    doubleClicked:words('ダブルクリックしたとき','When double clicked','双击时'),
    mouseMoved:words('押さずに動かしたとき','When moved','移动时'),
    mouseDragged:words('押したまま動かしたとき','When dragged','拖动时'),
    mouseWheel:words('ホイールを動かしたとき','When the wheel moves','滚轮移动时'),
    touchStarted:words('指が触れたとき（1.x）','When touched (1.x)','触摸时（1.x）'),
    touchMoved:words('指を動かしたとき（1.x）','When touch moves (1.x)','触摸移动时（1.x）'),
    touchEnded:words('指を離したとき（1.x）','When touch ends (1.x)','触摸结束时（1.x）'),
    keyPressed:words('キーを押したとき','When a key is pressed','按键时'),
    keyReleased:words('キーを離したとき','When a key is released','松开按键时'),
    keyTyped:words('文字を入力したとき','When a character is typed','输入字符时'),
    windowResized:words('画面サイズが変わったとき','When the window resizes','窗口大小改变时'),
    deviceMoved:words('端末を動かしたとき','When the device moves','设备移动时'),
    deviceTurned:words('端末を回したとき','When the device turns','设备转动时'),
    deviceShaken:words('端末を振ったとき','When the device is shaken','摇动设备时')
  };
  const EVENTS = Object.entries(eventLabels).map(([name,label]) => ({name,label,versions:/^(preload|touchStarted|touchMoved|touchEnded)$/.test(name)?[1]:[1,2]}));
  for (const event of EVENTS) {
    let template = TEMPLATES.find(t=>t.id===event.name);
    if (!template) { template={id:event.name,category:'events',source:'function '+event.name+'() {\n}\n'}; TEMPLATES.push(template); }
    Object.assign(template,{label:event.label,versions:event.versions,authoringAction:{type:'ensureEvent',name:event.name}});
  }
  function guided(ids, action) { for (const id of ids) {const template=TEMPLATES.find(t=>t.id===id);if(template)template.authoringAction=Object.assign({},action);} }
  guided(['loadImage','imageLoader','asyncSetup'],{type:'loadAsset',kind:'image'});
  guided(['loadSound','soundLoader'],{type:'loadAsset',kind:'sound'});
  guided(['image'],{type:'useResource',action:'image',kind:'image'});
  guided(['texture'],{type:'useResource',action:'texture',kind:'image'});
  guided(['oscillator'],{type:'createOscillator'});
  const resourceActions = {
    soundPlay:words('音声を再生','Play loaded audio','播放已加载音频'),
    soundStop:words('音声を停止','Stop loaded audio','停止已加载音频'),
    soundLoop:words('音声を繰り返す','Loop loaded audio','循环已加载音频'),
    soundVolume:words('音声の音量','Loaded audio volume','已加载音频音量'),
    soundRate:words('音声の再生速度','Loaded audio rate','已加载音频播放速度'),
    soundPan:words('音声の左右の位置','Loaded audio pan','已加载音频声像'),
    toneStart:words('オシレーターを鳴らす','Play oscillator','播放振荡器'),
    toneStop:words('オシレーターを消音','Silence oscillator','静音振荡器'),
    toneFreq:words('オシレーターの周波数','Oscillator frequency','振荡器频率'),
    toneVolume:words('オシレーターの音量','Oscillator volume','振荡器音量')
  };
  for (const [id,label] of Object.entries(resourceActions)) {
    let template=TEMPLATES.find(t=>t.id===id);
    if(!template){template={id,category:'sound',source:id+';'};TEMPLATES.push(template);}
    Object.assign(template,{label,authoringAction:{type:'useResource',action:id,kind:id.startsWith('tone')?'oscillator':'sound'}});
  }
  TEMPLATES.push({id:'webglCanvas',category:'three',label:words('3Dキャンバスを使う WEBGL','Use a 3D canvas · WEBGL','使用 3D 画布 · WEBGL'),source:'createCanvas(400, 400, WEBGL);',authoringAction:{type:'ensureWebGL'}});
  for(const template of TEMPLATES)if(template.category==='three'&&!template.authoringAction)template.requiresWebGL=true;
  for(const template of TEMPLATES){if(['imageLoader','asyncSetup','soundLoader'].includes(template.id))template.hiddenFromPalette=true;if(template.id==='texture')template.requiresWebGL=true;if(['imageLoader','loadImage','asyncSetup'].includes(template.id))template.label=words('画像を読み込む','Load an image','加载图像');if(['soundLoader','loadSound'].includes(template.id))template.label=words('音を読み込む','Load audio','加载音频');if(template.id==='oscillator')template.label=words('音の発生器を用意する','Prepare oscillator','准备振荡器');}
  const EXAMPLES = [
    {id:'drawing',label:words('指についてくる円','Follow your finger','跟随手指的圆'),source:'function setup() {\n  createCanvas(400, 400);\n}\n\nfunction draw() {\n  background(240);\n  noStroke();\n  fill("#6c5ce7");\n  circle(mouseX, mouseY, 80);\n}\n'},
    {id:'paint',label:words('指でお絵描き','Draw with your finger','用手指绘画'),source:'function setup() {\n  createCanvas(400, 400);\n  background(255);\n}\n\nfunction draw() {\n  if (mouseIsPressed) {\n    noStroke();\n    fill("#6c5ce7");\n    circle(mouseX, mouseY, 20);\n  }\n}\n'},
    {id:'pattern',label:words('回る模様','Rotating pattern','旋转图案'),source:'function setup() {\n  createCanvas(400, 400);\n}\n\nfunction draw() {\n  background("#17233d");\n  translate(width / 2, height / 2);\n  rotate(frameCount * 0.01);\n  noFill();\n  stroke("#a8c7fa");\n  for (let i = 0; i < 12; i++) {\n    rotate(TWO_PI / 12);\n    circle(80, 0, 100);\n  }\n}\n'},
    {id:'sound',requiresSound:true,label:words('タップして音を鳴らす','Tap to play a tone','点击播放音调'),source:'let tone;\nlet toneStarted = false;\n\nfunction setup() {\n  createCanvas(400, 400);\n  tone = new p5.Oscillator("sine");\n  tone.amp(0);\n}\n\nfunction draw() {\n  background("#17233d");\n  fill("#a8c7fa");\n  noStroke();\n  circle(mouseX, mouseY, mouseIsPressed ? 100 : 50);\n}\n\nfunction mousePressed() {\n  userStartAudio();\n  if (!toneStarted) { tone.start(); toneStarted = true; }\n  tone.freq(map(mouseX, 0, width, 220, 880));\n  tone.amp(0.1, 0.05);\n}\n\nfunction mouseReleased() {\n  tone.amp(0, 0.1);\n}\n'},
    {id:'three',label:words('回る3D立方体','Rotating 3D box','旋转的 3D 立方体'),source:'function setup() {\n  createCanvas(400, 400, WEBGL);\n}\n\nfunction draw() {\n  background(24);\n  orbitControl();\n  normalMaterial();\n  rotateX(frameCount * 0.01);\n  rotateY(frameCount * 0.02);\n  box(120);\n}\n'},
    {id:'parameters',label:words('スライダーとXYパッドで動かす円','Interactive parameters & XY pad','使用参数与XY板控制的圆'),source:'let pos = { x: 0.5, y: 0.5 }; // @xy(0, 1) "Position"\nlet size = 90; // @slider(20, 200, 2) "Size"\nlet ink = "#BA90E2"; // @color "Ink"\n\nfunction setup() {\n  createCanvas(400, 400);\n}\n\nfunction draw() {\n  background(22);\n  noStroke();\n  fill(ink);\n  circle(pos.x * width, pos.y * height, size);\n}\n'},
  ];
  const CATEGORY_LABELS = {events:words('イベント','Events','事件'),drawing:words('描く','Drawing','绘图'),style:words('色と線','Color & style','颜色与样式'),transform:words('移動と回転','Transforms','变换'),control:words('制御','Control','控制'),variables:words('変数と配列','Variables & arrays','变量与数组'),parameters:words('パラメータ','Parameters','参数'),math:words('計算','Math','数学'),input:words('入力と時間','Input & time','输入与时间'),text:words('文字','Text','文本'),functions:words('関数','Functions','函数'),assets:words('素材','Assets','素材'),sound:words('音','Sound','声音'),three:words('3D','3D','3D'),brush:words('ブラシ','Brush','画笔'),physics:words('物理','Physics','物理')};
  const NODE_LABELS = {function:words('関数','Function','函数'),expression_statement:words('実行','Do','执行'),call:words('呼び出し','Call','调用'),literal:words('値','Value','值'),identifier:words('名前','Name','名称'),binary:words('計算・比較','Math / comparison','计算与比较'),unary:words('単項演算','Unary','一元运算'),assignment:words('代入','Assign','赋值'),update:words('増減','Update','增减'),variable_declaration:words('宣言','Declare','声明'),variable_declarator:words('変数','Variable','变量'),array:words('配列','Array','数组'),member:words('プロパティ','Property','属性'),if:words('もし','If','如果'),for:words('繰り返し','For','循环'),for_of:words('要素ごとに','For each','遍历'),return:words('返す','Return','返回'),new:words('作成','Create','创建'),await:words('完了を待つ','Await','等待'),conditional:words('条件による値','Conditional value','条件值'),block:words('処理のまとまり','Statements','语句块'),raw_statement:words('カスタムコード','Custom code','自定义代码'),raw_expression:words('カスタム式','Custom expression','自定义表达式'),comment:words('コメント','Comment','注释')};
  Object.assign(NODE_LABELS,{logical:words('条件の組み合わせ','Logical operation','逻辑运算'),parenthesized:words('括弧','Parentheses','括号'),break:words('ループを抜ける','Break','跳出循环'),continue:words('次の繰り返しへ','Continue','继续循环'),empty:words('空の処理','Empty statement','空语句')});
  const P5_NAMES = Array.from(new Set(('createCanvas resizeCanvas background clear color fill noFill stroke noStroke strokeWeight strokeCap strokeJoin rect square circle ellipse arc line point triangle quad beginShape endShape vertex curveVertex bezierVertex quadraticVertex text textSize textFont textAlign textLeading textStyle textWidth image imageMode tint noTint loadImage loadFont loadJSON loadStrings loadSound soundFormats userStartAudio getAudioContext push pop translate rotate rotateX rotateY rotateZ scale applyMatrix resetMatrix angleMode rectMode ellipseMode frameRate noLoop loop redraw random randomSeed noise noiseSeed noiseDetail map constrain lerp norm dist mag abs ceil floor round min max pow sqrt sq exp log sin cos tan asin acos atan atan2 degrees radians millis second minute hour day month year keyIsDown touches accelerationX accelerationY accelerationZ pAccelerationX pAccelerationY pAccelerationZ rotationX rotationY rotationZ fullscreen pixelDensity displayDensity windowResized saveCanvas saveGif box sphere cylinder cone torus plane ellipsoid ambientLight directionalLight pointLight spotLight lights noLights ambientMaterial specularMaterial normalMaterial emissiveMaterial shininess texture textureMode orbitControl camera perspective ortho createVector createImage createGraphics blendMode erase noErase cursor noCursor smooth noSmooth').split(/\s+/).concat(Object.keys(P5_CALLS))));

  function friendlyErrorMessage(rawMessage, lang = 'ja') {
    const msg = String(rawMessage || '').trim();
    const firstLine = msg.split('\n')[0];

    let m = firstLine.match(/ReferenceError:\s*([a-zA-Z0-9_$]+)\s+is not defined/i);
    if (m) {
      if (lang === 'en') return `Variable or function "${m[1]}" is not defined. Check spelling or declaration.`;
      if (lang === 'zh') return `未定义的变量或函数 "${m[1]}"。请检查拼写或是否已声明。`;
      return `変数または関数「${m[1]}」が見つかりません。名前のつづりや、定義されているか確認してください。`;
    }

    m = firstLine.match(/ReferenceError:\s*Cannot access '([a-zA-Z0-9_$]+)' before initialization/i);
    if (m) {
      if (lang === 'en') return `Cannot access "${m[1]}" before initialization.`;
      if (lang === 'zh') return `在声明前无法访问 "${m[1]}"。`;
      return `「${m[1]}」を使う前に定義（宣言）する必要があります。`;
    }

    m = firstLine.match(/TypeError:\s*(?:([a-zA-Z0-9_$.]+)\s+is not a function|\(?intermediate value\)?(?:[^\n]*)\s+is not a function)/i);
    if (m) {
      const fnName = m[1] || '値';
      if (lang === 'en') return `"${fnName}" is not a function. Check the function name or type.`;
      if (lang === 'zh') return `"${fnName}" 不是函数，无法调用。请检查名称或类型。`;
      return `「${fnName}」は関数ではないため呼び出せません。名前のスペルや関数の定義を確認してください。`;
    }

    const readUndef1 = firstLine.match(/TypeError:\s*Cannot read propert(?:ies|y)\s+of\s+undefined\s*(?:\(reading\s*['"‘“]?([a-zA-Z0-9_$]+)['"’”]?\))?/i);
    const readUndef2 = !readUndef1 && firstLine.match(/TypeError:\s*Cannot read propert(?:ies|y)\s+['"‘“]?([a-zA-Z0-9_$]+)['"’”]?\s+of\s+undefined/i);
    if (readUndef1 || readUndef2) {
      const propName = (readUndef1 ? readUndef1[1] : readUndef2[1]) || null;
      const prop = propName ? `「${propName}」` : '値';
      if (lang === 'en') return `Cannot read ${propName ? `property "${propName}"` : 'properties'} of undefined. Check initial values.`;
      if (lang === 'zh') return `无法读取 undefined 的属性${propName ? ` "${propName}"` : ''}。请检查变量初始值。`;
      return `値が空（undefined）のため、${prop}を読み取れません。変数の初期値を確認してください。`;
    }

    const readNull1 = firstLine.match(/TypeError:\s*Cannot read propert(?:ies|y)\s+of\s+null\s*(?:\(reading\s*['"‘“]?([a-zA-Z0-9_$]+)['"’”]?\))?/i);
    const readNull2 = !readNull1 && firstLine.match(/TypeError:\s*Cannot read propert(?:ies|y)\s+['"‘“]?([a-zA-Z0-9_$]+)['"’”]?\s+of\s+null/i);
    if (readNull1 || readNull2) {
      const propName = (readNull1 ? readNull1[1] : readNull2[1]) || null;
      const prop = propName ? `「${propName}」` : '値';
      if (lang === 'en') return `Cannot read ${propName ? `property "${propName}"` : 'properties'} of null.`;
      if (lang === 'zh') return `无法读取 null 的属性${propName ? ` "${propName}"` : ''}。`;
      return `値が空（null）のため、${prop}を読み取れません。`;
    }

    const setMatch1 = firstLine.match(/TypeError:\s*Cannot set propert(?:ies|y)\s+of\s+(undefined|null)\s*(?:\(setting\s*['"‘“]?([a-zA-Z0-9_$]+)['"’”]?\))?/i);
    const setMatch2 = !setMatch1 && firstLine.match(/TypeError:\s*Cannot set propert(?:ies|y)\s+['"‘“]?([a-zA-Z0-9_$]+)['"’”]?\s+of\s+(undefined|null)/i);
    if (setMatch1 || setMatch2) {
      const nullType = setMatch1 ? (setMatch1[1] === 'null' ? 'null' : 'undefined') : (setMatch2[2] === 'null' ? 'null' : 'undefined');
      const propName = setMatch1 ? setMatch1[2] : setMatch2[1];
      const prop = propName ? `「${propName}」` : '値';
      if (lang === 'en') return `Cannot set ${propName ? `property "${propName}"` : 'property'} on ${nullType}.`;
      if (lang === 'zh') return `无法在 ${nullType} 上设置属性${propName ? ` "${propName}"` : ''}。`;
      return `対象が空（${nullType}）のため、${prop}を設定できません。`;
    }

    if (/Assignment to constant variable/i.test(firstLine)) {
      if (lang === 'en') return `Cannot reassign a constant variable. Use "let" instead of "const".`;
      if (lang === 'zh') return `无法修改常量。请使用 "let" 代替 "const"。`;
      return `const で定義された定数は変更できません。値を変更するには let で定義してください。`;
    }

    if (/Maximum call stack size exceeded/i.test(firstLine)) {
      if (lang === 'en') return `Maximum call stack size exceeded. Check for infinite recursion or loops.`;
      if (lang === 'zh') return `超出最大调用栈深度。请检查是否存在无限递归或循环。`;
      return `関数が自分自身を呼び出し続けています（無限ループ）。終了条件を確認してください。`;
    }

    const p5ArgMatch = firstLine.match(/(?:p5\.js says:\s*(?:\[[^\]]*\]\s*)?)?([a-zA-Z0-9_$]+)(?:\(\))?\s+(?:was expecting|expects)\s+at least\s+(\d+)\s+arguments?(?:,\s*but received only\s+(\d+))?/i);
    if (p5ArgMatch) {
      const fn = p5ArgMatch[1], min = p5ArgMatch[2], got = p5ArgMatch[3];
      if (lang === 'en') return `Function "${fn}" expects at least ${min} arguments${got ? `, but received only ${got}` : ''}.`;
      if (lang === 'zh') return `函数 "${fn}" 至少需要 ${min} 个参数${got ? `，但仅接收到 ${got} 个` : ''}。`;
      return `関数「${fn}」には引数が少なくとも${min}個必要です${got ? `（渡された引数: ${got}個）` : ''}。`;
    }

    m = firstLine.match(/^(ReferenceError|TypeError|RangeError|SyntaxError):\s*(.*)/i);
    if (m) {
      const typeName = m[1];
      const rest = m[2];
      if (lang === 'ja') {
        const typeJa = typeName === 'ReferenceError' ? '名前の参照エラー' :
                       typeName === 'TypeError' ? '型の不一致エラー' :
                       typeName === 'RangeError' ? '数値の範囲エラー' : '文法エラー';
        return `${typeJa}: ${rest}`;
      }
      if (lang === 'zh') {
        const typeZh = typeName === 'ReferenceError' ? '引用错误' :
                       typeName === 'TypeError' ? '类型错误' :
                       typeName === 'RangeError' ? '范围错误' : '语法错误';
        return `${typeZh}: ${rest}`;
      }
      return `${typeName}: ${rest}`;
    }

    return firstLine || (lang === 'ja' ? '実行時エラーが発生しました' : lang === 'zh' ? '发生运行时错误' : 'Runtime error occurred');
  }

  const catalogue = Object.freeze({API_VERSION:2,P5_CALLS,P5_NAMES,EVENTS,EVENT_NAMES:EVENTS.map(event=>event.name),TEMPLATES,EXAMPLES,CATEGORY_LABELS,NODE_LABELS,friendlyErrorMessage});
  root.EditRinBlockCatalogue = catalogue;
  if(typeof module==='object'&&module.exports)module.exports=catalogue;
})(typeof globalThis !== 'undefined' ? globalThis : this);
