package com.survivalcomunicator.app.security

import android.util.Base64
import android.util.Log
import com.survivalcomunicator.app.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Clase responsable de la verificación segura de pares (otros usuarios)
 * Implementa verificación de identidad basada en desafíos, firmas digitales
 * y verificación de huella digital para prevenir ataques MITM
 */
class PeerVerification(private val cryptoManager: CryptoManager) {
    private val TAG = "PeerVerification"
    
    // Tamaño de los desafíos en bytes
    private val CHALLENGE_SIZE = 32
    
    // Métodos de verificación disponibles
    enum class VerificationMethod {
        QR_CODE,       // Verificación mediante código QR
        WORDS_PHRASE,  // Verificación mediante frase de palabras
        NUMERIC_CODE,  // Verificación mediante código numérico
        CHALLENGE      // Verificación automática mediante desafío-respuesta
    }
    
    // Estado de verificación de un par
    enum class VerificationStatus {
        UNVERIFIED,    // No verificado
        PENDING,       // Verificación en progreso
        VERIFIED,      // Verificado completamente
        FAILED         // Verificación fallida
    }
    
    /**
     * Genera un desafío aleatorio para verificación
     */
    fun generateChallenge(): ByteArray {
        val random = SecureRandom()
        val challenge = ByteArray(CHALLENGE_SIZE)
        random.nextBytes(challenge)
        return challenge
    }
    
    /**
     * Firma un desafío con la clave privada del usuario
     */
    suspend fun signChallenge(challenge: ByteArray): ByteArray? {
        return withContext(Dispatchers.Default) {
            try {
                cryptoManager.sign(challenge)
            } catch (e: Exception) {
                Log.e(TAG, "Error firmando desafío: ${e.message}")
                null
            }
        }
    }
    
