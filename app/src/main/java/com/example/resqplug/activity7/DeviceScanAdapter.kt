package com.example.resqplug.activity7

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.resqplug.R

class DeviceScanAdapter(
    private var devices: List<DiscoveredPod>,
    private val onDeviceSelected: (DiscoveredPod) -> Unit
) : RecyclerView.Adapter<DeviceScanAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDeviceName: TextView = itemView.findViewById(R.id.tvDeviceName)
        val tvDeviceAddress: TextView = itemView.findViewById(R.id.tvDeviceAddress)
        val btnConnectDevice: Button = itemView.findViewById(R.id.btnConnectDevice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_a7_bluetooth_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.tvDeviceName.text = device.name
        holder.tvDeviceAddress.text = "MAC: ${device.address}${if (device.isPaired) " [PAIRED]" else ""}"

        holder.btnConnectDevice.setOnClickListener {
            onDeviceSelected(device)
        }
        holder.itemView.setOnClickListener {
            onDeviceSelected(device)
        }
    }

    override fun getItemCount(): Int = devices.size

    fun updateDevices(newDevices: List<DiscoveredPod>) {
        this.devices = newDevices
        notifyDataSetChanged()
    }
}
