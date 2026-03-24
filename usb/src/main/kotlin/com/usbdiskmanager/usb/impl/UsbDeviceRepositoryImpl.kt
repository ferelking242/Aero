package com.usbdiskmanager.usb.impl

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.StatFs
import com.usbdiskmanager.core.model.DiskDevice
import com.usbdiskmanager.core.model.DiskOperationResult
import com.usbdiskmanager.core.model.FileSystemType
import com.usbdiskmanager.core.util.executeShellCommand
import com.usbdiskmanager.usb.api.UsbDeviceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val ACTION_USB_PERMISSION = "com.usbdiskmanager.USB_PERMISSION"

private data class UsbMountInfo(
    val blockDevice: String,
    val mountPoint: String,
    val fsType: String
)

@Singleton
class UsbDeviceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : UsbDeviceRepository {

    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _connectedDevices = MutableStateFlow<List<DiskDevice>>(emptyList())
    override val connectedDevices = _connectedDevices.asStateFlow()

    private val rawDeviceMap = mutableMapOf<String, UsbDevice>()

    init {
        refreshConnectedDevices()
    }

    override fun refreshConnectedDevices() {
        val mounts = findExternalMountPoints()
        Timber.d("USB mounts found: ${mounts.map { it.mountPoint }}")

        val devices = usbManager.deviceList.values
        val diskDevices = devices.mapNotNull { usbDevice ->
            if (isMassStorageDevice(usbDevice)) {
                val id = deviceId(usbDevice)
                rawDeviceMap[id] = usbDevice
                buildDiskDevice(usbDevice, mounts)
            } else null
        }
        Timber.d("USB mass storage devices: ${diskDevices.size}")
        _connectedDevices.value = diskDevices
    }

    override fun onDeviceAttached(usbDevice: UsbDevice) {
        if (isMassStorageDevice(usbDevice)) {
            val id = deviceId(usbDevice)
            rawDeviceMap[id] = usbDevice
            val mounts = findExternalMountPoints()
            val diskDevice = buildDiskDevice(usbDevice, mounts)
            val current = _connectedDevices.value.toMutableList()
            current.removeAll { it.id == id }
            current.add(diskDevice)
            _connectedDevices.value = current
            Timber.i("USB mass storage attached: ${usbDevice.deviceName}, mountPoint=${diskDevice.mountPoint}")
        } else {
            Timber.d("Ignoring non-mass-storage USB device: ${usbDevice.deviceName} class=${usbDevice.deviceClass}")
        }
    }

    override fun onDeviceDetached(usbDevice: UsbDevice) {
        val id = deviceId(usbDevice)
        rawDeviceMap.remove(id)
        val current = _connectedDevices.value.toMutableList()
        current.removeAll { it.id == id }
        _connectedDevices.value = current
        Timber.i("USB device detached: ${usbDevice.deviceName}")
    }

    override suspend fun requestPermission(device: UsbDevice): Boolean =
        suspendCancellableCoroutine { cont ->
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                device.deviceId,
                Intent(ACTION_USB_PERMISSION).apply { `package` = context.packageName },
                PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            )

