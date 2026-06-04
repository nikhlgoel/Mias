package dev.mias.app.ui.knowledge

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mias.core.data.rag.Document
import dev.mias.core.ui.glass.GlassCard
import dev.mias.core.ui.theme.MiasColors
import dev.mias.core.ui.theme.MiasShapes
import dev.mias.core.ui.theme.MiasTypography
import java.text.DateFormat
import java.util.Date

@Composable
fun KnowledgeScreen(
    onNavigateBack: () -> Unit,
    onNavigateToModels: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KnowledgeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val pickDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.addDocument(uri) }

    // Surface status/errors briefly, then clear so they don't stick around.
    LaunchedEffect(state.statusMessage, state.errorMessage) {
        if (state.statusMessage != null || state.errorMessage != null) {
            kotlinx.coroutines.delay(2600)
            viewModel.clearMessages()
        }
    }

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
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = MiasColors.TextPrimary)
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text("Knowledge", style = MiasTypography.HeadlineMedium, color = MiasColors.TextPrimary)
                    Text(
                        "Answers can draw on your documents, fully on-device",
                        style = MiasTypography.BodySmall,
                        color = MiasColors.TextMuted,
                    )
                }
            }

            // Embedding-model requirement banner.
            if (!state.embeddingReady) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(MiasShapes.Card)
                        .background(MiasColors.HeatherContainer)
                        .clickable(onClick = onNavigateToModels)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "An embedding model is needed",
                            style = MiasTypography.LabelLarge,
                            color = MiasColors.TextHi,
                        )
                        Text(
                            "Install Nomic Embed in Models to add and search documents.",
                            style = MiasTypography.BodySmall,
                            color = MiasColors.TextLo,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MiasColors.Heather,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // "Use in chat" toggle — only meaningful once there are documents.
            if (state.documents.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Use my documents in chat", style = MiasTypography.LabelLarge, color = MiasColors.TextPrimary)
                        Text(
                            "Mias pulls relevant passages into each answer.",
                            style = MiasTypography.BodySmall,
                            color = MiasColors.TextSecondary,
                        )
                    }
                    Switch(
                        checked = state.useDocuments,
                        onCheckedChange = viewModel::setUseDocuments,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MiasColors.HeatherInk,
                            checkedTrackColor = MiasColors.Heather,
                        ),
                    )
                }
            }

            if (state.documents.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No documents yet.\nAdd a PDF, .txt or .md file and Mias can answer from it.",
                        style = MiasTypography.BodyMedium,
                        color = MiasColors.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    items(state.documents, key = { it.id }) { doc ->
                        DocumentRow(doc = doc, onDelete = { viewModel.deleteDocument(doc.id) })
                    }
                }
            }
        }

        // Add (FAB) — shows a spinner while a file is being ingested/embedded.
        FloatingActionButton(
            onClick = {
                if (!state.isIngesting) {
                    pickDocument.launch(
                        arrayOf("application/pdf", "text/plain", "text/markdown", "text/*"),
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(24.dp),
            containerColor = MiasColors.Primary,
            contentColor = MiasColors.TextOnPrimary,
        ) {
            if (state.isIngesting) {
                CircularProgressIndicator(
                    color = MiasColors.TextOnPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Icon(Icons.Rounded.Add, contentDescription = "Add document")
            }
        }

        // Lightweight status toast at the bottom.
        val message = state.errorMessage ?: state.statusMessage
        if (message != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 88.dp, bottom = 28.dp)
                    .clip(MiasShapes.Card)
                    .background(if (state.errorMessage != null) MiasColors.ErrorContainer else MiasColors.Surface3)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = message,
                    style = MiasTypography.BodySmall,
                    color = if (state.errorMessage != null) MiasColors.ErrorTone else MiasColors.TextHi,
                )
            }
        }
    }
}

@Composable
private fun DocumentRow(doc: Document, onDelete: () -> Unit) {
    GlassCard(accentColor = MiasColors.SurfaceGlass, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Description,
                contentDescription = null,
                tint = MiasColors.Heather,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.name,
                    style = MiasTypography.LabelLarge,
                    color = MiasColors.TextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                val passages = if (doc.chunkCount == 1) "1 passage" else "${doc.chunkCount} passages"
                Text(
                    text = "$passages · ${formatDate(doc.addedAt)}",
                    style = MiasTypography.BodySmall,
                    color = MiasColors.TextSecondary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Remove",
                    tint = MiasColors.TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private fun formatDate(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
