package com.dsann.music.ui.theme

import android.app.Activity
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateBottomPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Paints an opaque surface behind the platform navigation controls.
 *
 * Android 15+ keeps gesture navigation transparent regardless of
 * Window.navigationBarColor, so the actual protection must be drawn by app
 * content within the navigation-bar insets. Side insets are included for
 * landscape/tablet navigation layouts.
 */
@Composable
internal fun SolidSystemNavigationBarBackground() {
    val padding = WindowInsets.navigationBars.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val bottom = padding.calculateBottomPadding()
    val start = padding.calculateStartPadding(layoutDirection)
    val end = padding.calculateEndPadding(layoutDirection)
    val surfaceColor = DsmusicTheme.colorScheme.surface

    Box(modifier = Modifier.fillMaxSize()) {
        if (bottom > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(bottom)
                    .background(surfaceColor)
            )
        }
        if (start > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(start)
                    .background(surfaceColor)
            )
        }
        if (end > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(end)
                    .background(surfaceColor)
            )
        }
    }
}

/** Sets the platform navigation-bar colour for Android versions that still honour it. */
internal fun Activity.applySolidSystemNavigationBarColor() {
    val attributes = theme.obtainStyledAttributes(
        intArrayOf(
            com.google.android.material.R.attr.colorSurface,
            android.R.attr.colorBackground
        )
    )
    try {
        window.navigationBarColor = attributes.getColor(
            0,
            attributes.getColor(1, Color.BLACK)
        )
    } finally {
        attributes.recycle()
    }
}
