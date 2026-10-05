package com.hikariatelier.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/** Remembers when each work was last opened, so the gallery can sort by "recently opened". */
internal object RecentWorks {
    private const val PREFERENCES = "recent_works"
    private const val KEY = "opened_at"
    private const val LIMIT = 200
    private val openedAt = HashMap<String, Long>()
    private var loaded = false

    /** Changes whenever the history changes; read it inside Compose to refresh sorting. */
    var revision by mutableIntStateOf(0)
        private set

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        val raw = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return
        runCatching {
            val json = JSONObject(raw)
            json.keys().forEach { openedAt[it] = json.optLong(it) }
        }
    }

    @Synchronized
    fun touch(context: Context, workId: String, now: Long = System.currentTimeMillis()) {
        if (workId.isBlank()) return
        load(context)
        openedAt[workId] = now
        if (openedAt.size > LIMIT) {
            openedAt.entries.sortedBy { it.value }.take(openedAt.size - LIMIT).forEach { openedAt.remove(it.key) }
        }
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(KEY, JSONObject(openedAt as Map<*, *>).toString()).apply()
        revision++
    }

    @Synchronized
    fun lastOpened(context: Context, workId: String): Long {
        load(context)
        return openedAt[workId] ?: 0L
    }
}
