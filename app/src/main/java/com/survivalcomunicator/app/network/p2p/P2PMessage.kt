package com.survivalcomunicator.app.network.p2p

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Tipos de mensajes para la comunicación P2P.
 */
enum class P2PMessageType {
    HANDSHAKE,              // Solicitud inicial de conexión
    HANDSHAKE_RESPONSE,     // Respuesta a la solicitud de conexión
    MESSAGE,                // Mensaje de chat (cifrado)
    FILE_TRANSFER_REQUEST,  // Solicitud para enviar un archivo
    FILE_TRANSFER_RESPONSE, // Respuesta a la solicitud de archivo
    FILE_CHUNK,             // Fragmento de un archivo en transferencia
    PING,                   // Comprobación de conectividad
    PONG,                   // Respuesta a ping
    DISCONNECT              // Notificación de desconexión
}

/**
 * Clase que representa un mensaje intercambiado entre dispositivos en la red P2P.
 * Incluye soporte para serialización, firma y verificación.
 */
data class P2PMessage(
    val type: P2PMessageType,
    val senderId: String,
    val recipientId: String? = null,
    val payload: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val signature: String = ""
) {
    companion object {
        /**
         * Deserializa un mensaje desde un array de bytes.
         */
        fun deserialize(data: ByteArray): P2PMessage {
            val jsonString = String(data, StandardCharsets.UTF_8)
            val json = JSONObject(jsonString)
            
            val type = P2PMessageType.valueOf(json.getString("type"))
            val senderId = json.getString("senderId")
            val recipientId = if (json.has("recipientId")) json.getString("recipientId") else null
            val timestamp = json.getLong("timestamp")
            val signature = json.getString("signature")
            
            // Parsear payload
            val payloadJson = json.getJSONObject("payload")
            val payload = mutableMapOf<String, Any>()
            
            val keys = payloadJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = payloadJson.get(key)
                
                // Convertir valores JSON a tipos nativos
                payload[key] = when (value) {
                    is JSONObject -> mapOf() // No soportamos objetos anidados por simplicidad
                    else -> value
                }
            }
            
            return P2PMessage(
                type = type,
                senderId = senderId,
                recipientId = recipientId,
                payload = payload,
                timestamp = timestamp,
                signature = signature
            )
        }
    }
    
    /**
     * Serializa el mensaje a un array de bytes.
     */
    fun serialize(): ByteArray {
        val json = JSONObject()
        
        json.put("type", type.name)
        json.put("senderId", senderId)
        recipientId?.let { json.put("recipientId", it) }
        json.put("timestamp", timestamp)
        json.put("signature", signature)
        
        // Serializar payload
        val payloadJson = JSONObject()
        for ((key, value) in payload) {
            payloadJson.put(key, value)
        }
        
        json.put("payload", payloadJson)
        
        return json.toString().toByteArray(StandardCharsets.UTF_8)
    }
    
    /**
     * Obtiene el contenido que debe ser firmado (todos los datos excepto la firma).
     */
    fun getContentToSign(): ByteArray {
        val json = JSONObject()
        
        json.put("type", type.name)
        json.put("senderId", senderId)
        recipientId?.let { json.put("recipientId", it) }
        json.put("timestamp", timestamp)
        
        // Serializar payload en orden determinístico
        val payloadJson = JSONObject()
        val sortedKeys = payload.keys.sorted()
        for (key in sortedKeys) {
            payloadJson.put(key, payload[key])
        }
        
        json.put("payload", payloadJson)
        
        // Calcular hash SHA-256 del contenido
        val contentBytes = json.toString().toByteArray(StandardCharsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(contentBytes)
    }
    
    /**
     * Crea una nueva instancia de este mensaje con la firma proporcionada.
     */
    fun withSignature(signature: String): P2PMessage {
        return copy(signature = signature)
    }
    
    /**
     * Crea una respuesta a este mensaje.
     */
    fun createResponse(
        type: P2PMessageType,
        responderUserId: String,
        payload: Map<String, Any> = emptyMap()
    ): P2PMessage {
        return P2PMessage(
            type = type,
            senderId = responderUserId,
            recipientId = this.senderId,
            payload = payload,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Verifica si este mensaje puede ser procesado con seguridad.
     * Comprueba aspectos básicos como remitente, destinatario y tipo.
     */
    fun isValid(): Boolean {
        return senderId.isNotEmpty() && 
               (recipientId == null || recipientId.isNotEmpty()) &&
               (type != P2PMessageType.MESSAGE || recipientId != null)
    }
}
