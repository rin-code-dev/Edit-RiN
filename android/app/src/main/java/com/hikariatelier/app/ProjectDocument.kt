package com.hikariatelier.app

import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

internal const val PROJECT_CONFIG_FILE = "edit-rin.json"
internal data class ProjectLibraryScript(val url: String, val type: String = "classic")
internal data class ProjectDocumentConfig(
    val entryDocument: String? = null,
    val moduleEntry: String? = null,
    val libraries: List<ProjectLibraryScript> = emptyList(),
    val p5Url: String? = null,
    val classicScriptOrder: List<String> = emptyList(),
    val executionMode: String = "auto"
)

internal fun readProjectDocumentConfig(files: Map<String, String>): ProjectDocumentConfig {
    val json = files[PROJECT_CONFIG_FILE]?.let(::JSONObject) ?: return ProjectDocumentConfig()
    listOf("libraries", "classicScriptOrder").forEach { key ->
        require(!json.has(key) || json.isNull(key) || json.get(key) is JSONArray) { "$key must be an array" }
    }
    fun optionalPath(key: String): String? = if (!json.has(key) || json.isNull(key)) null else
        json.getString(key).takeIf { it.isNotBlank() }?.also { require(validProjectPath(it)) { "Invalid $key path" } }
    val libraries = json.optJSONArray("libraries")?.let { array ->
        require(array.length() <= 50) { "A project supports up to 50 library URLs" }
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val url = requireNotNull(normalizedExternalScriptUrl(item.getString("url"))) { "Library URLs must use HTTPS" }
            val type = item.optString("type", "classic").also { require(it in setOf("classic", "module")) }
            ProjectLibraryScript(url, type)
        }
    }.orEmpty()
    val order = json.optJSONArray("classicScriptOrder")?.let { array ->
        require(array.length() <= MAX_PROJECT_TEXT_FILES + 1)
        List(array.length()) { array.getString(it).also { path -> require(validProjectPath(path)) } }
            .also { require(it.distinct().size == it.size) { "Script order contains duplicates" } }
    }.orEmpty()
    val p5Url = if (!json.has("p5Url") || json.isNull("p5Url") || json.getString("p5Url").isBlank()) null
        else requireNotNull(normalizedExternalScriptUrl(json.getString("p5Url"))) { "p5 URLs must use HTTPS" }
    return ProjectDocumentConfig(optionalPath("entryDocument"), optionalPath("moduleEntry"), libraries, p5Url, order,
        json.optString("executionMode", "auto").also { require(it in setOf("auto", "classic", "module", "html")) })
}

internal fun withProjectDocumentConfig(files: Map<String, String>, config: ProjectDocumentConfig): Map<String, String> {
    val json = files[PROJECT_CONFIG_FILE]?.let(::JSONObject) ?: JSONObject()
    json.put("entryDocument", config.entryDocument ?: JSONObject.NULL).put("moduleEntry", config.moduleEntry ?: JSONObject.NULL)
        .put("p5Url", config.p5Url ?: JSONObject.NULL).put("executionMode", config.executionMode)
        .put("classicScriptOrder", JSONArray(config.classicScriptOrder)).put("libraries", JSONArray().apply {
            config.libraries.forEach { put(JSONObject().put("url", it.url).put("type", it.type)) }
        })
    val updated = files + (PROJECT_CONFIG_FILE to json.toString(2))
    readProjectDocumentConfig(updated)
    validateProjectTextFiles(updated)
    return updated
}

internal fun orderedClassicScriptFiles(files: Map<String, String>, config: ProjectDocumentConfig): List<String> {
    val available = files.keys.filter { isClassicJavaScriptProjectFile(it) && it != "sketch.js" }.sorted() + "sketch.js"
    return config.classicScriptOrder.filter { it in available } + available.filter { it !in config.classicScriptOrder }
}

internal fun projectEntryDocument(files: Map<String, String>, config: ProjectDocumentConfig): String? {
    if (config.executionMode in setOf("classic", "module")) return null
    val path = config.entryDocument ?: files.keys.firstOrNull { it.equals("index.html", true) }
        ?: files.keys.sorted().firstOrNull { it.substringAfterLast('/').equals("index.html", true) }
    if (config.executionMode == "html" || config.entryDocument != null) {
        require(path != null && path in files && isHtmlProjectFile(path)) { "HTML entry file is missing" }
    }
    return path?.takeIf { it in files && isHtmlProjectFile(it) }
}

