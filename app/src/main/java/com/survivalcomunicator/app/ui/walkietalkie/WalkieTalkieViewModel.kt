package com.survivalcomunicator.app.ui.walkietalkie

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.survivalcomunicator.app.App
import com.survivalcomunicator.app.models.User
import com.survivalcomunicator.app.services.AudioRecorderService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class WalkieTalkieViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = (application as App).repository
    private val audioRecorderService = AudioRecorderService(application)
    
    private val _isTransmitting = MutableLiveData<Boolean>()
    val isTransmitting: LiveData<Boolean> = _isTransmitting
    
    private val _connectedUsers = MutableLiveData<List<User>>()
    val connectedUsers: LiveData<List<User>> = _connectedUsers
    
    private val _incomingTransmission = MutableLiveData<IncomingTransmission?>()
    val incomingTransmission: LiveData<IncomingTransmission?> = _incomingTransmission
    
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    private var currentAudioFile: String? = null
    
    init {
        _isTransmitting.value = false
        _connectedUsers.value = emptyList()
        _incomingTransmission.value = null
    }
    
    fun connectToWalkieTalkieNetwork() {
        viewModelScope.launch {
            try {
                // Simular conexión al servicio de walkie-talkie
                delay(1000)
                
                // Simular usuarios conectados
                _connectedUsers.value = listOf(
                    User(id = "user1", username = "Usuario 1", publicKey = "dummy_key"),
                    User(id = "user2", username = "Usuario 2", publicKey = "dummy_key")
                )
                
                // En una aplicación real, aquí iniciaríamos la escucha de transmisiones
                startListeningForTransmissions()
            } catch (e: Exception) {
                _errorMessage.value = "Error al conectar: ${e.message}"
            }
        }
    }
    
    fun disconnectFromWalkieTalkieNetwork() {
        viewModelScope.launch {
            try {
                // Detener transmisión si está activa
                if (_isTransmitting.value == true) {
                    stopTransmitting()
                }
                
                // Simular desconexión
                _connectedUsers.value = emptyList()
                _incomingTransmission.value = null
                
                // En una aplicación real, aquí detendríamos la escucha de transmisiones
            } catch (e: Exception) {
                _errorMessage.value = "Error al desconectar: ${e.message}"
            }
        }
    }
    
    fun startTransmitting() {
        try {
            if (_isTransmitting.value == true) return
            
            val audioFile = File(getApplication<Application>().cacheDir, "walkie_audio_${UUID.randomUUID()}.3gp")
            audioRecorderService.startRecording(audioFile.absolutePath)
            currentAudioFile = audioFile.absolutePath
            
            _isTransmitting.value = true
            
            // En una aplicación real, aquí iniciaríamos la transmisión en vivo
        } catch (e: Exception) {
            _errorMessage.value = "Error al iniciar transmisión: ${e.message}"
        }
    }
    
    fun stopTransmitting() {
        try {
            if (_isTransmitting.value != true) return
            
            val audioFilePath = audioRecorderService.stopRecording()
            _isTransmitting.value = false
            
            // En una aplicación real, aquí enviaríamos el archivo de audio
            if (audioFilePath != null) {
                // Simular envío
                viewModelScope.launch {
                    delay(500) // Simular tiempo de procesamiento
                    // En una app real, aquí enviaríamos el audio al servidor
                }
            }
        } catch (e: Exception) {
            _errorMessage.value = "Error al detener transmisión: ${e.message}"
        }
    }
    
    private fun startListeningForTransmissions() {
        // En una aplicación real, aquí configuraríamos un listener para transmisiones entrantes
        
        // Por ahora, simularemos una transmisión entrante después de un tiempo
        viewModelScope.launch {
            delay(5000) // Simular que recibimos una transmisión después de 5 segundos
            
            _incomingTransmission.value = IncomingTransmission(
                senderId = "user1",
                senderName = "Usuario 1",
                timestamp = System.currentTimeMillis()
            )
            
            // Simular que la transmisión dura 3 segundos
            delay(3000)
            _incomingTransmission.value = null
        }
    }
}

data class IncomingTransmission(
    val senderId: String,
    val senderName: String,
    val timestamp: Long
)