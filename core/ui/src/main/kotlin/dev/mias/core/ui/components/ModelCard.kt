package dev.mias.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.mias.core.modelhub.model.DownloadState
import dev.mias.core.modelhub.model.DownloadStatus
import dev.mias.core.modelhub.model.ModelCard
import dev.mias.core.modelhub.model.ModelRole
import dev.mias.core.ui.glass.GlassCard
import dev.mias.core.ui.theme.MiasColors
import dev.mias.core.ui.theme.MiasShapes
import dev.mias.core.ui.theme.MiasTypography

/**
 * ModelCard — visual card for browsing or managing a model.
 *
 * Shows: model name, role badges, size, quant, download state,
 * and an action button.
 */
@Composable
fun ModelCard(
    modelCard: ModelCard,
    downloadState: DownloadState? = null,
    isActive: Boolean = false,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier.fillMaxWidth().clickable(onClick = onAction),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // ── Header row ──────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Brain icon
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(modelCard.roles.firstOrNull()?.color()?.copy(alpha = 0.15f) ?: MiasColors.SurfaceGlass),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Memory,
                            contentDescription = null,
                            tint = modelCard.roles.firstOrNull()?.color() ?: MiasColors.NeonCyan,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column {
                        Text(
                            text = modelCard.name,
                            style = MiasTypography.LabelLarge,
                            color = MiasColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${formatSize(modelCard.sizeBytes)} · ${modelCard.quantization}",
                            style = MiasTypography.LabelSmall,
                            color = MiasColors.TextSecondary,
                        )
                    }
                }

                // Active / download indicator
                if (isActive) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Active",
                        tint = MiasColors.NeonCyan,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = MiasColors.TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Spec + role badges ─────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (modelCard.parameterCount.isNotBlank()) {
                    SpecBadge(modelCard.parameterCount)
                }
                if (modelCard.quantization.isNotBlank()) {
                    SpecBadge(modelCard.quantization)
                }
                modelCard.roles.take(3).forEach { role ->
                    RoleBadge(role)
                }
            }

            // ── Download progress (if downloading) ───────────────────────
            downloadState?.let { state ->
                if (state.status == DownloadStatus.DOWNLOADING ||
                    state.status == DownloadStatus.PAUSED
                ) {
                    Spacer(Modifier.height(12.dp))
                    DownloadProgressBar(
                        progress = state.progressFraction,
                        status = state.status,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${(state.progressFraction * 100).toInt()}% · ${formatSize(state.bytesDownloaded)} / ${formatSize(modelCard.sizeBytes)}",
                        style = MiasTypography.LabelSmall,
                        color = MiasColors.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Neutral spec badge — model size ("1.5B"), quantization ("Q4_K_M"), etc.
 * Visually distinct from the colored [RoleBadge] so capability vs. spec
 * read as separate dimensions.
 */
@Composable
fun SpecBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MiasColors.Surface2)
            .border(0.5.dp, MiasColors.OutlineSoft, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MiasTypography.LabelSmall,
            color = MiasColors.TextSecondary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun RoleBadge(role: ModelRole, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(role.color().copy(alpha = 0.12f))
            .border(0.5.dp, role.color().copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = role.label(),
            style = MiasTypography.LabelSmall,
            color = role.color(),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun DownloadProgressBar(
    progress: Float,
    status: DownloadStatus,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "download_progress",
    )

    val trackColor = when (status) {
        DownloadStatus.DOWNLOADING -> MiasColors.NeonCyan
        DownloadStatus.PAUSED -> MiasColors.TextSecondary
        DownloadStatus.FAILED -> MiasColors.ErrorRed
        else -> MiasColors.NeonCyan
    }

    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = modifier
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp)),
        color = trackColor,
        trackColor = trackColor.copy(alpha = 0.15f),
        strokeCap = StrokeCap.Round,
    )
}

// ── Extensions ────────────────────────────────────────────────────────────────

fun ModelRole.color(): Color = when (this) {
    ModelRole.CHAT -> MiasColors.NeonCyan
    ModelRole.CODE -> Color(0xFF00FF88)
    ModelRole.RESEARCH -> Color(0xFFFFAA00)
    ModelRole.CREATIVE -> Color(0xFFFF44AA)
    ModelRole.SURVIVAL -> Color(0xFFFF4444)
    ModelRole.REASONING -> Color(0xFF8844FF)
    ModelRole.VISION -> Color(0xFF44AAFF)
    ModelRole.EMBEDDING -> Color(0xFF88AAFF)
}

fun ModelRole.label(): String = when (this) {
    ModelRole.CHAT -> "Chat"
    ModelRole.CODE -> "Code"
    ModelRole.RESEARCH -> "Research"
    ModelRole.CREATIVE -> "Creative"
    ModelRole.SURVIVAL -> "Survival"
    ModelRole.REASONING -> "Reasoning"
    ModelRole.VISION -> "Vision"
    ModelRole.EMBEDDING -> "Embed"
}

fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
    else -> String.format("%.1fGB", bytes.toDouble() / (1024 * 1024 * 1024))
}