/** Resolve relative entry paths once; URL encoding is only needed at the bridge boundary. */
internal data class ResolvedProjectRun(
    val documentPath: String?,
    val modulePath: String?,
    val classicScripts: List<String>,
    val libraries: List<ProjectLibraryScript>,
    val p5Url: String?
) {
    val documentMode get() = documentPath != null
    val moduleMode get() = modulePath != null
    val usesSeparateFiles get() = documentMode || moduleMode || classicScripts.isNotEmpty()

    fun runtimeConfig(token: String): String = JSONObject()
        .put("documentMode", documentMode).put("moduleMode", moduleMode)
        .put("moduleEntry", modulePath?.let { projectFileUrl(token, it) } ?: JSONObject.NULL)
        .put("classicScripts", JSONArray(classicScripts.map { projectFileUrl(token, it) }))
        .put("libraries", JSONArray().apply { libraries.forEach { put(JSONObject().put("url", it.url).put("type", it.type)) } })
        .put("p5Url", p5Url?.let { bundledP5ScriptAlias(it)?.let { alias -> projectFileUrl(token, alias) } ?: it } ?: JSONObject.NULL).toString()
}

internal fun resolveProjectRun(files: Map<String, String>, config: ProjectDocumentConfig, mainSource: String = ""): ResolvedProjectRun {
    val document = projectEntryDocument(files, config)
    val module = when {
        document != null || config.executionMode == "classic" -> null
        config.moduleEntry != null -> config.moduleEntry
        config.executionMode == "module" || projectUsesModuleSyntax(mainSource) -> "sketch.js"
        mainSource.isBlank() && files.keys.none(::isClassicJavaScriptProjectFile) ->
            files.keys.filter { it.endsWith(".mjs", true) }.singleOrNull()
        else -> null
    }
    if (module != null) {
        require(module == "sketch.js" || module in files) { "Module entry file is missing" }
        require(validProjectPath(module)) { "Invalid module entry path" }
    }
    val scripts = if (document == null && module == null && config.classicScriptOrder.isNotEmpty())
        orderedClassicScriptFiles(files, config) else emptyList()
    require(scripts.all(::validProjectPath)) { "Invalid classic script path" }
    return ResolvedProjectRun(document, module, scripts, config.libraries.toList(), config.p5Url)
}

internal fun projectUsesModuleSyntax(source: String): Boolean {
    var cursor = 0
    var braces = 0
    var previous = ""
    val regexPrefixes = setOf("", "=", "(", "[", "{", "}", ")", ",", ":", ";", "return", "case", "!", "?",
        "throw", "yield", "await", "typeof", "void", "delete", "in", "instanceof", "do", "else",
        "+", "-", "*", "%", "&", "|", "^", "~", "<", ">")
    while (cursor < source.length) {
        val c = source[cursor]
        when {
            source.startsWith("//", cursor) -> { cursor = source.indexOf('\n', cursor).takeIf { it >= 0 } ?: source.length; continue }
            source.startsWith("/*", cursor) -> { cursor = source.indexOf("*/", cursor + 2).takeIf { it >= 0 }?.plus(2) ?: source.length; continue }
            c == '/' && previous in regexPrefixes -> {
                val end = javaScriptRegexLiteralEnd(source, cursor)
                if (end != null) { cursor = end; previous = "literal"; continue }
            }
            c in "\"'`" -> {
                val quote = c; cursor++
                while (cursor < source.length) {
                    if (source[cursor] == '\\') { cursor = (cursor + 2).coerceAtMost(source.length); continue }
                    if (source[cursor++] == quote) break
                }
                previous = "literal"; continue
            }
            Character.isJavaIdentifierStart(c) -> {
                val start = cursor++
                while (cursor < source.length && Character.isJavaIdentifierPart(source[cursor])) cursor++
                val word = source.substring(start, cursor)
                var nextOffset = cursor
                while (nextOffset < source.length) {
                    when {
                        source[nextOffset].isWhitespace() -> nextOffset++
                        source.startsWith("/*", nextOffset) -> nextOffset = source.indexOf("*/", nextOffset + 2)
                            .takeIf { it >= 0 }?.plus(2) ?: source.length
                        source.startsWith("//", nextOffset) -> nextOffset = source.indexOf('\n', nextOffset + 2)
                            .takeIf { it >= 0 } ?: source.length
                        else -> break
                    }
                }
                val next = source.getOrNull(nextOffset)
                if (braces == 0 && previous != "." && (word == "export" || word == "import" && next != '(')) return true
                previous = word; continue
            }
        }
        if (c == '{') braces++ else if (c == '}') braces--
        if (!c.isWhitespace()) previous = c.toString()
        cursor++
    }
    return false
}

