// app/src/main/java/com/survivalcomunicator/app/data/ContactRepository.kt
package com.survivalcomunicator.app.data

import android.content.Context
import com.survivalcomunicator.app.database.AppDatabase
import com.survivalcomunicator.app.database.UserEntity
import com.survivalcomunicator.app.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactRepository(context: Context) {
    private val userDao = AppDatabase.getDatabase(context).userDao()

    suspend fun getAllUsers(): List<User> = withContext(Dispatchers.IO) {
        userDao.getAllUsers().map { it.toDomain() }
    }

    suspend fun getUserById(userId: String): User? = withContext(Dispatchers.IO) {
        userDao.getUserById(userId)?.toDomain()
    }

    suspend fun getUserByUsername(username: String): User? = withContext(Dispatchers.IO) {
        userDao.getUserByUsername(username)?.toDomain()
    }

    suspend fun insertUser(user: User) = withContext(Dispatchers.IO) {
        userDao.insert(user.toEntity())
    }

    suspend fun updateUser(user: User) = withContext(Dispatchers.IO) {
        userDao.update(user.toEntity())
    }

    suspend fun deleteUser(user: User) = withContext(Dispatchers.IO) {
        userDao.delete(user.toEntity())
    }

    suspend fun deleteUserById(userId: String) = withContext(Dispatchers.IO) {
        userDao.deleteById(userId)
    }

    suspend fun updateConnectionInfo(userId: String, serverAddress: String?, port: Int, lastSeen: Long) = withContext(Dispatchers.IO) {
        userDao.updateConnectionInfo(userId, serverAddress, port, lastSeen)
    }

    suspend fun updateVerificationStatus(userId: String, verificationInProgress: Boolean, verified: Boolean) = withContext(Dispatchers.IO) {
        userDao.updateVerificationStatus(userId, verificationInProgress, verified)
    }

    suspend fun updatePublicKey(userId: String, publicKey: String) = withContext(Dispatchers.IO) {
        userDao.updatePublicKey(userId, publicKey)
    }

    suspend fun getVerifiedUsers(): List<User> = withContext(Dispatchers.IO) {
        userDao.getVerifiedUsers().map { it.toDomain() }
    }

    suspend fun getUsersWithPendingVerification(): List<User> = withContext(Dispatchers.IO) {
        userDao.getUsersWithPendingVerification().map { it.toDomain() }
    }

    suspend fun getRecentlyActiveUsers(threshold: Long): List<User> = withContext(Dispatchers.IO) {
        userDao.getRecentlyActiveUsers(threshold).map { it.toDomain() }
    }

    suspend fun updateLastSeen(userId: String, timestamp: Long) = withContext(Dispatchers.IO) {
        userDao.updateLastSeen(userId, timestamp)
    }

    suspend fun getUserCount(): Int = withContext(Dispatchers.IO) {
        userDao.getUserCount()
    }

    suspend fun getVerifiedUserCount(): Int = withContext(Dispatchers.IO) {
        userDao.getVerifiedUserCount()
    }

    // -- Mapping between Entity and Domain model --
    private fun UserEntity.toDomain() = User(
        id = id,
        username = username,
        publicKey = publicKey,
        serverAddress = serverAddress,
        port = port,
        lastSeen = lastSeen,
        verificationInProgress = verificationInProgress,
        verified = verified
    )

    private fun User.toEntity() = UserEntity(
        id = id,
        username = username,
        publicKey = publicKey,
        lastSeen = lastSeen,
        serverAddress = serverAddress,
        port = port,
        verificationInProgress = verificationInProgress,
        verified = verified
    )
}
