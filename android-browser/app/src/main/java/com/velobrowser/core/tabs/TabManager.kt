package com.velobrowser.core.tabs

import com.velobrowser.domain.model.BrowserTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TabManager @Inject constructor() {

    companion object {
        const val MAX_ISOLATED_SLOTS = 4
    }

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
        if (!tab.isIsolated) {
            _activeTabId.value = tab.id
        }
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
            isIsolated = false,
            profileId = profileId,
            title = if (url.isEmpty()) "New Tab" else url
        )
        return openTab(tab)
    }

    fun openIsolatedTab(url: String = "", slot: Int): BrowserTab? {
        if (slot < 1 || slot > MAX_ISOLATED_SLOTS) return null
        val existing = _tabs.value.find { it.isIsolated && it.isolatedSlot == slot }
        if (existing != null) {
            return existing
        }
        val tab = BrowserTab(
            url = url,
            isIsolated = true,
            isolatedSlot = slot,
            title = if (url.isEmpty()) "Isolated Tab $slot" else url
        )
        val current = _tabs.value.toMutableList()
        current.add(tab)
        _tabs.value = current
        return tab
    }

    fun nextAvailableIsolatedSlot(): Int {
        val usedSlots = _tabs.value.filter { it.isIsolated }.map { it.isolatedSlot }.toSet()
        return (1..MAX_ISOLATED_SLOTS).firstOrNull { it !in usedSlots } ?: -1
    }

    fun isolatedTabCount(): Int = _tabs.value.count { it.isIsolated }

    fun canOpenMoreIsolatedTabs(): Boolean = isolatedTabCount() < MAX_ISOLATED_SLOTS

    fun closeTab(tabId: String) {
        val current = _tabs.value.toMutableList()
        val index = current.indexOfFirst { it.id == tabId }
        if (index < 0) return

        val closedTab = current[index]
        current.removeAt(index)
        _tabs.value = current

        if (_activeTabId.value == tabId) {
            val nonIsolated = current.filter { !it.isIsolated }
            _activeTabId.value = when {
                nonIsolated.isEmpty() -> null
                index < nonIsolated.size -> nonIsolated[index].id
                else -> nonIsolated.last().id
            }
        }
    }

    fun switchToTab(tabId: String) {
        val tab = _tabs.value.find { it.id == tabId } ?: return
        if (!tab.isIsolated) {
            _activeTabId.value = tabId
        }
    }

    fun updateTab(tabId: String, update: (BrowserTab) -> BrowserTab) {
        _tabs.value = _tabs.value.map { if (it.id == tabId) update(it) else it }
    }

    fun updateIsolatedTabBySlot(slot: Int, update: (BrowserTab) -> BrowserTab) {
        _tabs.value = _tabs.value.map {
            if (it.isIsolated && it.isolatedSlot == slot) update(it) else it
        }
    }

    fun closeAllTabs() {
        _tabs.value = emptyList()
        _activeTabId.value = null
    }

    fun closeIncognitoTabs() {
        val remaining = _tabs.value.filter { !it.isIncognito }
        _tabs.value = remaining
        if (_activeTabId.value != null && remaining.none { it.id == _activeTabId.value }) {
            _activeTabId.value = remaining.filter { !it.isIsolated }.lastOrNull()?.id
        }
    }

    fun closeIsolatedTab(slot: Int) {
        val tab = _tabs.value.find { it.isIsolated && it.isolatedSlot == slot } ?: return
        closeTab(tab.id)
    }

    fun tabCount(): Int = _tabs.value.size

    fun hasNoTabs(): Boolean = _tabs.value.isEmpty()

    fun normalTabs(): List<BrowserTab> = _tabs.value.filter { !it.isIncognito && !it.isIsolated }

    fun incognitoTabs(): List<BrowserTab> = _tabs.value.filter { it.isIncognito }

    fun isolatedTabs(): List<BrowserTab> = _tabs.value.filter { it.isIsolated }
}
