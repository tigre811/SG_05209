package com.smartgreen.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.smartgreen.R
import com.smartgreen.data.model.AlertThresholds
import com.smartgreen.data.model.SensorReading
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class AlertWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db   = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    override suspend fun doWork(): Result {
        val uid = auth.currentUser?.uid ?: return Result.success()

        return try {
            val devicesSnap = db.child("users").child(uid).child("devices").get().await()

            devicesSnap.children.forEach { deviceChild ->
                val deviceId = deviceChild.key ?: return@forEach
                val nombre   = deviceChild.child("nombre")
                    .getValue(String::class.java) ?: "Invernadero"

                val latestSnap = db.child("devices").child(deviceId)
                    .child("latest").get().await()
                val reading = latestSnap.getValue(SensorReading::class.java)
                    ?: return@forEach

                val threshSnap = db.child("users").child(uid)
                    .child("alertas").child(deviceId).get().await()
                val thresholds = threshSnap.getValue(AlertThresholds::class.java)
                    ?: AlertThresholds()

                evaluarAlertas(deviceId, nombre, reading, thresholds)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun evaluarAlertas(
        deviceId: String,
        nombre: String,
        reading: SensorReading,
        thresholds: AlertThresholds
    ) {
        val mensajes = mutableListOf<String>()

        if (reading.temperatura > thresholds.tempMax)
            mensajes.add("🌡️ Temp alta: %.1f°C".format(reading.temperatura))

        if (reading.temperatura < thresholds.tempMin)
            mensajes.add("🌡️ Temp baja: %.1f°C".format(reading.temperatura))

        if (reading.humedad > thresholds.humedadMax)
            mensajes.add("💧 Humedad alta: %.0f%%".format(reading.humedad))

        if (reading.humedad < thresholds.humedadMin)
            mensajes.add("💧 Humedad baja: %.0f%%".format(reading.humedad))

        if (mensajes.isEmpty()) return

        enviarNotificacion(
            deviceId = deviceId,
            titulo   = "⚠️ $nombre",
            mensaje  = mensajes.joinToString("  |  ")
        )
    }

    private fun enviarNotificacion(deviceId: String, titulo: String, mensaje: String) {
        val channelId = "smartgreen_alertas"
        val manager   = context.getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            channelId,
            "Alertas Smart Green",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Alertas de sensores" }
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
}

object AlertScheduler {

    private const val WORK_NAME = "smartgreen_alert_check"

    fun start(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}