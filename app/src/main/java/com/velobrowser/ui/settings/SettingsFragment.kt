package com.velobrowser.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.velobrowser.R
import com.velobrowser.data.local.datastore.SettingsDataStore
import com.velobrowser.domain.repository.HistoryRepository
import com.velobrowser.utils.LocaleUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat() {

    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var historyRepository: HistoryRepository

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        bindSettings()
    }

    private fun bindSettings() {
        lifecycleScope.launch {
            val settings = settingsDataStore.settings.first()

            // JavaScript
            findPreference<SwitchPreferenceCompat>("pref_javascript")?.apply {
                isChecked = settings.javascriptEnabled
                setOnPreferenceChangeListener { _, newValue ->
                    lifecycleScope.launch { settingsDataStore.setJavascriptEnabled(newValue as Boolean) }
                    true
                }
            }

            // Images
            findPreference<SwitchPreferenceCompat>("pref_images")?.apply {
                isChecked = settings.imagesEnabled
                setOnPreferenceChangeListener { _, newValue ->
                    lifecycleScope.launch { settingsDataStore.setImagesEnabled(newValue as Boolean) }
                    true
                }
            }

            // Ad Blocker
            findPreference<SwitchPreferenceCompat>("pref_adblocker")?.apply {
                isChecked = settings.adBlockerEnabled
                setOnPreferenceChangeListener { _, newValue ->
                    lifecycleScope.launch { settingsDataStore.setAdBlockerEnabled(newValue as Boolean) }
                    true
                }
            }

            // Ultra Fast Mode
            findPreference<SwitchPreferenceCompat>("pref_ultra_fast")?.apply {
                isChecked = settings.ultraFastMode
                setOnPreferenceChangeListener { _, newValue ->
                    lifecycleScope.launch { settingsDataStore.setUltraFastMode(newValue as Boolean) }
                    true
                }
            }

            // Safe Browsing
            findPreference<SwitchPreferenceCompat>("pref_safe_browsing")?.apply {
                isChecked = settings.safeBrowsing
                setOnPreferenceChangeListener { _, newValue ->
                    lifecycleScope.launch { settingsDataStore.setSafeBrowsing(newValue as Boolean) }
                    true
                }
            }

            // Search Engine
            findPreference<ListPreference>("pref_search_engine")?.apply {
                val engines = mapOf(
                    "https://www.google.com/search?q=" to "Google",
                    "https://duckduckgo.com/?q=" to "DuckDuckGo",
                    "https://www.bing.com/search?q=" to "Bing",
                    "https://search.brave.com/search?q=" to "Brave"
                )
                entries = engines.values.toTypedArray()
                entryValues = engines.keys.toTypedArray()
                value = settings.searchEngine
                setOnPreferenceChangeListener { _, newValue ->
                    lifecycleScope.launch { settingsDataStore.setSearchEngine(newValue as String) }
                    true
                }
            }

            // Homepage
            findPreference<EditTextPreference>("pref_homepage")?.apply {
                text = settings.homepage
                summary = settings.homepage
                setOnPreferenceChangeListener { pref, newValue ->
                    val url = newValue as String
                    (pref as EditTextPreference).summary = url
                    lifecycleScope.launch { settingsDataStore.setHomepage(url) }
                    true
                }
            }

            // Language
            findPreference<ListPreference>("pref_language")?.apply {
                val languages = LocaleUtils.getSupportedLanguages()
                entries = languages.map { it.second }.toTypedArray()
                entryValues = languages.map { it.first }.toTypedArray()
                value = settings.language
                setOnPreferenceChangeListener { _, newValue ->
                    val lang = newValue as String
                    lifecycleScope.launch { settingsDataStore.setLanguage(lang) }
                    LocaleUtils.setLanguage(requireContext(), lang)
                    restartActivity()
                    true
                }
            }

            // Clear Cache
            findPreference<Preference>("pref_clear_cache")?.setOnPreferenceClickListener {
                showConfirmDialog(getString(R.string.clear_cache)) {
                    android.webkit.WebStorage.getInstance().deleteAllData()
                    Toast.makeText(requireContext(), getString(R.string.cache_cleared), Toast.LENGTH_SHORT).show()
                }
                true
            }

            // Clear History
            findPreference<Preference>("pref_clear_history")?.setOnPreferenceClickListener {
                showConfirmDialog(getString(R.string.clear_history)) {
                    lifecycleScope.launch {
                        historyRepository.clearAllHistory()
                        Toast.makeText(requireContext(), getString(R.string.history_cleared), Toast.LENGTH_SHORT).show()
                    }
                }
                true
            }

            // Clear Cookies
            findPreference<Preference>("pref_clear_cookies")?.setOnPreferenceClickListener {
                showConfirmDialog(getString(R.string.clear_cookies)) {
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                    android.webkit.CookieManager.getInstance().flush()
                    Toast.makeText(requireContext(), getString(R.string.cookies_cleared), Toast.LENGTH_SHORT).show()
                }
                true
            }
        }
    }

    private fun showConfirmDialog(title: String, onConfirm: () -> Unit) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(getString(R.string.confirm_action))
            .setPositiveButton(getString(R.string.confirm)) { _, _ -> onConfirm() }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun restartActivity() {
        val intent = requireActivity().intent
        requireActivity().finish()
        startActivity(intent)
    }
}
