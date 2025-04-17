package com.survivalcomunicator.app.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)
    
    @Query("SELECT * FROM messages WHERE (senderId = :userId OR recipientId = :userId) ORDER BY timestamp DESC")
    fun getMessagesForUser(userId: String): Flow<List<MessageEntity>>
    
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>
    
    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)
}