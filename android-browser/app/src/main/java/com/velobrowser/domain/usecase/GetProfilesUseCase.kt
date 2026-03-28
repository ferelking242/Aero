package com.velobrowser.domain.usecase

import com.velobrowser.domain.model.Profile
import com.velobrowser.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetProfilesUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    operator fun invoke(): Flow<List<Profile>> = profileRepository.getAllProfiles()
}
