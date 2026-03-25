package com.usbdiskmanager.ps2.data.scanner

import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure-Kotlin ISO 9660 reader.
 *
 * Extracts the game serial number from SYSTEM.CNF (or SYSTEM.INI for CD discs)
 * without loading the whole ISO into memory.
 *
 * Sector size is always 2048 bytes.
 * Primary Volume Descriptor is at sector 16.
 */
object Iso9660Parser {

    private const val SECTOR_SIZE = 2048
    private const val PVD_SECTOR = 16L

    /**
     * Read the PS2 game serial from SYSTEM.CNF inside an ISO file.
     * Returns null if not found or if the ISO is not a valid PS2 disc.
     */
    fun readGameId(isoFile: File): String? {
        if (!isoFile.exists() || !isoFile.isFile) return null
        return try {
            RandomAccessFile(isoFile, "r").use { raf ->
                val rootRecord = readRootDirectoryRecord(raf) ?: return null
                val systemCnf = findFileInDirectory(
                    raf, rootRecord.extentLba, rootRecord.dataLength, "SYSTEM.CNF"
                ) ?: findFileInDirectory(
                    raf, rootRecord.extentLba, rootRecord.dataLength, "SYSTEM.INI"
                ) ?: return null

                val content = readFileContent(raf, systemCnf.extentLba, systemCnf.dataLength)
                parseBootLine(content)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to read game ID from ${isoFile.name}")
            null
        }
    }

    /**
     * Returns disc type based on volume descriptor flags (CD vs DVD).
     * Heuristic: if ISO < 700MB treat as CD, else DVD.
     */
    fun guessDiscType(isoFile: File): String =
        if (isoFile.length() < 734_003_200L) "CD" else "DVD"

    // ────────────────────────────────────────────
    // Private helpers
    // ────────────────────────────────────────────

    private data class DirectoryRecord(
        val extentLba: Long,
        val dataLength: Long,
        val isDirectory: Boolean,
        val name: String
    )

    private fun readRootDirectoryRecord(raf: RandomAccessFile): DirectoryRecord? {
        raf.seek(PVD_SECTOR * SECTOR_SIZE)
        val pvd = ByteArray(SECTOR_SIZE)
        raf.readFully(pvd)

        // Verify PVD signature
        if (pvd[0].toInt() != 0x01) return null
        val id = String(pvd, 1, 5)
        if (id != "CD001") return null

        // Root directory record starts at byte 156 in PVD
        return parseDirectoryRecord(pvd, 156)
    }

    private fun parseDirectoryRecord(data: ByteArray, offset: Int): DirectoryRecord? {
        if (offset >= data.size) return null
        val len = data[offset].toInt() and 0xFF
        if (len < 33) return null

        val buf = ByteBuffer.wrap(data, offset, len).order(ByteOrder.LITTLE_ENDIAN)
        val extentLba = buf.getInt(offset + 2).toLong() and 0xFFFFFFFFL
        val dataLen = buf.getInt(offset + 10).toLong() and 0xFFFFFFFFL
        val flags = data[offset + 25].toInt() and 0xFF
        val isDir = (flags and 0x02) != 0
        val idLen = data[offset + 32].toInt() and 0xFF
        val name = if (idLen > 0) {
            try {
                String(data, offset + 33, idLen, Charsets.ISO_8859_1)
                    .trimEnd('\u0000', '\u0001')
                    .substringBefore(';')
            } catch (_: Exception) { "" }
        } else ""

        return DirectoryRecord(extentLba, dataLen, isDir, name)
    }

    private fun findFileInDirectory(
        raf: RandomAccessFile,
        dirLba: Long,
        dirLength: Long,
        targetName: String
    ): DirectoryRecord? {
        val data = ByteArray(dirLength.coerceAtMost(65536L).toInt())
        raf.seek(dirLba * SECTOR_SIZE)
        raf.read(data)

        var pos = 0
        while (pos < data.size) {
            val len = data[pos].toInt() and 0xFF
            if (len == 0) {
                // Move to next sector boundary
                val next = ((pos / SECTOR_SIZE) + 1) * SECTOR_SIZE
                if (next >= data.size) break
                pos = next
                continue
            }
            val record = parseDirectoryRecord(data, pos)
            if (record != null && !record.isDirectory) {
                if (record.name.equals(targetName, ignoreCase = true)) return record
            }
            pos += len
        }
        return null
    }

    private fun readFileContent(raf: RandomAccessFile, lba: Long, length: Long): String {
        val bytes = ByteArray(length.coerceAtMost(4096L).toInt())
        raf.seek(lba * SECTOR_SIZE)
        raf.read(bytes)
        return String(bytes, Charsets.ISO_8859_1)
    }

    /**
     * Parse BOOT2 (DVD) or BOOT (CD) line from SYSTEM.CNF.
     * Example: `BOOT2 = cdrom0:\SCES_533.98;1`
     * Returns normalized game ID like "SCES_533.98".
     */
    private fun parseBootLine(content: String): String? {
        val regex = Regex("""BOOT2?\s*=\s*cdrom[^\\]?\\([A-Z_]+\.\d+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(content)
        return match?.groupValues?.getOrNull(1)
            ?.trim()
            ?.trimEnd(';', '1', '0', ';')
            ?.trim()
    }
}
