package com.hikariatelier.app

import android.os.SystemClock
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive

/** Time-based recording updates have their own composition scope. */
@Composable
internal fun EditorRecordingEffects(model: RecordingViewModel, onStart: (String) -> Unit) {
    val start by rememberUpdatedState(onStart)
    LaunchedEffect(model.pendingRecordingFormat) {
        val format = model.pendingRecordingFormat ?: return@LaunchedEffect
        while (model.recordingCountdownRemaining > 0) {
            delay(1000)
            model.recordingCountdownRemaining--
        }
        if (model.pendingRecordingFormat == format) {
            model.pendingRecordingFormat = null
            start(format)
        }
    }
    LaunchedEffect(model.isPreviewRecording, model.isRecordingSaving, model.recordingStartedAt) {
        while (model.isPreviewRecording && !model.isRecordingSaving) {
            model.recordingElapsedMillis = (SystemClock.elapsedRealtime() - model.recordingStartedAt)
                .coerceIn(0L, model.recordingLimitMillis)
            delay(200)
        }
    }
}

@Composable
internal fun rememberProjectCompletionSymbols(
    workId: String, file: String, text: String, mainText: String,
    files: Map<String, String>, session: EditorSessionViewModel, enabled: Boolean
): List<ProjectSymbol> {
    val cache = remember(workId) { ProjectCompletionCache() }
    val drafts = session.fileDrafts.toMap()
    val sources = remember(workId, file, text, mainText, files.toMap(), drafts, enabled) {
        if (!enabled) emptyMap() else projectSearchSources(workId, mainText, files, drafts)
            .toMutableMap().apply { put(file, text) }
    }
    val result by produceState<Pair<String, List<ProjectSymbol>>>("" to emptyList(), workId, sources) {
        if (sources.values.sumOf { it.length } >= 8_000) delay(120)
        value = workId to withContext(Dispatchers.Default) { cache.symbols(sources) { ensureActive() } }
    }
    return result.second.takeIf { result.first == workId }.orEmpty()
}
