package com.auramusic.player.core.playback.engine

import android.animation.ValueAnimator
import android.content.Context
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.auramusic.player.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Advanced audio playback engine utilizing dual ExoPlayer instances
 * (activePlayer and standbyPlayer) to achieve true gapless & equal-power crossfading.
 */
@Singleton
class ExoCrossfadeAudioEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Dual player instances
    val playerA: ExoPlayer = ExoPlayer.Builder(context).build()
    val playerB: ExoPlayer = ExoPlayer.Builder(context).build()

    // Pointers for active track vs crossfade target
    var activePlayer: ExoPlayer = playerA
        private set
    var standbyPlayer: ExoPlayer = playerB
        private set

    val primaryPlayer: ExoPlayer
        get() = activePlayer

    // Crossfade configuration (1s to 12s, default 4s)
    var crossfadeDurationMs: Long = 4000L
    var isCrossfadeEnabled: Boolean = true
    var autoResumeOnBluetooth: Boolean = true

    private var crossfadeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var pausedByZeroVolume = false
    private val _currentTrack = MutableStateFlow<Song?>(null)
    val currentTrack: StateFlow<Song?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgressMs = MutableStateFlow(0L)
    val playbackProgressMs: StateFlow<Long> = _playbackProgressMs.asStateFlow()

    init {
        setupPlayerListeners()
        startPositionPolling()
    }

    @OptIn(UnstableApi::class)
    fun setAudioAttributes(attributes: AudioAttributes, handleAudioFocus: Boolean) {
        playerA.setAudioAttributes(attributes, handleAudioFocus)
        playerB.setAudioAttributes(attributes, handleAudioFocus)
    }

    /**
     * Plays a given song immediately on the active player with reset volume.
     */
    fun playSongImmediate(song: Song) {
        crossfadeJob?.cancel()
        _currentTrack.value = song

        // Stop standby player if active
        standbyPlayer.stop()
        standbyPlayer.clearMediaItems()

        activePlayer.volume = 1.0f
        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.contentUri)
            .build()

        activePlayer.setMediaItem(mediaItem)
        activePlayer.prepare()
        activePlayer.play()
    }

    /**
     * Smoothly crossfades from current track to next track over configured duration.
     * Uses equal-power sinusoidal volume ramp:
     * activeVolume = cos(progress * pi / 2)
     * standbyVolume = sin(progress * pi / 2)
     */
    fun transitionCrossfadeTo(nextSong: Song) {
        if (!isCrossfadeEnabled || crossfadeDurationMs <= 0L) {
            playSongImmediate(nextSong)
            return
        }

        crossfadeJob?.cancel()
        crossfadeJob = scope.launch {
            val outgoingPlayer = activePlayer
            val incomingPlayer = standbyPlayer

            val mediaItem = MediaItem.Builder()
                .setMediaId(nextSong.id.toString())
                .setUri(nextSong.contentUri)
                .build()

            // Prepare incoming player at 0.0f volume
            incomingPlayer.volume = 0.0f
            incomingPlayer.setMediaItem(mediaItem)
            incomingPlayer.prepare()
            incomingPlayer.play()

            _currentTrack.value = nextSong

            val steps = 50
            val intervalMs = crossfadeDurationMs / steps

            for (i in 0..steps) {
                val progress = i.toFloat() / steps.toFloat()
                // Equal-power crossfade curve
                val fadeOut = cos(progress * (PI / 2)).toFloat().coerceIn(0f, 1f)
                val fadeIn = sin(progress * (PI / 2)).toFloat().coerceIn(0f, 1f)

                outgoingPlayer.volume = fadeOut
                incomingPlayer.volume = fadeIn

                delay(intervalMs)
            }

            // Finish transition: halt and clean outgoing player
            outgoingPlayer.stop()
            outgoingPlayer.clearMediaItems()
            outgoingPlayer.volume = 1.0f

            // Swap player pointers
            activePlayer = incomingPlayer
            standbyPlayer = outgoingPlayer
        }
    }

    fun play() {
        pausedByZeroVolume = false
        activePlayer.play()
    }

    fun pause() {
        activePlayer.pause()
    }

    fun isPlaying(): Boolean = activePlayer.isPlaying

    fun autoPauseDueToZeroVolume() {
        pausedByZeroVolume = true
        activePlayer.pause()
    }

    fun wasPausedDueToZeroVolume(): Boolean = pausedByZeroVolume

    fun resumeFromZeroVolume() {
        pausedByZeroVolume = false
        activePlayer.play()
    }

    fun shouldAutoResumeOnBluetooth(): Boolean = autoResumeOnBluetooth

    fun hasActiveQueue(): Boolean = _currentTrack.value != null

    fun seekTo(positionMs: Long) {
        activePlayer.seekTo(positionMs)
    }

    private fun setupPlayerListeners() {
        playerA.addListener(createPlayerListener(playerA))
        playerB.addListener(createPlayerListener(playerB))
    }

    private fun createPlayerListener(player: ExoPlayer) = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (player == activePlayer) {
                _isPlaying.value = isPlaying
            }
        }
    }

    private fun startPositionPolling() {
        scope.launch {
            while (isActive) {
                if (activePlayer.isPlaying) {
                    _playbackProgressMs.value = activePlayer.currentPosition
                }
                delay(200)
            }
        }
    }

    fun release() {
        crossfadeJob?.cancel()
        scope.cancel()
        playerA.release()
        playerB.release()
    }
}
