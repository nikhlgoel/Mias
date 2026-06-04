package dev.mias.app.ui.home

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mias.core.ui.components.AnimatedOrb
import dev.mias.core.ui.components.StatusPill
import dev.mias.core.ui.glass.CognitionGlow
import dev.mias.core.ui.theme.MiasColors
import dev.mias.core.ui.theme.MiasShapes
import dev.mias.core.ui.theme.MiasTypography
import kotlinx.coroutines.launch
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                state = state,
                onOpenConversation = { id ->
                    scope.launch { drawerState.close() }
                    onNavigateToChat(id)
                },
                onSeeAllChats = {
                    scope.launch { drawerState.close() }
                    onNavigateToChats()
                },
                onVoice = {
                    scope.launch { drawerState.close() }
                    onNavigateToVoice()
                },
                onVision = {
                    scope.launch { drawerState.close() }
                    onNavigateToVision()
                },
                onModels = {
                    scope.launch { drawerState.close() }
                    onNavigateToModelHub()
                },
                onSettings = {
                    scope.launch { drawerState.close() }
                    onNavigateToSettings()
                },
            )
        },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MiasColors.Surface1)
                .windowInsetsPadding(WindowInsets.statusBars)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            TopBar(
                state = state,
                onOpenDrawer = { scope.launch { drawerState.open() } },
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Greeting block ──
            Text(
                text = state.greeting,
                style = MiasTypography.DisplaySmall,
                color = MiasColors.TextHi,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = state.subtitle,
                style = MiasTypography.BodyLarge,
                color = MiasColors.TextLo,
            )
            if (state.activeChatModelName != null) {
                Spacer(modifier = Modifier.height(14.dp))
                ActiveModelRow(name = state.activeChatModelName!!)
            }

            // ── Orb — centered in the remaining vertical space ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CognitionGlow(
                        cognitionState = state.cognitionState,
                        intensity = 0.4f,
                    ) {
                        AnimatedOrb(
                            cognitionState = state.cognitionState,
                            size = 140.dp,
                            modifier = Modifier
                                .size(140.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    // No dead-end: with nothing installed, go to
                                    // Models instead of an empty chat that can only
                                    // report "no model available".
                                    if (state.isReady) onNavigateToChat(null)
                                    else onNavigateToModelHub()
                                },
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (state.isReady) "Tap to start a new chat"
                        else "Tap to add your first model",
                        style = MiasTypography.BodyMedium,
                        color = MiasColors.TextLo,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ── Navigation Drawer ─────────────────────────────────────────────────────────

@Composable
private fun DrawerContent(
    state: HomeUiState,
    onOpenConversation: (String) -> Unit,
    onSeeAllChats: () -> Unit,
    onVoice: () -> Unit,
    onVision: () -> Unit,
    onModels: () -> Unit,
    onSettings: () -> Unit,
) {
    // ModalDrawerSheet would add its own surface color; we control the
    // background directly so it matches the Mias surface ladder.
    Column(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .fillMaxHeight()
            .background(MiasColors.Surface2)
            .windowInsetsPadding(WindowInsets.statusBars)
            .navigationBarsPadding(),
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MiasShapes.Full)
                    .background(MiasColors.HeatherContainer)
                    .border(1.dp, MiasColors.OutlineSoft, MiasShapes.Full),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "M",
                    style = MiasTypography.LabelLarge,
                    color = MiasColors.Heather,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Mias",
                style = MiasTypography.HeadlineMedium,
                color = MiasColors.TextHi,
            )
            Text(
                text = "Your on-device assistant",
                style = MiasTypography.BodySmall,
                color = MiasColors.TextMuted,
            )
        }

        HorizontalDivider(color = MiasColors.OutlineSoft, thickness = 1.dp)

        // Conversations section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = MiasColors.TextMuted,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Chats",
                    style = MiasTypography.LabelLarge,
                    color = MiasColors.TextLo,
                )
            }
            if (state.recentConversationCount > state.recentConversations.size) {
                IconButton(
                    onClick = onSeeAllChats,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "See all chats",
                        tint = MiasColors.Heather,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        if (state.recentConversations.isEmpty()) {
            Text(
                text = "No conversations yet",
                style = MiasTypography.BodySmall,
                color = MiasColors.TextMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 4.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.recentConversations, key = { it.id }) { chat ->
                    DrawerConversationRow(
                        chat = chat,
                        onClick = { onOpenConversation(chat.id) },
                    )
                }
                if (state.recentConversationCount > state.recentConversations.size) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MiasShapes.Card)
                                .clickable(onClick = onSeeAllChats)
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "All conversations (${state.recentConversationCount})",
                                style = MiasTypography.LabelMedium,
                                color = MiasColors.Heather,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = MiasColors.Heather,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }
        }

        // Bottom — quick destinations + Settings
        HorizontalDivider(color = MiasColors.OutlineSoft, thickness = 1.dp)
        DrawerNavItem(
            icon = Icons.Rounded.Mic,
            label = "Voice chat",
            onClick = onVoice,
        )
        DrawerNavItem(
            icon = Icons.Rounded.PhotoCamera,
            label = "Visual chat",
            onClick = onVision,
        )
        DrawerNavItem(
            icon = Icons.Rounded.WorkspacePremium,
            label = "Models",
            onClick = onModels,
        )
        DrawerNavItem(
            icon = Icons.Rounded.Settings,
            label = "Settings",
            onClick = onSettings,
        )
    }
}

@Composable
private fun DrawerNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MiasColors.TextLo,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MiasTypography.LabelLarge,
            color = MiasColors.TextLo,
        )
    }
}

@Composable
private fun DrawerConversationRow(chat: RecentChat, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MiasShapes.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = chat.title,
                style = MiasTypography.LabelMedium,
                color = MiasColors.TextHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatRelative(chat.updatedAt),
                style = MiasTypography.LabelSmall,
                color = MiasColors.TextMuted,
            )
        }
        if (chat.preview.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = chat.preview,
                style = MiasTypography.BodySmall,
                color = MiasColors.TextLo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(
    state: HomeUiState,
    onOpenDrawer: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenDrawer) {
            Icon(
                imageVector = Icons.Rounded.Menu,
                contentDescription = "Open menu",
                tint = MiasColors.TextLo,
            )
        }
        StatusPill(
            brainState = state.brainState,
            cognitionState = state.cognitionState,
        )
    }
}

// ── Home content composables ──────────────────────────────────────────────────

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
