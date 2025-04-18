package com.survivalcomunicator.app
import android.app.Application
import com.survivalcomunicator.app.database.AppDatabase
import com.survivalcomunicator.app.network.NetworkServiceImpl
import com.survivalcomunicator.app.repository.Repository
import com.survivalcomunicator.app.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class App : Application() {
    
    private val applicationScope = CoroutineScope(Dispatchers.IO)
    
    // Lazy para inicializar componentes solo cuando se necesiten
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val preferencesManager by lazy { PreferencesManager(this) }
    private fun getServerUrlSync(): String {
        // Leer la URL guardada en preferencias de forma síncrona
        return runBlocking {
            preferencesManager.getServerUrl() ?: "http://192.168.1.35:3000/"
        }
    }
    private val networkService by lazy { 
        NetworkServiceImpl(
            serverUrl = getServerUrlSync()
        ) 
    }
    
    val repository by lazy { 
        Repository(
            messageDao = database.messageDao(),
            userDao = database.userDao(),
            networkService = networkService
        ) 
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Iniciar escucha de mensajes entrantes
        applicationScope.launch {
            repository.startListeningForMessages()
        }
    }
    
    private fun getServerUrl(): String {
        // En un escenario real, esto se obtendría de las preferencias
        // pero para simplificar, usamos un valor por defecto
        return "https://example.com/api"
    }
}