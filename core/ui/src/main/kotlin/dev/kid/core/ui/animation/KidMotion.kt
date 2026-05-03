package dev.kid.core.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * KidMotion — unified animation vocabulary for the entire app.
 *
 * Every motion in Kid should come from this file so the UI
 * feels consistent — like a living, breathing system.
 */
object KidMotion {

    // ── Durations ────────────────────────────────────────────────────────────
    const val FAST = 150
    const val NORMAL = 350
    const val SLOW = 600
    const val VERY_SLOW = 1000

    // ── Springs ──────────────────────────────────────────────────────────────

    /** Snappy response for interactive elements. */
    val snapSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    /** Smooth transition for state changes. */
    val smoothSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Orb pulse spring — gentle, organic feel. */
    val orbSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessVeryLow,
    )

    // ── Tweens ───────────────────────────────────────────────────────────────

    /** Standard fade in/out. */
    val fadeSpec: AnimationSpec<Float> = tween(durationMillis = NORMAL, easing = FastOutSlowInEasing)

    /** Thinking pulse — used for ThinkingDots and processing indicators. */
    val pulseSpec: AnimationSpec<Float> = infiniteRepeatable(
        animation = keyframes {
            durationMillis = 1200
            0f at 0 using LinearEasing
            1f at 400 using LinearEasing
            0f at 800 using LinearEasing
            0f at 1200
        },
        repeatMode = RepeatMode.Restart,
    )

    /** Slow breath cycle — used for idle orb. */
    val breatheSpec: AnimationSpec<Float> = infiniteRepeatable(
        animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse,
    )

    /** Fast action flash — used for tool execution indicator. */
    val flashSpec: AnimationSpec<Float> = tween(durationMillis = FAST, easing = LinearEasing)

    // ── Helper ────────────────────────────────────────────────────────────────

    /** Create an Animatable that starts pre-animated. */
    fun animatable(initial: Float = 0f) = Animatable(initial)
}
