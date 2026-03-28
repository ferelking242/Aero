package com.velobrowser.core.adblocker

import android.content.Context

class FilterListParser {

    data class FilterRules(
        val exactDomains: HashSet<String>,
        val domainPrefixes: HashSet<String>,
        val regexPatterns: List<Regex>
    )

    fun parse(context: Context, assetFileName: String): FilterRules {
        val exactDomains = HashSet<String>()
        val domainPrefixes = HashSet<String>()
        val regexList = mutableListOf<Regex>()

        try {
            context.assets.open(assetFileName).bufferedReader().useLines { lines ->
                lines.forEach { rawLine ->
                    val line = rawLine.trim()
                    when {
                        line.isEmpty() -> return@forEach
                        line.startsWith("!") -> return@forEach
                        line.startsWith("[") -> return@forEach
                        line.startsWith("||") -> {
                            val domain = line
                                .removePrefix("||")
                                .substringBefore("^")
                                .substringBefore("/")
                                .substringBefore("$")
                                .trim()
                            if (domain.isNotEmpty()) exactDomains.add(domain)
                        }
                        line.startsWith("|") -> {
                            val prefix = line.removePrefix("|").substringBefore("^").trim()
                            if (prefix.isNotEmpty()) domainPrefixes.add(prefix)
                        }
                        line.startsWith("/") && line.endsWith("/") -> {
                            val pattern = line.drop(1).dropLast(1)
                            runCatching { regexList.add(Regex(pattern, RegexOption.IGNORE_CASE)) }
                        }
                        line.contains("##") || line.contains("#@#") -> return@forEach
                        else -> {
                            val cleaned = line.substringBefore("$").trim()
                            if (cleaned.length > 3) {
                                runCatching {
                                    val escaped = cleaned
                                        .replace(".", "\\.")
                                        .replace("*", ".*")
                                        .replace("?", "\\?")
                                    regexList.add(Regex(escaped, RegexOption.IGNORE_CASE))
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // If asset file is unavailable, return empty rules
        }

        return FilterRules(exactDomains, domainPrefixes, regexList)
    }
}
