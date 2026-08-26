package com.example.resqplug.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

/**
 * Helper to enumerate and identify ResQPlug USB devices.
 */
class UsbDeviceHelper(context: Context) {

    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    companion object {
        // Espressif native USB
        const val VENDOR_ID_ESPRESSIF = 0x303A
        const val PID_ESP32_CDC_DEFAULT = 0x0002
        const val PID_ESP32_JTAG_SERIAL = 0x1001

        // Common bridge chips found on ESP32 boards
        const val VENDOR_ID_SILABS = 0x10C4
        const val PID_CP2102 = 0xEA60

        const val VENDOR_ID_WCH = 0x1A86
        const val PID_CH340 = 0x7523
        const val PID_CH9102 = 0x55D4

        const val VENDOR_ID_FTDI = 0x0403
        const val PID_FT232R = 0x6001
    }

    private val knownDevices = setOf(
        VENDOR_ID_ESPRESSIF to PID_ESP32_CDC_DEFAULT,
        VENDOR_ID_ESPRESSIF to PID_ESP32_JTAG_SERIAL,
        VENDOR_ID_SILABS to PID_CP2102,
        VENDOR_ID_WCH to PID_CH340,
        VENDOR_ID_WCH to PID_CH9102,
        VENDOR_ID_FTDI to PID_FT232R,
    )

    fun isUsbHostSupported(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_USB_HOST
        )
    }

    fun getConnectedDevices(): List<UsbDevice> {
        return usbManager.deviceList.values.toList()
    }

    fun findResQPlugDevice(): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull { device ->
            isResQPlugDevice(device)
        }
    }

    fun isResQPlugDevice(device: UsbDevice): Boolean {
        return (device.vendorId to device.productId) in knownDevices
    }

    fun getUniqueDeviceId(device: UsbDevice): String {
        val serial = device.serialNumber
        if (!serial.isNullOrEmpty()) {
            return serial
        }
        val deviceName = device.deviceName ?: "unknown"
        return "VID:${device.vendorId}_PID:${device.productId}_$deviceName"
    }

    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }

    fun openDevice(device: UsbDevice) = usbManager.openDevice(device)
}
