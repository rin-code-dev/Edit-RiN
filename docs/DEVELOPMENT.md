# 保守ガイド

この文書は、変更する場所と確認方法を探すための入口です。作品の形式やユーザー向けの実行設定は [WORK_STORAGE.md](WORK_STORAGE.md) と [p5.js互換性](../P5_COMPATIBILITY.md) を参照してください。

## ソースと生成物

| 場所 | 役割・編集方針 |
| --- | --- |
| `android/app/src/main/java/com/hikariatelier/app/` | アプリが管理するKotlinソース。下表から変更対象を探す。 |
| `android/app/src/main/res/` | Androidリソース。Composeの色・文字スタイルは `ui/theme/` も確認する。 |
| `www/p5_runner.html`、`p5_host.js`、`p5_bootstrap.js`、`p5_sketch.js` | アプリが管理するWebView実行環境。 |
| `www/p5-v*.min.js`、`p5.sound*.min.js`、`p5.webgpu.js`、`p5.brush-*.js`、`matter-*.min.js` | 同梱する第三者ライブラリの配布物。直接修正せず、更新時に出典・ライセンス・対応ソースも確認する。 |
| `third_party/licenses/`、`third_party/sources/` | 第三者ライブラリのライセンスと対応ソース。ビルドキャッシュではない。 |
| `third_party/resolved/` | リリース時の依存関係・通知の記録。日常のコード修正では手編集しない。 |
| `android/**/build/`、`.gradle/`、`.kotlin/` | 再生成されるビルド成果物・キャッシュ。ここを編集しても次のビルドで失われる。 |
| `outputs/` | ローカルで保管した成果物。ソース整理で一括削除しない。 |
| `docs/share/` | 独立した共有Webプレイヤー。Androidの実行環境とは入口が異なる。 |

`www/` がWeb資産の編集元です。`android/app/build.gradle` の `syncWebAssets` が採用ファイルを `android/app/build/generated/web-assets/public/` へコピーします。新しいWeb資産はコピー対象と `AssetWebClient` の配信規則も確認してください。`p5.min.js` という旧プロジェクトURLは `p5-v1.min.js` を返す互換エイリアスであり、同じ配布物を別ファイルとして保持する必要はありません。

アプリのバージョンの編集元は `android/app/build.gradle` の `versionCode` / `versionName` です。`package.json` はローカル作業用コマンドだけを定義し、アプリのバージョンを複製しません。

## Kotlinの役割

以下のファイル名は上記Kotlinソースディレクトリからの相対名です。

| 変更内容 | 主な入口 |
| --- | --- |
| 起動・画面の組み立て | `MainActivity.kt`、`EditorScreen.kt`、`EditorWorkspaceLayout.kt` |
| エディタ本文・Undo・選択範囲 | `EditorSessionViewModel.kt`、`EditorArea.kt`、`EditorTextActions.kt` |
| 補完・強調表示・検索 | `EditorCompletion.kt`、`ProjectTextHighlighter.kt`、`JavaScriptLexing.kt`、`ProjectSearch.kt` |
| 設定画面・永続化 | `SettingsScreen.kt`、`SettingsUiState.kt`、`SettingsViewModel.kt`、`SettingsRepository.kt` |
| 作品選択・保存・復旧の操作 | `WorkManagementViewModel.kt`、`WorkManagementDialogs.kt`、`WorkManagementSheets.kt` |
| 保存形式・ファイル入出力 | `WorkStoreRepository.kt`、`SplitWorkStore.kt`、`WorkStoreCodec.kt`、`WorkDocuments.kt` |
| 下書き・スナップショット | `DraftSnapshotRepository.kt`、`WorkSnapshotPersistence.kt`、`WorkSnapshotViewModel.kt` |
| 補助ファイル・素材・Web Editor取り込み | `ProjectPaths.kt`、`ProjectAssets.kt`、`P5WebEditor.kt` |
| 作品のHTML / module / classic実行設定 | `ProjectDocument.kt`、`WorkRuntimeDialog.kt`、`WorkLibraries.kt` |
| WebView実行・JS bridge・配信 | `PreviewController.kt`、`PreviewSession.kt`、`AssetWebClient.kt`、`PreviewWebViewHost.kt` |
| カメラ・マイク・ファイル選択・全画面 | `PreviewBrowserFeatures.kt` |
| ダウンロード・録画・画像保存 | `SketchDownloads.kt`、`SketchDownloadAdmission.kt`、`RecordingTransfer.kt`、`PreviewMediaActions.kt` |

