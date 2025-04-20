# Arquitectura P2P para Sistemas de Comunicación en Situaciones de Supervivencia

## 1. Introducción

Este documento describe una arquitectura de comunicación peer-to-peer (P2P) optimizada para situaciones de supervivencia o crisis. La arquitectura está diseñada para maximizar la privacidad, resistencia y disponibilidad, mientras minimiza la dependencia de infraestructura centralizada.

El sistema combina aspectos de redes federadas para el descubrimiento de usuarios con comunicación directa P2P para el intercambio de mensajes, ofreciendo así mayor privacidad que los sistemas de mensajería tradicionales basados en servidores.

## 2. Conceptos Fundamentales

### 2.1 Federación de Nodos

Un **nodo** es un servidor que forma parte de la red de comunicación. Los nodos están federados, lo que significa que comparten información entre sí según un protocolo definido. Cada usuario se registra principalmente en un nodo, pero puede cambiar de nodo si es necesario.

### 2.2 Registro de Usuarios

Cada usuario tiene un identificador único global y se registra en un nodo "hogar". Los datos de usuario mínimos almacenados incluyen:
- Identificador único (UUID)
- Nombre de usuario
- Clave pública
- Información de contacto actual (dirección IP, puerto, timestamp)
- Nodo hogar

### 2.3 Modelo de Comunicación

A diferencia de los sistemas tradicionales donde los mensajes pasan a través de servidores, en esta arquitectura:
- Los nodos funcionan principalmente como servicios de directorio
- La comunicación real ocurre directamente entre dispositivos
- Los servidores solo facilitan el descubrimiento de contactos

## 3. Componentes del Sistema

### 3.1 Descubrimiento de Nodos

El sistema utiliza un protocolo de descubrimiento para que los nodos encuentren otros nodos de la red:

```javascript
function broadcastAnnouncement() {
  const interfaces = os.networkInterfaces();
  const message = {
    type: 'ANNOUNCE',
    id: NODE_ID,
    name: NODE_NAME,
    port: NODE_PORT,
    timestamp: Date.now()
  };
  
  // Enviar a todas las interfaces de red
  Object.keys(interfaces).forEach((iface) => {
    interfaces[iface].forEach((details) => {
      if (details.family === 'IPv4' && !details.internal) {
        const subnet = details.address.split('.');
        subnet[3] = '255';
        const broadcastAddr = subnet.join('.');
        
        // Enviar mensaje de anuncio
        server.send(JSON.stringify(message), 0, message.length, 
          DISCOVERY_PORT, broadcastAddr);
      }
    });
  });
}
```

### 3.2 Sincronización de Usuarios

Los nodos intercambian periódicamente información sobre sus usuarios registrados:

