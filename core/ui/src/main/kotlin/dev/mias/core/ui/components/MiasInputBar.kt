package dev.mias.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.mias.core.ui.theme.MiasColors
import dev.mias.core.ui.theme.MiasShapes
import dev.mias.core.ui.theme.MiasTypography

@Composable
fun MiasInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Talk to Mias...",
    enabled: Boolean = true,
    isProcessing: Boolean = false,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MiasShapes.ExtraLarge)
            .background(MiasColors.SurfaceElevated.copy(alpha = 0.8f))
            .border(0.5.dp, MiasColors.GlassBorder, MiasShapes.ExtraLarge)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp, max = 160.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            enabled = enabled,
            textStyle = MiasTypography.BodyLarge.copy(color = MiasColors.TextPrimary),
            cursorBrush = SolidColor(MiasColors.Primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (value.isNotBlank()) {
                        onSend()
                        keyboardController?.hide()
                    }
                },
            ),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MiasTypography.BodyLarge,
                            color = MiasColors.TextTertiary,
                        )
                    }
                    innerTextField()
                }
            },
        )

        AnimatedVisibility(visible = isProcessing) {
            Box(modifier = Modifier.padding(8.dp)) {
                ThinkingDots(color = MiasColors.Primary)
            }
        }

        AnimatedVisibility(
            visible = value.isNotBlank() && !isProcessing,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank() && enabled,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send",
                    tint = MiasColors.Primary,
                )
            }
        }

        AnimatedVisibility(
            visible = value.isBlank() && !isProcessing,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            IconButton(
                onClick = { /* Voice input — future */ },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "Voice",
                    tint = MiasColors.TextSecondary,
                )
            }
        }
    }
}
