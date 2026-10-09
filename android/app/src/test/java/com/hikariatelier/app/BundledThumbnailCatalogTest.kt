package com.hikariatelier.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BundledThumbnailCatalogTest {
    @Test fun originalsUseValidatedImagesAcrossLanguagesButCopiesAndChangedParametersDoNot() {
        val assets = ApplicationProvider.getApplicationContext<Context>().assets
        val catalog = BundledThumbnailCatalog(assets)
        val originals = sampleCatalog(defaultWorks(assets), emptyList())
        assertEquals(10, originals.size)
        for (work in originals) {
            val input = capturePreviewRun(work, work.code, work.files, work.assets)
            val path = catalog.assetPath(work, input)
            assertNotNull(work.id, path)
            assertTrue(assets.open(path!!).use { it.readBytes() }.size > 100)
            assertEquals(path, catalog.assetPath(work, input.copy(source = localizedSampleCode(work.code, "ja"))))
            assertEquals(path, catalog.assetPath(work, input.copy(source = localizedSampleCode(work.code, "zh"))))
            assertNull(catalog.assetPath(work, input.copy(source = work.code + "\nbackground(0);")))
            assertNull(catalog.assetPath(work, input.copy(parameterValues = mapOf("speed" to "2"))))
            assertNull(catalog.assetPath(Work("copy", work.title, work.code), input))
        }
    }
}
