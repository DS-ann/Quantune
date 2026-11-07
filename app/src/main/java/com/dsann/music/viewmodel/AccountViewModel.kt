package com.dsann.music.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dsann.music.data.innertube.AccountManager
import com.dsann.music.data.innertube.OnlineAlbum
import com.dsann.music.data.innertube.OnlineArtist
import com.dsann.music.data.innertube.OnlinePlaylist
import com.dsann.music.data.innertube.OnlineSong
import com.dsann.music.data.innertube.UserProfile
import com.dsann.music.data.innertube.YouTubeMusic
import com.dsann.music.util.NetworkFailureClassifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Orchestrates standard account retrieval loops sourcing aggregated library contents,
 * preference values, and basic user lifecycle bindings.
 */
class AccountViewModel(application: Application) : AndroidViewModel(application) {

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _offlineError = MutableStateFlow(false)
    val offlineError = _offlineError.asStateFlow()

    private val _historyOfflineError = MutableStateFlow(false)
    val historyOfflineError = _historyOfflineError.asStateFlow()

    private val _profileOfflineError = MutableStateFlow(false)
    val profileOfflineError = _profileOfflineError.asStateFlow()

    private val _playlists = MutableStateFlow<List<OnlinePlaylist>>(emptyList())
    val playlists = _playlists.asStateFlow()

    private val _artists = MutableStateFlow<List<OnlineArtist>>(emptyList())
    val artists = _artists.asStateFlow()

    private val _albums = MutableStateFlow<List<OnlineAlbum>>(emptyList())
    val albums = _albums.asStateFlow()

    private val _songs = MutableStateFlow<List<OnlineSong>>(emptyList())
    val songs = _songs.asStateFlow()

    private val _podcasts = MutableStateFlow<List<OnlinePlaylist>>(emptyList())
    val podcasts = _podcasts.asStateFlow()

