package dev.kid.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.kid.core.speech.SpeechState

/**
 * Speech-to-Text Button for ChatScreen
 * Shows real-time feedback while recording
 */
@Composable
fun SpeechButton(
    state: SpeechState,
    confidence: Float,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    transcription: String = "",
    modifier: Modifier = Modifier,
) {
    val isListening = state == SpeechState.LISTENING
    val isProcessing = state == SpeechState.PROCESSING
    
    val backgroundColor = when {
        isListening -> Color(0xFFFF6B6B) // Red for active recording
        isProcessing -> Color(0xFF4ECDC4) // Teal for processing
        state == SpeechState.SUCCESS -> Color(0xFF51CF66) // Green for success
        state == SpeechState.ERROR -> Color(0xFFFFB347) // Orange for error
        else -> MaterialTheme.colorScheme.primary
    }
    
    val scale = animateDpAsState(
        targetValue = if (isListening) 32.dp else 28.dp,
    )
    
    val pulseScale = remember { 
        androidx.compose.animation.core.Animatable(1f)
    }
    
    // Pulsing animation when recording
    if (isListening) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            while (true) {
                pulseScale.animateTo(1.15f, animationSpec = androidx.compose.animation.core.tween(500))
                pulseScale.animateTo(1f, animationSpec = androidx.compose.animation.core.tween(500))
            }
        }
    }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Main button with pulse effect
        Box(
            modifier = Modifier
                .size(56.dp)
                .scale(pulseScale.value)
                .background(
                    color = backgroundColor,
                    shape = CircleShape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (isListening || isProcessing) {
                            onStopListening()
                        } else {
                            onStartListening()
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isListening) Icons.Filled.Mic else Icons.Filled.MicOff,
                contentDescription = if (isListening) "🎤 Stop Recording" else "🎤 Start Recording",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        
        // Show status text
        if (isListening) {
            Spacer(modifier = Modifier.size(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Animated dots
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(Color(0xFFFF6B6B), CircleShape),
                    )
                    if (index < 2) Spacer(modifier = Modifier.width(4.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Listening...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF6B6B),
                )
            }
        }
        
        // Show confidence when recording
        if (isListening && confidence > 0f) {
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                "Confidence: ${String.format("%.0f%%", confidence * 100)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        
        // Show transcription preview
        if (transcription.isNotEmpty() && !isListening) {
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                "\"$transcription\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Floating Action Button variant for speech
 */
@Composable
fun SpeechFAB(
    state: SpeechState,
    confidence: Float,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isListening = state == SpeechState.LISTENING
    
    val backgroundColor = animateColorAsState(
        targetValue = if (isListening) Color(0xFFFF6B6B) else MaterialTheme.colorScheme.primary,
    )
    
    Box(
        modifier = modifier
            .size(64.dp)
            .background(
                color = backgroundColor.value,
                shape = CircleShape,
            )
            .clickable {
                if (isListening) {
                    onStopListening()
                } else {
                    onStartListening()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isListening) Icons.Filled.Mic else Icons.Filled.MicOff,
            contentDescription = "Speech-to-Text",
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
    }
}
