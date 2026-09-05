package com.smartgreen.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.smartgreen.R
import com.smartgreen.databinding.FragmentPairDeviceBinding
import com.smartgreen.ui.viewmodel.DeviceViewModel
import com.smartgreen.ui.viewmodel.PairState

class PairDeviceFragment : Fragment() {

    private var _binding: FragmentPairDeviceBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DeviceViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPairDeviceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnPair.setOnClickListener {
            val code   = binding.etPairingCode.text.toString()
            val nombre = binding.etDeviceName.text.toString()
            viewModel.pairDevice(code, nombre)
        }

        viewModel.pairState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is PairState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnPair.isEnabled = false
                    binding.tvError.visibility = View.GONE
                }
                is PairState.Success -> {
                    findNavController().navigate(R.id.action_pair_to_devices)
                }
                is PairState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnPair.isEnabled = true
                    binding.tvError.text = state.msg
                    binding.tvError.visibility = View.VISIBLE
                }
                else -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnPair.isEnabled = true
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.resetPairState()
        _binding = null
    }
}
