package dev.kid.app.vision

import android.content.Context
import android.graphics.Bitmap
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.kid.core.common.KidResult
import dev.kid.core.data.hindsight.HindsightMemory
import dev.kid.core.inference.vision.VisionEngine

/**
 * Background worker that periodically checks the camera (or screen representation)
 * and processes frames using VisionEngine.
 * Inserts observed facts into HindsightMemory.
 */
@HiltWorker
class VisionWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val visionEngine: VisionEngine,
    private val hindsightMemory: HindsightMemory
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // In a real app, bind to CameraX and extract a frame here.
            // For now, we stub a mock Bitmap of 1x1 pixels to proxy camera data.
            val mockBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

            // Ensure VisionEngine is loaded
            visionEngine.loadModel()

            // Process internal ML visual logic
            val result = visionEngine.processFrame(mockBitmap)

            if (result is KidResult.Success) {
                // Record the fact into semantic memory dynamically
                val description = result.data
                hindsightMemory.storeFact(
                    content = "Visual Observation: $description",
                    sourceUserId = "vision_daemon",
                    conversationId = null
                )
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
