package com.auramusic.player.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.auramusic.player.data.local.dao.PlaylistDao
import com.auramusic.player.data.local.dao.SongDao
import com.auramusic.player.data.local.entity.PlaylistEntity
import com.auramusic.player.data.local.entity.PlaylistSongCrossRef
import com.auramusic.player.data.local.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
}