画面は状態の表示と操作の接続を担当し、作品操作は `WorkManagementViewModel`、保存先との通信は `WorkStoreRepository` が担当します。Composeの `remember` の位置を移すと、ダイアログ再表示や画面切り替え時に状態の寿命が変わるため、重複整理だけの変更では保持します。

## プレビューの流れ

1. `PreviewSession` がコード・ファイル・設定をスナップショットとして取得する。
2. `ProjectDocument` が実行するHTML、module、classicスクリプトを解決する。
3. `PreviewController` が実行トークン付きURLをWebViewに開き、`AssetWebClient` がそのスナップショットを配信する。
4. `p5_host.js` が共通のbridge、エラー報告、停止・再開、画像・録画・ダウンロードを管理する。
5. 通常のJS作品は `p5_bootstrap.js` がcore → addon → 作品コードの順に読み込む。`p5_sketch.js` はclassic作品コードをこの順番で実行するための入口。HTML作品は自身のscriptタグを使う。

HTML/module/classicの判定は `resolveProjectRun` に集約します。画面と実行側に独立した判定を追加しないでください。Android bridgeへ渡すJSONは解決済みの結果から生成します。

`__editKiro*` / `__editRin*` はKotlin、JavaScript、テストをまたぐbridgeの契約です。名称だけを一括置換せず、呼び出し側と受け側を確認してください。JSファイルの分割・読み込み順の変更はp5.jsのglobal / instance / moduleそれぞれで確認します。

## 整理時に保持する条件

- 保存成功が確定する前に編集中の値を捨てない。失敗・競合時は本文、補助ファイル、選択作品、Undoを保持する。
- 旧一括保存の読み込みは移行専用。新しい保存は `SplitWorkStore` を使い、旧ファイルを書き換えない。
- 古いプレビューのbridge応答は実行トークンで拒否する。コード・素材・権限要求を次の作品へ混ぜない。
- ダウンロードはnative側の保存結果ACKまで所有権を保つ。転送サイズ制限や同時保存数の制限を重複コードとして削除しない。
- 新規編集・取り込みの制限と、旧保存データを読む互換性を区別する。保存JSONの書き込み・読み込み上限を変更するときは両側を確認する。

## 確認コマンド

プロジェクトのルートで実行します。

```sh
# 軽量なJS確認
npm test

# AndroidのテストとデバッグAPK。ログは /tmp/edit-rin-build.log
./scripts/build-apk.sh debug --test
# 同じ処理を npm run build:check でも実行可能

# 差分中の空白・マージマーカー確認
git diff --check
```

実ブラウザの確認にはNode.js 22以降とChromium系ブラウザを使います。後ろの2本は `playwright-core` も必要です。以下のパスをローカル環境に合わせて指定してください。

```sh
BROWSER_BIN=/path/to/chromium node tests/authoring.browser.cjs
PLAYWRIGHT_MODULE=/path/to/playwright-core BROWSER_PATH=/path/to/chromium node tests/runtime-browser.cjs
PLAYWRIGHT_MODULE=/path/to/playwright-core BROWSER_PATH=/path/to/chromium node tests/p5-host.browser.cjs
```

ブラウザ試験は実行環境や書き出しの回帰を確認します。Android実機のタッチ、カメラ・マイク、権限画面、回転、SAFプロバイダの挙動には別途実機確認が必要です。

## 次の整理候補

`EditorScreen.kt` と `SettingsScreen.kt` は依然として大きく、画面の組み立てを追う負担が残っています。次の機能修正に合わせ、状態の所有者が明確なダイアログ・カテゴリ単位で分離する余地があります。行数だけを減らすための細分化や、設定項目を汎用DSLへ置き換える整理は避けます。

`p5_host.js` のGIFエンコード部分は独立性がありますが、分割には読込順とブラウザ試験の調整が必要です。現在の録画機能に必要な実装として扱います。

文書上の要確認事項として、`CONTRIBUTING.md` の個別許可を要求する記述と、`LICENSE` / `README.md` のGPL記述が一致していません。今回のコード整理ではライセンス文書を変更していません。
