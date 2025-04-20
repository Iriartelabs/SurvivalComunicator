package com.survivalcomunicator.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Entidad que representa a un usuario en el sistema
 */
@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String,
    
    @SerializedName("username")
    val username: String,
    
    @SerializedName("public_key")
    val publicKey: String,
    
    @SerializedName("last_seen")
    val lastSeen: Long,
    
    @SerializedName("ip_address")
    val ipAddress: String? = null,
    
    @SerializedName("port")
    val port: Int? = null,
    
    @SerializedName("home_server")
    val homeServer: String? = null,
    
    @SerializedName("device_name")
    val deviceName: String? = null,
    
    // Campos de verificación de seguridad
    @SerializedName("verification_in_progress")
    val verificationInProgress: Boolean = false,
    
    @SerializedName("verified")
    val verified: Boolean = false,
    
    @SerializedName("verification_timestamp")
    val verificationTimestamp: Long? = null,
    
    @SerializedName("verification_method")
    val verificationMethod: String? = null,
    
    // Campo para almacenar la firma digital del usuario (opcional)
    @SerializedName("signature")
    val signature: String? = null
) {
    /**
     * Devuelve true si este usuario ha sido visto recientemente (últimos 15 minutos)
     */
    fun isRecentlyActive(): Boolean {
        val fifteenMinutesAgo = System.currentTimeMillis() - (15 * 60 * 1000)
        return lastSeen > fifteenMinutesAgo
    }
    
    /**
     * Devuelve true si este usuario tiene información de conexión válida
     */
    fun hasValidConnectionInfo(): Boolean {
        return ipAddress != null && port != null && port > 0
    }
    
    /**
     * Genera una representación de texto simple del usuario
     */
    fun toDisplayString(): String {
        val status = when {
            verified -> "✓"
            verificationInProgress -> "…"
            else -> ""
        }
        return "$username $status"
    }
    
    /**
     * Compara este usuario con otro para ver si es el mismo (mismo ID)
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as User
        
        return id == other.id
    }
    
    /**
     * Genera un código hash basado en el ID
     */
    override fun hashCode(): Int {
        return id.hashCode()
    }
}