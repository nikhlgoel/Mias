package dev.mias.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.mias.core.ui.theme.MiasColors
import dev.mias.core.ui.theme.MiasShapes
import dev.mias.core.ui.theme.MiasTypography

/**
 * Modern glassmorphism input bar for chat interface.
 *
 * Features:
 * - Translucent background with gradient
 * - Subtle border for depth
 * - Rounded corners for modern look
 * - Optional leading/trailing icons
 */
@Composable
fun ModernInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "Type a message...",
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MiasColors.SurfaceGlass,
                            MiasColors.GlassHighlight.copy(alpha = 0.05f),
                        ),
                    ),
                    shape = MiasShapes.InputField,
                )
                .border(
                    width = 1.dp,
                    color = MiasColors.GlassBorder,
                    shape = MiasShapes.InputField,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.size(8.dp))
            }

            Box(
                modifier = Modifier.weight(1f),
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = hint,
                        style = MiasTypography.BodyMedium,
                        color = MiasColors.TextTertiary,
                    )
                }

                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MiasTypography.BodyLarge.copy(
                        color = MiasColors.TextPrimary,
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = MiasColors.TextPrimary,
                        unfocusedTextColor = MiasColors.TextPrimary,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = MiasColors.Primary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    enabled = enabled,
                    singleLine = true,
                )
            }
            
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.size(8.dp))
                trailingIcon()
            }
        }
    }
}

/**
 * Modern send button with gradient background.
 */
@Composable
fun ModernSendButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .background(
                brush = if (enabled) {
                    Brush.radialGradient(
                        colors = listOf(
                            MiasColors.Primary,
                            MiasColors.PrimaryDark,
                        ),
                    )
                } else {
                    Brush.linearGradient(colors = listOf(MiasColors.SurfaceDim, MiasColors.SurfaceDim))
                },
                shape = MiasShapes.IconButton,
            )
            .clickable(
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Send,
            contentDescription = "Send",
            tint = if (enabled) MiasColors.TextOnPrimary else MiasColors.TextTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}
