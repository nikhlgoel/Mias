package dev.kid.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Mias Shape System — Modern, fluid, and organic.
 *
 * Inspired by cutting-edge AI interfaces with:
 * - Smooth, generous corner radii
 * - Asymmetric bubble shapes for chat
 * - Glassmorphism-friendly rounded rectangles
 * - Specialized shapes for different UI elements
 */
object KidShapes {
    // ── Corner Radii ──
    val None = RoundedCornerShape(0.dp)
    val XSmall = RoundedCornerShape(4.dp)
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(12.dp)
    val Large = RoundedCornerShape(16.dp)
    val XLarge = RoundedCornerShape(24.dp)
    val XXLarge = RoundedCornerShape(32.dp)
    val Full = RoundedCornerShape(50)

    // ── Component Shapes ──
    val Card = Large
    val Button = Large
    val InputField = Large
    val BottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Dialog = XLarge
    val Dropdown = Medium
    val Chip = Full

    // ── Message Bubble Shapes ──
    val BubbleUser = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = 20.dp,
        bottomEnd = 4.dp,
    )

    val BubbleKid = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = 4.dp,
        bottomEnd = 20.dp,
    )

    // ── Glass Effects ──
    val Glass = XLarge
    val GlassCard = RoundedCornerShape(20.dp)
    val GlassPanel = RoundedCornerShape(24.dp)

    // ── Specialized Shapes ──
    val Orb = Full
    val StatusPill = Full
    val IconButton = Full
    val Avatar = Full
    val Thumbnail = Medium
}
