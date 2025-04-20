// app/src/main/java/com/survivalcomunicator/app/database/UserDao.kt
package com.survivalcomunicator.app.database

import androidx.room.*

@Dao
interface UserDao {

    @Query("SELECT * FROM users ORDER BY username")
    suspend fun getAllUsers(): List<UserEntity>

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserEntity)

    @Update
    suspend fun update(user: UserEntity)

    @Delete
    suspend fun delete(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteById(userId: String)

    @Query("""
        UPDATE users
        SET server_address           = :serverAddress,
            port                     = :port,
            last_seen                = :lastSeen
        WHERE id = :userId
    """)
    suspend fun updateConnectionInfo(
        userId: String,
        serverAddress: String?,
        port: Int,
        lastSeen: Long
    )

    @Query("""
        UPDATE users
        SET verification_in_progress = :verificationInProgress,
            verified                 = :verified
        WHERE id = :userId
    """)
    suspend fun updateVerificationStatus(
        userId: String,
        verificationInProgress: Boolean,
        verified: Boolean
    )

    @Query("UPDATE users SET public_key = :publicKey WHERE id = :userId")
    suspend fun updatePublicKey(userId: String, publicKey: String)

    @Query("SELECT * FROM users WHERE verified = 1 ORDER BY username")
    suspend fun getVerifiedUsers(): List<UserEntity>

    @Query("SELECT * FROM users WHERE verification_in_progress = 1")
    suspend fun getUsersWithPendingVerification(): List<UserEntity>

    @Query("SELECT * FROM users WHERE last_seen > :threshold ORDER BY last_seen DESC")
    suspend fun getRecentlyActiveUsers(threshold: Long): List<UserEntity>

    @Query("UPDATE users SET last_seen = :timestamp WHERE id = :userId")
    suspend fun updateLastSeen(userId: String, timestamp: Long)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Query("SELECT COUNT(*) FROM users WHERE verified = 1")
    suspend fun getVerifiedUserCount(): Int
}
