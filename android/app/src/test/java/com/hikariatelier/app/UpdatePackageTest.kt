package com.hikariatelier.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdatePackageTest {
    private val version = ReleaseVersion.parse("2.3.0")!!
    private val installed = UpdatePackageIdentity("com.hikariatelier.app", "2.2.2", 29, setOf("release-cert"))
    private val candidate = installed.copy(versionName = "2.3.0", versionCode = 30)

    @Test fun acceptsOnlySameAppNewerCodeMatchingTagAndSigners() {
        assertTrue(eligibleUpdatePackage(candidate, installed, version))
        assertFalse(eligibleUpdatePackage(candidate.copy(packageName = "com.hikariatelier.app.debug"), installed, version))
        assertFalse(eligibleUpdatePackage(candidate.copy(versionCode = 29), installed, version))
        assertFalse(eligibleUpdatePackage(candidate.copy(versionCode = 28), installed, version))
        assertFalse(eligibleUpdatePackage(candidate.copy(versionName = "2.4.0"), installed, version))
        assertFalse(eligibleUpdatePackage(candidate.copy(signers = setOf("other-cert")), installed, version))
        assertFalse(eligibleUpdatePackage(candidate.copy(signers = emptySet()), installed, version))
        assertFalse(eligibleUpdatePackage(candidate.copy(signers = setOf("release-cert", "extra-cert")), installed, version))
    }
    private fun release(name: String = "Edit-RiN-v2.3.0.apk", url: String =
        "https://github.com/rin-code-dev/Edit-RiN/releases/download/v2.3.0/Edit-RiN-v2.3.0.apk",
        size: Long = 123, digest: String? = null): JSONObject = JSONObject().put("tag_name", "v2.3.0")
        .put("assets", org.json.JSONArray().put(JSONObject().put("name", name)
            .put("browser_download_url", url).put("size", size).apply { if (digest != null) put("digest", digest) }))

    @Test fun findsReleaseApkAndOptionalGithubDigest() {
        assertNotNull(releaseApk(release()))
        assertEquals("a".repeat(64), releaseApk(release(digest = "sha256:" + "A".repeat(64)))?.sha256)
        assertNull(releaseApk(release(digest = "sha256:bad")))
    }
    @Test fun excludesDebugArchivesUntrustedUrlsAndInvalidSizes() {
        assertNull(releaseApk(release(name = "Edit-RiN-debug.apk")))
        assertNull(releaseApk(release(name = "Edit-RiN-v2.3.0-source.zip")))
        assertNull(releaseApk(release(url = "http://github.com/rin-code-dev/Edit-RiN/releases/download/v2.3.0/Edit-RiN-v2.3.0.apk")))
        assertNull(releaseApk(release(url = "https://example.com/update.apk")))
        assertNull(releaseApk(release(url = "https://github.com/other/repo/releases/download/v2.3.0/Edit-RiN-v2.3.0.apk")))
        assertNull(releaseApk(release(size = 0)))
        assertNull(releaseApk(release(size = MAX_UPDATE_BYTES + 1)))
    }
    @Test fun retainsNotificationForReleasesWithoutUsableApk() {
        val selected = newestRelease("""[{"tag_name":"v2.3.0"}]""")!!
        assertNull(selected.apk)
        assertNotNull(evaluateUpdate(selected, "2.2.2", false).release)
    }
    @Test fun rejectsAmbiguousDuplicateAssets() {
        val json = release()
        json.getJSONArray("assets").put(json.getJSONArray("assets").getJSONObject(0))
        assertNull(releaseApk(json))
    }
}
