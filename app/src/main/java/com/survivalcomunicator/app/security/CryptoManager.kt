// app/src/main/java/com/survivalcomunicator/app/security/CryptoManager.kt
package com.survivalcomunicator.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Gestor de criptografía para el sistema de comunicación P2P.
 * Utiliza AndroidKeyStore para almacenar las claves de manera segura
 * y proporciona métodos para cifrado, descifrado, firma y verificación.
 */
class CryptoManager {
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    
    companion object {
        private const val KEY_ALIAS = "survival_communicator_key"
        private const val RSA_ALGORITHM = "RSA"
        private const val AES_ALGORITHM = "AES"
        private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
        
        // Singleton para acceso global
        @Volatile
        private var instance: CryptoManager? = null
        
        fun getInstance(): CryptoManager {
            return instance ?: synchronized(this) {
                instance ?: CryptoManager().also { instance = it }
            }
        }
    }
    
    /**
     * Devuelve el par de claves existente o lo crea si no existe.
     */
    fun getOrCreateKeyPair(): KeyPair {
        val privateKey = getPrivateKey()
        val publicKey = getPublicKey()
        return if (privateKey != null && publicKey != null) {
            KeyPair(publicKey, privateKey)
        } else {
            createKeyPair()
        }
    }
    
    /**
     * Genera un nuevo par de claves RSA.
     */
    fun generateKeyPair(): KeyPair {
        return createKeyPair()
    }
    
