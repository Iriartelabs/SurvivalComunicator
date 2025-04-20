package com.survivalcomunicator.app.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Sistema de métricas P2P que recolecta estadísticas anónimas para
 * mejorar la calidad y confiabilidad del sistema de comunicación.
 * 
 * Estas métricas son completamente anónimas, no incluyen información
 * personal y su envío es opcional.
 */
class P2PMetrics private constructor(private val context: Context) {

    companion object {
        private const val PREF_NAME = "p2p_metrics_prefs"
        private const val KEY_METRICS_ENABLED = "metrics_enabled"
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val KEY_LAST_UPLOAD = "last_upload"
        private const val UPLOAD_INTERVAL = 24 * 60 * 60 * 1000L // 24 horas
        private const val MAX_STORED_METRICS = 50
        private const val METRICS_API_ENDPOINT = "https://api.survivalcomunicator.com/metrics"

        @Volatile
        private var instance: P2PMetrics? = null

        fun getInstance(context: Context): P2PMetrics {
            return instance ?: synchronized(this) {
                instance ?: P2PMetrics(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val metricsFile: File = File(context.filesDir, "p2p_metrics.json")
    private val cachedMetrics = mutableListOf<JSONObject>()
    
    // Contadores
    private val connectionsInitiated = AtomicLong(0)
    private val connectionsReceived = AtomicLong(0)
    private val connectionSuccessCount = AtomicLong(0)
    private val connectionFailureCount = AtomicLong(0)
    private val messagesSent = AtomicLong(0)
    private val messagesReceived = AtomicLong(0)
    private val directConnections = AtomicLong(0)
    private val stunConnections = AtomicLong(0)
    private val turnConnections = AtomicLong(0)
    private val natTraversalAttempts = AtomicLong(0)
    private val natTraversalSuccesses = AtomicLong(0)
    private val verificationAttempts = AtomicLong(0)
    private val verificationSuccesses = AtomicLong(0)
    
    // Tiempos
    private val totalConnectionTime = AtomicLong(0)
    private val connectionAttempts = AtomicLong(0)
    
    // ID de instalación anónimo (UUID aleatorio)
    private val installationId: String
        get() {
            var id = prefs.getString(KEY_INSTALLATION_ID, null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                prefs.edit {
                    putString(KEY_INSTALLATION_ID, id)
                }
            }
            return id
        }
    
    // Estado de recolección de métricas
    private var metricsEnabled: Boolean
        get() = prefs.getBoolean(KEY_METRICS_ENABLED, false)
        set(value) {
            prefs.edit {
                putBoolean(KEY_METRICS_ENABLED, value)
            }
        }

    init {
        // Cargar métricas guardadas
        loadStoredMetrics()
        
        // Iniciar subida periódica de métricas si está habilitada
        if (metricsEnabled) {
            scheduleMetricsUpload()
        }
    }

    /**
     * Habilita o deshabilita la recolección de métricas
     */
    fun setMetricsEnabled(enabled: Boolean) {
        if (metricsEnabled != enabled) {
            metricsEnabled = enabled
            
            if (enabled) {
                scheduleMetricsUpload()
            }
        }
    }

    /**
     * Verifica si las métricas están habilitadas
     */
    fun isMetricsEnabled(): Boolean = metricsEnabled

    /**
     * Registra el inicio de una conexión P2P
     */
    fun recordConnectionAttempt(isInitiator: Boolean) {
        if (!metricsEnabled) return
        
        if (isInitiator) {
            connectionsInitiated.incrementAndGet()
        } else {
            connectionsReceived.incrementAndGet()
        }
        
        connectionAttempts.incrementAndGet()
    }

    /**
     * Registra el resultado de una conexión P2P
     */
    fun recordConnectionResult(success: Boolean, durationMs: Long) {
        if (!metricsEnabled) return
        
        if (success) {
            connectionSuccessCount.incrementAndGet()
            totalConnectionTime.addAndGet(durationMs)
        } else {
            connectionFailureCount.incrementAndGet()
        }
    }

    /**
     * Registra un mensaje enviado
     */
    fun recordMessageSent() {
        if (!metricsEnabled) return
        messagesSent.incrementAndGet()
    }

    /**
     * Registra un mensaje recibido
     */
    fun recordMessageReceived() {
        if (!metricsEnabled) return
        messagesReceived.incrementAndGet()
    }

    /**
     * Registra el tipo de conexión establecida
     */
    fun recordConnectionType(connectionType: String) {
        if (!metricsEnabled) return
        
        when (connectionType) {
            "DIRECT" -> directConnections.incrementAndGet()
            "STUN" -> stunConnections.incrementAndGet()
            "TURN" -> turnConnections.incrementAndGet()
        }
    }

    /**
     * Registra un intento de NAT traversal
     */
    fun recordNatTraversalAttempt(success: Boolean) {
        if (!metricsEnabled) return
        
        natTraversalAttempts.incrementAndGet()
        if (success) {
            natTraversalSuccesses.incrementAndGet()
        }
    }

    /**
     * Registra un intento de verificación de par
     */
    fun recordVerificationAttempt(success: Boolean) {
        if (!metricsEnabled) return
        
        verificationAttempts.incrementAndGet()
        if (success) {
            verificationSuccesses.incrementAndGet()
        }
    }

    /**
     * Registra una métrica personalizada (nombre de evento y valor)
     */
    fun recordCustomMetric(name: String, value: Long) {
        if (!metricsEnabled) return
        
        coroutineScope.launch {
            try {
                val metricObject = JSONObject().apply {
                    put("type", "custom")
                    put("name", name)
                    put("value", value)
                    put("timestamp", System.currentTimeMillis())
                }
                
                addMetricToCache(metricObject)
            } catch (e: Exception) {
                // Ignorar errores en las métricas
            }
        }
    }

    /**
     * Programa la subida periódica de métricas
     */
    private fun scheduleMetricsUpload() {
        coroutineScope.launch {
            try {
                val lastUpload = prefs.getLong(KEY_LAST_UPLOAD, 0)
                val now = System.currentTimeMillis()
                
                if (now - lastUpload > UPLOAD_INTERVAL) {
                    collectAndUploadMetrics()
                }
            } catch (e: Exception) {
                // Ignorar errores en la subida de métricas
            }
        }
    }

    /**
     * Recolecta las métricas actuales y las sube al servidor
     */
    fun collectAndUploadMetrics() {
        if (!metricsEnabled) return
        
        coroutineScope.launch {
            try {
                // Crear objeto JSON con las métricas
                val metrics = collectCurrentMetrics()
                
                // Guardar métricas localmente
                addMetricToCache(metrics)
                
                // Intentar subir todas las métricas almacenadas
                uploadStoredMetrics()
                
                // Actualizar timestamp de última subida
                prefs.edit {
                    putLong(KEY_LAST_UPLOAD, System.currentTimeMillis())
                }
            } catch (e: Exception) {
                // Ignorar errores en la recolección de métricas
            }
        }
    }

    /**
     * Recolecta las métricas actuales en un objeto JSON
     */
    private fun collectCurrentMetrics(): JSONObject {
        return JSONObject().apply {
            put("type", "periodic")
            put("installation_id", installationId)
            put("timestamp", System.currentTimeMillis())
            put("app_version", getAppVersion())
            put("device_model", getDeviceModel())
            put("android_version", Build.VERSION.SDK_INT)
            
            put("connections", JSONObject().apply {
                put("initiated", connectionsInitiated.get())
                put("received", connectionsReceived.get())
                put("success", connectionSuccessCount.get())
                put("failure", connectionFailureCount.get())
                put("direct", directConnections.get())
                put("stun", stunConnections.get())
                put("turn", turnConnections.get())
                
                // Calcular tiempo promedio de conexión
                val attempts = connectionAttempts.get()
                val avgTime = if (attempts > 0) {
                    totalConnectionTime.get() / attempts
                } else {
                    0
                }
                put("avg_connect_time_ms", avgTime)
            })
            
            put("messages", JSONObject().apply {
                put("sent", messagesSent.get())
                put("received", messagesReceived.get())
            })
            
            put("nat_traversal", JSONObject().apply {
                put("attempts", natTraversalAttempts.get())
                put("successes", natTraversalSuccesses.get())
            })
            
            put("verification", JSONObject().apply {
                put("attempts", verificationAttempts.get())
                put("successes", verificationSuccesses.get())
            })
        }
    }

    /**
     * Agrega una métrica al caché local y guarda en disco si es necesario
     */
    private fun addMetricToCache(metric: JSONObject) {
        synchronized(cachedMetrics) {
            cachedMetrics.add(metric)
            
            // Mantener el tamaño del caché controlado
            if (cachedMetrics.size > MAX_STORED_METRICS) {
                cachedMetrics.removeAt(0)
            }
            
            // Guardar en disco
            saveMetricsToDisk()
        }
    }

    /**
     * Guarda las métricas en disco
     */
    private fun saveMetricsToDisk() {
        try {
            if (!metricsFile.exists()) {
                metricsFile.createNewFile()
            }
            
            FileOutputStream(metricsFile).use { fos ->
                BufferedWriter(OutputStreamWriter(fos)).use { writer ->
                    writer.write(JSONObject().put("metrics", cachedMetrics).toString())
                }
            }
        } catch (e: Exception) {
            // Ignorar errores de escritura
        }
    }

    /**
     * Carga las métricas guardadas en disco
     */
    private fun loadStoredMetrics() {
        try {
            if (metricsFile.exists()) {
                val content = metricsFile.readText()
                val json = JSONObject(content)
                val metricsArray = json.getJSONArray("metrics")
                
                synchronized(cachedMetrics) {
                    cachedMetrics.clear()
                    for (i in 0 until metricsArray.length()) {
                        cachedMetrics.add(metricsArray.getJSONObject(i))
                    }
                }
            }
        } catch (e: Exception) {
            // Ignorar errores de lectura
        }
    }

    /**
     * Sube las métricas almacenadas al servidor
     */
    private suspend fun uploadStoredMetrics() {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return  // No hay conexión
        }
        
        val metricsToUpload: List<JSONObject>
        synchronized(cachedMetrics) {
            if (cachedMetrics.isEmpty()) {
                return  // No hay métricas para subir
            }
            metricsToUpload = cachedMetrics.toList()
        }
        
        try {
            withContext(Dispatchers.IO) {
                // Crear payload
                val payload = JSONObject().put("metrics", metricsToUpload).toString()
                
                // Crear conexión
                val url = URL(METRICS_API_ENDPOINT)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                
                // Enviar datos
                connection.outputStream.use { os ->
                    BufferedWriter(OutputStreamWriter(os)).use { writer ->
                        writer.write(payload)
                        writer.flush()
                    }
                }
                
                // Verificar respuesta
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Éxito - eliminar métricas subidas
                    synchronized(cachedMetrics) {
                        cachedMetrics.clear()
                        saveMetricsToDisk()
                    }
                }
                
                connection.disconnect()
            }
        } catch (e: Exception) {
            // Falló la subida - mantener métricas para intentar más tarde
        }
    }

    /**
     * Obtiene la versión de la aplicación
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.versionCode})"
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * Obtiene el modelo del dispositivo
     */
    private fun getDeviceModel(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.startsWith(manufacturer)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    /**
     * Limpia todas las métricas almacenadas
     */
    fun clearAllMetrics() {
        synchronized(cachedMetrics) {
            cachedMetrics.clear()
            saveMetricsToDisk()
        }
        
        // Resetear contadores
        connectionsInitiated.set(0)
        connectionsReceived.set(0)
        connectionSuccessCount.set(0)
        connectionFailureCount.set(0)
        messagesSent.set(0)
        messagesReceived.set(0)
        directConnections.set(0)
        stunConnections.set(0)
        turnConnections.set(0)
        natTraversalAttempts.set(0)
        natTraversalSuccesses.set(0)
        verificationAttempts.set(0)
        verificationSuccesses.set(0)
        totalConnectionTime.set(0)
        connectionAttempts.set(0)
    }
}