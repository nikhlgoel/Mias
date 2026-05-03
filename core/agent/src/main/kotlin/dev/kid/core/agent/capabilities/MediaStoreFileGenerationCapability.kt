package dev.kid.core.agent.capabilities

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kid.core.agent.AgentCapability
import dev.kid.core.agent.ParameterType
import dev.kid.core.agent.ToolParameter
import dev.kid.core.common.KidResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreFileGenerationCapability @Inject constructor(
    @ApplicationContext private val context: Context,
) : AgentCapability {

    override val name: String = "file_generation"
    
    override val description: String =
        "Generates a file (txt, md, json, csv, html) directly into the user's public Documents folder so they can access it instantly."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "filename",
            description = "The name of the file to create, must include extension (e.g. report.md, data.csv)",
            required = true,
            type = ParameterType.STRING,
        ),
        ToolParameter(
            name = "content",
            description = "The actual precise text content to write into the file",
            required = true,
            type = ParameterType.STRING,
        ),
    )

    override suspend fun execute(input: Map<String, String>): KidResult<String> = withContext(Dispatchers.IO) {
        val filename = input["filename"]
            ?: return@withContext KidResult.Error("filename parameter is required")
        val content = input["content"]
            ?: return@withContext KidResult.Error("content parameter is required")
            
        val mimeType = getMimeTypeFromExtension(filename)

        try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/KidExports")
            }

            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                ?: return@withContext KidResult.Error("Failed to create target file URI in MediaStore.")

            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
            }
            
            KidResult.Success("Successfully generated $filename in Documents/KidExports. It is immediately visible to the user.")
        } catch (e: Exception) {
            KidResult.Error("Failed to generate file: ${e.message}", e)
        }
    }

    private fun getMimeTypeFromExtension(filename: String): String {
        return when {
            filename.endsWith(".md") -> "text/markdown"
            filename.endsWith(".txt") -> "text/plain"
            filename.endsWith(".json") -> "application/json"
            filename.endsWith(".csv") -> "text/csv"
            filename.endsWith(".html") -> "text/html"
            else -> "application/octet-stream"
        }
    }
}
