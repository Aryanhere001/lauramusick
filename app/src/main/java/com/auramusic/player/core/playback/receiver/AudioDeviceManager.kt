package com.auramusic.player.core.playback.receiver

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioDeviceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var isRegistered = false
    private var onBecomingNoisyCallback: (() -> Unit)? = null
    private var onBluetoothConnectedCallback: (() -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    // Headphone unplugged: pause immediately to prevent public blasting
                    onBecomingNoisyCallback?.invoke()
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    // Bluetooth audio peripheral connected: auto-resume if configured
                    onBluetoothConnectedCallback?.invoke()
                }
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_DISCONNECTED)
                    if (state == BluetoothHeadset.STATE_CONNECTED) {
                        onBluetoothConnectedCallback?.invoke()
                    }
                }
            }
        }
    }

    fun register(
        onBecomingNoisy: () -> Unit,
        onBluetoothConnected: () -> Unit
    ) {
        if (isRegistered) return
        this.onBecomingNoisyCallback = onBecomingNoisy
        this.onBluetoothConnectedCallback = onBluetoothConnected

        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        isRegistered = true
    }

    fun unregister() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {}
        isRegistered = false
        onBecomingNoisyCallback = null
        onBluetoothConnectedCallback = null
    }
}
