package com.survivalcomunicator.app.ui.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.survivalcomunicator.app.data.MessageRepository
import com.survivalcomunicator.app.model.ChatMessage
import com.survivalcomunicator.app.model.MessageStatus
import com.survivalcomunicator.app.model.User
import com.survivalcomunicator.app.network.OfflineMessagingManager
import com.survivalcomunicator.app.network.P2PClient
import com.survivalcomunicator.app.network.WebSocketManager
import com.survivalcomunicator.app.network.p2p.LocalNetworkDiscovery
import com.survivalcomunicator.app.security.CryptoManager
import com.survivalcomunicator.app.utils.NetworkUtils
import com.survivalcomunicator.app.utils.SessionManager
import kotlinx.coroutines.*
import java.util.*

/**
 * Extensión del ChatViewModel para soportar el modo de red local
 * Esta clase contiene solo las funciones relacionadas con el modo local
 * para facilitar la integración con el ChatViewModel existente
 */
class ChatViewModelWithLocalMode(application: Application) : AndroidViewModel(application) {
    private val TAG = "ChatViewModel_LocalMode"
    
    private val messageRepository: MessageRepository = MessageRepository(application)
    private val cryptoManager: CryptoManager = CryptoManager(application)
    private val sessionManager: SessionManager = SessionManager(application)
    
    private var localNetworkDiscovery: LocalNetworkDiscovery? = null
    
    // LiveData para el estado del modo local
    private val _localModeStatus = MutableLiveData<LocalModeStatus>()
    val localModeStatus: LiveData<LocalModeStatus> = _localModeStatus
    
    // Estado actual
    private var localModeEnabled = false
    private var currentContact: User? = null
    
