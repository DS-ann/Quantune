package com.dsann.music.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.dsann.music.R
import com.dsann.music.data.db.HistoryEventEntity
import com.dsann.music.data.db.PlaylistDao
import com.dsann.music.data.db.PlaylistEntity
import com.dsann.music.data.collaboration.DsmusicListenTogetherSession
import com.dsann.music.data.innertube.AccountManager
import com.dsann.music.data.innertube.InnerTubeClient
import com.dsann.music.data.innertube.SearchFilter
import com.dsann.music.data.innertube.SearchItem
import com.dsann.music.data.innertube.YouTubeMusic
import com.dsann.music.data.innertube.path
import com.dsann.music.model.PlayerGestureAction
import com.dsann.music.model.PlayerGestureSettings
import com.dsann.music.model.SongItem
import com.dsann.music.model.toPlaylistSongEntity
import com.dsann.music.service.AudioPlaybackService
import com.dsann.music.util.FavoritesManager
import com.dsann.music.util.PlaybackStateManager
import com.dsann.music.util.SongDownloader
import com.dsann.music.viewmodel.lyrics.PlayerLyricsStateController
import com.google.gson.JsonElement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.Locale
import java.util.Random
import javax.inject.Inject
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/**
 * Premium Kotlin-first ViewModel for D.S Music.
 * Migrated from legacy Java/LiveData architecture to StateFlow + Coroutines.
 * Optimized for Material 3 Expressive and Media3 integration.
 */
data class VideoPlaybackMetadata(
    val title: String,
    val uploader: String,
    val durationMs: Long = 0L,
    val contentLengthBytes: Long? = null,
    val bitrateBps: Long? = null,
    val resolution: String? = null,
    val mimeType: String? = null,
    val description: String? = null,
    val viewCount: Long? = null,
    val likeCount: Long? = null,
    val uploadDate: String? = null,
    val thumbnailUrl: String? = null,
    val uploaderUrl: String? = null
)

