# Release & Build Guide / リリース・ビルド手順

- **Application ID**: `com.hikariatelier.app`
- **Current Version**: `2.0.1` (Version Code: `18`)
- **Git Tag**: `v2.0.1`

---

## 日本語

### ビルド環境要件
- **JDK**: JDK 25 (`JAVA_HOME` に設定)
  - Gradle デーモンおよび Java コンパイルに JDK 25 を使用し、Android バイトコード互換性は Java 17 を維持します。
- **Android SDK**: Platform 35, Build Tools 36.0.0
- **ビルドツール**: Gradle Wrapper 9.5.0, Android Gradle Plugin (AGP) 9.3.2, Kotlin / Compose Compiler 2.2.10

### リリースビルド手順
1. **署名設定の作成**:
   `android/release-signing.properties.example` を `android/release-signing.properties` にコピーし、リリース用キーストアの情報を設定します。
2. **テストとビルド実行**:
   ```sh
   node tests/runner.test.cjs
   cd android
   ./gradlew testReleaseUnitTest assembleRelease lintRelease
   ```
3. **成果物の確認**:
   - 署名済みAPK: `android/app/build/outputs/apk/release/app-release.apk`
   - ※ 署名設定がない場合は `app-release-unsigned.apk` が生成されます（端末への直接インストール不可）。

### 注意点・公開時のチェック
- リリースビルドでは R8（最適化・難読化）とリソース縮小が有効です。WebView との JavaScript ブリッジは自動で保持されます。
- 署名鍵、パスワード、`release-signing.properties`、`local.properties`、難読化マップ（`mapping.txt`）は絶対に公開・コミットしないでください。
- 署名が異なるビルド（デバッグ版など）には上書きインストールできません。必要に応じてアプリ内で作品の ZIP バックアップを取得してからインストールしてください。

---

## English

### Build Requirements
- **JDK**: JDK 25 (set as `JAVA_HOME`). Gradle daemon and compilation target Java 25, while maintaining Android bytecode compatibility at Java 17.
- **Android SDK**: Platform 35, Build Tools 36.0.0.
- **Build Tools**: Gradle 9.5.0 wrapper, AGP 9.3.2, built-in Kotlin / Compose compiler 2.2.10.

### Release Steps
1. **Configure Signing**:
   Copy `android/release-signing.properties.example` to `android/release-signing.properties` and fill in your keystore credentials.
2. **Run Verification & Build**:
   ```sh
   node tests/runner.test.cjs
   cd android
   ./gradlew testReleaseUnitTest assembleRelease lintRelease
   ```
3. **Artifacts**:
   - Signed APK: `android/app/build/outputs/apk/release/app-release.apk`
   - Note: If signing is not configured, Gradle will produce `app-release-unsigned.apk` which cannot be installed directly.

### Publication Guidelines
- Keep your signing key, passwords, `release-signing.properties`, local SDK paths, and `mapping.txt` private.
- Attach the signed release APK, matching source ZIP, and SHA-256 checksums to GitHub Releases.
- Do not overwrite installations signed with different keys (e.g. debug builds); back up works to ZIP before upgrading.
