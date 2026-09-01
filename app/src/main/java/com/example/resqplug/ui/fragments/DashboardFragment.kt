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

class DashboardFragment : Fragment() {

    private lateinit var tvBulletinSource: TextView
    private lateinit var tvBulletinText: TextView
    private lateinit var tvBulletinUpdated: TextView
    private lateinit var tvMeshActive: TextView
    private lateinit var tvMeshNearest: TextView
    private lateinit var tvMeshFarthest: TextView
    private lateinit var btnSos: Button

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

        // Instant SOS trigger
        btnSos.setOnClickListener {
            val dashActivity = activity as? DashboardActivity
            dashActivity?.sendBroadcastMessage("URGENT: SOS DISTRESS SIGNAL BROADCASTED", Priority.SOS)
            Toast.makeText(requireContext(), "🚨 EMERGENCY SOS BROADCASTED", Toast.LENGTH_SHORT).show()
        }

        // Attach live Firestore listener for active nodes
        listenToActiveDevices()

        updateMeshHealth()
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

        // Use Firestore live active device count if available, otherwise local simulation
        val count = liveActiveNodeCount ?: dashActivity.simulationEngine.getNodeCount()
        tvMeshActive.text = getString(R.string.dash_mesh_active, count)
        tvMeshNearest.text = getString(R.string.dash_mesh_nearest, -68, "Direct / 1 Hop")
        tvMeshFarthest.text = getString(R.string.dash_mesh_farthest, 3, 4.2f)
    }
}
