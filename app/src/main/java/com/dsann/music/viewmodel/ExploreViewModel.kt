package com.dsann.music.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dsann.music.data.innertube.AlbumPage
import com.dsann.music.data.innertube.ArtistPage
import com.dsann.music.data.innertube.HomeSection
import com.dsann.music.data.innertube.HomeCategory
import com.dsann.music.data.innertube.HomeCategoryCacheParser
import com.dsann.music.data.innertube.InnerTubeClient
import com.dsann.music.data.innertube.OnlineSong
import com.dsann.music.data.innertube.OnlineAlbum
import com.dsann.music.data.innertube.OnlineArtist
import com.dsann.music.data.innertube.OnlinePlaylist
import com.dsann.music.data.innertube.PlaylistPage
import com.dsann.music.data.innertube.SearchFilter
import com.dsann.music.data.innertube.SearchItem
import com.dsann.music.data.innertube.YouTubeMusic
import com.dsann.music.ui.explore.buildExploreImageRequest
import com.dsann.music.util.NetworkFailureClassifier
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import java.util.concurrent.Executors

enum class DetailType {
    ALBUM, PLAYLIST, ARTIST, SECTION
}

/**
 * Serializes small Explore cache snapshots outside viewModelScope. Android may destroy
 * the Activity's ViewModels while the app is backgrounded; a ViewModel-owned write could
 * otherwise be cancelled before SharedPreferences receives the last feed snapshot.
 */
private object ExplorePreferenceCacheWriter {
    private val versions = HashMap<String, Long>()
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "DSMusic-ExploreCache").apply { isDaemon = true }
    }

    fun enqueue(key: String, write: () -> Unit) {
        val version = synchronized(versions) {
            val next = (versions[key] ?: 0L) + 1L
            versions[key] = next
            next
        }
        executor.execute {
            val isLatest = synchronized(versions) { versions[key] == version }
            if (isLatest) {
                runCatching(write)
            }
        }
    }
}

/**
 * ViewModel for the Explore (Online Music) tab.
 * Manages search, home feed, and online playback state.
 */
class ExploreViewModel(application: Application) : AndroidViewModel(application) {

    var cameFromLibrary: Boolean = false

    private val TAG = "ExploreViewModel"

    // ========== Unified MVI State ==========

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private val _subscriptionChanged = MutableStateFlow(false)
    val subscriptionChanged = _subscriptionChanged.asStateFlow()

    fun consumeSubscriptionChanged() {
        _subscriptionChanged.value = false
    }

    // Public delegated StateFlows for external backward compatibility
    val currentOnlineSong: StateFlow<OnlineSong?> = uiState
        .map { it.currentOnlineSong }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val onlineQueue: StateFlow<List<OnlineSong>> = uiState
        .map { it.onlineQueue }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentOnlineIndex: StateFlow<Int> = uiState
        .map { it.currentOnlineIndex }
        .stateIn(viewModelScope, SharingStarted.Eagerly, -1)

    val searchFilter: StateFlow<SearchFilter?> = uiState
        .map { it.searchFilter }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isLoadingMoreHome: StateFlow<Boolean> = uiState
        .map { it.isLoadingMoreHome }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val error: StateFlow<String?> = uiState
        .map { it.error }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val streamUrl: StateFlow<String?> = uiState
        .map { it.streamUrl }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isLoadingStream: StateFlow<Boolean> = uiState
        .map { it.isLoadingStream }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var lastDetailRetry: (() -> Unit)? = null
    private var searchContinuation: String? = null
    private var homeContinuationToken: String? = null
    private var exploreContinuationToken: String? = null
    private var homeLoaded = false
    private var explorePageActive = false
    private var genresLoaded = false
    private var genresJob: Job? = null
    private var cookieSyncInitialized = false
    private var lastSyncedCookie: String? = null

    fun setMood(mood: String?) {
        if (_uiState.value.currentMood == mood) return
        _uiState.update { it.copy(currentMood = mood) }
        homeLoaded = false // Force reload
        loadHomeFeed()
    }

    // ========== Search History ==========

    private val prefs = application.getSharedPreferences("explore_prefs", 0)
    private val SEARCH_HISTORY_KEY = "search_history"
    private val HOME_FEED_CACHE_KEY = "home_feed_cache_v1"
    private val GENRES_CACHE_KEY = "moods_and_genres_cache_v1"
    private val gson = com.google.gson.Gson()
    private val MAX_HISTORY = 20

    init {
        loadSearchHistory()
        val speedDialSongs = getPinnedSpeedDialSongs()
        val notInterested = prefs.getStringSet("not_interested_ids", null)?.toSet() ?: emptySet()
        _uiState.update { 
            it.copy(
                pinnedSpeedDialIds = speedDialSongs.map { s -> s.videoId }.toSet(),
                notInterestedIds = notInterested
            )
        }
        syncCookieFromDisk()
        homeLoaded = restoreHomeFeedCache()
        genresLoaded = restoreGenresCache()
    }

    private fun loadSearchHistory() {
        val historyString = prefs.getString("search_history_list", null)
        if (historyString != null) {
            val list = historyString.split("\n").filter { it.isNotBlank() }
            _uiState.update { it.copy(searchHistory = list) }
        } else {
            // Fallback & migration from old set-based format
            val oldSet = prefs.getStringSet(SEARCH_HISTORY_KEY, null)
            if (oldSet != null) {
                val list = oldSet.toList()
                _uiState.update { it.copy(searchHistory = list) }
                prefs.edit {
                        putString("search_history_list", list.joinToString("\n"))
                        .remove(SEARCH_HISTORY_KEY)
                }
            } else {
                _uiState.update { it.copy(searchHistory = emptyList()) }
            }
        }
    }

    private fun addToSearchHistory(query: String) {
        if (query.isBlank()) return
        val current = _uiState.value.searchHistory.toMutableList()
        current.remove(query) // Remove duplicate
        current.add(0, query) // Add to top
        val trimmed = current.take(MAX_HISTORY)
        _uiState.update { it.copy(searchHistory = trimmed) }
        prefs.edit {
                putString("search_history_list", trimmed.joinToString("\n"))
            }
    }

    fun removeFromSearchHistory(query: String) {
        val current = _uiState.value.searchHistory.toMutableList()
        current.remove(query)
        _uiState.update { it.copy(searchHistory = current) }
        prefs.edit {
            putString("search_history_list", current.joinToString("\n"))
        }
    }

    fun clearSearchHistory() {
        _uiState.update { it.copy(searchHistory = emptyList()) }
        prefs.edit {
            remove("search_history_list")
                .remove(SEARCH_HISTORY_KEY)
        }
    }

    // Shared event to signal that the host fragment should propagate current state to Player service INSTANTLY!
    private val _playbackTriggerEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val playbackTriggerEvent = _playbackTriggerEvent.asSharedFlow()

    private var searchJob: Job? = null
    private var suggestJob: Job? = null
    private var personalizationJob: Job? = null
    private var homeFeedJob: Job? = null
    private var homeRefreshJob: Job? = null
    private var homeContinuationJob: Job? = null
    private var searchContinuationJob: Job? = null
    private var detailLoadJob: Job? = null
    private var detailLoadGeneration: Long = 0L
    private var artistResolveJob: Job? = null

    private data class CachedHomeFeed(
        val sections: List<CachedHomeFeedSection> = emptyList(),
        val moods: List<String> = emptyList(),
        val currentMood: String? = null,
        val homeContinuation: String? = null,
        val exploreContinuation: String? = null,
        val loaded: Boolean = false
    )

    private data class CachedGenres(
        val categories: List<HomeCategory> = emptyList()
    )

    private data class CachedHomeFeedSection(
        val title: String,
        val items: List<CachedHomeFeedItem>,
        val browseEndpoint: String? = null,
        val params: String? = null
    )

    private data class CachedHomeFeedItem(
        val type: String,
        val song: OnlineSong? = null,
        val album: OnlineAlbum? = null,
        val artist: OnlineArtist? = null,
        val playlist: OnlinePlaylist? = null
    )

