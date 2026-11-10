@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS",
    "TYPE_MISMATCH_BASED_ON_JAVA_ANNOTATIONS"
)

package com.dsann.music.ui.library

import com.dsann.music.ui.rememberTaperedSongDividersEnabled
import com.dsann.music.ui.taperedSongDivider
import com.dsann.music.ui.NowPlayingCoverIndicator
import com.dsann.music.ui.rememberNowPlayingCoverColor

import com.dsann.music.ui.theme.DsmusicPageHeading
import com.dsann.music.ui.LocalRootTabActive
import com.dsann.music.ui.theme.DsmusicCard
import com.dsann.music.ui.theme.DsmusicSurface
import com.dsann.music.ui.theme.DsmusicListItem
import com.dsann.music.ui.theme.DsmusicButton
import com.dsann.music.ui.theme.DsmusicOutlinedButton
import com.dsann.music.ui.theme.DsmusicTextButton
import com.dsann.music.ui.theme.DsmusicIconButton
import com.dsann.music.ui.theme.DsmusicFilterChip
import com.dsann.music.ui.theme.DsmusicSwitchControl
import android.app.Activity
import androidx.activity.compose.BackHandler
import android.content.Context
import android.os.Build
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import com.dsann.music.ui.theme.DropdownMenu
import com.dsann.music.ui.theme.DropdownMenuItem
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import com.dsann.music.ui.theme.AlertDialog
import com.dsann.music.ui.theme.Button
import com.dsann.music.ui.theme.ButtonDefaults
import com.dsann.music.ui.theme.Card
import com.dsann.music.ui.theme.CardDefaults
import com.dsann.music.ui.theme.FilterChip
import com.dsann.music.ui.theme.FilterChipDefaults
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.IconButton
import com.dsann.music.ui.theme.ListItem
import com.dsann.music.ui.theme.ListItemDefaults
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.LocalPageTransitionDurationMillis
import com.dsann.music.ui.theme.ModalBottomSheet
import com.dsann.music.ui.theme.OutlinedButton
import com.dsann.music.ui.theme.OutlinedTextField
import com.dsann.music.ui.theme.OutlinedTextFieldDefaults
import com.dsann.music.ui.theme.Scaffold
import com.dsann.music.ui.theme.SnackbarHost
import com.dsann.music.ui.theme.SnackbarHostState
import com.dsann.music.ui.theme.Surface
import com.dsann.music.ui.theme.Text
import com.dsann.music.ui.theme.TextButton
import com.dsann.music.ui.theme.PullToRefreshBox
import com.dsann.music.ui.theme.rememberPullToRefreshState
import com.dsann.music.ui.theme.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.imageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.dsann.music.MainActivity
import com.dsann.music.R
import com.dsann.music.data.db.PlaylistEntity
import com.dsann.music.data.lyrics.MetadataRepair
import com.dsann.music.data.lyrics.engine.LyricsCacheManager
import com.dsann.music.data.innertube.AccountManager
import com.dsann.music.data.innertube.YouTubeMusic
import com.dsann.music.data.innertube.OnlineAlbum
import com.dsann.music.data.innertube.OnlineArtist
import com.dsann.music.data.innertube.OnlinePlaylist
import com.dsann.music.data.innertube.OnlineSong
import com.dsann.music.model.SongItem
import com.dsann.music.service.AudioPlaybackService
import com.dsann.music.service.CachedMediaMetadata
import com.dsann.music.util.SongDownloader
import com.dsann.music.model.toSongItem
import com.dsann.music.ui.CreateLocalPlaylistDialog
import com.dsann.music.ui.LocalPlaylistPickerDialog
import com.dsann.music.ui.PlaylistDestinationPickerDialog
import com.dsann.music.ui.YouTubeMusicPlaylistPickerDialog
import com.dsann.music.ui.explore.AccountScreen
import com.dsann.music.ui.explore.OnlineSongBottomSheet
import com.dsann.music.ui.explore.SongCreditsScreen
import com.dsann.music.viewmodel.AccountViewModel
import com.dsann.music.viewmodel.ExploreViewModel
import com.dsann.music.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.WeakHashMap

enum class SortOrder {
    A_Z,
    DATE_ADDED,
    ARTIST,
    DURATION
}

/**
 * MediaStore DATE_ADDED is stored in seconds, so several downloads made in the
 * same second can share the same value. Use the file timestamp for those ties;
 * also fall back to it when MediaStore has no date for an older download.
 */
private fun downloadedSongFileModifiedAtMs(song: SongItem): Long {
    val rawPath = (song.path ?: song.data).orEmpty()
    if (rawPath.isBlank()) return 0L
    return runCatching { java.io.File(rawPath).lastModified() }.getOrDefault(0L)
}

private fun downloadedSongAddedAtSeconds(song: SongItem): Long {
    return song.dateAdded.takeIf { it > 0L }
        ?: (downloadedSongFileModifiedAtMs(song) / 1000L)
}


