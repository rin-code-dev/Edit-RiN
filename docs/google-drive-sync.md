# Google Driveへの成果物コピー

`./scripts/sync-gdrive.sh` はrcloneを使い、`~/Documents/EditRiNデータ/` の内容を、選択したGoogle Driveリモートの `EditRiNデータ` フォルダへ追加・更新します。Drive側だけにあるファイルは削除しません。同名ファイルは更新されるため、過去版を残したいAPK・ZIPには版や日付を含む別名を使ってください。

## 初回設定

1. [rclone公式のインストール手順](https://rclone.org/install/)でrcloneを導入します。PATH上、または `~/.local/bin/rclone` に配置してください。
2. `rclone config`（PATHにない場合は `~/.local/bin/rclone config`）を実行し、Google Drive用のリモートを作成します。名前の例は `editrin-drive`、ストレージ種別は `drive` です。ブラウザで同期先のGoogleアカウントを確認して認証します。
3. `scripts/sync-gdrive.local.json` の `remote` にリモート名を設定します。既存ファイルがある場合は、ほかの項目を残して追記してください。

```json
{
  "remote": "editrin-drive"
}
```

環境変数 `EDIT_RIN_DRIVE_REMOTE=editrin-drive` でも指定できます。環境変数を優先します。名前には英数字・ハイフン・アンダースコアを使い、コロンやパスは付けません。

従来の `account` 項目と `EDIT_RIN_DRIVE_ACCOUNT` は利用しません。OSのGoogleアカウント連携からトークンを取り出したり、メールアドレスから転送先を推測したりしません。rcloneの認証は別途必要です。

既存の保存先を使う場合は、認証するアカウントと、そのリモートのルート直下にある `EditRiNデータ` が意図したフォルダか確認します。Google Driveに同名フォルダが複数ある場合は、転送前に保存先を確認してください。スクリプトは既存フォルダの整理・削除・重複解消を行いません。

## 実行

```sh
# 転送予定を確認。Driveへの書き込みは行わない。
./scripts/sync-gdrive.sh --dry-run

# ユーザーが同期を依頼した場合に実行。
./scripts/sync-gdrive.sh
```

`--dry-run` もリモートとの比較のため認証と通信が必要です。ローカルには実行ログを作成します。

転送は `rclone copy --checksum`、転送後の確認は `rclone check --one-way` です。通常のAPK・ZIP・ソースファイルをサイズとハッシュで確認し、同じサイズの変更も検出します。Google Docsなどのネイティブ文書の編集・バックアップは対象外です。転送中は対象ファイルの編集やAPKの上書きを避けてください。

- `sync`・`move`・`delete` は使いません。
- 転送または確認に失敗すると非ゼロで終了し、成功と表示しません。途中まで転送されたファイルは残ります。原因を確認したうえで同じコマンドを再実行できます。
- ログは `$XDG_STATE_HOME/edit-rin/drive-sync/`、未設定なら `~/.local/state/edit-rin/drive-sync/` に保存します。実行時にパスを表示します。
- 認証設定はrclone側で管理します。設定ファイルの中身やトークンをログ・会話・Gitへ出さないでください。
- rcloneの環境変数は `RCLONE_CONFIG` と `RCLONE_CONFIG_PASS` のみ引き継ぎます。サイズだけの比較やリンク追跡などを環境変数から有効にできないようにしています。

## 除外対象と制限

`scripts/sync-gdrive.exclude` を転送と確認の両方に適用します。内部指示書、署名鍵、認証・ローカル設定、開発用キャッシュ、引き継ぎ・監査ファイル、ログを除外します。シンボリックリンクも追跡しません。除外されたDrive上の既存ファイルを削除することはありません。

除外はパス名に基づきます。ZIPなどの中身や、別名の文書に含まれる秘密情報までは検査しません。成果物フォルダへ入れる前に公開・共有可能な内容か確認してください。除外されたファイルはバックアップされません。

## 軽量テスト

```sh
PYTHONDONTWRITEBYTECODE=1 python3 tests/sync-gdrive.test.py
```

Driveへ接続せず、一時フォルダで同サイズ変更、削除防止、除外、dry-run、ハッシュ不一致と失敗終了を確認します。rcloneが未導入の場合は実転送のローカルテストがスキップされます。Androidのビルドは行いません。

参考：[rclone copy](https://rclone.org/commands/rclone_copy/)、[rclone check](https://rclone.org/commands/rclone_check/)。
