package dev.mias.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mias.core.ui.components.AnimatedOrb
import dev.mias.core.ui.components.StatusPill
import dev.mias.core.ui.glass.CognitionGlow
import dev.mias.core.ui.theme.MiasColors
import dev.mias.core.ui.theme.MiasShapes
import dev.mias.core.ui.theme.MiasTypography
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    onNavigateToChat: (String?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToModelHub: () -> Unit = {},
    onNavigateToChats: () -> Unit = {},
    onNavigateToVoice: () -> Unit = {},
    onNavigateToVision: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MiasColors.Surface1)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        TopBar(
            state = state,
            onNavigateToChats = onNavigateToChats,
            onNavigateToModelHub = onNavigateToModelHub,
            onNavigateToSettings = onNavigateToSettings,
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = state.greeting,
            style = MiasTypography.DisplaySmall,
            color = MiasColors.TextHi,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = state.subtitle,
            style = MiasTypography.BodyMedium,
            color = MiasColors.TextLo,
        )

        if (state.activeChatModelName != null) {
            Spacer(modifier = Modifier.height(16.dp))
            ActiveModelRow(name = state.activeChatModelName!!)
        }

        Spacer(modifier = Modifier.height(36.dp))

        // ── Orb (the start-new-chat affordance) ──
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            CognitionGlow(
                cognitionState = state.cognitionState,
                intensity = 0.4f,
            ) {
                AnimatedOrb(
                    cognitionState = state.cognitionState,
                    size = 128.dp,
                    modifier = Modifier
                        .size(128.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onNavigateToChat(null) },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Tap to start a new chat",
            style = MiasTypography.LabelMedium,
            color = MiasColors.TextLo,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Quick actions ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuickChip(label = "Speak", icon = Icons.Rounded.Mic, onClick = onNavigateToVoice)
            Spacer(modifier = Modifier.width(10.dp))
            QuickChip(label = "Type", icon = Icons.Rounded.Keyboard) { onNavigateToChat(null) }
            Spacer(modifier = Modifier.width(10.dp))
            QuickChip(label = "See", icon = Icons.Rounded.PhotoCamera, onClick = onNavigateToVision)
        }

        Spacer(modifier = Modifier.height(36.dp))

        // ── Recent conversations ──
        if (state.recentConversations.isNotEmpty()) {
            RecentSection(
                items = state.recentConversations,
                totalCount = state.recentConversationCount,
                onOpen = { onNavigateToChat(it) },
                onSeeAll = onNavigateToChats,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TopBar(
    state: HomeUiState,
    onNavigateToChats: () -> Unit,
    onNavigateToModelHub: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusPill(
            brainState = state.brainState,
            cognitionState = state.cognitionState,
        )
        Row {
            TopBarIcon(Icons.Rounded.History, "Chats", onNavigateToChats)
            TopBarIcon(Icons.Rounded.WorkspacePremium, "Models", onNavigateToModelHub)
            TopBarIcon(Icons.Rounded.Settings, "Settings", onNavigateToSettings)
        }
    }
}

@Composable
private fun TopBarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MiasColors.TextLo,
        )
    }
}

@Composable
private fun ActiveModelRow(name: String) {
    Row(
        modifier = Modifier
            .clip(MiasShapes.Full)
            .background(MiasColors.Surface2)
            .border(1.dp, MiasColors.OutlineSoft, MiasShapes.Full)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(MiasShapes.Full)
                .background(MiasColors.SuccessTone),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Rounded.Memory,
            contentDescription = null,
            tint = MiasColors.TextLo,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = name,
            style = MiasTypography.LabelMedium,
            color = MiasColors.TextHi,
        )
    }
}

@Composable
private fun QuickChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MiasColors.Surface2,
            labelColor = MiasColors.TextHi,
            leadingIconContentColor = MiasColors.Heather,
        ),
        border = BorderStroke(1.dp, MiasColors.OutlineSoft),
    )
}

@Composable
private fun RecentSection(
    items: List<RecentChat>,
    totalCount: Int,
    onOpen: (String) -> Unit,
    onSeeAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Recent",
            style = MiasTypography.LabelLarge,
            color = MiasColors.TextLo,
        )
        if (totalCount > items.size) {
            Row(
                modifier = Modifier
                    .clip(MiasShapes.Full)
                    .clickable(onClick = onSeeAll)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "See all",
                    style = MiasTypography.LabelMedium,
                    color = MiasColors.Heather,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MiasColors.Heather,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item -> RecentRow(item = item, onClick = { onOpen(item.id) }) }
    }
}

@Composable
private fun RecentRow(item: RecentChat, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MiasShapes.Card)
            .background(MiasColors.Surface2)
            .border(1.dp, MiasColors.OutlineSoft, MiasShapes.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.title,
                style = MiasTypography.LabelLarge,
                color = MiasColors.TextHi,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatRelative(item.updatedAt),
                style = MiasTypography.LabelSmall,
                color = MiasColors.TextMuted,
            )
        }
        if (item.preview.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.preview,
                style = MiasTypography.BodySmall,
                color = MiasColors.TextLo,
                maxLines = 2,
            )
        }
    }
}

private fun formatRelative(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val deltaSec = (now - timestamp) / 1000
    return when {
        deltaSec < 60 -> "just now"
        deltaSec < 3600 -> "${deltaSec / 60}m"
        deltaSec < 86_400 -> "${deltaSec / 3600}h"
        deltaSec < 7 * 86_400 -> "${deltaSec / 86_400}d"
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(timestamp))
    }
}

