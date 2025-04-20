package com.survivalcomunicator.app.network.p2p

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.survivalcomunicator.app.model.User
import com.survivalcomunicator.app.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.InetAddress
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Clase responsable de descubrir y anunciar dispositivos en la red local
 * utilizando Network Service Discovery (NSD/mDNS)
 */
class LocalNetworkDiscovery(
    private val context: Context,
    private val userId: String,
    private val username: String,
    private val publicKey: String
) {
    private val TAG = "LocalNetworkDiscovery"
    
    // Constantes para el servicio NSD
    companion object {
        const val SERVICE_TYPE = "_survivalchat._tcp."
        const val LOCAL_SERVICE_NAME = "SurvivalChat_Local"
        const val NSD_PORT_DEFAULT = 8765
    }
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var nsdManager: NsdManager? = null
    
    // Puerto en el que escucha nuestro servicio
    private var localPort = NSD_PORT_DEFAULT
    
    // Servicio registrado localmente
    private var localServiceInfo: NsdServiceInfo? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var resolveListener: NsdManager.ResolveListener? = null
    
    // Dispositivos descubiertos
    private val discoveredDevices = ConcurrentHashMap<String, User>()
    
    // Estado de descubrimiento
    private val _discoveryState = MutableStateFlow(DiscoveryState.INACTIVE)
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState
    
    // Estado de dispositivos descubiertos
    private val _discoveredUsers = MutableStateFlow<List<User>>(emptyList())
    val discoveredUsers: StateFlow<List<User>> = _discoveredUsers
    
    /**
     * Estados posibles para el descubrimiento
     */
    enum class DiscoveryState {
        INACTIVE,
        STARTING,
        ACTIVE,
        FAILED
    }
    
    /**
     * Inicializa el descubrimiento local
     */
    fun initialize(servicePort: Int = NSD_PORT_DEFAULT) {
        try {
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            localPort = servicePort
            
            Log.d(TAG, "Servicio NSD inicializado con puerto $localPort")
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando servicio NSD: ${e.message}")
        }
    }
    
    /**
     * Inicia el servicio NSD para anunciar este dispositivo
     */
    fun startLocalService() {
        if (nsdManager == null) {
            initialize()
        }
        
        try {
            // Crear información del servicio
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "$LOCAL_SERVICE_NAME-$username"
                serviceType = SERVICE_TYPE
                port = localPort
                
                // Incluir datos adicionales del usuario
                val userData = JSONObject().apply {
                    put("user_id", userId)
                    put("username", username)
                    put("public_key", publicKey)
                    put("timestamp", System.currentTimeMillis())
                }.toString()
                
                // En Android 7.0+ podemos usar setAttribute
                try {
                    setAttribute("user_data", userData)
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo establecer atributo: ${e.message}")
                }
            }
            
            localServiceInfo = serviceInfo
            
            // Crear listener para registro del servicio
            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "Servicio local registrado: ${serviceInfo.serviceName}")
                    localServiceInfo = serviceInfo
                }
                
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "Error al registrar servicio: $errorCode")
                    _discoveryState.value = DiscoveryState.FAILED
                }
                
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "Servicio local desregistrado")
                    localServiceInfo = null
                }
                
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "Error al desregistrar servicio: $errorCode")
                }
            }
            
            // Registrar servicio
            nsdManager?.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                registrationListener
            )
            
            Log.d(TAG, "Servicio local iniciado en puerto $localPort")
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando servicio local: ${e.message}")
            _discoveryState.value = DiscoveryState.FAILED
        }
    }
    
    /**
     * Detiene el servicio local
     */
    fun stopLocalService() {
        try {
            registrationListener?.let { listener ->
                nsdManager?.unregisterService(listener)
                registrationListener = null
            }
            localServiceInfo = null
            Log.d(TAG, "Servicio local detenido")
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo servicio local: ${e.message}")
        }
    }
    
    /**
     * Comienza a descubrir servicios en la red local
     */
    fun startDiscovery() {
        if (nsdManager == null) {
            initialize()
        }
        
        if (discoveryListener != null) {
            Log.d(TAG, "Ya hay un descubrimiento en progreso")
            return
        }
        
        _discoveryState.value = DiscoveryState.STARTING
        
        try {
            // Crear listener para resolución de servicios
            resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "Error resolviendo servicio ${serviceInfo.serviceName}: $errorCode")
                }
                
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "Servicio resuelto: ${serviceInfo.serviceName}")
                    
                    // Ignorar nuestro propio servicio
                    if (serviceInfo.serviceName == localServiceInfo?.serviceName) {
                        Log.d(TAG, "Ignorando nuestro propio servicio")
                        return
                    }
                    
                    // Procesar el servicio descubierto
                    processDiscoveredService(serviceInfo)
                }
            }
            
            // Crear listener para descubrimiento
            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "Error al iniciar descubrimiento: $errorCode")
                    _discoveryState.value = DiscoveryState.FAILED
                }
                
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "Error al detener descubrimiento: $errorCode")
                }
                
                override fun onDiscoveryStarted(serviceType: String) {
                    Log.d(TAG, "Descubrimiento iniciado para $serviceType")
                    _discoveryState.value = DiscoveryState.ACTIVE
                }
                
                override fun onDiscoveryStopped(serviceType: String) {
                    Log.d(TAG, "Descubrimiento detenido para $serviceType")
                    _discoveryState.value = DiscoveryState.INACTIVE
                }
                
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "Servicio encontrado: ${serviceInfo.serviceName}")
                    
                    // Ignorar nuestro propio servicio
                    if (serviceInfo.serviceName == localServiceInfo?.serviceName) {
                        return
                    }
                    
                    // Resolver el servicio para obtener detalles
                    nsdManager?.resolveService(serviceInfo, resolveListener)
                }
                
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "Servicio perdido: ${serviceInfo.serviceName}")
                    
                    // Buscar y eliminar de la lista de dispositivos
                    var userIdToRemove: String? = null
                    
                    for ((userId, user) in discoveredDevices) {
                        if (user.deviceName == serviceInfo.serviceName) {
                            userIdToRemove = userId
                            break
                        }
                    }
                    
                    userIdToRemove?.let {
                        discoveredDevices.remove(it)
                        updateDiscoveredUsersList()
                    }
                }
            }
            
            // Iniciar descubrimiento
            nsdManager?.discoverServices(
                SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando descubrimiento: ${e.message}")
            _discoveryState.value = DiscoveryState.FAILED
        }
    }
    
    /**
     * Detiene el descubrimiento
     */
    fun stopDiscovery() {
        try {
            discoveryListener?.let { listener ->
                nsdManager?.stopServiceDiscovery(listener)
                discoveryListener = null
            }
            
            _discoveryState.value = DiscoveryState.INACTIVE
            Log.d(TAG, "Descubrimiento detenido")
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo descubrimiento: ${e.message}")
        }
    }
    
    /**
     * Procesa un servicio descubierto para extraer información del usuario
     */
    private fun processDiscoveredService(serviceInfo: NsdServiceInfo) {
        coroutineScope.launch {
            try {
                val host = serviceInfo.host
                val port = serviceInfo.port
                val serviceName = serviceInfo.serviceName
                
                Log.d(TAG, "Procesando servicio: $serviceName, IP: ${host.hostAddress}, Puerto: $port")
                
                // Intentar obtener información del usuario de los atributos
                var userData: JSONObject? = null
                
                // En Android 7.0+ podemos usar getAttributes
                try {
                    serviceInfo.attributes?.get("user_data")?.let { bytes ->
                        val userDataStr = String(bytes)
                        userData = JSONObject(userDataStr)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo obtener atributos del servicio: ${e.message}")
                }
                
                if (userData == null) {
                    // Usar heurística para extraer el nombre de usuario del nombre del servicio
                    val usernameFromService = serviceName.replace("$LOCAL_SERVICE_NAME-", "")
                    
                    // Crear un usuario básico con la información disponible
                    val user = User(
                        id = "local_${host.hostAddress}_$port",
                        username = usernameFromService,
                        publicKey = "", // Inicialmente vacío, se completará durante el handshake
                        lastSeen = System.currentTimeMillis(),
                        ipAddress = host.hostAddress,
                        port = port,
                        deviceName = serviceName
                    )
                    
                    discoveredDevices[user.id] = user
                } else {
                    // Crear usuario con información completa
                    val user = User(
                        id = userData.getString("user_id"),
                        username = userData.getString("username"),
                        publicKey = userData.getString("public_key"),
                        lastSeen = userData.optLong("timestamp", System.currentTimeMillis()),
                        ipAddress = host.hostAddress,
                        port = port,
                        deviceName = serviceName
                    )
                    
                    discoveredDevices[user.id] = user
                }
                
                // Actualizar lista de usuarios
                updateDiscoveredUsersList()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando servicio descubierto: ${e.message}")
            }
        }
    }
    
    /**
     * Actualiza la lista de usuarios descubiertos
     */
    private fun updateDiscoveredUsersList() {
        val sortedUsers = discoveredDevices.values.sortedByDescending { it.lastSeen }
        _discoveredUsers.value = sortedUsers
    }
    
    /**
     * Obtiene la lista actual de dispositivos descubiertos
     */
    fun getDiscoveredUsers(): List<User> {
        return discoveredDevices.values.toList()
    }
    
    /**
     * Limpia recursos
     */
    fun cleanup() {
        stopDiscovery()
        stopLocalService()
    }
}