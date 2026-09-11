package com.example.resqplug.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.resqplug.DashboardActivity
import com.example.resqplug.R
import com.example.resqplug.ui.SosIncidentAdapter

class SosHubFragment : Fragment() {

    private lateinit var rvSosIncidents: RecyclerView
    private lateinit var layoutSosEmptyState: LinearLayout
    private lateinit var tvSosActiveCount: TextView
    private lateinit var incidentAdapter: SosIncidentAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sos_hub, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvSosIncidents = view.findViewById(R.id.rvSosIncidents)
        layoutSosEmptyState = view.findViewById(R.id.layoutSosEmptyState)
        tvSosActiveCount = view.findViewById(R.id.tvSosActiveCount)

        setupRecyclerView()
        refreshSosIncidents()
    }

    private fun setupRecyclerView() {
        val dashActivity = activity as? DashboardActivity ?: return
        rvSosIncidents.layoutManager = LinearLayoutManager(requireContext())

        incidentAdapter = SosIncidentAdapter(
            incidents = emptyList(),
            userRole = dashActivity.userRole,
            onTriageClick = { incident, newStatus ->
                val responderName = if (dashActivity.userName.isNotEmpty()) dashActivity.userName else "Responder"
                dashActivity.simulationEngine.triageIncident(incident.id, newStatus, responderName)
                refreshSosIncidents()
            },
            onChatClick = { citizenName ->
                dashActivity.openDirectChatWithCitizen(citizenName)
            }
        )
        rvSosIncidents.adapter = incidentAdapter
    }

    fun refreshSosIncidents() {
        if (!isAdded) return
        val dashActivity = activity as? DashboardActivity ?: return
        val incidents = dashActivity.simulationEngine.getSosIncidents()
        val openCount = dashActivity.simulationEngine.getOpenSosCount()

        tvSosActiveCount.text = "[ $openCount OPEN ]"

        if (incidents.isEmpty()) {
            layoutSosEmptyState.visibility = View.VISIBLE
            rvSosIncidents.visibility = View.GONE
        } else {
            layoutSosEmptyState.visibility = View.GONE
            rvSosIncidents.visibility = View.VISIBLE
            incidentAdapter.updateData(incidents, dashActivity.userRole)
        }
    }
}
