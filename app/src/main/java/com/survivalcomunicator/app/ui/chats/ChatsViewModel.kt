package com.survivalcomunicator.app.ui.chats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.survivalcomunicator.app.App
import com.survivalcomunicator.app.models.Message
import com.survivalcomunicator.app.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = (application as App).repository
    
    private val _chats = MutableLiveData<List<ChatPreview>>()
    val chats: LiveData<List<ChatPreview>> = _chats
    
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    init {
        loadChats()
        repository.startListeningForMessages()
    }
    
    private fun loadChats() {
        viewModelScope.launch {
            try {
                // En una app real, construiríamos chats combinando usuarios y sus últimos mensajes
                // Por simplicidad, usamos datos de ejemplo
                val users = withContext(Dispatchers.IO) {
                    repository.getAllUsers().collect { userList ->
                        val chatPreviews = userList.map { user ->
                            // Para cada usuario, obtener su último mensaje
                            // En una app real, esto sería una consulta específica a la base de datos
                            ChatPreview(
                                userId = user.id,
                                username = user.username,
                                lastMessage = "Último mensaje...", // Placeholder
                                timestamp = System.currentTimeMillis(),
                                unreadCount = 0
                            )
                        }
                        _chats.postValue(chatPreviews)
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error al cargar los chats: ${e.message}"
            }
        }
    }
    
    fun addNewContact(username: String) {
        viewModelScope.launch {
            try {
                val user = repository.findUser(username)
                if (user != null) {
                    // Usuario encontrado, refrescar la lista de chats
                    loadChats()
                } else {
                    _errorMessage.value = "Usuario no encontrado"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error al añadir contacto: ${e.message}"
            }
        }
    }
}