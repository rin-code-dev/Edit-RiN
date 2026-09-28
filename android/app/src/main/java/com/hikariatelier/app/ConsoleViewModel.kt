package com.hikariatelier.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

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

internal class ConsoleViewModel : ViewModel() {
    val entries = mutableStateListOf<ConsoleEntry>()
    private var sequence by mutableLongStateOf(0L)

    var showConsole by mutableStateOf(false)
    var consoleHeight by mutableFloatStateOf(170f)
    var consoleExpanded by mutableStateOf(false)

    val errorCount: Int
        get() = entries.count { it.level == ConsoleLevel.ERROR }

    fun clear() {
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
        val previous = entries.lastOrNull()

        if (
            previous?.level == level &&
            previous.message == normalized &&
            previous.line == normalizedLine &&
            previous.file == file &&
            previous.workId == workId
        ) {
            val lastIndex = entries.lastIndex
            if (lastIndex >= 0) {
                entries[lastIndex] = previous.copy(count = previous.count + 1)
            }
            return
        }

        sequence += 1L
        entries.add(
            ConsoleEntry(
                id = sequence,
                level = level,
                message = normalized,
                line = normalizedLine,
                file = file,
                workId = workId
            )
        )

        while (entries.size > 200) {
            entries.removeAt(0)
        }
    }
}
