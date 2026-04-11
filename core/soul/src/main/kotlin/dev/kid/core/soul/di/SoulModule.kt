package dev.kid.core.soul.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * SoulEngine, SentimentAnalyzer, and LoraBlendPolicy all use
 * @Inject constructor + @Singleton — Hilt binds them automatically.
 * This module is kept as a placeholder for future manual providers.
 */
@Module
@InstallIn(SingletonComponent::class)
object SoulModule
