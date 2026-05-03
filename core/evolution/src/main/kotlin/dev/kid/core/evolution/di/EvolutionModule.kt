package dev.kid.core.evolution.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * EvolutionEngine, ConversationAnalyzer, KnowledgeConsolidator, and SelfOptimizer
 * all use @Inject constructor + @Singleton — Hilt binds them automatically.
 *
 * EvolutionWorker is wired via @HiltWorker + AssistedInject.
 * EvolutionService is an @AndroidEntryPoint Service.
 */
@Module
@InstallIn(SingletonComponent::class)
object EvolutionModule
