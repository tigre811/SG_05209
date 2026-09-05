package com.smartgreen.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.messaging.FirebaseMessaging
import com.smartgreen.data.model.AlertThresholds
import com.smartgreen.data.model.Device
import com.smartgreen.data.model.SensorReading
import kotlinx.coroutines.tasks.await

class FirebaseRepository {

    private val db   = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    val currentUid get() = auth.currentUser?.uid
    val isLoggedIn get() = auth.currentUser != null

    // ══════════════════════════════════════════
    //  AUTH
    // ══════════════════════════════════════════

    suspend fun login(email: String, password: String): Result<Unit> =
        runCatching {
            auth.signInWithEmailAndPassword(email, password).await()
            saveFcmTokenInternal()
        }

    suspend fun register(email: String, password: String): Result<Unit> =
        runCatching {
            auth.createUserWithEmailAndPassword(email, password).await()
            val uid = auth.currentUser!!.uid
            db.child("users").child(uid).child("email").setValue(email).await()
            saveFcmTokenInternal()
        }

    fun logout() = auth.signOut()

    // ══════════════════════════════════════════
    //  DISPOSITIVOS
    // ══════════════════════════════════════════

    fun listenUserDevices(onUpdate: (List<Device>) -> Unit): ValueEventListener {
        val uid = currentUid ?: return emptyListener()
        val ref = db.child("users").child(uid).child("devices")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val devices = snapshot.children.mapNotNull { child ->
                    val nombre = child.child("nombre")
                        .getValue(String::class.java) ?: return@mapNotNull null
                    val ultimaConexion = child.child("ultimaConexion")
                        .getValue(Long::class.java) ?: 0L
                    child.key?.let {
                        Device(deviceId = it, nombre = nombre, ultimaConexion = ultimaConexion)
                    }
                }
                onUpdate(devices)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removeDevicesListener(listener: ValueEventListener) {
        val uid = currentUid ?: return
        db.child("users").child(uid).child("devices").removeEventListener(listener)
    }

    suspend fun getDevice(deviceId: String): Device? = runCatching {
        val snap = db.child("devices").child(deviceId).child("info").get().await()
        Device(
            deviceId       = deviceId,
            nombre         = snap.child("nombre").getValue(String::class.java) ?: "",
            paired         = snap.child("paired").getValue(Boolean::class.java) ?: false,
            ownerUid       = snap.child("ownerUid").getValue(String::class.java) ?: "",
            pairingCode    = snap.child("pairingCode").getValue(String::class.java) ?: "",
            ultimaConexion = snap.child("ultimaConexion").getValue(Long::class.java) ?: 0L
        )
    }.getOrNull()

    // ══════════════════════════════════════════
    //  EMPAREJAMIENTO DIRECTO (sin Cloud Functions)
    // ══════════════════════════════════════════

    suspend fun pairDevice(pairingCode: String, nombre: String): Result<String> =
        runCatching {
            val uid = currentUid ?: throw Exception("No autenticado")
            val code = pairingCode.uppercase().trim()

            // 1. Buscar deviceId por pairingCode
            val codeSnap = db.child("pairingCodes").child(code).get().await()
            if (!codeSnap.exists()) throw Exception("not-found")

            val deviceId = codeSnap.getValue(String::class.java)
                ?: throw Exception("not-found")

            // 2. Verificar que no esté ya emparejado
            val infoSnap = db.child("devices").child(deviceId).child("info").get().await()
            val isPaired = infoSnap.child("paired").getValue(Boolean::class.java) ?: false
            if (isPaired) throw Exception("already-exists")

            val timestamp = System.currentTimeMillis() / 1000

            // 3. Actualizar info del dispositivo
            db.child("devices").child(deviceId).child("info").updateChildren(
                mapOf(
                    "ownerUid"       to uid,
                    "paired"         to true,
                    "nombre"         to nombre.trim(),
                    "ultimaConexion" to timestamp
                )
            ).await()

            // 4. Agregar dispositivo al usuario
            db.child("users").child(uid).child("devices").child(deviceId).updateChildren(
                mapOf(
                    "nombre"   to nombre.trim(),
                    "agregado" to timestamp
                )
            ).await()

            // 5. Crear alertas por defecto para este dispositivo
            db.child("users").child(uid).child("alertas").child(deviceId).updateChildren(
                mapOf(
                    "tempMax"    to 35.0,
                    "tempMin"    to 10.0,
                    "humedadMin" to 40.0,
                    "sueloMin"   to 20
                )
            ).await()

            deviceId
        }

    // ══════════════════════════════════════════
    //  LECTURA EN TIEMPO REAL
    // ══════════════════════════════════════════

    fun listenLatest(deviceId: String, onUpdate: (SensorReading) -> Unit): ValueEventListener {
        val ref = db.child("devices").child(deviceId).child("latest")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.getValue(SensorReading::class.java)?.let { onUpdate(it) }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removeLatestListener(deviceId: String, listener: ValueEventListener) {
        db.child("devices").child(deviceId).child("latest").removeEventListener(listener)
    }

    // ══════════════════════════════════════════
    //  HISTORIAL
    // ══════════════════════════════════════════

    suspend fun getHistory(deviceId: String, limit: Int = 96): List<SensorReading> =
        runCatching {
            val snap = db.child("devices").child(deviceId).child("lecturas")
                .orderByChild("timestamp")
                .limitToLast(limit)
                .get().await()
            snap.children.mapNotNull { it.getValue(SensorReading::class.java) }
        }.getOrDefault(emptyList())

    // ══════════════════════════════════════════
    //  ALERTAS
    // ══════════════════════════════════════════

    suspend fun getThresholds(deviceId: String): AlertThresholds {
        val uid = currentUid ?: return AlertThresholds()
        return runCatching {
            val snap = db.child("users").child(uid)
                .child("alertas").child(deviceId).get().await()
            snap.getValue(AlertThresholds::class.java) ?: AlertThresholds()
        }.getOrDefault(AlertThresholds())
    }

    suspend fun saveThresholds(deviceId: String, thresholds: AlertThresholds): Result<Unit> =
        runCatching {
            val uid = currentUid ?: throw Exception("No autenticado")
            db.child("users").child(uid).child("alertas").child(deviceId)
                .setValue(thresholds).await()
        }

    // ══════════════════════════════════════════
    //  FCM
    // ══════════════════════════════════════════

    private suspend fun saveFcmTokenInternal() {
        val uid = currentUid ?: return
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            db.child("users").child(uid).child("fcmToken").setValue(token).await()
        }
    }

    suspend fun saveFcmTokenPublic(token: String) {
        val uid = currentUid ?: return
        runCatching {
            db.child("users").child(uid).child("fcmToken").setValue(token).await()
        }
    }

    // ══════════════════════════════════════════
    //  HELPER
    // ══════════════════════════════════════════

    private fun emptyListener() = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) {}
        override fun onCancelled(e: DatabaseError) {}
    }
}
