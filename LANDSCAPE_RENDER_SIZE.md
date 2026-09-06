# Preview sizing / プレビューの描画サイズ

## English

Ordinary preview pane resizing preserves the logical canvas dimensions and drawing buffer established by `createCanvas`. The runner fits the whole canvas in the preview using uniform CSS scaling and centers it. It does not call `resizeCanvas` just to fit the pane. Screenshots and recordings use the drawing buffer.

A sketch may implement its own `windowResized` callback and call `resizeCanvas`; that sketch-controlled behavior takes precedence. New works offer fixed dimensions or a responsive template using `windowWidth`, `windowHeight`, and `windowResized`. Fullscreen orientation exchange is an explicit resize operation; see [fullscreen behavior](FONTS_AND_CANVAS_ORIENTATION.md).

Runner tests cover repeated resizing, high-DPI buffers, non-square canvases, WebGL-like contexts and paused content. Real-device rendering, touch coordinates and recording still require device testing.

## 日本語

通常のプレビュー枠の変更では、`createCanvas`で作成した論理寸法と描画バッファーを維持します。CSSの表示サイズだけを同じ倍率で調整し、作品全体を枠の中央に配置します。枠に収めるためだけに`resizeCanvas`を呼びません。スクリーンショット・録画は描画バッファーを使用します。

作品自身が`windowResized`や`resizeCanvas`を実装している場合は、その動作を優先します。新規作成時は固定サイズとレスポンシブを選択できます。全画面の縦横切替は明示的なリサイズ操作です。[全画面の動作説明](FONTS_AND_CANVAS_ORIENTATION.md)も参照してください。

ランナーテストは反復リサイズ、高DPI、長方形、WebGL相当のコンテキスト、停止中の内容を対象にしています。実機の描画・タッチ座標・録画は端末での確認が必要です。
