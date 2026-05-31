package dev.mias.app.ui.theme

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.mias.core.ui.theme.MiasColors

/**
 * Mias is dark-only for now. Material You / dynamic color is explicitly
 * disabled — wallpaper-derived colors would clobber the warm palette.
 */
private val MiasDarkColorScheme = darkColorScheme(
    // Primary = Heather
    primary = MiasColors.Heather,
    onPrimary = MiasColors.HeatherInk,
    primaryContainer = MiasColors.HeatherContainer,
    onPrimaryContainer = MiasColors.TextHi,
    inversePrimary = MiasColors.HeatherDim,

    // Secondary / tertiary stay on Heather variants for cohesion; tertiary
    // pivots to success for "model loaded" / "download complete" affordances.
    secondary = MiasColors.HeatherContainer,
    onSecondary = MiasColors.TextHi,
    secondaryContainer = MiasColors.HeatherContainer,
    onSecondaryContainer = MiasColors.TextHi,
    tertiary = MiasColors.SuccessTone,
    onTertiary = MiasColors.SuccessInk,
    tertiaryContainer = MiasColors.SuccessContainer,
    onTertiaryContainer = MiasColors.SuccessTone,

    // Surfaces
    background = MiasColors.Surface1,
    onBackground = MiasColors.TextHi,
    surface = MiasColors.Surface2,
    onSurface = MiasColors.TextHi,
    surfaceVariant = MiasColors.Surface3,
    onSurfaceVariant = MiasColors.TextLo,
    surfaceTint = MiasColors.Heather,
    inverseSurface = MiasColors.TextHi,
    inverseOnSurface = MiasColors.Surface1,

    // M3 surface containers used by NavigationBar, Card, BottomSheet, etc.
    surfaceContainerLowest = MiasColors.Surface0,
    surfaceContainerLow = MiasColors.Surface1,
    surfaceContainer = MiasColors.Surface2,
    surfaceContainerHigh = MiasColors.Surface3,
    surfaceContainerHighest = MiasColors.Surface4,

    // Outline
    outline = MiasColors.OutlineStrong,
    outlineVariant = MiasColors.OutlineSoft,

    // Error
    error = MiasColors.ErrorTone,
    onError = MiasColors.ErrorInk,
    errorContainer = MiasColors.ErrorContainer,
    onErrorContainer = MiasColors.ErrorTone,

    // Scrim sits on surface-0 so modals fade into the deepest tone.
    scrim = MiasColors.Surface0,
)

internal tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun MiasTheme(
    // Dark-only. Ignore system setting and dynamic color until a light scheme exists.
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            try {
                val activity = view.context.findActivity()
                val window = activity?.window ?: return@SideEffect
                // System bars tint to surface-0 so the chrome blends with the
                // app rather than framing it.
                @Suppress("DEPRECATION")
                window.statusBarColor = MiasColors.Surface0.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = MiasColors.Surface0.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            } catch (_: Exception) {
                // Some OEM skins throw on window-attribute writes.
            }
        }
    }

    MaterialTheme(
        colorScheme = MiasDarkColorScheme,
        typography = Typography(),
        shapes = Shapes(),
        content = content,
    )
}
