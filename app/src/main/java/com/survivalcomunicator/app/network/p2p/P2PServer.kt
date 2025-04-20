package com.survivalcomunicator.app.network.p2p

import android.content.Context
import android.util.Log
import com.survivalcomunicator.app.model.ChatMessage
import com.survivalcomunicator.app.security.CryptoManager
import com.survivalcomunicator.app.utils.SessionManager
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Servidor P2P que escucha conexiones entrantes
 */
class P2PServer(private val context: Context) {
    private val TAG = "P2PServer"
    
    // Constantes
    companion object {
        const val DEFAULT_PORT = 8765  // Puerto por defecto
        const val CONNECTION_TIMEOUT = 10000  // 10 segundos
    }
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cryptoManager = CryptoManager(context)
    private val sessionManager = SessionManager(context)
    
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    
    private var port = DEFAULT_PORT
    
    // Callbacks
    private var connectionCallback: ((Socket, String, String) -> Unit)? = null
    private var messageCallback: ((ChatMessage) -> Unit)? = null
    
    /**
     * Inicia el servidor en el puerto especificado
     */
    fun start(
        serverPort: Int = DEFAULT_PORT,
        onConnection: ((Socket, String, String) -> Unit)? = null
    ): Boolean {
        if (isRunning.get()) {
            Log.d(TAG, "El servidor ya está en ejecución")
            return true
        }
        
        port = serverPort
        connectionCallback = onConnection
        
        return try {
            // Crear socket del servidor
            serverSocket = ServerSocket(port)
            isRunning.set(true)
            
            Log.d(TAG, "Servidor P2P iniciado en puerto $port")
            
            // Iniciar proceso de escucha
            coroutineScope.launch {
                acceptConnections()
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando servidor P2P: ${e.message}")
            isRunning.set(false)
            false
        }
    }
    
    /**
     * Establece un callback para recibir mensajes
     */
    fun setMessageCallback(callback: (ChatMessage) -> Unit) {
        messageCallback = callback
    }
    
    /**
     * Bucle principal para aceptar conexiones
     */
    private suspend fun acceptConnections() {
        withContext(Dispatchers.IO) {
            try {
                while (isRunning.get() && serverSocket != null && !serverSocket!!.isClosed) {
                    try {
                        // Aceptar nueva conexión
                        val clientSocket = serverSocket!!.accept()
                        
                        // Manejar conexión en un nuevo coroutine
                        launch {
                            handleConnection(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (isRunning.get()) {
                            Log.e(TAG, "Error aceptando conexión: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e(TAG, "Error en bucle de aceptación: ${e.message}")
                }
            } finally {
                stop()
            }
        }
    }
    
    /**
     * Maneja una conexión entrante
     */
    private suspend fun handleConnection(socket: Socket) {
        try {
            // Configurar timeout
            socket.soTimeout = CONNECTION_TIMEOUT
            
            // Procesar handshake
            val result = performHandshake(socket)
            
            if (result != null) {
                val (userId, username, publicKey) = result
                
                Log.d(TAG, "Handshake exitoso con $username ($userId)")
                
                // Notificar callback de conexión si existe
                connectionCallback?.invoke(socket, userId, publicKey)
                
                // Si no hay callback, manejar directamente los mensajes
                if (connectionCallback == null) {
                    processMessages(socket, userId, publicKey)
                }
            } else {
                // Handshake fallido
                Log.d(TAG, "Handshake fallido, cerrando conexión")
                socket.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error manejando conexión: ${e.message}")
            try { socket.close() } catch (e2: Exception) {}
        }
    }
    
    /**
     * Realiza handshake para autenticar la conexión entrante
     */
    private suspend fun performHandshake(socket: Socket): Triple<String, String, String>? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = socket.getInputStream()
                val outputStream = socket.getOutputStream()
                
                val reader = BufferedReader(InputStreamReader(inputStream))
                val writer = BufferedWriter(OutputStreamWriter(outputStream))
                
                // Leer mensaje de handshake
                val handshakeMessage = withTimeoutOrNull(5000L) {
                    reader.readLine()
                } ?: return@withContext null
                
                // Parsear mensaje
                val jsonHandshake = JSONObject(handshakeMessage)
                
                if (jsonHandshake.getString("type") != "handshake") {
                    return@withContext null
                }
                
                val remoteUserId = jsonHandshake.getString("userId")
                val remoteUsername = jsonHandshake.getString("username")
                val remotePublicKey = jsonHandshake.getString("publicKey")
                
                // Enviar respuesta
                val responseMessage = JSONObject().apply {
                    put("type", "handshake_response")
                    put("userId", sessionManager.getUserId())
                    put("username", sessionManager.getUsername())
                    put("publicKey", sessionManager.getUserPublicKey())
                    put("timestamp", System.currentTimeMillis())
                }.toString()
                
                writer.write(responseMessage)
                writer.newLine()
                writer.flush()
                
                Triple(remoteUserId, remoteUsername, remotePublicKey)
            } catch (e: Exception) {
                Log.e(TAG, "Error en handshake: ${e.message}")
                null
            }
        }
    }
    
    /**
     * Procesa mensajes entrantes después del handshake
     */
    private suspend fun processMessages(socket: Socket, senderId: String, senderPublicKey: String) {
        withContext(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                
                while (isRunning.get() && !socket.isClosed) {
                    try {
                        // Leer mensaje
                        val messageStr = reader.readLine() ?: break
                        
                        // Parsear y procesar mensaje
                        processIncomingMessage(messageStr, senderId, senderPublicKey, socket)
                    } catch (e: Exception) {
                        if (e is InterruptedException) throw e
                        Log.e(TAG, "Error leyendo mensaje: ${e.message}")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando mensajes: ${e.message}")
            } finally {
                try { socket.close() } catch (e: Exception) {}
            }
        }
    }
    
    /**
     * Procesa un mensaje entrante
     */
    private fun processIncomingMessage(
        messageStr: String, 
        senderId: String, 
        senderPublicKey: String,
        socket: Socket
    ) {
        try {
            val jsonMessage = JSONObject(messageStr)
            when (val type = jsonMessage.getString("type")) {
                "chat_message" -> {
                    // Mensaje de chat
                    val messageId = jsonMessage.getString("messageId")
                    val encryptedContent = jsonMessage.getString("content")
                    val timestamp = jsonMessage.getLong("timestamp")
                    
                    // Obtener nombre del remitente si está disponible
                    val senderName = if (jsonMessage.has("senderName")) {
                        jsonMessage.getString("senderName")
                    } else {
                        null
                    }
                    
                    // Crear objeto de mensaje
                    val chatMessage = ChatMessage(
                        id = messageId,
                        senderId = senderId,
                        recipientId = sessionManager.getUserId() ?: "",
                        encryptedContent = encryptedContent,
                        timestamp = timestamp,
                        messageType = "text",
                        receivedViaP2P = true,
                        senderName = senderName
                    )
                    
                    // Notificar
                    messageCallback?.invoke(chatMessage)
                    
                    // Enviar confirmación de recepción
                    sendDeliveryReceipt(socket, messageId)
                }
                
                "ping" -> {
                    // Responder ping
                    sendPong(socket)
                }
                
                else -> {
                    Log.d(TAG, "Tipo de mensaje desconocido: $type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando mensaje: ${e.message}")
        }
    }
    
    /**
     * Envía acuse de recibo para un mensaje
     */
    private fun sendDeliveryReceipt(socket: Socket, messageId: String) {
        try {
            // Crear mensaje
            val receiptMessage = JSONObject().apply {
                put("type", "delivery_receipt")
                put("messageId", messageId)
                put("timestamp", System.currentTimeMillis())
            }.toString()
            
            // Enviar acuse
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
            writer.write(receiptMessage)
            writer.newLine()
            writer.flush()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando acuse de recibo: ${e.message}")
        }
    }
    
    /**
     * Envía respuesta a un ping
     */
    private fun sendPong(socket: Socket) {
        try {
            // Crear mensaje
            val pongMessage = JSONObject().apply {
                put("type", "pong")
                put("timestamp", System.currentTimeMillis())
            }.toString()
            
            // Enviar pong
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
            writer.write(pongMessage)
            writer.newLine()
            writer.flush()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando pong: ${e.message}")
        }
    }
    
    /**
     * Detiene el servidor
     */
    fun stop() {
        if (!isRunning.get()) {
            return
        }
        
        isRunning.set(false)
        
        try {
            serverSocket?.close()
            serverSocket = null
            
            Log.d(TAG, "Servidor P2P detenido")
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo servidor P2P: ${e.message}")
        }
    }
    
    /**
     * Libera recursos
     */
    fun cleanup() {
        stop()
        coroutineScope.cancel()
    }
    
    /**
     * Verifica si el servidor está en ejecución
     */
    fun isRunning(): Boolean {
        return isRunning.get() && serverSocket != null && !serverSocket!!.isClosed
    }
    
    /**
     * Obtiene el puerto en el que está escuchando el servidor
     */
    fun getPort(): Int {
        return port
    }
}