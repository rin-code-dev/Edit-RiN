package com.hikariatelier.app

/**
 * Generates a completely standalone single .html file that runs the sketch
 * in any standard web browser without needing Edit:RiN or local server.
 */
internal object StandaloneHtmlGenerator {

    private const val P5_V2_CDN = "https://cdn.jsdelivr.net/npm/p5@2.3.4/lib/p5.min.js"
    private const val P5_V1_CDN = "https://cdn.jsdelivr.net/npm/p5@1.11.5/lib/p5.min.js"
    private const val P5_SOUND_V2_CDN = "https://cdn.jsdelivr.net/npm/p5.sound@0.4.1/dist/p5.sound.min.js"
    private const val P5_SOUND_V1_CDN = "https://cdn.jsdelivr.net/npm/p5@1.11.5/lib/addons/p5.sound.min.js"
    private const val MATTER_CDN = "https://cdnjs.cloudflare.com/ajax/libs/matter-js/0.20.0/matter.min.js"
    private const val BRUSH_CDN = "https://cdn.jsdelivr.net/npm/p5.brush@2.2.1/dist/p5.brush.js"
    private const val P5_WEBGPU_CDN = "https://cdn.jsdelivr.net/npm/p5@2.3.4/lib/p5.webgpu.js"

    fun generate(work: Work, currentCode: String = work.code): String {
        val config = runCatching { readProjectDocumentConfig(work.files) }.getOrDefault(ProjectDocumentConfig())
        val resolvedRun = runCatching { resolveProjectRun(work.files, config, currentCode) }.getOrNull()

        val entryHtml = resolvedRun?.documentPath?.let { work.files[it] }

        return if (entryHtml != null) {
            bundleExistingHtml(entryHtml, work.files, currentCode).let { html ->
                val init = parameterInitialization(work, currentCode)
                val head = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
                if (head != null) html.replaceRange(head.range.last + 1, head.range.last + 1, init) else init + html
            }
        } else {
            generateNewStandaloneHtml(work, currentCode, config, resolvedRun)
        }
    }

