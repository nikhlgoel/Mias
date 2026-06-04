package dev.mias.core.inference.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.mias.core.inference.InferenceEngine
import dev.mias.core.inference.engine.LlamaCppEngine
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InferenceModule {

    @Provides
    @Singleton
    @Named("primaryEngine")
    fun providePrimaryEngine(engine: LlamaCppEngine): InferenceEngine = engine

    @Provides
    @Singleton
    @Named("survivalEngine")
    fun provideSurvivalEngine(engine: LlamaCppEngine): InferenceEngine = engine

    @Provides
    @Singleton
    fun provideEmbeddingProvider(
        provider: dev.mias.core.inference.engine.AutoEmbeddingProvider,
    ): dev.mias.core.common.model.EmbeddingProvider = provider
}
