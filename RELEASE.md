# Edit:RiN ver1.0.3

- applicationId: `com.hikariatelier.app`
- versionName: `1.0.3`
- versionCode: `9`
- Suggested Git tag: `v1.0.3`

## ビルド / Build

JDK 17、Android SDK Platform 35を使用します。SDKパスを `ANDROID_HOME` または
`android/local.properties` に設定してください。

1. `android/release-signing.properties.example` を `android/release-signing.properties` にコピーします。
2. 既存アプリの更新には同じ署名鍵を設定します。
3. 以下を実行します。依存関係を取得済みの場合は `--offline` を追加できます。

```sh
node tests/runner.test.cjs
cd android
./gradlew testReleaseUnitTest assembleRelease lintRelease
```

署名済み出力：`android/app/build/outputs/apk/release/app-release.apk`。
署名設定がない場合は未署名の `app-release-unsigned.apk` となり、そのままインストールできません。
R8の最適化・難読化とリソース縮小は有効です。WebViewのJavaScriptブリッジは保持します。

GitHub Releasesには署名済みAPK、同じソースのZIP、SHA-256チェックサムを添付します。
p5.jsソース・ライセンス資料も同梱します。署名鍵・パスワード・実際の署名設定・
ローカルSDK設定は公開しません。`mapping.txt` は公開アセットに含めずローカルに保管します。

Use JDK 17 and Android SDK 35. Configure your private signing properties using the example,
then run the commands above. Release builds enable R8 and resource shrinking. Keep your
signing key, passwords, local SDK configuration and mapping file private. Update an existing
installation with the same signing key and a higher version code.

デバッグ版など署名が異なるアプリには上書きできません。その場合は作品バックアップを
保存してから旧版を削除し、正式版で復元してください。フォントは再インポートが必要です。
