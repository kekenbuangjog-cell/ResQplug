package com.example.resqplug.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.resqplug.DashboardActivity
import com.example.resqplug.R
import com.example.resqplug.simulation.Priority
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

import android.widget.LinearLayout
import com.example.resqplug.simulation.UserRole

class DashboardFragment : Fragment() {

    private lateinit var tvBulletinSource: TextView
    private lateinit var tvBulletinText: TextView
    private lateinit var tvBulletinUpdated: TextView
    private lateinit var tvMeshActive: TextView
    private lateinit var tvMeshNearest: TextView
    private lateinit var tvMeshFarthest: TextView
    private lateinit var btnSos: Button

    private lateinit var layoutCommanderControls: LinearLayout
    private lateinit var btnToggleSilence: Button
    private lateinit var btnBroadcastEvacOrder: Button
    private lateinit var btnComposeBulletin: Button

    private var activeDevicesListener: ListenerRegistration? = null
    private var liveActiveNodeCount: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvBulletinSource = view.findViewById(R.id.tvBulletinSource)
        tvBulletinText = view.findViewById(R.id.tvBulletinText)
        tvBulletinUpdated = view.findViewById(R.id.tvBulletinUpdated)
        tvMeshActive = view.findViewById(R.id.tvMeshActive)
        tvMeshNearest = view.findViewById(R.id.tvMeshNearest)
        tvMeshFarthest = view.findViewById(R.id.tvMeshFarthest)
        btnSos = view.findViewById(R.id.btnSos)

        layoutCommanderControls = view.findViewById(R.id.layoutCommanderControls)
        btnToggleSilence = view.findViewById(R.id.btnToggleSilence)
        btnBroadcastEvacOrder = view.findViewById(R.id.btnBroadcastEvacOrder)
        btnComposeBulletin = view.findViewById(R.id.btnComposeBulletin)

        // Instant SOS trigger
        btnSos.setOnClickListener {
            val dashActivity = activity as? DashboardActivity
            dashActivity?.sendBroadcastMessage("URGENT: SOS DISTRESS SIGNAL BROADCASTED", Priority.SOS)
            Toast.makeText(requireContext(), "🚨 EMERGENCY SOS BROADCASTED", Toast.LENGTH_SHORT).show()
        }

        btnToggleSilence.setOnClickListener {
            val dashActivity = activity as? DashboardActivity ?: return@setOnClickListener
            val isNowActive = dashActivity.simulationEngine.toggleChannelSilence()
            val msg = if (isNowActive) "⚠️ MESH SILENCE ACTIVATED" else "✅ MESH SILENCE DEACTIVATED"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            updateMeshHealth()
        }

        btnBroadcastEvacOrder.setOnClickListener {
            val dashActivity = activity as? DashboardActivity ?: return@setOnClickListener
            val cmdName = if (dashActivity.userName.isNotEmpty()) dashActivity.userName else "Barangay Commander"
            dashActivity.simulationEngine.broadcastEvacuationOrder(cmdName)
            Toast.makeText(requireContext(), "🚨 MANDATORY EVACUATION ORDER BROADCASTED", Toast.LENGTH_SHORT).show()
            updateMeshHealth()
        }

        btnComposeBulletin.setOnClickListener {
            showComposeBulletinDialog()
        }

        // Attach live Firestore listener for active nodes
        listenToActiveDevices()

        updateMeshHealth()
    }

    private fun showComposeBulletinDialog() {
        val dashActivity = activity as? DashboardActivity ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_compose_bulletin, null)
        val etContent = dialogView.findViewById<android.widget.EditText>(R.id.etBulletinContent)
        val btnFlood = dialogView.findViewById<Button>(R.id.btnTplFlood)
        val btnRelief = dialogView.findViewById<Button>(R.id.btnTplRelief)
        val btnMedical = dialogView.findViewById<Button>(R.id.btnTplMedical)
        val btnUtility = dialogView.findViewById<Button>(R.id.btnTplUtility)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelBulletin)
        val btnBroadcast = dialogView.findViewById<Button>(R.id.btnBroadcastBulletin)

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnFlood.setOnClickListener {
            etContent.setText("Heavy rainfall warning in effect. Water level near riverbanks rising rapidly. Prepare go-bags.")
        }
        btnRelief.setOnClickListener {
            etContent.setText("Relief goods & potable water distribution starting at Banilad Gym evacuation center.")
        }
        btnMedical.setOnClickListener {
            etContent.setText("Medical triage station and emergency shelter active at Banilad Covered Court.")
        }
        btnUtility.setOnClickListener {
            etContent.setText("Power and communication restoration teams deployed. Stay clear of downed utility lines.")
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnBroadcast.setOnClickListener {
            val text = etContent.text.toString().trim()
            if (text.isNotEmpty()) {
                val cmdName = if (dashActivity.userName.isNotEmpty()) dashActivity.userName else "Barangay Commander"
                dashActivity.simulationEngine.postOfficialBulletin(cmdName, text)
                Toast.makeText(requireContext(), "📢 OFFICIAL BULLETIN BROADCASTED ACROSS MESH", Toast.LENGTH_SHORT).show()
                updateMeshHealth()
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Please type bulletin message or select a template", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activeDevicesListener?.remove()
        activeDevicesListener = null
    }

    private fun listenToActiveDevices() {
        activeDevicesListener = FirebaseFirestore.getInstance()
            .collection("active_devices")
            .whereEqualTo("status", "active")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("Firebase", "Listen failed for active devices", error)
                    liveActiveNodeCount = null
                    updateMeshHealth()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    liveActiveNodeCount = snapshots.size()
                    updateMeshHealth()
                }
            }
    }

    fun updateMeshHealth() {
        if (!isAdded) return
        val dashActivity = activity as? DashboardActivity ?: return

        // Live Community Bulletin Binding
        val bulletin = dashActivity.simulationEngine.latestCommunityBulletin
        if (bulletin != null) {
            tvBulletinSource.text = "OFFICIAL DISASTER BULLETIN: ${bulletin.first.uppercase()}"
            tvBulletinText.text = "\"${bulletin.second}\""
            tvBulletinUpdated.text = "UPDATED ${bulletin.third} • LORA MESH BROADCAST"
        }

        // Commander Command & Control Panel Visibility
        if (dashActivity.userRole == UserRole.COMMANDER) {
            layoutCommanderControls.visibility = View.VISIBLE
            val isSilence = dashActivity.simulationEngine.isChannelSilenceActive
            btnToggleSilence.text = if (isSilence) {
                "[ 🔴 SILENCE ACTIVE: TAP TO DEACTIVATE ]"
            } else {
                "[ ⚠️ TOGGLE MESH SILENCE MODE ]"
            }
        } else {
            layoutCommanderControls.visibility = View.GONE
        }

        // Use Firestore live active device count if available, otherwise local simulation
        val count = liveActiveNodeCount ?: dashActivity.simulationEngine.getNodeCount()
        tvMeshActive.text = getString(R.string.dash_mesh_active, count)
        tvMeshNearest.text = getString(R.string.dash_mesh_nearest, -68, "Direct / 1 Hop")
        tvMeshFarthest.text = getString(R.string.dash_mesh_farthest, 3, 4.2f)
    }
}
