package com.dsann.music.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MusicNote
import com.dsann.music.data.collaboration.DsmusicListenTogetherClient
import com.dsann.music.data.collaboration.SharedTrack
import com.dsann.music.data.collaboration.DsmusicListenTogetherSession
import com.dsann.music.ui.theme.DsmusicButton
import com.dsann.music.ui.theme.DsmusicCard
import com.dsann.music.ui.theme.DsmusicIconButton
import com.dsann.music.ui.theme.DsmusicOutlinedButton
import com.dsann.music.ui.theme.DsmusicPageHeading
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.OutlinedTextField
import com.dsann.music.ui.theme.Text
import com.dsann.music.viewmodel.PlayerSharedViewModel

@Composable
fun ListenTogetherScreen(viewModel: PlayerSharedViewModel, onBack: () -> Unit) {
    val client = DsmusicListenTogetherSession.client
    val context = LocalContext.current
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val songList by viewModel.songList.collectAsStateWithLifecycle()
    val currentSongIndex by viewModel.currentSongIndex.collectAsStateWithLifecycle()
    val remotePlayback by client.remotePlayback.collectAsStateWithLifecycle()
    val connectionState by client.connectionState.collectAsStateWithLifecycle()
    val roomCode by client.roomCode.collectAsStateWithLifecycle()
    val role by client.role.collectAsStateWithLifecycle()
    val roomUsers by client.roomUsers.collectAsStateWithLifecycle()
    val joinRequests by client.joinRequests.collectAsStateWithLifecycle()
    val joinPending by client.joinPending.collectAsStateWithLifecycle()
    val roomMessage by client.message.collectAsStateWithLifecycle()
    val localAddress by client.localAddress.collectAsStateWithLifecycle()
    val discoveredLocalRooms by client.discoveredLocalRooms.collectAsStateWithLifecycle()
    val localDiscoveryStatus by client.localDiscoveryStatus.collectAsStateWithLifecycle()

    var networkMode by rememberSaveable { mutableStateOf("SAME_NETWORK") }
    var serverUrl by rememberSaveable { mutableStateOf(DsmusicListenTogetherClient.DEFAULT_SERVER) }
    var displayName by rememberSaveable { mutableStateOf("Listener") }
    var joinCode by rememberSaveable { mutableStateOf("") }
    var showInternetJoinCode by remember { mutableStateOf(false) }
    var localHostAddress by rememberSaveable { mutableStateOf("") }
    var localJoinPin by rememberSaveable { mutableStateOf("") }
    var showLocalJoinPin by rememberSaveable { mutableStateOf(false) }
    var selectedLocalRoomEndpoint by rememberSaveable { mutableStateOf<String?>(null) }
    var showManualLocalAddress by rememberSaveable { mutableStateOf(false) }
    var showHostAddressFallback by rememberSaveable { mutableStateOf(false) }
    var advancedSettingsVisible by rememberSaveable { mutableStateOf(false) }
    var actionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var localPermissionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showJoinRequestDialog by remember { mutableStateOf(false) }

    val requiredLocalPermission = when {
        Build.VERSION.SDK_INT >= 37 -> Manifest.permission.ACCESS_LOCAL_NETWORK
        Build.VERSION.SDK_INT >= 33 -> Manifest.permission.NEARBY_WIFI_DEVICES
        else -> null
    }
    var localNetworkPermissionGranted by remember(requiredLocalPermission) {
        mutableStateOf(
            requiredLocalPermission == null ||
                androidx.core.content.ContextCompat.checkSelfPermission(context, requiredLocalPermission) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val localPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        localNetworkPermissionGranted = granted
        localPermissionMessage = if (granted) null else
            "Allow Nearby devices / Local network access to discover and join local rooms."
        if (granted && networkMode == "SAME_NETWORK") client.discoverLocalRooms(context)
    }

    val isConnected = connectionState == "Connected" && !roomCode.isNullOrBlank()
    val canStartSession = connectionState == "Disconnected" || connectionState == "Connection error"
    val statusLabel = when {
        joinPending -> if (connectionState == "Connecting" || connectionState == "Reconnecting") "JOINING" else "WAITING"
        isConnected -> "LIVE"
        connectionState == "Reconnecting" -> "RECONNECTING"
        connectionState == "Connecting" -> "CONNECTING"
        connectionState == "Connection error" -> "OFFLINE"
        else -> "READY"
    }
    val hostCurrentTrack = remember(currentSong?.id, currentSong?.videoId) {
        currentSong?.let { song ->
            song.videoId?.takeIf { it.isNotBlank() }?.let { videoId ->
                SharedTrack(
                    id = videoId,
                    title = song.title,
                    artist = song.artist,
                    album = song.albumName.orEmpty(),
                    durationMs = song.duration.coerceAtLeast(0L),
                    thumbnail = song.thumbnailUrl.orEmpty()
                )
            }
        }
    }
    val hostUpcomingQueue = remember(songList, currentSongIndex, currentSong?.id, currentSong?.videoId) {
        val index = currentSongIndex.takeIf { selected ->
            selected in songList.indices &&
                songList[selected].id == currentSong?.id &&
                songList[selected].videoId == currentSong?.videoId
        } ?: songList.indexOfFirst {
            it.id == currentSong?.id && it.videoId == currentSong?.videoId
        }
        if (index < 0) emptyList() else songList.drop(index + 1).mapNotNull { song ->
            song.videoId?.takeIf { it.isNotBlank() }?.let { videoId ->
                SharedTrack(
                    id = videoId,
                    title = song.title,
                    artist = song.artist,
                    album = song.albumName.orEmpty(),
                    durationMs = song.duration.coerceAtLeast(0L),
                    thumbnail = song.thumbnailUrl.orEmpty()
                )
            }
        }
    }
    val roomCurrentTrack = if (role == "HOST") hostCurrentTrack else remotePlayback?.track
    val roomUpcomingQueue = if (role == "HOST") hostUpcomingQueue else remotePlayback?.queue.orEmpty()

    BackHandler(enabled = joinPending) {
        client.cancelJoinRequest()
        onBack()
    }

    LaunchedEffect(joinRequests.map { it.userId }) {
        showJoinRequestDialog = joinRequests.isNotEmpty()
    }

    LaunchedEffect(networkMode, canStartSession, localNetworkPermissionGranted) {
        if (networkMode == "SAME_NETWORK" && canStartSession) {
            if (localNetworkPermissionGranted) client.discoverLocalRooms(context)
            else if (requiredLocalPermission != null) localPermissionLauncher.launch(requiredLocalPermission)
        } else {
            client.stopLocalRoomDiscovery()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsmusicTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.statusBarsPadding().fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DsmusicPageHeading(
                title = "Listen Together",
                modifier = Modifier.weight(1f)
            )
            ListenTogetherStatusPill(statusLabel = statusLabel, active = isConnected || joinPending)
            DsmusicIconButton(
                onClick = {
                    if (joinPending) client.cancelJoinRequest()
                    onBack()
                },
                modifier = Modifier
                    .size(42.dp)
                    .background(DsmusicTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = DsmusicTheme.colorScheme.onSurface
                )
            }
        }

        if (joinRequests.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(DsmusicTheme.colorScheme.surfaceContainerLow)
                    .clickable { showJoinRequestDialog = true }
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("JOIN REQUESTS", style = DsmusicTheme.typography.labelSmall, color = DsmusicTheme.colorScheme.primary)
                    Text(
                        if (joinRequests.size == 1) "1 person is waiting" else "${joinRequests.size} people are waiting",
                        style = DsmusicTheme.typography.bodySmall,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("Review", style = DsmusicTheme.typography.labelMedium, color = DsmusicTheme.colorScheme.primary)
            }
        }

        if (joinPending) {
            DsmusicCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(DsmusicTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Groups,
                            contentDescription = null,
                            tint = DsmusicTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(29.dp)
                        )
                    }
                    Text(
                        if (connectionState == "Connecting" || connectionState == "Reconnecting") "Connecting to room"
                        else "Waiting for host approval",
                        style = DsmusicTheme.typography.titleMedium,
                        color = DsmusicTheme.colorScheme.onSurface
                    )
                    Text(
                        when (connectionState) {
                            "Connecting" -> "Connecting to the room server…"
                            "Reconnecting" -> "Restoring the connection…"
                            else -> "Your request has been sent to the host."
                        },
                        style = DsmusicTheme.typography.bodySmall,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                    if (networkMode == "DIFFERENT_NETWORK" && joinCode.isNotBlank()) {
                        Text(
                            "ROOM  ·  ${joinCode.uppercase()}",
                            style = DsmusicTheme.typography.labelMedium,
                            color = DsmusicTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    }
                    DsmusicOutlinedButton(
                        onClick = {
                            showInternetJoinCode = false
                            client.cancelJoinRequest()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Cancel request") }
                }
            }
        } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(17.dp))
                .background(DsmusicTheme.colorScheme.surfaceContainerLow)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ListenTogetherModeOption(
                title = "Same Wi-Fi",
                subtitle = "Nearby devices",
                selected = networkMode == "SAME_NETWORK",
                enabled = canStartSession
            ) {
                networkMode = "SAME_NETWORK"
                showInternetJoinCode = false
            }
            ListenTogetherModeOption(
                title = "Internet",
                subtitle = "Different networks",
                selected = networkMode == "DIFFERENT_NETWORK",
                enabled = canStartSession
            ) {
                networkMode = "DIFFERENT_NETWORK"
                showInternetJoinCode = false
            }
        }

        DsmusicCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(19.dp)) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("Create or join", style = DsmusicTheme.typography.titleMedium)
                        Text(
                            if (networkMode == "SAME_NETWORK") "Local network"
                            else "Online room",
                            style = DsmusicTheme.typography.bodySmall,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ListenTogetherStatusPill(
                        statusLabel = if (networkMode == "SAME_NETWORK") "LOCAL" else "ONLINE",
                        active = false
                    )
                }

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it.take(32) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Display name") }
                )

                if (networkMode == "SAME_NETWORK") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DsmusicButton(
                            onClick = {
                                actionMessage = null
                                try {
                                    client.createLocalRoom(context, displayName)
                                } catch (error: Exception) {
                                    actionMessage = error.message ?: "Could not start a local room."
                                }
                            },
                            enabled = canStartSession && localNetworkPermissionGranted && displayName.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Host a room") }
                        DsmusicOutlinedButton(
                            onClick = { client.discoverLocalRooms(context) },
                            enabled = canStartSession && localNetworkPermissionGranted,
                            modifier = Modifier.weight(1f)
                        ) { Text("Find rooms") }
                    }

                    Text(
                        localPermissionMessage ?: localDiscoveryStatus,
                        style = DsmusicTheme.typography.bodySmall,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                    )

                    discoveredLocalRooms.forEach { room ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(13.dp))
                                .background(DsmusicTheme.colorScheme.surfaceContainerLow)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(room.serviceName, style = DsmusicTheme.typography.bodyMedium, maxLines = 1)
                                Text(
                                    "Nearby host",
                                    style = DsmusicTheme.typography.labelSmall,
                                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DsmusicButton(
                                onClick = {
                                    actionMessage = null
                                    if (selectedLocalRoomEndpoint != room.endpoint || !showLocalJoinPin) {
                                        selectedLocalRoomEndpoint = room.endpoint
                                        showLocalJoinPin = true
                                    } else if (localJoinPin.length == 6) {
                                        try {
                                            client.joinLocalRoom(room.endpoint, localJoinPin, displayName)
                                        } catch (error: Exception) {
                                            actionMessage = error.message ?: "Could not join the local room."
                                        }
                                    }
                                },
                                enabled = canStartSession && localNetworkPermissionGranted &&
                                    displayName.isNotBlank() &&
                                    (selectedLocalRoomEndpoint != room.endpoint || !showLocalJoinPin || localJoinPin.length == 6)
                            ) { Text(if (selectedLocalRoomEndpoint == room.endpoint && showLocalJoinPin) "Connect" else "Join") }
                        }
                    }

                    if (showLocalJoinPin && selectedLocalRoomEndpoint != null) {
                        OutlinedTextField(
                            value = localJoinPin,
                            onValueChange = { localJoinPin = it.filter(Char::isDigit).take(6) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("6-digit room PIN") }
                        )
                    }

                    Text(
                        if (showManualLocalAddress) "MANUAL CONNECTION" else "Can't see the host?",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showManualLocalAddress = !showManualLocalAddress }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        style = DsmusicTheme.typography.labelMedium,
                        color = DsmusicTheme.colorScheme.primary
                    )
                    if (showManualLocalAddress) {
                        OutlinedTextField(
                            value = localHostAddress,
                            onValueChange = { localHostAddress = it.trim().take(80) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Host address (IP:port)") },
                            placeholder = { Text("192.168.1.10:42173") }
                        )
                        DsmusicOutlinedButton(
                            onClick = {
                                actionMessage = null
                                if (!showLocalJoinPin) {
                                    selectedLocalRoomEndpoint = null
                                    showLocalJoinPin = true
                                } else if (localJoinPin.length == 6) {
                                    try {
                                        client.joinLocalRoom(localHostAddress, localJoinPin, displayName)
                                    } catch (error: Exception) {
                                        actionMessage = error.message ?: "Could not join the local room."
                                    }
                                }
                            },
                            enabled = canStartSession && localNetworkPermissionGranted &&
                                localHostAddress.isNotBlank() && displayName.isNotBlank() &&
                                (!showLocalJoinPin || localJoinPin.length == 6),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (showLocalJoinPin && selectedLocalRoomEndpoint == null) "Connect" else "Join with address") }
                    }
                } else {
                    DsmusicButton(
                        onClick = {
                            actionMessage = null
                            try {
                                client.createRoom(serverUrl, displayName)
                            } catch (error: Exception) {
                                actionMessage = error.message ?: "Could not create the room."
                            }
                        },
                        enabled = canStartSession && displayName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Create an internet room") }

                    Text(
                        if (showInternetJoinCode) "CLOSE ROOM CODE" else "JOIN WITH A ROOM CODE",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showInternetJoinCode = !showInternetJoinCode }
                            .padding(vertical = 5.dp, horizontal = 2.dp),
                        style = DsmusicTheme.typography.labelMedium,
                        color = DsmusicTheme.colorScheme.primary
                    )
                    if (showInternetJoinCode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = joinCode,
                                onValueChange = { joinCode = it.uppercase().filter(Char::isLetterOrDigit).take(12) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("Room code") }
                            )
                            DsmusicOutlinedButton(
                                onClick = {
                                    showInternetJoinCode = false
                                    actionMessage = null
                                    try {
                                        client.joinRoom(serverUrl, joinCode, displayName)
                                    } catch (error: Exception) {
                                        actionMessage = error.message ?: "Could not join the room."
                                    }
                                },
                                enabled = canStartSession && joinCode.isNotBlank() && displayName.isNotBlank()
                            ) { Text("Join") }
                        }
                    }

                    Text(
                        if (advancedSettingsVisible) "HIDE SERVER SETTINGS" else "SERVER SETTINGS",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { advancedSettingsVisible = !advancedSettingsVisible }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        style = DsmusicTheme.typography.labelMedium,
                        color = DsmusicTheme.colorScheme.primary
                    )
                    if (advancedSettingsVisible) {
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it.trim() },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("WebSocket server") },
                            placeholder = { Text("wss://server.example/ws") }
                        )
                    }
                }

                (actionMessage ?: roomMessage).takeIf { it.isNotBlank() }?.let { message ->
                    Text(
                        message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .background(DsmusicTheme.colorScheme.surfaceContainerLow)
                            .padding(10.dp),
                        style = DsmusicTheme.typography.bodySmall,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        }


        if (!roomCode.isNullOrBlank()) {
            DsmusicCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(19.dp)) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                if (networkMode == "SAME_NETWORK") "LOCAL ROOM PIN" else "ROOM CODE",
                                style = DsmusicTheme.typography.labelSmall,
                                color = DsmusicTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                roomCode.orEmpty(),
                                style = DsmusicTheme.typography.headlineSmall,
                                color = DsmusicTheme.colorScheme.onSurface
                            )
                        }
                        ListenTogetherStatusPill(statusLabel = role, active = true)
                    }

                    if (networkMode == "SAME_NETWORK" && role == "HOST") {
                        Text(
                            if (showHostAddressFallback) "Hide fallback address" else "Show fallback address",
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showHostAddressFallback = !showHostAddressFallback }
                                .padding(vertical = 3.dp, horizontal = 2.dp),
                            style = DsmusicTheme.typography.labelMedium,
                            color = DsmusicTheme.colorScheme.primary
                        )
                        if (showHostAddressFallback) {
                            Text(
                                localAddress ?: "Local IP address unavailable",
                                style = DsmusicTheme.typography.bodySmall,
                                color = DsmusicTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("LISTENERS", style = DsmusicTheme.typography.labelSmall, color = DsmusicTheme.colorScheme.primary)
                        Spacer(Modifier.weight(1f))
                        Text(
                            roomUsers.size.toString(),
                            style = DsmusicTheme.typography.labelMedium,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (roomUsers.isNotEmpty()) {
                        Text(
                            roomUsers.joinToString("  ·  "),
                            style = DsmusicTheme.typography.bodySmall,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }

                    if (role == "HOST" && currentSong?.videoId.isNullOrBlank()) {
                        Text(
                            "Play an online track to share it. Local-only tracks cannot be resolved on guests' devices.",
                            style = DsmusicTheme.typography.bodySmall,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (role == "HOST" || role == "GUEST") {
                        roomCurrentTrack?.let { track ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("NOW PLAYING", style = DsmusicTheme.typography.labelSmall, color = DsmusicTheme.colorScheme.primary)
                                Spacer(Modifier.weight(1f))
                                Text("LIVE", style = DsmusicTheme.typography.labelSmall, color = DsmusicTheme.colorScheme.primary)
                            }
                            ListenTogetherTrackRow(track = track, position = null, isCurrent = true)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("UP NEXT", style = DsmusicTheme.typography.labelSmall, color = DsmusicTheme.colorScheme.primary)
                            Spacer(Modifier.weight(1f))
                            Text(
                                roomUpcomingQueue.size.toString() + " tracks",
                                style = DsmusicTheme.typography.labelSmall,
                                color = DsmusicTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            if (roomUpcomingQueue.isEmpty()) {
                                "No upcoming tracks."
                            } else {
                                "${roomUpcomingQueue.size} tracks waiting in the shared queue."
                            },
                            style = DsmusicTheme.typography.bodySmall,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DsmusicOutlinedButton(
                        onClick = { client.leaveRoom() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Leave room") }
                }
            }
        }

        Text(
            "Only track details, queue and playback controls are shared. Audio stays on each device.",
            style = DsmusicTheme.typography.labelSmall,
            color = DsmusicTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(156.dp))
    }

    val activeJoinRequest = joinRequests.firstOrNull()
    if (showJoinRequestDialog && activeJoinRequest != null) {
        Dialog(onDismissRequest = { showJoinRequestDialog = false }) {
                DsmusicCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(DsmusicTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Groups,
                                contentDescription = null,
                                tint = DsmusicTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Text(
                            "ROOM JOIN REQUEST",
                            style = DsmusicTheme.typography.labelSmall,
                            color = DsmusicTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            activeJoinRequest.username,
                            style = DsmusicTheme.typography.titleLarge,
                            color = DsmusicTheme.colorScheme.onSurface
                        )
                        Text(
                            "Wants to join your listening room.",
                            style = DsmusicTheme.typography.bodyMedium,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                        if (joinRequests.size > 1) {
                            Text(
                                "${joinRequests.size - 1} more waiting",
                                style = DsmusicTheme.typography.labelSmall,
                                color = DsmusicTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DsmusicButton(
                            onClick = {
                                client.approveJoin(activeJoinRequest.userId)
                                showJoinRequestDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Approve request") }
                        DsmusicOutlinedButton(
                            onClick = { showJoinRequestDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Later") }
                    }
                }
            }
        }
}

@Composable
private fun RowScope.ListenTogetherModeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (selected) DsmusicTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            title,
            style = DsmusicTheme.typography.titleSmall,
            color = if (selected) DsmusicTheme.colorScheme.onPrimaryContainer
            else DsmusicTheme.colorScheme.onSurface
        )
        Text(
            subtitle,
            style = DsmusicTheme.typography.labelSmall,
            color = if (selected) DsmusicTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
            else DsmusicTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ListenTogetherStatusPill(statusLabel: String, active: Boolean) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (active) DsmusicTheme.colorScheme.primary.copy(alpha = 0.15f)
                else DsmusicTheme.colorScheme.surfaceContainerHigh
            )
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            statusLabel,
            style = DsmusicTheme.typography.labelSmall,
            color = if (active) DsmusicTheme.colorScheme.primary
            else DsmusicTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ListenTogetherTrackRow(
    track: SharedTrack,
    position: Int?,
    isCurrent: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (isCurrent) DsmusicTheme.colorScheme.primary.copy(alpha = 0.11f)
                else DsmusicTheme.colorScheme.surfaceContainerLow
            )
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        if (position != null) {
            Text(
                position.toString(),
                style = DsmusicTheme.typography.labelMedium,
                color = DsmusicTheme.colorScheme.primary
            )
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(DsmusicTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (track.thumbnail.startsWith("http")) {
                AsyncImage(
                    model = track.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = DsmusicTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(
                track.title.ifBlank { "Unknown title" },
                style = DsmusicTheme.typography.bodySmall,
                color = DsmusicTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                track.artist.ifBlank { "Unknown artist" },
                style = DsmusicTheme.typography.labelSmall,
                color = DsmusicTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        if (isCurrent) {
            Text(
                "LIVE",
                style = DsmusicTheme.typography.labelSmall,
                color = DsmusicTheme.colorScheme.primary
            )
        }
    }
}
