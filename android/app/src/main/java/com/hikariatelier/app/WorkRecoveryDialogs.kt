package com.hikariatelier.app

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@Composable
internal fun WorkRecoveryDialogs(vm: WorkManagementViewModel, text: (String) -> String, onChooseFolder: () -> Unit) {
    if (vm.showRestoreConfirmation) AlertDialog(
        onDismissRequest = { if (!vm.workSaving) vm.showRestoreConfirmation = false },
        title = { KeepLandscapeDialogImmersive(); Text(text("保存済みの内容に戻す")) },
        text = { Text(text("この作品のコード・補助ファイル・設定の未保存変更とUndo履歴を破棄します。復元しますか？")) },
        confirmButton = { TextButton(enabled = !vm.workSaving, onClick = { vm.restoreCurrentWork() }) { Text(text("復元")) } },
        dismissButton = { TextButton(enabled = !vm.workSaving, onClick = { vm.showRestoreConfirmation = false }) { Text(text("キャンセル")) } }
    )
    if (vm.showConflictDialog) AlertDialog(
        onDismissRequest = { if (!vm.workSaving) vm.showConflictDialog = false },
        title = { KeepLandscapeDialogImmersive(); Text(text("外部変更と統合")) },
        text = { Text(text("未編集の作品には外部変更を反映します。同じ作品に両方の変更がある場合は、現在の編集を別作品として保存して両方を保持します。")) },
        confirmButton = { TextButton(enabled = !vm.workSaving, onClick = { vm.mergeExternalChanges() }) { Text(text("統合")) } },
        dismissButton = { TextButton(enabled = !vm.workSaving, onClick = { vm.showConflictDialog = false }) { Text(text("キャンセル")) } }
    )
    if (vm.loadFailure != null) AlertDialog(
        onDismissRequest = {},
        title = { KeepLandscapeDialogImmersive(); Text(text("作品を読み込めませんでした")) },
        text = { Text(text("保存先にアクセスできないか、保存データを読み込めません。元のデータは保持しています。保存先を再接続して再試行してください。")) },
        confirmButton = { TextButton(enabled = !vm.workSaving, onClick = { vm.retryLoad() }) { Text(text("再試行")) } },
        dismissButton = { TextButton(enabled = !vm.workSaving, onClick = onChooseFolder) { Text(text("保存先を再接続")) } }
    )
    if (vm.pendingDraft != null && vm.loadFailure == null) AlertDialog(
        onDismissRequest = {},
        title = { KeepLandscapeDialogImmersive(); Text(text("復元先を確認できない下書き")) },
        text = { Text(text("保存先または保存済み内容が異なるため、自動復元を止めました。下書きを別作品として復元できます。")) },
        confirmButton = { TextButton(enabled = !vm.workSaving, onClick = { vm.recoverPendingDraftAsWork() }) { Text(text("別作品として復元")) } },
        dismissButton = { TextButton(enabled = !vm.workSaving, onClick = { vm.dismissPendingDraft() }) { Text(text("下書きを破棄")) } }
    )
}
