package dev.mias.core.inference.vision

/**
 * Pure compatibility checks for the MediaPipe vision path. Kept free of any
 * MediaPipe/Android types so it can be unit-tested and called from the app
 * layer to gate the UI *before* a doomed native load.
 *
 * MediaPipe's `LlmInference` vision path requires a `.task` bundle. A GGUF (or
 * any other text-model format) handed to it fails deep in native code with an
 * opaque `RET_CHECK ... Error building tflite model` — so we refuse it up front.
 */
object VisionModelSupport {

    /** True only for a MediaPipe `.task` bundle (the format vision needs). */
    fun isTaskBundle(localPath: String): Boolean =
        localPath.trim().endsWith(TASK_EXTENSION, ignoreCase = true)

    const val TASK_EXTENSION = ".task"
}
