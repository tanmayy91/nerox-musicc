/**
 * Nerox Music v3 — Real-time Liquid Glass
 *
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.music.nerox.ui.component

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp

// ─── Fixed AGSL Shader Source ─────────────────────────────────────────────────

private val LIQUID_GLASS_AGSL = """
    uniform float2 resolution;
    uniform float  time;
    uniform float4 baseColor;
    uniform float  refractionStrength;

    // Smooth noise helper for fluid movement
    float hash(float2 p) {
        return fract(sin(dot(p, float2(127.1031, 311.7137))) * 43758.5453123);
    }
    float smoothNoise(float2 p) {
        float2 i = floor(p);
        float2 f = fract(p);
        float2 u = f * f * (3.0 - 2.0 * f);
        return mix(
            mix(hash(i + float2(0,0)), hash(i + float2(1,0)), u.x),
            mix(hash(i + float2(0,1)), hash(i + float2(1,1)), u.x),
            u.y
        );
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution;

        // 1. Fluid, continuous wave displacement across coordinates (No center pinch point)
        float wave1 = sin(uv.x * 6.0 + time * 1.2) * cos(uv.y * 6.0 + time * 0.8);
        float wave2 = cos(uv.x * 12.0 - time * 1.5) * sin(uv.y * 10.0 + time * 1.1);
        float noise = smoothNoise(uv * 4.0 + time * 0.2);

        float totalRipple = (wave1 + wave2 * 0.5 + noise * 0.3) * 0.012 * refractionStrength;

        // 2. Uniform, subtle glass refraction tint
        float blend = smoothstep(0.0, 1.0, uv.y + totalRipple);
        float4 tinted = mix(baseColor * 0.9, baseColor * 1.1, blend);

        // 3. Ultra-soft rim sheen (No dark vignetting or glaring white streaks)
        float edgeDist = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
        float rimGlow = (1.0 - smoothstep(0.0, 0.08, edgeDist)) * 0.08;

        half4 col;
        col.r = half(clamp(tinted.r + rimGlow, 0.0, 1.0));
        col.g = half(clamp(tinted.g + rimGlow, 0.0, 1.0));
        col.b = half(clamp(tinted.b + rimGlow, 0.0, 1.0));
        col.a = half(baseColor.a);

        return col;
    }
""".trimIndent()

// ─── Public API ───────────────────────────────────────────────────────────────

@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    baseColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    refractionStrength: Float = 1f,
): Modifier = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        liquidGlassAGSL(shape, baseColor, refractionStrength)

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        liquidGlassRenderEffect(shape, baseColor)

    else ->
        liquidGlassFallback(shape, baseColor)
}

// ─── API-33 tier: full AGSL liquid glass ─────────────────────────────────────

@Composable
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Modifier.liquidGlassAGSL(
    shape: Shape,
    baseColor: Color,
    refractionStrength: Float,
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "liquidGlass")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 628.318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glassTime"
    )

    // Re-use native allocations to preserve performance
    val runtimeShader = remember { RuntimeShader(LIQUID_GLASS_AGSL) }
    val nativePaint = remember { Paint().apply { isAntiAlias = true } }

    return this
        .graphicsLayer {
            this.shape = shape
            this.clip = true
        }
        .drawWithCache {
            nativePaint.shader = runtimeShader

            onDrawBehind {
                runtimeShader.setFloatUniform("resolution", size.width, size.height)
                runtimeShader.setFloatUniform("time", time)
                runtimeShader.setFloatUniform("refractionStrength", refractionStrength)
                runtimeShader.setFloatUniform(
                    "baseColor",
                    baseColor.red,
                    baseColor.green,
                    baseColor.blue,
                    baseColor.alpha
                )

                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawRect(
                        0f, 0f, size.width, size.height,
                        nativePaint
                    )
                }
            }
        }
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.20f),
                    Color.White.copy(alpha = 0.05f),
                )
            ),
            shape = shape,
        )
}

// ─── API-31/32 tier: Enhanced surface ─────────────────────────────────────────

@Composable
@RequiresApi(Build.VERSION_CODES.S)
private fun Modifier.liquidGlassRenderEffect(
    shape: Shape,
    baseColor: Color,
): Modifier = this
    .clip(shape)
    .background(
        brush = Brush.linearGradient(
            colors = listOf(
                baseColor.copy(alpha = 0.85f),
                baseColor.copy(alpha = 0.65f),
            ),
            start = Offset.Zero,
            end = Offset(1600f, 1600f)
        )
    )
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.20f),
                Color.White.copy(alpha = 0.05f),
            )
        ),
        shape = shape,
    )

// ─── Fallback tier: polished gradient glass ───────────────────────────────────

@Composable
private fun Modifier.liquidGlassFallback(
    shape: Shape,
    baseColor: Color,
): Modifier = this
    .clip(shape)
    .background(baseColor.copy(alpha = 0.75f))
    .border(
        width = 1.dp,
        color = Color.White.copy(alpha = 0.15f),
        shape = shape
    )
    
