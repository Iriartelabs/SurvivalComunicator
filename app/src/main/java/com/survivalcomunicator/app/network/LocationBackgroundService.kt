package com.survivalcomunicator.app.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.survivalcomunicator.app.MainActivity
import com.survivalcomunicator.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Servicio en segundo plano que mantiene actualizada la información de ubicación del usuario.
 * Este servicio se ejecuta incluso cuando la aplicación está en segundo plano.
 */
class LocationBackgroundService : Service() {
    companion object {
        private const val TAG = "LocationBgService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "survival_communicator_location_channel"
        
        // Intent actions
        const val ACTION_START_SERVICE = "com.survivalcomunicator.app.START_LOCATION_SERVICE"
        const val ACTION_STOP_SERVICE = "com.survivalcomunicator.app.STOP_LOCATION_SERVICE"
        const val ACTION_FORCE_UPDATE = "com.survivalcomunicator.app.FORCE_LOCATION_UPDATE"
        
        // Intent para iniciar el servicio desde una actividad
        fun getStartIntent(context: Context): Intent {
            return Intent(context, LocationBackgroundService::class.java).apply {
                action = ACTION_START_SERVICE
            }
        }
        
        // Intent para detener el servicio desde una actividad
        fun getStopIntent(context: Context): Intent {
            return Intent(context, LocationBackgroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
        }
        
        // Intent para forzar una actualización desde una actividad
        fun getForceUpdateIntent(context: Context): Intent {
            return Intent(context, LocationBackgroundService::class.java).apply {
                action = ACTION_FORCE_UPDATE
            }
        }
    }
    
    // Inyección de dependencias
    private val networkService: NetworkService by inject()
    
    // Coroutine para operaciones asíncronas
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    // Gestor de ubicación
    private lateinit var locationManager: LocationManager
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servicio de ubicación creado")
        
        // Crear gestor de ubicación
        locationManager = LocationManager(applicationContext, networkService, serviceScope)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_SERVICE -> startLocationService()
            ACTION_STOP_SERVICE -> stopLocationService()
            ACTION_FORCE_UPDATE -> forceLocationUpdate()
        }
        
        // Si el servicio se reinicia después de ser terminado, iniciar automáticamente
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        // No permitimos binding con este servicio
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Servicio de ubicación destruido")
        
        // Detener gestor de ubicación
        locationManager.stop()
        
        // Cancelar todos los coroutines
        serviceScope.cancel()
    }
    
    /**
     * Inicia el servicio de ubicación y lo pone en primer plano.
     */
    private fun startLocationService() {
        Log.d(TAG, "Iniciando servicio de ubicación")
        
        // Crear notificación persistente
        val notification = createNotification()
        
        // Iniciar servicio en primer plano
        startForeground(NOTIFICATION_ID, notification)
        
        // Iniciar gestor de ubicación
        locationManager.start()
    }
    
    /**
     * Detiene el servicio de ubicación.
     */
    private fun stopLocationService() {
        Log.d(TAG, "Deteniendo servicio de ubicación")
        
        // Detener gestor de ubicación
        locationManager.stop()
        
        // Detener servicio
        stopForeground(true)
        stopSelf()
    }
    
    /**
     * Fuerza una actualización inmediata de la ubicación.
     */
    private fun forceLocationUpdate() {
        Log.d(TAG, "Forzando actualización de ubicación")
        
        serviceScope.launch {
            locationManager.forceUpdate()
        }
    }
    
    /**
     * Crea la notificación persistente para el servicio en primer plano.
     */
    private fun createNotification(): Notification {
        // Crear canal de notificación para Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ubicación Survival Communicator",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene actualizada tu ubicación para comunicación P2P"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        
        // Intent para abrir la aplicación al tocar la notificación
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Crear notificación
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Survival Communicator activo")
            .setContentText("Manteniendo tu ubicación actualizada para comunicación")
            .setSmallIcon(R.drawable.ic_notification) // Asegúrate de tener este recurso
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}