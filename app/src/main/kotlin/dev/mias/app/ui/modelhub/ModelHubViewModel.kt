package dev.mias.app.ui.modelhub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mias.core.common.MiasResult
import dev.mias.core.modelhub.manager.BrowseItem
import dev.mias.core.modelhub.manager.ModelManager
import dev.mias.core.modelhub.model.DownloadState
import dev.mias.core.modelhub.model.InstalledModel
import dev.mias.core.modelhub.model.ModelCard
import dev.mias.core.modelhub.model.ModelRole
import dev.mias.core.modelhub.registry.HuggingFaceRegistry
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelHubUiState(
    val installedModels: List<InstalledModel> = emptyList(),
    val catalogItems: List<BrowseItem> = emptyList(),
    val remoteResults: List<ModelCard> = emptyList(),
    val isSearchingRemote: Boolean = false,
    val downloadStates: Map<String, DownloadState> = emptyMap(),
    val activeSearchQuery: String = "",
    val selectedRole: ModelRole? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val storageUsedBytes: Long = 0L,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ModelHubViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val hfRegistry: HuggingFaceRegistry,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedRole = MutableStateFlow<ModelRole?>(null)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _remoteResults = MutableStateFlow<List<ModelCard>>(emptyList())
    private val _isSearchingRemote = MutableStateFlow(false)

    private var remoteSearchJob: Job? = null

    init {
        // Debounce remote search; fire when the user has typed at least 3 chars.
        viewModelScope.launch {
            _searchQuery
                .debounce(350L)
                .distinctUntilChanged()
                .onEach { q ->
                    if (q.length < 3) {
                        _remoteResults.value = emptyList()
                        _isSearchingRemote.value = false
                        remoteSearchJob?.cancel()
                    }
                }
                .filter { it.length >= 3 }
                .collect { q -> launchRemoteSearch(q) }
        }
    }

    private fun launchRemoteSearch(query: String) {
        remoteSearchJob?.cancel()
        remoteSearchJob = viewModelScope.launch {
            _isSearchingRemote.value = true
            when (val r = hfRegistry.search(query, limit = 8)) {
                is MiasResult.Success -> _remoteResults.value = r.data
                is MiasResult.Error -> {
                    _remoteResults.value = emptyList()
                    _errorMessage.value = "HF search failed: ${r.message}"
                }
            }
            _isSearchingRemote.value = false
        }
    }

    val uiState: StateFlow<ModelHubUiState> = combine(
        modelManager.installedModels,
        modelManager.activeDownloads,
        _searchQuery,
        _selectedRole,
        combine(_remoteResults, _isSearchingRemote) { results, loading -> results to loading },
    ) { installed, downloads, query, role, remote ->
        val (remoteResults, isSearching) = remote
        val catalog = modelManager.browseCurated()
        ModelHubUiState(
            installedModels = installed,
            catalogItems = catalog
                .filter { role == null || role in it.card.roles }
                .filter { query.isBlank() || it.card.name.contains(query, ignoreCase = true) },
            remoteResults = remoteResults
                .filter { role == null || role in it.roles },
            isSearchingRemote = isSearching,
            downloadStates = downloads,
            activeSearchQuery = query,
            selectedRole = role,
            storageUsedBytes = installed.sumOf { it.sizeOnDisk },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ModelHubUiState(),
    )

    fun onSearchQuery(query: String) { _searchQuery.value = query }
    fun onRoleFilter(role: ModelRole?) { _selectedRole.value = role }

    fun downloadModel(card: ModelCard) {
        _statusMessage.value = "Starting download: ${card.name}..."
        viewModelScope.launch {
            when (val result = modelManager.installModel(card)) {
                is MiasResult.Success -> _statusMessage.value = "Downloaded ${card.name}"
                is MiasResult.Error -> _errorMessage.value = result.message
            }
        }
    }

    fun pauseDownload(modelId: String) {
        viewModelScope.launch { modelManager.pauseDownload(modelId) }
    }

    fun resumeDownload(modelId: String) {
        viewModelScope.launch {
            when (val result = modelManager.resumeDownload(modelId)) {
                is MiasResult.Success -> _statusMessage.value = "Resumed"
                is MiasResult.Error -> _errorMessage.value = result.message
            }
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            when (val result = modelManager.uninstallModel(modelId)) {
                is MiasResult.Success -> _statusMessage.value = "Model removed"
                is MiasResult.Error -> _errorMessage.value = result.message
            }
        }
    }

    fun setModelRole(modelId: String, role: ModelRole) {
        viewModelScope.launch { modelManager.assignRole(modelId, role) }
    }

    fun autoAssignRoles() {
        viewModelScope.launch {
            when (val result = modelManager.autoAssignRoles()) {
                is MiasResult.Success -> _statusMessage.value = "Roles auto-assigned"
                is MiasResult.Error -> _errorMessage.value = result.message
            }
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
        _errorMessage.value = null
    }
}
