package com.p0lymatic.flashpoint.ui

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbDevice
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.p0lymatic.flashpoint.BuildConfig
import com.p0lymatic.flashpoint.core.BurnProgress
import com.p0lymatic.flashpoint.core.PartitionScheme
import com.p0lymatic.flashpoint.core.RawImageBurner
import com.p0lymatic.flashpoint.core.VentoyInstallProgress
import com.p0lymatic.flashpoint.core.VentoyInstaller
import com.p0lymatic.flashpoint.core.VentoyPhase
import com.p0lymatic.flashpoint.core.VentoyRelease
import com.p0lymatic.flashpoint.usb.UsbConnectionState
import com.p0lymatic.flashpoint.usb.UsbDeviceMonitor
import com.p0lymatic.flashpoint.usb.UsbMassStorageStreamer
import com.p0lymatic.flashpoint.usb.VirtualDriveMode
import com.p0lymatic.flashpoint.usb.VirtualUsbDriveStreamer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.io.RandomAccessFile
class FlashPointViewModel(application: Application) : AndroidViewModel(application) {

    private val usbMonitor = UsbDeviceMonitor(application)

    private val _usbState = MutableStateFlow<UsbConnectionState>(UsbConnectionState.NoDevice)
    val usbState: StateFlow<UsbConnectionState> = _usbState.asStateFlow()
    val availableDevices = usbMonitor.devices
    val deviceCapacities = usbMonitor.deviceCapacities

    private val _isDemoModeEnabled = MutableStateFlow(false)
    val isDemoModeEnabled: StateFlow<Boolean> = _isDemoModeEnabled.asStateFlow()

    private val _selectedVirtualMode = MutableStateFlow<VirtualDriveMode?>(null)
    val selectedVirtualMode: StateFlow<VirtualDriveMode?> = _selectedVirtualMode.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    fun setDemoModeEnabled(enabled: Boolean) {
        if (!BuildConfig.DEBUG && enabled) return
        _isDemoModeEnabled.value = enabled
        if (enabled) {
            if (_selectedVirtualMode.value == null && _usbState.value !is UsbConnectionState.Connected) {
                selectVirtualDevice(VirtualDriveMode.SUCCESS)
            }
            if (_ventoyRelease.value == null) {
                ensureDemoVentoyRelease()
            }
        } else {
            cleanupVirtualDisk()
            val dummyFile = File(getApplication<Application>().cacheDir, "ubuntu-24.04-demo.iso")
            if (dummyFile.exists()) dummyFile.delete()
            if (selectedImageUri.value?.toString()?.contains("ubuntu-24.04-demo.iso") == true) {
                selectedImageUri.value = null
                selectedImageName.value = null
            }
            if (_selectedVirtualMode.value != null) {
                _selectedVirtualMode.value = null
                _usbState.value = usbMonitor.state.value
            }
        }
    }

    fun selectVirtualDevice(mode: VirtualDriveMode) {
        if (!BuildConfig.DEBUG) return
        _selectedVirtualMode.value = mode
        val modeLabel = when (mode) {
            VirtualDriveMode.SUCCESS -> "(Success)"
            VirtualDriveMode.IO_ERROR -> "(I/O Error @ 60%)"
            VirtualDriveMode.VERIFY_FAILURE -> "(Corrupt MBR on Verify)"
        }
        _usbState.value = UsbConnectionState.Connected(
            device = null,
            capacityBytes = VirtualUsbDriveStreamer.TOTAL_CAPACITY_BYTES,
            vendor = "Virtual RAM Disk",
            model = modeLabel,
            isVirtual = true,
            virtualMode = mode
        )
    }

    fun cleanupVirtualDisk() {
        VirtualUsbDriveStreamer.cleanupFile(getApplication())
    }

    fun refreshUsbDevices() {
        usbMonitor.scanForDevices()
    }

    fun selectDevice(device: UsbDevice) {
        _selectedVirtualMode.value = null
        usbMonitor.selectDevice(device)
    }
    val selectedTab = MutableStateFlow(0)
    val selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageName = MutableStateFlow<String?>(null)

