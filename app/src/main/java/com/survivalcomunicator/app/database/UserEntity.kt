package com.survivalcomunicator.app.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val publicKey: String,
    val lastSeen: Long,
    val serverAddress: String?
)