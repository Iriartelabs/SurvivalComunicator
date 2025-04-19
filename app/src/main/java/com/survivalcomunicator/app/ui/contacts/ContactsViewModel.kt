package com.survivalcomunicator.app.ui.contacts

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.survivalcomunicator.app.App
import com.survivalcomunicator.app.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = (application as App).repository
    
    private val _contacts = MutableLiveData<List<User>>()
    val contacts: LiveData<List<User>> = _contacts
    
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    private val TAG = "ContactsViewModel"
    
    init {
        loadContacts()
    }
    
    fun loadContacts() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Iniciando carga de contactos")
                
                val users = repository.getAllUsers()
                    .flowOn(Dispatchers.IO)
                    .catch { e ->
                        Log.e(TAG, "Error al cargar contactos: ${e.message}")
                        _errorMessage.postValue("Error al cargar contactos: ${e.message}")
                    }
                    .firstOrNull() ?: emptyList()
                
                Log.d(TAG, "Contactos cargados: ${users.size}")
                _contacts.postValue(users)
                
            } catch (e: Exception) {
                Log.e(TAG, "Excepción al cargar contactos: ${e.message}")
                _errorMessage.postValue("Error al cargar contactos: ${e.message}")
            }
        }
    }
    
    fun addNewContact(username: String) {
        if (username.isBlank()) {
            _errorMessage.value = "El nombre de usuario no puede estar vacío"
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Buscando usuario: $username")
                val user = repository.findUser(username)
                if (user != null) {
                    Log.d(TAG, "Usuario encontrado: ${user.username}")
                    // Usuario encontrado, refrescar la lista de contactos
                    withContext(Dispatchers.Main) {
                        // Mostrar mensaje de éxito en el hilo principal
                        _errorMessage.value = "Contacto añadido: ${user.username}"
                        
                        // Refrescar la lista de contactos
                        loadContacts()
                    }
                } else {
                    Log.d(TAG, "Usuario no encontrado: $username")
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = "Usuario no encontrado: $username"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al añadir contacto: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error al añadir contacto: ${e.message}"
                }
            }
        }
    }
    
    fun refreshContacts() {
        loadContacts()
    }
}