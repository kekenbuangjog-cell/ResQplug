package com.example.resqplug.simulation

data class MeshNode(
    val id: String,
    var name: String,
    val color: Int,
    val isOnline: Boolean = true,
    val hops: Int = 1,
    val isYou: Boolean = false,
    var role: UserRole = UserRole.CITIZEN
)
