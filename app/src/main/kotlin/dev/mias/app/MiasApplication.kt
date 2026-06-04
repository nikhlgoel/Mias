/* Release #001 — first public build of Mias. Internal codename: "Mias". */
package dev.mias.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.mias.app.bootstrap.ModelWarmup
import dev.mias.app.bootstrap.PreferencesBootstrapper
import dev.mias.app.bootstrap.ToolBootstrapper
import dev.mias.core.modelhub.bootstrap.ModelBootstrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MiasApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var preferencesBootstrapper: PreferencesBootstrapper

    @Inject
    lateinit var modelBootstrapper: ModelBootstrapper

    @Inject
    lateinit var toolBootstrapper: ToolBootstrapper

    @Inject
    lateinit var modelWarmup: ModelWarmup

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        preferencesBootstrapper.start()
        // Wire agent capabilities into the ReAct tool registry so the agent
        // loop can actually execute actions.
        toolBootstrapper.register()
        // Reassign roles for already-installed models on every cold start.
        // This catches models that were installed before the role-assignment
        // bug was fixed, and any future cases where assignment was skipped.
        appScope.launch {
            modelBootstrapper.prepareFirstRunModels(autoDownload = false)
            // After roles are settled, warm the models so the first message is
            // fast. Runs on the IO-bound default scope, never the main thread.
            modelWarmup.warm()
        }
    }

    // WorkManager queries this property at init time. If Hilt has not
    // yet injected workerFactory, accessing the lateinit will crash.
    // Guard with isInitialized to provide a safe fallback.
    override val workManagerConfiguration: Configuration
        get() {
            return if (::workerFactory.isInitialized) {
                Configuration.Builder()
                    .setWorkerFactory(workerFactory)
                    .setMinimumLoggingLevel(Log.INFO)
                    .build()
            } else {
                // Safe fallback — WorkManager will use default factory.
                // HiltWorkers won't be available until Hilt finishes injecting,
                // but this prevents the crash during early init.
                Configuration.Builder()
                    .setMinimumLoggingLevel(Log.INFO)
                    .build()
            }
        }
}
