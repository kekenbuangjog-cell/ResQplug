package com.example.resqplug

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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

import com.example.resqplug.simulation.UserRole

import com.example.resqplug.ui.fragments.SosHubFragment

class DashboardActivity : AppCompatActivity() {

    private val frameDurationMs = 125L // 8 FPS

    private lateinit var tvNodeId: TextView
    private lateinit var tvBatteryPct: TextView
    private lateinit var tvRfStatus: TextView

    // Bottom Nav Views
    private lateinit var navDashboard: LinearLayout
    private lateinit var navSos: LinearLayout
    private lateinit var navMessages: LinearLayout
    private lateinit var navSettings: LinearLayout
    private lateinit var tvNavDashboardIcon: TextView
    private lateinit var tvNavDashboardText: TextView
    private lateinit var tvNavSosIcon: TextView
    private lateinit var tvNavSosText: TextView
    private lateinit var tvNavMessagesIcon: TextView
    private lateinit var tvNavMessagesText: TextView
    private lateinit var tvNavSettingsIcon: TextView
    private lateinit var tvNavSettingsText: TextView

    var userName: String = ""
        private set
    var nodeId: String = "UNKNOWN"
        private set
    var deviceId: String = "UNKNOWN"
        private set
    var hardwareName: String = "ESP32 Node"
        private set
    var deviceSerial: String = ""
        private set
    var vendorId: String = ""
        private set
    var productId: String = ""
        private set
    var userRole: UserRole = UserRole.CITIZEN
        private set
    var isUsbConnected: Boolean = true
        private set

    private lateinit var usbHelper: com.example.resqplug.usb.UsbDeviceHelper
    private var usbReceiver: com.example.resqplug.usb.UsbConnectionReceiver? = null

    val simulationEngine = SimulationEngine {
        runOnUiThread {
            onSimulationUpdated()
        }
    }

    private var activeTab = Tab.DASHBOARD

    enum class Tab {
        DASHBOARD, SOS_HUB, MESSAGES, SETTINGS
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

        usbHelper = com.example.resqplug.usb.UsbDeviceHelper(this)

        // Init Header views
        tvNodeId = findViewById(R.id.tvNodeId)
        tvBatteryPct = findViewById(R.id.tvBatteryPct)
        tvRfStatus = findViewById(R.id.tvRfStatus)
        val btnExitSession = findViewById<TextView>(R.id.btnExitSession)

        btnExitSession.setOnClickListener {
            markDeviceInactiveInFirestore()
            Toast.makeText(this, "[ ⏻ SESSION CLOSED - OFFLINE ]", Toast.LENGTH_SHORT).show()
            finishAffinity()
        }

        // Init Bottom Nav views
        navDashboard = findViewById(R.id.nav_dashboard)
        navSos = findViewById(R.id.nav_sos)
        navMessages = findViewById(R.id.nav_messages)
        navSettings = findViewById(R.id.nav_settings)
        tvNavDashboardIcon = findViewById(R.id.tvNavDashboardIcon)
        tvNavDashboardText = findViewById(R.id.tvNavDashboardText)
        tvNavSosIcon = findViewById(R.id.tvNavSosIcon)
        tvNavSosText = findViewById(R.id.tvNavSosText)
        tvNavMessagesIcon = findViewById(R.id.tvNavMessagesIcon)
        tvNavMessagesText = findViewById(R.id.tvNavMessagesText)
        tvNavSettingsIcon = findViewById(R.id.tvNavSettingsIcon)
        tvNavSettingsText = findViewById(R.id.tvNavSettingsText)

        // Get device telemetry from intent or phone hardware
        val intentNodeId = intent.getStringExtra("NODE_ID") ?: intent.getStringExtra("DEVICE_ID")
        nodeId = if (!intentNodeId.isNullOrEmpty() && intentNodeId != "UNKNOWN") {
            intentNodeId
        } else {
            usbHelper.getPhoneNodeId(this)
        }
        deviceId = nodeId

        hardwareName = intent.getStringExtra("HARDWARE_NAME") ?: "ESP32 Node"
        deviceSerial = intent.getStringExtra("DEVICE_SERIAL") ?: ""
        vendorId = intent.getStringExtra("VENDOR_ID") ?: ""
        productId = intent.getStringExtra("PRODUCT_ID") ?: ""

        val shortId = nodeId.takeLast(6).uppercase()
        tvNodeId.text = getString(R.string.dash_node_id, "#$shortId")
        tvBatteryPct.text = getString(R.string.dash_battery_pct, 100)
        updateRfStatusUI(isConnected = true)

