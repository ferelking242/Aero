package com.velobrowser.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velobrowser.core.isolated.IsolatedTabManager
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
    private val isolatedTabManager: IsolatedTabManager,
    private val settingsDataStore: SettingsDataStore,
    private val historyRepository: HistoryRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val addHistoryUseCase: AddHistoryUseCase
) : ViewModel() {

    val tabs: StateFlow<List<BrowserTab>> = tabManager.tabs
    val activeTabId: StateFlow<String?> = tabManager.activeTabId
    val settings: StateFlow<BrowserSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, BrowserSettings())
    val isolatedTabs = isolatedTabManager.isolatedTabs

    private val _loadUrlEvent = MutableSharedFlow<String>()
    val loadUrlEvent: SharedFlow<String> = _loadUrlEvent.asSharedFlow()

    private val _showTabsEvent = MutableSharedFlow<Unit>()
    val showTabsEvent: SharedFlow<Unit> = _showTabsEvent.asSharedFlow()

    private val _openIsolatedTabEvent = MutableSharedFlow<Pair<Int, String>>()
    val openIsolatedTabEvent: SharedFlow<Pair<Int, String>> = _openIsolatedTabEvent.asSharedFlow()

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

    fun openIsolatedTab(url: String = "") {
        if (!isolatedTabManager.canOpenMore()) return
        val slot = isolatedTabManager.nextAvailableSlot()
        if (slot < 1) return
        isolatedTabManager.openSlot(slot, url)
        tabManager.openIsolatedTab(url, slot)
        viewModelScope.launch {
            _openIsolatedTabEvent.emit(Pair(slot, url))
        }
    }

    fun openCurrentUrlInIsolatedTab() {
        val url = _currentUrl.value.ifEmpty { settings.value.homepage }
        openIsolatedTab(url)
    }

    fun canOpenIsolatedTab(): Boolean = isolatedTabManager.canOpenMore()

    fun isolatedTabCount(): Int = isolatedTabManager.count()

    fun closeTab(tabId: String) {
        tabManager.closeTab(tabId)
        val active = tabManager.activeTab
        if (active != null) {
            _currentUrl.value = active.url
            _currentTitle.value = active.title
            _isSecure.value = UrlUtils.isHttps(active.url)
        }
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
            _pageProgress.value = 0
            checkBookmarkStatus(tab.url)
        }
    }

    fun navigateTo(input: String) {
        val url = UrlUtils.processInput(input, settings.value.searchEngine)
        if (url.isNotEmpty()) {
            viewModelScope.launch { _loadUrlEvent.emit(url) }
        }
    }

    fun onTabPageStarted(tabId: String, url: String) {
        tabManager.updateTab(tabId) { it.copy(url = url) }
        if (tabId == activeTabId.value) {
            _currentUrl.value = url
            _isSecure.value = UrlUtils.isHttps(url)
            checkBookmarkStatus(url)
        }
    }

    fun onTabPageFinished(tabId: String, url: String, title: String) {
        tabManager.updateTab(tabId) { it.copy(url = url, title = title) }
        if (tabId == activeTabId.value) {
            _currentUrl.value = url
            _currentTitle.value = title
            _isSecure.value = UrlUtils.isHttps(url)
            checkBookmarkStatus(url)
        }
        val isIncognito = tabManager.tabs.value.find { it.id == tabId }?.isIncognito == true
        if (!isIncognito && url.startsWith("http")) {
            viewModelScope.launch {
                addHistoryUseCase(url, title, activeProfileId)
            }
        }
    }

    fun onProgressChanged(progress: Int) {
        _pageProgress.value = progress
    }

    fun showTabs() {
        viewModelScope.launch { _showTabsEvent.emit(Unit) }
    }

    fun setDesktopMode(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setDesktopMode(enabled) }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setDarkMode(enabled) }
    }

    fun setImagesEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setImagesEnabled(enabled) }
    }

    fun setAdBlockerEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAdBlockerEnabled(enabled) }
    }

    private val _clearDataEvent = MutableSharedFlow<Unit>()
    val clearDataEvent: SharedFlow<Unit> = _clearDataEvent.asSharedFlow()

    fun clearBrowsingData() {
        viewModelScope.launch {
            historyRepository.clearHistoryForProfile(activeProfileId)
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            _clearDataEvent.emit(Unit)
        }
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
    fun onIsolatedSlotClosed(slot: Int) {
        isolatedTabManager.closeSlot(slot)
        tabManager.closeIsolatedTab(slot)
    }
    fun onIsolatedSlotUpdated(slot: Int, url: String, title: String) {
        isolatedTabManager.updateSlot(slot, url, title)
        tabManager.updateIsolatedTabBySlot(slot) { it.copy(url = url, title = title) }
    }
}
