package com.survivalcomunicator.app.network.p2p

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.security.SecureRandom
import kotlin.experimental.or

/**
 * Cliente STUN básico para descubrir la dirección IP pública y puerto asignados por el NAT.
 * 
 * STUN (Session Traversal Utilities for NAT) es un protocolo que permite a los clientes
 * descubrir su dirección IP pública y el tipo de NAT detrás del cual se encuentran.
 * 
 * Esta implementación es una versión simplificada compatible con el protocolo STUN (RFC 5389).
 */
class StunClient {
    companion object {
        private const val TAG = "StunClient"
        
        // STUN Message Types
        private const val BINDING_REQUEST = 0x0001
        private const val BINDING_RESPONSE = 0x0101
        
        // STUN Attribute Types
        private const val MAPPED_ADDRESS = 0x0001
        private const val XOR_MAPPED_ADDRESS = 0x0020
        private const val ERROR_CODE = 0x0009
        
        // Servidores STUN públicos (agregamos varios para redundancia)
        private val STUN_SERVERS = arrayOf(
            ServerInfo("stun.l.google.com", 19302),
            ServerInfo("stun1.l.google.com", 19302),
            ServerInfo("stun2.l.google.com", 19302),
            ServerInfo("stun.stunprotocol.org", 3478),
            ServerInfo("stun.nextcloud.com", 3478)
        )
        
        private const val STUN_TIMEOUT = 5000 // 5 segundos
    }
    
    /**
     * Descubre la dirección IP pública y puerto asignados por el NAT.
     * Intenta con varios servidores STUN hasta que uno funcione.
     * 
     * @return Endpoint con la dirección IP pública y puerto, o null si falla
     */
    suspend fun discoverPublicEndpoint(): Endpoint? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Iniciando descubrimiento de endpoint público")
        
        // Intentar con cada servidor STUN hasta que uno funcione
        for (server in STUN_SERVERS) {
            try {
                val endpoint = queryStunServer(server.host, server.port)
                if (endpoint != null) {
                    Log.d(TAG, "Endpoint público descubierto: ${endpoint.ip}:${endpoint.port}")
                    return@withContext endpoint
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error con servidor STUN ${server.host}: ${e.message}")
                // Continuar con el siguiente servidor
            }
        }
        
        Log.e(TAG, "Fallo al descubrir endpoint público con todos los servidores STUN")
        null
    }
    
    /**
     * Consulta a un servidor STUN específico para determinar la dirección IP pública y puerto.
     * 
     * @param stunServer Nombre o IP del servidor STUN
     * @param stunPort Puerto del servidor STUN
     * @return Endpoint con la dirección IP pública y puerto, o null si falla
     */
    private suspend fun queryStunServer(stunServer: String, stunPort: Int): Endpoint? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Consultando servidor STUN $stunServer:$stunPort")
        
        var socket: DatagramSocket? = null
        
        try {
            // Crear socket UDP
            socket = DatagramSocket()
            socket.soTimeout = STUN_TIMEOUT
            
            // Generar un ID de transacción aleatorio
            val transactionId = ByteArray(12)
            SecureRandom().nextBytes(transactionId)
            
            // Crear mensaje STUN Binding Request
            val requestMessage = createStunBindingRequestMessage(transactionId)
            
            // Enviar solicitud al servidor STUN
            val serverAddress = InetAddress.getByName(stunServer)
            val requestPacket = DatagramPacket(requestMessage, requestMessage.size, serverAddress, stunPort)
            socket.send(requestPacket)
            
            // Recibir respuesta
            val responseBuffer = ByteArray(512)
            val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            
            try {
                socket.receive(responsePacket)
            } catch (e: SocketTimeoutException) {
                Log.e(TAG, "Timeout esperando respuesta de $stunServer")
                return@withContext null
            }
            
            // Procesar respuesta
            val response = responsePacket.data
            val responseLength = responsePacket.length
            
            // Verificar que es una respuesta STUN válida
            if (responseLength < 20) {
                Log.e(TAG, "Respuesta STUN demasiado corta")
                return@withContext null
            }
            
            // Verificar el tipo de mensaje (primeros 2 bytes)
            val messageType = ((response[0].toInt() and 0xFF) shl 8) or (response[1].toInt() and 0xFF)
            if (messageType != BINDING_RESPONSE) {
                Log.e(TAG, "Tipo de mensaje STUN inesperado: $messageType")
                return@withContext null
            }
            
            // Verificar ID de transacción (bytes 8-19)
            for (i in 0 until 12) {
                if (response[i + 8] != transactionId[i]) {
                    Log.e(TAG, "ID de transacción STUN no coincide")
                    return@withContext null
                }
            }
            
            // Extraer la dirección mapeada de los atributos
            val result = extractMappedAddress(response, responseLength, transactionId)
            
            if (result != null) {
                Log.d(TAG, "Dirección mapeada encontrada: ${result.ip}:${result.port}")
                result
            } else {
                Log.e(TAG, "No se encontró dirección mapeada en la respuesta STUN")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al consultar servidor STUN: ${e.message}")
            null
        } finally {
            socket?.close()
        }
    }
    
