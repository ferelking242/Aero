package com.usbdiskmanager.ps2.data.cover

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

enum class CoverType { DEFAULT, TWO_D, THREE_D }

/**
 * Downloads PS2 cover art from multiple sources and caches it locally.
 *
 * Sources tried in order:
 *   1. xlenore/ps2-covers GitHub repo (default → 2D → 3D)
 *   2. GameTDB — cover / coverM / coverHQ
 *   3. PSDB.net — by game ID
 *
 * Cover images are stored as: [artDir]/[GAMEID]_[type].png
 */
@Singleton
class CoverArtFetcher @Inject constructor() {

    companion object {
        private const val TIMEOUT_MS = 15_000
        private const val USER_AGENT = "UsbDiskManager/1.2 (Android)"

        // GitHub raw base for xlenore/ps2-covers
        private const val GITHUB_BASE =
            "https://raw.githubusercontent.com/xlenore/ps2-covers/main/covers"

        private val GAMETDB_BASES = listOf("cover", "coverM", "coverHQ")

        /**
         * Normalize a game ID for GitHub repo lookup.
         * "SLUS_215.03" → "SLUS_215.03" (kept as-is, repo uses original format)
         * "SLES-51444"  → "SLES-51444"
         */
        private fun normalizeForGithub(gameId: String): String = gameId.trim()

        /**
         * Build the GitHub raw URL for a given game ID and cover type.
         */
        fun githubCoverUrl(gameId: String, type: CoverType): String {
            val folder = when (type) {
                CoverType.DEFAULT -> "default"
                CoverType.TWO_D   -> "2D"
                CoverType.THREE_D -> "3D"
            }
            val normalized = normalizeForGithub(gameId)
            return "$GITHUB_BASE/$folder/$normalized.jpg"
        }

        /**
         * Try to match a game title to a PS2 game ID by normalizing the name.
         * Returns a best-guess ID based on the title tokens, used as a last-resort
         * when the ISO has no embedded game ID.
         * Example: "Need for Speed Hot Pursuit 2 (Europe).7z" → null (no reliable mapping)
         * This is intentionally conservative — we only use it in the fallback flow.
         */
        fun guessIdFromTitle(title: String): String? = null
    }

    /**
     * Download cover for [gameId] (e.g. "SLUS_215.03") into [artDir].
     * Prefers the GitHub repo as the primary source.
     * @param preferredType The cover type to try first (default: DEFAULT).
     * @return local file path if successful, null otherwise.
     */
    suspend fun fetchCover(
        gameId: String,
        region: String,
        artDir: String,
        preferredType: CoverType = CoverType.DEFAULT
    ): String? = withContext(Dispatchers.IO) {
        if (gameId.isBlank()) return@withContext null

        val safeId = gameId.replace("/", "_").replace("\\", "_")
        val typeTag = when (preferredType) {
            CoverType.DEFAULT -> "DEF"
            CoverType.TWO_D   -> "2D"
            CoverType.THREE_D -> "3D"
        }
        val localFile = File(artDir, "${safeId}_${typeTag}.jpg")
        if (localFile.exists() && localFile.length() > 0) {
            return@withContext localFile.absolutePath
        }

        File(artDir).mkdirs()

        // ── Source 1: GitHub xlenore/ps2-covers ───────────────────────────────
        // Try preferred type first, then the others
        val typeOrder = when (preferredType) {
            CoverType.DEFAULT -> listOf(CoverType.DEFAULT, CoverType.TWO_D, CoverType.THREE_D)
            CoverType.TWO_D   -> listOf(CoverType.TWO_D, CoverType.DEFAULT, CoverType.THREE_D)
            CoverType.THREE_D -> listOf(CoverType.THREE_D, CoverType.DEFAULT, CoverType.TWO_D)
        }

        for (type in typeOrder) {
            val url = githubCoverUrl(gameId, type)
            if (download(url, localFile)) {
                Timber.d("Cover from GitHub xlenore/ps2-covers [$type]: $url")
                return@withContext localFile.absolutePath
            }
        }

        // Also try with dash instead of underscore (some repos use both conventions)
        val dashId = gameId.replace("_", "-")
        if (dashId != gameId) {
            for (type in typeOrder) {
                val url = githubCoverUrl(dashId, type)
                if (download(url, localFile)) {
                    Timber.d("Cover from GitHub (dash variant) [$type]: $url")
                    return@withContext localFile.absolutePath
                }
            }
        }

        // ── Source 2: GameTDB ──────────────────────────────────────────────────
        val regionCode = mapRegionCode(region)
        val gametdbId = gameId.replace("_", "-")
        for (base in GAMETDB_BASES) {
            val url = "https://art.gametdb.com/ps2/$base/$regionCode/$gametdbId.jpg"
            if (download(url, localFile)) {
                Timber.d("Cover from GameTDB ($base): $url")
                return@withContext localFile.absolutePath
            }
        }
        val fallbackRegions = listOf("US", "EU", "JA").filter { it != regionCode }
        for (fallbackRegion in fallbackRegions) {
            for (base in listOf("cover", "coverM")) {
                val url = "https://art.gametdb.com/ps2/$base/$fallbackRegion/$gametdbId.jpg"
                if (download(url, localFile)) {
                    Timber.d("Cover from GameTDB fallback [$fallbackRegion/$base]: $url")
                    return@withContext localFile.absolutePath
                }
            }
        }

        // ── Source 3: PSDB.net ────────────────────────────────────────────────
        val psdbId = gameId.uppercase().replace(".", "").replace("_", "")
        val psdbUrl = "https://psdb.kingston-solutions.de/covers/${psdbId}_COV.jpg"
        if (download(psdbUrl, localFile)) {
            Timber.d("Cover from PSDB.net: $psdbUrl")
            return@withContext localFile.absolutePath
        }

        Timber.w("No cover found for gameId=$gameId region=$region type=$preferredType")
        null
    }

