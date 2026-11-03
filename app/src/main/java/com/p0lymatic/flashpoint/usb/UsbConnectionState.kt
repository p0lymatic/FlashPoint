package com.p0lymatic.flashpoint.usb

import android.hardware.usb.UsbDevice

sealed interface UsbConnectionState {
    data object NoDevice : UsbConnectionState
    data class PermissionRequested(val device: UsbDevice) : UsbConnectionState
    data class Connected(
        val device: UsbDevice,
        val capacityBytes: Long,
        val vendor: String,
        val model: String
    ) : UsbConnectionState
    data class Error(val message: String, val cause: Throwable? = null) : UsbConnectionState
}
