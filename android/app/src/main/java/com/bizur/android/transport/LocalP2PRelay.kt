package com.bizur.android.transport

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "LocalP2PRelay"
private const val DEFAULT_PORT = 9999

/**
 * Local P2P relay for same-network connections without any server.
 * 
 * When two devices are on the same WiFi network, they can:
 * 1. One device shows a QR code with its local IP + pre-key bundle
 * 2. Other device scans and connects directly via TCP
 * 3. They exchange SDP/ICE to establish WebRTC connection
 * 
 * This bypasses the need for any cloud signaling server.
 */
class LocalP2PRelay(
    private val context: Context,
    private val scope: CoroutineScope,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val connections = ConcurrentHashMap<String, Socket>()

    private val _localAddress = MutableStateFlow<String?>(null)
    val localAddress: StateFlow<String?> = _localAddress

    private val _isHosting = MutableStateFlow(false)
    val isHosting: StateFlow<Boolean> = _isHosting

    private val _incomingMessages = MutableSharedFlow<LocalP2PMessage>(extraBufferCapacity = 32)
    val incomingMessages: SharedFlow<LocalP2PMessage> = _incomingMessages

    data class LocalP2PMessage(
        val from: String,
        val type: String,
        val payload: String
    )

    /**
     * Get the device's local WiFi IP address.
     */
    fun getLocalIpAddress(): String? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipInt = wifiInfo.ipAddress
            if (ipInt == 0) return null
            
            return InetAddress.getByAddress(
                byteArrayOf(
                    (ipInt and 0xff).toByte(),
                    (ipInt shr 8 and 0xff).toByte(),
                    (ipInt shr 16 and 0xff).toByte(),
                    (ipInt shr 24 and 0xff).toByte()
                )
            ).hostAddress
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get local IP", e)
            return null
        }
    }

    /**
     * Start hosting a local relay server.
     * Other devices can connect to this IP:port.
     */
    suspend fun startHosting(port: Int = DEFAULT_PORT): Boolean = withContext(Dispatchers.IO) {
        if (_isHosting.value) return@withContext true

        try {
            serverSocket = ServerSocket(port)
            _localAddress.value = getLocalIpAddress()
            _isHosting.value = true

            serverJob = scope.launch(Dispatchers.IO) {
                while (isActive && serverSocket?.isClosed == false) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        handleClient(client)
                    } catch (e: Exception) {
                        if (isActive) Log.e(TAG, "Accept error", e)
                    }
                }
            }

            Log.i(TAG, "Hosting on ${_localAddress.value}:$port")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start hosting", e)
            false
        }
    }

    /**
     * Stop hosting the local relay.
     */
    fun stopHosting() {
        serverJob?.cancel()
        serverSocket?.close()
        serverSocket = null
        _isHosting.value = false
        _localAddress.value = null
        connections.values.forEach { it.close() }
        connections.clear()
    }

    /**
     * Connect to a peer's local relay.
     */
    suspend fun connectTo(peerIp: String, peerPort: Int = DEFAULT_PORT, peerId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val socket = Socket(peerIp, peerPort)
            connections[peerId] = socket

            scope.launch(Dispatchers.IO) {
                try {
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    while (socket.isConnected && !socket.isClosed) {
                        val line = reader.readLine() ?: break
                        parseAndEmit(line)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Read error for $peerId", e)
                } finally {
                    connections.remove(peerId)
                    socket.close()
                }
            }

            Log.i(TAG, "Connected to $peerId at $peerIp:$peerPort")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to $peerIp:$peerPort", e)
            false
        }
    }

    /**
     * Send a message to a connected peer.
     */
    suspend fun send(peerId: String, type: String, payload: String): Boolean = withContext(Dispatchers.IO) {
        val socket = connections[peerId] ?: return@withContext false
        try {
            val message = json.encodeToString(mapOf(
                "from" to peerId,
                "type" to type,
                "payload" to payload
            ))
            PrintWriter(socket.getOutputStream(), true).println(message)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Send failed to $peerId", e)
            false
        }
    }

    /**
     * Disconnect from a specific peer.
     */
    fun disconnect(peerId: String) {
        connections.remove(peerId)?.close()
    }

    /**
     * Close all connections and stop hosting.
     */
    fun close() {
        stopHosting()
    }

    private fun handleClient(client: Socket) {
        val clientId = "${client.inetAddress.hostAddress}:${client.port}"
        connections[clientId] = client

        scope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                while (client.isConnected && !client.isClosed) {
                    val line = reader.readLine() ?: break
                    parseAndEmit(line)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Client error $clientId", e)
            } finally {
                connections.remove(clientId)
                client.close()
            }
        }
    }

    private suspend fun parseAndEmit(line: String) {
        try {
            val map = json.decodeFromString<Map<String, String>>(line)
            val message = LocalP2PMessage(
                from = map["from"] ?: "",
                type = map["type"] ?: "",
                payload = map["payload"] ?: ""
            )
            _incomingMessages.emit(message)
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: $line", e)
        }
    }
}
