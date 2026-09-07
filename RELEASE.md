# Edit:KIRO 1.0.0-beta.4

## 日本語

このベータ版はapplicationId `com.hikariatelier.app`、versionCode `5`です。
ReleaseビルドではR8による縮小・難読化と不要リソースの除去を有効にしています。
WebViewから呼ぶJavaScriptブリッジはProGuardルールで保持します。

JDK 17とAndroid SDK Platform 35を使用してください。SDKパスはローカルの
`android/local.properties`または`ANDROID_HOME`で指定します。

1. `android/release-signing.properties.example`を`android/release-signing.properties`へコピーします。
2. 自分の署名鍵の場所、エイリアス、パスワードを設定します。
3. `android`で次を実行します。

```sh
./gradlew testReleaseUnitTest assembleRelease lintRelease
```

出力は`android/app/build/outputs/apk/release/app-release.apk`です。
署名設定がない場合は`app-release-unsigned.apk`となり、そのままインストールできません。
依存関係を取得済みの場合は`--offline`を追加できます。
`node tests/runner.test.cjs`で描画ランナーのテストを実行できます。

GitHub Releasesには署名済みAPKと対応する公開用プロジェクトZIPを添付してください。
ZIP内のp5.jsソース・ライセンス資料も一緒に配布します。署名鍵・パスワード・
`release-signing.properties`・`local.properties`は公開しないでください。
同じアプリを更新するときは同じ署名鍵を使い、versionCodeを増やします。
難読化されたクラッシュ解析用の`mapping.txt`もバージョンごとに保管してください。

以前のデバッグAPKと署名鍵が異なる場合は上書きインストールできません。
先にアプリから作品バックアップを保存し、デバッグ版をアンインストールした後で
Release版をインストールし、バックアップを復元してください。
インポート済みフォントは再インポートが必要です。

## English

This beta uses application ID `com.hikariatelier.app`, version code `5`, and
version name `1.0.0-beta.4`. Release enables R8 optimization/obfuscation and resource
shrinking, with explicit rules preserving the JavaScript bridge.

Use JDK 17 and Android SDK Platform 35. Copy the signing properties example to
`android/release-signing.properties`, enter your private signing configuration,
and run the command above from `android`. Without signing properties the output
is unsigned and cannot be installed directly.

Publish the signed APK and matching source ZIP together on GitHub Releases,
including the bundled p5.js source and notices. Keep keys, passwords, local SDK
configuration and real signing properties private. Retain the R8 mapping file
for each release. Future updates must use the same key and a higher version code.

A release signed with a different key cannot update an existing debug install.
Export a work backup before uninstalling the debug version, install the release,
then restore the backup. Imported fonts must be imported again.
