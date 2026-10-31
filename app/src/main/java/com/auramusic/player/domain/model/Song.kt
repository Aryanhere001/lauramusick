package com.auramusic.player.domain.model

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val contentUri: String,
    val artworkUri: String?,
    val filePath: String,
    val year: Int = 0,
    val isFavorite: Boolean = false
)

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val trackCount: Int,
    val year: Int
)

data class Artist(
    val name: String,
    val trackCount: Int,
    val albumCount: Int
)

data class Playlist(
    val id: Long,
    val name: String,
    val songs: List<Song> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
