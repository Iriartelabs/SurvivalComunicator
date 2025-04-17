package com.survivalcomunicator.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.survivalcomunicator.app.utils.CryptoManager
import com.survivalcomunicator.app.utils.PreferencesManager
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val preferencesManager = PreferencesManager(application)
    private val cryptoManager = CryptoManager()
    
    private val _username = MutableLiveData<String>()
    val username: LiveData<String> = _username
    
    private val _serverUrl = MutableLiveData<String>()
    val serverUrl: LiveData<String> = _serverUrl
    
    private val _publicKey = MutableLiveData<String>()
    val publicKey: LiveData<String> = _publicKey
    
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    init {
        loadSettings()
        loadKeys()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            _username.value = preferencesManager.getUsername() ?: ""
            _serverUrl.value = preferencesManager.getServerUrl() ?: "https://example.com"
        }
    }
    
    private fun loadKeys() {
        try {
            // Obtener o crear el par de claves
            val keyPair = cryptoManager.getOrCreateKeyPair()
            _publicKey.value = cryptoManager.getPublicKeyBase64()
        } catch (e: Exception) {
            _errorMessage.value = "Error al cargar claves: ${e.message}"
        }
    }
    
    fun saveSettings(username: String, serverUrl: String) {
        viewModelScope.launch {
            try {
                preferencesManager.saveUsername(username)
                preferencesManager.saveServerUrl(serverUrl)
                
                _username.value = username
                _serverUrl.value = serverUrl
            } catch (e: Exception) {
                _errorMessage.value = "Error al guardar ajustes: ${e.message}"
            }
        }
    }
    
    fun exportPublicKey(): String? {
        return try {
            cryptoManager.getPublicKeyBase64()
        } catch (e: Exception) {
            _errorMessage.value = "Error al exportar clave pública: ${e.message}"
            null
        }
    }
    
    fun importPublicKey(base64Key: String): Boolean {
        return try {
            // En una app real, aquí guardaríamos la clave en una lista de claves confiables
            cryptoManager.publicKeyFromBase64(base64Key)
            true
        } catch (e: Exception) {
            _errorMessage.value = "Error al importar clave pública: ${e.message}"
            false
        }
    }
}