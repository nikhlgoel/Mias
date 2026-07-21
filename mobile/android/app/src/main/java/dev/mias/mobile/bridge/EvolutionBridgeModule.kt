package dev.mias.mobile.bridge

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.mias.core.evolution.EvolutionEngine
import dev.mias.core.evolution.service.EvolutionWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Self-learning (evolution) control for the RN app. The heavy work — consolidate
 * memories, analyze conversations, optimize — runs in `core/evolution`, on a
 * WorkManager schedule when idle. This exposes a manual "run now" + status, and
 * (re)schedules the background job.
 */
class EvolutionBridgeModule(
    private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface EvolutionEntryPoint {
        fun engine(): EvolutionEngine
    }

    private val engine by lazy {
        EntryPointAccessors.fromApplication(
            reactContext.applicationContext,
            EvolutionEntryPoint::class.java,
        ).engine()
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun getName(): String = NAME

    @ReactMethod
    fun isRunning(promise: Promise) {
        promise.resolve(runCatching { engine.isRunning.value }.getOrDefault(false))
    }

    /** Run a full self-learning cycle now; resolves a summary of the session. */
    @ReactMethod
    fun runNow(promise: Promise) {
        scope.launch {
            try {
                val session = engine.runFullCycle()
                promise.resolve(
                    JSONObject()
                        .put("id", session.id)
                        .put("success", session.isSuccess)
                        .put("insights", session.totalInsightsGained)
                        .put("tasks", JSONArray(session.completedTasks.map { it.name }))
                        .put("errors", JSONArray(session.errors))
                        .toString(),
                )
            } catch (t: Throwable) {
                promise.reject("evolution_run", t.message, t)
            }
        }
    }

    /** Ensure the periodic background evolution job is scheduled. */
    @ReactMethod
    fun scheduleBackground(promise: Promise) {
        runCatching { EvolutionWorker.scheduleIfNotRunning(reactContext.applicationContext) }
        promise.resolve(null)
    }

    override fun invalidate() {
        scope.cancel()
        super.invalidate()
    }

    companion object {
        const val NAME = "MiasEvolution"
    }
}
