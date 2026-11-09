package com.dsann.music.ui.explore

import com.dsann.music.ui.theme.DsmusicPageHeading
import com.dsann.music.ui.LocalRootTabActive
import com.dsann.music.ui.library.RecapTabContent
import com.dsann.music.ui.theme.DsmusicCard
import com.dsann.music.ui.theme.DsmusicSurface
import com.dsann.music.ui.theme.DsmusicListItem
import com.dsann.music.ui.theme.DsmusicButton
import com.dsann.music.ui.theme.DsmusicOutlinedButton
import com.dsann.music.ui.theme.DsmusicTextButton
import com.dsann.music.ui.theme.DsmusicIconButton
import com.dsann.music.ui.theme.DsmusicFilterChip
import com.dsann.music.ui.theme.DsmusicSwitchControl
import android.R.attr.translationY
import android.content.res.Configuration
import android.graphics.Color.alpha
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.dsann.music.ui.YouTubeMusicPlaylistPickerDialog
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import com.dsann.music.ui.theme.LoadingIndicator
import com.dsann.music.ui.theme.FilterChip
import com.dsann.music.ui.theme.FilterChipDefaults
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.IconButton
import com.dsann.music.ui.theme.ListItem
import com.dsann.music.ui.theme.ListItemDefaults
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.LocalPageTransitionDurationMillis
import com.dsann.music.ui.theme.observeKey
import com.dsann.music.ui.theme.OutlinedButton
import com.dsann.music.ui.theme.Snackbar
import com.dsann.music.ui.theme.Surface
import com.dsann.music.ui.theme.Text
import com.dsann.music.ui.theme.TextField
import com.dsann.music.ui.theme.TextButton
import com.dsann.music.ui.theme.PullToRefreshBox
import com.dsann.music.ui.theme.PullToRefreshDefaults
import com.dsann.music.ui.theme.rememberPullToRefreshState
import com.dsann.music.ui.theme.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.dsann.music.MainActivity
import com.dsann.music.R
import com.dsann.music.data.innertube.OnlineSong
import com.dsann.music.data.innertube.SearchFilter
import com.dsann.music.data.innertube.SearchItem
import com.dsann.music.data.innertube.UserProfile
import com.dsann.music.model.SongItem
import com.dsann.music.viewmodel.AccountViewModel
import com.dsann.music.viewmodel.ExploreViewModel
import com.dsann.music.viewmodel.PlayerSharedViewModel
import com.dsann.music.viewmodel.DetailType
import kotlin.time.Duration.Companion.milliseconds

