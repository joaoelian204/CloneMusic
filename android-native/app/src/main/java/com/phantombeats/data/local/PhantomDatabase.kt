package com.phantombeats.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.phantombeats.data.local.dao.StreamCacheDao
import com.phantombeats.data.local.dao.SearchDao
import com.phantombeats.data.local.entity.StreamCacheEntity
import com.phantombeats.data.local.entity.SongEntity
import com.phantombeats.data.local.entity.SongFtsEntity

@Database(
    entities = [
        StreamCacheEntity::class,
        SongEntity::class,
        SongFtsEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PhantomDatabase : RoomDatabase() {
    abstract fun streamCacheDao(): StreamCacheDao
    abstract fun searchDao(): SearchDao
}
