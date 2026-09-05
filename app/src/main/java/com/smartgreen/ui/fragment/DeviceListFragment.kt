package com.smartgreen.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.smartgreen.R
import com.smartgreen.databinding.FragmentDeviceListBinding
import com.smartgreen.ui.adapter.DeviceAdapter
import com.smartgreen.ui.viewmodel.DeviceViewModel
import com.google.firebase.auth.FirebaseAuth
import com.smartgreen.MainActivity

class DeviceListFragment : Fragment() {

    private var _binding: FragmentDeviceListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DeviceViewModel by viewModels()
    private lateinit var adapter: DeviceAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDeviceListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // RecyclerView
        adapter = DeviceAdapter { device ->
            val bundle = Bundle().apply {
                putString("deviceId", device.deviceId)
                putString("deviceName", device.nombre)
            }
            findNavController().navigate(R.id.action_devices_to_dashboard, bundle)
        }
        binding.rvDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDevices.adapter = adapter

        // Agregar dispositivo
        binding.btnAddDevice.setOnClickListener {
            findNavController().navigate(R.id.action_devices_to_pair)
        }

        // Cerrar sesión
        binding.btnLogout.setOnClickListener {
            (requireActivity() as MainActivity).detenerServicio()
            FirebaseAuth.getInstance().signOut()
            findNavController().navigate(R.id.loginFragment)
        }

        // Observar dispositivos
        viewModel.devices.observe(viewLifecycleOwner) { devices ->
            adapter.submitList(devices)
            binding.tvDeviceCount.text = "${devices.size} dispositivo(s) conectado(s)"
            binding.tvEmpty.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
            binding.rvDevices.visibility = if (devices.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.loadDevices()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.stopListeningDevices()
        _binding = null
    }
}
