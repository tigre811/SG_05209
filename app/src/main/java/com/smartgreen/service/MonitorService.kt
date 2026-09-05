package com.smartgreen.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.smartgreen.MainActivity
import com.smartgreen.R
import com.smartgreen.data.model.AlertThresholds
import com.smartgreen.data.model.SensorReading

class MonitorService : Service() {

    private val db   = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    // Listeners activos por deviceId
    private val listeners = mutableMapOf<String, ValueEventListener>()

    // Última lectura por deviceId
    private val latestReadings = mutableMapOf<String, SensorReading>()
    private val deviceNames    = mutableMapOf<String, String>()

    // Umbrales por deviceId
    private val thresholds = mutableMapOf<String, AlertThresholds>()

    companion object {
        const val CHANNEL_MONITOR  = "smartgreen_monitor"
        const val CHANNEL_ALERTAS  = "smartgreen_alertas"
        const val NOTIF_ID_MONITOR = 1001
    }

    override fun onCreate() {
        super.onCreate()
        crearCanales()
        startForeground(NOTIF_ID_MONITOR, buildMonitorNotification("Conectando...", ""))
        cargarDispositivos()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY  // Se reinicia automáticamente si el sistema lo mata
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        // Limpiar listeners al destruir
        val uid = auth.currentUser?.uid ?: return
        listeners.forEach { (deviceId, listener) ->
            db.child("devices").child(deviceId).child("latest").removeEventListener(listener)
        }
        listeners.clear()
    }

    // ── Crear canales de notificación ────────────────────────
    private fun crearCanales() {
        val manager = getSystemService(NotificationManager::class.java)

        // Canal para la notificación persistente (baja prioridad → no hace sonido)
        val canalMonitor = NotificationChannel(
            CHANNEL_MONITOR,
            "Monitor Smart Green",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Datos en tiempo real" }

        // Canal para alertas (alta prioridad → hace sonido)
        val canalAlertas = NotificationChannel(
            CHANNEL_ALERTAS,
            "Alertas Smart Green",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Alertas de sensores" }

        manager.createNotificationChannel(canalMonitor)
        manager.createNotificationChannel(canalAlertas)
    }

    // ── Cargar dispositivos del usuario ──────────────────────
    private fun cargarDispositivos() {
        val uid = auth.currentUser?.uid ?: return

        db.child("users").child(uid).child("devices")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Limpiar listeners anteriores
                    listeners.forEach { (deviceId, listener) ->
                        db.child("devices").child(deviceId).child("latest")
                            .removeEventListener(listener)
                    }
                    listeners.clear()

                    // Registrar listener por cada dispositivo
                    snapshot.children.forEach { child ->
                        val deviceId = child.key ?: return@forEach
                        val nombre   = child.child("nombre")
                            .getValue(String::class.java) ?: "Invernadero"
                        deviceNames[deviceId] = nombre

                        // Cargar umbrales
                        db.child("users").child(uid).child("alertas").child(deviceId)
                            .addValueEventListener(object : ValueEventListener {
                                override fun onDataChange(snap: DataSnapshot) {
                                    thresholds[deviceId] = snap.getValue(AlertThresholds::class.java)
                                        ?: AlertThresholds()
                                }
                                override fun onCancelled(error: DatabaseError) {}
                            })

                        // Escuchar lecturas en tiempo real
                        escucharDispositivo(deviceId)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // ── Escuchar lectura en tiempo real de un dispositivo ────
    private fun escucharDispositivo(deviceId: String) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val reading = snapshot.getValue(SensorReading::class.java) ?: return
                latestReadings[deviceId] = reading

                // Actualizar notificación persistente
                actualizarNotificacionMonitor()

                // Evaluar alertas
                val t = thresholds[deviceId] ?: AlertThresholds()
                evaluarAlertas(deviceId, deviceNames[deviceId] ?: "Invernadero", reading, t)
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        db.child("devices").child(deviceId).child("latest").addValueEventListener(listener)
        listeners[deviceId] = listener
    }

    // ── Actualizar notificación persistente con todos los datos
    private fun actualizarNotificacionMonitor() {
        if (latestReadings.isEmpty()) return

        val manager = getSystemService(NotificationManager::class.java)

        // Si hay un solo dispositivo → mostrar directamente
        // Si hay varios → mostrar resumen
        val titulo: String
        val contenido: String

        if (latestReadings.size == 1) {
            val deviceId = latestReadings.keys.first()
            val reading  = latestReadings[deviceId]!!
            val nombre   = deviceNames[deviceId] ?: "Invernadero"
            titulo    = nombre
            contenido = "🌡️ %.1f°C   💧 %.0f%%".format(reading.temperatura, reading.humedad)
        } else {
            titulo    = "Smart Green — ${latestReadings.size} invernaderos"
            contenido = latestReadings.entries.joinToString("   ") { (id, r) ->
                "${deviceNames[id] ?: "Inv"}: %.0f°C %.0f%%".format(r.temperatura, r.humedad)
            }
        }

        manager.notify(NOTIF_ID_MONITOR, buildMonitorNotification(titulo, contenido))
    }

    // ── Construir notificación persistente ───────────────────
    private fun buildMonitorNotification(titulo: String, contenido: String) =
        NotificationCompat.Builder(this, CHANNEL_MONITOR)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titulo)
            .setContentText(contenido)
            .setOngoing(true)          // No se puede descartar deslizando
            .setSilent(true)           // Sin sonido
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    // ── Evaluar alertas y notificar si hay problema ──────────
    private fun evaluarAlertas(
        deviceId: String,
        nombre: String,
        reading: SensorReading,
        t: AlertThresholds
    ) {
        val mensajes = mutableListOf<String>()

        if (reading.temperatura > t.tempMax)
            mensajes.add("🌡️ Temp alta: %.1f°C".format(reading.temperatura))
        if (reading.temperatura < t.tempMin)
            mensajes.add("🌡️ Temp baja: %.1f°C".format(reading.temperatura))
        if (reading.humedad > t.humedadMax)
            mensajes.add("💧 Humedad alta: %.0f%%".format(reading.humedad))
        if (reading.humedad < t.humedadMin)
            mensajes.add("💧 Humedad baja: %.0f%%".format(reading.humedad))

        if (mensajes.isEmpty()) return

        val manager = getSystemService(NotificationManager::class.java)
        val notif = NotificationCompat.Builder(this, CHANNEL_ALERTAS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⚠️ $nombre")
            .setContentText(mensajes.joinToString("  |  "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensajes.joinToString("\n")))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // ID único por dispositivo para no solapar notificaciones
        manager.notify(deviceId.hashCode(), notif)
    }
}
