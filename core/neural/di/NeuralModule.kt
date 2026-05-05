package dev.mias.core.neural.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.mias.core.neural.NeuralArchitectureFramework
import dev.mias.core.neural.assembly.AssemblyAbstractionLayer
import dev.mias.core.neural.context.ContextAnalyzer
import dev.mias.core.neural.growth.GrowthEngine

import javax.inject.Singleton

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
}