    private fun generateNewStandaloneHtml(
        work: Work,
        currentCode: String,
        config: ProjectDocumentConfig,
        resolvedRun: ResolvedProjectRun?
    ): String {
        val title = escapeHtml(work.title.ifBlank { "Edit:RiN Sketch" })
        val isV2 = work.p5Version.startsWith("2")

        val p5CoreUrl = config.p5Url ?: if (isV2) P5_V2_CDN else P5_V1_CDN
        val p5SoundUrl = if (work.p5SoundEnabled) {
            if (isV2) P5_SOUND_V2_CDN else P5_SOUND_V1_CDN
        } else null

        val scripts = buildList {
            add("<script src=\"$p5CoreUrl\"></script>")
            if (p5SoundUrl != null) {
                add("<script src=\"$p5SoundUrl\"></script>")
            }
            if (work.libraries.containsKey("matter-js")) {
                add("<script src=\"$MATTER_CDN\"></script>")
            }
            if (work.libraries.containsKey("p5.brush") && isV2) {
                add("<script src=\"$BRUSH_CDN\"></script>")
            }
            for (lib in config.libraries) {
                val modAttr = if (lib.type == "module") " type=\"module\"" else ""
                add("<script$modAttr src=\"${escapeHtml(lib.url)}\"></script>")
            }
        }

        val isModule = resolvedRun?.moduleMode == true
        val classicAuxiliaryScripts = if (isModule) emptyList() else if (resolvedRun?.classicScripts?.isNotEmpty() == true) {
            resolvedRun.classicScripts
        } else {
            orderedClassicScriptFiles(work.files, config)
        }.filter { it != "sketch.js" && it in work.files }

        val auxiliaryInlined = classicAuxiliaryScripts.joinToString("\n") { path ->
            val code = work.files[path].orEmpty()
            val safeCommentPath = escapeHtml(path).replace("-->", "-- >")
            "<!-- Project file: $safeCommentPath -->\n<script>\n${escapeScriptContent(code)}\n</script>"
        }

        val moduleFiles = work.files + ("sketch.js" to currentCode)
        val mainScript = if (isModule) {
            val entry = requireNotNull(resolvedRun?.modulePath)
            "<script>window.__editRinRegisterModuleCallbacks=c=>{" +
                "for(const k in c)if(typeof c[k]==='function')window[k]=c[k];" +
                "if((c.setup||c.draw)&&window.p5&&!window.p5.instance)new window.p5()};</script>\n" +
                "<script type=\"importmap\">" + escapeScriptContent(standaloneModuleMap(moduleFiles, entry)) +
                "</script>\n<script type=\"module\">import " + org.json.JSONObject.quote("rin-project/$entry") + ";</script>"
        } else "<script>\n${escapeScriptContent(currentCode)}\n</script>"

        return buildString {
            appendLine("<!doctype html>")
            appendLine("<html lang=\"ja\">")
            appendLine("<head>")
            appendLine("  <meta charset=\"utf-8\">")
            appendLine("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">")
            appendLine("  <title>$title</title>")
            appendLine("  <style>")
            appendLine("    html, body {")
            appendLine("      margin: 0;")
            appendLine("      padding: 0;")
            appendLine("      width: 100%;")
            appendLine("      height: 100%;")
            appendLine("      overflow: hidden;")
            appendLine("      background-color: #000000;")
            appendLine("      display: flex;")
            appendLine("      justify-content: center;")
            appendLine("      align-items: center;")
            appendLine("    }")
            appendLine("    canvas {")
            appendLine("      display: block;")
            appendLine("      touch-action: none;")
            appendLine("    }")
            appendLine("  </style>")
            appendLine(parameterInitialization(work, currentCode))
            scripts.forEach { appendLine("  $it") }
            if (auxiliaryInlined.isNotBlank()) {
                appendLine(auxiliaryInlined)
            }
            appendLine("</head>")
            appendLine("<body>")
            appendLine(mainScript)
            appendLine("</body>")
            append("</html>")
        }
    }

    private fun parameterInitialization(work: Work, currentCode: String): String {
        val declarations = workParameters(work.files + ("sketch.js" to currentCode))
        val json = parameterValuesJson(declarations, work.parameterValues)
        return "<script>window.rinParams=" + escapeScriptContent(json) + ";</script>"
    }

    private fun bundleExistingHtml(
        html: String,
        files: Map<String, String>,
        mainSource: String
    ): String {
        // Strip Edit:RiN runtime wrappers
        var bundled = html
            .replace(Regex("""<script[^>]*src=["'][^"']*(?:p5_host|p5_bootstrap|p5_sketch)\.js["'][^>]*>\s*</script>""", RegexOption.IGNORE_CASE), "")

        // Replace bundled offline asset URLs with public CDN
        bundled = bundled
            .replace("p5-v2.min.js", P5_V2_CDN)
            .replace("p5-v1.min.js", P5_V1_CDN)
            .replace("p5.sound.min.js", P5_SOUND_V2_CDN)
            .replace("p5.sound-v1.min.js", P5_SOUND_V1_CDN)
            .replace("matter-0.20.0.min.js", MATTER_CDN)
            .replace("p5.brush-2.2.1.js", BRUSH_CDN)
            .replace("p5.webgpu.js", P5_WEBGPU_CDN)

        // Inline local JS files referenced via <script src="...">
        val scriptRegex = Regex("""<script([^>]*)src=["']([^"']+)["']([^>]*)>\s*</script>""", RegexOption.IGNORE_CASE)
        bundled = scriptRegex.replace(bundled) { match ->
            val preAttrs = match.groupValues[1]
            val rawSrc = match.groupValues[2]
            val postAttrs = match.groupValues[3]
            val src = rawSrc.substringBefore('?').substringBefore('#').trimStart('.', '/')

            val fileContent = when (src) {
                "sketch.js" -> mainSource
                else -> files[src]
            }

            if (fileContent != null) {
                "<script$preAttrs$postAttrs>\n${escapeScriptContent(fileContent)}\n</script>"
            } else {
                match.value
            }
        }

        // Inline local CSS files referenced via <link rel="stylesheet" href="...">
        val cssRegex = Regex("""<link([^>]*)href=["']([^"']+)["']([^>]*)>""", RegexOption.IGNORE_CASE)
        bundled = cssRegex.replace(bundled) { match ->
            val fullTag = match.value
            if (!fullTag.contains("stylesheet", ignoreCase = true)) return@replace fullTag
            val rawHref = match.groupValues[2]
            val href = rawHref.substringBefore('?').substringBefore('#').trimStart('.', '/')
            val cssContent = files[href]
            if (cssContent != null) {
                val safeCss = cssContent.replace(Regex("</style", RegexOption.IGNORE_CASE)) { "<\\/style" }
                "<style>\n$safeCss\n</style>"
            } else {
                fullTag
            }
        }

        return bundled
    }

    private fun escapeScriptContent(content: String): String {
        return content.replace(Regex("</script", RegexOption.IGNORE_CASE)) { "<\\/script" }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
