package com.survivalcomunicator.app.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.*

/**
 * Utilidades para verificar el estado de la red y obtener información de conectividad
 */
object NetworkUtils {
    private const val TAG = "NetworkUtils"
    
    /**
     * Verifica si hay una conexión a Internet disponible
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                   capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }
    }
    
    /**
     * Verifica si el dispositivo está conectado a una red WiFi
     */
    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
            return networkInfo != null && networkInfo.isConnected
        }
    }
    
    /**
     * Obtiene la dirección IP local del dispositivo
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                
                // Saltar interfaces loopback y virtuales
                if (networkInterface.isLoopback || networkInterface.isVirtual || !networkInterface.isUp) {
                    continue
                }
                
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    
                    // Si es una dirección IPv4 (no IPv6)
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo dirección IP local: ${e.message}")
        }
        
        return null
    }
    
    /**
     * Obtiene el SSID de la red WiFi actual (si está conectado a WiFi)
     */
    fun getCurrentSsid(context: Context): String? {
        try {
            if (isWifiConnected(context)) {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val wifiInfo = wifiManager.connectionInfo
                
                if (wifiInfo != null) {
                    // Limpiar las comillas que rodean el SSID
                    val ssid = wifiInfo.ssid
                    return if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                        ssid.substring(1, ssid.length - 1)
                    } else {
                        ssid
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo SSID: ${e.message}")
        }
        
        return null
    }
    
    /**
     * Verifica si un puerto específico está disponible
     */
    fun isPortAvailable(port: Int): Boolean {
        return try {
            val serverSocket = java.net.ServerSocket(port)
            serverSocket.close()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Encuentra un puerto disponible en un rango dado
     */
    fun findAvailablePort(startPort: Int = 8000, endPort: Int = 9000): Int {
        for (port in startPort..endPort) {
            if (isPortAvailable(port)) {
                return port
            }
        }
        
        // Si no se encuentra un puerto disponible, devolver -1
        return -1
    }
}