    private val viewModelScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        // Inicializar el descubrimiento en red local si está habilitado
        if (sessionManager.isLocalModeEnabled()) {
            initializeLocalNetworkDiscovery()
        }
    }
    
    /**
     * Inicializa el descubrimiento en red local
     */
    private fun initializeLocalNetworkDiscovery() {
        val userId = sessionManager.getUserId()
        val username = sessionManager.getUsername()
        val publicKey = sessionManager.getUserPublicKey()
        
        if (userId != null && username != null && publicKey != null) {
            localNetworkDiscovery = LocalNetworkDiscovery(
                getApplication(),
                userId,
                username,
                publicKey
            )
            
            localNetworkDiscovery?.initialize()
            
            // Si el modo local está activo, iniciar servicio
            if (sessionManager.isLocalModeEnabled()) {
                startLocalService()
            }
        } else {
            Log.e(TAG, "No se pudo inicializar el descubrimiento local: datos de usuario incompletos")
        }
    }
    
    /**
     * Activa el modo de red local para la sesión de chat actual
     */
    fun enableLocalMode(contact: User) {
        // Guardar estado
        localModeEnabled = true
        currentContact = contact
        
        // Verificar si el modo local ya está inicializado
        if (localNetworkDiscovery == null) {
            initializeLocalNetworkDiscovery()
        }
        
        // Verificar conexión WiFi
        if (!NetworkUtils.isWifiConnected(getApplication())) {
            _localModeStatus.postValue(LocalModeStatus.NO_WIFI)
            return
        }
        
        // Iniciar servicio si no está activo
        startLocalService()
        
        // Iniciar descubrimiento si no está activo
        if (localNetworkDiscovery?.discoveryState?.value == LocalNetworkDiscovery.DiscoveryState.INACTIVE) {
            localNetworkDiscovery?.startDiscovery()
        }
        
        // Buscar el dispositivo del contacto en la red local
        findContactInLocalNetwork(contact)
    }
    
    /**
     * Inicia el servicio local
     */
    private fun startLocalService() {
        localNetworkDiscovery?.startLocalService()
        _localModeStatus.postValue(LocalModeStatus.STARTING)
    }
    
    /**
     * Busca un contacto en la red local
     */
    private fun findContactInLocalNetwork(contact: User) {
        viewModelScope.launch {
            _localModeStatus.postValue(LocalModeStatus.SEARCHING_CONTACT)
            
            // Esperar hasta 10 segundos para ver si aparece el contacto
            var foundContact = false
            var attempts = 0
            
            while (!foundContact && attempts < 10) {
                delay(1000) // Esperar 1 segundo entre comprobaciones
                
                val discoveredUsers = localNetworkDiscovery?.getDiscoveredUsers() ?: emptyList()
                
                // Verificar si el contacto ha sido descubierto
                for (user in discoveredUsers) {
                    if (user.username == contact.username || user.id == contact.id) {
                        // Actualizar información de conexión del contacto
                        updateContactConnectionInfo(contact, user)
                        foundContact = true
                        break
                    }
                }
                
                attempts++
            }
            
            if (foundContact) {
                _localModeStatus.postValue(LocalModeStatus.CONTACT_FOUND)
            } else {
                _localModeStatus.postValue(LocalModeStatus.CONTACT_NOT_FOUND)
            }
        }
    }
    
    /**
     * Actualiza la información de conexión de un contacto
     */
    private fun updateContactConnectionInfo(contact: User, discoveredUser: User) {
        viewModelScope.launch {
            try {
                // Crear usuario actualizado con la información descubierta
                val updatedContact = contact.copy(
                    ipAddress = discoveredUser.ipAddress,
                    port = discoveredUser.port,
                    lastSeen = discoveredUser.lastSeen
                )
                
                // Guardar en la base de datos
                messageRepository.updateUserConnectionInfo(
                    userId = contact.id,
                    ipAddress = discoveredUser.ipAddress,
                    port = discoveredUser.port,
                    lastSeen = discoveredUser.lastSeen
                )
                
                // Actualizar referencia local
                currentContact = updatedContact
                
                Log.d(TAG, "Información de contacto actualizada para modo local: ${discoveredUser.ipAddress}:${discoveredUser.port}")
            } catch (e: Exception) {
                Log.e(TAG, "Error actualizando información de contacto: ${e.message}")
            }
        }
    }
    
    /**
     * Envía un mensaje en modo local
     */
    fun sendMessageLocalMode(content: String): String? {
        if (!localModeEnabled || currentContact == null) {
            return null
        }
        
        // Verificar si tenemos información de conexión
        if (currentContact?.ipAddress.isNullOrEmpty() || currentContact?.port == null) {
            _localModeStatus.postValue(LocalModeStatus.MISSING_CONNECTION_INFO)
            return null
        }
        
        try {
            val messageId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            val userId = sessionManager.getUserId() ?: return null
            
            // Cifrar el mensaje con la clave pública del destinatario
            val encryptedContent = cryptoManager.encryptMessage(
                content, 
                currentContact!!.publicKey
            )
            
            // Crear objeto de mensaje
            val message = ChatMessage(
                id = messageId,
                senderId = userId,
                recipientId = currentContact!!.id,
                encryptedContent = encryptedContent,
                decryptedContent = content, // Para mostrar inmediatamente
                timestamp = timestamp,
                messageType = "text",
                receivedViaP2P = true
            )
            
            // Guardar en la base de datos local
            messageRepository.saveMessage(message)
            
            // Enviar por P2P en modo local
            viewModelScope.launch {
                val p2pClient = P2PClient(getApplication())
                
                val success = p2pClient.sendDirectMessage(
                    currentContact!!.ipAddress!!,
                    currentContact!!.port!!,
                    messageId,
                    encryptedContent,
                    timestamp
                )
                
                if (success) {
                    Log.d(TAG, "Mensaje enviado con éxito en modo local")
                } else {
                    Log.e(TAG, "Error enviando mensaje en modo local")
                    _localModeStatus.postValue(LocalModeStatus.SEND_FAILED)
                }
            }
            
            return messageId
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando mensaje en modo local: ${e.message}")
            _localModeStatus.postValue(LocalModeStatus.ERROR)
            return null
        }
    }
    
    /**
     * Desactiva el modo de red local
     */
    fun disableLocalMode() {
        localModeEnabled = false
        
        // No detener la búsqueda si está configurada globalmente
        if (!sessionManager.isLocalModeEnabled()) {
            localNetworkDiscovery?.stopDiscovery()
            localNetworkDiscovery?.stopLocalService()
        }
        
        _localModeStatus.postValue(LocalModeStatus.DISABLED)
    }
    
    /**
     * Escanea activamente dispositivos en la red local
     */
    fun scanLocalDevices() {
        if (localNetworkDiscovery?.discoveryState?.value != LocalNetworkDiscovery.DiscoveryState.ACTIVE) {
            localNetworkDiscovery?.startDiscovery()
            _localModeStatus.postValue(LocalModeStatus.SCANNING)
        }
        
        // Si tenemos un contacto activo, intentar encontrarlo de nuevo
        currentContact?.let { contact ->
            findContactInLocalNetwork(contact)
        }
    }
    
    /**
     * Limpia los recursos al finalizar
     */
    override fun onCleared() {
        super.onCleared()
        
        // Solo detener si no está configurado globalmente
        if (!sessionManager.isLocalModeEnabled()) {
            localNetworkDiscovery?.cleanup()
        }
        
        viewModelScope.cancel()
    }
    
    /**
     * Estados del modo local
     */
    enum class LocalModeStatus {
        DISABLED,              // Modo local desactivado
        STARTING,              // Iniciando modo local
        ACTIVE,                // Modo local activo
        SCANNING,              // Escaneando dispositivos
        SEARCHING_CONTACT,     // Buscando contacto específico
        CONTACT_FOUND,         // Contacto encontrado en red local
        CONTACT_NOT_FOUND,     // Contacto no encontrado
        SEND_FAILED,           // Error enviando mensaje
        MISSING_CONNECTION_INFO, // Falta información de conexión
        NO_WIFI,               // Sin conexión WiFi
        ERROR                  // Error general
    }
}