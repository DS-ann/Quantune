package com.dsann.music.ui.library

import com.dsann.music.ui.LocalRootTabActive
import com.dsann.music.ui.theme.DsmusicPageHeading
import com.dsann.music.ui.theme.DsmusicCard
import com.dsann.music.ui.theme.DsmusicSurface
import com.dsann.music.ui.theme.DsmusicListItem
import com.dsann.music.ui.theme.DsmusicButton
import com.dsann.music.ui.theme.DsmusicOutlinedButton
import com.dsann.music.ui.theme.DsmusicTextButton
import com.dsann.music.ui.theme.DsmusicIconButton
import com.dsann.music.ui.theme.DsmusicFilterChip
import com.dsann.music.ui.theme.DsmusicSwitchControl
import android.os.Environment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import com.dsann.music.ui.theme.Button
import com.dsann.music.ui.theme.FilterChip
import com.dsann.music.ui.theme.FilterChipDefaults
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.IconButton
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.OutlinedButton
import com.dsann.music.ui.theme.OutlinedTextField
import com.dsann.music.ui.theme.OutlinedTextFieldDefaults
import com.dsann.music.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsann.music.model.SongItem
import com.dsann.music.ui.explore.LocalSongActionsBottomSheet
import com.dsann.music.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class LocalMusicFolder(
    val name: String,
    val path: String,
    val songCount: Int
)

private data class LocalMusicFolderListing(
    val folders: List<LocalMusicFolder> = emptyList(),
    val songs: List<SongItem> = emptyList()
)

/**
 * MediaStore's DATE_ADDED can be identical for multiple files (or unavailable on
 * older/partially indexed media). Use the filesystem timestamp as a tie-breaker
 * so Date Added remains a meaningful order instead of falling back to title order.
 * This is called from the background sorting block, never from composition.
 */
private fun localSongFileModifiedAt(song: SongItem): Long {
    val rawPath = (song.path ?: song.data).orEmpty()
    if (rawPath.isBlank()) return 0L
    return runCatching { java.io.File(rawPath).lastModified() }.getOrDefault(0L)
}

/**
 * A separate, local-only music destination.
 *
 * Every playback action builds the queue from MediaStore results, never from the online
 * Explore/library collections. Once a local track is started, next/previous stay in this
 * device-only queue until the user starts something elsewhere in the app.
 */
