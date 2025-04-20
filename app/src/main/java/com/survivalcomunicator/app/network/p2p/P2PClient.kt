package com.survivalcomunicator.app.network.p2p

import android.content.Context
import android.util.Log
import com.survivalcomunicator.app.model.ChatMessage
import com.survivalcomunicator.app.security.CryptoManager
import com.survivalcomunicator.app.utils.SessionManager
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cliente P2P para comunicaciones directas entre dispositivos
 */
class P2PClient(private val context: Context) {
    private val TAG = "P2PClient"
    
    // Constantes
    companion object {
        const val SOCKET_TIMEOUT = 10000  // 10 segundos
        const val HANDSHAKE_TIMEOUT = 5000  // 5 segundos
        const val DEFAULT_PORT = 8765
    }
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cryptoManager = CryptoManager(context)
    private val sessionManager = SessionManager(context)
    
    // Conexiones activas por usuario
    private val activeConnections = ConcurrentHashMap<String, P2PConnection>()
    
    // Callbacks
    private var messageListener: ((ChatMessage) -> Unit)? = null
    private var connectionListener: ((String, Boolean) -> Unit)? = null
    
    // Estado
    private val isRunning = AtomicBoolean(false)
    
    /**
     * Establece un callback para recibir mensajes
     */
    fun setMessageListener(listener: (ChatMessage) -> Unit) {
        messageListener = listener
    }
    
    /**
     * Establece un callback para cambios de estado de conexión
     */
    fun setConnectionListener(listener: (String, Boolean) -> Unit) {
        connectionListener = listener
    }
    
