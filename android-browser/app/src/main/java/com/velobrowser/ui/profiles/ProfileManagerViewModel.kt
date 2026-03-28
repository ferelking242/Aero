package com.velobrowser.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velobrowser.data.local.datastore.SettingsDataStore
import com.velobrowser.domain.model.Profile
import com.velobrowser.domain.usecase.CreateProfileUseCase
import com.velobrowser.domain.usecase.DeleteProfileUseCase
import com.velobrowser.domain.usecase.GetProfilesUseCase
import com.velobrowser.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileManagerViewModel @Inject constructor(
    private val getProfilesUseCase: GetProfilesUseCase,
    private val createProfileUseCase: CreateProfileUseCase,
    private val deleteProfileUseCase: DeleteProfileUseCase,
    private val profileRepository: ProfileRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val profiles: StateFlow<List<Profile>> = getProfilesUseCase()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeProfileId: StateFlow<Long> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.velobrowser.data.local.datastore.BrowserSettings())
        .let {
            kotlinx.coroutines.flow.MutableStateFlow(it.value.activeProfileId).also { mf ->
                viewModelScope.launch {
                    it.collect { settings -> mf.value = settings.activeProfileId }
                }
            }
        }

    fun createProfile(name: String, colorHex: String) {
        viewModelScope.launch {
            createProfileUseCase(name, colorHex)
        }
    }

    fun deleteProfile(profileId: Long) {
        viewModelScope.launch {
            try {
                deleteProfileUseCase(profileId)
            } catch (e: IllegalArgumentException) {
                // Cannot delete default profile — silently handled
            }
        }
    }

    fun switchToProfile(profileId: Long) {
        viewModelScope.launch {
            settingsDataStore.setActiveProfileId(profileId)
            // Clear cookies for the old session and load new profile's cookies
            android.webkit.CookieManager.getInstance().apply {
                flush()
                removeAllCookies(null)
            }
        }
    }

    fun renameProfile(profileId: Long, newName: String) {
        viewModelScope.launch {
            val profile = profileRepository.getProfileById(profileId) ?: return@launch
            profileRepository.updateProfile(profile.copy(name = newName.trim()))
        }
    }
}
