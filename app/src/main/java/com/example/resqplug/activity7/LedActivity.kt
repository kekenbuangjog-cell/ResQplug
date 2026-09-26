package com.example.resqplug.activity7

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.resqplug.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Screen 2 View: Activity 7 Hardware & LED Dashboard (8-Bit Pixel Retro Edition).
 * Incorporates strict 8 FPS radar pulse and 8 FPS stepped voltage animations.
 * Strictly adheres to MVP pattern with ZERO direct Firebase logic.
 */
class LedActivity : AppCompatActivity(), LedContract.View {

    private val frameDurationMs = 125L // 8 FPS Retro Standard

    private lateinit var presenter: LedContract.Presenter

    private lateinit var btnBack: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvUserEmail: TextView
    private lateinit var btnSignOut: Button
    private lateinit var tvDevicePresence: TextView
    private lateinit var tvLastSeen: TextView
    private lateinit var tvLoraStatus: TextView
    private lateinit var tvTransportMode: TextView
    private lateinit var tvLedBulbIcon: TextView
    private lateinit var tvLedVoltageBar: TextView
    private lateinit var tvActualLedStatus: TextView
    private lateinit var tvCommandRequested: TextView
    private lateinit var btnTurnOn: Button
    private lateinit var btnTurnOff: Button
    private lateinit var btnSimulateOffline: Button
    private lateinit var btnChangeDevice: Button
    private lateinit var tvStatusMessage: TextView

