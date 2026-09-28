package com.hikariatelier.app

/** ViewModels supplied by the Activity after it has been attached to its Application. */
internal data class EditorModels(
    val session: EditorSessionViewModel,
    val update: UpdateViewModel,
    val search: SearchReplaceViewModel,
    val console: ConsoleViewModel,
    val settings: SettingsViewModel,
    val recording: RecordingViewModel,
    val works: WorkManagementViewModel,
    val snapshots: WorkSnapshotViewModel
)
