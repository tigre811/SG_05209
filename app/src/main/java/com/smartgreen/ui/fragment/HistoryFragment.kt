package com.smartgreen.ui.fragment

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.smartgreen.data.model.SensorReading
import com.smartgreen.databinding.FragmentHistoryBinding
import com.smartgreen.ui.viewmodel.DeviceViewModel
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DeviceViewModel by viewModels()
    private lateinit var deviceId: String

    private var currentData: List<SensorReading> = emptyList()
    private var currentMode = "temp"   // "temp" o "hum"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        deviceId = arguments?.getString("deviceId") ?: ""

        setupChart()

        // Botones de selección de variable
        binding.btnShowTemp.setOnClickListener {
            currentMode = "temp"
            resaltarBoton(activo = true, inactivo = false)
            updateChart()
        }
        binding.btnShowHum.setOnClickListener {
            currentMode = "hum"
            resaltarBoton(activo = false, inactivo = true)
            updateChart()
        }

        // Cargar máximo 20 lecturas
        viewModel.history.observe(viewLifecycleOwner) { data ->
            binding.progressBar.visibility = View.GONE
            currentData = data.takeLast(20)   // ← máximo 20
            updateChart()
        }

        binding.progressBar.visibility = View.VISIBLE
        viewModel.loadHistory(deviceId, limit = 20)
    }

    private fun resaltarBoton(activo: Boolean, inactivo: Boolean) {
        binding.btnShowTemp.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (activo) 0xFF2D6B2D.toInt() else 0xFF1A4D1A.toInt()
        )
        binding.btnShowHum.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (inactivo) 0xFF2D6B2D.toInt() else 0xFF1A4D1A.toInt()
        )
        binding.btnShowTemp.setTextColor(if (activo) 0xFFA8D8A8.toInt() else 0xFF6A9E6A.toInt())
        binding.btnShowHum.setTextColor(if (inactivo) 0xFFA8D8A8.toInt() else 0xFF6A9E6A.toInt())
    }

    private fun setupChart() {
        binding.lineChart.apply {
            setBackgroundColor(Color.TRANSPARENT)
            description.isEnabled = false
            legend.textColor = Color.parseColor("#6A9E6A")
            setNoDataText("Cargando datos...")
            setNoDataTextColor(Color.parseColor("#6A9E6A"))

            // Habilitar deslizamiento horizontal
            isDragEnabled          = true
            setScaleEnabled(true)
            setPinchZoom(true)
            isDoubleTapToZoomEnabled = true

            // Mostrar solo 10 puntos a la vez, el resto se ve deslizando
            setVisibleXRangeMaximum(10f)

            xAxis.apply {
                position          = XAxis.XAxisPosition.BOTTOM
                textColor         = Color.parseColor("#6A9E6A")
                gridColor         = Color.parseColor("#1A4D1A")
                axisLineColor     = Color.parseColor("#2D6B2D")
                granularity       = 1f
                labelRotationAngle = -45f
            }
            axisLeft.apply {
                textColor     = Color.parseColor("#6A9E6A")
                gridColor     = Color.parseColor("#1A4D1A")
                axisLineColor = Color.parseColor("#2D6B2D")
            }
            axisRight.isEnabled = false
        }
    }

    private fun updateChart() {
        if (currentData.isEmpty()) return

        val sdf    = SimpleDateFormat("HH:mm", Locale.getDefault())
        val labels = currentData.map { sdf.format(Date(it.timestamp * 1000)) }

        val entries = currentData.mapIndexed { i, r ->
            val value = if (currentMode == "temp") r.temperatura.toFloat() else r.humedad.toFloat()
            Entry(i.toFloat(), value)
        }

        val label = if (currentMode == "temp") "Temperatura (°C)" else "Humedad (%)"

        val dataSet = LineDataSet(entries, label).apply {
            color      = Color.parseColor("#5EC95E")
            lineWidth  = 2f
            circleRadius = 4f
            setCircleColor(Color.parseColor("#5EC95E"))
            valueTextColor = Color.parseColor("#6A9E6A")
            valueTextSize  = 9f
            setDrawValues(true)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            fillColor = Color.parseColor("#1A4D1A")
            setDrawFilled(true)
        }

        binding.lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.lineChart.data = LineData(dataSet)

        // Mover la vista al punto más reciente (derecha)
        binding.lineChart.moveViewToX(entries.size.toFloat())
        binding.lineChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}