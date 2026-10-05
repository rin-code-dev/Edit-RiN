package com.hikariatelier.app

import android.content.res.AssetManager

internal fun defaultWorks(assets: AssetManager): List<Work> {
    val now = System.currentTimeMillis()
    data class DefaultWorkDef(
        val id: String,
        val title: String,
        val ratio: String = "1:1",
        val p5Version: String = P5_VERSION_CURRENT,
        val p5SoundEnabled: Boolean = false
    )
    val defs = listOf(
        DefaultWorkDef("halo", "Halo", "1:1"),
        DefaultWorkDef("gravity", "Gravity", "1:1", p5Version = P5_VERSION_LEGACY),
        DefaultWorkDef("wave-parameter", "wave Parameter", "1:1", p5Version = P5_VERSION_CURRENT),
        DefaultWorkDef("webgpu", "WebGPU", "1:1", p5Version = P5_VERSION_CURRENT),
        DefaultWorkDef("sound", "Sound", "1:1", p5Version = P5_VERSION_CURRENT, p5SoundEnabled = true),
        DefaultWorkDef("camera", "Camera", "1:1", p5Version = P5_VERSION_CURRENT),
        DefaultWorkDef("microphone", "Microphone", "1:1", p5Version = P5_VERSION_LEGACY, p5SoundEnabled = true)
    )
    return defs.map { def ->
        Work(
            id = def.id,
            title = def.title,
            code = assets.open("public/samples/${def.title}.js").bufferedReader().use { it.readText() },
            createdAt = now,
            updatedAt = now,
            previewAspectRatio = def.ratio,
            p5Version = def.p5Version,
            p5SoundEnabled = def.p5SoundEnabled
        )
    }
}
