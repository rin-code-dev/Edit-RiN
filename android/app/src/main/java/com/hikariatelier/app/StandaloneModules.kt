package com.hikariatelier.app

import java.net.URI
import java.util.Base64
import org.json.JSONObject

/** Embed project modules under stable bare specifiers, so data-URL modules can import each other. */
internal fun standaloneModuleMap(files: Map<String, String>, entry: String): String {
    val imports = JSONObject()
    files.filterKeys(::isJavaScriptProjectFile).forEach { (path, source) ->
        val rewritten = rewriteStandaloneModuleImports(source, path, files.keys)
        val code = rewritten + if (path == entry) moduleCallbackRegistrationTail() else ""
        val url = "data:text/javascript;base64," + Base64.getEncoder().encodeToString(code.toByteArray(Charsets.UTF_8))
        imports.put("rin-project/$path", url)
    }
    return JSONObject().put("imports", imports).toString()
}

internal fun rewriteStandaloneModuleImports(source: String, path: String, paths: Set<String>): String {
    val out = StringBuilder()
    var i = 0
    var word = ""
    var previous = ""
    var expectSpecifier = false
    while (i < source.length) {
        val c = source[i]
        if (source.startsWith("//", i) || source.startsWith("/*", i)) {
            val end = if (source.startsWith("//", i)) source.indexOf('\n', i).takeIf { it >= 0 } ?: source.length
                else source.indexOf("*/", i + 2).takeIf { it >= 0 }?.plus(2) ?: source.length
            out.append(source, i, end); i = end
        } else if (c == '\'' || c == '"' || c == '`') {
            val start = i++
            while (i < source.length) {
                if (source[i] == '\\') i = (i + 2).coerceAtMost(source.length)
                else if (source[i++] == c) break
            }
            val text = source.substring(start, i)
            val value = text.drop(1).dropLast(1)
            if (expectSpecifier && c != '`' && (value.startsWith(".") || value.startsWith("/"))) {
                val resolved = runCatching { URI("/$path").resolve(value).normalize().path.removePrefix("/") }.getOrNull()
                if (resolved in paths) out.append(JSONObject.quote("rin-project/$resolved")) else out.append(text)
            } else out.append(text)
            expectSpecifier = false; word = ""; previous = "literal"
        } else if (c.isLetterOrDigit() || c in "_$") {
            val start = i++
            while (i < source.length && (source[i].isLetterOrDigit() || source[i] in "_$")) i++
            word = source.substring(start, i)
            expectSpecifier = previous != "." && word in setOf("from", "import")
            previous = word; out.append(word)
        } else {
            if (!c.isWhitespace()) {
                expectSpecifier = expectSpecifier && c == '(' && word == "import"
                previous = c.toString()
            }
            out.append(c); i++
        }
    }
    return out.toString()
}
