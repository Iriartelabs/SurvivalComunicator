package com.survivalcomunicator.app.network

import com.survivalcomunicator.app.models.Message
import com.survivalcomunicator.app.models.User
import kotlinx.coroutines.flow.Flow

interface NetworkService {
    suspend fun registerUser(username: String, publicKey: String): User
    suspend fun findUser(username: String): User?
    suspend fun sendMessage(message: Message): Boolean
    fun receiveMessages(): Flow<Message>
    suspend fun updateUserStatus(userId: String, online: Boolean): Boolean
    suspend fun getOnlineUsers(): List<User>
}