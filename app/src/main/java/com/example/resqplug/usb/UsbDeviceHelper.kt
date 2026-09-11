package com.example.resqplug.usb

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

/**
 * Helper to enumerate, identify, and extract telemetry from ResQPlug ESP32 USB devices.
 */
class UsbDeviceHelper(context: Context) {

    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    companion object {
        // Espressif native USB
        const val VENDOR_ID_ESPRESSIF = 0x303A
        const val PID_ESP32_CDC_DEFAULT = 0x0002
        const val PID_ESP32_JTAG_SERIAL = 0x1001
        const val PID_ESP32S2_CDC = 0x0002
        const val PID_ESP32S3_CDC = 0x1001
        const val PID_ESP32C3_CDC = 0x1002

        // Silicon Labs CP210x USB-to-UART bridges
        const val VENDOR_ID_SILABS = 0x10C4
        const val PID_CP2102 = 0xEA60
        const val PID_CP2104 = 0xEA60
        const val PID_CP2105 = 0xEA70
        const val PID_CP2108 = 0xEA71

        // WCH CH340 / CH341 / CH9102 bridges (very common on ESP32 boards)
        const val VENDOR_ID_WCH = 0x1A86
        const val PID_CH340 = 0x7523
        const val PID_CH341 = 0x5523
        const val PID_CH340K = 0x7522
        const val PID_CH9102 = 0x55D4
        const val PID_CH9102F = 0x55D3

        // FTDI chips
        const val VENDOR_ID_FTDI = 0x0403
        const val PID_FT232R = 0x6001
        const val PID_FT2232 = 0x6010
        const val PID_FT4232 = 0x6011
        const val PID_FT230X = 0x6015

        // Prolific & QinHeng bridges
        const val VENDOR_ID_PROLIFIC = 0x067B
        const val PID_PL2303 = 0x2303
    }

    private val knownDevices = setOf(
        VENDOR_ID_ESPRESSIF to PID_ESP32_CDC_DEFAULT,
        VENDOR_ID_ESPRESSIF to PID_ESP32_JTAG_SERIAL,
        VENDOR_ID_ESPRESSIF to PID_ESP32C3_CDC,
        VENDOR_ID_SILABS to PID_CP2102,
        VENDOR_ID_SILABS to PID_CP2105,
        VENDOR_ID_SILABS to PID_CP2108,
        VENDOR_ID_WCH to PID_CH340,
        VENDOR_ID_WCH to PID_CH341,
        VENDOR_ID_WCH to PID_CH340K,
        VENDOR_ID_WCH to PID_CH9102,
        VENDOR_ID_WCH to PID_CH9102F,
        VENDOR_ID_FTDI to PID_FT232R,
        VENDOR_ID_FTDI to PID_FT2232,
        VENDOR_ID_FTDI to PID_FT230X,
        VENDOR_ID_PROLIFIC to PID_PL2303
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

    /**
     * Identifies if the USB device is an ESP32 or compatible USB serial bridge.
     */
    fun isResQPlugDevice(device: UsbDevice): Boolean {
        // Direct match against known ESP32 / USB-to-UART bridge pairs
        if ((device.vendorId to device.productId) in knownDevices) return true

        // Vendor match for known manufacturers
        if (device.vendorId == VENDOR_ID_ESPRESSIF ||
            device.vendorId == VENDOR_ID_SILABS ||
            device.vendorId == VENDOR_ID_WCH ||
            device.vendorId == VENDOR_ID_FTDI ||
            device.vendorId == VENDOR_ID_PROLIFIC) {
            return true
        }

        // Generic USB CDC-ACM communication class or vendor-specific serial class fallback
        if (device.deviceClass == UsbConstants.USB_CLASS_COMM ||
            device.deviceClass == UsbConstants.USB_CLASS_CDC_DATA ||
            device.deviceClass == UsbConstants.USB_CLASS_VENDOR_SPEC) {
            return true
        }

        // Check interfaces for CDC-ACM
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_COMM ||
                iface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA ||
                iface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC) {
                return true
            }
        }

        return false
    }

    /**
     * Generates a permanent 6-character Phone Node ID derived from ANDROID_ID.
     * Example: 'RQP-NODE-8E549C'
     */
    fun getPhoneNodeId(context: Context): String {
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "A1B2C3"
        val clean = androidId.replace(Regex("[^A-Za-z0-9]"), "").takeLast(6).uppercase()
        val padded = clean.padStart(6, '0')
        return "RQP-NODE-$padded"
    }

    /**
     * Generates a clean, consistent device identifier for Firebase and UI display.
     */
    fun getUniqueDeviceId(device: UsbDevice): String {
        val serial = device.serialNumber?.trim()
        if (!serial.isNullOrEmpty() && serial.length >= 4 && !serial.equals("null", ignoreCase = true)) {
            val cleanSerial = serial.replace(Regex("[^A-Za-z0-9]"), "").takeLast(6).uppercase()
            return "RQP-ESP32-$cleanSerial"
        }
        val vidHex = Integer.toHexString(device.vendorId).uppercase().padStart(4, '0')
        val pidHex = Integer.toHexString(device.productId).uppercase().padStart(4, '0')
        return "RQP-ESP32-${vidHex.takeLast(2)}${pidHex.takeLast(2)}"
    }

    /**
     * Returns a descriptive name of the physical hardware chip.
     */
    fun getHardwareName(device: UsbDevice): String {
        return when (device.vendorId) {
            VENDOR_ID_ESPRESSIF -> "ESP32 (Native USB CDC)"
            VENDOR_ID_SILABS -> "ESP32 DevKit (CP2102 Bridge)"
            VENDOR_ID_WCH -> {
                if (device.productId == PID_CH9102 || device.productId == PID_CH9102F) {
                    "ESP32 DevKit (CH9102 Bridge)"
                } else {
                    "ESP32 DevKit (CH340 Bridge)"
                }
            }
            VENDOR_ID_FTDI -> "ESP32 DevKit (FTDI Bridge)"
            VENDOR_ID_PROLIFIC -> "ESP32 Node (PL2303 Bridge)"
            else -> {
                val devName = device.productName ?: device.deviceName ?: "USB Serial"
                "ESP32 Node ($devName)"
            }
        }
    }

    fun getVendorIdHex(device: UsbDevice): String {
        return "0x" + Integer.toHexString(device.vendorId).uppercase().padStart(4, '0')
    }

    fun getProductIdHex(device: UsbDevice): String {
        return "0x" + Integer.toHexString(device.productId).uppercase().padStart(4, '0')
    }

    fun getSerialNumber(device: UsbDevice): String {
        val serial = device.serialNumber?.trim()
        return if (!serial.isNullOrEmpty() && !serial.equals("null", ignoreCase = true)) {
            serial
        } else {
            "N/A (${getVendorIdHex(device)}:${getProductIdHex(device)})"
        }
    }

    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }

    fun openDevice(device: UsbDevice) = usbManager.openDevice(device)
}
