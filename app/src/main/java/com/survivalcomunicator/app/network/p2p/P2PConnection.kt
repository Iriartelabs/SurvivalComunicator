package com.survivalcomunicator.app.network.p2p

import android.util.Log
import com.survivalcomunicator.app.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.*
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Clase que encapsula una conexión P2P con otro dispositivo
 */
class P2PConnection(
    private val socket: Socket,
    val userId: String,
    val publicKey: String,
    private val cryptoManager: CryptoManager
) {
    private val TAG = "P2PConnection"
    
    private val reader: BufferedReader
    private val writer: BufferedWriter
    private val isConnected = AtomicBoolean(true)
    private var lastSeen: Long = System.currentTimeMillis()
    
    init {
        try {
            // Configurar socket para timeout de lectura
            socket.soTimeout = 5000 // 5 segundos para timeout de lectura
            
            // Crear streams de entrada/salida
            val outputStream = socket.getOutputStream()
            val inputStream = socket.getInputStream()
            
            writer = BufferedWriter(OutputStreamWriter(outputStream))
            reader = BufferedReader(InputStreamReader(inputStream))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando conexión: ${e.message}")
            throw e
        }
    }
    
    /**
     * Envía un mensaje a través de la conexión
     */
    suspend fun sendMessage(message: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConnected.get() || socket.isClosed) {
                    return@withContext false
                }
                
                synchronized(writer) {
                    writer.write(message)
                    writer.newLine()
                    writer.flush()
                }
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando mensaje: ${e.message}")
                isConnected.set(false)
                false
            }
        }
    }
    
    /**
     * Recibe un mensaje de la conexión con timeout
     */
    suspend fun receiveMessage(timeoutMs: Long = 5000): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConnected.get() || socket.isClosed) {
                    return@withContext null
                }
                
                withTimeoutOrNull(timeoutMs) {
                    val line = reader.readLine()
                    
                    if (line != null) {
                        // Actualizar timestamp de último mensaje
                        lastSeen = System.currentTimeMillis()
                        line
                    } else {
                        // EOF alcanzado
                        isConnected.set(false)
                        null
                    }
                }
            } catch (e: SocketTimeoutException) {
                // Timeout de lectura, esto es normal
                null
            } catch (e: SocketException) {
                // Error en socket
                isConnected.set(false)
                Log.e(TAG, "Error en socket: ${e.message}")
                null
            } catch (e: Exception) {
                // Otro error
                Log.e(TAG, "Error recibiendo mensaje: ${e.message}")
                isConnected.set(false)
                null
            }
        }
    }
    
    /**
     * Cierra la conexión
     */
    fun close() {
        try {
            isConnected.set(false)
            
            try { writer.close() } catch (e: Exception) {}
            try { reader.close() } catch (e: Exception) {}
            try { socket.close() } catch (e: Exception) {}
            
        } catch (e: Exception) {
            Log.e(TAG, "Error cerrando conexión: ${e.message}")
        }
    }
    
    /**
     * Verifica si la conexión está activa
     */
    fun isConnected(): Boolean {
        return isConnected.get() && !socket.isClosed
    }
    
    /**
     * Actualiza el timestamp de último contacto
     */
    fun updateLastSeen() {
        lastSeen = System.currentTimeMillis()
    }
    
    /**
     * Obtiene el último timestamp de actividad
     */
    fun getLastSeen(): Long {
        return lastSeen
    }
    
    /**
     * Verifica si la conexión está inactiva por más de cierto tiempo
     */
    fun isStale(timeoutMs: Long): Boolean {
        return System.currentTimeMillis() - lastSeen > timeoutMs
    }
}