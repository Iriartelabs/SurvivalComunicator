package com.survivalcomunicator.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Representa un mensaje de chat en la aplicación
 */
@Entity(tableName = "messages")
data class ChatMessage(
    @PrimaryKey val id: String,
    val senderId: String,
    val recipientId: String,
    val encryptedContent: String,
    val timestamp: Long,
    val messageType: String = "text",
    val receivedViaP2P: Boolean = false,
    val senderName: String? = null,
    val decryptedContent: String? = null
)

/**
 * Estados posibles para un mensaje
 */
enum class MessageStatus {
    SENDING,   // El mensaje se está enviando
    SENT,      // El mensaje fue enviado al servidor o directamente al destinatario
    DELIVERED, // El mensaje fue entregado al destinatario
    READ,      // El mensaje fue leído por el destinatario
    FAILED     // El envío del mensaje falló
}

/**
 * Respuesta del servidor para consultas de estado de mensaje
 */
data class MessageStatusResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("results") val results: List<MessageStatusResult>
)

/**
 * Resultado de estado para un mensaje específico
 */
data class MessageStatusResult(
    @SerializedName("message_id") val messageId: String,
    @SerializedName("status") val status: String,
    @SerializedName("delivered_at") val deliveredAt: Long? = null,
    @SerializedName("read_at") val readAt: Long? = null
)

/**
 * Modelo para la ubicación de un usuario en la red
 */
data class UserLocation(
    @SerializedName("id") val id: String,
    @SerializedName("username") val username: String,
    @SerializedName("public_key") val publicKey: String,
    @SerializedName("ip_address") val ipAddress: String?,
    @SerializedName("port") val port: Int?,
    @SerializedName("last_seen") val lastSeen: Long,
    @SerializedName("home_server") val homeServer: String?
)

/**
 * Respuesta genérica del servidor
 */
data class ServerResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("message_id") val messageId: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("timestamp") val timestamp: Long? = null,
    @SerializedName("error") val error: String? = null
)