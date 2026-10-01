package com.hikariatelier.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.ViewParent
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal data class SystemBarVisibility(val status: Boolean = true, val navigation: Boolean = true)
internal fun systemBarVisibility(isLandscape: Boolean, showStatusBar: Boolean, showNavigationBar: Boolean) =
    SystemBarVisibility(!isLandscape && showStatusBar, !isLandscape && showNavigationBar)

internal val LocalSystemBarVisibility = compositionLocalOf { SystemBarVisibility() }

@Suppress("DEPRECATION")
private fun Window.applySystemBarVisibility(visibility: SystemBarVisibility) {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    statusBarColor = android.graphics.Color.TRANSPARENT
    navigationBarColor = android.graphics.Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) isNavigationBarContrastEnforced = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        attributes = attributes.apply {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
    val controller = WindowCompat.getInsetsController(this, decorView)
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    val status = WindowInsetsCompat.Type.statusBars()
    val navigation = WindowInsetsCompat.Type.navigationBars()
    if (visibility.status) controller.show(status) else controller.hide(status)
    if (visibility.navigation) controller.show(navigation) else controller.hide(navigation)
}

@Composable
private fun MaintainWindowBars(window: Window?, visibility: SystemBarVisibility) {
    DisposableEffect(window, visibility) {
        if (window == null) onDispose {} else {
            val decor = window.decorView
            val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { focused ->
                if (focused) window.applySystemBarVisibility(visibility)
            }
            decor.viewTreeObserver.addOnWindowFocusChangeListener(listener)
            window.applySystemBarVisibility(visibility)
            // Modal sheets can update their window flags after their content is composed.
            val reapply = Runnable { window.applySystemBarVisibility(visibility) }
            decor.post(reapply)
            onDispose {
                decor.removeCallbacks(reapply)
                if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
            }
        }
    }
}

@Composable
internal fun KeepLandscapeDialogImmersive(forceFullscreen: Boolean = false) {
    val view = LocalView.current
    val visibility = if (forceFullscreen) SystemBarVisibility(false, false) else LocalSystemBarVisibility.current
    val window = remember(view) {
        var parent: ViewParent? = view.parent
        var found: Window? = null
        while (parent != null) {
            if (parent is DialogWindowProvider) { found = parent.window; break }
            parent = parent.parent
        }
        found ?: (view.context as? Activity)?.window
    }
    DisposableEffect(window) {
        val previousRate = window?.attributes?.preferredRefreshRate
        window?.preferHighRefreshRate()
        onDispose {
            if (window != null && previousRate != null)
                window.attributes = window.attributes.apply { preferredRefreshRate = previousRate }
        }
    }
    MaintainWindowBars(if (view.isInEditMode) null else window, visibility)
}

@Composable
internal fun EditorWindowEffects(isLandscape: Boolean, manualRotation: Boolean, showStatusBar: Boolean, showNavigationBar: Boolean) {
    val view = LocalView.current
    LaunchedEffect(view, manualRotation) {
        if (!view.isInEditMode) {
            (view.context as Activity).requestedOrientation = if (manualRotation) {
                if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    MaintainWindowBars(if (view.isInEditMode) null else (view.context as Activity).window,
        systemBarVisibility(isLandscape, showStatusBar, showNavigationBar))
}
