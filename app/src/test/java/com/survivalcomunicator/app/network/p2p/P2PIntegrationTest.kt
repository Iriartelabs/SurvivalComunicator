package com.survivalcomunicator.app.network.p2p

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.survivalcomunicator.app.model.User
import com.survivalcomunicator.app.security.CryptoManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Pruebas de integración para el sistema P2P
 * Estas pruebas verifican el flujo completo de comunicación entre dispositivos
 * 
 * Nota: Estas pruebas requieren ejecutarse en dispositivos reales o emuladores
 * con conexión de red funcional. Se utilizan puertos aleatorios para evitar conflictos.
 */
@RunWith(AndroidJUnit4::class)
class P2PIntegrationTest {

    private lateinit var context: Context
    private lateinit var cryptoManager: CryptoManager
    private lateinit var serverA: P2PServer
    private lateinit var serverB: P2PServer
    private lateinit var clientA: P2PClient
    private lateinit var clientB: P2PClient
    
    // Usuarios de prueba
    private lateinit var userA: User
    private lateinit var userB: User
    
    // Puertos aleatorios para pruebas
    private val portA = 10000 + (Math.random() * 10000).toInt()
    private val portB = portA + 1

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
            port = portA
        )
        
        userB = User(
            id = UUID.randomUUID().toString(),
            username = "testUserB",
            publicKey = publicKeyB,
            lastSeen = System.currentTimeMillis(),
            ipAddress = "127.0.0.1",
            port = portB
        )
        
        // Inicializar servidores y clientes
        serverA = P2PServer(context)
        serverB = P2PServer(context)
        clientA = P2PClient(context)
        clientB = P2PClient(context)
    }

    @After
    fun tearDown() {
        // Detener servidores y clientes
        serverA.stop()
        serverB.stop()
        clientA.cleanup()
        clientB.cleanup()
    }

    /**
     * Prueba el flujo completo de conexión, envío y recepción de mensajes
     */
    @Test
    fun testFullCommunicationFlow() = runBlocking {
        // Para sincronizar entre hilos
        val messageReceivedLatch = CountDownLatch(2)
        var receivedMessageA: String? = null
        var receivedMessageB: String? = null
        
        // Iniciar servidores
        val serverAStarted = serverA.start(portA) { socket, userId, pubKey ->
            // Se establece conexión con servidor A
        }
        
        val serverBStarted = serverB.start(portB) { socket, userId, pubKey ->
            // Se establece conexión con servidor B
        }
        
        assertTrue("Servidor A debe iniciar correctamente", serverAStarted)
        assertTrue("Servidor B debe iniciar correctamente", serverBStarted)
        
        // Configurar receptores de mensajes
        clientA.setMessageListener { message ->
            receivedMessageA = message.encryptedContent
            messageReceivedLatch.countDown()
        }
        
        clientB.setMessageListener { message ->
            receivedMessageB = message.encryptedContent
            messageReceivedLatch.countDown()
        }
        
        // Establecer conexiones
        val connectAtoB = clientA.connect(
            userB.ipAddress!!, 
            userB.port!!, 
            userB.id,
            userB.publicKey
        )
        
        val connectBtoA = clientB.connect(
            userA.ipAddress!!, 
            userA.port!!, 
            userA.id,
            userA.publicKey
        )
        
        assertTrue("Conexión de A a B debe establecerse", connectAtoB)
        assertTrue("Conexión de B a A debe establecerse", connectBtoA)
        
        // Esperar a que las conexiones se estabilicen
        delay(1000)
        
        // Enviar mensajes
        val messageFromA = "Hello from A"
        val messageFromB = "Hello from B"
        
        clientA.sendMessage(
            UUID.randomUUID().toString(),
            userB.id,
            messageFromA,
            System.currentTimeMillis()
        )
        
        clientB.sendMessage(
            UUID.randomUUID().toString(),
            userA.id,
            messageFromB,
            System.currentTimeMillis()
        )
        
        // Esperar a que se reciban los mensajes (timeout de 5 segundos)
        val messagesReceived = messageReceivedLatch.await(5, TimeUnit.SECONDS)
        
        assertTrue("Los mensajes deben recibirse dentro del timeout", messagesReceived)
        assertEquals("El mensaje recibido por A debe ser correcto", messageFromB, receivedMessageA)
        assertEquals("El mensaje recibido por B debe ser correcto", messageFromA, receivedMessageB)
    }

    /**
     * Prueba el manejo de desconexiones y reconexiones
     */
    @Test
    fun testConnectionResiliency() = runBlocking {
        // Para sincronizar entre hilos
        val initialConnectionLatch = CountDownLatch(1)
        val reconnectionLatch = CountDownLatch(1)
        
        // Iniciar servidores
        serverA.start(portA)
        serverB.start(portB)
        
        // Establecer conexión inicial
        clientA.setConnectionListener { userId, connected ->
            if (connected) {
                initialConnectionLatch.countDown()
            }
        }
        
        clientA.connect(userB.ipAddress!!, userB.port!!, userB.id, userB.publicKey)
        
        // Esperar a que se establezca la conexión inicial
        assertTrue("La conexión inicial debe establecerse", 
            initialConnectionLatch.await(5, TimeUnit.SECONDS))
        
        // Simular desconexión cerrando el servidor B
        serverB.stop()
        delay(1000) // Esperar a que se detecte la desconexión
        
        // Reiniciar el servidor B y configurar para detectar reconexión
        serverB.start(portB)
        clientA.setConnectionListener { userId, connected ->
            if (connected && userId == userB.id) {
                reconnectionLatch.countDown()
            }
        }
        
        // Intentar reconexión
        clientA.connect(userB.ipAddress!!, userB.port!!, userB.id, userB.publicKey)
        
        // Verificar que la reconexión funciona
        assertTrue("La reconexión debe establecerse después de una desconexión",
            reconnectionLatch.await(5, TimeUnit.SECONDS))
    }

    /**
     * Prueba el rendimiento del sistema enviando múltiples mensajes
     */
    @Test
    fun testPerformanceWithMultipleMessages() = runBlocking {
        // Configuración
        val messageCount = 50
        val maxAllowedTimeMs = 10000 // 10 segundos para todos los mensajes
        
        // Iniciar servidores
        serverA.start(portA)
        serverB.start(portB)
        
        // Establecer conexiones
        clientA.connect(userB.ipAddress!!, userB.port!!, userB.id, userB.publicKey)
        clientB.connect(userA.ipAddress!!, userA.port!!, userA.id, userA.publicKey)
        
        // Esperar a que las conexiones se estabilicen
        delay(1000)
        
        // Contador de mensajes recibidos
        var receivedCount = 0
        val allMessagesLatch = CountDownLatch(messageCount)
        
        // Configurar receptor en B
        clientB.setMessageListener { message ->
            receivedCount++
            allMessagesLatch.countDown()
        }
        
        // Medir tiempo de inicio
        val startTime = System.currentTimeMillis()
        
        // Enviar mensajes desde A a B
        repeat(messageCount) { i ->
            clientA.sendMessage(
                UUID.randomUUID().toString(),
                userB.id,
                "Performance test message $i",
                System.currentTimeMillis()
            )
            
            // Pequeña pausa para evitar saturación
            if (i % 10 == 0) {
                delay(50)
            }
        }
        
        // Esperar a que se reciban todos los mensajes
        val allReceived = allMessagesLatch.await(maxAllowedTimeMs.toLong(), TimeUnit.MILLISECONDS)
        
        // Calcular tiempo total
        val elapsedTime = System.currentTimeMillis() - startTime
        
        // Verificaciones
        assertTrue("Todos los mensajes deben recibirse dentro del tiempo límite", allReceived)
        assertEquals("Se deben recibir todos los mensajes enviados", messageCount, receivedCount)
        
        // Mostrar estadísticas
        val messagesPerSecond = (messageCount * 1000.0 / elapsedTime)
        println("Rendimiento: $messagesPerSecond mensajes/segundo (${elapsedTime}ms para $messageCount mensajes)")
    }

    /**
     * Prueba casos extremos: cambios de conectividad
     * Esta prueba simula cambios en la conectividad al modificar la dirección IP
     */
    @Test
    fun testConnectivityChanges() = runBlocking {
        // Iniciar servidores
        serverA.start(portA)
        serverB.start(portB)
        
        // Establecer conexión inicial
        val initialConnection = clientA.connect(
            userB.ipAddress!!, 
            userB.port!!, 
            userB.id,
            userB.publicKey
        )
        
        assertTrue("La conexión inicial debe establecerse", initialConnection)
        
        // Simular cambio de IP para userB
        val newIpAddress = "127.0.0.1" // En un caso real sería diferente
        val updatedUserB = userB.copy(ipAddress = newIpAddress)
        
        // Verificar que la reconexión funciona con la nueva IP
        val reconnection = clientA.connect(
            updatedUserB.ipAddress!!, 
            updatedUserB.port!!, 
            updatedUserB.id,
            updatedUserB.publicKey
        )
        
        assertTrue("La reconexión debe funcionar después de un cambio de IP", reconnection)
        
        // Verificar que la comunicación sigue funcionando
        val messageReceivedLatch = CountDownLatch(1)
        var receivedMessage: String? = null
        
        clientB.setMessageListener { message ->
            receivedMessage = message.encryptedContent
            messageReceivedLatch.countDown()
        }
        
        val testMessage = "Message after IP change"
        clientA.sendMessage(
            UUID.randomUUID().toString(),
            userB.id,
            testMessage,
            System.currentTimeMillis()
        )
        
        assertTrue("El mensaje debe recibirse después del cambio de IP",
            messageReceivedLatch.await(5, TimeUnit.SECONDS))
        
        assertEquals("El mensaje recibido debe ser correcto", testMessage, receivedMessage)
    }
}