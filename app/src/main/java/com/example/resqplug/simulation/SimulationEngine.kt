package com.example.resqplug.simulation

import android.graphics.Color
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SimulationEngine(private val onTick: () -> Unit) {

    private val db = FirebaseFirestore.getInstance()

    private val messages = mutableListOf<ChatMessage>()
    private val nodes = mutableListOf<MeshNode>()
    private val sosIncidents = mutableListOf<SosIncident>()

    private var devicesListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null
    private var sosListener: ListenerRegistration? = null
    private var bulletinListener: ListenerRegistration? = null
    private var silenceListener: ListenerRegistration? = null

    var isChannelSilenceActive: Boolean = false
        private set

    // Author, Content, Timestamp
    var latestCommunityBulletin: Triple<String, String, String>? = Triple(
        "Brgy. Disaster Desk",
        "All sectors: Monitoring emergency communication net. Stay safe and report critical status.",
        "JUST NOW"
    )
        private set

    var userNode = MeshNode(
        id = "local_user",
        name = "You",
        color = Color.parseColor("#00FF88"),
        isOnline = true,
        hops = 0,
        isYou = true
    )

    fun setUserNodeId(nodeId: String) {
        if (nodeId.isNotEmpty()) {
            userNode = userNode.copy(id = nodeId)
            onTick()
        }
    }

    fun setUserName(name: String) {
        if (name.isNotEmpty()) {
            userNode.name = name
            onTick()
        }
    }

    fun setUserRole(role: UserRole) {
        userNode.role = role
        onTick()
    }

    fun start(nodeId: String, name: String, role: UserRole) {
        userNode = userNode.copy(id = nodeId)
        if (name.isNotEmpty()) userNode.name = name
        userNode.role = role

        stop() // Clean up existing listeners if re-starting

        listenToActiveDevices()
        listenToMeshMessages()
        listenToSosIncidents()
        listenToOfficialBulletin()
        listenToChannelSilence()
    }

    fun stop() {
        devicesListener?.remove()
        devicesListener = null
        messagesListener?.remove()
        messagesListener = null
        sosListener?.remove()
        sosListener = null
        bulletinListener?.remove()
        bulletinListener = null
        silenceListener?.remove()
        silenceListener = null
    }

    fun tick() {
        // Real-time network: No fake bot generation.
        // Tick is preserved for frame-rate synchronization with UI animations.
        onTick()
    }

    private fun listenToActiveDevices() {
        devicesListener = db.collection("active_devices")
            .whereEqualTo("status", "active")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("SimulationEngine", "Active devices listener error", error)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val peerList = mutableListOf<MeshNode>()
                    for (doc in snapshots.documents) {
                        val docNodeId = doc.getString("nodeId") ?: doc.id
                        val docName = doc.getString("userName") ?: docNodeId.takeLast(6).uppercase()
                        val docRoleStr = doc.getString("role") ?: "CITIZEN"
                        val docRole = try { UserRole.valueOf(docRoleStr) } catch (e: Exception) { UserRole.CITIZEN }

                        val isSelf = (docNodeId == userNode.id)
                        val color = getNodeColorForRole(docRole)

                        if (isSelf) {
                            if (docName.isNotEmpty() && docName != "UNKNOWN") {
                                userNode.name = docName
                            }
                            userNode.role = docRole
                        } else {
                            peerList.add(
                                MeshNode(
                                    id = docNodeId,
                                    name = if (docName.isNotEmpty()) docName else docNodeId.takeLast(6).uppercase(),
                                    color = color,
                                    isOnline = true,
                                    hops = 1,
                                    isYou = false,
                                    role = docRole
                                )
                            )
                        }
                    }

                    nodes.clear()
                    nodes.addAll(peerList)
                    onTick()
                }
            }
    }

    private fun listenToMeshMessages() {
        messagesListener = db.collection("mesh_messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(120)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("SimulationEngine", "Mesh messages listener error", error)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val msgList = mutableListOf<ChatMessage>()
                    for (doc in snapshots.documents) {
                        val msgId = doc.id
                        val senderId = doc.getString("senderId") ?: ""
                        val senderName = doc.getString("senderName") ?: "Unknown"
                        val senderRoleStr = doc.getString("senderRole") ?: "CITIZEN"
                        val senderRole = try { UserRole.valueOf(senderRoleStr) } catch (e: Exception) { UserRole.CITIZEN }
                        val text = doc.getString("text") ?: ""
                        val priorityStr = doc.getString("priority") ?: "STATUS"
                        val priority = try { Priority.valueOf(priorityStr) } catch (e: Exception) { Priority.STATUS }
                        val channel = doc.getString("channel") ?: "BROADCAST"
                        val recipientId = doc.getString("recipientId")
                        val recipientName = doc.getString("recipientName")
                        val isSystem = doc.getBoolean("isSystem") ?: false

                        val timestampStr = doc.getString("timeStr")
                            ?: doc.getTimestamp("createdAt")?.let { formatTimestamp(it) }
                            ?: "--:--"

                        val isSent = (senderId == userNode.id || (senderId.isEmpty() && senderName.equals(userNode.name, ignoreCase = true)))

                        val color = getNodeColorForRole(senderRole)

                        msgList.add(
                            ChatMessage(
                                id = msgId,
                                sender = senderName,
                                text = text,
                                priority = priority,
                                isSent = isSent,
                                isSystem = isSystem,
                                timestamp = timestampStr,
                                nodeColor = color,
                                role = senderRole,
                                senderId = senderId,
                                channel = channel,
                                recipientId = recipientId,
                                recipientName = recipientName
                            )
                        )
                    }

                    messages.clear()
                    messages.addAll(msgList)
                    onTick()
                }
            }
    }

    private fun listenToSosIncidents() {
        sosListener = db.collection("sos_incidents")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limitToLast(60)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("SimulationEngine", "SOS incidents listener error", error)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val incidentList = mutableListOf<SosIncident>()
                    for (doc in snapshots.documents) {
                        val incidentId = doc.id
                        val citizenName = doc.getString("citizenName") ?: "Unknown Citizen"
                        val distressMessage = doc.getString("distressMessage") ?: "Emergency Distress Signal"
                        val timeStr = doc.getString("timestamp")
                            ?: doc.getTimestamp("createdAt")?.let { formatTimestamp(it) }
                            ?: "--:--"
                        val hops = doc.getLong("hops")?.toInt() ?: 1
                        val statusStr = doc.getString("triageStatus") ?: "OPEN"
                        val triageStatus = try { TriageStatus.valueOf(statusStr) } catch (e: Exception) { TriageStatus.OPEN }
                        val dispatchedResponder = doc.getString("dispatchedResponder")

                        incidentList.add(
                            SosIncident(
                                id = incidentId,
                                citizenName = citizenName,
                                distressMessage = distressMessage,
                                timestamp = timeStr,
                                hops = hops,
                                triageStatus = triageStatus,
                                dispatchedResponder = dispatchedResponder
                            )
                        )
                    }

                    sosIncidents.clear()
                    sosIncidents.addAll(incidentList)
                    onTick()
                }
            }
    }

    private fun listenToOfficialBulletin() {
        bulletinListener = db.collection("official_bulletins")
            .document("latest")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("SimulationEngine", "Official bulletin listener error", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val author = snapshot.getString("author") ?: "Brgy. Disaster Desk"
                    val text = snapshot.getString("text") ?: "All sectors: Monitoring emergency channels."
                    val timeStr = snapshot.getString("timestamp") ?: "JUST NOW"
                    latestCommunityBulletin = Triple(author, text, timeStr)
                    onTick()
                }
            }
    }

    private fun listenToChannelSilence() {
        silenceListener = db.collection("system_config")
            .document("channel_silence")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("SimulationEngine", "Channel silence listener error", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    isChannelSilenceActive = snapshot.getBoolean("isActive") ?: false
                    onTick()
                }
            }
    }

    fun sendMessage(
        text: String,
        priority: Priority,
        channel: String = "BROADCAST",
        recipientId: String? = null,
        recipientName: String? = null
    ): ChatMessage {
        val senderDisplayName = if (userNode.name.isNotEmpty()) userNode.name else "You"
        val timeStr = getCurrentTime()
        val msgId = "msg_${System.currentTimeMillis()}_${userNode.id.takeLast(4)}"

        val localMsg = ChatMessage(
            id = msgId,
            sender = senderDisplayName,
            text = text,
            priority = priority,
            isSent = true,
            isSystem = false,
            timestamp = timeStr,
            nodeColor = userNode.color,
            role = userNode.role,
            senderId = userNode.id,
            channel = channel,
            recipientId = recipientId,
            recipientName = recipientName
        )

        // Optimistically add to local message list immediately
        messages.add(localMsg)
        onTick()

        // 1. Write to Firestore mesh_messages
        val messageDoc = hashMapOf(
            "messageId" to msgId,
            "senderId" to userNode.id,
            "senderName" to senderDisplayName,
            "senderRole" to userNode.role.name,
            "text" to text,
            "priority" to priority.name,
            "channel" to channel,
            "recipientId" to recipientId,
            "recipientName" to recipientName,
            "isSystem" to false,
            "timeStr" to timeStr,
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("mesh_messages").document(msgId)
            .set(messageDoc, SetOptions.merge())
            .addOnFailureListener { Log.e("SimulationEngine", "Failed to send mesh message", it) }

        // 2. Track SOS Incidents in Firestore
        if (priority == Priority.SOS) {
            val incidentId = "sos_${userNode.id}"
            val incidentDoc = hashMapOf(
                "incidentId" to incidentId,
                "citizenNodeId" to userNode.id,
                "citizenName" to senderDisplayName,
                "distressMessage" to text,
                "timestamp" to timeStr,
                "hops" to 0,
                "triageStatus" to TriageStatus.OPEN.name,
                "dispatchedResponder" to null,
                "createdAt" to FieldValue.serverTimestamp()
            )

            db.collection("sos_incidents").document(incidentId)
                .set(incidentDoc, SetOptions.merge())
                .addOnFailureListener { Log.e("SimulationEngine", "Failed to publish SOS incident", it) }
        }

        // 3. Track Bulletin if posted via Commander
        if (priority == Priority.BULLETIN) {
            postOfficialBulletin(senderDisplayName, text)
        }

        return localMsg
    }

    fun triageIncident(incidentId: String, status: TriageStatus, responderName: String) {
        val targetIncident = sosIncidents.firstOrNull { it.id == incidentId }
        val victimName = targetIncident?.citizenName ?: "Citizen"

        // Update local incident
        targetIncident?.triageStatus = status
        targetIncident?.dispatchedResponder = if (status == TriageStatus.OPEN) null else responderName

        // 1. Update Firestore sos_incidents doc
        val updates = hashMapOf<String, Any?>(
            "triageStatus" to status.name,
            "dispatchedResponder" to (if (status == TriageStatus.OPEN) null else responderName),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("sos_incidents").document(incidentId)
            .set(updates, SetOptions.merge())
            .addOnFailureListener { Log.e("SimulationEngine", "Failed to update SOS triage in Firestore", it) }

        // 2. Broadcast ACK message over mesh
        val ackText = when (status) {
            TriageStatus.DISPATCHED -> "[DISPATCH ACK: $responderName is EN ROUTE to $victimName]"
            TriageStatus.RESOLVED -> "[RESOLVED: $victimName's emergency resolved by $responderName]"
            TriageStatus.OPEN -> "[RE-OPENED: $victimName's distress status reset to OPEN]"
        }

        val ackMsgId = "ack_${System.currentTimeMillis()}"
        val ackDoc = hashMapOf(
            "messageId" to ackMsgId,
            "senderId" to userNode.id,
            "senderName" to responderName,
            "senderRole" to UserRole.RESPONDER.name,
            "text" to ackText,
            "priority" to Priority.EVAC.name,
            "channel" to "BROADCAST",
            "isSystem" to true,
            "timeStr" to getCurrentTime(),
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("mesh_messages").document(ackMsgId)
            .set(ackDoc, SetOptions.merge())
            .addOnFailureListener { Log.e("SimulationEngine", "Failed to broadcast ACK message", it) }

        onTick()
    }

    fun toggleChannelSilence(): Boolean {
        isChannelSilenceActive = !isChannelSilenceActive
        val newSilenceState = isChannelSilenceActive

        // 1. Update Firestore system_config/channel_silence
        val configDoc = hashMapOf(
            "isActive" to newSilenceState,
            "activatedBy" to userNode.name,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("system_config").document("channel_silence")
            .set(configDoc, SetOptions.merge())
            .addOnFailureListener { Log.e("SimulationEngine", "Failed to toggle silence in Firestore", it) }

        // 2. Broadcast system message
        val silenceText = if (newSilenceState) {
            "[COMMANDER OVERRIDE: MESH SILENCE ACTIVATED. EMERGENCY TRAFFIC ONLY (SOS / EVAC)]"
        } else {
            "[COMMANDER OVERRIDE: MESH SILENCE DEACTIVATED. ROUTINE TRAFFIC RESUMED]"
        }

        val silenceMsgId = "silence_${System.currentTimeMillis()}"
        val silenceDoc = hashMapOf(
            "messageId" to silenceMsgId,
            "senderId" to userNode.id,
            "senderName" to "COMMAND OVERRIDE",
            "senderRole" to UserRole.COMMANDER.name,
            "text" to silenceText,
            "priority" to Priority.EVAC.name,
            "channel" to "BROADCAST",
            "isSystem" to true,
            "timeStr" to getCurrentTime(),
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("mesh_messages").document(silenceMsgId)
            .set(silenceDoc, SetOptions.merge())
            .addOnFailureListener { Log.e("SimulationEngine", "Failed to broadcast silence message", it) }

        onTick()
        return isChannelSilenceActive
    }

    fun broadcastEvacuationOrder(commanderName: String) {
        val evacText = "🚨 MANDATORY EVACUATION ORDER: All high-risk sectors proceed to Banilad Gym immediately. Flooding imminent."
        postOfficialBulletin(commanderName, evacText)
    }

    fun postOfficialBulletin(author: String, text: String) {
        val timeStr = getCurrentTime()
        latestCommunityBulletin = Triple(author, text, timeStr)

        // 1. Write to official_bulletins/latest
        val bulletinDoc = hashMapOf(
            "author" to author,
            "text" to text,
            "timestamp" to timeStr,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("official_bulletins").document("latest")
            .set(bulletinDoc, SetOptions.merge())
            .addOnFailureListener { Log.e("SimulationEngine", "Failed to post official bulletin", it) }

        // 2. Broadcast to mesh_messages
        val msgId = "bulletin_${System.currentTimeMillis()}"
        val messageDoc = hashMapOf(
            "messageId" to msgId,
            "senderId" to userNode.id,
            "senderName" to author,
            "senderRole" to UserRole.COMMANDER.name,
            "text" to text,
            "priority" to Priority.BULLETIN.name,
            "channel" to "BROADCAST",
            "isSystem" to false,
            "timeStr" to timeStr,
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("mesh_messages").document(msgId)
            .set(messageDoc, SetOptions.merge())
            .addOnFailureListener { Log.e("SimulationEngine", "Failed to broadcast bulletin message", it) }

        onTick()
    }

    fun getSosIncidents(): List<SosIncident> = sosIncidents.toList()

    fun getOpenSosCount(): Int = sosIncidents.count { it.triageStatus != TriageStatus.RESOLVED }

    fun getMessages(): List<ChatMessage> = messages.toList()

    fun getAllActiveNodes(): List<MeshNode> {
        val list = mutableListOf<MeshNode>()
        list.add(userNode)
        list.addAll(nodes)
        return list
    }

    fun getNodes(): List<MeshNode> = nodes.toList()

    fun getNodeCount(): Int = nodes.size + 1 // +1 for local user

    fun getLastMessageForNode(senderName: String): ChatMessage? {
        return messages.filter {
            it.sender.equals(senderName, ignoreCase = true) ||
            it.recipientName?.equals(senderName, ignoreCase = true) == true
        }.lastOrNull()
    }

    fun getLatestMessage(): ChatMessage? {
        return messages.filter { !it.isSystem && (it.channel == "BROADCAST" || it.recipientName.isNullOrEmpty()) }.lastOrNull()
            ?: messages.filter { !it.isSystem }.lastOrNull()
    }

    private fun getNodeColorForRole(role: UserRole): Int {
        return when (role) {
            UserRole.CITIZEN -> Color.parseColor("#3498DB") // Blue
            UserRole.RESPONDER -> Color.parseColor("#F39C12") // Amber
            UserRole.COMMANDER -> Color.parseColor("#E74C3C") // Red
        }
    }

    private fun getCurrentTime(): String {
        val cal = java.util.Calendar.getInstance()
        return "%02d:%02d".format(
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE)
        )
    }

    private fun formatTimestamp(timestamp: Timestamp): String {
        return try {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            sdf.format(timestamp.toDate())
        } catch (e: Exception) {
            getCurrentTime()
        }
    }
}
