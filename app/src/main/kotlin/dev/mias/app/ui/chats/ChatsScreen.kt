package dev.mias.app.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mias.core.data.Conversation
import dev.mias.core.ui.glass.GlassCard
import dev.mias.core.ui.theme.MiasColors
import dev.mias.core.ui.theme.MiasTypography
import java.text.DateFormat
import java.util.Date

@Composable
fun ChatsScreen(
    onNavigateBack: () -> Unit,
    onOpenConversation: (String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MiasColors.Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MiasColors.TextPrimary,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "Chats",
                    style = MiasTypography.HeadlineMedium,
                    color = MiasColors.TextPrimary,
                )
            }

            if (state.conversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No conversations yet.\nTap + to start one.",
                        style = MiasTypography.BodyMedium,
                        color = MiasColors.TextSecondary,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    items(state.conversations, key = { it.id }) { conv ->
                        ConversationRow(
                            conv = conv,
                            onClick = { onOpenConversation(conv.id) },
                            onDelete = { pendingDeleteId = conv.id },
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { onOpenConversation(null) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(24.dp),
            containerColor = MiasColors.Primary,
            contentColor = MiasColors.TextPrimary,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "New chat")
        }

        if (pendingDeleteId != null) {
            AlertDialog(
                onDismissRequest = { pendingDeleteId = null },
                title = { Text("Delete this chat?") },
                text = { Text("The conversation history will be removed from this device.") },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDeleteId?.let(viewModel::deleteConversation)
                        pendingDeleteId = null
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun ConversationRow(
    conv: Conversation,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    GlassCard(
        accentColor = MiasColors.SurfaceGlass,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conv.title.ifBlank { "Untitled" },
                    style = MiasTypography.LabelLarge,
                    color = MiasColors.TextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = relativeTime(conv.updatedAt) +
                        " · ${conv.messages.size} message${if (conv.messages.size == 1) "" else "s"}",
                    style = MiasTypography.BodySmall,
                    color = MiasColors.TextSecondary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MiasColors.TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private fun relativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> "${diff / 3_600_000} hr ago"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000} day${if (diff / 86_400_000 == 1L) "" else "s"} ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
    }
}
