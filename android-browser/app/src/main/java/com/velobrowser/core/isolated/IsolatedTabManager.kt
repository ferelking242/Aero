package com.velobrowser.core.isolated

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IsolatedTabManager @Inject constructor() {

    companion object {
        const val MAX_SLOTS = 4

        const val ACTION_URL_CHANGED = "com.velobrowser.ISOLATED_URL_CHANGED"
        const val ACTION_TITLE_CHANGED = "com.velobrowser.ISOLATED_TITLE_CHANGED"
        const val ACTION_TAB_CLOSED = "com.velobrowser.ISOLATED_TAB_CLOSED"
        const val ACTION_LOAD_URL = "com.velobrowser.ISOLATED_LOAD_URL"

        const val EXTRA_SLOT = "slot"
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"

        private const val PREFS_NAME = "isolated_tabs_state"

        fun getPrefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun saveSlotState(context: Context, slot: Int, url: String, title: String) {
            getPrefs(context).edit()
                .putString("slot_${slot}_url", url)
                .putString("slot_${slot}_title", title)
                .putBoolean("slot_${slot}_active", true)
                .apply()
        }

        fun clearSlotState(context: Context, slot: Int) {
            getPrefs(context).edit()
                .remove("slot_${slot}_url")
                .remove("slot_${slot}_title")
                .putBoolean("slot_${slot}_active", false)
                .apply()
        }

        fun getSlotUrl(context: Context, slot: Int): String =
            getPrefs(context).getString("slot_${slot}_url", "") ?: ""

        fun getSlotTitle(context: Context, slot: Int): String =
            getPrefs(context).getString("slot_${slot}_title", "Isolated Tab $slot") ?: "Isolated Tab $slot"

        fun isSlotActive(context: Context, slot: Int): Boolean =
            getPrefs(context).getBoolean("slot_${slot}_active", false)
    }

    private val _isolatedTabs = MutableStateFlow<List<IsolatedTabInfo>>(emptyList())
    val isolatedTabs: StateFlow<List<IsolatedTabInfo>> = _isolatedTabs.asStateFlow()

    fun activeSlots(): List<Int> = _isolatedTabs.value.map { it.slot }

    fun canOpenMore(): Boolean = _isolatedTabs.value.size < MAX_SLOTS

    fun nextAvailableSlot(): Int {
        val used = _isolatedTabs.value.map { it.slot }.toSet()
        return (1..MAX_SLOTS).firstOrNull { it !in used } ?: -1
    }

    fun openSlot(slot: Int, url: String = "") {
        val current = _isolatedTabs.value.toMutableList()
        val existing = current.indexOfFirst { it.slot == slot }
        if (existing >= 0) {
            current[existing] = current[existing].copy(url = url, isActive = true)
        } else {
            current.add(IsolatedTabInfo(slot = slot, url = url, isActive = true))
        }
        _isolatedTabs.value = current
    }

    fun closeSlot(slot: Int) {
        _isolatedTabs.value = _isolatedTabs.value.filter { it.slot != slot }
    }

    fun updateSlot(slot: Int, url: String, title: String) {
        _isolatedTabs.value = _isolatedTabs.value.map {
            if (it.slot == slot) it.copy(url = url, title = title) else it
        }
    }

    fun getSlotInfo(slot: Int): IsolatedTabInfo? =
        _isolatedTabs.value.find { it.slot == slot }

    fun count(): Int = _isolatedTabs.value.size
}
