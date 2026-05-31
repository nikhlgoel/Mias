package dev.mias.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Mias Color System — Warm Dim + Heather.
 *
 * "A quiet evening, not a black void." Surfaces are a five-tone ladder of
 * warm brown-charcoal (hue ~55°, very low chroma). The single accent is
 * Heather — a dusty mauve that reads intimate and clearly its own (not a
 * Claude amber clone). Success and error stay palette-aware (muted, never
 * raw RGB green/red).
 *
 * Source of truth: `Color System v1.html` design handoff. Heather and the
 * surface ladder are locked; deviations should be deliberate.
 *
 * Field names are kept stable so older composables that referenced
 * `MiasColors.Primary` etc. don't need wholesale rewrites — only the
 * values changed.
 */
object MiasColors {

    // ── Surface ladder (warm brown-charcoal, hue ~55°) ──
    val Surface0 = Color(0xFF1A1612)            // Scrim, system bars
    val Surface1 = Color(0xFF231D18)            // App background
    val Surface2 = Color(0xFF2C2520)            // Cards, list items, inactive composer
    val Surface3 = Color(0xFF372F29)            // Assistant bubbles, chips, focused field
    val Surface4 = Color(0xFF463C34)            // Modal sheets, model picker

    // Stable aliases for older call sites.
    val Background = Surface1
    val Surface = Surface2
    val SurfaceElevated = Surface3
    val SurfaceOverlay = Surface4
    val SurfaceDim = Surface0
    val Card = Surface3

    // ── Outlines ──
    val OutlineSoft = Color(0xFF4A3F38)         // Hairline dividers
    val OutlineStrong = Color(0xFF6B5C50)       // Focus rings, strong dividers

    // ── Text ──
    val TextHi = Color(0xFFEFE5D3)              // Body, titles
    val TextLo = Color(0xFFA99A86)              // Labels, captions, secondary icons
    val TextMuted = Color(0xFF7A6D5E)           // Tertiary — timestamps, placeholders

    // Stable aliases.
    val TextPrimary = TextHi
    val TextSecondary = TextLo
    val TextTertiary = TextMuted

    // ── Accent: Heather (dusty mauve, OKLCH 0.74 .065 320) ──
    val Heather = Color(0xFFC5A8C9)
    val HeatherInk = Color(0xFF2A1F2B)           // Ink on Heather surfaces
    val HeatherDim = Color(0xFF6B5870)           // Pressed / disabled accent
    val HeatherContainer = Color(0xFF3A2E3D)     // Low-emphasis accent fills

    // Stable aliases.
    val Primary = Heather
    val PrimaryDark = HeatherDim
    val PrimaryLight = Heather                   // No separate light tone yet
    val PrimaryGlow = Color(0x40C5A8C9)           // 25% Heather for soft glows
    val PrimarySurface = HeatherContainer
    val TextOnPrimary = HeatherInk
    val TextAccent = Heather

    // ── Secondary surfaces ──
    // Use HeatherContainer as the secondary fill — no second accent yet.
    val Secondary = HeatherContainer
    val SecondaryLight = HeatherDim
    val SecondaryDark = HeatherInk
    val SecondaryGlow = Color(0x40C5A8C9)

    // ── Semantic ──
    val SuccessTone = Color(0xFFA4D2A9)         // Model loaded, download complete
    val SuccessInk = Color(0xFF1E2A21)
    val SuccessContainer = Color(0xFF24382A)
    val ErrorTone = Color(0xFFE08574)           // OOM, failed generation
    val ErrorInk = Color(0xFF2A1612)
    val ErrorContainer = Color(0xFF5C2A20)

    // Stable aliases.
    val Success = SuccessTone
    val Error = ErrorTone
    val ErrorRed = ErrorTone
    val Warning = Color(0xFFD4B68F)              // Warm cream-honey — palette-aware
    val Info = Heather

    // ── Cognition states (orb + processing indicators) ──
    // Palette-aware; no raw RGB greens/reds.
    val CognitionIdle = Heather
    val CognitionThinking = Heather
    val CognitionActing = SuccessTone
    val CognitionOffloading = Color(0xFFD4B68F)  // Warm honey for offload
    val CognitionStressed = ErrorTone
    val CognitionListening = Heather
    val CognitionSpeaking = Color(0xFFE2C2DA)     // Lighter heather variant

    // ── Sentiment (legacy — kept palette-aware for any lingering callers) ──
    val SentimentHappy = Color(0xFFD4B68F)
    val SentimentSad = HeatherDim
    val SentimentExcited = Color(0xFFE2C2DA)
    val SentimentFrustrated = ErrorTone
    val SentimentNeutral = TextLo
    val SentimentCurious = SuccessTone
    val SentimentInFlow = Heather

    // ── Thermal indicators ──
    val ThermalCool = SuccessTone
    val ThermalWarm = Color(0xFFD4B68F)
    val ThermalHot = ErrorTone
    val ThermalCritical = Color(0xFFB3624E)     // Deeper error

    // ── Gradient anchors (used by orb / splash) ──
    val GradientStart = Heather
    val GradientMid = HeatherContainer
    val GradientEnd = SuccessTone

    // ── Glass effects (palette-aware translucents) ──
    // Old glass used white alpha; replace with warm-cream alpha so the
    // glass tint stays inside the palette family.
    val GlassFill = Color(0x0DEFE5D3)            // 5% warm cream
    val GlassBorder = Color(0x804A3F38)          // 50% outline-soft
    val GlassHighlight = Color(0x26EFE5D3)        // 15% warm cream
    val GlassShadow = Color(0x801A1612)         // 50% surface-0
    val SurfaceGlass = Color(0x14C5A8C9)         // 8% heather tint
    val SurfaceGlassStroke = OutlineSoft

    // ── Message bubbles ──
    val BubbleUser = Heather
    val BubbleKid = Surface3
    val BubbleThought = HeatherContainer
    val BubbleAction = SuccessContainer
    val BubbleError = ErrorContainer

    // Compatibility alias (old code referenced NeonCyan for live indicators).
    val NeonCyan = SuccessTone
}
