package com.hikariatelier.app

import org.json.JSONObject
import java.util.UUID

/** Capture Compose-owned values before background parsing; never retain a mutable Work. */
internal data class PreviewRunInput(
    val token: String, val workId: String?, val source: String,
    val files: Map<String, String>, val assets: Map<String, ProjectAsset>,
    val p5Version: String, val soundEnabled: Boolean,
    val libraries: Map<String, String>, val parameterValues: Map<String, String>
)

internal data class PreparedPreviewRun(
    val assets: PreviewAssets, val workId: String?, val sketchCode: String,
    val p5Version: String, val soundEnabled: Boolean, val libraries: String,
    val parameters: String, val shaders: String, val sourceFiles: List<PreviewSourceFile>
)

internal fun capturePreviewRun(
    work: Work?, source: String, files: Map<String, String>, assets: Map<String, ProjectAsset>,
    drafts: Map<String, String> = emptyMap(), parameterValues: Map<String, String> = work?.parameterValues.orEmpty(),
    token: String = UUID.randomUUID().toString()
): PreviewRunInput = PreviewRunInput(
    token, work?.id, source,
    files.mapValues { (name, saved) -> drafts["${work?.id}/$name"] ?: saved }, assets.toMap(),
    normalizedP5Version(work?.p5Version), work?.p5SoundEnabled == true,
    work?.libraries.orEmpty().toMap(), parameterValues.toMap()
)

/** CPU-only work. Publication is separate so an obsolete background result cannot win. */
internal fun preparePreviewRun(input: PreviewRunInput): PreparedPreviewRun {
    val declarations = workParameters(input.files.toSortedMap() + ("sketch.js" to input.source))
    return PreparedPreviewRun(
        PreviewAssets(input.token, input.assets, input.files), input.workId,
        composeProjectSource(input.source, input.files), input.p5Version, input.soundEnabled,
        JSONObject(input.libraries).toString(), parameterValuesJson(declarations, input.parameterValues),
        JSONObject(input.files.filter { !it.key.endsWith(".js", ignoreCase = true) } as Map<*, *>).toString(),
        previewSourceFiles(input.source, input.files)
    )
}

/** Atomically exposes a complete run to the WebView bridge and asset-request thread. */
internal class PreviewSession {
    @Volatile private var requestedToken = "initial"
    @Volatile private var current = PreparedPreviewRun(PreviewAssets("initial", emptyMap()), null, "",
        P5_VERSION_CURRENT, false, "{}", "{}", "{}", emptyList())

    val assets get() = current.assets
    val workId get() = current.workId
    val sketchCode get() = current.sketchCode
    val p5Version get() = current.p5Version
    val soundEnabled get() = current.soundEnabled
    val libraries get() = current.libraries
    val parameters get() = current.parameters
    val shaders get() = current.shaders
    val sourceFiles get() = current.sourceFiles

    fun request(token: String) { requestedToken = token }
    fun isRequested(token: String) = requestedToken == token
    fun publish(run: PreparedPreviewRun): Boolean {
        if (run.assets.token != requestedToken) return false
        current = run
        return true
    }
    fun snapshotFor(token: String): PreparedPreviewRun? = current.takeIf {
        token != "initial" && token == requestedToken && token == it.assets.token
    }
    fun isCurrent(token: String) = snapshotFor(token) != null
    fun invalidate() { requestedToken = "" }

    /** Synchronous compatibility for small pure callers and unit tests. */
    fun prepare(work: Work?, source: String, supportingFiles: Map<String, String>,
                sourceAssets: Map<String, ProjectAsset>, drafts: Map<String, String> = emptyMap()): String {
        val input = capturePreviewRun(work, source, supportingFiles, sourceAssets, drafts)
        request(input.token)
        publish(preparePreviewRun(input))
        return input.token
    }
}

// A newline ends line comments; a semicolon prevents ASI from joining independent files.
internal const val PREVIEW_FILE_SEPARATOR = "\n;\n"
internal fun composeProjectSource(mainCode: String, files: Map<String, String>): String = buildString {
    files.filter { it.key.endsWith(".js", ignoreCase = true) }.toSortedMap().forEach { (_, code) ->
        append(code)
        append(PREVIEW_FILE_SEPARATOR)
    }
    append(mainCode)
    append("\n//# sourceURL=sketch.js")
}
