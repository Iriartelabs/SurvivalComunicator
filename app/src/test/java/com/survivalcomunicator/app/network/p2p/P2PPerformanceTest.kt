package com.survivalcomunicator.app.network.p2p

import android.content.Context
import android.os.BatteryManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.survivalcomunicator.app.model.User
import com.survivalcomunicator.app.security.CryptoManager
import com.survivalcomunicator.app.utils.NetworkUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Pruebas de rendimiento para el sistema P2P
 * 
 * Estas pruebas evalúan el rendimiento del sistema en diferentes escenarios
 * y miden el consumo de recursos como batería, memoria y ancho de banda.
 */
@RunWith(AndroidJUnit4::class)
class P2PPerformanceTest {

    private lateinit var context: Context
    private lateinit var cryptoManager: CryptoManager
    private lateinit var serverA: P2PServer
    private lateinit var clientA: P2PClient
    private lateinit var clientB: P2PClient
    
    // Usuarios de prueba
    private lateinit var userA: User
    private lateinit var userB: User
    
    // Puerto para pruebas
    private val serverPort = 10000 + (Math.random() * 10000).toInt()
    
    // Archivos para registro de métricas
    private lateinit var metricsFile: File
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        cryptoManager = CryptoManager(context)
        
        // Generar claves para usuarios de prueba
        val keyPairA = cryptoManager.generateKeyPair()
        val keyPairB = cryptoManager.generateKeyPair()
        
        val publicKeyA = cryptoManager.encodePublicKey(keyPairA.public)
        val publicKeyB = cryptoManager.encodePublicKey(keyPairB.public)
        
        // Crear usuarios de prueba
        userA = User(
            id = UUID.randomUUID().toString(),
            username = "testUserA",
            publicKey = publicKeyA,
            lastSeen = System.currentTimeMillis(),
            ipAddress = "127.0.0.1",
            port = serverPort
        )
        
        userB = User(
            id = UUID.randomUUID().toString(),
            username = "testUserB",
            publicKey = publicKeyB,
            lastSeen = System.currentTimeMillis(),
            ipAddress = "127.0.0.1",
            port = serverPort + 1
        )
        
        // Inicializar componentes
        serverA = P2PServer(context)
        clientA = P2PClient(context)
        clientB = P2PClient(context)
        
        // Crear archivo para métricas
        metricsFile = File(context.cacheDir, "performance_metrics.csv")
        
