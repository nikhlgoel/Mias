package dev.mias.app.ui.vision

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.mias.core.ui.glass.GlassCard
import dev.mias.core.ui.theme.MiasColors
import dev.mias.core.ui.theme.MiasShapes
import dev.mias.core.ui.theme.MiasTypography

@Composable
fun VisionChatScreen(
    onNavigateBack: () -> Unit,
    onNavigateToModels: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: VisionChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-check the assigned vision model whenever the screen comes back to
    // the foreground — the user may have just installed/assigned one in
    // the Models screen and returned here.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.refreshVisionModel()
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> viewModel.onImagePicked(uri) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MiasColors.Background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // ── Top bar ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MiasColors.TextPrimary,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "See",
                style = MiasTypography.HeadlineMedium,
                color = MiasColors.TextPrimary,
            )
        }

        if (!state.isCheckingModel && !state.hasVisionModel) {
            NoVisionModelState(onNavigateToModels = onNavigateToModels)
            return
        }

        // ── Body ────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .imePadding(),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            ImageArea(
                image = state.image,
                onPickRequest = {
                    pickImage.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                },
                onClear = { viewModel.clearImage() },
            )

            Spacer(modifier = Modifier.height(16.dp))

            PromptRow(
                prompt = state.prompt,
                isReady = state.isReady,
                isProcessing = state.isProcessing,
                onPromptChange = viewModel::onPromptChange,
                onSubmit = { viewModel.onSubmit() },
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (state.errorMessage != null) {
                GlassCard(accentColor = MiasColors.Error) {
                    Text(
                        text = state.errorMessage ?: "",
                        style = MiasTypography.BodyMedium,
                        color = MiasColors.Error,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (state.response.isNotBlank() || state.isProcessing) {
                GlassCard(accentColor = MiasColors.SurfaceGlass) {
                    Text(
                        text = if (state.response.isBlank()) "Reading the image…" else state.response,
                        style = MiasTypography.BodyLarge,
                        color = MiasColors.TextPrimary,
                    )
                }
            }

            Spacer(
                modifier = Modifier
                    .height(24.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            )
        }
    }
}

@Composable
private fun ImageArea(
    image: android.graphics.Bitmap?,
    onPickRequest: () -> Unit,
    onClear: () -> Unit,
) {
    if (image == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MiasShapes.Card)
                .background(MiasColors.SurfaceGlass)
                .border(
                    width = 1.dp,
                    color = MiasColors.GlassBorder,
                    shape = MiasShapes.Card,
                )
                .clickable(onClick = onPickRequest),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.PhotoLibrary,
                    contentDescription = null,
                    tint = MiasColors.TextSecondary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Pick an image to ask about",
                    style = MiasTypography.BodyMedium,
                    color = MiasColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Everything stays on this device.",
                    style = MiasTypography.LabelSmall,
                    color = MiasColors.TextTertiary,
                )
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "Selected image",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .clip(MiasShapes.Card)
                    .border(
                        width = 1.dp,
                        color = MiasColors.GlassBorder,
                        shape = MiasShapes.Card,
                    ),
            )
            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MiasColors.Background.copy(alpha = 0.7f)),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove image",
                    tint = MiasColors.TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun PromptRow(
    prompt: String,
    isReady: Boolean,
    isProcessing: Boolean,
    onPromptChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    text = "Ask about this image",
                    color = MiasColors.TextTertiary,
                )
            },
            singleLine = false,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (isReady) onSubmit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MiasColors.TextPrimary,
                unfocusedTextColor = MiasColors.TextPrimary,
            ),
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onSubmit,
            enabled = isReady,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isReady) MiasColors.Primary
                    else MiasColors.SurfaceGlass,
                ),
        ) {
            Icon(
                imageVector = if (isProcessing) Icons.Rounded.Add else Icons.AutoMirrored.Rounded.Send,
                contentDescription = if (isProcessing) "Working" else "Send",
                tint = if (isReady) MiasColors.TextPrimary else MiasColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun NoVisionModelState(onNavigateToModels: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.PhotoLibrary,
            contentDescription = null,
            tint = MiasColors.TextSecondary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No vision model yet",
            style = MiasTypography.HeadlineMedium,
            color = MiasColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Install a vision-capable model in Models and assign it " +
                "to the Vision role. Gemma 3n is a good starting point — search " +
                "\"gemma 3n\" on Hugging Face.",
            style = MiasTypography.BodyMedium,
            color = MiasColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .clip(MiasShapes.Card)
                .background(MiasColors.Primary.copy(alpha = 0.22f))
                .clickable(onClick = onNavigateToModels)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Browse models",
                style = MiasTypography.LabelMedium,
                color = MiasColors.TextPrimary,
            )
        }
    }
}
