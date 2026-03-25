package com.usbdiskmanager.ps2.data.scanner

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.usbdiskmanager.ps2.domain.model.DiscType
import com.usbdiskmanager.ps2.domain.model.GameRegion
import com.usbdiskmanager.ps2.domain.model.Ps2Game
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IsoScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        val DEFAULT_ISO_DIR: String
            get() = "${Environment.getExternalStorageDirectory()}/PS2Manager/ISO"

        val DEFAULT_UL_DIR: String
            get() = "${Environment.getExternalStorageDirectory()}/PS2Manager/UL"

        val DEFAULT_ART_DIR: String
            get() = "${Environment.getExternalStorageDirectory()}/PS2Manager/ART"
    }

    /**
     * Scan a list of directory paths and return all discovered PS2 ISO files.
     */
    suspend fun scanDirectories(paths: List<String>): List<Ps2Game> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<Ps2Game>()
            val seen = mutableSetOf<String>()

            for (path in paths) {
                val dir = File(path)
                if (!dir.exists() || !dir.isDirectory) continue
                dir.walkTopDown()
                    .maxDepth(4)
                    .filter { it.isFile && it.extension.lowercase() == "iso" }
                    .forEach { isoFile ->
                        if (seen.add(isoFile.absolutePath)) {
                            parseIso(isoFile)?.let { results.add(it) }
                        }
                    }
            }
            results.sortedBy { it.title }
        }

    /**
     * Scan a URI provided via SAF.
     */
    suspend fun scanUri(uri: Uri, artDir: String): List<Ps2Game> =
        withContext(Dispatchers.IO) {
            // SAF path: try to resolve to a real File path
            val realPath = resolveUriToPath(uri) ?: return@withContext emptyList()
            scanDirectories(listOf(realPath))
        }

    /**
     * Create the default PS2Manager folder structure if it doesn't exist.
     */
    suspend fun ensureStructure(base: String): Unit = withContext(Dispatchers.IO) {
        listOf(
            "$base/ISO",
            "$base/UL",
            "$base/ART"
        ).forEach { path ->
            val dir = File(path)
            if (!dir.exists()) {
                val ok = dir.mkdirs()
                Timber.d("Created dir $path: $ok")
            }
        }
    }

    // ────────────────────────────────────────────
    // Private helpers
    // ────────────────────────────────────────────

    private fun parseIso(file: File): Ps2Game? = try {
        val rawGameId = Iso9660Parser.readGameId(file) ?: ""
        val gameId = rawGameId.ifEmpty { deriveIdFromFilename(file.nameWithoutExtension) }
        val region = GameRegion.fromGameId(gameId)
        val discType = when (Iso9660Parser.guessDiscType(file)) {
            "CD" -> DiscType.CD
            else -> DiscType.DVD
        }
        val artDir = DEFAULT_ART_DIR
        val coverPath = coverArtPath(gameId, artDir)

        Ps2Game(
            id = file.absolutePath,
            title = file.nameWithoutExtension,
            gameId = gameId,
            isoPath = file.absolutePath,
            sizeMb = file.length(),
            region = region.label,
            coverPath = coverPath,
            discType = discType
        )
    } catch (e: Exception) {
        Timber.w(e, "Could not parse ISO: ${file.name}")
        null
    }

    private fun deriveIdFromFilename(name: String): String {
        // Try to extract game ID pattern from the filename itself
        val regex = Regex("[A-Z]{4}[_]\\d{3}\\.\\d{2}", RegexOption.IGNORE_CASE)
        return regex.find(name)?.value?.uppercase() ?: ""
    }

    private fun coverArtPath(gameId: String, artDir: String): String? {
        if (gameId.isEmpty()) return null
        val normalized = gameId.replace(".", "").replace("_", "")
        val file = File("$artDir/${normalized}_COV.png")
        return if (file.exists()) file.absolutePath else null
    }

    private fun resolveUriToPath(uri: Uri): String? {
        // For content:// URIs pointing to external storage
        val path = uri.path ?: return null
        // Pattern: /tree/primary:some/path
        val primary = path.substringAfter("primary:", "")
        if (primary.isNotEmpty()) {
            return "${Environment.getExternalStorageDirectory()}/$primary"
        }
        return path
    }
}