        // Cabecera del archivo de métricas
        if (!metricsFile.exists()) {
            metricsFile.writeText("test_name,timestamp,message_count,total_time_ms,messages_per_second,battery_drain_pct,memory_usage_mb,network_usage_kb\n")
        }
    }

    @After
    fun tearDown() {
        serverA.stop()
        clientA.cleanup()
        clientB.cleanup()
    }

    /**
     * Prueba el rendimiento del envío de mensajes midiendo
     * la capacidad máxima y el consumo de recursos
     */
    @Test
    fun testMessageThroughput() = runBlocking {
        // Configuración de la prueba
        val messageCount = 500
        val messageSize = 1024 // 1KB por mensaje
        val testDurationSeconds = 60 // Tiempo máximo para la prueba
        
        // Iniciar servidor
        serverA.start(serverPort)
        
        // Contador de mensajes recibidos
        val messagesReceived = CountDownLatch(messageCount)
        
        // Preparar mensaje de prueba (1KB)
        val testMessageContent = ByteArray(messageSize) { 0x41 }.toString(Charset.defaultCharset())
        
        // Medir batería inicial
        val initialBatteryLevel = getBatteryLevel()
        
        // Medir memoria inicial
        val initialMemory = getMemoryUsage()
        
        // Medir tráfico de red inicial
        val initialNetworkUsage = getNetworkUsage()
        
        // Configurar receptor
        serverA.setMessageCallback { message ->
            messagesReceived.countDown()
        }
        
        // Conectar cliente
        val connected = clientA.connect(
            userA.ipAddress!!, 
            userA.port!!, 
            userA.id,
            userA.publicKey
        )
        
        assertTrue("La conexión debe establecerse correctamente", connected)
        
        // Tiempo de inicio
        val startTime = System.currentTimeMillis()
        
        // Enviar mensajes
        repeat(messageCount) { i ->
            clientA.sendMessage(
                UUID.randomUUID().toString(),
                userA.id,
                testMessageContent,
                System.currentTimeMillis()
            )
            
            // Pequeña pausa cada 100 mensajes para permitir procesamiento
            if (i % 100 == 99) {
                delay(50)
            }
        }
        
        // Esperar a que se reciban todos los mensajes o se agote el tiempo
        val allReceived = messagesReceived.await(testDurationSeconds.toLong(), TimeUnit.SECONDS)
        
        // Tiempo final
        val endTime = System.currentTimeMillis()
        val elapsedTime = endTime - startTime
        
        // Medir batería final
        val finalBatteryLevel = getBatteryLevel()
        
        // Medir memoria final
        val finalMemory = getMemoryUsage()
        
        // Medir tráfico de red final
        val finalNetworkUsage = getNetworkUsage()
        
        // Calcular métricas
        val receivedCount = messageCount - messagesReceived.count
        val messagesPerSecond = receivedCount * 1000.0 / elapsedTime
        val batteryDrain = initialBatteryLevel - finalBatteryLevel
        val memoryUsage = finalMemory - initialMemory
        val networkUsage = finalNetworkUsage - initialNetworkUsage
        
        // Registrar resultados
        logPerformanceMetrics(
            testName = "message_throughput",
            messageCount = receivedCount,
            totalTimeMs = elapsedTime,
            messagesPerSecond = messagesPerSecond,
            batteryDrainPct = batteryDrain,
            memoryUsageMb = memoryUsage,
            networkUsageKb = networkUsage
        )
        
        // Verificar resultados
        assertTrue("Al menos el 90% de los mensajes deben recibirse correctamente", 
            receivedCount >= messageCount * 0.9)
        
        println("Resultado de prueba de rendimiento:")
        println("- Mensajes recibidos: $receivedCount de $messageCount")
        println("- Tiempo total: ${elapsedTime}ms")
        println("- Rendimiento: $messagesPerSecond mensajes/segundo")
        println("- Consumo de batería: $batteryDrain%")
        println("- Uso de memoria: $memoryUsage MB")
        println("- Tráfico de red: $networkUsage KB")
    }
    
    /**
     * Prueba la latencia de los mensajes en diferentes condiciones de red
     */
    @Test
    fun testMessageLatency() = runBlocking {
        // Configuración de la prueba
        val testIterations = 100
        
        // Iniciar servidor
        serverA.start(serverPort)
        
        // Conectar cliente
        clientA.connect(userA.ipAddress!!, userA.port!!, userA.id, userA.publicKey)
        
        // Esperar a que la conexión se estabilice
        delay(1000)
        
        // Medir latencias
        val latencies = mutableListOf<Long>()
        
        repeat(testIterations) {
            val latch = CountDownLatch(1)
            var responseTime: Long = 0
            
            // Configurar receptor temporal para esta prueba
            serverA.setMessageCallback { message ->
                // Responder inmediatamente con eco
                clientA.sendMessage(
                    UUID.randomUUID().toString(),
                    userA.id,
                    "ECHO:${message.encryptedContent}",
                    System.currentTimeMillis()
                )
            }
            
            clientA.setMessageListener { message ->
                if (message.encryptedContent.startsWith("ECHO:")) {
                    // Calcular tiempo de ida y vuelta
                    responseTime = System.currentTimeMillis() - message.timestamp
                    latch.countDown()
                }
            }
            
            // Enviar mensaje con timestamp
            val messageId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            
            clientA.sendMessage(
                messageId,
                userA.id,
                "PING:$timestamp",
                timestamp
            )
            
            // Esperar respuesta (timeout de 5 segundos)
            latch.await(5, TimeUnit.SECONDS)
            
            // Registrar latencia si se recibió respuesta
            if (responseTime > 0) {
                latencies.add(responseTime)
            }
            
            // Pequeña pausa entre iteraciones
            delay(100)
        }
        
        // Calcular estadísticas
        if (latencies.isNotEmpty()) {
            val avgLatency = latencies.average()
            val minLatency = latencies.minOrNull() ?: 0
            val maxLatency = latencies.maxOrNull() ?: 0
            
            // Latencias ordenadas para calcular percentiles
            val sortedLatencies = latencies.sorted()
            val median = if (sortedLatencies.size % 2 == 0) {
                (sortedLatencies[sortedLatencies.size/2 - 1] + sortedLatencies[sortedLatencies.size/2]) / 2.0
            } else {
                sortedLatencies[sortedLatencies.size/2].toDouble()
            }
            
            val percentile95 = sortedLatencies[(sortedLatencies.size * 0.95).toInt() - 1]
            
            // Registrar resultados
            logLatencyMetrics(
                testName = "message_latency",
                avgLatencyMs = avgLatency,
                minLatencyMs = minLatency,
                maxLatencyMs = maxLatency,
                medianLatencyMs = median,
                percentile95Ms = percentile95,
                successRate = latencies.size.toDouble() / testIterations
            )
            
            // Mostrar resultados
            println("Resultado de prueba de latencia:")
            println("- Iteraciones exitosas: ${latencies.size} de $testIterations")
            println("- Latencia promedio: ${avgLatency}ms")
            println("- Latencia mínima: ${minLatency}ms")
            println("- Latencia máxima: ${maxLatency}ms")
            println("- Latencia mediana: ${median}ms")
            println("- Percentil 95: ${percentile95}ms")
            
            // Verificar resultados
            assertTrue("La latencia promedio debe ser menor a 500ms en localhost", avgLatency < 500)
            assertTrue("Al menos el 90% de las pruebas deben completarse", 
                latencies.size >= testIterations * 0.9)
        } else {
            fail("No se recibieron respuestas de eco")
        }
    }
    
    /**
     * Prueba el consumo de batería durante uso prolongado
     */
    @Test
    fun testBatteryConsumption() = runBlocking {
        // Omitir prueba si estamos en un emulador (sin batería real)
        if (isEmulator()) {
            println("Prueba omitida en emulador")
            return@runBlocking
        }
        
        // Configuración de la prueba
        val testDurationMinutes = 5
        val messagingInterval = 1000L // 1 mensaje por segundo
        
        // Iniciar servidor
        serverA.start(serverPort)
        
        // Conectar cliente
        clientA.connect(userA.ipAddress!!, userA.port!!, userA.id, userA.publicKey)
        
        // Medir batería inicial
        val initialBatteryLevel = getBatteryLevel()
        val initialSystemUptime = SystemClock.elapsedRealtime()
        
        // Configurar receptor simple
        var messagesReceived = 0
        serverA.setMessageCallback { message ->
            messagesReceived++
        }
        
        // Enviar mensajes periódicamente durante el tiempo de prueba
        val endTime = System.currentTimeMillis() + testDurationMinutes * 60 * 1000
        var messagesSent = 0
        
        while (System.currentTimeMillis() < endTime) {
            clientA.sendMessage(
                UUID.randomUUID().toString(),
                userA.id,
                "Battery test message ${messagesSent++}",
                System.currentTimeMillis()
            )
            
            delay(messagingInterval)
        }
        
        // Medir batería final
        val finalBatteryLevel = getBatteryLevel()
        val finalSystemUptime = SystemClock.elapsedRealtime()
        
        // Calcular consumo
        val batteryDrain = initialBatteryLevel - finalBatteryLevel
        val testDurationMs = finalSystemUptime - initialSystemUptime
        val drainPerHour = batteryDrain * (3600 * 1000.0 / testDurationMs)
        
        // Registrar resultados
        logBatteryMetrics(
            testName = "battery_consumption",
            testDurationMinutes = testDurationMinutes,
            messagesSent = messagesSent,
            messagesReceived = messagesReceived,
            batteryDrainPct = batteryDrain,
            drainPerHourPct = drainPerHour
        )
        
        // Mostrar resultados
        println("Resultado de prueba de consumo de batería:")
        println("- Duración: $testDurationMinutes minutos")
        println("- Mensajes enviados: $messagesSent")
        println("- Mensajes recibidos: $messagesReceived")
        println("- Consumo de batería: $batteryDrain%")
        println("- Tasa de consumo: $drainPerHour% por hora")
        
        // Verificación básica
        assertTrue("Al menos el 90% de los mensajes deben recibirse", 
            messagesReceived >= messagesSent * 0.9)
    }
    
    /**
     * Obtiene el nivel actual de batería en porcentaje
     */
    private fun getBatteryLevel(): Float {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).toFloat()
    }
    
    /**
     * Obtiene el uso actual de memoria de la aplicación en MB
     */
    private fun getMemoryUsage(): Float {
        val rt = Runtime.getRuntime()
        val usedMemBytes = rt.totalMemory() - rt.freeMemory()
        return usedMemBytes / (1024f * 1024f) // Convertir a MB
    }
    
    /**
     * Obtiene el uso de red actual en KB
     * Nota: Esta es una aproximación simplificada
     */
    private fun getNetworkUsage(): Long {
        return try {
            val file = File("/proc/self/net/dev")
            if (file.exists()) {
                val lines = file.readLines()
                var totalBytes = 0L
                
                for (line in lines) {
                    if (line.contains("wlan0:") || line.contains("eth0:")) {
                        val parts = line.trim().split("\\s+".toRegex())
                        if (parts.size >= 10) {
                            // Rx bytes + Tx bytes
                            totalBytes += parts[1].toLong() + parts[9].toLong()
                        }
                    }
                }
                
                totalBytes / 1024 // Convertir a KB
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Registra métricas de rendimiento en el archivo de métricas
     */
    private fun logPerformanceMetrics(
        testName: String, 
        messageCount: Int, 
        totalTimeMs: Long, 
        messagesPerSecond: Double,
        batteryDrainPct: Float,
        memoryUsageMb: Float,
        networkUsageKb: Long
    ) {
        try {
            metricsFile.appendText(
                "$testName,${System.currentTimeMillis()},$messageCount,$totalTimeMs," +
                "$messagesPerSecond,$batteryDrainPct,$memoryUsageMb,$networkUsageKb\n"
            )
        } catch (e: Exception) {
            println("Error registrando métricas: ${e.message}")
        }
    }
    
    /**
     * Registra métricas de latencia en el archivo de métricas
     */
    private fun logLatencyMetrics(
        testName: String,
        avgLatencyMs: Double,
        minLatencyMs: Long,
        maxLatencyMs: Long,
        medianLatencyMs: Double,
        percentile95Ms: Long,
        successRate: Double
    ) {
        try {
            val latencyFile = File(context.cacheDir, "latency_metrics.csv")
            
            if (!latencyFile.exists()) {
                latencyFile.writeText("test_name,timestamp,avg_latency_ms,min_latency_ms," +
                    "max_latency_ms,median_latency_ms,percentile95_ms,success_rate\n")
            }
            
            latencyFile.appendText(
                "$testName,${System.currentTimeMillis()},$avgLatencyMs,$minLatencyMs," +
                "$maxLatencyMs,$medianLatencyMs,$percentile95Ms,$successRate\n"
            )
        } catch (e: Exception) {
            println("Error registrando métricas de latencia: ${e.message}")
        }
    }
    
    /**
     * Registra métricas de batería en el archivo de métricas
     */
    private fun logBatteryMetrics(
        testName: String,
        testDurationMinutes: Int,
        messagesSent: Int,
        messagesReceived: Int,
        batteryDrainPct: Float,
        drainPerHourPct: Double
    ) {
        try {
            val batteryFile = File(context.cacheDir, "battery_metrics.csv")
            
            if (!batteryFile.exists()) {
                batteryFile.writeText("test_name,timestamp,duration_minutes,messages_sent," +
                    "messages_received,battery_drain_pct,drain_per_hour_pct\n")
            }
            
            batteryFile.appendText(
                "$testName,${System.currentTimeMillis()},$testDurationMinutes,$messagesSent," +
                "$messagesReceived,$batteryDrainPct,$drainPerHourPct\n"
            )
        } catch (e: Exception) {
            println("Error registrando métricas de batería: ${e.message}")
        }
    }
    
    /**
     * Determina si estamos ejecutando en un emulador
     */
    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic") 
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("google_sdk") 
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
            || "google_sdk" == Build.PRODUCT)
    }
}