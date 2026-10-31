package com.auramusic.player.domain.repository

import com.auramusic.player.domain.model.Playlist
import com.auramusic.player.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun getSongs(): Flow<List<Song>>
    fun getFavorites(): Flow<List<Song>>
    suspend fun syncWithMediaStore()
    suspend fun toggleFavorite(songId: Long, isFavorite: Boolean)
    suspend fun incrementPlayCount(songId: Long)
    fun searchTracks(query: String): Flow<List<Song>>
}
