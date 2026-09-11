package com.example.resqplug.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.resqplug.DashboardActivity
import com.example.resqplug.R

import android.widget.Button
import android.widget.EditText
import androidx.core.content.ContextCompat
import com.example.resqplug.simulation.UserRole

class SettingsFragment : Fragment() {

    private lateinit var tvSettingsNodeName: TextView
    private lateinit var tvSettingsDeviceId: TextView
    private lateinit var tvSettingsRoleBadge: TextView
    private lateinit var tvSettingsRoleDesc: TextView
    private lateinit var etSettingsAccessCode: EditText
    private lateinit var btnSettingsVerifyCode: Button
    private lateinit var btnSettingsResetRole: Button
    private lateinit var tvSettingsRoleFeedback: TextView
    private lateinit var tvSettingsUsbStatus: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvSettingsNodeName = view.findViewById(R.id.tvSettingsNodeName)
        tvSettingsDeviceId = view.findViewById(R.id.tvSettingsDeviceId)
        tvSettingsRoleBadge = view.findViewById(R.id.tvSettingsRoleBadge)
        tvSettingsRoleDesc = view.findViewById(R.id.tvSettingsRoleDesc)
        etSettingsAccessCode = view.findViewById(R.id.etSettingsAccessCode)
        btnSettingsVerifyCode = view.findViewById(R.id.btnSettingsVerifyCode)
        btnSettingsResetRole = view.findViewById(R.id.btnSettingsResetRole)
        tvSettingsRoleFeedback = view.findViewById(R.id.tvSettingsRoleFeedback)
        tvSettingsUsbStatus = view.findViewById(R.id.tvSettingsUsbStatus)
        val btnSettingsEditName = view.findViewById<TextView>(R.id.btnSettingsEditName)

        btnSettingsEditName.setOnClickListener {
            val dashActivity = activity as? DashboardActivity ?: return@setOnClickListener
            dashActivity.showEditNameDialog()
        }

        btnSettingsVerifyCode.setOnClickListener {
            verifyAccessCode()
        }

        btnSettingsResetRole.setOnClickListener {
            resetToCitizen()
        }

        updateSettingsInfo()
    }

    private fun verifyAccessCode() {
        val dashActivity = activity as? DashboardActivity ?: return
        val code = etSettingsAccessCode.text.toString().trim()

        if (code.isEmpty()) {
            showFeedback("PLEASE ENTER AN ACCESS CODE", false)
            return
        }

        val verifiedRole = UserRole.verifyCode(code)
        if (verifiedRole != null) {
            dashActivity.updateUserRole(verifiedRole)
            etSettingsAccessCode.text.clear()
            showFeedback("ACCESS GRANTED: ${verifiedRole.displayName.uppercase()} ACTIVATED", true)
            updateSettingsInfo()
            showClearanceSuccessDialog(verifiedRole)
        } else {
            showFeedback("ACCESS DENIED: INVALID CLEARANCE CODE", false)
        }
    }

    private fun showClearanceSuccessDialog(role: UserRole) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_clearance_success, null)
        val tvHeader = dialogView.findViewById<TextView>(R.id.tvClearanceHeader)
        val tvBadge = dialogView.findViewById<TextView>(R.id.tvClearanceBadge)
        val tvDesc = dialogView.findViewById<TextView>(R.id.tvClearanceDesc)
        val btnOk = dialogView.findViewById<Button>(R.id.btnClearanceOk)

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        tvBadge.text = role.badgeText
        when (role) {
            UserRole.RESPONDER -> {
                tvHeader.text = "[ RESPONDER CLEARANCE ACTIVATED ]"
                tvHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.alert_amber))
                tvBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.alert_amber))
                tvBadge.setBackgroundResource(R.drawable.badge_role_responder)
                tvDesc.text = "Field triage and emergency dispatch capabilities are now unlocked in the SOS Hub."
            }
            UserRole.COMMANDER -> {
                tvHeader.text = "[ COMMANDER CLEARANCE ACTIVATED ]"
                tvHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.alert_red))
                tvBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.alert_red))
                tvBadge.setBackgroundResource(R.drawable.badge_role_commander)
                tvDesc.text = "Command authority active. Unlocks Official Bulletins, Mesh Silence override, and Evacuation Orders."
            }
            UserRole.CITIZEN -> {
                tvHeader.text = "[ CITIZEN ROLE ACTIVE ]"
                tvHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_green))
                tvBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_green))
                tvBadge.setBackgroundResource(R.drawable.badge_role_citizen)
                tvDesc.text = "Standard citizen profile active."
            }
        }

        btnOk.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun resetToCitizen() {
        val dashActivity = activity as? DashboardActivity ?: return
        dashActivity.updateUserRole(UserRole.CITIZEN)
        showFeedback("ROLE RESET: CITIZEN ACTIVE", true)
        updateSettingsInfo()
    }

    private fun showFeedback(message: String, isSuccess: Boolean) {
        tvSettingsRoleFeedback.text = message
        val color = if (isSuccess) {
            ContextCompat.getColor(requireContext(), R.color.accent_green)
        } else {
            ContextCompat.getColor(requireContext(), R.color.alert_red)
        }
        tvSettingsRoleFeedback.setTextColor(color)
        tvSettingsRoleFeedback.visibility = View.VISIBLE
    }

    fun updateSettingsInfo() {
        if (!isAdded) return
        val dashActivity = activity as? DashboardActivity ?: return
        val name = if (dashActivity.userName.isNotEmpty()) dashActivity.userName else "Operator"
        tvSettingsNodeName.text = getString(R.string.settings_node_name, name)
        tvSettingsDeviceId.text = "Phone Node UID: ${dashActivity.nodeId}"

        // Update USB Hardware Diagnostics Status
        if (dashActivity.isUsbConnected) {
            tvSettingsUsbStatus.text = "USB Dongle: ${dashActivity.hardwareName} [LINKED]"
            tvSettingsUsbStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_green))
        } else {
            tvSettingsUsbStatus.text = "USB Dongle: [UNPLUGGED - OFFLINE]"
            tvSettingsUsbStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.alert_red))
        }

        // Update Role UI
        val currentRole = dashActivity.userRole
        tvSettingsRoleBadge.text = currentRole.badgeText

        when (currentRole) {
            UserRole.CITIZEN -> {
                tvSettingsRoleBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_green))
                tvSettingsRoleBadge.setBackgroundResource(R.drawable.badge_role_citizen)
                tvSettingsRoleDesc.text = "Standard citizen profile. Reports emergency & status updates."
                btnSettingsResetRole.visibility = View.GONE
            }
            UserRole.RESPONDER -> {
                tvSettingsRoleBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.alert_amber))
                tvSettingsRoleBadge.setBackgroundResource(R.drawable.badge_role_responder)
                tvSettingsRoleDesc.text = "Verified First Responder (MDRRMO / Medic / Tanod). Field triage clearance."
                btnSettingsResetRole.visibility = View.VISIBLE
            }
            UserRole.COMMANDER -> {
                tvSettingsRoleBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.alert_red))
                tvSettingsRoleBadge.setBackgroundResource(R.drawable.badge_role_commander)
                tvSettingsRoleDesc.text = "Verified Incident Commander. Full official advisory & broadcast clearance."
                btnSettingsResetRole.visibility = View.VISIBLE
            }
        }
    }
}
