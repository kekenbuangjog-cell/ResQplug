package com.example.resqplug.simulation

enum class UserRole(val displayName: String, val badgeText: String) {
    CITIZEN("Citizen", "[ CITIZEN ]"),
    RESPONDER("First Responder", "[ RESPONDER ]"),
    COMMANDER("Barangay Commander", "[ COMMANDER ]");

    companion object {
        const val CODE_RESPONDER = "RQP-RESCUE-2026"
        const val CODE_COMMANDER = "RQP-CMD-2026"

        fun verifyCode(code: String): UserRole? {
            return when (code.trim().uppercase()) {
                CODE_RESPONDER -> RESPONDER
                CODE_COMMANDER -> COMMANDER
                else -> null
            }
        }
    }
}
