package com.survivalcomunicator.app.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.Collections
import java.util.Random
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gestiona la ubicación de red del dispositivo (IP y puerto) para comunicación P2P.
 * Detecta cambios en la red y actualiza la información en el servidor.
 */
class LocationManager(
    private val context: Context,
    private val networkService: NetworkService,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "LocationManager"
        private const val DEFAULT_P2P_PORT = 8765
        private const val MIN_UPDATE_INTERVAL = 60000L // 1 minuto
        private const val PORT_RANGE_START = 10000
        private const val PORT_RANGE_END = 65000
    }
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val isRunning = AtomicBoolean(false)
    private var updateJob: Job? = null
    
    // Información actual de ubicación
    private var currentIpAddress: String? = null
    private var currentPort: Int = 0
    
    // Timestamp de la última actualización
    private var lastUpdateTimestamp: Long = 0
    
    // LiveData para observar cambios de ubicación
    private val _locationData = MutableLiveData<LocationData>()
    val locationData: LiveData<LocationData> = _locationData
    
    // Callback para cambios de red en Android M+
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    // BroadcastReceiver para versiones anteriores de Android
    private var networkReceiver: BroadcastReceiver? = null
    
    /**
     * Inicia el monitoreo de cambios de red y las actualizaciones periódicas.
     */
    fun start() {
        if (isRunning.getAndSet(true)) {
            Log.d(TAG, "LocationManager ya está en ejecución")
            return
        }
        
        Log.d(TAG, "Iniciando LocationManager")
        
        // Determinar puerto P2P
        findAvailablePort()
        
        // Registrar receptor de cambios de red
        registerNetworkCallbacks()
        
        // Iniciar actualizaciones periódicas
        startPeriodicUpdates()
        
        // Realizar actualización inicial
        coroutineScope.launch {
            updateLocation()
        }
    }
    
    /**
     * Detiene el monitoreo y las actualizaciones.
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) {
            return
        }
        
        Log.d(TAG, "Deteniendo LocationManager")
        
        // Cancelar actualizaciones periódicas
        updateJob?.cancel()
        updateJob = null
        
        // Desregistrar receptores
        unregisterNetworkCallbacks()
    }
    
    /**
     * Fuerza una actualización inmediata de la ubicación.
     */
    suspend fun forceUpdate() {
        updateLocation()
    }
    
    /**
     * Consulta la ubicación de un usuario por su nombre de usuario.
     */
    suspend fun getUserLocation(username: String): LocationData? = withContext(Dispatchers.IO) {
        try {
            val response = networkService.getUserLocation(username)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                return@withContext LocationData(
                    username = body.username,
                    ipAddress = body.ipAddress,
                    port = body.port,
                    lastSeen = body.lastSeen,
                    isOnline = true
                )
            } else {
                Log.e(TAG, "Error al obtener ubicación: ${response.code()} - ${response.message()}")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al obtener ubicación: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Actualiza la ubicación en el servidor.
     */
    private suspend fun updateLocation() = withContext(Dispatchers.IO) {
        try {
            // Verificar si ha pasado suficiente tiempo desde la última actualización
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTimestamp < MIN_UPDATE_INTERVAL) {
                Log.d(TAG, "Omitiendo actualización, tiempo insuficiente desde la última")
                return@withContext
            }
            
            // Obtener dirección IP actual
            val ipAddress = getLocalIpAddress()
            
            if (ipAddress != null && ipAddress != currentIpAddress) {
                Log.d(TAG, "IP cambiada: $currentIpAddress -> $ipAddress")
                currentIpAddress = ipAddress
            }
            
            if (currentIpAddress != null && currentPort > 0) {
                // Enviar actualización al servidor
                val response = networkService.updateUserLocation(currentIpAddress!!, currentPort)
                
                if (response.isSuccessful) {
                    Log.d(TAG, "Ubicación actualizada con éxito en el servidor")
                    lastUpdateTimestamp = currentTime
                    
                    // Actualizar LiveData
                    _locationData.postValue(
                        LocationData(
                            username = "", // No lo conocemos aquí
                            ipAddress = currentIpAddress!!,
                            port = currentPort,
                            lastSeen = currentTime,
                            isOnline = true
                        )
                    )
                } else {
                    Log.e(TAG, "Error al actualizar ubicación: ${response.code()} - ${response.message()}")
                }
            } else {
                Log.e(TAG, "No se puede actualizar ubicación: IP o puerto no disponibles")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al actualizar ubicación: ${e.message}")
        }
    }
    
    /**
     * Inicia actualizaciones periódicas de ubicación.
     */
    private fun startPeriodicUpdates() {
        updateJob?.cancel()
        
        updateJob = coroutineScope.launch {
            while (isRunning.get()) {
                updateLocation()
                delay(5 * 60 * 1000) // 5 minutos
            }
        }
    }
    
    /**
     * Registra los callbacks para detectar cambios de red.
     */
    private fun registerNetworkCallbacks() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Para Android 7.0+
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Red disponible")
                    coroutineScope.launch {
                        delay(1000) // Esperar a que la red se estabilice
                        updateLocation()
                    }
                }
                
                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    Log.d(TAG, "Propiedades de enlace cambiadas")
                    coroutineScope.launch {
                        delay(1000) // Esperar a que los cambios se apliquen
                        updateLocation()
                    }
                }
                
                override fun onLost(network: Network) {
                    Log.d(TAG, "Red perdida")
                }
            }
            
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
        } else {
            // Para versiones anteriores
            val filter = IntentFilter().apply {
                addAction(ConnectivityManager.CONNECTIVITY_ACTION)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            }
            
            networkReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    Log.d(TAG, "Cambio de conectividad detectado")
                    coroutineScope.launch {
                        delay(1000) // Esperar a que los cambios se apliquen
                        updateLocation()
                    }
                }
            }
            
            context.registerReceiver(networkReceiver, filter)
        }
    }
    
    /**
     * Desregistra los callbacks de red.
     */
    private fun unregisterNetworkCallbacks() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback?.let {
                try {
                    connectivityManager.unregisterNetworkCallback(it)
                } catch (e: Exception) {
                    Log.e(TAG, "Error al desregistrar networkCallback: ${e.message}")
                }
                networkCallback = null
            }
        } else {
            networkReceiver?.let {
                try {
                    context.unregisterReceiver(it)
                } catch (e: Exception) {
                    Log.e(TAG, "Error al desregistrar networkReceiver: ${e.message}")
                }
                networkReceiver = null
            }
        }
    }
    
    /**
     * Obtiene la dirección IP local.
     */
    private fun getLocalIpAddress(): String? {
        try {
            // Intentar primero la red activa
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            if (activeNetworkInfo != null && activeNetworkInfo.isConnected) {
                // Obtener interfaces de red
                val networkInterfaces = NetworkInterface.getNetworkInterfaces()
                val interfaces = Collections.list(networkInterfaces)
                
                // Primero intentar con interfaces que no sean loopback ni virtuales
                for (networkInterface in interfaces) {
                    if (!networkInterface.isLoopback && !networkInterface.isVirtual) {
                        val addresses = Collections.list(networkInterface.inetAddresses)
                        for (address in addresses) {
                            if (!address.isLoopbackAddress && address.hostAddress.indexOf(':') == -1) {
                                return address.hostAddress
                            }
                        }
                    }
                }
                
                // Si no encontramos nada, intentar con cualquier interfaz
                for (networkInterface in interfaces) {
                    val addresses = Collections.list(networkInterface.inetAddresses)
                    for (address in addresses) {
                        if (!address.isLoopbackAddress && address.hostAddress.indexOf(':') == -1) {
                            return address.hostAddress
                        }
                    }
                }
            }
            
            // Si todo falla, intentar con InetAddress.getLocalHost()
            val localhost = InetAddress.getLocalHost()
            return localhost.hostAddress
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener IP local: ${e.message}")
        }
        
        return null
    }
    
    /**
     * Encuentra un puerto disponible para el servidor P2P.
     */
    private fun findAvailablePort() {
        // Primero intentar con el puerto por defecto
        if (isPortAvailable(DEFAULT_P2P_PORT)) {
            currentPort = DEFAULT_P2P_PORT
            return
        }
        
        // Si el puerto por defecto no está disponible, buscar uno aleatorio
        val random = Random()
        for (attempt in 1..10) {
            val port = PORT_RANGE_START + random.nextInt(PORT_RANGE_END - PORT_RANGE_START)
            if (isPortAvailable(port)) {
                currentPort = port
                return
            }
        }
        
        Log.e(TAG, "No se pudo encontrar un puerto disponible después de 10 intentos")
    }
    
    /**
     * Verifica si un puerto está disponible.
     */
    private fun isPortAvailable(port: Int): Boolean {
        return try {
            val serverSocket = ServerSocket(port)
            serverSocket.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Clase de datos para representar información de ubicación de un usuario.
 */
data class LocationData(
    val username: String,
    val ipAddress: String,
    val port: Int,
    val lastSeen: Long,
    val isOnline: Boolean
)