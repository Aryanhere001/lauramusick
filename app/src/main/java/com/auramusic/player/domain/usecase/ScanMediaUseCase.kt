package com.auramusic.player.domain.usecase

import com.auramusic.player.domain.repository.MediaRepository
import javax.inject.Inject

class ScanMediaUseCase @Inject constructor(
    private val repository: MediaRepository
) {
    suspend operator fun invoke() {
        repository.syncWithMediaStore()
    }
}
