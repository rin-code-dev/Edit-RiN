package com.hikariatelier.app

import org.json.JSONObject
import java.util.UUID

/** One immutable run snapshot for the WebView asset client and JavaScript bridge. */
internal class PreviewSession {
    @Volatile var assets = PreviewAssets("initial", emptyMap())
        private set
    @Volatile var workId: String? = null
        private set
    @Volatile var sketchCode = ""
        private set
    @Volatile var p5Version = P5_VERSION_CURRENT
        private set
    @Volatile var soundEnabled = false
        private set
    @Volatile var libraries = "{}"
        private set
    @Volatile var parameters = "{}"
        private set
    @Volatile var sourceFiles: List<PreviewSourceFile> = emptyList()
        private set

    fun prepare(
        work: Work?,
        source: String,
        supportingFiles: Map<String, String>,
        sourceAssets: Map<String, ProjectAsset>,
        drafts: Map<String, String> = emptyMap()
    ): String {
        val runningFiles = supportingFiles.mapValues { (name, code) ->
            drafts["${work?.id}/$name"] ?: code
        }
        workId = work?.id
        p5Version = normalizedP5Version(work?.p5Version)
        soundEnabled = work?.p5SoundEnabled == true
        libraries = JSONObject(work?.libraries.orEmpty()).toString()
        val declarations = workParameters(runningFiles.toSortedMap() + ("sketch.js" to source))
        parameters = parameterValuesJson(declarations, work?.parameterValues.orEmpty())
        sketchCode = composeProjectSource(source, runningFiles)
        sourceFiles = previewSourceFiles(source, runningFiles)
        assets = PreviewAssets(UUID.randomUUID().toString(), sourceAssets.toMap())
        return assets.token
    }
}

internal fun composeProjectSource(mainCode: String, files: Map<String, String>): String = buildString {
    files.toSortedMap().forEach { (name, code) ->
        append(code)
        append("\n//# sourceURL=")
        append(name)
        append("\n")
    }
    append(mainCode)
    append("\n//# sourceURL=sketch.js")
}
