package dev.mias.app.ui.theme

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import dev.mias.core.ui.theme.MiasColors

private val MiasDarkColorScheme = darkColorScheme(
    primary = MiasColors.Primary,
    onPrimary = MiasColors.TextOnPrimary,
    primaryContainer = MiasColors.PrimaryDark,
    onPrimaryContainer = MiasColors.TextPrimary,
    secondary = MiasColors.Secondary,
    onSecondary = MiasColors.Background,
    tertiary = MiasColors.CognitionOffloading,
    onTertiary = MiasColors.Background,
    background = MiasColors.Background,
    onBackground = MiasColors.TextPrimary,
    surface = MiasColors.Surface,
    onSurface = MiasColors.TextPrimary,
    surfaceVariant = MiasColors.SurfaceElevated,
    onSurfaceVariant = MiasColors.TextSecondary,
    error = MiasColors.Error,
    onError = MiasColors.TextPrimary,
    outline = MiasColors.GlassBorder,
    outlineVariant = MiasColors.SurfaceGlassStroke,
)

internal tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun MiasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = MiasDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            try {
                val activity = view.context.findActivity()
                activity?.window?.statusBarColor = MiasColors.Background.toArgb()
                activity?.window?.navigationBarColor = MiasColors.Surface.toArgb()
            } catch (_: Exception) {
                // Some OEM skins throw
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        shapes = Shapes(),
        content = content,
    )
}
