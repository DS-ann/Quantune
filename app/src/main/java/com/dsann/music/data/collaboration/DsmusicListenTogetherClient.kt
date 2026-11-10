package com.dsann.music.data.collaboration

import android.util.Log
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.CRC32
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class DiscoveredLocalRoom(
    val serviceName: String,
    val hostAddress: String,
    val port: Int
) {
    val endpoint: String get() = "$hostAddress:$port"
}

data class SharedTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val thumbnail: String = ""
)

data class RoomJoinRequest(val userId: String, val username: String)

data class RemotePlaybackState(
    val sequence: Long,
    val action: String,
    val track: SharedTrack?,
    val positionMs: Long,
    val isPlaying: Boolean,
    val queue: List<SharedTrack> = emptyList(),
    val queueTitle: String? = null
)

/**
 * Lightweight client for the Listen Together protobuf/WebSocket protocol used by Metrolist.
 * The protocol encoder/decoder below is an independent implementation of the published .proto
 * field layout; no Metrolist implementation classes are copied into Quantune.
 *
 * The session is retained for the app process so playback can stay synchronized while users
 * navigate between screens. It does not hold a permanent wake lock on older Android devices.
 */
class DsmusicListenTogetherClient {
    companion object {
        const val DEFAULT_SERVER = "wss://metroserverx.meowery.eu/ws"
        private const val TAG = "DsmusicListenTogether"
        private const val LOCAL_SERVICE_TYPE = "_dsmusic-listen._tcp."
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pingJob: Job? = null
    private var pingSequence = 0L

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    @Volatile private var socket: WebSocket? = null
    private var pendingMessage: ByteArray? = null
    @Volatile private var activeServerUrl: String? = null
    @Volatile private var sessionToken: String? = null
    @Volatile private var manualDisconnect = true
    @Volatile private var reconnectHandshakePending = false
    @Volatile private var connectionGeneration = 0L
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private var initialHandshakeType: String? = null
    @Volatile private var initialHandshakeSent = false
    private val pendingPingSamples = ConcurrentHashMap<Long, Pair<Long, Long>>()
    @Volatile private var serverClockOffsetMs = 0L
    @Volatile private var hasServerClockOffset = false
    private var lastSyncRequestElapsedMs = 0L
    private var remotePlaybackReceivedAtElapsedMs = 0L
    private var lastTrack: SharedTrack? = null
    private var lastSharedQueue: List<SharedTrack> = emptyList()
    private var ownUserId: String? = null
    private var remoteSequence = 0L

    private data class LocalPeer(
        val id: String,
        val name: String,
        val socket: Socket,
        val reader: BufferedReader,
        val writer: BufferedWriter
    )

    private var localMode = false
    private var localRoomPin: String? = null
    private var localServer: ServerSocket? = null
    private var localClientSocket: Socket? = null
    private var localClientWriter: BufferedWriter? = null
    private var localAcceptJob: Job? = null
    private var localClientReadJob: Job? = null
    private var leavingLocalMode = false
    private var lastPlaybackPositionMs = 0L
    private var lastPlaybackIsPlaying = false
    private val localPeers = ConcurrentHashMap<String, LocalPeer>()
    private val pendingLocalPeers = ConcurrentHashMap<String, LocalPeer>()
    private var nsdManager: NsdManager? = null
    private var localServiceRegistration: NsdManager.RegistrationListener? = null
    private var localDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private val _discoveredLocalRooms = MutableStateFlow<List<DiscoveredLocalRoom>>(emptyList())
    val discoveredLocalRooms: StateFlow<List<DiscoveredLocalRoom>> = _discoveredLocalRooms.asStateFlow()

    private val _localDiscoveryStatus = MutableStateFlow("Nearby room discovery has not started.")
    val localDiscoveryStatus: StateFlow<String> = _localDiscoveryStatus.asStateFlow()

    private val _connectionState = MutableStateFlow("Disconnected")
    val connectionState: StateFlow<String> = _connectionState.asStateFlow()

    private val _roomCode = MutableStateFlow<String?>(null)
    val roomCode: StateFlow<String?> = _roomCode.asStateFlow()

    private val _role = MutableStateFlow("NONE")
    val role: StateFlow<String> = _role.asStateFlow()

    private val _roomUsers = MutableStateFlow<List<String>>(emptyList())
    val roomUsers: StateFlow<List<String>> = _roomUsers.asStateFlow()

    private val _joinRequests = MutableStateFlow<List<RoomJoinRequest>>(emptyList())
    val joinRequests: StateFlow<List<RoomJoinRequest>> = _joinRequests.asStateFlow()

    private val _remotePlayback = MutableStateFlow<RemotePlaybackState?>(null)
    val remotePlayback: StateFlow<RemotePlaybackState?> = _remotePlayback.asStateFlow()

    private val _message = MutableStateFlow("Choose a connection type to start listening together.")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _localAddress = MutableStateFlow<String?>(null)
    val localAddress: StateFlow<String?> = _localAddress.asStateFlow()

    val isInRoom: Boolean
        get() = _roomCode.value != null

    /**
     * Starts a room hosted directly by this device. Guests connect to the host's
     * LAN address; no internet or third-party relay is involved.
     */
    fun createLocalRoom(context: Context, username: String) {
        val cleanName = username.trim()
        require(cleanName.isNotEmpty()) { "Enter a display name." }
        leaveRoom()

        val server = ServerSocket(0, 8, InetAddress.getByName("0.0.0.0"))
        val pin = (100000 + java.security.SecureRandom().nextInt(900000)).toString()
        localMode = true
        localRoomPin = pin
        localServer = server
        _roomCode.value = pin
        _role.value = "HOST"
        _roomUsers.value = listOf("$cleanName (host)")
        _joinRequests.value = emptyList()
        _remotePlayback.value = null
        _localAddress.value = findLocalIpv4Address()?.let { it + ":" + server.localPort }
        _connectionState.value = "Connected"
        _message.value = if (_localAddress.value != null) {
            "Local room is starting. Nearby listeners can discover it and join with the PIN."
        } else {
            "Local room is listening. Nearby discovery may still find it; manual address fallback is unavailable."
        }
        startLocalRoomAdvertisement(context, server.localPort)

        localAcceptJob = scope.launch {
            while (isActive && !server.isClosed) {
                val peerSocket = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                scope.launch { handleIncomingLocalPeer(peerSocket) }
            }
        }
    }

    private fun getNsdManager(context: Context): NsdManager? {
        nsdManager?.let { return it }
        val manager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
        nsdManager = manager
        return manager
    }

    private fun acquireMulticastLock(context: Context) {
        if (multicastLock?.isHeld == true) return
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return
        runCatching {
            multicastLock = wifi.createMulticastLock("DsmusicListenTogether").apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onFailure {
            Log.w(TAG, "Could not acquire Wi-Fi multicast lock", it)
        }
    }

    private fun releaseMulticastLockIfIdle() {
        if (localDiscoveryListener != null || localServiceRegistration != null) return
        runCatching {
            multicastLock?.takeIf { it.isHeld }?.release()
        }
        multicastLock = null
    }

    private fun startLocalRoomAdvertisement(context: Context, port: Int) {
        val manager = getNsdManager(context) ?: run {
            _message.value = "Room is ready. Nearby discovery is unavailable on this device; use the address fallback."
            return
        }
        acquireMulticastLock(context)
        val pinSuffix = localRoomPin.orEmpty().takeLast(4)
        val service = NsdServiceInfo().apply {
            serviceName = "Quantune $pinSuffix"
            serviceType = LOCAL_SERVICE_TYPE
            this.port = port
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                _message.value = "Room is discoverable on this Wi-Fi or hotspot. Share the PIN with listeners."
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Local room discovery registration failed: $errorCode")
                _message.value = "Room is running, but this network could not advertise it. Use the address fallback."
                if (localServiceRegistration === this) localServiceRegistration = null
                releaseMulticastLockIfIdle()
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                if (localServiceRegistration === this) localServiceRegistration = null
                releaseMulticastLockIfIdle()
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Local room discovery unregistration failed: $errorCode")
                if (localServiceRegistration === this) localServiceRegistration = null
                releaseMulticastLockIfIdle()
            }
        }
        localServiceRegistration = listener
        try {
            manager.registerService(service, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (error: Exception) {
            localServiceRegistration = null
            releaseMulticastLockIfIdle()
            Log.w(TAG, "Could not advertise local room", error)
            _message.value = "Room is running, but nearby discovery is unavailable. Use the address fallback."
        }
    }

    fun discoverLocalRooms(context: Context) {
        stopLocalRoomDiscovery()
        val manager = getNsdManager(context) ?: run {
            _localDiscoveryStatus.value = "Nearby discovery is unavailable. Enter the host address manually."
            return
        }
        acquireMulticastLock(context)
        _discoveredLocalRooms.value = emptyList()
        _localDiscoveryStatus.value = "Searching this Wi-Fi or hotspot for Quantune rooms…"

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                _localDiscoveryStatus.value = "Searching for nearby rooms…"
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType != LOCAL_SERVICE_TYPE) return
                val activeManager = nsdManager ?: return
                try {
                    activeManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            Log.d(TAG, "Could not resolve nearby room ${info.serviceName}: $errorCode")
                        }

                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val address = info.host?.hostAddress ?: return
                            val port = info.port
                            if (port !in 1..65535) return
                            val resolved = DiscoveredLocalRoom(
                                serviceName = info.serviceName.orEmpty(),
                                hostAddress = address,
                                port = port
                            )
                            _discoveredLocalRooms.value = (
                                _discoveredLocalRooms.value.filterNot {
                                    it.serviceName == resolved.serviceName ||
                                        it.endpoint == resolved.endpoint
                                } + resolved
                            ).sortedBy { it.serviceName.lowercase() }
                            _localDiscoveryStatus.value =
                                if (_discoveredLocalRooms.value.isEmpty()) "Searching for nearby rooms…"
                                else "Found ${_discoveredLocalRooms.value.size} nearby room(s)."
                        }
                    })
                } catch (error: Exception) {
                    Log.d(TAG, "Could not resolve local service", error)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                _discoveredLocalRooms.value = _discoveredLocalRooms.value
                    .filterNot { it.serviceName == serviceInfo.serviceName }
                _localDiscoveryStatus.value = if (_discoveredLocalRooms.value.isEmpty()) {
                    "No nearby rooms found yet. Check that both devices use the same Wi-Fi or hotspot."
                } else {
                    "Found ${_discoveredLocalRooms.value.size} nearby room(s)."
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                releaseMulticastLockIfIdle()
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Local room discovery start failed: $errorCode")
                _localDiscoveryStatus.value = "Nearby discovery failed. You can enter the host address manually."
                runCatching { manager.stopServiceDiscovery(this) }
                if (localDiscoveryListener === this) localDiscoveryListener = null
                releaseMulticastLockIfIdle()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Local room discovery stop failed: $errorCode")
            }
        }
        localDiscoveryListener = listener
        try {
            manager.discoverServices(LOCAL_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            scope.launch {
                delay(8_000L)
                if (localDiscoveryListener === listener && _discoveredLocalRooms.value.isEmpty()) {
                    _localDiscoveryStatus.value =
                        "No nearby room found yet. Check that both devices use the same Wi-Fi or hotspot."
                }
            }
        } catch (error: Exception) {
            localDiscoveryListener = null
            releaseMulticastLockIfIdle()
            _localDiscoveryStatus.value =
                error.message ?: "Nearby discovery could not start. Enter the host address manually."
        }
    }

    fun stopLocalRoomDiscovery() {
        val manager = nsdManager
        val listener = localDiscoveryListener
        localDiscoveryListener = null
        if (manager != null && listener != null) {
            runCatching { manager.stopServiceDiscovery(listener) }
        }
        _discoveredLocalRooms.value = emptyList()
        if (localServiceRegistration == null) {
            _localDiscoveryStatus.value = "Nearby room discovery stopped."
        }
        releaseMulticastLockIfIdle()
    }

    private fun stopLocalRoomAdvertisement() {
        val manager = nsdManager
        val listener = localServiceRegistration
        localServiceRegistration = null
        if (manager != null && listener != null) {
            runCatching { manager.unregisterService(listener) }
        }
        releaseMulticastLockIfIdle()
    }

    /**
     * Connects directly to a room hosted on the same Wi-Fi network or hotspot.
     * Enter the host address in IP:port form and the PIN shown by the host.
     */
    fun joinLocalRoom(hostAddress: String, pin: String, username: String) {
        val cleanName = username.trim()
        val cleanPin = pin.trim()
        val endpoint = hostAddress.trim()
        require(cleanName.isNotEmpty()) { "Enter a display name." }
        require(cleanPin.length == 6 && cleanPin.all(Char::isDigit)) {
            "Enter the six-digit PIN shown by the host."
        }
        val separator = endpoint.lastIndexOf(':')
        require(separator > 0 && separator < endpoint.lastIndex) {
            "Enter the host address in IP:port form."
        }
        val host = endpoint.substring(0, separator).removePrefix("[").removeSuffix("]")
        val port = endpoint.substring(separator + 1).toIntOrNull()
            ?: throw IllegalArgumentException("The host port is invalid.")
        require(port in 1..65535) { "The host port is invalid." }

        leaveRoom()
        localMode = true
        localRoomPin = cleanPin
        _connectionState.value = "Connecting"
        _message.value = "Connecting directly to the host…"

        localClientReadJob = scope.launch {
            var candidate: Socket? = null
            try {
                val connection = Socket()
                candidate = connection
                // Publish before connect() so leaving the screen/session can cancel
                // even a connection attempt that has not reached the host yet.
                localClientSocket = connection
                connection.connect(InetSocketAddress(host, port), 10_000)
                val reader = BufferedReader(InputStreamReader(connection.getInputStream(), Charsets.UTF_8))
                val writer = BufferedWriter(OutputStreamWriter(connection.getOutputStream(), Charsets.UTF_8))
                // Keep the writer reachable while the guest waits for host approval.
                localClientWriter = writer
                writeLocalLine(writer, JSONObject()
                    .put("type", "join")
                    .put("name", cleanName)
                    .put("pin", cleanPin)
                )
                val response = reader.readLine()
                    ?: throw IllegalStateException("The host closed the connection before approving the request.")
                val approval = JSONObject(response)
                if (approval.optString("type") != "approved") {
                    throw IllegalStateException(approval.optString("message", "The host did not approve this connection."))
                }

                localClientSocket = connection
                localClientWriter = writer
                _roomCode.value = approval.optString("pin", cleanPin)
                _role.value = "GUEST"
                _roomUsers.value = readLocalUsers(approval.optJSONArray("users"))
                _joinRequests.value = emptyList()
                _connectionState.value = "Connected"
                _message.value = "Connected directly to the host on your local network."
                approval.optJSONObject("state")?.let(::applyLocalPlayback)
                while (isActive && !connection.isClosed) {
                    val line = reader.readLine() ?: break
                    runCatching { handleLocalMessage(JSONObject(line)) }
                        .onFailure { Log.w(TAG, "Could not decode local room message", it) }
                }
            } catch (error: Exception) {
                if (localMode && (localClientSocket == null || localClientSocket === candidate)) {
                    _connectionState.value = "Connection error"
                    _message.value = error.message ?: "Could not connect to the local room."
                    clearRoomState()
                    localMode = false
                    localRoomPin = null
                    _localAddress.value = null
                }
            } finally {
                val finishedSocket = candidate
                if (finishedSocket != null && localClientSocket === finishedSocket) {
                    localClientSocket = null
                    localClientWriter = null
                    runCatching { finishedSocket.close() }
                    if (localMode && !leavingLocalMode) {
                        clearRoomState()
                        localMode = false
                        localRoomPin = null
                        _connectionState.value = "Disconnected"
                        _message.value = "The local host disconnected."
                    }
                } else {
                    runCatching { finishedSocket?.close() }
                }
            }
        }
    }

    private suspend fun handleIncomingLocalPeer(peerSocket: Socket) {
        var peer: LocalPeer? = null
        try {
            val reader = BufferedReader(InputStreamReader(peerSocket.getInputStream(), Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(peerSocket.getOutputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val request = JSONObject(requestLine)
            val expectedPin = localRoomPin
            if (!localMode || _role.value != "HOST" ||
                request.optString("type") != "join" ||
                expectedPin.isNullOrBlank() ||
                request.optString("pin") != expectedPin
            ) {
                writeLocalLine(writer, JSONObject()
                    .put("type", "rejected")
                    .put("message", "The room PIN is incorrect or the host is no longer available.")
                )
                return
            }

            val id = UUID.randomUUID().toString()
            val name = request.optString("name").trim().ifBlank { "Listener" }.take(32)
            peer = LocalPeer(id, name, peerSocket, reader, writer)
            pendingLocalPeers[id] = peer
            _joinRequests.value = _joinRequests.value
                .filterNot { it.userId == id } + RoomJoinRequest(id, name)
            _message.value = "$name wants to join your local room."

            while (scope.isActive && !peerSocket.isClosed && localMode) {
                val line = reader.readLine() ?: break
                val message = runCatching { JSONObject(line) }.getOrNull() ?: continue
                if (message.optString("type") == "leave") break
            }
        } catch (error: Exception) {
            Log.d(TAG, "Local listener disconnected: " + error.message, error)
        } finally {
            peer?.let { disconnected ->
                pendingLocalPeers.remove(disconnected.id)
                localPeers.remove(disconnected.id)
                _joinRequests.value = _joinRequests.value.filterNot { it.userId == disconnected.id }
                if (localMode && !leavingLocalMode) {
                    _roomUsers.value = _roomUsers.value.filterNot { it == disconnected.name }
                    broadcastLocalMessage(JSONObject()
                        .put("type", "user_left")
                        .put("name", disconnected.name)
                    )
                    _message.value = disconnected.name + " left the local room."
                }
            }
            runCatching { peerSocket.close() }
        }
    }

    private fun findLocalIpv4Address(): String? {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching null
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address &&
                        !address.isLoopbackAddress &&
                        !address.isLinkLocalAddress &&
                        address.isSiteLocalAddress
                    ) return@runCatching address.hostAddress
                }
            }
            null
        }.getOrNull()
    }

    private fun readLocalUsers(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val users = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val name = array.optString(index).trim()
            if (name.isNotBlank()) users.add(name)
        }
        return users.distinct()
    }

    private fun writeLocalLine(writer: BufferedWriter, message: JSONObject) {
        synchronized(writer) {
            writer.write(message.toString())
            writer.newLine()
            writer.flush()
        }
    }

    private fun sendLocalMessage(peer: LocalPeer, message: JSONObject) {
        runCatching { writeLocalLine(peer.writer, message) }
            .onFailure { Log.w(TAG, "Could not send to local listener " + peer.name, it) }
    }

    private fun broadcastLocalMessage(message: JSONObject) {
        localPeers.values.forEach { sendLocalMessage(it, message) }
    }

    private fun localTrackJson(track: SharedTrack?): Any {
        if (track == null) return JSONObject.NULL
        return JSONObject()
            .put("id", track.id)
            .put("title", track.title)
            .put("artist", track.artist)
            .put("album", track.album)
            .put("durationMs", track.durationMs)
            .put("thumbnail", track.thumbnail)
    }

    private fun parseLocalTrack(json: JSONObject?): SharedTrack? {
        if (json == null || json == JSONObject.NULL) return null
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        return SharedTrack(
            id = id,
            title = json.optString("title"),
            artist = json.optString("artist"),
            album = json.optString("album"),
            durationMs = json.optLong("durationMs"),
            thumbnail = json.optString("thumbnail")
        )
    }

    private fun parseLocalQueue(array: JSONArray?): List<SharedTrack> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            parseLocalTrack(array.optJSONObject(index))
        }
    }

    private fun localPlaybackMessage(
        action: String,
        track: SharedTrack?,
        positionMs: Long,
        playing: Boolean,
        queue: List<SharedTrack>? = null,
        queueTitle: String? = null
    ): JSONObject {
        val message = JSONObject()
            .put("type", "playback")
            .put("action", action)
            .put("track", localTrackJson(track))
            .put("positionMs", positionMs.coerceAtLeast(0L))
            .put("isPlaying", playing)
        if (queue != null) {
            message.put("queue", JSONArray().apply {
                queue.forEach { put(localTrackJson(it)) }
            })
        }
        if (!queueTitle.isNullOrBlank()) message.put("queueTitle", queueTitle)
        return message
    }

    private fun applyLocalPlayback(message: JSONObject) {
        val action = message.optString("action", "pause")
        val track = parseLocalTrack(message.optJSONObject("track"))
        if (track != null) lastTrack = track
        val queue = if (message.has("queue")) parseLocalQueue(message.optJSONArray("queue")) else null
        if (queue != null) lastSharedQueue = queue
        val playing = message.optBoolean("isPlaying", false)
        val position = message.optLong("positionMs", 0L).coerceAtLeast(0L)
        lastPlaybackPositionMs = position
        lastPlaybackIsPlaying = playing
        publishRemote(action, track ?: lastTrack, position, playing, queue, message.optString("queueTitle").takeIf { it.isNotBlank() })
    }

    private fun handleLocalMessage(message: JSONObject) {
        when (message.optString("type")) {
            "playback" -> applyLocalPlayback(message)
            "user_joined" -> {
                val name = message.optString("name")
                if (name.isNotBlank()) _roomUsers.value = (_roomUsers.value + name).distinct()
                _message.value = name + " joined the local room."
            }
            "user_left" -> {
                val name = message.optString("name")
                _roomUsers.value = _roomUsers.value.filterNot { it == name }
                _message.value = name + " left the local room."
            }
        }
    }

    fun createRoom(serverUrl: String, username: String) {
        val cleanName = username.trim()
        require(cleanName.isNotEmpty()) { "Enter a display name." }
        connectAndSend(serverUrl, "create_room", ProtoWriter().string(1, cleanName).toByteArray())
    }

    fun joinRoom(serverUrl: String, roomCode: String, username: String) {
        val cleanName = username.trim()
        val cleanCode = roomCode.trim().uppercase()
        require(cleanName.isNotEmpty()) { "Enter a display name." }
        require(cleanCode.isNotEmpty()) { "Enter a room code." }
        val payload = ProtoWriter()
            .string(1, cleanCode)
            .string(2, cleanName)
            .toByteArray()
        connectAndSend(serverUrl, "join_room", payload)
    }

    private fun connectAndSend(serverUrl: String, type: String, payload: ByteArray) {
        val normalized = serverUrl.trim()
        require(normalized.startsWith("wss://") || normalized.startsWith("ws://")) {
            "Server address must start with wss:// or ws://."
        }
        require(normalized.length <= 300) { "Server address is too long." }

        leaveRoom()
        reconnectAttempt = 0
        connectionGeneration += 1L
        manualDisconnect = false
        activeServerUrl = normalized
        initialHandshakeType = type
        initialHandshakeSent = false
        pendingMessage = encodeEnvelope(type, payload)
        _connectionState.value = "Connecting"
        _message.value = "Connecting to Listen Together server…"
        openInternetSocket(connectionGeneration)
    }

    private fun openInternetSocket(generation: Long) {
        if (manualDisconnect || generation != connectionGeneration) return
        val normalized = activeServerUrl ?: return
        val request = try {
            Request.Builder()
                .url(normalized)
                .header("User-Agent", "Quantune")
                .build()
        } catch (error: Exception) {
            if (sessionToken.isNullOrBlank()) {
                _connectionState.value = "Connection error"
                _message.value = error.message ?: "The room server address is invalid."
            } else {
                _connectionState.value = "Reconnecting"
                _message.value = "Could not open the connection. Retrying…"
            }
            return
        }

        try {
            val newSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (generation != connectionGeneration || manualDisconnect || socket !== webSocket) {
                        webSocket.close(1000, "Superseded connection")
                        return
                    }

                    startPingJob()
                    val token = sessionToken
                    if (!token.isNullOrBlank()) {
                        reconnectHandshakePending = true
                        _connectionState.value = "Reconnecting"
                        _message.value = "Connection restored. Rejoining your listening room…"
                        val reconnect = encodeEnvelope(
                            "reconnect",
                            ProtoWriter().string(1, token).toByteArray()
                        )
                        if (!webSocket.send(ByteString.of(*reconnect))) {
                            webSocket.cancel()
                        }
                    } else {
                        _connectionState.value = "Connected"
                        val handshake = pendingMessage
                        if (handshake != null) {
                            if (webSocket.send(ByteString.of(*handshake))) {
                                pendingMessage = null
                                initialHandshakeSent = true
                                _message.value = if (initialHandshakeType == "join_room") {
                                    "Join request sent. Waiting for the host to approve…"
                                } else {
                                    "Connected. Setting up your room…"
                                }
                            } else {
                                _message.value = "The room request could not be sent. Please retry."
                                webSocket.cancel()
                            }
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    if (generation != connectionGeneration || socket !== webSocket) return
                    try {
                        handleEnvelope(bytes.toByteArray())
                    } catch (error: Exception) {
                        Log.w(TAG, "Could not decode room message", error)
                        _message.value = "Received a room update that could not be read."
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    if (generation != connectionGeneration || socket !== webSocket) return
                    if (!sessionToken.isNullOrBlank() && !manualDisconnect) {
                        _connectionState.value = "Reconnecting"
                        _message.value = "The room connection is closing. Restoring it…"
                    }
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (generation != connectionGeneration || socket !== webSocket) return
                    socket = null
                    stopPingJob()
                    if (manualDisconnect) return
                    if (!sessionToken.isNullOrBlank()) {
                        reconnectHandshakePending = false
                        _connectionState.value = "Reconnecting"
                        _message.value = "Connection interrupted. Reconnecting to the room…"
                        scheduleReconnect(generation)
                    } else if (!initialHandshakeSent && pendingMessage != null) {
                        _connectionState.value = "Connecting"
                        _message.value = "The server connection closed before the room request was sent. Retrying…"
                        scheduleReconnect(generation)
                    } else {
                        clearRoomState()
                        _connectionState.value = "Disconnected"
                        _message.value = "The room connection closed before the session was confirmed. Rejoin to continue."
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (generation != connectionGeneration || socket !== webSocket) return
                    socket = null
                    stopPingJob()
                    Log.w(TAG, "Listen Together connection failed", t)
                    if (manualDisconnect) return
                    if (!sessionToken.isNullOrBlank()) {
                        reconnectHandshakePending = false
                        _connectionState.value = "Reconnecting"
                        _message.value = "Connection lost. Quantune will keep trying to restore the room."
                        scheduleReconnect(generation)
                    } else if (!initialHandshakeSent && pendingMessage != null) {
                        _connectionState.value = "Connecting"
                        _message.value = "Could not reach the server yet. Retrying the connection…"
                        scheduleReconnect(generation)
                    } else {
                        clearRoomState()
                        pendingMessage = null
                        _connectionState.value = "Connection error"
                        _message.value = t.message
                            ?: "Could not connect to the room server. Please retry."
                    }
                }
            })
            socket = newSocket
        } catch (error: Exception) {
            Log.w(TAG, "Could not create Listen Together WebSocket", error)
            if (!sessionToken.isNullOrBlank()) {
                _connectionState.value = "Reconnecting"
                _message.value = "Could not open the connection. Retrying…"
                scheduleReconnect(generation)
            } else {
                pendingMessage = null
                _connectionState.value = "Connection error"
                _message.value = error.message ?: "Could not open the Listen Together connection."
            }
        }
    }

    private fun scheduleReconnect(generation: Long) {
        val canResumeSession = !sessionToken.isNullOrBlank()
        val canRetryInitialConnection = !initialHandshakeSent && pendingMessage != null
        if (manualDisconnect || (!canResumeSession && !canRetryInitialConnection) || activeServerUrl.isNullOrBlank()) return
        if (reconnectJob?.isActive == true) return

        reconnectJob = scope.launch {
            val backoffMs = longArrayOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 15_000L, 15_000L)
            while (isActive &&
                generation == connectionGeneration &&
                !manualDisconnect &&
                (!sessionToken.isNullOrBlank() || (!initialHandshakeSent && pendingMessage != null))
            ) {
                if (socket != null) {
                    if (_connectionState.value == "Connected" &&
                        !reconnectHandshakePending &&
                        (_roomCode.value != null || (sessionToken.isNullOrBlank() && initialHandshakeSent))
                    ) break
                    delay(300L)
                    continue
                }

                val baseDelay = backoffMs[reconnectAttempt.coerceIn(0, backoffMs.lastIndex)]
                val jitter = SystemClock.elapsedRealtime() % 450L
                reconnectAttempt += 1
                _connectionState.value = "Reconnecting"
                _message.value = "Connection interrupted. Retrying shortly…"
                delay(baseDelay + jitter)

                if (!isActive || generation != connectionGeneration || manualDisconnect ||
                    (sessionToken.isNullOrBlank() && (initialHandshakeSent || pendingMessage == null))
                ) break

                openInternetSocket(generation)
                var waitedMs = 0L
                while (isActive && generation == connectionGeneration &&
                    !manualDisconnect && socket != null &&
                    reconnectHandshakePending && waitedMs < 25_000L
                ) {
                    delay(250L)
                    waitedMs += 250L
                }

                if (_connectionState.value == "Connected" &&
                    !reconnectHandshakePending &&
                    (_roomCode.value != null || (sessionToken.isNullOrBlank() && initialHandshakeSent))
                ) break

                if (reconnectHandshakePending) {
                    socket?.cancel()
                }
            }
            reconnectJob = null
        }
    }

    private fun stopPingJob() {
        pingJob?.cancel()
        pingJob = null
        pendingPingSamples.clear()
    }

    private fun updateRoomStateFromSnapshot(stateBytes: ByteArray, action: String = "sync_state") {
        val fields = ProtoReader(stateBytes).fields()
        val track = fields.firstBytes(4)?.let(::decodeTrack)
        val playing = fields.firstVarint(5) == 1L
        val rawPosition = fields.firstVarint(6) ?: 0L
        val lastUpdate = fields.firstVarint(7) ?: 0L
        val queue = fields.allBytes(9).mapNotNull(::decodeTrack)
        val queueTitle = fields.firstString(11)

        if (track != null) lastTrack = track
        lastSharedQueue = queue
        val position = compensateRemotePosition(rawPosition, playing, lastUpdate, track?.durationMs ?: 0L)
        publishRemote(action, track ?: lastTrack, position, playing, queue, queueTitle)

        _roomUsers.value = fields.allBytes(3).mapNotNull { userBytes ->
            val user = ProtoReader(userBytes).fields()
            val name = user.firstString(2).orEmpty()
            if (name.isBlank()) null
            else if (user.firstVarint(3) == 1L) "$name (host)" else name
        }.distinct()
    }

    private fun compensateRemotePosition(
        positionMs: Long,
        playing: Boolean,
        serverTimestampMs: Long,
        durationMs: Long
    ): Long {
        if (!playing || !hasServerClockOffset || serverTimestampMs <= 0L) {
            return positionMs.coerceAtLeast(0L)
        }
        val transitMs = (serverNowMs() - serverTimestampMs).coerceIn(0L, 8_000L)
        val adjusted = positionMs.coerceAtLeast(0L) + transitMs
        return if (durationMs > 0L) adjusted.coerceAtMost(durationMs) else adjusted
    }

    private fun serverNowMs(): Long = System.currentTimeMillis() + serverClockOffsetMs

    private fun resendHostStateAfterReconnect() {
        if (_role.value != "HOST") return
        val track = lastTrack ?: return
        val queue = lastSharedQueue
        val position = lastPlaybackPositionMs
        val wasPlaying = lastPlaybackIsPlaying

        sendPlayback(
            action = "change_track",
            track = track,
            positionMs = position,
            queue = queue,
            queueTitle = "Listen Together",
            replaceQueue = true
        )
        sendPlayback(
            action = if (wasPlaying) "play" else "pause",
            track = track,
            positionMs = position
        )
    }

    private fun handlePong(fields: List<ProtoField>) {
        val sequence = fields.firstVarint(4) ?: return
        val sample = pendingPingSamples.remove(sequence) ?: return
        val serverReceive = fields.firstVarint(2) ?: return
        val serverSend = fields.firstVarint(3) ?: return
        if (serverReceive <= 0L || serverSend < serverReceive) return

        val roundTripMs = (SystemClock.elapsedRealtime() - sample.second).coerceAtLeast(0L)
        if (roundTripMs > 12_000L) return
        val localMidpoint = sample.first + roundTripMs / 2L
        val serverMidpoint = serverReceive + (serverSend - serverReceive) / 2L
        val observedOffset = serverMidpoint - localMidpoint
        serverClockOffsetMs = if (hasServerClockOffset) {
            (serverClockOffsetMs * 3L + observedOffset) / 4L
        } else {
            observedOffset
        }
        hasServerClockOffset = true

        val driftValid = fields.firstVarint(5) == 1L
        val driftMs = fields.firstVarint(6) ?: 0L
        val stateMismatch = fields.firstVarint(7) == 1L
        if (!stateMismatch && driftValid && kotlin.math.abs(driftMs) > 1_000L) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastSyncRequestElapsedMs >= 4_000L) {
                lastSyncRequestElapsedMs = now
                send("request_sync", byteArrayOf())
            }
        }
    }

    fun approveJoin(userId: String) {
        if (_role.value != "HOST") return
        if (localMode && localServer != null) {
            val peer = pendingLocalPeers.remove(userId) ?: return
            localPeers[userId] = peer
            _roomUsers.value = (_roomUsers.value + peer.name).distinct()
            val users = JSONArray().apply { _roomUsers.value.forEach { put(it) } }
            val approval = JSONObject()
                .put("type", "approved")
                .put("pin", localRoomPin.orEmpty())
                .put("users", users)
                .put(
                    "state",
                    localPlaybackMessage(
                        if (lastPlaybackIsPlaying) "play" else "pause",
                        lastTrack,
                        lastPlaybackPositionMs,
                        lastPlaybackIsPlaying,
                        queue = lastSharedQueue,
                        queueTitle = "Listen Together"
                    )
                )
            scope.launch {
                sendLocalMessage(peer, approval)
                broadcastLocalMessage(JSONObject().put("type", "user_joined").put("name", peer.name))
            }
            _joinRequests.value = _joinRequests.value.filterNot { it.userId == userId }
            _message.value = peer.name + " joined your local room."
            return
        }
        send("approve_join", ProtoWriter().string(1, userId).toByteArray())
        _joinRequests.value = _joinRequests.value.filterNot { it.userId == userId }
    }

    fun leaveRoom() {
        manualDisconnect = true
        connectionGeneration += 1L
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectHandshakePending = false
        activeServerUrl = null
        sessionToken = null
        initialHandshakeType = null
        initialHandshakeSent = false
        stopPingJob()
        hasServerClockOffset = false
        serverClockOffsetMs = 0L
        lastSyncRequestElapsedMs = 0L
        pendingMessage = null
        stopLocalRoomDiscovery()
        stopLocalRoomAdvertisement()
        if (localMode) {
            leavingLocalMode = true
            runCatching {
                localClientWriter?.let { writeLocalLine(it, JSONObject().put("type", "leave")) }
            }
            localClientSocket?.let { runCatching { it.close() } }
            localClientSocket = null
            localClientWriter = null
            localClientReadJob?.cancel()
            localClientReadJob = null
            localAcceptJob?.cancel()
            localAcceptJob = null
            runCatching { localServer?.close() }
            localServer = null
            (pendingLocalPeers.values + localPeers.values).distinctBy { it.id }
                .forEach { runCatching { it.socket.close() } }
            pendingLocalPeers.clear()
            localPeers.clear()
            localMode = false
            localRoomPin = null
            _localAddress.value = null
            leavingLocalMode = false
        } else {
            try {
                if (_roomCode.value != null) send("leave_room", byteArrayOf())
            } catch (_: Exception) {
            }
        }
        socket?.close(1000, "Leaving room")
        socket = null
        _roomCode.value = null
        _role.value = "NONE"
        ownUserId = null
        _roomUsers.value = emptyList()
        _joinRequests.value = emptyList()
        _remotePlayback.value = null
        lastTrack = null
        lastSharedQueue = emptyList()
        lastPlaybackPositionMs = 0L
        lastPlaybackIsPlaying = false
        _connectionState.value = "Disconnected"
    }

    fun disconnect() = leaveRoom()

    private fun clearRoomState() {
        _roomCode.value = null
        _role.value = "NONE"
        ownUserId = null
        _roomUsers.value = emptyList()
        _joinRequests.value = emptyList()
        _remotePlayback.value = null
        lastTrack = null
        lastSharedQueue = emptyList()
    }

    fun sendTrackChange(
        track: SharedTrack,
        positionMs: Long = 0L,
        upcomingQueue: List<SharedTrack> = emptyList()
    ) {
        if (_role.value != "HOST") return
        lastTrack = track
        sendPlayback(
            action = "change_track",
            track = track,
            positionMs = positionMs,
            queue = upcomingQueue,
            queueTitle = "Listen Together",
            replaceQueue = true
        )
    }

    fun sendHostQueue(upcomingQueue: List<SharedTrack>, queueTitle: String = "Listen Together") {
        if (_role.value != "HOST") return
        sendPlayback(
            action = "sync_queue",
            track = lastTrack,
            positionMs = lastPlaybackPositionMs,
            queue = upcomingQueue,
            queueTitle = queueTitle,
            replaceQueue = true
        )
    }

    fun sendHostPlayback(isPlaying: Boolean, track: SharedTrack?, positionMs: Long) {
        if (_role.value != "HOST") return
        if (track != null) lastTrack = track
        sendPlayback(if (isPlaying) "play" else "pause", track ?: lastTrack, positionMs)
    }

    fun sendHostSeek(track: SharedTrack?, positionMs: Long) {
        if (_role.value != "HOST") return
        sendPlayback("seek", track ?: lastTrack, positionMs)
    }

    private fun sendPlayback(
        action: String,
        track: SharedTrack?,
        positionMs: Long,
        queue: List<SharedTrack>? = null,
        queueTitle: String? = null,
        replaceQueue: Boolean = false
    ) {
        val playing = when (action) {
            "play" -> true
            "pause", "change_track" -> false
            else -> lastPlaybackIsPlaying
        }
        lastPlaybackPositionMs = positionMs.coerceAtLeast(0L)
        lastPlaybackIsPlaying = playing
        if (queue != null) lastSharedQueue = queue
        if (localMode) {
            val message = localPlaybackMessage(action, track, positionMs, playing, queue, queueTitle)
            scope.launch { broadcastLocalMessage(message) }
            return
        }
        val writer = ProtoWriter()
            .string(1, action)
            .varint(3, positionMs.coerceAtLeast(0L))
        if (hasServerClockOffset) {
            writer.varint(11, serverNowMs())
        }
        if (track != null) {
            writer.string(2, track.id)
            writer.bytes(4, encodeTrack(track))
        }
        queue?.forEach { writer.bytes(6, encodeTrack(it)) }
        if (!queueTitle.isNullOrBlank()) writer.string(7, queueTitle)
        if (replaceQueue) writer.varint(12, 1L)
        send("playback_action", writer.toByteArray())
    }

    private fun startPingJob() {
        pingJob?.cancel()
        pingJob = scope.launch {
            repeat(3) {
                sendTimeSyncPing()
                delay(5_000L)
            }
            while (isActive) {
                sendTimeSyncPing()
                delay(15_000L)
            }
        }
    }

    private fun sendTimeSyncPing() {
        pingSequence += 1L
        val sequence = pingSequence
        val sentWallClock = System.currentTimeMillis()
        pendingPingSamples[sequence] = sentWallClock to SystemClock.elapsedRealtime()
        pendingPingSamples.entries
            .filter { SystemClock.elapsedRealtime() - it.value.second > 60_000L }
            .forEach { pendingPingSamples.remove(it.key) }

        val writer = ProtoWriter()
            .varint(1, sentWallClock)
            .varint(2, sequence)
        val playback = _remotePlayback.value
        val track = playback?.track ?: lastTrack
        if (playback != null && track != null && role.value == "GUEST" && hasServerClockOffset) {
            val elapsed = if (playback.isPlaying) {
                (SystemClock.elapsedRealtime() - remotePlaybackReceivedAtElapsedMs).coerceAtLeast(0L)
            } else 0L
            val position = (playback.positionMs + elapsed)
                .coerceAtLeast(0L)
                .let { value ->
                    if (track.durationMs > 0L) value.coerceAtMost(track.durationMs) else value
                }
            writer
                .varint(3, 1L)
                .string(4, track.id)
                .varint(5, position)
                .varint(6, serverNowMs())
        }

        val stateHash = CRC32().apply {
            val state = buildString {
                track?.let { append(it.id) }
                append('\n')
                lastSharedQueue.forEach { append(it.id).append('\n') }
            }
            update(state.toByteArray(Charsets.UTF_8))
        }.value
        writer.varint(7, 1L).varint(8, stateHash)
        send("ping", writer.toByteArray())
    }

    private fun send(type: String, payload: ByteArray) {
        val encoded = encodeEnvelope(type, payload)
        val current = socket
        val accepted = current?.send(ByteString.of(*encoded)) ?: false
        if (!accepted) {
            if (_connectionState.value != "Reconnecting") {
                _message.value = "Message could not be sent. Checking the room connection…"
            }
            if (current != null && !sessionToken.isNullOrBlank() && !manualDisconnect) {
                current.cancel()
            }
        }
    }

    private fun handleEnvelope(data: ByteArray) {
        val envelope = ProtoReader(data).fields()
        val type = envelope.firstString(1) ?: return
        var payload = envelope.firstBytes(2) ?: byteArrayOf()
        if (envelope.firstVarint(3) == 1L) {
            payload = runCatching {
                GZIPInputStream(ByteArrayInputStream(payload)).use { it.readBytes() }
            }.getOrDefault(payload)
        }
        val fields = ProtoReader(payload).fields()
        when (type) {
            "room_created" -> {
                val code = fields.firstString(1) ?: return
                _roomCode.value = code
                ownUserId = fields.firstString(2)
                fields.firstString(3)?.takeIf { it.isNotBlank() }?.let { sessionToken = it }
                pendingMessage = null
                reconnectHandshakePending = false
                reconnectAttempt = 0
                initialHandshakeSent = true
                _role.value = "HOST"
                _roomUsers.value = listOf("You (host)")
                _connectionState.value = "Connected"
                _message.value = "Room created. Share this code with friends."
            }
            "join_approved" -> {
                val code = fields.firstString(1) ?: return
                _roomCode.value = code
                ownUserId = fields.firstString(2)
                fields.firstString(3)?.takeIf { it.isNotBlank() }?.let { sessionToken = it }
                pendingMessage = null
                reconnectHandshakePending = false
                reconnectAttempt = 0
                initialHandshakeSent = true
                _role.value = "GUEST"
                fields.firstBytes(4)?.let { updateRoomStateFromSnapshot(it, "sync_state") }
                _connectionState.value = "Connected"
                _message.value = "You joined the listening room."
            }
            "reconnected" -> {
                val code = fields.firstString(1) ?: return
                _roomCode.value = code
                ownUserId = fields.firstString(2) ?: ownUserId
                val reconnectedAsHost = fields.firstVarint(4) == 1L
                val localHostTrack = if (reconnectedAsHost) lastTrack else null
                val localHostQueue = if (reconnectedAsHost) lastSharedQueue else emptyList()
                val localHostPosition = lastPlaybackPositionMs
                val localHostWasPlaying = lastPlaybackIsPlaying
                _role.value = if (reconnectedAsHost) "HOST" else "GUEST"
                reconnectHandshakePending = false
                reconnectAttempt = 0
                pendingMessage = null
                fields.firstBytes(3)?.let { updateRoomStateFromSnapshot(it, "sync_state") }
                if (reconnectedAsHost && localHostTrack != null) {
                    lastTrack = localHostTrack
                    lastSharedQueue = localHostQueue
                    lastPlaybackPositionMs = localHostPosition
                    lastPlaybackIsPlaying = localHostWasPlaying
                    resendHostStateAfterReconnect()
                }
                _connectionState.value = "Connected"
                _message.value = "Connection restored. Your listening room is synchronized."
            }
            "pong" -> handlePong(fields)
            "server_restarting" -> {
                _connectionState.value = "Reconnecting"
                _message.value = fields.firstString(2)
                    ?.takeIf { it.isNotBlank() }
                    ?: "The room server is restarting. Quantune will reconnect automatically."
            }
            "join_rejected" -> {
                _message.value = fields.firstString(1) ?: "The host declined the join request."
            }
            "join_request" -> {
                val id = fields.firstString(1) ?: return
                val name = fields.firstString(2) ?: "Listener"
                _joinRequests.value = (_joinRequests.value.filterNot { it.userId == id } + RoomJoinRequest(id, name))
                _message.value = "$name wants to join."
            }
            "user_joined" -> {
                val name = fields.firstString(2) ?: "Listener"
                _roomUsers.value = _roomUsers.value + name
                _message.value = "$name joined the room."
            }
            "user_left", "user_disconnected" -> {
                val name = fields.firstString(2) ?: "Listener"
                _roomUsers.value = _roomUsers.value.filterNot { it == name }
                _message.value = "$name left the room."
            }
            "sync_playback" -> handlePlayback(fields)
            "sync_state" -> {
                val track = fields.firstBytes(1)?.let(::decodeTrack)
                if (track != null) lastTrack = track
                val playing = fields.firstVarint(2) == 1L
                val rawPosition = fields.firstVarint(3) ?: 0L
                val lastUpdate = fields.firstVarint(4) ?: 0L
                val queue = fields.allBytes(5).mapNotNull(::decodeTrack)
                lastSharedQueue = queue
                val position = compensateRemotePosition(rawPosition, playing, lastUpdate, track?.durationMs ?: 0L)
                publishRemote("sync_state", track ?: lastTrack, position, playing, queue, fields.firstString(8))
            }
            "error" -> {
                val code = fields.firstString(1).orEmpty()
                val details = fields.firstString(2).orEmpty()
                val description = listOf(code, details).filter { it.isNotBlank() }.joinToString(": ")
                    .ifBlank { "The room server returned an error." }
                if (reconnectHandshakePending && code in setOf(
                        "session_not_found", "session_expired", "room_not_found",
                        "host_not_allowed", "missing_session_token"
                    )
                ) {
                    sessionToken = null
                    reconnectHandshakePending = false
                    reconnectJob?.cancel()
                    reconnectJob = null
                    manualDisconnect = true
                    activeServerUrl = null
                    socket?.close(1000, "Session cannot be restored")
                    socket = null
                    clearRoomState()
                    _connectionState.value = "Connection error"
                    _message.value = "Automatic reconnect is no longer available. Join the room again. $description"
                } else {
                    _message.value = description
                    if (reconnectHandshakePending) socket?.cancel()
                }
            }
            "kicked" -> {
                _message.value = fields.firstString(1) ?: "You were removed from the room."
                sessionToken = null
                reconnectHandshakePending = false
                reconnectJob?.cancel()
                reconnectJob = null
                manualDisconnect = true
                activeServerUrl = null
                stopPingJob()
                socket?.close(1000, "Room session ended")
                socket = null
                clearRoomState()
                _connectionState.value = "Disconnected"
            }
            "host_changed" -> {
                val hostId = fields.firstString(1)
                if (!hostId.isNullOrBlank() && ownUserId != null) {
                    _role.value = if (hostId == ownUserId) "HOST" else "GUEST"
                }
                _message.value = "The room host changed."
            }
        }
    }

    private fun handlePlayback(fields: List<ProtoField>) {
        val action = fields.firstString(1) ?: return
        val track = fields.firstBytes(4)?.let(::decodeTrack)
            ?: fields.firstString(2)?.let { id ->
                lastTrack?.takeIf { it.id == id }
            }
        if (action == "change_track" && track != null) lastTrack = track
        val rawPosition = fields.firstVarint(3) ?: 0L
        val queueWasSent = fields.any { it.number == 6 && it.wireType == 2 } ||
            fields.firstVarint(12) == 1L || action == "sync_queue"
        val queue = if (queueWasSent) fields.allBytes(6).mapNotNull(::decodeTrack) else null
        if (queue != null) lastSharedQueue = queue
        val playing = when (action) {
            "play" -> true
            "pause" -> false
            "change_track" -> false
            else -> _remotePlayback.value?.isPlaying ?: false
        }
        val position = compensateRemotePosition(
            rawPosition,
            playing,
            fields.firstVarint(9) ?: 0L,
            (track ?: lastTrack)?.durationMs ?: 0L
        )
        publishRemote(action, track ?: lastTrack, position, playing, queue, fields.firstString(7))
    }

    private fun publishRemote(
        action: String,
        track: SharedTrack?,
        position: Long,
        playing: Boolean,
        queue: List<SharedTrack>? = null,
        queueTitle: String? = null
    ) {
        if (queue != null) lastSharedQueue = queue
        remoteSequence += 1L
        remotePlaybackReceivedAtElapsedMs = SystemClock.elapsedRealtime()
        _remotePlayback.value = RemotePlaybackState(
            sequence = remoteSequence,
            action = action,
            track = track,
            positionMs = position.coerceAtLeast(0L),
            isPlaying = playing,
            queue = lastSharedQueue,
            queueTitle = queueTitle
        )
    }

    private fun encodeEnvelope(type: String, rawPayload: ByteArray): ByteArray {
        var payload = rawPayload
        var compressed = false
        if (payload.size > 100) {
            val zipped = ByteArrayOutputStream().use { output ->
                GZIPOutputStream(output).use { it.write(payload) }
                output.toByteArray()
            }
            if (zipped.size < payload.size) {
                payload = zipped
                compressed = true
            }
        }
        val envelope = ProtoWriter()
            .string(1, type)
            .bytes(2, payload)
        if (compressed) envelope.varint(3, 1L)
        return envelope.toByteArray()
    }

    private fun encodeTrack(track: SharedTrack): ByteArray = ProtoWriter()
        .string(1, track.id)
        .string(2, track.title)
        .string(3, track.artist)
        .string(4, track.album)
        .varint(5, track.durationMs.coerceAtLeast(0L))
        .string(6, track.thumbnail)
        .toByteArray()

    private fun decodeTrack(bytes: ByteArray): SharedTrack? {
        val fields = ProtoReader(bytes).fields()
        val id = fields.firstString(1)?.takeIf { it.isNotBlank() } ?: return null
        return SharedTrack(
            id = id,
            title = fields.firstString(2).orEmpty(),
            artist = fields.firstString(3).orEmpty(),
            album = fields.firstString(4).orEmpty(),
            durationMs = fields.firstVarint(5) ?: 0L,
            thumbnail = fields.firstString(6).orEmpty()
        )
    }
}

