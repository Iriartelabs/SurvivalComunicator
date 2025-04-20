package com.survivalcomunicator.app.database

import androidx.room.*
import com.survivalcomunicator.app.model.User

/**
 * Objeto de Acceso a Datos (DAO) para la entidad User
 * Proporciona métodos para interactuar con la tabla de usuarios en la base de datos
 */
@Dao
interface UserDao {
    
    /**
     * Obtiene todos los usuarios de la base de datos
     */
    @Query("SELECT * FROM users ORDER BY username")
    suspend fun getAllUsers(): List<User>
    
    /**
     * Obtiene un usuario por su ID
     */
    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: String): User?
    
    /**
     * Obtiene un usuario por su nombre de usuario
     */
    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun getUserByUsername(username: String): User?
    
    /**
     * Inserta un nuevo usuario
     */
    @Insert
    suspend fun insert(user: User)
    
    /**
     * Actualiza un usuario existente
     */
    @Update
    suspend fun update(user: User)
    
    /**
     * Elimina un usuario
     */
    @Delete
    suspend fun delete(user: User)
    
    /**
     * Elimina un usuario por su ID
     */
    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteById(userId: String)
    
    /**
     * Actualiza la información de conexión de un usuario
     */
    @Query("UPDATE users SET ip_address = :ipAddress, port = :port, last_seen = :lastSeen WHERE id = :userId")
    suspend fun updateConnectionInfo(userId: String, ipAddress: String, port: Int, lastSeen: Long)
    
    /**
     * Actualiza el estado de verificación de un usuario
     */
    @Query("UPDATE users SET verification_in_progress = :verificationInProgress, verified = :verified WHERE id = :userId")
    suspend fun updateVerificationStatus(userId: String, verificationInProgress: Boolean, verified: Boolean)
    
    /**
     * Actualiza la clave pública de un usuario
     */
    @Query("UPDATE users SET public_key = :publicKey WHERE id = :userId")
    suspend fun updatePublicKey(userId: String, publicKey: String)
    
    /**
     * Obtiene todos los usuarios verificados
     */
    @Query("SELECT * FROM users WHERE verified = 1 ORDER BY username")
    suspend fun getVerifiedUsers(): List<User>
    
    /**
     * Obtiene usuarios con verificación en progreso
     */
    @Query("SELECT * FROM users WHERE verification_in_progress = 1")
    suspend fun getUsersWithPendingVerification(): List<User>
    
    /**
     * Obtiene usuarios que han sido vistos recientemente (última hora)
     */
    @Query("SELECT * FROM users WHERE last_seen > :threshold ORDER BY last_seen DESC")
    suspend fun getRecentlyActiveUsers(threshold: Long): List<User>
    
    /**
     * Actualiza el timestamp de último avistamiento
     */
    @Query("UPDATE users SET last_seen = :timestamp WHERE id = :userId")
    suspend fun updateLastSeen(userId: String, timestamp: Long)
    
    /**
     * Obtiene la cantidad total de usuarios
     */
    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int
    
    /**
     * Obtiene la cantidad de usuarios verificados
     */
    @Query("SELECT COUNT(*) FROM users WHERE verified = 1")
    suspend fun getVerifiedUserCount(): Int
}