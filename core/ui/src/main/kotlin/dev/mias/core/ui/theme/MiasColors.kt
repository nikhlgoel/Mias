package dev.mias.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Mias Color System — Modern dark theme with vibrant accents.
 *
 * Inspired by cutting-edge AI application interfaces with:
 * - Deep space blacks with subtle blue undertones
 * - Vibrant gradient accents for intelligence states
 * - Glassmorphism support with translucent surfaces
 * - Cognition state colors that pulse with energy
 */
object MiasColors {

    // ── Surface System (Deep Space) ──
    val Background = Color(0xFF050507)        // Nearly black with subtle blue
    val Surface = Color(0xFF0C0C12)         // Slightly lighter for elevation
    val SurfaceElevated = Color(0xFF14141E)   // Cards, dialogs
    val SurfaceOverlay = Color(0xFF1A1A28)    // Hover states
    val Card = SurfaceElevated
    val SurfaceGlass = Color(0x140A0F1F)    // 8% white for glass effect
    val SurfaceGlassStroke = Color(0x260A0F1F) // 15% white border
    val SurfaceDim = Color(0xFF030305)

    // ── Primary (Electric Blue) ──
    val Primary = Color(0xFF00D4FF)           // Electric cyan-blue
    val PrimaryLight = Color(0xFF33DDFF)
    val PrimaryDark = Color(0xFF0099CC)
    val PrimaryGlow = Color(0x4000D4FF)      // 25% for glow effects
    val PrimarySurface = Color(0x1A00D4FF)   // 10% primary on surface

    // ── Secondary (Vibrant Purple) ──
    val Secondary = Color(0xFF8B5CF6)
    val SecondaryLight = Color(0xFFA78BFA)
    val SecondaryDark = Color(0xFF6D28D9)
    val SecondaryGlow = Color(0x408B5CF6)

    // ── Cognition State Colors (Energy States) ──
    val CognitionIdle = Color(0xFF00D4FF)        // Electric blue — ready
    val CognitionThinking = Color(0xFFA78BFA)    // Purple — processing
    val CognitionActing = Color(0xFF10B981)       // Emerald — executing
    val CognitionOffloading = Color(0xFFF59E0B)  // Amber — desktop offload
    val CognitionStressed = Color(0xFFEF4444)     // Red — thermal stress
    val CognitionListening = Color(0xFF06B6D4)    // Cyan — voice active
    val CognitionSpeaking = Color(0xFFEC4899)     // Pink — voice output

    // ── Sentiment Accents ──
    val SentimentHappy = Color(0xFFFBBF24)
    val SentimentSad = Color(0xFF60A5FA)
    val SentimentExcited = Color(0xFFF472B6)
    val SentimentFrustrated = Color(0xFFF87171)
    val SentimentNeutral = Color(0xFF94A3B8)
    val SentimentCurious = Color(0xFF34D399)
    val SentimentInFlow = Color(0xFF818CF8)

    // ── Text ──
    val TextPrimary = Color(0xFFF8FAFC)        // Nearly white
    val TextSecondary = Color(0xFF94A3B8)      // Slate-400
    val TextTertiary = Color(0xFF64748B)        // Slate-500
    val TextOnPrimary = Color(0xFF050507)
    val TextAccent = Color(0xFF00D4FF)

    // ── Gradient Colors ──
    val GradientStart = Color(0xFF00D4FF)
    val GradientMid = Color(0xFF8B5CF6)
    val GradientEnd = Color(0xFFEC4899)

    // ── Thermal System ──
    val ThermalCool = Color(0xFF10B981)
    val ThermalWarm = Color(0xFFFBBF24)
    val ThermalHot = Color(0xFFF87171)
    val ThermalCritical = Color(0xFFDC2626)

    // ── Functional ──
    val Error = Color(0xFFEF4444)
    val ErrorRed = Error
    val Warning = Color(0xFFF59E0B)
    val Success = Color(0xFF10B981)
    val Info = Color(0xFF3B82F6)
    val NeonCyan = CognitionListening

    // ── Message Bubbles ──
    val BubbleUser = Color(0xFF1E3A5F)        // Deep blue bubble
    val BubbleKid = Color(0xFF14141E)         // Surface matching
    val BubbleThought = Color(0xFF1E1433)      // Purple tinted
    val BubbleAction = Color(0xFF142218)        // Green tinted
    val BubbleError = Color(0xFF1E1414)        // Red tinted

    // ── Glass Effects ──
    val GlassFill = Color(0x0DFFFFFF)          // 5% white
    val GlassBorder = Color(0x1AFFFFFF)        // 10% white
    val GlassHighlight = Color(0x26FFFFFF)       // 15% white
    val GlassShadow = Color(0x80000000)        // Shadow overlay
}
