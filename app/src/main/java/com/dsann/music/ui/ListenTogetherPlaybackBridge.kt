package com.dsann.music.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsann.music.data.collaboration.DsmusicListenTogetherSession
import com.dsann.music.data.collaboration.RemotePlaybackState
import com.dsann.music.data.collaboration.SharedTrack
import com.dsann.music.model.SongItem
import com.dsann.music.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

@Composable
fun ListenTogetherPlaybackBridge(viewModel: PlayerSharedViewModel) {
    val client = DsmusicListenTogetherSession.client
    val role by client.role.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val songList by viewModel.songList.collectAsStateWithLifecycle()
    val currentSongIndex by viewModel.currentSongIndex.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val remotePlayback by client.remotePlayback.collectAsStateWithLifecycle()

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

    // Track changes include a complete upcoming queue snapshot, so late joiners and
    // newly selected songs receive the same ordered queue as existing listeners.
    LaunchedEffect(role, currentSong?.id, currentSong?.videoId) {
        if (role == "HOST") {
            val song = currentSong
            val videoId = song?.videoId
            if (song != null && !videoId.isNullOrBlank()) {
                client.sendTrackChange(
                    track = song.toSharedTrack(videoId),
                    positionMs = currentPosition.toLong(),
                    upcomingQueue = latestQueue
                )
            }
        }
    }

    // Queue edits (add, remove, clear, or reorder) are sent independently of track changes.
    LaunchedEffect(role, upcomingSharedQueue) {
        if (role == "HOST") {
            delay(450L)
            client.sendHostQueue(latestQueue)
        }
    }

    LaunchedEffect(role, isPlaying, currentSong?.id) {
        if (role == "HOST") {
            val song = currentSong
            client.sendHostPlayback(
                isPlaying = isPlaying,
                track = song?.videoId?.takeIf { it.isNotBlank() }?.let { song.toSharedTrack(it) },
                positionMs = currentPosition.toLong()
            )
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

                if (latestPlaying && elapsed > 0L &&
                    abs(observedPosition - expectedPosition) > 2_500L
                ) {
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

    LaunchedEffect(role, remotePlayback?.sequence) {
        if (role == "GUEST") {
            remotePlayback?.let { playback ->
                applyRemotePlayback(playback, currentSong, viewModel)
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

private suspend fun applyRemotePlayback(
    playback: RemotePlaybackState,
    currentSong: SongItem?,
    viewModel: PlayerSharedViewModel
) {
    val track = playback.track
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
            viewModel.playSong(incoming)
        }

        // The selected song is published before its stream is resolved. Wait for the
        // actual player-ready signal rather than guessing how long a slow connection needs.
        // The timeout still lets later sync messages proceed when a stream cannot load.
        if (!viewModel.isPlaybackReady.value) {
            withTimeoutOrNull(20_000L) {
                viewModel.isPlaybackReady.first { ready -> ready }
            }
        }

        if (!isDifferentTrack && playback.action == "change_track") {
            // The same video can occupy multiple queue positions. Restart that occurrence
            // at the host's position without unnecessarily resolving the same stream again.
            viewModel.seekTo(playback.positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            viewModel.pauseAudio()
        }
    }

    // Apply the shared upcoming list after a track change has loaded. The ViewModel
    // retains the active song as row zero, then preserves every ordered queue occurrence.
    val queueCurrent = viewModel.currentSong.value ?: currentSong
    if (queueCurrent != null) {
        val upcoming = playback.queue.mapIndexed { index, sharedTrack ->
            sharedTrack.toQueueSongItem(index)
        }.filter { !it.videoId.isNullOrBlank() }
        viewModel.applyListenTogetherUpcomingQueue(upcoming)
    }

    when (playback.action) {
        "play", "sync_state" -> {
            val current = viewModel.currentPosition.value.toLong()
            if (abs(current - playback.positionMs) > 1_500L) {
                viewModel.seekTo(playback.positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            }
            if (playback.isPlaying) viewModel.playAudio() else viewModel.pauseAudio()
        }
        "pause" -> {
            val current = viewModel.currentPosition.value.toLong()
            if (abs(current - playback.positionMs) > 450L) {
                viewModel.seekTo(playback.positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            }
            viewModel.pauseAudio()
        }
        "seek" -> viewModel.seekTo(playback.positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }
}
