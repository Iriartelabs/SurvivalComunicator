package com.survivalcomunicator.app.data

import android.content.Context
import android.util.Log
import com.survivalcomunicator.app.data.database.SurvivalDatabase
import com.survivalcomunicator.app.data.database.UserDao
import com.survivalcomunicator.app.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repositorio para gestionar los contactos en la base de datos
 */
class ContactRepository(context: Context) {
    private val TAG = "ContactRepository"
    
    private val userDao: UserDao
    
    init {
        val database = SurvivalDatabase.getInstance(context)
        userDao = database.userDao()
    }
    
    /**
     * Obtiene todos los contactos almacenados
     */
    suspend fun getAllContacts(): List<User> = withContext(Dispatchers.IO) {
        return@withContext userDao.getAllUsers()
    }
    
    /**
     * Obtiene un contacto por su ID
     */
    suspend fun getUserById(userId: String): User? = withContext(Dispatchers.IO) {
        return@withContext userDao.getUserById(userId)
    }
    
    /**
     * Obtiene un contacto por su nombre de usuario
     */
    suspend fun getUserByUsername(username: String): User? = withContext(Dispatchers.IO) {
        return@withContext userDao.getUserByUsername(username)
    }
    
    /**
     * Guarda o actualiza un contacto
     */
    suspend fun saveUser(user: User) = withContext(Dispatchers.IO) {
        val existingUser = userDao.getUserById(user.id)
        
        if (existingUser == null) {
            // Nuevo usuario
            userDao.insert(user)
            Log.d(TAG, "Usuario agregado: ${user.username}")
        } else {
            // Actualizar usuario existente
            userDao.update(user)
            Log.d(TAG, "Usuario actualizado: ${user.username}")
        }
    }
    
    /**
     * Actualiza la información de conexión de un usuario
     */
    suspend fun updateUserConnectionInfo(
        userId: String,
        ipAddress: String,
        port: Int,
        lastSeen: Long
    ) = withContext(Dispatchers.IO) {
        userDao.updateConnectionInfo(userId, ipAddress, port, lastSeen)
        Log.d(TAG, "Información de conexión actualizada para usuario $userId")
    }
    
    /**
     * Actualiza el estado de verificación de un contacto
     */
    suspend fun updateVerificationStatus(
        userId: String,
        verificationInProgress: Boolean,
        verified: Boolean
    ) = withContext(Dispatchers.IO) {
        userDao.updateVerificationStatus(userId, verificationInProgress, verified)
        Log.d(TAG, "Estado de verificación actualizado para usuario $userId: verificado=$verified, enProgreso=$verificationInProgress")
    }
    
    /**
     * Actualiza la clave pública de un contacto
     */
    suspend fun updatePublicKey(userId: String, publicKey: String) = withContext(Dispatchers.IO) {
        userDao.updatePublicKey(userId, publicKey)
        Log.d(TAG, "Clave pública actualizada para usuario $userId")
    }
    
    /**
     * Elimina un contacto
     */
    suspend fun deleteUser(userId: String) = withContext(Dispatchers.IO) {
        userDao.deleteById(userId)
        Log.d(TAG, "Usuario eliminado: $userId")
    }
    
    /**
     * Obtiene todos los contactos verificados
     */
    suspend fun getVerifiedContacts(): List<User> = withContext(Dispatchers.IO) {
        return@withContext userDao.getVerifiedUsers()
    }
    
    /**
     * Verifica si un contacto tiene un estado de verificación pendiente
     */
    suspend fun hasVerificationInProgress(userId: String): Boolean = withContext(Dispatchers.IO) {
        val user = userDao.getUserById(userId)
        return@withContext user?.verificationInProgress == true
    }
}