    private val _history = MutableStateFlow<List<OnlineSong>>(emptyList())
    val history = _history.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile = _userProfile.asStateFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        loadProfile()
        loadLibrary()
    }

    fun loadProfile() {
        val context = getApplication<Application>()
        if (!AccountManager.isLoggedIn(context)) {
            _userProfile.value = null
            _profileOfflineError.value = false
            return
        }

        _profileOfflineError.value = false
        viewModelScope.launch {
            try {
                YouTubeMusic.accountProfile().onSuccess { profile ->
                    _userProfile.value = profile
                    _profileOfflineError.value = false
                }.onFailure { error ->
                    _profileOfflineError.value = NetworkFailureClassifier.isNoInternetFailure(
                        error,
                        context
                    )
                    android.util.Log.w("AccountViewModel", "Failed to load profile", error)
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _profileOfflineError.value = NetworkFailureClassifier.isNoInternetFailure(
                    error,
                    context
                )
                android.util.Log.w("AccountViewModel", "Profile request failed", error)
            }
        }
    }

    fun loadLibrary() {
        val context = getApplication<Application>()
        if (!AccountManager.isLoggedIn(context)) {
            _playlists.value = emptyList()
            _artists.value = emptyList()
            _albums.value = emptyList()
            _songs.value = emptyList()
            _podcasts.value = emptyList()
            return
        }

        _offlineError.value = false
        _historyOfflineError.value = false
        viewModelScope.launch {
            _isLoading.value = true
            var hasSuccessfulRequest = false
            var hasOfflineFailure = false
            fun recordFailure(error: Throwable) {
                if (NetworkFailureClassifier.isNoInternetFailure(error, context)) {
                    hasOfflineFailure = true
                }
            }
            try {
                // Load Playlists
                YouTubeMusic.libraryPlaylists().onSuccess { items ->
                    val filtered = items.filter { it.title.isNotEmpty() && !it.title.equals("New playlist", ignoreCase = true) }
                    _playlists.value = filtered
                    hasSuccessfulRequest = true
                }.onFailure {
                    recordFailure(it)
                    android.util.Log.w("AccountViewModel", "Failed to load playlists", it)
                }

                // Load Podcasts
                YouTubeMusic.libraryPodcasts().onSuccess { items ->
                    _podcasts.value = items
                    hasSuccessfulRequest = true
                }.onFailure { throwable ->
                    recordFailure(throwable)
                    val filteredPlaylists = _playlists.value
                    _podcasts.value = filteredPlaylists.filter { it.title.lowercase().contains("podcast") || it.title.lowercase().contains("show") }
                    android.util.Log.w("AccountViewModel", "Failed to load podcasts, using playlist fallback", throwable)
                }

                // Load Albums
                YouTubeMusic.libraryAlbums().onSuccess { items ->
                    _albums.value = items
                    hasSuccessfulRequest = true
                }.onFailure {
                    recordFailure(it)
                    android.util.Log.w("AccountViewModel", "Failed to load library albums", it)
                }

                // Load Liked Songs (from playlist "LM")
                YouTubeMusic.playlist("LM").onSuccess { page ->
                    _songs.value = page.songs
                    hasSuccessfulRequest = true
                }.onFailure {
                    recordFailure(it)
                    android.util.Log.w("AccountViewModel", "Failed to load liked songs from playlist LM", it)
                }

                // Load History
                YouTubeMusic.history().onSuccess { items ->
                    _history.value = items
                    _historyOfflineError.value = false
                    hasSuccessfulRequest = true
                }.onFailure {
                    _historyOfflineError.value = NetworkFailureClassifier.isNoInternetFailure(
                        it,
                        context
                    )
                    recordFailure(it)
                    android.util.Log.w("AccountViewModel", "Failed to load listening history", it)
                }

                // Load Artists and merge with artists from saved albums and songs
                var fetchedArtists = emptyList<OnlineArtist>()
                YouTubeMusic.libraryArtists().onSuccess { items ->
                    fetchedArtists = items
                    hasSuccessfulRequest = true
                }.onFailure {
                    recordFailure(it)
                    android.util.Log.w("AccountViewModel", "Failed to load library artists", it)
                }

                // Snapshot the lists on the ViewModel thread, then merge/sort potentially large
                // libraries away from Main so opening Library cannot hitch while artists are built.
                val albumsSnapshot = _albums.value
                val songsSnapshot = _songs.value
                val mergedArtists = withContext(Dispatchers.Default) {
                    val compiledMap = mutableMapOf<String, OnlineArtist>()

                    // 1. Add explicitly followed artists
                    fetchedArtists.forEach { artist ->
                        compiledMap[artist.browseId] = artist
                    }

                    // 2. Add artists from saved albums
                    albumsSnapshot.forEach { album ->
                        album.artists.forEach { ref ->
                            val artistId = ref.id
                            if (artistId != null && !compiledMap.containsKey(artistId)) {
                                compiledMap[artistId] = OnlineArtist(
                                    browseId = artistId,
                                    title = ref.name,
                                    thumbnailUrl = album.thumbnailUrl
                                )
                            }
                        }
                    }

                    // 3. Add artists from liked songs
                    songsSnapshot.forEach { song ->
                        val artistId = song.artistId
                        if (artistId != null && !compiledMap.containsKey(artistId)) {
                            compiledMap[artistId] = OnlineArtist(
                                browseId = artistId,
                                title = song.artist,
                                thumbnailUrl = song.thumbnailUrl
                            )
                        }
                    }

                    compiledMap.values.toList().sortedBy { it.title.lowercase() }
                }
                _artists.value = mergedArtists
                _offlineError.value = hasOfflineFailure
            } catch (e: Exception) {
                recordFailure(e)
                _offlineError.value = hasOfflineFailure
                android.util.Log.e("AccountViewModel", "Error fetching library", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun onLoggedIn(cookies: String) {
        val context = getApplication<Application>()
        AccountManager.saveLoginState(context, cookies)
        YouTubeMusic.setCookie(cookies)
        refreshAll()
    }

    fun toggleSync(enabled: Boolean) {
        AccountManager.setSyncEnabled(getApplication(), enabled)
    }
    
    fun logout() {
        AccountManager.logout(getApplication())
        _userProfile.value = null
        _offlineError.value = false
        _historyOfflineError.value = false
        _profileOfflineError.value = false
        _playlists.value = emptyList()
        _artists.value = emptyList()
        _albums.value = emptyList()
        _songs.value = emptyList()
        _podcasts.value = emptyList()
        _history.value = emptyList()
    }
}
