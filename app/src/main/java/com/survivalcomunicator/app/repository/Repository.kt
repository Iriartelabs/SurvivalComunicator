// Ruta: app/src/main/java/com/survivalcomunicator/app/repository/Repository.kt
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Repository(
    private val messageDao: MessageDao,
    private val userDao: UserDao,
    private val networkService: NetworkService
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Funciones para usuarios
    suspend fun registerUser(username: String, publicKey: String): User {
        return withContext(Dispatchers.IO) {
            val user = networkService.registerUser(username, publicKey)
            saveUser(user)
            user
        }
    }
    
    suspend fun findUser(username: String): User? {
        return withContext(Dispatchers.IO) {
            // Primero buscar en la base de datos local
            val localUser = userDao.getUserByUsername(username)
            if (localUser != null) {
                User(
                    id = localUser.id,
                    username = localUser.username,
                    publicKey = localUser.publicKey,
                    lastSeen = localUser.lastSeen,
                    serverAddress = localUser.serverAddress
                )
            } else {
                // Si no está localmente, buscar en la red
                val remoteUser = networkService.findUser(username)
                if (remoteUser != null) {
                    saveUser(remoteUser)
                }
                remoteUser
            }
        }
    }
    
    // Obtener usuario por ID
    suspend fun getUserById(userId: String): User? {
        val entity = userDao.getUserById(userId)
        return entity?.let {
            User(
                id = it.id,
                username = it.username,
                publicKey = it.publicKey,
                lastSeen = it.lastSeen,
                serverAddress = it.serverAddress
            )
        }
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
    suspend fun sendMessage(message: Message): Boolean {
        return withContext(Dispatchers.IO) {
            try {
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
                val success = networkService.sendMessage(message)
                
                if (success) {
                    // Actualizar el estado del mensaje a "entregado"
                    val updatedEntity = messageEntity.copy(isDelivered = true)
                    messageDao.insertMessage(updatedEntity)
                }
                
                success
            } catch (e: Exception) {
                // Log del error y retornar falso
                false
            }
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
                    type = try {
                        MessageType.valueOf(entity.type)
                    } catch (e: Exception) {
                        MessageType.TEXT // Valor por defecto en caso de error
                    }
                )
            }
        }
    }
    
    // Helpers
    private suspend fun saveUser(user: User) {
        withContext(Dispatchers.IO) {
            val userEntity = UserEntity(
                id = user.id,
                username = user.username,
                publicKey = user.publicKey ?: "", // Usar string vacía si es null
                lastSeen = user.lastSeen,
                serverAddress = user.serverAddress
            )
            userDao.insertUser(userEntity)
        }
    }
    
    // Iniciar escucha de mensajes entrantes
    fun startListeningForMessages() {
        repositoryScope.launch {
            try {
                networkService.receiveMessages()
                    .catch { e -> 
                        // Manejar error pero continuar la escucha
                    }
                    .collect { message ->
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
            } catch (e: Exception) {
                // Si hay un error en la colección, intentamos reiniciar después de un tiempo
                repositoryScope.launch {
                    kotlinx.coroutines.delay(5000) // Esperar 5 segundos antes de reintentar
                    startListeningForMessages() // Reintentar la conexión
                }
            }
        }
    }
}