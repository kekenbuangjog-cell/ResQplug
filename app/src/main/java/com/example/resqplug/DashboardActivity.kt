package com.example.resqplug

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.resqplug.simulation.ChatMessage
import com.example.resqplug.simulation.Priority
import com.example.resqplug.simulation.SimulationEngine
import com.example.resqplug.ui.StarfieldView
import com.example.resqplug.ui.fragments.DashboardFragment
import com.example.resqplug.ui.fragments.MessagesFragment
import com.example.resqplug.ui.fragments.SettingsFragment
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    private val frameDurationMs = 125L // 8 FPS

    private lateinit var tvNodeId: TextView
    private lateinit var tvBatteryPct: TextView
    private lateinit var tvRfStatus: TextView

    // Bottom Nav Views
    private lateinit var navDashboard: LinearLayout
    private lateinit var navMessages: LinearLayout
    private lateinit var navSettings: LinearLayout
    private lateinit var tvNavDashboardIcon: TextView
    private lateinit var tvNavDashboardText: TextView
    private lateinit var tvNavMessagesIcon: TextView
    private lateinit var tvNavMessagesText: TextView
    private lateinit var tvNavSettingsIcon: TextView
    private lateinit var tvNavSettingsText: TextView

    var userName: String = ""
        private set
    var deviceId: String = "UNKNOWN"
        private set

    val simulationEngine = SimulationEngine {
        runOnUiThread {
            onSimulationUpdated()
        }
    }

    private var activeTab = Tab.DASHBOARD

    enum class Tab {
        DASHBOARD, MESSAGES, SETTINGS
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.dashMain)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Init Header views
        tvNodeId = findViewById(R.id.tvNodeId)
        tvBatteryPct = findViewById(R.id.tvBatteryPct)
        tvRfStatus = findViewById(R.id.tvRfStatus)

        // Init Bottom Nav views
        navDashboard = findViewById(R.id.nav_dashboard)
        navMessages = findViewById(R.id.nav_messages)
        navSettings = findViewById(R.id.nav_settings)
        tvNavDashboardIcon = findViewById(R.id.tvNavDashboardIcon)
        tvNavDashboardText = findViewById(R.id.tvNavDashboardText)
        tvNavMessagesIcon = findViewById(R.id.tvNavMessagesIcon)
        tvNavMessagesText = findViewById(R.id.tvNavMessagesText)
        tvNavSettingsIcon = findViewById(R.id.tvNavSettingsIcon)
        tvNavSettingsText = findViewById(R.id.tvNavSettingsText)

        // Get device ID from intent
        deviceId = intent.getStringExtra("DEVICE_ID") ?: "UNKNOWN"
        val shortId = if (deviceId.length > 6) deviceId.takeLast(6) else deviceId
        tvNodeId.text = getString(R.string.dash_node_id, "#${shortId.uppercase()}")
        tvBatteryPct.text = getString(R.string.dash_battery_pct, 85)

        // Set initial fragment if first load
        if (savedInstanceState == null) {
            selectTab(Tab.DASHBOARD)
        }

        // Bottom Nav Listeners
        navDashboard.setOnClickListener { selectTab(Tab.DASHBOARD) }
        navMessages.setOnClickListener { selectTab(Tab.MESSAGES) }
        navSettings.setOnClickListener { selectTab(Tab.SETTINGS) }

        // Show name dialog
        showNameDialog()

        // Starfield background
        val starfieldView = findViewById<StarfieldView>(R.id.dashStarfield)
        startStarfieldTwinkle(starfieldView)

        // Start simulation loop
        startSimulationLoop()
    }

    override fun onDestroy() {
        super.onDestroy()
        markDeviceInactiveInFirestore()
    }

    fun selectTab(tab: Tab) {
        activeTab = tab

        val fragment: Fragment = when (tab) {
            Tab.DASHBOARD -> DashboardFragment()
            Tab.MESSAGES -> MessagesFragment()
            Tab.SETTINGS -> SettingsFragment()
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainerView, fragment)
            .commit()

        updateBottomNavUI(tab)
    }

    private fun updateBottomNavUI(tab: Tab) {
        val activeColor = ContextCompat.getColor(this, R.color.accent_green)
        val inactiveColor = ContextCompat.getColor(this, R.color.text_gray)

        tvNavDashboardIcon.setTextColor(if (tab == Tab.DASHBOARD) activeColor else inactiveColor)
        tvNavDashboardText.setTextColor(if (tab == Tab.DASHBOARD) activeColor else inactiveColor)
        navDashboard.isSelected = (tab == Tab.DASHBOARD)

        tvNavMessagesIcon.setTextColor(if (tab == Tab.MESSAGES) activeColor else inactiveColor)
        tvNavMessagesText.setTextColor(if (tab == Tab.MESSAGES) activeColor else inactiveColor)
        navMessages.isSelected = (tab == Tab.MESSAGES)

        tvNavSettingsIcon.setTextColor(if (tab == Tab.SETTINGS) activeColor else inactiveColor)
        tvNavSettingsText.setTextColor(if (tab == Tab.SETTINGS) activeColor else inactiveColor)
        navSettings.isSelected = (tab == Tab.SETTINGS)
    }

    fun sendBroadcastMessage(text: String, priority: Priority): ChatMessage {
        val msg = simulationEngine.sendMessage(text, priority)
        val currentFrag = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
        if (currentFrag is MessagesFragment) {
            currentFrag.refreshMessages()
        }
        return msg
    }

    private fun onSimulationUpdated() {
        val currentFrag = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
        when (currentFrag) {
            is MessagesFragment -> currentFrag.refreshMessages()
            is DashboardFragment -> currentFrag.updateMeshHealth()
        }
    }

    private fun showNameDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_name_input, null)
        val etUserName = dialogView.findViewById<EditText>(R.id.etUserName)
        val btnOk = dialogView.findViewById<Button>(R.id.btnDialogOk)

        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.black)

        btnOk.setOnClickListener {
            val name = etUserName.text.toString().trim()
            if (name.isNotEmpty()) {
                userName = name
                updateUserNameInFirestore(name)
                val currentFrag = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
                if (currentFrag is SettingsFragment) {
                    currentFrag.updateSettingsInfo()
                }
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun updateUserNameInFirestore(name: String) {
        if (deviceId.isEmpty() || deviceId == "UNKNOWN") return
        val updates = hashMapOf(
            "deviceId" to deviceId,
            "userName" to name,
            "last_seen" to FieldValue.serverTimestamp()
        )
        FirebaseFirestore.getInstance().collection("active_devices")
            .document(deviceId)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener { Log.d("Firebase", "User name updated to: $name") }
            .addOnFailureListener { Log.e("Firebase", "Failed to update user name", it) }
    }

    private fun markDeviceInactiveInFirestore() {
        if (deviceId.isEmpty() || deviceId == "UNKNOWN") return
        val updates = hashMapOf(
            "deviceId" to deviceId,
            "status" to "inactive",
            "last_seen" to FieldValue.serverTimestamp()
        )
        FirebaseFirestore.getInstance().collection("active_devices")
            .document(deviceId)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener { Log.d("Firebase", "Device marked inactive: $deviceId") }
            .addOnFailureListener { Log.e("Firebase", "Failed to mark device inactive", it) }
    }

    private fun startSimulationLoop() {
        lifecycleScope.launch {
            while (isActive) {
                delay(frameDurationMs)
                simulationEngine.tick()
            }
        }
    }

    private fun startStarfieldTwinkle(starfieldView: StarfieldView) {
        lifecycleScope.launch {
            while (isActive) {
                delay(frameDurationMs)
                starfieldView.tick()
            }
        }
    }
}
