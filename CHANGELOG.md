# Changelog

## ver1.0.1 — 2026-09-08

- 作品メニューの「実行環境」から、p5.js 2.3.3と1.11.5を作品ごとに選択できるようにしました。新しい作品は2.3.3、旧形式から読み込んだ作品は互換性のため1.11.5で動きます。
- p5.sound 0.4.1を同梱しました。作品ごとにオンにすると、音声素材の再生、音の合成、FFTなどの解析を利用できます。プレビュー内のボタンをタップすると音声を開始します。
- p5.js Web Editorから取り込む作品は、`index.html`からp5.js 2.xとp5.soundの指定を読み取ります。
- 作品フォルダーの`works.json`について、将来の正のversion番号を上限で拒否していた問題を修正しました。アプリが理解できる既知フィールドを読み込みます。
- アプリ版を`1.0.1`、versionCodeを`7`へ更新しました。

## ver1.00 — 2026-09-08

正式リリース。アプリ表示は `1.00`、Gitタグは `v1.0.0`、versionCodeは `6` です。

- **p5.jsアカウント連携**：p5.js Web Editorのユーザー名から公開作品を一覧表示し、選択した作品の`sketch.js`、追加JavaScript、画像・フォント・JSON・シェーダーなどの素材を端末へ取り込めます。パスワードは使用しません。
- **縦画面の編集領域**：「編集時にプレビューを隠す」を標準でオンに変更しました。コード欄を選ぶとプレビューを非表示にし、フォーカスを外すかキーボードを閉じると戻ります。プレビュー縮小より優先し、横画面には影響しません。設定保存とZIPバックアップ・復元に対応します。
- **起動時の更新確認**：GitHubの正式リリースをバックグラウンドで確認し、新しいバージョンがあるときだけ表示します。最新・オフライン・通信失敗時は何も表示しません。設定からの手動確認は結果を表示します。
- **設定の整理**：「編集キー」「サイズ調整バー」「画面の向きを固定」「保存とバックアップ」など、名称と説明を簡潔に統一。日本語・英語・中国語に対応します。
- **構造と処理の改善**：更新状態をViewModelに集約して回転・画面移動をまたいで保持。更新ダイアログ、入力候補、縦画面の高さ計算を分離しました。候補検索の重複をなくし、カーソルより前のコード全体のコピーを避けます。プレビューの非表示・再表示でWebViewや作品を作り直しません。

Stable release: portrait preview hiding, silent startup checks for stable updates, clearer settings in all three languages, shared lifecycle-aware update state and reduced completion work. The signed APK updates beta.4 using the same signing certificate.

Previous releases: [Beta notes](BETA_NOTES.md).
