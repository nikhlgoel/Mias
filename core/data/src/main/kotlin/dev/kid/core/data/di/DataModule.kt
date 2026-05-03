package dev.kid.core.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.kid.core.data.ConversationRepository
import dev.kid.core.data.db.KidDatabase
import dev.kid.core.data.db.dao.ConversationDao
import dev.kid.core.data.db.dao.HindsightDao
import dev.kid.core.data.repository.ConversationRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KidDatabase =
        Room.databaseBuilder(context, KidDatabase::class.java, "kid-db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideConversationDao(db: KidDatabase): ConversationDao = db.conversationDao()

    @Provides
    fun provideHindsightDao(db: KidDatabase): HindsightDao = db.hindsightDao()

    @Provides
    @Singleton
    fun provideConversationRepository(impl: ConversationRepositoryImpl): ConversationRepository =
        impl
}
