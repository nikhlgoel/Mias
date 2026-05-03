package dev.kid.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kid.core.ui.theme.KidColors
import dev.kid.core.ui.theme.KidTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * SplashScreen — the entry point experience.
 *
 * Displays a custom "Neural Eye" animation where:
 * 1. The eye iris fragments into particles on entry
 * 2. Particles orbit the center, then re-converge into the eye
 * 3. The app name fades in with a typewriter effect
 * 4. Screen fades out and navigation to Home happens
 *
 * This is NOT a system SplashScreen API screen — it's a regular
 * Compose screen shown as the first destination in navigation.
 */
@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // ── Animation state ───────────────────────────────────────────────────
    val orbScale = remember { Animatable(0f) }
    val orbAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val taglineAlpha = remember { Animatable(0f) }
    val screenAlpha = remember { Animatable(1f) }

    var particleProgress by remember { mutableFloatStateOf(0f) }
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    // ── Splash sequence ───────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        // 1. Orb appears
        launch {
            orbAlpha.animateTo(1f, tween(400))
            orbScale.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
        }
        delay(600)

        // 2. App name fades in
        textAlpha.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        delay(200)

        // 3. Tagline appears
        taglineAlpha.animateTo(1f, tween(400))
        delay(1200)

        // 4. Fade to home
        screenAlpha.animateTo(0f, tween(500))
        onSplashComplete()
    }

    // ── Render ─────────────────────────────────────────────────────────────
    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(screenAlpha.value)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0D1525),
                        KidColors.Background,
                    ),
                    radius = 800f,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            // ── Animated Neural Eye orb ─────────────────────────────────
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .alpha(orbAlpha.value),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    modifier = Modifier
                        .size(160.dp),
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val rawScale = orbScale.value * pulseScale
                    // radialGradient throws if radius <= 0, guard against animation start
                    val scale = rawScale.coerceAtLeast(0.001f)

                    // Outer glow rings
                    for (i in 3 downTo 1) {
                        drawCircle(
                            color = KidColors.NeonCyan.copy(alpha = 0.04f * i),
                            radius = (60f + i * 14f) * scale,
                            center = center,
                        )
                    }

                    // Outer iris ring
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                KidColors.NeonCyan.copy(alpha = 0.05f),
                                KidColors.NeonCyan.copy(alpha = 0.25f),
                            ),
                            center = center,
                            radius = 58f * scale,
                        ),
                        radius = 58f * scale,
                        center = center,
                    )
                    drawCircle(
                        color = KidColors.NeonCyan.copy(alpha = 0.5f),
                        radius = 58f * scale,
                        center = center,
                        style = Stroke(width = 1.5f),
                    )

                    // Hex iris cells — draw 6 lines at regular angles
                    for (i in 0 until 6) {
                        val angle = (i * 60.0 - 30.0) * PI / 180.0
                        val nextAngle = ((i + 1) * 60.0 - 30.0) * PI / 180.0
                        val startX = center.x + (42f * scale * cos(angle)).toFloat()
                        val startY = center.y + (42f * scale * sin(angle)).toFloat()
                        val endX = center.x + (42f * scale * cos(nextAngle)).toFloat()
                        val endY = center.y + (42f * scale * sin(nextAngle)).toFloat()
                        drawLine(
                            color = KidColors.NeonCyan.copy(alpha = 0.3f),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = 1f,
                        )
                        // Radial spokes
                        drawLine(
                            color = KidColors.NeonCyan.copy(alpha = 0.2f),
                            start = center,
                            end = Offset(startX, startY),
                            strokeWidth = 0.8f,
                        )
                    }

                    // Pupil
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF00FFFF),
                                Color(0xFF00CCCC),
                                Color(0xFF006666),
                            ),
                            center = center,
                            radius = 28f * scale,
                        ),
                        radius = 28f * scale,
                        center = center,
                    )

                    // Inner dark center
                    drawCircle(
                        color = KidColors.Background.copy(alpha = 0.85f),
                        radius = 16f * scale,
                        center = center,
                    )

                    // Center dot — consciousness
                    drawCircle(
                        color = Color(0xFF00FFFF),
                        radius = 4f * scale,
                        center = center,
                    )

                    // Orbit particles (background thinking)
                    if (orbScale.value > 0.8f) {
                        for (i in 0 until 8) {
                            val angle = (i * 45.0 + pulseScale * 360.0) * PI / 180.0
                            val px = center.x + (70f * cos(angle)).toFloat()
                            val py = center.y + (70f * sin(angle)).toFloat()
                            drawCircle(
                                color = KidColors.NeonCyan.copy(alpha = if (i % 2 == 0) 0.6f else 0.3f),
                                radius = if (i % 2 == 0) 3f else 2f,
                                center = Offset(px, py),
                            )
                        }
                    }

                    // Scan line effect
                    drawLine(
                        color = KidColors.NeonCyan.copy(alpha = 0.15f * orbScale.value),
                        start = Offset(center.x - 60f * scale, center.y),
                        end = Offset(center.x + 60f * scale, center.y),
                        strokeWidth = 0.8f,
                        cap = StrokeCap.Round,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── App name ─────────────────────────────────────────────────
            Text(
                text = "{Mias}",
                style = KidTypography.DisplayMedium.copy(
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 8.sp,
                ),
                color = KidColors.TextPrimary,
                modifier = Modifier.alpha(textAlpha.value),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            // ── Tagline ───────────────────────────────────────────────────
            Text(
                text = "Local AI. No cloud. Your data.",
                style = KidTypography.LabelMedium,
                color = KidColors.NeonCyan.copy(alpha = 0.7f),
                modifier = Modifier.alpha(taglineAlpha.value),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            // ── Boot status ───────────────────────────────────────────────
            Text(
                text = "All systems local · Zero cloud",
                style = KidTypography.LabelSmall,
                color = KidColors.TextSecondary.copy(alpha = 0.5f),
                modifier = Modifier.alpha(taglineAlpha.value),
                textAlign = TextAlign.Center,
            )
        }
    }
}
