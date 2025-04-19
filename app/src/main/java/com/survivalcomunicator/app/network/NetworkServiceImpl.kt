// Ruta: app/src/main/java/com/survivalcomunicator/app/network/NetworkServiceImpl.kt
package com.survivalcomunicator.app.network

import android.util.Log
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
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder

class NetworkServiceImpl(private val serverUrl: String) : NetworkService {
    
    private val TAG = "NetworkServiceImpl"
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    private val retrofit by lazy {
        Log.d(TAG, "Base URL: '$serverUrl'")
        Retrofit.Builder()
            .baseUrl(serverUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    private val api by lazy {
        retrofit.create(NetworkApi::class.java)
    }
    
    override suspend fun registerUser(username: String, publicKey: String): User {
        val request = UserRegistrationRequest(username, publicKey)
        return api.registerUser(request)
    }
    
    override suspend fun findUser(username: String): User? {
        try {
            Log.d(TAG, "Buscando usuario: $username")
            val response = api.findUser(username)
            Log.d(TAG, "Respuesta: $response")
            return response?.user
        } catch (e: Exception) {
            Log.e(TAG, "Error al buscar usuario: ${e.message}", e)
            throw e
        }
    }
    
    override suspend fun sendMessage(message: Message): Boolean {
        val response = api.sendMessage(message)
        return response.success
    }
    
    override fun receiveMessages(): Flow<Message> = flow {
        // Polling REST: ajusta el endpoint según tu backend
        // Por ejemplo, podrías necesitar un endpoint como /messages/receive?userId=...
        // Aquí se deja como ejemplo, deberás adaptar la llamada real
        while (true) {
            // val messages = api.getNewMessages(userId) // <-- Implementa este método en NetworkApi y backend
            // for (msg in messages) emit(msg)
            kotlinx.coroutines.delay(5000) // Poll cada 5 segundos
        }
    }
    
    override suspend fun updateUserStatus(userId: String, online: Boolean): Boolean {
        val request = UserStatusRequest(online)
        val response = api.updateUserStatus(userId, request)
        return response.success
    }
    
    override suspend fun getOnlineUsers(): List<User> {
        return api.getOnlineUsers()
    }
}