package dev.mias.core.agent.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What the agent wants to do with a path. */
enum class FileOp { READ, WRITE, DELETE, LIST }

/** Result of a guard check. */
data class GuardDecision(val allowed: Boolean, val canonical: File?, val reason: String)

/**
 * The "don't wreck the phone" layer. Every file operation the agent requests
 * is classified here before it runs.
 *
 * Defense in depth — the OS already blocks system partitions and other apps'
 * private data without root, but this guard adds a software policy so the
 * agent also cannot:
 *   - touch system / other-app paths (belt-and-suspenders),
 *   - escape its allowed roots via `..` traversal,
 *   - destroy the app's OWN data (the model DB, downloaded models, the
 *     conversation store) — only its workspace inside the sandbox is writable.
 *
 * Read is permissive (you can read anything the OS lets you); WRITE and
 * DELETE are gated: allowed only inside known-safe roots, and additionally
 * require All-files access for shared storage.
 */
@Singleton
class StorageGuard @Inject constructor(
    @ApplicationContext private val context: Context,
    private val access: StorageAccessManager,
) {

    fun check(path: String, op: FileOp): GuardDecision {
        if (path.isBlank()) {
            return GuardDecision(false, null, "Empty path.")
        }

        val canonical = runCatching { File(path).canonicalFile }.getOrNull()
            ?: return GuardDecision(false, null, "Path could not be resolved.")
        val abs = canonical.absolutePath

        // 1. Never-touch system / other-app paths.
        PROTECTED_PREFIXES.firstOrNull { abs.startsWith(it) }?.let { p ->
            // The app's OWN external dir lives under /Android/data — allow that.
            val ownExternal = access.appExternalDir?.canonicalFile?.absolutePath
            if (!(p == ANDROID_DATA && ownExternal != null && abs.startsWith(ownExternal))) {
                return GuardDecision(false, canonical, "Protected system or other-app path is off-limits.")
            }
        }

        // 2. Protect the app's own internal data (DB, models, conversations).
        //    Everything under filesDir EXCEPT the agent workspace is read-only.
        val filesRoot = context.filesDir.canonicalFile.absolutePath
        val workspace = access.workspaceDir.canonicalFile.absolutePath
        if (abs.startsWith(filesRoot) && !abs.startsWith(workspace)) {
            return if (op == FileOp.READ || op == FileOp.LIST) {
                GuardDecision(true, canonical, "App internal data (read-only).")
            } else {
                GuardDecision(false, canonical, "The app's own data is protected from modification.")
            }
        }

        // 3. Always-safe roots: workspace + app external dir.
        if (abs.startsWith(workspace) || isUnderAppExternal(abs)) {
            return GuardDecision(true, canonical, "App-owned storage.")
        }

        // 4. Shared storage (Downloads, Documents, DCIM, …).
        val sharedRoot = runCatching { access.sharedStorageRoot.canonicalFile.absolutePath }.getOrNull()
        if (sharedRoot != null && abs.startsWith(sharedRoot)) {
            return when (op) {
                FileOp.READ, FileOp.LIST ->
                    GuardDecision(true, canonical, "Shared storage (read).")
                FileOp.WRITE, FileOp.DELETE ->
                    if (access.hasAllFilesAccess()) {
                        GuardDecision(true, canonical, "Shared storage (All files access granted).")
                    } else {
                        GuardDecision(
                            false, canonical,
                            "Writing/deleting in shared storage needs \"All files access\" " +
                                "enabled for Mias in system settings.",
                        )
                    }
            }
        }

        // 5. Anything else (unknown absolute path) is denied.
        return GuardDecision(false, canonical, "Path is outside the allowed storage areas.")
    }

    private fun isUnderAppExternal(abs: String): Boolean {
        val ext = access.appExternalDir?.canonicalFile?.absolutePath ?: return false
        return abs.startsWith(ext)
    }

    companion object {
        private const val ANDROID_DATA = "/storage/emulated/0/Android/data"

        /** Absolute prefixes the agent must never read, write, or delete. */
        private val PROTECTED_PREFIXES = listOf(
            "/system", "/vendor", "/product", "/apex", "/proc", "/sys", "/dev",
            "/init", "/sbin", "/etc", "/cache", "/config", "/mnt/vendor",
            "/data/data", "/data/user", "/data/system", "/data/misc", "/data/app",
            ANDROID_DATA, // other apps' private external data
            "/storage/emulated/0/Android/obb", // other apps' OBB
            "/sdcard/Android/data", "/sdcard/Android/obb",
        )
    }
}
