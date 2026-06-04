package dev.mias.app.ui.knowledge

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mias.core.common.MiasResult
import dev.mias.core.data.preferences.MiasPreferences
import dev.mias.core.data.rag.Document
import dev.mias.core.data.rag.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class KnowledgeUiState(
    val documents: List<Document> = emptyList(),
    val useDocuments: Boolean = true,
    val embeddingReady: Boolean = true,
    val isIngesting: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class KnowledgeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DocumentRepository,
    private val preferences: MiasPreferences,
) : ViewModel() {

    private val _isIngesting = MutableStateFlow(false)
    private val _embeddingReady = MutableStateFlow(true)
    private val _status = MutableStateFlow<String?>(null)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<KnowledgeUiState> = combine(
        repository.observeDocuments(),
        preferences.prefsFlow,
        combine(_isIngesting, _embeddingReady) { ingesting, ready -> ingesting to ready },
        combine(_status, _error) { status, error -> status to error },
    ) { docs, prefs, ingestReady, statusError ->
        KnowledgeUiState(
            documents = docs,
            useDocuments = prefs.useDocuments,
            embeddingReady = ingestReady.second,
            isIngesting = ingestReady.first,
            statusMessage = statusError.first,
            errorMessage = statusError.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = KnowledgeUiState(),
    )

    init {
        refreshEmbeddingReady()
    }

    fun setUseDocuments(enabled: Boolean) {
        viewModelScope.launch { preferences.setUseDocuments(enabled) }
    }

    fun addDocument(uri: Uri) {
        viewModelScope.launch {
            _isIngesting.value = true
            try {
                val (name, text) = readUri(uri)
                if (text.isBlank()) {
                    _error.value = "Couldn't read any text from that file. Scanned or image-only " +
                        "PDFs aren't supported yet — try a text PDF, .txt or .md."
                    return@launch
                }
                when (val result = repository.ingest(name, text)) {
                    is MiasResult.Success ->
                        _status.value = "Added \"${result.data.name}\" (${result.data.chunkCount} passages)"
                    is MiasResult.Error -> _error.value = result.message
                }
                refreshEmbeddingReady()
            } catch (e: Exception) {
                _error.value = "Couldn't add that file: ${e.message}"
            } finally {
                _isIngesting.value = false
            }
        }
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch { repository.deleteDocument(id) }
    }

    fun clearMessages() {
        _status.value = null
        _error.value = null
    }

    private fun refreshEmbeddingReady() {
        viewModelScope.launch { _embeddingReady.value = repository.isEmbeddingReady() }
    }

    /**
     * Read a picked document's display name and text off the main thread.
     * Handles PDFs (PdfBox text extraction) and plain text / markdown.
     */
    private suspend fun readUri(uri: Uri): Pair<String, String> = withContext(Dispatchers.IO) {
        val name = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        }.getOrNull() ?: "Document"

        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val isPdf = mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)

        val text = runCatching {
            if (isPdf) {
                dev.mias.app.util.PdfTextExtractor.extract(context, uri)
            } else {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }
        }.getOrNull().orEmpty()

        name to text
    }
}
