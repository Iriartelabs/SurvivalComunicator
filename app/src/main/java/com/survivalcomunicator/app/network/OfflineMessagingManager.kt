package com.survivalcomunicator.app.network

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.*
import com.survivalcomunicator.app.data.database.SurvivalDatabase
import com.survivalcomunicator.app.model.User
import com.survivalcomunicator.app.security.CryptoManager
import com.survivalcomunicator.app.utils.NetworkUtils
import kotlinx.coroutines.*
import retrofit2.Response
import java.util.*
import kotlin.collections.HashMap

/**
 * Entidad que representa un mensaje pendiente en la base de datos
 */
@Entity(tableName = "pending_messages")
data class PendingMessage(
    @PrimaryKey val id: String,
    val recipientId: String,
    val recipientUsername: String,
    val encryptedContent: String,
    val messageType: String,
    val timestamp: Long,
    val attempts: Int,
    val lastAttempt: Long?,
    val createdAt: Long,
    val expiresAt: Long,
    val status: String,
    val metadata: String?
)

/**
 * Entidad que representa el historial de entrega de un mensaje
 */
@Entity(tableName = "message_delivery_status")
data class MessageDeliveryStatus(
    @PrimaryKey val messageId: String,
    val recipientId: String,
    val timestamp: Long,
    val deliveredAt: Long?,
    val readAt: Long?,
    val status: String
)

/**
 * Data Access Object para mensajes pendientes
 */
@Dao
interface PendingMessageDao {
    @Query("SELECT * FROM pending_messages ORDER BY timestamp ASC")
    fun getAllPendingMessages(): List<PendingMessage>
    
    @Query("SELECT * FROM pending_messages WHERE recipientId = :recipientId ORDER BY timestamp ASC")
    fun getPendingMessagesForRecipient(recipientId: String): List<PendingMessage>
    
    @Query("SELECT COUNT(*) FROM pending_messages")
    fun getPendingMessageCount(): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPendingMessage(message: PendingMessage)
    
    @Delete
    fun deletePendingMessage(message: PendingMessage)
    
    @Query("DELETE FROM pending_messages WHERE id = :messageId")
    fun deleteById(messageId: String)
    
    @Query("UPDATE pending_messages SET attempts = attempts + 1, lastAttempt = :timestamp WHERE id = :messageId")
    fun incrementAttempt(messageId: String, timestamp: Long)
    
    @Query("UPDATE pending_messages SET status = :status WHERE id = :messageId")
    fun updateStatus(messageId: String, status: String)
    
    @Query("DELETE FROM pending_messages WHERE expiresAt < :currentTime")
    fun deleteExpiredMessages(currentTime: Long): Int
}

/**
 * Data Access Object para historial de entrega
 */
@Dao
interface MessageDeliveryStatusDao {
    @Query("SELECT * FROM message_delivery_status WHERE messageId = :messageId")
    fun getDeliveryStatus(messageId: String): MessageDeliveryStatus?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDeliveryStatus(status: MessageDeliveryStatus)
    
    @Query("UPDATE message_delivery_status SET status = :status, deliveredAt = :deliveredAt WHERE messageId = :messageId")
    fun updateDeliveryStatus(messageId: String, status: String, deliveredAt: Long)
    
    @Query("UPDATE message_delivery_status SET status = 'read', readAt = :readAt WHERE messageId = :messageId")
    fun markAsRead(messageId: String, readAt: Long)
    
    @Query("SELECT * FROM message_delivery_status WHERE status = :status")
    fun getMessagesByStatus(status: String): List<MessageDeliveryStatus>
}

/**
 * Clase que gestiona el almacenamiento y la entrega de mensajes offline
 */
