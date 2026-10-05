package com.hikariatelier.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProjectDocumentTest {
    @Test fun safeRelativeFolderPathsRoundTripAndTraversalIsRejected() {
        val path = "画像/空 の写真.png"
        assertTrue(validProjectPath(path))
        assertEquals(path, projectPathFromUrl(projectFileUrl("run", path), "run"))
        for (path in listOf("../x.js", "dir/../x.js", "/x.js", "dir//x.js", "dir/./x.js", "x\\y", "x%2fy", "x?y", "https:evil", "x\u0000"))
            assertFalse(path, validProjectPath(path))
        assertFalse(validProjectPath("__edit-rin__/p5_host.js"))
        assertTrue(validProjectPath("p5_host.js"))
        for (url in listOf("$PREVIEW_ORIGIN/project/run/%2e%2e/secret", "$PREVIEW_ORIGIN/project/run/dir%2fsecret",
            "$PREVIEW_ORIGIN/project/run/%252e%252e/secret", "$PREVIEW_ORIGIN/project/old/a.js", "https://example.org/project/run/a.js"))
            assertNull(url, projectPathFromUrl(url, "run"))
    }

    @Test fun moduleCssJsonAndSourceMapsHaveBrowserCompatibleMimeTypes() {
        assertEquals("application/javascript", projectTextMimeType("src/module.mjs"))
        assertEquals("text/css", projectTextMimeType("styles/style.css"))
        assertEquals("text/html", projectTextMimeType("pages/index.html"))
        assertEquals("application/json", projectTextMimeType("src/module.js.map"))
        assertEquals("application/json", projectTextMimeType("data/settings.json"))
    }

    @Test fun previousCurrentP5VersionRemainsCompatibleWithBundledCurrent() {
        assertEquals("2.3.4", P5_VERSION_CURRENT)
        assertEquals(P5_VERSION_CURRENT, normalizedP5Version("2.3.3"))
        assertEquals(P5_VERSION_LEGACY, normalizedP5Version(P5_VERSION_LEGACY))
    }

    @Test fun hostInjectionPrecedesAuthorScriptsWithoutChangingTheirOrderingAttributesOrBase() {
        val html = """
            <!DOCTYPE html><html><head><base href="../">
            <script async src="https://cdn.jsdelivr.net/npm/p5@2.3.3/lib/p5.min.js"></script>
            <script defer src="src/first.js"></script><script type="module" src="src/entry.mjs"></script>
            <link rel="stylesheet" href="styles/style.css"></head><body><main id="sketch"></main></body></html>
        """.trimIndent()
        val generated = prepareProjectHtml(html, "run")
        assertTrue(generated.indexOf("p5_host.js") < generated.indexOf("p5-v2.min.js"))
        assertTrue(generated.contains("<base href=\"../\">"))
        assertTrue(generated.contains("<script async src=\"$PREVIEW_ORIGIN/project/run/p5-v2.min.js\"></script>"))
        assertTrue(generated.contains("<script defer src=\"src/first.js\"></script>"))
        assertTrue(generated.contains("<script type=\"module\" src=\"src/entry.mjs\"></script>"))
        assertEquals(html.count { it == '\n' }, generated.count { it == '\n' })
        assertTrue(generated.indexOf("src/first.js") < generated.indexOf("src/entry.mjs"))
    }

    @Test fun commentsAndInlineScriptStringsAreNotMistakenForDocumentHeadsOrLibraryTags() {
        val html = "<!-- <head><script src='https://unpkg.com/p5@2.3.4/lib/p5.min.js'></script> -->" +
            "<html><body><script>const example = \"<head><script src='fake.js'>\";</script></body></html>"
        val generated = prepareProjectHtml(html, "run")
        assertTrue(generated.contains("const example = \"<head><script src='fake.js'>\";"))
        assertTrue(generated.contains("<!-- <head><script src='https://unpkg.com/p5@2.3.4/lib/p5.min.js'></script> -->"))
        assertEquals(1, Regex("p5_host\\.js").findAll(generated).count())
    }

    @Test fun onlyExactTrustedBundledP5VersionsAreRewritten() {
        assertEquals("p5-v1.min.js", bundledP5ScriptAlias("https://cdnjs.cloudflare.com/ajax/libs/p5.js/1.11.5/p5.min.js"))
        assertEquals("p5-v2.min.js", bundledP5ScriptAlias("https://unpkg.com/p5@2.3.4/lib/p5.min.js"))
        assertEquals("p5.webgpu.js", bundledP5ScriptAlias("https://cdn.jsdelivr.net/npm/p5@2.3.4/lib/p5.webgpu.js"))
        assertNull(bundledP5ScriptAlias("https://cdn.jsdelivr.net/npm/p5@1.9.4/lib/p5.min.js"))
        assertNull(bundledP5ScriptAlias("https://unpkg.com.evil.test/p5@2.3.4/lib/p5.min.js"))
        assertNull(bundledP5ScriptAlias("https://user@unpkg.com/p5@2.3.4/lib/p5.min.js"))
    }

    @Test fun exactLegacyAndModernSoundDistributionsUseTheirCompatibleBundles() {
        assertEquals("p5.sound-v1.min.js", bundledP5ScriptAlias("https://cdn.jsdelivr.net/npm/p5@1.11.5/lib/addons/p5.sound.min.js"))
        assertEquals("p5.sound-v1.min.js", bundledP5ScriptAlias("https://cdnjs.cloudflare.com/ajax/libs/p5.js/1.11.5/addons/p5.sound.js"))
        assertEquals("p5.sound.min.js", bundledP5ScriptAlias("https://unpkg.com/p5.sound@0.4.1/dist/p5.sound.js"))
        assertEquals("p5.sound.min.js", bundledP5ScriptAlias("https://cdn.jsdelivr.net/npm/p5.sound@0.4.1/dist/p5.sound.min.js"))
        assertNull(bundledP5ScriptAlias("https://unpkg.com/p5.sound@0.3.12/dist/p5.sound.min.js"))
        assertNull(bundledP5ScriptAlias("https://cdn.jsdelivr.net/npm/p5@1.9.4/lib/addons/p5.sound.min.js"))
    }

    @Test fun changingBundledP5RemovesOnlyItsIntegrityWithoutMistakingOtherAttributeStringsForSrc() {
        val html = """
            <head><script async src='https://unpkg.com/p5@2.3.3/lib/p5.js' integrity='sha384-old-core' crossorigin='anonymous'></script>
            <script src='https://example.org/user.js' integrity='sha384-user'></script>
            <script data-note=" src='https://unpkg.com/p5@2.3.4/lib/p5.js' " src='user.js'></script></head>
        """.trimIndent()
        val generated = prepareProjectHtml(html, "run")
        assertFalse(generated.contains("sha384-old-core"))
        assertTrue(generated.contains("integrity='sha384-user'"))
        assertTrue(generated.contains("crossorigin='anonymous'"))
        assertTrue(generated.contains("data-note=\" src='https://unpkg.com/p5@2.3.4/lib/p5.js' \" src='user.js'"))
        assertTrue(generated.contains("/__edit-rin__/p5_host.js"))
        assertEquals(html.count { it == '\n' }, generated.count { it == '\n' })
    }

    @Test fun authorCspKeepsItsP5SourceAndIntegrityInsteadOfRewritingItsPolicy() {
        val html = "<head><meta http-equiv='Content-Security-Policy' content=\"script-src https://unpkg.com\">" +
            "<script src='https://unpkg.com/p5@2.3.4/lib/p5.min.js' integrity='sha384-original'></script></head>"
        val generated = prepareProjectHtml(html, "run")
        assertTrue(generated.contains("script-src https://unpkg.com"))
        assertTrue(generated.contains("src='https://unpkg.com/p5@2.3.4/lib/p5.min.js' integrity='sha384-original'"))
        assertTrue(generated.contains("/__edit-rin__/p5_host.js"))
        assertFalse(generated.contains("p5-v2.min.js"))
    }

    @Test fun configurationPreservesUnknownKeysAndLibraryOrderAndRejectsUnsafeUrls() {
        val files = mapOf(PROJECT_CONFIG_FILE to "{\"custom\":{\"value\":7}}", "src/a.js" to "const a = 1;")
        val config = ProjectDocumentConfig(libraries = listOf(ProjectLibraryScript("https://example.org/first.js"),
            ProjectLibraryScript("https://example.org/second.mjs", "module")), classicScriptOrder = listOf("sketch.js", "src/a.js"), executionMode = "classic")
        val updated = withProjectDocumentConfig(files, config)
        assertEquals(7, JSONObject(updated.getValue(PROJECT_CONFIG_FILE)).getJSONObject("custom").getInt("value"))
        assertEquals(config, readProjectDocumentConfig(updated))
        val runtime = JSONObject(resolveProjectRun(updated, config).runtimeConfig("run"))
        assertTrue(runtime.getJSONArray("classicScripts").getString(0).endsWith("/sketch.js"))
        assertEquals("https://example.org/first.js", runtime.getJSONArray("libraries").getJSONObject(0).getString("url"))
        for (url in listOf("javascript:alert(1)", "http://example.org/script.js", "https://user:pass@example.org/script.js"))
            assertNull(normalizedExternalScriptUrl(url))
        assertTrue(runCatching { readProjectDocumentConfig(mapOf(PROJECT_CONFIG_FILE to "{\"libraries\":\"lost\"}")) }.isFailure)
    }

    @Test fun explicitClassicModeSuppressesHtmlWhileModuleModeUsesEntryFile() {
        val files = mapOf("pages/index.html" to "<html></html>", "src/entry.mjs" to "export const n = 1;")
        assertEquals("pages/index.html", projectEntryDocument(files, ProjectDocumentConfig()))
        assertNull(projectEntryDocument(files, ProjectDocumentConfig(executionMode = "classic")))
        val config = ProjectDocumentConfig(moduleEntry = "src/entry.mjs", executionMode = "module")
        val runtime = JSONObject(resolveProjectRun(files, config).runtimeConfig("run"))
        assertTrue(runtime.getBoolean("moduleMode"))
        assertFalse(runtime.getBoolean("documentMode"))
        assertEquals("$PREVIEW_ORIGIN/project/run/src/entry.mjs", runtime.getString("moduleEntry"))
    }

    @Test fun moduleDetectionIgnoresDynamicImportsCommentsAndStrings() {
        assertTrue(projectUsesModuleSyntax("import { paint } from './paint.js';\nfunction setup() {}"))
        assertTrue(projectUsesModuleSyntax("export function setup() {}"))
        assertFalse(projectUsesModuleSyntax("const text = 'import x from file'; // export value\nimport('./optional.mjs');"))
        assertFalse(projectUsesModuleSyntax("import /* optional */ ('./optional.mjs');"))
        val ordinary = JSONObject(resolveProjectRun(mapOf("optional.mjs" to "export const n=1"), ProjectDocumentConfig(), "function setup() {}").runtimeConfig("run"))
        assertFalse(ordinary.getBoolean("moduleMode"))
    }

    @Test fun regexLiteralsKeepLegacySupportingScriptsWhileStaticImportsStillSelectModules() {
        val classic = "const re = /export|import[\\/]/gi; if (true) /export/.test('x'); function setup(){helper();}"
        assertFalse(projectUsesModuleSyntax(classic))
        val runtime = JSONObject(resolveProjectRun(mapOf("helper.js" to "function helper() {}"),
            ProjectDocumentConfig(), classic).runtimeConfig("run"))
        assertFalse(runtime.getBoolean("moduleMode"))
        assertTrue(projectUsesModuleSyntax("const re = /export/; import /* library */ { paint } from './paint.mjs';"))
        assertTrue(projectUsesModuleSyntax("const re = /import/; export function setup() {}"))
        assertFalse(projectUsesModuleSyntax("const ratio = width / height; function setup() {}"))
        assertTrue(projectUsesModuleSyntax("const ratio = width / height; export const result = ratio;"))
    }

    @Test fun resolvedEntryPathsRemainRelativeAndOnlyTheBridgeEncodesTheirUrls() {
        val entry = "scripts/作品 の入口.mjs"
        val config = ProjectDocumentConfig(moduleEntry = entry, executionMode = "module",
            p5Url = "https://unpkg.com/p5@2.3.3/lib/p5.min.js",
            libraries = listOf(ProjectLibraryScript("https://example.org/first.js"), ProjectLibraryScript("https://example.org/second.mjs", "module")))
        val run = resolveProjectRun(mapOf(entry to "export function setup() {}"), config)
        assertEquals(entry, run.modulePath)
        assertNull(run.documentPath)
        assertTrue(run.usesSeparateFiles)
        val runtime = JSONObject(run.runtimeConfig("owner"))
        assertEquals(setOf("documentMode", "moduleMode", "moduleEntry", "classicScripts", "libraries", "p5Url"), runtime.keys().asSequence().toSet())
        assertEquals(entry, projectPathFromUrl(runtime.getString("moduleEntry"), "owner"))
        assertEquals("$PREVIEW_ORIGIN/project/owner/p5-v2.min.js", runtime.getString("p5Url"))
        assertEquals("https://example.org/second.mjs", runtime.getJSONArray("libraries").getJSONObject(1).getString("url"))
        assertEquals("module", runtime.getJSONArray("libraries").getJSONObject(1).getString("type"))
    }

    @Test fun oneResolverPreservesHtmlPriorityAutomaticModuleRulesAndInvalidEntryErrors() {
        val files = mapOf("pages/index.html" to "<p>Page</p>", "src/entry.mjs" to "export const n=1;")
        val html = resolveProjectRun(files, ProjectDocumentConfig(moduleEntry = "missing.mjs"), "export function setup() {}")
        assertEquals("pages/index.html", html.documentPath)
        assertNull(html.modulePath)
        val soleModule = resolveProjectRun(files - "pages/index.html", ProjectDocumentConfig())
        assertEquals("src/entry.mjs", soleModule.modulePath)
        assertFalse(resolveProjectRun(files - "pages/index.html" + ("helper.js" to "const n=1;"), ProjectDocumentConfig()).moduleMode)
        val classic = resolveProjectRun(files, ProjectDocumentConfig(executionMode = "classic", classicScriptOrder = listOf("sketch.js")))
        assertNull(classic.documentPath)
        assertEquals(listOf("sketch.js"), classic.classicScripts)
        assertTrue(runCatching { resolveProjectRun(files, ProjectDocumentConfig(executionMode = "module", moduleEntry = "missing.mjs")) }.isFailure)
        assertTrue(runCatching { resolveProjectRun(emptyMap(), ProjectDocumentConfig(executionMode = "html")) }.isFailure)
    }
}
