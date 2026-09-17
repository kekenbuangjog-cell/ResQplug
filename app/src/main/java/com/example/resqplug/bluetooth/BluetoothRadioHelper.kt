package com.example.resqplug.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothRadioHelper(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothRadioHelper"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val TARGET_DEVICE_NAME = "ResQPlug-Radio-01"
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    fun isBluetoothSupported(): Boolean = (bluetoothAdapter != null)

    fun isBluetoothEnabled(): Boolean = (bluetoothAdapter?.isEnabled == true)

    @SuppressLint("MissingPermission")
    fun findPairedResQPlugDevice(): BluetoothDevice? {
        val adapter = bluetoothAdapter ?: return null
        if (!adapter.isEnabled) return null

        try {
            val paired = adapter.bondedDevices ?: return null
            for (device in paired) {
                val name = device.name ?: ""
                if (name.contains("ResQPlug", ignoreCase = true) || name.contains("Radio", ignoreCase = true)) {
                    Log.d(TAG, "Found paired ResQPlug Bluetooth device: $name (${device.address})")
                    return device
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read bonded devices", e)
        }
        return null
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice): Boolean {
        disconnect()
        try {
            Log.d(TAG, "Attempting Bluetooth SPP connection to ${device.name}...")
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothAdapter?.cancelDiscovery()
            socket?.connect()
            inputStream = socket?.inputStream
            outputStream = socket?.outputStream
            Log.d(TAG, "Connected to Bluetooth SPP radio successfully!")
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Bluetooth connection failed", e)
            disconnect()
            return false
        }
    }

    fun sendData(data: String): Boolean {
        return try {
            outputStream?.write((data + "\n").toByteArray(Charsets.UTF_8))
            outputStream?.flush()
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write data to Bluetooth socket", e)
            false
        }
    }

    fun isConnected(): Boolean = (socket?.isConnected == true)

    fun disconnect() {
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (ignored: IOException) {}
        inputStream = null
        outputStream = null
        socket = null
    }
}
