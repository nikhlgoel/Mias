package dev.kid.app.ui.theme

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
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

internal tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun KidTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            try {
                val activity = view.context.findActivity()
                activity?.enableEdgeToEdge()
            } catch (_: Exception) {
                // Some OEM skins throw when edge-to-edge is set during SideEffect
            }
        }
    }

    MaterialTheme(
        colorScheme = KidDarkColorScheme,
        content = content,
    )
}
