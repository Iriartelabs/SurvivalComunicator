package com.survivalcomunicator.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Database(
    entities = [
        UserEntity::class,
        MessageEntity::class,
        PendingMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SurvivalDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun messageDao(): MessageDao
    abstract fun pendingMessageDao(): PendingMessageDao

    companion object {
        @Volatile
        private var INSTANCE: SurvivalDatabase? = null

        fun getDatabase(context: Context): SurvivalDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SurvivalDatabase::class.java,
                    "survival_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Entidades
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val publicKey: String,
    val ipAddress: String? = null,
    val port: Int? = null,
    val lastSeen: Long = 0,
    val homeServer: String? = null,
    val isVerified: Boolean = false
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val content: String,
    val senderId: String,
    val recipientId: String,
    val timestamp: Long,
    val status: MessageStatus,
    val isEncrypted: Boolean
)

@Entity(tableName = "pending_messages")
data class PendingMessageEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val recipientId: String,
    val encryptedContent: ByteArray,
    val timestamp: Long,
    val attempts: Int = 0,
    val lastAttempt: Long = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PendingMessageEntity

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

// Convertidores para tipos Room
class Converters {
    @TypeConverter
    fun fromMessageStatus(status: MessageStatus): String {
        return status.name
    }

    @TypeConverter
    fun toMessageStatus(value: String): MessageStatus {
        return enumValueOf(value)
    }
}