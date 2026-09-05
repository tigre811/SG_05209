package com.smartgreen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import com.google.firebase.auth.FirebaseAuth
import com.smartgreen.databinding.ActivityMainBinding
import com.smartgreen.service.MonitorService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) iniciarServicio()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        if (FirebaseAuth.getInstance().currentUser != null) {
            navController.navigate(R.id.action_login_to_devices)
            pedirPermisoYIniciar()
        }
    }

    // Llamar desde LoginFragment y RegisterFragment tras login exitoso
    fun pedirPermisoYIniciar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> iniciarServicio()
                else -> requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            iniciarServicio()
        }
    }

    // Inicia el servicio de monitoreo en segundo plano
    fun iniciarServicio() {
        val intent = Intent(this, MonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    // Detener el servicio al cerrar sesión
    fun detenerServicio() {
        stopService(Intent(this, MonitorService::class.java))
    }
}
