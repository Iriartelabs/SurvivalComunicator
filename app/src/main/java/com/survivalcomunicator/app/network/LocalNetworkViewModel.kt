package com.survivalcomunicator.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.survivalcomunicator.app.data.ContactRepository
import com.survivalcomunicator.app.model.User
import com.survivalcomunicator.app.network.p2p.LocalNetworkDiscovery
import com.survivalcomunicator.app.utils.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para gestionar el estado del modo de red local
 */
class LocalNetworkViewModel(application: Application) : AndroidViewModel(application) {

    private val contactRepository = ContactRepository(application)
    private val sessionManager = SessionManager(application)
    
    // Estado del modo local
    private val _isLocalModeEnabled = MutableLiveData<Boolean>()
    val isLocalModeEnabled: LiveData<Boolean> = _isLocalModeEnabled
    
    // Estado del descubrimiento
    private val _discoveryState = MutableStateFlow(LocalNetworkDiscovery.DiscoveryState.INACTIVE)
    val discoveryState: StateFlow<LocalNetworkDiscovery.DiscoveryState> = _discoveryState
    
    // Usuarios descubiertos
    private val _discoveredUsers = MutableStateFlow<List<User>>(emptyList())
    val discoveredUsers: StateFlow<List<User>> = _discoveredUsers
    
    init {
        // Cargar estado inicial del modo local
        _isLocalModeEnabled.value = sessionManager.isLocalModeEnabled()
    }
    
    /**
     * Activa o desactiva el modo de red local
     */
    fun setLocalModeEnabled(enabled: Boolean) {
        _isLocalModeEnabled.value = enabled
        
        // Guardar preferencia
        sessionManager.setLocalModeEnabled(enabled)
    }
    
    /**
     * Actualiza el estado de descubrimiento
     */
    fun updateDiscoveryState(state: LocalNetworkDiscovery.DiscoveryState) {
        _discoveryState.value = state
    }
    
    /**
     * Actualiza la lista de usuarios descubiertos
     */
    fun updateDiscoveredUsers(users: List<User>) {
        _discoveredUsers.value = users
    }
    
    /**
     * Guarda un usuario en la base de datos si no existe
     */
    fun saveUserIfNotExists(user: User) {
        viewModelScope.launch {
            // Verificar si el usuario ya existe
            val existingUser = contactRepository.getUserById(user.id)
            
            if (existingUser == null) {
                // Guardar nuevo usuario
                contactRepository.saveUser(user)
            } else if (existingUser.lastSeen < user.lastSeen) {
                // Actualizar información si es más reciente
                contactRepository.updateUser(user)
            }
        }
    }
    
    /**
     * Guarda la lista de usuarios descubiertos en la base de datos
     */
    fun syncDiscoveredUsers() {
        viewModelScope.launch {
            val users = _discoveredUsers.value
            
            for (user in users) {
                saveUserIfNotExists(user)
            }
        }
    }
}