@Composable
fun LocalMusicScreen(viewModel: PlayerSharedViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val localSongs by viewModel.localSongs.collectAsStateWithLifecycle(emptyList())
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteSongIds.collectAsStateWithLifecycle(emptySet())

    val storageRootPath = remember {
        Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
    }
    var showAsFolders by rememberSaveable { mutableStateOf(false) }
    var currentFolderPath by rememberSaveable { mutableStateOf(storageRootPath) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortOrder by rememberSaveable { mutableStateOf(SortOrder.A_Z) }
    var sortAscending by rememberSaveable { mutableStateOf(true) }
    val sortDirectionRotation by animateFloatAsState(
        targetValue = if (sortAscending) 0f else 180f,
        animationSpec = tween(durationMillis = 220),
        label = "localMusicSortDirection"
    )
    var isScanning by remember { mutableStateOf(false) }
    var selectedLocalSongForMenu by remember { mutableStateOf<SongItem?>(null) }

    LaunchedEffect(storageRootPath) {
        if (currentFolderPath != storageRootPath &&
            !currentFolderPath.startsWith("$storageRootPath/")
        ) {
            currentFolderPath = storageRootPath
        }
    }

    val rootTabActive = LocalRootTabActive.current
    LaunchedEffect(rootTabActive) {
        if (!rootTabActive) {
            cancelLocalMusicScan(viewModel, owner = "local_music")
            return@LaunchedEffect
        }
        if (localSongs.isNotEmpty()) return@LaunchedEffect
        isScanning = true
        try {
            scanLocalFiles(context, viewModel, forceRefresh = false, owner = "local_music")
        } finally {
            isScanning = false
        }
    }

    // A large MediaStore library must not be filtered/sorted on the main thread.
    val visibleSongs by produceState(
        initialValue = emptyList<SongItem>(),
        localSongs,
        searchQuery,
        sortOrder,
        sortAscending
    ) {
        // Avoid sorting the entire MediaStore collection for every intermediate keystroke.
        if (searchQuery.isNotBlank()) delay(140L)
        val query = searchQuery.trim()
        value = withContext(Dispatchers.Default) {
            val filtered = if (query.isEmpty()) {
                localSongs
            } else {
                localSongs.filter {
                    it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true) ||
                        (it.path?.contains(query, ignoreCase = true) == true)
                }
            }
            val sortedBySelectedOrder = when (sortOrder) {
                SortOrder.A_Z -> filtered.sortedWith(
                    Comparator { first, second -> first.title.compareTo(second.title, ignoreCase = true) }
                )
                SortOrder.DATE_ADDED -> filtered.sortedWith(
                    compareBy<SongItem> { it.dateAdded }
                        .thenBy { localSongFileModifiedAt(it) }
                        .thenBy { it.id }
                )
                SortOrder.ARTIST -> filtered.sortedWith(
                    Comparator { first, second ->
                        val artistOrder = first.artist.compareTo(second.artist, ignoreCase = true)
                        if (artistOrder != 0) artistOrder
                        else first.title.compareTo(second.title, ignoreCase = true)
                    }
                )
                SortOrder.DURATION -> filtered.sortedBy { it.duration }
            }
            if (sortAscending) sortedBySelectedOrder else sortedBySelectedOrder.asReversed()
        }
    }

    val folderListing by produceState(
        initialValue = LocalMusicFolderListing(),
        visibleSongs,
        currentFolderPath,
        storageRootPath
    ) {
        value = withContext(Dispatchers.Default) {
            val storageRoot = storageRootPath.replace('\\', '/').trimEnd('/')
            val currentPathRaw = currentFolderPath.replace('\\', '/').trimEnd('/')
            val currentPath = if (
                currentPathRaw == storageRoot || currentPathRaw.startsWith("$storageRoot/")
            ) currentPathRaw else storageRoot
            val folderCounts = linkedMapOf<String, MutableSet<Long>>()
            val songsInFolder = ArrayList<SongItem>()
            val prefix = "$currentPath/"

            for (song in visibleSongs) {
                val rawPath = (song.path ?: song.data).orEmpty()
                if (rawPath.isBlank()) continue
                val path = rawPath.replace('\\', '/').trimEnd('/')
                if (!path.startsWith(prefix)) continue

                val relative = path.removePrefix(prefix)
                if (relative.isBlank()) continue
                val slash = relative.indexOf('/')
                if (slash < 0) {
                    songsInFolder.add(song)
                } else {
                    val childName = relative.substring(0, slash)
                    if (childName.isBlank()) continue
                    val childPath = "$currentPath/$childName"
                    folderCounts.getOrPut(childPath) { linkedSetOf() }.add(song.id)
                }
            }

            val folders = folderCounts.map { (path, ids) ->
                LocalMusicFolder(
                    name = path.substringAfterLast('/'),
                    path = path,
                    songCount = ids.size
                )
            }.sortedBy { it.name.lowercase() }

            LocalMusicFolderListing(folders = folders, songs = songsInFolder)
        }
    }

    val visibleSongsInView = if (showAsFolders) folderListing.songs else visibleSongs
    val contentIsEmpty = if (showAsFolders) {
        folderListing.folders.isEmpty() && folderListing.songs.isEmpty()
    } else {
        visibleSongs.isEmpty()
    }
    val parentFolderPath = remember(currentFolderPath, storageRootPath) {
        val current = currentFolderPath.trimEnd('/')
        if (current == storageRootPath) storageRootPath
        else current.substringBeforeLast('/', storageRootPath).ifBlank { storageRootPath }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // Keep the Local Music title and scan action pinned while the content below scrolls.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DsmusicPageHeading(
                title = "Local Music",
                subtitle = "${localSongs.size} songs on this device",
                modifier = Modifier.weight(1f)
            )
            DsmusicIconButton(
                enabled = !isScanning,
                onClick = {
                    scope.launch {
                        isScanning = true
                        try {
                            scanLocalFiles(context, viewModel, owner = "local_music")
                        } finally {
                            isScanning = false
                        }
                    }
                }
            ) {
                Icon(painter = painterResource(id = com.dsann.music.R.drawable.ic_refresh), contentDescription = "Refresh local music")
            }
        }

        // Search, filters, folders and songs share one scrollable area below the fixed heading.
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 176.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item(key = "local_music_search", contentType = "search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search local music") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            DsmusicIconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 8.dp),
                    shape = CircleShape,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DsmusicTheme.colorScheme.primary,
                        unfocusedBorderColor = DsmusicTheme.colorScheme.outlineVariant
                    )
                )
            }

            item(key = "local_music_actions", contentType = "actions") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DsmusicButton(
                        enabled = visibleSongsInView.isNotEmpty(),
                        onClick = {
                            viewModel.setSongList(visibleSongsInView)
                            viewModel.playSongAtIndex(0)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 2.dp))
                        Text("Play all")
                    }
                    DsmusicOutlinedButton(
                        enabled = visibleSongsInView.isNotEmpty(),
                        onClick = {
                            val shuffledLocalSongs = visibleSongsInView.shuffled()
                            viewModel.setSongList(shuffledLocalSongs)
                            viewModel.playSongAtIndex(0)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 2.dp))
                        Text("Shuffle")
                    }
                }
            }

            item(key = "local_music_filters", contentType = "filters") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DsmusicIconButton(
                        onClick = { sortAscending = !sortAscending }
                    ) {
                        Icon(
                            Icons.Rounded.ArrowUpward,
                            contentDescription = if (sortAscending) "Sort ascending" else "Sort descending",
                            modifier = Modifier.rotate(sortDirectionRotation)
                        )
                    }
                    SortOrder.entries.forEach { order ->
                        DsmusicFilterChip(
                            selected = sortOrder == order,
                            onClick = { sortOrder = order },
                            label = {
                                Text(
                                    text = order.name.replace("_", " "),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            border = null,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = DsmusicTheme.colorScheme.surfaceContainerHigh,
                                selectedContainerColor = DsmusicTheme.colorScheme.primaryContainer,
                                selectedLabelColor = DsmusicTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                    DsmusicFilterChip(
                        selected = showAsFolders,
                        onClick = {
                            showAsFolders = !showAsFolders
                            currentFolderPath = storageRootPath
                        },
                        label = { Text("Folders", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = DsmusicTheme.colorScheme.surfaceContainerHigh,
                            selectedContainerColor = DsmusicTheme.colorScheme.primaryContainer,
                            selectedLabelColor = DsmusicTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            if (contentIsEmpty) {
                item(key = "local_music_empty", contentType = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .padding(bottom = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = when {
                                    isScanning -> "Scanning this device…"
                                    searchQuery.isNotBlank() -> "No local songs match that search"
                                    else -> "No local music found"
                                },
                                color = DsmusicTheme.colorScheme.onSurfaceVariant
                            )
                            if (!isScanning && localSongs.isEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                DsmusicOutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            isScanning = true
                                            try {
                                                scanLocalFiles(context, viewModel, owner = "local_music")
                                            } finally {
                                                isScanning = false
                                            }
                                        }
                                    }
                                ) {
                                    Text("Scan device")
                                }
                            }
                        }
                    }
                }
            } else {
                if (showAsFolders && currentFolderPath != storageRootPath) {
                    item(key = "folder_parent", contentType = "folder") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { currentFolderPath = parentFolderPath }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Parent folder")
                            Column(modifier = Modifier.padding(start = 10.dp)) {
                                Text("..", fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (parentFolderPath == storageRootPath) "Internal storage" else parentFolderPath.substringAfter(storageRootPath).trimStart('/'),
                                    style = DsmusicTheme.typography.bodySmall,
                                    color = DsmusicTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (showAsFolders) {
                    items(
                        items = folderListing.folders,
                        key = { folder -> "folder:${folder.path}" },
                        contentType = { "folder" }
                    ) { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { currentFolderPath = folder.path }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Folder,
                                contentDescription = "Folder",
                                tint = DsmusicTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(
                                    text = folder.name,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${folder.songCount} songs",
                                    style = DsmusicTheme.typography.bodySmall,
                                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = "Open folder",
                                tint = DsmusicTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = visibleSongsInView,
                    key = { _, song -> "song:${song.id}" },
                    contentType = { _, _ -> "local-song" }
                ) { index, song ->
                    val isCurrentLocalSong =
                        currentSong?.videoId.isNullOrEmpty() && currentSong?.id == song.id
                    SongListItem(
                        song = song,
                        isPlaying = isCurrentLocalSong,
                        isFavorite = favoriteIds.contains(song.id),
                        localIndex = index,
                        localCount = visibleSongsInView.size,
                        onClick = {
                            viewModel.setSongList(visibleSongsInView)
                            viewModel.playSongAtIndex(index)
                        },
                        onLongClick = { selectedLocalSongForMenu = song },
                        artworkRequestSizePx = 60
                    )
                }
            }
        }
    }

    selectedLocalSongForMenu?.let { selectedSong ->
        LocalSongActionsBottomSheet(
            song = selectedSong,
            localQueue = visibleSongsInView,
            selectedSongIndex = visibleSongsInView.indexOfFirst { it.id == selectedSong.id },
            playerSharedViewModel = viewModel,
            onDismissRequest = { selectedLocalSongForMenu = null },
            onDeleteSong = { localSong ->
                scope.launch {
                    val deleted = withContext(Dispatchers.IO) {
                        val rowsDeleted = runCatching {
                            context.contentResolver.delete(localSong.contentUri, null, null)
                        }.getOrDefault(0)
                        if (rowsDeleted > 0) {
                            true
                        } else {
                            val rawPath = (localSong.path ?: localSong.data).orEmpty()
                            if (rawPath.isBlank()) {
                                false
                            } else {
                                val file = java.io.File(rawPath)
                                !file.exists() || file.delete()
                            }
                        }
                    }
                    scanLocalFiles(context, viewModel, owner = "local_music")
                    com.dsann.music.ui.SnackbarController.showMessage(
                        if (deleted) "Deleted: ${localSong.title}"
                        else "Could not delete ${localSong.title}. The file may require storage permission."
                    )
                }
            }
        )
    }
}
