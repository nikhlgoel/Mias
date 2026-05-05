package dev.mias.core.language.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.mias.core.language.IntentExtractor
import dev.mias.core.language.RegexIntentExtractor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LanguageModule {

    @Binds
    @Singleton
    abstract fun bindIntentExtractor(impl: RegexIntentExtractor): IntentExtractor
}
