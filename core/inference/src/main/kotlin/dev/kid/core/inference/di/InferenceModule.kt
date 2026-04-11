package dev.kid.core.inference.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kid.core.inference.InferenceEngine
import dev.kid.core.inference.engine.GemmaLiteRtEngine
import dev.kid.core.inference.engine.OnnxInferenceEngine
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InferenceModule {

    @Provides
    @Singleton
    @Named("gemma")
    fun provideGemmaEngine(engine: GemmaLiteRtEngine): InferenceEngine = engine

    @Provides
    @Singleton
    @Named("mobilellm")
    fun provideMobileLlmEngine(engine: OnnxInferenceEngine): InferenceEngine = engine
}