@Composable
fun LibraryScreen(
    viewModel: PlayerSharedViewModel,
    onEditSong: (SongItem) -> Unit = {},
    onNavigateToExplore: () -> Unit = {},
    bottomContentPadding: Dp = 176.dp
) {
    val context = LocalContext.current
    val rootTabActive = LocalRootTabActive.current
    val mainActivity = remember(context) { getActivityFromContext(context) as? MainActivity }
    val fragmentActivity = remember(context) { getActivityFromContext(context) as? androidx.fragment.app.FragmentActivity }
    val fragmentManager = remember(fragmentActivity) { fragmentActivity?.supportFragmentManager }

    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsStateWithLifecycle()
    val playerExpansionFraction by viewModel.playerExpansionFraction.collectAsStateWithLifecycle()
    val isPlayerBackActive = isPlayerExpanded || playerExpansionFraction > 0.01f
    val scope = rememberCoroutineScope()

    var activeTab by rememberSaveable { mutableStateOf("Playlists") }
    LaunchedEffect(activeTab) {
        // Migrate removed destinations and hide Listening Recap on Android 10 and lower.
        if (activeTab == "Device Files" || activeTab == "Recap") {
            activeTab = "Playlists"
        }
    }
    var searchQuery by rememberSaveable { mutableStateOf("")}
    var sortOrder by rememberSaveable { mutableStateOf(SortOrder.A_Z) }
    var playlistsAsList by rememberSaveable { mutableStateOf(false) }
    var albumsAsList by rememberSaveable { mutableStateOf(false) }
    var artistsAsList by rememberSaveable { mutableStateOf(false) }
    val currentCollectionAsList = when (activeTab) {
        "Playlists" -> playlistsAsList
        "Albums" -> albumsAsList
        "Artists" -> artistsAsList
        else -> false
    }
    var isRefreshing by remember { mutableStateOf(false) }

    var showAccountScreen by rememberSaveable { mutableStateOf(false) }
    var showHistoryScreen by rememberSaveable { mutableStateOf(false) }
    var isLibrarySubpageOpen by rememberSaveable { mutableStateOf(false) }
    val hideLibraryChrome = activeTab == "Downloaded songs" || isLibrarySubpageOpen

    val accountViewModel = remember(fragmentActivity) {
        fragmentActivity?.let { ViewModelProvider(it)[AccountViewModel::class.java] }
    }
    val exploreViewModel = remember(fragmentActivity) {
        fragmentActivity?.let { ViewModelProvider(it)[ExploreViewModel::class.java] }
    }

    // Library categories use one attached horizontal track, with a shared
    // 100% → 50% → 100% opacity curve at the same configurable transition speed.
    val pageTransitionDurationMillis = LocalPageTransitionDurationMillis.current
    val libraryTabOrder = remember {
        listOf("Playlists", "Songs", "Albums", "Artists", "History", "Podcasts", "Downloaded songs", "Recap")
    }
    var previousLibraryTab by rememberSaveable { mutableStateOf(activeTab) }
    val libraryTabOpacity = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(activeTab) {
        if (previousLibraryTab != activeTab) {
            previousLibraryTab = activeTab
            val firstHalf = (pageTransitionDurationMillis / 2).coerceAtLeast(1)
            val secondHalf = (pageTransitionDurationMillis - firstHalf).coerceAtLeast(1)
            libraryTabOpacity.animateTo(
                0.5f,
                androidx.compose.animation.core.tween(
                    durationMillis = firstHalf,
                    easing = androidx.compose.animation.core.LinearEasing
                )
            )
            libraryTabOpacity.animateTo(
                1f,
                androidx.compose.animation.core.tween(
                    durationMillis = secondHalf,
                    easing = androidx.compose.animation.core.LinearEasing
                )
            )
        }
    }

    val libraryOverlayVisible =
        (showAccountScreen && accountViewModel != null) || showHistoryScreen
    var previousLibraryOverlayVisible by remember { mutableStateOf(libraryOverlayVisible) }
    val libraryBaseOpacity = remember {
        androidx.compose.animation.core.Animatable(1f)
    }
    LaunchedEffect(libraryOverlayVisible) {
        if (previousLibraryOverlayVisible != libraryOverlayVisible) {
            previousLibraryOverlayVisible = libraryOverlayVisible
            // Keep the underlying page present while the account/history page travels in or out.
            // It dims to 50% at the midpoint but is never faded to a blank surface.
            val firstHalf = (pageTransitionDurationMillis / 2).coerceAtLeast(1)
            val secondHalf = (pageTransitionDurationMillis - firstHalf).coerceAtLeast(1)
            libraryBaseOpacity.animateTo(
                0.5f,
                androidx.compose.animation.core.tween(
                    durationMillis = firstHalf,
                    easing = androidx.compose.animation.core.LinearEasing
                )
            )
            libraryBaseOpacity.animateTo(
                1f,
                androidx.compose.animation.core.tween(
                    durationMillis = secondHalf,
                    easing = androidx.compose.animation.core.LinearEasing
                )
            )
        }
    }

    val history by accountViewModel?.history?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val accountOfflineError by accountViewModel?.offlineError?.collectAsStateWithLifecycle(false)
        ?: remember { mutableStateOf(false) }
    val accountHistoryOfflineError by accountViewModel?.historyOfflineError?.collectAsStateWithLifecycle(false)
        ?: remember { mutableStateOf(false) }
    val currentOnlineSong by exploreViewModel?.currentOnlineSong?.collectAsStateWithLifecycle(null) ?: remember { mutableStateOf(null) }
    val isLoadingStream by exploreViewModel?.isLoadingStream?.collectAsStateWithLifecycle(false) ?: remember { mutableStateOf(false) }

    val localSongs by viewModel.localSongs.collectAsStateWithLifecycle(emptyList())
    val favoriteIds by viewModel.favoriteSongIds.collectAsStateWithLifecycle(emptySet())
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val lyricsCache = remember(context) { LyricsCacheManager(context.applicationContext) }

    var selectedSongForMenu by remember { mutableStateOf<OnlineSong?>(null) }
    var selectedCachedSongForMenu by remember { mutableStateOf<CachedSongEntry?>(null) }
    var selectedDownloadedSongForMenu by remember { mutableStateOf<SongItem?>(null) }
    var songToAddPlaylist by remember { mutableStateOf<SongItem?>(null) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    val localPlaylistsForAdd by viewModel.localPlaylistsFlow.collectAsStateWithLifecycle(emptyList())
    var showLocalPlaylistPicker by remember { mutableStateOf(false) }
    var showYouTubePlaylistPicker by remember { mutableStateOf(false) }
    var showCreateLocalPlaylistDialog by remember { mutableStateOf(false) }
    var showCreditsForSong by remember { mutableStateOf<OnlineSong?>(null)}

    // Library sections are local UI state rather than separate NavController destinations.
    // Consume Back here so it returns to the Library landing page.
    BackHandler(
        // Let the player consume Back first. Keep the current Library subsection intact
        // until the full player has completely collapsed.
        enabled = !isPlayerBackActive && (
            activeTab != "Playlists" || showAccountScreen || showHistoryScreen || showCreditsForSong != null
        )
    ) {
        when {
            showCreditsForSong != null -> showCreditsForSong = null
            showHistoryScreen -> showHistoryScreen = false
            showAccountScreen -> showAccountScreen = false
            activeTab != "Playlists" -> {
                activeTab = "Playlists"
                searchQuery = ""
                mainActivity?.showBottomNavWithAnimation()
            }
        }
    }

    // Migrate old saved Cache/blank destinations to Playlists; Cache now lives inside Playlists.
    LaunchedEffect(Unit) {
        if (activeTab.isBlank() || activeTab == "Cache") activeTab = "Playlists"
    }

    // Scan only while Library is visible. Cached inactive tabs must not keep scanning.
    LaunchedEffect(rootTabActive) {
        if (!rootTabActive) {
            cancelLocalMusicScan(viewModel, owner = "library")
            return@LaunchedEffect
        }
        if (localSongs.isEmpty()) {
            scanLocalFiles(context, viewModel, forceRefresh = false)
        }
    }

    // Observe download completion only while this root tab is active.
    LaunchedEffect(activeTab, rootTabActive) {
        if (!rootTabActive) return@LaunchedEffect
        if (activeTab == "Downloaded songs") {
            SongDownloader.downloadProgress.collect { progress ->
                if (progress.isEmpty()) {
                    scanLocalFiles(context, viewModel)
                }
            }
        }
    }

    // Observe online playback triggers only while Library is the active root tab.
    LaunchedEffect(exploreViewModel, rootTabActive) {
        if (!rootTabActive) return@LaunchedEffect
        exploreViewModel?.playbackTriggerEvent?.collect {
            val song = exploreViewModel.currentOnlineSong.value
            if (song != null) {
                val videoId = song.videoId
                val durationMs = song.durationMs
                val onlineQueue = exploreViewModel.onlineQueue.value
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
                            os.albumName
                        )
                    }
                    viewModel.setSongList(songItems)
                    val currentIdx = exploreViewModel.currentOnlineIndex.value
                    viewModel.playSongAtIndex(currentIdx)
                } else {
                    val songItem = SongItem.createOnlineSong(
                        videoId, song.title, song.artist, "", durationMs, song.thumbnailUrl, song.artistId, song.albumName
                    )
                    viewModel.playSong(songItem)
                }
            }
        }
    }

    val subscriptionChanged by exploreViewModel?.subscriptionChanged?.collectAsStateWithLifecycle(false) ?: remember { mutableStateOf(false) }
    LaunchedEffect(subscriptionChanged) {
        if (subscriptionChanged) {
            accountViewModel?.loadLibrary()
            exploreViewModel?.consumeSubscriptionChanged()
        }
    }

    val openCachedSongMenu: (CachedSongEntry) -> Unit = { item ->
        if (exploreViewModel != null) {
            selectedCachedSongForMenu = item
            selectedSongForMenu = OnlineSong(
                videoId = item.videoId,
                title = item.title,
                artist = item.artist,
                albumName = item.albumName,
                durationMs = item.durationMs,
                thumbnailUrl = android.net.Uri.fromFile(
                    AudioPlaybackService.getCachedMediaArtworkFile(context, item.songId)
                ).toString()
            )
        } else {
            com.dsann.music.ui.SnackbarController.showMessage(
                "Song options are unavailable right now"
            )
        }
    }

    val deleteDownloadedSong: (SongItem) -> Unit = { downloadedSong ->
        scope.launch {
            val deleted = withContext(Dispatchers.IO) {
                val rowsDeleted = runCatching {
                    context.contentResolver.delete(downloadedSong.contentUri, null, null)
                }.getOrDefault(0)
                if (rowsDeleted > 0) {
                    true
                } else {
                    val rawPath = (downloadedSong.path ?: downloadedSong.data).orEmpty()
                    if (rawPath.isBlank()) {
                        false
                    } else {
                        val file = java.io.File(rawPath)
                        !file.exists() || file.delete()
                    }
                }
            }
            scanLocalFiles(context, viewModel)
            com.dsann.music.ui.SnackbarController.showMessage(
                if (deleted) "Deleted: ${downloadedSong.title}"
                else "Could not delete ${downloadedSong.title}. The file may require storage permission."
            )
        }
    }

    val nestedScrollConnection = remember(mainActivity) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (mainActivity != null) {
                    if (consumed.y < -10f) {
                        mainActivity.hideBottomNavWithAnimation()
                    } else if (consumed.y > 10f) {
                        mainActivity.showBottomNavWithAnimation()
                    }
                }
                return super.onPostScroll(consumed, available, source)
            }
        }
    }

    Scaffold(
        // snackbarHost = { SnackbarHost(hostState = snackbarHostState) }, // Replaced by global SnackbarController
        containerColor = DsmusicTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                if (!hideLibraryChrome) {
                // Fixed D.S Launcher-style Library header. The header is static; only the content list scrolls.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DsmusicPageHeading(
                        title = "Library",
                        modifier = Modifier.weight(1f)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        if (activeTab == "Playlists" || activeTab == "Albums" || activeTab == "Artists") {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                                    .background(DsmusicTheme.colorScheme.surfaceContainerLow)
                                    .clickable {
                                        when (activeTab) {
                                            "Playlists" -> playlistsAsList = !playlistsAsList
                                            "Albums" -> albumsAsList = !albumsAsList
                                            "Artists" -> artistsAsList = !artistsAsList
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (currentCollectionAsList) Icons.Rounded.GridView else Icons.Rounded.ViewList,
                                    contentDescription = if (currentCollectionAsList) "Show as grid" else "Show as list",
                                    tint = DsmusicTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }

                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                                .background(DsmusicTheme.colorScheme.surfaceContainerLow)
                                .clickable { activeTab = "History" },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_history),
                                contentDescription = "Listening History",
                                tint = DsmusicTheme.colorScheme.onSurface,
                                modifier = Modifier.size(21.dp)
                            )
                        }

                        val userProfile by accountViewModel?.userProfile?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
                        if (userProfile?.avatarUrl != null) {
                            AsyncImage(
                                model = libraryArtworkRequestBuilder(LocalContext.current, userProfile?.avatarUrl)
                                    .build(),
                                contentDescription = "Account Settings",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showAccountScreen = true },
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                                    .background(DsmusicTheme.colorScheme.surfaceContainerLow)
                                    .clickable { showAccountScreen = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AccountCircle,
                                    contentDescription = "Account Settings",
                                    tint = DsmusicTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(25.dp)
                                )
                            }
                        }
                    }
                }

                // Keep Library navigation visible while browsing.
                val tabs = listOf(
                    "Playlists" to "Playlists",
                    "Songs" to "Songs",
                    "Albums" to "Albums",
                    "Artists" to "Artists",
                    "History" to "History",
                    "Podcasts" to "Podcasts"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { (label, tabKey) ->
                        val selected = activeTab == tabKey
                        DsmusicFilterChip(
                            selected = selected,
                            onClick = {
                                activeTab = tabKey
                                if (tabKey == "History") accountViewModel?.loadLibrary()
                            },
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

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (hideLibraryChrome) Modifier.statusBarsPadding() else Modifier)
                    .nestedScroll(nestedScrollConnection)
                    .clipToBounds()
                    .graphicsLayer {
                        alpha = (libraryBaseOpacity.value * libraryTabOpacity.value)
                            .coerceIn(0.5f, 1f)
                    }
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = activeTab,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val oldIndex = libraryTabOrder.indexOf(initialState).coerceAtLeast(0)
                        val newIndex = libraryTabOrder.indexOf(targetState).takeIf { it >= 0 } ?: oldIndex + 1
                        val direction = if (newIndex >= oldIndex) 1 else -1
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
                    label = "Library Attached Page Slide"
                ) { tab ->
                when (tab) {
                    "Playlists" -> PlaylistsTabContent(
                        nestedScrollConnection,
                        viewModel,
                        playlistsAsList,
                        isPlayerBackActive = isPlayerBackActive,
                        onCachedSongLongClick = openCachedSongMenu,
                        onOpenDownloads = {
                            activeTab = "Downloaded songs"
                            searchQuery = ""
                        },
                        onLibrarySubpageChanged = { isLibrarySubpageOpen = it },
                        bottomContentPadding = bottomContentPadding
                    )
                    "Podcasts" -> PodcastsTabContent(nestedScrollConnection)
                    "Songs" -> SongsTabContent(nestedScrollConnection, viewModel)
                    "Albums" -> AlbumsTabContent(nestedScrollConnection, albumsAsList)
                    "Artists" -> ArtistsTabContent(nestedScrollConnection, artistsAsList)
                    "Recap" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        RecapTabContent(nestedScrollConnection, viewModel, onNavigateToExplore)
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Recap is not supported on your version of Android.",
                                color = DsmusicTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    "Downloaded songs" -> {
                        // Scan Local Files helper
                        val isLandscapeLayout = LocalConfiguration.current.orientation ==
                            android.content.res.Configuration.ORIENTATION_LANDSCAPE
                        val onRefreshAction = {
                            scope.launch {
                                isRefreshing = true
                                scanLocalFiles(context, viewModel)
                                isRefreshing = false
                            }
                        }
                        var isSelectingDownloads by rememberSaveable { mutableStateOf(false) }
                        var selectedDownloadIds by rememberSaveable { mutableStateOf(setOf<Long>()) }
                        var downloadSelectionMenuExpanded by remember { mutableStateOf(false) }
                        var downloadOptionsExpanded by remember { mutableStateOf(false) }
                        var downloadSortMenuExpanded by remember { mutableStateOf(false) }
                        var downloadSortAscending by rememberSaveable { mutableStateOf(true) }
                        var isDownloadSearchVisible by rememberSaveable { mutableStateOf(false) }
                        var downloadSearchQuery by rememberSaveable { mutableStateOf("") }
                        // Share one scroll state across portrait and landscape layouts.
                        val downloadListState = rememberLazyListState()

                        val pullToRefreshState = rememberPullToRefreshState()
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = { onRefreshAction() },
                            state = pullToRefreshState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Spacer(modifier = Modifier.height(6.dp))

                                // Keep this folder scoped to files saved by Quantune's downloader.
                                // Build the download list away from the UI thread. Large libraries
                                // used to filter, sort and group synchronously during composition,
                                // which stalled both first display and every search/sort change.
                                val sourceSongs by produceState(
                                    initialValue = emptyList<SongItem>(),
                                    localSongs,
                                    activeTab
                                ) {
                                    value = withContext(Dispatchers.Default) {
                                        localSongs.filter(::isDsmusicDownloadedSong)
                                    }
                                }

                                val filteredSongs by produceState(
                                    initialValue = emptyList<SongItem>(),
                                    sourceSongs,
                                    downloadSearchQuery
                                ) {
                                    if (downloadSearchQuery.isNotBlank()) delay(120L)
                                    val query = downloadSearchQuery.trim()
                                    value = withContext(Dispatchers.Default) {
                                        if (query.isEmpty()) sourceSongs else sourceSongs.filter {
                                            it.title.contains(query, ignoreCase = true) ||
                                                it.artist.contains(query, ignoreCase = true)
                                        }
                                    }
                                }

                                // Downloads are a single playlist, not an alphabetically sectioned file browser.
                                // Sorting changes track order while keeping all rows in one continuous queue.
                                val selectedDownloadSongs = sourceSongs.filter { it.id in selectedDownloadIds }
                                val downloadSelectionControls: @Composable (Modifier) -> Unit = { controlModifier ->
                                    DownloadsSelectionControls(
                                        isSelecting = isSelectingDownloads,
                                        selectedCount = selectedDownloadIds.size,
                                        onStartSelection = { isSelectingDownloads = true },
                                        onSelectAll = { selectedDownloadIds = filteredSongs.map { it.id }.toSet() },
                                        onOpenMenu = { downloadSelectionMenuExpanded = true },
                                        menuExpanded = downloadSelectionMenuExpanded,
                                        onDismissMenu = { downloadSelectionMenuExpanded = false },
                                        onPlayNext = {
                                            selectedDownloadSongs.asReversed().forEach(viewModel::addToQueueNext)
                                            downloadSelectionMenuExpanded = false
                                            selectedDownloadIds = emptySet()
                                            isSelectingDownloads = false
                                            com.dsann.music.ui.SnackbarController.showMessage(
                                                "Added " + selectedDownloadSongs.size + " songs to play next"
                                            )
                                        },
                                        onAddToQueue = {
                                            selectedDownloadSongs.forEach(viewModel::addToQueue)
                                            downloadSelectionMenuExpanded = false
                                            selectedDownloadIds = emptySet()
                                            isSelectingDownloads = false
                                            com.dsann.music.ui.SnackbarController.showMessage(
                                                "Added " + selectedDownloadSongs.size + " songs to queue"
                                            )
                                        },
                                        onDeleteSelected = {
                                            downloadSelectionMenuExpanded = false
                                            scope.launch {
                                                val deletedCount = withContext(Dispatchers.IO) {
                                                    selectedDownloadSongs.count { selectedSong ->
                                                        val rowsDeleted = runCatching {
                                                            context.contentResolver.delete(selectedSong.contentUri, null, null)
                                                        }.getOrDefault(0)
                                                        if (rowsDeleted > 0) {
                                                            true
                                                        } else {
                                                            val rawPath = (selectedSong.path ?: selectedSong.data).orEmpty()
                                                            if (rawPath.isBlank()) false else {
                                                                val file = java.io.File(rawPath)
                                                                !file.exists() || file.delete()
                                                            }
                                                        }
                                                    }
                                                }
                                                scanLocalFiles(context, viewModel)
                                                selectedDownloadIds = emptySet()
                                                isSelectingDownloads = false
                                                com.dsann.music.ui.SnackbarController.showMessage(
                                                    "Deleted " + deletedCount + " of " +
                                                        selectedDownloadSongs.size + " selected songs"
                                                )
                                            }
                                        },
                                        onClearSelection = {
                                            downloadSelectionMenuExpanded = false
                                            selectedDownloadIds = emptySet()
                                            isSelectingDownloads = false
                                        },
                                        onDone = {
                                            selectedDownloadIds = emptySet()
                                            isSelectingDownloads = false
                                            downloadSelectionMenuExpanded = false
                                        },
                                        modifier = controlModifier
                                    )
                                }

                                val sortedAndGrouped by produceState<List<Any>>(
                                    initialValue = emptyList(),
                                    filteredSongs,
                                    sortOrder,
                                    downloadSortAscending
                                ) {
                                    value = withContext(Dispatchers.Default) {
                                        val ascendingSorted = when (sortOrder) {
                                            SortOrder.A_Z -> filteredSongs.sortedBy { it.title.lowercase() }
                                            SortOrder.DATE_ADDED -> filteredSongs.sortedWith(
                                                compareBy<SongItem> { downloadedSongAddedAtSeconds(it) }
                                                    .thenBy { downloadedSongFileModifiedAtMs(it) }
                                                    .thenBy { it.id }
                                            )
                                            SortOrder.ARTIST -> filteredSongs.sortedBy { it.artist.lowercase() }
                                            SortOrder.DURATION -> filteredSongs.sortedBy { it.duration }
                                        }
                                        val sorted = if (downloadSortAscending) {
                                            ascendingSorted
                                        } else {
                                            ascendingSorted.asReversed()
                                        }
                                        sorted.map { it as Any }
                                    }
                                }

                                val downloadPlaylistHero: @Composable () -> Unit = {
                                    val headerSong = filteredSongs.firstOrNull()
                                    if (headerSong != null) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                AsyncImage(
                                                    model = libraryArtworkRequestBuilder(
                                                        context,
                                                        headerSong.getAlbumArtUri() ?: R.drawable.ic_music_note
                                                    ).crossfade(false).build(),
                                                    contentDescription = "Downloads playlist artwork",
                                                    modifier = Modifier
                                                        .size(if (isLandscapeLayout) 180.dp else 216.dp)
                                                        .clip(RoundedCornerShape(18.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Text(
                                                    "Downloads",
                                                    style = DsmusicTheme.typography.headlineMedium,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Text(
                                                    "Quantune · ${sourceSongs.size} songs",
                                                    style = DsmusicTheme.typography.bodyMedium,
                                                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    "Saved on this device",
                                                    style = DsmusicTheme.typography.labelMedium,
                                                    color = DsmusicTheme.colorScheme.primary
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                DsmusicButton(
                                                    onClick = {
                                                        val playlist = sortedAndGrouped.filterIsInstance<SongItem>()
                                                        if (playlist.isNotEmpty()) {
                                                            viewModel.setShuffleEnabled(false)
                                                            viewModel.setSongList(playlist)
                                                            viewModel.playSongAtIndex(0)
                                                        }
                                                    },
                                                    enabled = sortedAndGrouped.isNotEmpty(),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                                    Spacer(Modifier.width(8.dp))
                                                    Text("Play all")
                                                }
                                                DsmusicOutlinedButton(
                                                    onClick = {
                                                        val playlist = sortedAndGrouped.filterIsInstance<SongItem>().shuffled()
                                                        if (playlist.isNotEmpty()) {
                                                            viewModel.setShuffleEnabled(true)
                                                            viewModel.setSongList(playlist)
                                                            viewModel.playSongAtIndex(0)
                                                        }
                                                    },
                                                    enabled = sortedAndGrouped.isNotEmpty(),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Rounded.Shuffle, contentDescription = null)
                                                    Spacer(Modifier.width(8.dp))
                                                    Text("Shuffle")
                                                }
                                            }
                                            com.dsann.music.ui.theme.HorizontalDivider()
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (filteredSongs.isEmpty()) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Downloads",
                                                style = DsmusicTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "0 songs",
                                                style = DsmusicTheme.typography.bodySmall,
                                                color = DsmusicTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                    Box {
                                        DsmusicIconButton(onClick = { downloadOptionsExpanded = true }) {
                                            Icon(Icons.Rounded.MoreVert, contentDescription = "Download options")
                                        }
                                        DropdownMenu(
                                            expanded = downloadOptionsExpanded,
                                            onDismissRequest = { downloadOptionsExpanded = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Sort by") },
                                                onClick = {
                                                    downloadOptionsExpanded = false
                                                    downloadSortMenuExpanded = true
                                                },
                                                trailingIcon = {
                                                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Search") },
                                                onClick = {
                                                    isDownloadSearchVisible = !isDownloadSearchVisible
                                                    if (!isDownloadSearchVisible) downloadSearchQuery = ""
                                                    downloadOptionsExpanded = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Select") },
                                                onClick = {
                                                    isSelectingDownloads = true
                                                    downloadOptionsExpanded = false
                                                }
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = downloadSortMenuExpanded,
                                            onDismissRequest = { downloadSortMenuExpanded = false }
                                        ) {
                                            Text(
                                                "SORT BY",
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
                                                style = DsmusicTheme.typography.labelSmall,
                                                color = DsmusicTheme.colorScheme.onSurfaceVariant
                                            )
                                            SortOrder.entries.forEach { order ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            when (order) {
                                                                SortOrder.A_Z -> "A–Z"
                                                                SortOrder.DATE_ADDED -> "Date added"
                                                                SortOrder.ARTIST -> "Artist"
                                                                SortOrder.DURATION -> "Duration"
                                                            }
                                                        )
                                                    },
                                                    onClick = { sortOrder = order },
                                                    trailingIcon = {
                                                        if (sortOrder == order) Icon(
                                                            Icons.Rounded.Check,
                                                            contentDescription = "Selected sort",
                                                            tint = DsmusicTheme.colorScheme.primary
                                                        )
                                                    }
                                                )
                                            }
                                            com.dsann.music.ui.theme.HorizontalDivider()
                                            Text(
                                                "ORDER",
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
                                                style = DsmusicTheme.typography.labelSmall,
                                                color = DsmusicTheme.colorScheme.onSurfaceVariant
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Ascending") },
                                                onClick = {
                                                    downloadSortAscending = true
                                                },
                                                trailingIcon = {
                                                    if (downloadSortAscending) Icon(
                                                        Icons.Rounded.Check,
                                                        contentDescription = "Ascending selected",
                                                        tint = DsmusicTheme.colorScheme.primary
                                                    )
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Descending") },
                                                onClick = {
                                                    downloadSortAscending = false
                                                },
                                                trailingIcon = {
                                                    if (!downloadSortAscending) Icon(
                                                        Icons.Rounded.Check,
                                                        contentDescription = "Descending selected",
                                                        tint = DsmusicTheme.colorScheme.primary
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }

                                if (isDownloadSearchVisible) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                            .clip(CircleShape)
                                            .background(DsmusicTheme.colorScheme.surfaceContainerLow)
                                            .border(1.dp, DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.42f), CircleShape)
                                            .padding(horizontal = 14.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.Search,
                                            contentDescription = "Search downloads",
                                            tint = DsmusicTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                            if (downloadSearchQuery.isEmpty()) {
                                                Text(
                                                    "Search downloads…",
                                                    style = DsmusicTheme.typography.bodyMedium,
                                                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            BasicTextField(
                                                value = downloadSearchQuery,
                                                onValueChange = { downloadSearchQuery = it },
                                                singleLine = true,
                                                textStyle = DsmusicTheme.typography.bodyMedium.copy(
                                                    color = DsmusicTheme.colorScheme.onSurface
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        DsmusicIconButton(onClick = {
                                            downloadSearchQuery = ""
                                            isDownloadSearchVisible = false
                                        }) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Close search")
                                        }
                                    }
                                }

                                if (sourceSongs.isNotEmpty() && isSelectingDownloads) {
                                    downloadSelectionControls(
                                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }

                                if (sortedAndGrouped.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            when {
                                                downloadSearchQuery.isNotEmpty() -> "No matching songs found"
                                                activeTab == "Downloaded songs" -> "No downloaded songs yet. Downloaded tracks will appear here."
                                                else -> "Scan device files to load songs"
                                            },
                                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    // Downloads is a compact continuous track list in both orientations. Use a LazyColumn
                                    // rather than a one-column LazyVerticalGrid so vertical drags
                                    // that begin on a song row stay with the scrolling list.
                                    val downloadListContent: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {

                                        item(key = "download-playlist-header") {
    if (!isLandscapeLayout) downloadPlaylistHero()
}
itemsIndexed(
                                            items = sortedAndGrouped,
                                            key = { _, item ->
                                                if (item is SongItem) "downloaded-song:${item.id}"
                                                else "downloaded-section:$item"
                                            },
                                            contentType = { _, item ->
                                                if (item is SongItem) "downloaded-song" else "section-header"
                                            }
                                        ) { index, item ->
                                            if (item is String) {
                                                Text(
                                                    text = item,
                                                    style = DsmusicTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = DsmusicTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 20.dp, vertical = 10.dp)
                                                )
                                            } else if (item is SongItem) {
                                                val isPlaying = currentSong?.id == item.id
                                                val isFav = favoriteIds.contains(item.id)
                                                val localIndex = index
                                                val count = sortedAndGrouped.size.coerceAtLeast(1)
                                                SongListItem(
                                                    song = item,
                                                    isPlaying = isPlaying,
                                                    isFavorite = isFav,
                                                    localIndex = localIndex,
                                                    localCount = count,
                                                    selectionMode = isSelectingDownloads,
                                                    isSelected = item.id in selectedDownloadIds,
                                                    onClick = {
                                                        if (isSelectingDownloads) {
                                                            selectedDownloadIds = if (item.id in selectedDownloadIds) {
                                                                selectedDownloadIds - item.id
                                                            } else {
                                                                selectedDownloadIds + item.id
                                                            }
                                                        } else {
                                                            // Keep the Downloads tab as a playable queue and start the
                                                            // tapped track immediately. Long-press still opens its options.
                                                            val downloadQueue =
                                                                sortedAndGrouped.filterIsInstance<SongItem>()
                                                            val queueIndex = downloadQueue.indexOfFirst {
                                                                it.id == item.id
                                                            }
                                                            if (queueIndex >= 0) {
                                                                viewModel.setSongList(downloadQueue)
                                                                viewModel.playSongAtIndex(queueIndex)
                                                            }
                                                        }
                                                    },
                                                    onLongClick = {
                                                        if (isSelectingDownloads) {
                                                            selectedDownloadIds = if (item.id in selectedDownloadIds) {
                                                                selectedDownloadIds - item.id
                                                            } else {
                                                                selectedDownloadIds + item.id
                                                            }
                                                        } else {
                                                            viewModel.setSongList(
                                                                sortedAndGrouped.filterIsInstance<SongItem>()
                                                            )
                                                            selectedDownloadedSongForMenu = item
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    if (isLandscapeLayout) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .weight(0.42f)
                                                    .fillMaxHeight()
                                                    .verticalScroll(rememberScrollState())
                                                    .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = bottomContentPadding),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                downloadPlaylistHero()
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .width(1.dp)
                                                    .background(DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            )
                                            LazyColumn(
                                                state = downloadListState,
                                                userScrollEnabled = true,
                                                modifier = Modifier.weight(0.58f).fillMaxHeight(),
                                                contentPadding = PaddingValues(
                                                    start = 8.dp,
                                                    top = 12.dp,
                                                    end = 8.dp,
                                                    bottom = bottomContentPadding
                                                ),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                downloadListContent()
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            state = downloadListState,
                                            userScrollEnabled = true,
                                            modifier = Modifier.fillMaxSize().weight(1f),
                                            contentPadding = PaddingValues(
                                                start = 0.dp,
                                                top = 0.dp,
                                                end = 0.dp,
                                                bottom = bottomContentPadding
                                            ),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            downloadListContent()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "History" -> HistoryScreen(
                        history = history,
                        currentOnlineSong = currentOnlineSong,
                        isLoadingStream = isLoadingStream,
                        isRefreshing = accountViewModel?.isLoading?.collectAsStateWithLifecycle()?.value == true,
                        isOfflineError = accountHistoryOfflineError,
                        onRefresh = { accountViewModel?.loadLibrary() },
                        onBack = { activeTab = "Playlists" },
                        onSongClick = { song, queue, index ->
                            exploreViewModel?.playOnlineSongWithQueue(song, queue, index)
                        },
                        onSongMenuClick = { song -> selectedSongForMenu = song },
                        bottomContentPadding = bottomContentPadding,
                        showTopBar = false
                    )
                    "" -> PlaylistsTabContent(
                        nestedScrollConnection,
                        viewModel,
                        playlistsAsList,
                        isPlayerBackActive = isPlayerBackActive,
                        onCachedSongLongClick = openCachedSongMenu,
                        onOpenDownloads = {
                            activeTab = "Downloaded songs"
                            searchQuery = ""
                        },
                        onLibrarySubpageChanged = { isLibrarySubpageOpen = it },
                        bottomContentPadding = bottomContentPadding
                    )
                }
                }
            }
        }

        // Overlay AccountScreen
        AnimatedVisibility(
            visible = showAccountScreen && accountViewModel != null,
            enter = slideInHorizontally(
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = pageTransitionDurationMillis,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            ) { it },
            exit = slideOutHorizontally(
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = pageTransitionDurationMillis,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            ) { it },
            modifier = Modifier.fillMaxSize()
        ) {
            if (accountViewModel != null) {
                AccountScreen(
                    viewModel = accountViewModel,
                    onBack = { showAccountScreen = false },
                    onSongClick = { song, queue, index ->
                        exploreViewModel?.playOnlineSongWithQueue(song, queue, index)
                    },
                    onNavigateToSignIn = {
                        showAccountScreen = false
                        mainActivity?.navigateToGoogleSignIn()
                    }
                )
            }
        }

        // Overlay HistoryScreen
        AnimatedVisibility(
            visible = showHistoryScreen,
            enter = slideInHorizontally(
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = pageTransitionDurationMillis,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            ) { it },
            exit = slideOutHorizontally(
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = pageTransitionDurationMillis,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            ) { it },
            modifier = Modifier.fillMaxSize()
        ) {
            HistoryScreen(
                history = history,
                currentOnlineSong = currentOnlineSong,
                isLoadingStream = isLoadingStream,
                isRefreshing = accountViewModel?.isLoading?.collectAsStateWithLifecycle()?.value == true,
                        isOfflineError = accountHistoryOfflineError,
                onRefresh = { accountViewModel?.loadLibrary() },
                onBack = { showHistoryScreen = false },
                onSongClick = { song, queue, index ->
                    exploreViewModel?.playOnlineSongWithQueue(song, queue, index)
                },
                onSongMenuClick = { song ->
                    selectedSongForMenu = song
                },
                bottomContentPadding = bottomContentPadding
            )
        }

        // Song 3-Dot Menu Bottom Sheet
        selectedSongForMenu?.let { song ->
            val isPinned = exploreViewModel?.uiState?.collectAsState()?.value?.pinnedSpeedDialIds?.contains(song.videoId) == true
            if (exploreViewModel != null) {
                OnlineSongBottomSheet(
                    song = song,
                    isPinned = isPinned,
                    onDismissRequest = {
                        selectedSongForMenu = null
                        selectedCachedSongForMenu = null
                    },
                    playerSharedViewModel = viewModel,
                    exploreViewModel = exploreViewModel,
                    onPlaylistAddClick = { onlineSongItem ->
                        songToAddPlaylist = onlineSongItem
                        showAddToPlaylistDialog = true
                    },
                    onViewCreditsClick = { s ->
                        showCreditsForSong = s
                    },
                    onRemoveFromCache = selectedCachedSongForMenu?.let { cachedItem ->
                        {
                            val removedResources = AudioPlaybackService.removeMediaCacheForSong(
                                context = context.applicationContext,
                                songId = cachedItem.songId,
                                videoId = cachedItem.videoId,
                                cacheUri = cachedItem.cachedUri
                            )
                            val track = MetadataRepair().repair(
                                cachedItem.title,
                                cachedItem.artist,
                                null,
                                cachedItem.durationMs,
                                null,
                                null
                            )
                            lyricsCache.evict(track)
                            com.dsann.music.ui.SnackbarController.showMessage(
                                if (removedResources > 0) {
                                    "Removed from cache: ${cachedItem.title}"
                                } else {
                                    "Cache entry not found: ${cachedItem.title}"
                                }
                            )
                        }
                    },
                    onNavigateToExplore = { onNavigateToExplore() }
                )
            }
        }

        selectedDownloadedSongForMenu?.let { downloadedSong ->
            if (exploreViewModel != null) {
                val downloadedMenuSong = OnlineSong(
                    videoId = downloadedSong.videoId?.takeIf { it.isNotBlank() }
                        ?: "local-track-${downloadedSong.id}",
                    title = downloadedSong.title,
                    artist = downloadedSong.artist,
                    albumName = downloadedSong.albumName,
                    albumId = downloadedSong.albumId.takeIf { it > 0L }?.toString(),
                    artistId = downloadedSong.artistId,
                    durationMs = downloadedSong.duration,
                    thumbnailUrl = downloadedSong.getAlbumArtUri()?.toString(),
                    artistRefs = downloadedSong.artistRefs.toList()
                )
                OnlineSongBottomSheet(
                    song = downloadedMenuSong,
                    isPinned = false,
                    downloadedSong = downloadedSong,
                    onDeleteDownloaded = { deleteDownloadedSong(downloadedSong) },
                    onDismissRequest = { selectedDownloadedSongForMenu = null },
                    playerSharedViewModel = viewModel,
                    exploreViewModel = exploreViewModel,
                    onPlaylistAddClick = { selectedSong ->
                        songToAddPlaylist = selectedDownloadedSongForMenu ?: selectedSong
                        showAddToPlaylistDialog = true
                    },
                    onViewCreditsClick = { creditSong -> showCreditsForSong = creditSong },
                    onNavigateToExplore = { onNavigateToExplore() }
                )
            } else {
                LaunchedEffect(downloadedSong.id) {
                    com.dsann.music.ui.SnackbarController.showMessage("Song options are unavailable right now")
                    selectedDownloadedSongForMenu = null
                }
            }
        }

        val playlistSong = songToAddPlaylist
        val playlistVideoId = playlistSong?.videoId?.takeIf {
            it.isNotBlank() && !it.startsWith("local-track-", ignoreCase = true)
        }

        if (showAddToPlaylistDialog && playlistSong != null) {
            PlaylistDestinationPickerDialog(
                showYouTubeMusicOption = playlistVideoId != null,
                onQuantunePlaylistSelected = {
                    showAddToPlaylistDialog = false
                    showLocalPlaylistPicker = true
                },
                onYouTubeMusicSelected = {
                    showAddToPlaylistDialog = false
                    showYouTubePlaylistPicker = true
                },
                onDismiss = {
                    showAddToPlaylistDialog = false
                    songToAddPlaylist = null
                }
            )
        }

        if (showLocalPlaylistPicker && playlistSong != null) {
            LocalPlaylistPickerDialog(
                playlists = localPlaylistsForAdd,
                onCreateNew = {
                    showLocalPlaylistPicker = false
                    showCreateLocalPlaylistDialog = true
                },
                onPlaylistSelected = { playlist ->
                    viewModel.addSongToLocalPlaylist(playlist.id, playlistSong)
                    showLocalPlaylistPicker = false
                    songToAddPlaylist = null
                    com.dsann.music.ui.SnackbarController.showMessage(
                        "Added to app playlist: ${playlist.title}"
                    )
                },
                onDismiss = {
                    showLocalPlaylistPicker = false
                    songToAddPlaylist = null
                }
            )
        }

        if (showYouTubePlaylistPicker && playlistSong != null && playlistVideoId != null) {
            YouTubeMusicPlaylistPickerDialog(
                videoIds = listOf(playlistVideoId),
                onPlaylistAdded = { playlistTitle ->
                    showYouTubePlaylistPicker = false
                    songToAddPlaylist = null
                    com.dsann.music.ui.SnackbarController.showMessage(
                        "Added to YouTube Music playlist: $playlistTitle"
                    )
                },
                onDismiss = {
                    showYouTubePlaylistPicker = false
                    songToAddPlaylist = null
                }
            )
        }

        if (showCreateLocalPlaylistDialog) {
            CreateLocalPlaylistDialog(
                onConfirm = { name ->
                    viewModel.createLocalPlaylist(name)
                    showCreateLocalPlaylistDialog = false
                    showLocalPlaylistPicker = true
                },
                onDismiss = {
                    showCreateLocalPlaylistDialog = false
                    showLocalPlaylistPicker = true
                }
            )
        }

        // Overlay SongCreditsScreen
        AnimatedVisibility(
            visible = showCreditsForSong != null,
            enter = slideInHorizontally(
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = pageTransitionDurationMillis,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                ),
                initialOffsetX = { it }
            ),
            exit = slideOutHorizontally(
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = pageTransitionDurationMillis,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                ),
                targetOffsetX = { it }
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            showCreditsForSong?.let { song ->
                SongCreditsScreen(
                    song = song,
                    onBack = { showCreditsForSong = null }
                )
            }
        }
    }
}
}

private fun libraryArtworkRequestBuilder(
    context: Context,
    data: Any?
): ImageRequest.Builder = ImageRequest.Builder(context)
    .data(data)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListItem(
    song: SongItem,
    isPlaying: Boolean,
    isFavorite: Boolean,
    localIndex: Int,
    localCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    artworkRequestSizePx: Int = 128,
    selectionMode: Boolean = false,
    isSelected: Boolean = false
) {
    val context = LocalContext.current
    val artModel = remember(song.id, song.thumbnailUrl, song.albumId, song.path) { song.getAlbumArtUri() ?: R.drawable.ic_music_note }
    val playingCoverColor = rememberNowPlayingCoverColor(artModel, isPlaying, artworkRequestSizePx.coerceAtMost(96))
    // The lazy list reuses rows while playback/favourite state changes. Keep the
    // immutable Coil request stable instead of rebuilding it during each row recompose.
    val artworkRequest = remember(context, artModel, artworkRequestSizePx) {
        ImageRequest.Builder(context)
            .data(artModel)
            .size(artworkRequestSizePx, artworkRequestSizePx)
            .crossfade(false)
            .error(R.drawable.ic_music_note)
            .placeholder(R.drawable.ic_music_note)
            .fallback(R.drawable.ic_music_note)
            .build()
    }
    val shape = DsmusicTheme.shapes.medium
    val accent = DsmusicTheme.colorScheme.primary
    val titleColor = DsmusicTheme.colorScheme.onSurface
    val secondaryColor = DsmusicTheme.colorScheme.onSurfaceVariant
    val taperedDividersEnabled = rememberTaperedSongDividersEnabled()
    val showTaperedDivider = taperedDividersEnabled &&
        !isSelected && !isPlaying && localIndex < localCount - 1

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clip(shape)
            .background(
                when {
                    isSelected -> DsmusicTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)
                    isPlaying -> accent.copy(alpha = 0.13f)
                    else -> DsmusicTheme.colorScheme.surfaceContainerLow
                }
            )
            .then(
                when {
                    isSelected -> Modifier.border(1.dp, DsmusicTheme.colorScheme.primary.copy(alpha = 0.65f), shape)
                    isPlaying -> Modifier.border(1.dp, accent.copy(alpha = 0.48f), shape)
                    else -> Modifier
                }
            )
            .taperedSongDivider(
                enabled = showTaperedDivider,
                color = DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) DsmusicTheme.colorScheme.primary else Color.Transparent)
                    .border(1.dp, if (isSelected) DsmusicTheme.colorScheme.primary else DsmusicTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = DsmusicTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Box(modifier = Modifier.size(48.dp)) {
            AsyncImage(
                model = artworkRequest,
                contentDescription = "Album Art",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            if (isPlaying) {
                NowPlayingCoverIndicator(
                    isPlaying = true,
                    coverColor = playingCoverColor,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            BasicText(
                text = song.title,
                style = DsmusicTheme.typography.titleSmall.copy(
                    color = titleColor,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium
                ),
                maxLines = 1
            )
            BasicText(
                text = song.artist,
                style = DsmusicTheme.typography.bodySmall.copy(
                    color = secondaryColor
                ),
                maxLines = 1
            )
        }

        if (isFavorite) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = "Favorite",
                tint = accent,
                modifier = Modifier.padding(end = 7.dp).size(17.dp)
            )
        }

    }
}

@Composable
private fun UnifiedLibraryContent(
    nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    onNavigateToTab: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = remember { getActivityFromContext(context) as? androidx.fragment.app.FragmentActivity }
    val accountViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[AccountViewModel::class.java] }
    }
    val exploreViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[ExploreViewModel::class.java] }
    }

    val onlinePlaylists by accountViewModel?.playlists?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val onlineAlbums by accountViewModel?.albums?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val onlineArtists by accountViewModel?.artists?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val onlinePodcasts by accountViewModel?.podcasts?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val onlineSongs by accountViewModel?.songs?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }
    // Keep category callbacks current without rebuilding all library tiles on parent recomposition.
    val navigateToTab = rememberUpdatedState(onNavigateToTab)

    LaunchedEffect(Unit) {
        accountViewModel?.refreshAll()
    }

    val unifiedItems = remember(onlinePlaylists, onlineAlbums, onlineArtists, onlinePodcasts, onlineSongs) {
        val list = mutableListOf<UnifiedLibraryItem>()

        // Keep the key library actions first in a stable tile order.
        list.addAll(
            listOf(
                LibraryCategoryItem("Playlists", "Your playlists", Icons.AutoMirrored.Rounded.PlaylistPlay) {
                    navigateToTab.value("Playlists")
                },
                LibraryCategoryItem("Liked songs", "Your YouTube Music likes", Icons.Rounded.Favorite) {
                    navigateToTab.value("Songs")
                },
                LibraryCategoryItem("Albums", "Browse saved albums", Icons.Rounded.Album) {
                    navigateToTab.value("Albums")
                },
                LibraryCategoryItem("Artists", "Browse artists", Icons.Rounded.Person) {
                    navigateToTab.value("Artists")
                },
                LibraryCategoryItem("Podcasts", "Podcast library", Icons.Rounded.Podcasts) {
                    navigateToTab.value("Podcasts")
                },
                LibraryCategoryItem("Downloaded songs", "Music saved on this device", Icons.AutoMirrored.Rounded.QueueMusic) {
                    navigateToTab.value("Downloaded songs")
                },
            )
        )

        // Keep the unsupported Recap destination out of Library's category grid too.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            list.add(
                LibraryCategoryItem("Recap", "Your listening recap", Icons.Rounded.BarChart) {
                    navigateToTab.value("Recap")
                }
            )
        }

        onlinePlaylists.forEach { playlist ->
            list.add(object : UnifiedLibraryItem {
                override val title = playlist.title
                override val thumbnailUrl = playlist.thumbnailUrl
                override val subtitle = "Playlist" + (if (playlist.songCount?.isNotEmpty() == true) " • ${playlist.songCount}" else "")
                override val onClick = {
                    exploreViewModel?.cameFromLibrary = true
                    exploreViewModel?.loadPlaylist(playlist.playlistId)
                    navigateToExplore(activity)
                }
                override val isCircle = false
            })
        }

        onlineAlbums.forEach { album ->
            list.add(object : UnifiedLibraryItem {
                override val title = album.title
                override val thumbnailUrl = album.thumbnailUrl
                override val subtitle = "Album" + (if (album.artists.isNotEmpty()) " • ${album.artists.firstOrNull()?.name}" else "")
                override val onClick = {
                    exploreViewModel?.cameFromLibrary = true
                    exploreViewModel?.loadAlbum(album.browseId)
                    navigateToExplore(activity)
                }
                override val isCircle = false
            })
        }

        onlineArtists.forEach { artist ->
            list.add(object : UnifiedLibraryItem {
                override val title = artist.title
                override val thumbnailUrl = artist.thumbnailUrl
                override val subtitle = "Artist"
                override val onClick = {
                    exploreViewModel?.cameFromLibrary = true
                    exploreViewModel?.loadArtist(artist.browseId, artist.thumbnailUrl)
                    navigateToExplore(activity)
                }
                override val isCircle = true
            })
        }

        onlinePodcasts.forEach { podcast ->
            list.add(object : UnifiedLibraryItem {
                override val title = podcast.title
                override val thumbnailUrl = podcast.thumbnailUrl
                override val subtitle = "Podcast"
                override val onClick = {
                    exploreViewModel?.cameFromLibrary = true
                    exploreViewModel?.loadPlaylist(podcast.playlistId)
                    navigateToExplore(activity)
                }
                override val isCircle = false
            })
        }

        // Categories stay first; the saved items below retain their source order.
        list
    }

    if (unifiedItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No items found in your library", color = DsmusicTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 176.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            items(unifiedItems) { item ->
                UnifiedLibraryCard(item)
            }
        }
    }
}

private interface UnifiedLibraryItem {
    val title: String
    val thumbnailUrl: String?
    val subtitle: String
    val onClick: () -> Unit
    val isCircle: Boolean
    val icon: ImageVector? get() = null
}

private data class LibraryCategoryItem(
    override val title: String,
    override val subtitle: String,
    override val icon: ImageVector,
    override val onClick: () -> Unit
) : UnifiedLibraryItem {
    override val thumbnailUrl: String? = null
    override val isCircle: Boolean = false
}

private fun libraryCardGradient(title: String): List<Color> {
    val palettes = listOf(
        listOf(Color(0xFFFF5F6D), Color(0xFFFFC371)),
        listOf(Color(0xFF4776E6), Color(0xFF8E54E9)),
        listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
        listOf(Color(0xFFEE0979), Color(0xFFFF6A00)),
        listOf(Color(0xFF0575E6), Color(0xFF00F260)),
        listOf(Color(0xFF7F00FF), Color(0xFFE100FF)),
        listOf(Color(0xFFFC4A1A), Color(0xFFF7B733))
    )
    return palettes[Math.floorMod(title.hashCode(), palettes.size)]
}



@Composable
private fun DownloadsSelectionControls(
    isSelecting: Boolean,
    selectedCount: Int,
    onStartSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onOpenMenu: () -> Unit,
    menuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClearSelection: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isSelecting) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "$selectedCount selected",
                modifier = Modifier.weight(1f),
                style = DsmusicTheme.typography.labelLarge,
                color = DsmusicTheme.colorScheme.primary
            )
            DsmusicTextButton(onClick = onSelectAll) { Text("Select all") }
            Box {
                DsmusicIconButton(onClick = onOpenMenu) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Selected download actions")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = onDismissMenu
                ) {
                    DropdownMenuItem(text = { Text("Play next") }, onClick = onPlayNext)
                    DropdownMenuItem(text = { Text("Add to queue") }, onClick = onAddToQueue)
                    DropdownMenuItem(text = { Text("Delete selected") }, onClick = onDeleteSelected)
                    DropdownMenuItem(text = { Text("Clear selection") }, onClick = onClearSelection)
                }
            }
            DsmusicTextButton(onClick = onDone) { Text("Done") }
        }
    }
}