```javascript
async function syncWithNode(node) {
  try {
    // Obtener usuarios del nodo remoto
    const response = await axios.get(`http://${node.address}:${node.port}/api/users/sync`);
    const remoteUsers = response.data.users;
    
    // Procesar usuarios remotos
    for (const user of remoteUsers) {
      // Verificar datos mínimos
      if (!user.id || !user.username || !user.public_key) continue;
      
      // Almacenar o actualizar usuario
      await storeOrUpdateUser(user);
    }
    
    // Enviar usuarios locales al nodo remoto
    const localUsers = await getAllLocalUsers();
    await axios.post(`http://${node.address}:${node.port}/api/users/sync`, {
      users: localUsers
    });
  } catch (error) {
    console.error(`Error de comunicación con nodo ${node.name}:`, error.message);
  }
}
```

### 3.3 Registro de Ubicación

Los clientes actualizan periódicamente su ubicación en su nodo hogar:

```javascript
async function updateLocation(userId, ipAddress, port) {
  const timestamp = Date.now();
  
  // Actualizar en base de datos
  await db.run(
    'UPDATE users SET ip_address = ?, port = ?, last_seen = ? WHERE id = ?',
    [ipAddress, port, timestamp, userId]
  );
  
  // Propagar actualización a otros nodos
  broadcastUserUpdate({
    id: userId,
    ip_address: ipAddress,
    port: port,
    last_seen: timestamp,
    home_server: NODE_NAME
  });
}
```

### 3.4 Servicio de Búsqueda de Usuarios

API para que los clientes encuentren la ubicación actual de otros usuarios:

```javascript
router.get('/api/users/locate/:username', async (req, res) => {
  try {
    const { username } = req.params;
    
    // Buscar usuario localmente
    let user = await getUserByUsername(username);
    
    // Si no se encuentra localmente, consultar a otros nodos
    if (!user) {
      user = await queryOtherNodesForUser(username);
    }
    
    if (!user) {
      return res.status(404).json({ error: 'Usuario no encontrado' });
    }
    
    // Devolver sólo información relevante para contacto
    res.json({
      id: user.id,
      username: user.username,
      public_key: user.public_key,
      ip_address: user.ip_address,
      port: user.port,
      last_seen: user.last_seen
    });
  } catch (error) {
    res.status(500).json({ error: 'Error al localizar usuario' });
  }
});
```

### 3.5 Establecimiento de Conexión P2P

Una vez que un cliente obtiene la dirección IP y puerto de otro usuario, establece una conexión directa:

```kotlin
suspend fun establishDirectConnection(userId: String, targetIp: String, targetPort: Int): Connection {
    try {
        // Crear socket para conexión directa
        val socket = Socket()
        val socketAddress = InetSocketAddress(targetIp, targetPort)
        
        // Configurar timeout
        socket.connect(socketAddress, CONNECTION_TIMEOUT)
        
        // Configurar streams de entrada/salida
        val outputStream = socket.getOutputStream()
        val inputStream = socket.getInputStream()
        
        // Realizar handshake
        val handshakeResult = performHandshake(socket, userId)
        
        // Crear y devolver objeto de conexión
        return Connection(
            socket = socket,
            outputStream = outputStream,
            inputStream = inputStream,
            userId = userId,
            establishedAt = System.currentTimeMillis()
        )
    } catch (e: Exception) {
        throw ConnectionException("Error al establecer conexión P2P: ${e.message}")
    }
}
```

### 3.6 NAT Traversal

Para superar las limitaciones de NAT, el sistema implementa técnicas estándar de NAT traversal:

```kotlin
class NatTraversal {
    private val stunClient = StunClient()
    private val turnClient = TurnClient()
    
