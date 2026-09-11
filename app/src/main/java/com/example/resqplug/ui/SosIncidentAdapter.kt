package com.example.resqplug.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.resqplug.R
import com.example.resqplug.simulation.SosIncident
import com.example.resqplug.simulation.TriageStatus

import com.example.resqplug.simulation.UserRole

class SosIncidentAdapter(
    private var incidents: List<SosIncident>,
    private var userRole: UserRole = UserRole.CITIZEN,
    private val onTriageClick: (SosIncident, TriageStatus) -> Unit,
    private val onChatClick: (String) -> Unit
) : RecyclerView.Adapter<SosIncidentAdapter.IncidentViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IncidentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sos_incident, parent, false)
        return IncidentViewHolder(view)
    }

    override fun onBindViewHolder(holder: IncidentViewHolder, position: Int) {
        val incident = incidents[position]
        holder.bind(incident, userRole, onTriageClick, onChatClick)
    }

    override fun getItemCount(): Int = incidents.size

    fun updateData(newIncidents: List<SosIncident>, newRole: UserRole) {
        this.incidents = newIncidents
        this.userRole = newRole
        notifyDataSetChanged()
    }

    fun updateIncidents(newIncidents: List<SosIncident>) {
        this.incidents = newIncidents
        notifyDataSetChanged()
    }

    class IncidentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCitizen: TextView = itemView.findViewById(R.id.tvIncidentCitizen)
        private val tvHop: TextView = itemView.findViewById(R.id.tvIncidentHop)
        private val tvTime: TextView = itemView.findViewById(R.id.tvIncidentTime)
        private val tvMessage: TextView = itemView.findViewById(R.id.tvIncidentMessage)
        private val tvStatusBadge: TextView = itemView.findViewById(R.id.tvIncidentStatusBadge)
        private val tvResponderTag: TextView = itemView.findViewById(R.id.tvIncidentResponderTag)
        private val btnTriage: Button = itemView.findViewById(R.id.btnIncidentTriage)
        private val btnChat: Button = itemView.findViewById(R.id.btnIncidentChat)

        fun bind(
            incident: SosIncident,
            userRole: UserRole,
            onTriageClick: (SosIncident, TriageStatus) -> Unit,
            onChatClick: (String) -> Unit
        ) {
            tvCitizen.text = "🚨 ${incident.citizenName.uppercase()}"
            tvHop.text = if (incident.hops == 0) "LOCAL" else if (incident.hops == 1) "1 HOP" else "${incident.hops} HOPS"
            tvTime.text = incident.timestamp
            tvMessage.text = "\"${incident.distressMessage}\""

            val isAuthorizedResponder = (userRole == UserRole.RESPONDER || userRole == UserRole.COMMANDER)

            // Status Badge & Triage button state
            when (incident.triageStatus) {
                TriageStatus.OPEN -> {
                    tvStatusBadge.text = "[ 🚨 OPEN - AWAITING AID ]"
                    tvStatusBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.alert_red))
                    tvStatusBadge.setBackgroundResource(R.drawable.badge_triage_open)

                    tvResponderTag.visibility = View.GONE

                    if (isAuthorizedResponder) {
                        btnTriage.visibility = View.VISIBLE
                        btnTriage.text = "[ 🚑 DISPATCH AID ]"
                        btnTriage.setBackgroundResource(R.drawable.btn_send_evac)
                        btnTriage.setOnClickListener { onTriageClick(incident, TriageStatus.DISPATCHED) }
                    } else {
                        btnTriage.visibility = View.GONE
                    }
                }
                TriageStatus.DISPATCHED -> {
                    val resp = incident.dispatchedResponder ?: "Responder"
                    tvStatusBadge.text = "[ 🚑 RESCUER EN ROUTE ]"
                    tvStatusBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.alert_amber))
                    tvStatusBadge.setBackgroundResource(R.drawable.badge_triage_dispatched)

                    tvResponderTag.text = "Unit: $resp"
                    tvResponderTag.visibility = View.VISIBLE

                    if (isAuthorizedResponder) {
                        btnTriage.visibility = View.VISIBLE
                        btnTriage.text = "[ ✅ MARK RESOLVED ]"
                        btnTriage.setBackgroundResource(R.drawable.btn_send)
                        btnTriage.setOnClickListener { onTriageClick(incident, TriageStatus.RESOLVED) }
                    } else {
                        btnTriage.visibility = View.GONE
                    }
                }
                TriageStatus.RESOLVED -> {
                    tvStatusBadge.text = "[ ✅ RESOLVED ]"
                    tvStatusBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.accent_green))
                    tvStatusBadge.setBackgroundResource(R.drawable.badge_triage_resolved)

                    tvResponderTag.visibility = View.GONE

                    if (isAuthorizedResponder) {
                        btnTriage.visibility = View.VISIBLE
                        btnTriage.text = "[ 🔄 RE-OPEN ]"
                        btnTriage.setBackgroundResource(R.drawable.pixel_status_box)
                        btnTriage.setOnClickListener { onTriageClick(incident, TriageStatus.OPEN) }
                    } else {
                        btnTriage.visibility = View.GONE
                    }
                }
            }

            btnChat.setOnClickListener {
                onChatClick(incident.citizenName)
            }
        }
    }
}
