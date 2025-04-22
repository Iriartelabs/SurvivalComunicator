package com.survivalcomunicator.app.network

import com.survivalcomunicator.app.data.models.User
import com.survivalcomunicator.app.data.models.MessageStatus

// Modelo para ubicación de usuario
data class UserLocation(
    val userId: String,
    val ipAddress: String,
    val port: Int,
    val lastSeen: Long
)

// Interfaz única y completa
interface NetworkService {
    suspend fun registerUser(username: String, publicKey: String): User
    suspend fun getUserLocation(username: String): UserLocation?
    suspend fun updateUserLocation(userId: String, ipAddress: String, port: Int): Boolean
    suspend fun storeOfflineMessage(recipientId: String, encryptedContent: ByteArray, messageId: String): Boolean
    suspend fun confirmMessageReceived(messageId: String): Boolean
    suspend fun markMessageRead(messageId: String): Boolean
    suspend fun checkMessageStatus(messageId: String): MessageStatus
    suspend fun notifyUserOnline(userId: String): Boolean
    suspend fun getServerUrl(): String
}

// Implementación completa
class NetworkServiceImpl : NetworkService {
    private val apiService: ApiService = RetrofitClient.createApiService()
    
    override suspend fun registerUser(username: String, publicKey: String): User {
        val response = apiService.registerUser(
            mapOf(
                "username" to username, 
                "public_key" to publicKey
            )
        )
        
        if (response.isSuccessful) {
            val body = response.body()
            return User(
                id = body?.get("id") as String,
                username = body["username"] as String,
                publicKey = body["public_key"] as String
            )
        } else {
            throw Exception("Error registering user: ${response.message()}")
        }
    }
    
    override suspend fun getUserLocation(username: String): UserLocation? {
        val response = apiService.getUserLocation(username)
        if (response.isSuccessful) {
            val body = response.body()
            return body?.let {
                UserLocation(
                    userId = it["id"] as String,
                    ipAddress = it["ip_address"] as String,
                    port = (it["port"] as Double).toInt(),
                    lastSeen = (it["last_seen"] as Double).toLong()
                )
            }
        }
        return null
    }
    
    override suspend fun updateUserLocation(userId: String, ipAddress: String, port: Int): Boolean {
        val response = apiService.updateUserLocation(
            mapOf(
                "user_id" to userId,
                "ip_address" to ipAddress,
                "port" to port
            )
        )
        return response.isSuccessful
    }
    
    override suspend fun storeOfflineMessage(recipientId: String, encryptedContent: ByteArray, messageId: String): Boolean {
        val base64Content = android.util.Base64.encodeToString(encryptedContent, android.util.Base64.NO_WRAP)
        val response = apiService.storeOfflineMessage(
            mapOf(
                "recipient_id" to recipientId,
                "content" to base64Content,
                "message_id" to messageId
            )
        )
        return response.isSuccessful
    }
    
    override suspend fun confirmMessageReceived(messageId: String): Boolean {
        val response = apiService.confirmMessageReceived(
            mapOf("message_id" to messageId)
        )
        return response.isSuccessful
    }
    
    override suspend fun markMessageRead(messageId: String): Boolean {
        val response = apiService.markMessageRead(
            mapOf("message_id" to messageId)
        )
        return response.isSuccessful
    }
    
    override suspend fun checkMessageStatus(messageId: String): MessageStatus {
        val response = apiService.checkMessageStatus(messageId)
        if (response.isSuccessful) {
            val status = response.body()?.get("status") as String
            return MessageStatus.valueOf(status)
        }
        return MessageStatus.FAILED
    }
    
    override suspend fun notifyUserOnline(userId: String): Boolean {
        val response = apiService.notifyUserOnline(
            mapOf("user_id" to userId)
        )
        return response.isSuccessful
    }
    
    override suspend fun getServerUrl(): String {
        return RetrofitClient.BASE_URL
    }
}

// Interfaz de API para Retrofit
interface ApiService {
    @POST("api/users/register")
    suspend fun registerUser(@Body user: Map<String, Any>): Response<Map<String, Any>>
    
    @GET("api/users/locate/{username}")
    suspend fun getUserLocation(@Path("username") username: String): Response<Map<String, Any>>
    
    @POST("api/users/update-location")
    suspend fun updateUserLocation(@Body locationData: Map<String, Any>): Response<Map<String, Any>>
    
    @POST("api/messages/store-offline")
    suspend fun storeOfflineMessage(@Body messageData: Map<String, Any>): Response<Map<String, Any>>
    
    @POST("api/messages/confirm-received")
    suspend fun confirmMessageReceived(@Body data: Map<String, Any>): Response<Map<String, Any>>
    
    @POST("api/messages/mark-read")
    suspend fun markMessageRead(@Body data: Map<String, Any>): Response<Map<String, Any>>
    
    @GET("api/messages/status/{messageId}")
    suspend fun checkMessageStatus(@Path("messageId") messageId: String): Response<Map<String, Any>>
    
    @POST("api/users/notify-online")
    suspend fun notifyUserOnline(@Body data: Map<String, Any>): Response<Map<String, Any>>
}