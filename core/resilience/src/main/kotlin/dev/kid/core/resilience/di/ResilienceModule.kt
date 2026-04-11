package dev.kid.core.resilience.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kid.core.resilience.CheckpointManager
import dev.kid.core.resilience.ConnectivityMonitor
import dev.kid.core.resilience.DeviceHealthMonitor
import dev.kid.core.resilience.OperationQueue
import dev.kid.core.resilience.RetryExecutor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ResilienceModule {

    // All classes use constructor injection via @Inject @Singleton,
    // so no explicit @Provides needed unless binding interfaces.
    // This module exists for future interface bindings.

    @Provides
    @Singleton
    fun provideResilienceFacade(
        connectivity: ConnectivityMonitor,
        health: DeviceHealthMonitor,
        checkpoint: CheckpointManager,
        queue: OperationQueue,
        retry: RetryExecutor,
    ): ResilienceFacade = ResilienceFacade(connectivity, health, checkpoint, queue, retry)
}

/**
 * Convenience facade aggregating all resilience components.
 * Inject this when you need broad resilience capabilities.
 */
data class ResilienceFacade(
    val connectivity: ConnectivityMonitor,
    val health: DeviceHealthMonitor,
    val checkpoint: CheckpointManager,
    val queue: OperationQueue,
    val retry: RetryExecutor,
)
