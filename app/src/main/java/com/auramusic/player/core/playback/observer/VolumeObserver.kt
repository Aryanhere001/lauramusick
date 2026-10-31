package com.auramusic.player.core.playback.observer

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings

class VolumeObserver(
    private val context: Context,
    private val onVolumeChanged: (volume: Int) -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousVolume: Int = -1

    fun start() {
        previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            this
        )
    }

    fun stop() {
        context.contentResolver.unregisterContentObserver(this)
    }

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (currentVolume != previousVolume) {
            previousVolume = currentVolume
            onVolumeChanged(currentVolume)
        }
    }
}
