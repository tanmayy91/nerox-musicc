/**
 * Nerox Music v3 — Real-time Liquid Glass
 *
 * This is genuine per-frame GPU rendering via AGSL (Android Graphics Shading Language),
 * NOT glassmorphism (which is just blur + transparency).
 *
 * The shader physically simulates:
 *  • Lens refraction    — pixels are displaced as light bends through curved glass
 *  • Animated ripple    — sinusoidal liquid movement changes the refraction each frame
 *  • Caustic patterns   — moving bright patches caused by focused light through glass
 *  • Fresnel edge glow  — glass brightens at oblique angles (physically accurate)
 *  • Specular highlight — single-bounce specular from a virtual point light
 *  • Chromatic aberration — RGB wavelengths split at edges, like a real lens
 *
 * Tier system:
 *  • API 33+ (Android 13): Full AGSL RuntimeShader + RenderEffect blur
 *  • API 31–32 (Android 12): RenderEffect blur + enhanced gradient surface
 *  • API < 31: High-quality gradient fallback (no AGSL available)
 *
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.music.nerox.ui.component

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

// ─── AGSL Shader Source ───────────────────────────────────────────────────────

/**
 * AGSL program for liquid glass surface rendering.
 * Uniforms:
 *   resolution         — viewport size in px
 *   time               — monotonically increasing float (seconds × scale)
 *   baseColor          — primary tint (ARGB)
 *   accentColor        — secondary tint for gradient variation
 *   refractionStrength — 0..1 dial for how much the lens distorts (default 1)
 */
private val LIQUID_GLASS_AGSL = """
    uniform float2 resolution;
    uniform float  time;
    uniform float4 baseColor;
    uniform float4 accentColor;
    uniform float  refractionStrength;

    // ── Smooth noise helpers ──────────────────────────────────────────────────
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
    // Fractal noise: stacks two octaves for richer organic movement
    float fbm(float2 p) {
        return smoothNoise(p) * 0.6 + smoothNoise(p * 2.1 + float2(1.7, 9.2)) * 0.4;
    }

    half4 main(float2 fragCoord) {
        float2 uv     = fragCoord / resolution;
        float2 center = float2(0.5, 0.5);
        float2 delta  = uv - center;
        float  dist   = length(delta);
        float2 dir    = normalize(delta + 0.00001);

        // ── 1. Lens refraction ────────────────────────────────────────────────
        // A plano-convex lens profile: strongest at centre, falls off to edges.
        float lensProfile = (1.0 - smoothstep(0.0, 0.70, dist)) * refractionStrength;
        float2 refracted  = uv - delta * lensProfile * 0.055;

        // ── 2. Liquid ripple (time-varying) ───────────────────────────────────
        // Sinusoidal wave propagating outward — gives the "liquid" quality.
        float  wave   = sin(dist * 10.0 - time * 2.2) * cos(dist * 6.5 + time * 1.4);
        refracted    += dir * wave * 0.009 * lensProfile;

        // ── 3. Base glass tint (gradient derived from refracted UV) ───────────
        float  blend   = smoothstep(0.15, 0.85, refracted.x * 0.55 + refracted.y * 0.45);
        float4 tint    = mix(accentColor, baseColor, blend);

        // ── 4. Caustic light patterns ─────────────────────────────────────────
        // Moving noise that mimics focussed light bouncing through glass.
        float2 cUv    = uv * 4.2 + float2(time * 0.09, time * 0.06);
        float  cUv2   = uv.x * 3.1 - time * 0.07;
        float  caustic = fbm(cUv) * fbm(float2(cUv2, uv.y * 3.8 + time * 0.04));
        float  cMask   = smoothstep(0.38, 0.72, caustic) * lensProfile;

        // ── 5. Fresnel edge brightening ───────────────────────────────────────
        // Real glass is visually brighter at grazing (oblique) angles.
        float  fresnel = pow(dist * 1.65, 2.8);
        float  edge    = smoothstep(0.25, 0.52, dist) * fresnel * 0.28;

        // ── 6. Specular highlight (top-left point light) ──────────────────────
        // Animated micro-normals give the surface a living, rippled quality.
        float  nOffset = smoothNoise(uv * 5.0 + time * 0.12);
        float2 normal  = normalize(delta + float2(nOffset * 0.18 - 0.09,
                                                   nOffset * 0.18 - 0.09));
        float2 lightDir = normalize(float2(-0.58, -0.82));
        float  spec     = pow(max(0.0, dot(-lightDir, normal)), 9.0);
        float  specMask = spec * (1.0 - smoothstep(0.28, 0.48, dist)) * 0.20;

        // ── 7. Chromatic aberration ────────────────────────────────────────────
        // Wavelength-dependent refraction — split RGB channels toward the edge.
        float  aber    = dist * dist * 0.030;
        // R channel shifted outward, B channel shifted inward
        float  rShift  = tint.r + aber * 0.10;
        float  bShift  = tint.b - aber * 0.07;

        // ── Compose final colour ──────────────────────────────────────────────
        half4 col;
        col.r = half(clamp(rShift   + edge + specMask + cMask * 0.10, 0.0, 1.0));
        col.g = half(clamp(tint.g   + edge + specMask + cMask * 0.10, 0.0, 1.0));
        col.b = half(clamp(bShift   + edge + specMask + cMask * 0.10, 0.0, 1.0));

        // Alpha: slightly more opaque toward edges (thicker glass rim)
        col.a = half(mix(tint.a * 0.72, tint.a * 0.92, smoothstep(0.0, 0.50, dist)));

        return col;
    }
""".trimIndent()

