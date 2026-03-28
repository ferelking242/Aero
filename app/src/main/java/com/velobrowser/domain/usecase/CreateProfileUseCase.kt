package com.velobrowser.domain.usecase

import com.velobrowser.domain.model.Profile
import com.velobrowser.domain.repository.ProfileRepository
import javax.inject.Inject

class CreateProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(name: String, colorHex: String): Long {
        require(name.isNotBlank()) { "Profile name cannot be blank" }
        val profile = Profile(name = name.trim(), colorHex = colorHex)
        return profileRepository.insertProfile(profile)
    }
}