@HiltViewModel
class PlayerSharedViewModel @Inject constructor(
    private val application: Application,
    private val playlistDao: PlaylistDao
) : AndroidViewModel(application) {
    
    val bgScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    fun cleanupBgScope() {
        bgScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    // Local custom playlists backing flow (OuterTune style)
    val localPlaylistsFlow = playlistDao.getAllPlaylists().stateIn(
        bgScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // ========== PERSONAL LISTENING ANALYTICS & RECAP MODELS ==========

    data class TopSongStat(
        val songId: String,
        val title: String,
        val artist: String,
        val thumbnailUrl: String?,
        val count: Int
    )

    data class TopArtistStat(
        val artist: String,
        val count: Int,
        val thumbnailUrl: String?
    )

    data class ListeningRecap(
        val totalPlayCount: Int,
        val totalMinutes: Long,
        val topSongs: List<TopSongStat>,
        val topArtists: List<TopArtistStat>,
        val peakMood: String,
        val peakMoodDescription: String
    )

    // Raw chronological history powers the period-based Replay tab. The recap
    // aggregate remains available for lightweight summary consumers.
    val listeningHistoryEventsFlow = playlistDao.getAllHistoryEventsFlow().stateIn(
        bgScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Listening Recap is unavailable through Android 10. Do not subscribe to history
    // or aggregate the entire event table on those runtimes; the Replay history flow above
    // remains separate and continues to serve its own feature.
    val listeningRecapFlow = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
        flowOf<ListeningRecap?>(null).stateIn(
            bgScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )
    } else {
        playlistDao.getAllHistoryEventsFlow().map { events ->
            if (events.isEmpty()) return@map null

            val totalPlayCount = events.size
            val totalMs = events.sumOf { it.duration }
            val totalMinutes = totalMs / 1000 / 60

            val topSongs = events.groupBy { it.songId }
                .map { (songId, list) ->
                    val first = list.first()
                    TopSongStat(
                        songId = songId,
                        title = first.title,
                        artist = first.artist,
                        thumbnailUrl = first.thumbnailUrl,
                        count = list.size
                    )
                }
                .sortedByDescending { it.count }
                .take(10)

            val topArtists = events.groupBy { it.artist }
                .map { (artist, list) ->
                    TopArtistStat(
                        artist = artist,
                        count = list.size,
                        thumbnailUrl = list.firstOrNull { !it.thumbnailUrl.isNullOrEmpty() }?.thumbnailUrl
                    )
                }
                .sortedByDescending { it.count }
                .take(5)

            val hourCounts = events.groupBy { it.hourOfDay }.mapValues { it.value.size }
            val peakHour = hourCounts.maxByOrNull { it.value }?.key ?: 12

            val (peakMood, peakMoodDescription) = when (peakHour) {
                in 5..11 -> "Morning Spark" to "You find your energy in morning melodies."
                in 12..16 -> "Afternoon Groove" to "Midday rhythms keep you focused and moving."
                in 17..21 -> "Evening Harmony" to "Winding down with perfect sunset soundtracks."
                else -> "Midnight Mystique" to "Late night is when your true music flow awakens."
            }

            ListeningRecap(
                totalPlayCount = totalPlayCount,
                totalMinutes = totalMinutes,
                topSongs = topSongs,
                topArtists = topArtists,
                peakMood = peakMood,
                peakMoodDescription = peakMoodDescription
            )
        }.flowOn(Dispatchers.Default).stateIn(
            bgScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )
    }

    val artistProfileMap = mutableStateMapOf<String, String>()

    fun resolveArtistProfileImage(artistName: String) {
        if (artistProfileMap.containsKey(artistName)) return
        bgScope.launch(Dispatchers.IO) {
            try {
                val result = YouTubeMusic.search(artistName, SearchFilter.ARTISTS)
                result.onSuccess { searchResult ->
                    val artistItem = searchResult.items.firstOrNull { it is SearchItem.Artist } as? SearchItem.Artist
                    val url = artistItem?.artist?.thumbnailUrl
                    if (!url.isNullOrEmpty()) {
                        withContext(Dispatchers.Main) {
                            artistProfileMap[artistName] = url
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PlayerSharedViewModel", "Failed to fetch artist profile image for $artistName: ${e.message}")
            }
        }
    }

    private var trackHistoryJob: Job? = null

    private fun trackHistoryForSong(song: SongItem) {
        trackHistoryJob?.cancel()
        trackHistoryJob = bgScope.launch(Dispatchers.IO) {
            val prefs = appContext.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            if (prefs.getBoolean("pause_history", false)) {
                Log.d("PlayerSharedViewModel", "History tracking is paused, skipping local history logging for: ${song.title}")
                return@launch
            }

            var accumulatedMs = 0L
            var lastPersistedMs = 0L
            var insertedId: Long? = null
            var activeSegmentStartMs: Long? = null

            suspend fun persistHistoryProgress() {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val historyEvent = HistoryEventEntity(
                    eventId = insertedId ?: 0L,
                    songId = song.videoId ?: song.id.toString(),
                    title = song.title,
                    artist = song.artist,
                    duration = accumulatedMs,
                    thumbnailUrl = song.thumbnailUrl ?: song.path,
                    timestamp = System.currentTimeMillis(),
                    hourOfDay = hour
                )
                val rowId = playlistDao.insertHistoryEvent(historyEvent)
                if (insertedId == null) {
                    insertedId = rowId
                    Log.d("PlayerSharedViewModel", "Logged listening history for: ${song.title}")
                }
                lastPersistedMs = accumulatedMs
            }

            try {
                while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                    // StateFlow suspends while paused instead of waking an IO worker every
                    // second just to discover that playback has not resumed.
                    if (!_isPlaying.value) {
                        _isPlaying.first { it }
                    }

                    val segmentStartMs = android.os.SystemClock.elapsedRealtime()
                    activeSegmentStartMs = segmentStartMs
                    // Create the first history row after 2s; update it only every 15s after
                    // that. A pause interrupts this wait immediately so short sessions flush.
                    val checkpointDelayMs = if (insertedId == null) {
                        2_000L
                    } else {
                        HISTORY_PROGRESS_PERSIST_INTERVAL_MS
                    }
                    val pausedBeforeCheckpoint = withTimeoutOrNull(checkpointDelayMs) {
                        _isPlaying.first { !it }
                        true
                    } == true

                    val segmentDurationMs = (
                        android.os.SystemClock.elapsedRealtime() - segmentStartMs
                    ).coerceAtLeast(0L)
                    activeSegmentStartMs = null
                    accumulatedMs += segmentDurationMs

                    val firstCheckpoint = insertedId == null && accumulatedMs >= 2_000L
                    val periodicCheckpoint = insertedId != null &&
                        accumulatedMs - lastPersistedMs >= HISTORY_PROGRESS_PERSIST_INTERVAL_MS
                    // Preserve the old immediate-pause checkpoint behavior without polling.
                    val pauseCheckpoint = (pausedBeforeCheckpoint || !_isPlaying.value) &&
                        accumulatedMs >= 2_000L && accumulatedMs > lastPersistedMs

                    if (firstCheckpoint || periodicCheckpoint || pauseCheckpoint) {
                        persistHistoryProgress()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("PlayerSharedViewModel", "Error logging history: ${error.message}", error)
            } finally {
                // Include the in-flight playback segment if a song change cancels the job
                // before its next checkpoint, then flush the final listening duration.
                activeSegmentStartMs?.let { startedAt ->
                    accumulatedMs += (
                        android.os.SystemClock.elapsedRealtime() - startedAt
                    ).coerceAtLeast(0L)
                    activeSegmentStartMs = null
                }
                if (accumulatedMs >= 2_000L && accumulatedMs > lastPersistedMs) {
                    withContext(NonCancellable) {
                        runCatching { persistHistoryProgress() }
                            .onFailure { error ->
                                Log.e("PlayerSharedViewModel", "Failed to flush final listening history: ${error.message}", error)
                            }
                    }
                }
            }
        }
    }

    fun createLocalPlaylist(title: String) {
        bgScope.launch(Dispatchers.IO) {
            playlistDao.insertPlaylist(PlaylistEntity(title = title))
        }
    }

    suspend fun markLocalPlaylistUploaded(playlistId: Long, youtubePlaylistId: String) {
        withContext(Dispatchers.IO) {
            val playlist = playlistDao.getPlaylistById(playlistId)
                ?: throw IllegalStateException("The local playlist no longer exists.")
            playlistDao.updatePlaylist(playlist.copy(youtubePlaylistId = youtubePlaylistId))
        }
    }

    fun deleteLocalPlaylist(playlistId: Long) {
        bgScope.launch(Dispatchers.IO) {
            val playlist = playlistDao.getPlaylistById(playlistId)
            if (playlist != null) {
                playlistDao.deletePlaylist(playlist)
                playlistDao.deleteAllSongsInPlaylist(playlistId)
            }
        }
    }

    fun addSongToLocalPlaylist(playlistId: Long, song: SongItem) {
        bgScope.launch(Dispatchers.IO) {
            val count = playlistDao.getSongCountForPlaylistSync(playlistId)
            val entity = song.toPlaylistSongEntity(playlistId, count)
            playlistDao.insertPlaylistSong(entity)
        }
    }

    fun addSongsToLocalPlaylist(playlistId: Long, songs: List<SongItem>) {
        if (songs.isEmpty()) return
        bgScope.launch(Dispatchers.IO) {
            var position = playlistDao.getSongCountForPlaylistSync(playlistId)
            songs.forEach { song ->
                playlistDao.insertPlaylistSong(song.toPlaylistSongEntity(playlistId, position))
                position += 1
            }
        }
    }

    fun removeSongFromLocalPlaylist(playlistId: Long, songId: String) {
        bgScope.launch(Dispatchers.IO) {
            playlistDao.deletePlaylistSong(playlistId, songId)
        }
    }

    fun getSongsForLocalPlaylist(playlistId: Long) = playlistDao.getSongsForPlaylist(playlistId)

    private val appContext: Context get() = application.applicationContext

    companion object {
        private const val TAG = "PlayerSharedViewModel"
        
        const val REPEAT_OFF = 0
        const val REPEAT_ONCE = 1
        const val REPEAT_INFINITY = 2
        private const val HISTORY_PROGRESS_PERSIST_INTERVAL_MS = 15_000L
        private const val RAPID_NAVIGATION_SETTLE_MS = 220L
    }

    private val shuffleRandom = Random()
    private var favoritesManager: FavoritesManager? = null
    @SuppressLint("StaticFieldLeak")
    var audioService: AudioPlaybackService? = null
        set(value) {
            field = value
            _audioServiceState.value = value
            val service = value ?: return
            service.setViewModel(this)

            // Service binding can complete before its asynchronous cache/player initialization.
            // Never dereference service.player until the service announces readiness.
            service.runWhenPlayerReady {
                val activeMediaItem = service.player.currentMediaItem
                if (activeMediaItem != null) {
                    val activeSongId = activeMediaItem.mediaId.toLongOrNull()
                    if (activeSongId != null) {
                        val matchedSong = findSongById(activeSongId)
                        if (matchedSong != null && _currentSong.value != matchedSong) {
                            Log.d(TAG, "Service is already playing song: ${matchedSong.title}. Restoring view model state.")
                            _currentSongIndex.value = findSongIndexById(activeSongId)
                            _currentSong.value = matchedSong
                            _songUri.value = matchedSong.contentUri
                            _isPlaybackReady.value = true
                        }
                    }
                } else {
                    // Cold-start warm preloading: when the service connects, immediately
                    // load the restored playback song so pressing Play works instantly.
                    val restoredSong = _currentSong.value
                    val restoredUri = _songUri.value
                    val restoredPos = _currentPosition.value.toLong()
                    if (restoredSong != null && restoredUri != null) {
                        if (service.getCurrentSourceUri() != restoredUri) {
                            Log.d(TAG, "Cold-start pre-loading restored song: ${restoredSong.title} at pos $restoredPos")
                            service.loadAudio(restoredUri, restoredPos)
                        } else {
                            Log.d(TAG, "Skipping cold-start pre-loading: song is already active in service")
                        }
                    }
                }
            }
        }

    fun restoreStateFrom(oldVm: PlayerSharedViewModel) {
        Log.d(TAG, "Restoring UI state from old ViewModel instance to prevent memory leak and state wipe.")
        _songList.value = oldVm._songList.value
        _localSongs.value = oldVm._localSongs.value
        _isVideoMode.value = oldVm._isVideoMode.value
        _currentSongIndex.value = oldVm._currentSongIndex.value
        _currentSong.value = oldVm._currentSong.value
        _songUri.value = oldVm._songUri.value
        _isPlaying.value = oldVm._isPlaying.value
        _isLoading.value = oldVm._isLoading.value
        _currentPosition.value = oldVm._currentPosition.value
        _duration.value = oldVm._duration.value
        _isShuffleEnabled.value = oldVm._isShuffleEnabled.value
        _repeatMode.value = oldVm._repeatMode.value
        repeatOnceQueueOrder = oldVm.repeatOnceQueueOrder
        repeatOncePosition = oldVm.repeatOncePosition
        repeatOncePass = oldVm.repeatOncePass
        _isPlayerExpanded.value = oldVm._isPlayerExpanded.value
        _sleepTimerEndTime.value = oldVm._sleepTimerEndTime.value
        _sleepTimerMode.value = oldVm._sleepTimerMode.value
        lyricsController.setLyricsModeEnabled(oldVm.lyricsController.isLyricsModeEnabled.value)
        
        // Preserve any active sleep timer jobs
        this.sleepTimerJob = oldVm.sleepTimerJob
        
        // Ensure UI stays in sync with ExoPlayer if active
        _isPlaybackReady.value = oldVm._isPlaybackReady.value
    }

    // ========== STATEFLOW DEFINITIONS ==========

    private val _songUri = MutableStateFlow<Uri?>(null)
    val songUri: StateFlow<Uri?> = _songUri.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    fun setLoading(loading: Boolean) { _isLoading.value = loading }

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    // Song Library Management
    private val _songList = MutableStateFlow<List<SongItem>>(emptyList())
    val songList: StateFlow<List<SongItem>> = _songList.asStateFlow()

    private val _isQueueExpanded = MutableStateFlow(false)
    val isQueueExpanded: StateFlow<Boolean> = _isQueueExpanded.asStateFlow()
    fun setQueueExpanded(expanded: Boolean) {
        _isQueueExpanded.value = expanded
    }

    private val _isVideoMode = MutableStateFlow(false)
    val isVideoMode: StateFlow<Boolean> = _isVideoMode.asStateFlow()

    private val _videoPlaybackMetadata = MutableStateFlow<VideoPlaybackMetadata?>(null)
    val videoPlaybackMetadata: StateFlow<VideoPlaybackMetadata?> = _videoPlaybackMetadata.asStateFlow()

    fun setVideoModeState(enabled: Boolean) {
        _isVideoMode.value = enabled
        if (!enabled) _videoPlaybackMetadata.value = null
    }

    fun setVideoPlaybackMetadata(metadata: VideoPlaybackMetadata?) {
        _videoPlaybackMetadata.value = metadata
    }

    fun isAutoplayEnabled(): Boolean = application
        .getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        .getBoolean("autoplay_enabled", true)

    // ── Player gestures ─────────────────────────────────────────────────────
    private val gesturePreferences = application.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
    private val _playerGestureSettings = MutableStateFlow(
        PlayerGestureSettings.fromPreferences(gesturePreferences)
    )
    val playerGestureSettings: StateFlow<PlayerGestureSettings> = _playerGestureSettings.asStateFlow()

    fun setPlayerGesture(key: String, action: PlayerGestureAction) {
        val allowed = setOf(
            "cover_swipe_left", "cover_swipe_right", "cover_swipe_up",
            "cover_double_tap", "cover_long_press",
            "mini_swipe_left", "mini_swipe_right", "mini_swipe_down"
        )
        if (key !in allowed) return
        gesturePreferences.edit { putString(key, action.name) }
        _playerGestureSettings.value = PlayerGestureSettings.fromPreferences(gesturePreferences)
    }

    fun setDoubleTapSeekSeconds(seconds: Int) {
        gesturePreferences.edit { putInt("double_tap_seek_seconds", seconds.coerceIn(3, 30)) }
        _playerGestureSettings.value = PlayerGestureSettings.fromPreferences(gesturePreferences)
    }

    /** Refresh gesture state after Settings imports a backup. */
    fun refreshPlayerGestureSettings() {
        _playerGestureSettings.value = PlayerGestureSettings.fromPreferences(gesturePreferences)
    }

    // Autoplay keeps a 20-track buffer ahead of the current song. Each refill is
    // anchored to the last queued online track so later batches continue the radio chain.
    private val AUTOPLAY_REFILL_THRESHOLD = 20
    private val AUTOPLAY_REFILL_BATCH_SIZE = 20

    /**
     * Returns null only when the queue has no online-backed track to seed autoplay.
     * Cached/downloaded songs retain their videoId as source metadata while their
     * offline flags and contentUri continue to determine how they are played.
     */
    fun getAutoplaySeedVideoId(): String? =
        _songList.value.lastOrNull { !it.videoId.isNullOrBlank() }?.videoId

    private var autoplayFetchJob: Job? = null
    private var autoplayQueueGeneration = 0L
    @Volatile private var listenTogetherQueueAuthoritative = false
    private val isListenTogetherGuest: Boolean
        get() = DsmusicListenTogetherSession.client.role.value == "GUEST"
    private var listenTogetherPreviousShuffle: Boolean? = null
    private var listenTogetherPreviousRepeatMode: Int? = null
    private var autoplayFetchGeneration = -1L
    private val autoplayFetchedVideoIds = mutableSetOf<String>()
    private var pendingRapidSkipLoadJob: Job? = null
    // During a burst of Next/Previous presses, keep only the intended song ID here.
    // UI, lyrics, persistence, metadata and stream resolution are updated once the burst settles.
    private var pendingRapidNavigationJob: Job? = null
    private var pendingRapidNavigationSongId: Long? = null
    private var pendingRapidNavigationPreserveRepeatOnce = false
    private var nextSongPrefetchJob: Job? = null

    /**
     * Add another batch before the queue runs out. The current song is excluded from the
     * remaining count. Shuffle can select any other queued song, so all non-current entries
     * count as available; repeat modes do not need automatic queue extension.
     */
    fun maybeRefillAutoplayQueue() {
        if (listenTogetherQueueAuthoritative || !isAutoplayEnabled() || _repeatMode.value != REPEAT_OFF) return
        val songs = _songList.value
        val currentSong = _currentSong.value ?: return
        val currentIndex = songs.indexOfFirst { it.id == currentSong.id }
        if (currentIndex !in songs.indices) return

        val remaining = if (_isShuffleEnabled.value) {
            (songs.size - 1).coerceAtLeast(0)
        } else {
            (songs.size - currentIndex - 1).coerceAtLeast(0)
        }
        if (remaining >= AUTOPLAY_REFILL_THRESHOLD) return

        // Start recommendations from the end of the existing queue, not the currently
        // playing item. This avoids re-requesting the same radio list on every refill.
        val seedVideoId = getAutoplaySeedVideoId() ?: return
        prefetchAutoplayRecommendations(seedVideoId, AUTOPLAY_REFILL_BATCH_SIZE)
    }

    // A single request at a time prevents multiple transitions from issuing duplicate
    // recommendation requests while the queue is being refilled.
    fun prefetchAutoplayRecommendations(
        videoId: String,
        batchSize: Int = AUTOPLAY_REFILL_BATCH_SIZE
    ): Job? {
        if (videoId.isBlank() || !isAutoplayEnabled()) return null
        autoplayFetchJob?.takeIf { it.isActive }?.let { activeJob ->
            if (autoplayFetchGeneration == autoplayQueueGeneration) return activeJob
            activeJob.cancel()
        }
        if (videoId in autoplayFetchedVideoIds) return null
        if (_songList.value.none { it.videoId == videoId }) return null

        val requestGeneration = autoplayQueueGeneration
        lateinit var requestJob: Job
        requestJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                if (!isAutoplayEnabled() || requestGeneration != autoplayQueueGeneration) return@launch

                // Continue with a generated radio queue first. If it contains fewer than
                // the requested batch, merge related tracks as a fallback to reach 20.
                val radioResult = YouTubeMusic.startRadio(videoId)
                var candidates = radioResult.getOrDefault(emptyList())
                var requestSucceeded = radioResult.isSuccess

                val existingIds = _songList.value.mapNotNull { it.videoId }.toMutableSet()
                var fresh = candidates.filter {
                    it.videoId.isNotBlank() && it.videoId !in existingIds
                }.distinctBy { it.videoId }

                if (fresh.size < batchSize) {
                    val relatedResult = YouTubeMusic.relatedSongs(videoId)
                    candidates = candidates + relatedResult.getOrDefault(emptyList())
                    requestSucceeded = requestSucceeded || relatedResult.isSuccess
                    fresh = candidates.filter {
                        it.videoId.isNotBlank() && it.videoId !in existingIds
                    }.distinctBy { it.videoId }
                }

                if (!isAutoplayEnabled() || requestGeneration != autoplayQueueGeneration) return@launch

                // Do not append recommendations for a queue item that was removed while
                // the network request was running (for example, after the user cleared it).
                val latest = _songList.value
                if (latest.none { it.videoId == videoId }) return@launch

                val additions = fresh.take(batchSize).map { onlineSong ->
                    SongItem.createOnlineSong(
                        onlineSong.videoId,
                        onlineSong.title,
                        onlineSong.artist,
                        "",
                        onlineSong.durationMs,
                        onlineSong.thumbnailUrl,
                        onlineSong.artistId,
                        onlineSong.albumName
                    ).apply { isAutoplayRecommendation = true }
                }
                val latestIds = latest.mapNotNull { it.videoId }.toSet()
                val uniqueAdditions = additions.filter { it.videoId !in latestIds }
                if (uniqueAdditions.isNotEmpty()) {
                    _songList.value = latest + uniqueAdditions
                    audioService?.refreshNativeQueue()
                    Log.d(
                        TAG,
                        "Autoplay queued ${uniqueAdditions.size} new recommendations for $videoId"
                    )
                } else {
                    Log.w(TAG, "Autoplay returned no new songs for $videoId")
                }

                // Remember completed attempts (including valid empty responses) to avoid
                // repeatedly fetching the same radio seed on every queue refresh.
                if (requestSucceeded) autoplayFetchedVideoIds += videoId
            } catch (error: Exception) {
                Log.w(TAG, "Autoplay recommendation request failed for $videoId", error)
            } finally {
                if (autoplayFetchJob === requestJob) {
                    autoplayFetchJob = null
                    autoplayFetchGeneration = -1L
                }
            }
        }
        autoplayFetchGeneration = requestGeneration
        autoplayFetchJob = requestJob
        requestJob.start()
        return requestJob
    }

    /** Remove unplayed recommendations inserted automatically, keeping the current item and manual queue. */
    fun removeAutoplayRecommendationsFromQueue() {
        val songs = _songList.value
        val currentIndex = _currentSongIndex.value
        if (songs.isEmpty() || currentIndex !in songs.indices) return
        val updated = songs.filterIndexed { index, song ->
            index <= currentIndex || !song.isAutoplayRecommendation
        }
        if (updated.size != songs.size) {
            _songList.value = updated
            if (_repeatMode.value == REPEAT_ONCE) resetRepeatOnceCycleFromCurrent()
            audioService?.refreshNativeQueue()
        }
    }

    private val _localSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val localSongs: StateFlow<List<SongItem>> = _localSongs.asStateFlow()

    // Dynamic blend color extracted from album art — drives the activity-level mini player background
    private val _miniPlayerBlendColor = MutableStateFlow(0)
    val miniPlayerBlendColor: StateFlow<Int> = _miniPlayerBlendColor.asStateFlow()

    private val _bottomNavTranslationY = MutableStateFlow(0f)
    val bottomNavTranslationY: StateFlow<Float> = _bottomNavTranslationY.asStateFlow()
    fun setBottomNavTranslationY(translation: Float) {
        _bottomNavTranslationY.value = translation
    }

    private val _bottomNavHeight = MutableStateFlow(0f)
    val bottomNavHeight: StateFlow<Float> = _bottomNavHeight.asStateFlow()
    fun setBottomNavHeight(height: Float) {
        _bottomNavHeight.value = height
    }

    private val _playerBackgroundColor = MutableStateFlow(0xFF0F0F0F.toInt())
    val playerBackgroundColor: StateFlow<Int> = _playerBackgroundColor.asStateFlow()

    private val _currentSongIndex = MutableStateFlow(-1)
    val currentSongIndex: StateFlow<Int> = _currentSongIndex.asStateFlow()

    private val _currentSong = MutableStateFlow<SongItem?>(null)
    val currentSong: StateFlow<SongItem?> = _currentSong.asStateFlow()

    private val _currentSongArtwork = MutableStateFlow<ByteArray?>(null)
    val currentSongArtwork: StateFlow<ByteArray?> = _currentSongArtwork.asStateFlow()

    // Lyrics State (Delegated to Controller)
    val lyricsController = PlayerLyricsStateController(TAG)

    // Shuffle & Repeat
    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(REPEAT_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    // Repeat Once plays the current queue order twice, then stops. Keep a stable
    // order snapshot so shuffle still visits every queued track exactly once per pass.
    private var repeatOnceQueueOrder: List<Long> = emptyList()
    private var repeatOncePosition: Int = 0
    private var repeatOncePass: Int = 0

    private val _favoriteSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteSongIds: StateFlow<Set<Long>> = _favoriteSongIds.asStateFlow()

    // Favorites
    private val _isCurrentSongFavorite = MutableStateFlow(false)
    val isCurrentSongFavorite: StateFlow<Boolean> = _isCurrentSongFavorite.asStateFlow()

    private val _isCurrentSongDisliked = MutableStateFlow(false)
    val isCurrentSongDisliked: StateFlow<Boolean> = _isCurrentSongDisliked.asStateFlow()

    // Service Reference
    private val _audioServiceState = MutableStateFlow<AudioPlaybackService?>(null)
    val audioServiceState: StateFlow<AudioPlaybackService?> = _audioServiceState.asStateFlow()

    private val _likesCount = MutableStateFlow("Like")
    val likesCount: StateFlow<String> = _likesCount.asStateFlow()
    val likesCountFlow get() = likesCount

    private val _showSignInDialog = MutableStateFlow(false)
    val showSignInDialog: StateFlow<Boolean> = _showSignInDialog.asStateFlow()

    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded.asStateFlow()

    // The Activity-level BackHandler has priority over nested Settings NavHosts.
    // It sends a one-shot request here so the sheet itself can run its normal collapse animation.
    private val _playerBackCollapseRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val playerBackCollapseRequests = _playerBackCollapseRequests.asSharedFlow()

    fun requestPlayerCollapseFromBack() {
        _playerBackCollapseRequests.tryEmit(Unit)
    }

    fun setPlayerExpanded(expanded: Boolean) {
        _isPlayerExpanded.value = expanded
    }

    private val _playerExpansionFraction = MutableStateFlow(0f)
    val playerExpansionFraction: StateFlow<Float> = _playerExpansionFraction.asStateFlow()

    // Read only from graphicsLayer draw lambdas so the navigation dock can follow
    // player expansion without recomposing the retained tabs on every frame.
    private val _playerExpansionProgress = mutableFloatStateOf(0f)
    val playerExpansionProgress: Float
        get() = _playerExpansionProgress.floatValue

    fun setPlayerExpansionProgress(fraction: Float) {
        _playerExpansionProgress.floatValue = fraction.coerceIn(0f, 1f)
    }

    fun setPlayerExpansionFraction(fraction: Float) {
        _playerExpansionFraction.value = fraction
        setPlayerExpansionProgress(fraction)
    }

    private val _isPlaybackReady = MutableStateFlow(false)
    val isPlaybackReady: StateFlow<Boolean> = _isPlaybackReady.asStateFlow()

    fun setPlaybackReady(ready: Boolean) {
        _isPlaybackReady.value = ready
    }

    fun dismissSignInDialog() {
        _showSignInDialog.value = false
    }

    // ========== SLEEP TIMER SYSTEM ==========
    enum class SleepTimerMode {
        OFF,
        END_OF_SONG,
        END_OF_QUEUE,
        CUSTOM
    }

    private val _sleepTimerMode = MutableStateFlow(SleepTimerMode.OFF)
    val sleepTimerMode: StateFlow<SleepTimerMode> = _sleepTimerMode.asStateFlow()

    private val _sleepTimerEndTime = MutableStateFlow(0L)
    val sleepTimerEndTime: StateFlow<Long> = _sleepTimerEndTime.asStateFlow()

    private var sleepTimerJob: Job? = null

    fun setSleepTimerMode(mode: SleepTimerMode) {
        _sleepTimerMode.value = mode
        if (mode != SleepTimerMode.CUSTOM) {
            sleepTimerJob?.cancel()
            sleepTimerJob = null
            _sleepTimerEndTime.value = 0L
        }
    }

    fun startCustomSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerMode.value = SleepTimerMode.CUSTOM
        val endTime = System.currentTimeMillis() + (minutes * 60 * 1000L)
        _sleepTimerEndTime.value = endTime

        sleepTimerJob = bgScope.launch(Dispatchers.Main) {
            // One deadline timer is sufficient; waking the Main dispatcher every second
            // does not update any UI state and can compete with frame work on API 23.
            delay((endTime - System.currentTimeMillis()).coerceAtLeast(0L))
            if (_sleepTimerMode.value == SleepTimerMode.CUSTOM) {
                pauseAudio()
                setSleepTimerMode(SleepTimerMode.OFF)
            }
        }
    }

    fun cancelSleepTimer() {
        setSleepTimerMode(SleepTimerMode.OFF)
    }

    // Dynamic likes count tracking
    private var baseLikesCountInt = 0

    private fun parseLikesCount(likesStr: String): Int {
        var str = likesStr.uppercase().trim().replace(",", "").replace(".", "")
        // Bail early on known non-numeric YouTube strings
        if (str.isBlank() || str == "LIKE" || str == "LIKED" ||
            str.contains("NO LIKE") || str.contains("HIDDEN") ||
            !str.any(Char::isDigit)) return 0

        var multiplier = 1
        when {
            str.endsWith("B") -> { multiplier = 1_000_000_000; str = str.removeSuffix("B") }
            str.endsWith("M") -> { multiplier = 1_000_000;     str = str.removeSuffix("M") }
            str.endsWith("K") -> { multiplier = 1_000;         str = str.removeSuffix("K") }
        }
        return ((str.toDoubleOrNull() ?: return 0) * multiplier).toInt()
    }

    private fun formatLikesCount(count: Int): String {
        if (count <= 0) return "Like"
        if (count < 10_000) { // Under 10,000 likes, show the exact count with commas for interactive increment visibility (e.g. 1,234 -> 1,235)
            return String.format(Locale.US, "%,d", count)
        }
        if (count < 1_000_000) {
            val k = count / 1_000.0
            return String.format(Locale.US, "%.1fK", k).replace(".0K", "K")
        }
        val m = count / 1_000_000.0
        return String.format(Locale.US, "%.1fM", m).replace(".0M", "M")
    }

    private fun updateLikesCountDisplay(userLiked: Boolean) {
        if (baseLikesCountInt <= 0) {
            _likesCount.value = if (userLiked) "1" else "Like"
            return
        }
        val targetCount = if (userLiked) baseLikesCountInt + 1 else baseLikesCountInt
        _likesCount.value = formatLikesCount(targetCount)
    }

    private var engagementJob: Job? = null

    private fun fetchEngagementAndHistoryForSong(song: SongItem) {
        if (song.videoId.isNullOrEmpty()) {
            baseLikesCountInt = 0
            _likesCount.value = "Like"
            return
        }

        engagementJob?.cancel()
        engagementJob = bgScope.launch(Dispatchers.IO) {
            try {
                // Initialize default state
                withContext(Dispatchers.Main) {
                    _likesCount.value = "Like"
                }
                baseLikesCountInt = 0

                // Fetch player info for likes count
                val playerResult = YouTubeMusic.player(song.videoId).getOrNull()

                // Fallback Chain for Real Likes Count
                var likesCountStr = playerResult?.likesCount
                var remoteLikeStatus: Boolean?

                // Always try nextYoutubeWeb endpoint (highly reliable for authenticated like status and guest likes)
                try {
                    val nextYoutubeWebJson = InnerTubeClient.nextYoutubeWeb(song.videoId)
                    remoteLikeStatus = parseLikeStatusFromNextResponse(nextYoutubeWebJson)
                    if (likesCountStr.isNullOrBlank()) {
                        likesCountStr = parseLikesFromNextResponse(nextYoutubeWebJson)
                        if (!likesCountStr.isNullOrBlank()) {
                            Log.d("LikesFix", "Count resolved from nextYoutubeWeb: $likesCountStr")
                        }
                    }
                    if (remoteLikeStatus != null) {
                        Log.d("LikesFix", "Remote like status resolved: $remoteLikeStatus")
                        withContext(Dispatchers.Main) {
                            favoritesManager?.setFavorite(song.id, remoteLikeStatus)
                            _isCurrentSongFavorite.value = remoteLikeStatus
                        }
                    }
                } catch (e: Exception) {
                    Log.w("LikesFix", "nextYoutubeWeb failed: ${e.message}")
                }

                // Fallback 2: Try nextAndroid endpoint (extremely reliable for mobile engagement/exact likes)
                if (likesCountStr.isNullOrBlank()) {
                    try {
                        val nextAndroidJson = InnerTubeClient.nextAndroid(song.videoId)
                        likesCountStr = parseLikesFromNextResponse(nextAndroidJson)
                        if (!likesCountStr.isNullOrBlank()) {
                            Log.d("LikesFix", "Count resolved from nextAndroid: $likesCountStr")
                        }
                    } catch (e: Exception) {
                        Log.w("LikesFix", "nextAndroid failed: ${e.message}")
                    }
                }

                // Fallback 3: Try next web remix endpoint
                if (likesCountStr.isNullOrBlank()) {
                    try {
                        val nextJson = InnerTubeClient.next(song.videoId)
                        likesCountStr = parseLikesFromNextResponse(nextJson)
                        if (!likesCountStr.isNullOrBlank()) {
                            Log.d("LikesFix", "Count resolved from next(WEB_REMIX): $likesCountStr")
                        }
                    } catch (e: Exception) {
                        Log.w("LikesFix", "next(WEB_REMIX) failed: ${e.message}")
                    }
                }

                if (likesCountStr.isNullOrBlank()) {
                    Log.e("LikesFix", "All tiers failed — no likes count found for videoId=${song.videoId}")
                }

                likesCountStr?.let { count ->
                    baseLikesCountInt = parseLikesCount(count)
                    // Post to Main thread safely to update display Flow
                    withContext(Dispatchers.Main) {
                        updateLikesCountDisplay(_isCurrentSongFavorite.value)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to report playback start or fetch engagement: ${e.message}")
            }
        }
    }

    private fun parseLikeStatusFromNextResponse(json: JsonElement): Boolean? {
        if (json.isJsonObject) {
            val obj = json.asJsonObject
            
            // ── CHECK 0b (NEW): segmentedLikeDislikeButtonViewModel ─────────────
            // Newer ViewComponent-based layout (YouTube 2025+)
            if (obj.has("segmentedLikeDislikeButtonViewModel")) {
                val vm = obj.getAsJsonObject("segmentedLikeDislikeButtonViewModel")
                val lbvm = vm.get("likeButtonViewModel")
                if (lbvm != null && lbvm.isJsonObject) {
                    val lbvmObj = lbvm.asJsonObject
                    // Check for nested toggleButtonViewModel configurations
                    lbvmObj.path("toggleButtonViewModel.toggleButtonViewModel.isToggled")?.asBoolean?.let { return it }
                    lbvmObj.path("toggleButtonViewModel.isToggled")?.asBoolean?.let { return it }
                }
            }

            if (obj.has("likeButtonViewModel")) {
                val lbvmObj = obj.getAsJsonObject("likeButtonViewModel")
                lbvmObj.path("toggleButtonViewModel.toggleButtonViewModel.isToggled")?.asBoolean?.let { return it }
                lbvmObj.path("toggleButtonViewModel.isToggled")?.asBoolean?.let { return it }
            }

            // Check for segmentedLikeDislikeButtonRenderer
            if (obj.has("segmentedLikeDislikeButtonRenderer")) {
                val seg = obj.getAsJsonObject("segmentedLikeDislikeButtonRenderer")
                val likeButtonEl = seg.get("likeButton")
                if (likeButtonEl != null && likeButtonEl.isJsonObject) {
                    val inner = likeButtonEl.asJsonObject
                    if (inner.has("toggleButtonRenderer")) {
                        val tbr = inner.getAsJsonObject("toggleButtonRenderer")
                        return tbr.get("isToggled")?.asBoolean ?: false
                    }
                    if (inner.has("likeButtonRenderer")) {
                        val lbr = inner.getAsJsonObject("likeButtonRenderer")
                        val status = lbr.get("likeStatus")?.asString
                        if (status != null) {
                            return status == "LIKE"
                        }
                    }
                }
            }
            
            // Check for toggleButtonRenderer representing LIKE icon
            if (obj.has("toggleButtonRenderer")) {
                val toggleRenderer = obj.getAsJsonObject("toggleButtonRenderer")
                val iconType = toggleRenderer.path("defaultIcon.iconType")?.asString 
                    ?: toggleRenderer.path("defaultIcon.icon.iconType")?.asString
                val isLikeButton = iconType == "LIKE" || iconType?.contains("THUMB", ignoreCase = true) == true || iconType?.contains("FAVORITE", ignoreCase = true) == true
                
                if (isLikeButton) {
                    return toggleRenderer.get("isToggled")?.asBoolean ?: false
                }
            }

            if (obj.has("likeButtonRenderer")) {
                val lbr = obj.getAsJsonObject("likeButtonRenderer")
                val status = lbr.get("likeStatus")?.asString
                if (status != null) {
                    return status == "LIKE"
                }
            }
            
            for (entry in obj.entrySet()) {
                val result = parseLikeStatusFromNextResponse(entry.value)
                if (result != null) return result
            }
        } else if (json.isJsonArray) {
            val arr = json.asJsonArray
            for (element in arr) {
                val result = parseLikeStatusFromNextResponse(element)
                if (result != null) return result
            }
        }
        return null
    }

    private fun parseLikesFromNextResponse(json: JsonElement): String? {
        if (json.isJsonObject) {
            val obj = json.asJsonObject

            // ── CHECK 0 (NEW): segmentedLikeDislikeButtonRenderer ──────────────
            // Current YouTube /next response format (2024-2025)
            if (obj.has("segmentedLikeDislikeButtonRenderer")) {
                val seg = obj.getAsJsonObject("segmentedLikeDislikeButtonRenderer")
                // Path: likeButton -> likeButtonRenderer -> likeCount / likesCount
                val likeButtonEl = seg.get("likeButton")
                if (likeButtonEl != null && likeButtonEl.isJsonObject) {
                    val inner = likeButtonEl.asJsonObject
                    if (inner.has("likeButtonRenderer")) {
                        val lbr = inner.getAsJsonObject("likeButtonRenderer")
                        val el = lbr.get("likeCount") ?: lbr.get("likesCount")
                        if (el != null && el.isJsonPrimitive) {
                            val s = el.asString
                            if (s.isNotBlank()) return s
                        }
                    }
                }
            }

            // ── CHECK 0b (NEW): segmentedLikeDislikeButtonViewModel ─────────────
            // Newer ViewComponent-based layout (YouTube 2025+)
            if (obj.has("segmentedLikeDislikeButtonViewModel")) {
                val vm = obj.getAsJsonObject("segmentedLikeDislikeButtonViewModel")
                // likeButtonViewModel -> likeCountEntity -> likeCountIfLiked / likeCountIfIndifferent
                val lbvm = vm.get("likeButtonViewModel")
                if (lbvm != null && lbvm.isJsonObject) {
                    val lbvmObj = lbvm.asJsonObject
                    // toggledText holds the "liked" count string
                    lbvmObj.path("toggleButtonViewModel.toggledText.styleText.style1.text.content")
                        ?.asString?.let { if (it.isNotBlank() && it.any(Char::isDigit)) return it }
                    // defaultText holds the unliked count string
                    lbvmObj.path("toggleButtonViewModel.defaultText.styleText.style1.text.content")
                        ?.asString?.let { if (it.isNotBlank() && it.any(Char::isDigit)) return it }
                }
            }
            
            // Check for factoidRenderer representing likes (Highly reliable standard YouTube engagement panel)
            if (obj.has("factoidRenderer")) {
                val factoid = obj.getAsJsonObject("factoidRenderer")
                val label = factoid.path("label.runs.0.text")?.asString 
                    ?: factoid.path("label.simpleText")?.asString
                val isLikesFactoid = label?.contains("like", ignoreCase = true) == true
                if (isLikesFactoid) {
                    factoid.path("value.simpleText")?.asString?.let { if (it.isNotBlank()) return it }
                    factoid.path("value.runs.0.text")?.asString?.let { if (it.isNotBlank()) return it }
                }
            }

            // Check for likeButtonRenderer
            if (obj.has("likeButtonRenderer")) {
                val likeRenderer = obj.getAsJsonObject("likeButtonRenderer")
                
                val likesCountEl = likeRenderer.get("likesCount") ?: likeRenderer.get("likeCount")
                if (likesCountEl != null) {
                    if (likesCountEl.isJsonPrimitive) {
                        val str = likesCountEl.asString
                        if (str.isNotBlank()) return str
                    } else if (likesCountEl.isJsonObject) {
                        val countObj = likesCountEl.asJsonObject
                        countObj.get("simpleText")?.asString?.let { if (it.isNotBlank()) return it }
                        countObj.getAsJsonArray("runs")?.firstOrNull()?.asJsonObject?.get("text")?.asString?.let {
                            if (it.isNotBlank()) return it
                        }
                    }
                }
                
                val likesText = likeRenderer.getAsJsonObject("likesCountText") ?: likeRenderer.getAsJsonObject("likeCountText")
                if (likesText != null) {
                    likesText.get("simpleText")?.asString?.let { if (it.isNotBlank()) return it }
                    likesText.getAsJsonArray("runs")?.firstOrNull()?.asJsonObject?.get("text")?.asString?.let {
                        if (it.isNotBlank()) return it
                    }
                }
            }
            
            // Check for toggleButtonRenderer representing LIKE icon
            if (obj.has("toggleButtonRenderer")) {
                val toggleRenderer = obj.getAsJsonObject("toggleButtonRenderer")
                val iconType = toggleRenderer.path("defaultIcon.iconType")?.asString 
                    ?: toggleRenderer.path("defaultIcon.icon.iconType")?.asString
                val isLikeButton = iconType == "LIKE" || iconType?.contains("THUMB", ignoreCase = true) == true || iconType?.contains("FAVORITE", ignoreCase = true) == true
                
                if (isLikeButton) {
                    toggleRenderer.path("defaultText.simpleText")?.asString?.let { if (it.isNotBlank()) return it }
                    toggleRenderer.path("defaultText.runs.0.text")?.asString?.let { if (it.isNotBlank()) return it }
                    toggleRenderer.path("toggledText.simpleText")?.asString?.let { if (it.isNotBlank()) return it }
                    toggleRenderer.path("toggledText.runs.0.text")?.asString?.let { if (it.isNotBlank()) return it }
                }
            }
            
            // Recurse down object entries
            for (entry in obj.entrySet()) {
                val result = parseLikesFromNextResponse(entry.value)
                if (result != null) return result
            }
        } else if (json.isJsonArray) {
            val arr = json.asJsonArray
            for (element in arr) {
                val result = parseLikesFromNextResponse(element)
                if (result != null) return result
            }
        }
        return null
    }

    private val _isCurrentSongDownloaded = MutableStateFlow(false)
    val isCurrentSongDownloaded: StateFlow<Boolean> = _isCurrentSongDownloaded.asStateFlow()

    private val _currentSongDownloadProgress = MutableStateFlow<Int?>(null)
    val currentSongDownloadProgress: StateFlow<Int?> = _currentSongDownloadProgress.asStateFlow()

    // ========== INITIALIZATION ==========

    init {
        initFavorites(appContext)
        loadLastPlaybackState()
        bgScope.launch(Dispatchers.Main) {
            currentSong.collect { song ->
                _likesCount.value = "Like"
                _isCurrentSongDisliked.value = false
                if (song != null) {
                    SongDownloader.refreshDownloadedStatus(appContext, song)
                    // Clear the previous track's image immediately. Cached-track artwork is
                    // loaded from D.S Music's persistent artwork store; downloaded-file artwork
                    // continues to come from the active file:// or content:// playback source.
                    _currentSongArtwork.value = null
                    val embedded = withContext(Dispatchers.IO) {
                        val savedCacheArtwork = if (song.isCachedTrack) {
                            runCatching {
                                // Cache playlist rows carry the exact artwork file URI. Prefer it
                                // over deriving the cache key from SongItem.id, since older cache
                                // metadata may use a non-numeric song ID.
                                val artworkFromSongUri = song.thumbnailUrl
                                    ?.takeIf { it.startsWith("file://", ignoreCase = true) }
                                    ?.let { android.net.Uri.parse(it).path }
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { java.io.File(it) }
                                    ?.takeIf { it.isFile && it.length() > 0L }
                                val artworkFile = artworkFromSongUri
                                    ?: AudioPlaybackService.getCachedMediaArtworkFile(
                                        appContext,
                                        song.id.toString()
                                    ).takeIf { it.isFile && it.length() > 0L }
                                artworkFile?.readBytes()
                            }.getOrNull()
                        } else {
                            null
                        }
                        savedCacheArtwork ?: song.getEmbeddedPicture(appContext)
                    }
                    _currentSongArtwork.value = embedded
                    
                    val colors = extractColorsFromArtwork(song, embedded)
                    _miniPlayerBlendColor.value = colors.first
                    _playerBackgroundColor.value = colors.second

                    // Trigger exact real engagement likes count and watch history fetching
                    fetchEngagementAndHistoryForSong(song)
                    trackHistoryForSong(song)
                } else {
                    _currentSongArtwork.value = null
                    _miniPlayerBlendColor.value = 0
                    _playerBackgroundColor.value = 0xFF0F0F0F.toInt()
                }
            }
        }

        bgScope.launch(Dispatchers.Default) {
            combine(
                currentSong,
                SongDownloader.downloadProgress,
                SongDownloader.downloadedVideoIds
            ) { song, progressMap, downloadedIds ->
                if (song == null) {
                    false to null
                } else {
                    val videoId = song.videoId
                    if (videoId.isNullOrEmpty()) {
                        true to null
                    } else {
                        val progress = progressMap[videoId]
                        if (progress != null) {
                            false to progress
                        } else {
                            val downloaded = videoId in downloadedIds ||
                                withContext(Dispatchers.IO) {
                                    SongDownloader.isSongDownloaded(appContext, song)
                                }
                            downloaded to null
                        }
                    }
                }
            }.collect { (downloaded, progress) ->
                _isCurrentSongDownloaded.value = downloaded
                _currentSongDownloadProgress.value = progress
            }
        }
    }

    private suspend fun extractColorsFromArtwork(song: SongItem, embeddedArtwork: ByteArray?): Triple<Int, Int, Int> {
        val artworkSource = embeddedArtwork ?: song.getAlbumArtUri() ?: R.drawable.ic_music_note
        
        val request = ImageRequest.Builder(appContext)
            .data(artworkSource)
            .size(72, 72)
            .allowHardware(false) // Hardware Bitmaps are not allowed for Palette extraction!
            .build()
            
        return withContext(Dispatchers.IO) {
            var bitmap: Bitmap? = null
            try {
                val result = appContext.imageLoader.execute(request)
                if (result is SuccessResult) {
                    bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                }
            } catch (e: Exception) {
                Log.e(TAG, "Coil failed to load album art for palette: ${e.message}")
            }

            if (bitmap != null) {
                try {
                    val palette = Palette.from(bitmap)
                        .maximumColorCount(24) // More swatches for richer seed selection
                        .generate()
                    
                    // 1. Extract palette swatches and pick the most chromatic + relevant accent
                    val allSwatches = palette.swatches
                    val totalPopulation = allSwatches.sumOf { it.population }.coerceAtLeast(1)
                    val candidateSwatches = listOfNotNull(
                        palette.vibrantSwatch,
                        palette.darkVibrantSwatch,
                        palette.lightVibrantSwatch,
                        palette.mutedSwatch,
                        palette.darkMutedSwatch,
                        palette.lightMutedSwatch,
                        palette.dominantSwatch
                    )
                    val bestAccentSwatch = candidateSwatches.maxByOrNull { swatch ->
                        val sat = swatch.hsl[1]
                        val pop = swatch.population.toFloat() / totalPopulation.toFloat()
                        sat * (0.25f + pop)
                    }
                    val accentColor = bestAccentSwatch?.rgb
                        ?: palette.getVibrantColor(palette.getDominantColor(0xFF8338EC.toInt()))

                    // 2. Extract the dominant color as background seed
                    val baseBgColor = palette.getDominantColor(0xFF0F0F0F.toInt())
                    
                    // 3. Pick the best seed for theme generation (prefer vibrant, fall back to accent)
                    val themeSeed = (palette.vibrantSwatch?.rgb
                        ?: palette.lightVibrantSwatch?.rgb
                        ?: palette.darkVibrantSwatch?.rgb
                        ?: bestAccentSwatch?.rgb
                        ?: palette.dominantSwatch?.rgb
                        ?: accentColor)
                    
                    // Convert both to HSL for precise visual enhancements
                    val accentHsl = FloatArray(3)
                    val bgHsl = FloatArray(3)
                    ColorUtils.colorToHSL(accentColor, accentHsl)
                    ColorUtils.colorToHSL(baseBgColor, bgHsl)
                    
                    // Detect monochrome only when palette is truly near-grayscale.
                    val maxChannelDelta = maxOf(
                        abs(Color.red(baseBgColor) - Color.green(baseBgColor)),
                        abs(Color.green(baseBgColor) - Color.blue(baseBgColor)),
                        abs(Color.blue(baseBgColor) - Color.red(baseBgColor))
                    ) / 255f
                    val colorfulPopulation = allSwatches
                        .filter { it.hsl[1] >= 0.16f }
                        .sumOf { it.population }
                    val colorfulRatio = colorfulPopulation.toFloat() / totalPopulation.toFloat()
                    val isMonochromatic = (
                        colorfulRatio < 0.08f &&
                            bgHsl[1] < 0.14f &&
                            accentHsl[1] < 0.18f &&
                            maxChannelDelta < 0.09f
                        ) || (bgHsl[1] < 0.06f && accentHsl[1] < 0.08f)
                    
                    val finalAccent = if (isMonochromatic) {
                        // For grayscale/monochromatic art, apply a matching pure white accent color (saturation = 0)
                        accentHsl[0] = 0f
                        accentHsl[1] = 0f
                        accentHsl[2] = 1f
                        ColorUtils.HSLToColor(accentHsl)
                    } else {
                        // For colorful art, boost the saturation and lightness of the extracted accent color so it pops
                        if (accentHsl[2] < 0.60f) {
                            accentHsl[2] = 0.65f // Elevate brightness for crisp contrast
                        }
                        if (accentHsl[1] < 0.30f) {
                            accentHsl[1] = 0.60f // Inject beautiful saturation to keep it vibrant
                        }
                        ColorUtils.HSLToColor(accentHsl)
                    }
                    
                    val finalBg = if (isMonochromatic) {
                        // For grayscale/monochromatic art, apply a pure neutral gray/charcoal background seed (saturation = 0)
                        bgHsl[0] = 0f
                        bgHsl[1] = 0f
                        bgHsl[2] = 0.30f
                        ColorUtils.HSLToColor(bgHsl)
                    } else {
                        // For colorful art, avoid muddy hue from near-gray dominant colors:
                        // if dominant saturation is low, borrow hue from accent and keep saturation tasteful.
                        if (bgHsl[1] < 0.16f) {
                            bgHsl[0] = accentHsl[0]
                            bgHsl[1] = (accentHsl[1] * 0.36f).coerceIn(0.18f, 0.40f)
                        } else {
                            bgHsl[1] = bgHsl[1].coerceIn(0.18f, 0.60f)
                        }
                        bgHsl[2] = bgHsl[2].coerceIn(0.22f, 0.48f)
                        ColorUtils.HSLToColor(bgHsl)
                    }

                    // Boost theme seed saturation for richer M3 palette generation
                    val seedHsl = FloatArray(3)
                    ColorUtils.colorToHSL(themeSeed, seedHsl)
                    if (!isMonochromatic && seedHsl[1] < 0.35f) {
                        seedHsl[1] = 0.50f
                    }
                    if (!isMonochromatic && seedHsl[2] < 0.30f) {
                        seedHsl[2] = 0.45f
                    }
                    val finalSeed = if (isMonochromatic) {
                        0xFF6750A4.toInt() // Fallback to M3 purple for grayscale art
                    } else {
                        ColorUtils.HSLToColor(seedHsl)
                    }
                    
                    return@withContext Triple(finalAccent, finalBg, finalSeed)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to generate Palette from bitmap: ${e.message}")
                }
            }

            // Sleek fallback colors (vibrant neon violet and elegant deep charcoal)
            Triple(0xFF8338EC.toInt(), 0xFF0A0B0E.toInt(), 0xFF6750A4.toInt())
        }
    }

    fun initFavorites(context: Context) {
        if (favoritesManager == null) {
            favoritesManager = FavoritesManager(context)
            refreshFavorites()
        }
    }

    fun refreshFavorites() {
        val ids = favoritesManager?.favoriteIds?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
        _favoriteSongIds.value = ids
        _currentSong.value?.let { song ->
            val isFav = ids.contains(song.id)
            _isCurrentSongFavorite.value = isFav
            updateLikesCountDisplay(isFav)
        }
    }

    /**
     * Prime the player with its last persisted memory.
     */
    fun loadLastPlaybackState() {
        bgScope.launch(Dispatchers.IO) {
            val lastSong = PlaybackStateManager.getLastSong(appContext) ?: return@launch
            val lastQueue = PlaybackStateManager.getLastQueue(appContext)
            val lastIndex = PlaybackStateManager.getLastIndex(appContext)
            val lastPos = PlaybackStateManager.getLastPosition(appContext)

            Log.d(TAG, "Loading persisted playback state: ${lastSong.title} at pos $lastPos")

            withContext(Dispatchers.Main) {
                // The current song and queue index are persisted separately. Prefer the saved
                // index only when it still points to this track; older snapshots can otherwise
                // restore the row immediately before the song that was actually playing.
                val restoredQueue = ArrayList(lastQueue)
                val savedVideoId = lastSong.videoId?.takeIf { it.isNotBlank() }
                val matchingIndices = restoredQueue.indices.filter { index ->
                    val queuedSong = restoredQueue[index]
                    val queuedVideoId = queuedSong.videoId?.takeIf { it.isNotBlank() }
                    if (savedVideoId != null && queuedVideoId != null) {
                        queuedVideoId == savedVideoId
                    } else {
                        queuedSong.id == lastSong.id
                    }
                }
                val restoredIndex = matchingIndices.minByOrNull { kotlin.math.abs(it - lastIndex) }
                    ?: run {
                        // Keep the complete saved queue, but make sure the selected track is
                        // present so the restored index and current song can never disagree.
                        restoredQueue.add(lastSong)
                        restoredQueue.lastIndex
                    }

                _songList.value = restoredQueue
                _currentSongIndex.value = restoredIndex
                _currentSong.value = lastSong
                _duration.value = lastSong.duration.toInt()
                _currentPosition.value = lastPos.toInt()

                // Prepare lazy resolver pointer
                if (!lastSong.videoId.isNullOrEmpty()) {
                    lastSong.contentUri = "innertube://${lastSong.videoId}".toUri()
                }
                _songUri.value = lastSong.contentUri

                Log.d(TAG, "Restored queue index $restoredIndex for ${lastSong.title}")
            }
        }
    }

    private var playbackStateSaveJob: Job? = null
    @Volatile private var playbackStateSaveGeneration = 0L
    private val playbackStateSaveMutex = Mutex()

    private fun copySongForPlaybackSnapshot(song: SongItem): SongItem = song.copy().apply {
        thumbnailUrl = song.thumbnailUrl
        mediaStoreRelativePath = song.mediaStoreRelativePath
        albumName = song.albumName
        playbackContextName = song.playbackContextName
        videoId = song.videoId
        artistId = song.artistId
        artistRefs = ArrayList(song.artistRefs)
        lufs = song.lufs
        isCachedTrack = song.isCachedTrack
        isDownloadedOffline = song.isDownloadedOffline
        isAutoplayRecommendation = song.isAutoplayRecommendation
        contentUri = song.contentUri
    }

    fun savePlaybackState() {
        val generation = ++playbackStateSaveGeneration
        playbackStateSaveJob?.cancel()
        playbackStateSaveJob = bgScope.launch {
            // Coalesce rapid track changes, then serialize/write only the latest snapshot.
            delay(180L)
            val song = _currentSong.value?.let(::copySongForPlaybackSnapshot) ?: return@launch
            val queue = _songList.value.map(::copySongForPlaybackSnapshot)
            val index = _currentSongIndex.value
            val position = _currentPosition.value.toLong()

            withContext(Dispatchers.IO) {
                playbackStateSaveMutex.withLock {
                    if (generation != playbackStateSaveGeneration) return@withLock
                    PlaybackStateManager.saveState(appContext, song, position, queue, index)
                }
            }
        }
    }

    /** Immediate background persistence for lifecycle shutdown/task removal. */
    fun savePlaybackStateImmediately() {
        val generation = ++playbackStateSaveGeneration
        playbackStateSaveJob?.cancel()
        playbackStateSaveJob = null
        val song = _currentSong.value?.let(::copySongForPlaybackSnapshot) ?: return
        val queue = _songList.value.map(::copySongForPlaybackSnapshot)
        val index = _currentSongIndex.value
        val position = _currentPosition.value.toLong()
        bgScope.launch(Dispatchers.IO) {
            playbackStateSaveMutex.withLock {
                if (generation != playbackStateSaveGeneration) return@withLock
                PlaybackStateManager.saveState(appContext, song, position, queue, index)
            }
        }
    }

    // ========== SERVICE & MANAGERS ==========


    val songListFlow get() = songList
    val currentSongIndexFlow get() = currentSongIndex
    val isCurrentSongFavoriteFlow get() = isCurrentSongFavorite
    
    // Lyrics delegated flows
    val syncedLyrics get() = lyricsController.syncedLyrics
    val plainLyrics get() = lyricsController.plainLyrics
    val isLyricsLoading get() = lyricsController.isLoading
    val lyricsError get() = lyricsController.error
    val isLyricsModeEnabled get() = lyricsController.isLyricsModeEnabled
    val providerResults get() = lyricsController.providerResults
    val selectedProvider get() = lyricsController.selectedProvider

    // ========== PLAYBACK CONTROLS ==========

    fun playAudio(notifyListenTogether: Boolean = true) {
        audioService?.play()
        if (notifyListenTogether && isListenTogetherGuest) {
            DsmusicListenTogetherSession.client.sendGuestPlaybackControl(
                isPlaying = true,
                positionMs = _currentPosition.value.toLong()
            )
        }
    }
    fun pauseAudio(notifyListenTogether: Boolean = true) {
        audioService?.pause()
        if (notifyListenTogether && isListenTogetherGuest) {
            DsmusicListenTogetherSession.client.sendGuestPlaybackControl(
                isPlaying = false,
                positionMs = _currentPosition.value.toLong()
            )
        }
    }
    fun seekTo(position: Int) {
        val safePosition = if (_duration.value > 0) {
            position.coerceIn(0, _duration.value)
        } else {
            position.coerceAtLeast(0)
        }
        _currentPosition.value = safePosition
        audioService?.seekTo(safePosition)
    }
    fun toggleFavorite(songId: Long) = toggleFavorite()

    fun toggleFavorite() {
        val song = _currentSong.value ?: return
        val manager = favoritesManager ?: return

        // Guard guest likes: Prompts premium Sign In Dialog
        if (!AccountManager.isLoggedIn(appContext)) {
            _showSignInDialog.value = true
            return
        }

        val newState = !manager.isFavorite(song.id)
        manager.setFavorite(song.id, newState)
        _isCurrentSongFavorite.value = newState
        updateLikesCountDisplay(newState)

        // If we liked it, we cannot have it disliked
        if (newState) {
            _isCurrentSongDisliked.value = false
        }

        audioService?.updateWidgetState(_isPlaying.value)

        // Sync like status to YouTube Music in the background
        if (!song.videoId.isNullOrEmpty()) {
            bgScope.launch(Dispatchers.IO) {
                try {
                    val status = if (newState) {
                        YouTubeMusic.LikeStatus.LIKE
                    } else {
                        YouTubeMusic.LikeStatus.INDIFFERENT
                    }
                    val result = YouTubeMusic.updateLikeStatus(song.videoId!!, status)
                    Log.d(TAG, "Synced like status to YouTube Music for ${song.title}: success=${result.getOrDefault(false)}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync like to YouTube Music: ${e.message}")
                }
            }
        }
    }

    fun toggleDislike() {
        val song = _currentSong.value ?: return
        
        // Guard guest dislikes: Prompts premium Sign In Dialog
        if (!AccountManager.isLoggedIn(appContext)) {
            _showSignInDialog.value = true
            return
        }

        val newState = !_isCurrentSongDisliked.value
        _isCurrentSongDisliked.value = newState

        // If we are now disliking, we cannot have it liked
        if (newState && _isCurrentSongFavorite.value) {
            val manager = favoritesManager
            if (manager != null) {
                manager.setFavorite(song.id, false)
                _isCurrentSongFavorite.value = false
                updateLikesCountDisplay(false)
            }
        }

        audioService?.updateWidgetState(_isPlaying.value)

        // Sync dislike status to YouTube Music in the background
        if (!song.videoId.isNullOrEmpty()) {
            bgScope.launch(Dispatchers.IO) {
                try {
                    val status = if (newState) {
                        YouTubeMusic.LikeStatus.DISLIKE
                    } else {
                        YouTubeMusic.LikeStatus.INDIFFERENT
                    }
                    val result = YouTubeMusic.updateLikeStatus(song.videoId!!, status)
                    Log.d(TAG, "Synced dislike status to YouTube Music for ${song.title}: success=${result.getOrDefault(false)}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync dislike to YouTube Music: ${e.message}")
                }
            }
        }
    }

    fun toggleLoopMode() {
        val nextMode = when (_repeatMode.value) {
            REPEAT_OFF -> REPEAT_ONCE
            REPEAT_ONCE -> REPEAT_INFINITY
            else -> REPEAT_OFF
        }
        setRepeatMode(nextMode)
    }

    fun setRepeatMode(mode: Int) {
        val normalizedMode = if (listenTogetherQueueAuthoritative) {
            REPEAT_OFF
        } else when (mode) {
            REPEAT_OFF, REPEAT_ONCE, REPEAT_INFINITY -> mode
            else -> REPEAT_OFF
        }
        if (_repeatMode.value == normalizedMode) return

        _repeatMode.value = normalizedMode
        if (normalizedMode == REPEAT_ONCE) {
            resetRepeatOnceCycleFromCurrent()
        } else {
            clearRepeatOnceCycle()
        }
        audioService?.let { service ->
            service.runWhenPlayerReady { service.refreshNativeQueue() }
        }
    }

    private fun clearRepeatOnceCycle() {
        repeatOnceQueueOrder = emptyList()
        repeatOncePosition = 0
        repeatOncePass = 0
    }

    private fun resetRepeatOnceCycleFromCurrent(startIndex: Int = _currentSongIndex.value) {
        val songs = _songList.value
        if (songs.isEmpty()) {
            clearRepeatOnceCycle()
            return
        }

        val currentSong = _currentSong.value
        val matchingCurrentIndex = currentSong?.let { active ->
            songs.indexOfFirst { it.id == active.id && it.videoId == active.videoId }
                .takeIf { it >= 0 }
        }
        val start = startIndex.takeIf { it in songs.indices }
            ?: matchingCurrentIndex
            ?: 0

        val order = if (_isShuffleEnabled.value && songs.size > 1) {
            listOf(start) + songs.indices.filter { it != start }.shuffled(shuffleRandom)
        } else {
            (start until songs.size).toList() + (0 until start).toList()
        }

        repeatOnceQueueOrder = order.map { songs[it].id }
        repeatOncePosition = 0
        repeatOncePass = 0
    }

    private fun advanceRepeatOnceCursor(nextIndex: Int) {
        if (_repeatMode.value != REPEAT_ONCE) return
        val targetSong = _songList.value.getOrNull(nextIndex) ?: return
        val order = repeatOnceQueueOrder
        if (order.isEmpty()) {
            resetRepeatOnceCycleFromCurrent(nextIndex)
            return
        }

        val nextPosition = repeatOncePosition + 1
        when {
            nextPosition < order.size && order[nextPosition] == targetSong.id -> {
                repeatOncePosition = nextPosition
            }
            repeatOncePosition == order.lastIndex &&
                repeatOncePass == 0 &&
                order.firstOrNull() == targetSong.id -> {
                repeatOncePass = 1
                repeatOncePosition = 0
            }
            else -> {
                val actualPosition = order.indexOf(targetSong.id)
                if (actualPosition >= 0) {
                    repeatOncePosition = actualPosition
                } else {
                    resetRepeatOnceCycleFromCurrent(nextIndex)
                }
            }
        }
    }

    fun updateSongIndexOnly(index: Int) {
        if (index !in _songList.value.indices) return
        val song = _songList.value[index]
        val previousSong = _currentSong.value
        val isDifferentTrack = previousSong?.id != song.id ||
            previousSong?.videoId != song.videoId

        _currentSongIndex.value = index
        if (isDifferentTrack) advanceRepeatOnceCursor(index)
        if (isDifferentTrack) {
            lyricsController.clearForSongChange(song)
            // Clear the previous video's title/uploader immediately. The player effect
            // will ask AudioPlaybackService to resolve this queue item's matching video.
            _videoPlaybackMetadata.value = null
        }
        _currentSong.value = song
    }
    
    fun retryLyrics() = lyricsController.retryLyrics(appContext, _currentSong.value)

    fun fetchLyrics() = lyricsController.fetchLyrics(appContext, _currentSong.value)

    fun fetchLyricsForCurrentSong() = fetchLyrics()

    fun setLyricsModeEnabled(enabled: Boolean) {
        lyricsController.setLyricsModeEnabled(enabled)
    }

    fun selectLyricsProvider(providerName: String) {
        lyricsController.selectProvider(appContext, _currentSong.value, providerName)
    }


    fun setIsPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun postIsPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun setCurrentPosition(position: Int) {
        _currentPosition.value = position
    }

    fun setDuration(dur: Int) {
        _duration.value = dur
    }

    // ========== LIBRARY MANAGEMENT ==========

    fun setListenTogetherQueueAuthoritative(enabled: Boolean) {
        if (listenTogetherQueueAuthoritative == enabled) return
        listenTogetherQueueAuthoritative = enabled
        if (enabled) {
            listenTogetherPreviousShuffle = _isShuffleEnabled.value
            listenTogetherPreviousRepeatMode = _repeatMode.value

            // A guest must consume the host's exact queue order, not a local shuffle
            // permutation or repeat cycle. Restore the guest's prior choices on exit.
            setShuffleEnabled(false)
            setRepeatMode(REPEAT_OFF)

            autoplayQueueGeneration += 1L
            autoplayFetchJob?.cancel()
            autoplayFetchJob = null
            autoplayFetchGeneration = -1L
        } else {
            val restoreShuffle = listenTogetherPreviousShuffle
            val restoreRepeatMode = listenTogetherPreviousRepeatMode
            listenTogetherPreviousShuffle = null
            listenTogetherPreviousRepeatMode = null

            if (restoreShuffle != null) setShuffleEnabled(restoreShuffle)
            if (restoreRepeatMode != null) setRepeatMode(restoreRepeatMode)
        }
    }

    /**
     * Apply the host's ordered upcoming tracks while retaining the guest's current item.
     * Duplicate tracks are preserved because queue identity is per occurrence, not per song.
     */
    fun applyListenTogetherUpcomingQueue(upcomingSongs: List<SongItem>) {
        val current = _currentSong.value ?: return
        val nextQueue = listOf(current) + upcomingSongs
        val currentQueue = _songList.value
        val unchanged = currentQueue.size == nextQueue.size &&
            currentQueue.indices.all { index ->
                currentQueue[index].videoId == nextQueue[index].videoId &&
                    currentQueue[index].title == nextQueue[index].title &&
                    currentQueue[index].artist == nextQueue[index].artist
            } &&
            _currentSongIndex.value == 0
        if (unchanged) return

        autoplayQueueGeneration += 1L
        autoplayFetchJob?.cancel()
        autoplayFetchJob = null
        autoplayFetchGeneration = -1L
        _songList.value = nextQueue
        _currentSongIndex.value = 0
        if (_repeatMode.value == REPEAT_ONCE) resetRepeatOnceCycleFromCurrent(0)
        audioService?.refreshNativeQueue()
        savePlaybackState()
    }

    fun setSongList(songs: List<SongItem>) {
        // A replacement queue is a new Autoplay session. Do not carry the previous
        // queue's completed radio-seed markers into Cache/Downloaded playlists: an
        // offline copy may reuse a video ID whose recommendations were fetched earlier.
        // Cancel any request belonging to the previous queue; its finally block must
        // not clear a newer queue's request when it completes.
        autoplayQueueGeneration += 1L
        autoplayFetchJob?.cancel()
        autoplayFetchJob = null
        autoplayFetchGeneration = -1L
        autoplayFetchedVideoIds.clear()
        _songList.value = songs
        if (_repeatMode.value == REPEAT_ONCE) resetRepeatOnceCycleFromCurrent()
        Log.d(TAG, "Song list updated: ${songs.size} songs; reset Autoplay seed history")
        
        // Proactive queue warmer
        if (songs.isNotEmpty()) {
            bgScope.launch(Dispatchers.IO) {
                songs.take(3).forEach { song ->
                    if (!song.isCachedTrack && !song.isDownloadedOffline) {
                        song.videoId?.let { YouTubeMusic.prefetchStream(it) }
                    }
                }
            }
        }
    }

    fun setLocalSongs(songs: List<SongItem>) {
        _localSongs.value = songs
    }

    fun setCurrentSong(song: SongItem?) {
        val previousSong = _currentSong.value
        if (previousSong?.id != song?.id || previousSong?.videoId != song?.videoId) {
            lyricsController.clearForSongChange(song)
            _isVideoMode.value = false
            _videoPlaybackMetadata.value = null
        }
        _currentSong.value = song
    }

    /**
     * Pushes the final reordered queue into the native Media3 queue. Drag gestures defer this
     * until release so each intermediate pointer movement does not rebuild the player queue.
     */
    fun syncQueueWithPlaybackService() {
        audioService?.let { service ->
            service.runWhenPlayerReady { service.refreshNativeQueue() }
        }
    }

    /** Publish a same-ID replacement when its actual playback source changes. */
    fun refreshCurrentSongSource(song: SongItem) {
        if (_currentSong.value?.id != song.id) return
        _currentSong.value = song
        _songUri.value = song.contentUri

        // Keep the selected queue row in sync with the resolved source. Otherwise a Cache
        // playlist row can remain marked offline-only after its cache entry has been removed.
        val queue = _songList.value
        val queueIndex = queue.indexOfFirst { it.id == song.id && it.videoId == song.videoId }
        if (queueIndex >= 0 && queue[queueIndex] != song) {
            _songList.value = queue.toMutableList().also { it[queueIndex] = song }
        }
    }

    fun playSongAtIndex(
        index: Int,
        preserveRepeatOnceCycle: Boolean = false,
        debouncePlaybackLoadMs: Long = 0L,
        fromListenTogether: Boolean = false,
        autoPlay: Boolean = true
    ) {
        // Guest UI actions remain locked, but the room bridge may apply the host's
        // authoritative track selection through this explicit internal path.
        if ((listenTogetherQueueAuthoritative || isListenTogetherGuest) && !fromListenTogether) return
        if (fromListenTogether && !isListenTogetherGuest) return
        // A direct queue selection takes priority over a pending Next/Previous burst.
        cancelPendingRapidNavigation()
        val songs = _songList.value
        if (index !in songs.indices) {
            Log.w(TAG, "Invalid song index: $index")
            return
        }

        val song = songs[index]

        // Force innertube:// scheme for ALL YouTube Music songs to guarantee fresh stream resolution
        if (
            !song.videoId.isNullOrEmpty() &&
            !song.isCachedTrack &&
            !song.isDownloadedOffline
        ) {
            song.contentUri = "innertube://${song.videoId}".toUri()
            Log.d(TAG, "Injected lazy resolver URI for: ${song.title}")
        }

        val previousSong = _currentSong.value
        val isDifferentTrack = previousSong?.id != song.id ||
            previousSong?.videoId != song.videoId

        _currentSongIndex.value = index
        if (_repeatMode.value == REPEAT_ONCE && !preserveRepeatOnceCycle) {
            resetRepeatOnceCycleFromCurrent(index)
        }
        lyricsController.clearForSongChange(song)
        // Keep video mode enabled across queue changes so the service can bind the
        // matching video for the new track. It validates both song ID and video ID,
        // and falls back to this track's audio stream when no video is available.
        if (isDifferentTrack) {
            _videoPlaybackMetadata.value = null
        }

        // SongItem equality includes its playback source, so selecting a cached/downloaded
        // representation of the same track ID emits a real current-song update without a
        // null-frame flicker or a stale playing icon.
        _currentSong.value = song
        _songUri.value = song.contentUri
        // Publish the new track's timeline immediately. The controls and progress display
        // must not remain attached to the previous song while its stream is resolving.
        _currentPosition.value = 0
        _duration.value = song.duration.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        _isPlaybackReady.value = false
        _isLoading.value = true

        _isCurrentSongFavorite.value = favoritesManager?.isFavorite(song.id) ?: false
        baseLikesCountInt = 0
        _likesCount.value = "Like"



        dispatchSelectedSongPlayback(song, debouncePlaybackLoadMs, autoPlay)
    }

    /**
     * Repeated Next/Previous presses still update the selected queue index immediately,
     * but only the final selection in a rapid burst starts a stream resolution. This avoids
     * spawning/cancelling several network resolvers, MediaSession updates and artwork jobs
     * for tracks the listener has already skipped past.
     */
    private fun dispatchSelectedSongPlayback(song: SongItem, debounceMs: Long, autoPlay: Boolean) {
        pendingRapidSkipLoadJob?.cancel()
        pendingRapidSkipLoadJob = null

        if (debounceMs > 0L) {
            pendingRapidSkipLoadJob = viewModelScope.launch {
                delay(debounceMs)
                val latestSong = _currentSong.value?.takeIf { it.id == song.id } ?: return@launch
                pendingRapidSkipLoadJob = null
                startSelectedSongPlayback(latestSong, autoPlay)
            }
        } else {
            startSelectedSongPlayback(song, autoPlay)
        }
    }

    private fun startSelectedSongPlayback(song: SongItem, autoPlay: Boolean) {
        val selectedSong = _currentSong.value?.takeIf { it.id == song.id } ?: song
        audioService?.let { service ->
            if (autoPlay) service.loadAndPlay(selectedSong.contentUri)
            else service.loadAudio(selectedSong.contentUri)
            if (!selectedSong.thumbnailUrl.isNullOrBlank()) {
                service.setSongMetadataByUrl(selectedSong.title, selectedSong.thumbnailUrl)
            } else {
                service.setSongMetadataById(selectedSong.title, selectedSong.albumId)
            }
            updateRecentArtists(selectedSong.artist)
        } ?: run {
            Log.w(TAG, "AudioService not bound yet, song queued for cold-start loading")
        }

        savePlaybackState()
        scheduleNextSongPrefetch(selectedSong.id)
    }

    private fun updateRecentArtists(artist: String?) {
        if (artist.isNullOrBlank() || artist.equals("<unknown>", ignoreCase = true)) return
        
        bgScope.launch(Dispatchers.IO) {
            val prefs = appContext.getSharedPreferences("explore_prefs", Context.MODE_PRIVATE)
            val history = prefs.getString("recent_artists_list", "") ?: ""
            val currentList = history.split("|").filter { it.isNotBlank() }.toMutableList()
            
            currentList.remove(artist.trim())
            currentList.add(0, artist.trim())
            val trimmed = currentList.take(3)
            
            prefs.edit {
                putString("recent_artists_list", trimmed.joinToString("|"))
                    .putString("last_played_artist", artist)
            }
        }
    }

    fun findSongById(songId: Long): SongItem? {
        val song = _songList.value.firstOrNull { it.id == songId }
            ?: _localSongs.value.firstOrNull { it.id == songId }
        if (song != null) return song

        val service = audioService ?: return null
        val item = service.player.currentMediaItem ?: return null
        val currentId = item.mediaId.toLongOrNull()
        if (currentId == songId) {
            val title = item.mediaMetadata.title?.toString() ?: "Unknown"
            val artist = item.mediaMetadata.artist?.toString() ?: "Unknown"
            val videoId = item.mediaId
            val artworkUri = item.mediaMetadata.artworkUri?.toString()
            return SongItem.createOnlineSong(
                videoId = videoId,
                title = title,
                artist = artist,
                streamUrl = item.localConfiguration?.uri?.toString(),
                durationMs = service.player.duration,
                thumbnailUrl = artworkUri,
                albumName = item.mediaMetadata.albumTitle?.toString()
            )
        }
        return null
    }

    fun dismissPlayer() {
        _currentSong.value = null
        audioService?.dismissService()
    }

    fun playSong(song: SongItem?) {
        if (song == null) return
        val index = findSongIndexById(song.id)
        if (index != -1) {
            playSongAtIndex(index)
        } else {
            addToQueue(song)
            val newIndex = findSongIndexById(song.id)
            if (newIndex != -1) playSongAtIndex(newIndex)
        }
    }

    /**
     * Applies a host-selected track on a guest. This deliberately bypasses the guest's
     * local track-selection lock, but never grants guests permission to choose tracks.
     * The track is loaded paused; the host's following play/pause event decides its state.
     */
    fun playListenTogetherTrack(song: SongItem) {
        if (!isListenTogetherGuest) return
        val videoId = song.videoId?.takeIf { it.isNotBlank() } ?: return
        val nextQueue = _songList.value.toMutableList()
        val existingIndex = nextQueue.indexOfFirst { it.videoId == videoId }
        val targetIndex = if (existingIndex >= 0) {
            nextQueue[existingIndex] = song
            existingIndex
        } else {
            nextQueue.add(song)
            nextQueue.lastIndex
        }
        _songList.value = nextQueue
        _currentSongIndex.value = targetIndex
        playSongAtIndex(
            index = targetIndex,
            fromListenTogether = true,
            autoPlay = false
        )
    }

    fun findSongIndexById(songId: Long): Int = _songList.value.indexOfFirst { it.id == songId }

    /**
     * Coalesce rapid navigation into one final selection. Intermediate taps only advance a
     * lightweight target ID; they do not clear lyric state, recompose the player for every
     * skipped track, update the notification, or start/cancel stream and artwork work.
     */
    private fun scheduleRapidNavigation(song: SongItem, preserveRepeatOnceCycle: Boolean = false) {
        pendingRapidNavigationJob?.cancel()
        pendingRapidNavigationSongId = song.id
        pendingRapidNavigationPreserveRepeatOnce = preserveRepeatOnceCycle
        pendingRapidNavigationJob = viewModelScope.launch {
            delay(RAPID_NAVIGATION_SETTLE_MS)
            val targetId = pendingRapidNavigationSongId ?: return@launch
            val preserveRepeatOnce = pendingRapidNavigationPreserveRepeatOnce
            pendingRapidNavigationJob = null
            pendingRapidNavigationSongId = null
            pendingRapidNavigationPreserveRepeatOnce = false

            // Resolve by stable ID so a concurrent queue edit cannot turn the pending
            // selection into a different song merely because its index changed.
            val finalIndex = findSongIndexById(targetId)
            if (finalIndex >= 0) {
                playSongAtIndex(
                    finalIndex,
                    preserveRepeatOnceCycle = preserveRepeatOnce
                )
            }
        }
    }

    private fun commitPendingRapidNavigation(): Boolean {
        val targetId = pendingRapidNavigationSongId ?: return false
        val preserveRepeatOnce = pendingRapidNavigationPreserveRepeatOnce
        pendingRapidNavigationJob?.cancel()
        pendingRapidNavigationJob = null
        pendingRapidNavigationSongId = null
        pendingRapidNavigationPreserveRepeatOnce = false

        val finalIndex = findSongIndexById(targetId)
        if (finalIndex < 0) return false
        playSongAtIndex(
            finalIndex,
            preserveRepeatOnceCycle = preserveRepeatOnce
        )
        return true
    }

    private fun cancelPendingRapidNavigation() {
        pendingRapidNavigationJob?.cancel()
        pendingRapidNavigationJob = null
        pendingRapidNavigationSongId = null
        pendingRapidNavigationPreserveRepeatOnce = false
    }

    private fun pendingRapidNavigationIndex(): Int? {
        val targetId = pendingRapidNavigationSongId ?: return null
        return findSongIndexById(targetId).takeIf { it >= 0 }
    }

    fun playNextSong(force: Boolean = false) {
        if (listenTogetherQueueAuthoritative || isListenTogetherGuest) return
        val songs = _songList.value
        val navigationBaseIndex = pendingRapidNavigationIndex() ?: _currentSongIndex.value

        if (!force) {
            if (_sleepTimerMode.value == SleepTimerMode.END_OF_SONG) {
                cancelPendingRapidNavigation()
                pauseAudio()
                setSleepTimerMode(SleepTimerMode.OFF)
                return
            }
            if (
                _sleepTimerMode.value == SleepTimerMode.END_OF_QUEUE &&
                navigationBaseIndex == songs.lastIndex
            ) {
                commitPendingRapidNavigation()
                pauseAudio()
                setSleepTimerMode(SleepTimerMode.OFF)
                return
            }
        }

        val activeRepeatMode = _repeatMode.value
        val nextIndex = getNextSongIndexFrom(navigationBaseIndex)
        if (nextIndex >= 0) {
            if (activeRepeatMode == REPEAT_ONCE) advanceRepeatOnceCursor(nextIndex)
            scheduleRapidNavigation(
                songs[nextIndex],
                preserveRepeatOnceCycle = activeRepeatMode == REPEAT_ONCE
            )
            return
        }

        // If a burst has already selected the last available track, let that target
        // commit instead of cancelling it or pausing the track that preceded the burst.
        if (pendingRapidNavigationSongId != null) {
            commitPendingRapidNavigation()
            return
        }

        if (activeRepeatMode == REPEAT_ONCE) {
            // Repeat Once is finite: do not let Autoplay extend the queue after its
            // second complete pass.
            pauseAudio()
            setCurrentPosition(0)
        } else if (isAutoplayEnabled()) {
            val currentSongId = _currentSong.value?.id
            // The last item can be a locally served copy. Check the entire queue for
            // an online-backed source instead of rejecting autoplay on that item's ID.
            val sourceVideoId = getAutoplaySeedVideoId()
            if (sourceVideoId.isNullOrBlank() || currentSongId == null) {
                // A queue with no online-backed entries is genuinely local-only.
                audioService?.pause()
            } else {
                val job = prefetchAutoplayRecommendations(sourceVideoId)
                if (job == null) {
                    // A previous/completed fetch can mean the queue already has recommendations.
                    // Recheck before interpreting a null job as an empty queue.
                    val alreadyQueuedIndex = if (_currentSong.value?.id == currentSongId) {
                        getNextSongIndex()
                    } else {
                        -1
                    }
                    if (alreadyQueuedIndex >= 0) playSongAtIndex(alreadyQueuedIndex)
                    else audioService?.pause()
                } else {
                    viewModelScope.launch {
                        job.join()
                        // Track identity, not videoId, guards against a manual change when
                        // the currently playing cache/download copy has no resolver URI.
                        if (_currentSong.value?.id != currentSongId) return@launch
                        val appendedIndex = getNextSongIndex()
                        if (appendedIndex >= 0) {
                            playSongAtIndex(appendedIndex)
                        } else {
                            audioService?.pause()
                            com.dsann.music.ui.SnackbarController.showMessage(
                                "Autoplay couldn't find another track. Playback has stopped."
                            )
                        }
                    }
                }
            }
        } else {
            audioService?.pause()
        }
    }

    fun playPreviousSong() {
        if (listenTogetherQueueAuthoritative || isListenTogetherGuest) return
        val songs = _songList.value
        if (songs.isEmpty()) return

        // Base subsequent taps on the pending destination, not the still-playing song.
        val currentIdx = pendingRapidNavigationIndex() ?: _currentSongIndex.value
        val shuffle = _isShuffleEnabled.value

        if (shuffle && songs.size > 1) {
            var previous = currentIdx
            while (previous == currentIdx) previous = shuffleRandom.nextInt(songs.size)
            scheduleRapidNavigation(songs[previous])
            return
        }

        val previousIndex = currentIdx - 1
        if (previousIndex < 0) {
            commitPendingRapidNavigation()
            audioService?.seekTo(0)
        } else {
            scheduleRapidNavigation(songs[previousIndex])
        }
    }

    fun getNextSongIndex(): Int = getNextSongIndexFrom(_currentSongIndex.value)

    private fun getNextSongIndexFrom(currentIdx: Int): Int {
        val songs = _songList.value
        if (songs.isEmpty()) return -1

        val mode = _repeatMode.value
        val shuffle = _isShuffleEnabled.value

        if (mode == REPEAT_INFINITY) return if (currentIdx in songs.indices) currentIdx else 0
        if (mode == REPEAT_ONCE) {
            val order = repeatOnceQueueOrder
            if (order.isEmpty()) return -1

            val nextId = when {
                repeatOncePosition + 1 < order.size -> order[repeatOncePosition + 1]
                repeatOncePass == 0 -> order.firstOrNull()
                else -> null
            } ?: return -1

            return songs.indexOfFirst { it.id == nextId }
        }
        if (shuffle && songs.size > 1) {
            var next = currentIdx
            while (next == currentIdx) next = shuffleRandom.nextInt(songs.size)
            return next
        }

        val next = currentIdx + 1
        return if (next >= songs.size) -1 else next
    }

    private fun scheduleNextSongPrefetch(selectedSongId: Long) {
        // Resolve the predicted neighbour on Main because shuffle/repeat cursors are
        // ViewModel-owned state. Only the network prefetch itself runs on IO.
        val nextIndex = getNextSongIndex()
        val nextSong = _songList.value.getOrNull(nextIndex) ?: return
        if (nextSong.isCachedTrack || nextSong.isDownloadedOffline) return
        val nextVideoId = nextSong.videoId?.takeIf { it.isNotBlank() } ?: return

        nextSongPrefetchJob?.cancel()
        nextSongPrefetchJob = bgScope.launch(Dispatchers.IO) {
            // A fast Next/Previous burst should prefetch only for the final selected song.
            delay(280L)
            if (_currentSong.value?.id != selectedSongId) return@launch
            YouTubeMusic.prefetchStream(nextVideoId)
        }
    }

    private var specJob: kotlinx.coroutines.Job? = null
    
    /**
     * Touch prediction prefetching. Call this on ACTION_DOWN.
     */
    fun specPrefetch(song: SongItem) {
        specJob?.cancel()
        specJob = bgScope.launch(Dispatchers.IO) {
            if (!song.isCachedTrack && !song.isDownloadedOffline) {
                song.videoId?.let { YouTubeMusic.prefetchStream(it) }
            }
        }
    }

    // ========== QUEUE MANAGEMENT ==========

    fun reorderQueue(
        fromIndex: Int,
        toIndex: Int,
        syncPlaybackService: Boolean = true
    ) {
        val songs = _songList.value
        val newList = com.dsann.music.util.OrderUtils.move(songs, fromIndex, toIndex)
        if (newList === songs) return

        _currentSongIndex.value = com.dsann.music.util.OrderUtils.moveIndex(
            currentIndex = _currentSongIndex.value,
            fromIndex = fromIndex,
            toIndex = toIndex
        )
        _songList.value = newList
        if (_repeatMode.value == REPEAT_ONCE) resetRepeatOnceCycleFromCurrent()
        if (syncPlaybackService) audioService?.refreshNativeQueue()
    }

    /** Clear every queued track except the one that is currently playing. */
    fun clearQueueExceptCurrentSong() {
        val playingSong = _currentSong.value
        val retainedQueue = playingSong?.let(::listOf) ?: emptyList()

        // Invalidate any recommendation request or stream prefetch for the old tail.
        // A late Autoplay response must not repopulate a queue the user just cleared.
        autoplayQueueGeneration += 1L
        autoplayFetchJob?.cancel()
        autoplayFetchJob = null
        autoplayFetchGeneration = -1L
        autoplayFetchedVideoIds.clear()
        nextSongPrefetchJob?.cancel()
        nextSongPrefetchJob = null

        _songList.value = retainedQueue
        _currentSongIndex.value = if (playingSong != null) 0 else -1

        if (_repeatMode.value == REPEAT_ONCE) {
            resetRepeatOnceCycleFromCurrent()
        }

        // AudioPlaybackService.refreshNativeQueue() keeps Media3's current item
        // and removes the remaining native queue entries without restarting playback.
        audioService?.refreshNativeQueue()
        savePlaybackState()
        Log.d(TAG, "Queue cleared; retained playing track: ${playingSong?.title ?: "none"}")
    }

    fun removeFromQueue(songId: Long) {
        val currentQueue = _songList.value
        val removeIndex = currentQueue.indexOfFirst { it.id == songId }
        if (removeIndex < 0) return
        val updatedQueue = currentQueue.toMutableList().apply { removeAt(removeIndex) }
        _songList.value = updatedQueue
        _currentSongIndex.value = _currentSong.value?.let { active ->
            updatedQueue.indexOfFirst { it.id == active.id }
        } ?: -1
        if (_repeatMode.value == REPEAT_ONCE) resetRepeatOnceCycleFromCurrent()
        audioService?.refreshNativeQueue()
        savePlaybackState()
    }

    fun addToQueue(song: SongItem) {
        val newList = ArrayList(_songList.value)
        if (newList.none { it.id == song.id }) {
            newList.add(song)
            _songList.value = newList
            if (_repeatMode.value == REPEAT_ONCE) resetRepeatOnceCycleFromCurrent()
            audioService?.refreshNativeQueue()
        }
    }

    fun addToQueueNext(song: SongItem) {
        val currentQueue = _songList.value
        val activeSong = _currentSong.value
        val activeIndex = _currentSongIndex.value.takeIf { index ->
            index in currentQueue.indices &&
                (activeSong == null || currentQueue[index].id == activeSong.id)
        } ?: activeSong?.let { active ->
            currentQueue.indexOfFirst { it.id == active.id }
        } ?: -1

        // Remove existing copies of the requested track before inserting it next. If the
        // requested track is currently playing, retain that exact queue occurrence.
        // Recalculate the active index while building the new list: removing a copy that
        // appeared before the current item shifts its index and caused "next of next".
        val newList = ArrayList<SongItem>(currentQueue.size + 1)
        var newCurrentIndex = -1
        currentQueue.forEachIndexed { index, queuedSong ->
            if (queuedSong.id != song.id || index == activeIndex) {
                if (index == activeIndex) newCurrentIndex = newList.size
                newList.add(queuedSong)
            }
        }

        val insertIndex = (newCurrentIndex + 1).coerceIn(0, newList.size)
        newList.add(insertIndex, song)

        _songList.value = newList
        _currentSongIndex.value = newCurrentIndex
        if (_repeatMode.value == REPEAT_ONCE) resetRepeatOnceCycleFromCurrent()
        audioService?.refreshNativeQueue()
        savePlaybackState()
    }

    // ========== UTILS ==========

    fun setShuffleEnabled(enabled: Boolean) {
        if (listenTogetherQueueAuthoritative && enabled) return
        if (_isShuffleEnabled.value != enabled) {
            _isShuffleEnabled.value = enabled
            if (_repeatMode.value == REPEAT_ONCE) {
                resetRepeatOnceCycleFromCurrent()
                audioService?.let { service ->
                    service.runWhenPlayerReady { service.refreshNativeQueue() }
                }
            }
            audioService?.setShuffleModeEnabled(enabled)
        }
    }
    fun toggleShuffle() {
        setShuffleEnabled(!_isShuffleEnabled.value)
    }


    override fun onCleared() {
        // Stop history tracking and stateIn collectors owned by this ViewModel.
        cleanupBgScope()
        super.onCleared()
        audioService = null
        _audioServiceState.value = null
    }
}
