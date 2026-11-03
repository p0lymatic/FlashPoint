package com.p0lymatic.flashpoint.ui

import android.app.Application
import android.net.Uri
import android.hardware.usb.UsbDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.p0lymatic.flashpoint.core.BurnProgress
import com.p0lymatic.flashpoint.core.PartitionScheme
import com.p0lymatic.flashpoint.core.RawImageBurner
import com.p0lymatic.flashpoint.core.VentoyInstallProgress
import com.p0lymatic.flashpoint.core.VentoyInstaller
import com.p0lymatic.flashpoint.core.VentoyRelease
import com.p0lymatic.flashpoint.usb.UsbConnectionState
import com.p0lymatic.flashpoint.usb.UsbDeviceMonitor
import com.p0lymatic.flashpoint.usb.UsbMassStorageStreamer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class FlashPointViewModel(application: Application) : AndroidViewModel(application) {

    private val usbMonitor = UsbDeviceMonitor(application)

    val usbState: StateFlow<UsbConnectionState> = usbMonitor.state
    val availableDevices = usbMonitor.devices
    val deviceCapacities = usbMonitor.deviceCapacities

    fun refreshUsbDevices() {
        usbMonitor.scanForDevices()
    }

    fun selectDevice(device: UsbDevice) {
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

    val selectedPartitionScheme = MutableStateFlow(PartitionScheme.GPT)
    val showCancelDialog = MutableStateFlow(false)

    private var burnJob: Job? = null
    private var ventoyJob: Job? = null

    init {
        usbMonitor.register()
        usbMonitor.scanForDevices()
    }

    fun onImageSelected(uri: Uri, displayName: String?) {
        selectedImageUri.value = uri
        selectedImageName.value = displayName
    }

    fun startBurn() {
        val uri = selectedImageUri.value ?: return
        val connected = usbState.value as? UsbConnectionState.Connected ?: return
        if (_isBurning.value) return

        burnJob = viewModelScope.launch(Dispatchers.IO) {
            _isBurning.value = true
            _burnProgress.value = null
            try {
                val app = getApplication<Application>()
                val usbManager = app.getSystemService(android.content.Context.USB_SERVICE)
                        as android.hardware.usb.UsbManager
                val streamer = UsbMassStorageStreamer(usbManager, connected.device)
                streamer.open()
                try {
                    val burner = RawImageBurner(app.contentResolver, streamer)
                    burner.burn(uri, connected.capacityBytes).collect { progress ->
                        _burnProgress.value = progress
                    }
                } finally {
                    streamer.close()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _burnProgress.value = _burnProgress.value?.copy(
                    phase = com.p0lymatic.flashpoint.core.BurnPhase.FAILED
                )
            } finally {
                _isBurning.value = false
            }
        }
    }

    fun cancelBurn() {
        burnJob?.cancel()
        burnJob = null
        showCancelDialog.value = false
    }

    fun fetchVentoyRelease() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                val installer = VentoyInstaller(OkHttpClient(), app.cacheDir)
                val release = installer.fetchLatestRelease()
                _ventoyRelease.value = release
            } catch (_: Exception) {
                // Silently handle fetch failures; user can retry
            }
        }
    }

    fun installVentoy() {
        val release = _ventoyRelease.value ?: return
        val connected = usbState.value as? UsbConnectionState.Connected ?: return

        ventoyJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                val usbManager = app.getSystemService(android.content.Context.USB_SERVICE)
                        as android.hardware.usb.UsbManager
                val streamer = UsbMassStorageStreamer(usbManager, connected.device)
                streamer.open()
                try {
                    val installer = VentoyInstaller(OkHttpClient(), app.cacheDir)
                    installer.install(release, streamer, selectedPartitionScheme.value)
                        .collect { progress ->
                            _ventoyInstallProgress.value = progress
                        }
                } finally {
                    streamer.close()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Error already emitted via flow FAILED phase
            }
        }
    }

    fun cancelVentoyInstall() {
        ventoyJob?.cancel()
        ventoyJob = null
    }

    override fun onCleared() {
        super.onCleared()
        usbMonitor.unregister()
        burnJob?.cancel()
        ventoyJob?.cancel()
    }
}
