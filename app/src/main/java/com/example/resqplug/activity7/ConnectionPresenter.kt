package com.example.resqplug.activity7

import android.annotation.SuppressLint
import android.content.Context
import com.example.resqplug.bluetooth.BluetoothRadioHelper
import com.example.resqplug.usb.UsbDeviceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Presenter for Screen 1.5: Hardware Gateway & Transport Discovery Bridge.
 * Mediates hardware queries between USB / Bluetooth helpers and the View.
 */
class ConnectionPresenter(
    private val context: Context,
    private val userEmail: String,
    private val usbHelper: UsbDeviceHelper = UsbDeviceHelper(context),
    private val bluetoothHelper: BluetoothRadioHelper = HardwareSessionManager.getBluetoothHelper(context)
) : ConnectionContract.Presenter {

    private var view: ConnectionContract.View? = null
    private val presenterScope = CoroutineScope(Dispatchers.Main + Job())

    override fun attachView(view: ConnectionContract.View) {
        this.view = view
        view.showOperatorEmail(userEmail)
        checkUsbConnection()
        scanBluetoothPods()
    }

    override fun detachView() {
        this.view = null
    }

    override fun checkUsbConnection() {
        val device = usbHelper.findResQPlugDevice()
        if (device != null) {
            val name = usbHelper.getHardwareName(device)
            view?.setUsbStatus(true, name)
            view?.showStatus("ESP32 USB Device Linked: $name")
        } else {
            view?.setUsbStatus(false, null)
        }
    }

    @SuppressLint("MissingPermission")
    override fun scanBluetoothPods() {
        if (!bluetoothHelper.isBluetoothSupported()) {
            view?.showStatus("Bluetooth not supported on this device")
            view?.displayDiscoveredDevices(emptyList())
            return
        }

        if (!bluetoothHelper.isBluetoothEnabled()) {
            view?.showStatus("Bluetooth is OFF. Turn ON in Android Settings.")
            view?.displayDiscoveredDevices(emptyList())
            return
        }

        view?.showLoading(true)
        val bonded = bluetoothHelper.getBondedDevices()
        val pods = bonded.map { device ->
            val name = device.name ?: "Unknown Pod"
            DiscoveredPod(name = name, address = device.address, isPaired = true)
        }

        view?.displayDiscoveredDevices(pods)
        view?.showLoading(false)
        if (pods.isNotEmpty()) {
            view?.showStatus("Found ${pods.size} paired Bluetooth device(s)")
        } else {
            view?.showStatus("No paired pods found. Pair 'ResQPlug-Dongle' in Android Settings.")
        }
    }

    override fun selectBluetoothDevice(device: DiscoveredPod) {
        view?.showLoading(true)
        view?.showStatus("CONNECTING TO ${device.name}...")

        presenterScope.launch {
            val connected = HardwareSessionManager.connectBluetooth(context, device.address, device.name)
            view?.showLoading(false)
            if (connected) {
                // Send handshake status query
                HardwareSessionManager.sendCommand("STATUS")
                view?.showStatus("LINKED TO ${device.name}!")
                view?.navigateToLedDashboard("BLUETOOTH", device.name)
            } else {
                view?.showError("COULD NOT CONNECT TO ${device.name}. PLEASE ENSURE IT IS POWERED ON.")
            }
        }
    }

    override fun selectUsbDevice() {
        val device = usbHelper.findResQPlugDevice()
        if (device != null) {
            val name = usbHelper.getHardwareName(device)
            HardwareSessionManager.setUsbMode(name)
            view?.navigateToLedDashboard("USB_OTG", name)
        } else {
            view?.showError("NO USB DEVICE DETECTED. PLEASE INSERT OTG CABLE.")
        }
    }

    override fun selectSimulationMode() {
        HardwareSessionManager.setSimulationMode()
        view?.navigateToLedDashboard("SIMULATION", "VIRTUAL_ESP32")
    }
}
