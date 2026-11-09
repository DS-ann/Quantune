@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dsann.music.ui.explore

import com.dsann.music.ui.theme.DsmusicCard
import com.dsann.music.ui.theme.DsmusicSurface
import com.dsann.music.ui.theme.DsmusicListItem
import com.dsann.music.ui.theme.DsmusicButton
import com.dsann.music.ui.theme.DsmusicOutlinedButton
import com.dsann.music.ui.theme.DsmusicTextButton
import com.dsann.music.ui.theme.DsmusicIconButton
import com.dsann.music.ui.theme.DsmusicFilterChip
import com.dsann.music.ui.theme.DsmusicSwitchControl
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import com.dsann.music.ui.theme.ButtonDefaults
import com.dsann.music.ui.theme.animateWidth
import com.dsann.music.ui.theme.FilledTonalButton
import com.dsann.music.ui.theme.HorizontalDivider
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.IconButton
import com.dsann.music.ui.theme.OutlinedTextField
import com.dsann.music.ui.theme.IconButtonDefaults
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.AlertDialog
import com.dsann.music.ui.theme.DropdownMenu
import com.dsann.music.ui.theme.DropdownMenuItem
import com.dsann.music.ui.theme.Text
import com.dsann.music.ui.theme.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dsann.music.data.innertube.AlbumPage
import com.dsann.music.data.innertube.ArtistPage
import com.dsann.music.data.innertube.HomeSection
import com.dsann.music.data.innertube.OnlineAlbum
import com.dsann.music.data.innertube.OnlineArtist
import com.dsann.music.data.innertube.OnlinePlaylist
import com.dsann.music.data.innertube.OnlineSong
import com.dsann.music.data.innertube.PlaylistPage
import com.dsann.music.data.innertube.YouTubeMusic
import com.dsann.music.data.innertube.SearchItem
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.dsann.music.viewmodel.PlayerSharedViewModel


/**
 * Enhanced premium detail view for an Album using Parallax visual language.
 */
@Composable
fun AlbumDetailView(
    albumPage: AlbumPage,
    currentPlayingVideoId: String?,
    isLoadingStream: Boolean,
    isPlaybackActive: Boolean,
    onBack: () -> Unit,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onStartRadioClick: (String) -> Unit,
    onCollectionMenuClick: () -> Unit,
    bottomContentPadding: Dp = 176.dp
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    AdaptiveDetailContainer(
        isLandscape = isLandscape,
        thumbnailUrl = albumPage.album.thumbnailUrl,
        title = albumPage.album.title,
        subtitle = "Album • ${albumPage.songs.size} tracks",
        sharedElementKey = albumPage.album.browseId,
        onBack = onBack,
        bottomContentPadding = bottomContentPadding,
        landscapeHeaderOnLeft = true,
        compactCoverHeader = true,
        headerActions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    ExpressiveConnectedButtonGroup(
                        onShuffleClick = { if (albumPage.songs.isNotEmpty()) onSongClick(albumPage.songs.first(), albumPage.songs.shuffled(), 0) },
                        onRadioClick = { if (albumPage.songs.isNotEmpty()) onStartRadioClick(albumPage.songs.first().videoId) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                DsmusicIconButton(onClick = onCollectionMenuClick) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Album options")
                }
            }
        }
    ) {
        item {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        }
        itemsIndexed(
            items = albumPage.songs,
            key = { _, song -> "album-song-${song.videoId}" }
        ) { index, song ->
            SearchResultItem(
                item = SearchItem.Song(song),
                isCurrentlyPlaying = song.videoId == currentPlayingVideoId,
                isLoading = isLoadingStream && song.videoId == currentPlayingVideoId,
                isPlaybackActive = isPlaybackActive,
                onSongMenuClick = onSongMenuClick,
                onClick = { onSongClick(song, albumPage.songs, index) }
            )
        }
    }
}

/**
 * Enhanced premium detail view for a Playlist leveraging Unified Parallax system.
 */
