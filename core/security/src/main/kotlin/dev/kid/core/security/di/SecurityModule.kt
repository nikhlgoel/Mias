package dev.kid.core.security.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * BiometricGate, ExclusivityLock, and ZkVault all use
 * @Inject constructor + @Singleton — Hilt binds them automatically.
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule
