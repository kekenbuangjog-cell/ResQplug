package com.example.resqplug.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.resqplug.DashboardActivity
import com.example.resqplug.R

class SettingsFragment : Fragment() {

    private lateinit var tvSettingsNodeName: TextView
    private lateinit var tvSettingsDeviceId: TextView

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

        updateSettingsInfo()
    }

    fun updateSettingsInfo() {
        val dashActivity = activity as? DashboardActivity ?: return
        val name = if (dashActivity.userName.isNotEmpty()) dashActivity.userName else "Operator"
        tvSettingsNodeName.text = getString(R.string.settings_node_name, name)
        tvSettingsDeviceId.text = getString(R.string.settings_device_id, dashActivity.deviceId)
    }
}