    suspend fun establishConnection(peerAddress: PeerAddress): Connection {
        // Intentar conexión directa primero
        try {
            return directConnect(peerAddress.ip, peerAddress.port)
        } catch (e: Exception) {
            // Falló la conexión directa
        }
        
        // Intentar NAT hole punching usando STUN
        try {
            val mappedEndpoint = stunClient.discoverPublicEndpoint()
            return holePunchingConnect(peerAddress, mappedEndpoint)
        } catch (e: Exception) {
            // Falló el hole punching
        }
        
        // Como último recurso, usar relay TURN
        return turnClient.relayConnection(peerAddress)
    }
}
```

### 3.7 Cifrado Extremo a Extremo

Toda la comunicación entre pares es cifrada:

```kotlin
fun encryptMessage(message: String, recipientPublicKey: PublicKey): EncryptedMessage {
    // Generar clave de sesión aleatoria (AES-256)
    val sessionKey = generateRandomAESKey()
    
    // Cifrar el mensaje con la clave de sesión
    val encryptedContent = encryptWithAES(message.toByteArray(), sessionKey)
    
    // Cifrar la clave de sesión con la clave pública del destinatario
    val encryptedSessionKey = encryptWithRSA(sessionKey, recipientPublicKey)
    
    // Construir mensaje cifrado
    return EncryptedMessage(
        encryptedContent = encryptedContent,
        encryptedSessionKey = encryptedSessionKey,
        senderPublicKeyId = myPublicKeyId,
        timestamp = System.currentTimeMillis()
    )
}
```

## 4. Flujo de Comunicación

### 4.1 Proceso Completo de Comunicación

El proceso completo para la comunicación entre dos usuarios sería:

1. **Actualización de ubicación:**
   - Cada cliente actualiza periódicamente su ubicación (IP, puerto) en su nodo hogar
   - Esta información se propaga a otros nodos en la red federada

2. **Búsqueda de contactos:**
   - Usuario A desea comunicarse con Usuario B
   - A consulta a su nodo hogar por la ubicación de B
   - Si el nodo no tiene información de B, consulta a otros nodos
   - A recibe la última ubicación conocida de B

3. **Establecimiento de conexión P2P:**
   - A intenta establecer conexión directa con B
   - Si falla debido a NAT, se intentan técnicas de NAT traversal (STUN/TURN)
   - Una vez establecida la conexión, se realiza un handshake criptográfico

4. **Comunicación directa:**
   - Los mensajes viajan directamente entre A y B
   - Todos los mensajes son cifrados usando la clave pública del destinatario
   - La conexión permanece abierta mientras ambos estén online

5. **Desconexión y reconexión:**
   - Si la conexión se pierde, los clientes intentan reconectar automáticamente
   - Si un usuario cambia de ubicación, actualiza su información en el nodo
   - El otro usuario obtendrá la nueva ubicación en su próximo intento de conexión

### 4.2 Diagrama de Secuencia

```
Usuario A                Nodo A                  Nodo B                  Usuario B
   |                       |                       |                       |
   |-- Actualizar ubicación->                      |                       |
   |                       |-- Sincronizar usuarios->                      |
   |                       |<- Sincronizar usuarios--|                       |
   |                       |                       |<- Actualizar ubicación--|
   |                       |                       |                       |
   |-- Solicitar ubicación de B ->                 |                       |
   |                       |-- Consultar por B ------>                    |
   |                       |<- Enviar ubicación de B -|                       |
   |<- Responder con ubicación de B -|                       |                       |
   |                       |                       |                       |
   |---------------------- Establecer conexión P2P directa --------------->|
   |<--------------------- Handshake criptográfico ----------------------->|
   |                       |                       |                       |
   |<--------------------- Comunicación cifrada directa ------------------>|
   |                       |                       |                       |
```

## 5. Resiliencia y Escenarios de Fallo

### 5.1 Cambio de Red

Cuando un usuario cambia de red (y por tanto de IP):

```kotlin
fun handleNetworkChange() {
    // Detectar nueva IP/interfaz
    val newIpAddress = getLocalIpAddress()
    
    // Actualizar en servidor local
    updateLocationOnServer(userId, newIpAddress, listeningPort)
    
    // Cerrar conexiones existentes con reconexión pendiente
    activeConnections.forEach { (userId, connection) ->
        connection.markForReconnection()
    }
}
```

### 5.2 Nodo Inaccesible

Si el nodo hogar de un usuario está inaccesible:

```kotlin
suspend fun handleHomeNodeUnavailable() {
    // Intentar nodos alternativos de la lista conocida
    for (alternativeNode in knownNodes) {
        try {
            // Intentar registrarse en nodo alternativo
            val result = registerTemporarilyWithNode(alternativeNode)
            if (result.success) {
                currentNode = alternativeNode
                return
            }
        } catch (e: Exception) {
            // Continuar con el siguiente nodo
        }
    }
    
    // Si todos los nodos conocidos fallan, entrar en modo solo P2P
    enterPurePeerMode()
}
```

### 5.3 Modo Offline Total

El sistema puede funcionar en modo completamente offline en una red local:

```kotlin
fun enterLocalNetworkMode() {
    // Desactivar comunicación con nodos remotos
    disableNodeCommunication()
    
    // Activar descubrimiento local
    startLocalDiscovery()
    
    // Configurar mDNS/Bonjour para descubrimiento en LAN
    setupMulticastDiscovery()
    
    // Notificar al usuario
    notifyUser("Modo red local activado - Solo comunicación en red actual")
}
```

## 6. Implementación Técnica

### 6.1 Estructura del Servidor Nodo

```javascript
// Estructura básica de un nodo servidor
const express = require('express');
const bodyParser = require('body-parser');
const sqlite3 = require('sqlite3');
const crypto = require('crypto');
const dgram = require('dgram');
const app = express();
const udpServer = dgram.createSocket('udp4');

