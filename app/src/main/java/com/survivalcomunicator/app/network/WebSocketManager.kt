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

object WebSocketManager {

    private var client: HttpClient? = null
    private var session: WebSocketSession? = null
    private var receiveJob: Job? = null

    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 100)
    val messages: SharedFlow<Message> = _messages.asSharedFlow()

    suspend fun connect(serverUrl: String, userId: String, cryptoManager: CryptoManager) {
        if (client != null) return // Ya conectado

        client = HttpClient(CIO) {
            install(WebSockets)
        }

        val uri = serverUrl.replace("http", "ws").removeSuffix("/") + "/ws/chat/$userId"

        session = client!!.webSocketSession {
            url.takeFrom(uri)
        }

        Log.d("WebSocketManager", "Conectado a $uri")

        receiveJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                for (frame in session!!.incoming) {
                    if (frame is Frame.Text) {
                        val encryptedBase64 = frame.readText()
                        try {
                            val decrypted = cryptoManager.decryptMessage(encryptedBase64)
                            val message = Json.decodeFromString<Message>(decrypted)
                            _messages.emit(message)
                        } catch (e: Exception) {
                            Log.e("WebSocket", "Error al descifrar: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("WebSocket", "Conexión cerrada: ${e.message}")
            }
        }
    }

    suspend fun sendMessage(message: Message, recipientPublicKey: String, cryptoManager: CryptoManager) {
        val publicKey = cryptoManager.publicKeyFromBase64(recipientPublicKey)
        val json = Json.encodeToString(message)
        val encrypted = cryptoManager.encryptMessage(json, publicKey)
        session?.send(Frame.Text(encrypted))
    }

    fun disconnect() {
        receiveJob?.cancel()
        session?.cancel()
        client?.close()
        client = null
    }
}
