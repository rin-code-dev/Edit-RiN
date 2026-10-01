package com.hikariatelier.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemBarVisibilityTest {
    @Test fun landscapeHidesBothRegardlessOfSettings() {
        for (status in listOf(false, true)) for (navigation in listOf(false, true))
            assertEquals(SystemBarVisibility(false, false), systemBarVisibility(true, status, navigation))
    }
    @Test fun portraitHonorsBothSettingsIndependently() {
        for (status in listOf(false, true)) for (navigation in listOf(false, true))
            assertEquals(SystemBarVisibility(status, navigation), systemBarVisibility(false, status, navigation))
    }
}
