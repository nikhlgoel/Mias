package dev.mias.app.ui.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mias.core.common.MiasResult
import dev.mias.core.common.di.IoDispatcher
import dev.mias.core.inference.vision.MediaPipeVisionEngine
import dev.mias.core.inference.vision.VisionModelSupport
import dev.mias.core.modelhub.manager.ModelManager
import dev.mias.core.modelhub.model.InstalledModel
import dev.mias.core.modelhub.model.ModelRole
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class VisionUiState(
    val image: Bitmap? = null,
    val prompt: String = "",
    val response: String = "",
    val isProcessing: Boolean = false,
    val isCheckingModel: Boolean = true,
    val visionModel: InstalledModel? = null,
    /** Name of a model assigned to Vision that is the wrong format (e.g. a GGUF). */
    val incompatibleModelName: String? = null,
    val errorMessage: String? = null,
) {
    val isReady: Boolean get() = image != null && prompt.isNotBlank() && !isProcessing
    val hasVisionModel: Boolean get() = visionModel != null
}

@HiltViewModel
class VisionChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager,
    private val visionEngine: MediaPipeVisionEngine,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(VisionUiState())
    val state: StateFlow<VisionUiState> = _state.asStateFlow()

    init {
        refreshVisionModel()
    }

    fun refreshVisionModel() {
        viewModelScope.launch {
            val model = modelManager.getModelForRole(ModelRole.VISION)
            // A model assigned to Vision is only usable if it's a .task bundle;
            // a GGUF would fail deep in MediaPipe, so treat it as "not installed"
            // and surface its name so we can explain why.
            val usable = model != null && VisionModelSupport.isTaskBundle(model.localPath)
            _state.update {
                it.copy(
                    visionModel = if (usable) model else null,
                    incompatibleModelName = if (model != null && !usable) model.card.name else null,
                    isCheckingModel = false,
                )
            }
        }
    }

    fun onImagePicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val bitmap = withContext(ioDispatcher) { decodeBitmap(uri) }
            if (bitmap == null) {
                _state.update { it.copy(errorMessage = "Couldn't read that image.") }
                return@launch
            }
            _state.update {
                it.copy(
                    image = bitmap,
                    response = "",
                    errorMessage = null,
                )
            }
        }
    }

    fun onPromptChange(text: String) {
        _state.update { it.copy(prompt = text) }
    }

    fun clearImage() {
        _state.update {
            it.copy(image = null, response = "", errorMessage = null)
        }
    }

    fun onSubmit() {
        val current = _state.value
        val bitmap = current.image ?: return
        val prompt = current.prompt.trim().ifBlank { "Describe this image." }
        val model = current.visionModel ?: run {
            _state.update { it.copy(errorMessage = "No vision model assigned. Install one in Models.") }
            return
        }
        if (current.isProcessing) return

        _state.update {
            it.copy(
                isProcessing = true,
                response = "",
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            visionEngine.processStream(model.localPath, bitmap, prompt).collect { chunk ->
                when (chunk) {
                    is MiasResult.Success -> _state.update {
                        it.copy(response = it.response + chunk.data)
                    }
                    is MiasResult.Error -> _state.update {
                        it.copy(errorMessage = chunk.message, isProcessing = false)
                    }
                }
            }
            _state.update { it.copy(isProcessing = false) }
        }
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                BitmapFactory.decodeStream(stream, null, options)?.let { full ->
                    resize(full, MAX_IMAGE_DIM)
                }
            }
        }.getOrNull()
    }

    private fun resize(source: Bitmap, maxDim: Int): Bitmap {
        val largestSide = maxOf(source.width, source.height)
        if (largestSide <= maxDim) return source
        val scale = maxDim.toFloat() / largestSide
        val newW = (source.width * scale).toInt().coerceAtLeast(1)
        val newH = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, newW, newH, true)
    }

    companion object {
        private const val MAX_IMAGE_DIM: Int = 1024
    }
}