// Base de datos para almacenar usuarios y nodos
const db = new sqlite3.Database('./survival_net.db');

// Inicializar tablas
db.serialize(() => {
  db.run(`CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    username TEXT UNIQUE NOT NULL,
    public_key TEXT NOT NULL,
    ip_address TEXT,
    port INTEGER,
    last_seen INTEGER,
    home_server TEXT
  )`);
  
  db.run(`CREATE TABLE IF NOT EXISTS known_nodes (
    id TEXT PRIMARY KEY,
    name TEXT,
    address TEXT NOT NULL,
    port INTEGER NOT NULL,
    last_seen INTEGER
  )`);
});

// API para registro de ubicación
app.post('/api/location', async (req, res) => {
  const { userId, ipAddress, port } = req.body;
  await updateUserLocation(userId, ipAddress, port);
  res.json({ success: true });
});

// API para búsqueda de usuarios
app.get('/api/users/locate/:username', async (req, res) => {
  const user = await findUserByUsername(req.params.username);
  res.json(user || { error: 'Not found' });
});

// Sincronización periódica con otros nodos
setInterval(syncWithKnownNodes, 5 * 60 * 1000);

// Descubrimiento de nuevos nodos
udpServer.on('message', handleNodeDiscovery);
udpServer.bind(NODE_DISCOVERY_PORT);

