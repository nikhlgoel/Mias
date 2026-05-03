package dev.kid.core.resilience

import android.content.Context
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors device health: available RAM, storage, battery-aware scheduling.
 * Used to make smart decisions about model loading and background tasks.
 */
@Singleton
class DeviceHealthMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _health = MutableStateFlow(snapshot())
    val health: StateFlow<DeviceHealth> = _health.asStateFlow()

    /** Take a fresh snapshot of device health. */
    fun refresh(): DeviceHealth {
        val h = snapshot()
        _health.value = h
        return h
    }

    private fun snapshot(): DeviceHealth {
        val runtime = Runtime.getRuntime()
        val availableRamMb = (runtime.freeMemory() / (1024 * 1024)).toInt()
        val totalRamMb = (runtime.maxMemory() / (1024 * 1024)).toInt()

        val modelsDir = File(context.filesDir, "models")
        val storageFreeBytes = context.filesDir.usableSpace
        val storageUsedByModels = if (modelsDir.exists()) {
            modelsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else {
            0L
        }

        return DeviceHealth(
            availableRamMb = availableRamMb,
            totalRamMb = totalRamMb,
            storageFreeBytes = storageFreeBytes,
            storageUsedByModelsBytes = storageUsedByModels,
            cpuCores = runtime.availableProcessors(),
            processId = Process.myPid(),
        )
    }

    /** Can the device load a model requiring [requiredRamMb] RAM? */
    fun canLoadModel(requiredRamMb: Int): Boolean {
        return refresh().availableRamMb >= requiredRamMb
    }

    /** Is storage available for a download of [sizeBytes]? */
    fun hasStorageFor(sizeBytes: Long): Boolean {
        return refresh().storageFreeBytes > sizeBytes + STORAGE_BUFFER_BYTES
    }

    companion object {
        private const val STORAGE_BUFFER_BYTES = 500_000_000L // Keep 500MB free
    }
}

data class DeviceHealth(
    val availableRamMb: Int,
    val totalRamMb: Int,
    val storageFreeBytes: Long,
    val storageUsedByModelsBytes: Long,
    val cpuCores: Int,
    val processId: Int,
)
