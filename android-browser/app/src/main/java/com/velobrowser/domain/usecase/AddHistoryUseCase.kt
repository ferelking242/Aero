package com.velobrowser.domain.usecase

import com.velobrowser.domain.model.HistoryEntry
import com.velobrowser.domain.repository.HistoryRepository
import javax.inject.Inject

class AddHistoryUseCase @Inject constructor(
    private val historyRepository: HistoryRepository
) {
    suspend operator fun invoke(url: String, title: String, profileId: Long) {
        if (url.isBlank()) return
        val entry = HistoryEntry(url = url, title = title.ifBlank { url }, profileId = profileId)
        historyRepository.addEntry(entry)
    }
}
