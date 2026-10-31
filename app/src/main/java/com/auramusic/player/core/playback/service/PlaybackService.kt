package com.auramusic.player.core.playback.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.auramusic.player.core.playback.engine.ExoCrossfadeAudioEngine
import com.auramusic.player.core.playback.observer.VolumeObserver
import com.auramusic.player.core.playback.receiver.AudioDeviceManager
import com.auramusic.player.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var audioEngine: ExoCrossfadeAudioEngine

    @Inject
    lateinit var audioDeviceManager: AudioDeviceManager

    private var mediaSession: MediaSession? = null
    private var volumeObserver: VolumeObserver? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // 1. Configure audio attributes for music playback
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        audioEngine.setAudioAttributes(audioAttributes, true)

        // 2. Open MainActivity on notification tap
        val activityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 3. Initialize MediaSession with primary player instance
        mediaSession = MediaSession.Builder(this, audioEngine.primaryPlayer)
            .setSessionActivity(pendingIntent)
            .setCallback(MediaSessionCallback())
            .build()

        // 4. Register Audio Device & Noisy receivers
        audioDeviceManager.register(
            onBecomingNoisy = { audioEngine.pause() },
            onBluetoothConnected = {
                if (audioEngine.hasActiveQueue() && audioEngine.shouldAutoResumeOnBluetooth()) {
                    audioEngine.play()
                }
            }
        )

        // 5. Register Volume 0 auto-pause observer
        volumeObserver = VolumeObserver(this) { currentVolume ->
            if (currentVolume == 0 && audioEngine.isPlaying()) {
                audioEngine.autoPauseDueToZeroVolume()
            } else if (currentVolume > 0 && audioEngine.wasPausedDueToZeroVolume()) {
                audioEngine.resumeFromZeroVolume()
            }
        }.also { it.start() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        volumeObserver?.stop()
        audioDeviceManager.unregister()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        audioEngine.release()
        super.onDestroy()
    }

    private inner class MediaSessionCallback : MediaSession.Callback {
        // Handle custom Media3 session commands or intercept player commands
    }
}