private data class ProtoField(
    val number: Int,
    val wireType: Int,
    val numberValue: Long? = null,
    val byteValue: ByteArray? = null
)

private class ProtoReader(private val input: ByteArray) {
    private var index = 0

    fun fields(): List<ProtoField> {
        val result = ArrayList<ProtoField>()
        while (index < input.size) {
            val tag = readVarint().toInt()
            if (tag == 0) break
            val number = tag ushr 3
            val wire = tag and 7
            when (wire) {
                0 -> result.add(ProtoField(number, wire, numberValue = readVarint()))
                1 -> {
                    require(index + 8 <= input.size) { "Truncated protobuf fixed64 field" }
                    index += 8
                }
                2 -> {
                    val length = readVarint().toInt()
                    require(length >= 0 && index + length <= input.size) { "Invalid protobuf field length" }
                    result.add(ProtoField(number, wire, byteValue = input.copyOfRange(index, index + length)))
                    index += length
                }
                5 -> {
                    require(index + 4 <= input.size) { "Truncated protobuf fixed32 field" }
                    index += 4
                }
                else -> throw IllegalArgumentException("Unsupported protobuf wire type $wire")
            }
        }
        return result
    }

    private fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (shift < 64 && index < input.size) {
            val value = input[index++].toInt() and 0xff
            result = result or ((value and 0x7f).toLong() shl shift)
            if ((value and 0x80) == 0) return result
            shift += 7
        }
        throw IllegalArgumentException("Malformed protobuf varint")
    }
}

