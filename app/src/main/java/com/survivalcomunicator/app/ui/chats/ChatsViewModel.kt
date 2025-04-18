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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getDatabase(application)
    private val messageDao = database.messageDao()
    private val userDao = database.userDao()
    private val preferencesManager = PreferencesManager(application)
    private val networkService = NetworkServiceImpl(getServerUrl())
    private val repository = Repository(messageDao, userDao, networkService)
    
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
            repository.startListeningForMessages()
        }
    }
    
    private fun loadChats() {
        viewModelScope.launch {
            try {
                // Utilizamos withContext para cambiar al hilo de IO para la operación inicial
                withContext(Dispatchers.IO) {
                    repository.getAllUsers()
                        .flowOn(Dispatchers.IO)  // Aseguramos que la recolección se haga en el hilo de IO
                        .catch { e -> 
                            _errorMessage.postValue("Error al cargar los chats: ${e.message}")
                        }
                        .collect { userList ->
                            // Procesamos cada usuario para obtener la información del chat
                            val chatPreviews = userList.map { user ->
                                // En una app real, aquí obtendrías el último mensaje
                                ChatPreview(
                                    userId = user.id,
                                    username = user.username,
                                    lastMessage = getLastMessageForUser(user.id),
                                    timestamp = System.currentTimeMillis(),
                                    unreadCount = getUnreadCountForUser(user.id)
                                )
                            }
                            // Usamos postValue para actualizar el LiveData desde un hilo secundario
                            _chats.postValue(chatPreviews)
                        }
                }
            } catch (e: Exception) {
                _errorMessage.postValue("Error al cargar los chats: ${e.message}")
            }
        }
    }
    
    // Método simulado para obtener el último mensaje
    private fun getLastMessageForUser(userId: String): String {
        // En una app real, esto vendría de la base de datos
        return "Último mensaje..."
    }
    
    // Método simulado para obtener el conteo de mensajes no leídos
    private fun getUnreadCountForUser(userId: String): Int {
        // En una app real, esto vendría de la base de datos
        return 0
    }
    
    fun addNewContact(username: String) {
        if (username.isBlank()) {
            _errorMessage.value = "El nombre de usuario no puede estar vacío"
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
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