    /**
     * Crea un mensaje STUN Binding Request
     */
    private fun createStunBindingRequestMessage(transactionId: ByteArray): ByteArray {
        // Crear un buffer para el mensaje
        val message = ByteBuffer.allocate(20) // encabezado STUN: 20 bytes
        
        // Tipo de mensaje: Binding Request (00 01)
        message.put(0, (BINDING_REQUEST shr 8).toByte())
        message.put(1, BINDING_REQUEST.toByte())
        
        // Longitud del mensaje (excluyendo encabezado): 0 bytes (no hay atributos)
        message.put(2, 0)
        message.put(3, 0)
        
        // Valor mágico de cookie RFC 5389 (0x2112A442)
        message.put(4, 0x21.toByte())
        message.put(5, 0x12.toByte())
        message.put(6, 0xA4.toByte())
        message.put(7, 0x42.toByte())
        
        // ID de transacción (12 bytes)
        for (i in 0 until 12) {
            message.put(8 + i, transactionId[i])
        }
        
        return message.array()
    }
    
    /**
     * Extrae la dirección IP y puerto de la respuesta STUN.
     * Intenta primero XOR-MAPPED-ADDRESS, y luego MAPPED-ADDRESS si el primero no está presente.
     */
    private fun extractMappedAddress(response: ByteArray, length: Int, transactionId: ByteArray): Endpoint? {
        try {
            var offset = 20 // Comenzar después del encabezado
            
            while (offset + 4 <= length) {
                // Leer tipo de atributo
                val attributeType = ((response[offset].toInt() and 0xFF) shl 8) or (response[offset + 1].toInt() and 0xFF)
                
                // Leer longitud del atributo
                val attributeLength = ((response[offset + 2].toInt() and 0xFF) shl 8) or (response[offset + 3].toInt() and 0xFF)
                
                // Verificar que el atributo completo está dentro de la respuesta
                if (offset + 4 + attributeLength > length) {
                    Log.e(TAG, "Atributo STUN fuera de límites")
                    break
                }
                
                // Procesar atributo según su tipo
                if (attributeType == XOR_MAPPED_ADDRESS || attributeType == MAPPED_ADDRESS) {
                    // XOR-MAPPED-ADDRESS o MAPPED-ADDRESS encontrado
                    val isXorMapped = attributeType == XOR_MAPPED_ADDRESS
                    
                    // Verificar longitud mínima para dirección IPv4
                    if (attributeLength < 8) {
                        offset += 4 + attributeLength
                        continue
                    }
                    
                    // Verificar familia de direcciones (IPv4 = 0x01, IPv6 = 0x02)
                    val family = response[offset + 5].toInt() and 0xFF
                    if (family != 1) {
                        offset += 4 + attributeLength
                        continue // Solo soportamos IPv4 por ahora
                    }
                    
                    // Extraer puerto (bytes 6-7)
                    var port = ((response[offset + 6].toInt() and 0xFF) shl 8) or (response[offset + 7].toInt() and 0xFF)
                    
                    // Si es XOR-MAPPED-ADDRESS, aplicar XOR con los primeros 2 bytes del Magic Cookie
                    if (isXorMapped) {
                        port = port xor 0x2112
                    }
                    
                    // Extraer dirección IP (bytes 8-11)
                    val ipBytes = ByteArray(4)
                    System.arraycopy(response, offset + 8, ipBytes, 0, 4)
                    
                    // Si es XOR-MAPPED-ADDRESS, aplicar XOR con el Magic Cookie
                    if (isXorMapped) {
                        ipBytes[0] = ipBytes[0] xor 0x21
                        ipBytes[1] = ipBytes[1] xor 0x12
                        ipBytes[2] = ipBytes[2] xor 0xA4.toByte()
                        ipBytes[3] = ipBytes[3] xor 0x42
                    }
                    
                    // Convertir bytes a cadena de dirección IP
                    val ip = "${ipBytes[0].toInt() and 0xFF}." +
                             "${ipBytes[1].toInt() and 0xFF}." +
                             "${ipBytes[2].toInt() and 0xFF}." +
                             "${ipBytes[3].toInt() and 0xFF}"
                    
                    return Endpoint(ip, port)
                }
                
                // Avanzar al siguiente atributo
                offset += 4 + attributeLength
                
                // Alinear a 4 bytes
                if (attributeLength % 4 != 0) {
                    offset += 4 - (attributeLength % 4)
                }
            }
            
            // No se encontró dirección mapeada
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error al extraer dirección mapeada: ${e.message}")
            return null
        }
    }
    
    /**
     * Información de un servidor STUN
     */
    data class ServerInfo(val host: String, val port: Int)
}