// ===== Shared Transition Locals =====

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ExploreTransitionHost(
    enabled: Boolean,
    content: @Composable (SharedTransitionScope?) -> Unit
) {
    if (enabled) {
        SharedTransitionLayout { content(this) }
    } else {
        content(null)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElementIfAvailable(key: String): Modifier {
    if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M) return this
    val sharedScope = LocalSharedTransitionScope.current
    val animatedScope = LocalAnimatedVisibilityScope.current
    return if (sharedScope != null && animatedScope != null) {
        with(sharedScope) {
            this@sharedElementIfAvailable.sharedElement(
                rememberSharedContentState(key = key),
                animatedVisibilityScope = animatedScope
            )
        }
    } else this
}

// ===== Explore Screen =====

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    playerSharedViewModel: PlayerSharedViewModel,
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToListenTogether: () -> Unit = {},
    showListenTogetherSubpage: Boolean = false,
    onCloseListenTogetherSubpage: () -> Unit = {},
    showSongRecognizerSubpage: Boolean = false,
    onCloseSongRecognizerSubpage: () -> Unit = {},
    isSearchDestination: Boolean = false,
    onLeaveSearchDestination: () -> Unit = {},
    bottomContentPadding: Dp = 176.dp,
    onSongRecognizerShortcutVisibilityChange: (Boolean) -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val mainActivity = context as? MainActivity
    val scope = rememberCoroutineScope()

    val accountVM: AccountViewModel = viewModel()
    val userProfile by accountVM.userProfile.collectAsStateWithLifecycle()
    val accountHistory by accountVM.history.collectAsStateWithLifecycle()
    val recentAccountHistory = remember(accountHistory) { accountHistory.take(10) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val searchQuery = uiState.searchQuery
    val searchResults = uiState.searchResults
    val searchTopResult = uiState.searchTopResult
    val searchSections = uiState.searchSections
    val suggestions = uiState.suggestions
    val suggestionItems = uiState.suggestionItems
    val isSearching = uiState.isSearching
    val homeSections = uiState.homeSections
    val isLoadingHome = uiState.isLoadingHome
    val homeFeedError = uiState.homeFeedError
    val homeFeedOfflineError = uiState.homeFeedOfflineError
    val searchOfflineError = uiState.searchOfflineError
    val detailOfflineError = uiState.detailOfflineError
    val isLoadingStream = uiState.isLoadingStream
    val currentMood = uiState.currentMood
    val currentOnlineSong = uiState.currentOnlineSong
    val currentPlayerSong by playerSharedViewModel.currentSong.collectAsStateWithLifecycle()
    val localSongs by playerSharedViewModel.localSongs.collectAsStateWithLifecycle(emptyList())
    val isPlaybackActive by playerSharedViewModel.isPlaying.collectAsStateWithLifecycle()
    val isPlayerLoading by playerSharedViewModel.isLoading.collectAsStateWithLifecycle()
    // Use the player-owned track for highlights, regardless of whether it came from
    // Explore, cache, or Downloads. The Explore VM only tracks online selections.
    val currentPlayingVideoId = if (currentPlayerSong != null) {
        currentPlayerSong?.videoId
    } else {
        currentOnlineSong?.videoId
    }
    val currentTrackIsLoading = if (currentPlayerSong != null) {
        isPlayerLoading
    } else {
        isLoadingStream
    }
    val error = uiState.error
    val albumDetail = uiState.albumDetail
    val artistDetail = uiState.artistDetail
    val playlistDetail = uiState.playlistDetail
    val sectionDetail = uiState.sectionDetail
    val isLoadingDetail = uiState.isLoadingDetail
    val isRefreshing = uiState.isRefreshing
    val isLoadingMoreHome by viewModel.isLoadingMoreHome.collectAsStateWithLifecycle()
    val searchHistory = uiState.searchHistory
    val homeMoods = uiState.homeMoods
    val homeCategories = uiState.homeCategories
    val sortedHomeCategories = remember(homeCategories) {
        // Do not let malformed or legacy cache entries reach the typed comparator.
        homeCategories
            .filterIsInstance<com.dsann.music.data.innertube.HomeCategory>()
            .distinctBy { it.browseId + "|" + it.params.orEmpty() }
            .sortedBy { it.title.trim().lowercase() }
    }
    val isLoadingGenres = uiState.isLoadingGenres
    val genresError = uiState.genresError
    val genresOfflineError = uiState.genresOfflineError
    val detailStack = uiState.detailStack

    var isSearchActive by rememberSaveable(isSearchDestination) {
        mutableStateOf(false)
    }
    var showReplayScreen by rememberSaveable { mutableStateOf(false) }
    var showSearchGenreBrowser by rememberSaveable { mutableStateOf(false) }
    var selectedSongForMenu by remember { mutableStateOf<OnlineSong?>(null) }
    var selectedReplayLocalSongForMenu by remember { mutableStateOf<SongItem?>(null) }
    var selectedCollectionForMenu by remember { mutableStateOf<SearchItem?>(null) }
    var selectedCollectionMenuSongs by remember { mutableStateOf<List<OnlineSong>?>(null) }
    var selectedCollectionMenuContinuation by remember { mutableStateOf<String?>(null) }
    var showCreditsForSong by remember { mutableStateOf<OnlineSong?>(null) }

    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var songsToAddPlaylist by remember { mutableStateOf<List<SongItem>?>(null) }

    fun activateSearchItem(item: SearchItem, songQueue: List<OnlineSong>? = null) {
        when (item) {
            is SearchItem.Song -> {
                val queue = songQueue.orEmpty().ifEmpty { listOf(item.song) }
                val index = queue.indexOfFirst { it.videoId == item.song.videoId }.coerceAtLeast(0)
                viewModel.playOnlineSongWithQueue(item.song, queue, index)
                isSearchActive = false
            }
            is SearchItem.Album -> {
                isSearchActive = false
                viewModel.loadAlbum(item.album.browseId)
            }
            is SearchItem.Artist -> {
                isSearchActive = false
                viewModel.loadArtist(item.artist.browseId, item.artist.thumbnailUrl)
            }
            is SearchItem.Playlist -> {
                isSearchActive = false
                viewModel.loadPlaylist(item.playlist.playlistId)
            }
        }
    }

    val rootTabActive = LocalRootTabActive.current
    LaunchedEffect(rootTabActive) {
        if (!rootTabActive) return@LaunchedEffect
        viewModel.playbackTriggerEvent.collect {
            val song = viewModel.currentOnlineSong.value
            if (song != null) {
                val videoId = song.videoId
                val durationMs = song.durationMs
                val onlineQueue = viewModel.onlineQueue.value
                val playbackContextTitle = viewModel.uiState.value.let { state ->
                    when (state.detailStack.lastOrNull()?.name) {
                        "PLAYLIST" -> state.playlistDetail?.playlist?.title
                        "ALBUM" -> state.albumDetail?.album?.title
                        else -> null
                    }
                }
                if (onlineQueue.isNotEmpty()) {
                    val songItems = onlineQueue.map { os ->
                        SongItem.createOnlineSong(
                            os.videoId,
                            os.title,
                            os.artist,
                            "", // Empty streamUrl permits automatic resolving pipeline promotion
                            os.durationMs,
                            os.thumbnailUrl,
                            os.artistId,
                            os.albumName,
                            os.artistRefs
                        ).apply {
                            playbackContextName = playbackContextTitle
                        }
                    }
                    playerSharedViewModel.setSongList(songItems)
                    val currentIdx = viewModel.currentOnlineIndex.value
                    playerSharedViewModel.playSongAtIndex(currentIdx)
                } else {
                    val songItem = SongItem.createOnlineSong(
                        videoId,
                        song.title,
                        song.artist,
                        "",
                        durationMs,
                        song.thumbnailUrl,
                        song.artistId,
                        song.albumName,
                        song.artistRefs
                    ).apply {
                        playbackContextName = playbackContextTitle
                    }
                    playerSharedViewModel.playSong(songItem)
                }
            }
        }
    }

    LaunchedEffect(rootTabActive) {
        if (!rootTabActive) return@LaunchedEffect
        playerSharedViewModel.currentSongIndex.collect { idx ->
            if (idx >= 0) {
                val queue = viewModel.onlineQueue.value
                val activeVideoId = playerSharedViewModel.currentSong.value?.videoId
                // The shared player index also changes for Library/Cache queues. Do not use
                // that unrelated index to restore a stale Explore selection/highlight.
                if (idx < queue.size &&
                    !activeVideoId.isNullOrBlank() &&
                    queue[idx].videoId == activeVideoId
                ) {
                    viewModel.updateActiveSongAndIndex(idx, queue[idx])
                }
            }
        }
    }

    val homeListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    var hasVisitedSpecificSearchFilter by remember { mutableStateOf(false) }

    val isPlayerExpanded by playerSharedViewModel.isPlayerExpanded.collectAsStateWithLifecycle()
    val playerExpansionFraction by playerSharedViewModel.playerExpansionFraction.collectAsStateWithLifecycle()
    // Keep every Explore-level Back action subordinate until the player is fully closed.
    val isPlayerBackActive = isPlayerExpanded || playerExpansionFraction > 0.01f

    androidx.activity.compose.BackHandler(enabled = showCreditsForSong != null && !isPlayerBackActive) {
        showCreditsForSong = null
    }

    val prefs = remember { context.getSharedPreferences("explore_prefs", 0) }
    var lastPlayedArtist by rememberSaveable { mutableStateOf(prefs.getString("last_played_artist", null)) }
    var customProfileName by remember {
        mutableStateOf(prefs.getString("custom_profile_name", "").orEmpty())
    }

    DisposableEffect(context) {
        val listener =
            android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                if (key == "last_played_artist") lastPlayedArtist =
                    p.getString("last_played_artist", null)
                if (key == "custom_profile_name") customProfileName =
                    p.getString("custom_profile_name", "").orEmpty()
            }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val appPrefs = remember { context.getSharedPreferences("AppSettings", 0) }
    val genreGradientStyle by appPrefs.observeKey("genre_card_gradient_style", "Theme")
    // Read theme values in the composable scope. The remember calculation below is
    // a regular lambda, so it must only use these already-resolved values.
    val genreThemePrimary = DsmusicTheme.colorScheme.primary
    val genreThemeSecondary = DsmusicTheme.colorScheme.secondary
    val genreThemeTertiary = DsmusicTheme.colorScheme.tertiary
    val genreGradientPalette = remember(
        genreGradientStyle,
        genreThemePrimary,
        genreThemeSecondary,
        genreThemeTertiary
    ) {
        val primary = genreThemePrimary
        val secondary = genreThemeSecondary
        val tertiary = genreThemeTertiary
        fun blend(start: Color, end: Color, amount: Float): Color {
            val t = amount.coerceIn(0f, 1f)
            return Color(
                red = start.red + (end.red - start.red) * t,
                green = start.green + (end.green - start.green) * t,
                blue = start.blue + (end.blue - start.blue) * t,
                alpha = 1f
            )
        }
        when (genreGradientStyle) {
            "Aurora" -> listOf(
                Color(0xFF2C9B91), Color(0xFF6579D8), Color(0xFF9B66C8),
                Color(0xFF35AFC0), Color(0xFF7560B9), Color(0xFF5DAD83)
            )
            "Ocean" -> listOf(
                Color(0xFF2879C7), Color(0xFF269DA8), Color(0xFF5867C9),
                Color(0xFF3C9BCC), Color(0xFF346FAD), Color(0xFF58B5B1)
            )
            "Sunset" -> listOf(
                Color(0xFFE36D62), Color(0xFFF0A64D), Color(0xFFC95D91),
                Color(0xFFE98252), Color(0xFFDB6A78), Color(0xFFF1B15F)
            )
            "Forest" -> listOf(
                Color(0xFF38865D), Color(0xFF8AA64F), Color(0xFF368D88),
                Color(0xFF4F9A70), Color(0xFF739B58), Color(0xFF368C69)
            )
            "Berry" -> listOf(
                Color(0xFFA65BC8), Color(0xFFD56D9C), Color(0xFF6265CD),
                Color(0xFFB55CB4), Color(0xFF8D62CB), Color(0xFFD278B5)
            )
            else -> listOf(
                primary,
                secondary,
                tertiary,
                blend(primary, secondary, 0.5f),
                blend(secondary, tertiary, 0.5f),
                blend(tertiary, primary, 0.5f),
                blend(primary, secondary, 0.25f),
                blend(secondary, tertiary, 0.25f),
                blend(tertiary, primary, 0.25f)
            )
        }
    }
    DisposableEffect(context) {
        val listener =
            android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == "yt_cookies" && !isSearchDestination) viewModel.forceReloadHomeFeed()
            }
        appPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { appPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    var isAccountVisible by rememberSaveable { mutableStateOf(false) }
    var showExploreCustomizer by rememberSaveable { mutableStateOf(false) }
    var hiddenExploreSections by remember {
        mutableStateOf(prefs.getStringSet("hidden_home_sections", emptySet())?.toSet().orEmpty())
    }
    var exploreSectionOrder by remember {
        mutableStateOf(prefs.getString("home_section_order", "").orEmpty()
            .split("||").filter { it.isNotBlank() })
    }
    var showExploreMoodChips by rememberSaveable {
        mutableStateOf(prefs.getBoolean("show_home_mood_chips", true))
    }
    val orderedExploreSectionTitles = remember(homeSections, exploreSectionOrder) {
        val loadedTitles = homeSections.map { it.title }
        (exploreSectionOrder.filter { it in loadedTitles } +
            loadedTitles.filter { it !in exploreSectionOrder }).distinct()
    }
    val visibleHomeSections = remember(homeSections, hiddenExploreSections, orderedExploreSectionTitles) {
        val order = orderedExploreSectionTitles.withIndex().associate { it.value to it.index }
        homeSections.filterNot { it.title in hiddenExploreSections }
            .sortedBy { order[it.title] ?: Int.MAX_VALUE }
    }

    fun setExploreMoodChipsVisible(visible: Boolean) {
        showExploreMoodChips = visible
        prefs.edit().putBoolean("show_home_mood_chips", visible).apply()
    }

    fun setExploreSectionVisibility(title: String, visible: Boolean) {
        val updated = if (visible) hiddenExploreSections - title else hiddenExploreSections + title
        hiddenExploreSections = updated
        prefs.edit().putStringSet("hidden_home_sections", updated.toMutableSet()).apply()
    }

    fun moveExploreSection(title: String, direction: Int) {
        val updated = orderedExploreSectionTitles.toMutableList()
        val from = updated.indexOf(title)
        if (from < 0) return
        val to = (from + direction).coerceIn(0, updated.lastIndex)
        if (from == to) return
        val moved = updated.removeAt(from)
        updated.add(to, moved)
        exploreSectionOrder = updated
        prefs.edit().putString("home_section_order", updated.joinToString("||")).apply()
    }

    fun resetExploreCustomizer() {
        hiddenExploreSections = emptySet()
        exploreSectionOrder = homeSections.map { it.title }
        showExploreMoodChips = true
        prefs.edit()
            .remove("hidden_home_sections")
            .remove("home_section_order")
            .putBoolean("show_home_mood_chips", true)
            .apply()
    }

    val detailScreenActive = detailStack.isNotEmpty() || isLoadingDetail || detailOfflineError

    val handleDetailBack = remember(viewModel, mainActivity) {
        {
            val currentState = viewModel.uiState.value
            if (currentState.isLoadingDetail) {
                // A pending page has not been pushed onto detailStack yet. Cancel it and
                // reveal the same parent page instead of consuming Back against an empty stack.
                viewModel.cancelDetailLoad()
            } else if (currentState.detailOfflineError) {
                // A failed detail request is not pushed onto the stack; Back should
                // dismiss its offline state and reveal the existing parent page.
                viewModel.clearError()
            } else {
                val currentStackSize = currentState.detailStack.size
                val isLastDetail = currentStackSize <= 1
                val wasFromLibrary = viewModel.cameFromLibrary && isLastDetail

                viewModel.popDetailStack()

                if (isLastDetail) {
                    viewModel.cameFromLibrary = false
                }

                if (wasFromLibrary) {
                    onNavigateToLibrary()
                }
            }
        }
    }

    androidx.activity.compose.BackHandler(enabled = detailScreenActive && !isPlayerBackActive) {
        handleDetailBack()
    }

    // Active query entry must take priority over the non-active results back handler.
    // Otherwise the first back press only clears the query while leaving the query page open.
    androidx.activity.compose.BackHandler(enabled = isSearchActive && !isPlayerBackActive) {
        if (isSearchDestination) {
            onLeaveSearchDestination()
        } else {
            isSearchActive = false
            viewModel.clearSearch()
        }
    }

    androidx.activity.compose.BackHandler(
        enabled = !isSearchActive && searchQuery.isNotBlank() && !isPlayerBackActive
    ) {
        viewModel.clearSearch()
    }

    androidx.activity.compose.BackHandler(
        enabled = isSearchDestination && !isSearchActive && !detailScreenActive &&
            (showSearchGenreBrowser || searchQuery.isBlank()) &&
            !isPlayerBackActive
    ) {
        if (showSearchGenreBrowser) {
            showSearchGenreBrowser = false
        } else {
            onLeaveSearchDestination()
        }
    }

    androidx.activity.compose.BackHandler(enabled = isAccountVisible && !isPlayerBackActive) {
        isAccountVisible = false
    }

    androidx.activity.compose.BackHandler(enabled = showReplayScreen && !isPlayerBackActive) {
        showReplayScreen = false
    }

    androidx.activity.compose.BackHandler(
        enabled = showListenTogetherSubpage && !isPlayerBackActive
    ) {
        onCloseListenTogetherSubpage()
    }

    androidx.activity.compose.BackHandler(
        enabled = isSearchDestination && showSongRecognizerSubpage && !isPlayerBackActive
    ) {
        onCloseSongRecognizerSubpage()
    }

    LaunchedEffect(isSearchDestination) {
        if (isSearchDestination) {
            viewModel.loadMoodsAndGenres()
        } else {
            viewModel.loadHomeFeed()
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!isLandscape && mainActivity != null) {
                    if (consumed.y < -10f) mainActivity.hideBottomNavWithAnimation()
                    else if (consumed.y > 10f) mainActivity.showBottomNavWithAnimation()
                }
                return super.onPostScroll(consumed, available, source)
            }
        }
    }


    val screenPriority = remember {
        mapOf(
            "home" to 0,
            "account" to 1,
            "loading" to 2,
            "offline" to 2,
            "album" to 3,
            "playlist" to 3,
            "artist" to 3,
            "section" to 3,
            "credits" to 3,
            "listen_together" to 4,
            "song_recognizer" to 4
        )
    }

    val currentScreen =
        remember(detailStack, isLoadingDetail, detailOfflineError, isAccountVisible, showCreditsForSong, showReplayScreen, showListenTogetherSubpage, showSongRecognizerSubpage) {
            when {
                isSearchDestination && showSongRecognizerSubpage -> "song_recognizer"
                showListenTogetherSubpage -> "listen_together"
                detailOfflineError -> "offline"
                isLoadingDetail -> "loading"
                showCreditsForSong != null -> "credits"
                isAccountVisible -> "account"
                detailStack.isNotEmpty() -> {
                    when (detailStack.last()) {
                        DetailType.ALBUM -> "album"
                        DetailType.PLAYLIST -> "playlist"
                        DetailType.ARTIST -> "artist"
                        DetailType.SECTION -> "section"
                    }
                }
                showReplayScreen -> "recap"
                else -> "home"
            }
        }

    // The shortcut belongs to the Search root/results page, not genre browsing or
    // a nested album/playlist/artist/section detail pushed from those results.
    LaunchedEffect(isSearchDestination, currentScreen, showSearchGenreBrowser, showSongRecognizerSubpage) {
        if (isSearchDestination) {
            onSongRecognizerShortcutVisibilityChange(
                currentScreen == "home" && !showSearchGenreBrowser && !showSongRecognizerSubpage
            )
        }
    }

    // AnimatedContent keeps the outgoing and incoming pages side-by-side for the
    // whole slide. A shared opacity dip reaches 50% at the midpoint, then returns to 100%.
    val pageTransitionDurationMillis = LocalPageTransitionDurationMillis.current
    var previousScreen by remember { mutableStateOf(currentScreen) }
    val pageTransitionOpacity = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(currentScreen) {
        if (previousScreen != currentScreen) {
            previousScreen = currentScreen
            val firstHalf = (pageTransitionDurationMillis / 2).coerceAtLeast(1)
            val secondHalf = (pageTransitionDurationMillis - firstHalf).coerceAtLeast(1)
            pageTransitionOpacity.animateTo(
                0.5f,
                androidx.compose.animation.core.tween(
                    durationMillis = firstHalf,
                    easing = androidx.compose.animation.core.LinearEasing
                )
            )
            pageTransitionOpacity.animateTo(
                1f,
                androidx.compose.animation.core.tween(
                    durationMillis = secondHalf,
                    easing = androidx.compose.animation.core.LinearEasing
                )
            )
        }
    }

    val currentFilter by viewModel.searchFilter.collectAsStateWithLifecycle()

    // Returning from a filtered result type to Top results should show its first section,
    // rather than retaining the previous category's deep scroll position.
    LaunchedEffect(currentFilter) {
        if (currentFilter != null) {
            hasVisitedSpecificSearchFilter = true
        } else if (hasVisitedSpecificSearchFilter) {
            searchListState.scrollToItem(0)
            hasVisitedSpecificSearchFilter = false
        }
    }

    val onSearchHeaderQueryChange = remember(viewModel) {
        { query: String -> viewModel.setSearchQuery(query) }
    }
    val onSearchHeaderSearch = remember(viewModel) {
        { query: String ->
            val trimmedQuery = query.trim()
            // Close the editor before starting the request; search results and loading
            // state then render in the results container instead of the keyboard page.
            isSearchActive = false
            if (trimmedQuery.isNotEmpty()) {
                viewModel.search(trimmedQuery)
            }
        }
    }
    val onSearchHeaderActiveChange = remember {
        { active: Boolean -> isSearchActive = active }
    }
    val onSearchHeaderClearHistory = remember(viewModel) {
        { viewModel.clearSearchHistory() }
    }
    val onSearchHeaderRemoveHistoryItem = remember(viewModel) {
        { item: String -> viewModel.removeFromSearchHistory(item) }
    }
    val onSearchHeaderAccountVisibleChange = remember {
        { visible: Boolean -> isAccountVisible = visible }
    }
    val onSearchHeaderMoodClick = remember(viewModel) {
        { mood: String? -> viewModel.setMood(mood) }
    }
    val onSearchHeaderFilterClick = remember(viewModel) {
        { filter: SearchFilter? -> viewModel.setSearchFilter(filter) }
    }
    val onSearchHeaderClearSearch = remember(viewModel) {
        { viewModel.clearSearch() }
    }

    // Solid D.S Launcher surface: no animated mesh, blur, or parallax layer behind long lists.
    val backgroundColor = DsmusicTheme.colorScheme.background

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor).clipToBounds()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // ===== Main Content (Now encapsulates scrolling headers) =====
            Box(modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
                // Let artist/detail navigation from the full player replace Replay,
                // then naturally return to Replay when the detail stack is closed.

                    ExploreTransitionHost(
                    enabled = android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.M
                ) { sharedTransitionScope ->
                    CompositionLocalProvider(LocalSharedTransitionScope provides sharedTransitionScope) {
                        AnimatedContent(
                            targetState = currentScreen,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                alpha = pageTransitionOpacity.value.coerceIn(0.5f, 1f)
                            },
                            transitionSpec = {
                                val direction = if (targetState == "home" || targetState == "recap") -1 else 1
                                slideInHorizontally(
                                    animationSpec = androidx.compose.animation.core.tween(
                                        durationMillis = pageTransitionDurationMillis,
                                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                                    )
                                ) { width -> direction * width } togetherWith
                                    slideOutHorizontally(
                                        animationSpec = androidx.compose.animation.core.tween(
                                            durationMillis = pageTransitionDurationMillis,
                                            easing = androidx.compose.animation.core.FastOutSlowInEasing
                                        )
                                    ) { width -> -direction * width }
                            },
                            label = "Explore Attached Page Slide"
                        ) { screen ->
                            CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                                when (screen) {
                                    "recap" -> RecapTabContent(
                                        nestedScrollConnection = nestedScrollConnection,
                                        viewModel = playerSharedViewModel,
                                        onNavigateToExplore = { showReplayScreen = false },
                                        localSongs = localSongs,
                                        onSongMenuClick = { replaySong ->
                                            val videoId = replaySong.videoId?.takeIf { it.isNotBlank() }
                                            if (videoId != null) {
                                                val localPath = replaySong.path ?: replaySong.data
                                                val artwork = replaySong.thumbnailUrl?.takeIf { candidate ->
                                                    val uri = runCatching { android.net.Uri.parse(candidate) }.getOrNull()
                                                    val scheme = uri?.scheme?.lowercase(java.util.Locale.ROOT)
                                                    val uriPath = uri?.path
                                                    (scheme in setOf("http", "https", "content", "android.resource") ||
                                                        (scheme == "file" && uriPath != localPath)) &&
                                                        candidate != replaySong.contentUri.toString() && candidate != localPath
                                                }
                                                selectedSongForMenu = OnlineSong(
                                                    videoId = videoId,
                                                    title = replaySong.title,
                                                    artist = replaySong.artist,
                                                    albumName = replaySong.albumName,
                                                    albumId = replaySong.albumId.takeIf { it > 0L }?.toString(),
                                                    artistId = replaySong.artistId,
                                                    durationMs = replaySong.duration,
                                                    thumbnailUrl = artwork,
                                                    artistRefs = replaySong.artistRefs.toList()
                                                )
                                            } else {
                                                selectedReplayLocalSongForMenu = replaySong
                                            }
                                        },
                                        onArtistClick = { artistName ->
                                            viewModel.loadArtistByName(artistName)
                                        }
                                    )
                                    "account" -> {
                                        AccountScreen(
                                            viewModel = accountVM,
                                            onBack = { isAccountVisible = false },
                                            onSongClick = { song, queue, index ->
                                                viewModel.playOnlineSongWithQueue(
                                                    song,
                                                    queue,
                                                    index
                                                )
                                            },
                                            onNavigateToSignIn = {
                                                mainActivity?.navigateToGoogleSignIn()
                                            }
                                        )
                                    }

                                    "loading" -> Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(DsmusicTheme.colorScheme.background)
                                            .statusBarsPadding()
                                            .padding(bottom = bottomContentPadding)
                                    ) {
                                        DetailScreenSkeleton()
                                    }

                                    "offline" -> Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(DsmusicTheme.colorScheme.background)
                                            .statusBarsPadding()
                                            .padding(bottom = bottomContentPadding),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        com.dsann.music.ui.theme.NoInternetState(
                                            modifier = Modifier.fillMaxSize(),
                                            onRetry = { viewModel.retryLastDetailLoad() }
                                        )
                                    }

                                    "album" -> albumDetail?.let { detail ->
                                        AlbumDetailView(
                                            albumPage = detail,
                                            currentPlayingVideoId = currentPlayingVideoId,
                                            isLoadingStream = currentTrackIsLoading,
                                             isPlaybackActive = isPlaybackActive,
                                            onBack = { handleDetailBack() },
                                            bottomContentPadding = bottomContentPadding,
                                            onSongClick = { song, queue, index ->
                                                viewModel.playOnlineSongWithQueue(
                                                    song,
                                                    queue,
                                                    index
                                                )
                                            },
                                            onSongMenuClick = { selectedSongForMenu = it },
                                            onCollectionMenuClick = {
                                                selectedCollectionForMenu = SearchItem.Album(detail.album)
                                                selectedCollectionMenuSongs = detail.songs
                                                selectedCollectionMenuContinuation = null
                                            },
                                            onStartRadioClick = { videoId ->
                                                viewModel.startRadio(
                                                    videoId
                                                )
                                            }
                                        )
                                    }

                                    "playlist" -> playlistDetail?.let { detail ->
                                        PlaylistDetailView(
                                            playlistPage = detail,
                                            currentPlayingVideoId = currentPlayingVideoId,
                                            isLoadingStream = currentTrackIsLoading,
                                             isPlaybackActive = isPlaybackActive,
                                            onBack = { handleDetailBack() },
                                            bottomContentPadding = bottomContentPadding,
                                            onSongClick = { song, queue, index ->
                                                viewModel.playOnlineSongWithQueue(
                                                    song,
                                                    queue,
                                                    index
                                                )
                                            },
                                            onSongMenuClick = { selectedSongForMenu = it },
                                            onCollectionMenuClick = {
                                                selectedCollectionForMenu = SearchItem.Playlist(detail.playlist)
                                                selectedCollectionMenuSongs = detail.songs
                                                selectedCollectionMenuContinuation = detail.continuation
                                            },
                                            onStartRadioClick = { videoId ->
                                                viewModel.startRadio(
                                                    videoId
                                                )
                                            },
                                            onPlaylistSaved = { title, description, privacy ->
                                                viewModel.updatePlaylistDetails(
                                                    detail.playlist.playlistId,
                                                    title,
                                                    description,
                                                    privacy
                                                )
                                            },
                                            onPlaylistDeleted = {
                                                accountVM.loadLibrary()
                                                handleDetailBack()
                                            }
                                        )
                                    }

                                    "artist" -> artistDetail?.let { detail ->
                                        ArtistDetailView(
                                            artistPage = detail,
                                            currentPlayingVideoId = currentPlayingVideoId,
                                            isPlaybackActive = isPlaybackActive,
                                             isSubscribed = detail.artist.isSubscribed,
                                            onBack = { handleDetailBack() },
                                            bottomContentPadding = bottomContentPadding,
                                            onSongClick = { song, queue, index ->
                                                viewModel.playOnlineSongWithQueue(
                                                    song,
                                                    queue,
                                                    index
                                                )
                                            },
                                            onAlbumClick = { viewModel.loadAlbum(it.browseId) },
                                            onPlaylistClick = { viewModel.loadPlaylist(it.playlistId) },
                                            onArtistClick = {
                                                viewModel.loadArtist(
                                                    it.browseId,
                                                    it.thumbnailUrl
                                                )
                                            },
                                            onSongMenuClick = { selectedSongForMenu = it },
                                            onSubscribeClick = { channelId ->
                                                if (detail.artist.isSubscribed) {
                                                    viewModel.unsubscribeFromArtist(channelId)
                                                } else {
                                                    viewModel.subscribeToArtist(channelId)
                                                }
                                            },
                                            onStartRadioClick = { videoId ->
                                                viewModel.startRadio(
                                                    videoId
                                                )
                                            },
                                            onSectionClick = { browseId, params, title ->
                                                viewModel.loadSectionDetails(
                                                    browseId,
                                                    params,
                                                    title
                                                )
                                            }
                                        )
                                    }

                                    "section" -> sectionDetail?.let { detail ->
                                        SectionDetailView(
                                            section = detail,
                                            currentPlayingVideoId = currentPlayingVideoId,
                                            isLoadingStream = currentTrackIsLoading,
                                             isPlaybackActive = isPlaybackActive,
                                            onBack = { handleDetailBack() },
                                            bottomContentPadding = bottomContentPadding,
                                            onSongClick = { song, queue, index ->
                                                viewModel.playOnlineSongWithQueue(
                                                    song,
                                                    queue,
                                                    index
                                                )
                                            },
                                            onAlbumClick = { viewModel.loadAlbum(it.browseId) },
                                            onPlaylistClick = { viewModel.loadPlaylist(it.playlistId) },
                                            onArtistClick = {
                                                viewModel.loadArtist(
                                                    it.browseId,
                                                    it.thumbnailUrl
                                                )
                                            },
                                            onSongMenuClick = { selectedSongForMenu = it },
                                            onStartRadioClick = { videoId ->
                                                viewModel.startRadio(
                                                    videoId
                                                )
                                            }
                                        )
                                    }

                                    "listen_together" -> com.dsann.music.ui.ListenTogetherScreen(
                                        viewModel = playerSharedViewModel,
                                        onBack = onCloseListenTogetherSubpage
                                    )

                                    "song_recognizer" -> com.dsann.music.ui.SongRecognizerScreen(
                                        onBack = onCloseSongRecognizerSubpage,
                                        onPlaySong = { song ->
                                            playerSharedViewModel.setSongList(listOf(song))
                                            playerSharedViewModel.playSongAtIndex(0)
                                            com.dsann.music.ui.SnackbarController.showMessage("Playing ${song.title}")
                                        },
                                        onPlayNextSong = { song ->
                                            playerSharedViewModel.addToQueueNext(song)
                                            com.dsann.music.ui.SnackbarController.showMessage("Playing next: ${song.title}")
                                        },
                                        onAddToQueue = { song ->
                                            playerSharedViewModel.addToQueue(song)
                                            com.dsann.music.ui.SnackbarController.showMessage("Added to queue: ${song.title}")
                                        }
                                    )

                                    "credits" -> showCreditsForSong?.let { s ->
                                        SongCreditsScreen(
                                            song = s,
                                            onBack = { showCreditsForSong = null }
                                        )
                                    }

                                    "home" -> Column(modifier = Modifier.fillMaxSize()) {
                                        SearchHeader(
                                            searchQuery = searchQuery,
                                            onQueryChange = onSearchHeaderQueryChange,
                                            onSearch = onSearchHeaderSearch,
                                            isSearchActive = isSearchActive,
                                            isSearchDestination = isSearchDestination,
                                            showSearchEntry = isSearchDestination,
                                            onLeaveSearchDestination = onLeaveSearchDestination,
                                            accountHistory = accountHistory,
                                            onAccountHistorySongClick = { song, queue, index ->
                                                viewModel.playOnlineSongWithQueue(
                                                    song,
                                                    queue,
                                                    index
                                                )
                                            },
                                            onSearchActiveChange = onSearchHeaderActiveChange,
                                            searchResults = searchResults,
                                            searchHistory = searchHistory,
                                            onClearSearchHistory = onSearchHeaderClearHistory,
                                            onRemoveFromSearchHistory = onSearchHeaderRemoveHistoryItem,
                                            onHistoryItemClick = { historyItem ->
                                                scope.launch {
                                                    isSearchActive = false
                                                    delay(50.milliseconds)
                                                    viewModel.search(historyItem)
                                                }
                                            },
                                            suggestions = suggestions,
                                            suggestionItems = suggestionItems,
                                            onSuggestionClick = { suggestion ->
                                                scope.launch {
                                                    isSearchActive = false
                                                    delay(50.milliseconds)
                                                    viewModel.search(suggestion)
                                                }
                                            },
                                            onSuggestionItemClick = { item ->
                                                activateSearchItem(item)
                                            },
                                            onCollectionMenuClick = { item ->
                                                selectedCollectionForMenu = item
                                                selectedCollectionMenuSongs = null
                                                selectedCollectionMenuContinuation = null
                                            },
                                            onAccountVisibleChange = onSearchHeaderAccountVisibleChange,
                                            onCustomizeExplore = { showExploreCustomizer = true },
                                            userProfile = userProfile,
                                            homeMoods = if (!isSearchDestination && showExploreMoodChips) homeMoods else emptyList(),
                                            currentMood = if (isSearchDestination) null else currentMood,
                                            onMoodClick = onSearchHeaderMoodClick,
                                            currentFilter = currentFilter,
                                            onFilterClick = onSearchHeaderFilterClick,
                                            isLandscape = isLandscape,
                                            onClearSearch = onSearchHeaderClearSearch,
                                            showGenreBrowser = showSearchGenreBrowser,
                                            onGenreBrowserBack = { showSearchGenreBrowser = false },
                                            onNavigateToListenTogether = onNavigateToListenTogether
                                        )

                                        Box(modifier = Modifier.weight(1f)) {
                                            when {
                                                isSearchDestination && showSearchGenreBrowser && !isSearchActive && searchQuery.isBlank() -> {
                                                    LazyColumn(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = bottomContentPadding),
                                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        item(key = "search-genre-intro") {
                                                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                                Text(
                                                                    "Every sound has a place.",
                                                                    style = DsmusicTheme.typography.titleLarge,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = DsmusicTheme.colorScheme.onSurface
                                                                )
                                                                Text(
                                                                    "Choose a genre to explore its music.",
                                                                    style = DsmusicTheme.typography.bodySmall,
                                                                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        }
                                                        if (sortedHomeCategories.isEmpty()) {
                                                            item(key = "search-genre-load-state") {
                                                                Column(
                                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                                                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                                ) {
                                                                    when {
                                                                        isLoadingGenres -> {
                                                                            LoadingIndicator(Modifier.size(30.dp))
                                                                            Text(
                                                                                "Loading moods and genres…",
                                                                                style = DsmusicTheme.typography.bodyMedium,
                                                                                color = DsmusicTheme.colorScheme.onSurfaceVariant
                                                                            )
                                                                        }
                                                                        genresError != null -> {
                                                                            if (genresOfflineError) {
                                                                                com.dsann.music.ui.theme.NoInternetState(
                                                                                    modifier = Modifier.fillMaxWidth(),
                                                                                    onRetry = {
                                                                                        viewModel.loadMoodsAndGenres(forceRefresh = true)
                                                                                    },
                                                                                    compact = true
                                                                                )
                                                                            } else {
                                                                                Text(
                                                                                    genresError,
                                                                                    style = DsmusicTheme.typography.bodyMedium,
                                                                                    color = DsmusicTheme.colorScheme.onSurfaceVariant,
                                                                                    textAlign = TextAlign.Center
                                                                                )
                                                                                DsmusicTextButton(
                                                                                    onClick = { viewModel.loadMoodsAndGenres(forceRefresh = true) }
                                                                                ) {
                                                                                    Text("Try again")
                                                                                }
                                                                            }
                                                                        }
                                                                        else -> {
                                                                            LoadingIndicator(Modifier.size(26.dp))
                                                                            Text(
                                                                                "Preparing music discovery…",
                                                                                style = DsmusicTheme.typography.bodySmall,
                                                                                color = DsmusicTheme.colorScheme.onSurfaceVariant
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        itemsIndexed(
                                                            items = sortedHomeCategories.chunked(2),
                                                            key = { rowIndex, rowCategories ->
                                                                // Include the full browse destination and row position so
                                                                // repeated browse IDs still have stable, unique keys.
                                                                "genre-row:$rowIndex:" + rowCategories.joinToString("|") {
                                                                    "${it.browseId}:${it.params.orEmpty()}:${it.title}"
                                                                }
                                                            }
                                                        ) { rowIndex, rowCategories ->
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                            ) {
                                                                rowCategories.forEachIndexed { columnIndex, category ->
                                                                    val genreIndex = rowIndex * 2 + columnIndex
                                                                    val firstColor = genreGradientPalette[genreIndex % genreGradientPalette.size]
                                                                    val secondColor = genreGradientPalette[
                                                                        (genreIndex + 1) % genreGradientPalette.size
                                                                    ]
                                                                    val tileBackground = Brush.linearGradient(
                                                                        listOf(
                                                                            firstColor.copy(alpha = 0.34f),
                                                                            secondColor.copy(alpha = 0.27f),
                                                                            DsmusicTheme.colorScheme.surfaceContainerHigh
                                                                        )
                                                                    )
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .weight(1f)
                                                                            .height(112.dp)
                                                                            .clip(RoundedCornerShape(20.dp))
                                                                            .background(tileBackground)
                                                                            .clickable {
                                                                                // Keep the genre browser as this detail page's parent so Back
                                                                                // returns to the same category cards, not the Search home page.
                                                                                viewModel.loadSectionDetails(
                                                                                    category.browseId,
                                                                                    category.params,
                                                                                    category.title,
                                                                                    preferFallbackTitle = true
                                                                                )
                                                                            }
                                                                            .padding(12.dp)
                                                                    ) {
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .align(Alignment.TopEnd)
                                                                                
                                                                                .size(40.dp)
                                                                                .clip(CircleShape)
                                                                                .background(firstColor.copy(alpha = 0.16f)),
                                                                            contentAlignment = Alignment.Center
                                                                        ) {
                                                                            Icon(
                                                                                Icons.Rounded.MusicNote,
                                                                                contentDescription = null,
                                                                                tint = firstColor,
                                                                                modifier = Modifier.size(22.dp)
                                                                            )
                                                                        }
                                                                        Column(
                                                                            modifier = Modifier.fillMaxSize(),
                                                                            verticalArrangement = Arrangement.Bottom
                                                                        ) {
                                                                            Text(
                                                                                category.title,
                                                                                style = DsmusicTheme.typography.titleMedium,
                                                                                fontWeight = FontWeight.Bold,
                                                                                color = DsmusicTheme.colorScheme.onSurface,
                                                                                maxLines = 2,
                                                                                overflow = TextOverflow.Ellipsis
                                                                            )
                                                                            Text(
                                                                                "Explore music",
                                                                                style = DsmusicTheme.typography.labelSmall,
                                                                                color = DsmusicTheme.colorScheme.onSurfaceVariant,
                                                                                maxLines = 1
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                                if (rowCategories.size == 1) Spacer(Modifier.weight(1f))
                                                            }
                                                        }
                                                    }
                                                }
                                                isSearchDestination && !isSearchActive && searchQuery.isBlank() -> {
                                                    LazyColumn(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentPadding = PaddingValues(
                                                            start = 18.dp,
                                                            end = 18.dp,
                                                            top = 18.dp,
                                                            bottom = bottomContentPadding
                                                        ),
                                                        verticalArrangement = Arrangement.spacedBy(22.dp)
                                                    ) {
                                                        item(key = "search-discovery-cards") {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                                verticalAlignment = Alignment.Top
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .height(190.dp)
                                                                        .clip(RoundedCornerShape(24.dp))
                                                                        .background(
                                                                            Brush.linearGradient(
                                                                                listOf(
                                                                                    DsmusicTheme.colorScheme.primaryContainer.copy(alpha = 0.90f),
                                                                                    DsmusicTheme.colorScheme.surfaceContainerHigh,
                                                                                    DsmusicTheme.colorScheme.surfaceContainerLow
                                                                                )
                                                                            )
                                                                        )
                                                                        .padding(14.dp)
                                                                ) {
                                                                    Icon(
                                                                        Icons.Rounded.Search,
                                                                        contentDescription = null,
                                                                        tint = DsmusicTheme.colorScheme.primary.copy(alpha = 0.68f),
                                                                        modifier = Modifier
                                                                            .align(Alignment.TopEnd)
                                                                            .padding(top = 26.dp)
                                                                            .size(25.dp)
                                                                    )
                                                                    Column(
                                                                        modifier = Modifier.fillMaxSize(),
                                                                        verticalArrangement = Arrangement.spacedBy(7.dp)
                                                                    ) {
                                                                        Text(
                                                                            "YOUR NEXT FAVOURITE",
                                                                            style = DsmusicTheme.typography.labelSmall.copy(
                                                                                letterSpacing = 1.0.sp,
                                                                                fontWeight = FontWeight.Bold
                                                                            ),
                                                                            color = DsmusicTheme.colorScheme.primary
                                                                        )
                                                                        Spacer(Modifier.weight(1f))
                                                                        Text(
                                                                            "Find your\nnext repeat.",
                                                                            style = DsmusicTheme.typography.titleLarge.copy(
                                                                                fontSize = 22.sp,
                                                                                lineHeight = 25.sp,
                                                                                fontWeight = FontWeight.ExtraBold
                                                                            ),
                                                                            color = DsmusicTheme.colorScheme.onSurface,
                                                                            maxLines = 2,
                                                                            overflow = TextOverflow.Ellipsis
                                                                        )
                                                                        Text(
                                                                            "Songs, artists, albums and playlists.",
                                                                            style = DsmusicTheme.typography.bodySmall,
                                                                            color = DsmusicTheme.colorScheme.onSurfaceVariant,
                                                                            maxLines = 2,
                                                                            overflow = TextOverflow.Ellipsis
                                                                        )
                                                                    }
                                                                }

                                                                if (isSearchDestination) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .weight(1f)
                                                                            .height(190.dp)
                                                                            .clip(RoundedCornerShape(24.dp))
                                                                            .background(
                                                                                Brush.linearGradient(
                                                                                    listOf(
                                                                                        genreGradientPalette[0].copy(alpha = 0.30f),
                                                                                        genreGradientPalette[1].copy(alpha = 0.22f),
                                                                                        DsmusicTheme.colorScheme.surfaceContainerHigh
                                                                                    )
                                                                                )
                                                                            )
                                                                            .clickable {
                                                                                viewModel.loadMoodsAndGenres()
                                                                                showSearchGenreBrowser = true
                                                                            }
                                                                            .padding(14.dp)
                                                                    ) {
                                                                        Icon(
                                                                            Icons.Rounded.MusicNote,
                                                                            contentDescription = null,
                                                                            tint = genreGradientPalette[0],
                                                                            modifier = Modifier
                                                                                .align(Alignment.TopEnd)
                                                                                .padding(top = 28.dp)
                                                                                .size(28.dp)
                                                                        )
                                                                        Column(
                                                                            modifier = Modifier.fillMaxSize(),
                                                                            verticalArrangement = Arrangement.spacedBy(7.dp)
                                                                        ) {
                                                                            Text(
                                                                                "DISCOVER BY SOUND",
                                                                                style = DsmusicTheme.typography.labelSmall.copy(
                                                                                    letterSpacing = 1.0.sp,
                                                                                    fontWeight = FontWeight.Bold
                                                                                ),
                                                                                color = DsmusicTheme.colorScheme.primary
                                                                            )
                                                                            Spacer(Modifier.weight(1f))
                                                                            Text(
                                                                                "Explore genres",
                                                                                style = DsmusicTheme.typography.titleLarge.copy(
                                                                                    fontSize = 22.sp,
                                                                                    lineHeight = 25.sp,
                                                                                    fontWeight = FontWeight.ExtraBold
                                                                                ),
                                                                                color = DsmusicTheme.colorScheme.onSurface,
                                                                                maxLines = 2,
                                                                                overflow = TextOverflow.Ellipsis
                                                                            )
                                                                            Text(
                                                                                if (sortedHomeCategories.isNotEmpty()) {
                                                                                    sortedHomeCategories.take(2).joinToString(" · ") { it.title } +
                                                                                        if (sortedHomeCategories.size > 2) " · more" else ""
                                                                                } else {
                                                                                    "Moods, genres and curated sounds."
                                                                                },
                                                                                style = DsmusicTheme.typography.bodySmall,
                                                                                color = DsmusicTheme.colorScheme.onSurfaceVariant,
                                                                                maxLines = 2,
                                                                                overflow = TextOverflow.Ellipsis
                                                                            )
                                                                            Row(
                                                                                verticalAlignment = Alignment.CenterVertically,
                                                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                                            ) {
                                                                                Text(
                                                                                    "Browse",
                                                                                    style = DsmusicTheme.typography.labelLarge,
                                                                                    fontWeight = FontWeight.Bold,
                                                                                    color = DsmusicTheme.colorScheme.primary
                                                                                )
                                                                                Icon(
                                                                                    Icons.Rounded.ChevronRight,
                                                                                    contentDescription = null,
                                                                                    tint = DsmusicTheme.colorScheme.primary,
                                                                                    modifier = Modifier.size(17.dp)
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        if (searchHistory.isNotEmpty()) {
                                                            item(key = "search-recent-heading") {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Column(modifier = Modifier.weight(1f)) {
                                                                        Text(
                                                                            "Recent searches",
                                                                            style = DsmusicTheme.typography.titleMedium,
                                                                            fontWeight = FontWeight.Bold,
                                                                            color = DsmusicTheme.colorScheme.onSurface
                                                                        )
                                                                        Text(
                                                                            "Pick up where you left off.",
                                                                            style = DsmusicTheme.typography.bodySmall,
                                                                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                                                                        )
                                                                    }
                                                                    DsmusicTextButton(onClick = onSearchHeaderClearHistory) {
                                                                        Text("Clear all")
                                                                    }
                                                                }
                                                            }
                                                            itemsIndexed(
                                                                searchHistory,
                                                                key = { index, item -> "landing-history:$index:$item" }
                                                            ) { index, historyItem ->
                                                                Column {
                                                                    Row(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .clip(RoundedCornerShape(12.dp))
                                                                            .clickable {
                                                                                scope.launch {
                                                                                    isSearchActive = false
                                                                                    delay(50.milliseconds)
                                                                                    viewModel.search(historyItem)
                                                                                }
                                                                            }
                                                                            .padding(horizontal = 4.dp, vertical = 8.dp),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .size(36.dp)
                                                                                .clip(CircleShape)
                                                                                .background(DsmusicTheme.colorScheme.surfaceContainerHigh),
                                                                            contentAlignment = Alignment.Center
                                                                        ) {
                                                                            Icon(
                                                                                Icons.Rounded.History,
                                                                                contentDescription = null,
                                                                                tint = DsmusicTheme.colorScheme.primary,
                                                                                modifier = Modifier.size(18.dp)
                                                                            )
                                                                        }
                                                                        Spacer(Modifier.width(12.dp))
                                                                        Text(
                                                                            historyItem,
                                                                            modifier = Modifier.weight(1f),
                                                                            style = DsmusicTheme.typography.bodyMedium,
                                                                            color = DsmusicTheme.colorScheme.onSurface,
                                                                            maxLines = 1,
                                                                            overflow = TextOverflow.Ellipsis
                                                                        )
                                                                        DsmusicIconButton(
                                                                            onClick = { onSearchHeaderRemoveHistoryItem(historyItem) },
                                                                            modifier = Modifier.size(36.dp)
                                                                        ) {
                                                                            Icon(
                                                                                Icons.Rounded.Close,
                                                                                contentDescription = "Remove search",
                                                                                tint = DsmusicTheme.colorScheme.onSurfaceVariant,
                                                                                modifier = Modifier.size(17.dp)
                                                                            )
                                                                        }
                                                                    }
                                                                    if (index < searchHistory.lastIndex) {
                                                                        Box(
                                                                            Modifier
                                                                                .fillMaxWidth()
                                                                                .padding(start = 52.dp, end = 8.dp)
                                                                                .height(0.7.dp)
                                                                                .background(DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        if (userProfile != null && recentAccountHistory.isNotEmpty()) {
                                                            item(key = "search-played-heading") {
                                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                    Text(
                                                                        "Recently played",
                                                                        style = DsmusicTheme.typography.titleMedium,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = DsmusicTheme.colorScheme.onSurface
                                                                    )
                                                                    Text(
                                                                        "Jump straight back into your music.",
                                                                        style = DsmusicTheme.typography.bodySmall,
                                                                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                            }
                                                            itemsIndexed(
                                                                recentAccountHistory,
                                                                key = { index, _ -> "search-played:$index" }
                                                            ) { index, song ->
                                                                Column {
                                                                    Row(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .clip(RoundedCornerShape(12.dp))
                                                                            .clickable {
                                                                                viewModel.playOnlineSongWithQueue(song, recentAccountHistory, index)
                                                                            }
                                                                            .padding(vertical = 7.dp, horizontal = 4.dp),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        AsyncImage(
                                                                            model = rememberExploreImageRequest(song.thumbnailUrl, 144),
                                                                            contentDescription = null,
                                                                            modifier = Modifier
                                                                                .size(48.dp)
                                                                                .clip(RoundedCornerShape(11.dp)),
                                                                            contentScale = ContentScale.Crop
                                                                        )
                                                                        Spacer(Modifier.width(12.dp))
                                                                        Column(modifier = Modifier.weight(1f)) {
                                                                            Text(
                                                                                song.title,
                                                                                style = DsmusicTheme.typography.bodyMedium,
                                                                                fontWeight = FontWeight.SemiBold,
                                                                                color = DsmusicTheme.colorScheme.onSurface,
                                                                                maxLines = 1,
                                                                                overflow = TextOverflow.Ellipsis
                                                                            )
                                                                            Text(
                                                                                song.artist,
                                                                                style = DsmusicTheme.typography.bodySmall,
                                                                                color = DsmusicTheme.colorScheme.onSurfaceVariant,
                                                                                maxLines = 1,
                                                                                overflow = TextOverflow.Ellipsis
                                                                            )
                                                                        }
                                                                        Icon(
                                                                            Icons.Rounded.MusicNote,
                                                                            contentDescription = "Play",
                                                                            tint = DsmusicTheme.colorScheme.primary,
                                                                            modifier = Modifier.padding(horizontal = 8.dp).size(19.dp)
                                                                        )
                                                                    }
                                                                    if (index < recentAccountHistory.lastIndex) {
                                                                        Box(
                                                                            Modifier
                                                                                .fillMaxWidth()
                                                                                .padding(start = 64.dp, end = 8.dp)
                                                                                .height(0.7.dp)
                                                                                .background(DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        if (homeMoods.isEmpty() && searchHistory.isEmpty() && recentAccountHistory.isEmpty()) {
                                                            item(key = "search-empty-start") {
                                                                Column(
                                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                                ) {
                                                                    Icon(
                                                                        Icons.Rounded.MusicNote,
                                                                        contentDescription = null,
                                                                        tint = DsmusicTheme.colorScheme.primary,
                                                                        modifier = Modifier.size(30.dp)
                                                                    )
                                                                    Text(
                                                                        "A world of music awaits.",
                                                                        style = DsmusicTheme.typography.titleMedium,
                                                                        fontWeight = FontWeight.SemiBold
                                                                    )
                                                                    Text(
                                                                        "Tap the search field above to get started.",
                                                                        style = DsmusicTheme.typography.bodySmall,
                                                                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                isSearching && searchResults.isEmpty() && searchTopResult == null -> {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) { LoadingIndicator() }
                                                }

                                                searchOfflineError && searchResults.isEmpty() && searchTopResult == null -> {
                                                    com.dsann.music.ui.theme.NoInternetState(
                                                        modifier = Modifier.fillMaxSize(),
                                                        onRetry = { viewModel.search(searchQuery) }
                                                    )
                                                }

                                                searchQuery.isNotBlank() && (searchResults.isNotEmpty() || searchTopResult != null) -> {
                                                    val topSongQueue = remember(searchSections, searchTopResult) {
                                                        (searchSections.flatMap { section ->
                                                            section.items.filterIsInstance<SearchItem.Song>().map { it.song }
                                                        } + listOfNotNull((searchTopResult as? SearchItem.Song)?.song))
                                                            .distinctBy { it.videoId }
                                                    }
                                                    LazyColumn(
                                                        state = searchListState,
                                                        modifier = Modifier.fillMaxSize().background(Color.Transparent),
                                                        contentPadding = PaddingValues(bottom = bottomContentPadding)
                                                    ) {
                                                        if (searchOfflineError) {
                                                            item(key = "search-offline-retry") {
                                                                com.dsann.music.ui.theme.NoInternetState(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    onRetry = { viewModel.loadMoreResults() },
                                                                    compact = true,
                                                                    title = "Connection interrupted",
                                                                    message = "Some results could not be loaded."
                                                                )
                                                            }
                                                        }
                                                        if (currentFilter == null) {
                                                            searchTopResult?.let { top ->
                                                                item(key = "search-top-result") {
                                                                    SearchTopResultCard(
                                                                        item = top,
                                                                        isCurrentlyPlaying = top is SearchItem.Song &&
                                                                            top.song.videoId == currentPlayingVideoId,
                                                                        isLoading = top is SearchItem.Song && currentTrackIsLoading &&
                                                                            top.song.videoId == currentPlayingVideoId,
                                                                         isPlaybackActive = isPlaybackActive,
                                                                        onSongMenuClick = { selectedSongForMenu = it },
                                                                        onCollectionMenuClick = { item ->
                                                                            selectedCollectionForMenu = item
                                                                            selectedCollectionMenuSongs = null
                                                                            selectedCollectionMenuContinuation = null
                                                                        },
                                                                        onTouchDown = {
                                                                            if (top is SearchItem.Song) {
                                                                                playerSharedViewModel.specPrefetch(
                                                                                    SongItem.createOnlineSong(
                                                                                        top.song.videoId, top.song.title, top.song.artist, "",
                                                                                        top.song.durationMs, top.song.thumbnailUrl, top.song.artistId,
                                                                                        top.song.albumName
                                                                                    )
                                                                                )
                                                                            }
                                                                        },
                                                                        onClick = { activateSearchItem(top, topSongQueue) }
                                                                    )
                                                                }
                                                            }

                                                            searchSections.forEach { section ->
                                                                item(key = "search-section:${section.filter.name}") {
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth()
                                                                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Text(
                                                                            section.filter.label,
                                                                            modifier = Modifier.weight(1f),
                                                                            style = DsmusicTheme.typography.titleMedium,
                                                                            fontWeight = FontWeight.SemiBold
                                                                        )
                                                                        DsmusicTextButton(onClick = { viewModel.setSearchFilter(section.filter) }) {
                                                                            Text("See all")
                                                                        }
                                                                    }
                                                                }

                                                                val songRows = section.items.filterIsInstance<SearchItem.Song>()
                                                                val useRows = section.filter in setOf(
                                                                    SearchFilter.SONGS, SearchFilter.VIDEOS,
                                                                    SearchFilter.EPISODES, SearchFilter.LIVE
                                                                )
                                                                if (useRows) {
                                                                    items(
                                                                        section.items,
                                                                        key = { item ->
                                                                            when (item) {
                                                                                is SearchItem.Song -> "song:${item.song.videoId}"
                                                                                is SearchItem.Album -> "album:${item.album.browseId}"
                                                                                is SearchItem.Artist -> "artist:${item.artist.browseId}"
                                                                                is SearchItem.Playlist -> "playlist:${item.playlist.playlistId}"
                                                                            }
                                                                        }
                                                                    ) { item ->
                                                                        SearchResultItem(
                                                                            item = item,
                                                                            isCurrentlyPlaying = item is SearchItem.Song &&
                                                                                item.song.videoId == currentPlayingVideoId,
                                                                            isLoading = item is SearchItem.Song && currentTrackIsLoading &&
                                                                                item.song.videoId == currentPlayingVideoId,
                                                                            isPlaybackActive = isPlaybackActive,
                                                                            onSongMenuClick = { selectedSongForMenu = it },
                                                                            onCollectionMenuClick = { item ->
                                                                                selectedCollectionForMenu = item
                                                                                selectedCollectionMenuSongs = null
                                                                                selectedCollectionMenuContinuation = null
                                                                            },
                                                                            onTouchDown = {
                                                                                if (item is SearchItem.Song) {
                                                                                    playerSharedViewModel.specPrefetch(
                                                                                        SongItem.createOnlineSong(
                                                                                            item.song.videoId, item.song.title, item.song.artist, "",
                                                                                            item.song.durationMs, item.song.thumbnailUrl, item.song.artistId,
                                                                                            item.song.albumName
                                                                                        )
                                                                                    )
                                                                                }
                                                                            },
                                                                            onClick = { activateSearchItem(item, songRows.map { it.song }) }
                                                                        )
                                                                    }
                                                                } else {
                                                                    item(key = "search-shelf:${section.filter.name}") {
                                                                        LazyRow(
                                                                            contentPadding = PaddingValues(horizontal = 12.dp),
                                                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                                        ) {
                                                                            items(
                                                                                section.items,
                                                                                key = { item ->
                                                                                    when (item) {
                                                                                        is SearchItem.Song -> "song:${item.song.videoId}"
                                                                                        is SearchItem.Album -> "album:${item.album.browseId}"
                                                                                        is SearchItem.Artist -> "artist:${item.artist.browseId}"
                                                                                        is SearchItem.Playlist -> "playlist:${item.playlist.playlistId}"
                                                                                    }
                                                                                }
                                                                            ) { item ->
                                                                                SearchBrowseCard(
                                                                                    item = item,
                                                                                    onClick = { activateSearchItem(item) },
                                                                                    onLongClick = {
                                                                                        when (item) {
                                                                                            is SearchItem.Song -> selectedSongForMenu = item.song
                                                                                            is SearchItem.Album, is SearchItem.Playlist -> {
                                                                                                selectedCollectionForMenu = item
                                                                                                selectedCollectionMenuSongs = null
                                                                                                selectedCollectionMenuContinuation = null
                                                                                            }
                                                                                            is SearchItem.Artist -> Unit
                                                                                        }
                                                                                    }
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            if (searchSections.isEmpty() && searchResults.isNotEmpty()) {
                                                                item(key = "search-fallback-heading") {
                                                                    Text(
                                                                        "More results",
                                                                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp),
                                                                        style = DsmusicTheme.typography.titleMedium,
                                                                        fontWeight = FontWeight.SemiBold
                                                                    )
                                                                }
                                                                items(searchResults, key = { item ->
                                                                    when (item) {
                                                                        is SearchItem.Song -> "song:${item.song.videoId}"
                                                                        is SearchItem.Album -> "album:${item.album.browseId}"
                                                                        is SearchItem.Artist -> "artist:${item.artist.browseId}"
                                                                        is SearchItem.Playlist -> "playlist:${item.playlist.playlistId}"
                                                                    }
                                                                }) { item ->
                                                                    SearchResultItem(
                                                                        item = item,
                                                                        isCurrentlyPlaying = item is SearchItem.Song && item.song.videoId == currentPlayingVideoId,
                                                                        isLoading = item is SearchItem.Song && currentTrackIsLoading && item.song.videoId == currentPlayingVideoId,
                                                                        isPlaybackActive = isPlaybackActive,
                                                                        onSongMenuClick = { selectedSongForMenu = it },
                                                                        onCollectionMenuClick = { collection ->
                                                                            selectedCollectionForMenu = collection
                                                                            selectedCollectionMenuSongs = null
                                                                            selectedCollectionMenuContinuation = null
                                                                        },
                                                                        onClick = { activateSearchItem(item) }
                                                                    )
                                                                }
                                                            }
                                                        } else {
                                                            items(searchResults, key = { item ->
                                                                when (item) {
                                                                    is SearchItem.Song -> "song:${item.song.videoId}"
                                                                    is SearchItem.Album -> "album:${item.album.browseId}"
                                                                    is SearchItem.Artist -> "artist:${item.artist.browseId}"
                                                                    is SearchItem.Playlist -> "playlist:${item.playlist.playlistId}"
                                                                }
                                                            }) { item ->
                                                                SearchResultItem(
                                                                    item = item,
                                                                    isCurrentlyPlaying = item is SearchItem.Song && item.song.videoId == currentPlayingVideoId,
                                                                    isLoading = item is SearchItem.Song && currentTrackIsLoading && item.song.videoId == currentPlayingVideoId,
                                                                    isPlaybackActive = isPlaybackActive,
                                                                    onSongMenuClick = { selectedSongForMenu = it },
                                                                    onCollectionMenuClick = { item ->
                                                                        selectedCollectionForMenu = item
                                                                        selectedCollectionMenuSongs = null
                                                                        selectedCollectionMenuContinuation = null
                                                                    },
                                                                    onTouchDown = {
                                                                        if (item is SearchItem.Song) {
                                                                            playerSharedViewModel.specPrefetch(
                                                                                SongItem.createOnlineSong(
                                                                                    item.song.videoId, item.song.title, item.song.artist, "",
                                                                                    item.song.durationMs, item.song.thumbnailUrl, item.song.artistId,
                                                                                    item.song.albumName, item.song.artistRefs
                                                                                )
                                                                            )
                                                                        }
                                                                    },
                                                                    onClick = {
                                                                        activateSearchItem(
                                                                            item,
                                                                            searchResults.filterIsInstance<SearchItem.Song>().map { it.song }
                                                                        )
                                                                    }
                                                                )
                                                            }
                                                            if (isSearching) item {
                                                                Box(
                                                                    Modifier.fillMaxWidth().padding(16.dp),
                                                                    contentAlignment = Alignment.Center
                                                                ) { LoadingIndicator(Modifier.size(28.dp)) }
                                                            }
                                                        }
                                                    }

                                                    LaunchedEffect(searchListState, currentFilter, searchResults.size, searchSections.size) {
                                                        if (currentFilter != null) {
                                                            snapshotFlow { searchListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                                                                .collect { lastVisibleIndex ->
                                                                    val threshold = searchResults.size - 3
                                                                    if (lastVisibleIndex != null && lastVisibleIndex >= threshold) {
                                                                        viewModel.loadMoreResults()
                                                                    }
                                                                }
                                                        }
                                                    }
                                                }

                                                searchQuery.isNotBlank() -> {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Rounded.Search,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(48.dp),
                                                                tint = DsmusicTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Text(
                                                                text = "No results found for \"$searchQuery\"",
                                                                style = DsmusicTheme.typography.bodyLarge,
                                                                color = DsmusicTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }

                                                else -> {
                                                    val pullToRefreshState =
                                                        rememberPullToRefreshState()
                                                    PullToRefreshBox(
                                                        isRefreshing = isRefreshing,
                                                        onRefresh = { viewModel.refreshHomeFeed() },
                                                        state = pullToRefreshState,
                                                        modifier = Modifier.fillMaxSize(),
                                                        indicator = {
                                                            // During a pull, show the reversible pull indicator.
                                                            // Once refresh begins on release, the centered loading state below
                                                            // takes over when there is no existing feed to keep on screen.
                                                            if (homeSections.isNotEmpty() ||
                                                                (!isRefreshing && pullToRefreshState.progress > 0.05f)
                                                            ) {
                                                                PullToRefreshDefaults.LoadingIndicator(
                                                                    state = pullToRefreshState,
                                                                    isRefreshing = isRefreshing,
                                                                    modifier = Modifier.align(Alignment.TopCenter)
                                                                )
                                                            }
                                                        }
                                                    ) {
                                                        when {
                                                            homeSections.isNotEmpty() -> {
                                                                PrefetchExploreHomeSectionImages(
                                                                    sections = visibleHomeSections,
                                                                    state = homeListState
                                                                )
                                                                LazyColumn(
                                                                    state = homeListState,
                                                                    modifier = Modifier.fillMaxSize()
                                                                        .background(Color.Transparent),
                                                                    contentPadding = PaddingValues(
                                                                        bottom = bottomContentPadding
                                                                    )
                                                                ) {

                                                                    if (homeFeedOfflineError) {
                                                                        item(key = "home-network-retry") {
                                                                            Row(
                                                                                modifier = Modifier
                                                                                    .fillMaxWidth()
                                                                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                                                                verticalAlignment = Alignment.CenterVertically,
                                                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                                            ) {
                                                                                Icon(
                                                                                    imageVector = Icons.Rounded.CloudOff,
                                                                                    contentDescription = null,
                                                                                    modifier = Modifier.size(22.dp),
                                                                                    tint = DsmusicTheme.colorScheme.onSurfaceVariant
                                                                                )
                                                                                Column(modifier = Modifier.weight(1f)) {
                                                                                    Text(
                                                                                        "Network not available",
                                                                                        style = DsmusicTheme.typography.titleSmall,
                                                                                        color = DsmusicTheme.colorScheme.onSurface
                                                                                    )
                                                                                    Text(
                                                                                        "Showing the feed already loaded. Connect and refresh to get new content.",
                                                                                        style = DsmusicTheme.typography.bodySmall,
                                                                                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                                                                                    )
                                                                                }
                                                                                DsmusicOutlinedButton(
                                                                                    onClick = { viewModel.refreshHomeFeed() }
                                                                                ) {
                                                                                    Text("Refresh")
                                                                                }
                                                                            }
                                                                        }
                                                                    }

                                                                    item {
                                                                        Box(
                                                                            modifier = Modifier.fillMaxWidth(),
                                                                            contentAlignment = Alignment.TopStart
                                                                        ) {
                                                                            WelcomeGreetingBanner(
                                                                                userName = customProfileName.takeIf { it.isNotBlank() }
                                                                                    ?: userProfile?.name,
                                                                                onListenTogetherClick = if (!isSearchDestination) {
                                                                                    onNavigateToListenTogether
                                                                                } else null,
                                                                                onRecapClick = if (!isSearchDestination) {
                                                                                    { showReplayScreen = true }
                                                                                } else null
                                                                            )
                                                                        }
                                                                    }
                                                                    items(
                                                                        items = visibleHomeSections,
                                                                        key = { section -> "section-${section.title}" }
                                                                    ) { section ->
                                                                        HomeSectionRow(
                                                                            section = section,
                                                                            currentPlayingVideoId = currentPlayingVideoId,
                                                                            isPlaybackActive = isPlaybackActive,
                                                                            onSongTouchDown = { song ->
                                                                                playerSharedViewModel.specPrefetch(
                                                                                    SongItem.createOnlineSong(
                                                                                        song.videoId,
                                                                                        song.title,
                                                                                        song.artist,
                                                                                        "",
                                                                                        song.durationMs,
                                                                                        song.thumbnailUrl,
                                                                                        song.artistId
                                                                                    )
                                                                                )
                                                                            },
                                                                            onSongClick = { song ->
                                                                                val sectionSongs =
                                                                                    section.items.filterIsInstance<SearchItem.Song>()
                                                                                        .map { it.song }
                                                                                val idx =
                                                                                    sectionSongs.indexOfFirst { it.videoId == song.videoId }
                                                                                viewModel.playOnlineSongWithQueue(
                                                                                    song,
                                                                                    sectionSongs,
                                                                                    idx
                                                                                )
                                                                            },
                                                                            onAlbumClick = {
                                                                                viewModel.loadAlbum(
                                                                                    it.browseId
                                                                                )
                                                                            },
                                                                            onArtistClick = {
                                                                                viewModel.loadArtist(
                                                                                    it.browseId,
                                                                                    it.thumbnailUrl
                                                                                )
                                                                            },
                                                                            onPlaylistClick = {
                                                                                viewModel.loadPlaylist(
                                                                                    it.playlistId
                                                                                )
                                                                            },
                                                                            onSongMenuClick = {
                                                                                selectedSongForMenu =
                                                                                    it
                                                                            },
                                                                            onCollectionMenuClick = { item ->
                                                                                 selectedCollectionForMenu = item
                                                                                 selectedCollectionMenuSongs = null
                                                                                 selectedCollectionMenuContinuation = null
                                                                             },
                                                                             onSectionClick = { browseId, params, title ->
                                                                                viewModel.loadSectionDetails(
                                                                                    browseId,
                                                                                    params,
                                                                                    title
                                                                                )
                                                                            }
                                                                        )
                                                                    }
                                                                    item {
                                                                        if (isLoadingMoreHome) {
                                                                            Box(
                                                                                modifier = Modifier.fillMaxWidth()
                                                                                    .padding(24.dp),
                                                                                contentAlignment = Alignment.Center
                                                                            ) {
                                                                                LoadingIndicator(
                                                                                    modifier = Modifier.size(
                                                                                        32.dp
                                                                                    )
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                // Recreate the pagination observer when a page is appended or its loading
                                                                // state changes; otherwise the captured section count becomes stale.
                                                                LaunchedEffect(homeListState, visibleHomeSections.size, isLoadingMoreHome) {
                                                                    snapshotFlow { homeListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                                                                        .collect { lastVisibleIndex ->
                                                                            if (
                                                                                !isLoadingMoreHome &&
                                                                                visibleHomeSections.isNotEmpty() &&
                                                                                lastVisibleIndex != null &&
                                                                                lastVisibleIndex >= visibleHomeSections.size
                                                                            ) {
                                                                                viewModel.loadMoreHomeSections()
                                                                            }
                                                                        }
                                                                }
                                                            }

                                                            isLoadingHome || (isRefreshing && homeSections.isEmpty()) -> {
                                                                Box(
                                                                    modifier = Modifier.fillMaxSize(),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Column(
                                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                                                    ) {
                                                                        LoadingIndicator(modifier = Modifier.size(36.dp))
                                                                        Text(
                                                                            "Loading Explore",
                                                                            style = DsmusicTheme.typography.bodyMedium,
                                                                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                                                                        )
                                                                    }
                                                                }
                                                            }

                                                            homeFeedError != null && homeSections.isEmpty() -> {
                                                                if (homeFeedOfflineError) {
                                                                    com.dsann.music.ui.theme.NoInternetState(
                                                                        modifier = Modifier.fillMaxSize(),
                                                                        onRetry = { viewModel.refreshHomeFeed() }
                                                                    )
                                                                } else {
                                                                    Box(
                                                                        modifier = Modifier.fillMaxSize().padding(24.dp),
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        Column(
                                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                                                        ) {
                                                                            Text(
                                                                                "Couldn't load Explore",
                                                                                style = DsmusicTheme.typography.titleMedium,
                                                                                color = DsmusicTheme.colorScheme.onSurface
                                                                            )
                                                                            Text(
                                                                                homeFeedError,
                                                                                style = DsmusicTheme.typography.bodyMedium,
                                                                                color = DsmusicTheme.colorScheme.onSurfaceVariant,
                                                                                textAlign = TextAlign.Center
                                                                            )
                                                                            DsmusicOutlinedButton(onClick = { viewModel.refreshHomeFeed() }) {
                                                                                Text("Try again")
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }

                                                            else -> {
                                                                Box(
                                                                    modifier = Modifier.fillMaxSize()
                                                                        .verticalScroll(rememberScrollState()),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Column(
                                                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Rounded.MusicNote,
                                                                            contentDescription = null,
                                                                            modifier = Modifier.size(56.dp),
                                                                            tint = DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                                        )
                                                                        Text(
                                                                            "Nothing to show right now",
                                                                            style = DsmusicTheme.typography.titleMedium,
                                                                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                                                                        )
                                                                        DsmusicOutlinedButton(onClick = { viewModel.refreshHomeFeed() }) {
                                                                            Text("Refresh")
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                

                // Error Snackbar
                error?.let { errorMsg ->
                    Snackbar(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        action = { DsmusicTextButton(onClick = { viewModel.clearError() }) { Text("OK") } }
                    ) { Text(errorMsg) }
                }

                // Song 3-Dot Menu Bottom Sheet (Premium Overhaul like YTM)
                // Song 3-Dot Menu Bottom Sheet (Premium Overhaul like YTM)
                selectedReplayLocalSongForMenu?.let { song ->
                    LocalSongActionsBottomSheet(
                        song = song,
                        localQueue = listOf(song),
                        selectedSongIndex = 0,
                        playerSharedViewModel = playerSharedViewModel,
                        onDismissRequest = { selectedReplayLocalSongForMenu = null },
                        onDeleteSong = {},
                        showDeleteAction = false
                    )
                }

                selectedSongForMenu?.let { song ->
                    val isPinned = uiState.pinnedSpeedDialIds.contains(song.videoId)
                    OnlineSongBottomSheet(
                        song = song,
                        isPinned = isPinned,
                        onDismissRequest = { selectedSongForMenu = null },
                        playerSharedViewModel = playerSharedViewModel,
                        exploreViewModel = viewModel,
                        onPlaylistAddClick = { onlineSongItem ->
                            songsToAddPlaylist = listOf(onlineSongItem)
                            showAddToPlaylistDialog = true
                        },
                        onViewCreditsClick = { s ->
                            showCreditsForSong = s
                        }
                    )
                }

                selectedCollectionForMenu?.let { collection ->
                    OnlineCollectionActionsBottomSheet(
                        collection = collection,
                        initialSongs = selectedCollectionMenuSongs,
                        initialContinuation = selectedCollectionMenuContinuation,
                        playerSharedViewModel = playerSharedViewModel,
                        exploreViewModel = viewModel,
                        onDismissRequest = {
                            selectedCollectionForMenu = null
                            selectedCollectionMenuSongs = null
                            selectedCollectionMenuContinuation = null
                        },
                        onAddToPlaylist = { songs ->
                            songsToAddPlaylist = songs
                            showAddToPlaylistDialog = true
                        }
                    )
                }


            }
        }

        if (showAddToPlaylistDialog && !songsToAddPlaylist.isNullOrEmpty()) {
            val videoIds = songsToAddPlaylist.orEmpty().mapNotNull { it.videoId }
                .filter { it.isNotBlank() }.distinct()
            YouTubeMusicPlaylistPickerDialog(
                videoIds = videoIds,
                onPlaylistAdded = { playlistTitle ->
                    showAddToPlaylistDialog = false
                    songsToAddPlaylist = null
                    com.dsann.music.ui.SnackbarController.showMessage(
                        "Added ${videoIds.size} tracks to YouTube Music playlist: $playlistTitle"
                    )
                },
                onDismiss = {
                    showAddToPlaylistDialog = false
                    songsToAddPlaylist = null
                }
            )
        }

        if (showExploreCustomizer) {
            ExploreCustomizerDialog(
                sectionTitles = orderedExploreSectionTitles,
                hiddenSectionTitles = hiddenExploreSections,
                showMoodChips = showExploreMoodChips,
                onMoodChipsChange = ::setExploreMoodChipsVisible,
                onSectionVisibilityChange = ::setExploreSectionVisibility,
                onMoveSection = ::moveExploreSection,
                onReset = ::resetExploreCustomizer,
                onDismiss = { showExploreCustomizer = false }
            )
        }
    }
}


@Composable
private fun ExploreCustomizerDialog(
    sectionTitles: List<String>,
    hiddenSectionTitles: Set<String>,
    showMoodChips: Boolean,
    onMoodChipsChange: (Boolean) -> Unit,
    onSectionVisibilityChange: (String, Boolean) -> Unit,
    onMoveSection: (String, Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        DsmusicSurface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(24.dp),
            color = DsmusicTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Explore customizer",
                            style = DsmusicTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Arrange your home feed",
                            style = DsmusicTheme.typography.bodySmall,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DsmusicTextButton(onClick = onDismiss) { Text("Done") }
                }

                DsmusicSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = DsmusicTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mood categories", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Show or hide the category chips at the top",
                                style = DsmusicTheme.typography.bodySmall,
                                color = DsmusicTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DsmusicSwitchControl(
                            checked = showMoodChips,
                            onCheckedChange = onMoodChipsChange
                        )
                    }
                }

                Text(
                    "HOME SECTIONS",
                    style = DsmusicTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )

                if (sectionTitles.isEmpty()) {
                    Text(
                        "Home sections will appear here after Explore has loaded its feed.",
                        style = DsmusicTheme.typography.bodyMedium,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    sectionTitles.forEachIndexed { index, title ->
                        val visible = title !in hiddenSectionTitles
                        DsmusicSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = DsmusicTheme.colorScheme.surfaceContainerLow
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        title,
                                        style = DsmusicTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2
                                    )
                                    Text(
                                        if (visible) "Shown on Explore" else "Hidden",
                                        style = DsmusicTheme.typography.labelSmall,
                                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DsmusicSwitchControl(
                                    checked = visible,
                                    onCheckedChange = { onSectionVisibilityChange(title, it) }
                                )
                                DsmusicIconButton(
                                    onClick = { onMoveSection(title, -1) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text(
                                        "↑",
                                        style = DsmusicTheme.typography.titleMedium,
                                        color = if (index > 0) DsmusicTheme.colorScheme.onSurface
                                            else DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                    )
                                }
                                DsmusicIconButton(
                                    onClick = { onMoveSection(title, 1) },
                                    enabled = index < sectionTitles.lastIndex,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text(
                                        "↓",
                                        style = DsmusicTheme.typography.titleMedium,
                                        color = if (index < sectionTitles.lastIndex) DsmusicTheme.colorScheme.onSurface
                                            else DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DsmusicTextButton(onClick = onReset) { Text("Reset layout") }
                    DsmusicTextButton(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}

    @Composable
    fun SongCreditsScreen(song: OnlineSong, onBack: () -> Unit) {
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        DsmusicSurface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            color = DsmusicTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DsmusicIconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = DsmusicTheme.colorScheme.onBackground
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Mini Top Header: Circular Avatar + Artist + Info
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AsyncImage(
                                model = rememberExploreImageRequest(song.thumbnailUrl, 96),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                text = song.artist,
                                style = DsmusicTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = DsmusicTheme.colorScheme.onBackground
                            )
                        }
                        Text(
                            text = "Song • ${
                                java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                            }",
                            style = DsmusicTheme.typography.bodySmall,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Placeholder to center the middle content perfectly
                    Spacer(modifier = Modifier.size(48.dp))
                }

                val contributors = remember(song.artist) {
                    song.artist.split(Regex("[,&]| and "), 0)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                }

                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // LEFT: Sticky Cover Art
                        Box(
                            modifier = Modifier.weight(0.45f).fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = rememberExploreImageRequest(song.thumbnailUrl, 512),
                                contentDescription = song.title,
                                modifier = Modifier
                                    .size(240.dp)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // RIGHT: Scrollable Credits
                        Column(
                            modifier = Modifier
                                .weight(0.55f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 24.dp, horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Text(
                                text = song.title,
                                style = DsmusicTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = DsmusicTheme.colorScheme.onBackground
                            )

                            CreditsTextSection(title = "Performed by", content = contributors)

                            CreditsTextSection(
                                title = "Written by",
                                content = listOf(contributors.firstOrNull() ?: song.artist)
                            )

                            CreditsTextSection(
                                title = "Produced by",
                                content = listOf(if (contributors.size > 1) contributors.last() else "Quantune Engine")
                            )

                            CreditsTextSection(
                                title = "Music metadata provided by",
                                content = listOf(
                                    contributors.firstOrNull() ?: "Online Stream Analytics"
                                )
                            )

                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                } else {
                    // Scrollable Credits Body (PORTRAIT)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))

                        // Center Cover Art
                        AsyncImage(
                            model = rememberExploreImageRequest(song.thumbnailUrl, 640),
                            contentDescription = song.title,
                            modifier = Modifier
                                .size(320.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // Large Bold Title
                        Text(
                            text = song.title,
                            style = DsmusicTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 34.sp,
                                letterSpacing = (-0.5).sp
                            ),
                            textAlign = TextAlign.Center,
                            color = DsmusicTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(40.dp))

                        // Left-aligned dynamic section lists
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(28.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            CreditsTextSection(title = "Performed by", content = contributors)

                            CreditsTextSection(
                                title = "Written by",
                                content = listOf(contributors.firstOrNull() ?: song.artist)
                            )

                            CreditsTextSection(
                                title = "Produced by",
                                content = listOf(if (contributors.size > 1) contributors.last() else "Quantune Engine")
                            )

                            CreditsTextSection(
                                title = "Music metadata provided by",
                                content = listOf(
                                    contributors.firstOrNull() ?: "Online Stream Analytics"
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(64.dp))
                    }
                }
            }
        }
    }

    @Composable
    fun CreditsTextSection(title: String, content: List<String>) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                style = DsmusicTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DsmusicTheme.colorScheme.onBackground
            )
            content.forEach { item ->
                Text(
                    text = item,
                    style = DsmusicTheme.typography.bodyLarge,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    
    @Composable
    fun SearchHeader(
        searchQuery: String,
        onQueryChange: (String) -> Unit,
        onSearch: (String) -> Unit,
        isSearchActive: Boolean,
        isSearchDestination: Boolean,
        showSearchEntry: Boolean,
        onLeaveSearchDestination: () -> Unit,
        accountHistory: List<OnlineSong>,
        onAccountHistorySongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
        onSearchActiveChange: (Boolean) -> Unit,
        searchResults: List<SearchItem>,
        searchHistory: List<String>,
        onClearSearchHistory: () -> Unit,
        onRemoveFromSearchHistory: (String) -> Unit,
        onHistoryItemClick: (String) -> Unit,
        suggestions: List<String>,
        suggestionItems: List<SearchItem>,
        onSuggestionClick: (String) -> Unit,
        onSuggestionItemClick: (SearchItem) -> Unit,
        onCollectionMenuClick: (SearchItem) -> Unit,
        onAccountVisibleChange: (Boolean) -> Unit,
        onCustomizeExplore: () -> Unit,
        userProfile: UserProfile?,
        homeMoods: List<String>,
        currentMood: String?,
        onMoodClick: (String?) -> Unit,
        currentFilter: SearchFilter?,
        onFilterClick: (SearchFilter?) -> Unit,
        isLandscape: Boolean,
        onClearSearch: () -> Unit,
        showGenreBrowser: Boolean,
        onGenreBrowserBack: () -> Unit,
        onNavigateToListenTogether: () -> Unit
    ) {
        val recentAccountHistory = remember(accountHistory) { accountHistory.take(10) }
        // Avoid Material3 SearchBar's expanded full-window implementation here. On
        // legacy Android runtimes it can open an empty-looking overlay. Render the
        // active query-entry page in the normal Compose tree instead.
        if (isSearchActive) {
            val searchFocusRequester = remember { FocusRequester() }
            val keyboardController = LocalSoftwareKeyboardController.current
            val submitSearch: () -> Unit = {
                if (searchQuery.isNotBlank()) {
                    keyboardController?.hide()
                    onSearch(searchQuery.trim())
                }
            }
            LaunchedEffect(searchFocusRequester) {
                delay(100.milliseconds)
                searchFocusRequester.requestFocus()
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DsmusicTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 18.dp, top = 14.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    DsmusicPageHeading(
                        title = "Search",
                        subtitle = "Find your next favourite.",
                        kicker = "FIND YOUR SOUND",
                        modifier = Modifier.weight(1f)
                    )
                    DsmusicIconButton(
                        onClick = {
                            keyboardController?.hide()
                            if (isSearchDestination) {
                                onLeaveSearchDestination()
                            } else {
                                onSearchActiveChange(false)
                                onClearSearch()
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(DsmusicTheme.colorScheme.surfaceContainerHigh, CircleShape),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = DsmusicTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .height(62.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(DsmusicTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = DsmusicTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocusRequester)
                            .padding(vertical = 10.dp),
                        singleLine = true,
                        textStyle = DsmusicTheme.typography.bodyLarge.copy(
                            color = DsmusicTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                        decorationBox = { innerTextField ->
                            Box {
                                if (searchQuery.isBlank()) {
                                    BasicText(
                                        text = "Songs, artists, albums, playlists...",
                                        style = DsmusicTheme.typography.bodyLarge.copy(
                                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                                        ),
                                        maxLines = 1
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (searchQuery.isNotBlank()) {
                        DsmusicIconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Clear query",
                                tint = DsmusicTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    DsmusicIconButton(
                        onClick = submitSearch,
                        enabled = searchQuery.isNotBlank(),
                        modifier = Modifier
                            .size(46.dp)
                            .background(
                                if (searchQuery.isNotBlank()) DsmusicTheme.colorScheme.primary
                                else DsmusicTheme.colorScheme.surfaceContainerLow,
                                CircleShape
                            ),
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = if (searchQuery.isNotBlank()) DsmusicTheme.colorScheme.onPrimary
                                else DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    if (searchQuery.isBlank()) {
                        if (searchHistory.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Recent Searches",
                                        style = DsmusicTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                                    )
                                    DsmusicTextButton(onClick = onClearSearchHistory) {
                                        Text("Clear all")
                                    }
                                }
                            }
                            itemsIndexed(
                                searchHistory,
                                key = { index, item -> "active-history:$index:$item" }
                            ) { index, historyItem ->
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onHistoryItemClick(historyItem) }
                                            .padding(horizontal = 18.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(DsmusicTheme.colorScheme.surfaceContainerHigh),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Rounded.History,
                                                contentDescription = null,
                                                tint = DsmusicTheme.colorScheme.primary,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            historyItem,
                                            modifier = Modifier.weight(1f),
                                            style = DsmusicTheme.typography.bodyMedium,
                                            color = DsmusicTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        DsmusicIconButton(
                                            onClick = { onRemoveFromSearchHistory(historyItem) },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = "Remove search",
                                                tint = DsmusicTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                    if (index < searchHistory.lastIndex) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(start = 64.dp, end = 18.dp)
                                                .height(0.7.dp)
                                                .background(DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
                                        )
                                    }
                                }
                            }
                        }

                        if (userProfile != null && recentAccountHistory.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Recently Played (YouTube Music)",
                                    style = DsmusicTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = DsmusicTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            itemsIndexed(
                                recentAccountHistory,
                                key = { idx, _ -> "active-played:$idx" }
                            ) { idx, song ->
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                onSearchActiveChange(false)
                                                onAccountHistorySongClick(song, recentAccountHistory, idx)
                                            }
                                            .padding(horizontal = 18.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = rememberExploreImageRequest(song.thumbnailUrl, 128),
                                            contentDescription = null,
                                            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(9.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                song.title,
                                                style = DsmusicTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = DsmusicTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                song.artist,
                                                style = DsmusicTheme.typography.bodySmall,
                                                color = DsmusicTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            Icons.Rounded.MusicNote,
                                            contentDescription = "Play",
                                            tint = DsmusicTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp).size(18.dp)
                                        )
                                    }
                                    if (idx < recentAccountHistory.lastIndex) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(start = 72.dp, end = 18.dp)
                                                .height(0.7.dp)
                                                .background(DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
                                        )
                                    }
                                }
                            }
                        }

                        if (searchHistory.isEmpty() &&
                            (userProfile == null || accountHistory.isEmpty())
                        ) {
                            item {
                                Text(
                                    text = "Search for a song, artist, album, or playlist.",
                                    style = DsmusicTheme.typography.bodyLarge,
                                    color = DsmusicTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(24.dp)
                                )
                            }
                        }
                    } else if (suggestions.isNotEmpty() || suggestionItems.isNotEmpty()) {
                        item(key = "typed-query") {
                            DsmusicListItem(
                                headlineContent = { Text("Search for \"$searchQuery\"") },
                                leadingContent = { Icon(Icons.Rounded.Search, null) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { onSearch(searchQuery.trim()) }
                            )
                        }
                        items(suggestions, key = { "suggestion:$it" }) { suggestion ->
                            DsmusicListItem(
                                headlineContent = { Text(suggestion) },
                                leadingContent = { Icon(Icons.Rounded.Search, null) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { onSuggestionClick(suggestion) }
                            )
                        }
                        items(
                            suggestionItems,
                            key = { item ->
                                when (item) {
                                    is SearchItem.Song -> "suggestion-song:" + item.song.videoId
                                    is SearchItem.Album -> "suggestion-album:" + item.album.browseId
                                    is SearchItem.Artist -> "suggestion-artist:" + item.artist.browseId
                                    is SearchItem.Playlist -> "suggestion-playlist:" + item.playlist.playlistId
                                }
                            }
                        ) { item ->
                            SearchResultItem(
                                item = item,
                                isCurrentlyPlaying = false,
                                isLoading = false,
                                onCollectionMenuClick = onCollectionMenuClick,
                                onClick = { onSuggestionItemClick(item) }
                            )
                        }
                    } else {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = DsmusicTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "No suggestions yet",
                                    style = DsmusicTheme.typography.titleMedium,
                                    color = DsmusicTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Press Search to find results for \"$searchQuery\".",
                                    style = DsmusicTheme.typography.bodyMedium,
                                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            return
        }

        // Keep the collapsed Explore header simple. We intentionally avoid switching back
        // into Material3's expandable SearchBar after submitting, which could re-expand
        // over the results and make the screen appear to open and immediately close.
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isSearchDestination && showGenreBrowser) {
                    DsmusicIconButton(
                        onClick = onGenreBrowserBack,
                        modifier = Modifier
                            .size(42.dp)
                            .background(DsmusicTheme.colorScheme.surfaceContainerLow, CircleShape),
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to Search",
                            tint = DsmusicTheme.colorScheme.onSurface
                        )
                    }
                    DsmusicPageHeading(
                        title = "Genres",
                        subtitle = "Browse by sound.",
                        kicker = "DISCOVER",
                        modifier = Modifier.weight(1f)
                    )
                } else if (!(isSearchDestination && searchQuery.isNotBlank())) {
                    DsmusicPageHeading(
                        title = if (isSearchDestination) "Search" else "Explore",
                        subtitle = if (isSearchDestination) "Find your next favourite." else null,
                        kicker = if (isSearchDestination) "FIND YOUR SOUND" else "QUANTUNE",
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (!isSearchDestination) {
                    DsmusicIconButton(
                        onClick = onCustomizeExplore,
                        modifier = Modifier
                            .size(42.dp)
                            .background(DsmusicTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(14.dp)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings_customize),
                            contentDescription = "Customize Explore",
                            modifier = Modifier.size(21.dp),
                            tint = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                DsmusicIconButton(
                    onClick = { onAccountVisibleChange(true) },
                    modifier = Modifier
                        .size(42.dp)
                        .background(DsmusicTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (userProfile?.avatarUrl != null) {
                        AsyncImage(
                            model = rememberExploreImageRequest(userProfile.avatarUrl, 96),
                            contentDescription = "Account",
                            modifier = Modifier.size(30.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AccountCircle,
                            contentDescription = "Account",
                            modifier = Modifier.size(25.dp),
                            tint = DsmusicTheme.colorScheme.primary
                        )
                    }
                }
            }
            if (showSearchEntry && !showGenreBrowser) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp)
                        .height(60.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(DsmusicTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onSearchActiveChange(true) }
                        .padding(horizontal = 17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = DsmusicTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        BasicText(
                            text = searchQuery.ifBlank { "Search music" },
                            style = DsmusicTheme.typography.titleSmall.copy(
                                color = DsmusicTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1
                        )
                        if (searchQuery.isBlank()) {
                            BasicText(
                                text = "Songs, artists, albums and playlists",
                                style = DsmusicTheme.typography.bodySmall.copy(
                                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1
                            )
                        }
                    }
                    if (searchQuery.isBlank()) {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = "Open music search",
                            tint = DsmusicTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(21.dp)
                        )
                    } else {
                        DsmusicIconButton(
                            onClick = onClearSearch,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                tint = DsmusicTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (isSearchDestination && currentMood != null && !isSearchActive) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "active-search-mood") {
                        DsmusicFilterChip(
                            selected = true,
                            onClick = { onMoodClick(null) },
                            shape = CircleShape,
                            label = {
                                Text(
                                    "Mood: $currentMood  ×",
                                    style = DsmusicTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        )
                    }
                }
            }

            // Category Chips — fetched from YouTube Music (hidden during active search inputs, without animations)
            if (!isSearchDestination && !isSearchActive && searchQuery.isBlank() && searchResults.isEmpty() && homeMoods.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(homeMoods) { label ->
                        val selected = currentMood == label
                        DsmusicFilterChip(
                            selected = selected,
                            onClick = { onMoodClick(if (selected) null else label) },
                            shape = CircleShape,
                            label = {
                                Text(
                                    label,
                                    style = DsmusicTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }
            }

            // Search Result Filter Chips (hidden during active search typing inputs, without animations)
            if (!isSearchActive && searchQuery.isNotBlank()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters: List<Pair<SearchFilter?, String>> =
                        listOf(null to "Top results") + SearchFilter.values().map { it to it.label }
                    items(filters) { (filter, label) ->
                        val selected = currentFilter == filter
                        DsmusicFilterChip(
                            selected = selected,
                            onClick = { onFilterClick(filter) },
                            shape = CircleShape,
                            label = {
                                Text(
                                    label,
                                    style = DsmusicTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }
            }
        }

    }