// Iniciar servidor
app.listen(PORT, () => {
  console.log(`Nodo iniciado en puerto ${PORT}`);
});
```

### 6.2 Cliente P2P en Kotlin (Android)

```kotlin
class P2PClient(
    private val context: Context,
    private val userId: String,
    private val cryptoManager: CryptoManager
) {
    private val serverApi = RetrofitClient.createNodeService()
    private val connections = mutableMapOf<String, Connection>()
    private val connectionListeners = mutableListOf<ConnectionListener>()
    private val messageListeners = mutableListOf<MessageListener>()
    private val p2pServer = P2PServer(context)
    
    init {
        // Iniciar servidor para recibir conexiones entrantes
        p2pServer.start { connection ->
            handleIncomingConnection(connection)
        }
        
        // Actualizar ubicación periódicamente
        startLocationUpdates()
    }
    
    // Buscar un usuario para comunicación
    suspend fun findUser(username: String): UserInfo? {
        return try {
            serverApi.locateUser(username)
        } catch (e: Exception) {
            null
        }
    }
    
    // Establecer conexión con otro usuario
    suspend fun connectToUser(userInfo: UserInfo): Boolean {
        return try {
            // Si ya existe una conexión, reutilizarla
            if (connections.containsKey(userInfo.id) && 
                connections[userInfo.id]?.isActive == true) {
                return true
            }
            
            // Intentar conexión P2P
            val connection = natTraversal.establishConnection(
                PeerAddress(userInfo.ipAddress, userInfo.port)
            )
            
            // Realizar handshake de autenticación
            val successful = performAuthHandshake(connection, userInfo)
            if (successful) {
                connections[userInfo.id] = connection
                launchConnectionMonitoring(userInfo.id, connection)
                notifyNewConnection(userInfo)
                return true
            }
            
            return false
        } catch (e: Exception) {
            Log.e("P2PClient", "Error conectando a ${userInfo.username}: ${e.message}")
            return false
        }
    }
    
    // Enviar mensaje directamente al otro usuario
    suspend fun sendMessage(userId: String, message: String): Boolean {
        val connection = connections[userId] ?: return false
        
        try {
            // Obtener clave pública del destinatario
            val recipient = getRecipientInfo(userId)
            
            // Cifrar mensaje
            val encryptedMessage = cryptoManager.encryptMessage(
                message, 
                recipient.publicKey
            )
            
            // Enviar por la conexión P2P
            connection.send(encryptedMessage.encode())
            return true
        } catch (e: Exception) {
            Log.e("P2PClient", "Error enviando mensaje: ${e.message}")
            return false
        }
    }
    
    // Gestionar conexiones entrantes
    private fun handleIncomingConnection(connection: RawConnection) {
        viewModelScope.launch {
            try {
                // Autenticar conexión entrante
                val peerInfo = authenticateIncomingConnection(connection)
                
                // Almacenar conexión
                if (peerInfo != null) {
                    val processedConnection = Connection(
                        socket = connection.socket,
                        userId = peerInfo.userId,
                        establishedAt = System.currentTimeMillis()
                    )
                    
                    connections[peerInfo.userId] = processedConnection
                    launchConnectionMonitoring(peerInfo.userId, processedConnection)
                    notifyNewConnection(peerInfo)
                }
            } catch (e: Exception) {
                Log.e("P2PClient", "Error en conexión entrante: ${e.message}")
                connection.close()
            }
        }
    }
    
    // Monitorizar conexión para detectar desconexiones
    private fun launchConnectionMonitoring(userId: String, connection: Connection) {
        viewModelScope.launch {
            try {
                while (connection.isActive) {
                    val data = connection.receive()
                    if (data != null) {
                        processIncomingData(userId, data)
                    }
                }
            } catch (e: Exception) {
                Log.d("P2PClient", "Conexión cerrada con $userId: ${e.message}")
                connections.remove(userId)
                notifyConnectionClosed(userId)
            }
        }
    }
}
```

## 7. Consideraciones de Seguridad

### 7.1 Protección contra Suplantación

Para prevenir ataques de suplantación de identidad, cada usuario firma sus mensajes con su clave privada:

```kotlin
fun createSignedMessage(message: String, privateKey: PrivateKey): SignedMessage {
    // Calcular hash del mensaje
    val messageBytes = message.toByteArray(Charset.forName("UTF-8"))
    val messageDigest = MessageDigest.getInstance("SHA-256").digest(messageBytes)
    
    // Firmar el hash con la clave privada
    val signature = Signature.getInstance("SHA256withRSA")
    signature.initSign(privateKey)
    signature.update(messageDigest)
    val digitalSignature = signature.sign()
    
    // Crear mensaje firmado
    return SignedMessage(
        content = message,
        signature = digitalSignature,
        signerPublicKeyId = myPublicKeyId,
        timestamp = System.currentTimeMillis()
    )
}
```

### 7.2 Verificación de Pares

Durante el establecimiento de la conexión, los pares verifican sus identidades mutuamente:

```kotlin
suspend fun verifyPeer(connection: Connection, expectedUser: UserInfo): Boolean {
    // Generar token aleatorio
    val challengeToken = generateRandomToken()
    
    // Enviar desafío
    connection.send(VerificationMessage(
        type = "CHALLENGE",
        token = challengeToken
    ))
    
    // Esperar respuesta
    val response = connection.receiveTimeout<SignedResponse>(VERIFICATION_TIMEOUT)
    if (response == null) return false
    
    // Verificar firma con la clave pública del usuario
    val publicKey = cryptoManager.publicKeyFromBase64(expectedUser.publicKey)
    return cryptoManager.verifySignature(
        response.signature,
        challengeToken.toByteArray(),
        publicKey
    )
}
```

### 7.3 Protección contra Ataques de Intermediario (MITM)

El sistema utiliza certificados preinstalados para autenticar los nodos:

```kotlin
val trustManager = X509TrustManager {
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        // Verificar certificado del servidor contra nuestra lista de confianza
        val serverCert = chain[0]
        val trustedFingerprints = loadTrustedFingerprints()
        
        val fingerprint = calculateFingerprint(serverCert)
        if (!trustedFingerprints.contains(fingerprint)) {
            throw CertificateException("Certificado de servidor no confiable")
        }
    }
}
```

## 8. Extensiones y Mejoras Futuras

### 8.1 Soporte para Redes Mesh

En situaciones donde incluso la infraestructura básica está comprometida, el sistema podría expandirse para soportar redes mesh:

```kotlin
class MeshNetworkExtension(private val p2pClient: P2PClient) {
    private val meshNodes = mutableSetOf<MeshNode>()
    
