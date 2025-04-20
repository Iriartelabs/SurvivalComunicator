package com.survivalcomunicator.app.security

import android.util.Base64
import com.survivalcomunicator.app.model.User
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.security.KeyPair
import java.security.MessageDigest

/**
 * Pruebas unitarias para la clase PeerVerification
 */
class PeerVerificationTest {

    @MockK
    private lateinit var cryptoManager: CryptoManager

    private lateinit var peerVerification: PeerVerification
    private lateinit var testUser: User

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        
        // Mock Base64 y MessageDigest que son clases Android
        mockkStatic(Base64::class)
        mockkStatic(MessageDigest::class)
        
        every { Base64.decode(any<String>(), any()) } returns "test-decoded-key".toByteArray()
        every { Base64.encode(any<ByteArray>(), any()) } returns "test-encoded-data".toByteArray()
        
        val mockDigest = mockk<MessageDigest>(relaxed = true)
        every { MessageDigest.getInstance(any()) } returns mockDigest
        every { mockDigest.digest(any()) } returns byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        
        peerVerification = PeerVerification(cryptoManager)
        
        testUser = User(
            id = "test-user-id",
            username = "testuser",
            publicKey = "test-public-key",
            lastSeen = System.currentTimeMillis()
        )
    }

    @Test
    fun `test generateChallenge creates non-empty byte array`() {
        // When
        val challenge = peerVerification.generateChallenge()
        
        // Then
        assertNotNull("Challenge should not be null", challenge)
        assertTrue("Challenge should not be empty", challenge.isNotEmpty())
    }

    @Test
    fun `test signChallenge calls cryptoManager sign`() = runBlocking {
        // Given
        val challenge = byteArrayOf(1, 2, 3, 4)
        val expectedSignature = byteArrayOf(10, 20, 30, 40)
        every { cryptoManager.sign(challenge) } returns expectedSignature
        
        // When
        val signature = peerVerification.signChallenge(challenge)
        
        // Then
        assertEquals("Signature should match expected", expectedSignature, signature)
        verify { cryptoManager.sign(challenge) }
    }

    @Test
    fun `test verifySignature calls cryptoManager verify`() = runBlocking {
        // Given
        val challenge = byteArrayOf(1, 2, 3, 4)
        val signature = byteArrayOf(10, 20, 30, 40)
        val publicKey = "test-public-key"
        
        every { cryptoManager.verify(challenge, signature, publicKey) } returns true
        
        // When
        val result = peerVerification.verifySignature(signature, challenge, publicKey)
        
        // Then
        assertTrue("Signature verification should succeed", result)
        verify { cryptoManager.verify(challenge, signature, publicKey) }
    }

    @Test
    fun `test calculateFingerprint formats hash correctly`() {
        // Given
        val publicKey = "test-public-key"
        val mockHash = byteArrayOf(0xA, 0xB, 0xC, 0xD, 0xE, 0xF)
        
        val mockDigest = mockk<MessageDigest>()
        every { MessageDigest.getInstance("SHA-256") } returns mockDigest
        every { mockDigest.digest(any()) } returns mockHash
        
        // When
        val fingerprint = peerVerification.calculateFingerprint(publicKey)
        
        // Then
        assertTrue("Fingerprint should be formatted with colons", fingerprint.contains(":"))
        assertTrue("Fingerprint should be uppercase hex", fingerprint.matches(Regex("([0-9A-F]{2}:)*([0-9A-F]{2})")))
    }

    @Test
    fun `test generateSafetyNumber creates numeric string with spaces`() {
        // Given
        val myPublicKey = "my-public-key"
        val theirPublicKey = "their-public-key"
        
        // When
        val safetyNumber = peerVerification.generateSafetyNumber(myPublicKey, theirPublicKey)
        
        // Then
        assertNotNull("Safety number should not be null", safetyNumber)
        assertTrue("Safety number should contain spaces", safetyNumber.contains(" "))
        assertTrue("Safety number should be numeric with spaces", 
            safetyNumber.replace(" ", "").matches(Regex("[0-9]+")))
    }

    @Test
    fun `test generateVerificationWords creates space-separated words`() {
        // Given
        val myPublicKey = "my-public-key"
        val theirPublicKey = "their-public-key"
        
        // When
        val words = peerVerification.generateVerificationWords(myPublicKey, theirPublicKey)
        
        // Then
        assertNotNull("Verification words should not be null", words)
        assertTrue("Verification words should contain spaces", words.contains(" "))
        
        val wordCount = words.split(" ").size
        assertEquals("Should generate 6 words", 6, wordCount)
    }

    @Test
    fun `test same key pairs generate same verification words`() {
        // Given
        val keyA = "key-a"
        val keyB = "key-b"
        
        // When
        val words1 = peerVerification.generateVerificationWords(keyA, keyB)
        val words2 = peerVerification.generateVerificationWords(keyA, keyB)
        
        // Then
        assertEquals("Same keys should generate same words", words1, words2)
    }

    @Test
    fun `test same key pairs in different order generate same verification words`() {
        // Given
        val keyA = "key-a"
        val keyB = "key-b"
        
        // When
        val words1 = peerVerification.generateVerificationWords(keyA, keyB)
        val words2 = peerVerification.generateVerificationWords(keyB, keyA)
        
        // Then
        assertEquals("Key order should not affect generated words", words1, words2)
    }

    @Test
    fun `test generateQRVerificationData includes user information`() {
        // Given
        every { cryptoManager.objectToJson(any()) } returns "{\"user_id\":\"test-user-id\",\"username\":\"testuser\"}"
        
        // When
        val qrData = peerVerification.generateQRVerificationData(testUser)
        
        // Then
        assertNotNull("QR data should not be null", qrData)
        verify { cryptoManager.objectToJson(match { map -> 
            map["user_id"] == testUser.id && 
            map["username"] == testUser.username &&
            map["public_key"] == testUser.publicKey
        }) }
    }

    @Test
    fun `test verifyQRData validates user information`() {
        // Given
        val jsonData = "{\"user_id\":\"test-user-id\",\"username\":\"testuser\",\"public_key\":\"test-public-key\",\"fingerprint\":\"test-fingerprint\"}"
        
        every { cryptoManager.jsonToObject(jsonData) } returns mapOf(
            "user_id" to "test-user-id",
            "username" to "testuser",
            "public_key" to "test-public-key",
            "fingerprint" to "test-fingerprint"
        )
        
        every { peerVerification.calculateFingerprint(any()) } returns "test-fingerprint"
        
        // When
        val user = peerVerification.verifyQRData(jsonData)
        
        // Then
        assertNotNull("Verified user should not be null", user)
        assertEquals("User ID should match", "test-user-id", user?.id)
        assertEquals("Username should match", "testuser", user?.username)
    }

    @Test
    fun `test verifyQRData returns null for invalid fingerprint`() {
        // Given
        val jsonData = "{\"user_id\":\"test-user-id\",\"username\":\"testuser\",\"public_key\":\"test-public-key\",\"fingerprint\":\"wrong-fingerprint\"}"
        
        every { cryptoManager.jsonToObject(jsonData) } returns mapOf(
            "user_id" to "test-user-id",
            "username" to "testuser",
            "public_key" to "test-public-key",
            "fingerprint" to "wrong-fingerprint"
        )
        
        every { peerVerification.calculateFingerprint(any()) } returns "correct-fingerprint"
        
        // When
        val user = peerVerification.verifyQRData(jsonData)
        
        // Then
        assertNull("User should be null for invalid fingerprint", user)
    }

    @Test
    fun `test performChallengeVerification succeeds with valid signature`() = runBlocking {
        // Given
        val challenge = byteArrayOf(1, 2, 3, 4)
        val signature = byteArrayOf(10, 20, 30, 40)
        
        // Mock para generar desafío
        every { peerVerification.generateChallenge() } returns challenge
        
        // Mock para firmar desafío
        every { peerVerification.signChallenge(challenge) } returns signature
        
        // Mock para verificar firma
        every { peerVerification.verifySignature(signature, challenge, testUser.publicKey) } returns true
        
        // Mock para huellas digitales
        every { peerVerification.calculateFingerprint(testUser.publicKey) } returns "fingerprint"
        every { peerVerification.calculateFingerprint(testUser.publicKey) } returns "fingerprint"
        
        // When
        val result = peerVerification.performChallengeVerification(testUser, testUser.publicKey)
        
        // Then
        assertTrue("Challenge verification should succeed", result)
    }

    @Test
    fun `test performChallengeVerification fails with invalid signature`() = runBlocking {
        // Given
        val challenge = byteArrayOf(1, 2, 3, 4)
        val signature = byteArrayOf(10, 20, 30, 40)
        
        // Mock para generar desafío
        every { peerVerification.generateChallenge() } returns challenge
        
        // Mock para firmar desafío
        every { peerVerification.signChallenge(challenge) } returns signature
        
        // Mock para verificar firma - devuelve false
        every { peerVerification.verifySignature(signature, challenge, testUser.publicKey) } returns false
        
        // When
        val result = peerVerification.performChallengeVerification(testUser, testUser.publicKey)
        
        // Then
        assertFalse("Challenge verification should fail with invalid signature", result)
    }
}