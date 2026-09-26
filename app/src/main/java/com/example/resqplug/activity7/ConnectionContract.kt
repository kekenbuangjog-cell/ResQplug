package com.example.resqplug.activity7

/**
 * Data model for a detected/paired Bluetooth Pod or hardware node.
 */
data class DiscoveredPod(
    val name: String,
    val address: String,
    val isPaired: Boolean = false
)

/**
 * MVP Contract for Screen 1.5: Hardware Gateway & Transport Discovery Bridge.
 */
interface ConnectionContract {

    interface View {
        fun showLoading(isScanning: Boolean)
        fun showOperatorEmail(email: String)
        fun showStatus(statusText: String)
        fun showError(errorText: String)
        fun setUsbStatus(isDetected: Boolean, deviceName: String?)
        fun displayDiscoveredDevices(devices: List<DiscoveredPod>)
        fun navigateToLedDashboard(transportMode: String, deviceIdentifier: String)
    }

    interface Presenter {
        fun attachView(view: View)
        fun detachView()
        fun checkUsbConnection()
        fun scanBluetoothPods()
        fun selectBluetoothDevice(device: DiscoveredPod)
        fun selectUsbDevice()
        fun selectSimulationMode()
    }
}
