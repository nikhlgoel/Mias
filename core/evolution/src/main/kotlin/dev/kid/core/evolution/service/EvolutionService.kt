package dev.kid.core.evolution.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.kid.core.evolution.EvolutionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * EvolutionService — background "consciousness" daemon.
 *
 * Runs as an Android Foreground Service when the user enables
 * Background Evolution mode. Periodically triggers the EvolutionEngine
 * to consolidate memories, analyze patterns, and self-optimize.
 *
 * Shows a minimal notification ("Kid is thinking in the background").
 *
 * Lifecycle: started/stopped by the app via Settings toggle.
 * Uses a SupervisorJob scope so individual task failures don't kill the service.
 */
@AndroidEntryPoint
class EvolutionService : Service() {

    @Inject lateinit var evolutionEngine: EvolutionEngine

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Thinking in the background…"))
        startEvolutionLoop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startEvolutionLoop() {
        serviceScope.launch {
            while (true) {
                try {
                    updateNotification("Consolidating memories…")
                    evolutionEngine.consolidateMemoriesOnly()
                    updateNotification("Analyzing patterns…")
                    evolutionEngine.analyzeOnly()
                    updateNotification("Thinking in the background…")
                } catch (_: Exception) {
                    // Individual failures are non-fatal
                }
                delay(EVOLUTION_INTERVAL_MS)
            }
        }
    }

    private fun updateNotification(status: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun buildNotification(status: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kid")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Kid Background Thinking",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "Shown while Kid evolves in the background"
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "kid_evolution_bg"
        private const val NOTIFICATION_ID = 42_001
        private const val EVOLUTION_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6 hours

        fun start(context: Context) {
            context.startForegroundService(Intent(context, EvolutionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EvolutionService::class.java))
        }
    }
}
