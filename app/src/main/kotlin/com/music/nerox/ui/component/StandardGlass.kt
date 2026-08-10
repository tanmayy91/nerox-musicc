/**
 * Nerox Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.music.nerox.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Provides the app's standard glass surface.
 *
 * This is intentionally opaque and static: there are no shaders, blur effects,
 * animated gradients, or translucent layers. Keeping the surface solid makes
 * it consistent across Android versions and avoids graphics-driver-specific
 * rendering and allocation issues.
 */
@Composable
fun Modifier.standardGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    baseColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
): Modifier = this
    .clip(shape)
    .background(baseColor)
    .border(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        shape = shape,
    )