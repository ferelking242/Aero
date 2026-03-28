package com.velobrowser.domain.repository

import com.velobrowser.domain.model.Profile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun getAllProfiles(): Flow<List<Profile>>
    suspend fun getProfileById(id: Long): Profile?
    suspend fun insertProfile(profile: Profile): Long
    suspend fun updateProfile(profile: Profile)
    suspend fun deleteProfile(profileId: Long)
    suspend fun getDefaultProfile(): Profile?
    suspend fun setDefaultProfile(profileId: Long)
}
