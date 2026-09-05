package com.smartgreen.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.smartgreen.R
import com.smartgreen.databinding.FragmentLoginBinding
import com.smartgreen.ui.viewmodel.AuthState
import com.smartgreen.ui.viewmodel.AuthViewModel
import com.smartgreen.MainActivity

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Sin registro — acceso solo por invitación del administrador
        binding.tvRegister.visibility = View.GONE

        binding.btnLogin.setOnClickListener {
            val email    = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            viewModel.login(email, password)
        }

        binding.tvForgotPassword.setOnClickListener {
            mostrarDialogoRecuperacion()
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AuthState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnLogin.isEnabled     = false
                    binding.tvError.visibility     = View.GONE
                }
                is AuthState.Success -> {
                    (requireActivity() as MainActivity).pedirPermisoYIniciar()
                    findNavController().navigate(R.id.action_login_to_devices)
                }
                is AuthState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled     = true
                    binding.tvError.setTextColor(0xFFE05252.toInt())
                    binding.tvError.text           = state.msg
                    binding.tvError.visibility     = View.VISIBLE
                }
                else -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled     = true
                }
            }
        }
    }

    private fun mostrarDialogoRecuperacion() {
        val emailInput = TextInputEditText(requireContext()).apply {
            hint      = "Ingresa tu correo"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Recuperar contraseña")
            .setMessage("Te enviaremos un correo para restablecer tu contraseña.")
            .setView(emailInput)
            .setPositiveButton("Enviar") { _, _ ->
                val email = emailInput.text.toString().trim()
                if (email.isEmpty()) {
                    binding.tvError.text       = "Ingresa tu correo primero"
                    binding.tvError.visibility = View.VISIBLE
                    return@setPositiveButton
                }
                enviarCorreoRecuperacion(email)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun enviarCorreoRecuperacion(email: String) {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tvError.setTextColor(0xFFE05252.toInt())
            binding.tvError.text       = "Ingresa un correo válido"
            binding.tvError.visibility = View.VISIBLE
            return
        }
        binding.progressBar.visibility = View.VISIBLE
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                binding.tvError.setTextColor(0xFF5EC95E.toInt())
                binding.tvError.text       = "✓ Correo enviado. Revisa tu bandeja o spam"
                binding.tvError.visibility = View.VISIBLE
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.tvError.setTextColor(0xFFE05252.toInt())
                val msg = when {
                    e.message?.contains("INVALID_EMAIL") == true   -> "Correo inválido"
                    e.message?.contains("EMAIL_NOT_FOUND") == true -> "Correo no registrado"
                    e.message?.contains("NETWORK_ERROR") == true   -> "Sin conexión a internet"
                    else -> "Error: ${e.message}"
                }
                binding.tvError.text       = msg
                binding.tvError.visibility = View.VISIBLE
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.resetState()
        _binding = null
    }
}