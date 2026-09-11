package com.example.resqplug

import android.content.Intent
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.resqplug.ui.StarfieldView
import com.example.resqplug.usb.UsbConnectionReceiver
import com.example.resqplug.usb.UsbDeviceHelper
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val frameDurationMs = 125L // 8 FPS = 125ms per frame step
    private val loadingDelayMs = 2000L // 2s simulated connecting sequence

    private lateinit var usbHelper: UsbDeviceHelper
    private var usbReceiver: UsbConnectionReceiver? = null
    private var isDeviceConnected = false
    private var isConnecting = false
    private var connectedDeviceId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        usbHelper = UsbDeviceHelper(this)

        val tvEmoticon = findViewById<TextView>(R.id.tvEmoticon)
        val tvSearchingStatus = findViewById<TextView>(R.id.tvSearchingStatus)
        val tvDeviceId = findViewById<TextView>(R.id.tvDeviceId)
        val btnTestOverride = findViewById<Button>(R.id.btnTestOverride)
        val starfieldView = findViewById<StarfieldView>(R.id.starfieldView)

        // 8 FPS Stepped Dot Searching Animation
        startSteppedSearchingAnimation(tvSearchingStatus)

        // 8 FPS Emoticon Blink
        startEmoticonBlink(tvEmoticon)

        // 8 FPS Starfield Twinkle
        startStarfieldTwinkle(starfieldView)

        // Handle USB intent from manifest auto-launch (e.g. app launched by plugging in device)
        handleUsbIntent(intent)

        // Handle Test Override Code Button
        btnTestOverride.setOnClickListener {
            if (!isDeviceConnected && !isConnecting) {
                showTestOverrideDialog(tvSearchingStatus, tvDeviceId)
            }
        }
    }

    override fun onStart() {
        super.onStart()

        // Register USB attach/detach receiver
        usbReceiver = UsbConnectionReceiver(
            onDeviceAttached = { device -> onUsbDeviceAttached(device) },
            onDeviceDetached = { device -> onUsbDeviceDetached(device) }
        )
        registerReceiver(usbReceiver, usbReceiver!!.createIntentFilter())

        // Scan for already-connected devices (e.g. device was already plugged in before app launched)
        scanForExistingDevices()
    }

    override fun onStop() {
        super.onStop()
        usbReceiver?.let { unregisterReceiver(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUsbIntent(intent)
    }

    private fun handleUsbIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val device: UsbDevice? = IntentCompat.getParcelableExtra(
                intent,
                UsbManager.EXTRA_DEVICE,
                UsbDevice::class.java
            )
            device?.let { onUsbDeviceAttached(it) }
        }
    }

    private fun scanForExistingDevices() {
        val device = usbHelper.findResQPlugDevice()
        if (device != null && !isDeviceConnected) {
            onUsbDeviceAttached(device)
        }
    }

    private fun onUsbDeviceAttached(device: UsbDevice) {
        if (!usbHelper.isResQPlugDevice(device)) return
        if (isDeviceConnected || isConnecting) return

        val tvSearchingStatus = findViewById<TextView>(R.id.tvSearchingStatus)
        val tvDeviceId = findViewById<TextView>(R.id.tvDeviceId)

        runOnUiThread {
            startConnectionSequence(device, tvSearchingStatus, tvDeviceId)
        }
    }

    private fun onUsbDeviceDetached(device: UsbDevice) {
        if (!isDeviceConnected) return
        val detachedId = usbHelper.getUniqueDeviceId(device)
        if (connectedDeviceId.isNotEmpty() && detachedId != connectedDeviceId) return

        val tvSearchingStatus = findViewById<TextView>(R.id.tvSearchingStatus)
        val tvDeviceId = findViewById<TextView>(R.id.tvDeviceId)

        runOnUiThread {
            isDeviceConnected = false
            isConnecting = false
            tvSearchingStatus.text = getString(R.string.splash_searching)
            tvSearchingStatus.setTextColor(getColor(R.color.accent_green))
            tvDeviceId.visibility = View.GONE
            startSteppedSearchingAnimation(tvSearchingStatus)
        }
    }

    private var phoneNodeId: String = ""
    private var connectedHardwareName: String = "ESP32 Node"
    private var connectedSerial: String = ""
    private var connectedVendorId: String = ""
    private var connectedProductId: String = ""

    private fun startConnectionSequence(
        device: UsbDevice,
        tvStatus: TextView,
        tvDeviceId: TextView
    ) {
        isConnecting = true
        phoneNodeId = usbHelper.getPhoneNodeId(this)
        val hardwareName = usbHelper.getHardwareName(device)
        val serialNumber = usbHelper.getSerialNumber(device)
        val vendorIdHex = usbHelper.getVendorIdHex(device)
        val productIdHex = usbHelper.getProductIdHex(device)

        connectedDeviceId = phoneNodeId
        connectedHardwareName = hardwareName
        connectedSerial = serialNumber
        connectedVendorId = vendorIdHex
        connectedProductId = productIdHex

        // Register rich hardware & phone telemetry to Firebase Firestore
        registerDeviceToFirebase(
            nodeId = phoneNodeId,
            hardwareName = hardwareName,
            serialNumber = serialNumber,
            vendorId = vendorIdHex,
            productId = productIdHex
        )

        lifecycleScope.launch {
            // Phase 1: Show detected dongle chip with amber text
            tvStatus.text = "[ DETECTED: $hardwareName ]"
            tvStatus.setTextColor(getColor(R.color.alert_amber))

            // Animate 8-bit connection dots
            val connectDots = arrayOf(
                "[ LINKING TO $hardwareName     ]",
                "[ LINKING TO $hardwareName .   ]",
                "[ LINKING TO $hardwareName . . ]",
                "[ LINKING TO $hardwareName . . . ]"
            )
            var step = 0
            val totalTicks = (loadingDelayMs / frameDurationMs).toInt()
            for (tick in 0 until totalTicks) {
                delay(frameDurationMs * 2)
                tvStatus.text = connectDots[step % connectDots.size]
                step++
            }

            // Phase 2: Show "LINKED" with green text + phone's permanent Node ID
            isDeviceConnected = true
            isConnecting = false
            tvStatus.text = "[ ⚡ ESP32 HARDWARE LINKED ]"
            tvStatus.setTextColor(getColor(R.color.accent_green_alt))
            tvDeviceId.text = "NODE ID: $phoneNodeId"
            tvDeviceId.visibility = View.VISIBLE

            // Phase 3: Wait 1.5s then fade smoothly to dashboard
            delay(1500L)
            startFadeToDashboard()
        }
    }

    private fun showTestOverrideDialog(tvStatus: TextView, tvDeviceId: TextView) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_test_override, null)
        val etTestCode = dialogView.findViewById<EditText>(R.id.etTestCode)
        val tvFeedback = dialogView.findViewById<TextView>(R.id.tvTestFeedback)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelTest)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btnSubmitTest)

        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSubmit.setOnClickListener {
            val code = etTestCode.text.toString().trim()
            if (code.equals("T3ST", ignoreCase = true)) {
                dialog.dismiss()
                startTestModeSequence(tvStatus, tvDeviceId)
            } else {
                tvFeedback.text = "[ ACCESS DENIED: INVALID CODE ]"
                tvFeedback.visibility = View.VISIBLE
            }
        }

        dialog.show()
    }

    private fun startTestModeSequence(
        tvStatus: TextView,
        tvDeviceId: TextView
    ) {
        isConnecting = true
        phoneNodeId = usbHelper.getPhoneNodeId(this)
        val testHardwareName = "ESP32 DevKit (T3ST Sim Mode)"
        val testSerial = "T3ST-SIM-001"
        val testVendorId = "0x303A"
        val testProductId = "0x0002"

        connectedDeviceId = phoneNodeId
        connectedHardwareName = testHardwareName
        connectedSerial = testSerial
        connectedVendorId = testVendorId
        connectedProductId = testProductId

        // Register test node to Firebase Firestore
        registerDeviceToFirebase(
            nodeId = phoneNodeId,
            hardwareName = testHardwareName,
            serialNumber = testSerial,
            vendorId = testVendorId,
            productId = testProductId
        )

        lifecycleScope.launch {
            // Phase 1: Show "TEST OVERRIDE..." with amber text
            tvStatus.text = "[ TEST OVERRIDE: T3ST DETECTED ]"
            tvStatus.setTextColor(getColor(R.color.alert_amber))

            // Animate 8-bit connection dots
            val connectDots = arrayOf(
                "[ LINKING TO TEST SIMULATOR     ]",
                "[ LINKING TO TEST SIMULATOR .   ]",
                "[ LINKING TO TEST SIMULATOR . . ]",
                "[ LINKING TO TEST SIMULATOR . . . ]"
            )
            var step = 0
            val totalTicks = (loadingDelayMs / frameDurationMs).toInt()
            for (tick in 0 until totalTicks) {
                delay(frameDurationMs * 2)
                tvStatus.text = connectDots[step % connectDots.size]
                step++
            }

            // Phase 2: Show "LINKED" with green text + phone's permanent Node ID
            isDeviceConnected = true
            isConnecting = false
            tvStatus.text = "[ ⚡ TEST OVERRIDE: T3ST LINKED ]"
            tvStatus.setTextColor(getColor(R.color.accent_green_alt))
            tvDeviceId.text = "NODE ID: $phoneNodeId"
            tvDeviceId.visibility = View.VISIBLE

            // Phase 3: Wait 1.5s then fade smoothly to dashboard
            delay(1500L)
            startFadeToDashboard()
        }
    }

    private fun startFadeToDashboard() {
        val mainLayout = findViewById<FrameLayout>(R.id.main)

        // Create fade overlay
        val fadeOverlay = View(this)
        fadeOverlay.setBackgroundColor(Color.BLACK)
        fadeOverlay.alpha = 0f
        mainLayout.addView(fadeOverlay)

        lifecycleScope.launch {
            // 8 ticks x 125ms = 1000ms fade to black
            for (tick in 0 until 8) {
                delay(frameDurationMs)
                val alpha = (tick + 1) * 0.125f
                fadeOverlay.alpha = alpha.coerceAtMost(1f)
            }
            fadeOverlay.alpha = 1f

            // Launch dashboard with Phone Node ID and attached dongle telemetry
            val intent = Intent(this@MainActivity, DashboardActivity::class.java).apply {
                putExtra("NODE_ID", phoneNodeId)
                putExtra("DEVICE_ID", phoneNodeId)
                putExtra("HARDWARE_NAME", connectedHardwareName)
                putExtra("DEVICE_SERIAL", connectedSerial)
                putExtra("VENDOR_ID", connectedVendorId)
                putExtra("PRODUCT_ID", connectedProductId)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun startSteppedSearchingAnimation(tvStatus: TextView) {
        lifecycleScope.launch {
            val dotPatterns = arrayOf(
                "[ SEARCHING FOR RESQPLUG DEVICE     ]",
                "[ SEARCHING FOR RESQPLUG DEVICE .   ]",
                "[ SEARCHING FOR RESQPLUG DEVICE . . ]",
                "[ SEARCHING FOR RESQPLUG DEVICE . . . ]"
            )
            var step = 0
            while (isActive) {
                if (!isDeviceConnected && !isConnecting) {
                    delay(frameDurationMs * 2) // Step dots every 250ms (2 ticks at 8 FPS)
                    tvStatus.text = dotPatterns[step % dotPatterns.size]
                    step++
                } else {
                    delay(frameDurationMs)
                }
            }
        }
    }

    private fun startEmoticonBlink(tvEmoticon: TextView) {
        lifecycleScope.launch {
            var isOn = true
            while (isActive) {
                delay(frameDurationMs * 4) // Blink every 500ms (4 ticks at 8 FPS)
                isOn = !isOn
                tvEmoticon.alpha = if (isOn) 1.0f else 0.2f
            }
        }
    }

    private fun startStarfieldTwinkle(starfieldView: StarfieldView) {
        lifecycleScope.launch {
            while (isActive) {
                delay(frameDurationMs) // Tick every 125ms (8 FPS)
                starfieldView.tick()
            }
        }
    }

    private fun registerDeviceToFirebase(
        nodeId: String,
        hardwareName: String,
        serialNumber: String,
        vendorId: String,
        productId: String
    ) {
        val db = FirebaseFirestore.getInstance()
        val device = hashMapOf(
            "nodeId" to nodeId,
            "deviceId" to nodeId,
            "connectedDongle" to hardwareName,
            "dongleSerial" to serialNumber,
            "dongleVendorId" to vendorId,
            "dongleProductId" to productId,
            "userName" to "",
            "role" to "CITIZEN",
            "status" to "active",
            "platform" to "android",
            "connected_at" to FieldValue.serverTimestamp(),
            "last_seen" to FieldValue.serverTimestamp()
        )
        db.collection("active_devices")
            .document(nodeId)
            .set(device)
            .addOnSuccessListener { Log.d("Firebase", "Registered active node: $nodeId with dongle: $hardwareName") }
            .addOnFailureListener { Log.e("Firebase", "Registration failed", it) }
    }
}
