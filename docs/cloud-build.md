# クラウドでデバッグ・リリースAPKを作る

GitHub管理のランナーでビルドし、完成したAPKをこのPCにダウンロードします。ローカルではGradleを起動しません。Git、Python 3.12以上、GitHub CLIのインストールと `gh auth login` が必要です。

```sh
./scripts/cloud-build.sh debug
./scripts/cloud-build.sh debug --test
./scripts/cloud-build.sh release
./scripts/cloud-build.sh release --test
```

APKは `~/Documents/EditRiNデータ/デバッグ/` に実行番号付きで保存します。`--output DIR` で変更できます。実行番号、ソースコミット、APKと署名証明書のSHA-256を記録したJSONも保存します。端末へのインストールやGoogle Drive同期は行いません。

リリースAPKは `~/Documents/EditRiNデータ/リリース/vVERSION/Edit-RiN-vVERSION-release-RUN_ID.apk` に保存します。JSONと `.sha256` も保存し、既存ファイルを上書きしません。バージョンは `android/app/build.gradle` の値を使用し、自動では増やしません。GitHub Releasesへの投稿、タグ作成、ストア公開は行いません。

## 送信するソース

現在のHEADを基に、`android/`、`www/`、`third_party/`、ライセンスとクラウドビルド用ファイルの現在の内容を反映します。その範囲では、ディスクに保存済みの未追跡ファイルと削除も反映します。エディター上で保存していない変更は含みません。範囲外の未コミット変更は送信しません。

```sh
./scripts/cloud-build.sh --dry-run
```

送信前に、除外対象のパス、シンボリックリンク、一般的な秘密情報とローカルユーザー名・ホームパスを検査します。この検査はすべての秘密情報を検出する保証ではありません。公開リポジトリではビルド用ブランチのソースも公開されます。

作業フォルダーのブランチとステージ状態は変更しません。一時リポジトリから `build/cloud` だけに通常のpushを行います。main、タグ、Releasesは更新しません。競合するpushは失敗させ、force-pushしません。

## 初回設定

`build-debug.yml` をデフォルトブランチに登録します。既存Dev版と同じデバッグ署名鍵をBase64化し、リポジトリのActions Secret `CLOUD_DEBUG_KEYSTORE_BASE64` に登録します。鍵の内容をログやソースへ載せないでください。通常のAndroidデバッグ鍵（alias/passwordがAndroid標準）を使用します。

実行時にJDK 25、Android SDK 37、Build Tools 37.0.0を用意します。デバッグ鍵はSecretsから復元し、Gradleへ署名設定を明示します。完成APKの署名がその鍵と一致しなければ成果物を保存しません。通常はAPK作成のみ、`--test` を付けた場合は単体テストも実行します。テスト失敗時はAPKを配布せず、Actionsにテストレポートを保存します。APKとレポートの保存期間は7日です。

## リリース署名の初回設定

`build-release.yml` と関連スクリプトをデフォルトブランチに登録します。既存の正式版と同じ署名を使用します。ローカルの `android/release-signing.properties` が設定済みで、信頼できる過去の正式版APKがある場合は次で照合・登録できます。

```sh
python3 scripts/setup-cloud-release-signing.py --reference-apk /path/to/trusted-release.apk --dry-run
python3 scripts/setup-cloud-release-signing.py --reference-apk /path/to/trusted-release.apk
```

2行目はGitHub Actions Secretsを変更します。鍵は再生成しません。登録対象は `CLOUD_RELEASE_KEYSTORE_BASE64`、`CLOUD_RELEASE_STORE_PASSWORD`、`CLOUD_RELEASE_KEY_ALIAS`、`CLOUD_RELEASE_KEY_PASSWORD`、`CLOUD_RELEASE_CERT_SHA256` の5つです。秘密値は標準入力で登録し、ソースやログには出しません。設定済みなら通常のビルド時に再登録は不要です。

クラウド側で署名証明書の一致を確認し、一時的な署名設定で `assembleRelease` を実行します。R8・リソース縮小は既存のrelease設定に従います。`--test` は `testReleaseUnitTest` を追加します。完成APKをapksignerで検証し、正式版の署名、`com.hikariatelier.app`、非デバッグ設定、ソースと同じversionCode/versionNameを確認します。照合に失敗した場合はAPKを成果物として保存しません。秘密ファイルは終了時に削除します。Actions Artifactsの保持期間は7日です。

## 中断・失敗した場合

ローカルの待機を中断してもクラウド側の実行は続きます。表示されたActionsのURLで確認できます。成功済みの実行は次で受け取れます。

```sh
./scripts/cloud-build.sh debug --download-run RUN_ID
./scripts/cloud-build.sh release --download-run RUN_ID
```

実行IDと依頼IDを照合して待機し、成果物のソースコミット・実行ID・SHA-256を検証してから保存します。「最新のAPK」を無条件で取得することはありません。
