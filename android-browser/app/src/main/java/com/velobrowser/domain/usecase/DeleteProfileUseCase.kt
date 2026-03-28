package com.velobrowser.domain.usecase

import com.velobrowser.domain.repository.ProfileRepository
import javax.inject.Inject

class DeleteProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(profileId: Long) {
        require(profileId != 1L) { "Cannot delete the default profile" }
        profileRepository.deleteProfile(profileId)
    }
}
