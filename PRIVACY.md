# プライバシー / Privacy

## 日本語

作品・設定・フォント・キャプチャは端末に保存します。広告やアクセス解析はありません。

作品が外部の素材を読み込む場合、その接続先に通信します。起動時と「アップデートを確認」を押したときに、更新確認のためGitHubに接続します。p5.js連携を使用すると、入力したユーザー名を含むリクエストをp5.js Web Editorへ送り、公開作品とその素材を取得します。パスワードは入力・保存・送信しません。作品や設定は送信しませんが、接続先にはIPアドレスなど通常の通信情報が伝わります。

作品がカメラ・マイクを要求したときだけ、Androidの権限確認を表示します。許可した場合、実行中の作品が映像・音声を利用できます。作品のファイル選択はAndroidの選択画面を使い、選んだファイルを作品に渡します。保存はDownload/Edit-RiN、または選択した保存先へ行います。Android 6〜9ではDownloadへの保存にストレージ権限を要求し、許可されない場合は保存先選択を使います。外部ライブラリのコードも作品として実行されるため、読み込むURLは利用者が選びます。

端末の設定によっては、Androidのシステムバックアップが行われます。お問い合わせは[Issues](https://github.com/rin-code-dev/Edit-RiN/issues)へお願いします。

## English

Works, settings, fonts and captures are stored on your device. There are no ads or analytics.

Sketches may contact external services. At startup and when you select “Check for updates”, the app connects to GitHub without sending your works or settings. When you use p5.js integration, the app sends a request containing the username you entered to the p5.js Web Editor and downloads public works and their assets. It never asks for, stores, or sends your password. Destinations receive normal connection information, such as your IP address.

Camera and microphone permission is requested only when a sketch asks for those devices. Once allowed, the running sketch can use their input. Sketch file selection uses the Android picker and passes the selected files to the sketch. Downloads go to Download/Edit-RiN or a selected destination. Android 6–9 requests storage permission for Downloads and falls back to the destination picker if permission is unavailable. External library code runs as part of the sketch; users choose those URLs.

Android may back up app data according to your device settings. For questions, use [Issues](https://github.com/rin-code-dev/Edit-RiN/issues).
