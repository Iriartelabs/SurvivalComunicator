package com.survivalcomunicator.app.ui.verification

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.survivalcomunicator.app.data.ContactRepository
import com.survivalcomunicator.app.model.User
import com.survivalcomunicator.app.security.CryptoManager
import com.survivalcomunicator.app.security.PeerVerification
import com.survivalcomunicator.app.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * ViewModel para la verificación de identidad de contactos
 */
class VerificationViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "VerificationViewModel"
    
    private val contactRepository = ContactRepository(application)
    private val cryptoManager = CryptoManager(application)
    private val sessionManager = SessionManager(application)
    private val peerVerification = PeerVerification(cryptoManager)
    
    // LiveData para el contacto seleccionado
    private val _contact = MutableLiveData<User?>()
    val contact: LiveData<User?> = _contact
    
    // LiveData para el estado de verificación
    private val _verificationStatus = MutableLiveData<PeerVerification.VerificationStatus>()
    val verificationStatus: LiveData<PeerVerification.VerificationStatus> = _verificationStatus
    
    // LiveData para el progreso de verificación
    private val _verificationProgress = MutableLiveData<Int>()
    val verificationProgress: LiveData<Int> = _verificationProgress

    /**
     * Carga la información de un contacto
     */
    fun loadContactInfo(contactId: String) {
        viewModelScope.launch {
            try {
                val contactInfo = contactRepository.getUserById(contactId)
                _contact.postValue(contactInfo)

                if (contactInfo != null) {
                    _verificationStatus.postValue(getVerificationStatus(contactInfo))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando información de contacto: ${e.message}")
            }
        }
    }
    
    /**
     * Determina el estado de verificación actual
     */
    private fun getVerificationStatus(contact: User): PeerVerification.VerificationStatus {
        return when {
            contact.verified == true -> PeerVerification.VerificationStatus.VERIFIED
            contact.verificationInProgress == true -> PeerVerification.VerificationStatus.PENDING
            else -> PeerVerification.VerificationStatus.UNVERIFIED
        }
    }
    
    /**
     * Calcula la huella digital de un contacto
     */
    fun getContactFingerprint(contact: User): String =
        peerVerification.calculateFingerprint(contact.publicKey)
    
    /**
     * Genera datos para código QR de verificación
     */
    fun generateQRData(contact: User): String =
        peerVerification.generateQRVerificationData(contact)
    
    /**
     * Genera palabras de verificación
     */
    fun generateVerificationWords(myPublicKey: String, theirPublicKey: String): String =
        peerVerification.generateVerificationWords(myPublicKey, theirPublicKey)
    
    /**
     * Genera número de seguridad
     */
    fun generateSafetyNumber(myPublicKey: String, theirPublicKey: String): String =
        peerVerification.generateSafetyNumber(myPublicKey, theirPublicKey)
    
    /**
     * Verifica datos recibidos de un código QR
     */
    fun verifyQRData(qrData: String, expectedContactId: String): Boolean {
        return try {
            val user = peerVerification.verifyQRData(qrData)
            if (user != null && user.id == expectedContactId) {
                markContactAsVerified(expectedContactId)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando datos QR: ${e.message}")
            false
        }
    }
    
    /**
     * Inicia verificación por desafío-respuesta
     */
    fun startChallengeVerification(contactId: String) {
        viewModelScope.launch {
            try {
                _verificationStatus.postValue(PeerVerification.VerificationStatus.PENDING)
                _verificationProgress.postValue(0)
                
                val contact = contactRepository.getUserById(contactId)
                if (contact == null) {
                    _verificationStatus.postValue(PeerVerification.VerificationStatus.FAILED)
                    return@launch
                }
                
                withContext(Dispatchers.IO) {
                    contactRepository.updateVerificationStatus(
                        contactId,
                        verificationInProgress = true,
                        verified = false
                    )
                }
                
                _verificationProgress.postValue(50)
                
                val verified = peerVerification.performChallengeVerification(
                    contact,
                    contact.publicKey
                )
                
                _verificationProgress.postValue(100)
                
                if (verified) {
                    markContactAsVerified(contactId)
                } else {
                    withContext(Dispatchers.IO) {
                        contactRepository.updateVerificationStatus(
                            contactId,
                            verificationInProgress = false,
                            verified = false
                        )
                    }
                    _verificationStatus.postValue(PeerVerification.VerificationStatus.FAILED)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en verificación por desafío: ${e.message}")
                _verificationStatus.postValue(PeerVerification.VerificationStatus.FAILED)
            }
        }
    }
    
    /**
     * Continúa la verificación por desafío-respuesta
     */
    fun continueChallengeVerification(contactId: String) {
        viewModelScope.launch {
            try {
                _verificationStatus.postValue(PeerVerification.VerificationStatus.PENDING)
                _verificationProgress.postValue(0)
                
                withContext(Dispatchers.IO) {
                    contactRepository.updateVerificationStatus(
                        contactId,
                        verificationInProgress = true,
                        verified = false
                    )
                }
                
                _verificationProgress.postValue(50)
                
                val contact = contactRepository.getUserById(contactId)
                if (contact == null) {
                    _verificationStatus.postValue(PeerVerification.VerificationStatus.FAILED)
                    return@launch
                }
                
                val verified = peerVerification.performChallengeVerification(
                    contact,
                    contact.publicKey
                )
                
                _verificationProgress.postValue(100)
                
                if (verified) {
                    markContactAsVerified(contactId)
                } else {
                    withContext(Dispatchers.IO) {
                        contactRepository.updateVerificationStatus(
                            contactId,
                            verificationInProgress = false,
                            verified = false
                        )
                    }
                    _verificationStatus.postValue(PeerVerification.VerificationStatus.FAILED)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en verificación por desafío: ${e.message}")
                _verificationStatus.postValue(PeerVerification.VerificationStatus.FAILED)
            }
        }
    }

    /**
     * Compara huellas digitales
     */
    fun compareFingerprints(contactId: String, scannedFingerprint: String): Boolean {
        return try {
            val contact = _contact.value ?: return false
            val expected = peerVerification.calculateFingerprint(contact.publicKey)
            val match = expected == scannedFingerprint
            if (match) markContactAsVerified(contactId)
            match
        } catch (e: Exception) {
            Log.e(TAG, "Error comparando huellas digitales: ${e.message}")
            false
        }
    }

    /**
     * Compara palabras de verificación
     */
    fun compareVerificationWords(contactId: String, providedWords: String): Boolean {
        return try {
            val contact = _contact.value ?: return false
            val myKey = sessionManager.getUserPublicKey() ?: return false
            val expected = peerVerification.generateVerificationWords(myKey, contact.publicKey)
            val match = expected.equals(providedWords, ignoreCase = true)
            if (match) markContactAsVerified(contactId)
            match
        } catch (e: Exception) {
            Log.e(TAG, "Error comparando palabras de verificación: ${e.message}")
            false
        }
    }

    /**
     * Compara números de seguridad
     */
    fun compareSecurityNumbers(contactId: String, providedNumber: String): Boolean {
        return try {
            val contact = _contact.value ?: return false
            val myKey = sessionManager.getUserPublicKey() ?: return false
            val expected = peerVerification.generateSafetyNumber(myKey, contact.publicKey)
            val match = expected == providedNumber
            if (match) markContactAsVerified(contactId)
            match
        } catch (e: Exception) {
            Log.e(TAG, "Error comparando números de seguridad: ${e.message}")
            false
        }
    }

    /**
     * Marca un contacto como verificado en la base de datos y actualiza el LiveData
     */
    private fun markContactAsVerified(contactId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                contactRepository.updateVerificationStatus(
                    contactId,
                    verificationInProgress = false,
                    verified = true
                )
            }
            _verificationStatus.postValue(PeerVerification.VerificationStatus.VERIFIED)
            loadContactInfo(contactId)
        }
    }
}
