package dev.mias.app.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text

/**
 * Represents a single permission with description
 */
data class PermissionRequest(
    val permission: String,
    val title: String,
    val description: String,
    val isRequired: Boolean = true,
)

/**
 * Check if permission is already granted
 */
fun hasPermission(context: Context, permission: String): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

/**
 * Get all required app permissions
 */
fun getRequiredAppPermissions(): List<PermissionRequest> = buildList {
    add(
        PermissionRequest(
            permission = Manifest.permission.RECORD_AUDIO,
            title = "Microphone access",
            description = "Allow Mias to transcribe your voice on-device so you can " +
                "speak instead of type. Audio never leaves your phone.",
            isRequired = true,
        ),
    )
    add(
        PermissionRequest(
            permission = Manifest.permission.CAMERA,
            title = "Camera access",
            description = "Optional. Enables visual context — for example, asking " +
                "Mias about a photo or scene — once the vision pipeline is enabled.",
            isRequired = false,
        ),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(
            PermissionRequest(
                permission = Manifest.permission.POST_NOTIFICATIONS,
                title = "Notifications",
                description = "Optional. Lets Mias keep you informed about background " +
                    "model downloads and routine memory upkeep.",
                isRequired = false,
            ),
        )
    }
}

/**
 * Composable for requesting a single permission with explanation
 */
@Composable
fun PermissionRequestDialog(
    permissionRequest: PermissionRequest,
    onGranted: () -> Unit,
    onDenied: () -> Unit,
) {
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                onGranted()
            } else {
                onDenied()
            }
        },
    )
    
    AlertDialog(
        onDismissRequest = { },
        title = { Text(permissionRequest.title) },
        text = { Text(permissionRequest.description) },
        confirmButton = {
            Button(
                onClick = {
                    requestPermissionLauncher.launch(permissionRequest.permission)
                },
            ) {
                Text(if (permissionRequest.isRequired) "Continue" else "Allow")
            }
        },
        dismissButton = {
            Button(onClick = onDenied) {
                Text(if (permissionRequest.isRequired) "Not now" else "Not now")
            }
        },
    )
}

/**
 * Composable for handling permission flow on app startup
 */
@Composable
fun PermissionFlowHandler(
    context: Context,
    onAllPermissionsGranted: () -> Unit,
) {
    val permissions = remember { getRequiredAppPermissions() }
    val (currentIndex, setCurrentIndex) = remember { mutableStateOf(0) }
    val (show, setShow) = remember { mutableStateOf(false) }
    
    LaunchedEffect(currentIndex) {
        // Check if first permission needs to be shown
        if (currentIndex < permissions.size) {
            if (!hasPermission(context, permissions[currentIndex].permission)) {
                setShow(true)
            } else {
                setCurrentIndex(currentIndex + 1)
            }
        } else {
            onAllPermissionsGranted()
        }
    }
    
    if (show && currentIndex < permissions.size) {
        PermissionRequestDialog(
            permissionRequest = permissions[currentIndex],
            onGranted = {
                setShow(false)
                setCurrentIndex(currentIndex + 1)
            },
            onDenied = {
                setShow(false)
                if (permissions[currentIndex].isRequired) {
                    // Required permission denied - show message
                    // For now, continue to next
                }
                setCurrentIndex(currentIndex + 1)
            },
        )
    }
}
