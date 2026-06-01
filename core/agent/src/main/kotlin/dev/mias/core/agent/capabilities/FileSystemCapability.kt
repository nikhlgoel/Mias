package dev.mias.core.agent.capabilities

import android.os.Environment
import dev.mias.core.agent.AgentCapability
import dev.mias.core.agent.ToolParameter
import dev.mias.core.agent.storage.FileOp
import dev.mias.core.agent.storage.StorageAccessManager
import dev.mias.core.agent.storage.StorageGuard
import dev.mias.core.common.MiasResult
import dev.mias.core.common.runCatchingMias
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File system agent — reads, writes, lists, and manages files across the
 * device, within the limits enforced by [StorageGuard].
 *
 * Access tiers (handled automatically per Android version):
 *   - App workspace + app external storage: always read/write.
 *   - Shared storage (Downloads, Documents, DCIM, …): read always; write/
 *     delete when the user has enabled "All files access".
 *   - System paths and other apps' private data: always blocked.
 *
 * Paths may be:
 *   - relative (resolved inside the workspace), e.g. `notes/todo.txt`
 *   - a named root: `downloads/`, `documents/`, `pictures/`, `music/`,
 *     `movies/`, `dcim/`, `app_external/`, `workspace/`
 *   - absolute, e.g. `/storage/emulated/0/Download/report.pdf`
 */
@Singleton
class FileSystemCapability @Inject constructor(
    private val access: StorageAccessManager,
    private val guard: StorageGuard,
) : AgentCapability {

    override val name = "file_ops"

    override val description = "Read, write, list, delete, and inspect files on the device. " +
        "Use 'access_info' first if unsure what is reachable. Paths can be relative " +
        "(workspace), a named root (downloads/, documents/, pictures/, app_external/), " +
        "or absolute. System files and other apps' data are blocked for safety."

    override val parameters = listOf(
        ToolParameter("operation", "One of: read, write, append, list, delete, mkdir, info, access_info"),
        ToolParameter("path", "File or directory path (relative, named root, or absolute)", required = false),
        ToolParameter("content", "Content for write/append", required = false),
    )

    override suspend fun execute(input: Map<String, String>): MiasResult<String> {
        val operation = input["operation"]?.lowercase()
            ?: return MiasResult.Error("Missing parameter: operation")

        if (operation == "access_info") {
            return MiasResult.Success(access.describeAccess())
        }

        val rawPath = input["path"].orEmpty()
        return runCatchingMias {
            val target = resolve(rawPath)
            val op = operationKind(operation)
                ?: throw IllegalArgumentException("Unknown operation: $operation")

            val decision = guard.check(target.absolutePath, op)
            if (!decision.allowed) {
                return@runCatchingMias "Blocked: ${decision.reason}"
            }
            val file = decision.canonical ?: target

            when (operation) {
                "read" -> readFile(file, rawPath)
                "write" -> writeFile(file, requireContent(input), append = false, rawPath)
                "append" -> writeFile(file, requireContent(input), append = true, rawPath)
                "list" -> listDir(file, rawPath)
                "delete" -> deleteFile(file, rawPath)
                "mkdir" ->
                    if (file.mkdirs() || file.isDirectory) "Created directory $rawPath"
                    else "Could not create $rawPath"
                "info" -> infoFile(file)
                else -> throw IllegalArgumentException("Unknown operation: $operation")
            }
        }
    }

    private fun operationKind(operation: String): FileOp? = when (operation) {
        "read", "info" -> FileOp.READ
        "write", "append", "mkdir" -> FileOp.WRITE
        "delete" -> FileOp.DELETE
        "list" -> FileOp.LIST
        else -> null
    }

    private fun requireContent(input: Map<String, String>): String =
        input["content"] ?: throw IllegalArgumentException("Content required for this operation")

    private fun readFile(file: File, path: String): String {
        if (!file.exists()) throw IllegalStateException("File not found: $path")
        if (file.isDirectory) throw IllegalStateException("$path is a directory; use list")
        val text = file.readText()
        return if (text.length > MAX_READ_SIZE) text.take(MAX_READ_SIZE) + "\n...[truncated]" else text
    }

    private fun writeFile(file: File, content: String, append: Boolean, path: String): String {
        file.parentFile?.mkdirs()
        if (append) file.appendText(content) else file.writeText(content)
        return "${if (append) "Appended" else "Wrote"} ${content.length} chars to $path"
    }

    private fun listDir(dir: File, path: String): String {
        if (!dir.exists() || !dir.isDirectory) throw IllegalStateException("Not a directory: $path")
        return dir.listFiles()?.sortedBy { it.name }?.joinToString("\n") { e ->
            if (e.isDirectory) "${e.name}/" else "${e.name} (${e.length()} bytes)"
        }?.ifBlank { "(empty)" } ?: "(empty)"
    }

    private fun deleteFile(file: File, path: String): String {
        if (!file.exists()) throw IllegalStateException("File not found: $path")
        // Refuse recursive directory wipes — one file / empty dir at a time.
        if (file.isDirectory && (file.listFiles()?.isNotEmpty() == true)) {
            throw IllegalStateException("Directory not empty: $path (delete contents individually)")
        }
        return if (file.delete()) "Deleted $path" else "Could not delete $path"
    }

    private fun infoFile(file: File): String = if (!file.exists()) {
        "Does not exist."
    } else {
        buildString {
            append(if (file.isDirectory) "directory" else "file")
            append(", ${file.length()} bytes")
            append(if (file.canRead()) ", readable" else ", not readable")
            append(if (file.canWrite()) ", writable" else ", read-only")
        }
    }

    /** Resolve a relative / named-root / absolute path string to a File. */
    private fun resolve(path: String): File {
        if (path.startsWith("/")) return File(path)

        val slash = path.indexOf('/')
        val head = (if (slash >= 0) path.substring(0, slash) else path).lowercase()
        val tail = if (slash >= 0) path.substring(slash + 1) else ""

        val root = namedRoot(head)
        return if (root != null) File(root, tail) else File(access.workspaceDir, path)
    }

    @Suppress("DEPRECATION")
    private fun namedRoot(name: String): File? = when (name) {
        "workspace" -> access.workspaceDir
        "app_external" -> access.appExternalDir
        "downloads", "download" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        "documents" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        "pictures" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        "dcim" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        "music" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        "movies" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        else -> null
    }

    companion object {
        private const val MAX_READ_SIZE = 100_000 // 100KB
    }
}
