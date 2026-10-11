# クラウドでデバッグAPKを作る

GitHub管理のランナーでビルドし、完成したAPKをこのPCにダウンロードします。ローカルではGradleを起動しません。Git、Python 3.12以上、GitHub CLIのインストールと `gh auth login` が必要です。

```sh
./scripts/cloud-build.sh debug
./scripts/cloud-build.sh debug --test
```

APKは `~/Documents/EditRiNデータ/デバッグ/` に実行番号付きで保存します。`--output DIR` で変更できます。実行番号、ソースコミット、SHA-256を記録したJSONも保存します。端末へのインストールやGoogle Drive同期は行いません。

## 送信するソース

現在のHEADを基に、`android/`、`www/`、`third_party/`、ライセンスとクラウドビルド用ファイルの現在の内容を反映します。その範囲では、ディスクに保存済みの未追跡ファイルと削除も反映します。エディター上で保存していない変更は含みません。範囲外の未コミット変更は送信しません。

```sh
./scripts/cloud-build.sh --dry-run
```

送信前に、除外対象のパス、シンボリックリンク、一般的な秘密情報とローカルユーザー名・ホームパスを検査します。この検査はすべての秘密情報を検出する保証ではありません。公開リポジトリではビルド用ブランチのソースも公開されます。

作業フォルダーのブランチとステージ状態は変更しません。一時リポジトリから `codex/cloud-build` だけに通常のpushを行います。main、タグ、Releasesは更新しません。競合するpushは失敗させ、force-pushしません。

## 初回設定

`build-debug.yml` をデフォルトブランチに登録します。既存Dev版と同じデバッグ署名鍵をBase64化し、リポジトリのActions Secret `CLOUD_DEBUG_KEYSTORE_BASE64` に登録します。鍵の内容をログやソースへ載せないでください。通常のAndroidデバッグ鍵（alias/passwordがAndroid標準）を使用します。

実行時にJDK 25、Android SDK 37、Build Tools 37.0.0を用意します。デバッグ鍵はSecretsから復元します。通常はAPK作成のみ、`--test` を付けた場合は単体テストも実行します。テスト失敗時はAPKを配布せず、Actionsにテストレポートを保存します。APKとレポートの保存期間は7日です。

## 中断・失敗した場合

ローカルの待機を中断してもクラウド側の実行は続きます。表示されたActionsのURLで確認できます。成功済みの実行は次で受け取れます。

```sh
./scripts/cloud-build.sh --download-run RUN_ID
```

実行IDと依頼IDを照合して待機し、成果物のソースコミット・実行ID・SHA-256を検証してから保存します。「最新のAPK」を無条件で取得することはありません。
