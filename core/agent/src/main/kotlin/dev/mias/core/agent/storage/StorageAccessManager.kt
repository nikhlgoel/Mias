package dev.mias.core.agent.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Knows what storage the app can actually touch on the *current* device and
 * Android version, and how to ask for more.
 *
 * Tiers (lowest → highest), all supported from minSdk 30 up to the latest:
 *   1. **App sandbox** — `filesDir` and `getExternalFilesDir`. Always RW, no
 *      permission. The agent's safe default workspace lives here.
 *   2. **Media collections** — Downloads / Pictures / etc. via MediaStore.
 *      Readable with the `READ_MEDIA_*` runtime permissions (API 33+) or
 *      `READ_EXTERNAL_STORAGE` (API ≤32).
 *   3. **All-files access** — `MANAGE_EXTERNAL_STORAGE`. Broad RW over shared
 *      storage. User must enable it in Settings (cannot be a runtime dialog).
 *      Even then the OS still blocks `/system`, other apps' `/Android/data` &
 *      `/Android/obb`, and other packages' private dirs.
 */
@Singleton
class StorageAccessManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Always-writable workspace inside the app sandbox. */
    val workspaceDir: File by lazy {
        File(context.filesDir, "workspace").also { it.mkdirs() }
    }

    /** App-private external dir (RW without any permission on all versions). */
    val appExternalDir: File? get() = context.getExternalFilesDir(null)

    /** Shared-storage root, e.g. `/storage/emulated/0`. */
    @Suppress("DEPRECATION")
    val sharedStorageRoot: File get() = Environment.getExternalStorageDirectory()

    /**
     * True when the user has granted "All files access" (broad shared-storage
     * RW). On API 30+ this is `Environment.isExternalStorageManager()`.
     */
    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            false
        }

    /**
     * Intent that opens the system page where the user toggles "All files
     * access" for this app. Caller launches it from an Activity. Null below
     * API 30 (not applicable to our minSdk, kept defensive).
     */
    fun allFilesAccessSettingsIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    /** Runtime media-read permissions to request for the running OS version. */
    fun mediaReadPermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
        )
        else -> listOf("android.permission.READ_EXTERNAL_STORAGE")
    }

    /** Human-readable summary of what the agent can currently reach. */
    fun describeAccess(): String = buildString {
        append("App workspace: read/write. ")
        append("App external storage: read/write. ")
        if (hasAllFilesAccess()) {
            append("Shared storage (Downloads, Documents, media, …): read/write (All files access granted).")
        } else {
            append("Shared storage: media is readable; broad read/write needs ")
            append("\"All files access\" enabled for Mias in system settings.")
        }
    }
}