@Composable
fun PlaylistDetailView(
    playlistPage: PlaylistPage,
    currentPlayingVideoId: String?,
    isLoadingStream: Boolean,
    isPlaybackActive: Boolean,
    onBack: () -> Unit,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onStartRadioClick: (String) -> Unit,
    onPlaylistSaved: (String, String, String) -> Unit = { _, _, _ -> },
    onPlaylistDeleted: () -> Unit = {},
    onCollectionMenuClick: () -> Unit = {},
    bottomContentPadding: Dp = 176.dp
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val scope = rememberCoroutineScope()
    var editing by remember(playlistPage.playlist.playlistId) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var editTitle by remember(playlistPage.playlist.title) { mutableStateOf(playlistPage.playlist.title) }
    var editDescription by remember(playlistPage.description) { mutableStateOf(playlistPage.description.orEmpty()) }
    var editPrivacy by remember(playlistPage.privacy) { mutableStateOf(playlistPage.privacy ?: "PRIVATE") }
    var confirmDelete by remember(playlistPage.playlist.playlistId) { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    AdaptiveDetailContainer(
        isLandscape = isLandscape,
        thumbnailUrl = playlistPage.playlist.thumbnailUrl,
        title = playlistPage.playlist.title,
        subtitle = if (playlistPage.playlist.isPodcast) {
            "Podcast • ${playlistPage.songs.size} episodes"
        } else {
            "Curated Playlist • ${playlistPage.songs.size} items"
        },
        sharedElementKey = playlistPage.playlist.playlistId,
        onBack = onBack,
        bottomContentPadding = bottomContentPadding,
        landscapeHeaderOnLeft = true,
        headerActions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    ExpressiveConnectedButtonGroup(
                        onShuffleClick = { if (playlistPage.songs.isNotEmpty()) onSongClick(playlistPage.songs.first(), playlistPage.songs.shuffled(), 0) },
                        onRadioClick = { if (playlistPage.songs.isNotEmpty()) onStartRadioClick(playlistPage.songs.first().videoId) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                DsmusicIconButton(onClick = onCollectionMenuClick) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist options")
                }
                if (playlistPage.isOwned) {
                    DsmusicIconButton(
                        onClick = {
                            editTitle = playlistPage.playlist.title
                            editDescription = playlistPage.description.orEmpty()
                            editPrivacy = playlistPage.privacy ?: "PRIVATE"
                            editing = true
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(DsmusicTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit playlist")
                    }
                    DsmusicIconButton(
                        onClick = { confirmDelete = true },
                        enabled = !deleting,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(DsmusicTheme.colorScheme.errorContainer.copy(alpha = 0.72f))
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Delete playlist",
                            tint = DsmusicTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    ) {
        item {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        }
        itemsIndexed(
            items = playlistPage.songs,
            key = { _, song -> "playlist-song-${song.videoId}" }
        ) { index, song ->
            SearchResultItem(
                item = SearchItem.Song(song),
                isCurrentlyPlaying = song.videoId == currentPlayingVideoId,
                isLoading = isLoadingStream && song.videoId == currentPlayingVideoId,
                isPlaybackActive = isPlaybackActive,
                onSongMenuClick = onSongMenuClick,
                onClick = { onSongClick(song, playlistPage.songs, index) }
            )
        }
    }

    if (editing && playlistPage.isOwned) {
        AlertDialog(
            onDismissRequest = { if (!saving) editing = false },
            title = { Text("Edit playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !saving
                    )
                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        enabled = !saving
                    )
                    Text("Who can see it", style = DsmusicTheme.typography.labelLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            "PRIVATE" to "Private · only you",
                            "UNLISTED" to "Unlisted · anyone with the link",
                            "PUBLIC" to "Public · everyone"
                        ).forEach { (value, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = !saving) { editPrivacy = value }
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                com.dsann.music.ui.theme.RadioButton(
                                    selected = editPrivacy == value,
                                    onClick = { editPrivacy = value },
                                    enabled = !saving
                                )
                                Text(label, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                DsmusicTextButton(
                    enabled = editTitle.isNotBlank() && !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            val result = YouTubeMusic.editPlaylist(
                                playlistId = playlistPage.playlist.playlistId,
                                title = editTitle.trim(),
                                description = editDescription,
                                privacyStatus = editPrivacy
                            )
                            result.onSuccess {
                                onPlaylistSaved(editTitle.trim(), editDescription, editPrivacy)
                                editing = false
                                com.dsann.music.ui.SnackbarController.showMessage("Playlist updated on YouTube Music.")
                            }.onFailure { error ->
                                com.dsann.music.ui.SnackbarController.showMessage(
                                    error.message ?: "Could not update the playlist."
                                )
                            }
                            saving = false
                        }
                    }
                ) { Text(if (saving) "Saving…" else "Save") }
            },
            dismissButton = {
                DsmusicTextButton(enabled = !saving, onClick = { editing = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmDelete && playlistPage.isOwned) {
        AlertDialog(
            onDismissRequest = { if (!deleting) confirmDelete = false },
            title = { Text("Delete playlist?") },
            text = {
                Text("Delete “${playlistPage.playlist.title}” from your YouTube Music library? This cannot be undone.")
            },
            confirmButton = {
                DsmusicTextButton(
                    enabled = !deleting,
                    onClick = {
                        if (!deleting) {
                            deleting = true
                            scope.launch {
                                YouTubeMusic.deletePlaylist(playlistPage.playlist.playlistId)
                                    .onSuccess { deleted ->
                                        if (deleted) {
                                            com.dsann.music.ui.SnackbarController.showMessage(
                                                "Playlist deleted from YouTube Music."
                                            )
                                            confirmDelete = false
                                            onPlaylistDeleted()
                                        } else {
                                            com.dsann.music.ui.SnackbarController.showMessage(
                                                "YouTube Music did not confirm playlist deletion."
                                            )
                                        }
                                    }
                                    .onFailure { error ->
                                        com.dsann.music.ui.SnackbarController.showMessage(
                                            error.message ?: "Could not delete this playlist."
                                        )
                                    }
                                deleting = false
                            }
                        }
                    }
                ) { Text(if (deleting) "Deleting…" else "Delete") }
            },
            dismissButton = {
                DsmusicTextButton(
                    enabled = !deleting,
                    onClick = { confirmDelete = false }
                ) { Text("Cancel") }
            }
        )
    }
}

/**
 * High-Fidelity Detail view for an Artist with Parallax scaling and Dynamic Content rendering.
 */
@Composable
fun ArtistDetailView(
    artistPage: ArtistPage,
    currentPlayingVideoId: String?,
    isPlaybackActive: Boolean,
    isSubscribed: Boolean = false,
    onBack: () -> Unit,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit,
    onPlaylistClick: (OnlinePlaylist) -> Unit,
    onArtistClick: (OnlineArtist) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onSubscribeClick: ((String) -> Unit)? = null,
    onStartRadioClick: ((String) -> Unit)? = null,
    onSectionClick: ((String, String?, String) -> Unit)? = null,
    bottomContentPadding: Dp = 176.dp
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val formattedSubtitle = remember(artistPage.artist.subscriberCount) {
        val raw = artistPage.artist.subscriberCount ?: ""
        if (raw.isBlank()) return@remember ""
        val parts = raw.split(Regex(pattern = " • | •|• |•| · |·|,"))
        parts.joinToString(" • ") { part ->
            val trimmed = part.trim()
            val lower = trimmed.lowercase()
            when {
                lower.contains("subscriber") || lower.contains("subscribers") || lower.contains("subs") -> {
                    trimmed.replace("subscribers", "Subscribers", ignoreCase = true)
                        .replace("subscriber", "Subscriber", ignoreCase = true)
                        .replace("subs", "Subscribers", ignoreCase = true)
                }

                lower.contains("monthly listener") || lower.contains("monthly listeners") || lower.contains(
                    "listeners"
                ) -> {
                    trimmed.replace("monthly listeners", "Monthly Listeners", ignoreCase = true)
                        .replace("monthly listener", "Monthly Listener", ignoreCase = true)
                        .replace("listeners", "Monthly Listeners", ignoreCase = true)
                }

                else -> "$trimmed Subscribers"
            }
        }
    }

    AdaptiveDetailContainer(
        isLandscape = isLandscape,
        thumbnailUrl = artistPage.artist.thumbnailUrl,
        title = artistPage.artist.title,
        subtitle = formattedSubtitle,
        isCircular = true,
        sharedElementKey = artistPage.artist.browseId,
        onBack = onBack,
        bottomContentPadding = bottomContentPadding,
        landscapeHeaderOnLeft = true,
        compactCoverHeader = true,
        headerActions = {
            // Follow/Subscribe Button
            if (onSubscribeClick != null) {
                val label = if (isSubscribed) "Subscribed" else "Subscribe"
                DsmusicButton(
                    onClick = {
                        onSubscribeClick(artistPage.artist.browseId)
                    },
                    shape = CircleShape,
                    modifier = Modifier.height(38.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isSubscribed) {
                            DsmusicTheme.colorScheme.surfaceVariant
                        } else {
                            DsmusicTheme.colorScheme.primaryContainer
                        },
                        contentColor = if (isSubscribed) {
                            DsmusicTheme.colorScheme.onSurfaceVariant
                        } else {
                            DsmusicTheme.colorScheme.onPrimaryContainer
                        }
                    )
                ) {
                    Text(
                        label,
                        fontWeight = FontWeight.Bold,
                        style = DsmusicTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Start Radio Button
            if (onStartRadioClick != null) {
                DsmusicIconButton(
                    onClick = {
                        val allSongs = artistPage.sections.flatMap { it.items }
                            .filterIsInstance<SearchItem.Song>().map { it.song }
                        if (allSongs.isNotEmpty()) onStartRadioClick(allSongs.random().videoId)
                    },
                    modifier = Modifier.size(40.dp)
                        .background(DsmusicTheme.colorScheme.secondaryContainer, CircleShape)
                ) {
                    Icon(
                        painter = painterResource(id = com.dsann.music.R.drawable.ic_radio),
                        contentDescription = "Start Radio",
                        tint = DsmusicTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
            }

            // Play All Button
            DsmusicIconButton(
                onClick = {
                    val allSongs = artistPage.sections.flatMap { it.items }
                        .filterIsInstance<SearchItem.Song>().map { it.song }
                    if (allSongs.isNotEmpty()) onSongClick(allSongs.first(), allSongs, 0)
                },
                modifier = Modifier.size(56.dp)
                    .background(DsmusicTheme.colorScheme.onBackground, CircleShape)
            ) {
                Icon(painter = painterResource(id = com.dsann.music.R.drawable.ic_play)
                    , "Play All",
                    tint = DsmusicTheme.colorScheme.background,
                    modifier = Modifier.size(28.dp))
            }
        }
    ) {
        artistPage.sections.forEach { section ->
            val isTopSongs = section.title.contains("song", ignoreCase = true)
            if (isTopSongs) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Top songs", style = DsmusicTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = DsmusicTheme.colorScheme.onBackground)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                if (section.browseEndpoint != null) {
                                    onSectionClick?.invoke(section.browseEndpoint, section.params, "Top songs")
                                } else {
                                    val songs = section.items.filterIsInstance<SearchItem.Song>().map { it.song }
                                    if (songs.isNotEmpty()) onSongClick(songs.first(), songs, 0)
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(DsmusicTheme.colorScheme.surfaceContainerHigh, CircleShape)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (section.browseEndpoint != null) "See all" else "Play all",
                                    style = DsmusicTheme.typography.labelMedium,
                                    color = DsmusicTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Rounded.ChevronRight, "See all",
                                tint = DsmusicTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                val songs = section.items.filterIsInstance<SearchItem.Song>()
                val topSongsToTake = songs.take(5)
                val columns = if (isLandscape) 2 else 1
                val songChunks = topSongsToTake.chunked(columns)

                songChunks.forEachIndexed { chunkIdx, chunk ->
                    item(key = "top-songs-row-$chunkIdx") {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            chunk.forEachIndexed { colIdx, item ->
                                val song = item.song
                                val isPlaying = isPlaybackActive && song.videoId == currentPlayingVideoId
                                val index = chunkIdx * columns + colIdx
                                Box(modifier = Modifier.weight(1f)) {
                                    ArtistTopSongItem(
                                        song = song,
                                        isPlaying = isPlaying,
                                        onClick = { onSongClick(song, songs.map { it.song }, index) },
                                        onMenuClick = { onSongMenuClick(song) }
                                    )
                                }
                            }
                            if (chunk.size < columns) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                item {
                    HomeSectionRow(
                        section = section,
                        currentPlayingVideoId = currentPlayingVideoId,
                        isPlaybackActive = isPlaybackActive,
                        onSongClick = { song ->
                            val sectionSongs = section.items.filterIsInstance<SearchItem.Song>().map { it.song }
                            val idx = sectionSongs.indexOfFirst { it.videoId == song.videoId }
                            onSongClick(song, sectionSongs, idx)
                        },
                        onAlbumClick = onAlbumClick,
                        onArtistClick = onArtistClick,
                        onPlaylistClick = onPlaylistClick,
                        onSongMenuClick = onSongMenuClick,
                        onSectionClick = { browseId, params, title ->
                            onSectionClick?.invoke(browseId, params, title)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Adaptive container that hosts both optimized 2-pane landscape layout and hero parallax portrait layout.
 */
@Composable
fun AdaptiveDetailContainer(
    isLandscape: Boolean,
    thumbnailUrl: String?,
    title: String,
    subtitle: String,
    isCircular: Boolean = false,
    sharedElementKey: String? = null,
    headerActions: @Composable (RowScope.() -> Unit)?,
    onBack: () -> Unit,
    landscapeHeaderOnLeft: Boolean = false,
    compactCoverHeader: Boolean = false,
    bottomContentPadding: Dp = 176.dp,
    content: LazyListScope.() -> Unit
) {
    val lazyListState = rememberLazyListState()

    val context = LocalContext.current
    val activity = remember(context) { context as? androidx.fragment.app.FragmentActivity }
    val playerSharedViewModel = remember(activity) {
        activity?.let { androidx.lifecycle.ViewModelProvider(it)[PlayerSharedViewModel::class.java] }
    }
    val isPlayerExpanded = playerSharedViewModel?.isPlayerExpanded?.collectAsStateWithLifecycle()?.value ?: false
    val playerExpansionFraction = playerSharedViewModel?.playerExpansionFraction?.collectAsStateWithLifecycle()?.value ?: 0f
    val isPlayerBackActive = isPlayerExpanded || playerExpansionFraction > 0.01f

    androidx.activity.compose.BackHandler(enabled = !isPlayerBackActive) {
        onBack()
    }

    Box(
        // Paint the page surface behind the system status bar, then inset all page
        // content below it. This keeps genre/playlist details aligned during transitions.
        modifier = Modifier
            .fillMaxSize()
            .background(DsmusicTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        if (isLandscape) {
        if (landscapeHeaderOnLeft) {
            // Album and playlist pages use a true 50/50 split: cover, metadata and
            // collection actions stay in the left pane while the complete song list
            // owns the right pane.
            Row(
                modifier = Modifier.fillMaxSize().background(DsmusicTheme.colorScheme.background),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 18.dp, top = 10.dp, end = 16.dp, bottom = bottomContentPadding + 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DsmusicIconButton(
                            onClick = onBack,
                            modifier = Modifier.background(DsmusicTheme.colorScheme.surfaceVariant, CircleShape),
                            shape = IconButtonDefaults.filledShape
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = DsmusicTheme.colorScheme.onSurface
                            )
                        }
                    }
                    BoxWithConstraints(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        val coverSize = minOf(maxWidth * 0.86f, maxHeight * 0.82f, 300.dp)
                        val coverShape = if (isCircular) CircleShape else RoundedCornerShape(24.dp)
                        Box(
                            modifier = Modifier
                                .size(coverSize)
                                .clip(coverShape)
                        ) {
                            AsyncImage(
                                model = rememberExploreImageRequest(thumbnailUrl, 512),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (sharedElementKey != null) {
                                            Modifier.sharedElementIfAvailable("image-$sharedElementKey")
                                        } else Modifier
                                    ),
                                contentScale = ContentScale.Crop
                            )
                            // Match the portrait hero's artwork veil while retaining the
                            // fixed two-pane landscape header and its metadata/actions.
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.26f),
                                                Color.Transparent,
                                                DsmusicTheme.colorScheme.background.copy(alpha = 0.58f)
                                            )
                                        )
                                    )
                            )
                        }
                    }
                    Text(
                        text = title,
                        style = DsmusicTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DsmusicTheme.colorScheme.onBackground,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (sharedElementKey != null) {
                                    Modifier.sharedElementIfAvailable("title-$sharedElementKey")
                                } else Modifier
                            )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = DsmusicTheme.typography.bodyMedium,
                        color = DsmusicTheme.colorScheme.primary.copy(alpha = 0.85f),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (headerActions != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            headerActions()
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                )

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(top = 18.dp, bottom = bottomContentPadding, start = 16.dp, end = 20.dp)
                ) {
                    content()
                }
            }
        } else {
        // --- LANDSCAPE TWO-PANE ---
        Box(modifier = Modifier.fillMaxSize().background(DsmusicTheme.colorScheme.background)) {
            // Keep the ambient tint without drawing a second blurred copy of the full-size cover.
            if (!thumbnailUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DsmusicTheme.colorScheme.background.copy(alpha = 0.88f))
                )
            }
            
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT: Sticky Hero (Fixed premium width to eliminate stretching)
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                        .padding(start = 16.dp, top = 24.dp, bottom = 24.dp, end = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val baseModifier = Modifier
                        .fillMaxHeight(0.85f)
                        .aspectRatio(1f)
                        .clip(if (isCircular) CircleShape else RoundedCornerShape(24.dp))

                    AsyncImage(
                        model = rememberExploreImageRequest(thumbnailUrl, 512),
                        contentDescription = null,
                        modifier = if (sharedElementKey != null) {
                            baseModifier.sharedElementIfAvailable("image-$sharedElementKey")
                        } else baseModifier,
                        contentScale = ContentScale.Crop
                    )
                }

                // RIGHT: Scrollable Info & Content (Consumes remaining width tightly)
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(top = 32.dp, bottom = bottomContentPadding, start = 8.dp, end = 24.dp)
                ) {
                    item {
                        Text(
                            text = title,
                            style = DsmusicTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = DsmusicTheme.colorScheme.onBackground,
                            modifier = if (sharedElementKey != null) {
                                Modifier.sharedElementIfAvailable("title-$sharedElementKey")
                            } else Modifier
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = DsmusicTheme.typography.titleMedium,
                            color = DsmusicTheme.colorScheme.primary.copy(alpha = 0.8f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    if (headerActions != null) {
                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                headerActions()
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                    content()
                }
            }

            // Floating Back Button (High-contrast visible)
            Box(modifier = Modifier.padding(start = 16.dp, top = 16.dp)) {
                DsmusicIconButton(
                    onClick = onBack,
                    modifier = Modifier.background(DsmusicTheme.colorScheme.surfaceVariant, CircleShape),
                    shape = IconButtonDefaults.filledShape
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack, 
                        contentDescription = "Back", 
                        tint = DsmusicTheme.colorScheme.onSurface
                    )


                }
            }
        }
        }
    } else if (compactCoverHeader) {
        // Keep cover art, title, metadata and page actions in one compact header.
        // This is lighter than the full-width parallax artwork on older devices
        // and prevents Subscribe / Details actions from becoming a detached section.
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize().background(DsmusicTheme.colorScheme.background),
            contentPadding = PaddingValues(top = 56.dp, bottom = bottomContentPadding)
        ) {
            item(key = "detail-cover-header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(DsmusicTheme.colorScheme.surfaceContainerLow)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val coverModifier = Modifier
                            .size(116.dp)
                            .clip(if (isCircular) CircleShape else RoundedCornerShape(18.dp))
                        AsyncImage(
                            model = rememberExploreImageRequest(thumbnailUrl, 384),
                            contentDescription = null,
                            modifier = if (sharedElementKey != null) {
                                coverModifier.sharedElementIfAvailable("image-$sharedElementKey")
                            } else coverModifier,
                            contentScale = ContentScale.Crop
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text(
                                text = title,
                                style = DsmusicTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = DsmusicTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = if (sharedElementKey != null) {
                                    Modifier.sharedElementIfAvailable("title-$sharedElementKey")
                                } else Modifier
                            )
                            if (subtitle.isNotBlank()) {
                                Text(
                                    text = subtitle,
                                    style = DsmusicTheme.typography.bodyMedium,
                                    color = DsmusicTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (headerActions != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            headerActions()
                        }
                    }
                }
            }
            content()
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            DsmusicIconButton(
                onClick = onBack,
                modifier = Modifier.background(
                    DsmusicTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                    CircleShape
                ),
                shape = IconButtonDefaults.filledShape
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = DsmusicTheme.colorScheme.onSurface
                )
            }
        }
    } else {
        // --- PORTRAIT: PARALLAX ---
        val density = LocalDensity.current
        val fadeDistancePx = with(density) { 240.dp.toPx() }
        val imageAlpha by remember {
            derivedStateOf {
                val offset = if (lazyListState.firstVisibleItemIndex == 0) lazyListState.firstVisibleItemScrollOffset.toFloat() else fadeDistancePx
                1f - (offset / fadeDistancePx).coerceIn(0f, 1f)
            }
        }
        
        // 1:1 scroll tracking to move elements up in perfect sync with the list (no parallax)
        val scrollOffset by remember {
            derivedStateOf {
                if (lazyListState.firstVisibleItemIndex == 0) {
                    -lazyListState.firstVisibleItemScrollOffset.toFloat()
                } else {
                    -fadeDistancePx
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(DsmusicTheme.colorScheme.background)) {
            // Retain the gradient veil but avoid a second full-width artwork draw and blur
            // pass on every scroll frame.
            if (!thumbnailUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .graphicsLayer { 
                            translationY = scrollOffset
                        }
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    DsmusicTheme.colorScheme.background.copy(alpha = 0.2f),
                                    DsmusicTheme.colorScheme.background.copy(alpha = 0.6f),
                                    DsmusicTheme.colorScheme.background
                                )
                            )
                        )
                )
            }

            // Foreground image translating 1:1 on scroll
            Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                val baseModifier = Modifier.fillMaxSize().graphicsLayer { 
                    alpha = imageAlpha 
                    translationY = scrollOffset
                }
                AsyncImage(
                    model = rememberExploreImageRequest(thumbnailUrl, 512),
                    contentDescription = null,
                    modifier = if (sharedElementKey != null) {
                        baseModifier.sharedElementIfAvailable("image-$sharedElementKey")
                    } else baseModifier,
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer { 
                            alpha = imageAlpha 
                            translationY = scrollOffset
                        }
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Transparent,
                                    DsmusicTheme.colorScheme.background
                                )
                            )
                        )
                )
            }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomContentPadding)
            ) {
                item { Spacer(modifier = Modifier.height(240.dp)) }

                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
                        Text(
                            text = title,
                            style = DsmusicTheme.typography.displayMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = DsmusicTheme.colorScheme.onBackground,
                            modifier = if (sharedElementKey != null) {
                                Modifier.sharedElementIfAvailable("title-$sharedElementKey")
                            } else Modifier
                        )
                        if (subtitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = subtitle,
                                style = DsmusicTheme.typography.titleMedium,
                                color = DsmusicTheme.colorScheme.primary.copy(alpha = 0.8f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (headerActions != null) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            headerActions()
                        }
                    }
                }

                content()
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                DsmusicIconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack, 
                        contentDescription = "Back", 
                        tint = Color.White
                    )
                }
            }
        }
    }
}
}

