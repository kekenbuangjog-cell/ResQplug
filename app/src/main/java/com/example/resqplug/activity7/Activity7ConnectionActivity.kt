package com.example.resqplug.activity7

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.resqplug.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Screen 1.5: Hardware Gateway & Transport Discovery Bridge.
 * Displays real-time USB-OTG detection and Bluetooth Pod scanning
 * with 8 FPS retro animations before entering Screen 2 (LedActivity).
 */
class Activity7ConnectionActivity : AppCompatActivity(), ConnectionContract.View {

    private val frameDurationMs = 125L // 8 FPS Retro Standard

    private lateinit var presenter: ConnectionContract.Presenter
    private lateinit var deviceAdapter: DeviceScanAdapter

    private lateinit var btnConnBack: Button
    private lateinit var connProgressBar: ProgressBar
    private lateinit var tvOperatorEmail: TextView
    private lateinit var tvUsbStatus: TextView
    private lateinit var btnConnectUsb: Button
    private lateinit var tvBtRadarSweep: TextView
    private lateinit var rvBluetoothDevices: RecyclerView
    private lateinit var btnScanBluetooth: Button
    private lateinit var btnTestOverride: Button
    private lateinit var tvTerminalStatus: TextView

    private var radarJob: Job? = null
    private var isScanningRadar = true
    private var userEmail: String = "operator@resqplug.local"

    // Launcher for Android 12+ Bluetooth permissions
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            presenter.scanBluetoothPods()
        } else {
            showError("BLUETOOTH PERMISSIONS DENIED")
        }
    }

    // USB connection receiver for live OTG plug/unplug events
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    presenter.checkUsbConnection()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_a7_connection)

        userEmail = intent.getStringExtra("EXTRA_VERIFIED_EMAIL") ?: "operator@resqplug.local"

        initViews()
        initPresenter()
        setupListeners()
        start8FpsRadarSweep()

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        registerReceiver(usbReceiver, filter)
    }

    override fun onResume() {
        super.onResume()
        presenter.checkUsbConnection()
    }

    override fun onDestroy() {
        super.onDestroy()
        radarJob?.cancel()
        try {
            unregisterReceiver(usbReceiver)
        } catch (ignored: Exception) {}
        presenter.detachView()
    }

    private fun initViews() {
        btnConnBack = findViewById(R.id.btnConnBack)
        connProgressBar = findViewById(R.id.connProgressBar)
        tvOperatorEmail = findViewById(R.id.tvOperatorEmail)
        tvUsbStatus = findViewById(R.id.tvUsbStatus)
        btnConnectUsb = findViewById(R.id.btnConnectUsb)
        tvBtRadarSweep = findViewById(R.id.tvBtRadarSweep)
        rvBluetoothDevices = findViewById(R.id.rvBluetoothDevices)
        btnScanBluetooth = findViewById(R.id.btnScanBluetooth)
        btnTestOverride = findViewById(R.id.btnTestOverride)
        tvTerminalStatus = findViewById(R.id.tvTerminalStatus)

        rvBluetoothDevices.layoutManager = LinearLayoutManager(this)
        deviceAdapter = DeviceScanAdapter(emptyList()) { selectedPod ->
            presenter.selectBluetoothDevice(selectedPod)
        }
        rvBluetoothDevices.adapter = deviceAdapter
    }

    private fun initPresenter() {
        presenter = ConnectionPresenter(this, userEmail)
        presenter.attachView(this)
    }

    private fun setupListeners() {
        btnConnBack.setOnClickListener {
            finish()
        }

        btnConnectUsb.setOnClickListener {
            presenter.selectUsbDevice()
        }

        btnScanBluetooth.setOnClickListener {
            checkPermissionsAndScanBluetooth()
        }

        btnTestOverride.setOnClickListener {
            presenter.selectSimulationMode()
        }
    }

    private fun checkPermissionsAndScanBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val connectGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            val scanGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

            if (!connectGranted || !scanGranted) {
                bluetoothPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    )
                )
                return
            }
        } else {
            val locGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!locGranted) {
                bluetoothPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                )
                return
            }
        }

        presenter.scanBluetoothPods()
    }

    // =========================================================================
    // 8 FPS Radar Sweep Animation
    // =========================================================================

    private fun start8FpsRadarSweep() {
        radarJob?.cancel()
        radarJob = lifecycleScope.launch {
            val radarGlyphs = arrayOf("◜", "◝", "◞", "◟")
            var step = 0
            while (isActive) {
                val glyph = radarGlyphs[step % radarGlyphs.size]
                if (isScanningRadar) {
                    tvBtRadarSweep.text = "RADAR SWEEP [ $glyph ] SCANNING 2.4GHz SPP..."
                    tvBtRadarSweep.setTextColor(ContextCompat.getColor(this@Activity7ConnectionActivity, R.color.signal_cyan))
                } else {
                    tvBtRadarSweep.text = "RADAR SWEEP [ ▣ ] SCAN READY"
                }
                step++
                delay(frameDurationMs) // 125ms per tick @ 8 FPS
            }
        }
    }

    // =========================================================================
    // ConnectionContract.View Implementations
    // =========================================================================

    override fun showLoading(isScanning: Boolean) {
        connProgressBar.visibility = if (isScanning) View.VISIBLE else View.GONE
        btnScanBluetooth.isEnabled = !isScanning
        isScanningRadar = isScanning
    }

    override fun showOperatorEmail(email: String) {
        tvOperatorEmail.text = "[ OPERATOR: $email // VERIFIED ]"
    }

    override fun showStatus(statusText: String) {
        tvTerminalStatus.text = "[ $statusText ]"
    }

    override fun showError(errorText: String) {
        tvTerminalStatus.text = "[ ⚠️ $errorText ]"
        Toast.makeText(this, errorText, Toast.LENGTH_SHORT).show()
    }

    override fun setUsbStatus(isDetected: Boolean, deviceName: String?) {
        if (isDetected) {
            tvUsbStatus.text = "● STATUS: $deviceName [LINK READY]"
            tvUsbStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            btnConnectUsb.isEnabled = true
            btnConnectUsb.text = "[ 🔌 CONNECT VIA USB-OTG ]"
        } else {
            tvUsbStatus.text = "○ STATUS: NO USB-OTG HARDWARE ATTACHED"
            tvUsbStatus.setTextColor(ContextCompat.getColor(this, R.color.text_gray))
            btnConnectUsb.isEnabled = false
            btnConnectUsb.text = "[ 🔌 AWAITING USB CABLE... ]"
        }
    }

    override fun displayDiscoveredDevices(devices: List<DiscoveredPod>) {
        deviceAdapter.updateDevices(devices)
    }

    override fun navigateToLedDashboard(transportMode: String, deviceIdentifier: String) {
        val intent = Intent(this, LedActivity::class.java).apply {
            putExtra("EXTRA_VERIFIED_EMAIL", userEmail)
            putExtra("EXTRA_TRANSPORT_MODE", transportMode)
            putExtra("EXTRA_DEVICE_IDENTIFIER", deviceIdentifier)
        }
        startActivity(intent)
    }
}
