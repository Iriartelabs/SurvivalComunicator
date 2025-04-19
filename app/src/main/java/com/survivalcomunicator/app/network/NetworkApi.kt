package com.survivalcomunicator.app.network

import com.survivalcomunicator.app.models.Message
import com.survivalcomunicator.app.models.User
import retrofit2.http.*

interface NetworkApi {
    @POST("users/register")
    suspend fun registerUser(@Body userData: UserRegistrationRequest): User
    
    @GET("users/find/{username}")
    suspend fun findUser(@Path("username") username: String): UserResponse?
    
    @POST("messages/send")
    suspend fun sendMessage(@Body message: Message): SendMessageResponse
    
    @GET("users/online")
    suspend fun getOnlineUsers(): List<User>
    
    @POST("users/{userId}/status")
    suspend fun updateUserStatus(
        @Path("userId") userId: String,
        @Body status: UserStatusRequest
    ): UserStatusResponse
}

data class UserResponse(
    val user: User
)

data class UserRegistrationRequest(
    val username: String,
    val publicKey: String
)

data class SendMessageResponse(
    val success: Boolean,
    val messageId: String
)

data class UserStatusRequest(
    val online: Boolean
)

data class UserStatusResponse(
    val success: Boolean
)