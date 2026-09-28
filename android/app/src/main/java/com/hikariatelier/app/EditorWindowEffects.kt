package com.hikariatelier.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.ViewParent
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.*

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
internal fun KeepLandscapeDialogImmersive(
    enabled: Boolean = true
) {

    val localView =
        LocalView.current

    fun findDialogWindow(): Window? {
        var current: ViewParent? = localView.parent
        while (current != null) {
            if (current is DialogWindowProvider) {
                return current.window
            }
            current = current.parent
        }
        return (localView.context as? Activity)?.window
    }

    DisposableEffect(localView) {
        val window = findDialogWindow()
        val previousRate = window?.attributes?.preferredRefreshRate
        window?.preferHighRefreshRate()
        onDispose {
            if (window != null && previousRate != null) {
                window.attributes = window.attributes.apply { preferredRefreshRate = previousRate }
            }
        }
    }

    DisposableEffect(
        localView,
        enabled
    ) {

        val dialogWindow =
            if (
                enabled &&
                !localView.isInEditMode
            ) {
                findDialogWindow()
            } else {
                null
            }

        if (dialogWindow == null) {
            onDispose {}
        } else {

            @Suppress("DEPRECATION")
            fun applyImmersiveMode() {

                WindowCompat
                    .setDecorFitsSystemWindows(
                        dialogWindow,
                        false
                    )

                dialogWindow.navigationBarColor =
                    android.graphics.Color.TRANSPARENT

                dialogWindow.statusBarColor =
                    android.graphics.Color.TRANSPARENT

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dialogWindow.attributes = dialogWindow.attributes.apply {
                        layoutInDisplayCutoutMode =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                            } else {
                                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                            }
                    }
                }

                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q
                ) {
                    dialogWindow
                        .isNavigationBarContrastEnforced =
                        false
                }

                val controller =
                    WindowCompat
                        .getInsetsController(
                            dialogWindow,
                            dialogWindow.decorView
                        )

                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                controller.hide(
                    WindowInsetsCompat
                        .Type
                        .systemBars()
                )
            }

            val focusListener =
                android.view.ViewTreeObserver
                    .OnWindowFocusChangeListener {
                        hasFocus ->

                        if (hasFocus) {
                            applyImmersiveMode()
                        }
                    }

            val observer =
                dialogWindow.decorView
                    .viewTreeObserver

            observer.addOnWindowFocusChangeListener(
                focusListener
            )

            applyImmersiveMode()

            onDispose {
                val currentObserver =
                    dialogWindow.decorView
                        .viewTreeObserver

                if (currentObserver.isAlive) {
                    currentObserver
                        .removeOnWindowFocusChangeListener(
                            focusListener
                        )
                }
            }
        }
    }
}

@Composable
internal fun EditorWindowEffects(isLandscape: Boolean, manualRotation: Boolean, showStatusBar: Boolean) {
    val view = LocalView.current
LaunchedEffect(
    view,
    manualRotation
) {

    if (!view.isInEditMode) {

        val activity =
            view.context as Activity

        activity.requestedOrientation =
            if (manualRotation) {

                if (isLandscape) {
                    ActivityInfo
                        .SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    ActivityInfo
                        .SCREEN_ORIENTATION_PORTRAIT
                }

            } else {
                ActivityInfo
                    .SCREEN_ORIENTATION_UNSPECIFIED
            }
    }
}

LaunchedEffect(
    view,
    isLandscape,
    showStatusBar
) {

    @Suppress("DEPRECATION")
    if (!view.isInEditMode) {

        val window =
            (view.context as Activity).window

        window.navigationBarColor =
            android.graphics.Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {

            window.isNavigationBarContrastEnforced =
                false
        }

        val controller =
            WindowCompat.getInsetsController(
                window,
                view
            )

        controller.systemBarsBehavior =
            WindowInsetsControllerCompat
                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (isLandscape || !showStatusBar) {

            controller.hide(
                WindowInsetsCompat
                    .Type
                    .systemBars()
            )

        } else {

            controller.show(
                WindowInsetsCompat
                    .Type
                    .systemBars()
            )
        }
    }
}

DisposableEffect(
    view,
    isLandscape
) {

    if (
        view.isInEditMode ||
        !isLandscape
    ) {
        onDispose {}
    } else {

        val activityWindow =
            (view.context as Activity).window

        fun restoreLandscapeImmersive() {

            val controller =
                WindowCompat
                    .getInsetsController(
                        activityWindow,
                        activityWindow.decorView
                    )

            controller.systemBarsBehavior =
                WindowInsetsControllerCompat
                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            controller.hide(
                WindowInsetsCompat
                    .Type
                    .systemBars()
            )
        }

        val focusListener =
            android.view.ViewTreeObserver
                .OnWindowFocusChangeListener {
                    hasFocus ->

                    if (hasFocus) {
                        restoreLandscapeImmersive()
                    }
                }

        val observer =
            activityWindow.decorView
                .viewTreeObserver

        observer.addOnWindowFocusChangeListener(
            focusListener
        )

        restoreLandscapeImmersive()

        onDispose {
            val currentObserver =
                activityWindow.decorView
                    .viewTreeObserver

            if (currentObserver.isAlive) {
                currentObserver
                    .removeOnWindowFocusChangeListener(
                        focusListener
                    )
            }
        }
    }
}

}
