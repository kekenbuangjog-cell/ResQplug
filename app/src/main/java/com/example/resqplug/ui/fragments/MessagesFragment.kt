package com.example.resqplug.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.resqplug.DashboardActivity
import com.example.resqplug.R
import com.example.resqplug.simulation.Priority
import com.example.resqplug.ui.MessageAdapter

class MessagesFragment : Fragment() {

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessageInput: EditText
    private lateinit var btnSendMessage: Button
    private lateinit var btnPrioritySos: Button
    private lateinit var btnPriorityEvac: Button
    private lateinit var btnPriorityStatus: Button

    private lateinit var messageAdapter: MessageAdapter
    private var selectedPriority: Priority = Priority.STATUS

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_messages, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvMessages = view.findViewById(R.id.rvMessages)
        etMessageInput = view.findViewById(R.id.etMessageInput)
        btnSendMessage = view.findViewById(R.id.btnSendMessage)
        btnPrioritySos = view.findViewById(R.id.btnPrioritySos)
        btnPriorityEvac = view.findViewById(R.id.btnPriorityEvac)
        btnPriorityStatus = view.findViewById(R.id.btnPriorityStatus)

        val dashActivity = activity as? DashboardActivity
        val messages = dashActivity?.simulationEngine?.getMessages() ?: emptyList()

        val layoutManager = LinearLayoutManager(requireContext())
        layoutManager.stackFromEnd = true
        rvMessages.layoutManager = layoutManager

        messageAdapter = MessageAdapter(messages)
        rvMessages.adapter = messageAdapter

        // Priority Selection
        btnPrioritySos.setOnClickListener { selectPriority(Priority.SOS) }
        btnPriorityEvac.setOnClickListener { selectPriority(Priority.EVAC) }
        btnPriorityStatus.setOnClickListener { selectPriority(Priority.STATUS) }

        // Send Message
        btnSendMessage.setOnClickListener { sendMessage() }
        etMessageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        updatePriorityUI()
    }

    private fun selectPriority(priority: Priority) {
        selectedPriority = priority
        updatePriorityUI()
    }

    private fun updatePriorityUI() {
        btnPrioritySos.alpha = if (selectedPriority == Priority.SOS) 1.0f else 0.4f
        btnPriorityEvac.alpha = if (selectedPriority == Priority.EVAC) 1.0f else 0.4f
        btnPriorityStatus.alpha = if (selectedPriority == Priority.STATUS) 1.0f else 0.4f
    }

    private fun sendMessage() {
        val text = etMessageInput.text.toString().trim()
        if (text.isEmpty()) return

        val dashActivity = activity as? DashboardActivity ?: return
        dashActivity.sendBroadcastMessage(text, selectedPriority)
        etMessageInput.text.clear()

        refreshMessages()
    }

    fun refreshMessages() {
        val dashActivity = activity as? DashboardActivity ?: return
        val messages = dashActivity.simulationEngine.getMessages()
        messageAdapter.updateMessages(messages)
        if (messages.isNotEmpty()) {
            rvMessages.scrollToPosition(messages.size - 1)
        }
    }
}
