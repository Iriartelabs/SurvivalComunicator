package com.survivalcomunicator.app.network.p2p

import android.util.Log
import com.survivalcomunicator.app.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Servicio de relay que actúa como intermediario cuando la conexión directa P2P
 * no es posible debido a restricciones de NAT. Utilizamos un enfoque TURN simplificado.
 * 
 * TURN (Traversal Using Relays around NAT) es una extensión de STUN que permite
 * a los clientes solicitar a un servidor que actúe como relay para sus datos.
 */
class RelayService(
    private val cryptoManager: CryptoManager,
    private val apiBaseUrl: String,
    private val relayServerUrl: String
) {
    companion object {
        private const val TAG = "RelayService"
        private const val CONNECT_TIMEOUT = 30000 // 30 segundos
        private const val READ_TIMEOUT = 30000 // 30 segundos
        private const val API_TIMEOUT = 10000 // 10 segundos
        
        // Tipos de mensajes para el protocolo relay
        private const val MSG_TYPE_CONNECT = "CONNECT" // Solicitud de conexión
        private const val MSG_TYPE_DATA = "DATA" // Datos de aplicación
        private const val MSG_TYPE_DISCONNECT = "DISCONNECT" // Desconexión
    }
    
    // Cliente HTTP para solicitudes a la API
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(API_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(API_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        .writeTimeout(API_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        .build()
    
    /**
     * Crea una conexión a través del servicio de relay.
     * 
     * @param targetHost Dirección IP del dispositivo destino
     * @param targetPort Puerto del dispositivo destino
     * @return Socket conectado o null si la operación falla
     */
    suspend fun createRelayConnection(targetHost: String, targetPort: Int): Socket? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Intentando crear conexión relay con $targetHost:$targetPort")
        
        try {
            // Solicitar un canal relay al servidor
            val relayChannel = requestRelayChannel(targetHost, targetPort)
            if (relayChannel == null) {
                Log.e(TAG, "No se pudo obtener canal relay")
                return@withContext null
            }
            
            // Conectar al servidor relay
            val socket = Socket()
            socket.connect(java.net.InetSocketAddress(relayServerUrl, relayChannel.relayPort), CONNECT_TIMEOUT)
            socket.soTimeout = READ_TIMEOUT
            
            // Enviar mensaje de autenticación inicial
            sendAuthMessage(socket, relayChannel.channelId, targetHost, targetPort)
            
            // Verificar respuesta del servidor
            if (!verifyRelayResponse(socket)) {
                Log.e(TAG, "Falló la verificación del servidor relay")
                socket.close()
                return@withContext null
            }
            
            // Crear y devolver una socket wrapper que encapsula y cifra el tráfico
            return@withContext RelaySocketWrapper(socket, cryptoManager, relayChannel.channelId)
        } catch (e: Exception) {
            Log.e(TAG, "Error al crear conexión relay: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Solicita un canal de relay al servidor.
     * 
     * @return Información del canal o null si falla
     */
    private suspend fun requestRelayChannel(targetHost: String, targetPort: Int): RelayChannel? = withContext(Dispatchers.IO) {
        try {
            // Preparar datos para la solicitud
            val userId = cryptoManager.getUserId()
            val username = cryptoManager.getUsername()
            val timestamp = System.currentTimeMillis()
            
            // Crear payload
            val requestData = JSONObject().apply {
                put("userId", userId)
                put("username", username)
                put("targetHost", targetHost)
                put("targetPort", targetPort)
                put("timestamp", timestamp)
            }
            
            // Firmar la solicitud
            val signature = cryptoManager.signData(requestData.toString().toByteArray())
            requestData.put("signature", signature)
            
            // Realizar solicitud HTTP
            val request = Request.Builder()
                .url("$apiBaseUrl/api/relay/request")
                .post(requestData.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Error al solicitar canal relay: ${response.code}")
                    return@withContext null
                }
                
                // Procesar respuesta
                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) {
                    Log.e(TAG, "Respuesta vacía del servidor relay")
                    return@withContext null
                }
                
                val jsonResponse = JSONObject(responseBody)
                val channelId = jsonResponse.getString("channelId")
                val relayPort = jsonResponse.getInt("relayPort")
                val expiry = jsonResponse.getLong("expiry")
                
                Log.d(TAG, "Canal relay obtenido: ID=$channelId, Puerto=$relayPort")
                return@withContext RelayChannel(channelId, relayPort, expiry)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al solicitar canal relay: ${e.message}")
            null
        }
    }
    
    /**
     * Envía mensaje de autenticación inicial al servidor relay
     */
    private fun sendAuthMessage(socket: Socket, channelId: String, targetHost: String, targetPort: Int) {
        val outputStream = socket.getOutputStream()
        
        // Crear mensaje de autenticación
        val authMessage = JSONObject().apply {
            put("type", MSG_TYPE_CONNECT)
            put("channelId", channelId)
            put("userId", cryptoManager.getUserId())
            put("targetHost", targetHost)
            put("targetPort", targetPort)
            put("timestamp", System.currentTimeMillis())
        }
        
        // Firmar mensaje
        val signature = cryptoManager.signData(authMessage.toString().toByteArray())
        authMessage.put("signature", signature)
        
        // Enviar mensaje
        val authBytes = authMessage.toString().toByteArray()
        val lengthBytes = ByteArray(4)
        lengthBytes[0] = (authBytes.size shr 24).toByte()
        lengthBytes[1] = (authBytes.size shr 16).toByte()
        lengthBytes[2] = (authBytes.size shr 8).toByte()
        lengthBytes[3] = authBytes.size.toByte()
        
        outputStream.write(lengthBytes)
        outputStream.write(authBytes)
        outputStream.flush()
    }
    
    /**
     * Verifica la respuesta del servidor relay tras la autenticación
     */
    private fun verifyRelayResponse(socket: Socket): Boolean {
        try {
            val inputStream = socket.getInputStream()
            
            // Leer longitud del mensaje
            val lengthBytes = ByteArray(4)
            if (inputStream.read(lengthBytes) != 4) {
                return false
            }
            
            val messageLength = ((lengthBytes[0].toInt() and 0xFF) shl 24) or
                               ((lengthBytes[1].toInt() and 0xFF) shl 16) or
                               ((lengthBytes[2].toInt() and 0xFF) shl 8) or
                               (lengthBytes[3].toInt() and 0xFF)
            
            if (messageLength <= 0 || messageLength > 10240) {
                Log.e(TAG, "Longitud de respuesta relay inválida: $messageLength")
                return false
            }
            
            // Leer el mensaje
            val messageBytes = ByteArray(messageLength)
            var totalRead = 0
            
            while (totalRead < messageLength) {
                val read = inputStream.read(messageBytes, totalRead, messageLength - totalRead)
                if (read == -1) break
                totalRead += read
            }
            
            if (totalRead != messageLength) {
                Log.e(TAG, "Lectura incompleta de respuesta relay")
                return false
            }
            
            // Parsear respuesta
            val responseJson = JSONObject(String(messageBytes))
            val success = responseJson.optBoolean("success", false)
            
            if (!success) {
                val error = responseJson.optString("error", "Error desconocido")
                Log.e(TAG, "Error en respuesta relay: $error")
                return false
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error al verificar respuesta relay: ${e.message}")
            return false
        }
    }
    
    /**
     * Wrapper para un socket que pasa a través de un relay.
     * Encapsula la comunicación con el protocolo relay y maneja el cifrado.
     */
    private inner class RelaySocketWrapper(
        private val underlying: Socket,
        private val cryptoManager: CryptoManager,
        private val channelId: String
    ) : Socket() {
        private val inputStream = RelayInputStream(underlying.getInputStream())
        private val outputStream = RelayOutputStream(underlying.getOutputStream())
        
        override fun getInputStream(): InputStream = inputStream
        override fun getOutputStream(): OutputStream = outputStream
        
        override fun close() {
            try {
                // Enviar mensaje de desconexión antes de cerrar
                val disconnectMessage = JSONObject().apply {
                    put("type", MSG_TYPE_DISCONNECT)
                    put("channelId", channelId)
                    put("timestamp", System.currentTimeMillis())
                }
                
                val signature = cryptoManager.signData(disconnectMessage.toString().toByteArray())
                disconnectMessage.put("signature", signature)
                
                // Enviar mensaje
                val msgBytes = disconnectMessage.toString().toByteArray()
                val lengthBytes = ByteArray(4)
                lengthBytes[0] = (msgBytes.size shr 24).toByte()
                lengthBytes[1] = (msgBytes.size shr 16).toByte()
                lengthBytes[2] = (msgBytes.size shr 8).toByte()
                lengthBytes[3] = msgBytes.size.toByte()
                
                underlying.getOutputStream().write(lengthBytes)
                underlying.getOutputStream().write(msgBytes)
                underlying.getOutputStream().flush()
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando mensaje de desconexión: ${e.message}")
            } finally {
                underlying.close()
            }
        }
        
        // Delegar otros métodos al socket subyacente
        override fun connect(endpoint: java.net.SocketAddress) = underlying.connect(endpoint)
        override fun connect(endpoint: java.net.SocketAddress, timeout: Int) = underlying.connect(endpoint, timeout)
        override fun bind(bindpoint: java.net.SocketAddress) = underlying.bind(bindpoint)
        override fun getInetAddress() = underlying.inetAddress
        override fun getLocalAddress() = underlying.localAddress
        override fun getPort() = underlying.port
        override fun getLocalPort() = underlying.localPort
        override fun getRemoteSocketAddress() = underlying.remoteSocketAddress
        override fun getLocalSocketAddress() = underlying.localSocketAddress
        override fun shutdownInput() = underlying.shutdownInput()
        override fun shutdownOutput() = underlying.shutdownOutput()
        override fun isConnected() = underlying.isConnected
        override fun isBound() = underlying.isBound
        override fun isClosed() = underlying.isClosed
        override fun isInputShutdown() = underlying.isInputShutdown
        override fun isOutputShutdown() = underlying.isOutputShutdown
        override fun setSoTimeout(timeout: Int) = underlying.setSoTimeout(timeout)
        override fun getSoTimeout() = underlying.soTimeout
        override fun setSendBufferSize(size: Int) = underlying.setSendBufferSize(size)
        override fun getSendBufferSize() = underlying.sendBufferSize
        override fun setReceiveBufferSize(size: Int) = underlying.setReceiveBufferSize(size)
        override fun getReceiveBufferSize() = underlying.receiveBufferSize
        override fun setTcpNoDelay(on: Boolean) = underlying.setTcpNoDelay(on)
        override fun getTcpNoDelay() = underlying.tcpNoDelay
        override fun setKeepAlive(on: Boolean) = underlying.setKeepAlive(on)
        override fun getKeepAlive() = underlying.keepAlive
        override fun setTrafficClass(tc: Int) = underlying.setTrafficClass(tc)
        override fun getTrafficClass() = underlying.trafficClass
        override fun setReuseAddress(on: Boolean) = underlying.setReuseAddress(on)
        override fun getReuseAddress() = underlying.reuseAddress
        override fun setOOBInline(on: Boolean) = underlying.setOOBInline(on)
        override fun getOOBInline() = underlying.oobInline
        override fun setSoLinger(on: Boolean, linger: Int) = underlying.setSoLinger(on, linger)
        override fun getSoLinger() = underlying.soLinger
        
        /**
         * InputStream que maneja la recepción de datos a través del relay.
         */
        private inner class RelayInputStream(private val underlyingInput: InputStream) : InputStream() {
            override fun read(): Int {
                val buffer = ByteArray(1)
                val bytesRead = read(buffer, 0, 1)
                return if (bytesRead == 1) buffer[0].toInt() and 0xFF else -1
            }
            
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                try {
                    // Leer encabezado del mensaje
                    val lengthBytes = ByteArray(4)
                    if (underlyingInput.read(lengthBytes) != 4) {
                        return -1
                    }
                    
                    val messageLength = ((lengthBytes[0].toInt() and 0xFF) shl 24) or
                                       ((lengthBytes[1].toInt() and 0xFF) shl 16) or
                                       ((lengthBytes[2].toInt() and 0xFF) shl 8) or
                                       (lengthBytes[3].toInt() and 0xFF)
                    
                    if (messageLength <= 0) {
                        return -1
                    }
                    
                    // Leer mensaje completo
                    val messageBytes = ByteArray(messageLength)
                    var totalRead = 0
                    
                    while (totalRead < messageLength) {
                        val bytesRead = underlyingInput.read(messageBytes, totalRead, messageLength - totalRead)
                        if (bytesRead == -1) break
                        totalRead += bytesRead
                    }
                    
                    if (totalRead != messageLength) {
                        return -1
                    }
                    
                    // Parsear mensaje
                    val messageJson = JSONObject(String(messageBytes))
                    val type = messageJson.optString("type", "")
                    
                    if (type != MSG_TYPE_DATA) {
                        // No es un mensaje de datos, ignorar
                        return 0
                    }
                    
                    // Extraer datos encapsulados
                    val encryptedData = messageJson.optString("data", "")
                    if (encryptedData.isEmpty()) {
                        return 0
                    }
                    
                    // Descifrar datos
                    val decryptedData = cryptoManager.decryptMessage(encryptedData)
                    val dataBytes = decryptedData.toByteArray()
                    
                    // Copiar datos al buffer
                    val bytesToCopy = minOf(len, dataBytes.size)
                    System.arraycopy(dataBytes, 0, b, off, bytesToCopy)
                    
                    return bytesToCopy
                } catch (e: Exception) {
                    Log.e(TAG, "Error leyendo desde relay: ${e.message}")
                    return -1
                }
            }
            
            override fun available() = 0
            
            override fun close() {
                underlyingInput.close()
            }
        }
        
        /**
         * OutputStream que maneja el envío de datos a través del relay.
         */
        private inner class RelayOutputStream(private val underlyingOutput: OutputStream) : OutputStream() {
            override fun write(b: Int) {
                write(byteArrayOf(b.toByte()), 0, 1)
            }
            
            override fun write(b: ByteArray, off: Int, len: Int) {
                if (len <= 0) return
                
                try {
                    // Extraer datos a enviar
                    val dataToSend = ByteArray(len)
                    System.arraycopy(b, off, dataToSend, 0, len)
                    
                    // Cifrar datos
                    val encryptedData = cryptoManager.encryptMessage(String(dataToSend))
                    
                    // Crear mensaje de datos
                    val dataMessage = JSONObject().apply {
                        put("type", MSG_TYPE_DATA)
                        put("channelId", channelId)
                        put("data", encryptedData)
                        put("timestamp", System.currentTimeMillis())
                    }
                    
                    // Firmar mensaje
                    val signature = cryptoManager.signData(dataMessage.toString().toByteArray())
                    dataMessage.put("signature", signature)
                    
                    // Enviar mensaje
                    val messageBytes = dataMessage.toString().toByteArray()
                    val lengthBytes = ByteArray(4)
                    lengthBytes[0] = (messageBytes.size shr 24).toByte()
                    lengthBytes[1] = (messageBytes.size shr 16).toByte()
                    lengthBytes[2] = (messageBytes.size shr 8).toByte()
                    lengthBytes[3] = messageBytes.size.toByte()
                    
                    underlyingOutput.write(lengthBytes)
                    underlyingOutput.write(messageBytes)
                } catch (e: Exception) {
                    Log.e(TAG, "Error escribiendo a relay: ${e.message}")
                    throw IOException("Error escribiendo a relay: ${e.message}")
                }
            }
            
            override fun flush() {
                underlyingOutput.flush()
            }
            
            override fun close() {
                underlyingOutput.close()
            }
        }
    }
}

/**
 * Información sobre un canal de relay
 */
data class RelayChannel(
    val channelId: String,
    val relayPort: Int,
    val expiry: Long
)