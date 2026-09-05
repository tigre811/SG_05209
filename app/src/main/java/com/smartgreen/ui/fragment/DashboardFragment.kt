package com.smartgreen.ui.fragment

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.smartgreen.R
import com.smartgreen.data.model.AlertThresholds
import com.smartgreen.data.model.SensorReading
import com.smartgreen.databinding.FragmentDashboardBinding
import com.smartgreen.ui.viewmodel.DeviceViewModel
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DeviceViewModel by viewModels()

    private lateinit var deviceId: String
    private lateinit var deviceName: String

    private var thresholds = AlertThresholds()
    private var lastAlertMsg  = ""
    private var lastAlertTime = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        deviceId   = arguments?.getString("deviceId")   ?: ""
        deviceName = arguments?.getString("deviceName") ?: "Invernadero"

        binding.tvDeviceName.text = deviceName

        binding.btnHistory.setOnClickListener {
            val bundle = Bundle().apply { putString("deviceId", deviceId) }
            findNavController().navigate(R.id.action_dashboard_to_history, bundle)
        }

        binding.btnAlerts.setOnClickListener {
            val bundle = Bundle().apply { putString("deviceId", deviceId) }
            findNavController().navigate(R.id.action_dashboard_to_alerts, bundle)
        }

        viewModel.thresholds.observe(viewLifecycleOwner) { t -> thresholds = t }
        viewModel.loadThresholds(deviceId)

        viewModel.reading.observe(viewLifecycleOwner) { reading ->
            actualizarUI(reading)
            evaluarAlertas(reading)
        }

        viewModel.selectDevice(deviceId)
    }

    private fun actualizarUI(reading: SensorReading) {
        binding.tvTemperatura.text = String.format("%.1f°C", reading.temperatura)
        binding.tvHumedad.text     = String.format("%.0f%%", reading.humedad)

        // Color temperatura
        binding.tvTemperatura.setTextColor(when {
            reading.temperatura > thresholds.tempMax -> 0xFFE05252.toInt()
            reading.temperatura < thresholds.tempMin -> 0xFF5B9BD5.toInt()
            else -> 0xFFC8E6C3.toInt()
        })

        // Color humedad (alerta si está fuera de rango)
        binding.tvHumedad.setTextColor(when {
            reading.humedad > thresholds.humedadMax -> 0xFFE05252.toInt()
            reading.humedad < thresholds.humedadMin -> 0xFFE05252.toInt()
            else -> 0xFFC8E6C3.toInt()
        })

        val sdf  = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val hora = sdf.format(Date(reading.timestamp * 1000))
        binding.tvLastUpdate.text = "Última lectura: $hora"
    }

    private fun evaluarAlertas(reading: SensorReading) {
        val mensajes = mutableListOf<String>()

        if (reading.temperatura > thresholds.tempMax)
            mensajes.add("🌡️ Temp alta: %.1f°C".format(reading.temperatura))
        if (reading.temperatura < thresholds.tempMin)
            mensajes.add("🌡️ Temp baja: %.1f°C".format(reading.temperatura))
        if (reading.humedad > thresholds.humedadMax)
            mensajes.add("💧 Humedad alta: %.0f%%".format(reading.humedad))
        if (reading.humedad < thresholds.humedadMin)
            mensajes.add("💧 Humedad baja: %.0f%%".format(reading.humedad))

        if (mensajes.isEmpty()) {
            binding.layoutEmergencia.visibility = View.GONE
            return
        }

        val textoAlerta = mensajes.joinToString("  |  ")
        binding.layoutEmergencia.visibility = View.VISIBLE
        binding.tvEmergencia.text = textoAlerta

        val ahora      = System.currentTimeMillis()
        val cambio     = textoAlerta != lastAlertMsg
        val pasaron5min = (ahora - lastAlertTime) > 5 * 60 * 1000

        if (cambio || pasaron5min) {
            lastAlertMsg  = textoAlerta
            lastAlertTime = ahora
            enviarNotificacionLocal("⚠️ $deviceName", textoAlerta)
        }
    }

    private fun enviarNotificacionLocal(titulo: String, mensaje: String) {
        val context   = requireContext()
        val channelId = "smartgreen_alertas"
        val manager   = context.getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(channelId, "Alertas Smart Green", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Alertas de sensores" }
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensaje))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(deviceId.hashCode(), notification)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.stopListeningDevice()
        _binding = null
    }
}