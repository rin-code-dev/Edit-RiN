package com.hikariatelier.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

internal enum class PreviewStage {
    ACCEPTED,
    PREPARING,
    RUNTIME_READY,
    VISUAL_READY
}

internal sealed interface PreviewExecutionResult {
    val token: String?
    val workId: String?

    data class Accepted(override val token: String, override val workId: String?) : PreviewExecutionResult
    data class Preparing(override val token: String, override val workId: String?) : PreviewExecutionResult
    data class RuntimeReady(override val token: String, override val workId: String?) : PreviewExecutionResult
    data class VisualReady(override val token: String, override val workId: String?) : PreviewExecutionResult
    data class Rejected(val reason: String) : PreviewExecutionResult {
        override val token: String? get() = null
        override val workId: String? get() = null
    }
    data class Failed(
        override val token: String?,
        override val workId: String?,
        val message: String,
        val cause: Throwable? = null
    ) : PreviewExecutionResult
    data class TimedOut(
        override val token: String,
        override val workId: String?,
        val stage: PreviewStage
    ) : PreviewExecutionResult
    data class Cancelled(override val token: String?, override val workId: String?) : PreviewExecutionResult
    data class Superseded(override val token: String?, override val workId: String?) : PreviewExecutionResult

    val isVisualReady: Boolean get() = this is VisualReady
    val isRuntimeReady: Boolean get() = this is RuntimeReady || this is VisualReady
}

/**
 * Coordinates preview execution requests, lifecycle progression, and bounded stage completion.
 */
internal class PreviewExecutionCoordinator {
    private val pendingCompletions = ConcurrentHashMap<String, ExecutionWaiters>()
    private val completedResults = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, PreviewExecutionResult>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PreviewExecutionResult>?): Boolean {
                return size > 64
            }
        }
    )
    @Volatile private var activeToken: String? = null

    private class ExecutionWaiters(
        val workId: String?,
        val runtimeDeferred: CompletableDeferred<PreviewExecutionResult> = CompletableDeferred(),
        val visualDeferred: CompletableDeferred<PreviewExecutionResult> = CompletableDeferred()
    )

    fun registerRequest(token: String, workId: String?) {
        // Complete older tokens as Superseded
        val oldToken = activeToken
        if (oldToken != null && oldToken != token) {
            notifyTokenFinished(oldToken, PreviewExecutionResult.Superseded(oldToken, null))
        }
        activeToken = token
        pendingCompletions[token] = ExecutionWaiters(workId)
    }

    fun notifyPreparing(token: String) {
        // State update hook
    }

    fun notifyRuntimeReady(token: String) {
        val waiters = pendingCompletions[token] ?: return
        if (activeToken == token) {
            waiters.runtimeDeferred.complete(PreviewExecutionResult.RuntimeReady(token, waiters.workId))
        }
    }

    fun notifyVisualReady(token: String) {
        val waiters = pendingCompletions[token] ?: return
        if (activeToken == token) {
            val visualResult = PreviewExecutionResult.VisualReady(token, waiters.workId)
            waiters.runtimeDeferred.complete(visualResult)
            waiters.visualDeferred.complete(visualResult)
        }
    }

    fun notifyError(token: String, message: String, cause: Throwable? = null) {
        val waiters = pendingCompletions[token] ?: return
        if (activeToken == token) {
            val failure = PreviewExecutionResult.Failed(token, waiters.workId, message, cause)
            waiters.runtimeDeferred.complete(failure)
            waiters.visualDeferred.complete(failure)
        }
    }

    fun notifyCancelled(token: String) {
        notifyTokenFinished(token, PreviewExecutionResult.Cancelled(token, null))
    }

    fun notifyDisposed() {
        val all = pendingCompletions.keys.toList()
        all.forEach { token ->
            notifyTokenFinished(token, PreviewExecutionResult.Cancelled(token, null))
        }
        activeToken = null
    }

    private fun notifyTokenFinished(token: String, result: PreviewExecutionResult) {
        completedResults[token] = result
        val waiters = pendingCompletions.remove(token) ?: return
        waiters.runtimeDeferred.complete(result)
        waiters.visualDeferred.complete(result)
    }

    suspend fun awaitStage(
        token: String,
        targetStage: PreviewStage = PreviewStage.RUNTIME_READY,
        timeoutMillis: Long = 5000L
    ): PreviewExecutionResult {
        val waiters = pendingCompletions[token] ?: run {
            return completedResults[token] ?: PreviewExecutionResult.Failed(token, null, "Request token not registered")
        }

        val deferred = when (targetStage) {
            PreviewStage.VISUAL_READY -> waiters.visualDeferred
            else -> waiters.runtimeDeferred
        }

        val result = withTimeoutOrNull(timeoutMillis) {
            deferred.await()
        } ?: PreviewExecutionResult.TimedOut(token, waiters.workId, targetStage)

        if (targetStage == PreviewStage.VISUAL_READY || result !is PreviewExecutionResult.RuntimeReady) {
            completedResults[token] = result
            pendingCompletions.remove(token)
        }
        return result
    }
}
