package com.example.resqplug.activity7

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.example.resqplug.bluetooth.BluetoothRadioHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Singleton managing the live physical link (Bluetooth SPP / USB-OTG) between
 * Activity 7 and the ESP32 microcontroller running resqplug_unified_node.ino.
 */
object HardwareSessionManager {

    private const val TAG = "HardwareSessionManager"

    private var bluetoothHelper: BluetoothRadioHelper? = null
    var activeTransport: String = "SIMULATION" // "BLUETOOTH", "USB_OTG", "SIMULATION"
    var connectedDeviceName: String = "VIRTUAL_ESP32"

    private var readerJob: Job? = null
    private var telemetryListener: ((String) -> Unit)? = null
    private var disconnectListener: (() -> Unit)? = null

    fun getBluetoothHelper(context: Context): BluetoothRadioHelper {
        if (bluetoothHelper == null) {
            bluetoothHelper = BluetoothRadioHelper(context.applicationContext)
        }
        return bluetoothHelper!!
    }

    suspend fun connectBluetooth(context: Context, address: String, deviceName: String): Boolean {
        val helper = getBluetoothHelper(context)
        val device: BluetoothDevice? = helper.getRemoteDevice(address)
        if (device == null) {
            Log.e(TAG, "Cannot resolve Bluetooth device for address: $address")
            return false
        }

        return withContext(Dispatchers.IO) {
            val success = helper.connectToDevice(device)
            if (success) {
                activeTransport = "BLUETOOTH"
                connectedDeviceName = deviceName
                startReaderLoop()
                Log.d(TAG, "Successfully linked to Bluetooth pod: $deviceName ($address)")
            }
            success
        }
    }

    fun setSimulationMode() {
        disconnect()
        activeTransport = "SIMULATION"
        connectedDeviceName = "VIRTUAL_ESP32"
    }

    fun setUsbMode(deviceName: String) {
        disconnect()
        activeTransport = "USB_OTG"
        connectedDeviceName = deviceName
    }

    fun sendCommand(cmd: String): Boolean {
        Log.d(TAG, "Sending hardware command: $cmd (Transport: $activeTransport)")
        return when (activeTransport) {
            "BLUETOOTH" -> {
                val sent = bluetoothHelper?.sendData(cmd) ?: false
                if (!sent) {
                    Log.w(TAG, "Failed to send Bluetooth command: $cmd")
                }
                sent
            }
            "USB_OTG" -> {
                // USB-OTG serial transmission placeholder (or bridged via USB helper)
                true
            }
            else -> {
                // Simulation mode: treat as success
                true
            }
        }
    }

    fun isHardwareConnected(): Boolean {
        return when (activeTransport) {
            "BLUETOOTH" -> bluetoothHelper?.isConnected() == true
            "USB_OTG" -> true
            else -> true // Simulation considered connected in sandbox
        }
    }

    fun isRealHardwareConnected(): Boolean {
        return when (activeTransport) {
            "BLUETOOTH" -> bluetoothHelper?.isConnected() == true
            "USB_OTG" -> true
            else -> false
        }
    }

    fun registerTelemetryListener(
        onTelemetry: (String) -> Unit,
        onDisconnect: () -> Unit
    ) {
        this.telemetryListener = onTelemetry
        this.disconnectListener = onDisconnect
    }

    fun unregisterTelemetryListener() {
        this.telemetryListener = null
        this.disconnectListener = null
    }

    private fun startReaderLoop() {
        readerJob?.cancel()
        val inputStream = bluetoothHelper?.getInputStream() ?: return

        readerJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                while (isActive) {
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        Log.d(TAG, "ESP32 -> Phone: $trimmed")
                        withContext(Dispatchers.Main) {
                            telemetryListener?.invoke(trimmed)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Bluetooth reader stream closed: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    disconnectListener?.invoke()
                }
            }
        }
    }

    fun disconnect() {
        readerJob?.cancel()
        readerJob = null
        bluetoothHelper?.disconnect()
        activeTransport = "SIMULATION"
        connectedDeviceName = "VIRTUAL_ESP32"
    }
}
