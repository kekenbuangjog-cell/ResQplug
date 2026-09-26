package com.example.resqplug.activity7

/**
 * Model-View-Presenter (MVP) Contract for Screen 2: LED & Hardware Control Dashboard
 */
interface LedContract {

    interface View {
        fun showLoading(isLoading: Boolean)
        fun updateLedStatus(actualStatus: String, commandRequested: String)
        fun updateDevicePresence(isOnline: Boolean, lastSeenFormatted: String)
        fun updateLoraStatus(loraStatus: String)
        fun updateTransportMode(transport: String)
        fun showUserAuth(userEmail: String, isAnonymous: Boolean)
        fun showStatusMessage(message: String)
        fun showError(errorMessage: String)
        fun onLoggedOut()
    }

    interface Presenter {
        fun attachView(view: View)
        fun detachView()
        fun requestLedOn()
        fun requestLedOff()
        fun toggleLed()
        fun simulateOffline(enable: Boolean)
        fun checkHeartbeat()
        fun logOut()
    }
}