    fun enableMeshMode() {
        // Configurar radio y protocolos para redes mesh
        initializeRadioInterface()
        
        // Registrar callbacks para reenvío de mensajes
        p2pClient.registerMessageInterceptor { message, sender ->
            if (message.ttl > 0) {
                // Reenviar mensaje a otros nodos mesh (excepto el origen)
                val forwardMessage = message.copy(ttl = message.ttl - 1)
                forwardMessageToMeshNodes(forwardMessage, sender)
            }
            true // Continuar procesamiento normal del mensaje
        }
    }
    
    private fun forwardMessageToMeshNodes(message: MeshMessage, excludeNode: String) {
        meshNodes.forEach { node ->
            if (node.id != excludeNode) {
                viewModelScope.launch {
                    try {
                        node.connection.send(message.serialize())
                    } catch (e: Exception) {
                        // Manejar error de envío
                    }
                }
            }
        }
    }
}
```

### 8.2 Mensajes Offline

Para situaciones donde los destinatarios no están disponibles, se puede implementar un sistema de mensajes offline:

```kotlin
class OfflineMessaging(private val context: Context) {
    private val pendingMessages = mutableListOf<PendingMessage>()
    private val database = AppDatabase.getDatabase(context)
    
    // Almacenar mensaje para entrega posterior
    suspend fun storeForLaterDelivery(message: Message, recipientId: String) {
        val pendingMsg = PendingMessage(
            id = UUID.randomUUID().toString(),
            recipientId = recipientId,
            encryptedContent = message.encryptedContent,
            timestamp = System.currentTimeMillis(),
            attempts = 0
        )
        
        database.pendingMessageDao().insert(pendingMsg)
        pendingMessages.add(pendingMsg)
    }
    
    // Proceso de entrega de mensajes pendientes
    suspend fun attemptDelivery() {
        val messages = database.pendingMessageDao().getAllPending()
        
        for (msg in messages) {
            try {
                val recipient = p2pClient.findUser(msg.recipientId)
                if (recipient != null) {
                    val connected = p2pClient.connectToUser(recipient)
                    if (connected) {
                        val delivered = p2pClient.sendRawMessage(
                            msg.recipientId, 
                            msg.encryptedContent
                        )
                        
                        if (delivered) {
                            database.pendingMessageDao().delete(msg.id)
                        } else {
                            database.pendingMessageDao().incrementAttempt(msg.id)
                        }
                    }
                }
            } catch (e: Exception) {
                // Registrar intento fallido
                database.pendingMessageDao().incrementAttempt(msg.id)
            }
        }
    }
}
```

## 9. Conclusión

Esta arquitectura de comunicación proporciona un equilibrio entre la facilidad de uso y la privacidad requerida en situaciones de crisis. Al combinar:

1. **Federación de servidores** para el descubrimiento de usuarios
2. **Comunicación directa P2P** para los mensajes
3. **Cifrado extremo a extremo** para la privacidad
4. **Técnicas de NAT traversal** para superar limitaciones de red

El sistema ofrece un alto nivel de resistencia y privacidad, minimizando la dependencia de infraestructura centralizada y reduciendo la exposición de metadatos.

Para situaciones de supervivencia donde las redes convencionales pueden estar comprometidas, esta arquitectura proporciona múltiples capas de redundancia y fallback, permitiendo la comunicación incluso en condiciones adversas.
