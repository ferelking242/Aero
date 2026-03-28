package com.velobrowser.domain.usecase

import com.velobrowser.domain.repository.HistoryRepository
import javax.inject.Inject

class ClearHistoryUseCase @Inject constructor(
    private val historyRepository: HistoryRepository
) {
    suspend operator fun invoke(profileId: Long? = null) {
        if (profileId != null) {
            historyRepository.clearHistoryForProfile(profileId)
        } else {
            historyRepository.clearAllHistory()
        }
    }
}
