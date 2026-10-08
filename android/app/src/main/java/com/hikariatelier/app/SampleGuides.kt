package com.hikariatelier.app

/** Bundled-only learning notes; never saved into user work or template data. */
internal val sampleGuides = mapOf(
    "shapes" to "配色と図形の重なりを学ぶ作品。タップで3つの配色を切り替えます。",
    "touch" to "タップやドラッグで波紋を描きます。時間による変化と、描画する波紋の数の管理を学べます。",
    "halo" to "36本の楕円が静かに回る光の輪。パラメータで速度を変更します。回転・繰り返し・位相のずれを学べます。",
    "gravity" to "64個の粒子が重力で軌道を描きます。画面を押したまま指を動かすと、引力の中心が移動します。パラメータで強さと色を変更できます。",
    "wave-parameter" to "細い平行線がゆっくり揺れるレンズ状の作品。パラメータで速度・線幅・色を変更します。",
    "webgpu" to "18枚の薄い板が静かにねじれる立体彫刻です。速度・大きさ・差し色を変更できます。WebGPUが使えない場合はWebGLで描画します。",
    "sound" to "指で弾く短い音と波形の作品。押したまま横へ動かすと音程が変わります。小さな音量から試してください。",
    "camera" to "カメラ映像を白黒の網点で映します。タップで開始・停止できます。カメラの許可が必要です。失敗したときは、もう一度タップしてお試しください。",
    "microphone" to "声の大きさに合わせて、放射状の線が開きます。タップで開始・停止できます。マイクの許可が必要です。音はスピーカーへ出力しません。",
    "sensor" to "端末を傾けてビー玉を動かします。タップで傾きを補正し、端末を振るとリセットします。センサーがなくてもドラッグで操作できます。",
)

internal fun sampleGuide(work: Work): String? =
    if (work.isSample) sampleGuides[work.id.removePrefix("builtin-sample:").trimEnd(':')] else null
