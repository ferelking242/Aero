package com.velobrowser.core.adblocker

import android.content.Context
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdBlocker @Inject constructor() {

    private var rules: FilterListParser.FilterRules? = null
    private val parser = FilterListParser()
    private val emptyResponse = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    fun initialize(context: Context) {
        Thread {
            rules = parser.parse(context, "adblock_rules.txt")
        }.start()
    }

    fun shouldBlock(url: String): Boolean {
        val filterRules = rules ?: return false

        return try {
            val host = extractHost(url)

            // Check exact domain match (fastest — O(1))
            if (host != null && filterRules.exactDomains.contains(host)) return true

            // Check subdomain matches
            if (host != null) {
                var domain = host
                while (domain.contains(".")) {
                    domain = domain.substringAfter(".")
                    if (filterRules.exactDomains.contains(domain)) return true
                }
            }

            // Check URL prefix patterns
            for (prefix in filterRules.domainPrefixes) {
                if (url.startsWith(prefix, ignoreCase = true)) return true
            }

            // Check regex patterns (slowest — last resort)
            for (regex in filterRules.regexPatterns) {
                if (regex.containsMatchIn(url)) return true
            }

            false
        } catch (e: Exception) {
            false
        }
    }

    fun getBlockedResponse(): WebResourceResponse = emptyResponse

    private fun extractHost(url: String): String? {
        return try {
            val withoutScheme = url.substringAfter("://")
            withoutScheme.substringBefore("/").substringBefore("?").substringBefore(":").lowercase()
        } catch (e: Exception) {
            null
        }
    }
}
