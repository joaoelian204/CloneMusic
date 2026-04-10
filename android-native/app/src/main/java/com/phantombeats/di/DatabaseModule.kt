package com.phantombeats.di

import android.content.Context
import com.phantombeats.data.local.AppDatabase
import com.phantombeats.data.local.dao.FavoriteDao
import com.phantombeats.data.local.dao.PlaylistDao
import com.phantombeats.data.local.dao.SearchHistoryDao
import com.phantombeats.data.local.dao.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideSongDao(database: AppDatabase): SongDao = database.songDao()     

    @Provides
    fun provideFavoriteDao(database: AppDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    fun providePlaylistDao(database: AppDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun provideSearchHistoryDao(database: AppDatabase): SearchHistoryDao = database.searchHistoryDao()

    @Provides
    fun provideSearchDao(database: com.phantombeats.data.local.PhantomDatabase): com.phantombeats.data.local.dao.SearchDao = database.searchDao()
}