    private val _burnProgress = MutableStateFlow<BurnProgress?>(null)
    val burnProgress: StateFlow<BurnProgress?> = _burnProgress.asStateFlow()

    private val _isBurning = MutableStateFlow(false)
    val isBurning: StateFlow<Boolean> = _isBurning.asStateFlow()

    private val _ventoyRelease = MutableStateFlow<VentoyRelease?>(null)
    val ventoyRelease: StateFlow<VentoyRelease?> = _ventoyRelease.asStateFlow()

    private val _ventoyInstallProgress = MutableStateFlow<VentoyInstallProgress?>(null)
    val ventoyInstallProgress: StateFlow<VentoyInstallProgress?> = _ventoyInstallProgress.asStateFlow()
    private val _doneMetrics = MutableStateFlow<DoneMetrics?>(null)
    val doneMetrics: StateFlow<DoneMetrics?> = _doneMetrics.asStateFlow()

    private val _failedDetails = MutableStateFlow<FailedDetails?>(null)
    val failedDetails: StateFlow<FailedDetails?> = _failedDetails.asStateFlow()

    fun resetDoneState() {
        _doneMetrics.value = null
        _burnProgress.value = null
        _ventoyInstallProgress.value = null
        _isBurning.value = false
        _errorMessage.value = null
        cleanupVirtualDisk()
    }

    fun resetFailedState() {
        _failedDetails.value = null
        _burnProgress.value = null
        _ventoyInstallProgress.value = null
        _isBurning.value = false
        _errorMessage.value = null
        cleanupVirtualDisk()
    }

    val isCheckingVentoy = MutableStateFlow(false)
    val isDownloadingOfflineVentoy = MutableStateFlow(false)
    val offlineVentoyFileExists = MutableStateFlow(false)

    val selectedPartitionScheme = MutableStateFlow(PartitionScheme.GPT)
    val selectedArchitecture = MutableStateFlow(com.p0lymatic.flashpoint.core.TargetArchitecture.X86_64)
    val isChangelogExploded = MutableStateFlow(false)
    val showCancelDialog = MutableStateFlow(false)

    private var burnJob: Job? = null
    private var ventoyJob: Job? = null

    init {
        usbMonitor.register()
        usbMonitor.scanForDevices()
        checkOfflineVentoyStatus()
        viewModelScope.launch {
            usbMonitor.state.collect { state ->
                if (_selectedVirtualMode.value == null) {
                    _usbState.value = state
                }
            }
        }
    }

    fun onImageSelected(uri: Uri, displayName: String?) {
        selectedImageUri.value = uri
        selectedImageName.value = displayName
    }

    fun ensureDummyDemoImage(): Pair<Uri, Long> {
        val app = getApplication<Application>()
        val dummyFile = File(app.cacheDir, "ubuntu-24.04-demo.iso")
        val dummySize = 100L * 1024L * 1024L // 100 MB
        if (!dummyFile.exists() || dummyFile.length() != dummySize) {
            RandomAccessFile(dummyFile, "rw").use { raf ->
                raf.setLength(dummySize)
                raf.seek(510)
                raf.write(byteArrayOf(0x55.toByte(), 0xAA.toByte()))
            }
        }
        val uri = Uri.fromFile(dummyFile)
        selectedImageUri.value = uri
        selectedImageName.value = "ubuntu-24.04-desktop-amd64-demo.iso (100 MB)"
        return Pair(uri, dummySize)
    }

    fun ensureDemoVentoyRelease(): VentoyRelease {
        val app = getApplication<Application>()
        val ventoyDir = File(app.filesDir, "ventoy_cache")
        if (!ventoyDir.exists()) ventoyDir.mkdirs()
        val dummyTar = File(ventoyDir, "ventoy-1.0.99-linux.tar.gz")
        if (!dummyTar.exists()) {
            dummyTar.createNewFile()
        }
        val rel = VentoyRelease(
            version = "1.0.99",
            downloadUrl = "ventoy-1.0.99-linux.tar.gz",
            publishedAt = "2024-11-01 (Demo)",
            assetSize = dummyTar.length(),
            changelog = "Developer Demo Mode Ventoy 1.0.99 installation package."
        )
        _ventoyRelease.value = rel
        offlineVentoyFileExists.value = true
        return rel
    }