private class ProtoWriter {
    private val output = ByteArrayOutputStream()

    fun string(number: Int, value: String): ProtoWriter {
        if (value.isNotEmpty()) bytes(number, value.toByteArray(Charsets.UTF_8))
        return this
    }

    fun bytes(number: Int, value: ByteArray): ProtoWriter {
        tag(number, 2)
        writeVarint(value.size.toLong())
        output.write(value)
        return this
    }

    fun varint(number: Int, value: Long): ProtoWriter {
        if (value == 0L) return this
        tag(number, 0)
        writeVarint(value)
        return this
    }

    fun toByteArray(): ByteArray = output.toByteArray()

    private fun tag(number: Int, wireType: Int) = writeVarint(((number shl 3) or wireType).toLong())

    private fun writeVarint(value: Long) {
        var remaining = value
        while ((remaining and -128L) != 0L) {
            output.write(((remaining and 0x7fL) or 0x80L).toInt())
            remaining = remaining ushr 7
        }
        output.write(remaining.toInt())
    }
}

private fun List<ProtoField>.firstBytes(number: Int): ByteArray? =
    firstOrNull { it.number == number && it.wireType == 2 }?.byteValue

private fun List<ProtoField>.allBytes(number: Int): List<ByteArray> =
    filter { it.number == number && it.wireType == 2 }.mapNotNull { it.byteValue }

private fun List<ProtoField>.firstString(number: Int): String? =
    firstBytes(number)?.toString(Charsets.UTF_8)

private fun List<ProtoField>.firstVarint(number: Int): Long? =
    firstOrNull { it.number == number && it.wireType == 0 }?.numberValue


/** Process-wide room session; retained while users navigate between app tabs. */
object DsmusicListenTogetherSession {
    val client: DsmusicListenTogetherClient by lazy { DsmusicListenTogetherClient() }
}
