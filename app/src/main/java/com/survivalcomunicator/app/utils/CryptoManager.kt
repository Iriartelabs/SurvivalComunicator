package com.survivalcomunicator.app.utils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.util.Base64
import javax.crypto.Cipher

class CryptoManager {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }
    
    companion object {
        private const val KEY_ALIAS = "survival_communicator_key"
    }
    
    // Genera un par de claves RSA si no existe
    fun getOrCreateKeyPair(): KeyPair {
        val privateKey = getPrivateKey()
        val publicKey = getPublicKey()
        
        return if (privateKey != null && publicKey != null) {
            KeyPair(publicKey, privateKey)
        } else {
            createKeyPair()
        }
    }
    
    // Recupera la clave privada del KeyStore
    private fun getPrivateKey(): PrivateKey? {
        return if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.getKey(KEY_ALIAS, null) as PrivateKey
        } else {
            null
        }
    }
    
    // Recupera la clave pública del KeyStore
    private fun getPublicKey(): PublicKey? {
        return if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.getCertificate(KEY_ALIAS)?.publicKey
        } else {
            null
        }
    }
    
    // Crea un nuevo par de claves RSA
    private fun createKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore"
        )
        
        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setKeySize(2048)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
            setDigests(KeyProperties.DIGEST_SHA256)
        }.build()
        
        keyPairGenerator.initialize(parameterSpec)
        return keyPairGenerator.generateKeyPair()
    }
    
    // Encripta un mensaje usando la clave pública del destinatario
    fun encryptMessage(message: String, recipientPublicKey: PublicKey): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey)
        val encryptedBytes = cipher.doFinal(message.toByteArray())
        return Base64.getEncoder().encodeToString(encryptedBytes)
    }
    
    // Desencripta un mensaje usando la clave privada propia
    fun decryptMessage(encryptedMessage: String): String {
        val privateKey = getPrivateKey() ?: throw IllegalStateException("No private key found")
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        val encryptedBytes = Base64.getDecoder().decode(encryptedMessage)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes)
    }
    
    // Obtiene la clave pública en formato Base64 para compartir
    fun getPublicKeyBase64(): String {
        val publicKey = getPublicKey() ?: throw IllegalStateException("No public key found")
        return Base64.getEncoder().encodeToString(publicKey.encoded)
    }
    
    // Convierte una clave pública de Base64 a objeto PublicKey
    fun publicKeyFromBase64(base64Key: String): PublicKey {
        val keyFactory = java.security.KeyFactory.getInstance("RSA")
        val keySpec = java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(base64Key))
        return keyFactory.generatePublic(keySpec)
    }
}