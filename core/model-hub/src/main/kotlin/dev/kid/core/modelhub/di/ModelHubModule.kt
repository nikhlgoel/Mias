package dev.kid.core.modelhub.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.kid.core.modelhub.db.ModelDao
import dev.kid.core.modelhub.db.ModelHubDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ModelHubHttpClient

@Module
@InstallIn(SingletonComponent::class)
object ModelHubModule {

    @Provides
    @Singleton
    fun provideModelHubDatabase(
        @ApplicationContext context: Context,
    ): ModelHubDatabase = Room.databaseBuilder(
        context,
        ModelHubDatabase::class.java,
        "kid_model_hub.db",
    ).fallbackToDestructiveMigration()
        .build()

    @Provides
    @Singleton
    fun provideModelDao(db: ModelHubDatabase): ModelDao = db.modelDao()

    @Provides
    @Singleton
    @ModelHubHttpClient
    fun provideModelHubHttpClient(): HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                },
            )
        }
        engine {
            requestTimeout = 0 // No timeout for large model downloads
        }
    }
}
