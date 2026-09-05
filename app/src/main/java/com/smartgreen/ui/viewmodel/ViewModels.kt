package com.smartgreen.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.ValueEventListener
import com.smartgreen.data.model.AlertThresholds
import com.smartgreen.data.model.Device
import com.smartgreen.data.model.SensorReading
import com.smartgreen.data.repository.FirebaseRepository
import kotlinx.coroutines.launch

// ── Estados sellados ──────────────────────────────────────────

sealed class AuthState {
    object Idle    : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val msg: String) : AuthState()
}

sealed class PairState {
    object Idle    : PairState()
    object Loading : PairState()
    data class Success(val deviceId: String) : PairState()
    data class Error(val msg: String) : PairState()
}

sealed class SaveState {
    object Idle    : SaveState()
    object Loading : SaveState()
    object Success : SaveState()
    data class Error(val msg: String) : SaveState()
}

// ── AuthViewModel ─────────────────────────────────────────────

class AuthViewModel : ViewModel() {

    private val repo = FirebaseRepository()

    private val _state = MutableLiveData<AuthState>(AuthState.Idle)
    val state: LiveData<AuthState> = _state

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.value = AuthState.Error("Completa todos los campos")
            return
        }
        _state.value = AuthState.Loading
        viewModelScope.launch {
            repo.login(email.trim(), password).fold(
                onSuccess = { _state.value = AuthState.Success },
                onFailure = { _state.value = AuthState.Error(friendlyError(it.message)) }
            )
        }
    }

    fun register(email: String, password: String, confirm: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.value = AuthState.Error("Completa todos los campos")
            return
        }
        if (password != confirm) {
            _state.value = AuthState.Error("Las contraseñas no coinciden")
            return
        }
        if (password.length < 6) {
            _state.value = AuthState.Error("Mínimo 6 caracteres")
            return
        }
        _state.value = AuthState.Loading
        viewModelScope.launch {
            repo.register(email.trim(), password).fold(
                onSuccess = { _state.value = AuthState.Success },
                onFailure = { _state.value = AuthState.Error(friendlyError(it.message)) }
            )
        }
    }

    fun resetState() { _state.value = AuthState.Idle }

    private fun friendlyError(msg: String?): String = when {
        msg == null                              -> "Error desconocido"
        msg.contains("INVALID_LOGIN_CREDENTIALS") -> "Correo o contraseña incorrectos"
        msg.contains("EMAIL_EXISTS")             -> "Este correo ya está registrado"
        msg.contains("WEAK_PASSWORD")            -> "Contraseña muy débil"
        msg.contains("NETWORK_ERROR")            -> "Sin conexión a internet"
        else                                     -> "Error: $msg"
    }
}

// ── DeviceViewModel ───────────────────────────────────────────

class DeviceViewModel : ViewModel() {

    private val repo = FirebaseRepository()

    private val _devices       = MutableLiveData<List<Device>>()
    val devices: LiveData<List<Device>> = _devices

    private val _reading       = MutableLiveData<SensorReading>()
    val reading: LiveData<SensorReading> = _reading

    private val _history       = MutableLiveData<List<SensorReading>>()
    val history: LiveData<List<SensorReading>> = _history

    private val _pairState     = MutableLiveData<PairState>(PairState.Idle)
    val pairState: LiveData<PairState> = _pairState

    private val _saveState     = MutableLiveData<SaveState>(SaveState.Idle)
    val saveState: LiveData<SaveState> = _saveState

    private val _thresholds    = MutableLiveData<AlertThresholds>()
    val thresholds: LiveData<AlertThresholds> = _thresholds

    private var devicesListener: ValueEventListener? = null
    private var latestListener:  ValueEventListener? = null
    private var currentDeviceId: String? = null

    // ── Dispositivos ──────────────────────────────────────────

    fun loadDevices() {
        devicesListener = repo.listenUserDevices { _devices.postValue(it) }
    }

    fun stopListeningDevices() {
        devicesListener?.let { repo.removeDevicesListener(it) }
        devicesListener = null
    }

    // ── Dispositivo seleccionado ──────────────────────────────

    fun selectDevice(deviceId: String) {
        latestListener?.let {
            currentDeviceId?.let { id -> repo.removeLatestListener(id, it) }
        }
        currentDeviceId = deviceId
        latestListener = repo.listenLatest(deviceId) { _reading.postValue(it) }
    }

    fun stopListeningDevice() {
        latestListener?.let {
            currentDeviceId?.let { id -> repo.removeLatestListener(id, it) }
        }
        latestListener  = null
        currentDeviceId = null
    }

    // ── Historial ─────────────────────────────────────────────

    fun loadHistory(deviceId: String, limit: Int = 96) {
        viewModelScope.launch {
            val data = repo.getHistory(deviceId, limit)
            _history.postValue(data)
        }
    }

    // ── Emparejamiento ────────────────────────────────────────

    fun pairDevice(code: String, nombre: String) {
        if (code.isBlank() || nombre.isBlank()) {
            _pairState.value = PairState.Error("Completa todos los campos")
            return
        }
        _pairState.value = PairState.Loading
        viewModelScope.launch {
            repo.pairDevice(code, nombre).fold(
                onSuccess = { _pairState.value = PairState.Success(it) },
                onFailure = {
                    val msg = when {
                        it.message?.contains("not-found") == true      -> "Código inválido"
                        it.message?.contains("already-exists") == true -> "Dispositivo ya emparejado"
                        else -> "Error: ${it.message}"
                    }
                    _pairState.value = PairState.Error(msg)
                }
            )
        }
    }

    fun resetPairState() { _pairState.value = PairState.Idle }

    // ── Umbrales ──────────────────────────────────────────────

    fun loadThresholds(deviceId: String) {
        viewModelScope.launch {
            val t = repo.getThresholds(deviceId)
            _thresholds.postValue(t)
        }
    }

    fun saveThresholds(deviceId: String, thresholds: AlertThresholds) {
        _saveState.value = SaveState.Loading
        viewModelScope.launch {
            repo.saveThresholds(deviceId, thresholds).fold(
                onSuccess = { _saveState.value = SaveState.Success },
                onFailure = { _saveState.value = SaveState.Error(it.message ?: "Error") }
            )
        }
    }

    fun resetSaveState() { _saveState.value = SaveState.Idle }

    // ── Limpieza ──────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        stopListeningDevices()
        stopListeningDevice()
    }
}