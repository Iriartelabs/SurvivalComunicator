package com.survivalcomunicator.app.network

import android.util.Log
import com.survivalcomunicator.app.model.MessageStatusResponse
import com.survivalcomunicator.app.model.ServerResponse
import com.survivalcomunicator.app.model.User
import com.survivalcomunicator.app.model.UserLocation
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.*

/**
 * Interfaz para la API REST del servidor
 */
interface NodeApiService {
    @GET("/api/users/locate/{username}")
    suspend fun locateUser(@Path("username") username: String): Response<UserLocation>
    
    @POST("/api/users/update-location")
    suspend fun updateUserLocation(@Body data: Map<String, Any>): Response<ServerResponse>
    
    @POST("/api/messages/offline")
    suspend fun storeOfflineMessage(@Body data: Map<String, Any>): Response<ServerResponse>
    
    @POST("/api/messages/confirm-receipt")
    suspend fun confirmMessageReceived(@Body data: Map<String, String>): Response<ServerResponse>
    
    @POST("/api/messages/read")
    suspend fun markMessageRead(@Body data: Map<String, String>): Response<ServerResponse>
    
    @POST("/api/messages/status")
    suspend fun checkMessageStatus(@Body data: Map<String, List<String>>): Response<MessageStatusResponse>
    
    @POST("/api/users/online")
    suspend fun notifyUserOnline(@Body data: Map<String, Any>): Response<ServerResponse>
}

/**
 * Interfaz para servicios de red
 */
interface NetworkService {
    suspend fun getUserLocation(username: String): UserLocation?
    suspend fun updateUserLocation(userId: String, ipAddress: String, port: Int): Boolean
    suspend fun storeOfflineMessage(
        senderId: String,
        recipientUsername: String,
        encryptedContent: String,
        messageType: String,
        timestamp: Long
    ): Response<ServerResponse>
    suspend fun confirmMessageReceived(messageId: String): Boolean
    suspend fun markMessageRead(messageId: String): Boolean
    suspend fun checkMessageStatus(messageIds: List<String>): Response<MessageStatusResponse>
    suspend fun notifyUserOnline(username: String, ipAddress: String, port: Int): Response<ServerResponse>
}

/**
 * Implementación del servicio de red que se comunica con el servidor Node.js
 */
class NetworkServiceImpl(baseUrl: String) : NetworkService {
    private val TAG = "NetworkServiceImpl"
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val apiService = retrofit.create(NodeApiService::class.java)
    
    /**
     * Obtiene la ubicación actual de un usuario
     */
    override suspend fun getUserLocation(username: String): UserLocation? {
        return try {
            val response = apiService.locateUser(username)
            if (response.isSuccessful) {
                response.body()
            } else {
                Log.w(TAG, "Error localizando usuario: ${response.code()} - ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en getUserLocation: ${e.message}")
            null
        }
    }
    
    /**
     * Actualiza la ubicación del usuario en el servidor
     */
    override suspend fun updateUserLocation(userId: String, ipAddress: String, port: Int): Boolean {
        return try {
            val data = mapOf(
                "user_id" to userId,
                "ip_address" to ipAddress,
                "port" to port,
                "timestamp" to System.currentTimeMillis()
            )
            
            val response = apiService.updateUserLocation(data)
            
            if (response.isSuccessful) {
                Log.d(TAG, "Ubicación actualizada con éxito")
                true
            } else {
                Log.w(TAG, "Error actualizando ubicación: ${response.code()} - ${response.message()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en updateUserLocation: ${e.message}")
            false
        }
    }
    
    /**
     * Almacena un mensaje para entrega offline en el servidor
     */
    override suspend fun storeOfflineMessage(
        senderId: String,
        recipientUsername: String,
        encryptedContent: String,
        messageType: String,
        timestamp: Long
    ): Response<ServerResponse> {
        val data = mapOf(
            "sender_id" to senderId,
            "recipient_username" to recipientUsername,
            "encrypted_content" to encryptedContent,
            "message_type" to messageType,
            "timestamp" to timestamp
        )
        
        return apiService.storeOfflineMessage(data)
    }
    
    /**
     * Confirma la recepción de un mensaje
     */
    override suspend fun confirmMessageReceived(messageId: String): Boolean {
        return try {
            val data = mapOf("message_id" to messageId)
            
            val response = apiService.confirmMessageReceived(data)
            
            if (response.isSuccessful) {
                Log.d(TAG, "Confirmación de recepción enviada para mensaje $messageId")
                true
            } else {
                Log.w(TAG, "Error confirmando recepción: ${response.code()} - ${response.message()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en confirmMessageReceived: ${e.message}")
            false
        }
    }
    
    /**
     * Marca un mensaje como leído en el servidor
     */
    override suspend fun markMessageRead(messageId: String): Boolean {
        return try {
            val data = mapOf("message_id" to messageId)
            
            val response = apiService.markMessageRead(data)
            
            if (response.isSuccessful) {
                Log.d(TAG, "Mensaje $messageId marcado como leído en servidor")
                true
            } else {
                Log.w(TAG, "Error marcando mensaje como leído: ${response.code()} - ${response.message()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en markMessageRead: ${e.message}")
            false
        }
    }
    
    /**
     * Consulta el estado de entrega de varios mensajes
     */
    override suspend fun checkMessageStatus(messageIds: List<String>): Response<MessageStatusResponse> {
        val data = mapOf("message_ids" to messageIds)
        return apiService.checkMessageStatus(data)
    }
    
    /**
     * Notifica al servidor que un usuario está online
     */
    override suspend fun notifyUserOnline(username: String, ipAddress: String, port: Int): Response<ServerResponse> {
        val data = mapOf(
            "username" to username,
            "ip_address" to ipAddress,
            "port" to port,
            "timestamp" to System.currentTimeMillis()
        )
        
        return apiService.notifyUserOnline(data)
    }
}