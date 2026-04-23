package com.phantombeats.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.phantombeats.data.local.dao.FavoriteDao
import com.phantombeats.data.local.dao.PlaylistDao
import com.phantombeats.data.local.dao.SearchHistoryDao
import com.phantombeats.data.local.dao.SongDao
import com.phantombeats.data.local.entity.FavoriteEntity
import com.phantombeats.data.local.entity.PlaylistEntity
import com.phantombeats.data.local.entity.PlaylistSongCrossRef
import com.phantombeats.data.local.entity.SearchHistoryEntity
import com.phantombeats.data.local.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        SearchHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "phantom_beats_offline_db"
                )
                // Aquí en el futuro se pueden agregar Migraciones de DB o Callbacks
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