    /**
     * Try to fetch a cover using a game title when no gameId is available.
     * The title is matched against known PS2 naming conventions.
     * Example: "Need for Speed Hot Pursuit 2 (Europe).7z"
     */
    suspend fun fetchCoverByTitle(
        title: String,
        artDir: String,
        preferredType: CoverType = CoverType.DEFAULT
    ): String? = withContext(Dispatchers.IO) {
        // Extract the clean title without extension and region tags
        val cleanTitle = title
            .substringBeforeLast(".")
            .replace(Regex("\\s*\\(.*?\\)\\s*"), " ")
            .replace(Regex("\\s*\\[.*?\\]\\s*"), " ")
            .trim()

        val safeTitle = cleanTitle.replace(Regex("[^A-Za-z0-9 _-]"), "_")
        val localFile = File(artDir, "${safeTitle}_TITLE.jpg")
        if (localFile.exists() && localFile.length() > 0) {
            return@withContext localFile.absolutePath
        }

        File(artDir).mkdirs()

        // Try GameTDB search by title slug
        val titleSlug = cleanTitle.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

        // We cannot easily search the GitHub repo by name since it's indexed by game ID.
        // Fallback: try GameTDB via title lookup (limited)
        val gametdbRegions = listOf("US", "EU", "JA")
        for (region in gametdbRegions) {
            val url = "https://art.gametdb.com/ps2/cover/$region/$titleSlug.jpg"
            if (download(url, localFile)) {
                Timber.d("Cover from GameTDB by title slug [$region]: $url")
                return@withContext localFile.absolutePath
            }
        }

        Timber.w("No cover found for title='$cleanTitle'")
        null
    }

    /**
     * Download all missing covers for a list of game IDs.
     * Processes one at a time to avoid hammering servers.
     */
    suspend fun fetchAllCovers(
        games: List<Pair<String, String>>,
        artDir: String,
        preferredType: CoverType = CoverType.DEFAULT,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Int {
        var fetched = 0
        games.forEachIndexed { i, (gameId, region) ->
            val result = fetchCover(gameId, region, artDir, preferredType)
            if (result != null) fetched++
            onProgress(i + 1, games.size)
        }
        return fetched
    }

    private fun download(urlStr: String, target: File): Boolean {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.instanceFollowRedirects = true
            conn.connect()

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                BufferedInputStream(conn.inputStream, 65536).use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output, 65536)
                    }
                }
                val valid = target.length() > 500
                if (!valid) target.delete()
                valid
            } else {
                conn.disconnect()
                false
            }
        } catch (e: Exception) {
            Timber.v("Cover attempt failed [$urlStr]: ${e.message}")
            false
        }
    }

    private fun mapRegionCode(region: String): String = when {
        region.contains("NTSC-U", ignoreCase = true) || region.contains("USA", ignoreCase = true) -> "US"
        region.contains("NTSC-J", ignoreCase = true) || region.contains("Japan", ignoreCase = true) -> "JA"
        region.contains("PAL", ignoreCase = true) -> "EU"
        else -> "US"
    }
}
