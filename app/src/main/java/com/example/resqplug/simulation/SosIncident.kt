package com.example.resqplug.simulation

enum class TriageStatus(val label: String) {
    OPEN("🚨 OPEN - AWAITING AID"),
    DISPATCHED("🚑 RESCUER EN ROUTE"),
    RESOLVED("✅ RESOLVED")
}

data class SosIncident(
    val id: String,
    val citizenName: String,
    val distressMessage: String,
    val timestamp: String,
    val hops: Int,
    var triageStatus: TriageStatus = TriageStatus.OPEN,
    var dispatchedResponder: String? = null
)
