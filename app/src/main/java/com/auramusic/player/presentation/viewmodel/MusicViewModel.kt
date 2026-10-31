package com.auramusic.player.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auramusic.player.core.audiofx.EqualizerManager
import com.auramusic.player.core.playback.engine.ExoCrossfadeAudioEngine
import com.auramusic.player.domain.model.Song
import com.auramusic.player.domain.repository.MediaRepository
import com.auramusic.player.domain.usecase.ManageQueueUseCase
import com.auramusic.player.domain.usecase.RepeatMode
import com.auramusic.player.domain.usecase.ScanMediaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val songs: List<Song> = emptyList(),
    val filteredSongs: List<Song> = emptyList(),
    val currentQueue: List<Song> = emptyList(),
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val progressMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val crossfadeDurationSec: Int = 4,
    val autoPauseOnZeroVolume: Boolean = true,
    val autoResumeOnBluetooth: Boolean = true,
    val isScanning: Boolean = false,
    val searchQuery: String = "",
    val searchHistory: List<String> = emptyList(),
    val isMiniPlayerVisible: Boolean = false,
    val isNowPlayingExpanded: Boolean = false
)

sealed interface PlayerEvent {
    data class PlaySong(val song: Song) : PlayerEvent
    object TogglePlayPause : PlayerEvent
    object SkipNext : PlayerEvent
    object SkipPrevious : PlayerEvent
    data class SeekTo(val positionMs: Long) : PlayerEvent
    object ToggleShuffle : PlayerEvent
    object ToggleRepeat : PlayerEvent
    data class ToggleFavorite(val songId: Long) : PlayerEvent
    data class SetCrossfadeDuration(val seconds: Int) : PlayerEvent
    data class Search(val query: String) : PlayerEvent
    object ClearSearch : PlayerEvent
    data class ReorderQueue(val fromIndex: Int, val toIndex: Int) : PlayerEvent
    object ScanStorage : PlayerEvent
    object ExpandNowPlaying : PlayerEvent
    object CollapseNowPlaying : PlayerEvent
}

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val scanMediaUseCase: ScanMediaUseCase,
    private val manageQueueUseCase: ManageQueueUseCase,
    private val audioEngine: ExoCrossfadeAudioEngine,
    val equalizerManager: EqualizerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var originalQueue: List<Song> = emptyList()

    init {
        observeRepository()
        observeAudioEngine()
        scanStorage()
    }

    private fun observeRepository() {
        viewModelScope.launch {
            repository.getSongs().collect { songs ->
                _uiState.update { it.copy(songs = songs, filteredSongs = songs) }
                if (originalQueue.isEmpty() && songs.isNotEmpty()) {
                    originalQueue = songs
                    _uiState.update { it.copy(currentQueue = songs) }
                }
            }
        }
    }

    private fun observeAudioEngine() {
        viewModelScope.launch {
            combine(
                audioEngine.currentTrack,
                audioEngine.isPlaying,
                audioEngine.playbackProgressMs
            ) { track, isPlaying, progress ->
                Triple(track, isPlaying, progress)
            }.collect { (track, isPlaying, progress) ->
                _uiState.update {
                    it.copy(
                        currentSong = track,
                        isPlaying = isPlaying,
                        progressMs = progress,
                        durationMs = track?.durationMs ?: 0L,
                        isMiniPlayerVisible = track != null
                    )
                }
            }
        }
    }

    fun onEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.PlaySong -> {
                audioEngine.transitionCrossfadeTo(event.song)
            }
            PlayerEvent.TogglePlayPause -> {
                if (audioEngine.isPlaying()) audioEngine.pause() else audioEngine.play()
            }
            PlayerEvent.SkipNext -> skipNext()
            PlayerEvent.SkipPrevious -> skipPrevious()
            is PlayerEvent.SeekTo -> audioEngine.seekTo(event.positionMs)
            PlayerEvent.ToggleShuffle -> toggleShuffle()
            PlayerEvent.ToggleRepeat -> toggleRepeat()
            is PlayerEvent.ToggleFavorite -> toggleFavorite(event.songId)
            is PlayerEvent.SetCrossfadeDuration -> {
                val clamped = event.seconds.coerceIn(1, 12)
                audioEngine.crossfadeDurationMs = clamped * 1000L
                _uiState.update { it.copy(crossfadeDurationSec = clamped) }
            }
            is PlayerEvent.Search -> filterSearch(event.query)
            PlayerEvent.ClearSearch -> {
                _uiState.update { it.copy(searchQuery = "", filteredSongs = it.songs) }
            }
            is PlayerEvent.ReorderQueue -> {
                val updated = manageQueueUseCase.reorderQueue(
                    _uiState.value.currentQueue,
                    event.fromIndex,
                    event.toIndex
                )
                _uiState.update { it.copy(currentQueue = updated) }
            }
            PlayerEvent.ScanStorage -> scanStorage()
            PlayerEvent.ExpandNowPlaying -> _uiState.update { it.copy(isNowPlayingExpanded = true) }
            PlayerEvent.CollapseNowPlaying -> _uiState.update { it.copy(isNowPlayingExpanded = false) }
        }
    }

    private fun skipNext() {
        val state = _uiState.value
        val currentIndex = state.currentQueue.indexOfFirst { it.id == state.currentSong?.id }
        if (currentIndex == -1) return

        if (currentIndex + 1 < state.currentQueue.size) {
            val nextSong = state.currentQueue[currentIndex + 1]
            audioEngine.transitionCrossfadeTo(nextSong)
        } else if (state.repeatMode == RepeatMode.ALL && state.currentQueue.isNotEmpty()) {
            audioEngine.transitionCrossfadeTo(state.currentQueue.first())
        }
    }

    private fun skipPrevious() {
        val state = _uiState.value
        if (state.progressMs > 3000L) {
            audioEngine.seekTo(0)
            return
        }
        val currentIndex = state.currentQueue.indexOfFirst { it.id == state.currentSong?.id }
        if (currentIndex > 0) {
            audioEngine.transitionCrossfadeTo(state.currentQueue[currentIndex - 1])
        } else if (state.repeatMode == RepeatMode.ALL && state.currentQueue.isNotEmpty()) {
            audioEngine.transitionCrossfadeTo(state.currentQueue.last())
        }
    }

    private fun toggleShuffle() {
        val currentState = _uiState.value
        val (shuffledQueue, _) = manageQueueUseCase.toggleShuffle(
            currentState.currentQueue,
            originalQueue,
            currentState.currentSong,
            currentState.isShuffle
        )
        _uiState.update { it.copy(currentQueue = shuffledQueue, isShuffle = !currentState.isShuffle) }
    }

    private fun toggleRepeat() {
        _uiState.update {
            val nextMode = when (it.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            it.copy(repeatMode = nextMode)
        }
    }

    private fun toggleFavorite(songId: Long) {
        val song = _uiState.value.songs.find { it.id == songId } ?: return
        viewModelScope.launch {
            repository.toggleFavorite(songId, !song.isFavorite)
        }
    }

    private fun filterSearch(query: String) {
        _uiState.update { state ->
            val trimmed = query.trim()
            val filtered = if (trimmed.isEmpty()) {
                state.songs
            } else {
                state.songs.filter {
                    it.title.contains(trimmed, ignoreCase = true) ||
                    it.artist.contains(trimmed, ignoreCase = true) ||
                    it.album.contains(trimmed, ignoreCase = true)
                }
            }
            val newHistory = if (trimmed.isNotEmpty() && !state.searchHistory.contains(trimmed)) {
                listOf(trimmed) + state.searchHistory.take(7)
            } else state.searchHistory

            state.copy(searchQuery = query, filteredSongs = filtered, searchHistory = newHistory)
        }
    }

    private fun scanStorage() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            try {
                scanMediaUseCase()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uiState.update { it.copy(isScanning = false) }
            }
        }
    }
}