    private fun getPrivateKey(): PrivateKey? {
        return if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.getKey(KEY_ALIAS, null) as PrivateKey
        } else null
    }
    
    private fun getPublicKey(): PublicKey? {
        return if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.getCertificate(KEY_ALIAS)?.publicKey
        } else null
    }
    
    private fun createKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore"
        )
        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).apply {
            setKeySize(2048)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
            setDigests(KeyProperties.DIGEST_SHA256)
        }.build()

        keyPairGenerator.initialize(parameterSpec)
        return keyPairGenerator.generateKeyPair()
    }
    
    /**
     * Codifica una clave pública en formato Base64.
     */
    fun encodePublicKey(publicKey: PublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }
    
    /**
     * Obtiene la clave pública actual en formato Base64.
     */
    fun getMyPublicKeyBase64(): String {
        val publicKey = getPublicKey() ?: throw IllegalStateException("No public key found")
        return encodePublicKey(publicKey)
    }
    
    /**
     * Decodifica una clave pública desde Base64.
     */
    fun publicKeyFromBase64(base64Key: String): PublicKey {
        val keyBytes = Base64.decode(base64Key, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance(RSA_ALGORITHM)
        return keyFactory.generatePublic(keySpec)
    }
    
    /**
     * Cifra un mensaje para un destinatario usando su clave pública.
     * Utiliza cifrado híbrido RSA+AES para mayor eficiencia y seguridad.
     */
    fun encryptMessage(message: String, recipientPublicKeyBase64: String): ByteArray {
        // Primero recuperamos la clave pública del destinatario
        val recipientPublicKey = publicKeyFromBase64(recipientPublicKeyBase64)
        
        // Generar clave de sesión AES
        val secretKey = generateAESKey()
        
        // Cifrar mensaje con AES
        val encryptedContent = encryptWithAES(message.toByteArray(StandardCharsets.UTF_8), secretKey)
        
        // Cifrar clave AES con la clave pública del destinatario
        val encryptedKey = encryptWithRSA(secretKey.encoded, recipientPublicKey)
        
        // Combinar en un solo array
        val result = ByteArray(4 + encryptedKey.size + encryptedContent.size)
        System.arraycopy(intToBytes(encryptedKey.size), 0, result, 0, 4)
        System.arraycopy(encryptedKey, 0, result, 4, encryptedKey.size)
        System.arraycopy(encryptedContent, 0, result, 4 + encryptedKey.size, encryptedContent.size)
        
        return result
    }
    
    /**
     * Descifra un mensaje con la clave privada del usuario actual.
     */
    fun decryptMessage(encryptedData: ByteArray): String {
        // Obtener clave privada
        val privateKey = getPrivateKey() ?: throw IllegalStateException("No private key found")
        
        // Extraer tamaño de clave cifrada
        val keySize = bytesToInt(encryptedData, 0)
        
        // Extraer clave cifrada
        val encryptedKey = ByteArray(keySize)
        System.arraycopy(encryptedData, 4, encryptedKey, 0, keySize)
        
        // Extraer contenido cifrado
        val encryptedContent = ByteArray(encryptedData.size - 4 - keySize)
        System.arraycopy(encryptedData, 4 + keySize, encryptedContent, 0, encryptedContent.size)
        
        // Descifrar clave AES
        val decryptedKeyBytes = decryptWithRSA(encryptedKey, privateKey)
        val secretKey = SecretKeySpec(decryptedKeyBytes, AES_ALGORITHM)
        
        // Descifrar contenido
        val decryptedContent = decryptWithAES(encryptedContent, secretKey)
        
        return String(decryptedContent, StandardCharsets.UTF_8)
    }
    
    /**
     * Versión alternativa para compatibilidad con la implementación anterior.
     * Utiliza únicamente cifrado RSA directamente.
     */
    fun encryptMessageLegacy(message: String, recipientPublicKey: PublicKey): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey)
        val encryptedBytes = cipher.doFinal(message.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
    }
    
    /**
     * Descifra un mensaje cifrado con la versión legacy del método.
     */
    fun decryptMessageLegacy(encryptedMessage: String): String {
        val privateKey = getPrivateKey() ?: throw IllegalStateException("No private key found")
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        val decodedBytes = Base64.decode(encryptedMessage, Base64.DEFAULT)
        val decryptedBytes = cipher.doFinal(decodedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }
    
    /**
     * Firma unos datos con la clave privada del usuario.
     */
    fun signData(data: ByteArray): ByteArray {
        val privateKey = getPrivateKey() ?: throw IllegalStateException("No private key found")
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }
    
    /**
     * Verifica la firma de unos datos usando la clave pública proporcionada.
     */
    fun verifySignature(signature: ByteArray, data: ByteArray, publicKey: PublicKey): Boolean {
        val sig = Signature.getInstance(SIGNATURE_ALGORITHM)
        sig.initVerify(publicKey)
        sig.update(data)
        return sig.verify(signature)
    }
    
    // Métodos de ayuda para cifrado AES
    
    private fun generateAESKey(): SecretKey {
        val generator = KeyGenerator.getInstance(AES_ALGORITHM)
        generator.init(256)
        return generator.generateKey()
    }
    
    private fun encryptWithAES(data: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data)
        
        // Combinar IV y datos cifrados
        val result = ByteArray(iv.size + encryptedData.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(encryptedData, 0, result, iv.size, encryptedData.size)
        return result
    }
    
    private fun decryptWithAES(encryptedData: ByteArray, key: SecretKey): ByteArray {
        // Extraer IV (primeros 12 bytes para GCM)
        val iv = ByteArray(12)  // GCM utiliza IV de 12 bytes
        System.arraycopy(encryptedData, 0, iv, 0, iv.size)
        
        // Extraer datos cifrados
        val ciphertext = ByteArray(encryptedData.size - iv.size)
        System.arraycopy(encryptedData, iv.size, ciphertext, 0, ciphertext.size)
        
        // Descifrar
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)  // 128-bit tag length
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(ciphertext)
    }
    
    // Métodos auxiliares para RSA
    
    private fun encryptWithRSA(data: ByteArray, publicKey: PublicKey): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(data)
    }
    
    private fun decryptWithRSA(encryptedData: ByteArray, privateKey: PrivateKey): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        return cipher.doFinal(encryptedData)
    }
    
    // Utilidades de conversión
    
    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value shr 24).toByte(),
            (value shr 16).toByte(),
            (value shr 8).toByte(),
            value.toByte()
        )
    }
    
    private fun bytesToInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
               ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
               ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
               (bytes[offset + 3].toInt() and 0xFF)
    }
}