package com.p0lymatic.flashpoint.usb

import android.hardware.usb.UsbDevice

sealed interface UsbConnectionState {
    data object NoDevice : UsbConnectionState
    data class PermissionRequested(val device: UsbDevice) : UsbConnectionState
    data class Connected(
        val device: UsbDevice? = null,
        val capacityBytes: Long,
        val vendor: String,
        val model: String,
        val isVirtual: Boolean = false,
        val virtualMode: VirtualDriveMode? = null
    ) : UsbConnectionState
    data class Error(val message: String, val cause: Throwable? = null) : UsbConnectionState
}
