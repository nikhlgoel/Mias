package dev.kid.app.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import dev.kid.app.permissions.PermissionFlowHandler
import dev.kid.app.ui.theme.KidTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            )
        } catch (e: Exception) {
            Log.w("MainActivity", "Edge-to-edge setup failed: ${e.message}")
        }
        setContent {
            KidTheme {
                var ready by remember { mutableStateOf(false) }
                PermissionFlowHandler(
                    context = this,
                    onAllPermissionsGranted = { ready = true },
                )
                if (ready) {
                    KidNavHost()
                }
            }
        }
    }
}