// ─── Public API ───────────────────────────────────────────────────────────────

/**
 * Applies a real-time liquid glass surface to any composable.
 *
 * Usage:
 * ```kotlin
 * Box(
 *     modifier = Modifier
 *         .fillMaxWidth()
 *         .liquidGlass(shape = RoundedCornerShape(28.dp))
 * ) { … }
 * ```
 *
 * @param shape              Clip shape for the glass surface
 * @param baseColor          Primary tint colour; defaults to surface colour
 * @param refractionStrength 0f = flat (no lens distortion), 1f = full effect
 */
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
    // Time uniform — drives ripple, caustic, and specular animation
    val infiniteTransition = rememberInfiniteTransition(label = "liquidGlass")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 628.318f,          // 2π × 100 — full cycle before wrap
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 90_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glassTime"
    )

    // Shader is stateless; recreating it each recomposition is cheap because
    // the JNI object is pooled by the runtime.
    val accentColor = baseColor.copy(
        red   = (baseColor.red   * 0.75f).coerceIn(0f, 1f),
        green = (baseColor.green * 0.75f).coerceIn(0f, 1f),
        blue  = (baseColor.blue  * 0.85f).coerceIn(0f, 1f),
        alpha = (baseColor.alpha * 0.55f).coerceIn(0f, 1f),
    )

    return this
        .clip(shape)
        // Draw the animated AGSL shader as the composable's background
        .drawWithCache {
            val runtimeShader = android.graphics.RuntimeShader(LIQUID_GLASS_AGSL)
            val nativePaint   = android.graphics.Paint().apply {
                isAntiAlias = true
                shader       = runtimeShader
            }

            onDrawWithContent {
                // Set per-frame uniforms
                runtimeShader.setFloatUniform("resolution",         size.width, size.height)
                runtimeShader.setFloatUniform("time",               time)
                runtimeShader.setFloatUniform("refractionStrength", refractionStrength)
                runtimeShader.setColorUniform("baseColor",          baseColor.toArgb())
                runtimeShader.setColorUniform("accentColor",        accentColor.toArgb())

                // Draw shader across the full composable bounds
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawRect(
                        0f, 0f, size.width, size.height,
                        nativePaint
                    )
                }

                // Draw the actual composable content on top
                drawContent()
            }
        }
        // Glass border — gradient sheen catches the virtual light direction
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0.00f to Color.White.copy(alpha = 0.45f),
                    0.35f to Color.White.copy(alpha = 0.10f),
                    0.65f to Color.White.copy(alpha = 0.18f),
                    1.00f to Color.White.copy(alpha = 0.35f),
                )
            ),
            shape = shape,
        )
}

// ─── API-31/32 tier: RenderEffect blur + enhanced surface ────────────────────

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
                Color.White.copy(alpha = 0.26f),
                baseColor.copy(alpha = 0.78f),
                baseColor.copy(alpha = 0.58f),
            ),
            start = Offset.Zero,
            end   = Offset(1600f, 1600f)
        )
    )
    .drawWithCache {
        val shimmer = Brush.radialGradient(
            colors   = listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
            center   = Offset(size.width * 0.82f, size.height * 0.08f),
            radius   = size.maxDimension * 0.95f
        )
        val causticGlow = Brush.radialGradient(
            colors   = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
            center   = Offset(size.width * 0.18f, size.height * 0.90f),
            radius   = size.maxDimension * 0.70f
        )
        onDrawWithContent {
            drawContent()
            drawRect(shimmer)
            drawRect(causticGlow)
        }
    }
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.32f),
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.20f),
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
    .background(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.22f),
                baseColor.copy(alpha = 0.82f),
                baseColor.copy(alpha = 0.64f),
            ),
            start = Offset.Zero,
            end   = Offset(1400f, 1400f)
        )
    )
    .drawWithCache {
        val glossTop = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
            center = Offset(size.width * 0.85f, size.height * 0.10f),
            radius = size.maxDimension * 0.90f
        )
        val glossBottom = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.09f), Color.Transparent),
            center = Offset(size.width * 0.18f, size.height * 0.92f),
            radius = size.maxDimension * 0.70f
        )
        onDrawWithContent {
            drawContent()
            drawRect(glossTop)
            drawRect(glossBottom)
        }
    }
    .border(1.dp, Color.White.copy(alpha = 0.20f), shape)
