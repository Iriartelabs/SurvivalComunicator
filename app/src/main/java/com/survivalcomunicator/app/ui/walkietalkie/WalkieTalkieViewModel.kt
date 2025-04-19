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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import com.survivalcomunicator.app.utils.PreferencesManager
import com.survivalcomunicator.app.network.NetworkServiceImpl
import com.survivalcomunicator.app.repository.Repository
import com.survivalcomunicator.app.database.AppDatabase

class WalkieTalkieViewModel(application: Application) : AndroidViewModel(application) {
    
    private val audioRecorderService = AudioRecorderService(application)
    private val preferencesManager = PreferencesManager(getApplication())
    
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
    
    private suspend fun getNetworkService(): NetworkServiceImpl {
        val url = preferencesManager.getServerUrl() ?: ""
        return NetworkServiceImpl(url)
    }

    private suspend fun getRepository(): Repository {
        return Repository(
            AppDatabase.getDatabase(getApplication()).messageDao(),
            AppDatabase.getDatabase(getApplication()).userDao(),
            getNetworkService()
        )
    }

    fun connectToWalkieTalkieNetwork() {
        viewModelScope.launch {
            try {
                val repository = getRepository()
                val users = repository.getAllUsers().firstOrNull() ?: emptyList()
                _connectedUsers.value = users
                startListeningForTransmissions()
            } catch (e: Exception) {
                _errorMessage.value = "Error al conectar: ${e.message}"
            }
        }
    }

    fun disconnectFromWalkieTalkieNetwork() {
        viewModelScope.launch {
            try {
                if (_isTransmitting.value == true) {
                    stopTransmitting()
                }
                _connectedUsers.value = emptyList()
                _incomingTransmission.value = null
                // Aquí se debe detener la escucha real de transmisiones si aplica
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
            // Aquí deberías implementar la transmisión real del audio grabado al backend
        } catch (e: Exception) {
            _errorMessage.value = "Error al iniciar transmisión: ${e.message}"
        }
    }

    fun stopTransmitting() {
        try {
            if (_isTransmitting.value != true) return
            val audioFilePath = audioRecorderService.stopRecording()
            _isTransmitting.value = false
            if (audioFilePath != null) {
                // Aquí deberías implementar el envío real del archivo de audio al backend
            }
        } catch (e: Exception) {
            _errorMessage.value = "Error al detener transmisión: ${e.message}"
        }
    }

    private fun startListeningForTransmissions() {
        // Aquí deberías implementar la escucha real de transmisiones entrantes (REST polling o WebSocket)
        // Por ejemplo, podrías usar un Flow que consulte periódicamente el backend por nuevas transmisiones
    }
}

data class IncomingTransmission(
    val senderId: String,
    val senderName: String,
    val timestamp: Long
)