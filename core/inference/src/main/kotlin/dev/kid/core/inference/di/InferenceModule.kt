package dev.kid.core.inference.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kid.core.inference.InferenceEngine
import dev.kid.core.inference.engine.LlamaCppEngine
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
    fun provideEmbeddingProvider(engine: dev.kid.core.inference.engine.EmbeddingEngine): dev.kid.core.common.model.EmbeddingProvider = engine
}
