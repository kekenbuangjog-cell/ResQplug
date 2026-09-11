package com.example.resqplug.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.resqplug.R
import com.example.resqplug.simulation.MeshNode

import com.example.resqplug.simulation.UserRole

class ActiveContactAdapter(
    private var contacts: List<MeshNode>,
    private val onContactClick: (MeshNode) -> Unit
) : RecyclerView.Adapter<ActiveContactAdapter.ContactViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_online_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = contacts[position]
        holder.bind(contact)
        holder.itemView.setOnClickListener { onContactClick(contact) }
    }

    override fun getItemCount(): Int = contacts.size

    fun updateContacts(newContacts: List<MeshNode>) {
        this.contacts = newContacts
        notifyDataSetChanged()
    }

    class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAvatar: TextView = itemView.findViewById(R.id.tvContactAvatar)
        private val vOnlineDot: View = itemView.findViewById(R.id.vOnlineStatusDot)
        private val tvName: TextView = itemView.findViewById(R.id.tvContactName)
        private val tvHop: TextView = itemView.findViewById(R.id.tvContactHop)
        private val tvContactRole: TextView = itemView.findViewById(R.id.tvContactRole)

        fun bind(node: MeshNode) {
            // First letter or icon
            val initial = if (node.name.isNotEmpty()) node.name.take(1).uppercase() else "?"
            tvAvatar.text = initial
            tvAvatar.setTextColor(node.color)

            tvName.text = node.name
            tvHop.text = if (node.isYou) "YOU" else if (node.hops == 1) "1 HOP" else "${node.hops} HOPS"

            vOnlineDot.setBackgroundResource(
                if (node.isOnline) R.drawable.dot_online_green else R.drawable.dot_offline_gray
            )

            // Role binding
            tvContactRole.text = node.role.badgeText
            when (node.role) {
                UserRole.CITIZEN -> {
                    tvContactRole.setTextColor(ContextCompat.getColor(itemView.context, R.color.accent_green))
                }
                UserRole.RESPONDER -> {
                    tvContactRole.setTextColor(ContextCompat.getColor(itemView.context, R.color.alert_amber))
                }
                UserRole.COMMANDER -> {
                    tvContactRole.setTextColor(ContextCompat.getColor(itemView.context, R.color.alert_red))
                }
            }
        }
    }
}
