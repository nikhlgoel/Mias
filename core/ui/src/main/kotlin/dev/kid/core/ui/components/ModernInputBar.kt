package dev.kid.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.kid.core.ui.theme.KidColors
import dev.kid.core.ui.theme.KidShapes

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
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            KidColors.SurfaceGlass,
                            KidColors.GlassHighlight.copy(alpha = 0.05f),
                        ),
                    ),
                    shape = KidShapes.InputField,
                )
                .border(
                    width = 1.dp,
                    color = KidColors.GlassBorder,
                    shape = KidShapes.InputField,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.size(8.dp))
            }
            
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.weight(1f),
            ) {
                if (text.isEmpty()) {
                    androidx.compose.material3.Text(
                        text = hint,
                        style = dev.kid.core.ui.theme.KidTypography.BodyMedium,
                        color = KidColors.TextTertiary,
                    )
                }
                
                androidx.compose.material3.TextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = dev.kid.core.ui.theme.KidTypography.BodyLarge.copy(
                        color = KidColors.TextPrimary,
                    ),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedTextColor = KidColors.TextPrimary,
                        unfocusedTextColor = KidColors.TextPrimary,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = KidColors.Primary,
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
                            KidColors.Primary,
                            KidColors.PrimaryDark,
                        ),
                    )
                } else {
                    Brush.solid(KidColors.SurfaceDim)
                },
                shape = KidShapes.IconButton,
            )
            .clickable(
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = androidx.compose.material.icons.Icons.Rounded.Send,
            contentDescription = "Send",
            tint = if (enabled) KidColors.TextOnPrimary else KidColors.TextTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}
