package com.hikariatelier.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class ConsoleLevel {
    LOG,
    WARNING,
    ERROR
}

internal data class ConsoleEntry(
    val id: Long,
    val level: ConsoleLevel,
    val message: String,
    val line: Int? = null,
    val file: String? = null,
    val workId: String? = null,
    val count: Int = 1
)

internal class ConsoleViewModel(private val operationScope: CoroutineScope? = null) : ViewModel() {
    val entries = mutableStateListOf<ConsoleEntry>()
    private var sequence = 0L
    private val pending = ArrayDeque<ConsoleEntry>()
    private var flushJob: Job? = null

    var showConsole by mutableStateOf(false)
    var consoleHeight by mutableFloatStateOf(170f)
    var consoleExpanded by mutableStateOf(false)

    val errorCount: Int
        get() = entries.count { it.level == ConsoleLevel.ERROR }

    fun clear() {
        flushJob?.cancel()
        flushJob = null
        pending.clear()
        entries.clear()
    }

    fun append(
        level: ConsoleLevel,
        message: String,
        line: Int? = null,
        file: String? = null,
        workId: String? = null
    ) {
        val normalized = message.trim()
        if (normalized.isBlank()) return

        val normalizedLine = line?.takeIf { it > 0 }
        val previous = pending.lastOrNull()

        if (
            previous?.level == level &&
            previous.message == normalized &&
            previous.line == normalizedLine &&
            previous.file == file &&
            previous.workId == workId
        ) {
            pending.removeLast()
            pending.addLast(previous.copy(count = (previous.count.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
        } else {
            sequence += 1L
            pending.addLast(
            ConsoleEntry(
                id = sequence,
                level = level,
                message = normalized,
                line = normalizedLine,
                file = file,
                workId = workId
            )
        )
        }
        while (pending.size > 200) pending.removeFirst()
        if (level == ConsoleLevel.ERROR) {
            flushJob?.cancel()
            flushJob = null
            flush()
        } else if (flushJob == null) {
            flushJob = (operationScope ?: viewModelScope).launch {
                delay(100)
                flush()
                flushJob = null
            }
        }
    }

    private fun flush() {
        Snapshot.withMutableSnapshot {
            while (pending.isNotEmpty()) {
                val next = pending.removeFirst()
                val previous = entries.lastOrNull()
                if (previous != null && previous.level == next.level && previous.message == next.message &&
                    previous.line == next.line && previous.file == next.file && previous.workId == next.workId) {
                    entries[entries.lastIndex] = previous.copy(
                        count = (previous.count.toLong() + next.count).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                } else entries.add(next)
            }
            while (entries.size > 200) entries.removeAt(0)
        }
    }
}