    fun startBurn() {
        val connected = usbState.value as? UsbConnectionState.Connected ?: return
        if (_isBurning.value) return
        val startTime = System.currentTimeMillis()
        burnJob = viewModelScope.launch(Dispatchers.IO) {
            _isBurning.value = true
            _burnProgress.value = null
            _doneMetrics.value = null
            _errorMessage.value = null
            try {
                val app = getApplication<Application>()
                val isVirtual = connected.isVirtual
                val uri: Uri
                val totalBytes: Long

                if (isVirtual && selectedImageUri.value == null) {
                    val (dummyUri, dummySize) = ensureDummyDemoImage()
                    uri = dummyUri
                    totalBytes = dummySize
                } else {
                    uri = selectedImageUri.value ?: return@launch
                    totalBytes = getUriSize(app, uri).takeIf { it > 0 } ?: connected.capacityBytes
                }

                val streamer: UsbMassStorageStreamer = if (isVirtual) {
                    VirtualUsbDriveStreamer(
                        context = app,
                        mode = connected.virtualMode ?: VirtualDriveMode.SUCCESS,
                        expectedTotalBytes = totalBytes
                    )
                } else {
                    val usbManager = app.getSystemService(Context.USB_SERVICE)
                            as android.hardware.usb.UsbManager
                    UsbMassStorageStreamer(usbManager, connected.device!!)
                }

                streamer.open()
                try {
                    val burner = RawImageBurner(app.contentResolver, streamer)
                    val devName = if (isVirtual) {
                        "${connected.vendor} ${connected.model}".trim()
                    } else {
                        val activeDev = connected.device
                        if (activeDev != null) {
                            listOfNotNull(activeDev.manufacturerName, activeDev.productName)
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                                .ifEmpty { activeDev.deviceName }
                        } else "USB Drive"
                    }
                    val devCap = formatBytes(connected.capacityBytes)
                    val imageName = selectedImageName.value ?: "Raw Disk Image"

                    burner.burn(uri, totalBytes).collect { progress ->
                        _burnProgress.value = progress
                        if (progress.phase == com.p0lymatic.flashpoint.core.BurnPhase.COMPLETED) {
                            val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                            val elapsedSec = elapsedMs / 1000.0
                            val speedMBs = if (elapsedSec > 0) (progress.totalBytes / elapsedSec) / (1024.0 * 1024.0) else 0.0
                            val formattedTime = "%02d:%02d".format(elapsedMs / 60000, (elapsedMs % 60000) / 1000)

                            _doneMetrics.value = DoneMetrics(
                                driveNameAndSize = "$devName ($devCap)".trim(),
                                targetOrImageName = imageName,
                                schemeAndArchitecture = "Raw / Native",
                                averageSpeed = "%.1f MB/s".format(speedMBs),
                                elapsedTime = formattedTime
                            )
                        } else if (progress.phase == com.p0lymatic.flashpoint.core.BurnPhase.FAILED) {
                            val errMsg = progress.errorMessage ?: "Image write failed"
                            _errorMessage.value = errMsg
                            _failedDetails.value = FailedDetails(
                                phase = "Writing Sectors",
                                rawError = errMsg,
                                driveName = devName,
                                imageName = imageName
                            )
                            cleanupVirtualDisk()
                        }
                    }
                } finally {
                    streamer.close()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                cleanupVirtualDisk()
                throw e
            } catch (e: Exception) {
                _burnProgress.value = _burnProgress.value?.copy(
                    phase = com.p0lymatic.flashpoint.core.BurnPhase.FAILED
                )
                val errMsg = e.message ?: "An error occurred during write"
                _errorMessage.value = errMsg
                _failedDetails.value = FailedDetails(
                    phase = "Writing Sectors",
                    rawError = e.stackTraceToString().ifBlank { errMsg },
                    driveName = (usbState.value as? UsbConnectionState.Connected)?.device?.productName ?: "USB Drive",
                    imageName = selectedImageName.value
                )
                cleanupVirtualDisk()
            } finally {
                _isBurning.value = false
            }
        }
    }

    fun cancelBurn() {
        burnJob?.cancel()
        burnJob = null
        showCancelDialog.value = false
        _isBurning.value = false
        cleanupVirtualDisk()
    }

    fun checkOfflineVentoyStatus(release: VentoyRelease? = _ventoyRelease.value) {
        val app = getApplication<Application>()
        val ventoyDir = File(app.filesDir, "ventoy_cache")
        if (release != null) {
            val fileName = release.downloadUrl.substringAfterLast('/')
            val targetFile = File(ventoyDir, fileName)
            offlineVentoyFileExists.value = targetFile.exists() && (release.assetSize <= 0L || targetFile.length() == release.assetSize)
        } else {
            val existing = ventoyDir.listFiles()?.firstOrNull {
                it.isFile && it.name.startsWith("ventoy-") && it.name.endsWith(".tar.gz") && it.length() > 0
            }
            if (existing != null) {
                offlineVentoyFileExists.value = true
                val version = existing.name.removePrefix("ventoy-").removeSuffix("-linux.tar.gz").removeSuffix(".tar.gz")
                _ventoyRelease.value = VentoyRelease(
                    version = version,
                    downloadUrl = existing.name,
                    publishedAt = "Offline Cache",
                    assetSize = existing.length()
                )
            } else {
                offlineVentoyFileExists.value = false
            }
        }
    }

    fun downloadOfflineVentoy() {
        val release = _ventoyRelease.value ?: return
        if (isDownloadingOfflineVentoy.value) return

        viewModelScope.launch(Dispatchers.IO) {
            isDownloadingOfflineVentoy.value = true
            try {
                val app = getApplication<Application>()
                val ventoyDir = File(app.filesDir, "ventoy_cache")
                if (!ventoyDir.exists()) {
                    ventoyDir.mkdirs()
                }
                val fileName = release.downloadUrl.substringAfterLast('/')
                val targetFile = File(ventoyDir, fileName)
                val tempFile = File(ventoyDir, "$fileName.tmp")

                val request = Request.Builder()
                    .url(release.downloadUrl)
                    .build()

                val client = OkHttpClient()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed with code ${response.code}")
                }
                val body = response.body ?: throw IllegalStateException("Empty body")

                tempFile.sink().buffer().use { sink ->
                    val source = body.source()
                    val bufferSize = 8192L
                    while (true) {
                        val read = source.read(sink.buffer, bufferSize)
                        if (read == -1L) break
                        sink.emitCompleteSegments()
                    }
                }

                if (tempFile.exists()) {
                    if (targetFile.exists()) targetFile.delete()
                    tempFile.renameTo(targetFile)
                }
                checkOfflineVentoyStatus(release)
            } catch (_: Exception) {
                // Silently handle download failure; status remains unchanged
            } finally {
                isDownloadingOfflineVentoy.value = false
            }
        }
    }

