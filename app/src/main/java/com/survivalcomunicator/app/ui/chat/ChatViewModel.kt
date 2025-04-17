package com.survivalcomunicator.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.survivalcomunicator.app.database.AppDatabase
import com.survivalcomunicator.app.models.Message
import com.survivalcomunicator.app.models.MessageType
import com.survivalcomunicator.app.models.User
import com.survivalcomunicator.app.network.NetworkServiceImpl
import com.survivalcomunicator.app.repository.Repository
import com.survivalcomunicator.app.services.AudioRecorderService
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class ChatViewModel(
    application: Application,
    private val userId: String
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
    
    init {
        loadMessages()
        loadUserInfo()
    }
    
    private fun loadUserInfo() {
        viewModelScope.launch {
            try {
                // Obtener información del usuario actual (para "mío" vs "suyo")
                // En una app real, esto vendría de preferencias o sesión
                currentUser = User(
                    id = "current_user_id",
                    username = "Mi Usuario",
                    publicKey = "dummy_key"
                )
                
                // Obtener información del usuario del chat
                val user = userDao.getUserById(userId)
                if (user != null) {
                    chatPartner = User(
                        id = user.id,
                        username = user.username,
                        publicKey = user.publicKey,
                        lastSeen = user.lastSeen,
                        serverAddress = user.serverAddress
                    )
                    _chatTitle.value = user.username
                } else {
                    _chatTitle.value = "Chat"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error al cargar información del usuario: ${e.message}"
            }
        }
    }
    
    private fun loadMessages() {
        viewModelScope.launch {
            try {
                repository.getMessagesForUser(userId).collect { messagesList ->
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
        if (currentUser == null) return
        
        val message = Message(
            senderId = currentUser!!.id,
            recipientId = userId,
            content = text,
            type = MessageType.TEXT
        )
        
        viewModelScope.launch {
            try {
                repository.sendMessage(message)
            } catch (e: Exception) {
                _errorMessage.value = "Error al enviar mensaje: ${e.message}"
            }
        }
    }
    
    fun startWalkieTalkieRecording() {
        try {
            val audioFile = File(getApplication<Application>().cacheDir, "audio_${UUID.randomUUID()}.3gp")
            audioRecorderService.startRecording(audioFile.absolutePath)
        } catch (e: Exception) {
            _errorMessage.value = "Error al iniciar grabación: ${e.message}"
        }
    }
    
    fun stopWalkieTalkieRecording() {
        try {
            val audioFilePath = audioRecorderService.stopRecording()
            if (audioFilePath != null && currentUser != null) {
                // En una app real, subiríamos el archivo de audio y enviaríamos la URL
                // Por ahora, sólo enviamos un mensaje que indica un audio
                val message = Message(
                    senderId = currentUser!!.id,
                    recipientId = userId,
                    content = "walkie_talkie_audio", // En una app real, esta sería la URL
                    type = MessageType.WALKIE_TALKIE
                )
                
                viewModelScope.launch {
                    try {
                        repository.sendMessage(message)
                    } catch (e: Exception) {
                        _errorMessage.value = "Error al enviar audio: ${e.message}"
                    }
                }
            }
        } catch (e: Exception) {
            _errorMessage.value = "Error al detener grabación: ${e.message}"
        }
    }
}

class ChatViewModelFactory(
    private val application: Application,
    private val userId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(application, userId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}