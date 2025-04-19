// Ruta: app/src/main/java/com/survivalcomunicator/app/ui/chats/ChatsViewModel.kt
package com.survivalcomunicator.app.ui.chats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.survivalcomunicator.app.database.AppDatabase
import com.survivalcomunicator.app.models.User
import com.survivalcomunicator.app.network.NetworkServiceImpl
import com.survivalcomunicator.app.repository.Repository
import com.survivalcomunicator.app.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getDatabase(application)
    private val messageDao = database.messageDao()
    private val userDao = database.userDao()
    private val preferencesManager = PreferencesManager(application)
    
    private suspend fun getNetworkService(): NetworkServiceImpl {
        val url = preferencesManager.getServerUrl() ?: ""
        return NetworkServiceImpl(url)
    }

    private suspend fun getRepository(): Repository {
        return Repository(messageDao, userDao, getNetworkService())
    }
    
    private val _chats = MutableLiveData<List<ChatPreview>>()
    val chats: LiveData<List<ChatPreview>> = _chats
    
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    init {
        loadChats()
        startMessageListener()
    }
    
    private fun getServerUrl(): String {
        // Valor por defecto, en una app real esto vendría de las preferencias
        return "https://example.com/api"
    }
    
    private fun startMessageListener() {
        viewModelScope.launch(Dispatchers.IO) {
            val repository = getRepository()
            repository.startListeningForMessages()
        }
    }
    
    private fun loadChats() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val repository = getRepository()
                    repository.getAllUsers()
                        .flowOn(Dispatchers.IO)
                        .catch { e ->
                            _errorMessage.postValue("Error al cargar los chats: ${e.message}")
                        }
                        .collect { userList ->
                            val chatPreviews = userList.map { user ->
                                val lastMessage = getLastMessageForUser(user.id)
                                val unreadCount = getUnreadCountForUser(user.id)
                                ChatPreview(
                                    userId = user.id,
                                    username = user.username,
                                    lastMessage = lastMessage,
                                    timestamp = System.currentTimeMillis(),
                                    unreadCount = unreadCount
                                )
                            }
                            _chats.postValue(chatPreviews)
                        }
                }
            } catch (e: Exception) {
                _errorMessage.postValue("Error al cargar los chats: ${e.message}")
            }
        }
    }
    
    private suspend fun getLastMessageForUser(userId: String): String {
        // Obtiene el último mensaje real entre el usuario actual y el usuario dado
        val messages = messageDao.getMessagesForUser(userId).firstOrNull() ?: emptyList()
        return messages.firstOrNull()?.content ?: "Sin mensajes"
    }

    private suspend fun getUnreadCountForUser(userId: String): Int {
        // Cuenta los mensajes no leídos enviados al usuario actual por el usuario dado
        val messages = messageDao.getMessagesForUser(userId).firstOrNull() ?: emptyList()
        return messages.count { !it.isRead && it.senderId == userId }
    }
    
    fun addNewContact(username: String) {
        if (username.isBlank()) {
            _errorMessage.value = "El nombre de usuario no puede estar vacío"
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val repository = getRepository()
                val user = repository.findUser(username)
                if (user != null) {
                    // Usuario encontrado, refrescar la lista de chats
                    loadChats()
                } else {
                    _errorMessage.postValue("Usuario no encontrado")
                }
            } catch (e: Exception) {
                _errorMessage.postValue("Error al añadir contacto: ${e.message}")
            }
        }
    }
    
    fun refreshChats() {
        loadChats()
    }
}