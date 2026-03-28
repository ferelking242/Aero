package com.velobrowser.core.tabs

import com.velobrowser.domain.model.BrowserTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TabManager @Inject constructor() {

    private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    val activeTab: BrowserTab?
        get() = _tabs.value.find { it.id == _activeTabId.value }

    fun openTab(tab: BrowserTab): BrowserTab {
        val current = _tabs.value.toMutableList()
        current.add(tab)
        _tabs.value = current
        _activeTabId.value = tab.id
        return tab
    }

    fun openNewTab(
        url: String = "",
        isIncognito: Boolean = false,
        profileId: Long = 1L
    ): BrowserTab {
        val tab = BrowserTab(
            url = url,
            isIncognito = isIncognito,
            profileId = profileId,
            title = if (url.isEmpty()) "New Tab" else url
        )
        return openTab(tab)
    }

    fun closeTab(tabId: String) {
        val current = _tabs.value.toMutableList()
        val index = current.indexOfFirst { it.id == tabId }
        if (index < 0) return

        current.removeAt(index)
        _tabs.value = current

        if (_activeTabId.value == tabId) {
            _activeTabId.value = when {
                current.isEmpty() -> null
                index < current.size -> current[index].id
                else -> current.last().id
            }
        }
    }

    fun switchToTab(tabId: String) {
        if (_tabs.value.any { it.id == tabId }) {
            _activeTabId.value = tabId
        }
    }

    fun updateTab(tabId: String, update: (BrowserTab) -> BrowserTab) {
        _tabs.value = _tabs.value.map { if (it.id == tabId) update(it) else it }
    }

    fun closeAllTabs() {
        _tabs.value = emptyList()
        _activeTabId.value = null
    }

    fun closeIncognitoTabs() {
        val remaining = _tabs.value.filter { !it.isIncognito }
        _tabs.value = remaining
        if (_activeTabId.value != null && remaining.none { it.id == _activeTabId.value }) {
            _activeTabId.value = remaining.lastOrNull()?.id
        }
    }

    fun tabCount(): Int = _tabs.value.size

    fun hasNoTabs(): Boolean = _tabs.value.isEmpty()
}
