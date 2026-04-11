package dev.kid.core.ui.components

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
import dev.kid.core.ui.theme.KidColors
import dev.kid.core.ui.theme.KidShapes
import dev.kid.core.ui.theme.KidTypography

@Composable
fun KidInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Talk to Kid...",
    enabled: Boolean = true,
    isProcessing: Boolean = false,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(KidShapes.ExtraLarge)
            .background(KidColors.SurfaceElevated.copy(alpha = 0.8f))
            .border(0.5.dp, KidColors.GlassBorder, KidShapes.ExtraLarge)
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
            textStyle = KidTypography.BodyLarge.copy(color = KidColors.TextPrimary),
            cursorBrush = SolidColor(KidColors.Primary),
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
                            style = KidTypography.BodyLarge,
                            color = KidColors.TextTertiary,
                        )
                    }
                    innerTextField()
                }
            },
        )

        AnimatedVisibility(visible = isProcessing) {
            Box(modifier = Modifier.padding(8.dp)) {
                ThinkingDots(color = KidColors.Primary)
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
                    tint = KidColors.Primary,
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
                    tint = KidColors.TextSecondary,
                )
            }
        }
    }
}