internal fun moduleCallbackRegistrationTail(): String {
    val callbacks = listOf("preload", "setup", "draw", "windowResized", "mouseMoved", "mouseDragged", "mousePressed",
        "mouseReleased", "mouseClicked", "doubleClicked", "mouseWheel", "keyPressed", "keyReleased", "keyTyped",
        "touchStarted", "touchMoved", "touchEnded", "deviceMoved", "deviceTurned", "deviceShaken")
    return "\n;window.__editRinRegisterModuleCallbacks?.({" + callbacks.joinToString(",") {
        "$it:typeof $it==='function'?$it:undefined"
    } + "});window.__editRinModuleReady?.();\n"
}

private data class ProjectHtmlTag(val start: Int, val end: Int, val name: String, val closing: Boolean, val text: String)
private data class ProjectHtmlAttribute(val start: Int, val end: Int, val name: String, val valueStart: Int, val valueEnd: Int)

private fun projectHtmlAttributes(tag: String): List<ProjectHtmlAttribute> = buildList {
    var cursor = Regex("^<\\s*[^\\s>]+").find(tag)?.range?.last?.plus(1) ?: return@buildList
    while (cursor < tag.length) {
        val start = cursor
        while (cursor < tag.length && tag[cursor].isWhitespace()) cursor++
        if (cursor >= tag.length || tag[cursor] in ">/") break
        val nameStart = cursor
        while (cursor < tag.length && !tag[cursor].isWhitespace() && tag[cursor] !in "=></") cursor++
        if (cursor == nameStart) { cursor++; continue }
        val name = tag.substring(nameStart, cursor).lowercase()
        while (cursor < tag.length && tag[cursor].isWhitespace()) cursor++
        var valueStart = cursor
        var valueEnd = cursor
        if (tag.getOrNull(cursor) == '=') {
            cursor++
            while (cursor < tag.length && tag[cursor].isWhitespace()) cursor++
            val quote = tag.getOrNull(cursor)?.takeIf { it == '\'' || it == '"' }
            if (quote != null) cursor++
            valueStart = cursor
            while (cursor < tag.length && (if (quote != null) tag[cursor] != quote else !tag[cursor].isWhitespace() && tag[cursor] != '>')) cursor++
            valueEnd = cursor
            if (quote != null && cursor < tag.length) cursor++
        }
        add(ProjectHtmlAttribute(start, cursor, name, valueStart, valueEnd))
    }
}

/** A small tag scanner leaves author script contents and all script attributes intact. */
private fun projectHtmlTags(html: String): List<ProjectHtmlTag> = buildList {
    var cursor = 0
    while (cursor < html.length) {
        val start = html.indexOf('<', cursor).takeIf { it >= 0 } ?: break
        if (html.startsWith("<!--", start)) { cursor = html.indexOf("-->", start + 4).takeIf { it >= 0 }?.plus(3) ?: html.length; continue }
        var end = start + 1
        var quote: Char? = null
        while (end < html.length) {
            val c = html[end++]
            if (quote != null) { if (c == quote) quote = null }
            else if (c == '\'' || c == '"') quote = c
            else if (c == '>') break
        }
        val raw = html.substring(start, end)
        val match = Regex("^<\\s*(/?)\\s*([A-Za-z][A-Za-z0-9:-]*)").find(raw)
        cursor = end
        if (match != null) {
            val tag = ProjectHtmlTag(start, end, match.groupValues[2].lowercase(), match.groupValues[1].isNotEmpty(), raw)
            add(tag)
            if (!tag.closing && tag.name in setOf("script", "style", "textarea", "title")) {
                val close = Regex("</\\s*${tag.name}\\s*>", RegexOption.IGNORE_CASE).find(html, end)
                if (close != null) { add(ProjectHtmlTag(close.range.first, close.range.last + 1, tag.name, true, close.value)); cursor = close.range.last + 1 }
                else cursor = html.length
            }
        }
    }
}

