package com.p0lymatic.flashpoint.usb

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UsbDeviceMonitor(private val context: Context) {

    private val _state = MutableStateFlow<UsbConnectionState>(UsbConnectionState.NoDevice)
    val state: StateFlow<UsbConnectionState> = _state.asStateFlow()

    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("NewApi")
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    @Suppress("DEPRECATION")
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (device != null && findMassStorageInterface(device) != null) {
                        requestPermission(device)
                    }
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    _state.value = UsbConnectionState.NoDevice
                }

                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    @Suppress("DEPRECATION")
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (granted && device != null) {
                        probeDevice(device)
                    } else {
                        _state.value = UsbConnectionState.Error("USB permission denied")
                    }
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun register() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    fun unregister() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // Receiver was not registered
        }
    }

    fun requestPermission(device: UsbDevice) {
        _state.value = UsbConnectionState.PermissionRequested(device)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERMISSION), flags
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    @SuppressLint("NewApi")
    fun scanForDevices() {
        val deviceList = usbManager.deviceList
        for ((_, device) in deviceList) {
            if (findMassStorageInterface(device) != null) {
                requestPermission(device)
                return
            }
        }
    }

    private fun findMassStorageInterface(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbMassStorageStreamer.MASS_STORAGE_CLASS &&
                iface.interfaceSubclass == UsbMassStorageStreamer.SCSI_SUBCLASS &&
                iface.interfaceProtocol == UsbMassStorageStreamer.BOT_PROTOCOL
            ) {
                return iface
            }
        }
        return null
    }

    private fun probeDevice(device: UsbDevice) {
        val streamer = UsbMassStorageStreamer(usbManager, device)
        try {
            streamer.open()
            streamer.testUnitReady()
            val info = streamer.inquiry()
            val (lastLba, blockSize) = streamer.getCapacity()
            val capacityBytes = (lastLba + 1) * blockSize
            _state.value = UsbConnectionState.Connected(
                device = device,
                capacityBytes = capacityBytes,
                vendor = info.vendor,
                model = info.product
            )
        } catch (e: Exception) {
            _state.value = UsbConnectionState.Error("Failed to probe USB device: ${e.message}", e)
        } finally {
            streamer.close()
        }
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.p0lymatic.flashpoint.USB_PERMISSION"
    }
}
