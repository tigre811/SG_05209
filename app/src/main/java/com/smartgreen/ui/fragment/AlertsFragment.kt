package com.smartgreen.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.smartgreen.data.model.AlertThresholds
import com.smartgreen.databinding.FragmentAlertsBinding
import com.smartgreen.ui.viewmodel.DeviceViewModel
import com.smartgreen.ui.viewmodel.SaveState

class AlertsFragment : Fragment() {

    private var _binding: FragmentAlertsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DeviceViewModel by viewModels()
    private lateinit var deviceId: String

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAlertsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        deviceId = arguments?.getString("deviceId") ?: ""

        fun seekListener(onChanged: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) = onChanged(p)
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        }

        binding.seekTempMax.setOnSeekBarChangeListener(seekListener { binding.tvTempMax.text = "${it}°C" })
        binding.seekTempMin.setOnSeekBarChangeListener(seekListener { binding.tvTempMin.text = "${it}°C" })
        binding.seekHumMin.setOnSeekBarChangeListener(seekListener  { binding.tvHumMin.text  = "${it}%" })
        binding.seekHumMax.setOnSeekBarChangeListener(seekListener  { binding.tvHumMax.text  = "${it}%" })

        binding.btnSave.setOnClickListener {
            val thresholds = AlertThresholds(
                tempMax    = binding.seekTempMax.progress.toDouble(),
                tempMin    = binding.seekTempMin.progress.toDouble(),
                humedadMin = binding.seekHumMin.progress.toDouble(),
                humedadMax = binding.seekHumMax.progress.toDouble()
            )
            viewModel.saveThresholds(deviceId, thresholds)
        }

        viewModel.thresholds.observe(viewLifecycleOwner) { t ->
            binding.seekTempMax.progress = t.tempMax.toInt()
            binding.seekTempMin.progress = t.tempMin.toInt()
            binding.seekHumMin.progress  = t.humedadMin.toInt()
            binding.seekHumMax.progress  = t.humedadMax.toInt()
            binding.tvTempMax.text = "${t.tempMax.toInt()}°C"
            binding.tvTempMin.text = "${t.tempMin.toInt()}°C"
            binding.tvHumMin.text  = "${t.humedadMin.toInt()}%"
            binding.tvHumMax.text  = "${t.humedadMax.toInt()}%"
        }

        viewModel.saveState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is SaveState.Success -> {
                    binding.tvSaveStatus.text = "✓ Guardado correctamente"
                    binding.tvSaveStatus.setTextColor(0xFF5EC95E.toInt())
                    binding.tvSaveStatus.visibility = View.VISIBLE
                }
                is SaveState.Error -> {
                    binding.tvSaveStatus.text = "Error: ${state.msg}"
                    binding.tvSaveStatus.setTextColor(0xFFE05252.toInt())
                    binding.tvSaveStatus.visibility = View.VISIBLE
                }
                else -> {}
            }
        }

        viewModel.loadThresholds(deviceId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.resetSaveState()
        _binding = null
    }
}