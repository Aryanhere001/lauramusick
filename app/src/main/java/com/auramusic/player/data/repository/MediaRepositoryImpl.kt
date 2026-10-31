package com.auramusic.player.data.repository

import com.auramusic.player.data.local.dao.PlaylistDao
import com.auramusic.player.data.local.dao.SongDao
import com.auramusic.player.data.local.entity.PlaylistEntity
import com.auramusic.player.data.local.entity.SongEntity
import com.auramusic.player.data.scanner.MediaStoreScanner
import com.auramusic.player.domain.model.Playlist
import com.auramusic.player.domain.model.Song
import com.auramusic.player.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val scanner: MediaStoreScanner,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao
) : MediaRepository {

    override fun getSongs(): Flow<List<Song>> {
        return songDao.getAllSongs().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getFavorites(): Flow<List<Song>> {
        return songDao.getFavoriteSongs().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun syncWithMediaStore() {
        val scanned = scanner.scanAudioFiles()
        val entities = scanned.map { it.toEntity() }
        songDao.insertSongs(entities)
    }

    override suspend fun toggleFavorite(songId: Long, isFavorite: Boolean) {
        songDao.updateFavorite(songId, isFavorite)
    }

    override suspend fun incrementPlayCount(songId: Long) {
        songDao.incrementPlayCount(songId)
    }

    override fun searchTracks(query: String): Flow<List<Song>> {
        return songDao.searchSongs(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    private fun SongEntity.toDomain() = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        durationMs = durationMs,
        contentUri = contentUri,
        artworkUri = artworkUri,
        filePath = filePath,
        year = year,
        isFavorite = isFavorite
    )

    private fun Song.toEntity() = SongEntity(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        durationMs = durationMs,
        contentUri = contentUri,
        artworkUri = artworkUri,
        filePath = filePath,
        year = year,
        isFavorite = isFavorite
    )
}
