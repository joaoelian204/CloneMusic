package com.phantombeats.di

import android.content.Context
import androidx.room.Room
import com.phantombeats.data.extractors.NewPipeExtractorAdapter
import com.phantombeats.data.local.PhantomDatabase
import com.phantombeats.data.local.dao.StreamCacheDao
import com.phantombeats.data.repository.RoomStreamCacheRepository
import com.phantombeats.domain.repository.StreamCacheRepository
import com.phantombeats.domain.repository.StreamExtractor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppDatabaseModule {

    @Provides
    @Singleton
    fun providePhantomDatabase(@ApplicationContext context: Context): PhantomDatabase {
        return Room.databaseBuilder(
            context,
            PhantomDatabase::class.java,
            "phantom_db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StreamCacheRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindStreamCacheRepository(
        roomStreamCacheRepository: RoomStreamCacheRepository
    ): StreamCacheRepository
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ExtractorModule {
    
    // @IntoSet instruye a Hilt a armar una Colección (Set) de implementaciones
    // de StreamExtractor para inyectarlas directamente al Orquestador
    @Binds
    @IntoSet
    abstract fun bindNewPipeExtractor(
        newPipeExtractorAdapter: NewPipeExtractorAdapter
    ): StreamExtractor

    // Si tuvieras un SoundCloudExtractorAdapter, pondrías otro @Binds @IntoSet aquí.
}