        // Set initial fragment if first load
        if (savedInstanceState == null) {
            selectTab(Tab.DASHBOARD)
        }

        // Bottom Nav Listeners
        navDashboard.setOnClickListener { selectTab(Tab.DASHBOARD) }
        navSos.setOnClickListener { selectTab(Tab.SOS_HUB) }
        navMessages.setOnClickListener { selectTab(Tab.MESSAGES) }
        navSettings.setOnClickListener { selectTab(Tab.SETTINGS) }

        // Load saved User Role (default to CITIZEN if not set)
        val savedRoleStr = getSharedPreferences("resqplug_prefs", MODE_PRIVATE)
            .getString("USER_ROLE", "CITIZEN") ?: "CITIZEN"
        userRole = try { UserRole.valueOf(savedRoleStr) } catch (e: Exception) { UserRole.CITIZEN }

        // Load saved Callsign / Name from SharedPreferences (or prompt if first boot)
        val savedName = getSharedPreferences("resqplug_prefs", MODE_PRIVATE)
            .getString("USER_NAME", "") ?: ""
        if (savedName.isNotEmpty()) {
            userName = savedName
            updateUserNameInFirestore(savedName)
        } else {
            showNameDialog()
        }

        // Start real-time Firestore synchronization engine
        simulationEngine.start(nodeId, userName, userRole)

        // Starfield background
        val starfieldView = findViewById<StarfieldView>(R.id.dashStarfield)
        startStarfieldTwinkle(starfieldView)

        // Start simulation loop
        startSimulationLoop()
    }

    override fun onStart() {
        super.onStart()

        // Register USB receiver to handle unplug & replug in real time
        usbReceiver = com.example.resqplug.usb.UsbConnectionReceiver(
            onDeviceAttached = { device -> onUsbDeviceAttached(device) },
            onDeviceDetached = { device -> onUsbDeviceDetached(device) }
        )
        registerReceiver(usbReceiver, usbReceiver!!.createIntentFilter())

        // Check if currently connected
        val connectedDevice = usbHelper.findResQPlugDevice()
        isUsbConnected = (connectedDevice != null)
        if (connectedDevice != null) {
            hardwareName = usbHelper.getHardwareName(connectedDevice)
            deviceSerial = usbHelper.getSerialNumber(connectedDevice)
            vendorId = usbHelper.getVendorIdHex(connectedDevice)
            productId = usbHelper.getProductIdHex(connectedDevice)
        }
        updateRfStatusUI(isUsbConnected)
        if (isUsbConnected) {
            markDeviceActiveInFirestore()
        }
    }