/** Only exact bundled versions from trusted p5 CDN hosts are redirected offline. */
internal fun bundledP5ScriptAlias(url: String): String? = runCatching {
    val decoded = url.replace("&amp;", "&")
    val uri = URI(if (decoded.startsWith("//")) "https:$decoded" else decoded)
    if (uri.scheme !in setOf("https", "http") || uri.host !in setOf("cdn.jsdelivr.net", "cdnjs.cloudflare.com", "unpkg.com") ||
        uri.rawUserInfo != null) return null
    val path = uri.path.orEmpty()
    if (Regex("^(?:/npm)?/p5\\.sound@0\\.4\\.1/dist/p5\\.sound(?:\\.min)?\\.js$").matches(path)) return "p5.sound.min.js"
    val version = Regex("(?:p5@|/(?:p5|p5\\.js)/)([0-9]+\\.[0-9]+\\.[0-9]+)(?:/|$)").find(path)?.groupValues?.get(1) ?: return null
    when {
        (path.endsWith("/addons/p5.sound.js") || path.endsWith("/addons/p5.sound.min.js")) && version == P5_VERSION_LEGACY -> "p5.sound-v1.min.js"
        path.endsWith("/p5.webgpu.js") && version == P5_VERSION_CURRENT -> "p5.webgpu.js"
        path.endsWith("/p5.min.js") || path.endsWith("/p5.js") -> when (version) {
            P5_VERSION_LEGACY -> "p5-v1.min.js"; P5_VERSION_CURRENT, "2.3.3" -> "p5-v2.min.js"; else -> null
        }
        else -> null
    }
}.getOrNull()

internal fun prepareProjectHtml(html: String, token: String): String {
    val tags = projectHtmlTags(html)
    val authorCsp = tags.filter { it.name == "meta" && !it.closing }.any { tag ->
        projectHtmlAttributes(tag.text).firstOrNull { it.name == "http-equiv" }?.let { attribute ->
            tag.text.substring(attribute.valueStart, attribute.valueEnd).trim().equals("Content-Security-Policy", true)
        } == true
    }
    val host = "<script src=\"${projectRuntimeAssetUrl(token, "p5_host.js")}\"></script>"
    val head = tags.firstOrNull { it.name == "head" && !it.closing }
    val insertion = head?.end ?: tags.firstOrNull { it.name == "html" && !it.closing }?.end
        ?: Regex("<!doctype[^>]*>", RegexOption.IGNORE_CASE).find(html)?.range?.last?.plus(1) ?: 0
    val edits = mutableListOf(Triple(insertion, insertion, if (head == null) "<head>$host</head>" else host))
    tags.filter { it.name == "script" && !it.closing }.forEach { tag ->
        if (authorCsp) return@forEach
        val attributes = projectHtmlAttributes(tag.text)
        val attribute = attributes.firstOrNull { it.name == "src" } ?: return@forEach
        val value = tag.text.substring(attribute.valueStart, attribute.valueEnd)
        val alias = bundledP5ScriptAlias(value) ?: return@forEach
        edits += Triple(tag.start + attribute.valueStart, tag.start + attribute.valueEnd, projectFileUrl(token, alias))
        attributes.filter { it.name == "integrity" }.forEach { sri ->
            edits += Triple(tag.start + sri.start, tag.start + sri.end,
                tag.text.substring(sri.start, sri.end).map { if (it == '\n') '\n' else ' ' }.joinToString(""))
        }
    }
    return buildString {
        var position = 0
        edits.sortedBy { it.first }.forEach { (start, end, replacement) -> append(html, position, start); append(replacement); position = end }
        append(html, position, html.length)
    }
}
