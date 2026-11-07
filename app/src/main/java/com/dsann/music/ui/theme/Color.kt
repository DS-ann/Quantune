package com.dsann.music.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// D.S Launcher-inspired palette shared with legacy Canvas and View UI.
val Primary = Color(0xFF7C9CFF)
val OnPrimary = Color(0xFF0B1220)
val PrimaryContainer = Color(0xFF263652)
val OnPrimaryContainer = Color(0xFFE6EDFF)

val Secondary = Color(0xFFB8C6DE)
val OnSecondary = Color(0xFF172131)
val SecondaryContainer = Color(0xFF27364A)
val OnSecondaryContainer = Color(0xFFDCE6F7)

val Tertiary = Color(0xFF8CC9D8)
val OnTertiary = Color(0xFF0B2228)
val TertiaryContainer = Color(0xFF23434C)
val OnTertiaryContainer = Color(0xFFD6F4FA)

val Error = Color(0xFFFF8C91)
val OnError = Color(0xFF2B080D)
val ErrorContainer = Color(0xFF5B2028)
val OnErrorContainer = Color(0xFFFFDADD)

val Outline = Color(0xFF65748A)
val OutlineVariant = Color(0xFF354255)

val Surface = Color(0xFF121620)
val OnSurface = Color(0xFFE9EDF5)
val SurfaceVariant = Color(0xFF263141)
val OnSurfaceVariant = Color(0xFFB7C3D5)

val SurfaceContainerLow = Color(0xFF151C26)
val SurfaceContainer = Color(0xFF1B2431)
val SurfaceContainerHigh = Color(0xFF222D3B)
val SurfaceContainerHighest = Color(0xFF2A3747)

// Standard Android ARGB values for XML, Canvas and legacy View compatibility.
val PrimaryArgb: Int get() = Primary.toArgb()
val OnPrimaryArgb: Int get() = OnPrimary.toArgb()
val PrimaryContainerArgb: Int get() = PrimaryContainer.toArgb()
val OnPrimaryContainerArgb: Int get() = OnPrimaryContainer.toArgb()

val SecondaryArgb: Int get() = Secondary.toArgb()
val OnSecondaryArgb: Int get() = OnSecondary.toArgb()
val SecondaryContainerArgb: Int get() = SecondaryContainer.toArgb()
val OnSecondaryContainerArgb: Int get() = OnSecondaryContainer.toArgb()

val TertiaryArgb: Int get() = Tertiary.toArgb()

val SurfaceArgb: Int get() = Surface.toArgb()
val OnSurfaceArgb: Int get() = OnSurface.toArgb()
val SurfaceVariantArgb: Int get() = SurfaceVariant.toArgb()
val OnSurfaceVariantArgb: Int get() = OnSurfaceVariant.toArgb()

val SurfaceContainerLowArgb: Int get() = SurfaceContainerLow.toArgb()
val SurfaceContainerArgb: Int get() = SurfaceContainer.toArgb()
val SurfaceContainerHighArgb: Int get() = SurfaceContainerHigh.toArgb()
val SurfaceContainerHighestArgb: Int get() = SurfaceContainerHighest.toArgb()
