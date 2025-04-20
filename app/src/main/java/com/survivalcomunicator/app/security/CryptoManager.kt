// app/src/main/java/com/survivalcomunicator/app/utils/CryptoManager.kt
package com.survivalcomunicator.app.utils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * Manejador de criptografía usando el AndroidKeyStore.
 */
class CryptoManager {
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    companion object {
        private const val KEY_ALIAS = "survival_communicator_key"
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
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setKeySize(2048)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
            setDigests(KeyProperties.DIGEST_SHA256)
        }.build()

        keyPairGenerator.initialize(parameterSpec)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Encripta un texto con la clave pública del receptor.
     */
    fun encryptMessage(message: String, recipientPublicKey: PublicKey): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey)
        val encryptedBytes = cipher.doFinal(message.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
    }

    /**
     * Desencripta un texto con la clave privada propia.
     */
    fun decryptMessage(encryptedMessage: String): String {
        val privateKey = getPrivateKey() ?: throw IllegalStateException("No private key found")
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        val decodedBytes = Base64.decode(encryptedMessage, Base64.DEFAULT)
        val decryptedBytes = cipher.doFinal(decodedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Obtiene la clave pública en Base64 para compartir.
     */
    fun getPublicKeyBase64(): String {
        val publicKey = getPublicKey() ?: throw IllegalStateException("No public key found")
        return Base64.encodeToString(publicKey.encoded, Base64.DEFAULT)
    }

    /**
     * Reconstruye una PublicKey a partir de un string Base64.
     */
    fun publicKeyFromBase64(base64Key: String): PublicKey {
        val decodedBytes = Base64.decode(base64Key, Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(decodedBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(keySpec)
    }
}

