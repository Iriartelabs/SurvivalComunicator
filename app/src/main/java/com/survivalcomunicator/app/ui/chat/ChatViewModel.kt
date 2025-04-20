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
import com.survivalcomunicator.app.security.CryptoManager
import com.survivalcomunicator.app.utils.NetworkUtils
import com.survivalcomunicator.app.utils.SessionManager
import kotlinx.coroutines.*
import java.util.*

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "ChatViewModel"
    
    private val messageRepository: MessageRepository = MessageRepository(application)
    private val cryptoManager: CryptoManager = CryptoManager(application)
    private val webSocketManager: WebSocketManager = WebSocketManager.getInstance()
    private val sessionManager: SessionManager = SessionManager(application)
    
    private var p2pClient: P2PClient? = null
    private var offlineMessagingManager: OfflineMessagingManager? = null
    
    // LiveData para mensajes en el chat
    private val _messages = MutableLiveData<List<ChatMessage>>(listOf())
    val messages: LiveData<List<ChatMessage>> = _messages
    
    // LiveData para el estado de conexión
    private val _connectionStatus = MutableLiveData<ConnectionStatus>(ConnectionStatus.DISCONNECTED)
    val connectionStatus: LiveData<ConnectionStatus> = _connectionStatus
    
    // LiveData para el estado de los mensajes (enviado, entregado, leído)
    private val _messageStatuses = MutableLiveData<Map<String, MessageStatus>>(mapOf())
    val messageStatuses: LiveData<Map<String, MessageStatus>> = _messageStatuses
    
    // Estado del usuario actual
    private var currentContact: User? = null
    private var p2pConnectionActive = false
    
    // Modo de conexión actual
    private var connectionMode = ConnectionMode.AUTOMATIC
    
    private val viewModelScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        initOfflineMessagingManager()
        observeMessageStatusUpdates()
    }
    
    /**
     * Inicializa el gestor de mensajes offline
     */
    private fun initOfflineMessagingManager() {
        val userId = sessionManager.getUserId() ?: return
        val username = sessionManager.getUsername() ?: return
        
        offlineMessagingManager = OfflineMessagingManager(
            context = getApplication(),
            networkService = sessionManager.getNetworkService(),
            cryptoManager = cryptoManager,
            userId = userId,
            username = username
        )
    }
    
    /**
     * Observa cambios en el estado de entrega de mensajes
     */
    private fun observeMessageStatusUpdates() {
        offlineMessagingManager?.messageStatusUpdates?.observeForever { (messageId, statusStr) ->
            val status = when (statusStr) {
                "delivered" -> MessageStatus.DELIVERED
                "read" -> MessageStatus.READ
                else -> MessageStatus.SENT
            }
            
            updateMessageStatus(messageId, status)
        }
    }
    
    /**
     * Inicia la conexión para chat con un contacto
     */
    fun startChatWith(contact: User) {
        viewModelScope.launch {
            currentContact = contact
            
            // Establecer conexión según el modo seleccionado
            when (connectionMode) {
                ConnectionMode.P2P_ONLY -> connectP2P(contact)
                ConnectionMode.WEBSOCKET_ONLY -> connectWebSocket()
                ConnectionMode.AUTOMATIC -> connectAutomatic(contact)
            }
            
            // Cargar mensajes del historial
            loadChatHistory(contact.id)
            
            // Sincronizar estados de mensajes
            syncMessageStatuses()
        }
    }
    
    /**
     * Modo de conexión automática: intenta P2P primero, luego WebSocket si falla
     */
    private suspend fun connectAutomatic(contact: User) {
        _connectionStatus.postValue(ConnectionStatus.CONNECTING)
        
        // Intentar conexión P2P primero
        val p2pSuccess = connectP2P(contact)
        
        if (p2pSuccess) {
            // P2P exitoso
            _connectionStatus.postValue(ConnectionStatus.CONNECTED_P2P)
        } else {
            // Si P2P falla, intentar WebSocket
            connectWebSocket()
            _connectionStatus.postValue(ConnectionStatus.CONNECTED_WEBSOCKET)
        }
    }
    
    /**
     * Establece conexión P2P con un contacto
     */
    private suspend fun connectP2P(contact: User): Boolean {
        try {
            _connectionStatus.postValue(ConnectionStatus.CONNECTING)
            
            // Inicializar cliente P2P si no existe
            if (p2pClient == null) {
                p2pClient = P2PClient(getApplication())
                
                // Configurar callback para mensajes recibidos
                p2pClient?.setMessageListener { message ->
                    handleIncomingMessage(message)
                }
            }
            
            // Obtener ubicación actual del contacto
            val locationInfo = sessionManager.getNetworkService().getUserLocation(contact.username)
            
            if (locationInfo == null || locationInfo.ipAddress.isNullOrEmpty() || locationInfo.port == null) {
                Log.d(TAG, "No se pudo obtener información de ubicación para ${contact.username}")
                return false
            }
            
            // Intentar conexión P2P
            val connected = p2pClient?.connect(
                locationInfo.ipAddress,
                locationInfo.port,
                contact.id,
                contact.publicKey
            ) ?: false
            
            p2pConnectionActive = connected
            
            if (connected) {
                _connectionStatus.postValue(ConnectionStatus.CONNECTED_P2P)
                Log.d(TAG, "Conexión P2P establecida con ${contact.username}")
            } else {
                _connectionStatus.postValue(ConnectionStatus.P2P_FAILED)
                Log.d(TAG, "No se pudo establecer conexión P2P con ${contact.username}")
            }
            
            return connected
        } catch (e: Exception) {
            Log.e(TAG, "Error estableciendo conexión P2P: ${e.message}")
            _connectionStatus.postValue(ConnectionStatus.P2P_FAILED)
            return false
        }
    }
    
    /**
     * Establece conexión vía WebSocket (fallback)
     */
    private fun connectWebSocket() {
        try {
            _connectionStatus.postValue(ConnectionStatus.CONNECTING)
            
            webSocketManager.connect(
                serverUrl = sessionManager.getServerUrl(),
                userId = sessionManager.getUserId() ?: return,
                onOpen = {
                    _connectionStatus.postValue(ConnectionStatus.CONNECTED_WEBSOCKET)
                    Log.d(TAG, "Conexión WebSocket establecida")
                },
                onMessage = { message ->
                    handleIncomingMessage(message)
                },
                onClose = {
                    _connectionStatus.postValue(ConnectionStatus.DISCONNECTED)
                    Log.d(TAG, "Conexión WebSocket cerrada")
                },
                onError = { error ->
                    _connectionStatus.postValue(ConnectionStatus.ERROR)
                    Log.e(TAG, "Error en WebSocket: $error")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error estableciendo conexión WebSocket: ${e.message}")
            _connectionStatus.postValue(ConnectionStatus.ERROR)
        }
    }
    
    /**
     * Carga el historial de conversación con un contacto
     */
    private suspend fun loadChatHistory(contactId: String) {
        try {
            val chatHistory = messageRepository.getChatMessages(contactId)
            _messages.postValue(chatHistory)
            
            // Cargar estados de mensajes
            val messageIds = chatHistory
                .filter { it.senderId == sessionManager.getUserId() }
                .map { it.id }
            
            if (messageIds.isNotEmpty()) {
                val statuses = offlineMessagingManager?.getMessageStatuses(messageIds) ?: mapOf()
                val statusMap = messageIds.associateWith { messageId ->
                    when (statuses[messageId]) {
                        "delivered" -> MessageStatus.DELIVERED
                        "read" -> MessageStatus.READ
                        else -> MessageStatus.SENT
                    }
                }
                
                _messageStatuses.postValue(statusMap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando historial de chat: ${e.message}")
        }
    }
    
    /**
     * Sincroniza estados de mensajes con el servidor
     */
    private suspend fun syncMessageStatuses() {
        try {
            offlineMessagingManager?.syncMessageStatusesWithServer()
        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando estados de mensajes: ${e.message}")
        }
    }
    
    /**
     * Envía un mensaje al contacto actual
     */
    fun sendMessage(content: String) {
        if (content.isBlank() || currentContact == null) return
        
        viewModelScope.launch {
            try {
                val messageId = UUID.randomUUID().toString()
                val timestamp = System.currentTimeMillis()
                val userId = sessionManager.getUserId() ?: return@launch
                
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
                    timestamp = timestamp,
                    messageType = "text"
                )
                
                // Guardar en la base de datos local
                messageRepository.saveMessage(message)
                
                // Actualizar UI
                val currentList = _messages.value?.toMutableList() ?: mutableListOf()
                currentList.add(message)
                _messages.postValue(currentList)
                
                // Intentar enviar por la conexión activa
                val delivered = when {
                    p2pConnectionActive -> {
                        p2pClient?.sendMessage(
                            message.id,
                            message.recipientId,
                            message.encryptedContent,
                            message.timestamp
                        ) ?: false
                    }
                    webSocketManager.isConnected() -> {
                        webSocketManager.sendMessage(
                            recipientId = message.recipientId,
                            encryptedContent = message.encryptedContent,
                            messageId = message.id
                        )
                        true
                    }
                    else -> false
                }
                
                // Actualizar estado inicial del mensaje
                updateMessageStatus(messageId, MessageStatus.SENT)
                
                // Si la entrega inmediata falla, almacenar para entrega posterior
                if (!delivered) {
                    offlineMessagingManager?.storeMessageForLaterDelivery(
                        recipientId = currentContact!!.id,
                        recipientUsername = currentContact!!.username,
                        encryptedContent = encryptedContent,
                        messageType = "text"
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando mensaje: ${e.message}")
            }
        }
    }
    
    /**
     * Maneja un mensaje entrante
     */
    private fun handleIncomingMessage(message: ChatMessage) {
        viewModelScope.launch {
            try {
                // Guardar mensaje en la base de datos
                messageRepository.saveMessage(message)
                
                // Actualizar UI
                val currentList = _messages.value?.toMutableList() ?: mutableListOf()
                
                // Evitar duplicados
                if (!currentList.any { it.id == message.id }) {
                    currentList.add(message)
                    _messages.postValue(currentList)
                }
                
                // Marcar como recibido si es un mensaje offline
                if (message.messageType == "offline_delivery") {
                    offlineMessagingManager?.handleOfflineMessageReceived(
                        messageId = message.id,
                        senderId = message.senderId,
                        senderUsername = message.senderName ?: "",
                        encryptedContent = message.encryptedContent,
                        timestamp = message.timestamp
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando mensaje entrante: ${e.message}")
            }
        }
    }
    
    /**
     * Actualiza el estado de un mensaje
     */
    private fun updateMessageStatus(messageId: String, status: MessageStatus) {
        val currentStatuses = _messageStatuses.value?.toMutableMap() ?: mutableMapOf()
        currentStatuses[messageId] = status
        _messageStatuses.postValue(currentStatuses)
    }
    
    /**
     * Cambia el modo de conexión
     */
    fun setConnectionMode(mode: ConnectionMode) {
        if (connectionMode != mode) {
            connectionMode = mode
            
            // Reconectar con el nuevo modo si hay un contacto activo
            currentContact?.let { contact ->
                viewModelScope.launch {
                    // Desconectar conexiones actuales
                    disconnectAll()
                    
                    // Reconectar con el nuevo modo
                    when (mode) {
                        ConnectionMode.P2P_ONLY -> connectP2P(contact)
                        ConnectionMode.WEBSOCKET_ONLY -> connectWebSocket()
                        ConnectionMode.AUTOMATIC -> connectAutomatic(contact)
                    }
                }
            }
        }
    }
    
    /**
     * Desconecta todas las conexiones activas
     */
    private fun disconnectAll() {
        if (p2pConnectionActive) {
            p2pClient?.disconnect()
            p2pConnectionActive = false
        }
        
        if (webSocketManager.isConnected()) {
            webSocketManager.disconnect()
        }
        
        _connectionStatus.postValue(ConnectionStatus.DISCONNECTED)
    }
    
    /**
     * Intenta reconectar si se perdió la conexión
     */
    fun reconnect() {
        currentContact?.let { contact ->
            viewModelScope.launch {
                // Intentar reconexión según el modo seleccionado
                when (connectionMode) {
                    ConnectionMode.P2P_ONLY -> connectP2P(contact)
                    ConnectionMode.WEBSOCKET_ONLY -> connectWebSocket()
                    ConnectionMode.AUTOMATIC -> connectAutomatic(contact)
                }
            }
        }
    }
    
    /**
     * Verifica el estado de entrega de un mensaje
     */
    fun refreshMessageStatus(messageId: String) {
        viewModelScope.launch {
            try {
                val status = offlineMessagingManager?.getMessageStatus(messageId) ?: "unknown"
                
                val messageStatus = when (status) {
                    "delivered" -> MessageStatus.DELIVERED
                    "read" -> MessageStatus.READ
                    else -> MessageStatus.SENT
                }
                
                updateMessageStatus(messageId, messageStatus)
            } catch (e: Exception) {
                Log.e(TAG, "Error actualizando estado de mensaje: ${e.message}")
            }
        }
    }
    
    /**
     * Marca un mensaje como leído e informa al remitente
     */
    fun markMessageAsRead(messageId: String, senderId: String) {
        viewModelScope.launch {
            try {
                // Verificar conexión activa para enviar confirmación
                when {
                    p2pConnectionActive -> {
                        p2pClient?.sendReadReceipt(messageId, senderId)
                    }
                    webSocketManager.isConnected() -> {
                        webSocketManager.sendReadReceipt(messageId, senderId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando confirmación de lectura: ${e.message}")
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        disconnectAll()
        viewModelScope.cancel()
        offlineMessagingManager?.cleanup()
    }
    
    /**
     * Enumeración de posibles estados de conexión
     */
    enum class ConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED_P2P,
        CONNECTED_WEBSOCKET,
        P2P_FAILED,
        ERROR
    }
    
    /**
     * Modos de conexión disponibles
     */
    enum class ConnectionMode {
        AUTOMATIC,      // Intenta P2P primero, luego WebSocket
        P2P_ONLY,       // Solo usa conexión P2P
        WEBSOCKET_ONLY  // Solo usa conexión WebSocket
    }
}