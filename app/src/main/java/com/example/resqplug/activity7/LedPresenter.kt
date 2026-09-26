package com.example.resqplug.activity7

import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Presenter layer for Screen 2: LED & Hardware Control Dashboard (MVP Pattern).
 * Mediates between LedActivity (View), HardwareSessionManager (Direct Hardware),
 * and LedRepository (Firestore Cloud Sync).
 */
class LedPresenter(
    private val repository: LedRepository = LedRepository()
) : LedContract.Presenter {

    private var view: LedContract.View? = null
    private var currentData: LedData = LedData()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val heartbeatIntervalMs = 3000L
    private val heartbeatTimeoutThresholdMs = 15000L

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            checkHeartbeat()
            mainHandler.postDelayed(this, heartbeatIntervalMs)
        }
    }

    override fun attachView(view: LedContract.View) {
        this.view = view
        view.showLoading(true)

        val email = repository.getCurrentUserEmail() ?: "operator@resqplug.local"
        this.view?.showUserAuth(email, false)

        // 1. Register live hardware telemetry listener (Bluetooth / USB)
        HardwareSessionManager.registerTelemetryListener(
            onTelemetry = { rawLine ->
                handleHardwareTelemetry(rawLine)
            },
            onDisconnect = {
                this.view?.updateDevicePresence(false, "Disconnected")
                this.view?.showStatusMessage("Physical hardware link lost")
            }
        )

        // Send initial status query to hardware
        HardwareSessionManager.sendCommand("STATUS")

        // 2. Start real-time Firestore sync
        repository.startListening(
            onDataReceived = { data ->
                this.currentData = data
                this.view?.showLoading(false)
                val activeEmail = if (data.operatorEmail.isNotBlank()) data.operatorEmail else email
                this.view?.showUserAuth(activeEmail, false)
                this.view?.updateLedStatus(data.actualStatus, data.command)
                this.view?.updateLoraStatus(data.loraStatus)
                this.view?.updateTransportMode(data.transport)
                evaluateDevicePresence(data)
            },
            onError = { error ->
                this.view?.showLoading(false)
                // If physical hardware is connected, don't block the screen with Firestore errors
                if (!HardwareSessionManager.isRealHardwareConnected()) {
                    this.view?.showError("Sync Error: $error")
                }
            }
        )

        // 3. Start periodic heartbeat watchdog
        mainHandler.post(heartbeatRunnable)
    }

    override fun detachView() {
        mainHandler.removeCallbacks(heartbeatRunnable)
        HardwareSessionManager.unregisterTelemetryListener()
        repository.stopListening()
        this.view = null
    }

    override fun logOut() {
        view?.showLoading(true)
        HardwareSessionManager.disconnect()
        repository.signOut {
            view?.showLoading(false)
            view?.onLoggedOut()
        }
    }

    override fun requestLedOn() {
        view?.showLoading(true)
        view?.showStatusMessage("Sending command: TURN ON...")

        // Direct hardware transmission over Bluetooth / USB
        val hwSent = HardwareSessionManager.sendCommand("LED:ON")
        if (hwSent) {
            view?.updateLedStatus("ON", "ON")
        }

        // Cloud sync to Firestore
        repository.sendCommand(
            targetCommand = "ON",
            onSuccess = {
                view?.showLoading(false)
                view?.showStatusMessage("Command [ON] acknowledged by ESP32!")
            },
            onFailure = { error ->
                view?.showLoading(false)
                if (hwSent) {
                    view?.showStatusMessage("Command [ON] sent to hardware!")
                } else {
                    view?.showError("Command Failed: $error")
                }
            }
        )
    }

    override fun requestLedOff() {
        view?.showLoading(true)
        view?.showStatusMessage("Sending command: TURN OFF...")

        // Direct hardware transmission over Bluetooth / USB
        val hwSent = HardwareSessionManager.sendCommand("LED:OFF")
        if (hwSent) {
            view?.updateLedStatus("OFF", "OFF")
        }

        // Cloud sync to Firestore
        repository.sendCommand(
            targetCommand = "OFF",
            onSuccess = {
                view?.showLoading(false)
                view?.showStatusMessage("Command [OFF] acknowledged by ESP32!")
            },
            onFailure = { error ->
                view?.showLoading(false)
                if (hwSent) {
                    view?.showStatusMessage("Command [OFF] sent to hardware!")
                } else {
                    view?.showError("Command Failed: $error")
                }
            }
        )
    }

    override fun toggleLed() {
        if (currentData.actualStatus.equals("ON", ignoreCase = true)) {
            requestLedOff()
        } else {
            requestLedOn()
        }
    }

    override fun simulateOffline(enable: Boolean) {
        val newState = !enable
        view?.showStatusMessage(if (enable) "Simulating offline disconnect..." else "Restoring online presence...")
        repository.setDevicePresence(newState) {
            checkHeartbeat()
        }
    }

    override fun checkHeartbeat() {
        // Query hardware for status if actively connected
        if (HardwareSessionManager.isRealHardwareConnected()) {
            HardwareSessionManager.sendCommand("STATUS")
        }
        evaluateDevicePresence(currentData)
    }

    private fun handleHardwareTelemetry(rawLine: String) {
        val upper = rawLine.uppercase()

        if (upper.contains("ACTUAL_LED:ON") || upper.contains("LED:ON")) {
            view?.updateLedStatus("ON", "ON")
        } else if (upper.contains("ACTUAL_LED:OFF") || upper.contains("LED:OFF")) {
            view?.updateLedStatus("OFF", "OFF")
        }

        if (upper.contains("LORA:ONLINE")) {
            view?.updateLoraStatus("ONLINE_433MHZ")
        } else if (upper.contains("LORA:OFFLINE")) {
            view?.updateLoraStatus("OFFLINE")
        }

        // Telemetry received directly from hardware proves it is actively online
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val formattedTime = timeFormat.format(Date())
        view?.updateDevicePresence(true, "$formattedTime (Direct Link)")
    }

    private fun evaluateDevicePresence(data: LedData) {
        // If direct hardware link is live, prioritize direct link presence
        if (HardwareSessionManager.isRealHardwareConnected()) {
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val formattedTime = timeFormat.format(Date())
            view?.updateDevicePresence(true, "$formattedTime (Direct Link)")
            return
        }

        val now = System.currentTimeMillis()
        val diff = now - data.lastSeen
        val isExplicitOffline = data.deviceStatus.equals("offline", ignoreCase = true)
        val isTimedOut = (data.lastSeen > 0L && diff > heartbeatTimeoutThresholdMs)

        val isOnline = !isExplicitOffline && !isTimedOut
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val formattedLastSeen = if (data.lastSeen > 0L) {
            "${timeFormat.format(Date(data.lastSeen))} (${diff / 1000}s ago)"
        } else {
            "Never"
        }

        view?.updateDevicePresence(isOnline, formattedLastSeen)
    }
}