@Composable
fun ArtistTopSongItem(
    song: OnlineSong,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onMenuClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            AsyncImage(
                model = rememberExploreImageRequest(song.thumbnailUrl, 128),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(DsmusicTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedEqualizerBars(
                        modifier = Modifier.size(24.dp).height(16.dp),
                        color = DsmusicTheme.colorScheme.inverseOnSurface
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = DsmusicTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (isPlaying) DsmusicTheme.colorScheme.primary else DsmusicTheme.colorScheme.onBackground,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${song.artist}${song.duration?.let { " • $it" } ?: ""}",
                style = DsmusicTheme.typography.bodyMedium,
                color = DsmusicTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        DsmusicIconButton(onClick = onMenuClick) {
            Icon(Icons.Rounded.MoreVert, "More", tint = DsmusicTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Animated skeleton shown while waiting for Detail content to load.
 */
@Composable
fun DetailScreenSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize()
            .background(DsmusicTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ShimmerModifier(width = 140.dp, height = 140.dp, shape = RoundedCornerShape(16.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                ShimmerModifier(width = 160.dp, height = 24.dp, shape = RoundedCornerShape(8.dp))
                Spacer(modifier = Modifier.height(8.dp))
                ShimmerModifier(width = 100.dp, height = 16.dp, shape = RoundedCornerShape(6.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShimmerModifier(width = 90.dp, height = 36.dp, shape = RoundedCornerShape(18.dp))
                    ShimmerModifier(width = 90.dp, height = 36.dp, shape = RoundedCornerShape(18.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(6) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ShimmerModifier(width = 48.dp, height = 48.dp, shape = RoundedCornerShape(6.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        ShimmerModifier(width = 160.dp, height = 16.dp, shape = RoundedCornerShape(8.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        ShimmerModifier(width = 100.dp, height = 12.dp, shape = RoundedCornerShape(6.dp))
                    }
                }
            }
        }
    }
}

/**
 * Reusable Material 3 Expressive Button Group (Shuffle / Radio).
 * Renders standard M3 Expressive buttons utilizing native shape morphing
 * and native width animation on click/interaction.
 */

@Composable
fun ExpressiveConnectedButtonGroup(
    onShuffleClick: () -> Unit,
    onRadioClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    com.dsann.music.ui.theme.ButtonGroup(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        expandedRatio = 0.3f,
        overflowIndicator = {}
    ) {
        val scope = this
        
        customItem(
            buttonGroupContent = {
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val cornerRadius by animateDpAsState(
                    targetValue = if (isPressed) 12.dp else 28.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "ShuffleCorner"
                )
                DsmusicButton(
                    onClick = onShuffleClick,
                    modifier = with(scope) {
                        Modifier
                            .animateWidth(interactionSource)
                            .weight(1f)
                            .height(52.dp)
                    },
                    interactionSource = interactionSource,
                    shape = RoundedCornerShape(cornerRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DsmusicTheme.colorScheme.primary,
                        contentColor = DsmusicTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Shuffle", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            menuContent = {}
        )
        
        customItem(
            buttonGroupContent = {
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val cornerRadius by animateDpAsState(
                    targetValue = if (isPressed) 12.dp else 28.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "RadioCorner"
                )
                DsmusicButton(
                    onClick = onRadioClick,
                    modifier = with(scope) {
                        Modifier
                            .animateWidth(interactionSource)
                            .weight(1f)
                            .height(52.dp)
                    },
                    interactionSource = interactionSource,
                    shape = RoundedCornerShape(cornerRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DsmusicTheme.colorScheme.secondaryContainer,
                        contentColor = DsmusicTheme.colorScheme.onSecondaryContainer
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Rounded.Radio, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Radio", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            menuContent = {}
        )
    }
}

/**
 * Generic detail view for any section type (Albums, Artists, Playlists, Songs)
 */
@Composable
fun SectionDetailView(
    section: HomeSection,
    currentPlayingVideoId: String?,
    isLoadingStream: Boolean,
    isPlaybackActive: Boolean,
    onBack: () -> Unit,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit,
    onPlaylistClick: (OnlinePlaylist) -> Unit,
    onArtistClick: (OnlineArtist) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onStartRadioClick: (String) -> Unit,
    bottomContentPadding: Dp = 176.dp
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val hasSongsOnly = section.items.isNotEmpty() && section.items.all { it is SearchItem.Song }

    if (hasSongsOnly) {
        val songs = section.items.filterIsInstance<SearchItem.Song>().map { it.song }
        AdaptiveDetailContainer(
            isLandscape = isLandscape,
            thumbnailUrl = songs.firstOrNull()?.thumbnailUrl,
            title = section.title,
            subtitle = "Songs • ${songs.size} items",
            sharedElementKey = section.title,
            onBack = onBack,
            bottomContentPadding = bottomContentPadding,
            headerActions = {
                ExpressiveConnectedButtonGroup(
                    onShuffleClick = { if (songs.isNotEmpty()) onSongClick(songs.first(), songs.shuffled(), 0) },
                    onRadioClick = { if (songs.isNotEmpty()) onStartRadioClick(songs.random().videoId) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        ) {
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            }
            itemsIndexed(
                items = songs,
                key = { _, song -> "section-song-${song.videoId}" }
            ) { index, song ->
                SearchResultItem(
                    item = SearchItem.Song(song),
                    isCurrentlyPlaying = song.videoId == currentPlayingVideoId,
                    isLoading = isLoadingStream && song.videoId == currentPlayingVideoId,
                isPlaybackActive = isPlaybackActive,
                    onSongMenuClick = onSongMenuClick,
                    onClick = { onSongClick(song, songs, index) }
                )
            }
        }
    } else {
        val firstItemThumb = when (val item = section.items.firstOrNull()) {
            is SearchItem.Song -> item.song.thumbnailUrl
            is SearchItem.Album -> item.album.thumbnailUrl
            is SearchItem.Artist -> item.artist.thumbnailUrl
            is SearchItem.Playlist -> item.playlist.thumbnailUrl
            null -> null
        }

        AdaptiveDetailContainer(
            isLandscape = isLandscape,
            thumbnailUrl = firstItemThumb,
            title = section.title,
            subtitle = "Collection • ${section.items.size} items",
            sharedElementKey = section.title,
            onBack = onBack,
            bottomContentPadding = bottomContentPadding,
            headerActions = null
        ) {
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            }

            val columns = if (isLandscape) 4 else 2
            val chunked = section.items.chunked(columns)

            itemsIndexed(chunked) { _, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowItems.forEach { item ->
                        Box(modifier = Modifier.weight(1f)) {
                            HomeCarouselItem(
                                item = item,
                                isPlaying = isPlaybackActive && when (item) {
                                    is SearchItem.Song -> item.song.videoId == currentPlayingVideoId
                                    else -> false
                                },
                                onClick = {
                                    when (item) {
                                        is SearchItem.Song -> onSongClick(item.song, listOf(item.song), 0)
                                        is SearchItem.Album -> onAlbumClick(item.album)
                                        is SearchItem.Artist -> onArtistClick(item.artist)
                                        is SearchItem.Playlist -> onPlaylistClick(item.playlist)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    if (rowItems.size < columns) {
                        repeat(columns - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

