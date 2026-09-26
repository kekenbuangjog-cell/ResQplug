package com.example.resqplug.simulation

import android.graphics.Color
import android.util.Log
import com.example.resqplug.hardware.ResQPlugHardwareBridge
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class SimulationEngine(private val onTick: () -> Unit) {

    private val db = FirebaseFirestore.getInstance()

    private val messages = mutableListOf<ChatMessage>()
    private val nodes = mutableListOf<MeshNode>()
    private val sosIncidents = mutableListOf<SosIncident>()

    // Local-first radio peer directory and message deduplication
    private val localLoraNodes = ConcurrentHashMap<String, MeshNode>()
    private val seenMessageIds = Collections.synchronizedSet(HashSet<String>())
    private val radioPacketListener = { packet: String -> handleInboundRadioPacket(packet) }

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

        ResQPlugHardwareBridge.addPacketListener(radioPacketListener)
        listenToActiveDevices()
        listenToMeshMessages()
        listenToSosIncidents()
        listenToOfficialBulletin()
        listenToChannelSilence()
    }

    fun stop() {
        ResQPlugHardwareBridge.removePacketListener(radioPacketListener)
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
        // Pure event-driven network: No polling or unthrottled UI thread invalidations.
    }

    private var cachedDeviceDocuments = listOf<com.google.firebase.firestore.DocumentSnapshot>()

    private fun listenToActiveDevices() {
        devicesListener = db.collection("active_devices")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("SimulationEngine", "Active devices listener error", error)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    cachedDeviceDocuments = snapshots.documents
                    rebuildMergedNodes()
                }
            }
    }

    fun recheckFreshness() {
        rebuildMergedNodes()
    }

    private fun rebuildMergedNodes() {
        val now = System.currentTimeMillis()
        val staleThresholdMs = 90_000L // 90 seconds timeout
        val peerMap = mutableMapOf<String, MeshNode>()

        // 1. Process Cloud Firestore snapshots first
        for (doc in cachedDeviceDocuments) {
            val docNodeId = doc.getString("nodeId") ?: doc.id
            val docName = doc.getString("userName") ?: docNodeId.takeLast(6).uppercase()
            val docRoleStr = doc.getString("role") ?: "CITIZEN"
            val docRole = try { UserRole.valueOf(docRoleStr) } catch (e: Exception) { UserRole.CITIZEN }
            val status = doc.getString("status") ?: "inactive"

            val lastSeenTs = doc.getTimestamp("last_seen")?.toDate()?.time
            val isStale = if (lastSeenTs != null) {
                (now - lastSeenTs) > staleThresholdMs
            } else {
                false
            }

            val isOnline = (status == "active") && !isStale
            val isSelf = (docNodeId == userNode.id)
            val color = getNodeColorForRole(docRole)

            if (isSelf) {
                if (docName.isNotEmpty() && docName != "UNKNOWN") {
                    userNode.name = docName
                }
                userNode.role = docRole
            } else {
                peerMap[docNodeId] = MeshNode(
                    id = docNodeId,
                    name = if (docName.isNotEmpty()) docName else docNodeId.takeLast(6).uppercase(),
                    color = color,
                    isOnline = isOnline,
                    hops = 1,
                    isYou = false,
                    role = docRole,
                    rssi = null,
                    transport = "CLOUD",
                    lastSeenMs = lastSeenTs ?: now
                )
            }
        }

        // 2. Overlay live LoRa peers (Local RF takes precedence for link metrics!)
        for ((nodeId, loraNode) in localLoraNodes) {
            if (nodeId == userNode.id) continue

            val isStale = (now - loraNode.lastSeenMs) > staleThresholdMs
            val online = !isStale

            val existingCloud = peerMap[nodeId]
            if (existingCloud != null) {
                peerMap[nodeId] = existingCloud.copy(
                    isOnline = online || existingCloud.isOnline,
                    hops = loraNode.hops,
                    rssi = loraNode.rssi,
                    transport = if (online && existingCloud.isOnline) "HYBRID" else if (online) "LORA" else "CLOUD",
                    lastSeenMs = maxOf(loraNode.lastSeenMs, existingCloud.lastSeenMs)
                )
            } else {
                peerMap[nodeId] = loraNode.copy(isOnline = online)
            }
        }

        nodes.clear()
        nodes.addAll(peerMap.values)
        onTick()
    }

    private fun handleInboundRadioPacket(rawPacket: String) {
        if (!rawPacket.startsWith("[RX:")) return

        try {
            var rssi: Int? = null
            val rssiMatch = Regex("""RSSI:(-?\d+)""").find(rawPacket)
            if (rssiMatch != null) {
                rssi = rssiMatch.groupValues[1].toIntOrNull()
            }

            val dataIndex = rawPacket.indexOf("DATA:")
            if (dataIndex < 0) return
            val lastBracket = rawPacket.lastIndexOf(']')
            if (lastBracket <= dataIndex + 5) return
            val payload = rawPacket.substring(dataIndex + 5, lastBracket).trim().removePrefix("[").removeSuffix("]")

            val parts = payload.split('|')
            if (parts.isEmpty()) return

            val tagAndId = parts[0]
            val tag = tagAndId.substringBefore(':')
            val id = tagAndId.substringAfter(':')

            when (tag) {
                "BCN" -> {
                    // [BCN:ID|NAME|ROLE|HOPS]
                    val name = if (parts.size > 1) parts[1] else id.takeLast(6).uppercase()
                    val roleStr = if (parts.size > 2) parts[2] else "CITIZEN"
                    val role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.CITIZEN }
                    val hops = if (parts.size > 3) parts[3].toIntOrNull() ?: 1 else 1

                    if (id != userNode.id && id.isNotEmpty()) {
                        localLoraNodes[id] = MeshNode(
                            id = id,
                            name = name,
                            color = getNodeColorForRole(role),
                            isOnline = true,
                            hops = hops,
                            isYou = false,
                            role = role,
                            rssi = rssi,
                            transport = "LORA",
                            lastSeenMs = System.currentTimeMillis()
                        )
                        rebuildMergedNodes()
                    }
                }
                "MSG" -> {
                    // [MSG:ID|FROM_ID|FROM_NAME|PRIORITY|HOPS|TEXT]
                    val msgId = id
                    val senderId = if (parts.size > 1) parts[1] else ""
                    val senderName = if (parts.size > 2) parts[2] else "Unknown"
                    val prioStr = if (parts.size > 3) parts[3] else "STATUS"
                    val priority = try { Priority.valueOf(prioStr) } catch (e: Exception) { Priority.STATUS }
                    val hops = if (parts.size > 4) parts[4].toIntOrNull() ?: 1 else 1
                    val text = if (parts.size > 5) parts.drop(5).joinToString("|") else ""

                    if (senderId == userNode.id) return
                    if (seenMessageIds.contains(msgId) || messages.any { it.id == msgId }) return
                    seenMessageIds.add(msgId)

                    val chatMsg = ChatMessage(
                        id = msgId,
                        sender = senderName,
                        text = text,
                        priority = priority,
                        isSent = false,
                        isSystem = false,
                        timestamp = getCurrentTime(),
                        nodeColor = getNodeColorForRole(UserRole.CITIZEN),
                        role = UserRole.CITIZEN,
                        senderId = senderId,
                        channel = "BROADCAST",
                        recipientId = null,
                        recipientName = null
                    )
                    messages.add(chatMsg)

                    if (senderId.isNotEmpty()) {
                        localLoraNodes[senderId] = MeshNode(
                            id = senderId,
                            name = senderName,
                            color = getNodeColorForRole(UserRole.CITIZEN),
                            isOnline = true,
                            hops = hops,
                            isYou = false,
                            role = UserRole.CITIZEN,
                            rssi = rssi,
                            transport = "LORA",
                            lastSeenMs = System.currentTimeMillis()
                        )
                        rebuildMergedNodes()
                    } else {
                        onTick()
                    }
                }
                "SOS" -> {
                    // [SOS:ID|CITIZEN_ID|CITIZEN_NAME|HOPS|TEXT]
                    val msgId = id
                    val citizenId = if (parts.size > 1) parts[1] else ""
                    val citizenName = if (parts.size > 2) parts[2] else "Evacuee in Distress"
                    val hops = if (parts.size > 3) parts[3].toIntOrNull() ?: 1 else 1
                    val text = if (parts.size > 4) parts.drop(4).joinToString("|") else ""

                    if (citizenId == userNode.id) return
                    if (seenMessageIds.contains(msgId) || messages.any { it.id == msgId }) return
                    seenMessageIds.add(msgId)

                    val incidentId = "sos_${citizenId.ifEmpty { msgId }}"
                    val incident = SosIncident(
                        id = incidentId,
                        citizenName = citizenName,
                        distressMessage = text,
                        timestamp = getCurrentTime(),
                        hops = hops,
                        triageStatus = TriageStatus.OPEN,
                        dispatchedResponder = null
                    )
                    val existingIdx = sosIncidents.indexOfFirst { it.id == incidentId }
                    if (existingIdx >= 0) {
                        sosIncidents[existingIdx] = incident
                    } else {
                        sosIncidents.add(0, incident)
                    }

                    val chatMsg = ChatMessage(
                        id = msgId,
                        sender = citizenName,
                        text = "🚨 SOS DISTRESS: $text",
                        priority = Priority.SOS,
                        isSent = false,
                        isSystem = false,
                        timestamp = getCurrentTime(),
                        nodeColor = Color.parseColor("#E74C3C"),
                        role = UserRole.CITIZEN,
                        senderId = citizenId,
                        channel = "BROADCAST",
                        recipientId = null,
                        recipientName = null
                    )
                    messages.add(chatMsg)

                    if (citizenId.isNotEmpty()) {
                        localLoraNodes[citizenId] = MeshNode(
                            id = citizenId,
                            name = citizenName,
                            color = Color.parseColor("#E74C3C"),
                            isOnline = true,
                            hops = hops,
                            isYou = false,
                            role = UserRole.CITIZEN,
                            rssi = rssi,
                            transport = "LORA",
                            lastSeenMs = System.currentTimeMillis()
                        )
                        rebuildMergedNodes()
                    } else {
                        onTick()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SimulationEngine", "Error handling inbound radio packet: $rawPacket", e)
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
                    val firestoreIds = msgList.map { it.id }.toSet()
                    seenMessageIds.addAll(firestoreIds)
                    val localOnly = messages.filter { !firestoreIds.contains(it.id) }
                    messages.clear()
                    messages.addAll(msgList)
                    messages.addAll(localOnly)
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
        seenMessageIds.add(msgId)
        messages.add(localMsg)
        onTick()

        // 0. Format and transmit over live LoRa radio via ResQPlugHardwareBridge
        val loraPacket = if (priority == Priority.SOS) {
            "[SOS:$msgId|${userNode.id}|$senderDisplayName|1|$text]"
        } else {
            "[MSG:$msgId|${userNode.id}|$senderDisplayName|${priority.name}|1|$text]"
        }
        ResQPlugHardwareBridge.sendLoraPacket(loraPacket)

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

    fun getNodeCount(): Int {
        val remoteOnline = nodes.count { it.isOnline }
        val localOnline = if (userNode.isOnline) 1 else 0
        return remoteOnline + localOnline
    }

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
