package com.usbdiskmanager.ps2.data.converter

import com.usbdiskmanager.ps2.domain.model.ConversionProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts a PS2 ISO file to the USBExtreme/OPL UL format.
 *
 * UL format splits the ISO into 1 GB parts named:
 *   ul.[GAMEID_normalized].[part_index_hex2]
 * e.g.  ul.SLUS01234.00  ul.SLUS01234.01
 *
 * A ul.cfg catalog file (binary, 64 bytes per entry) is maintained in outputDir.
 *
 * The converter streams the ISO in 4 MB chunks — never loads the whole file
 * into RAM. It supports resuming from any byte offset, so interrupted jobs
 * can continue exactly where they stopped.
 */
@Singleton
class UlConverter @Inject constructor() {

    companion object {
        const val PART_SIZE: Long = 1_073_741_824L // 1 GiB
        const val BUFFER_SIZE: Int = 4 * 1024 * 1024 // 4 MiB
        const val PROGRESS_INTERVAL: Long = 500L // emit every 500 ms
    }

    /**
     * Converts [isoFile] → UL parts in [outputDir].
     *
     * @param resumeOffset byte offset to resume from (0 = fresh start).
     * Emits [ConversionProgress] updates on every [PROGRESS_INTERVAL] ms.
     */
    fun convert(
        isoFile: File,
        gameId: String,
        outputDir: String,
        resumeOffset: Long = 0L
    ): Flow<ConversionProgress> = flow {
        val totalBytes = isoFile.length()
        val outputDirectory = File(outputDir).also { it.mkdirs() }
        val normalizedId = normalizeGameId(gameId)
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesWritten = resumeOffset
        var currentPart = (resumeOffset / PART_SIZE).toInt()
        var positionInPart = resumeOffset % PART_SIZE
        var lastEmit = System.currentTimeMillis()
        var lastBytes = resumeOffset
        val startTime = System.currentTimeMillis()

        Timber.d("UlConverter: starting convert $gameId total=$totalBytes resume=$resumeOffset")

        FileInputStream(isoFile).use { fis ->
            BufferedInputStream(fis, BUFFER_SIZE).use { bis ->
                // Skip already-written bytes
                if (resumeOffset > 0) {
                    Timber.d("Skipping $resumeOffset bytes (resume)")
                    var skipped = 0L
                    while (skipped < resumeOffset) {
                        val toSkip = minOf(resumeOffset - skipped, BUFFER_SIZE.toLong())
                        val actual = bis.skip(toSkip)
                        if (actual <= 0) break
                        skipped += actual
                    }
                }

                // Determine starting part output file (append if partial)
                var partFile = openPartFile(outputDirectory, normalizedId, currentPart, positionInPart)

                while (true) {
                    if (!currentCoroutineContext().isActive) {
                        Timber.d("UlConverter: cancelled at $bytesWritten")
                        partFile.close()
                        break
                    }

                    val toRead = minOf(
                        buffer.size.toLong(),
                        totalBytes - bytesWritten,
                        PART_SIZE - positionInPart
                    ).toInt()

                    if (toRead <= 0) {
                        partFile.close()
                        break
                    }

                    val read = bis.read(buffer, 0, toRead)
                    if (read <= 0) {
                        partFile.close()
                        break
                    }

                    partFile.write(buffer, 0, read)
                    bytesWritten += read
                    positionInPart += read

                    // Roll over to next part
                    if (positionInPart >= PART_SIZE) {
                        partFile.close()
                        currentPart++
                        positionInPart = 0L
                        if (bytesWritten < totalBytes) {
                            partFile = openPartFile(outputDirectory, normalizedId, currentPart, 0L)
                        }
                    }

                    // Emit progress at intervals
                    val now = System.currentTimeMillis()
                    if (now - lastEmit >= PROGRESS_INTERVAL) {
                        val elapsed = (now - lastEmit).coerceAtLeast(1L)
                        val bytesDelta = bytesWritten - lastBytes
                        val speedMbps = (bytesDelta.toDouble() / 1_048_576.0) / (elapsed / 1000.0)
                        val remaining = if (speedMbps > 0.0) {
                            ((totalBytes - bytesWritten) / 1_048_576.0 / speedMbps).toLong()
                        } else Long.MAX_VALUE

                        emit(
                            ConversionProgress(
                                gameId = gameId,
                                isoPath = isoFile.absolutePath,
                                bytesWritten = bytesWritten,
                                totalBytes = totalBytes,
                                currentPart = currentPart,
                                speedMbps = speedMbps,
                                remainingSeconds = remaining
                            )
                        )
                        lastEmit = now
                        lastBytes = bytesWritten
                    }
                }

                // Final progress
                if (bytesWritten == totalBytes) {
                    emit(
                        ConversionProgress(
                            gameId = gameId,
                            isoPath = isoFile.absolutePath,
                            bytesWritten = bytesWritten,
                            totalBytes = totalBytes,
                            currentPart = currentPart,
                            speedMbps = 0.0,
                            remainingSeconds = 0L
                        )
                    )
                    Timber.d("UlConverter: completed $gameId in ${System.currentTimeMillis() - startTime} ms")
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Delete all part files for a game (used by cancel).
     */
    fun deletePartFiles(outputDir: String, gameId: String) {
        val normalizedId = normalizeGameId(gameId)
        val dir = File(outputDir)
        if (!dir.exists()) return
        dir.listFiles { f -> f.name.startsWith("ul.$normalizedId.") }
            ?.forEach { it.delete() }
        Timber.d("Deleted UL parts for $gameId in $outputDir")
    }

    /**
     * Returns the total bytes already written based on existing part files.
     * Used to calculate resume offset after a crash.
     */
    fun calculateResumeOffset(outputDir: String, gameId: String): Long {
        val normalizedId = normalizeGameId(gameId)
        val dir = File(outputDir)
        if (!dir.exists()) return 0L
        val parts = dir.listFiles { f -> f.name.startsWith("ul.$normalizedId.") }
            ?.sortedBy { it.name }
            ?: return 0L
        return parts.sumOf { it.length() }
    }

    fun partFileName(gameId: String, part: Int): String =
        "ul.${normalizeGameId(gameId)}.${part.toString(16).padStart(2, '0')}"

    private fun normalizeGameId(gameId: String): String =
        gameId.replace("_", "").replace(".", "").uppercase()

    private fun openPartFile(dir: File, normalizedId: String, part: Int, offset: Long): RandomAccessFile {
        val name = "ul.$normalizedId.${part.toString(16).padStart(2, '0')}"
        val file = File(dir, name)
        val raf = RandomAccessFile(file, "rw")
        if (offset > 0 && file.length() >= offset) {
            raf.seek(offset)
        } else {
            raf.setLength(0L)
        }
        return raf
    }
}
