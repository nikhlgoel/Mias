package dev.kid.core.speech.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Speech module — SpeechEngine uses @Inject constructor + @Singleton,
 * so Hilt binds it automatically as a singleton.
 *
 * SpeechViewModel is @HiltViewModel and is created per-screen by the
 * Hilt ViewModel factory — no manual binding needed here.
 *
 * This module is the required Hilt entry point for the :core:speech module.
 * Without @InstallIn, Hilt does not scan the module for bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
object SpeechModule
