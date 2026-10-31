package com.auramusic.player.domain.usecase

import com.auramusic.player.domain.model.Song
import javax.inject.Inject

enum class RepeatMode {
    OFF, ALL, ONE
}

class ManageQueueUseCase @Inject constructor() {

    fun reorderQueue(queue: List<Song>, fromIndex: Int, toIndex: Int): List<Song> {
        if (fromIndex !in queue.indices || toIndex !in queue.indices) return queue
        val mutable = queue.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return mutable
    }

    fun toggleShuffle(
        currentQueue: List<Song>,
        originalQueue: List<Song>,
        currentSong: Song?,
        isShuffleActive: Boolean
    ): Pair<List<Song>, Int> {
        if (currentSong == null || currentQueue.size <= 1) {
            return currentQueue to 0
        }

        return if (!isShuffleActive) {
            // Enable shuffle: current song stays at index 0, rest are shuffled
            val remaining = currentQueue.filter { it.id != currentSong.id }.shuffled()
            val newQueue = listOf(currentSong) + remaining
            newQueue to 0
        } else {
            // Restore original list order
            val restoredIndex = originalQueue.indexOfFirst { it.id == currentSong.id }.coerceAtLeast(0)
            originalQueue to restoredIndex
        }
    }
}