    fun fetchVentoyRelease() {
        if (isCheckingVentoy.value) return
        viewModelScope.launch(Dispatchers.IO) {
            isCheckingVentoy.value = true
            try {
                val app = getApplication<Application>()
                val ventoyDir = File(app.filesDir, "ventoy_cache")
                if (!ventoyDir.exists()) ventoyDir.mkdirs()
                val installer = VentoyInstaller(OkHttpClient(), ventoyDir)
                val release = installer.fetchLatestRelease()
                _ventoyRelease.value = release
                checkOfflineVentoyStatus(release)
            } catch (_: Exception) {
                // Silently handle fetch failures; user can retry
            } finally {
                isCheckingVentoy.value = false
            }
        }
    }

    fun installVentoy() {
        val connected = usbState.value as? UsbConnectionState.Connected ?: return
        val isVirtual = connected.isVirtual
        val release = _ventoyRelease.value ?: if (isVirtual) {
            ensureDemoVentoyRelease()
        } else {
            return
        }

        val devName = if (isVirtual) {
            "${connected.vendor} ${connected.model}".trim()
        } else {
            val activeDev = connected.device!!
            listOfNotNull(activeDev.manufacturerName, activeDev.productName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifEmpty { activeDev.deviceName }
        }
        val devCap = formatBytes(connected.capacityBytes)

        _doneMetrics.value = null
        _errorMessage.value = null
        ventoyJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                val streamer: UsbMassStorageStreamer = if (isVirtual) {
                    VirtualUsbDriveStreamer(
                        context = app,
                        mode = connected.virtualMode ?: VirtualDriveMode.SUCCESS,
                        expectedTotalBytes = 34L * 1024L * 1024L
                    )
                } else {
                    val usbManager = app.getSystemService(Context.USB_SERVICE)
                            as android.hardware.usb.UsbManager
                    UsbMassStorageStreamer(usbManager, connected.device!!)
                }

                streamer.open()
                try {
                    val ventoyDir = File(app.filesDir, "ventoy_cache")
                    if (!ventoyDir.exists()) ventoyDir.mkdirs()
                    if (isVirtual) {
                        val demoFile = File(ventoyDir, release.downloadUrl.substringAfterLast('/'))
                        if (!demoFile.exists()) demoFile.createNewFile()
                    }
                    val installer = VentoyInstaller(OkHttpClient(), ventoyDir)
                    installer.install(
                        release = release,
                        streamer = streamer,
                        scheme = selectedPartitionScheme.value,
                        arch = selectedArchitecture.value,
                        driveName = devName,
                        driveCapacity = devCap
                    ).collect { progress ->
                        _ventoyInstallProgress.value = progress
                        if (progress.phase == VentoyPhase.COMPLETED && progress.metrics != null) {
                            val m = progress.metrics
                            _doneMetrics.value = DoneMetrics(
                                driveNameAndSize = "${m.driveName} (${m.driveCapacity})".trim(),
                                targetOrImageName = "Ventoy ${m.ventoyVersion}",
                                schemeAndArchitecture = "${m.partitionScheme} (${m.targetArchitecture})",
                                averageSpeed = "%.1f MB/s".format(m.averageSpeedMBs),
                                elapsedTime = m.formattedDuration
                            )
                        } else if (progress.phase == VentoyPhase.FAILED) {
                            val errMsg = progress.message
                            _errorMessage.value = errMsg
                            _failedDetails.value = FailedDetails(
                                phase = "Ventoy Installation",
                                rawError = errMsg,
                                driveName = devName,
                                imageName = "Ventoy ${release.version}"
                            )
                            cleanupVirtualDisk()
                        }
                    }
                    checkOfflineVentoyStatus(release)
                } finally {
                    streamer.close()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                cleanupVirtualDisk()
                throw e
            } catch (e: Exception) {
                val errMsg = e.message ?: "Ventoy installation failed"
                _errorMessage.value = errMsg
                _failedDetails.value = FailedDetails(
                    phase = "Ventoy Installation",
                    rawError = e.stackTraceToString().ifBlank { errMsg },
                    driveName = devName,
                    imageName = "Ventoy ${release.version}"
                )
                cleanupVirtualDisk()
            }
        }
    }

    fun cancelVentoyInstall() {
        ventoyJob?.cancel()
        ventoyJob = null
        _ventoyInstallProgress.value = null
        cleanupVirtualDisk()
    }

    override fun onCleared() {
        super.onCleared()
        usbMonitor.unregister()
        burnJob?.cancel()
        ventoyJob?.cancel()
        cleanupVirtualDisk()
    }
}
fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000L -> "%.1f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}
private fun getUriSize(context: Context, uri: Uri): Long {
    try {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                return cursor.getLong(sizeIdx)
            }
        }
    } catch (_: Exception) {}
    try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            return pfd.statSize
        }
    } catch (_: Exception) {}
    return -1L
}
