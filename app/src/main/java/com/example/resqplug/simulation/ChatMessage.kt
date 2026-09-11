package com.example.resqplug.simulation

data class ChatMessage(
    val id: String,
    val sender: String,
    val text: String,
    val priority: Priority,
    val isSent: Boolean,
    val isSystem: Boolean,
    val timestamp: String,
    val nodeColor: Int,
    val role: UserRole = UserRole.CITIZEN,
    val senderId: String = "",
    val channel: String = "BROADCAST",
    val recipientId: String? = null,
    val recipientName: String? = null
)

enum class Priority { SOS, EVAC, STATUS, BULLETIN }
