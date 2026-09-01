package com.example.resqplug.simulation

data class ChatMessage(
    val id: String,
    val sender: String,
    val text: String,
    val priority: Priority,
    val isSent: Boolean,
    val isSystem: Boolean,
    val timestamp: String,
    val nodeColor: Int
)

enum class Priority { SOS, EVAC, STATUS }
