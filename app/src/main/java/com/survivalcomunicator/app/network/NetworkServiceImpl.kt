// Ruta: app/src/main/java/com/survivalcomunicator/app/network/NetworkServiceImpl.kt
package com.survivalcomunicator.app.network

import com.survivalcomunicator.app.models.Message
import com.survivalcomunicator.app.models.MessageType
import com.survivalcomunicator.app.models.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

class NetworkServiceImpl(private val serverUrl: String) : NetworkService {
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(serverUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    private val api by lazy {
        retrofit.create(NetworkApi::class.java)
    }
    
    override suspend fun registerUser(username: String, publicKey: String): User {
        // En una implementación real, esta llamada registraría al usuario en el servidor
        // Simulación mejorada que al menos proporciona un ID único para cada usuario
        return User(
            id = UUID.randomUUID().toString(),
            username = username,
            publicKey = publicKey,
            serverAddress = serverUrl
        )
    }
    
    override suspend fun findUser(username: String): User? {
        // Simulación mejorada para pruebas
        // Siempre encuentra un usuario con el nombre dado para facilitar el desarrollo
        return if (username.isNotBlank()) {
            User(
                id = UUID.nameUUIDFromBytes(username.toByteArray()).toString(),
                username = username,
                publicKey = "simulated_public_key_for_$username",
                serverAddress = serverUrl
            )
        } else {
            null
        }
    }
    
    override suspend fun sendMessage(message: Message): Boolean {
        // Simulamos un pequeño retraso para imitar la latencia de red
        delay(300)
        return true
    }
    
    override fun receiveMessages(): Flow<Message> = flow {
        // En una implementación real, esto se conectaría a un WebSocket o similar
        // Por ahora, emitimos un mensaje de simulación cada 30 segundos para probar
        while (true) {
            delay(30000) // Cada 30 segundos
            val simulatedMessage = Message(
                id = UUID.randomUUID().toString(),
                senderId = "simulated_sender",
                recipientId = "current_user_id", // Asumiendo que este es el ID del usuario actual
                content = "Este es un mensaje simulado para probar la recepción",
                timestamp = System.currentTimeMillis(),
                isDelivered = true,
                type = MessageType.TEXT
            )
            emit(simulatedMessage)
        }
    }
    
    override suspend fun updateUserStatus(userId: String, online: Boolean): Boolean {
        delay(200) // Pequeño retraso para simular
        return true
    }
    
    override suspend fun getOnlineUsers(): List<User> {
        // Devolvemos una lista de usuarios simulados para pruebas
        return listOf(
            User(
                id = "online_user_1",
                username = "Usuario Online 1",
                publicKey = "public_key_1",
                lastSeen = System.currentTimeMillis()
            ),
            User(
                id = "online_user_2",
                username = "Usuario Online 2",
                publicKey = "public_key_2",
                lastSeen = System.currentTimeMillis()
            )
        )
    }
}