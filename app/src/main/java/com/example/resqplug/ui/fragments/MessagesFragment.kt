package com.example.resqplug.ui.fragments

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.resqplug.DashboardActivity
import com.example.resqplug.R
import com.example.resqplug.simulation.ChatMessage
import com.example.resqplug.simulation.MeshNode
import com.example.resqplug.simulation.Priority
import com.example.resqplug.ui.ActiveContactAdapter
import com.example.resqplug.ui.ConversationAdapter
import com.example.resqplug.ui.ConversationItem
import com.example.resqplug.ui.MessageAdapter

class MessagesFragment : Fragment() {

    private lateinit var rvActiveContacts: RecyclerView
    private lateinit var rvConversations: RecyclerView
    private lateinit var tvActiveOnlineCount: TextView

    private lateinit var activeContactAdapter: ActiveContactAdapter
    private lateinit var conversationAdapter: ConversationAdapter

    // Active Chat Dialog References (if open)
    private var activeChatDialog: Dialog? = null
    private var dialogMessageAdapter: MessageAdapter? = null
    private var currentChatRecipientId: String? = null
    private var currentChatRecipientName: String? = null // null means public broadcast

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_messages, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvActiveContacts = view.findViewById(R.id.rvActiveContacts)
        rvConversations = view.findViewById(R.id.rvConversations)
        tvActiveOnlineCount = view.findViewById(R.id.tvActiveOnlineCount)

        setupActiveContactsRecycler()
        setupConversationsRecycler()

