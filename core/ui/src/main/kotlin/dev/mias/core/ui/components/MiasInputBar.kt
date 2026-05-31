package dev.mias.core.ui.components

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
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

/**
 * Composer pill for the chat / vision screens.
 *
 * - Background: Surface3 with a hairline OutlineSoft border, fully pilled.
 * - Trailing button morphs:
 *     blank, idle      → mic (low-emphasis on SurfaceGlass)
 *     has text, idle   → send (Heather circle, HeatherInk icon)
 *     processing       → stop (Heather circle, HeatherInk icon)
 */
@Composable
fun MiasInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Write to Mias",
    enabled: Boolean = true,
    isProcessing: Boolean = false,
    onStop: (() -> Unit)? = null,
    onAttach: (() -> Unit)? = null,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MiasShapes.Full)
            .background(MiasColors.Surface3)
            .border(1.dp, MiasColors.OutlineSoft, MiasShapes.Full)
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (onAttach != null) {
            IconButton(
                onClick = onAttach,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Attach image",
                    tint = MiasColors.TextLo,
                )
            }
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp, max = 160.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            enabled = enabled,
            textStyle = MiasTypography.BodyLarge.copy(color = MiasColors.TextHi),
            cursorBrush = SolidColor(MiasColors.Heather),
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
                            color = MiasColors.TextMuted,
                        )
                    }
                    innerTextField()
                }
            },
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Trailing affordance — stop / send / mic. Only one is visible at a time.
        when {
            isProcessing -> AccentCircleButton(
                icon = Icons.Rounded.Stop,
                contentDescription = "Stop",
                onClick = { onStop?.invoke() },
                enabled = onStop != null,
            )
            value.isNotBlank() -> AccentCircleButton(
                icon = Icons.AutoMirrored.Rounded.Send,
                contentDescription = "Send",
                onClick = {
                    onSend()
                    keyboardController?.hide()
                },
                enabled = enabled,
            )
            else -> IconButton(
                onClick = { /* Voice handled by the screen-level mic button */ },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "Voice",
                    tint = MiasColors.TextLo,
                )
            }
        }
    }
}

@Composable
private fun AccentCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(40.dp)
            .clip(MiasShapes.Full)
            .background(
                if (enabled) MiasColors.Heather
                else MiasColors.HeatherDim,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MiasColors.HeatherInk,
            modifier = Modifier.size(20.dp),
        )
    }
}
