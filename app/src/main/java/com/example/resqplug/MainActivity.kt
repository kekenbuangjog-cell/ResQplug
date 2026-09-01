package com.example.resqplug

import android.content.Intent
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
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
        val starfieldView = findViewById<StarfieldView>(R.id.starfieldView)

        // 8 FPS Stepped Dot Searching Animation
        startSteppedSearchingAnimation(tvSearchingStatus)

        // 8 FPS Emoticon Blink
        startEmoticonBlink(tvEmoticon)

        // 8 FPS Starfield Twinkle
        startStarfieldTwinkle(starfieldView)

        // Handle USB intent from manifest auto-launch
        handleUsbIntent(intent)

        // Auto-Simulate Connection after 2.5s if no physical device is detected
        startAutoSimulationSearch(tvSearchingStatus, tvDeviceId)

        // Manual tap to immediately simulate device detection (simulation mode skip)
        tvSearchingStatus.setOnClickListener {
            if (!isDeviceConnected && !isConnecting) {
                val simId = "RQP-SIM-${(1000..9999).random()}"
                startConnectionSequence(simId, tvSearchingStatus, tvDeviceId)
            }
        }

        tvDeviceId.setOnClickListener {
            if (!isDeviceConnected && !isConnecting) {
                val simId = "RQP-SIM-${(1000..9999).random()}"
                startConnectionSequence(simId, tvSearchingStatus, tvDeviceId)
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

        // Scan for already-connected devices
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

    private fun startAutoSimulationSearch(tvSearchingStatus: TextView, tvDeviceId: TextView) {
        lifecycleScope.launch {
            delay(2500L)
            if (!isDeviceConnected && !isConnecting && !isFinishing) {
                val simId = "RQP-SIM-${(1000..9999).random()}"
                startConnectionSequence(simId, tvSearchingStatus, tvDeviceId)
            }
        }
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

        val deviceId = usbHelper.getUniqueDeviceId(device)
        val tvSearchingStatus = findViewById<TextView>(R.id.tvSearchingStatus)
        val tvDeviceId = findViewById<TextView>(R.id.tvDeviceId)

        runOnUiThread {
            startConnectionSequence(deviceId, tvSearchingStatus, tvDeviceId)
        }
    }

    private fun onUsbDeviceDetached(device: UsbDevice) {
        if (!isDeviceConnected) return
        if (connectedDeviceId.isNotEmpty() && !connectedDeviceId.startsWith("RQP-SIM-")) {
            val detachedId = usbHelper.getUniqueDeviceId(device)
            if (detachedId != connectedDeviceId) return
        }

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

    private fun startConnectionSequence(
        deviceId: String,
        tvStatus: TextView,
        tvDeviceId: TextView
    ) {
        isConnecting = true
        connectedDeviceId = deviceId

        // Register device to Firebase
        registerDeviceToFirebase(deviceId)

        lifecycleScope.launch {
            // Phase 1: Show "CONNECTING..." with amber text
            tvStatus.text = getString(R.string.splash_connecting)
            tvStatus.setTextColor(getColor(R.color.alert_amber))

            // Animate dots during connecting phase
            val connectDots = arrayOf(
                "[ CONNECTING TO RESQPLUG DEVICE     ]",
                "[ CONNECTING TO RESQPLUG DEVICE .   ]",
                "[ CONNECTING TO RESQPLUG DEVICE . . ]",
                "[ CONNECTING TO RESQPLUG DEVICE . . . ]"
            )
            var step = 0
            val totalTicks = (loadingDelayMs / frameDurationMs).toInt()
            for (tick in 0 until totalTicks) {
                delay(frameDurationMs * 2)
                tvStatus.text = connectDots[step % connectDots.size]
                step++
            }

            // Phase 2: Show "CONNECTED" with green text + device ID
            isDeviceConnected = true
            isConnecting = false
            tvStatus.text = getString(R.string.splash_connected)
            tvStatus.setTextColor(getColor(R.color.accent_green_alt))
            tvDeviceId.text = getString(R.string.splash_device_id, deviceId)
            tvDeviceId.visibility = View.VISIBLE

            // Phase 3: Wait 1.5s then fade to dashboard
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

            // Launch dashboard
            val intent = Intent(this@MainActivity, DashboardActivity::class.java)
            intent.putExtra("DEVICE_ID", connectedDeviceId)
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

    private fun registerDeviceToFirebase(deviceId: String) {
        val db = FirebaseFirestore.getInstance()
        val device = hashMapOf(
            "deviceId" to deviceId,
            "userName" to "",
            "status" to "active",
            "platform" to "android",
            "timestamp" to FieldValue.serverTimestamp(),
            "last_seen" to FieldValue.serverTimestamp()
        )
        db.collection("active_devices")
            .document(deviceId)
            .set(device)
            .addOnSuccessListener { Log.d("Firebase", "Registered device: $deviceId") }
            .addOnFailureListener { Log.e("Firebase", "Registration failed", it) }
    }
}