    override fun onStop() {
        super.onStop()
        usbReceiver?.let { unregisterReceiver(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        simulationEngine.stop()
        markDeviceInactiveInFirestore()
    }

    private fun onUsbDeviceAttached(device: android.hardware.usb.UsbDevice) {
        if (!usbHelper.isResQPlugDevice(device)) return
        hardwareName = usbHelper.getHardwareName(device)
        deviceSerial = usbHelper.getSerialNumber(device)
        vendorId = usbHelper.getVendorIdHex(device)
        productId = usbHelper.getProductIdHex(device)

        isUsbConnected = true
        runOnUiThread {
            updateRfStatusUI(isConnected = true)
            markDeviceActiveInFirestore()
            Toast.makeText(this, "[ ⚡ ESP32 LINKED: $hardwareName ]", Toast.LENGTH_SHORT).show()

            val currentFrag = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
            if (currentFrag is SettingsFragment) {
                currentFrag.updateSettingsInfo()
            }
        }
    }

    private fun onUsbDeviceDetached(device: android.hardware.usb.UsbDevice) {
        isUsbConnected = false
        runOnUiThread {
            updateRfStatusUI(isConnected = false)
            markDeviceInactiveInFirestore()
            Toast.makeText(this, "[ ⚠️ DONGLE UNPLUGGED - OFFLINE ]", Toast.LENGTH_SHORT).show()

            val currentFrag = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
            if (currentFrag is SettingsFragment) {
                currentFrag.updateSettingsInfo()
            }
        }
    }

    private fun updateRfStatusUI(isConnected: Boolean) {
        if (isConnected) {
            tvRfStatus.text = "[ ⚡ ESP32 LINKED ]"
            tvRfStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
        } else {
            tvRfStatus.text = "[ ⚠️ DONGLE UNPLUGGED ]"
            tvRfStatus.setTextColor(ContextCompat.getColor(this, R.color.alert_red))
        }
    }

    fun selectTab(tab: Tab) {
        // Restrict SOS Hub tab to verified Responders and Commanders only
        if (tab == Tab.SOS_HUB && userRole == UserRole.CITIZEN) {
            selectTab(Tab.DASHBOARD)
            return
        }

        activeTab = tab

        val fragment: Fragment = when (tab) {
            Tab.DASHBOARD -> DashboardFragment()
            Tab.SOS_HUB -> SosHubFragment()
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
        val alertRed = ContextCompat.getColor(this, R.color.alert_red)

        tvNavDashboardIcon.setTextColor(if (tab == Tab.DASHBOARD) activeColor else inactiveColor)
        tvNavDashboardText.setTextColor(if (tab == Tab.DASHBOARD) activeColor else inactiveColor)
        navDashboard.isSelected = (tab == Tab.DASHBOARD)

        // Only show SOS Hub tab to verified Responders & Commanders
        val isAuthorizedResponder = (userRole == UserRole.RESPONDER || userRole == UserRole.COMMANDER)
        navSos.visibility = if (isAuthorizedResponder) View.VISIBLE else View.GONE

        val openSosCount = simulationEngine.getOpenSosCount()
        tvNavSosIcon.setTextColor(if (tab == Tab.SOS_HUB) alertRed else if (openSosCount > 0) alertRed else inactiveColor)
        tvNavSosText.setTextColor(if (tab == Tab.SOS_HUB) alertRed else inactiveColor)
        tvNavSosText.text = if (openSosCount > 0) "SOS ($openSosCount)" else "SOS Hub"
        navSos.isSelected = (tab == Tab.SOS_HUB)

        tvNavMessagesIcon.setTextColor(if (tab == Tab.MESSAGES) activeColor else inactiveColor)
        tvNavMessagesText.setTextColor(if (tab == Tab.MESSAGES) activeColor else inactiveColor)
        navMessages.isSelected = (tab == Tab.MESSAGES)

        tvNavSettingsIcon.setTextColor(if (tab == Tab.SETTINGS) activeColor else inactiveColor)
        tvNavSettingsText.setTextColor(if (tab == Tab.SETTINGS) activeColor else inactiveColor)
        navSettings.isSelected = (tab == Tab.SETTINGS)
    }

    fun openDirectChatWithCitizen(citizenName: String) {
        selectTab(Tab.MESSAGES)
        supportFragmentManager.executePendingTransactions()
        val frag = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
        if (frag is MessagesFragment) {
            frag.openDirectChatForCitizen(citizenName)
        }
    }

    fun sendBroadcastMessage(
        text: String,
        priority: Priority,
        channel: String = "BROADCAST",
        recipientId: String? = null,
        recipientName: String? = null
    ): ChatMessage {
        val msg = simulationEngine.sendMessage(text, priority, channel, recipientId, recipientName)
        val currentFrag = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
        if (currentFrag is MessagesFragment) {
            currentFrag.refreshMeshComms()
        }
        return msg
    }

    private fun onSimulationUpdated() {
        val openSosCount = simulationEngine.getOpenSosCount()
        tvNavSosText.text = if (openSosCount > 0) "SOS ($openSosCount)" else "SOS Hub"

        val currentFrag = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
        when (currentFrag) {
            is MessagesFragment -> currentFrag.refreshMeshComms()
            is DashboardFragment -> currentFrag.updateMeshHealth()
            is SosHubFragment -> currentFrag.refreshSosIncidents()
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
                getSharedPreferences("resqplug_prefs", MODE_PRIVATE).edit()
                    .putString("USER_NAME", name)
                    .apply()
                simulationEngine.setUserName(name)
                updateUserNameInFirestore(name)

                val currentFrag = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
                when (currentFrag) {
                    is SettingsFragment -> currentFrag.updateSettingsInfo()
                    is MessagesFragment -> currentFrag.refreshMeshComms()
                    is DashboardFragment -> currentFrag.updateMeshHealth()
                    is SosHubFragment -> currentFrag.refreshSosIncidents()
                }
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please enter your name / callsign", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    fun showEditNameDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_name_input, null)
        val etUserName = dialogView.findViewById<EditText>(R.id.etUserName)
        val btnOk = dialogView.findViewById<Button>(R.id.btnDialogOk)

        if (userName.isNotEmpty()) {
            etUserName.setText(userName)
            etUserName.setSelection(userName.length)
        }

        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.black)

        btnOk.setOnClickListener {
            val name = etUserName.text.toString().trim()
            if (name.isNotEmpty()) {
                userName = name
                getSharedPreferences("resqplug_prefs", MODE_PRIVATE).edit()
                    .putString("USER_NAME", name)
                    .apply()
                simulationEngine.setUserName(name)
                updateUserNameInFirestore(name)

                val currentFrag = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
                when (currentFrag) {
                    is SettingsFragment -> currentFrag.updateSettingsInfo()
                    is MessagesFragment -> currentFrag.refreshMeshComms()
                    is DashboardFragment -> currentFrag.updateMeshHealth()
                    is SosHubFragment -> currentFrag.refreshSosIncidents()
                }
                Toast.makeText(this, "[ CALLSIGN UPDATED: $name ]", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please enter your name / callsign", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun markDeviceActiveInFirestore() {
        if (nodeId.isEmpty() || nodeId == "UNKNOWN") return
        val updates = hashMapOf(
            "nodeId" to nodeId,
            "deviceId" to nodeId,
            "connectedDongle" to hardwareName,
            "dongleSerial" to deviceSerial,
            "dongleVendorId" to vendorId,
            "dongleProductId" to productId,
            "userName" to userName,
            "role" to userRole.name,
            "status" to "active",
            "platform" to "android",
            "last_seen" to FieldValue.serverTimestamp()
        )
        FirebaseFirestore.getInstance().collection("active_devices")
            .document(nodeId)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener { Log.d("Firebase", "Node $nodeId marked active in Firestore with dongle: $hardwareName") }
            .addOnFailureListener { Log.e("Firebase", "Failed to mark active in Firestore", it) }
    }

    private fun markDeviceInactiveInFirestore() {
        if (nodeId.isEmpty() || nodeId == "UNKNOWN") return
        val updates = hashMapOf(
            "nodeId" to nodeId,
            "deviceId" to nodeId,
            "status" to "inactive",
            "connectedDongle" to "NONE (UNPLUGGED)",
            "last_seen" to FieldValue.serverTimestamp()
        )
        FirebaseFirestore.getInstance().collection("active_devices")
            .document(nodeId)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener { Log.d("Firebase", "Node $nodeId marked inactive in Firestore") }
            .addOnFailureListener { Log.e("Firebase", "Failed to mark device inactive", it) }
    }

    private fun updateUserNameInFirestore(name: String) {
        if (nodeId.isEmpty() || nodeId == "UNKNOWN") return
        val updates = hashMapOf(
            "nodeId" to nodeId,
            "deviceId" to nodeId,
            "userName" to name,
            "last_seen" to FieldValue.serverTimestamp()
        )
        FirebaseFirestore.getInstance().collection("active_devices")
            .document(nodeId)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener { Log.d("Firebase", "User name updated to: $name for node $nodeId") }
            .addOnFailureListener { Log.e("Firebase", "Failed to update user name", it) }
    }

    fun updateUserRole(role: UserRole) {
        userRole = role
        getSharedPreferences("resqplug_prefs", MODE_PRIVATE).edit()
            .putString("USER_ROLE", role.name)
            .apply()
        simulationEngine.setUserRole(role)
        updateUserRoleInFirestore(role)

        // If downgraded to Citizen while currently on SOS Hub tab, redirect to Dashboard
        if (role == UserRole.CITIZEN && activeTab == Tab.SOS_HUB) {
            selectTab(Tab.DASHBOARD)
        } else {
            updateBottomNavUI(activeTab)
        }

        val currentFrag = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
        when (currentFrag) {
            is SettingsFragment -> currentFrag.updateSettingsInfo()
            is MessagesFragment -> currentFrag.refreshMeshComms()
            is DashboardFragment -> currentFrag.updateMeshHealth()
            is SosHubFragment -> currentFrag.refreshSosIncidents()
        }
    }

    private fun updateUserRoleInFirestore(role: UserRole) {
        if (nodeId.isEmpty() || nodeId == "UNKNOWN") return
        val updates = hashMapOf(
            "nodeId" to nodeId,
            "deviceId" to nodeId,
            "role" to role.name,
            "last_seen" to FieldValue.serverTimestamp()
        )
        FirebaseFirestore.getInstance().collection("active_devices")
            .document(nodeId)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener { Log.d("Firebase", "User role updated to: ${role.name} for node $nodeId") }
            .addOnFailureListener { Log.e("Firebase", "Failed to update user role", it) }
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
