package com.example.resqplug.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.resqplug.R
import com.example.resqplug.simulation.ChatMessage
import com.example.resqplug.simulation.MeshNode
import com.example.resqplug.simulation.Priority

sealed class ConversationItem {
    data class BroadcastRoom(
        val activeCount: Int,
        val lastMessage: ChatMessage?
    ) : ConversationItem()

    data class DirectChat(
        val node: MeshNode,
        val lastMessage: ChatMessage?
    ) : ConversationItem()
}

class ConversationAdapter(
    private var items: List<ConversationItem>,
    private val onItemClick: (ConversationItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_BROADCAST = 0
        private const val TYPE_DIRECT = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ConversationItem.BroadcastRoom -> TYPE_BROADCAST
            is ConversationItem.DirectChat -> TYPE_DIRECT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ConvViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        (holder as ConvViewHolder).bind(item)
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<ConversationItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    class ConvViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAvatar: TextView = itemView.findViewById(R.id.tvConvAvatar)
        private val vOnlineDot: View = itemView.findViewById(R.id.vConvOnlineDot)
        private val tvName: TextView = itemView.findViewById(R.id.tvConvName)
        private val tvTime: TextView = itemView.findViewById(R.id.tvConvTime)
        private val tvPriorityBadge: TextView = itemView.findViewById(R.id.tvConvPriorityBadge)
        private val tvLastMessage: TextView = itemView.findViewById(R.id.tvConvLastMessage)

        fun bind(item: ConversationItem) {
            when (item) {
                is ConversationItem.BroadcastRoom -> {
                    tvAvatar.text = "ALL"
                    tvAvatar.setBackgroundResource(R.drawable.pixel_broadcast_avatar)
                    vOnlineDot.setBackgroundResource(R.drawable.dot_online_green)
                    tvName.text = "PUBLIC MESH BROADCAST"
                    tvName.setTextColor(ContextCompat.getColor(itemView.context, R.color.signal_cyan))

                    val lastMsg = item.lastMessage
                    if (lastMsg != null) {
                        tvTime.text = lastMsg.timestamp
                        tvLastMessage.text = "${lastMsg.sender}: \"${lastMsg.text}\""
                        bindPriority(lastMsg.priority)
                    } else {
                        tvTime.text = "--:--"
                        tvLastMessage.text = "Open emergency net (${item.activeCount} nodes in range)"
                        tvPriorityBadge.visibility = View.GONE
                    }
                }
                is ConversationItem.DirectChat -> {
                    val node = item.node
                    val initial = if (node.name.isNotEmpty()) node.name.take(1).uppercase() else "?"
                    tvAvatar.text = initial
                    tvAvatar.setTextColor(node.color)
                    tvAvatar.setBackgroundResource(R.drawable.pixel_avatar_box)

                    vOnlineDot.setBackgroundResource(
                        if (node.isOnline) R.drawable.dot_online_green else R.drawable.dot_offline_gray
                    )

                    tvName.text = node.name
                    tvName.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_white))

                    val lastMsg = item.lastMessage
                    if (lastMsg != null) {
                        tvTime.text = lastMsg.timestamp
                        tvLastMessage.text = "\"${lastMsg.text}\""
                        bindPriority(lastMsg.priority)
                    } else {
                        tvTime.text = "--:--"
                        tvLastMessage.text = if (node.isYou) "Your local device" else "Connected via ${node.hops} hop(s)"
                        tvPriorityBadge.visibility = View.GONE
                    }
                }
            }
        }

        private fun bindPriority(priority: Priority) {
            tvPriorityBadge.visibility = View.VISIBLE
            tvPriorityBadge.text = priority.name
            when (priority) {
                Priority.SOS -> {
                    tvPriorityBadge.setBackgroundResource(R.drawable.badge_priority_sos)
                    tvPriorityBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.alert_red))
                }
                Priority.EVAC -> {
                    tvPriorityBadge.setBackgroundResource(R.drawable.badge_priority_evac)
                    tvPriorityBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.alert_amber))
                }
                Priority.STATUS -> {
                    tvPriorityBadge.setBackgroundResource(R.drawable.badge_priority_status)
                    tvPriorityBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.accent_green))
                }
                Priority.BULLETIN -> {
                    tvPriorityBadge.setBackgroundResource(R.drawable.badge_priority_bulletin)
                    tvPriorityBadge.setTextColor(android.graphics.Color.parseColor("#9B59B6"))
                }
            }
        }
    }
}