    /**
     * Verifica la firma de un desafío con la clave pública del par
     */
    suspend fun verifySignature(signature: ByteArray, challenge: ByteArray, publicKey: String): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                cryptoManager.verify(challenge, signature, publicKey)
            } catch (e: Exception) {
                Log.e(TAG, "Error verificando firma: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Calcula la huella digital (fingerprint) de una clave pública
     * La huella digital es un hash SHA-256 formateado para ser legible
     */
    fun calculateFingerprint(publicKey: String): String {
        try {
            // Decodificar clave pública
            val publicKeyBytes = Base64.decode(publicKey, Base64.DEFAULT)
            
            // Calcular hash SHA-256
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(publicKeyBytes)
            
            // Formatear como hexadecimal con separadores cada 2 bytes
            val formatted = StringBuilder()
            for (i in hash.indices) {
                if (i > 0 && i % 2 == 0) formatted.append(':')
                formatted.append(String.format("%02X", hash[i]))
            }
            
            return formatted.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error calculando huella digital: ${e.message}")
            return ""
        }
    }
    
    /**
     * Genera un código de verificación de seguridad para compartir entre usuarios
     * Este código se basa en las claves públicas de ambos usuarios
     */
    fun generateSafetyNumber(myPublicKey: String, theirPublicKey: String): String {
        try {
            // Combinar ambas claves públicas (ordenadas lexicográficamente)
            val keys = if (myPublicKey < theirPublicKey) {
                myPublicKey + theirPublicKey
            } else {
                theirPublicKey + myPublicKey
            }
            
            // Decodificar y concatenar
            val combinedBytes = Base64.decode(keys, Base64.DEFAULT)
            
            // Calcular hash SHA-256
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(combinedBytes)
            
            // Convertir a un número de 5 bloques de 5 dígitos (25 dígitos en total)
            val numbers = StringBuilder()
            for (i in 0 until 25) {
                val index = i % hash.size
                val digit = (hash[index].toInt() and 0xFF) % 10
                numbers.append(digit)
                
                // Agregar espacios entre bloques de 5 dígitos
                if ((i + 1) % 5 == 0 && i < 24) {
                    numbers.append(" ")
                }
            }
            
            return numbers.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error generando número de seguridad: ${e.message}")
            return ""
        }
    }
    
    /**
     * Genera una frase de verificación usando un conjunto predefinido de palabras
     * Esta frase es más fácil de comparar verbalmente que un código numérico
     */
    fun generateVerificationWords(myPublicKey: String, theirPublicKey: String): String {
        try {
            // Lista de palabras comunes y fáciles de pronunciar
            val wordList = listOf(
                "manzana", "banana", "cereza", "delfín", "elefante",
                "fuego", "girasol", "hotel", "iguana", "jardín",
                "kiwi", "limón", "montaña", "naranja", "océano",
                "perro", "queso", "río", "sol", "tigre",
                "uva", "violeta", "wifi", "xilófono", "yoga",
                "zapato", "árbol", "búho", "casa", "dulce",
                "estrella", "flor", "gato", "huevo", "isla",
                "juego", "koala", "luna", "nube", "oso",
                "piano", "quizás", "rosa", "silla", "tren"
            )
            
            // Combinar claves (ordenadas lexicográficamente)
            val keys = if (myPublicKey < theirPublicKey) {
                myPublicKey + theirPublicKey
            } else {
                theirPublicKey + myPublicKey
            }
            
            // Calcular hash SHA-256
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(Base64.decode(keys, Base64.DEFAULT))
            
            // Seleccionar 6 palabras basadas en el hash
            val result = StringBuilder()
            for (i in 0 until 6) {
                // Usar 4 bytes del hash para seleccionar cada palabra (con módulo)
                val index = i * 4
                val wordIndex = (
                    ((hash[index % hash.size].toInt() and 0xFF) shl 24) or
                    ((hash[(index + 1) % hash.size].toInt() and 0xFF) shl 16) or
                    ((hash[(index + 2) % hash.size].toInt() and 0xFF) shl 8) or
                    (hash[(index + 3) % hash.size].toInt() and 0xFF)
                ) % wordList.size
                
                if (i > 0) result.append(" ")
                result.append(wordList[Math.abs(wordIndex)])
            }
            
            return result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error generando palabras de verificación: ${e.message}")
            return ""
        }
    }
    
    /**
     * Genera un código QR con la información de verificación
     * @return String con datos para generar el código QR
     */
    fun generateQRVerificationData(user: User): String {
        try {
            // Crear JSON con información del usuario
            val data = HashMap<String, Any>()
            data["user_id"] = user.id
            data["username"] = user.username
            data["public_key"] = user.publicKey
            data["fingerprint"] = calculateFingerprint(user.publicKey)
            data["timestamp"] = System.currentTimeMillis()
            
            // Convertir a JSON y devolver
            return cryptoManager.objectToJson(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error generando datos para QR: ${e.message}")
            return ""
        }
    }
    
    /**
     * Verifica datos recibidos de un código QR
     * @return User si la verificación es exitosa, null en caso contrario
     */
    fun verifyQRData(qrData: String): User? {
        try {
            // Parsear JSON
            val data = cryptoManager.jsonToObject(qrData)
            
            // Verificar campos obligatorios
            if (!data.containsKey("user_id") || !data.containsKey("username") ||
                !data.containsKey("public_key") || !data.containsKey("fingerprint")) {
                Log.e(TAG, "Datos QR inválidos: campos requeridos faltantes")
                return null
            }
            
            val userId = data["user_id"] as String
            val username = data["username"] as String
            val publicKey = data["public_key"] as String
            val fingerprint = data["fingerprint"] as String
            
            // Verificar que la huella digital coincida con la clave pública
            val calculatedFingerprint = calculateFingerprint(publicKey)
            if (fingerprint != calculatedFingerprint) {
                Log.e(TAG, "Verificación QR fallida: huella digital no coincide")
                return null
            }
            
            // Crear y devolver usuario
            return User(
                id = userId,
                username = username,
                publicKey = publicKey,
                lastSeen = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando datos QR: ${e.message}")
            return null
        }
    }
    
    /**
     * Realiza un proceso de verificación basado en desafío-respuesta
     * @return true si la verificación es exitosa, false en caso contrario
     */
    suspend fun performChallengeVerification(user: User, publicKey: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Generar desafío aleatorio
                val challenge = generateChallenge()
                
                // 2. Firmar el desafío con nuestra clave privada
                val signature = signChallenge(challenge) ?: return@withContext false
                
                // 3. Verificar la firma con la clave pública del contacto
                val verified = verifySignature(signature, challenge, publicKey)
                
                // 4. Verificar que la huella digital coincida
                val expectedFingerprint = calculateFingerprint(user.publicKey)
                val actualFingerprint = calculateFingerprint(publicKey)
                
                verified && expectedFingerprint == actualFingerprint
            } catch (e: Exception) {
                Log.e(TAG, "Error en verificación por desafío: ${e.message}")
                false
            }
        }
    }
}