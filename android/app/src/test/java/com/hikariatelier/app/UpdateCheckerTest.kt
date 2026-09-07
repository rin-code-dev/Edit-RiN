package com.hikariatelier.app

import org.junit.Assert.*
import org.junit.Test

class UpdateCheckerTest {
    @Test fun comparesBetaNumbersNumerically() {
        assertTrue(ReleaseVersion.parse("v1.0.0-beta.10")!! > ReleaseVersion.parse("1.0.0-beta.3")!!)
    }
    @Test fun stableFollowsBetaAndMetadataIsIgnored() {
        assertTrue(ReleaseVersion.parse("1.0.0")!! > ReleaseVersion.parse("1.0.0-beta.99")!!)
        assertEquals(0, ReleaseVersion.parse("1.0.0+build1")!!.compareTo(ReleaseVersion.parse("v1.0.0")!!))
        assertTrue(ReleaseVersion.parse("1.1.0-beta.1")!! > ReleaseVersion.parse("1.0.9")!!)
    }
    @Test fun rejectsUnknownTags() {
        listOf("", "latest", "1.0", "1.0.0-beta..3").forEach { assertNull(ReleaseVersion.parse(it)) }
    }
    @Test fun includesPrereleasesAndIgnoresDraftsWithoutTrustingOrder() {
        val json = """[
            {"tag_name":"v1.0.0-beta.3","prerelease":true},
            {"tag_name":"v2.0.0","draft":true},
            {"tag_name":"v1.0.0-beta.10","prerelease":true},
            {"tag_name":"latest"}
        ]"""
        assertEquals("v1.0.0-beta.10", newestRelease(json)?.tag)
        assertNull(newestRelease("[]"))
    }
}