class OfflineMessagingManager(
    private val context: Context,
    private val networkService: NetworkService,
    private val cryptoManager: CryptoManager,
    private val userId: String,
    private val username: String
) {
    private val TAG = "OfflineMessagingManager"
    
    private val database = SurvivalDatabase.getInstance(context)
    private val pendingMessageDao = database.pendingMessageDao()
    private val deliveryStatusDao = database.messageDeliveryStatusDao()
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Intervalo para intentar reenviar mensajes pendientes
    private val RETRY_INTERVAL = 5 * 60 * 1000L // 5 minutos
    
    // Caché de status de mensajes para acceso rápido
    private val messageStatusCache = HashMap<String, String>()
    
    // LiveData que puede ser observado para ver cambios en el status de mensajes
    private val _messageStatusUpdates = MutableLiveData<Pair<String, String>>()
    val messageStatusUpdates: LiveData<Pair<String, String>> = _messageStatusUpdates
    
    // Bandera para rastrear si el reintento está en progreso
    private var isRetryScheduled = false
    
    init {
        // Iniciar limpieza de mensajes expirados
        cleanExpiredMessages()
        
        // Programar reintentos periódicos
        scheduleRetries()
    }
    
    /**
     * Almacena un mensaje para entrega posterior cuando el destinatario no está disponible
     */
    suspend fun storeMessageForLaterDelivery(
        recipientId: String,
        recipientUsername: String,
        encryptedContent: String,
        messageType: String = "text",
        metadata: String? = null
    ): String {
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val expiresAt = now + 7 * 24 * 60 * 60 * 1000 // 7 días
        
        val pendingMessage = PendingMessage(
            id = messageId,
            recipientId = recipientId,
            recipientUsername = recipientUsername,
            encryptedContent = encryptedContent,
            messageType = messageType,
            timestamp = now,
            attempts = 0,
            lastAttempt = null,
            createdAt = now,
            expiresAt = expiresAt,
            status = "pending",
            metadata = metadata
        )
        
        pendingMessageDao.insertPendingMessage(pendingMessage)
        
        // Registrar en historial de entrega
        val deliveryStatus = MessageDeliveryStatus(
            messageId = messageId,
            recipientId = recipientId,
            timestamp = now,
            deliveredAt = null,
            readAt = null,
            status = "pending"
        )
        
        deliveryStatusDao.insertDeliveryStatus(deliveryStatus)
        
        // Actualizar caché
        messageStatusCache[messageId] = "pending"
        
        // Intentar entrega inmediata si hay conexión
        if (NetworkUtils.isNetworkAvailable(context)) {
            coroutineScope.launch {
                attemptToDeliverMessage(pendingMessage)
            }
        }
        
        return messageId
    }
    
    /**
     * Intenta entregar un mensaje pendiente al destinatario
     */
    private suspend fun attemptToDeliverMessage(message: PendingMessage): Boolean {
        try {
            Log.d(TAG, "Intentando entregar mensaje ${message.id} a ${message.recipientUsername}")
            
            // Incrementar contador de intentos
            pendingMessageDao.incrementAttempt(message.id, System.currentTimeMillis())
            
            // 1. Localizar al destinatario
            val recipientLocation = networkService.getUserLocation(message.recipientUsername)
                ?: return false
            
            // 2. Verificar si tiene información de conexión
            if (recipientLocation.ipAddress.isNullOrEmpty() || recipientLocation.port == null) {
                Log.d(TAG, "No hay información de conexión para ${message.recipientUsername}")
                return false
            }
            
            // 3. Preparar mensaje para entrega
            val deliveryPackage = mapOf(
                "message_id" to message.id,
                "sender_id" to userId,
                "sender_username" to username,
                "encrypted_content" to message.encryptedContent,
                "message_type" to message.messageType,
                "timestamp" to message.timestamp
            )
            
            // 4. Intentar enviar directamente al destinatario vía P2P
            val p2pClient = P2PClient(context)
            val p2pDeliverySuccess = p2pClient.sendDirectMessage(
                recipientLocation.ipAddress,
                recipientLocation.port,
                "offline_message_delivery",
                deliveryPackage
            )
            
            if (p2pDeliverySuccess) {
                // Mensaje entregado con éxito
                handleSuccessfulDelivery(message.id)
                return true
            }
            
            // 5. Si la entrega P2P falla, intentar vía servidor
            val serverResponse = networkService.storeOfflineMessage(
                senderId = userId,
                recipientUsername = message.recipientUsername,
                encryptedContent = message.encryptedContent,
                messageType = message.messageType,
                timestamp = message.timestamp
            )
            
            if (serverResponse.isSuccessful && serverResponse.body()?.success == true) {
                // El servidor ha aceptado el mensaje para entrega posterior
                updateMessageStatus(message.id, "server_queued")
                return true
            }
            
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error entregando mensaje ${message.id}: ${e.message}")
            return false
        }
    }
    
    /**
     * Procesa un mensaje entregado con éxito
     */
    private fun handleSuccessfulDelivery(messageId: String) {
        coroutineScope.launch {
            try {
                val now = System.currentTimeMillis()
                
                // Actualizar estado en historial
                deliveryStatusDao.updateDeliveryStatus(
                    messageId = messageId,
                    status = "delivered",
                    deliveredAt = now
                )
                
                // Eliminar de la cola de pendientes
                pendingMessageDao.deleteById(messageId)
                
                // Actualizar caché y notificar
                messageStatusCache[messageId] = "delivered"
                _messageStatusUpdates.postValue(Pair(messageId, "delivered"))
                
                Log.d(TAG, "Mensaje $messageId entregado con éxito")
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando entrega de mensaje $messageId: ${e.message}")
            }
        }
    }
    
    /**
     * Marca un mensaje como leído
     */
    fun markMessageAsRead(messageId: String) {
        coroutineScope.launch {
            try {
                val now = System.currentTimeMillis()
                deliveryStatusDao.markAsRead(messageId, now)
                
                // Actualizar caché y notificar
                messageStatusCache[messageId] = "read"
                _messageStatusUpdates.postValue(Pair(messageId, "read"))
                
                // Notificar al servidor si es posible
                try {
                    networkService.markMessageRead(messageId)
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo notificar al servidor sobre lectura de mensaje: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marcando mensaje como leído: ${e.message}")
            }
        }
    }
    
    /**
     * Actualiza el estado de un mensaje
     */
    private fun updateMessageStatus(messageId: String, status: String) {
        coroutineScope.launch {
            try {
                pendingMessageDao.updateStatus(messageId, status)
                
                // Actualizar caché y notificar
                messageStatusCache[messageId] = status
                _messageStatusUpdates.postValue(Pair(messageId, status))
                
                Log.d(TAG, "Estado de mensaje $messageId actualizado a $status")
            } catch (e: Exception) {
                Log.e(TAG, "Error actualizando estado de mensaje: ${e.message}")
            }
        }
    }
    
    /**
     * Programa reintentos periódicos para mensajes pendientes
     */
    private fun scheduleRetries() {
        if (isRetryScheduled) return
        
        isRetryScheduled = true
        
        coroutineScope.launch {
            while (true) {
                delay(RETRY_INTERVAL)
                
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    continue
                }
                
                processRetryQueue()
            }
        }
    }
    
    /**
     * Procesa la cola de mensajes pendientes
     */
    private suspend fun processRetryQueue() {
        try {
            val pendingMessages = pendingMessageDao.getAllPendingMessages()
            
            if (pendingMessages.isEmpty()) {
                return
            }
            
            Log.d(TAG, "Procesando ${pendingMessages.size} mensajes pendientes")
            
            // Aplicar estrategia de backoff exponencial
            val now = System.currentTimeMillis()
            val messagesToRetry = pendingMessages.filter { message ->
                when {
                    message.attempts == 0 -> true
                    message.attempts <= 2 && (now - (message.lastAttempt ?: 0)) > 5 * 60 * 1000 -> true
                    message.attempts <= 5 && (now - (message.lastAttempt ?: 0)) > 30 * 60 * 1000 -> true
                    message.attempts <= 10 && (now - (message.lastAttempt ?: 0)) > 2 * 60 * 60 * 1000 -> true
                    message.attempts > 10 && (now - (message.lastAttempt ?: 0)) > 6 * 60 * 60 * 1000 -> true
                    else -> false
                }
            }
            
            for (message in messagesToRetry) {
                // Pequeña pausa entre intentos
                delay(500)
                
                val delivered = attemptToDeliverMessage(message)
                if (delivered) {
                    Log.d(TAG, "Mensaje ${message.id} entregado en reintento")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando cola de reintentos: ${e.message}")
        }
    }
    
    /**
     * Limpia mensajes expirados
     */
    private fun cleanExpiredMessages() {
        coroutineScope.launch {
            try {
                val now = System.currentTimeMillis()
                val count = pendingMessageDao.deleteExpiredMessages(now)
                
                if (count > 0) {
                    Log.d(TAG, "Eliminados $count mensajes expirados")
                }
                
                // Programar próxima limpieza
                delay(6 * 60 * 60 * 1000) // 6 horas
                cleanExpiredMessages()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error limpiando mensajes expirados: ${e.message}")
            }
        }
    }
    
    /**
     * Obtiene el estado actual de un mensaje
     */
    suspend fun getMessageStatus(messageId: String): String {
        // Primero verificar caché
        messageStatusCache[messageId]?.let { return it }
        
        // Si no está en caché, consultar base de datos
        val deliveryStatus = deliveryStatusDao.getDeliveryStatus(messageId)
        val status = deliveryStatus?.status ?: "unknown"
        
        // Actualizar caché
        messageStatusCache[messageId] = status
        
        return status
    }
    
    /**
     * Obtiene varios estados de mensajes de una vez
     */
    suspend fun getMessageStatuses(messageIds: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        
        for (messageId in messageIds) {
            result[messageId] = getMessageStatus(messageId)
        }
        
        return result
    }
    
    /**
     * Sincroniza estados de mensaje con el servidor
     */
    suspend fun syncMessageStatusesWithServer() {
        try {
            if (!NetworkUtils.isNetworkAvailable(context)) {
                return
            }
            
            // Obtener mensajes enviados pero aún pendientes de confirmación de entrega
            val pendingConfirmation = deliveryStatusDao.getMessagesByStatus("pending")
            
            if (pendingConfirmation.isEmpty()) {
                return
            }
            
            // Consultar estado en el servidor
            val messageIds = pendingConfirmation.map { it.messageId }
            val response = networkService.checkMessageStatus(messageIds)
            
            if (!response.isSuccessful || response.body() == null) {
                return
            }
            
            val serverStatuses = response.body()!!.results
            
            // Actualizar estados locales
            serverStatuses.forEach { serverStatus ->
                val messageId = serverStatus.messageId
                val status = serverStatus.status
                
                if (status == "delivered" || status == "read") {
                    val now = System.currentTimeMillis()
                    deliveryStatusDao.updateDeliveryStatus(messageId, status, serverStatus.deliveredAt ?: now)
                    
                    if (status == "read" && serverStatus.readAt != null) {
                        deliveryStatusDao.markAsRead(messageId, serverStatus.readAt)
                    }
                    
                    // Actualizar caché y notificar
                    messageStatusCache[messageId] = status
                    _messageStatusUpdates.postValue(Pair(messageId, status))
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando estados de mensaje: ${e.message}")
        }
    }
    
    /**
     * Maneja la recepción de un mensaje offline
     */
    fun handleOfflineMessageReceived(
        messageId: String,
        senderId: String,
        senderUsername: String,
        encryptedContent: String,
        timestamp: Long
    ): Boolean {
        try {
            // Responder confirmación de recepción
            coroutineScope.launch {
                try {
                    networkService.confirmMessageReceived(messageId)
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo confirmar recepción al servidor: ${e.message}")
                }
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando mensaje offline recibido: ${e.message}")
            return false
        }
    }
    
    /**
     * Libera recursos cuando ya no se necesitan
     */
    fun cleanup() {
        coroutineScope.cancel()
    }
}