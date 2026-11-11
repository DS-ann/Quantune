package com.dsann.music.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberUpdatedState
import com.dsann.music.data.collaboration.DsmusicListenTogetherSession
import com.dsann.music.data.collaboration.RemotePlaybackState
import com.dsann.music.data.collaboration.SharedTrack
import com.dsann.music.model.SongItem
import com.dsann.music.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun ListenTogetherPlaybackBridge(viewModel: PlayerSharedViewModel) {
    val client = DsmusicListenTogetherSession.client
    // This bridge must keep observing playback while the activity is backgrounded.
    // Lifecycle-aware collectors stop at STARTED/STOPPED and silently break room sync.
    val role by client.role.collectAsState()
    val remotePlayback by client.remotePlayback.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val songList by viewModel.songList.collectAsState()
    val currentSongIndex by viewModel.currentSongIndex.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val latestCurrentSong by rememberUpdatedState(currentSong)
    val latestSong by rememberUpdatedState(currentSong)
    val latestPlaying by rememberUpdatedState(isPlaying)
    val latestPosition by rememberUpdatedState(currentPosition)

    val upcomingSharedQueue = remember(songList, currentSongIndex, currentSong?.id, currentSong?.videoId) {
        val selectedIndex = currentSongIndex.takeIf { index ->
            index in songList.indices &&
                songList[index].id == currentSong?.id &&
                songList[index].videoId == currentSong?.videoId
        } ?: songList.indexOfFirst { it.id == currentSong?.id && it.videoId == currentSong?.videoId }
        if (selectedIndex < 0) {
            emptyList()
        } else {
            songList.drop(selectedIndex + 1).mapNotNull { song ->
                song.videoId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { song.toSharedTrack(it) }
            }
        }
    }
    val latestQueue by rememberUpdatedState(upcomingSharedQueue)

    LaunchedEffect(role) {
        viewModel.setListenTogetherQueueAuthoritative(role == "GUEST")
    }

    // Queue edits (add, remove, clear, or reorder) are sent independently of track changes.
    LaunchedEffect(role, upcomingSharedQueue) {
        if (role == "HOST") {
            delay(450L)
            client.sendHostQueue(latestQueue)
        }
    }

    val lastAppliedGuestControlSequence = remember { mutableLongStateOf(-1L) }
    val lastAnnouncedHostTrack = remember { mutableStateOf("") }

    LaunchedEffect(role) {
        if (role != "HOST") lastAnnouncedHostTrack.value = ""
    }

    // Publish track changes and their resulting transport state in one ordered effect.
    // Sending these from separate effects let "play" overtake "change_track" on a host
    // skip; the later change_track then paused every guest until another host action.
    LaunchedEffect(role, isPlaying, currentSong?.id, currentSong?.videoId, isLoading) {
        if (role == "HOST") {
            val incomingGuestControl = remotePlayback
            if (
                incomingGuestControl != null &&
                incomingGuestControl.action in setOf("play", "pause") &&
                incomingGuestControl.sequence == lastAppliedGuestControlSequence.longValue
            ) {
                // The host has just applied this guest request. Consume the marker so
                // a later independent host action with the same play state is not lost.
                lastAppliedGuestControlSequence.longValue = -1L
                return@LaunchedEffect
            }

            val song = currentSong
            val videoId = song?.videoId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
            val trackKey = song.id.toString() + ":" + videoId
            val sharedTrack = song.toSharedTrack(videoId)

            // Start guest stream resolution as soon as the host selects a different song.
            // Do not make every listener wait for the host's stream resolver to finish.
            if (lastAnnouncedHostTrack.value != trackKey) {
                client.sendTrackChange(
                    track = sharedTrack,
                    positionMs = 0L,
                    upcomingQueue = latestQueue
                )
                lastAnnouncedHostTrack.value = trackKey
            }

            // The track announcement above is independent of isLoading. Only the final
            // transport state waits for the host's selected track to become ready.
            if (!isLoading) {
                client.sendHostPlayback(
                    isPlaying = isPlaying,
                    track = sharedTrack,
                    positionMs = currentPosition.toLong()
                )
            }
        }
    }

    // Do not rebroadcast a full "play" action on a timer. On a slow connection,
    // queued play actions can arrive late and repeatedly seek guests back to the same
    // old position, making a short part of the song sound as if it is looping.
    // Play/pause and track changes are already sent by the event-driven effects above.
    // Only send a seek when the host's position actually jumps away from its timeline.
    LaunchedEffect(role, currentSong?.id, currentSong?.videoId) {
        if (role == "HOST") {
            var previousPosition = latestPosition.toLong().coerceAtLeast(0L)
            var previousSampleAt = SystemClock.elapsedRealtime()

            while (true) {
                delay(500L)
                val now = SystemClock.elapsedRealtime()
                val elapsed = (now - previousSampleAt).coerceIn(0L, 2_000L)
                val observedPosition = latestPosition.toLong().coerceAtLeast(0L)
                val expectedPosition = previousPosition + if (latestPlaying) elapsed else 0L

                // Compare against the expected timeline in both playback states.
                // Paused seeks must be relayed too, and absolute difference detects
                // both forward and backward jumps.
                if (elapsed > 0L && abs(observedPosition - expectedPosition) > 1_200L) {
                    val song = latestSong
                    val videoId = song?.videoId
                    if (song != null && !videoId.isNullOrBlank()) {
                        client.sendHostSeek(
                            track = song.toSharedTrack(videoId),
                            positionMs = observedPosition
                        )
                    }
                }

                previousPosition = observedPosition
                previousSampleAt = now
            }
        }
    }

    // Apply only the newest transport/track event. Loading an older skipped track must
    // not hold up a newer host selection; the service cancels superseded URI resolutions.
    // Queue-only snapshots are handled separately so they cannot cancel a track load.
    LaunchedEffect(role) {
        var guestPlaybackJob: Job? = null
        client.remotePlayback.collect { playback ->
            if (playback != null) {
                when {
                    role == "GUEST" && playback.action in setOf("sync_queue", "queue_clear") -> {
                        applyRemoteQueue(
                            client.remotePlayback.value?.queue ?: playback.queue,
                            viewModel
                        )
                    }
                    role == "GUEST" -> {
                        guestPlaybackJob?.cancel()
                        guestPlaybackJob = launch {
                            applyRemotePlayback(
                                playback,
                                viewModel.currentSong.value,
                                viewModel
                            )
                        }
                    }
                    role == "HOST" && playback.action in setOf("play", "pause") -> {
                        // The server already relays this request to every other listener.
                        // Apply only transport state on the host: guest position/track/queue
                        // data must never change the host's authoritative playback timeline.
                        if (viewModel.isPlaying.value != playback.isPlaying) {
                            lastAppliedGuestControlSequence.longValue = playback.sequence
                            if (playback.isPlaying) {
                                viewModel.playAudio(notifyListenTogether = false)
                            } else {
                                viewModel.pauseAudio(notifyListenTogether = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun SongItem.toSharedTrack(videoId: String): SharedTrack = SharedTrack(
    id = videoId,
    title = title,
    artist = artist,
    album = albumName.orEmpty(),
    durationMs = duration.coerceAtLeast(0L),
    thumbnail = thumbnailUrl.orEmpty()
)

private fun SharedTrack.toQueueSongItem(queueIndex: Int): SongItem {
    // Same video can appear multiple times in a queue; incorporate its occurrence index
    // so Compose queue rows and PlayerSharedViewModel have distinct identities.
    val stableId = (((id.hashCode().toLong() and 0x7fffffffL) + 1L) * 100_000L) + queueIndex + 1L
    return SongItem(
        id = stableId,
        title = title.ifBlank { "Shared track" },
        artist = artist.ifBlank { "Unknown artist" },
        albumId = 0L,
        path = null,
        duration = durationMs.coerceAtLeast(0L),
        dateAdded = 0L,
        data = null
    ).apply {
        videoId = this@toQueueSongItem.id
        thumbnailUrl = this@toQueueSongItem.thumbnail.takeIf { it.startsWith("http") }
        albumName = this@toQueueSongItem.album
    }
}

private fun applyRemoteQueue(queue: List<SharedTrack>, viewModel: PlayerSharedViewModel) {
    if (viewModel.currentSong.value == null) return
    val upcoming = queue.mapIndexed { index, sharedTrack ->
        sharedTrack.toQueueSongItem(index)
    }.filter { !it.videoId.isNullOrBlank() }
    viewModel.applyListenTogetherUpcomingQueue(upcoming)
}

private suspend fun applyRemotePlayback(
    playback: RemotePlaybackState,
    currentSong: SongItem?,
    viewModel: PlayerSharedViewModel
) {
    val track = playback.track.takeUnless {
        playback.action == "sync_queue" || playback.action == "queue_clear"
    }
    if (track != null) {
        val isDifferentTrack = currentSong?.videoId != track.id
        if (isDifferentTrack) {
            val incoming = SongItem(
                id = (track.id.hashCode().toLong() and 0x7fffffffL) + 1L,
                title = track.title.ifBlank { "Shared track" },
                artist = track.artist.ifBlank { "Unknown artist" },
                albumId = 0L,
                path = null,
                duration = track.durationMs.coerceAtLeast(0L),
                dateAdded = System.currentTimeMillis() / 1000L,
                data = null
            ).apply {
                videoId = track.id
                thumbnailUrl = track.thumbnail.takeIf { it.startsWith("http") }
                albumName = track.album
            }
            viewModel.playListenTogetherTrack(incoming)
        }

        // Do not seek into an unprepared stream. The collector cancels this wait as soon
        // as a newer host action arrives, so rapid skips always supersede stale loads.
        if (!viewModel.isPlaybackReady.value) {
            viewModel.isPlaybackReady.first { ready -> ready }
        }


    }

    // A slow stream load adds time between receipt and playback. Advance the host's
    // reported position by that wait so the guest does not start several seconds behind.
    val elapsedSinceEvent = if (playback.isPlaying && playback.receivedAtElapsedMs > 0L) {
        (SystemClock.elapsedRealtime() - playback.receivedAtElapsedMs).coerceAtLeast(0L)
    } else {
        0L
    }
    val rawTargetPosition = playback.positionMs + elapsedSinceEvent
    val durationMs = playback.track?.durationMs ?: 0L
    val targetPositionMs = if (durationMs > 0L) {
        rawTargetPosition.coerceAtMost(durationMs)
    } else {
        rawTargetPosition
    }

    // Use the newest queue snapshot, not the potentially stale snapshot attached to
    // the earlier track event that started this load.
    val latestQueue = DsmusicListenTogetherSession.client.remotePlayback.value?.queue ?: playback.queue
    applyRemoteQueue(latestQueue, viewModel)

    when (playback.action) {
        "play", "sync_state" -> {
            val current = viewModel.currentPosition.value.toLong()
            if (abs(current - targetPositionMs) > 1_500L) {
                viewModel.seekTo(targetPositionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), fromListenTogether = true)
            }
            if (playback.isPlaying) {
                viewModel.playAudio(notifyListenTogether = false)
            } else {
                viewModel.pauseAudio(notifyListenTogether = false)
            }
        }
        "pause", "change_track" -> {
            val current = viewModel.currentPosition.value.toLong()
            if (abs(current - targetPositionMs) > 450L) {
                viewModel.seekTo(targetPositionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), fromListenTogether = true)
            }
            // A host track change is loaded paused; the following host play event starts it.
            viewModel.pauseAudio(notifyListenTogether = false)
        }
        "seek" -> {
            viewModel.seekTo(targetPositionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), fromListenTogether = true)
            if (playback.isPlaying) viewModel.playAudio(notifyListenTogether = false)
            else viewModel.pauseAudio(notifyListenTogether = false)
        }
    }
}
