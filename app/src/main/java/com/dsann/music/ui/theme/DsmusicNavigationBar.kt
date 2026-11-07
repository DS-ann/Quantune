package com.dsann.music.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DsmusicNavigationDestination(
    val route: String,
    val label: String,
    val iconResId: Int
)

/**
 * Lightweight bottom navigation dock. The system gesture/navigation inset is
 * reserved as a separate bottom region, so it cannot reduce the actual icon/label
 * viewport and clip the dock on compact phone layouts.
 */
@Composable
fun DsmusicNavigationBar(
    destinations: List<DsmusicNavigationDestination>,
    currentRoute: String?,
    labelsMode: String,
    iconSize: Dp,
    bottomInset: Dp,
    barStyle: String = "Floating",
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit
) {
    val surface = DsmusicTheme.colorScheme.surfaceContainer
    val selectedColor = DsmusicTheme.colorScheme.primary
    val selectedContainer = DsmusicTheme.colorScheme.primaryContainer
    val selectedIconOnContainer = DsmusicTheme.colorScheme.onPrimaryContainer
    val inactiveColor = DsmusicTheme.colorScheme.onSurfaceVariant

    val dockShape = when (barStyle) {
        "Docked" -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
        "Minimal" -> RoundedCornerShape(0.dp)
        else -> RoundedCornerShape(26.dp)
    }
    val isFloating = barStyle == "Floating"
    val horizontalInset = if (isFloating) 14.dp else 0.dp
    val verticalInset = if (isFloating) 4.dp else 0.dp
    // Use a true black/white hairline independent of the active accent or album colour.
    val borderBase = if (surface.luminance() > 0.5f) Color.Black else Color.White
    val borderColor = borderBase.copy(alpha = if (isFloating) 0.78f else 0.56f)
    val iconSlotHeight = when {
        iconSize.value <= 22f -> 30.dp
        iconSize.value >= 30f -> 34.dp
        else -> 34.dp
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(
                    start = horizontalInset,
                    top = verticalInset,
                    end = horizontalInset,
                    bottom = verticalInset
                )
                .then(
                    if (isFloating) Modifier.shadow(12.dp, dockShape, clip = false)
                    else Modifier
                )
                .background(
                    color = if (barStyle == "Minimal") Color.Transparent else surface,
                    shape = dockShape
                )
                .border(
                    width = if (isFloating) 0.9.dp else 0.75.dp,
                    color = borderColor,
                    shape = dockShape
                )
                // Keep the icon/label content inside the exact same rounded boundary
                // used to draw the dock surface and outline.
                .clip(dockShape)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEach { destination ->
                val selected = currentRoute == destination.route
                val tint = if (selected) selectedColor else inactiveColor
                val iconTint = if (selected && barStyle != "Minimal") selectedIconOnContainer else tint
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onNavigate(destination.route) }
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 52.dp, height = iconSlotHeight)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected && barStyle != "Minimal") selectedContainer
                                else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(destination.iconResId),
                            contentDescription = destination.label,
                            colorFilter = ColorFilter.tint(iconTint),
                            modifier = Modifier.size(iconSize)
                        )
                    }
                    if (labelsMode == "Always" || (labelsMode == "Selected" && selected)) {
                        BasicText(
                            text = destination.label,
                            style = TextStyle(
                                color = tint,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                textAlign = TextAlign.Center
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
        // Reserve the full-width system inset below the outlined dock without painting a second surface.
        if (bottomInset > 0.dp) {
            androidx.compose.foundation.layout.Spacer(
                Modifier.fillMaxWidth().height(bottomInset)
            )
        }
    }
}