    /**
     * Stop page content requests when Explore is hidden but preserve its already-loaded state.
     * Thumbnail prefetches remain cache-only and do not cause hidden pages to be composed.
     */
    fun setExplorePageActive(active: Boolean, loadHomeOnActivate: Boolean = true) {
        if (explorePageActive == active) {
            if (active && loadHomeOnActivate && !homeLoaded) loadHomeFeed()
            return
        }
        explorePageActive = active
        if (active) {
            if (loadHomeOnActivate && !homeLoaded) loadHomeFeed()
            return
        }

        searchJob?.cancel()
        searchJob = null
        suggestJob?.cancel()
        suggestJob = null
        personalizationJob?.cancel()
        personalizationJob = null
        // A first load or an explicit refresh must finish even when the user
        // switches tabs. Its result is cached, so returning to Explore is instant.
        homeContinuationJob?.cancel()
        homeContinuationJob = null
        searchContinuationJob?.cancel()
        searchContinuationJob = null
        detailLoadGeneration += 1L
        detailLoadJob?.cancel()
        detailLoadJob = null
        artistResolveJob?.cancel()
        artistResolveJob = null
        _uiState.update {
            it.copy(
                isSearching = false,
                isLoadingMoreHome = false,
                isLoadingDetail = false
            )
        }
    }


    // ========== Search Methods ==========

    fun setSearchQuery(query: String) {
        _uiState.update {
            it.copy(searchQuery = query, suggestions = emptyList(), suggestionItems = emptyList())
        }
        if (query.isNotBlank()) {
            fetchSuggestions(query)
        } else {
            suggestJob?.cancel()
            _uiState.update { it.copy(suggestions = emptyList(), suggestionItems = emptyList()) }
        }
    }

    fun setSearchFilter(filter: SearchFilter?) {
        _uiState.update { it.copy(searchFilter = filter) }
        // Re-search with filter if we have a query
        val query = _uiState.value.searchQuery
        if (query.isNotBlank()) {
            search(query)
        }
    }

