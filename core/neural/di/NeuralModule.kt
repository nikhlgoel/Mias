package dev.kid.core.neural.di*

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kid.core.neural.NeuralArchitectureFramework
import dev.kid.core.neural.assembly.AssemblyAbstractionLayer
import dev.kid.core.neural.context.ContextAnalyzer
import dev.kid.core.neural.growth.GrowthEngine
import dev.kid.core.neural.quantum.QuantumBridge
import javax.inject.Singleton*

/**
 * Hilt Module for Neural Architecture Framework.
 *
 * Provides:
 * - NeuralArchitectureFramework (main entry point)
 * - AssemblyAbstractionLayer (deep model access)
 * - ContextAnalyzer (deeper understanding)
 * - GrowthEngine (continuous improvement)
 * - QuantumBridge (future-ready quantum support)
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NeuralModule {

    @Binds
    @Singleton
    abstract fun bindNeuralArchitecture(
        impl: NeuralArchitectureFramework,
    ): NeuralArchitectureFramework

    @Binds
    @Singleton
    abstract fun bindAssemblyLayer(
        impl: AssemblyAbstractionLayer,
    ): AssemblyAbstractionLayer

    @Binds
    @Singleton
    abstract fun bindContextAnalyzer(
        impl: ContextAnalyzer,
    ): ContextAnalyzer

    @Binds
    @Singleton
    abstract fun bindGrowthEngine(
        impl: GrowthEngine,
    ): GrowthEngine

    @Binds
    @Singleton
    abstract fun bindQuantumBridge(
        impl: QuantumBridge,
    ): QuantumBridge
}
