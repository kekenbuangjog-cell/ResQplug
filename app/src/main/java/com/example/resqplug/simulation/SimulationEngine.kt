package com.example.resqplug.simulation

import android.graphics.Color
import kotlin.random.Random

class SimulationEngine(private val onTick: () -> Unit) {

    private val messages = mutableListOf<ChatMessage>()
    private val nodes = mutableListOf<MeshNode>()
    private var tickCount = 0

    private val nodeColors = intArrayOf(
        Color.parseColor("#3498DB"),
        Color.parseColor("#E74C3C"),
        Color.parseColor("#9B59B6"),
        Color.parseColor("#F39C12")
    )

    private val nodeNames = listOf("Node-A", "Node-B", "Node-C", "Node-D")

    private val incomingPresets = listOf(
        Priority.STATUS to "All clear here",
        Priority.EVAC to "Flood rising, evacuate now",
        Priority.STATUS to "Online and ready",
        Priority.SOS to "Need medical assistance",
        Priority.STATUS to "Connected and monitoring",
        Priority.EVAC to "Water level rising fast",
        Priority.STATUS to "Standing by for orders"
    )

    fun tick() {
        tickCount++

        // Spawn nodes every ~3s (24 ticks at 8 FPS)
        if (tickCount % 24 == 0 && nodes.size < 4) {
            spawnNode()
        }

        // Random incoming messages every ~5s (40 ticks)
        if (tickCount % 40 == 0 && nodes.isNotEmpty() && Random.nextFloat() < 0.4f) {
            receiveMessage()
        }

        onTick()
    }

    private fun spawnNode() {
        val index = nodes.size
        val name = nodeNames[index]
        val color = nodeColors[index]
        nodes.add(MeshNode("node_$index", name, color))

        // System message: node joined
        messages.add(
            ChatMessage(
                id = "sys_${System.currentTimeMillis()}",
                sender = name,
                text = "[$name joined the mesh]",
                priority = Priority.STATUS,
                isSent = false,
                isSystem = true,
                timestamp = getCurrentTime(),
                nodeColor = color
            )
        )
        onTick()
    }

    private fun receiveMessage() {
        val node = nodes.random()
        val (priority, text) = incomingPresets.random()

        messages.add(
            ChatMessage(
                id = "msg_${System.currentTimeMillis()}",
                sender = node.name,
                text = text,
                priority = priority,
                isSent = false,
                isSystem = false,
                timestamp = getCurrentTime(),
                nodeColor = node.color
            )
        )
        onTick()
    }

    fun sendMessage(text: String, priority: Priority): ChatMessage {
        val msg = ChatMessage(
            id = "msg_${System.currentTimeMillis()}",
            sender = "You",
            text = text,
            priority = priority,
            isSent = true,
            isSystem = false,
            timestamp = getCurrentTime(),
            nodeColor = Color.parseColor("#00FF88")
        )
        messages.add(msg)
        return msg
    }

    fun getMessages(): List<ChatMessage> = messages.toList()

    fun getNodeCount(): Int = nodes.size + 1 // +1 for "You"

    private fun getCurrentTime(): String {
        val cal = java.util.Calendar.getInstance()
        return "%02d:%02d".format(
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE)
        )
    }
}
