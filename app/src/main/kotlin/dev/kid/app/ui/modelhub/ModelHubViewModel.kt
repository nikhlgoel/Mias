package dev.kid.app.ui.modelhub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kid.core.common.KidResult
import dev.kid.core.modelhub.manager.BrowseItem
import dev.kid.core.modelhub.manager.ModelManager
import dev.kid.core.modelhub.model.DownloadState
import dev.kid.core.modelhub.model.InstalledModel
import dev.kid.core.modelhub.model.ModelCard
import dev.kid.core.modelhub.model.ModelRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelHubUiState(
    val installedModels: List<InstalledModel> = emptyList(),
    val catalogItems: List<BrowseItem> = emptyList(),
    val downloadStates: Map<String, DownloadState> = emptyMap(),
    val activeSearchQuery: String = "",
    val selectedRole: ModelRole? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val storageUsedBytes: Long = 0L,
)

@HiltViewModel
class ModelHubViewModel @Inject constructor(
    private val modelManager: ModelManager,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedRole = MutableStateFlow<ModelRole?>(null)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ModelHubUiState> = combine(
        modelManager.installedModels,
        modelManager.activeDownloads,
        _searchQuery,
        _selectedRole,
    ) { installed, downloads, query, role ->
        val catalog = modelManager.browseCurated()
        ModelHubUiState(
            installedModels = installed,
            catalogItems = catalog
                .filter { role == null || role in it.card.roles }
                .filter { query.isBlank() || it.card.name.contains(query, ignoreCase = true) },
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
                is KidResult.Success -> _statusMessage.value = "Downloaded ${card.name}"
                is KidResult.Error -> _errorMessage.value = result.message
            }
        }
    }

    fun pauseDownload(modelId: String) {
        viewModelScope.launch { modelManager.pauseDownload(modelId) }
    }

    fun resumeDownload(modelId: String) {
        viewModelScope.launch { modelManager.resumePendingDownloads() }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            when (val result = modelManager.uninstallModel(modelId)) {
                is KidResult.Success -> _statusMessage.value = "Model removed"
                is KidResult.Error -> _errorMessage.value = result.message
            }
        }
    }

    fun setModelRole(modelId: String, role: ModelRole) {
        viewModelScope.launch { modelManager.assignRole(modelId, role) }
    }

    fun autoAssignRoles() {
        viewModelScope.launch {
            when (val result = modelManager.autoAssignRoles()) {
                is KidResult.Success -> _statusMessage.value = "Roles auto-assigned"
                is KidResult.Error -> _errorMessage.value = result.message
            }
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
        _errorMessage.value = null
    }
}
