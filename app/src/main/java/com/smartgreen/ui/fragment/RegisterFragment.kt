package com.smartgreen.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.smartgreen.R
import com.smartgreen.databinding.FragmentRegisterBinding
import com.smartgreen.ui.viewmodel.AuthState
import com.smartgreen.ui.viewmodel.AuthViewModel
import com.smartgreen.MainActivity

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRegister.setOnClickListener {
            val email   = binding.etEmail.text.toString()
            val pass    = binding.etPassword.text.toString()
            val confirm = binding.etConfirm.text.toString()
            viewModel.register(email, pass, confirm)
        }

        binding.tvLogin.setOnClickListener {
            findNavController().navigateUp()
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AuthState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnRegister.isEnabled = false
                    binding.tvError.visibility = View.GONE
                }
                /*is AuthState.Success -> {
                    (requireActivity() as MainActivity).pedirPermisoNotificaciones()
                    findNavController().navigate(R.id.action_register_to_devices)
                }*/
                is AuthState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRegister.isEnabled = true
                    binding.tvError.text = state.msg
                    binding.tvError.visibility = View.VISIBLE
                }
                else -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRegister.isEnabled = true
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

