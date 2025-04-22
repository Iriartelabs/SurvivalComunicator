package com.survivalcomunicator.app.data.models

import java.util.UUID

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

enum class ConnectionType {
    NONE,
    P2P,
    WEBSOCKET
}

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val senderId: String,
    val recipientId: String,
    val timestamp: Long = System.currentTimeMillis(),
    var status: MessageStatus = MessageStatus.SENDING,
    val recipientPublicKey: String? = null,
    val isEncrypted: Boolean = true
) {
    // Constructor para crear mensaje de texto simple
    companion object {
        fun createTextMessage(
            content: String,
            senderId: String,
            recipientId: String,
            recipientPublicKey: String? = null
        ): Message {
            return Message(
                content = content,
                senderId = senderId,
                recipientId = recipientId,
                recipientPublicKey = recipientPublicKey
            )
        }
    }
}