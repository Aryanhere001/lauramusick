package com.auramusic.player.core.audiofx

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EqualizerManager @Inject constructor() {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    data class BandInfo(
        val index: Short,
        val centerFreqHz: Int,
        val minMilliBels: Short,
        val maxMilliBels: Short,
        var currentMilliBels: Short
    )

    private val _bands = MutableStateFlow<List<BandInfo>>(emptyList())
    val bands: StateFlow<List<BandInfo>> = _bands.asStateFlow()

    private val _bassStrength = MutableStateFlow(0)
    val bassStrength: StateFlow<Int> = _bassStrength.asStateFlow()

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    fun attachToAudioSession(audioSessionId: Int) {
        release()
        if (audioSessionId == 0) return

        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = _isEnabled.value
            }
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = _isEnabled.value
                if (strengthSupported) {
                    setStrength(_bassStrength.value.toShort())
                }
            }

            // Read band parameters
            equalizer?.let { eq ->
                val numBands = eq.numberOfBands
                val minLevel = eq.bandLevelRange[0]
                val maxLevel = eq.bandLevelRange[1]
                val bandList = (0 until numBands).map { i ->
                    val shortIndex = i.toShort()
                    BandInfo(
                        index = shortIndex,
                        centerFreqHz = eq.getCenterFreq(shortIndex) / 1000,
                        minMilliBels = minLevel,
                        maxMilliBels = maxLevel,
                        currentMilliBels = eq.getBandLevel(shortIndex)
                    )
                }
                _bands.value = bandList
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setBandLevel(bandIndex: Short, milliBels: Short) {
        equalizer?.setBandLevel(bandIndex, milliBels)
        _bands.value = _bands.value.map { band ->
            if (band.index == bandIndex) band.copy(currentMilliBels = milliBels) else band
        }
    }

    fun setBassBoost(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _bassStrength.value = clamped
        if (bassBoost?.strengthSupported == true) {
            bassBoost?.setStrength(clamped.toShort())
        }
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        equalizer?.enabled = enabled
        bassBoost?.enabled = enabled
    }

    fun release() {
        equalizer?.release()
        equalizer = null
        bassBoost?.release()
        bassBoost = null
    }
}
