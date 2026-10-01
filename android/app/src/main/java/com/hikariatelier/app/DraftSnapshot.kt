package com.hikariatelier.app

internal data class DraftSnapshot(
    val workId: String,
    val code: String,
    val updatedAt: Long,
    val storeKey: String? = null,
    val baseHash: String? = null,
    val bases: Map<String, String> = emptyMap()
)
