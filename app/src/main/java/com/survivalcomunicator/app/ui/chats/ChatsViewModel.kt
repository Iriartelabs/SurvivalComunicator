// Ruta: app/src/main/java/com/survivalcomunicator/app/ui/chats/ChatsViewModel.kt
package com.survivalcomunicator.app.ui.chat

import android.app.Application
import androidx.lifecycle.*
import com.survivalcomunicator.app.database.AppDatabase
import com.survivalcomunicator.app.models.Message
import com.survivalcomunicator.app.models.MessageType
import com.survivalcomunicator.app.models.User
import com.survivalcomunicator.app.network.WebSocketManager
import com.survivalcomunicator.app.services.AudioRecorderService
import com.survivalcomunicator.app.utils.CryptoManager
import com.survivalcomunicator.app.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

class ChatsViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val userId: String = savedStateHandle["userId"] ?: throw IllegalStateException("userId no encontrado")

    private val database = AppDatabase.getDatabase(application)
    private val messageDao = database.messageDao()
    private val userDao = database.userDao()
    private val preferencesManager = PreferencesManager(application)
    private val cryptoManager = CryptoManager()
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
        viewModelScope.launch {
            loadUserInfo()
            loadMessages()
            startWebSocket()
            listenForMessages()
        }
    }

    private suspend fun loadUserInfo() {
        val currentUserId = preferencesManager.getUserId()
        val serverUrl = preferencesManager.getServerUrl() ?: ""
        if (currentUserId == null) {
            _errorMessage.postValue("Debes registrarte antes de usar el chat")
            return
        }

        currentUser = User(
            id = currentUserId,
            username = "Mi Usuario",
            publicKey = cryptoManager.getPublicKeyBase64()
        )

        val user = userDao.getUserById(userId)
        if (user != null) {
            chatPartner = user
            _chatTitle.postValue(user.username)
        } else {
            _chatTitle.postValue("Chat")
        }
    }

    private fun loadMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            val msgs = messageDao.getMessagesForUser(userId).map {
                MessageViewModel(
                    id = it.id,
                    content = it.content,
                    timestamp = it.timestamp,
                    isMine = it.senderId == currentUser?.id,
                    isRead = it.isRead,
                    isDelivered = it.isDelivered,
                    type = MessageType.valueOf(it.type)
                )
            }.sortedBy { it.timestamp }

            _messages.postValue(msgs)
        }
    }

    private suspend fun startWebSocket() {
        val userId = currentUser?.id ?: return
        val serverUrl = preferencesManager.getServerUrl() ?: return
        WebSocketManager.connect(serverUrl, userId, cryptoManager)
    }

    private fun listenForMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            WebSocketManager.messages.collectLatest { message ->
                messageDao.insertMessage(message.toEntity())
                if (message.senderId == userId) {
                    loadMessages()
                }
            }
        }
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank() || currentUser == null || chatPartner == null) return

        val message = Message(
            senderId = currentUser!!.id,
            recipientId = chatPartner!!.id,
            content = text,
            type = MessageType.TEXT
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                messageDao.insertMessage(message.toEntity())
                WebSocketManager.sendMessage(message, chatPartner!!.publicKey ?: "", cryptoManager)
                loadMessages()
            } catch (e: Exception) {
                _errorMessage.postValue("Error al enviar mensaje: ${e.message}")
            }
        }
    }

    fun startWalkieTalkieRecording() {
        try {
            cleanupAudioFile()
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
                sendWalkieTalkieMessage(audioFilePath)
            }
        } catch (e: Exception) {
            _errorMessage.value = "Error al detener grabación: ${e.message}"
        } finally {
            cleanupAudioFile()
        }
    }

    private fun sendWalkieTalkieMessage(audioPath: String) {
        if (currentUser == null || chatPartner == null) return

        val message = Message(
            senderId = currentUser!!.id,
            recipientId = chatPartner!!.id,
            content = audioPath,
            type = MessageType.WALKIE_TALKIE
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                messageDao.insertMessage(message.toEntity())
                WebSocketManager.sendMessage(message, chatPartner!!.publicKey ?: "", cryptoManager)
                loadMessages()
            } catch (e: Exception) {
                _errorMessage.postValue("Error al enviar audio: ${e.message}")
            }
        }
    }

    private fun cleanupAudioFile() {
        currentAudioFile?.takeIf { it.exists() }?.delete()
        currentAudioFile = null
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorderService.dispose()
        cleanupAudioFile()
    }
}

private fun Message.toEntity() = com.survivalcomunicator.app.database.MessageEntity(
    id = id,
    senderId = senderId,
    recipientId = recipientId,
    content = content,
    timestamp = timestamp,
    isRead = isRead,
    isDelivered = isDelivered,
    type = type.name
)
