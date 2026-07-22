package dev.mias.mobile

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.facebook.react.defaults.DefaultReactNativeHost
import dagger.hilt.android.HiltAndroidApp
import dev.mias.core.modelhub.bootstrap.ModelBootstrapper
import dev.mias.mobile.bootstrap.ModelWarmup
import dev.mias.mobile.bootstrap.StartupPreferences
import dev.mias.mobile.bootstrap.ToolBootstrapper
import dev.mias.mobile.bridge.MiasNativePackage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hilt host + React host + app-startup wiring (the last relocated from the
 * deleted Compose app at the S7 cutover):
 *  - ToolBootstrapper → populate the ReAct tool registry (else tool calls fail),
 *  - StartupPreferences → apply the persisted HuggingFace token,
 *  - ModelBootstrapper → assign roles to already-installed models,
 *  - ModelWarmup → warm the embedding model for fast RAG/Hindsight,
 *  - HiltWorkerFactory → so the background self-learning EvolutionWorker runs.
 */
@HiltAndroidApp
class MainApplication : Application(), ReactApplication, Configuration.Provider {

  @Inject lateinit var workerFactory: HiltWorkerFactory
  @Inject lateinit var toolBootstrapper: ToolBootstrapper
  @Inject lateinit var startupPreferences: StartupPreferences
  @Inject lateinit var modelBootstrapper: ModelBootstrapper
  @Inject lateinit var modelWarmup: ModelWarmup

  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  override val reactNativeHost: ReactNativeHost =
      object : DefaultReactNativeHost(this) {
        override fun getPackages(): List<ReactPackage> =
            PackageList(this).packages.apply { add(MiasNativePackage()) }

        override fun getJSMainModuleName(): String = "index"
        override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG
        override val isNewArchEnabled: Boolean = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
        override val isHermesEnabled: Boolean = BuildConfig.IS_HERMES_ENABLED
      }

  override val reactHost: ReactHost
    get() = getDefaultReactHost(applicationContext, reactNativeHost)

  override fun onCreate() {
    super.onCreate()
    loadReactNative(this)

    startupPreferences.start()
    toolBootstrapper.register()
    appScope.launch {
      runCatching { modelBootstrapper.prepareFirstRunModels(autoDownload = false) }
      modelWarmup.warm()
    }
  }

  // WorkManager on-demand init: supply the Hilt worker factory so the
  // @HiltWorker EvolutionWorker can be instantiated. Guarded for early init.
  override val workManagerConfiguration: Configuration
    get() =
        if (::workerFactory.isInitialized) {
          Configuration.Builder()
              .setWorkerFactory(workerFactory)
              .setMinimumLoggingLevel(Log.INFO)
              .build()
        } else {
          Configuration.Builder().setMinimumLoggingLevel(Log.INFO).build()
        }
}
