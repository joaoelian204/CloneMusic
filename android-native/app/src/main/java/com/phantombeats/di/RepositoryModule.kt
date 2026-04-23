package com.phantombeats.di

import com.phantombeats.data.repository.ClientStreamResolver
import com.phantombeats.data.repository.SongRepositoryImpl
import com.phantombeats.domain.repository.SongRepository
import com.phantombeats.domain.repository.StreamResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSongRepository(
        songRepositoryImpl: SongRepositoryImpl
    ): SongRepository

    @Binds
    @Singleton
    abstract fun bindStreamResolver(
        resolverImpl: ClientStreamResolver
    ): StreamResolver

}