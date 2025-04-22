package com.survivalcomunicator.app.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,
    val username: String,
    // Nota: cambia serverAddress por server_address si es necesario
    // para que coincida con la columna en la base de datos
    @ColumnInfo(name = "server_address")
    val serverAddress: String? = null,
    val port: Int? = null,
    @ColumnInfo(name = "last_seen")
    val lastSeen: Long = 0,

    // Campos para verificación
    @ColumnInfo(name = "public_key") // Asegúrate de que el nombre coincida con la columna
    val publicKey: String = "",
    @ColumnInfo(name = "verification_in_progress")
    val verificationInProgress: Boolean = false,
    @ColumnInfo(name = "verified")
    val verified: Boolean = false
)