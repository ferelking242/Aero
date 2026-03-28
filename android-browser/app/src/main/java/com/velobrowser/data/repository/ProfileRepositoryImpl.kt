package com.velobrowser.data.repository

import com.velobrowser.data.local.db.dao.ProfileDao
import com.velobrowser.data.local.db.entity.ProfileEntity
import com.velobrowser.domain.model.Profile
import com.velobrowser.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val profileDao: ProfileDao
) : ProfileRepository {

    override fun getAllProfiles(): Flow<List<Profile>> =
        profileDao.getAllProfiles().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getProfileById(id: Long): Profile? =
        profileDao.getProfileById(id)?.toDomain()

    override suspend fun insertProfile(profile: Profile): Long =
        profileDao.insertProfile(ProfileEntity.fromDomain(profile))

    override suspend fun updateProfile(profile: Profile) =
        profileDao.updateProfile(ProfileEntity.fromDomain(profile))

    override suspend fun deleteProfile(profileId: Long) =
        profileDao.deleteProfile(profileId)

    override suspend fun getDefaultProfile(): Profile? =
        profileDao.getDefaultProfile()?.toDomain()

    override suspend fun setDefaultProfile(profileId: Long) {
        profileDao.clearDefaultFlags()
        profileDao.setDefaultProfile(profileId)
    }
}
