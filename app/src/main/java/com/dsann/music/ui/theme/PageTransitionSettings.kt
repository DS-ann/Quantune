package com.dsann.music.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Shared page-transition duration provided by the Activity-scoped settings.
 * Nested navigation hosts use the same setting without threading another parameter
 * through every screen API.
 */
val LocalPageTransitionDurationMillis = staticCompositionLocalOf { 300 }