            val filter = IntentFilter(ACTION_USB_PERMISSION)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action == ACTION_USB_PERMISSION) {
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        context.unregisterReceiver(this)
                        if (cont.isActive) cont.resume(granted)
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }

            if (usbManager.hasPermission(device)) {
                context.unregisterReceiver(receiver)
                cont.resume(true)
            } else {
                usbManager.requestPermission(device, permissionIntent)
            }

            cont.invokeOnCancellation {
                try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            }
        }

    override fun hasPermission(device: UsbDevice): Boolean =
        usbManager.hasPermission(device)

    /**
     * Android auto-mounts USB drives. This method refreshes the device state
     * to detect if the OS has auto-mounted the drive. If not found, returns
     * an honest error message instead of a fake success.
     */
    override suspend fun mountDevice(deviceId: String): DiskOperationResult {
        val device = rawDeviceMap[deviceId]
            ?: return DiskOperationResult.Error("Périphérique introuvable: $deviceId")

        return try {
            val mounts = findExternalMountPoints()
            val mountInfo = matchDeviceToMount(device, mounts)

            if (mountInfo != null) {
                updateDeviceMountState(deviceId, mountInfo.mountPoint, true)
                val (total, free) = getSpaceInfo(mountInfo.mountPoint)
                updateDeviceSpaceInfo(deviceId, total, free, mountInfo.fsType)
                DiskOperationResult.Success(
                    "Monté sur ${mountInfo.mountPoint} — " +
                    "Total: ${formatBytes(total)}, Libre: ${formatBytes(free)}"
                )
            } else {
                DiskOperationResult.Error(
                    "Le système n'a pas encore monté ce périphérique.\n" +
                    "Attends quelques secondes après avoir branché la clé, puis appuie sur Actualiser."
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Mount check failed for $deviceId")
            DiskOperationResult.Error("Erreur: ${e.message}")
        }
    }

    override suspend fun unmountDevice(deviceId: String): DiskOperationResult {
        val device = _connectedDevices.value.find { it.id == deviceId }
            ?: return DiskOperationResult.Error("Périphérique introuvable")

        return try {
            val mountPoint = device.mountPoint
            if (mountPoint != null) {
                val result = executeShellCommand("umount \"$mountPoint\" 2>&1 || umount -l \"$mountPoint\" 2>&1")
                if (result.isSuccess || !File(mountPoint).exists()) {
                    updateDeviceMountState(deviceId, null, false)
                    DiskOperationResult.Success("Périphérique éjecté en toute sécurité")
                } else {
                    updateDeviceMountState(deviceId, null, false)
                    DiskOperationResult.Success("Périphérique marqué comme éjecté (${result.output.take(80)})")
                }
            } else {
                updateDeviceMountState(deviceId, null, false)
                DiskOperationResult.Success("Périphérique non monté")
            }
        } catch (e: Exception) {
            Timber.e(e, "Unmount failed for $deviceId")
            DiskOperationResult.Error("Erreur d'éjection: ${e.message}")
        }
    }

    override fun formatDevice(
        deviceId: String,
        fileSystem: String,
        label: String
    ): Flow<DiskOperationResult> = flow {
        emit(DiskOperationResult.Progress(0, "Préparation du formatage…"))
        val device = rawDeviceMap[deviceId]
        if (device == null) {
            emit(DiskOperationResult.Error("Périphérique introuvable"))
            return@flow
        }

        emit(DiskOperationResult.Progress(10, "Recherche du bloc device…"))
        val mounts = findExternalMountPoints()
        val mountInfo = matchDeviceToMount(device, mounts)
        if (mountInfo == null) {
            emit(DiskOperationResult.Error(
                "Impossible de trouver le bloc device. " +
                "Le formatage nécessite les accès root ou une prise en charge fabricant."
            ))
            return@flow
        }

        emit(DiskOperationResult.Progress(20, "Démontage…"))
        executeShellCommand("umount \"${mountInfo.mountPoint}\" 2>&1 || true")

        emit(DiskOperationResult.Progress(30, "Formatage en $fileSystem…"))
        val cmd = buildFormatCommand(mountInfo.blockDevice, fileSystem, label)
        Timber.d("Format command: $cmd")
        val result = executeShellCommand(cmd)

        if (result.isSuccess) {
            emit(DiskOperationResult.Progress(100, "Formatage terminé !"))
            emit(DiskOperationResult.Success("Périphérique formaté en $fileSystem avec succès"))
        } else {
            emit(DiskOperationResult.Error(
                "Formatage échoué: ${result.output.take(200)}\n\n" +
                "Note: le formatage nécessite les accès root."
            ))
        }
    }

    override suspend fun refreshDevice(deviceId: String): DiskDevice? {
        val device = rawDeviceMap[deviceId] ?: return null
        val mounts = findExternalMountPoints()
        val diskDevice = buildDiskDevice(device, mounts)
        val current = _connectedDevices.value.toMutableList()
        val index = current.indexOfFirst { it.id == deviceId }
        if (index >= 0) current[index] = diskDevice else current.add(diskDevice)
        _connectedDevices.value = current
        return diskDevice
    }

    override fun getRawDevice(deviceId: String): UsbDevice? = rawDeviceMap[deviceId]

    // ─── Device helpers ───────────────────────────────────────────────────────

    private fun deviceId(device: UsbDevice): String =
        "${device.vendorId}_${device.productId}_${device.deviceName.hashCode()}"

    /**
     * Only accept USB Mass Storage (class 8) devices.
     * Fixed: removed the erroneous `return true` that accepted ALL USB devices.
     */
    private fun isMassStorageDevice(device: UsbDevice): Boolean {
        if (device.deviceClass == 8) return true
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == 8) return true
        }
        return false
    }

    private fun buildDiskDevice(device: UsbDevice, mounts: List<UsbMountInfo>): DiskDevice {
        val id = deviceId(device)
        val mountInfo = matchDeviceToMount(device, mounts)
        val mountPoint = mountInfo?.mountPoint
        val (total, free) = getSpaceInfo(mountPoint)
        val fsType = mountInfo?.fsType?.let { normalizeFsType(it) }

        return DiskDevice(
            id = id,
            name = device.productName?.takeIf { it.isNotBlank() }
                ?: "USB Drive (${device.vendorId.toString(16).uppercase()}:${device.productId.toString(16).uppercase()})",
            vendorId = device.vendorId,
            productId = device.productId,
            serialNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) device.serialNumber else null,
            totalSpace = total,
            freeSpace = free,
            usedSpace = total - free,
            fileSystem = FileSystemType.fromString(fsType),
            mountPoint = mountPoint,
            isMounted = mountPoint != null,
            isWritable = mountPoint != null && File(mountPoint).canWrite()
        )
    }

    // ─── Mount detection ──────────────────────────────────────────────────────

    /**
     * Find external USB / SD card mount points by reading /proc/mounts.
     * Falls back to scanning /storage/ for XXXX-XXXX directories.
     */
    private fun findExternalMountPoints(): List<UsbMountInfo> {
        val result = mutableListOf<UsbMountInfo>()

        try {
            File("/proc/mounts").forEachLine { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 3) {
                    val blk = parts[0]
                    val mnt = parts[1]
                    val fs = parts[2]
                    if (isExternalMount(mnt, fs)) {
                        result.add(UsbMountInfo(blk, mnt, fs))
                        Timber.v("External mount found: $mnt ($fs)")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w("Cannot read /proc/mounts: ${e.message}")
        }

        if (result.isEmpty()) {
            try {
                File("/storage").listFiles()
                    ?.filter { dir ->
                        dir.isDirectory &&
                        dir.name.matches(Regex("[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}")) &&
                        dir.canRead()
                    }
                    ?.forEach { dir ->
                        result.add(UsbMountInfo("vold", dir.absolutePath, "vfat"))
                        Timber.v("Storage dir found: ${dir.absolutePath}")
                    }
            } catch (e: Exception) {
                Timber.w("Cannot scan /storage: ${e.message}")
            }
        }

        return result
    }

    private fun isExternalMount(mountPath: String, fsType: String): Boolean {
        if (!mountPath.startsWith("/storage/") && !mountPath.startsWith("/mnt/media_rw/"))
            return false
        if (mountPath.contains("/emulated"))
            return false
        val usbFs = setOf("vfat", "exfat", "fuseblk", "ntfs", "ufsd", "texfat", "sdfat", "fuse")
        return fsType in usbFs || mountPath.matches(Regex(".*/[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}.*"))
    }

    /**
     * Try to match a specific UsbDevice to an external mount point.
     * Strategy:
     *   1. Sysfs: parse bus/device numbers → find block device in sysfs → match /proc/mounts
     *   2. If exactly one USB device and one mount → assign directly
     *   3. Otherwise → null (cannot reliably match multiple devices)
     */
    private fun matchDeviceToMount(device: UsbDevice, mounts: List<UsbMountInfo>): UsbMountInfo? {
        if (mounts.isEmpty()) return null

        val sysfsMatch = tryMatchViaSysfs(device, mounts)
        if (sysfsMatch != null) return sysfsMatch

        val massStorageCount = usbManager.deviceList.values.count { isMassStorageDevice(it) }
        if (massStorageCount == 1 && mounts.size >= 1) {
            Timber.d("Single USB device, assigning first mount: ${mounts.first().mountPoint}")
            return mounts.first()
        }

        return null
    }

    private fun tryMatchViaSysfs(device: UsbDevice, mounts: List<UsbMountInfo>): UsbMountInfo? {
        return try {
            // device.deviceName = "/dev/bus/usb/001/002"
            val parts = device.deviceName.split("/")
            val busNum = parts.getOrNull(4)?.trimStart('0')?.ifEmpty { "0" }?.toIntOrNull() ?: return null
            val devNum = parts.getOrNull(5)?.trimStart('0')?.ifEmpty { "0" }?.toIntOrNull() ?: return null

            val sysfsDir = File("/sys/bus/usb/devices/").listFiles()?.firstOrNull { dir ->
                try {
                    File("${dir.absolutePath}/busnum").readText().trim().toInt() == busNum &&
                    File("${dir.absolutePath}/devnum").readText().trim().toInt() == devNum
                } catch (_: Exception) { false }
            } ?: return null

            var blockName: String? = null
            sysfsDir.walkTopDown().maxDepth(10).forEach { file ->
                if (file.name == "block" && file.isDirectory && blockName == null) {
                    blockName = file.listFiles()?.firstOrNull()?.name
                }
            }

            if (blockName == null) return null
            Timber.v("Sysfs block device for ${device.deviceName}: $blockName")

            mounts.firstOrNull { mount ->
                mount.blockDevice.contains(blockName!!) ||
                mount.blockDevice.endsWith("/$blockName") ||
                mount.blockDevice.endsWith("/${blockName}1") ||
                mount.blockDevice.endsWith("/${blockName}p1")
            }
        } catch (e: Exception) {
            Timber.v("Sysfs matching failed: ${e.message}")
            null
        }
    }

    // ─── Space info ──────────────────────────────────────────────────────────

    /**
     * Get total/free bytes for a MOUNTED path (not a block device).
     * StatFs only works on mounted file system paths.
     */
    private fun getSpaceInfo(mountPoint: String?): Pair<Long, Long> {
        if (mountPoint == null) return Pair(0L, 0L)
        return try {
            val stat = StatFs(mountPoint)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            Timber.d("StatFs($mountPoint): total=${formatBytes(total)}, free=${formatBytes(free)}")
            Pair(total, free)
        } catch (e: Exception) {
            Timber.w("StatFs failed on $mountPoint: ${e.message}")
            Pair(0L, 0L)
        }
    }

    private fun normalizeFsType(raw: String): String = when (raw.lowercase()) {
        "vfat" -> "FAT32"
        "fuseblk" -> "exFAT"
        "exfat", "texfat", "sdfat" -> "exFAT"
        "ntfs", "ufsd" -> "NTFS"
        "ext4" -> "EXT4"
        "ext3" -> "EXT3"
        "ext2" -> "EXT2"
        else -> raw.uppercase()
    }

    // ─── State updates ───────────────────────────────────────────────────────

    private fun updateDeviceMountState(deviceId: String, mountPoint: String?, isMounted: Boolean) {
        val current = _connectedDevices.value.toMutableList()
        val index = current.indexOfFirst { it.id == deviceId }
        if (index >= 0) {
            current[index] = current[index].copy(
                mountPoint = mountPoint,
                isMounted = isMounted,
                isWritable = isMounted && mountPoint != null && File(mountPoint).canWrite()
            )
            _connectedDevices.value = current
        }
    }

    private fun updateDeviceSpaceInfo(deviceId: String, total: Long, free: Long, fsType: String) {
        val current = _connectedDevices.value.toMutableList()
        val index = current.indexOfFirst { it.id == deviceId }
        if (index >= 0) {
            current[index] = current[index].copy(
                totalSpace = total,
                freeSpace = free,
                usedSpace = total - free,
                fileSystem = FileSystemType.fromString(normalizeFsType(fsType))
            )
            _connectedDevices.value = current
        }
    }

    // ─── Format helpers ──────────────────────────────────────────────────────

    private fun buildFormatCommand(blockDevice: String, fileSystem: String, label: String): String {
        val labelFlag = if (label.isNotEmpty()) "-n \"$label\"" else ""
        return when (fileSystem.uppercase()) {
            "FAT32" -> "mkfs.vfat -F 32 $labelFlag $blockDevice"
            "EXFAT" -> "mkfs.exfat $labelFlag $blockDevice"
            "NTFS" -> "mkfs.ntfs --fast $labelFlag $blockDevice"
            "EXT4" -> "mkfs.ext4 $labelFlag $blockDevice"
            else -> "mkfs.vfat -F 32 $blockDevice"
        }
    }

    // ─── Utility ─────────────────────────────────────────────────────────────

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) { value /= 1024; unit++ }
        return "%.1f %s".format(value, units[unit])
    }
}
