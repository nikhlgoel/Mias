package dev.mias.core.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.mias.core.data.ConversationRepository
import dev.mias.core.data.db.MiasDatabase
import dev.mias.core.data.db.dao.ConversationDao
import dev.mias.core.data.db.dao.HindsightDao
import dev.mias.core.data.repository.ConversationRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MiasDatabase =
        Room.databaseBuilder(context, MiasDatabase::class.java, "mias-db")
            .addMigrations(
                MiasDatabase.MIGRATION_1_2,
                MiasDatabase.MIGRATION_2_3,
                MiasDatabase.MIGRATION_3_4,
            )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideConversationDao(db: MiasDatabase): ConversationDao = db.conversationDao()

    @Provides
    fun provideHindsightDao(db: MiasDatabase): HindsightDao = db.hindsightDao()

    @Provides
    fun provideDocumentDao(db: MiasDatabase): dev.mias.core.data.db.dao.DocumentDao =
        db.documentDao()

    @Provides
    @Singleton
    fun provideConversationRepository(impl: ConversationRepositoryImpl): ConversationRepository =
        impl

    @Provides
    @Singleton
    fun provideDocumentRepository(
        impl: dev.mias.core.data.rag.DocumentRepositoryImpl,
    ): dev.mias.core.data.rag.DocumentRepository = impl
}
