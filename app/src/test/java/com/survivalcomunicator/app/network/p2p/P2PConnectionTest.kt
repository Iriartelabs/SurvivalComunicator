package com.survivalcomunicator.app.network.p2p

import com.survivalcomunicator.app.security.CryptoManager
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.*
import java.net.Socket
import java.net.SocketException

/**
 * Pruebas unitarias para la clase P2PConnection
 */
class P2PConnectionTest {

    @MockK
    private lateinit var mockSocket: Socket

    @MockK
    private lateinit var mockOutputStream: ByteArrayOutputStream

    @MockK
    private lateinit var mockInputStream: ByteArrayInputStream

    @MockK
    private lateinit var mockCryptoManager: CryptoManager

    private lateinit var connection: P2PConnection
    private val userId = "test-user-id"
    private val publicKey = "test-public-key"

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)

        every { mockSocket.isClosed } returns false
        every { mockSocket.getOutputStream() } returns mockOutputStream
        every { mockSocket.getInputStream() } returns mockInputStream

        connection = P2PConnection(mockSocket, userId, publicKey, mockCryptoManager)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `test isConnected returns true when socket is open`() {
        // Given
        every { mockSocket.isClosed } returns false

        // When/Then
        assertTrue("Connection should be active when socket is open", connection.isConnected())
    }

    @Test
    fun `test isConnected returns false when socket is closed`() {
        // Given
        every { mockSocket.isClosed } returns true

        // When/Then
        assertFalse("Connection should be inactive when socket is closed", connection.isConnected())
    }

    @Test
    fun `test close method closes socket and streams`() {
        // Given
        every { mockSocket.close() } just runs

        // When
        connection.close()

        // Then
        verify { mockSocket.close() }
        assertFalse("Connection should be inactive after closing", connection.isConnected())
    }

    @Test
    fun `test sendMessage writes message to output stream`() = runBlocking {
        // Given
        val testMessage = "Hello, world!"
        every { mockOutputStream.write(any()) } just runs
        every { mockOutputStream.flush() } just runs

        // When
        val result = connection.sendMessage(testMessage)

        // Then
        assertTrue("Message should be sent successfully", result)
    }

    @Test
    fun `test sendMessage returns false when socket is closed`() = runBlocking {
        // Given
        val testMessage = "Hello, world!"
        every { mockSocket.isClosed } returns true

        // When
        val result = connection.sendMessage(testMessage)

        // Then
        assertFalse("Message should fail to send when socket is closed", result)
    }

    @Test
    fun `test sendMessage handles exception`() = runBlocking {
        // Given
        val testMessage = "Hello, world!"
        every { mockOutputStream.write(any()) } throws IOException("Test exception")

        // When
        val result = connection.sendMessage(testMessage)

        // Then
        assertFalse("Message should fail to send when exception occurs", result)
        assertFalse("Connection should be marked as inactive after exception", connection.isConnected())
    }

    @Test
    fun `test receiveMessage returns null when socket is closed`() = runBlocking {
        // Given
        every { mockSocket.isClosed } returns true

        // When
        val result = connection.receiveMessage()

        // Then
        assertNull("No message should be received when socket is closed", result)
    }

    @Test
    fun `test receiveMessage handles socket exception`() = runBlocking {
        // Given
        every { mockInputStream.read() } throws SocketException("Test exception")

        // When
        val result = connection.receiveMessage()

        // Then
        assertNull("No message should be received when exception occurs", result)
        assertFalse("Connection should be marked as inactive after exception", connection.isConnected())
    }

    @Test
    fun `test isStale returns true when connection is old`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val timeout = 5000L // 5 seconds

        // When
        connection.updateLastSeen() // Update to current time
        val initialResult = connection.isStale(timeout)

        // Then
        assertFalse("New connection should not be stale", initialResult)

        // Mock time passing
        mockkStatic(System::class)
        every { System.currentTimeMillis() } returns currentTime + timeout + 1000

        // When/Then
        assertTrue("Connection should be stale after timeout", connection.isStale(timeout))

        unmockkStatic(System::class)
    }
}