@Composable
private fun LibraryGradientPlaylistHero(
    title: String,
    subtitle: String,
    status: String,
    artwork: Any?,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    actions: @Composable () -> Unit
) {
    val context = LocalContext.current
    val palette = remember(title) { libraryCardGradient(title) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(188.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(palette))
        ) {
            if (artwork != null) {
                AsyncImage(
                    model = libraryArtworkRequestBuilder(context, artwork).crossfade(false).build(),
                    contentDescription = "$title artwork",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Rounded.PlaylistPlay,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(68.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.08f),
                                palette[0].copy(alpha = 0.18f),
                                palette[1].copy(alpha = 0.46f),
                                DsmusicTheme.colorScheme.background.copy(alpha = 0.78f)
                            )
                        )
                    )
            )
        }
        if (showTitle) {
            Text(
                title,
                style = DsmusicTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = DsmusicTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            subtitle,
            style = DsmusicTheme.typography.bodyMedium,
            color = DsmusicTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            status,
            style = DsmusicTheme.typography.labelMedium,
            color = DsmusicTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        actions()
    }
}

@Composable
private fun DsmusicLibraryListTile(
    title: String,
    subtitle: String,
    iconBackground: Color = DsmusicTheme.colorScheme.primaryContainer,
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(DsmusicTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(12.dp))
                .background(iconBackground),
            contentAlignment = Alignment.Center,
            content = { iconContent() }
        )
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = DsmusicTheme.typography.bodySmall,
                color = DsmusicTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DsmusicLibraryGridTile(
    title: String,
    backgroundColor: Color,
    iconColor: Color,
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().height(190.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f)
                .background(DsmusicTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
            content = { iconContent() }
        )
        Text(
            title,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun UnifiedLibraryCard(item: UnifiedLibraryItem) {
    val accent = remember(item.title) { libraryCardGradient(item.title) }
    val hasArtwork = !item.thumbnailUrl.isNullOrBlank()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = accent.map { it.copy(alpha = if (hasArtwork) 0.13f else 0.20f) }
                )
            )
            .clickable { item.onClick() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(if (item.isCircle) CircleShape else RoundedCornerShape(12.dp))
                .background(
                    if (hasArtwork) {
                        Brush.linearGradient(accent.map { it.copy(alpha = 0.16f) })
                    } else {
                        Brush.linearGradient(accent)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (hasArtwork) {
                androidx.compose.foundation.Image(
                    painter = coil.compose.rememberAsyncImagePainter(
                        model = libraryArtworkRequestBuilder(LocalContext.current, item.thumbnailUrl)
                            .crossfade(false)
                            .build()
                    ),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon ?: if (item.isCircle) Icons.Rounded.Person else Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            item.title,
            fontWeight = FontWeight.Bold,
            style = DsmusicTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = DsmusicTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            item.subtitle,
            style = DsmusicTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = DsmusicTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}


@Composable
private fun PlaylistsTabContent(
    nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    viewModel: PlayerSharedViewModel,
    showAsList: Boolean = false,
    isPlayerBackActive: Boolean = false,
    onCachedSongLongClick: (CachedSongEntry) -> Unit,
    onOpenDownloads: () -> Unit,
    onLibrarySubpageChanged: (Boolean) -> Unit = {},
    bottomContentPadding: Dp = 176.dp
) {
    val context = LocalContext.current
    val lyricsCache = remember(context) { LyricsCacheManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val pageTransitionDurationMillis = LocalPageTransitionDurationMillis.current
    val activity = remember(context) { getActivityFromContext(context) as? androidx.fragment.app.FragmentActivity }
    val accountViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[AccountViewModel::class.java] }
    }
    val exploreViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[ExploreViewModel::class.java] }
    }
    val onlinePlaylists by accountViewModel?.playlists?.collectAsStateWithLifecycle(emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val localPlaylists by viewModel.localPlaylistsFlow.collectAsStateWithLifecycle(emptyList())

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var selectedLocalPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var showCachePlaylist by remember { mutableStateOf(false) }
    LaunchedEffect(showCachePlaylist, selectedLocalPlaylist?.id) {
        val isSubpageOpen = showCachePlaylist || selectedLocalPlaylist != null
        onLibrarySubpageChanged(isSubpageOpen)
        if (isSubpageOpen) (activity as? MainActivity)?.showBottomNavWithAnimation()
    }
    val playlistGridState = rememberLazyGridState()

    BackHandler(
        enabled = (showCachePlaylist || selectedLocalPlaylist != null) && !isPlayerBackActive
    ) {
        if (selectedLocalPlaylist != null) selectedLocalPlaylist = null
        else showCachePlaylist = false
    }

    LaunchedEffect(Unit) { accountViewModel?.refreshAll() }

    if (showCreatePlaylistDialog) {
        var name by remember { mutableStateOf("") }
        var creating by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!creating) showCreatePlaylistDialog = false },
            title = { Text("Create playlist", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Playlist name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !creating
                    )
                    Text(
                        if (AccountManager.isLoggedIn(context)) {
                            "This playlist will be saved to your YouTube Music library."
                        } else {
                            "You are signed out. This playlist will be stored on this device."
                        },
                        style = DsmusicTheme.typography.bodySmall,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                DsmusicButton(
                    onClick = {
                        val title = name.trim()
                        if (title.isNotEmpty() && !creating) {
                            if (!AccountManager.isLoggedIn(context)) {
                                viewModel.createLocalPlaylist(title)
                                showCreatePlaylistDialog = false
                            } else {
                                creating = true
                                scope.launch {
                                    YouTubeMusic.createPlaylist(
                                        title = title,
                                        description = "",
                                        privacyStatus = "PRIVATE"
                                    ).onSuccess { playlistId ->
                                        showCreatePlaylistDialog = false
                                        accountViewModel?.loadLibrary()
                                        exploreViewModel?.cameFromLibrary = true
                                        exploreViewModel?.loadPlaylist(playlistId)
                                        navigateToExplore(activity)
                                    }.onFailure { error ->
                                        com.dsann.music.ui.SnackbarController.showMessage(
                                            error.message ?: "Could not create the YouTube Music playlist."
                                        )
                                    }
                                    creating = false
                                }
                            }
                        }
                    },
                    enabled = name.isNotBlank() && !creating
                ) {
                    Text(if (creating) "Creating…" else "Create")
                }
            },
            dismissButton = {
                DsmusicTextButton(
                    onClick = { showCreatePlaylistDialog = false },
                    enabled = !creating
                ) { Text("Cancel") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = showCachePlaylist,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val direction = if (targetState) 1 else -1
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
            label = "Library Cache Page Slide"
        ) { showCachePage ->
    if (showCachePage) {
        // Keep Cache inside the root Library content instead of a fullscreen Dialog.
        // Dialog windows sit above MainActivity's persistent navigation and mini-player overlays.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DsmusicTheme.colorScheme.background)
        ) {
            StreamCacheTabContent(
                context = context,
                viewModel = viewModel,
                exploreViewModel = exploreViewModel,
                lyricsCache = lyricsCache,
                onSongLongClick = onCachedSongLongClick,
                onBack = { showCachePlaylist = false },
                bottomContentPadding = bottomContentPadding,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Playlists",
                    modifier = Modifier.weight(1f),
                    style = DsmusicTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                DsmusicIconButton(onClick = { showCreatePlaylistDialog = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Create playlist", tint = DsmusicTheme.colorScheme.primary)
                }
            }

            LazyVerticalGrid(
                state = playlistGridState,
                columns = if (showAsList) GridCells.Fixed(1) else GridCells.Adaptive(minSize = 152.dp),
                contentPadding = PaddingValues(bottom = bottomContentPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(nestedScrollConnection)
            ) {
                item(key = "ds-cache-folder", contentType = "special-playlist-folder") {
                    if (showAsList) {
                        DsmusicLibraryListTile(
                            title = "Cache",
                            subtitle = "Cached audio, artwork and lyrics",
                            onClick = { showCachePlaylist = true }
                        ) {
                            Icon(Icons.Rounded.Folder, null, tint = DsmusicTheme.colorScheme.onPrimaryContainer)
                        }
                    } else {
                        DsmusicLibraryGridTile(
                            title = "Cache",
                            backgroundColor = DsmusicTheme.colorScheme.surfaceContainerHigh,
                            iconColor = DsmusicTheme.colorScheme.onPrimaryContainer,
                            onClick = { showCachePlaylist = true }
                        ) {
                            Icon(Icons.Rounded.Folder, null, modifier = Modifier.size(42.dp), tint = DsmusicTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }

                item(key = "ds-downloads-folder", contentType = "special-library-downloads") {
                    if (showAsList) {
                        DsmusicLibraryListTile(
                            title = "Downloads",
                            subtitle = "Songs saved on this device",
                            onClick = onOpenDownloads
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.QueueMusic,
                                null,
                                tint = DsmusicTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    } else {
                        DsmusicLibraryGridTile(
                            title = "Downloads",
                            backgroundColor = DsmusicTheme.colorScheme.surfaceContainerHigh,
                            iconColor = DsmusicTheme.colorScheme.onPrimaryContainer,
                            onClick = onOpenDownloads
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.QueueMusic,
                                null,
                                modifier = Modifier.size(42.dp),
                                tint = DsmusicTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                items(
                    items = localPlaylists,
                    key = { "local-" + it.id },
                    contentType = { "local-playlist" }
                ) { playlist ->
                    if (showAsList) {
                        DsmusicLibraryListTile(
                            title = playlist.title,
                            subtitle = "On this device",
                            onClick = { selectedLocalPlaylist = playlist }
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = DsmusicTheme.colorScheme.onPrimaryContainer)
                        }
                    } else {
                        DsmusicLibraryGridTile(
                            title = playlist.title,
                            backgroundColor = DsmusicTheme.colorScheme.surfaceContainerHigh,
                            iconColor = DsmusicTheme.colorScheme.onPrimaryContainer,
                            onClick = { selectedLocalPlaylist = playlist }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.PlaylistPlay,
                                null,
                                modifier = Modifier.size(42.dp),
                                tint = DsmusicTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                items(
                    items = onlinePlaylists,
                    key = { "youtube-" + it.playlistId },
                    contentType = { "online-playlist" }
                ) { playlist ->
                    OnlinePlaylistCard(
                        playlist = playlist,
                        asList = showAsList,
                        onClick = {
                            exploreViewModel?.cameFromLibrary = true
                            exploreViewModel?.loadPlaylist(playlist.playlistId)
                            navigateToExplore(activity)
                        },
                    )
                }
            }
        }
    }
        }
        AnimatedContent(
            targetState = selectedLocalPlaylist,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val direction = if (targetState != null) 1 else -1
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
            label = "Library Local Playlist Page Slide"
        ) { selectedPlaylist ->
            if (selectedPlaylist != null) {
                val playlist = localPlaylists.firstOrNull { it.id == selectedPlaylist.id } ?: selectedPlaylist
                LocalPlaylistDetailsScreen(
                    playlist = playlist,
                    viewModel = viewModel,
                    accountViewModel = accountViewModel,
                    bottomContentPadding = bottomContentPadding,
                    onDismiss = { selectedLocalPlaylist = null }
                )
            }
        }
    }
}


@Composable
private fun LocalPlaylistSongsList(
    playlist: PlaylistEntity,
    songs: List<SongItem>,
    viewModel: PlayerSharedViewModel,
    onDismiss: () -> Unit,
    isSelecting: Boolean = false,
    selectedSongKeys: Set<String> = emptySet(),
    onToggleSelection: (SongItem) -> Unit = {},
    emptyMessage: String = "This playlist is empty",
    bottomContentPadding: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 14.dp, bottom = bottomContentPadding)
    ) {
        item(key = "playlist-songs-heading") {
            Text(
                "Songs",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = DsmusicTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DsmusicTheme.colorScheme.onSurface
            )
        }
        if (songs.isEmpty()) {
            item(key = "playlist-empty") {
                Box(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emptyMessage, color = DsmusicTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            itemsIndexed(items = songs, key = { index, song -> "playlist-song:" + song.id + ":" + index }) { _, song ->
                LocalPlaylistSongRow(
                    playlist = playlist,
                    songs = songs,
                    song = song,
                    viewModel = viewModel,
                    onDismiss = onDismiss,
                    isSelecting = isSelecting,
                    isSelected = (song.videoId ?: song.id.toString()) in selectedSongKeys,
                    onToggleSelection = { onToggleSelection(song) }
                )
            }
        }
    }
}

@Composable
private fun LocalPlaylistSongRow(
    playlist: PlaylistEntity,
    songs: List<SongItem>,
    song: SongItem,
    viewModel: PlayerSharedViewModel,
    onDismiss: () -> Unit,
    isSelecting: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {}
) {
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying = currentSong?.id == song.id
    val playingCoverColor = rememberNowPlayingCoverColor(song.thumbnailUrl, isPlaying)
    DsmusicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = DsmusicTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (isSelecting) {
                        onToggleSelection()
                    } else {
                        viewModel.setSongList(songs)
                        viewModel.playSong(song)
                        onDismiss()
                    }
                }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(DsmusicTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (!song.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = libraryArtworkRequestBuilder(
                            LocalContext.current,
                            song.thumbnailUrl
                        ).crossfade(false).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Rounded.MusicNote,
                        null,
                        tint = DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isPlaying) {
                    NowPlayingCoverIndicator(
                        isPlaying = true,
                        coverColor = playingCoverColor,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = DsmusicTheme.colorScheme.onSurface
                )
                Text(
                    song.artist,
                    style = DsmusicTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelecting) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) DsmusicTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .border(
                            1.dp,
                            if (isSelected) DsmusicTheme.colorScheme.primary
                            else DsmusicTheme.colorScheme.outlineVariant,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = DsmusicTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                DsmusicIconButton(
                    onClick = {
                        viewModel.removeSongFromLocalPlaylist(
                            playlist.id,
                            song.videoId ?: song.id.toString()
                        )
                    }
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Remove song",
                        tint = DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}



@Composable
private fun LocalPlaylistDetailsScreen(
    playlist: PlaylistEntity,
    viewModel: PlayerSharedViewModel,
    accountViewModel: AccountViewModel?,
    bottomContentPadding: Dp = 176.dp,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val songsEntity by viewModel.getSongsForLocalPlaylist(playlist.id).collectAsStateWithLifecycle(emptyList())
    val songs = remember(songsEntity) { songsEntity.map { it.toSongItem() } }

    var playlistOptionsExpanded by remember { mutableStateOf(false) }
    var playlistSortMenuExpanded by remember { mutableStateOf(false) }
    var playlistSortOrder by rememberSaveable(playlist.id) { mutableStateOf(SortOrder.A_Z) }
    var playlistSortAscending by rememberSaveable(playlist.id) { mutableStateOf(true) }
    var isPlaylistSearchVisible by rememberSaveable(playlist.id) { mutableStateOf(false) }
    var playlistSearchQuery by rememberSaveable(playlist.id) { mutableStateOf("") }
    var isSelectingPlaylistSongs by remember { mutableStateOf(false) }
    var selectedPlaylistSongKeys by remember { mutableStateOf(setOf<String>()) }
    var playlistSelectionMenuExpanded by remember { mutableStateOf(false) }

    fun playlistSongKey(song: SongItem): String =
        song.videoId?.takeIf { it.isNotBlank() } ?: song.id.toString()

    val filteredPlaylistSongs by produceState(
        initialValue = songs,
        songs,
        playlistSearchQuery,
        playlistSortOrder,
        playlistSortAscending
    ) {
        if (playlistSearchQuery.isNotBlank()) delay(120L)
        val query = playlistSearchQuery.trim()
        value = withContext(Dispatchers.Default) {
            val matching = if (query.isEmpty()) songs else songs.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true)
            }
            val ascendingSorted = when (playlistSortOrder) {
                SortOrder.A_Z -> matching.sortedBy { it.title.lowercase() }
                SortOrder.DATE_ADDED -> matching.sortedWith(
                    compareBy<SongItem> { downloadedSongAddedAtSeconds(it) }
                        .thenBy { downloadedSongFileModifiedAtMs(it) }
                        .thenBy { it.id }
                )
                SortOrder.ARTIST -> matching.sortedBy { it.artist.lowercase() }
                SortOrder.DURATION -> matching.sortedBy { it.duration }
            }
            if (playlistSortAscending) ascendingSorted else ascendingSorted.asReversed()
        }
    }
    val selectedPlaylistSongs = songs.filter { playlistSongKey(it) in selectedPlaylistSongKeys }
    // Only YouTube video IDs can be copied into a YouTube Music playlist. Local MediaStore
    // IDs and malformed/legacy rows are deliberately left in the device-only playlist.
    val uploadableVideoIds = remember(songsEntity) {
        songsEntity.mapNotNull { entry ->
            entry.songId.takeIf { id ->
                id.toLongOrNull() == null &&
                    id.length == 11 && id.all { character ->
                    character in 'A'..'Z' || character in 'a'..'z' ||
                        character in '0'..'9' || character == '_' || character == '-'
                }
            }
        }.distinct()
    }
    val alreadyUploaded = !playlist.youtubePlaylistId.isNullOrBlank()
    var isUploading by remember(playlist.id) { mutableStateOf(false) }

    val uploadLocalPlaylist: () -> Unit = {
        when {
            alreadyUploaded -> {
                com.dsann.music.ui.SnackbarController.showMessage(
                    "This playlist has already been uploaded to YouTube Music."
                )
            }
            !AccountManager.isLoggedIn(context) -> {
                com.dsann.music.ui.SnackbarController.showMessage(
                    "Sign in to YouTube Music before uploading this playlist."
                )
            }
            uploadableVideoIds.isEmpty() -> {
                com.dsann.music.ui.SnackbarController.showMessage(
                    "There are no YouTube Music tracks to upload. Local audio files cannot be added to a YouTube playlist."
                )
            }
            isUploading -> Unit
            else -> {
                isUploading = true
                scope.launch {
                    try {
                        val remotePlaylistId = YouTubeMusic.createPlaylist(
                            title = playlist.title,
                            description = "Created with Quantune",
                            privacyStatus = "PRIVATE",
                            videoIds = uploadableVideoIds
                        ).getOrThrow()

                        try {
                            viewModel.markLocalPlaylistUploaded(playlist.id, remotePlaylistId)
                        } catch (saveError: Exception) {
                            com.dsann.music.ui.SnackbarController.showMessage(
                                "Uploaded to YouTube Music, but Quantune could not save the link. Do not upload again."
                            )
                            return@launch
                        }

                        accountViewModel?.loadLibrary()
                        val skipped = (songsEntity.size - uploadableVideoIds.size).coerceAtLeast(0)
                        val message = buildString {
                            append("Uploaded ")
                            append(uploadableVideoIds.size)
                            append(" tracks as a private YouTube Music playlist. Your local playlist was kept.")
                            if (skipped > 0) {
                                append(" ")
                                append(skipped)
                                append(" local or duplicate entries were skipped.")
                            }
                        }
                        com.dsann.music.ui.SnackbarController.showMessage(message)
                    } catch (error: Exception) {
                        com.dsann.music.ui.SnackbarController.showMessage(
                            error.message ?: "Could not upload this playlist to YouTube Music."
                        )
                    } finally {
                        isUploading = false
                    }
                }
            }
        }
    }
    val playlistOverflowMenu: @Composable () -> Unit = {
        Box {
            DsmusicIconButton(onClick = { playlistOptionsExpanded = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist options")
            }
            DropdownMenu(
                expanded = playlistOptionsExpanded,
                onDismissRequest = { playlistOptionsExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Sort by") },
                    onClick = {
                        playlistOptionsExpanded = false
                        playlistSortMenuExpanded = true
                    },
                    trailingIcon = {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Search") },
                    onClick = {
                        isPlaylistSearchVisible = !isPlaylistSearchVisible
                        if (!isPlaylistSearchVisible) playlistSearchQuery = ""
                        playlistOptionsExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Select") },
                    onClick = {
                        isSelectingPlaylistSongs = true
                        selectedPlaylistSongKeys = emptySet()
                        playlistOptionsExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            when {
                                isUploading -> "Uploading…"
                                alreadyUploaded -> "Uploaded"
                                else -> "Upload"
                            }
                        )
                    },
                    onClick = {
                        playlistOptionsExpanded = false
                        uploadLocalPlaylist()
                    },
                    enabled = !isUploading && !alreadyUploaded
                )
            }
            DropdownMenu(
                expanded = playlistSortMenuExpanded,
                onDismissRequest = { playlistSortMenuExpanded = false }
            ) {
                Text(
                    "SORT BY",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
                    style = DsmusicTheme.typography.labelSmall,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )
                SortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                when (order) {
                                    SortOrder.A_Z -> "A–Z"
                                    SortOrder.DATE_ADDED -> "Date added"
                                    SortOrder.ARTIST -> "Artist"
                                    SortOrder.DURATION -> "Duration"
                                }
                            )
                        },
                        onClick = { playlistSortOrder = order },
                        trailingIcon = {
                            if (playlistSortOrder == order) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = "Selected sort",
                                    tint = DsmusicTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }
                com.dsann.music.ui.theme.HorizontalDivider()
                Text(
                    "ORDER",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
                    style = DsmusicTheme.typography.labelSmall,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )
                DropdownMenuItem(
                    text = { Text("Ascending") },
                    onClick = { playlistSortAscending = true },
                    trailingIcon = {
                        if (playlistSortAscending) Icon(
                            Icons.Rounded.Check,
                            contentDescription = "Ascending selected",
                            tint = DsmusicTheme.colorScheme.primary
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Descending") },
                    onClick = { playlistSortAscending = false },
                    trailingIcon = {
                        if (!playlistSortAscending) Icon(
                            Icons.Rounded.Check,
                            contentDescription = "Descending selected",
                            tint = DsmusicTheme.colorScheme.primary
                        )
                    }
                )
            }
        }
    }

    val playlistSearchBar: @Composable () -> Unit = {
        if (isPlaylistSearchVisible) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(CircleShape)
                    .background(DsmusicTheme.colorScheme.surfaceContainerLow)
                    .border(1.dp, DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.42f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = "Search playlist",
                    tint = DsmusicTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    if (playlistSearchQuery.isEmpty()) {
                        Text(
                            "Search playlist songs…",
                            style = DsmusicTheme.typography.bodyMedium,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = playlistSearchQuery,
                        onValueChange = { playlistSearchQuery = it },
                        singleLine = true,
                        textStyle = DsmusicTheme.typography.bodyMedium.copy(
                            color = DsmusicTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                DsmusicIconButton(onClick = {
                    playlistSearchQuery = ""
                    isPlaylistSearchVisible = false
                }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close search")
                }
            }
        }
    }

    val clearPlaylistSelection = {
        playlistSelectionMenuExpanded = false
        selectedPlaylistSongKeys = emptySet()
        isSelectingPlaylistSongs = false
    }
    val selectionControls: @Composable (Modifier) -> Unit = { controlModifier ->
        CacheSelectionControls(
            isSelecting = true,
            selectedCount = selectedPlaylistSongKeys.size,
            totalCount = filteredPlaylistSongs.size,
            menuExpanded = playlistSelectionMenuExpanded,
            onStartSelection = { isSelectingPlaylistSongs = true },
            onSelectAll = {
                selectedPlaylistSongKeys = filteredPlaylistSongs.map(::playlistSongKey).toSet()
            },
            onOpenMenu = { playlistSelectionMenuExpanded = true },
            onDismissMenu = { playlistSelectionMenuExpanded = false },
            onPlayNext = {
                selectedPlaylistSongs.asReversed().forEach(viewModel::addToQueueNext)
                com.dsann.music.ui.SnackbarController.showMessage(
                    "Added " + selectedPlaylistSongs.size + " playlist songs to play next"
                )
                clearPlaylistSelection()
            },
            onAddToQueue = {
                selectedPlaylistSongs.forEach(viewModel::addToQueue)
                com.dsann.music.ui.SnackbarController.showMessage(
                    "Added " + selectedPlaylistSongs.size + " playlist songs to queue"
                )
                clearPlaylistSelection()
            },
            onRemoveSelected = {
                selectedPlaylistSongs.forEach { song ->
                    viewModel.removeSongFromLocalPlaylist(
                        playlist.id,
                        song.videoId ?: song.id.toString()
                    )
                }
                com.dsann.music.ui.SnackbarController.showMessage(
                    "Removed " + selectedPlaylistSongs.size + " songs from this playlist"
                )
                clearPlaylistSelection()
            },
            onClearSelection = clearPlaylistSelection,
            onDone = clearPlaylistSelection,
            menuContentDescription = "Selected playlist actions",
            modifier = controlModifier
        )
    }

    val artworkUrl = playlist.thumbnailUrl?.takeIf { it.isNotBlank() }
        ?: songs.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
    val isLandscapeLayout =
        LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    var artworkColor by remember(playlist.id) { mutableStateOf(Color(0xFF26364C)) }
    LaunchedEffect(playlist.id, artworkUrl) {
        if (!artworkUrl.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val request = libraryArtworkRequestBuilder(context, artworkUrl)
                        .allowHardware(false)
                        .build()
                    val result = context.imageLoader.execute(request)
                    if (result is SuccessResult) {
                        val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        if (bitmap != null) {
                            val palette = Palette.from(bitmap).generate()
                            val extracted = palette.getDarkVibrantColor(
                                palette.getDarkMutedColor(
                                    palette.getDominantColor(0xFF26364C.toInt())
                                )
                            )
                            withContext(Dispatchers.Main) {
                                artworkColor = Color(extracted)
                            }
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DsmusicTheme.colorScheme.background)
    ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isLandscapeLayout) 520.dp else 440.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                artworkColor.copy(alpha = 0.72f),
                                artworkColor.copy(alpha = 0.32f),
                                DsmusicTheme.colorScheme.background
                            )
                        )
                    )
            )
            if (isLandscapeLayout) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 16.dp, top = 10.dp, end = 14.dp, bottom = bottomContentPadding),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DsmusicIconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back to Library"
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            if (alreadyUploaded) {
                                Text(
                                    "Uploaded",
                                    style = DsmusicTheme.typography.labelSmall,
                                    color = DsmusicTheme.colorScheme.primary
                                )
                            }
                            playlistOverflowMenu()
                            DsmusicIconButton(
                                onClick = {
                                    viewModel.deleteLocalPlaylist(playlist.id)
                                    onDismiss()
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Delete playlist",
                                    tint = DsmusicTheme.colorScheme.error
                                )
                            }
                        }
                        LibraryGradientPlaylistHero(
                            title = playlist.title,
                            subtitle = songs.size.toString() + " songs · On this device",
                            status = "Saved in Quantune",
                            artwork = artworkUrl,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DsmusicButton(
                                    onClick = {
                                        if (filteredPlaylistSongs.isNotEmpty()) {
                                            viewModel.setSongList(filteredPlaylistSongs)
                                            viewModel.playSong(filteredPlaylistSongs.first())
                                            onDismiss()
                                        }
                                    },
                                    enabled = songs.isNotEmpty(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Play All")
                                }
                                DsmusicOutlinedButton(
                                    onClick = {
                                        if (filteredPlaylistSongs.isNotEmpty()) {
                                            val shuffled = filteredPlaylistSongs.shuffled()
                                            viewModel.setSongList(shuffled)
                                            viewModel.playSong(shuffled.first())
                                            onDismiss()
                                        }
                                    },
                                    enabled = songs.isNotEmpty(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Shuffle, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Shuffle")
                                }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    )
                    Column(
                        modifier = Modifier.weight(0.58f).fillMaxHeight()
                    ) {
                        playlistSearchBar()
                        if (isSelectingPlaylistSongs) {
                            selectionControls(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        LocalPlaylistSongsList(
                            playlist = playlist,
                            songs = filteredPlaylistSongs,
                            viewModel = viewModel,
                            onDismiss = onDismiss,
                            isSelecting = isSelectingPlaylistSongs,
                            selectedSongKeys = selectedPlaylistSongKeys,
                            onToggleSelection = { song ->
                                val key = playlistSongKey(song)
                                selectedPlaylistSongKeys = if (key in selectedPlaylistSongKeys) {
                                    selectedPlaylistSongKeys - key
                                } else {
                                    selectedPlaylistSongKeys + key
                                }
                            },
                            emptyMessage = if (songs.isEmpty()) "This playlist is empty" else "No matching songs",
                            bottomContentPadding = bottomContentPadding,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DsmusicIconButton(onClick = onDismiss) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back to Library",
                                tint = DsmusicTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            playlist.title,
                            modifier = Modifier.weight(1f),
                            style = DsmusicTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = DsmusicTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (alreadyUploaded) {
                            Text(
                                "Uploaded",
                                style = DsmusicTheme.typography.labelSmall,
                                color = DsmusicTheme.colorScheme.primary
                            )
                        }
                        playlistOverflowMenu()
                        DsmusicIconButton(
                            onClick = {
                                viewModel.deleteLocalPlaylist(playlist.id)
                                onDismiss()
                            }
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Delete playlist",
                                tint = DsmusicTheme.colorScheme.error
                            )
                        }
                    }
                    playlistSearchBar()
                    if (isSelectingPlaylistSongs) {
                        selectionControls(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomContentPadding)
                    ) {
                        item(key = "playlist-details-header") {
                            LibraryGradientPlaylistHero(
                                title = playlist.title,
                                subtitle = songs.size.toString() + " songs · On this device",
                                status = "Saved in Quantune",
                                artwork = artworkUrl,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                showTitle = false
                            ) {
                                if (filteredPlaylistSongs.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        DsmusicButton(
                                            onClick = {
                                                viewModel.setSongList(filteredPlaylistSongs)
                                                viewModel.playSong(filteredPlaylistSongs.first())
                                                onDismiss()
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Play All")
                                        }
                                        DsmusicOutlinedButton(
                                            onClick = {
                                                val shuffled = filteredPlaylistSongs.shuffled()
                                                viewModel.setSongList(shuffled)
                                                viewModel.playSong(shuffled.first())
                                                onDismiss()
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.Shuffle, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Shuffle")
                                        }
                                    }
                                }
                            }
                        }
                        item(key = "playlist-songs-heading") {
                            Text(
                                "Songs",
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                style = DsmusicTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = DsmusicTheme.colorScheme.onSurface
                            )
                        }
                        if (filteredPlaylistSongs.isEmpty()) {
                            item(key = "playlist-empty") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(180.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(if (songs.isEmpty()) "This playlist is empty" else "No matching songs", color = DsmusicTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            itemsIndexed(items = filteredPlaylistSongs, key = { index, song -> "playlist-song:" + song.id + ":" + index }) { _, song ->
                                LocalPlaylistSongRow(
                                    playlist = playlist,
                                    songs = filteredPlaylistSongs,
                                    song = song,
                                    viewModel = viewModel,
                                    onDismiss = onDismiss,
                                    isSelecting = isSelectingPlaylistSongs,
                                    isSelected = playlistSongKey(song) in selectedPlaylistSongKeys,
                                    onToggleSelection = {
                                        val key = playlistSongKey(song)
                                        selectedPlaylistSongKeys = if (key in selectedPlaylistSongKeys) {
                                            selectedPlaylistSongKeys - key
                                        } else {
                                            selectedPlaylistSongKeys + key
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
}

@Composable
private fun OnlinePlaylistCard(
    playlist: OnlinePlaylist,
    asList: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val thumbnailRequest = remember(context, playlist.thumbnailUrl) {
        playlist.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { url ->
            libraryArtworkRequestBuilder(context, url)
                .crossfade(false)
                .build()
        }
    }

    if (asList) {
        DsmusicListItem(
            headlineContent = {
                Text(playlist.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Text(playlist.songCount ?: "Playlist", color = DsmusicTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            },
            leadingContent = {
                if (!playlist.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = thumbnailRequest,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(DsmusicTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = DsmusicTheme.colorScheme.onPrimaryContainer)
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = DsmusicTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
        )
        return
    }
    DsmusicCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DsmusicTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (playlist.thumbnailUrl?.isNotEmpty() == true) {
                    AsyncImage(
                        model = thumbnailRequest,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DsmusicTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            null,
                            tint = DsmusicTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    playlist.title,
                    fontWeight = FontWeight.Bold,
                    style = DsmusicTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = DsmusicTheme.colorScheme.onSurface
                )
                Text(
                    playlist.songCount ?: "Playlist",
                    style = DsmusicTheme.typography.bodySmall,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PodcastsTabContent(nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection) {
    val context = LocalContext.current
    val activity = remember { getActivityFromContext(context) as? androidx.fragment.app.FragmentActivity }

    val accountViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[AccountViewModel::class.java] }
    }
    val exploreViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[ExploreViewModel::class.java] }
    }

    val onlinePodcasts by accountViewModel?.podcasts?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }

    LaunchedEffect(Unit) {
        accountViewModel?.refreshAll()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Your Subscribed Podcasts",
            style = DsmusicTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = DsmusicTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (onlinePodcasts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Podcasts,
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .alpha(0.6f),
                        tint = DsmusicTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No podcasts found",
                        style = DsmusicTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DsmusicTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 176.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .nestedScroll(nestedScrollConnection)
            ) {
                items(onlinePodcasts) { podcast ->
                    OnlinePlaylistCard(podcast, onClick = {
                        exploreViewModel?.cameFromLibrary = true
                        exploreViewModel?.loadPlaylist(podcast.playlistId)
                        navigateToExplore(activity)
                    })
                }
            }
        }
    }
}

@Composable
private fun SongsTabContent(nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection, viewModel: PlayerSharedViewModel) {
    val context = LocalContext.current
    val activity = remember { getActivityFromContext(context) as? androidx.fragment.app.FragmentActivity }

    val accountViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[AccountViewModel::class.java] }
    }

    val onlineSongs by accountViewModel?.songs?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        accountViewModel?.refreshAll()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Liked Songs (YT Music)",
            style = DsmusicTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = DsmusicTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (onlineSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No online songs found", color = DsmusicTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .nestedScroll(nestedScrollConnection),
                contentPadding = PaddingValues(bottom = 176.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(onlineSongs) { song ->
                    val isPlaying = currentSong?.videoId == song.videoId
                    val playingCoverColor = rememberNowPlayingCoverColor(song.thumbnailUrl, isPlaying)
                    DsmusicListItem(
                        headlineContent = { Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(song.artist, color = DsmusicTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = {
                            Box(
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (song.thumbnailUrl?.isNotEmpty() == true) {
                                    androidx.compose.foundation.Image(
                                        painter = coil.compose.rememberAsyncImagePainter(
                                            model = libraryArtworkRequestBuilder(LocalContext.current, song.thumbnailUrl)
                                                .crossfade(false)
                                                .build()
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(48.dp))
                                }
                                if (isPlaying) {
                                    NowPlayingCoverIndicator(
                                        isPlaying = true,
                                        coverColor = playingCoverColor,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = DsmusicTheme.colorScheme.surfaceContainer),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val list = onlineSongs.map { s ->
                                    SongItem.createOnlineSong(
                                        s.videoId,
                                        s.title,
                                        s.artist,
                                        "",
                                        s.durationMs,
                                        s.thumbnailUrl,
                                        s.artistId
                                    )
                                }
                                viewModel.setSongList(list)
                                val idx = list.indexOfFirst { it.videoId == song.videoId }
                                if (idx != -1) {
                                    viewModel.playSongAtIndex(idx)
                                }
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumsTabContent(
    nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    showAsList: Boolean = false
) {
    val context = LocalContext.current
    val activity = remember { getActivityFromContext(context) as? androidx.fragment.app.FragmentActivity }

    val accountViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[AccountViewModel::class.java] }
    }
    val exploreViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[ExploreViewModel::class.java] }
    }

    val onlineAlbums by accountViewModel?.albums?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }

    LaunchedEffect(Unit) {
        accountViewModel?.refreshAll()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "YT Music Albums",
            style = DsmusicTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = DsmusicTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (onlineAlbums.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No online albums found", color = DsmusicTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (showAsList) 1 else 2),
                contentPadding = PaddingValues(bottom = 176.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .nestedScroll(nestedScrollConnection)
            ) {
                items(onlineAlbums) { album ->
                    OnlineAlbumCard(album, asList = showAsList, onClick = {
                        exploreViewModel?.cameFromLibrary = true
                        exploreViewModel?.loadAlbum(album.browseId)
                        navigateToExplore(activity)
                    })
                }
            }
        }
    }
}

@Composable
private fun OnlineAlbumCard(
    album: OnlineAlbum,
    asList: Boolean = false,
    onClick: () -> Unit
) {
    if (asList) {
        DsmusicListItem(
            headlineContent = { Text(album.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(album.artists.firstOrNull()?.name ?: "Unknown Artist", color = DsmusicTheme.colorScheme.onSurfaceVariant, maxLines = 1) },
            leadingContent = {
                if (!album.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = libraryArtworkRequestBuilder(LocalContext.current, album.thumbnailUrl).crossfade(false).build(),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(DsmusicTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Album, null, tint = DsmusicTheme.colorScheme.onPrimaryContainer)
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = DsmusicTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
        )
        return
    }
    DsmusicCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DsmusicTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (album.thumbnailUrl?.isNotEmpty() == true) {
                    androidx.compose.foundation.Image(
                        painter = coil.compose.rememberAsyncImagePainter(
                            model = libraryArtworkRequestBuilder(LocalContext.current, album.thumbnailUrl)
                                .crossfade(false)
                                .build()
                        ),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DsmusicTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Album,
                            null,
                            tint = DsmusicTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    album.title,
                    fontWeight = FontWeight.Bold,
                    style = DsmusicTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = DsmusicTheme.colorScheme.onSurface
                )
                Text(
                    album.artists.firstOrNull()?.name ?: "Unknown Artist",
                    style = DsmusicTheme.typography.bodySmall,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ArtistsTabContent(
    nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    showAsList: Boolean = false
) {
    val context = LocalContext.current
    val activity = remember { getActivityFromContext(context) as? androidx.fragment.app.FragmentActivity }

    val accountViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[AccountViewModel::class.java] }
    }
    val exploreViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[ExploreViewModel::class.java] }
    }

    val onlineArtists by accountViewModel?.artists?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }

    LaunchedEffect(Unit) {
        accountViewModel?.refreshAll()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Followed Artists (YT Music)",
            style = DsmusicTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = DsmusicTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (onlineArtists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No online artists found", color = DsmusicTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (showAsList) 1 else 2),
                contentPadding = PaddingValues(bottom = 176.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .nestedScroll(nestedScrollConnection)
            ) {
                items(onlineArtists) { artist ->
                    OnlineArtistCard(artist, asList = showAsList, onClick = {
                        exploreViewModel?.cameFromLibrary = true
                        exploreViewModel?.loadArtist(artist.browseId, artist.thumbnailUrl)
                        navigateToExplore(activity)
                    })
                }
            }
        }
    }
}

@Composable
private fun OnlineArtistCard(
    artist: OnlineArtist,
    asList: Boolean = false,
    onClick: () -> Unit
) {
    if (asList) {
        DsmusicListItem(
            headlineContent = { Text(artist.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text("Artist", color = DsmusicTheme.colorScheme.onSurfaceVariant, maxLines = 1) },
            leadingContent = {
                if (!artist.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = libraryArtworkRequestBuilder(LocalContext.current, artist.thumbnailUrl).crossfade(false).build(),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(DsmusicTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Person, null, tint = DsmusicTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = DsmusicTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(CircleShape)
                .background(DsmusicTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (artist.thumbnailUrl?.isNotEmpty() == true) {
                androidx.compose.foundation.Image(
                    painter = coil.compose.rememberAsyncImagePainter(
                        model = libraryArtworkRequestBuilder(LocalContext.current, artist.thumbnailUrl)
                            .crossfade(false)
                            .build()
                    ),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = DsmusicTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            artist.title,
            fontWeight = FontWeight.Bold,
            style = DsmusicTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = DsmusicTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            "Artist",
            style = DsmusicTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = DsmusicTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private data class ReplaySongSummary(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val totalMs: Long,
    val sessions: Int,
    val lastTimestamp: Long
)

private data class ReplayArtistSummary(
    val name: String,
    val totalMs: Long,
    val sessions: Int
)

private data class ReplayMonthSummary(
    val label: String,
    val totalMs: Long
)

@Composable
fun RecapTabContent(
    nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    viewModel: PlayerSharedViewModel,
    onNavigateToExplore: () -> Unit,
    onArtistClick: (String) -> Unit = {},
    localSongs: List<SongItem> = emptyList(),
    onSongMenuClick: (SongItem) -> Unit = {}
) {
    val context = LocalContext.current
    val rootTabActive = LocalRootTabActive.current
    LaunchedEffect(rootTabActive) {
        if (!rootTabActive) {
            cancelLocalMusicScan(viewModel, owner = "library")
            return@LaunchedEffect
        }
        if (localSongs.isEmpty()) scanLocalFiles(context, viewModel, forceRefresh = false)
    }
    val events by viewModel.listeningHistoryEventsFlow.collectAsStateWithLifecycle(emptyList())
    val activePlayerSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val playerIsPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val years = remember(events) {
        events.map { event ->
            java.util.Calendar.getInstance().apply {
                timeInMillis = event.timestamp
            }.get(java.util.Calendar.YEAR).toString()
        }.distinct().sortedDescending()
    }
    val periods = remember(years) {
        listOf("This month", "Last 30 days") + years + "All time"
    }
    var selectedPeriod by rememberSaveable { mutableStateOf("This month") }
    LaunchedEffect(periods) {
        if (selectedPeriod !in periods) selectedPeriod = "All time"
    }

    val now = System.currentTimeMillis()
    val startTime = remember(selectedPeriod) {
        when (selectedPeriod) {
            "This month" -> java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.DAY_OF_MONTH, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            "Last 30 days" -> now - 30L * 24L * 60L * 60L * 1000L
            "All time" -> Long.MIN_VALUE
            else -> selectedPeriod.toIntOrNull()?.let { year ->
                java.util.Calendar.getInstance().apply {
                    clear()
                    set(year, java.util.Calendar.JANUARY, 1, 0, 0, 0)
                }.timeInMillis
            } ?: Long.MIN_VALUE
        }
    }
    val filteredEvents = remember(events, selectedPeriod, startTime) {
        events.filter { it.timestamp >= startTime && it.timestamp <= now }
    }
    val topSongs = remember(filteredEvents) {
        filteredEvents.groupBy { it.songId }
            .map { entry ->
                val id = entry.key
                val rows = entry.value
                val latest = rows.maxByOrNull { it.timestamp } ?: rows.first()
                ReplaySongSummary(
                    id = id,
                    title = latest.title,
                    artist = latest.artist,
                    // History can store a downloaded audio path here. Keep only real remote
                    // artwork URLs; local tracks resolve their embedded/MediaStore cover below.
                    thumbnailUrl = rows.asSequence()
                        .sortedByDescending { it.timestamp }
                        .mapNotNull { it.thumbnailUrl?.takeIf { url ->
                            url.startsWith("http://", ignoreCase = true) ||
                                url.startsWith("https://", ignoreCase = true)
                        } }
                        .firstOrNull(),
                    totalMs = rows.sumOf { it.duration.coerceAtLeast(0L) },
                    sessions = rows.size,
                    lastTimestamp = rows.maxOfOrNull { it.timestamp } ?: latest.timestamp
                )
            }
            .sortedByDescending { it.totalMs }
            .take(100)
    }
    val topArtists = remember(filteredEvents) {
        filteredEvents.groupBy { it.artist.ifBlank { "Unknown Artist" } }
            .map { entry ->
                ReplayArtistSummary(
                    name = entry.key,
                    totalMs = entry.value.sumOf { it.duration.coerceAtLeast(0L) },
                    sessions = entry.value.size
                )
            }
            .sortedByDescending { it.totalMs }
            .take(8)
    }
    val monthly = remember(filteredEvents) {
        val monthFormatter = java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault())
        filteredEvents.groupBy { event ->
            val calendar = java.util.Calendar.getInstance().apply { timeInMillis = event.timestamp }
            "%04d-%02d".format(
                java.util.Locale.ROOT,
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH) + 1
            )
        }.toList()
            .sortedBy { it.first }
            .takeLast(8)
            .map { entry ->
                val timestamp = entry.second.minOfOrNull { it.timestamp } ?: 0L
                ReplayMonthSummary(
                    label = monthFormatter.format(java.util.Date(timestamp)),
                    totalMs = entry.second.sumOf { it.duration.coerceAtLeast(0L) }
                )
            }
    }
    val totalMs = remember(filteredEvents) {
        filteredEvents.sumOf { it.duration.coerceAtLeast(0L) }
    }
    val activeDays = remember(filteredEvents) {
        filteredEvents.map { event ->
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
                .format(java.util.Date(event.timestamp))
        }.distinct().size
    }
    val peakHour = remember(filteredEvents) {
        filteredEvents.groupBy { it.hourOfDay.coerceIn(0, 23) }
            .maxByOrNull { it.value.size }?.key
    }
    val peakHabit = when (peakHour ?: -1) {
        in 5..11 -> "Morning Spark"
        in 12..16 -> "Afternoon Groove"
        in 17..21 -> "Evening Harmony"
        -1 -> "Waiting for data"
        else -> "Midnight Mystique"
    }
    val totalMinutes = totalMs / 60_000L
    val totalListenText = if (totalMinutes >= 60L) {
        (totalMinutes / 60L).toString() + "h " + (totalMinutes % 60L).toString() + "m"
    } else {
        totalMinutes.toString() + " min"
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(topArtists) {
        topArtists.forEach { artist -> viewModel.resolveArtistProfileImage(artist.name) }
    }

    fun toSongItem(summary: ReplaySongSummary): SongItem {
        // Online downloads retain their YouTube ID, while device tracks use the
        // MediaStore ID. Resolve either form to the scanned SongItem to preserve
        // its real file path, album ID, embedded cover and downloaded-source metadata.
        val localSong = localSongs.firstOrNull {
            !it.videoId.isNullOrBlank() && it.videoId == summary.id
        } ?: summary.id.toLongOrNull()?.let { localId ->
            localSongs.firstOrNull { it.id == localId }
        }
        if (localSong != null) {
            return localSong.withPlaybackSource(
                uri = localSong.contentUri,
                cached = localSong.isCachedTrack,
                downloaded = localSong.isDownloadedOffline
            ).apply {
                if (thumbnailUrl.isNullOrBlank()) {
                    thumbnailUrl = summary.thumbnailUrl
                }
            }
        }
        val remoteArtwork = summary.thumbnailUrl?.takeIf {
            it.startsWith("http://", ignoreCase = true) ||
                it.startsWith("https://", ignoreCase = true)
        }
        val localId = summary.id.toLongOrNull()
        return if (localId != null) {
            SongItem(
                localId, summary.title, summary.artist, -1L, null,
                summary.totalMs, summary.lastTimestamp
            ).apply { thumbnailUrl = remoteArtwork }
        } else {
            SongItem.createOnlineSong(
                videoId = summary.id, title = summary.title, artist = summary.artist,
                streamUrl = null, durationMs = summary.totalMs, thumbnailUrl = remoteArtwork
            )
        }
    }

    fun playReplay(shuffle: Boolean) {
        val queue = topSongs.map { summary -> toSongItem(summary) }
        if (queue.isEmpty()) return
        viewModel.setShuffleEnabled(shuffle)
        viewModel.setSongList(queue)
        viewModel.playSongAtIndex(0)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .nestedScroll(nestedScrollConnection),
        contentPadding = PaddingValues(bottom = 176.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "replay-header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DsmusicIconButton(onClick = onNavigateToExplore) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to Explore"
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Replay",
                        style = DsmusicTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Your listening, recapped on this device",
                        style = DsmusicTheme.typography.bodyMedium,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item(key = "replay-periods") {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                periods.forEach { period ->
                    DsmusicFilterChip(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        label = { Text(period) },
                        border = null
                    )
                }
            }
        }

        if (filteredEvents.isEmpty()) {
            item(key = "replay-empty") {
                Box(
                    Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.BarChart,
                            contentDescription = null,
                            tint = DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Nothing to replay yet", fontWeight = FontWeight.Bold)
                        Text(
                            "Songs you play will appear here with listening time, top artists and your most-played tracks.",
                            textAlign = TextAlign.Center,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant,
                            style = DsmusicTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            item(key = "replay-hero") {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    DsmusicTheme.colorScheme.primaryContainer,
                                    DsmusicTheme.colorScheme.tertiaryContainer,
                                    DsmusicTheme.colorScheme.secondaryContainer
                                )
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            selectedPeriod.uppercase(),
                            style = DsmusicTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            totalListenText,
                            style = DsmusicTheme.typography.displayMedium,
                            fontWeight = FontWeight.Black,
                            color = DsmusicTheme.colorScheme.onSurface
                        )
                        Text(
                            "listened",
                            style = DsmusicTheme.typography.titleMedium,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                            Column {
                                Text(filteredEvents.size.toString(), fontWeight = FontWeight.Bold, style = DsmusicTheme.typography.titleLarge)
                                Text("plays", style = DsmusicTheme.typography.labelSmall, color = DsmusicTheme.colorScheme.onSurfaceVariant)
                            }
                            Column {
                                Text(topSongs.size.toString(), fontWeight = FontWeight.Bold, style = DsmusicTheme.typography.titleLarge)
                                Text("songs", style = DsmusicTheme.typography.labelSmall, color = DsmusicTheme.colorScheme.onSurfaceVariant)
                            }
                            Column {
                                Text(activeDays.toString(), fontWeight = FontWeight.Bold, style = DsmusicTheme.typography.titleLarge)
                                Text("active days", style = DsmusicTheme.typography.labelSmall, color = DsmusicTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            "Peak listening time · " + peakHabit,
                            style = DsmusicTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = DsmusicTheme.colorScheme.onSurface
                        )
                        topSongs.firstOrNull()?.let { first ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                    .background(DsmusicTheme.colorScheme.surface.copy(alpha = 0.42f))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!first.thumbnailUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = libraryArtworkRequestBuilder(context, first.thumbnailUrl).crossfade(false).build(),
                                        contentDescription = null,
                                        modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        Modifier.size(54.dp).clip(RoundedCornerShape(10.dp))
                                            .background(DsmusicTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) { Icon(Icons.Rounded.MusicNote, null) }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Your #1 song", style = DsmusicTheme.typography.labelSmall, color = DsmusicTheme.colorScheme.onSurfaceVariant)
                                    Text(first.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(first.artist, style = DsmusicTheme.typography.bodySmall, color = DsmusicTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            item(key = "replay-actions") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DsmusicButton(
                        onClick = { playReplay(false) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = topSongs.isNotEmpty()
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                    DsmusicOutlinedButton(
                        onClick = { playReplay(true) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = topSongs.isNotEmpty()
                    ) {
                        Icon(Icons.Rounded.Shuffle, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                    if (AccountManager.isLoggedIn(context)) {
                        DsmusicIconButton(
                            onClick = {
                                val ids = topSongs.map { it.id }.filter { it.toLongOrNull() == null }.take(100)
                                if (ids.isNotEmpty()) {
                                    scope.launch {
                                        YouTubeMusic.createPlaylist(
                                            title = "Replay - " + selectedPeriod,
                                            description = "Made with Quantune Replay",
                                            privacyStatus = "PRIVATE",
                                            videoIds = ids
                                        ).onSuccess {
                                            accountViewModelRefreshReplay(context)
                                            com.dsann.music.ui.SnackbarController.showMessage("Saved Replay to YouTube Music")
                                        }.onFailure { error ->
                                            com.dsann.music.ui.SnackbarController.showMessage(
                                                error.message ?: "Could not save Replay"
                                            )
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, "Save Replay playlist")
                        }
                    }
                }
            }
            if (monthly.size > 1) {
                item(key = "replay-monthly") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Text("Listening by month", style = DsmusicTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val maximum = monthly.maxOfOrNull { it.totalMs }?.coerceAtLeast(1L) ?: 1L
                        monthly.forEach { month ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(month.label, modifier = Modifier.width(48.dp), style = DsmusicTheme.typography.labelSmall)
                                Box(
                                    Modifier.weight(1f).height(9.dp).clip(CircleShape)
                                        .background(DsmusicTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth((month.totalMs.toFloat() / maximum).coerceIn(0f, 1f))
                                            .fillMaxHeight()
                                            .background(DsmusicTheme.colorScheme.primary, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (topArtists.isNotEmpty()) {
                item(key = "replay-top-artists") {
                    Column {
                        Text(
                            "Top artists",
                            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
                            style = DsmusicTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(topArtists, key = { it.name }) { artist ->
                                Column(
                                    Modifier.width(104.dp).clickable { onArtistClick(artist.name) },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val image = viewModel.artistProfileMap[artist.name]
                                    if (!image.isNullOrBlank()) {
                                        AsyncImage(
                                            model = libraryArtworkRequestBuilder(LocalContext.current, image).crossfade(false).build(),
                                            contentDescription = null,
                                            modifier = Modifier.size(88.dp).clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            Modifier.size(88.dp).clip(CircleShape)
                                                .background(DsmusicTheme.colorScheme.secondaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.Person, null, modifier = Modifier.size(36.dp))
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(artist.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        artist.sessions.toString() + " plays",
                                        style = DsmusicTheme.typography.labelSmall,
                                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item(key = "replay-top-songs-title") {
                Text(
                    "Top songs",
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp),
                    style = DsmusicTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            itemsIndexed(topSongs, key = { _, item -> item.id }) { index, item ->
                val replaySong = remember(item.id, localSongs) { toSongItem(item) }
                val artworkCandidate = remember(replaySong.id, replaySong.thumbnailUrl, replaySong.path, replaySong.data, replaySong.contentUri) {
                    replaySong.thumbnailUrl?.takeIf { candidate ->
                        val uri = runCatching { android.net.Uri.parse(candidate) }.getOrNull()
                        val scheme = uri?.scheme?.lowercase(java.util.Locale.ROOT)
                        val uriPath = uri?.path
                        val isImageSource = scheme in setOf("http", "https", "content", "android.resource") ||
                            (scheme == "file" && uriPath != replaySong.path && uriPath != replaySong.data)
                        isImageSource && candidate != replaySong.path && candidate != replaySong.data &&
                            candidate != replaySong.contentUri.toString()
                    }
                }
                // Downloaded history can contain the audio path instead of its artwork.
                // Resolve embedded cover data off the main thread as a fallback.
                val embeddedArtwork by produceState<ByteArray?>(
                    initialValue = null, replaySong.id, replaySong.path, replaySong.data,
                    replaySong.contentUri, artworkCandidate
                ) {
                    if (artworkCandidate == null) {
                        value = withContext(Dispatchers.IO) { replaySong.getEmbeddedPicture(context) }
                    }
                }
                val albumArtwork = replaySong.albumId.takeIf { it > 0L }?.let {
                    "content://media/external/audio/albumart/$it"
                }
                val artworkModel: Any? = artworkCandidate ?: embeddedArtwork ?: albumArtwork

                Row(
                    modifier = Modifier.fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                val queue = topSongs.map { summary -> toSongItem(summary) }
                                viewModel.setShuffleEnabled(false)
                                viewModel.setSongList(queue)
                                viewModel.playSongAtIndex(index)
                            },
                            onLongClick = { onSongMenuClick(replaySong) }
                        )
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        (index + 1).toString(),
                        modifier = Modifier.width(32.dp),
                        style = DsmusicTheme.typography.titleLarge,
                        color = if (index < 3) DsmusicTheme.colorScheme.primary
                            else DsmusicTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    val isCurrentReplaySong = activePlayerSong?.let { playingSong ->
                        playingSong.id == replaySong.id ||
                            (!playingSong.videoId.isNullOrBlank() &&
                                playingSong.videoId == replaySong.videoId)
                    } == true
                    val replayCoverColor = rememberNowPlayingCoverColor(
                        artworkModel,
                        enabled = isCurrentReplaySong
                    )
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (artworkModel != null) {
                            AsyncImage(
                                model = artworkModel,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                Modifier.fillMaxSize()
                                    .background(DsmusicTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Rounded.MusicNote, null) }
                        }
                        if (isCurrentReplaySong) {
                            NowPlayingCoverIndicator(
                                isPlaying = true,
                                coverColor = replayCoverColor,
                                isAnimating = playerIsPlaying,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.artist, style = DsmusicTheme.typography.bodySmall, color = DsmusicTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text((item.totalMs / 60_000L).toString() + " min", style = DsmusicTheme.typography.labelMedium)
                        Text(item.sessions.toString() + " plays", style = DsmusicTheme.typography.labelSmall, color = DsmusicTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun accountViewModelRefreshReplay(context: Context) {
    val activity = getActivityFromContext(context) as? androidx.fragment.app.FragmentActivity ?: return
    ViewModelProvider(activity)[AccountViewModel::class.java].loadLibrary()
}


private fun isDsmusicDownloadedSong(song: SongItem): Boolean {
    if (isDsmusicDownloadedLocation(song.mediaStoreRelativePath, song.path ?: song.data)) return true
    val relativePath = song.mediaStoreRelativePath.orEmpty().trim('/').lowercase(java.util.Locale.ROOT)
    val rawPath = (song.path ?: song.data).orEmpty().replace('\\', '/')
    val filename = rawPath.substringAfterLast('/')
    val inPublicMusicArea = relativePath.startsWith("music/") ||
        relativePath.startsWith("download/") || relativePath.startsWith("downloads/") ||
        rawPath.contains("/Music/", ignoreCase = true) || rawPath.contains("/Download/", ignoreCase = true)
    if (!inPublicMusicArea || filename.isBlank()) return false
    val expectedBaseName =
        "${com.dsann.music.util.SongDownloader.cleanTitle(song.title)} - " +
            com.dsann.music.util.SongDownloader.cleanArtist(song.artist)
    val expectedFilename = expectedBaseName.replace(Regex("[^a-zA-Z0-9._\\s()\\[\\]-]"), "_")
    return filename.substringBeforeLast('.', "").equals(expectedFilename, ignoreCase = true)
}

private fun isDsmusicDownloadedLocation(relativePathValue: String?, rawPath: String?): Boolean {
    val relativePath = relativePathValue.orEmpty().trim('/').lowercase(java.util.Locale.ROOT)
    val appFolders = listOf(
        "music/quantune", "download/quantune", "downloads/quantune",
        "music/d.s music", "download/d.s music", "downloads/d.s music"
    )
    if (appFolders.any { relativePath == it || relativePath.startsWith("$it/") }) return true

    val path = rawPath.orEmpty().replace('\\', '/').lowercase(java.util.Locale.ROOT)
    return path.contains("/music/quantune/") ||
        path.contains("/download/quantune/") ||
        path.contains("/downloads/quantune/") ||
        path.contains("/music/d.s music/") ||
        path.contains("/download/d.s music/") ||
        path.contains("/downloads/d.s music/")
}

// A single scanner serves Library and Local Music. Repeated refresh signals reuse the
// in-flight job instead of launching overlapping MediaStore queries and metadata reads.
private object LocalMusicScanCoordinator {
    private data class ScanEntry(val owner: String, val job: Job)

    private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scanMutex = Mutex()
    private val lock = Any()
    private val scanJobs = WeakHashMap<PlayerSharedViewModel, ScanEntry>()
    private val completedScans = WeakHashMap<PlayerSharedViewModel, Boolean>()

    suspend fun scan(
        context: Context,
        viewModel: PlayerSharedViewModel,
        forceRefresh: Boolean,
        owner: String
    ) {
        val job = synchronized(lock) {
            if (!forceRefresh && completedScans[viewModel] == true) {
                return@synchronized null
            }

            val activeEntry = scanJobs[viewModel]
            val activeJob = activeEntry?.job?.takeIf { it.isActive }
            if (activeJob != null && activeEntry?.owner == owner && !forceRefresh) {
                return@synchronized activeJob
            }
            // A manual refresh or a different active screen supersedes the older query.
            // The mutex ensures the new pass starts only after the old cursor is closed.
            activeJob?.cancel()
            if (forceRefresh) completedScans.remove(viewModel)

            lateinit var scanJob: Job
            scanJob = coordinatorScope.launch(start = CoroutineStart.LAZY) {
                try {
                    val completed = scanMutex.withLock {
                        performLocalFilesScan(context.applicationContext, viewModel)
                    }
                    if (completed) {
                        synchronized(lock) {
                            if (scanJobs[viewModel]?.job === scanJob) {
                                completedScans[viewModel] = true
                            }
                        }
                    }
                } finally {
                    synchronized(lock) {
                        if (scanJobs[viewModel]?.job === scanJob) {
                            scanJobs.remove(viewModel)
                        }
                    }
                }
            }
            scanJobs[viewModel] = ScanEntry(owner, scanJob)
            scanJob.start()
            scanJob
        }
        job?.join()
    }

    fun cancel(viewModel: PlayerSharedViewModel, owner: String? = null) {
        val job = synchronized(lock) {
            scanJobs[viewModel]
                ?.takeIf { owner == null || it.owner == owner }
                ?.job
        }
        job?.cancel()
    }
}

internal suspend fun scanLocalFiles(
    context: Context,
    viewModel: PlayerSharedViewModel,
    forceRefresh: Boolean = true,
    owner: String = "library"
) {
    LocalMusicScanCoordinator.scan(context, viewModel, forceRefresh, owner)
}

internal fun cancelLocalMusicScan(viewModel: PlayerSharedViewModel, owner: String? = null) {
    LocalMusicScanCoordinator.cancel(viewModel, owner)
}

private suspend fun performLocalFilesScan(context: Context, viewModel: PlayerSharedViewModel): Boolean =
    withContext(Dispatchers.IO) {
        val songs = ArrayList<SongItem>()
        try {
            currentCoroutineContext().ensureActive()
            val projection = mutableListOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.TITLE,
                android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
                android.provider.MediaStore.Audio.Media.ARTIST,
                android.provider.MediaStore.Audio.Media.IS_MUSIC,
                android.provider.MediaStore.Audio.Media.ALBUM_ID,
                android.provider.MediaStore.Audio.Media.ALBUM,
                android.provider.MediaStore.Audio.Media.DATA,
                android.provider.MediaStore.Audio.Media.DURATION,
                android.provider.MediaStore.Audio.Media.DATE_ADDED
            ).apply {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    add(android.provider.MediaStore.Audio.Media.RELATIVE_PATH)
                }
            }.toTypedArray()
            val prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            val downloadedVideoIdsByUri =
                com.dsann.music.util.SongDownloader.getDownloadedVideoIdsByUri(context)
            val hiddenFolders = (prefs.getString("hidden_folders", "") ?: "")
                .split("||")
                .filter { it.isNotEmpty() }
            val ignoreShort = prefs.getBoolean("ignore_short_audio", false)
            val minDurationMs = if (ignoreShort) {
                prefs.getFloat("ignore_short_audio_duration", 30f).toLong() * 1000L
            } else {
                0L
            }

            val cursor = context.contentResolver.query(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${android.provider.MediaStore.Audio.Media.TITLE} ASC"
            ) ?: return@withContext false

            cursor.use { c ->
                val idCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
                val displayNameCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DISPLAY_NAME)
                val artistCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
                val isMusicCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.IS_MUSIC)
                val albumCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM_ID)
                val albumNameCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM)
                val dataCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
                val relativePathCol = c.getColumnIndex("relative_path")
                val durationCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)
                val dateCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATE_ADDED)

                while (c.moveToNext()) {
                    // A large library can take noticeable time to walk on Android 6.
                    // Check every row and around the optional embedded-tag read so leaving
                    // the tab releases the cursor and stops further file work promptly.
                    currentCoroutineContext().ensureActive()

                    val path = c.getString(dataCol)
                    val relativePath = if (relativePathCol >= 0) c.getString(relativePathCol) else null
                    val isDownloadedFile = isDsmusicDownloadedLocation(relativePath, path)
                    if (!isDownloadedFile && c.getInt(isMusicCol) == 0) continue
                    if (!isDownloadedFile && path != null && hiddenFolders.any { path.startsWith(it) }) continue

                    val duration = c.getLong(durationCol)
                    if (!isDownloadedFile && ignoreShort && duration < minDurationMs) continue

                    val displayName = c.getString(displayNameCol).orEmpty().ifBlank {
                        path?.substringAfterLast('/') ?: ""
                    }
                    val baseName = displayName.substringBeforeLast('.', displayName)
                    val nameSeparator = baseName.lastIndexOf(" - ")
                    val fallbackTitle = if (nameSeparator > 0) {
                        baseName.substring(0, nameSeparator).trim()
                    } else {
                        baseName
                    }
                    val fallbackArtist = if (nameSeparator > 0) {
                        baseName.substring(nameSeparator + 3).trim()
                    } else {
                        ""
                    }
                    val mediaId = c.getLong(idCol)
                    val mediaUri = android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    )

                    val downloadExtension = displayName.substringAfterLast('.', "")
                        .lowercase(java.util.Locale.ROOT)
                    val hasReadableDownloadedMetadata = downloadExtension in setOf(
                        "mp3", "m4a", "mp4", "m4b", "m4p",
                        "opus", "ogg", "flac", "aac", "wav", "mka", "webm"
                    )
                    val embeddedMetadata = if (isDownloadedFile && hasReadableDownloadedMetadata) {
                        com.dsann.music.util.DownloadedAudioMetadataReader.read(
                            context = context,
                            filePath = path,
                            mediaUri = mediaUri,
                            cacheKey = "media-$mediaId"
                        )
                    } else {
                        null
                    }
                    currentCoroutineContext().ensureActive()

                    val storedTitle = c.getString(titleCol).orEmpty()
                    val storedArtist = c.getString(artistCol).orEmpty()
                    val storedTitleIsUseful = storedTitle.isNotBlank() &&
                        !storedTitle.equals("<unknown>", ignoreCase = true) &&
                        !storedTitle.equals("unknown title", ignoreCase = true) &&
                        !storedTitle.equals(baseName, ignoreCase = true)
                    val title = when {
                        storedTitleIsUseful -> storedTitle
                        !embeddedMetadata?.title.isNullOrBlank() -> embeddedMetadata!!.title!!
                        storedTitle.isNotBlank() && !storedTitle.equals("<unknown>", ignoreCase = true) -> storedTitle
                        fallbackTitle.isNotBlank() -> fallbackTitle
                        else -> "Unknown Title"
                    }

                    val storedArtistIsUseful = storedArtist.isNotBlank() &&
                        !storedArtist.equals("<unknown>", ignoreCase = true) &&
                        !storedArtist.equals("unknown artist", ignoreCase = true)
                    val artist = when {
                        storedArtistIsUseful -> storedArtist
                        !embeddedMetadata?.artist.isNullOrBlank() -> embeddedMetadata!!.artist!!
                        fallbackArtist.isNotBlank() -> fallbackArtist
                        storedArtist.isNotBlank() -> storedArtist
                        else -> "Unknown Artist"
                    }
                    val mediaStoreAlbum = c.getString(albumNameCol)?.takeIf {
                        it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true)
                    }

                    songs.add(
                        SongItem(
                            mediaId,
                            title,
                            artist,
                            c.getLong(albumCol),
                            path,
                            duration,
                            c.getLong(dateCol)
                        ).apply {
                            mediaStoreRelativePath = relativePath
                            albumName = mediaStoreAlbum ?: embeddedMetadata?.album
                            val sourceVideoId = downloadedVideoIdsByUri[mediaUri.toString()]
                            if (!sourceVideoId.isNullOrBlank()) {
                                videoId = sourceVideoId
                                isDownloadedOffline = true
                            }
                            thumbnailUrl = embeddedMetadata?.artworkUri
                                ?: sourceVideoId?.takeIf { it.isNotBlank() }?.let {
                                    "https://img.youtube.com/vi/$it/hqdefault.jpg"
                                }
                        }
                    )
                }
            }

            // Never publish a partial list if this scan was interrupted or failed.
            currentCoroutineContext().ensureActive()
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                viewModel.setLocalSongs(songs)
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.e("LocalMusicScanner", "MediaStore scan failed; retaining the previous library snapshot", error)
            false
        }
    }

private fun getActivityFromContext(context: Context): Activity? {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun navigateToExplore(activity: Activity?) {
    (activity as? MainActivity)?.showArtistPage(null, null)
}


private data class CachedSongEntry(
    val songId: String,
    val title: String,
    val artist: String,
    val albumName: String,
    val videoId: String,
    val durationMs: Long,
    val cachedUri: String,
    val dateAddedMs: Long = 0L,
    val cacheFileModifiedAtMs: Long = 0L
)

private val attemptedCacheTitleRepairs =
    java.util.Collections.synchronizedSet(mutableSetOf<String>())

private fun isGenericCachedSongTitle(value: String?): Boolean {
    val title = value?.trim().orEmpty()
    return title.isBlank() ||
        title.equals("Quantune", ignoreCase = true) ||
        title.equals("D.S Music", ignoreCase = true) ||
        title.equals("Unknown Title", ignoreCase = true) ||
        title.equals("<unknown>", ignoreCase = true) ||
        title.equals("Unknown", ignoreCase = true) ||
        title.equals("Now Playing", ignoreCase = true)
}

/**
 * Repairs Cache entries written by older builds with the app name as their track title.
 * The video ID is already stored with the cached stream, so metadata can be recovered
 * without re-downloading the audio itself.
 */
private suspend fun repairCachedSongTitle(
    context: Context,
    entry: CachedSongEntry
): Boolean {
    val videoId = entry.videoId.takeIf { it.isNotBlank() } ?: return false
    if (!attemptedCacheTitleRepairs.add(videoId)) return false

    val result = runCatching {
        YouTubeMusic.player(videoId, metadataOnly = true).getOrNull()
    }.getOrNull() ?: return false

    val repairedTitle = result.title.trim()
    if (isGenericCachedSongTitle(repairedTitle)) return false

    AudioPlaybackService.rememberMediaCacheMetadata(
        context = context,
        cacheKey = entry.cachedUri,
        title = repairedTitle,
        artist = result.artist.ifBlank { entry.artist },
        songId = entry.songId,
        durationMs = entry.durationMs.takeIf { it > 0L } ?: result.durationMs,
        videoId = videoId,
        albumName = entry.albumName,
        thumbnailUrl = ""
    )
    return true
}

private data class CacheTabSnapshot(
    val entries: List<CachedSongEntry>
)

private suspend fun readCacheTabSnapshot(context: Context): CacheTabSnapshot = withContext(Dispatchers.IO) {
    val entriesBySong = LinkedHashMap<String, Pair<CachedSongEntry, Long>>()

    // AudioPlaybackService performs one synchronized pass over SimpleCache and reads
    // metadata through one SharedPreferences instance. Artwork and lyrics can load
    // independently; neither should delay or hide a fully cached song in this playlist.
    AudioPlaybackService.getCompleteCachedMediaEntries(context).forEach { cached ->
        val metadata = cached.metadata
        val entry = CachedSongEntry(
            songId = metadata.songId,
            title = metadata.title.takeUnless(::isGenericCachedSongTitle) ?: "Unknown Title",
            artist = metadata.artist.ifBlank { "Unknown artist" },
            albumName = metadata.albumName,
            videoId = metadata.videoId,
            durationMs = metadata.durationMs,
            cachedUri = cached.cacheKey,
            // New entries use the saved cache date. Older cache metadata did not
            // always contain one, so fall back to the actual cached chunk file date.
            dateAddedMs = metadata.cachedAtMs.takeIf { it > 0L }
                ?: cached.cacheFileModifiedAtMs,
            cacheFileModifiedAtMs = cached.cacheFileModifiedAtMs
        )
        val existing = entriesBySong[metadata.songId]
        if (existing == null || cached.cachedBytes > existing.second) {
            entriesBySong[metadata.songId] = entry to cached.cachedBytes
        }
    }

    CacheTabSnapshot(
        entries = entriesBySong.values.map { it.first }.sortedBy { it.title.lowercase() }
    )
}


@Composable
private fun CachedSongListItem(
    song: SongItem,
    isCurrentSong: Boolean,
    isFavorite: Boolean,
    isSelecting: Boolean,
    isSelected: Boolean,
    localIndex: Int,
    localCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // Cache is a real playlist: reuse the same borderless song-row treatment as Downloads.
    // The standard row only outlines a selected or currently playing track.
    SongListItem(
        song = song,
        isPlaying = isCurrentSong,
        isFavorite = isFavorite,
        localIndex = localIndex,
        localCount = localCount,
        onClick = onClick,
        onLongClick = onLongClick,
        selectionMode = isSelecting,
        isSelected = isSelected
    )
}

@Composable
private fun CacheSelectionControls(
    isSelecting: Boolean,
    selectedCount: Int,
    totalCount: Int,
    menuExpanded: Boolean,
    onStartSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onRemoveSelected: () -> Unit,
    onClearSelection: () -> Unit,
    onDone: () -> Unit,
    menuContentDescription: String = "Selected cache actions",
    modifier: Modifier = Modifier
) {
    if (!isSelecting) {
        DsmusicTextButton(onClick = onStartSelection, modifier = modifier) {
            Text("Select songs")
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "$selectedCount selected",
                style = DsmusicTheme.typography.labelLarge,
                color = DsmusicTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DsmusicTextButton(onClick = onSelectAll, modifier = Modifier.weight(1f)) {
                    Text(if (selectedCount == totalCount) "All selected" else "Select all")
                }
                Box {
                    DsmusicIconButton(onClick = onOpenMenu) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = menuContentDescription)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = onDismissMenu
                    ) {
                        DropdownMenuItem(text = { Text("Play next") }, onClick = onPlayNext)
                        DropdownMenuItem(text = { Text("Add to queue") }, onClick = onAddToQueue)
                        DropdownMenuItem(text = { Text("Remove selected") }, onClick = onRemoveSelected)
                        DropdownMenuItem(text = { Text("Clear selection") }, onClick = onClearSelection)
                    }
                }
                DsmusicTextButton(onClick = onDone) { Text("Done") }
            }
        }
    }
}



@OptIn(ExperimentalFoundationApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun StreamCacheTabContent(
    context: Context,
    viewModel: PlayerSharedViewModel,
    exploreViewModel: ExploreViewModel?,
    lyricsCache: LyricsCacheManager,
    onSongLongClick: (CachedSongEntry) -> Unit,
    onBack: () -> Unit,
    bottomContentPadding: Dp = 176.dp,
    modifier: Modifier = Modifier
) {
    var cacheEntries by remember { mutableStateOf(emptyList<CachedSongEntry>()) }
    var isSelectingCache by remember { mutableStateOf(false) }
    var selectedCacheSongIds by remember { mutableStateOf(setOf<String>()) }
    var cacheSelectionMenuExpanded by remember { mutableStateOf(false) }
    var cacheOptionsExpanded by remember { mutableStateOf(false) }
    var cacheSortMenuExpanded by remember { mutableStateOf(false) }
    var cacheSortAscending by rememberSaveable { mutableStateOf(true) }
    var isCacheSearchVisible by rememberSaveable { mutableStateOf(false) }
    var cacheSearchQuery by rememberSaveable { mutableStateOf("") }
    var cacheSortOrder by rememberSaveable { mutableStateOf(SortOrder.A_Z) }

    val scope = rememberCoroutineScope()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteSongIds.collectAsStateWithLifecycle(emptySet())
    val mediaCacheRevision by AudioPlaybackService.mediaCacheRevision.collectAsStateWithLifecycle()
    val rootTabActive = LocalRootTabActive.current
    val isLandscapeLayout = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Poll only while the Library root tab is active. Cache stays in the root content
    // viewport, above the persistent navigation and mini-player overlays.
    LaunchedEffect(context, lyricsCache, rootTabActive, mediaCacheRevision) {
        if (!rootTabActive) return@LaunchedEffect
        var snapshot = readCacheTabSnapshot(context).entries
        cacheEntries = snapshot

        // Repair legacy entries in the background. The cached audio bytes are untouched;
        // only the saved display metadata is corrected from the associated video ID.
        launch {
            var repairedAnyTitle = false
            snapshot.filter { it.title == "Unknown Title" && it.videoId.isNotBlank() }
                .forEach { entry ->
                    if (repairCachedSongTitle(context, entry)) {
                        repairedAnyTitle = true
                    }
                }
            if (repairedAnyTitle) {
                val repairedSnapshot = readCacheTabSnapshot(context).entries
                snapshot = repairedSnapshot
                cacheEntries = repairedSnapshot
            }
        }

        while (true) {
            delay(10_000L)
            val updated = readCacheTabSnapshot(context).entries
            if (updated != snapshot) {
                snapshot = updated
                cacheEntries = updated
                selectedCacheSongIds = selectedCacheSongIds.intersect(updated.map { it.songId }.toSet())
            }
        }
    }

    val cacheQueue = remember(cacheEntries, context) {
        cacheEntries.map { item ->
            SongItem(
                item.songId.toLongOrNull() ?: item.videoId.hashCode().toLong(),
                item.title,
                item.artist,
                -1L,
                item.cachedUri,
                item.durationMs,
                System.currentTimeMillis()
            ).apply {
                videoId = item.videoId.takeIf { it.isNotBlank() }
                isCachedTrack = true
                isDownloadedOffline = false
                contentUri = android.net.Uri.parse(item.cachedUri)
                thumbnailUrl = android.net.Uri.fromFile(
                    AudioPlaybackService.getCachedMediaArtworkFile(context, item.songId)
                ).toString()
            }
        }
    }
    val cacheQueueByUri = remember(cacheQueue) {
        cacheQueue.associateBy { it.contentUri.toString() }
    }

    val filteredCacheEntries by produceState(
        initialValue = emptyList<CachedSongEntry>(),
        cacheEntries,
        cacheSearchQuery,
        cacheSortOrder,
        cacheSortAscending
    ) {
        if (cacheSearchQuery.isNotBlank()) delay(120L)
        val query = cacheSearchQuery.trim()
        value = withContext(Dispatchers.Default) {
            val matching = if (query.isBlank()) {
                cacheEntries
            } else {
                cacheEntries.filter {
                    it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true)
                }
            }
            val ascendingSorted = when (cacheSortOrder) {
                SortOrder.A_Z ->
                    matching.sortedBy { it.title.lowercase() }
                SortOrder.DATE_ADDED ->
                    matching.sortedWith(
                        compareBy<CachedSongEntry> { it.dateAddedMs }
                            .thenBy { it.cacheFileModifiedAtMs }
                            .thenBy { it.title.lowercase() }
                            .thenBy { it.songId }
                    )
                SortOrder.ARTIST ->
                    matching.sortedBy { it.artist.lowercase() }
                SortOrder.DURATION ->
                    matching.sortedBy { it.durationMs }
            }
            if (cacheSortAscending) ascendingSorted else ascendingSorted.asReversed()
        }
    }
    val filteredCacheQueue = remember(filteredCacheEntries, cacheQueueByUri) {
        filteredCacheEntries.mapNotNull { cacheQueueByUri[it.cachedUri] }
    }
    val selectedCacheEntries = cacheEntries.filter { it.songId in selectedCacheSongIds }
    val selectedCacheQueue = cacheQueue.filter { queueSong ->
        selectedCacheEntries.any { it.cachedUri == queueSong.contentUri.toString() }
    }

    fun toggleCacheSelection(songId: String) {
        selectedCacheSongIds = if (songId in selectedCacheSongIds) {
            selectedCacheSongIds - songId
        } else {
            selectedCacheSongIds + songId
        }
    }

    fun startPlaylist(shuffle: Boolean) {
        if (filteredCacheQueue.isEmpty()) return
        exploreViewModel?.clearOnlinePlaybackSelection()
        val playlist = if (shuffle) filteredCacheQueue.shuffled() else filteredCacheQueue
        viewModel.setShuffleEnabled(shuffle)
        viewModel.setSongList(playlist)
        viewModel.playSongAtIndex(0)
        cacheOptionsExpanded = false
    }

    fun playSelectedNext() {
        exploreViewModel?.clearOnlinePlaybackSelection()
        selectedCacheQueue.asReversed().forEach(viewModel::addToQueueNext)
        cacheSelectionMenuExpanded = false
        selectedCacheSongIds = emptySet()
        isSelectingCache = false
        com.dsann.music.ui.SnackbarController.showMessage(
            "Added " + selectedCacheQueue.size + " cached songs to play next"
        )
    }

    fun addSelectedToQueue() {
        exploreViewModel?.clearOnlinePlaybackSelection()
        selectedCacheQueue.forEach(viewModel::addToQueue)
        cacheSelectionMenuExpanded = false
        selectedCacheSongIds = emptySet()
        isSelectingCache = false
        com.dsann.music.ui.SnackbarController.showMessage(
            "Added " + selectedCacheQueue.size + " cached songs to queue"
        )
    }

    fun removeSelectedCache() {
        cacheSelectionMenuExpanded = false
        scope.launch {
            val removedIds = selectedCacheEntries.map { it.songId }.toSet()
            val removedResources = withContext(Dispatchers.IO) {
                selectedCacheEntries.sumOf { entry ->
                    AudioPlaybackService.removeMediaCacheForSong(
                        context = context.applicationContext,
                        songId = entry.songId,
                        videoId = entry.videoId,
                        cacheUri = entry.cachedUri
                    )
                }
            }
            selectedCacheEntries.forEach { entry ->
                val track = MetadataRepair().repair(
                    entry.title,
                    entry.artist,
                    null,
                    entry.durationMs,
                    null,
                    null
                )
                lyricsCache.evict(track)
            }
            cacheEntries = cacheEntries.filterNot { it.songId in removedIds }
            selectedCacheSongIds = emptySet()
            isSelectingCache = false
            com.dsann.music.ui.SnackbarController.showMessage(
                "Removed $removedResources cached files from the selected songs"
            )
        }
    }

    val selectionControls: @Composable (Modifier) -> Unit = { controlModifier ->
        CacheSelectionControls(
            isSelecting = true,
            selectedCount = selectedCacheSongIds.size,
            totalCount = filteredCacheEntries.size,
            menuExpanded = cacheSelectionMenuExpanded,
            onStartSelection = { isSelectingCache = true },
            onSelectAll = { selectedCacheSongIds = filteredCacheEntries.map { it.songId }.toSet() },
            onOpenMenu = { cacheSelectionMenuExpanded = true },
            onDismissMenu = { cacheSelectionMenuExpanded = false },
            onPlayNext = { playSelectedNext() },
            onAddToQueue = { addSelectedToQueue() },
            onRemoveSelected = { removeSelectedCache() },
            onClearSelection = {
                cacheSelectionMenuExpanded = false
                selectedCacheSongIds = emptySet()
                isSelectingCache = false
            },
            onDone = {
                cacheSelectionMenuExpanded = false
                selectedCacheSongIds = emptySet()
                isSelectingCache = false
            },
            modifier = controlModifier
        )
    }

    val playlistActions: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DsmusicButton(
                onClick = { startPlaylist(false) },
                enabled = filteredCacheQueue.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Play all")
            }
            DsmusicOutlinedButton(
                onClick = { startPlaylist(true) },
                enabled = filteredCacheQueue.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Shuffle")
            }
        }
    }

    val cachePlaylistHero: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            LibraryGradientPlaylistHero(
                title = "Cache",
                subtitle = filteredCacheEntries.size.toString() + " songs · Quantune",
                status = "Available offline",
                artwork = filteredCacheEntries.firstOrNull()?.let {
                    AudioPlaybackService.getCachedMediaArtworkFile(context, it.songId)
                },
                modifier = Modifier.fillMaxWidth(),
                actions = playlistActions
            )
            com.dsann.music.ui.theme.HorizontalDivider()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DsmusicIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to playlists")
            }
            Spacer(Modifier.weight(1f))
            Box {
                DsmusicIconButton(onClick = { cacheOptionsExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Cache options")
                }
                DropdownMenu(
                    expanded = cacheOptionsExpanded,
                    onDismissRequest = { cacheOptionsExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Sort by") },
                        onClick = {
                            cacheOptionsExpanded = false
                            cacheSortMenuExpanded = true
                        },
                        trailingIcon = {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Search") },
                        onClick = {
                            isCacheSearchVisible = !isCacheSearchVisible
                            if (!isCacheSearchVisible) cacheSearchQuery = ""
                            cacheOptionsExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Select") },
                        onClick = {
                            isSelectingCache = true
                            cacheOptionsExpanded = false
                        }
                    )
                }
                DropdownMenu(
                    expanded = cacheSortMenuExpanded,
                    onDismissRequest = { cacheSortMenuExpanded = false }
                ) {
                    Text(
                        "SORT BY",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
                        style = DsmusicTheme.typography.labelSmall,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (order) {
                                        SortOrder.A_Z -> "A–Z"
                                        SortOrder.DATE_ADDED -> "Date added"
                                        SortOrder.ARTIST -> "Artist"
                                        SortOrder.DURATION -> "Duration"
                                    }
                                )
                            },
                            onClick = { cacheSortOrder = order },
                            trailingIcon = {
                                if (cacheSortOrder == order) Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = "Selected sort",
                                    tint = DsmusicTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                    com.dsann.music.ui.theme.HorizontalDivider()
                    Text(
                        "ORDER",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
                        style = DsmusicTheme.typography.labelSmall,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                    DropdownMenuItem(
                        text = { Text("Ascending") },
                        onClick = {
                            cacheSortAscending = true
                        },
                        trailingIcon = {
                            if (cacheSortAscending) Icon(
                                Icons.Rounded.Check,
                                contentDescription = "Ascending selected",
                                tint = DsmusicTheme.colorScheme.primary
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Descending") },
                        onClick = {
                            cacheSortAscending = false
                        },
                        trailingIcon = {
                            if (!cacheSortAscending) Icon(
                                Icons.Rounded.Check,
                                contentDescription = "Descending selected",
                                tint = DsmusicTheme.colorScheme.primary
                            )
                        }
                    )
                }
            }
        }

        if (isCacheSearchVisible) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(CircleShape)
                    .background(DsmusicTheme.colorScheme.surfaceContainerLow)
                    .border(1.dp, DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.42f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = "Search cache",
                    tint = DsmusicTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    if (cacheSearchQuery.isEmpty()) {
                        Text(
                            "Search cached songs…",
                            style = DsmusicTheme.typography.bodyMedium,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = cacheSearchQuery,
                        onValueChange = { cacheSearchQuery = it },
                        singleLine = true,
                        textStyle = DsmusicTheme.typography.bodyMedium.copy(
                            color = DsmusicTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                DsmusicIconButton(onClick = {
                    cacheSearchQuery = ""
                    isCacheSearchVisible = false
                }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close search")
                }
            }
        }

        if (isSelectingCache) {
            selectionControls(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        }

        val cacheListContent: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {

            item(key = "cache-playlist-header") {
    if (!isLandscapeLayout) cachePlaylistHero()
}
if (filteredCacheEntries.isEmpty()) {
                item(key = "cache-playlist-empty") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(42.dp),
                            tint = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                        Text("No cached songs yet", style = DsmusicTheme.typography.titleMedium)
                        Text(
                            if (cacheEntries.isEmpty()) {
                                "Cached audio, artwork and lyrics will appear here."
                            } else {
                                "No matching cached songs. Try a different search."
                            },
                            style = DsmusicTheme.typography.bodyMedium,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                itemsIndexed(filteredCacheEntries, key = { _, item -> item.songId }) { index, item ->
                    val queueSong = filteredCacheQueue.getOrNull(index)
                    val isCurrentSong = currentSong?.isCachedTrack == true &&
                        currentSong?.id == queueSong?.id &&
                        currentSong?.contentUri?.toString() == item.cachedUri
                    val isSelected = item.songId in selectedCacheSongIds
                    if (queueSong != null) {
                        CachedSongListItem(
                            song = queueSong,
                            isCurrentSong = isCurrentSong,
                            isFavorite = favoriteIds.contains(queueSong.id),
                            isSelecting = isSelectingCache,
                            isSelected = isSelected,
                            localIndex = index,
                            localCount = filteredCacheEntries.size,
                            onClick = {
                                if (isSelectingCache) {
                                    toggleCacheSelection(item.songId)
                                } else {
                                    exploreViewModel?.clearOnlinePlaybackSelection()
                                    viewModel.setSongList(filteredCacheQueue)
                                    viewModel.playSongAtIndex(index)
                                }
                            },
                            onLongClick = {
                                if (isSelectingCache) toggleCacheSelection(item.songId)
                                else onSongLongClick(item)
                            }
                        )
                    }
                }
            }
        
        }
        if (isLandscapeLayout) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = bottomContentPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    cachePlaylistHero()
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )
                LazyColumn(
                    modifier = Modifier.weight(0.58f).fillMaxHeight(),
                    contentPadding = PaddingValues(start = 8.dp, top = 12.dp, end = 8.dp, bottom = bottomContentPadding),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item(key = "cache-playlist-songs-heading") {
                        Text(
                            "Songs",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = DsmusicTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    cacheListContent()
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 0.dp, top = 16.dp, end = 0.dp, bottom = bottomContentPadding),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                cacheListContent()
            }
        }
    }
}
