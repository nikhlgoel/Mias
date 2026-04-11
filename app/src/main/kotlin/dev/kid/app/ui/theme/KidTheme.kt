package dev.kid.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.kid.core.ui.theme.KidColors

private val KidDarkColorScheme = darkColorScheme(
    primary = KidColors.Primary,
    onPrimary = KidColors.TextOnPrimary,
    primaryContainer = KidColors.PrimaryDark,
    onPrimaryContainer = KidColors.TextPrimary,
    secondary = KidColors.CognitionActing,
    onSecondary = KidColors.Background,
    tertiary = KidColors.CognitionOffloading,
    onTertiary = KidColors.Background,
    background = KidColors.Background,
    onBackground = KidColors.TextPrimary,
    surface = KidColors.Surface,
    onSurface = KidColors.TextPrimary,
    surfaceVariant = KidColors.SurfaceElevated,
    onSurfaceVariant = KidColors.TextSecondary,
    error = KidColors.Error,
    onError = KidColors.TextPrimary,
    outline = KidColors.GlassBorder,
    outlineVariant = KidColors.SurfaceGlassStroke,
)

@Composable
fun KidTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = KidColors.Background.toArgb()
            window.navigationBarColor = KidColors.Background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = KidDarkColorScheme,
        content = content,
    )
}
