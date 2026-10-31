package com.auramusic.player.di

import android.content.Context
import androidx.room.Room
import com.auramusic.player.data.local.dao.PlaylistDao
import com.auramusic.player.data.local.dao.SongDao
import com.auramusic.player.data.local.db.MusicDatabase
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
    fun provideMusicDatabase(@ApplicationContext context: Context): MusicDatabase {
        return Room.databaseBuilder(
            context,
            MusicDatabase::class.java,
            "music_database"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideSongDao(database: MusicDatabase): SongDao {
        return database.songDao()
    }

    @Provides
    @Singleton
    fun providePlaylistDao(database: MusicDatabase): PlaylistDao {
        return database.playlistDao()
    }
}
