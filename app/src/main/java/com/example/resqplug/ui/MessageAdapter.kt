package com.example.resqplug.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.resqplug.R
import com.example.resqplug.simulation.ChatMessage
import com.example.resqplug.simulation.Priority

class MessageAdapter(private var messages: List<ChatMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val VIEW_TYPE_SYSTEM = 3
    }

    override fun getItemViewType(position: Int): Int {
        val msg = messages[position]
        return when {
            msg.isSystem -> VIEW_TYPE_SYSTEM
            msg.isSent -> VIEW_TYPE_SENT
            else -> VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val view = inflater.inflate(R.layout.item_message_sent, parent, false)
                SentViewHolder(view)
            }
            VIEW_TYPE_RECEIVED -> {
                val view = inflater.inflate(R.layout.item_message_received, parent, false)
                ReceivedViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_message_system, parent, false)
                SystemViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        when (holder) {
            is SentViewHolder -> holder.bind(msg)
            is ReceivedViewHolder -> holder.bind(msg)
            is SystemViewHolder -> holder.bind(msg)
        }
    }

    override fun getItemCount(): Int = messages.size

    fun updateMessages(newMessages: List<ChatMessage>) {
        this.messages = newMessages
        notifyDataSetChanged()
    }

    class SentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPriority: TextView = itemView.findViewById(R.id.tvPriority)
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)

        fun bind(msg: ChatMessage) {
            tvPriority.text = "[ ${msg.priority.name} ]"
            tvPriority.setTextColor(getPriorityColor(itemView, msg.priority))
            tvMessage.text = "\"${msg.text}\""
            tvTime.text = msg.timestamp
        }
    }

    class ReceivedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDot: TextView = itemView.findViewById(R.id.tvDot)
        private val tvSender: TextView = itemView.findViewById(R.id.tvSender)
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)

        fun bind(msg: ChatMessage) {
            tvDot.setTextColor(msg.nodeColor)
            tvSender.text = msg.sender
            tvSender.setTextColor(msg.nodeColor)
            tvMessage.text = "\"${msg.text}\""
            tvTime.text = msg.timestamp
        }
    }

    class SystemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSystemMsg: TextView = itemView.findViewById(R.id.tvSystemMsg)

        fun bind(msg: ChatMessage) {
            tvSystemMsg.text = msg.text
        }
    }
}

private fun getPriorityColor(view: View, priority: Priority): Int {
    return when (priority) {
        Priority.SOS -> ContextCompat.getColor(view.context, R.color.alert_red)
        Priority.EVAC -> ContextCompat.getColor(view.context, R.color.alert_amber)
        Priority.STATUS -> ContextCompat.getColor(view.context, R.color.accent_green)
    }
}
