package com.hikariatelier.app

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** Queued notices are delivered to the current screen, including after recreation. */
internal data class UiNotice(val text: String, val arguments: List<Any> = emptyList(), val haptic: Boolean = false)
internal class UiNotices {
    private val channel = Channel<UiNotice>(Channel.UNLIMITED)
    val events = channel.receiveAsFlow()
    fun sendWithHaptic(text: String) { channel.trySend(UiNotice(text, haptic = true)) }
    fun send(text: String, vararg arguments: Any) { channel.trySend(UiNotice(text, arguments.toList())) }
}
