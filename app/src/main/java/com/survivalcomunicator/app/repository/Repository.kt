// Ruta: app/src/main/java/com/survivalcomunicator/app/repository/Repository.kt
package com.survivalcomunicator.app.repository

import com.survivalcomunicator.app.database.MessageDao
import com.survivalcomunicator.app.database.UserDao
import com.survivalcomunicator.app.models.User
import com.survivalcomunicator.app.network.NetworkService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class Repository(
    private val messageDao: MessageDao,
    private val userDao: UserDao,
    private val networkService: NetworkService
) {
    // Registrar usuario en el servidor
    suspend fun registerUser(username: String, publicKey: String): User {
        return withContext(Dispatchers.IO) {
            val user = networkService.registerUser(username, publicKey)
            saveUser(user)
            user
        }
    }

    // Buscar usuario por nombre
    suspend fun findUser(username: String): User? {
        return withContext(Dispatchers.IO) {
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

    // Obtener todos los usuarios
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

    // Obtener mensajes de un usuario
    fun getMessagesForUser(userId: String) = messageDao.getMessagesForUser(userId)

    // Guardar usuario localmente
    private suspend fun saveUser(user: User) {
        val entity = com.survivalcomunicator.app.database.UserEntity(
            id = user.id,
            username = user.username,
            publicKey = user.publicKey  ?: "",
            lastSeen = user.lastSeen,
            serverAddress = user.serverAddress
        )
        userDao.insertUser(entity)
    }
}