    fun search(query: String) {
        val trimmedQuery = query.trim()
        searchJob?.cancel()
        _uiState.update { 
            it.copy(
                searchQuery = query,
                isSearching = true,
                searchOfflineError = false,
                suggestions = emptyList(),
                suggestionItems = emptyList(),
                searchResults = emptyList(),
                searchTopResult = null,
                searchSections = emptyList()
            )
        }

        searchJob = viewModelScope.launch {
            try {
                if (trimmedQuery.isNotEmpty()) {
                    addToSearchHistory(trimmedQuery)
                }
                val activeFilter = _uiState.value.searchFilter
                val result = if (activeFilter == null) {
                    YouTubeMusic.searchTop(trimmedQuery)
                } else {
                    YouTubeMusic.search(trimmedQuery, activeFilter)
                }
                result.onSuccess { searchResult ->
                    // Filter duplicates to prevent duplicate keys in lazy lists
                    val seenIds = mutableSetOf<String>()
                    val uniqueItems = searchResult.items.filter { item ->
                        val id = when (item) {
                            is SearchItem.Song -> "song-${item.song.videoId}"
                            is SearchItem.Album -> "album-${item.album.browseId}"
                            is SearchItem.Artist -> "artist-${item.artist.browseId}"
                            is SearchItem.Playlist -> "playlist-${item.playlist.playlistId}"
                        }
                        val isNotInterested = item is SearchItem.Song && _uiState.value.notInterestedIds.contains(item.song.videoId)
                        !isNotInterested && seenIds.add(id)
                    }
                    val visibleTop = searchResult.topResult?.takeUnless { top ->
                        top is SearchItem.Song && _uiState.value.notInterestedIds.contains(top.song.videoId)
                    }
                    val visibleSections = searchResult.sections.mapNotNull { section ->
                        val visibleItems = section.items.filterNot { item ->
                            item is SearchItem.Song && _uiState.value.notInterestedIds.contains(item.song.videoId)
                        }
                        visibleItems.takeIf { it.isNotEmpty() }?.let { section.copy(items = it) }
                    }
                    val visibleResults = uniqueItems.filterNot { item ->
                        visibleTop != null && when {
                            item is SearchItem.Song && visibleTop is SearchItem.Song ->
                                item.song.videoId == visibleTop.song.videoId
                            item is SearchItem.Album && visibleTop is SearchItem.Album ->
                                item.album.browseId == visibleTop.album.browseId
                            item is SearchItem.Artist && visibleTop is SearchItem.Artist ->
                                item.artist.browseId == visibleTop.artist.browseId
                            item is SearchItem.Playlist && visibleTop is SearchItem.Playlist ->
                                item.playlist.playlistId == visibleTop.playlist.playlistId
                            else -> false
                        }
                    }

                    _uiState.update {
                        it.copy(
                            searchResults = visibleResults,
                            searchTopResult = visibleTop,
                            searchSections = visibleSections,
                            suggestions = emptyList(),
                            suggestionItems = emptyList(),
                            searchOfflineError = false
                        )
                    }
                    searchContinuation = searchResult.continuation
                    // Only warm visible artwork and the first likely playback target. Larger prefetch
                    // bursts compete with scrolling and playback on low-RAM/older Android devices.
                    prefetchThumbnails(uniqueItems.take(6))
                    uniqueItems
                        .filterIsInstance<SearchItem.Song>()
                        .firstOrNull()
                        ?.let { YouTubeMusic.prefetchStream(it.song.videoId) }
                }
                result.onFailure { e ->
                    Log.e(TAG, "Search failed", e)
                    _uiState.update {
                        it.copy(
                            error = "Search failed: ${e.message}",
                            searchOfflineError = NetworkFailureClassifier.isNoInternetFailure(
                                e,
                                getApplication<Application>()
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Search error", e)
                _uiState.update {
                    it.copy(
                        error = e.message,
                        searchOfflineError = NetworkFailureClassifier.isNoInternetFailure(
                            e,
                            getApplication<Application>()
                        )
                    )
                }
            } finally {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun loadMoreResults() {
        if (!explorePageActive) return
        val cont = searchContinuation ?: return
        if (_uiState.value.isSearching) return
        
        // Synchronously set to true to prevent any other triggers in the same frame/recomposition loop
        _uiState.update { it.copy(isSearching = true) }

        searchContinuationJob?.cancel()
        searchContinuationJob = viewModelScope.launch {
            try {
                val result = YouTubeMusic.searchContinuation(cont, _uiState.value.searchFilter)
                result.onSuccess { searchResult ->
                    var newItemsToPrefetch = emptyList<SearchItem>()
                    _uiState.update { state ->
                        val currentList = state.searchResults
                        val seenIds = currentList.map { item ->
                            when (item) {
                                is SearchItem.Song -> "song-${item.song.videoId}"
                                is SearchItem.Album -> "album-${item.album.browseId}"
                                is SearchItem.Artist -> "artist-${item.artist.browseId}"
                                is SearchItem.Playlist -> "playlist-${item.playlist.playlistId}"
                            }
                        }.toMutableSet()

                        val uniqueNewItems = searchResult.items.filter { item ->
                            val id = when (item) {
                                is SearchItem.Song -> "song-${item.song.videoId}"
                                is SearchItem.Album -> "album-${item.album.browseId}"
                                is SearchItem.Artist -> "artist-${item.artist.browseId}"
                                is SearchItem.Playlist -> "playlist-${item.playlist.playlistId}"
                            }
                            val isNotInterested = item is SearchItem.Song && _uiState.value.notInterestedIds.contains(item.song.videoId)
                            !isNotInterested && seenIds.add(id)
                        }

                        searchContinuation = searchResult.continuation
                        newItemsToPrefetch = uniqueNewItems
                        state.copy(searchResults = currentList + uniqueNewItems)
                    }
                    prefetchThumbnails(newItemsToPrefetch.take(8))
                }.onFailure { failure ->
                    _uiState.update {
                        it.copy(
                            searchOfflineError = NetworkFailureClassifier.isNoInternetFailure(
                                failure,
                                getApplication<Application>()
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Load more failed", e)
                _uiState.update {
                    it.copy(
                        searchOfflineError = NetworkFailureClassifier.isNoInternetFailure(
                            e,
                            getApplication<Application>()
                        )
                    )
                }
            } finally {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    private fun fetchSuggestions(query: String) {
        if (!explorePageActive) return
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(300.milliseconds)
            try {
                val result = YouTubeMusic.searchSuggestionsWithItems(query)
                result.onSuccess { suggestions ->
                    _uiState.update {
                        if (it.searchQuery != query) it
                        else it.copy(
                            suggestions = suggestions.queries.take(8),
                            suggestionItems = suggestions.items.take(3)
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Suggestions are supplementary; the submitted query remains searchable.
            }
        }
    }

    fun clearSearch() {
        _uiState.update { 
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                searchOfflineError = false,
                suggestions = emptyList(),
                suggestionItems = emptyList(),
                searchTopResult = null,
                searchSections = emptyList(),
                searchFilter = null
            )
        }
        searchContinuation = null
    }

    // ========== Home Feed ==========

    private suspend fun getPersonalizedSections(): List<HomeSection> {
        val historyString = prefs.getString("recent_artists_list", "") ?: ""
        if (historyString.isBlank()) return emptyList()
        // Limit the optional personalization queries on slower devices.
        val artists = historyString.split("|").map { it.trim() }.filter { it.isNotBlank() }.take(3)
        
        val sections = mutableListOf<HomeSection>()
        val allMixedSongs = mutableListOf<SearchItem>()
        
        artists.forEachIndexed { index, artist ->
            try {
                val result = YouTubeMusic.search(artist, SearchFilter.SONGS)
                val searchResult = result.getOrNull()
                if (searchResult != null && searchResult.items.isNotEmpty()) {
                    val title = when (index) {
                        0 -> "More from $artist"
                        1 -> "Because you listened to $artist"
                        else -> "Vibes like $artist"
                    }
                    // Shuffle the fetched items to ensure fresh songs show up on every refresh
                    val shuffledItems = searchResult.items.shuffled()
                    sections.add(HomeSection(title, shuffledItems.take(10)))
                    allMixedSongs.addAll(shuffledItems)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Failed to load personalized section for $artist", e)
            }
        }
        
        // Generate a combined "Your Personalized Mix" that dynamically shuffles on every refresh
        if (allMixedSongs.isNotEmpty()) {
            val shuffledMix = allMixedSongs.distinctBy { item ->
                when (item) {
                    is SearchItem.Song -> item.song.videoId
                    is SearchItem.Album -> item.album.browseId
                    is SearchItem.Artist -> item.artist.browseId
                    is SearchItem.Playlist -> item.playlist.playlistId
                }
            }.shuffled().take(12)
            
            if (shuffledMix.isNotEmpty()) {
                sections.add(0, HomeSection("Your Personalized Mix", shuffledMix))
            }
        }
        
        return sections
    }

    private fun syncCookieFromDisk() {
        val ctx = getApplication<Application>()
        val cookie = ctx.getSharedPreferences("AppSettings", 0).getString("yt_cookies", null)
        if (!cookieSyncInitialized || cookie != lastSyncedCookie) {
            InnerTubeClient.cookie = cookie
            lastSyncedCookie = cookie
            cookieSyncInitialized = true
        }
    }

    private fun restoreHomeFeedCache(): Boolean {
        val json = prefs.getString(HOME_FEED_CACHE_KEY, null) ?: return false
        return try {
            // CachedHomeFeed contains nested lists of custom models. Using ::class.java
            // erases those element types and Gson silently creates LinkedTreeMap entries,
            // which later crash when Compose iterates them as CachedHomeFeedSection.
            val cacheType = object : com.google.gson.reflect.TypeToken<CachedHomeFeed>() {}.type
            val cached: CachedHomeFeed = gson.fromJson(json, cacheType) ?: return false
            val restoredSections = cached.sections.mapNotNull { section ->
                val restoredItems = section.items.mapNotNull { item ->
                    when (item.type) {
                        "song" -> item.song?.let { SearchItem.Song(it) }
                        "album" -> item.album?.let { SearchItem.Album(it) }
                        "artist" -> item.artist?.let { SearchItem.Artist(it) }
                        "playlist" -> item.playlist?.let { SearchItem.Playlist(it) }
                        else -> null
                    }
                }
                if (restoredItems.isEmpty()) null else HomeSection(
                    title = section.title,
                    items = restoredItems,
                    browseEndpoint = section.browseEndpoint,
                    params = section.params
                )
            }
            // A successful feed can legitimately contain no shelves (for example an
            // empty Explore response). Preserve its loaded marker to avoid refetching
            // every time the Activity/Explore ViewModel is recreated.
            val hasSnapshot = cached.loaded || restoredSections.isNotEmpty() || cached.moods.isNotEmpty()
            if (!hasSnapshot) return false
            homeContinuationToken = cached.homeContinuation
            exploreContinuationToken = cached.exploreContinuation
            _uiState.update { state ->
                state.copy(
                    homeSections = restoredSections,
                    homeMoods = cached.moods,
                    currentMood = cached.currentMood,
                    isLoadingHome = false,
                    homeFeedError = null
                )
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Ignoring invalid cached Explore feed", e)
            false
        }
    }

    private fun persistHomeFeedCache() {
        val state = _uiState.value
        if (!homeLoaded && state.homeSections.isEmpty() && state.homeMoods.isEmpty()) return
        val cached = CachedHomeFeed(
            sections = state.homeSections.map { section ->
                CachedHomeFeedSection(
                    title = section.title,
                    browseEndpoint = section.browseEndpoint,
                    params = section.params,
                    items = section.items.mapNotNull { item ->
                        when (item) {
                            is SearchItem.Song -> CachedHomeFeedItem("song", song = item.song)
                            is SearchItem.Album -> CachedHomeFeedItem("album", album = item.album)
                            is SearchItem.Artist -> CachedHomeFeedItem("artist", artist = item.artist)
                            is SearchItem.Playlist -> CachedHomeFeedItem("playlist", playlist = item.playlist)
                        }
                    }
                )
            },
            moods = state.homeMoods,
            currentMood = state.currentMood,
            homeContinuation = homeContinuationToken,
            exploreContinuation = exploreContinuationToken,
            loaded = homeLoaded
        )
        ExplorePreferenceCacheWriter.enqueue(HOME_FEED_CACHE_KEY) {
            val json = gson.toJson(cached)
            prefs.edit().putString(HOME_FEED_CACHE_KEY, json).commit()
        }
    }

    private fun restoreGenresCache(): Boolean {
        val json = prefs.getString(GENRES_CACHE_KEY, null) ?: return false
        return runCatching {
            // Validate and rebuild every entry explicitly. This repairs old caches containing
            // Gson LinkedTreeMap values and does not depend on release generic Signature data.
            val categories = HomeCategoryCacheParser.parse(json)
            if (categories.isEmpty()) return false
            _uiState.update { it.copy(homeCategories = categories, genresError = null) }
            true
        }.getOrElse { error ->
            Log.w(TAG, "Ignoring invalid moods-and-genres cache", error)
            false
        }
    }

    private fun persistGenresCache(categories: List<HomeCategory>) {
        if (categories.isEmpty()) return
        val cached = CachedGenres(categories)
        ExplorePreferenceCacheWriter.enqueue(GENRES_CACHE_KEY) {
            prefs.edit().putString(GENRES_CACHE_KEY, gson.toJson(cached)).commit()
        }
    }

    /**
     * Search discovery genres come from YouTube Music's dedicated catalogue browse
     * endpoint (FEmusic_moods_and_genres), not the Home feed's chip cloud.
     */
    fun loadMoodsAndGenres(forceRefresh: Boolean = false) {
        if (!forceRefresh && (genresLoaded || _uiState.value.homeCategories.isNotEmpty())) return
        if (genresJob?.isActive == true) return
        _uiState.update {
            it.copy(isLoadingGenres = true, genresError = null, genresOfflineError = false)
        }
        genresJob = viewModelScope.launch {
            try {
                val result = YouTubeMusic.moodsAndGenres()
                result.onSuccess { categories ->
                    _uiState.update {
                        it.copy(
                            homeCategories = categories,
                            isLoadingGenres = false,
                            genresError = null,
                            genresOfflineError = false
                        )
                    }
                    genresLoaded = categories.isNotEmpty()
                    persistGenresCache(categories)
                }.onFailure { error ->
                    Log.w(TAG, "Moods and genres failed to load", error)
                    _uiState.update {
                        it.copy(
                            isLoadingGenres = false,
                            genresError = "Couldn't load genres. Check your connection and try again.",
                            genresOfflineError = NetworkFailureClassifier.isNoInternetFailure(
                                error,
                                getApplication<Application>()
                            )
                        )
                    }
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Log.w(TAG, "Moods and genres request failed", error)
                _uiState.update {
                    it.copy(
                        isLoadingGenres = false,
                        genresError = "Couldn't load genres. Check your connection and try again.",
                        genresOfflineError = NetworkFailureClassifier.isNoInternetFailure(
                            error,
                            getApplication<Application>()
                        )
                    )
                }
            } finally {
                genresJob = null
            }
        }
    }

    fun forceReloadHomeFeed() {
        val diskCookie = getApplication<Application>()
            .getSharedPreferences("AppSettings", 0)
            .getString("yt_cookies", null)
        if (cookieSyncInitialized && diskCookie == lastSyncedCookie) return
        syncCookieFromDisk()
        homeLoaded = false
        if (explorePageActive) loadHomeFeed()
    }

    private suspend fun fetchMergedHomeFeed(): List<HomeSection> = coroutineScope {
        // Each source has its own continuation chain. Clear both before rebuilding the feed.
        homeContinuationToken = null
        exploreContinuationToken = null
        val sections = mutableListOf<HomeSection>()
        val mood = _uiState.value.currentMood

        if (mood != null) {
            // Parallel Synthesis: Mood specific feed creation
            try {
                val playTask = async { YouTubeMusic.search(mood, SearchFilter.PLAYLISTS) }
                val songTask = async { YouTubeMusic.search("$mood music", SearchFilter.SONGS) }
                val albumTask = async { YouTubeMusic.search("$mood releases", SearchFilter.ALBUMS) }

                // Wait for all simultaneously
                val responses = awaitAll(playTask, songTask, albumTask)
                if (responses.none { it.isSuccess }) {
                    val cause = responses.mapNotNull { it.exceptionOrNull() }.firstOrNull()
                    throw java.io.IOException(
                        "Network unavailable. Check your connection and try again.",
                        cause
                    )
                }

                responses[0].onSuccess { res ->
                    if (res.items.isNotEmpty()) sections.add(HomeSection("$mood Curations", res.items.shuffled().take(12)))
                }
                responses[1].onSuccess { res ->
                    if (res.items.isNotEmpty()) sections.add(HomeSection("Top $mood Tracks", res.items.take(20)))
                }
                responses[2].onSuccess { res ->
                    if (res.items.isNotEmpty()) sections.add(HomeSection("$mood Spotlight", res.items.shuffled().take(12)))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Failed to synthesize mood feed", e)
            }
            return@coroutineScope sections
        }

        // Standard Feed Scenario: Launch Parallel background queries
        val homeTask = if (InnerTubeClient.cookie != null) {
            async { YouTubeMusic.home() }
        } else null

        val exploreTask = async { YouTubeMusic.explore() }

        // Fetch the online feed first so local personalization cannot delay first content.

        // 2. Await Network streams
        val homeResult = homeTask?.await()
        val exploreResult = exploreTask.await()
        if ((homeResult == null || homeResult.isFailure) && exploreResult.isFailure) {
            val cause = exploreResult.exceptionOrNull() ?: homeResult?.exceptionOrNull()
            throw java.io.IOException(
                "Network unavailable. Check your connection and try again.",
                cause
            )
        }
        val homePage = homeResult?.getOrNull()
        val explorePage = exploreResult.getOrNull()
        homeContinuationToken = homePage?.continuation
        exploreContinuationToken = explorePage?.continuation
            ?.takeIf { it != homeContinuationToken }

        // Extract Moods
        val allMoods = mutableListOf<String>()
        homePage?.moods?.let { allMoods.addAll(it) }
        if (allMoods.isEmpty()) {
            explorePage?.moods?.let { allMoods.addAll(it) }
        }
        _uiState.update { state ->
            state.copy(
                homeMoods = allMoods.distinct().ifEmpty { state.homeMoods }
            )
        }

        // 3. Merge Results safely in explicit priority order
        // Priority A: Personalized Home Content from YT
        homePage?.sections?.let { sections.addAll(it) }

        // Priority B: Generic trending explore feeds
        explorePage?.sections?.forEach { sec ->
            if (sections.none { it.title.equals(sec.title, ignoreCase = true) }) {
                sections.add(sec)
            }
        }

        // 4. Post-processing: Filter out disliked / not interested songs, and inject/merge local Speed Dial!
        val notInterested = _uiState.value.notInterestedIds
        val pinnedSongs = getPinnedSpeedDialSongs().filterNot { notInterested.contains(it.videoId) }

        val processedSections = sections.map { sec ->
            sec.copy(items = sec.items.filterNot { item ->
                item is SearchItem.Song && notInterested.contains(item.song.videoId)
            })
        }.filter { it.items.isNotEmpty() }.toMutableList()

        // Handle Speed dial merging
        if (pinnedSongs.isNotEmpty()) {
            val speedDialIndex = processedSections.indexOfFirst { it.title.contains("speed dial", ignoreCase = true) }
            if (speedDialIndex != -1) {
                val originalSec = processedSections[speedDialIndex]
                val originalItems = originalSec.items
                val pinnedItems = pinnedSongs.map { SearchItem.Song(it) }
                val mergedItems = (pinnedItems + originalItems).distinctBy { item ->
                    when (item) {
                        is SearchItem.Song -> item.song.videoId
                        is SearchItem.Album -> item.album.browseId
                        is SearchItem.Artist -> item.artist.browseId
                        is SearchItem.Playlist -> item.playlist.playlistId
                    }
                }.take(9)
                processedSections[speedDialIndex] = originalSec.copy(items = mergedItems)
            } else {
                val pinnedItems = pinnedSongs.map { SearchItem.Song(it) }.take(9)
                val insertIndex = if (processedSections.isNotEmpty() && processedSections[0].title.contains("personalized mix", ignoreCase = true)) 1 else 0
                processedSections.add(insertIndex, HomeSection("Speed dial", pinnedItems))
            }
        }

        processedSections
    }

    private fun loadPersonalizedSectionsInBackground() {
        if (!explorePageActive || _uiState.value.currentMood != null || personalizationJob?.isActive == true) return

        personalizationJob = viewModelScope.launch {
            try {
                val localStats = getPersonalizedSections()
                if (localStats.isNotEmpty()) {
                    _uiState.update { state ->
                        if (state.currentMood != null) {
                            state
                        } else {
                            val updatedSections = state.homeSections.toMutableList()
                            val additions = localStats.filter { section ->
                                updatedSections.none {
                                    it.title.equals(section.title, ignoreCase = true)
                                }
                            }
                            if (additions.isEmpty()) {
                                state
                            } else {
                                updatedSections.addAll(minOf(3, updatedSections.size), additions)
                                state.copy(homeSections = updatedSections)
                            }
                        }
                    }
                    persistHomeFeedCache()
                    prefetchThumbnails(localStats.take(2).flatMap { it.items.take(3) })
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "Personalized sections failed to load", e)
            }
        }
    }

    fun loadHomeFeed() {
        if (!explorePageActive || homeLoaded || _uiState.value.isLoadingHome) return

        syncCookieFromDisk()
        _uiState.update {
            it.copy(
                isLoadingHome = true,
                homeFeedError = null,
                homeFeedOfflineError = false
            )
        }
        homeFeedJob?.cancel()
        homeFeedJob = viewModelScope.launch {
            try {
                val mergedSections = fetchMergedHomeFeed()
                // Publish the main feed immediately; personalization is optional and loaded afterwards.
                _uiState.update {
                    it.copy(
                        homeSections = mergedSections,
                        isLoadingHome = false,
                        homeFeedError = null,
                        homeFeedOfflineError = false
                    )
                }
                homeLoaded = true
                persistHomeFeedCache()
                prefetchThumbnails(mergedSections.take(3).flatMap { it.items.take(3) })
                loadPersonalizedSectionsInBackground()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Home feed error", e)
                homeLoaded = _uiState.value.homeSections.isNotEmpty()
                _uiState.update {
                    it.copy(
                        homeFeedError = "Couldn't load Explore. Please try again.",
                        homeFeedOfflineError = NetworkFailureClassifier.isNoInternetFailure(
                            e,
                            getApplication<Application>()
                        )
                    )
                }
            } finally {
                _uiState.update { if (it.isLoadingHome) it.copy(isLoadingHome = false) else it }
            }
        }
    }

    fun refreshHomeFeed() {
        if (!explorePageActive) return
        syncCookieFromDisk()
        homeLoaded = false
        _uiState.update {
            it.copy(
                isRefreshing = true,
                homeFeedError = null,
                homeFeedOfflineError = false
            )
        }
        homeFeedJob?.cancel()
        homeRefreshJob?.cancel()
        homeRefreshJob = viewModelScope.launch {
            try {
                val mergedSections = fetchMergedHomeFeed()
                _uiState.update {
                    it.copy(
                        homeSections = mergedSections,
                        homeFeedError = null,
                        homeFeedOfflineError = false
                    )
                }
                homeLoaded = true
                persistHomeFeedCache()
                prefetchThumbnails(mergedSections.take(3).flatMap { it.items.take(3) })
                loadPersonalizedSectionsInBackground()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Refresh error", e)
                homeLoaded = _uiState.value.homeSections.isNotEmpty()
                _uiState.update {
                    it.copy(
                        homeFeedError = "Couldn't refresh Explore. Please try again.",
                        homeFeedOfflineError = NetworkFailureClassifier.isNoInternetFailure(
                            e,
                            getApplication<Application>()
                        )
                    )
                }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun loadMoreHomeSections() {
        if (!explorePageActive) return
        // Prefer the authenticated Home continuation, then continue the anonymous Explore
        // feed. Without this fallback, signed-out users permanently stop after page one.
        val loadingHomeContinuation = homeContinuationToken != null
        val token = homeContinuationToken ?: exploreContinuationToken ?: return
        if (_uiState.value.isLoadingHome || _uiState.value.isLoadingMoreHome) return

        _uiState.update { it.copy(isLoadingMoreHome = true) }
        homeContinuationJob?.cancel()
        homeContinuationJob = viewModelScope.launch {
            try {
                val result = YouTubeMusic.homeContinuation(token)
                result.onSuccess { page ->
                    val current = _uiState.value.homeSections.toMutableList()
                    page.sections.forEach { newSec ->
                        if (current.none { it.title.equals(newSec.title, ignoreCase = true) }) {
                            current.add(newSec)
                        }
                    }
                    _uiState.update { it.copy(homeSections = current) }
                    val nextToken = page.continuation?.takeIf { it != token }
                    if (loadingHomeContinuation) {
                        homeContinuationToken = nextToken
                    } else {
                        exploreContinuationToken = nextToken
                    }
                    persistHomeFeedCache()
                    prefetchThumbnails(page.sections.take(3).flatMap { it.items.take(4) })
                }.onFailure { failure ->
                    _uiState.update {
                        it.copy(
                            homeFeedOfflineError = NetworkFailureClassifier.isNoInternetFailure(
                                failure,
                                getApplication<Application>()
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Load more home sections failed", e)
                _uiState.update {
                    it.copy(
                        homeFeedOfflineError = NetworkFailureClassifier.isNoInternetFailure(
                            e,
                            getApplication<Application>()
                        )
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoadingMoreHome = false) }
            }
        }
    }

    // ========== Online Playback ==========

    /** Clear stale Explore "now playing" highlights when playback is handed to Cache/Library. */
    fun clearOnlinePlaybackSelection() {
        _uiState.update { state ->
            state.copy(
                currentOnlineSong = null,
                onlineQueue = emptyList(),
                currentOnlineIndex = -1,
                streamUrl = null,
                isLoadingStream = false,
                streamError = null
            )
        }
    }

    fun playOnlineSong(song: OnlineSong) {
        _uiState.update { 
            it.copy(
                currentOnlineSong = song,
                isLoadingStream = false,
                streamError = null
            )
        }

        // Track played artist for home feed personalization
        if (song.artist.isNotBlank() && !song.artist.equals("<unknown>", ignoreCase = true)) {
            val historyString = prefs.getString("recent_artists_list", "") ?: ""
            val currentList = if (historyString.isBlank()) {
                mutableListOf()
            } else {
                historyString.split("|").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
            }
            currentList.remove(song.artist.trim())
            currentList.add(0, song.artist.trim())
            val trimmedList = currentList.take(3)
            prefs.edit {
                putString("recent_artists_list", trimmedList.joinToString("|"))
                    .putString("last_played_artist", song.artist) // Backwards compatibility
            }
        }

        // EXTREME LATENCY REMOVAL: Deleted explicit blocking network call! 
        // ExoPlayer uses ResolvingDataSource to resolve URIs asynchronously in the background AFTER opening the player instantly!
    }

    /**
     * Play an online song and set queue context.
     */
    fun playOnlineSongWithQueue(song: OnlineSong, queue: List<OnlineSong>, index: Int) {
        _uiState.update { 
            it.copy(
                onlineQueue = queue,
                currentOnlineIndex = index
            )
        }
        playOnlineSong(song)

        // Fire-and-forget trigger informing ExploreFragment to hand over data to main player IMMEDIATELY without waiting.
        _playbackTriggerEvent.tryEmit(Unit)

        // PREFETCH NEXT SONG TO WARM UP THE CACHE INSTANTLY!
        if (index + 1 < queue.size) {
            val nextSong = queue[index + 1]
            YouTubeMusic.prefetchStream(nextSong.videoId)
        }
    }

    fun addToQueueNext(song: OnlineSong) {
        val currentQueue = _uiState.value.onlineQueue.toMutableList()
        val currentIndex = _uiState.value.currentOnlineIndex
        currentQueue.add(currentIndex + 1, song)
        _uiState.update { it.copy(onlineQueue = currentQueue) }
    }

    fun addToQueueLast(song: OnlineSong) {
        val currentQueue = _uiState.value.onlineQueue.toMutableList()
        currentQueue.add(song)
        _uiState.update { it.copy(onlineQueue = currentQueue) }
    }

    fun updateActiveSongAndIndex(index: Int, song: OnlineSong?) {
        _uiState.update { 
            it.copy(
                currentOnlineIndex = index,
                currentOnlineSong = song
            )
        }
    }

    // ========== Detail Pages ==========

    fun retryLastDetailLoad() {
        lastDetailRetry?.invoke()
    }

    private fun beginDetailLoad(): Long {
        detailLoadGeneration += 1L
        detailLoadJob?.cancel()
        detailLoadJob = null
        artistResolveJob?.cancel()
        artistResolveJob = null
        _uiState.update {
            it.copy(isLoadingDetail = true, error = null, detailOfflineError = false)
        }
        return detailLoadGeneration
    }

    private fun finishDetailLoad(requestId: Long) {
        // A cancelled request must never clear the loading state of a newer page request.
        if (requestId == detailLoadGeneration) {
            detailLoadJob = null
            _uiState.update { it.copy(isLoadingDetail = false) }
        }
    }

    fun cancelDetailLoad() {
        detailLoadGeneration += 1L
        detailLoadJob?.cancel()
        detailLoadJob = null
        artistResolveJob?.cancel()
        artistResolveJob = null
        _uiState.update { it.copy(isLoadingDetail = false) }
    }

    fun loadAlbum(browseId: String) {
        lastDetailRetry = { loadAlbum(browseId) }
        val requestId = beginDetailLoad()
        detailLoadJob = viewModelScope.launch {
            try {
                val result = YouTubeMusic.album(browseId)
                if (requestId == detailLoadGeneration) {
                    result.onSuccess { res ->
                        _uiState.update {
                            it.copy(
                                albumDetail = res,
                                detailStack = it.detailStack + DetailType.ALBUM
                            )
                        }
                        prefetchUrls(listOfNotNull(res.album.thumbnailUrl) + res.songs.map { it.thumbnailUrl })
                    }
                    result.onFailure { failure ->
                        _uiState.update {
                            it.copy(
                                error = "Failed to load album",
                                detailOfflineError = NetworkFailureClassifier.isNoInternetFailure(
                                    failure,
                                    getApplication<Application>()
                                )
                            )
                        }
                    }
                }
            } finally {
                finishDetailLoad(requestId)
            }
        }
    }

    /**
     * Resolves an album page for songs whose menu payload contains only the album title.
     * Downloaded/cache metadata often retains the name but not YouTube's browse ID.
     */
    fun loadAlbumByName(albumName: String, artistName: String) {
        val cleanAlbumName = albumName.trim()
        val cleanArtistName = artistName.trim()
        if (cleanAlbumName.isBlank()) {
            _uiState.update { it.copy(error = "Album information unavailable") }
            return
        }

        detailLoadGeneration += 1L
        val requestId = detailLoadGeneration
        detailLoadJob?.cancel()
        detailLoadJob = null
        artistResolveJob?.cancel()
        _uiState.update {
            it.copy(isLoadingDetail = true, error = null, detailOfflineError = false)
        }
        lastDetailRetry = { loadAlbumByName(albumName, artistName) }

        artistResolveJob = viewModelScope.launch {
            try {
                val query = listOf(cleanAlbumName, cleanArtistName)
                    .filter { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
                    .joinToString(" ")
                val result = YouTubeMusic.search(query, SearchFilter.ALBUMS)
                if (requestId != detailLoadGeneration) return@launch

                val albums = result.getOrNull()?.items
                    ?.filterIsInstance<SearchItem.Album>()
                    .orEmpty()
                val matchingTitle = albums.filter {
                    it.album.title.trim().equals(cleanAlbumName, ignoreCase = true)
                }
                val match = matchingTitle.firstOrNull { candidate ->
                    cleanArtistName.isBlank() || candidate.album.artists.any { artist ->
                        artist.name.contains(cleanArtistName, ignoreCase = true) ||
                            cleanArtistName.contains(artist.name, ignoreCase = true)
                    }
                } ?: matchingTitle.firstOrNull() ?: albums.firstOrNull()

                artistResolveJob = null
                if (match != null) {
                    loadAlbum(match.album.browseId)
                } else {
                    _uiState.update {
                        it.copy(
                            isLoadingDetail = false,
                            error = result.exceptionOrNull()?.message
                                ?: "Could not find the album \"$cleanAlbumName\"",
                            detailOfflineError = NetworkFailureClassifier.isNoInternetFailure(
                                result.exceptionOrNull(),
                                getApplication<Application>()
                            )
                        )
                    }
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (requestId == detailLoadGeneration) {
                    artistResolveJob = null
                    _uiState.update {
                        it.copy(
                            isLoadingDetail = false,
                            error = error.message ?: "Could not open album page",
                            detailOfflineError = NetworkFailureClassifier.isNoInternetFailure(
                                error,
                                getApplication<Application>()
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * A track menu can lack both album browse ID and album title. Resolve the selected
     * track through YouTube Music search before declaring album navigation unavailable.
     */
    fun loadAlbumForSong(title: String, artistName: String) {
        val cleanTitle = title.trim()
        val cleanArtist = artistName.trim()
        if (cleanTitle.isBlank()) {
            _uiState.update { it.copy(error = "Song information unavailable") }
            return
        }

        detailLoadGeneration += 1L
        val requestId = detailLoadGeneration
        detailLoadJob?.cancel()
        detailLoadJob = null
        artistResolveJob?.cancel()
        _uiState.update {
            it.copy(isLoadingDetail = true, error = null, detailOfflineError = false)
        }
        lastDetailRetry = { loadAlbumForSong(title, artistName) }

        artistResolveJob = viewModelScope.launch {
            try {
                val query = listOf(cleanTitle, cleanArtist)
                    .filter { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
                    .joinToString(" ")
                val result = YouTubeMusic.search(query, SearchFilter.SONGS)
                if (requestId != detailLoadGeneration) return@launch

                val candidates = result.getOrNull()?.items
                    ?.filterIsInstance<SearchItem.Song>()
                    ?.map { it.song }
                    .orEmpty()
                val exactMatch = candidates.firstOrNull { candidate ->
                    candidate.title.trim().equals(cleanTitle, ignoreCase = true) &&
                        (cleanArtist.isBlank() ||
                            candidate.artist.contains(cleanArtist, ignoreCase = true) ||
                            cleanArtist.contains(candidate.artist, ignoreCase = true))
                } ?: candidates.firstOrNull {
                    it.title.trim().equals(cleanTitle, ignoreCase = true)
                } ?: candidates.firstOrNull()

                artistResolveJob = null
                when {
                    exactMatch == null -> _uiState.update {
                        it.copy(
                            isLoadingDetail = false,
                            error = result.exceptionOrNull()?.message
                                ?: "Could not find the album for \"$cleanTitle\"",
                            detailOfflineError = NetworkFailureClassifier.isNoInternetFailure(
                                result.exceptionOrNull(),
                                getApplication<Application>()
                            )
                        )
                    }
                    !exactMatch.albumId.isNullOrBlank() -> loadAlbum(exactMatch.albumId)
                    !exactMatch.albumName.isNullOrBlank() ->
                        loadAlbumByName(exactMatch.albumName, exactMatch.artist)
                    else -> _uiState.update {
                        it.copy(isLoadingDetail = false, error = "Album information is unavailable for this song")
                    }
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (requestId == detailLoadGeneration) {
                    artistResolveJob = null
                    _uiState.update {
                        it.copy(
                            isLoadingDetail = false,
                            error = error.message ?: "Could not resolve this song's album",
                            detailOfflineError = NetworkFailureClassifier.isNoInternetFailure(
                                error,
                                getApplication<Application>()
                            )
                        )
                    }
                }
            }
        }
    }

    fun loadArtistByName(name: String, fallbackThumbnailUrl: String? = null) {
        val cleanName = name.trim()
        if (cleanName.isBlank() || cleanName.equals("Unknown Artist", ignoreCase = true)) return
        detailLoadGeneration += 1L
        val requestId = detailLoadGeneration
        detailLoadJob?.cancel()
        detailLoadJob = null
        artistResolveJob?.cancel()
        _uiState.update {
            it.copy(isLoadingDetail = true, error = null, detailOfflineError = false)
        }
        lastDetailRetry = { loadArtistByName(name, fallbackThumbnailUrl) }
        artistResolveJob = viewModelScope.launch {
            try {
                val result = YouTubeMusic.search(cleanName, SearchFilter.ARTISTS)
                if (requestId != detailLoadGeneration) return@launch
                val artists = result.getOrNull()?.items?.filterIsInstance<SearchItem.Artist>().orEmpty()
                val match = artists.firstOrNull {
                    it.artist.title.trim().equals(cleanName, ignoreCase = true)
                } ?: artists.firstOrNull()
                artistResolveJob = null
                if (match != null) {
                    loadArtist(match.artist.browseId, match.artist.thumbnailUrl ?: fallbackThumbnailUrl)
                } else {
                    _uiState.update {
                        it.copy(
                            isLoadingDetail = false,
                            error = "Could not find an artist page for $cleanName",
                            detailOfflineError = NetworkFailureClassifier.isNoInternetFailure(
                                result.exceptionOrNull(),
                                getApplication<Application>()
                            )
                        )
                    }
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (requestId == detailLoadGeneration) {
                    artistResolveJob = null
                    _uiState.update {
                        it.copy(
                            isLoadingDetail = false,
                            error = error.message ?: "Could not open artist page",
                            detailOfflineError = NetworkFailureClassifier.isNoInternetFailure(
                                error,
                                getApplication<Application>()
                            )
                        )
                    }
                }
            }
        }
    }

    fun loadArtist(
        browseId: String,
        fallbackThumbnailUrl: String? = null,
        fallbackArtistName: String? = null
    ) {
        lastDetailRetry = { loadArtist(browseId, fallbackThumbnailUrl, fallbackArtistName) }
        val requestId = beginDetailLoad()
        detailLoadJob = viewModelScope.launch {
            try {
                val result = YouTubeMusic.artist(browseId)
                if (requestId == detailLoadGeneration) {
                    result.onSuccess { res ->
                        _uiState.update { state ->
                            val correctedThumbnailUrl = res.artist.thumbnailUrl.takeIf { !it.isNullOrBlank() } ?: fallbackThumbnailUrl
                            val updatedArtist = res.artist.copy(thumbnailUrl = correctedThumbnailUrl)
                            state.copy(
                                artistDetail = res.copy(artist = updatedArtist),
                                detailStack = state.detailStack + DetailType.ARTIST
                            )
                        }
                        prefetchUrls(listOfNotNull(res.artist.thumbnailUrl))
                        prefetchThumbnails(res.sections.flatMap { it.items.take(4) })
                    }
                    result.onFailure { failure ->
                        val nameFallback = fallbackArtistName?.trim()?.takeIf {
                            it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true)
                        }
                        if (nameFallback != null) {
                            // Avoid cancelling this request from inside itself. The generation
                            // change makes its finally block leave the new search's loading state alone.
                            detailLoadJob = null
                            loadArtistByName(nameFallback, fallbackThumbnailUrl)
                        } else {
                            _uiState.update {
                                it.copy(
                                    error = "Failed to load artist",
                                    detailOfflineError = NetworkFailureClassifier.isNoInternetFailure(
                                        failure,
                                        getApplication<Application>()
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (requestId == detailLoadGeneration) {
                    val nameFallback = fallbackArtistName?.trim()?.takeIf {
                        it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true)
                    }
                    if (nameFallback != null) {
                        detailLoadJob = null
                        loadArtistByName(nameFallback, fallbackThumbnailUrl)
                    } else {
                        _uiState.update {
                            it.copy(
                                error = error.message ?: "Could not load artist",
                                detailOfflineError = NetworkFailureClassifier.isNoInternetFailure(
                                    error,
                                    getApplication<Application>()
                                )
                            )
                        }
                    }
                }
            } finally {
                finishDetailLoad(requestId)
            }
        }
    }

    fun loadPlaylist(playlistId: String) {
        lastDetailRetry = { loadPlaylist(playlistId) }
        val requestId = beginDetailLoad()
        detailLoadJob = viewModelScope.launch {
            try {
                val result = YouTubeMusic.playlist(playlistId)
                if (requestId == detailLoadGeneration) {
                    result.onSuccess { res ->
                        _uiState.update {
                            it.copy(
                                playlistDetail = res,
                                detailStack = it.detailStack + DetailType.PLAYLIST
                            )
                        }
                        prefetchUrls(listOfNotNull(res.playlist.thumbnailUrl) + res.songs.map { it.thumbnailUrl })
                    }
                    result.onFailure { failure ->
                        _uiState.update {
                            it.copy(
                                error = "Failed to load playlist",
                                detailOfflineError = NetworkFailureClassifier.isNoInternetFailure(
                                    failure,
                                    getApplication<Application>()
                                )
                            )
                        }
                    }
                }
            } finally {
                finishDetailLoad(requestId)
            }
        }
    }

    fun updatePlaylistDetails(
        playlistId: String,
        title: String,
        description: String,
        privacy: String
    ) {
        _uiState.update { state ->
            val page = state.playlistDetail
            if (page?.playlist?.playlistId != playlistId) {
                state
            } else {
                state.copy(
                    playlistDetail = page.copy(
                        playlist = page.playlist.copy(title = title),
                        description = description.takeIf { it.isNotBlank() },
                        privacy = privacy
                    )
                )
            }
        }
    }

    fun loadSectionDetails(
        browseId: String,
        params: String?,
        fallbackTitle: String = "Section",
        preferFallbackTitle: Boolean = false
    ) {
        lastDetailRetry = {
            loadSectionDetails(browseId, params, fallbackTitle, preferFallbackTitle)
        }
        val requestId = beginDetailLoad()
        detailLoadJob = viewModelScope.launch {
            try {
                val result = YouTubeMusic.section(browseId, params)
                if (requestId == detailLoadGeneration) {
                    result.onSuccess { res ->
                        _uiState.update {
                            val cardTitle = fallbackTitle.takeIf { it.isNotBlank() }
                            val correctedTitle = if (preferFallbackTitle && cardTitle != null) {
                                cardTitle
                            } else if (res.title == "Section") {
                                fallbackTitle
                            } else {
                                res.title
                            }
                            it.copy(
                                sectionDetail = res.copy(title = correctedTitle),
                                detailStack = it.detailStack + DetailType.SECTION
                            )
                        }
                        prefetchThumbnails(res.items.take(12))
                    }
                    result.onFailure { failure ->
                        _uiState.update {
                            it.copy(
                                error = "Failed to load section",
                                detailOfflineError = NetworkFailureClassifier.isNoInternetFailure(
                                    failure,
                                    getApplication<Application>()
                                )
                            )
                        }
                    }
                }
            } finally {
                finishDetailLoad(requestId)
            }
        }
    }
    fun popDetailStack() {
        _uiState.update { state ->
            if (state.detailStack.isEmpty()) return@update state.copy(detailOfflineError = false)
            val nextStack = state.detailStack.dropLast(1)
            val popped = state.detailStack.last()
            when (popped) {
                DetailType.ALBUM -> state.copy(albumDetail = null, detailStack = nextStack, detailOfflineError = false)
                DetailType.PLAYLIST -> state.copy(playlistDetail = null, detailStack = nextStack, detailOfflineError = false)
                DetailType.ARTIST -> state.copy(artistDetail = null, detailStack = nextStack, detailOfflineError = false)
                DetailType.SECTION -> state.copy(sectionDetail = null, detailStack = nextStack, detailOfflineError = false)
            }
        }
    }

    fun resetToHome() {
        _uiState.update { state ->
            state.copy(
                albumDetail = null,
                artistDetail = null,
                playlistDetail = null,
                sectionDetail = null,
                detailStack = emptyList(),
                searchQuery = "",
                searchResults = emptyList(),
                suggestionItems = emptyList(),
                searchTopResult = null,
                searchSections = emptyList(),
                isSearching = false,
                searchOfflineError = false,
                detailOfflineError = false
            )
        }
        cameFromLibrary = false
    }
    // ========== Start Radio ==========

    fun startRadio(videoId: String) {
        startRadioInternal(videoId, seedSong = null)
    }

    /** Start a mix from a specific song and keep that song at the front of the queue. */
    fun startRadio(seedSong: OnlineSong) {
        startRadioInternal(seedSong.videoId, seedSong)
    }

    private fun startRadioInternal(videoId: String, seedSong: OnlineSong?) {
        _uiState.update { it.copy(isLoadingStream = true, error = null, streamError = null) }
        viewModelScope.launch {
            try {
                val radioResult = YouTubeMusic.startRadio(videoId)
                val radioSongs = radioResult.getOrElse { radioError ->
                    Log.w(TAG, "Radio request failed; falling back to related songs", radioError)
                    emptyList()
                }

                // A radio response may be empty or contain only the seed song. Fall back
                // to related songs so Start mix can still populate a multi-song queue.
                val needsRelatedFallback = radioSongs.isEmpty() ||
                    (seedSong != null && radioSongs.none { it.videoId != seedSong.videoId })
                val candidates = if (!needsRelatedFallback) {
                    radioSongs
                } else {
                    YouTubeMusic.relatedSongs(videoId).getOrElse { relatedError ->
                        Log.w(TAG, "Related-song fallback failed for $videoId", relatedError)
                        emptyList()
                    }
                }

                val mixQueue = if (seedSong != null) {
                    listOf(seedSong) + candidates.filterNot { it.videoId == seedSong.videoId }
                } else {
                    candidates
                }

                if (mixQueue.isNotEmpty()) {
                    playOnlineSongWithQueue(mixQueue.first(), mixQueue, 0)
                    if (seedSong != null && mixQueue.size == 1) {
                        _uiState.update {
                            it.copy(error = "Could not load related songs; playing the selected song only")
                        }
                    }
                } else {
                    _uiState.update { it.copy(error = "Failed to start radio: no songs were returned") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start radio for $videoId", e)
                _uiState.update { it.copy(error = "Failed to start radio") }
            } finally {
                _uiState.update { it.copy(isLoadingStream = false) }
            }
        }
    }

    // ========== YouTube Playlist Management ==========

    fun subscribeToArtist(channelId: String) {
        // Optimistically update UI state
        _uiState.update { state ->
            if (state.artistDetail?.artist?.browseId == channelId) {
                state.copy(
                    artistDetail = state.artistDetail.copy(
                        artist = state.artistDetail.artist.copy(isSubscribed = true)
                    )
                )
            } else {
                state
            }
        }
        viewModelScope.launch {
            try {
                val result = YouTubeMusic.subscribeArtist(channelId)
                result.onSuccess {
                    Log.d(TAG, "Subscribed to artist $channelId")
                    _subscriptionChanged.value = true
                    _uiState.update { state ->
                        if (state.artistDetail?.artist?.browseId == channelId) {
                            state.copy(
                                artistDetail = state.artistDetail.copy(
                                    artist = state.artistDetail.artist.copy(isSubscribed = true)
                                )
                            )
                        } else {
                            state
                        }
                    }
                }
                result.onFailure { e ->
                    Log.e(TAG, "Subscribe failed, reverting", e)
                    _uiState.update { state ->
                        if (state.artistDetail?.artist?.browseId == channelId) {
                            state.copy(
                                artistDetail = state.artistDetail.copy(
                                    artist = state.artistDetail.artist.copy(isSubscribed = false)
                                )
                            )
                        } else {
                            state
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Subscribe error", e)
            }
        }
    }

    fun unsubscribeFromArtist(channelId: String) {
        // Optimistically update UI state
        _uiState.update { state ->
            if (state.artistDetail?.artist?.browseId == channelId) {
                state.copy(
                    artistDetail = state.artistDetail.copy(
                        artist = state.artistDetail.artist.copy(isSubscribed = false)
                    )
                )
            } else {
                state
            }
        }
        viewModelScope.launch {
            try {
                val result = YouTubeMusic.unsubscribeArtist(channelId)
                result.onSuccess {
                    Log.d(TAG, "Unsubscribed from artist $channelId")
                    _subscriptionChanged.value = true
                    _uiState.update { state ->
                        if (state.artistDetail?.artist?.browseId == channelId) {
                            state.copy(
                                artistDetail = state.artistDetail.copy(
                                    artist = state.artistDetail.artist.copy(isSubscribed = false)
                                )
                            )
                        } else {
                            state
                        }
                    }
                }
                result.onFailure { e ->
                    Log.e(TAG, "Unsubscribe failed, reverting", e)
                    _uiState.update { state ->
                        if (state.artistDetail?.artist?.browseId == channelId) {
                            state.copy(
                                artistDetail = state.artistDetail.copy(
                                    artist = state.artistDetail.artist.copy(isSubscribed = true)
                                )
                            )
                        } else {
                            state
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unsubscribe error", e)
            }
        }
    }



    fun clearError() {
        _uiState.update { 
            it.copy(
                error = null,
                streamError = null,
                detailOfflineError = false
            )
        }
    }

    // ========== Speed Dial & Not Interested ==========

    fun getPinnedSpeedDialSongs(): List<OnlineSong> {
        val json = prefs.getString("speed_dial_songs_json", null) ?: return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<OnlineSong?>>() {}.type
            val restoredSongs: List<OnlineSong?> = gson.fromJson(json, type) ?: emptyList()
            restoredSongs.mapNotNull { restored ->
                val song = restored ?: return@mapNotNull null
                // Gson can bypass Kotlin constructor defaults. Older/corrupt preferences may
                // therefore restore artistRefs as null even though the model declares a default.
                val safeArtistRefs = song.artistRefs.orEmpty().filterNotNull()
                song.copy(artistRefs = safeArtistRefs)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Re-read user-owned Explore preferences after a Settings backup is imported.
     * The ExploreViewModel is activity-scoped and can outlive the restore screen,
     * so constructor-only loading would otherwise leave its UI state stale.
     */
    fun reloadRestoredPreferences() {
        loadSearchHistory()
        val pinnedSongs = getPinnedSpeedDialSongs()
        val notInterested = prefs.getStringSet("not_interested_ids", mutableSetOf<String>()).orEmpty()
        _uiState.update { state ->
            val filteredSections = state.homeSections.map { section ->
                section.copy(items = section.items.filterNot { item ->
                    item is SearchItem.Song && item.song.videoId in notInterested
                })
            }.filter { it.items.isNotEmpty() }
            state.copy(
                pinnedSpeedDialIds = pinnedSongs.map { it.videoId }.toSet(),
                notInterestedIds = notInterested,
                homeSections = filteredSections
            )
        }
        homeLoaded = false
    }

    private fun savePinnedSpeedDialSongs(songs: List<OnlineSong>) {
        prefs.edit { putString("speed_dial_songs_json", gson.toJson(songs)) }
        _uiState.update { state -> state.copy(pinnedSpeedDialIds = songs.map { it.videoId }.toSet()) }
    }

    fun pinToSpeedDial(song: OnlineSong) {
        val current = getPinnedSpeedDialSongs().toMutableList()
        val exists = current.any { it.videoId == song.videoId }
        if (exists) {
            current.removeAll { it.videoId == song.videoId }
            viewModelScope.launch {
                try {
                    YouTubeMusic.updateLikeStatus(song.videoId, YouTubeMusic.LikeStatus.INDIFFERENT)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send indifferent status to YT", e)
                }
            }
        } else {
            current.add(0, song)
            viewModelScope.launch {
                try {
                    YouTubeMusic.updateLikeStatus(song.videoId, YouTubeMusic.LikeStatus.LIKE)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send like status to YT", e)
                }
            }
        }
        savePinnedSpeedDialSongs(current)
        
        viewModelScope.launch {
            val mergedSections = fetchMergedHomeFeed()
            _uiState.update { it.copy(homeSections = mergedSections) }
            persistHomeFeedCache()
        }
    }

    fun setNotInterested(song: OnlineSong) {
        val current = _uiState.value.notInterestedIds.toMutableSet()
        current.add(song.videoId)
        _uiState.update { it.copy(notInterestedIds = current) }
        
        prefs.edit { putStringSet("not_interested_ids", current) }

        viewModelScope.launch {
            try {
                YouTubeMusic.updateLikeStatus(song.videoId, YouTubeMusic.LikeStatus.DISLIKE)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send not interested/dislike feedback to YouTube Music", e)
            }
            
            val mergedSections = fetchMergedHomeFeed()
            _uiState.update { it.copy(homeSections = mergedSections) }
            persistHomeFeedCache()
        }

        val filteredSearchResults = _uiState.value.searchResults.filterNot { item ->
            item is SearchItem.Song && item.song.videoId == song.videoId
        }
        
        val updatedAlbumDetail = _uiState.value.albumDetail?.let { album ->
            album.copy(songs = album.songs.filterNot { it.videoId == song.videoId })
        }
        val updatedPlaylistDetail = _uiState.value.playlistDetail?.let { playlist ->
            playlist.copy(songs = playlist.songs.filterNot { it.videoId == song.videoId })
        }

        _uiState.update { state ->
            state.copy(
                searchResults = filteredSearchResults,
                albumDetail = updatedAlbumDetail,
                playlistDetail = updatedPlaylistDetail
            )
        }
    }

    private fun prefetchThumbnails(items: List<SearchItem>) {
        // Prefetch only a small leading window on older devices. Rows load their own
        // artwork when they become visible, so warming an entire detail page is wasteful.
        val maxPrefetchCount = if (
            android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M
        ) 4 else 12
        val urls = items.asSequence()
            .take(maxPrefetchCount)
            .mapNotNull { item ->
                when (item) {
                    is SearchItem.Song -> item.song.thumbnailUrl
                    is SearchItem.Album -> item.album.thumbnailUrl
                    is SearchItem.Artist -> item.artist.thumbnailUrl
                    is SearchItem.Playlist -> item.playlist.thumbnailUrl
                }
            }
            .toList()
        prefetchUrls(urls)
    }

    private fun prefetchUrls(urls: List<String?>) {
        val maxPrefetchCount = if (
            android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M
        ) 4 else 12
        val selectedUrls = urls.asSequence()
            .filterNotNull()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(maxPrefetchCount)
            .toList()
        if (selectedUrls.isEmpty()) return

        val applicationContext = getApplication<Application>()
        viewModelScope.launch {
            val loader = applicationContext.imageLoader
            selectedUrls.forEach { url ->
                // Prefetch a useful detail-size copy on every Android version.
                // Visible cards still request their own layout-specific decode size.
                val request = buildExploreImageRequest(
                    applicationContext,
                    url,
                    targetSizePx = 256
                ) ?: return@forEach
                loader.enqueue(request)
            }
        }
    }
}

@Stable
data class ExploreUiState(
    val searchQuery: String = "",
    val searchResults: List<SearchItem> = emptyList(),
    val searchTopResult: SearchItem? = null,
    val searchSections: List<com.dsann.music.data.innertube.SearchResultSection> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val suggestionItems: List<SearchItem> = emptyList(),
    val isSearching: Boolean = false,
    val searchOfflineError: Boolean = false,
    val homeSections: List<HomeSection> = emptyList(),
    val isLoadingHome: Boolean = false,
    val homeFeedError: String? = null,
    val homeFeedOfflineError: Boolean = false,
    val isLoadingStream: Boolean = false,
    val currentMood: String? = null,
    val currentOnlineSong: OnlineSong? = null,
    val error: String? = null,
    val albumDetail: AlbumPage? = null,
    val artistDetail: ArtistPage? = null,
    val playlistDetail: PlaylistPage? = null,
    val sectionDetail: HomeSection? = null,
    val isLoadingDetail: Boolean = false,
    val detailOfflineError: Boolean = false,
    val isRefreshing: Boolean = false,
    val searchHistory: List<String> = emptyList(),
    val homeMoods: List<String> = emptyList(),
    val homeCategories: List<HomeCategory> = emptyList(),
    val isLoadingGenres: Boolean = false,
    val genresError: String? = null,
    val genresOfflineError: Boolean = false,
    val searchFilter: SearchFilter? = null,
    val streamUrl: String? = null,
    val streamError: String? = null,
    val onlineQueue: List<OnlineSong> = emptyList(),
    val currentOnlineIndex: Int = -1,
    val isLoadingMoreHome: Boolean = false,
    val pinnedSpeedDialIds: Set<String> = emptySet(),
    val notInterestedIds: Set<String> = emptySet(),
    val detailStack: List<DetailType> = emptyList()
)
