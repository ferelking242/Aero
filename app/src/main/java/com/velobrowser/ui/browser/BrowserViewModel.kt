package com.velobrowser.ui.browser

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velobrowser.core.tabs.TabManager
import com.velobrowser.data.local.datastore.BrowserSettings
import com.velobrowser.data.local.datastore.SettingsDataStore
import com.velobrowser.domain.model.BookmarkEntry
import com.velobrowser.domain.model.BrowserTab
import com.velobrowser.domain.repository.BookmarkRepository
import com.velobrowser.domain.repository.HistoryRepository
import com.velobrowser.domain.usecase.AddHistoryUseCase
import com.velobrowser.utils.UrlUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val tabManager: TabManager,
    private val settingsDataStore: SettingsDataStore,
    private val historyRepository: HistoryRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val addHistoryUseCase: AddHistoryUseCase
) : ViewModel() {

    val tabs: StateFlow<List<BrowserTab>> = tabManager.tabs
    val activeTabId: StateFlow<String?> = tabManager.activeTabId
    val settings: StateFlow<BrowserSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, BrowserSettings())

    private val _loadUrlEvent = MutableSharedFlow<String>()
    val loadUrlEvent: SharedFlow<String> = _loadUrlEvent.asSharedFlow()

    private val _showTabsEvent = MutableSharedFlow<Unit>()
    val showTabsEvent: SharedFlow<Unit> = _showTabsEvent.asSharedFlow()

    private val _pageProgress = MutableStateFlow(0)
    val pageProgress: StateFlow<Int> = _pageProgress.asStateFlow()

    private val _currentUrl = MutableStateFlow("")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _currentTitle = MutableStateFlow("")
    val currentTitle: StateFlow<String> = _currentTitle.asStateFlow()

    private val _isSecure = MutableStateFlow(false)
    val isSecure: StateFlow<Boolean> = _isSecure.asStateFlow()

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked: StateFlow<Boolean> = _isBookmarked.asStateFlow()

    val activeTab: BrowserTab?
        get() = tabManager.activeTab

    val activeProfileId: Long
        get() = settings.value.activeProfileId

    init {
        if (tabManager.hasNoTabs()) {
            openNewTab()
        }
    }

    fun openNewTab(url: String = "", incognito: Boolean = false) {
        val tab = tabManager.openNewTab(
            url = url,
            isIncognito = incognito,
            profileId = activeProfileId
        )
        if (url.isNotEmpty()) {
            viewModelScope.launch { _loadUrlEvent.emit(url) }
        }
    }

    fun closeTab(tabId: String) {
        tabManager.closeTab(tabId)
        if (tabManager.hasNoTabs()) {
            openNewTab()
        }
    }

    fun switchToTab(tabId: String) {
        tabManager.switchToTab(tabId)
        val tab = tabManager.activeTab
        if (tab != null) {
            _currentUrl.value = tab.url
            _currentTitle.value = tab.title
            _isSecure.value = UrlUtils.isHttps(tab.url)
        }
    }

    fun navigateTo(input: String) {
        val url = UrlUtils.processInput(input, settings.value.searchEngine)
        if (url.isNotEmpty()) {
            viewModelScope.launch { _loadUrlEvent.emit(url) }
        }
    }

    fun onPageStarted(url: String) {
        _currentUrl.value = url
        _isSecure.value = UrlUtils.isHttps(url)
        tabManager.updateTab(activeTabId.value ?: return) { it.copy(url = url) }
        checkBookmarkStatus(url)
    }

    fun onPageFinished(url: String, title: String) {
        _currentUrl.value = url
        _currentTitle.value = title
        _isSecure.value = UrlUtils.isHttps(url)
        tabManager.updateTab(activeTabId.value ?: return) { it.copy(url = url, title = title) }

        val isIncognito = activeTab?.isIncognito == true
        if (!isIncognito && url.startsWith("http")) {
            viewModelScope.launch {
                addHistoryUseCase(url, title, activeProfileId)
            }
        }
        checkBookmarkStatus(url)
    }

    fun onProgressChanged(progress: Int) {
        _pageProgress.value = progress
    }

    fun showTabs() {
        viewModelScope.launch { _showTabsEvent.emit(Unit) }
    }

    fun toggleBookmark() {
        val url = _currentUrl.value
        val title = _currentTitle.value
        if (url.isBlank() || !url.startsWith("http")) return

        viewModelScope.launch {
            if (_isBookmarked.value) {
                val bookmark = bookmarkRepository.getBookmarkByUrl(url, activeProfileId)
                if (bookmark != null) {
                    bookmarkRepository.deleteBookmark(bookmark.id)
                    _isBookmarked.value = false
                }
            } else {
                bookmarkRepository.addBookmark(
                    BookmarkEntry(url = url, title = title, profileId = activeProfileId)
                )
                _isBookmarked.value = true
            }
        }
    }

    private fun checkBookmarkStatus(url: String) {
        if (!url.startsWith("http")) {
            _isBookmarked.value = false
            return
        }
        viewModelScope.launch {
            _isBookmarked.value = bookmarkRepository.isBookmarked(url, activeProfileId)
        }
    }

    fun getBookmarks() = bookmarkRepository.getBookmarksForProfile(activeProfileId)
    fun getHistory() = historyRepository.getHistoryForProfile(activeProfileId)
    fun tabCount() = tabManager.tabCount()
}
