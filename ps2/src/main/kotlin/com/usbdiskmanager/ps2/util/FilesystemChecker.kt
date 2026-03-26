package com.usbdiskmanager.ps2.util

import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class MountInfo(
    val mountPoint: String,
    val fsType: String,
    val blockDevice: String
)

@Singleton
class FilesystemChecker @Inject constructor() {

    fun getFsType(path: String): String? {
        val mounts = readMounts()
        val best = mounts
            .filter { path.startsWith(it.mountPoint) }
            .maxByOrNull { it.mountPoint.length }
        return best?.fsType
    }

    fun isFat32(path: String): Boolean {
        val fs = getFsType(path)?.lowercase() ?: return false
        return fs in setOf("vfat", "fat32", "msdos")
    }

    fun isExternalMount(path: String): Boolean {
        if (path.contains("/sdcard") || path.contains("/storage/emulated")) return false
        val mounts = readMounts()
        val best = mounts
            .filter { path.startsWith(it.mountPoint) }
            .maxByOrNull { it.mountPoint.length }
        return best != null && isExternalMountPoint(best.mountPoint, best.fsType)
    }

    /**
     * Returns a list of all external/USB mount points, deduplicated.
     * The same USB can appear at both /storage/XXXX-XXXX and /mnt/media_rw/XXXX-XXXX.
     * We prefer /storage/ over /mnt/media_rw/ and keep only one entry per volume.
     * Deduplication uses both folder name AND block device to avoid false duplicates.
     */
    fun listExternalMounts(): List<MountInfo> {
        val all = readMounts().filter { isExternalMountPoint(it.mountPoint, it.fsType) }

        val seenNames = mutableSetOf<String>()
        val seenBlocks = mutableSetOf<String>()
        val deduped = mutableListOf<MountInfo>()

        // First pass: prefer /storage/ mounts (user-accessible)
        for (m in all) {
            val nameKey = File(m.mountPoint).name.lowercase()
            val blockKey = m.blockDevice.lowercase().let {
                // Normalize: /dev/block/sda1 and /dev/block/sda are the same disk
                it.trimEnd('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
            }
            val isNewByName = seenNames.add(nameKey)
            val isNewByBlock = if (m.blockDevice != "vold" && m.blockDevice.isNotBlank())
                seenBlocks.add(blockKey) else true
            if (m.mountPoint.startsWith("/storage/") && isNewByName && isNewByBlock) {
                deduped.add(m)
            }
        }
        // Second pass: add remaining mounts not already covered
        for (m in all) {
            val nameKey = File(m.mountPoint).name.lowercase()
            val blockKey = m.blockDevice.lowercase().trimEnd('0','1','2','3','4','5','6','7','8','9')
            val isNewByName = seenNames.add(nameKey)
            val isNewByBlock = if (m.blockDevice != "vold" && m.blockDevice.isNotBlank())
                seenBlocks.add(blockKey) else true
            if (!m.mountPoint.startsWith("/storage/") && isNewByName && isNewByBlock) {
                deduped.add(m)
            }
        }

        Timber.d("listExternalMounts: ${deduped.map { it.mountPoint }}")
        return deduped
    }

    private fun isExternalMountPoint(mnt: String, fs: String): Boolean {
        val externalPrefixes = listOf(
            "/storage/", "/mnt/media_rw/", "/mnt/usb", "/mnt/ext", "/mnt/sdcard2",
            "/mnt/external", "/mnt/usbdisk"
        )
        if (!externalPrefixes.any { mnt.startsWith(it) }) return false
        if (mnt.contains("/emulated") || mnt == "/storage/emulated") return false
        val usbFs = setOf(
            "vfat", "exfat", "fuseblk", "ntfs", "ufsd", "texfat", "sdfat",
            "fuse", "fat32", "msdos"
        )
        return fs.lowercase() in usbFs ||
            mnt.matches(Regex(".*/[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}.*"))
    }

    private fun readMounts(): List<MountInfo> {
        val result = mutableListOf<MountInfo>()
        for (mountFile in listOf("/proc/mounts", "/proc/self/mounts")) {
            try {
                File(mountFile).forEachLine { line ->
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        result.add(MountInfo(parts[1], parts[2], parts[0]))
                    }
                }
                if (result.isNotEmpty()) return result
            } catch (e: Exception) {
                Timber.w(e, "Could not read $mountFile")
            }
        }
        scanStorageDir("/storage", result)
        scanStorageDir("/mnt/media_rw", result)
        return result
    }

    private fun scanStorageDir(root: String, result: MutableList<MountInfo>) {
        try {
            File(root).listFiles()
                ?.filter { it.isDirectory && it.canRead() &&
                    !it.name.equals("emulated", true) &&
                    !it.name.equals("self", true) }
                ?.forEach { dir ->
                    result.add(MountInfo(dir.absolutePath, "vfat", "vold"))
                }
        } catch (_: Exception) {}
    }

    fun labelFor(mountPoint: String): String {
        val name = File(mountPoint).name
        return "USB ($name)"
    }
}
