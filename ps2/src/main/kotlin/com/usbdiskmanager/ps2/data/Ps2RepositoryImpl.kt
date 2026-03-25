package com.usbdiskmanager.ps2.data

import android.content.Context
import android.os.Environment
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.usbdiskmanager.ps2.data.converter.UlCfgManager
import com.usbdiskmanager.ps2.data.converter.UlConverter
import com.usbdiskmanager.ps2.data.cover.CoverArtFetcher
import com.usbdiskmanager.ps2.data.db.ConversionJobDao
import com.usbdiskmanager.ps2.data.scanner.IsoScanner
import com.usbdiskmanager.ps2.domain.model.ConversionJob
import com.usbdiskmanager.ps2.domain.model.ConversionProgress
import com.usbdiskmanager.ps2.domain.model.ConversionStatus
import com.usbdiskmanager.ps2.domain.model.DiscType
import com.usbdiskmanager.ps2.domain.model.Ps2Game
import com.usbdiskmanager.ps2.domain.repository.Ps2Repository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.ps2DataStore by preferencesDataStore(name = "ps2_prefs")

@Singleton
class Ps2RepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanner: IsoScanner,
    private val converter: UlConverter,
    private val cfgManager: UlCfgManager,
    private val coverFetcher: CoverArtFetcher,
    private val jobDao: ConversionJobDao
) : Ps2Repository {

    companion object {
        private val SCAN_PATHS_KEY = stringSetPreferencesKey("scan_paths")
    }

    private val _games = MutableStateFlow<List<Ps2Game>>(emptyList())
    override val games: Flow<List<Ps2Game>> = _games.asStateFlow()

    override val conversionJobs: Flow<List<ConversionJob>> = jobDao.observeAll()

    override suspend fun scanIsoDirectories() {
        val paths = getScanPaths()
        val found = scanner.scanDirectories(paths)

        // Merge conversion status from existing DB jobs
        val jobs = jobDao.getResumable()
        val statusMap = jobs.associate { it.isoPath to it }

        _games.value = found.map { game ->
            val job = statusMap[game.isoPath]
            if (job != null) {
                game.copy(conversionStatus = statusFromJobStatus(job.status))
            } else game
        }
        Timber.d("Scan complete: ${found.size} ISO(s) found")
    }

    override suspend fun addScanPath(path: String) {
        context.ps2DataStore.edit { prefs ->
            val current = prefs[SCAN_PATHS_KEY] ?: setOf(IsoScanner.DEFAULT_ISO_DIR)
            prefs[SCAN_PATHS_KEY] = current + path
        }
    }

    override suspend fun removeScanPath(path: String) {
        context.ps2DataStore.edit { prefs ->
            val current = prefs[SCAN_PATHS_KEY] ?: emptySet()
            prefs[SCAN_PATHS_KEY] = current - path
        }
    }

    override suspend fun getScanPaths(): List<String> {
        var paths: Set<String> = setOf(IsoScanner.DEFAULT_ISO_DIR)
        context.ps2DataStore.edit { prefs ->
            if (!prefs.contains(SCAN_PATHS_KEY)) {
                prefs[SCAN_PATHS_KEY] = setOf(IsoScanner.DEFAULT_ISO_DIR)
            }
            paths = prefs[SCAN_PATHS_KEY] ?: setOf(IsoScanner.DEFAULT_ISO_DIR)
        }
        return paths.toList()
    }

    override fun convertToUl(isoPath: String, outputDir: String): Flow<ConversionProgress> {
        val isoFile = File(isoPath)
        val game = _games.value.firstOrNull { it.isoPath == isoPath }
        val gameId = game?.gameId ?: isoFile.nameWithoutExtension
        val gameName = game?.title ?: isoFile.nameWithoutExtension
        val isCD = game?.discType == DiscType.CD

        return converter.convert(isoFile, gameId, outputDir, resumeOffset = 0L)
            .onEach { progress ->
                val status = if (progress.isComplete) "DONE" else "RUNNING"
                jobDao.updateProgress(isoPath, progress.bytesWritten, progress.currentPart, status)
                if (progress.isComplete) {
                    val parts = (progress.totalBytes + UlConverter.PART_SIZE - 1) / UlConverter.PART_SIZE
                    cfgManager.addOrUpdateEntry(outputDir, gameId, gameName, parts.toInt(), isCD)
                    updateGameStatus(isoPath, ConversionStatus.COMPLETED)
                } else {
                    updateGameStatus(isoPath, ConversionStatus.IN_PROGRESS)
                }
            }
            .onCompletion { err ->
                if (err != null) {
                    jobDao.updateStatus(isoPath, "ERROR", err.message ?: "Unknown error")
                    updateGameStatus(isoPath, ConversionStatus.ERROR)
                }
            }
    }

    override suspend fun pauseConversion(isoPath: String) {
        jobDao.updateStatus(isoPath, "PAUSED")
        updateGameStatus(isoPath, ConversionStatus.PAUSED)
    }

    override fun resumeConversion(isoPath: String): Flow<ConversionProgress> = flow {
        val job = jobDao.getByPath(isoPath) ?: return@flow
        val isoFile = File(isoPath)
        val outputDir = job.outputDir
        val resumeOffset = converter.calculateResumeOffset(outputDir, job.gameId)
        val game = _games.value.firstOrNull { it.isoPath == isoPath }
        val isCD = game?.discType == DiscType.CD

        emitAll(
            converter.convert(isoFile, job.gameId, outputDir, resumeOffset)
                .onEach { progress ->
                    val status = if (progress.isComplete) "DONE" else "RUNNING"
                    jobDao.updateProgress(isoPath, progress.bytesWritten, progress.currentPart, status)
                    if (progress.isComplete) {
                        val parts = (progress.totalBytes + UlConverter.PART_SIZE - 1) / UlConverter.PART_SIZE
                        cfgManager.addOrUpdateEntry(outputDir, job.gameId, job.gameTitle, parts.toInt(), isCD)
                        updateGameStatus(isoPath, ConversionStatus.COMPLETED)
                    } else {
                        updateGameStatus(isoPath, ConversionStatus.IN_PROGRESS)
                    }
                }
                .onCompletion { err ->
                    if (err != null) {
                        jobDao.updateStatus(isoPath, "ERROR", err.message ?: "")
                        updateGameStatus(isoPath, ConversionStatus.ERROR)
                    }
                }
        )
    }

    override suspend fun cancelConversion(isoPath: String) {
        val job = jobDao.getByPath(isoPath) ?: return
        converter.deletePartFiles(job.outputDir, job.gameId)
        cfgManager.removeEntry(job.outputDir, job.gameId)
        jobDao.delete(isoPath)
        updateGameStatus(isoPath, ConversionStatus.NOT_CONVERTED)
    }

    override suspend fun fetchCoverArt(gameId: String, region: String, outputDir: String): String? {
        val path = coverFetcher.fetchCover(gameId, region, IsoScanner.DEFAULT_ART_DIR)
        if (path != null) {
            _games.update { list ->
                list.map { g -> if (g.gameId == gameId) g.copy(coverPath = path) else g }
            }
        }
        return path
    }

    override suspend fun getGame(isoPath: String): Ps2Game? =
        _games.value.firstOrNull { it.isoPath == isoPath }

    override suspend fun getJob(isoPath: String): ConversionJob? =
        jobDao.getByPath(isoPath)

    override suspend fun ensureDirectoryStructure(basePath: String) {
        scanner.ensureStructure(basePath)
    }

    /** Create a pending job record (called before starting conversion). */
    suspend fun createJob(isoPath: String, outputDir: String) {
        val isoFile = File(isoPath)
        val game = _games.value.firstOrNull { it.isoPath == isoPath }
        val gameId = game?.gameId ?: isoFile.nameWithoutExtension
        val title = game?.title ?: isoFile.nameWithoutExtension
        jobDao.upsert(
            ConversionJob(
                isoPath = isoPath,
                gameId = gameId,
                gameTitle = title,
                outputDir = outputDir,
                totalBytes = isoFile.length(),
                status = "RUNNING"
            )
        )
        updateGameStatus(isoPath, ConversionStatus.IN_PROGRESS)
    }

    private fun updateGameStatus(isoPath: String, status: ConversionStatus) {
        _games.update { list ->
            list.map { g -> if (g.isoPath == isoPath) g.copy(conversionStatus = status) else g }
        }
    }

    private fun statusFromJobStatus(s: String): ConversionStatus = when (s) {
        "DONE"    -> ConversionStatus.COMPLETED
        "RUNNING" -> ConversionStatus.IN_PROGRESS
        "PAUSED"  -> ConversionStatus.PAUSED
        "ERROR"   -> ConversionStatus.ERROR
        else      -> ConversionStatus.NOT_CONVERTED
    }
}