        refreshMeshComms()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activeChatDialog?.dismiss()
        activeChatDialog = null
    }

    private fun setupActiveContactsRecycler() {
        rvActiveContacts.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false
        )

        activeContactAdapter = ActiveContactAdapter(emptyList()) { node ->
            openChatForNode(node)
        }
        rvActiveContacts.adapter = activeContactAdapter
    }

    private fun setupConversationsRecycler() {
        rvConversations.layoutManager = LinearLayoutManager(requireContext())

        conversationAdapter = ConversationAdapter(emptyList()) { item ->
            when (item) {
                is ConversationItem.BroadcastRoom -> openBroadcastChat()
                is ConversationItem.DirectChat -> openChatForNode(item.node)
            }
        }
        rvConversations.adapter = conversationAdapter
    }

    fun refreshMessages() { refreshMeshComms() }

    fun refreshMeshComms() {
        if (!isAdded) return
        val dashActivity = activity as? DashboardActivity ?: return
        val engine = dashActivity.simulationEngine

        val allNodes = engine.getAllActiveNodes()
        val onlineCount = allNodes.count { it.isOnline }
        tvActiveOnlineCount.text = getString(R.string.msg_active_count, onlineCount)

        activeContactAdapter.updateContacts(allNodes)

        // Build Conversation Items
        val convItems = mutableListOf<ConversationItem>()

        // 1. Broadcast Room
        convItems.add(
            ConversationItem.BroadcastRoom(
                activeCount = engine.getNodeCount(),
                lastMessage = engine.getLatestMessage()
            )
        )

        // 2. Direct peer threads (other active peers)
        for (node in engine.getNodes()) {
            convItems.add(
                ConversationItem.DirectChat(
                    node = node,
                    lastMessage = engine.getLastMessageForNode(node.name)
                )
            )
        }

        conversationAdapter.updateItems(convItems)

        // If chat dialog is currently open, refresh its messages
        refreshOpenChatDialog()
    }

    private fun openBroadcastChat() {
        currentChatRecipientId = null
        currentChatRecipientName = null
        showChatDialog(
            title = "📡 PUBLIC MESH BROADCAST",
            subtitle = "⚡ ONLINE • ALL NODES IN RANGE • LORA / REAL-TIME"
        )
    }

    private fun openChatForNode(node: MeshNode) {
        if (node.isYou) {
            openBroadcastChat()
            return
        }
        currentChatRecipientId = node.id
        currentChatRecipientName = node.name
        val hopTag = if (node.hops == 1) "1 HOP" else "${node.hops} HOPS"
        showChatDialog(
            title = node.name.uppercase(),
            subtitle = "⚡ ONLINE • $hopTag • DIRECT ENCRYPTED LINK"
        )
    }

    private fun showChatDialog(title: String, subtitle: String) {
        val dashActivity = activity as? DashboardActivity ?: return
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_retro_chat)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))

        val tvTitle = dialog.findViewById<TextView>(R.id.tvChatTitle)
        val tvSub = dialog.findViewById<TextView>(R.id.tvChatSubtitle)
        val btnBack = dialog.findViewById<Button>(R.id.btnChatBack)
        val rvDialogMessages = dialog.findViewById<RecyclerView>(R.id.rvDialogMessages)
        val btnPriorityCycle = dialog.findViewById<Button>(R.id.btnPriorityCycle)
        val etInput = dialog.findViewById<EditText>(R.id.etDialogMessageInput)
        val btnSend = dialog.findViewById<ImageButton>(R.id.btnDialogSendMessage)

        tvTitle.text = title
        tvSub.text = subtitle

        btnBack.setOnClickListener { dialog.dismiss() }

        var currentPriority = Priority.STATUS
        val isCommander = (dashActivity.userRole == com.example.resqplug.simulation.UserRole.COMMANDER)

        fun applyPriorityTheme(priority: Priority) {
            currentPriority = priority
            when (priority) {
                Priority.STATUS -> {
                    btnPriorityCycle.text = "[ STATUS ]"
                    btnPriorityCycle.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_green))
                    btnPriorityCycle.setBackgroundResource(R.drawable.badge_priority_cycle_status)
                    etInput.setBackgroundResource(R.drawable.compose_input_bg)
                    btnSend.setBackgroundResource(R.drawable.btn_send)
                    btnSend.setColorFilter(ContextCompat.getColor(requireContext(), R.color.bg_navy))
                }
                Priority.EVAC -> {
                    btnPriorityCycle.text = "[ EVAC ]"
                    btnPriorityCycle.setTextColor(ContextCompat.getColor(requireContext(), R.color.alert_amber))
                    btnPriorityCycle.setBackgroundResource(R.drawable.badge_priority_cycle_evac)
                    etInput.setBackgroundResource(R.drawable.compose_input_bg_evac)
                    btnSend.setBackgroundResource(R.drawable.btn_send_evac)
                    btnSend.setColorFilter(ContextCompat.getColor(requireContext(), R.color.bg_navy))
                }
                Priority.SOS -> {
                    btnPriorityCycle.text = "[ 🚨 SOS ]"
                    btnPriorityCycle.setTextColor(ContextCompat.getColor(requireContext(), R.color.alert_red))
                    btnPriorityCycle.setBackgroundResource(R.drawable.badge_priority_cycle_sos)
                    etInput.setBackgroundResource(R.drawable.compose_input_bg_sos)
                    btnSend.setBackgroundResource(R.drawable.btn_send_sos)
                    btnSend.setColorFilter(ContextCompat.getColor(requireContext(), R.color.text_white))
                }
                Priority.BULLETIN -> {
                    btnPriorityCycle.text = "[ 📢 BULLETIN ]"
                    btnPriorityCycle.setTextColor(Color.parseColor("#9B59B6"))
                    btnPriorityCycle.setBackgroundResource(R.drawable.badge_priority_cycle_bulletin)
                    etInput.setBackgroundResource(R.drawable.compose_input_bg_bulletin)
                    btnSend.setBackgroundResource(R.drawable.btn_send_bulletin)
                    btnSend.setColorFilter(ContextCompat.getColor(requireContext(), R.color.text_white))
                }
            }
        }

        // Set initial default priority on chat open
        applyPriorityTheme(Priority.STATUS)

        // Single Clickable Badge cycles priorities (Commander unlocks exclusive BULLETIN)
        btnPriorityCycle.setOnClickListener {
            val nextPriority = when (currentPriority) {
                Priority.STATUS -> Priority.EVAC
                Priority.EVAC -> Priority.SOS
                Priority.SOS -> if (isCommander) Priority.BULLETIN else Priority.STATUS
                Priority.BULLETIN -> Priority.STATUS
            }
            applyPriorityTheme(nextPriority)
        }

        // Setup messages recycler view
        val layoutManager = LinearLayoutManager(requireContext())
        layoutManager.stackFromEnd = true
        rvDialogMessages.layoutManager = layoutManager

        val filteredMessages = getFilteredMessagesForCurrentChat()
        dialogMessageAdapter = MessageAdapter(filteredMessages)
        rvDialogMessages.adapter = dialogMessageAdapter
        if (filteredMessages.isNotEmpty()) {
            rvDialogMessages.scrollToPosition(filteredMessages.size - 1)
        }

        fun doSendMessage() {
            val text = etInput.text.toString().trim()
            if (text.isEmpty()) return

            // Channel Silence Enforcement: Non-emergency status traffic is blocked during silence override
            if (dashActivity.simulationEngine.isChannelSilenceActive && currentPriority == Priority.STATUS) {
                Toast.makeText(requireContext(), "⚠️ MESH SILENCE ACTIVE: ONLY SOS / EVAC MESSAGES PERMITTED", Toast.LENGTH_LONG).show()
                return
            }

            val channel = if (currentChatRecipientName == null) "BROADCAST" else "DIRECT"
            dashActivity.sendBroadcastMessage(
                text = text,
                priority = currentPriority,
                channel = channel,
                recipientId = currentChatRecipientId,
                recipientName = currentChatRecipientName
            )
            etInput.text.clear()
            // Automatically reset back to [ STATUS ] after send
            applyPriorityTheme(Priority.STATUS)
            refreshMeshComms()
        }

        btnSend.setOnClickListener { doSendMessage() }
        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                doSendMessage()
                true
            } else {
                false
            }
        }

        dialog.setOnDismissListener {
            activeChatDialog = null
            dialogMessageAdapter = null
            currentChatRecipientId = null
            currentChatRecipientName = null
        }

        activeChatDialog = dialog
        dialog.show()
    }

    private fun getFilteredMessagesForCurrentChat(): List<ChatMessage> {
        val dashActivity = activity as? DashboardActivity ?: return emptyList()
        val allMsgs = dashActivity.simulationEngine.getMessages()
        val recipient = currentChatRecipientName

        return if (recipient == null) {
            // Broadcast room shows all broadcast messages + system announcements
            allMsgs.filter { it.channel == "BROADCAST" || it.isSystem || it.recipientName.isNullOrEmpty() }
        } else {
            // Direct chat shows messages between this device and target recipient + system alerts
            allMsgs.filter {
                it.isSystem ||
                (it.channel == "DIRECT" && (
                    (it.sender.equals(recipient, ignoreCase = true) && (it.recipientName.isNullOrEmpty() || it.recipientName.equals(dashActivity.userName, ignoreCase = true))) ||
                    (it.isSent && it.recipientName?.equals(recipient, ignoreCase = true) == true)
                )) ||
                (!it.isSent && it.sender.equals(recipient, ignoreCase = true)) ||
                (it.isSent && it.recipientName?.equals(recipient, ignoreCase = true) == true)
            }
        }
    }

    private fun refreshOpenChatDialog() {
        val adapter = dialogMessageAdapter ?: return
        val dialog = activeChatDialog ?: return
        val msgs = getFilteredMessagesForCurrentChat()
        adapter.updateMessages(msgs)
        val rv = dialog.findViewById<RecyclerView>(R.id.rvDialogMessages)
        if (msgs.isNotEmpty()) {
            rv.scrollToPosition(msgs.size - 1)
        }
    }

    fun openDirectChatForCitizen(citizenName: String) {
        val dashActivity = activity as? DashboardActivity ?: return
        val existingNode = dashActivity.simulationEngine.getAllActiveNodes()
            .firstOrNull { it.name.equals(citizenName, ignoreCase = true) }

        val targetNode = existingNode ?: MeshNode(
            id = "node_${System.currentTimeMillis()}",
            name = citizenName,
            color = Color.parseColor("#3498DB"),
            isOnline = true,
            hops = 1,
            isYou = false
        )
        openChatForNode(targetNode)
    }
}
