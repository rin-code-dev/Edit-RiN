package com.hikariatelier.app

/** Replace only known standalone comments in canonical bundled sources. */
internal fun localizedSampleCode(source: String, language: String): String =
    source.splitToSequence('\n').joinToString("\n") { line ->
        val comment = line.trimStart().takeIf { it.startsWith("// ") }?.removePrefix("// ")
        val translation = sampleCommentTranslations[comment]
        val text = when (language) {
            "ja" -> translation?.first
            "zh" -> translation?.second
            else -> null
        }
        if (text == null) line else line.takeWhile { it.isWhitespace() } + "// " + text
    }

internal val sampleCommentTranslations = mapOf(
    "Halo — quiet orbital light" to ("静かな光の軌道" to "静谧的光轨"),
    "Repeating ellipses create one slowly breathing halo." to ("楕円の繰り返しで静かに呼吸する輪を描きます。" to "重复的椭圆组成缓缓呼吸的光环。"),
    "Each ring follows the same rule with a small phase offset." to ("同じ規則の輪を少しずつずらして重ねます。" to "遵循同一规则的光环以微小相位差叠加。"),
    "Palette — balance" to ("色と余白のバランス" to "配色与留白的平衡"),
    "Tap to change the palette." to ("タップで配色を切り替え。" to "点击切换配色。"),
    "A disc, its cutout, and one counterweight." to ("円と切り抜き、小さな重心。" to "圆形、镂空和一个小小的重心。"),
    "Ripples — touch and time" to ("タッチで広がる波紋" to "触摸扩散的波纹"),
    "Tap or drag to paint." to ("タップ・ドラッグで波紋を描きます。" to "点击或拖动绘制波纹。"),
    "A quiet pool remains visible before and after interaction." to ("操作していない時も静かな水面。" to "交互前后，静谧的水面始终可见。"),
    "Gravity — orbital ink" to ("重力で描く軌道" to "引力描绘的轨道"),
    "Hold or drag to move the attractor." to ("押したまま動かして重力の中心を移動。" to "按住或拖动以移动引力中心。"),
    "Particle count" to ("粒子の数" to "粒子数量"),
    "Softening prevents an infinite force at the center." to ("中心で力が発散しないよう距離を制限。" to "限制距离，避免中心的引力无限增大。"),
    "Weave — flowing threads" to ("動く織り模様" to "流动的线条"),
    "Change speed, thickness and color in Parameters." to ("パラメータで速度・線幅・色を変更。" to "在参数中调整速度、线宽和颜色。"),
    "Parallel threads bend into one floating lens." to ("平行な糸がひとつのレンズに曲がります。" to "平行线弯曲形成悬浮的透镜。"),
    "WebGPU — kinetic sculpture" to ("動く彫刻" to "动态雕塑"),
    "WebGL remains available." to ("失敗時はWebGLを使います。" to "失败时仍可使用WebGL。"),
    "Eighteen thin slabs slowly twist around one axis." to ("18枚の薄い板が静かにねじれます。" to "十八片薄板围绕同一轴线缓慢扭转。"),
    "Sound — pocket instrument" to ("指で弾く楽器" to "指尖乐器"),
    "Hold and slide to change pitch." to ("押したまま動かすと音程が変わります。" to "按住并滑动改变音高。"),
    "Short notes stop even when drawing is paused." to ("停止時にも短い音で自動的に止まります。" to "即使绘制暂停，短音也会自动停止。"),
    "Sound becomes a standing wave; idle motion stays subtle." to ("音を定在波に、待機中は静かな揺れ。" to "声音化为驻波，静止时只保留轻微起伏。"),
    "Preview pause/resume controls this audio context." to ("プレビューの停止・再開と音を連動。" to "预览的暂停与继续会控制此音频上下文。"),
    "Camera — halftone mirror" to ("網点の鏡" to "网点镜像"),
    "Tap to allow the camera; tap again to stop." to ("タップでカメラを許可し、再タップで停止。" to "点击授权摄像头，再次点击停止。"),
    "Center crop before sampling, preserving the source aspect ratio." to ("比率を保った中央切り抜き。" to "居中裁剪后采样，保持原始宽高比。"),
    "Microphone — voice bloom" to ("Microphone — 声で開く線" to "Microphone — 随声音展开的线条"),
    "Tap to allow the microphone; tap again to stop." to ("タップでマイクを許可し、再タップで停止。" to "点击授权麦克风，再次点击停止。"),
    "No speaker connection: avoids feedback." to ("スピーカーに接続せずハウリングを防止。" to "不连接扬声器，避免啸叫。"),
    "A fine radial aperture opens with the voice." to ("声で開く繊細な放射状の絞り。" to "精细的放射状线条随着声音展开。"),
    "Sensor — tilt marble" to ("Sensor — 傾きで動く円盤" to "Sensor — 倾斜控制的圆盘"),
    "Tap to calibrate; shake to reset." to ("タップで現在の傾きを基準にし、振るとリセット。" to "点击校准，摇动重置。"),
    "Drag also works without sensors." to ("センサーがなくてもドラッグで動かせます。" to "没有传感器时也可拖动操作。"),
    "A soft shadow gives a single disc weight." to ("柔らかな影で一枚の円に重さを。" to "柔和的阴影赋予圆盘重量。")
)
