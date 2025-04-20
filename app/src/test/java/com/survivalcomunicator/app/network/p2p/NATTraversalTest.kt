package com.survivalcomunicator.app.network.p2p

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.net.Socket
import com.survivalcomunicator.app.model.PeerAddress

/**
 * Pruebas unitarias para la clase NATTraversal
 */
class NATTraversalTest {

    private lateinit var mockStunClient: StunClient
    private lateinit var mockTurnClient: TurnClient
    private lateinit var mockSocket: Socket

    private lateinit var natTraversal: NATTraversal

    @Before
    fun setup() {
        // Crear mocks manualmente sin anotaciones
        mockStunClient = mockk(relaxed = true)
        mockTurnClient = mockk(relaxed = true)
        mockSocket = mockk(relaxed = true)

        natTraversal = NATTraversal()

        // Inyectar los mocks en la instancia
        NATTraversal::class.java.getDeclaredField("stunClient").apply {
            isAccessible = true
            set(natTraversal, mockStunClient)
        }
        NATTraversal::class.java.getDeclaredField("turnClient").apply {
            isAccessible = true
            set(natTraversal, mockTurnClient)
        }
    }

    @Test
    fun `test direct connection succeeds`() = runBlocking {
        // Given
        val peerAddress = PeerAddress("192.168.1.5", 8765)
        mockkConstructor(Socket::class)
        every { anyConstructed<Socket>().connect(any(), any()) } just runs

        // When
        val connection = natTraversal.establishConnection(peerAddress)

        // Then
        assertNotNull("Connection should be established", connection)
        assertTrue("Connection method should be DIRECT", connection.connectionType == ConnectionType.DIRECT)
    }

    @Test
    fun `test direct connection failure falls back to STUN`() = runBlocking {
        // Given
        val peerAddress = PeerAddress("192.168.1.5", 8765)

        // Direct falla
        mockkConstructor(Socket::class)
        every { anyConstructed<Socket>().connect(any(), any()) } throws Exception("Connection refused")

        // STUN discovery succeeds
        val mappedEndpoint = MappedEndpoint("203.0.113.5", 34567)
        every { mockStunClient.discoverPublicEndpoint() } returns mappedEndpoint

        // Hole-punch con STUN succeeds
        every { mockStunClient.createHolePunch(peerAddress, mappedEndpoint) } returns mockSocket
        every { mockSocket.isConnected } returns true

        // When
        val connection = natTraversal.establishConnection(peerAddress)

        // Then
        assertNotNull("Connection should be established", connection)
        assertEquals("Connection method should be STUN", ConnectionType.STUN, connection.connectionType)

        verify { mockStunClient.discoverPublicEndpoint() }
        verify { mockStunClient.createHolePunch(peerAddress, mappedEndpoint) }
    }

    @Test
    fun `test all methods fail except TURN relay`() = runBlocking {
        // Given
        val peerAddress = PeerAddress("192.168.1.5", 8765)

        // Direct falla
        mockkConstructor(Socket::class)
        every { anyConstructed<Socket>().connect(any(), any()) } throws Exception("Connection refused")

        // STUN falla
        every { mockStunClient.discoverPublicEndpoint() } throws Exception("STUN server unreachable")

        // TURN succeeds
        every { mockTurnClient.relayConnection(peerAddress) } returns mockSocket
        every { mockSocket.isConnected } returns true

        // When
        val connection = natTraversal.establishConnection(peerAddress)

        // Then
        assertNotNull("Connection should be established", connection)
        assertEquals("Connection method should be TURN", ConnectionType.TURN, connection.connectionType)

        verify { mockTurnClient.relayConnection(peerAddress) }
    }

    @Test
    fun `test all connection methods fail`() = runBlocking {
        // Given
        val peerAddress = PeerAddress("192.168.1.5", 8765)

        mockkConstructor(Socket::class)
        every { anyConstructed<Socket>().connect(any(), any()) } throws Exception("Connection refused")
        every { mockStunClient.discoverPublicEndpoint() } throws Exception("STUN server unreachable")
        every { mockTurnClient.relayConnection(peerAddress) } throws Exception("TURN relay failed")

        // When/Then
        assertThrows(ConnectionException::class.java) {
            runBlocking {
                natTraversal.establishConnection(peerAddress)
            }
        }

        verify { mockStunClient.discoverPublicEndpoint() }
        verify { mockTurnClient.relayConnection(peerAddress) }
    }

    @Test
    fun `test connection timeout handling`() = runBlocking {
        // Given
        val peerAddress = PeerAddress("192.168.1.5", 8765)

        mockkConstructor(Socket::class)
        every { anyConstructed<Socket>().connect(any(), any()) } throws java.net.SocketTimeoutException("Connection timed out")
        every { mockStunClient.discoverPublicEndpoint() } throws java.net.SocketTimeoutException("STUN request timed out")
        every { mockTurnClient.relayConnection(peerAddress) } returns mockSocket
        every { mockSocket.isConnected } returns true

        // When
        val connection = natTraversal.establishConnection(peerAddress)

        // Then
        assertNotNull("Connection should be established via TURN after timeouts", connection)
        assertEquals("Connection method should be TURN", ConnectionType.TURN, connection.connectionType)

        verify { mockStunClient.discoverPublicEndpoint() }
        verify { mockTurnClient.relayConnection(peerAddress) }
    }
}