    private var isSimulatedOffline = false
    private var isDeviceOnline = false
    private var radarJob: Job? = null
    private var voltageJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_led)

        initViews()
        initPresenter()
        setupListeners()
        start8FpsRadarPulse()

        val passedEmail = intent.getStringExtra("EXTRA_VERIFIED_EMAIL")
        if (!passedEmail.isNullOrBlank()) {
            tvUserEmail.text = passedEmail
        }

        val passedTransport = intent.getStringExtra("EXTRA_TRANSPORT_MODE")
        val passedDevice = intent.getStringExtra("EXTRA_DEVICE_IDENTIFIER")
        if (!passedTransport.isNullOrBlank()) {
            if (!passedDevice.isNullOrBlank()) {
                tvTransportMode.text = "Transport : $passedTransport ($passedDevice)"
            } else {
                tvTransportMode.text = "Transport : $passedTransport"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        radarJob?.cancel()
        voltageJob?.cancel()
        presenter.detachView()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        progressBar = findViewById(R.id.progressBar)
        tvUserEmail = findViewById(R.id.tvUserEmail)
        btnSignOut = findViewById(R.id.btnSignOut)
        tvDevicePresence = findViewById(R.id.tvDevicePresence)
        tvLastSeen = findViewById(R.id.tvLastSeen)
        tvLoraStatus = findViewById(R.id.tvLoraStatus)
        tvTransportMode = findViewById(R.id.tvTransportMode)
        tvLedBulbIcon = findViewById(R.id.tvLedBulbIcon)
        tvLedVoltageBar = findViewById(R.id.tvLedVoltageBar)
        tvActualLedStatus = findViewById(R.id.tvActualLedStatus)
        tvCommandRequested = findViewById(R.id.tvCommandRequested)
        btnTurnOn = findViewById(R.id.btnTurnOn)
        btnTurnOff = findViewById(R.id.btnTurnOff)
        btnSimulateOffline = findViewById(R.id.btnSimulateOffline)
        btnChangeDevice = findViewById(R.id.btnChangeDevice)
        tvStatusMessage = findViewById(R.id.tvStatusMessage)
    }

    private fun initPresenter() {
        presenter = LedPresenter()
        presenter.attachView(this)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnSignOut.setOnClickListener {
            presenter.logOut()
        }

        btnTurnOn.setOnClickListener {
            presenter.requestLedOn()
        }

        btnTurnOff.setOnClickListener {
            presenter.requestLedOff()
        }

        btnSimulateOffline.setOnClickListener {
            isSimulatedOffline = !isSimulatedOffline
            btnSimulateOffline.text = if (isSimulatedOffline) {
                "[ 📡 RESTORE ONLINE PRESENCE ]"
            } else {
                "[ 📡 TEST OFFLINE (SIMULATE DISCONNECT) ]"
            }
            presenter.simulateOffline(isSimulatedOffline)
        }

        btnChangeDevice.setOnClickListener {
            val email = tvUserEmail.text.toString().substringBefore(" [").trim()
            val intent = Intent(this, Activity7ConnectionActivity::class.java).apply {
                putExtra("EXTRA_VERIFIED_EMAIL", email)
            }
            startActivity(intent)
            finish()
        }
    }

    // =========================================================================
    // 8 FPS Stepped Animation Engine
    // =========================================================================

    private fun start8FpsRadarPulse() {
        radarJob?.cancel()
        radarJob = lifecycleScope.launch {
            val radarGlyphs = arrayOf("▫", "▣", "◼", "▣")
            var step = 0
            while (isActive) {
                if (isDeviceOnline) {
                    val glyph = radarGlyphs[step % radarGlyphs.size]
                    tvDevicePresence.text = "● ONLINE [ $glyph ]"
                    tvDevicePresence.setTextColor(ContextCompat.getColor(this@LedActivity, R.color.accent_green))
                    step++
                } else {
                    tvDevicePresence.text = "● OFFLINE [ ✖ ]"
                    tvDevicePresence.setTextColor(ContextCompat.getColor(this@LedActivity, R.color.alert_red))
                }
                delay(frameDurationMs) // 125ms per tick @ 8 FPS
            }
        }
    }

    private fun animate8FpsVoltageTransition(turnOn: Boolean) {
        voltageJob?.cancel()
        voltageJob = lifecycleScope.launch {
            if (turnOn) {
                tvLedVoltageBar.text = "[ GPIO 4: ░░░░░░░░░░░░░░░░ ] 0.0V"
                tvLedVoltageBar.setTextColor(ContextCompat.getColor(this@LedActivity, R.color.alert_red))
                delay(frameDurationMs)

                tvLedVoltageBar.text = "[ GPIO 4: ████████░░░░░░░░ ] 1.8V"
                tvLedVoltageBar.setTextColor(ContextCompat.getColor(this@LedActivity, R.color.alert_amber))
                delay(frameDurationMs)

                tvLedVoltageBar.text = "[ GPIO 4: ████████████████ ] 3.3V"
                tvLedVoltageBar.setTextColor(ContextCompat.getColor(this@LedActivity, R.color.accent_green))
            } else {
                tvLedVoltageBar.text = "[ GPIO 4: ████████░░░░░░░░ ] 1.5V"
                tvLedVoltageBar.setTextColor(ContextCompat.getColor(this@LedActivity, R.color.alert_amber))
                delay(frameDurationMs)

                tvLedVoltageBar.text = "[ GPIO 4: ░░░░░░░░░░░░░░░░ ] 0.0V"
                tvLedVoltageBar.setTextColor(ContextCompat.getColor(this@LedActivity, R.color.alert_red))
            }
        }
    }

    // =========================================================================
    // LedContract.View Implementations
    // =========================================================================

    override fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun updateLedStatus(actualStatus: String, commandRequested: String) {
        val isOn = actualStatus.equals("ON", ignoreCase = true)

        animate8FpsVoltageTransition(isOn)

        tvActualLedStatus.text = "[ ACTUAL STATUS: ${if (isOn) "HIGH / ON" else "LOW / OFF"} ]"
        tvActualLedStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (isOn) R.color.accent_green else R.color.alert_red
            )
        )

        tvCommandRequested.text = "Requested Command: $commandRequested"
        tvLedBulbIcon.alpha = if (isOn) 1.0f else 0.25f
    }

    override fun updateDevicePresence(isOnline: Boolean, lastSeenFormatted: String) {
        this.isDeviceOnline = isOnline
        tvLastSeen.text = "Heartbeat: $lastSeenFormatted"
    }

    override fun updateLoraStatus(loraStatus: String) {
        tvLoraStatus.text = "LoRa Radio: $loraStatus (SX1278 Ra-02 @ 433MHz)"
    }

    override fun updateTransportMode(transport: String) {
        val passedTransport = intent.getStringExtra("EXTRA_TRANSPORT_MODE")
        val passedDevice = intent.getStringExtra("EXTRA_DEVICE_IDENTIFIER")
        if (!passedTransport.isNullOrBlank()) {
            if (!passedDevice.isNullOrBlank()) {
                tvTransportMode.text = "Transport : $passedTransport ($passedDevice)"
            } else {
                tvTransportMode.text = "Transport : $passedTransport"
            }
        } else {
            tvTransportMode.text = "Transport : $transport"
        }
    }

    override fun showUserAuth(userEmail: String, isAnonymous: Boolean) {
        val badge = if (isAnonymous) " [Guest/Lab]" else " [Verified]"
        tvUserEmail.text = "$userEmail$badge"
    }

    override fun showStatusMessage(message: String) {
        tvStatusMessage.text = "[ $message ]"
    }

    override fun showError(errorMessage: String) {
        tvStatusMessage.text = "[ ⚠️ $errorMessage ]"
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    }

    override fun onLoggedOut() {
        Toast.makeText(this, "Logged out of Firebase session", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, Activity7AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }
}
