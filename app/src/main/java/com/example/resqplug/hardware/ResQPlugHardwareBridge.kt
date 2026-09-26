package com.example.resqplug.hardware

import android.content.Context
import android.util.Log
import com.example.resqplug.bluetooth.BluetoothRadioHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Central singleton managing the active physical link (Bluetooth SPP / USB-OTG)
 * between the Android application and the ESP32 LoRa transceiver.
 */
object ResQPlugHardwareBridge {

    private const val TAG = "ResQPlugBridge"

    enum class Transport {
        NONE,
        USB_OTG,
        BLUETOOTH,
        SIMULATION
    }

    var activeTransport: Transport = Transport.NONE
        private set

    var hardwareName: String = ""
        private set
    var dongleId: String = ""
        private set
    var deviceSerial: String = ""
        private set
    var nodeId: String = ""
        private set

    private var bluetoothHelper: BluetoothRadioHelper? = null
    private var readerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Listeners for incoming raw packets from ESP32 (e.g. [RX:RSSI:...|DATA:...], [BEACON_TX:...])
    private val packetListeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val connectionStateListeners = CopyOnWriteArrayList<(Boolean, Transport) -> Unit>()

    fun getBluetoothHelper(context: Context): BluetoothRadioHelper {
        if (bluetoothHelper == null) {
            bluetoothHelper = BluetoothRadioHelper(context.applicationContext)
        }
        return bluetoothHelper!!
    }

    /**
     * Handover already established Bluetooth connection from MainActivity
     */
    fun attachBluetoothSession(
        helper: BluetoothRadioHelper,
        name: String,
        id: String,
        serial: String,
        phoneNodeId: String
    ) {
        bluetoothHelper = helper
        hardwareName = name
        dongleId = id
        deviceSerial = serial
        nodeId = phoneNodeId
        activeTransport = Transport.BLUETOOTH

        startReaderLoop()
        notifyConnectionState(true)
        Log.d(TAG, "Attached Bluetooth session: $name ($id)")
    }

    fun attachUsbSession(
        name: String,
        id: String,
        serial: String,
        phoneNodeId: String
    ) {
        hardwareName = name
        dongleId = id
        deviceSerial = serial
        nodeId = phoneNodeId
        activeTransport = Transport.USB_OTG

        notifyConnectionState(true)
        Log.d(TAG, "Attached USB session: $name ($id)")
    }

    fun setSimulationMode(phoneNodeId: String) {
        disconnect()
        nodeId = phoneNodeId
        hardwareName = "Virtual LoRa Pod"
        dongleId = "POD-SIM"
        activeTransport = Transport.SIMULATION
        notifyConnectionState(true)
    }

    fun addPacketListener(listener: (String) -> Unit) {
        if (!packetListeners.contains(listener)) {
            packetListeners.add(listener)
        }
    }

    fun removePacketListener(listener: (String) -> Unit) {
        packetListeners.remove(listener)
    }

    fun addConnectionStateListener(listener: (Boolean, Transport) -> Unit) {
        if (!connectionStateListeners.contains(listener)) {
            connectionStateListeners.add(listener)
        }
    }

    fun removeConnectionStateListener(listener: (Boolean, Transport) -> Unit) {
        connectionStateListeners.remove(listener)
    }

    private fun notifyConnectionState(isConnected: Boolean) {
        for (listener in connectionStateListeners) {
            try {
                listener(isConnected, activeTransport)
            } catch (e: Exception) {
                Log.e(TAG, "Error in connection listener", e)
            }
        }
    }

    fun sendCommand(cmd: String): Boolean {
        Log.d(TAG, "sendCommand: $cmd via $activeTransport")
        return when (activeTransport) {
            Transport.BLUETOOTH -> {
                val helper = bluetoothHelper
                if (helper != null && helper.isConnected()) {
                    helper.sendData(cmd)
                } else {
                    Log.w(TAG, "Bluetooth not connected, cannot send: $cmd")
                    false
                }
            }
            Transport.USB_OTG -> {
                true
            }
            Transport.SIMULATION -> {
                true
            }
            Transport.NONE -> false
        }
    }

    /**
     * Formats and transmits a packet over the LoRa physical radio
     */
    fun sendLoraPacket(packet: String): Boolean {
        val cmd = if (packet.startsWith("TX:")) packet else "TX:$packet"
        return sendCommand(cmd)
    }

    fun isConnected(): Boolean {
        return when (activeTransport) {
            Transport.BLUETOOTH -> bluetoothHelper?.isConnected() == true
            Transport.USB_OTG -> true
            Transport.SIMULATION -> true
            Transport.NONE -> false
        }
    }

    private fun startReaderLoop() {
        readerJob?.cancel()
        val helper = bluetoothHelper ?: return
        val inputStream = helper.getInputStream() ?: return

        readerJob = scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                Log.d(TAG, "Started background packet reader loop")
                while (isActive && helper.isConnected()) {
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        dispatchPacket(trimmed)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Reader loop terminated: ${e.message}")
            } finally {
                if (activeTransport == Transport.BLUETOOTH && helper.isConnected() == false) {
                    notifyConnectionState(false)
                }
            }
        }
    }

    private fun dispatchPacket(packet: String) {
        Log.d(TAG, "RX Packet: $packet")
        for (listener in packetListeners) {
            try {
                listener(packet)
            } catch (e: Exception) {
                Log.e(TAG, "Error in packet listener", e)
            }
        }
    }

    fun disconnect() {
        readerJob?.cancel()
        readerJob = null
        if (activeTransport == Transport.BLUETOOTH) {
            bluetoothHelper?.disconnect()
        }
        activeTransport = Transport.NONE
        notifyConnectionState(false)
    }
}
