package com.survivalcomunicator.app.network.p2p

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException

/**
 * Clase que implementa técnicas de NAT Traversal para permitir conexiones P2P
 * entre dispositivos situados detrás de diferentes NATs.
 */
class NATTraversal(
    private val stunClient: StunClient,
    private val relayService: RelayService
) {
    companion object {
        private const val TAG = "NATTraversal"
        private const val HOLE_PUNCH_TIMEOUT = 15000 // 15 segundos
        private const val CONNECT_TIMEOUT = 5000 // 5 segundos
        private const val HOLE_PUNCH_ATTEMPTS = 5
        private const val RELAY_PORT = 8765
    }

    /**
     * Intenta establecer una conexión P2P con el dispositivo remoto utilizando varias técnicas
     * de NAT traversal en orden de preferencia:
     * 1. Conexión directa (si es posible)
     * 2. UDP Hole Punching utilizando STUN
     * 3. TCP Hole Punching
     * 4. Relay a través de un servidor TURN
     *
     * @param host Dirección IP o hostname del dispositivo remoto
     * @param port Puerto del dispositivo remoto
     * @return Socket conectado o null si fallan todas las técnicas
     */
    suspend fun establishConnection(host: String, port: Int): Socket? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Intentando conexión directa con $host:$port")
            
            // 1. Intentar conexión directa primero (la más rápida si funciona)
            val directSocket = tryDirectConnection(host, port)
            if (directSocket != null) {
                Log.d(TAG, "Conexión directa establecida con éxito")
                return@withContext directSocket
            }

            // 2. Si falla la conexión directa, intentar UDP hole punching con STUN
            Log.d(TAG, "Intentando UDP hole punching con $host:$port")
            val publicEndpoint = stunClient.discoverPublicEndpoint()
            if (publicEndpoint != null) {
                val socketAfterHolePunch = tryUdpHolePunching(host, port, publicEndpoint)
                if (socketAfterHolePunch != null) {
                    Log.d(TAG, "Hole punching UDP exitoso")
                    return@withContext socketAfterHolePunch
                }
            }

            // 3. Si UDP hole punching falla, intentar TCP hole punching
            Log.d(TAG, "Intentando TCP hole punching con $host:$port")
            val socketAfterTcpHolePunch = tryTcpHolePunching(host, port)
            if (socketAfterTcpHolePunch != null) {
                Log.d(TAG, "Hole punching TCP exitoso")
                return@withContext socketAfterTcpHolePunch
            }

            // 4. Si todo lo anterior falla, utilizar relay como último recurso
            Log.d(TAG, "Intentando conexión vía relay con $host:$port")
            val relaySocket = relayService.createRelayConnection(host, port)
            if (relaySocket != null) {
                Log.d(TAG, "Conexión vía relay establecida con éxito")
                return@withContext relaySocket
            }

            // Si todas las técnicas fallan, retornar null
            Log.e(TAG, "Todas las técnicas de NAT traversal fallaron")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error durante NAT traversal: ${e.message}")
            null
        }
    }

    /**
     * Intenta una conexión TCP directa. Esta es la más sencilla y rápida si ambos
     * dispositivos están en la misma red o si los NATs son favorables.
     */
    private suspend fun tryDirectConnection(host: String, port: Int): Socket? = withContext(Dispatchers.IO) {
        return@withContext try {
            val socket = Socket()
            socket.soTimeout = CONNECT_TIMEOUT
            val address = InetSocketAddress(host, port)
            socket.connect(address, CONNECT_TIMEOUT)
            
            if (socket.isConnected) {
                socket
            } else {
                socket.close()
                null
            }
        } catch (e: IOException) {
            Log.d(TAG, "Conexión directa fallida: ${e.message}")
            null
        }
    }

    /**
     * Implementa UDP hole punching utilizando STUN para atravesar NAT.
     * Esta técnica funciona enviando paquetes UDP para abrir un "agujero" en el NAT.
     */
    private suspend fun tryUdpHolePunching(
        host: String, 
        port: Int, 
        publicEndpoint: Endpoint
    ): Socket? = withContext(Dispatchers.IO) {
        // Socket UDP para hole punching
        val udpSocket = DatagramSocket()
        
        try {
            // Enviar paquetes UDP para abrir el NAT (hole punching)
            val targetAddress = InetAddress.getByName(host)
            val holePunchPayload = "HOLE_PUNCH:${publicEndpoint.ip}:${publicEndpoint.port}".toByteArray()
            
            for (i in 1..HOLE_PUNCH_ATTEMPTS) {
                val packet = DatagramPacket(holePunchPayload, holePunchPayload.size, targetAddress, port)
                udpSocket.send(packet)
                Log.d(TAG, "Enviado paquete hole punch $i a $host:$port")
                Thread.sleep(200) // Esperar un poco entre paquetes
            }

            // Después del hole punching, intentar conexión TCP regular
            val socketAfterHolePunch = tryDirectConnection(host, port)
            if (socketAfterHolePunch != null) {
                return@withContext socketAfterHolePunch
            }

            // Si aún falla, podríamos necesitar esperar un poco e intentar de nuevo
            Thread.sleep(1000)
            tryDirectConnection(host, port)
        } catch (e: Exception) {
            Log.e(TAG, "Error en UDP hole punching: ${e.message}")
            null
        } finally {
            if (!udpSocket.isClosed) {
                udpSocket.close()
            }
        }
    }

    /**
     * Implementa TCP hole punching, una técnica más avanzada para NATs más restrictivos.
     * Funciona realizando intentos de conexión simultáneos desde ambos lados.
     */
    private suspend fun tryTcpHolePunching(host: String, port: Int): Socket? = withContext(Dispatchers.IO) {
        var clientSocket: Socket? = null
        var successfulConnection = false
        
        try {
            // Crear socket cliente
            clientSocket = Socket()
            clientSocket.reuseAddress = true
            clientSocket.soTimeout = HOLE_PUNCH_TIMEOUT
            
            // Intentar conexión TCP repetidamente durante un período (técnica de hole punching TCP)
            val remoteAddress = InetSocketAddress(host, port)
            
            for (i in 1..HOLE_PUNCH_ATTEMPTS) {
                try {
                    Log.d(TAG, "Intento de TCP hole punching $i")
                    clientSocket.connect(remoteAddress, CONNECT_TIMEOUT)
                    
                    // Si llegamos aquí, la conexión fue exitosa
                    successfulConnection = true
                    break
                } catch (e: IOException) {
                    Log.d(TAG, "Intento $i falló: ${e.message}")
                    
                    // Crear un nuevo socket para el siguiente intento
                    clientSocket.close()
                    clientSocket = Socket()
                    clientSocket.reuseAddress = true
                    clientSocket.soTimeout = HOLE_PUNCH_TIMEOUT
                    
                    // Esperar un poco antes del siguiente intento
                    Thread.sleep(500)
                }
            }
            
            // Retornar el socket si fue exitoso
            if (successfulConnection && clientSocket.isConnected) {
                return@withContext clientSocket
            } else {
                clientSocket.close()
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en TCP hole punching: ${e.message}")
            clientSocket?.close()
            return@withContext null
        }
    }
}

/**
 * Representa un endpoint con dirección IP y puerto.
 */
data class Endpoint(
    val ip: String,
    val port: Int
)