/* Release #001 — first public build of Mias. Internal codename: "Kid". */
package dev.kid.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KidApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

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
