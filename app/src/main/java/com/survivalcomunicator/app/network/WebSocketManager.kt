package com.survivalcomunicator.app.network

import android.util.Log
import com.survivalcomunicator.app.models.Message
import com.survivalcomunicator.app.utils.CryptoManager
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.ktor.http.takeFrom
import java.net.ConnectException

object WebSocketManager {

    private var client: HttpClient? = null
    private var session: WebSocketSession? = null
    private var receiveJob: Job? = null
    private var reconnectJob: Job? = null
    private var serverUrl: String = ""
    private var userId: String = ""
    private var cryptoManager: CryptoManager? = null
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 100)
    val messages: SharedFlow<Message> = _messages.asSharedFlow()

    private val reconnectInterval = 5000L // 5 segundos entre intentos de reconexión
    private var isReconnecting = false

    suspend fun connect(serverUrl: String, userId: String, cryptoManager: CryptoManager) {
        if (client != null) return // Ya conectado
        
        this.serverUrl = serverUrl
        this.userId = userId
        this.cryptoManager = cryptoManager

        try {
            connectWebSocket()
        } catch (e: Exception) {
            Log.e("WebSocketManager", "Error al conectar: ${e.message}")
            _connectionState.value = ConnectionState.Error(e.message ?: "Error desconocido")
            startReconnectionProcess()
        }
    }

    private suspend fun connectWebSocket() {
        client = HttpClient(CIO) {
            install(WebSockets)
        }

        val uri = serverUrl.replace("http", "ws").removeSuffix("/") + "/ws/chat/$userId"

        try {
            _connectionState.value = ConnectionState.Connecting
            
            session = client!!.webSocketSession {
                url.takeFrom(uri)
            }

            _connectionState.value = ConnectionState.Connected
            Log.d("WebSocketManager", "Conectado a $uri")

            receiveJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    for (frame in session!!.incoming) {
                        if (frame is Frame.Text) {
                            val encryptedBase64 = frame.readText()
                            try {
                                val decrypted = cryptoManager?.decryptMessage(encryptedBase64)
                                if (decrypted != null) {
                                    val message = Json.decodeFromString<Message>(decrypted)
                                    _messages.emit(message)
                                }
                            } catch (e: Exception) {
                                Log.e("WebSocket", "Error al descifrar: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WebSocket", "Conexión cerrada: ${e.message}")
                    _connectionState.value = ConnectionState.Disconnected
                    startReconnectionProcess()
                }
            }
        } catch (e: ConnectException) {
            Log.e("WebSocketManager", "Error de conexión: ${e.message}")
            _connectionState.value = ConnectionState.Error(e.message ?: "Error de conexión")
            throw e
        } catch (e: Exception) {
            Log.e("WebSocketManager", "Error al establecer sesión WebSocket: ${e.message}")
            _connectionState.value = ConnectionState.Error(e.message ?: "Error desconocido")
            throw e
        }
    }

    private fun startReconnectionProcess() {
        if (isReconnecting) return
        
        isReconnecting = true
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            while (isReconnecting) {
                try {
                    delay(reconnectInterval)
                    Log.d("WebSocketManager", "Intentando reconectar...")
                    connectWebSocket()
                    isReconnecting = false
                    Log.d("WebSocketManager", "Reconexión exitosa")
                } catch (e: Exception) {
                    Log.e("WebSocketManager", "Error en reconexión: ${e.message}")
                }
            }
        }
    }

    suspend fun sendMessage(message: Message, recipientPublicKey: String) {
        val publicKey = cryptoManager?.publicKeyFromBase64(recipientPublicKey)
        if (publicKey == null) {
            Log.e("WebSocketManager", "Clave pública inválida")
            return
        }
        
        val json = Json.encodeToString(message)
        val encrypted = cryptoManager?.encryptMessage(json, publicKey) ?: return
        
        try {
            if (session == null || session?.isActive != true) {
                Log.w("WebSocketManager", "Sesión inactiva, intentando reconectar")
                connectWebSocket()
            }
            
            session?.send(Frame.Text(encrypted))
        } catch (e: Exception) {
            Log.e("WebSocketManager", "Error al enviar mensaje: ${e.message}")
            throw e
        }
    }

    fun disconnect() {
        isReconnecting = false
        reconnectJob?.cancel()
        receiveJob?.cancel()
        session?.cancel()
        client?.close()
        client = null
        session = null
        _connectionState.value = ConnectionState.Disconnected
        Log.d("WebSocketManager", "Desconectado")
    }
    
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
}