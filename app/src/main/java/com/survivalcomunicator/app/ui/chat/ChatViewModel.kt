// Ruta: app/src/main/java/com/survivalcomunicator/app/ui/chat/ChatViewModel.kt
package com.survivalcomunicator.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.survivalcomunicator.app.App
import com.survivalcomunicator.app.database.AppDatabase
import com.survivalcomunicator.app.models.Message
import com.survivalcomunicator.app.models.MessageType
import com.survivalcomunicator.app.models.User
import com.survivalcomunicator.app.network.NetworkServiceImpl
import com.survivalcomunicator.app.repository.Repository
import com.survivalcomunicator.app.services.AudioRecorderService
import com.survivalcomunicator.app.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ChatViewModel(
    application: Application,
    private val userId: String,
    private val repository: com.survivalcomunicator.app.repository.Repository
) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getDatabase(application)
    private val messageDao = database.messageDao()
    private val userDao = database.userDao()
    private val networkService = NetworkServiceImpl("https://your-server-url.com") // Cambiar por URL real
    private val repository = Repository(messageDao, userDao, networkService)
    private val audioRecorderService = AudioRecorderService(application)
    
    private val _messages = MutableLiveData<List<MessageViewModel>>()
    val messages: LiveData<List<MessageViewModel>> = _messages
    
    private val _chatTitle = MutableLiveData<String>()
    val chatTitle: LiveData<String> = _chatTitle
    
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    private var currentUser: User? = null
    private var chatPartner: User? = null
    private var currentAudioFile: File? = null
    
    init {
        loadUserInfo()
        loadMessages()
    }

    private fun getServerUrl(): String {
        // En una app real, esto sería obtenido de las preferencias
        return "https://example.com/api"
    }
    
    private fun loadUserInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Intentar obtener el ID del usuario actual desde las preferencias
                val currentUserId = preferencesManager.getUserId() ?: "current_user_id"
                
                // Obtener información del usuario actual (para "mío" vs "suyo")
                currentUser = User(
                    id = currentUserId,
                    username = "Mi Usuario",
                    publicKey = "dummy_key"
                )
                
                // Obtener información del usuario del chat
                val user = repository.getUserById(userId)
                if (user != null) {
                    chatPartner = User(
                        id = user.id,
                        username = user.username,
                        publicKey = user.publicKey,
                        lastSeen = user.lastSeen,
                        serverAddress = user.serverAddress
                    )
                    withContext(Dispatchers.Main) {
                        _chatTitle.value = user.username
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _chatTitle.value = "Chat"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error al cargar información del usuario: ${e.message}"
                }
            }
        }
    }
    
    private fun loadMessages() {
        viewModelScope.launch {
            try {
                repository.getMessagesForUser(userId)
                    .flowOn(Dispatchers.IO)
                    .catch { e ->
                        _errorMessage.postValue("Error al cargar mensajes: ${e.message}")
                    }
                    .collect { messagesList ->
                        val viewModels = messagesList.map { message ->
                            MessageViewModel(
                                id = message.id,
                                content = message.content,
                                timestamp = message.timestamp,
                                isMine = message.senderId == currentUser?.id,
                                isRead = message.isRead,
                                isDelivered = message.isDelivered,
                                type = message.type
                            )
                        }
                        _messages.value = viewModels.sortedBy { it.timestamp }
                    }
            } catch (e: Exception) {
                _errorMessage.value = "Error al cargar mensajes: ${e.message}"
            }
        }
    }
    
    fun sendTextMessage(text: String) {
        if (text.isBlank() || currentUser == null) return
        
        val message = Message(
            senderId = currentUser!!.id,
            recipientId = userId,
            content = text,
            type = MessageType.TEXT
        )
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = repository.sendMessage(message)
                if (!success) {
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = "No se pudo enviar el mensaje"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error al enviar mensaje: ${e.message}"
                }
            }
        }
    }
    
    fun startWalkieTalkieRecording() {
        try {
            // Limpiar cualquier archivo anterior
            cleanupAudioFile()
            
            // Crear un nuevo archivo temporal
            val cacheDir = getApplication<Application>().cacheDir
            currentAudioFile = File(cacheDir, "audio_${UUID.randomUUID()}.3gp")
            audioRecorderService.startRecording(currentAudioFile!!.absolutePath)
        } catch (e: Exception) {
            _errorMessage.value = "Error al iniciar grabación: ${e.message}"
            cleanupAudioFile()
        }
    }
    
    fun stopWalkieTalkieRecording() {
        try {
            val audioFilePath = audioRecorderService.stopRecording()
            if (audioFilePath != null && currentUser != null) {
                // En una app real, subiríamos el archivo de audio y enviaríamos la URL
                sendWalkieTalkieMessage(audioFilePath)
            }
        } catch (e: Exception) {
            _errorMessage.value = "Error al detener grabación: ${e.message}"
        } finally {
            cleanupAudioFile()
        }
    }
    
    private fun sendWalkieTalkieMessage(audioPath: String) {
        if (currentUser == null) return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val message = Message(
                    senderId = currentUser!!.id,
                    recipientId = userId,
                    content = audioPath, // En una app real, esta sería la URL o referencia al audio
                    type = MessageType.WALKIE_TALKIE
                )
                
                val success = repository.sendMessage(message)
                if (!success) {
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = "No se pudo enviar el audio"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error al enviar audio: ${e.message}"
                }
            }
        }
    }
    
    private fun cleanupAudioFile() {
        currentAudioFile?.let {
            if (it.exists()) {
                try {
                    it.delete()
                } catch (e: Exception) {
                    // Ignorar errores al eliminar
                }
            }
        }
        currentAudioFile = null
    }
    
    override fun onCleared() {
        super.onCleared()
        // Liberar recursos cuando el ViewModel se destruya
        audioRecorderService.dispose()
        cleanupAudioFile()
    }
}

class ChatViewModelFactory(
    private val application: Application,
    private val userId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            val repository = (application as App).repository
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(application, userId, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}