package com.smartgreen.data.model

// Sin campo suelo — solo temperatura y humedad
data class SensorReading(
    val temperatura: Double = 0.0,
    val humedad: Double = 0.0,
    val timestamp: Long = 0L,
    val emergencia: Boolean = false
)

data class Device(
    val deviceId: String = "",
    val nombre: String = "",
    val paired: Boolean = false,
    val ownerUid: String = "",
    val pairingCode: String = "",
    val ultimaConexion: Long = 0L,
    var latest: SensorReading? = null
) {
    // Considera online si envió datos en los últimos 20 minutos
    fun isOnline(): Boolean {
        if (ultimaConexion == 0L) return false
        val now = System.currentTimeMillis() / 1000
        return (now - ultimaConexion) < 20 * 60
    }
}

// Umbrales: se agrega humedadMax, se elimina sueloMin
data class AlertThresholds(
    val tempMax: Double = 35.0,
    val tempMin: Double = 10.0,
    val humedadMin: Double = 40.0,
    val humedadMax: Double = 85.0   // ← nuevo
)