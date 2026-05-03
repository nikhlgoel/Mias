package dev.kid.core.language.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kid.core.language.IntentExtractor
import dev.kid.core.language.RegexIntentExtractor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LanguageModule {

    @Binds
    @Singleton
    abstract fun bindIntentExtractor(impl: RegexIntentExtractor): IntentExtractor
}
