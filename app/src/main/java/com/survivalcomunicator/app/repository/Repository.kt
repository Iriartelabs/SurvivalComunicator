package com.survivalcomunicator.app.repository

import com.survivalcomunicator.app.database.MessageDao
import com.survivalcomunicator.app.database.MessageEntity
import com.survivalcomunicator.app.database.UserDao
import com.survivalcomunicator.app.database.UserEntity
import com.survivalcomunicator.app.models.Message
import com.survivalcomunicator.app.models.MessageType
import com.survivalcomunicator.app.models.User
import com.survivalcomunicator.app.network.NetworkService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class Repository(
    private val messageDao: MessageDao,
    private val userDao: UserDao,
    private val networkService: NetworkService
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    // Funciones para usuarios
    suspend fun registerUser(username: String, publicKey: String): User {
        val user = networkService.registerUser(username, publicKey)
        saveUser(user)
        return user
    }
    
    suspend fun findUser(username: String): User? {
        // Primero buscar en la base de datos local
        val localUser = userDao.getUserByUsername(username)
        if (localUser != null) {
            return User(
                id = localUser.id,
                username = localUser.username,
                publicKey = localUser.publicKey,
                lastSeen = localUser.lastSeen,
                serverAddress = localUser.serverAddress
            )
        }
        
        // Si no está localmente, buscar en la red
        val remoteUser = networkService.findUser(username)
        if (remoteUser != null) {
            saveUser(remoteUser)
        }
        return remoteUser
    }
    
    fun getAllUsers(): Flow<List<User>> {
        return userDao.getAllUsers().map { entities ->
            entities.map { entity ->
                User(
                    id = entity.id,
                    username = entity.username,
                    publicKey = entity.publicKey,
                    lastSeen = entity.lastSeen,
                    serverAddress = entity.serverAddress
                )
            }
        }
    }
    
    // Funciones para mensajes
    suspend fun sendMessage(message: Message) {
        // Guardar localmente
        val messageEntity = MessageEntity(
            id = message.id,
            senderId = message.senderId,
            recipientId = message.recipientId,
            content = message.content,
            timestamp = message.timestamp,
            isRead = message.isRead,
            isDelivered = message.isDelivered,
            type = message.type.name
        )
        messageDao.insertMessage(messageEntity)
        
        // Enviar a través de la red
        coroutineScope.launch {
            networkService.sendMessage(message)
        }
    }
    
    fun getMessagesForUser(userId: String): Flow<List<Message>> {
        return messageDao.getMessagesForUser(userId).map { entities ->
            entities.map { entity ->
                Message(
                    id = entity.id,
                    senderId = entity.senderId,
                    recipientId = entity.recipientId,
                    content = entity.content,
                    timestamp = entity.timestamp,
                    isRead = entity.isRead,
                    isDelivered = entity.isDelivered,
                    type = MessageType.valueOf(entity.type)
                )
            }
        }
    }
    
    // Helpers
    private suspend fun saveUser(user: User) {
        val userEntity = UserEntity(
            id = user.id,
            username = user.username,
            publicKey = user.publicKey,
            lastSeen = user.lastSeen,
            serverAddress = user.serverAddress
        )
        userDao.insertUser(userEntity)
    }
    
    // Iniciar escucha de mensajes entrantes
    fun startListeningForMessages() {
        coroutineScope.launch {
            networkService.receiveMessages().collect { message ->
                // Guardar mensaje recibido en la base de datos local
                val messageEntity = MessageEntity(
                    id = message.id,
                    senderId = message.senderId,
                    recipientId = message.recipientId,
                    content = message.content,
                    timestamp = message.timestamp,
                    isRead = false,
                    isDelivered = true,
                    type = message.type.name
                )
                messageDao.insertMessage(messageEntity)
            }
        }
    }
}