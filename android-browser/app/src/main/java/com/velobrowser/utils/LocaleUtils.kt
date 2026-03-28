package com.velobrowser.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.Locale

object LocaleUtils {

    private const val PREFS_NAME = "locale_prefs"
    private const val KEY_LANGUAGE = "language"

    fun applyLocale(context: Context): Context {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val language = prefs.getString(KEY_LANGUAGE, "system") ?: "system"
        return wrapContext(context, language)
    }

    fun setLanguage(context: Context, language: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    fun getCurrentLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, "system") ?: "system"
    }

    private fun wrapContext(context: Context, language: String): Context {
        if (language == "system") return context

        val locale = Locale(language)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun getSupportedLanguages(): List<Pair<String, String>> = listOf(
        "system" to "System Default",
        "en" to "English",
        "fr" to "Français"
    )
}
