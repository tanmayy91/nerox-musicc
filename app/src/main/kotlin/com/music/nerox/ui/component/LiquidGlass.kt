/**
 * vivimusic Project (C) 2026
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(20.dp),
    baseColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
): Modifier {
    return this
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.24f),
                    baseColor.copy(alpha = 0.82f),
                    baseColor.copy(alpha = 0.64f)
                ),
                start = Offset.Zero,
                end = Offset(1400f, 1400f)
            )
        )
        .drawWithCache {
            val glossTop = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(size.width * 0.85f, size.height * 0.1f),
                radius = size.maxDimension * 0.9f
            )
            val glossBottom = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.09f), Color.Transparent),
                center = Offset(size.width * 0.2f, size.height * 0.92f),
                radius = size.maxDimension * 0.7f
            )
            onDrawWithContent {
                drawContent()
                drawRect(glossTop)
                drawRect(glossBottom)
            }
        }
        .border(1.dp, Color.White.copy(alpha = 0.2f), shape)
}