    /**
     * Conecta con un usuario remoto
     */
    fun connect(ipAddress: String, port: Int, userId: String, publicKey: String): Boolean {
        if (activeConnections.containsKey(userId)) {
            Log.d(TAG, "Ya existe una conexión activa con el usuario $userId")
            return true
        }
        
        return try {
            coroutineScope.launch {
                connectAsync(ipAddress, port, userId, publicKey)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando conexión con $ipAddress:$port: ${e.message}")
            false
        }
    }
    
    /**
     * Establece conexión asíncrona con un usuario remoto
     */
    private suspend fun connectAsync(ipAddress: String, port: Int, userId: String, publicKey: String) {
        try {
            // Crear socket
            val socket = Socket()
            val socketAddress = InetSocketAddress(ipAddress, port)
            
            // Establecer timeout para la conexión
            withContext(Dispatchers.IO) {
                socket.connect(socketAddress, SOCKET_TIMEOUT)
            }
            
            Log.d(TAG, "Conexión establecida con $ipAddress:$port")
            
            // Realizar handshake para autenticar
            val success = performHandshake(socket, userId)
            
            if (success) {
                // Crear objeto de conexión
                val connection = P2PConnection(
                    socket = socket,
                    userId = userId,
                    publicKey = publicKey,
                    cryptoManager = cryptoManager
                )
                
                // Almacenar en conexiones activas
                activeConnections[userId] = connection
                
                // Notificar
                connectionListener?.invoke(userId, true)
                
                // Iniciar monitoreo de mensajes entrantes
                launchMessageMonitor(connection)
                
                Log.d(TAG, "Handshake exitoso con $userId")
            } else {
                socket.close()
                Log.e(TAG, "Fallo en handshake con $userId")
                connectionListener?.invoke(userId, false)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error conectando con $ipAddress:$port: ${e.message}")
            connectionListener?.invoke(userId, false)
        }
    }
    
    /**
     * Realiza handshake para autenticar la conexión
     */
    private suspend fun performHandshake(socket: Socket, remoteUserId: String): Boolean {
        return try {
            val handshakeTimeout = withTimeoutOrNull(HANDSHAKE_TIMEOUT.toLong()) {
                val outputStream = socket.getOutputStream()
                val inputStream = socket.getInputStream()
                val writer = BufferedWriter(OutputStreamWriter(outputStream))
                val reader = BufferedReader(InputStreamReader(inputStream))
                
                // Crear mensaje de handshake
                val userId = sessionManager.getUserId() ?: return@withTimeoutOrNull false
                val username = sessionManager.getUsername() ?: return@withTimeoutOrNull false
                val publicKey = sessionManager.getUserPublicKey() ?: return@withTimeoutOrNull false
                
                val handshakeMessage = JSONObject().apply {
                    put("type", "handshake")
                    put("userId", userId)
                    put("username", username)
                    put("publicKey", publicKey)
                    put("timestamp", System.currentTimeMillis())
                }.toString()
                
                // Enviar mensaje de handshake
                writer.write(handshakeMessage)
                writer.newLine()
                writer.flush()
                
                // Esperar respuesta
                val response = reader.readLine()
                
                if (response != null) {
                    try {
                        val jsonResponse = JSONObject(response)
                        val type = jsonResponse.getString("type")
                        val responseUserId = jsonResponse.getString("userId")
                        
                        // Verificar que la respuesta sea del usuario esperado
                        type == "handshake_response" && responseUserId == remoteUserId
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parseando respuesta de handshake: ${e.message}")
                        false
                    }
                } else {
                    false
                }
            }
            
            handshakeTimeout ?: false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en handshake: ${e.message}")
            false
        }
    }
    
    /**
     * Inicia monitoreo de mensajes entrantes para una conexión
     */
    private fun launchMessageMonitor(connection: P2PConnection) {
        coroutineScope.launch {
            try {
                while (isActive && connection.isConnected()) {
                    try {
                        // Leer mensajes entrantes
                        val message = connection.receiveMessage()
                        
                        if (message != null) {
                            // Procesar mensaje recibido
                            processIncomingMessage(message, connection.userId)
                        }
                    } catch (e: SocketTimeoutException) {
                        // Timeout de lectura, esto es normal
                        continue
                    } catch (e: Exception) {
                        Log.e(TAG, "Error leyendo mensajes de ${connection.userId}: ${e.message}")
                        break
                    }
                }
                
                // Cerrar conexión si salimos del bucle
                closeConnection(connection.userId)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error en monitor de mensajes para ${connection.userId}: ${e.message}")
                closeConnection(connection.userId)
            }
        }
    }
    
    /**
     * Procesa un mensaje entrante
     */
    private fun processIncomingMessage(message: String, senderId: String) {
        try {
            val jsonMessage = JSONObject(message)
            when (val type = jsonMessage.getString("type")) {
                "chat_message" -> {
                    val messageId = jsonMessage.getString("messageId")
                    val encryptedContent = jsonMessage.getString("content")
                    val timestamp = jsonMessage.getLong("timestamp")
                    
                    // Crear objeto de mensaje
                    val chatMessage = ChatMessage(
                        id = messageId,
                        senderId = senderId,
                        recipientId = sessionManager.getUserId() ?: "",
                        encryptedContent = encryptedContent,
                        timestamp = timestamp,
                        messageType = "text",
                        receivedViaP2P = true
                    )
                    
                    // Notificar
                    messageListener?.invoke(chatMessage)
                    
                    // Enviar confirmación de recepción
                    sendDeliveryReceipt(senderId, messageId)
                }
                
                "delivery_receipt" -> {
                    val messageId = jsonMessage.getString("messageId")
                    Log.d(TAG, "Recibido acuse de recibo para mensaje $messageId")
                    // Aquí podríamos actualizar el estado del mensaje
                }
                
                "read_receipt" -> {
                    val messageId = jsonMessage.getString("messageId")
                    Log.d(TAG, "Recibido acuse de lectura para mensaje $messageId")
                    // Aquí podríamos actualizar el estado del mensaje a 'leído'
                }
                
                "ping" -> {
                    // Responder al ping
                    sendPong(senderId)
                }
                
                "pong" -> {
                    // Actualizar tiempo de último contacto
                    activeConnections[senderId]?.updateLastSeen()
                }
                
                else -> {
                    Log.d(TAG, "Tipo de mensaje desconocido: $type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando mensaje entrante: ${e.message}")
        }
    }
    
    /**
     * Envía un mensaje directamente a otro dispositivo
     */
    fun sendMessage(messageId: String, recipientId: String, encryptedContent: String, timestamp: Long): Boolean {
        val connection = activeConnections[recipientId]
        
        if (connection == null || !connection.isConnected()) {
            Log.d(TAG, "No hay conexión activa con $recipientId")
            return false
        }
        
        return try {
            // Crear mensaje en formato JSON
            val jsonMessage = JSONObject().apply {
                put("type", "chat_message")
                put("messageId", messageId)
                put("content", encryptedContent)
                put("timestamp", timestamp)
            }.toString()
            
            // Enviar mensaje
            connection.sendMessage(jsonMessage)
            
            Log.d(TAG, "Mensaje enviado a $recipientId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando mensaje a $recipientId: ${e.message}")
            false
        }
    }
    
    /**
     * Envía un mensaje directamente a una dirección IP y puerto específicos
     * útil para cuando no tenemos una conexión establecida
     */
    fun sendDirectMessage(ipAddress: String, port: Int, messageId: String, encryptedContent: String, timestamp: Long): Boolean {
        return try {
            coroutineScope.launch {
                try {
                    // Crear socket temporal
                    val socket = Socket()
                    val socketAddress = InetSocketAddress(ipAddress, port)
                    
                    withContext(Dispatchers.IO) {
                        socket.connect(socketAddress, SOCKET_TIMEOUT)
                    }
                    
                    // Crear mensaje en formato JSON
                    val jsonMessage = JSONObject().apply {
                        put("type", "chat_message")
                        put("messageId", messageId)
                        put("content", encryptedContent)
                        put("timestamp", timestamp)
                        put("senderId", sessionManager.getUserId())
                        put("senderName", sessionManager.getUsername())
                    }.toString()
                    
                    // Enviar mensaje
                    val outputStream = socket.getOutputStream()
                    val writer = BufferedWriter(OutputStreamWriter(outputStream))
                    
                    writer.write(jsonMessage)
                    writer.newLine()
                    writer.flush()
                    
                    // Cerrar socket
                    socket.close()
                    
                    Log.d(TAG, "Mensaje directo enviado a $ipAddress:$port")
                } catch (e: Exception) {
                    Log.e(TAG, "Error enviando mensaje directo a $ipAddress:$port: ${e.message}")
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando envío directo a $ipAddress:$port: ${e.message}")
            false
        }
    }
    
    /**
     * Envía acuse de recibo para un mensaje
     */
    private fun sendDeliveryReceipt(recipientId: String, messageId: String) {
        val connection = activeConnections[recipientId]
        
        if (connection == null || !connection.isConnected()) {
            Log.d(TAG, "No hay conexión activa con $recipientId para enviar acuse")
            return
        }
        
        try {
            // Crear mensaje de acuse
            val jsonReceipt = JSONObject().apply {
                put("type", "delivery_receipt")
                put("messageId", messageId)
                put("timestamp", System.currentTimeMillis())
            }.toString()
            
            // Enviar acuse
            connection.sendMessage(jsonReceipt)
            
            Log.d(TAG, "Acuse de recibo enviado para mensaje $messageId")
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando acuse de recibo: ${e.message}")
        }
    }
    
    /**
     * Envía confirmación de lectura para un mensaje
     */
    fun sendReadReceipt(messageId: String, recipientId: String) {
        val connection = activeConnections[recipientId]
        
        if (connection == null || !connection.isConnected()) {
            Log.d(TAG, "No hay conexión activa con $recipientId para enviar confirmación de lectura")
            return
        }
        
        try {
            // Crear mensaje de confirmación
            val jsonReceipt = JSONObject().apply {
                put("type", "read_receipt")
                put("messageId", messageId)
                put("timestamp", System.currentTimeMillis())
            }.toString()
            
            // Enviar confirmación
            connection.sendMessage(jsonReceipt)
            
            Log.d(TAG, "Confirmación de lectura enviada para mensaje $messageId")
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando confirmación de lectura: ${e.message}")
        }
    }
    
    /**
     * Envía un ping para verificar la conexión
     */
    fun ping(userId: String) {
        val connection = activeConnections[userId]
        
        if (connection == null || !connection.isConnected()) {
            Log.d(TAG, "No hay conexión activa con $userId para enviar ping")
            return
        }
        
        try {
            // Crear mensaje de ping
            val jsonPing = JSONObject().apply {
                put("type", "ping")
                put("timestamp", System.currentTimeMillis())
            }.toString()
            
            // Enviar ping
            connection.sendMessage(jsonPing)
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando ping a $userId: ${e.message}")
        }
    }
    
    /**
     * Envía un pong en respuesta a un ping
     */
    private fun sendPong(recipientId: String) {
        val connection = activeConnections[recipientId]
        
        if (connection == null || !connection.isConnected()) {
            return
        }
        
        try {
            // Crear mensaje de pong
            val jsonPong = JSONObject().apply {
                put("type", "pong")
                put("timestamp", System.currentTimeMillis())
            }.toString()
            
            // Enviar pong
            connection.sendMessage(jsonPong)
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando pong a $recipientId: ${e.message}")
        }
    }
    
    /**
     * Cierra una conexión activa
     */
    fun closeConnection(userId: String) {
        try {
            val connection = activeConnections.remove(userId)
            
            connection?.close()
            
            connectionListener?.invoke(userId, false)
            
            Log.d(TAG, "Conexión cerrada con $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error cerrando conexión con $userId: ${e.message}")
        }
    }
    
    /**
     * Desconecta todos los usuarios
     */
    fun disconnect() {
        try {
            isRunning.set(false)
            
            // Cerrar todas las conexiones
            val userIds = ArrayList(activeConnections.keys)
            
            for (userId in userIds) {
                closeConnection(userId)
            }
            
            activeConnections.clear()
            
            Log.d(TAG, "Todas las conexiones P2P cerradas")
        } catch (e: Exception) {
            Log.e(TAG, "Error desconectando todas las conexiones: ${e.message}")
        }
    }
    
    /**
     * Libera recursos
     */
    fun cleanup() {
        disconnect()
        coroutineScope.cancel()
    }
    
    /**
     * Verifica si hay una conexión activa con un usuario
     */
    fun isConnected(userId: String): Boolean {
        val connection = activeConnections[userId]
        return connection != null && connection.isConnected()
    }
    
    /**
     * Obtiene la lista de usuarios conectados
     */
    fun getConnectedUsers(): List<String> {
        return activeConnections.keys.toList()
    }
}