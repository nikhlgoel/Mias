package dev.kid.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Mias Color System — dark-first with cognition state accents.
 *
 * The palette draws from deep space blacks with blue-shifted accents
 * for the primary "intelligence" color. State colors communicate
 * what Kid is doing without words.
 */
object KidColors {

    // ── Surface System ──
    val Background = Color(0xFF0A0A0F)
    val Surface = Color(0xFF12121A)
    val SurfaceElevated = Color(0xFF1A1A26)
    val Card = SurfaceElevated
    val SurfaceGlass = Color(0x33FFFFFF) // 20% white for glass
    val SurfaceGlassStroke = Color(0x1AFFFFFF) // 10% white border
    val SurfaceDim = Color(0xFF08080C)

    // ── Primary (Intelligence Blue) ──
    val Primary = Color(0xFF6C8EFF)
    val PrimaryLight = Color(0xFF8EAAFF)
    val PrimaryDark = Color(0xFF4A6ADB)
    val PrimaryGlow = Color(0x4D6C8EFF) // 30% for glow effects

    // ── Cognition State Colors ──
    val CognitionIdle = Color(0xFF6C8EFF)       // Blue — waiting
    val CognitionThinking = Color(0xFFBA8CFF)    // Purple — processing
    val CognitionActing = Color(0xFF4ADE80)      // Green — executing
    val CognitionOffloading = Color(0xFFFFD166)  // Gold — sending to desktop
    val CognitionStressed = Color(0xFFFF6B6B)    // Red — thermal/resource pressure
    val CognitionListening = Color(0xFF22D3EE)   // Cyan — voice active

    // ── Sentiment Accents ──
    val SentimentHappy = Color(0xFFFBBF24)
    val SentimentSad = Color(0xFF60A5FA)
    val SentimentExcited = Color(0xFFF472B6)
    val SentimentFrustrated = Color(0xFFF87171)
    val SentimentNeutral = Color(0xFF94A3B8)
    val SentimentCurious = Color(0xFF34D399)
    val SentimentInFlow = Color(0xFF818CF8)

    // ── Text ──
    val TextPrimary = Color(0xFFF0F0F5)
    val TextSecondary = Color(0xFFA0A0B8)
    val TextTertiary = Color(0xFF6B7280)
    val TextOnPrimary = Color(0xFF0A0A0F)
    val TextAccent = Color(0xFF6C8EFF)

    // ── Thermal System ──
    val ThermalCool = Color(0xFF4ADE80)
    val ThermalWarm = Color(0xFFFBBF24)
    val ThermalHot = Color(0xFFFF6B6B)
    val ThermalCritical = Color(0xFFDC2626)

    // ── Functional ──
    val Error = Color(0xFFEF4444)
    val ErrorRed = Error
    val Warning = Color(0xFFF59E0B)
    val Success = Color(0xFF10B981)
    val Info = Color(0xFF3B82F6)
    val NeonCyan = CognitionListening

    // ── Message Bubbles ──
    val BubbleUser = Color(0xFF1E3A5F)
    val BubbleKid = Color(0xFF1A1A26)
    val BubbleThought = Color(0xFF2A1F3D)
    val BubbleAction = Color(0xFF1F2D1F)
    val BubbleError = Color(0xFF2D1F1F)

    // ── Glass Effects ──
    val GlassFill = Color(0x1AFFFFFF)  // 10%
    val GlassBorder = Color(0x33FFFFFF)  // 20%
    val GlassHighlight = Color(0x4DFFFFFF)  // 30%
}
