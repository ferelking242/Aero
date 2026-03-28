package com.velobrowser.utils

import android.util.Patterns

object UrlUtils {

    fun processInput(input: String, searchEngineUrl: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.isEmpty() -> ""
            isValidUrl(trimmed) -> normalizeUrl(trimmed)
            else -> "$searchEngineUrl${android.net.Uri.encode(trimmed)}"
        }
    }

    fun isValidUrl(input: String): Boolean {
        val withScheme = if (!input.contains("://")) "https://$input" else input
        return Patterns.WEB_URL.matcher(withScheme).matches() &&
                (input.contains(".") || input.startsWith("localhost"))
    }

    fun normalizeUrl(url: String): String {
        return if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }
    }

    fun getDomainName(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host ?: url
            host.removePrefix("www.")
        } catch (e: Exception) {
            url
        }
    }

    fun getFaviconUrl(pageUrl: String): String {
        return try {
            val uri = android.net.Uri.parse(pageUrl)
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: return ""
            "$scheme://$host/favicon.ico"
        } catch (e: Exception) {
            ""
        }
    }

    fun isHttps(url: String): Boolean = url.startsWith("https://")

    fun isDataUrl(url: String): Boolean = url.startsWith("data:")

    fun isAboutUrl(url: String): Boolean = url.startsWith("about:")
}
