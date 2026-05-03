package dev.kid.core.agent.capabilities

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kid.core.agent.AgentCapability
import dev.kid.core.agent.ToolParameter
import dev.kid.core.common.KidResult
import dev.kid.core.common.runCatchingKid
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File system agent — enables Kid to read, write, list, and delete files
 * within a sandboxed workspace directory.
 */
@Singleton
class FileSystemCapability @Inject constructor(
    @ApplicationContext private val context: Context,
) : AgentCapability {

    private val workspaceDir: File by lazy {
        File(context.filesDir, "workspace").also { it.mkdirs() }
    }

    override val name = "file_ops"

    override val description = "Perform file operations: read, write, list, delete files " +
        "in the AI workspace. All paths are relative to the workspace root."

    override val parameters = listOf(
        ToolParameter("operation", "One of: read, write, list, delete, exists"),
        ToolParameter("path", "Relative file path within workspace"),
        ToolParameter("content", "File content (required for write)", required = false),
    )

    override suspend fun execute(input: Map<String, String>): KidResult<String> {
        val operation = input["operation"]
            ?: return KidResult.Error("Missing parameter: operation")
        val path = input["path"] ?: ""

        return runCatchingKid {
            val target = resolveSecure(path)

            when (operation.lowercase()) {
                "read" -> {
                    if (!target.exists()) throw IllegalStateException("File not found: $path")
                    if (target.length() > MAX_READ_SIZE) {
                        target.readText().take(MAX_READ_SIZE.toInt()) + "\n...[truncated]"
                    } else {
                        target.readText()
                    }
                }

                "write" -> {
                    val content = input["content"]
                        ?: throw IllegalArgumentException("Content required for write")
                    target.parentFile?.mkdirs()
                    target.writeText(content)
                    "Written ${content.length} chars to $path"
                }

                "list" -> {
                    val dir = if (path.isBlank()) workspaceDir else target
                    if (!dir.exists() || !dir.isDirectory) {
                        throw IllegalStateException("Not a directory: $path")
                    }
                    dir.listFiles()?.joinToString("\n") { entry ->
                        val suffix = if (entry.isDirectory) "/" else " (${entry.length()} bytes)"
                        "${entry.name}$suffix"
                    } ?: "(empty)"
                }

                "delete" -> {
                    if (!target.exists()) throw IllegalStateException("File not found: $path")
                    target.delete()
                    "Deleted $path"
                }

                "exists" -> {
                    if (target.exists()) "true (${target.length()} bytes)" else "false"
                }

                // External egress is intentionally blocked at capability level.
                // Any future export/share flow must go through ManualAccessConsentGate.
                "export", "share", "backup" -> {
                    "Blocked: external data access requires explicit manual owner consent"
                }

                else -> throw IllegalArgumentException("Unknown operation: $operation")
            }
        }
    }

    /**
     * Resolve path securely — prevent path traversal attacks.
     * All paths must resolve within the workspace directory.
     */
    private fun resolveSecure(relativePath: String): File {
        val resolved = File(workspaceDir, relativePath).canonicalFile
        require(resolved.path.startsWith(workspaceDir.canonicalPath)) {
            "Path traversal blocked: $relativePath"
        }
        return resolved
    }

    companion object {
        private const val MAX_READ_SIZE = 100_000L // 100KB
    }
}
