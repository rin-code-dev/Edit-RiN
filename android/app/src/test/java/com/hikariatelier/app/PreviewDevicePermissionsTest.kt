package com.hikariatelier.app

import android.Manifest
import android.webkit.PermissionRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewDevicePermissionsTest {
    @Test fun onlyTheRequestedCapturePermissionIsNeeded() {
        assertEquals(listOf(Manifest.permission.CAMERA),
            previewDevicePermissions(listOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)))
        assertEquals(listOf(Manifest.permission.RECORD_AUDIO),
            previewDevicePermissions(listOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)))
    }

    @Test fun unknownResourcesCannotAcquireUnrelatedPermissions() {
        assertEquals(emptyList<String>(), previewDevicePermissions(listOf("unknown", PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)))
        assertEquals(listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), previewDevicePermissions(listOf(
            PermissionRequest.RESOURCE_VIDEO_CAPTURE, "unknown", PermissionRequest.RESOURCE_AUDIO_CAPTURE,
            PermissionRequest.RESOURCE_VIDEO_CAPTURE)))
    }
}
