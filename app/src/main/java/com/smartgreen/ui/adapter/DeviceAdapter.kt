package com.smartgreen.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smartgreen.R
import com.smartgreen.data.model.Device
import com.smartgreen.databinding.ItemDeviceBinding
import java.text.SimpleDateFormat
import java.util.*

class DeviceAdapter(
    private val onClick: (Device) -> Unit
) : ListAdapter<Device, DeviceAdapter.DeviceViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: Device) {
            binding.tvDeviceName.text = device.nombre.ifEmpty { "Dispositivo sin nombre" }

            // Estado online/offline
            val isOnline = device.isOnline()
            if (isOnline) {
                binding.statusDot.setBackgroundResource(R.drawable.dot_green)
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val hora = sdf.format(Date(device.ultimaConexion * 1000))
                binding.tvLastSeen.text = "Última lectura: $hora"
            } else {
                binding.statusDot.setBackgroundResource(R.drawable.dot_amber)
                binding.tvLastSeen.text = if (device.ultimaConexion == 0L)
                    "Sin datos aún" else "Sin señal"
            }

            binding.root.setOnClickListener { onClick(device) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Device>() {
        override fun areItemsTheSame(a: Device, b: Device) = a.deviceId == b.deviceId
        override fun areContentsTheSame(a: Device, b: Device) = a == b
    }
}
