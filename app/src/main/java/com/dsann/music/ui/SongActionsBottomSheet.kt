@file:Suppress("DEPRECATION")

package com.dsann.music.ui

import com.dsann.music.ui.theme.DsmusicCard
import com.dsann.music.ui.theme.DsmusicSurface
import com.dsann.music.ui.theme.DsmusicListItem
import com.dsann.music.ui.theme.DsmusicButton
import com.dsann.music.ui.theme.DsmusicOutlinedButton
import com.dsann.music.ui.theme.DsmusicTextButton
import com.dsann.music.ui.theme.DsmusicIconButton
import com.dsann.music.ui.theme.HorizontalDivider
import com.dsann.music.ui.explore.ExploreActionSheetHeader
import com.dsann.music.ui.theme.DsmusicFilterChip
import com.dsann.music.ui.theme.DsmusicSwitchControl
import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.Formatter
import java.io.File
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Share
import com.dsann.music.ui.theme.AlertDialog
import com.dsann.music.ui.theme.Button
import com.dsann.music.ui.theme.ButtonDefaults
import com.dsann.music.ui.theme.ButtonGroupDefaults
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.IconButton
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.OutlinedTextField
import com.dsann.music.ui.theme.Surface
import com.dsann.music.ui.theme.Text
import com.dsann.music.ui.theme.TextButton
import com.dsann.music.ui.theme.ToggleButton
import com.dsann.music.ui.theme.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.Glide
import com.dsann.music.MainActivity
import com.dsann.music.R
import com.dsann.music.model.SongItem
import com.dsann.music.data.innertube.AccountManager
import com.dsann.music.data.innertube.OnlinePlaylist
import com.dsann.music.data.innertube.YouTubeMusic
import com.dsann.music.service.AudioPlaybackService
import com.dsann.music.service.CurrentAudioFormatDetails
import com.dsann.music.ui.player.OnlineStreamMetadata
import com.dsann.music.ui.player.OnlineStreamMetadataReader
import com.dsann.music.ui.theme.GoogleSansFlex
import com.dsann.music.ui.theme.DsmusicAppThemeFromPreferences
import com.dsann.music.viewmodel.PlayerSharedViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.widget.FrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SongActionsBottomSheet : BottomSheetDialogFragment() {

    private var song: SongItem? = null
    private var actionListener: ActionListener? = null
    private var showAddToQueue: Boolean = true
    private var showRemoveFromQueue: Boolean = false
    private var showDelete: Boolean = true
    private var queueSongMode: Boolean = false

    interface ActionListener {
        fun onPlayNext(song: SongItem)
        fun onAddToQueue(song: SongItem)
        fun onDelete(song: SongItem)
        fun onEdit(song: SongItem)
        fun onPlay(song: SongItem)
        fun onFavorite(song: SongItem, isFav: Boolean)
        fun onShare(song: SongItem)
    }

    companion object {
        @JvmStatic
        fun newInstance(
            song: SongItem,
            showAddToQueue: Boolean = true,
            showRemoveFromQueue: Boolean = false,
            showDelete: Boolean = true,
            queueSongMode: Boolean = false
        ): SongActionsBottomSheet {
            val fragment = SongActionsBottomSheet()
            val args = Bundle()
            args.putSerializable("song", song)
            args.putBoolean("showAddToQueue", showAddToQueue)
            args.putBoolean("showRemoveFromQueue", showRemoveFromQueue)
            args.putBoolean("showDelete", showDelete)
            args.putBoolean("queueSongMode", queueSongMode)
            fragment.arguments = args
            return fragment
        }
    }

    private lateinit var viewModel: PlayerSharedViewModel

    fun setActionListener(listener: ActionListener) {
        this.actionListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        song = arguments?.getSerializable("song") as? SongItem
        showAddToQueue = arguments?.getBoolean("showAddToQueue", true) ?: true
        showRemoveFromQueue = arguments?.getBoolean("showRemoveFromQueue", false) ?: false
        showDelete = arguments?.getBoolean("showDelete", true) ?: true
        queueSongMode = arguments?.getBoolean("queueSongMode", false) ?: false
        viewModel = ViewModelProvider(requireActivity())[PlayerSharedViewModel::class.java]
    }

    override fun onStart() {
        super.onStart()
        val sheetDialog = dialog as? BottomSheetDialog ?: return
        val sheetContainer = sheetDialog.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        // Let the Compose surface provide the only visible sheet edge. Material's
        // default shape drawable adds a pale outline around the whole dialog.
        sheetDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        sheetContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        // The queue action sheet should open as a complete action panel, not
        // stop at Material's default peek/half-expanded position.
        sheetContainer.post {
            val behavior = BottomSheetBehavior.from(sheetContainer)
            behavior.skipCollapsed = true
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
                val playerBackgroundColor by viewModel.playerBackgroundColor.collectAsStateWithLifecycle()
                DsmusicAppThemeFromPreferences(
                    dynamicAlbumColor = if (currentSong != null) playerBackgroundColor else null
                ) {
                    BottomSheetScaffoldContent(
                        song = song,
                        actionListener = actionListener,
                        viewModel = viewModel,
                        showAddToQueue = showAddToQueue,
                        showRemoveFromQueue = showRemoveFromQueue,
                        showDelete = showDelete,
                        queueSongMode = queueSongMode,
                        onDismiss = { dismiss() }
                    )
                }
            }
        }
    }
}

enum class BottomSheetTab { OPTIONS, INFO }

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun BottomSheetScaffoldContent(
    song: SongItem?,
    actionListener: SongActionsBottomSheet.ActionListener?,
    viewModel: PlayerSharedViewModel,
    showAddToQueue: Boolean = true,
    showRemoveFromQueue: Boolean = false,
    showDelete: Boolean = true,
    queueSongMode: Boolean = false,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(BottomSheetTab.OPTIONS) }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val conversionScope = rememberCoroutineScope()
    val favoritesManager = remember { com.dsann.music.util.FavoritesManager(context) }
    val handleEditSong: () -> Unit = {
        val target = song
        // Close the dialog fragment before changing the Activity navigation destination.
        // Posting the callback avoids competing fragment transactions on the same frame.
        onDismiss()
        if (target != null) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                actionListener?.onEdit(target)
            }
        }
    }
    val completedDownloads by com.dsann.music.util.SongDownloader.downloadedVideoIds
        .collectAsStateWithLifecycle(emptySet())
    var hasDownloadedOpus by remember(song?.id) { mutableStateOf(false) }
    val convertingKeys by com.dsann.music.util.OpusToMp3Converter.convertingKeys
        .collectAsStateWithLifecycle(emptySet())
    val isConvertingToMp3 = song?.let { target ->
        com.dsann.music.util.OpusToMp3Converter.conversionKeys(target).any { it in convertingKeys }
    } == true

    LaunchedEffect(queueSongMode, song?.id, song?.videoId, song?.path, completedDownloads.contains(song?.videoId), isConvertingToMp3) {
        hasDownloadedOpus = if (queueSongMode) false else song?.let { target ->
            withContext(Dispatchers.IO) {
                com.dsann.music.util.SongDownloader.findDownloadedOpusUri(context, target) != null
            }
        } ?: false
    }

    val convertToMp3: () -> Unit = {
        val target = song
        // Do not silently swallow a tap just because the asynchronous Opus-file check is stale.
        // The converter resolves the source itself and returns a useful error when it is absent.
        if (target != null && !isConvertingToMp3) {
            conversionScope.launch {
                val result = try {
                    withContext(Dispatchers.IO) {
                        com.dsann.music.util.OpusToMp3Converter.convert(context.applicationContext, target)
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    com.dsann.music.util.Mp3ConversionResult(
                        success = false,
                        message = error.message ?: "MP3 conversion failed"
                    )
                }
                if (result.success) {
                    hasDownloadedOpus = false
                    com.dsann.music.util.SongDownloader.refreshDownloadedStatus(context, target)
                }
                SnackbarController.showMessage(
                    result.message,
                    iconResId = if (result.success) R.drawable.ic_downloaded else null
                )
            }
        }
    }
    var isFavorite by remember { mutableStateOf(song?.let { favoritesManager.isFavorite(it.id) } ?: false) }
    var albumArtBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val localPlaylists by viewModel.localPlaylistsFlow.collectAsStateWithLifecycle(emptyList())

    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    LaunchedEffect(song, queueSongMode) {
        if (song != null && !queueSongMode) {
            withContext(Dispatchers.IO) {
                val embedded = song.getEmbeddedPicture(context)
                val model = embedded ?: song.getAlbumArtUri()
                if (model != null) {
                    try {
                        albumArtBitmap = Glide.with(context).asBitmap().load(model).override(256).submit().get()
                    } catch (_: Exception) { /* Fallback */ }
                }
            }
        }
    }

    val onlineVideoId = song?.videoId?.takeIf { it.isNotBlank() }
    if (showAddToPlaylistDialog && onlineVideoId != null) {
        YouTubeMusicPlaylistPickerDialog(
            videoIds = listOf(onlineVideoId),
            onPlaylistAdded = { playlistTitle ->
                showAddToPlaylistDialog = false
                onDismiss()
                SnackbarController.showMessage("Added to YouTube Music playlist: $playlistTitle")
            },
            onDismiss = { showAddToPlaylistDialog = false }
        )
    } else if (showAddToPlaylistDialog) {
        // Device-only tracks have no YouTube video ID; retain local playlist support for them.
        LocalPlaylistPickerDialog(
            playlists = localPlaylists,
            onCreateNew = {
                showCreatePlaylistDialog = true
                showAddToPlaylistDialog = false
            },
            onPlaylistSelected = { playlist ->
                if (song != null) viewModel.addSongToLocalPlaylist(playlist.id, song)
                showAddToPlaylistDialog = false
                onDismiss()
            },
            onDismiss = { showAddToPlaylistDialog = false }
        )
    }

    if (showCreatePlaylistDialog) {
        CreateLocalPlaylistDialog(
            onConfirm = { name ->
                viewModel.createLocalPlaylist(name)
                showCreatePlaylistDialog = false
                showAddToPlaylistDialog = true
            },
            onDismiss = {
                showCreatePlaylistDialog = false
                showAddToPlaylistDialog = true
            }
        )
    }

    DsmusicSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = DsmusicTheme.shapes.extraLarge, // Expressive corner radius
        color = DsmusicTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        showBorder = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (queueSongMode) 4.dp else 12.dp, bottom = 16.dp)
                .then(
                    if (isLandscape && !queueSongMode) {
                        Modifier.padding(start = 88.dp, end = 20.dp)
                    } else {
                        Modifier.padding(horizontal = if (queueSongMode) 14.dp else 20.dp)
                    }
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Expressive Handle
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(DsmusicTheme.colorScheme.outlineVariant)
            )

            Spacer(modifier = Modifier.height(if (queueSongMode) 4.dp else 24.dp))

            if (queueSongMode) {
                // Queue actions use the same compact identity header and quick-action
                // structure as the downloaded-song sheet, without online/social actions.
                ExploreActionSheetHeader(
                    thumbnailUrl = song?.getAlbumArtUri()?.toString(),
                    title = song?.title.orEmpty(),
                    subtitle = song?.artist.orEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    trailingContent = {
                        DsmusicIconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                )

                HorizontalDivider(
                    color = DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                if (selectedTab == BottomSheetTab.INFO) {
                    DsmusicTextButton(
                        onClick = { selectedTab = BottomSheetTab.OPTIONS },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Back to song actions")
                    }
                    InfoContent(song)
                } else {
                    OptionsContent(
                        song = song,
                        isFavorite = false,
                        onFavoriteChange = {},
                        actionListener = actionListener,
                        onAddToPlaylist = {},
                        showAddToQueue = false,
                        onDismiss = onDismiss,
                        showDelete = false,
                        showRemoveFromQueue = true,
                        hasDownloadedOpus = false,
                        isConvertingToMp3 = false,
                        showLikeAndShare = false,
                        showAddToPlaylist = false,
                        localSongMode = true,
                        showInfoAction = true,
                        onShowInfoAction = { selectedTab = BottomSheetTab.INFO }
                    )
                }
            } else if (!isLandscape) {
                // PORTRAIT: Standard Vertical Layout
                HeaderSection(song, albumArtBitmap, onEdit = handleEditSong)

                Spacer(modifier = Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            val direction = if (targetState == BottomSheetTab.INFO) 1 else -1
                            (slideInHorizontally { direction * it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -direction * it } + fadeOut())
                        },
                        label = "TabTransition"
                    ) { tab ->
                        if (tab == BottomSheetTab.OPTIONS) {
                            OptionsContent(
                                song = song,
                                isFavorite = isFavorite,
                                onFavoriteChange = { isFavorite = it },
                                actionListener = actionListener,
                                onAddToPlaylist = { showAddToPlaylistDialog = true },
                                showAddToQueue = showAddToQueue,
                                showRemoveFromQueue = showRemoveFromQueue,
                                showDelete = showDelete,
                                onDismiss = onDismiss,
                                hasDownloadedOpus = hasDownloadedOpus,
                                isConvertingToMp3 = isConvertingToMp3,
                                onConvertToMp3 = convertToMp3
                            )
                        } else {
                            InfoContent(song)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                ExpressiveTabSwitcher(selectedTab, onTabSelected = { selectedTab = it })
            } else {
                // LANDSCAPE: keep identity separate and give the tab selector
                // the wider content pane. A switcher in the 40% identity pane made
                // the OPTIONS and INFO labels collide on compact landscape phones.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(0.4f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HeaderSection(
                            song = song,
                            bitmap = albumArtBitmap,
                            onEdit = handleEditSong,
                            isLandscape = true
                        )
                    }

                    Column(
                        modifier = Modifier.weight(0.6f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ExpressiveTabSwitcher(
                            selectedTab,
                            onTabSelected = { selectedTab = it },
                            compact = true
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            AnimatedContent(
                                targetState = selectedTab,
                                transitionSpec = {
                                    val direction = if (targetState == BottomSheetTab.INFO) 1 else -1
                                    (slideInHorizontally { direction * it } + fadeIn()) togetherWith
                                    (slideOutHorizontally { -direction * it } + fadeOut())
                                },
                                label = "TabTransition"
                            ) { tab ->
                                if (tab == BottomSheetTab.OPTIONS) {
                                    OptionsContent(
                                        song = song,
                                        isFavorite = isFavorite,
                                        onFavoriteChange = { isFavorite = it },
                                        actionListener = actionListener,
                                        onAddToPlaylist = { showAddToPlaylistDialog = true },
                                        showAddToQueue = showAddToQueue,
                                        showRemoveFromQueue = showRemoveFromQueue,
                                        showDelete = showDelete,
                                        onDismiss = onDismiss,
                                        hasDownloadedOpus = hasDownloadedOpus,
                                        isConvertingToMp3 = isConvertingToMp3,
                                        onConvertToMp3 = convertToMp3
                                    )
                                } else {
                                    InfoContent(song)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongActionTile(
    modifier: Modifier,
    label: String,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .height(68.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.size(25.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Text(
            text = label,
            style = DsmusicTheme.typography.labelMedium,
            fontFamily = GoogleSansFlex,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SongActionWideTile(
    modifier: Modifier,
    label: String,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(22.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = DsmusicTheme.typography.labelLarge,
            fontFamily = GoogleSansFlex,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun OptionsContent(
    song: SongItem?,
    isFavorite: Boolean,
    onFavoriteChange: (Boolean) -> Unit,
    actionListener: SongActionsBottomSheet.ActionListener?,
    onAddToPlaylist: () -> Unit,
    showAddToQueue: Boolean = true,
    onDismiss: () -> Unit,
    showDelete: Boolean = true,
    showRemoveFromQueue: Boolean = false,
    hasDownloadedOpus: Boolean = false,
    isConvertingToMp3: Boolean = false,
    onConvertToMp3: () -> Unit = {},
    downloadedSongMode: Boolean = false,
    onDeleteDownloaded: () -> Unit = {},
    showLikeAndShare: Boolean = true,
    showAddToPlaylist: Boolean = true,
    localSongMode: Boolean = false,
    showInfoAction: Boolean = false,
    onShowInfoAction: () -> Unit = {},
    showStandaloneDelete: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "QUICK ACTIONS",
            style = DsmusicTheme.typography.labelSmall.copy(
                letterSpacing = 1.15.sp,
                fontWeight = FontWeight.Bold
            ),
            color = DsmusicTheme.colorScheme.onSurfaceVariant
        )
        ActionButtonRow1(
            song,
            isFavorite,
            onFavoriteChange,
            actionListener,
            onDismiss,
            showLikeAndShare = showLikeAndShare
        )
        ActionButtonRow2(
            song,
            actionListener,
            onDismiss,
            showAddToQueue = showAddToQueue,
            downloadedSongMode = downloadedSongMode,
            onDeleteDownloaded = onDeleteDownloaded,
            localSongMode = localSongMode
        )
        if (showAddToPlaylist) {
            DestructiveActionRow(song, actionListener, onAddToPlaylist, onDismiss, showDelete = showDelete)
        }
        if (showInfoAction) {
            SongActionWideTile(
                modifier = Modifier.fillMaxWidth(),
                label = "Info",
                containerColor = DsmusicTheme.colorScheme.surfaceVariant,
                contentColor = DsmusicTheme.colorScheme.onSurface,
                onClick = onShowInfoAction
            ) {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = null,
                    tint = DsmusicTheme.colorScheme.onSurface,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
        if (showRemoveFromQueue) {
            SongActionWideTile(
                modifier = Modifier.fillMaxWidth(),
                label = "Remove from queue",
                containerColor = DsmusicTheme.colorScheme.errorContainer,
                contentColor = DsmusicTheme.colorScheme.onErrorContainer,
                enabled = song != null,
                onClick = {
                    song?.let { actionListener?.onDelete(it) }
                    onDismiss()
                }
            ) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = DsmusicTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
        if (hasDownloadedOpus || isConvertingToMp3) {
            SongActionWideTile(
                modifier = Modifier.fillMaxWidth(),
                label = if (isConvertingToMp3) "Converting to MP3…" else "Convert Opus to MP3",
                containerColor = DsmusicTheme.colorScheme.tertiaryContainer,
                contentColor = DsmusicTheme.colorScheme.onTertiaryContainer,
                enabled = !isConvertingToMp3,
                onClick = onConvertToMp3
            ) {
                Icon(
                    Icons.Rounded.Download,
                    contentDescription = null,
                    tint = DsmusicTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
        if (showStandaloneDelete) {
            SongActionWideTile(
                modifier = Modifier.fillMaxWidth(),
                label = "Delete",
                containerColor = DsmusicTheme.colorScheme.errorContainer,
                contentColor = DsmusicTheme.colorScheme.onErrorContainer,
                enabled = song != null,
                onClick = {
                    song?.let { actionListener?.onDelete(it) }
                    onDismiss()
                }
            ) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = DsmusicTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}


@Composable
fun DestructiveActionRow(
    song: SongItem?,
    actionListener: SongActionsBottomSheet.ActionListener?,
    onAddToPlaylist: () -> Unit,
    onDismiss: () -> Unit,
    showDelete: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SongActionWideTile(
            modifier = Modifier.weight(if (showDelete) 1f else 2f),
            label = "Add to playlist",
            containerColor = DsmusicTheme.colorScheme.secondaryContainer,
            contentColor = DsmusicTheme.colorScheme.onSecondaryContainer,
            onClick = onAddToPlaylist
        ) {
            Icon(
                Icons.Rounded.PlaylistAdd,
                contentDescription = null,
                tint = DsmusicTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(21.dp)
            )
        }
        if (showDelete) {
            SongActionWideTile(
                modifier = Modifier.weight(1f),
                label = "Delete",
                containerColor = DsmusicTheme.colorScheme.errorContainer,
                contentColor = DsmusicTheme.colorScheme.onErrorContainer,
                onClick = {
                    song?.let { actionListener?.onDelete(it) }
                    onDismiss()
                }
            ) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = DsmusicTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

/**
 * Adds online tracks to playlists owned by the connected YouTube Music account.
 * Local-only tracks continue to use LocalPlaylistPickerDialog.
 */
@Composable
fun YouTubeMusicPlaylistPickerDialog(
    videoIds: List<String>,
    onPlaylistAdded: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isAccountConnected = remember(context) { AccountManager.isLoggedIn(context) }
    val safeVideoIds = remember(videoIds) {
        videoIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    var playlists by remember { mutableStateOf<List<OnlinePlaylist>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }

    suspend fun refreshPlaylists() {
        if (!isAccountConnected) return
        isLoading = true
        loadError = null
        YouTubeMusic.libraryPlaylists()
            .onSuccess { items ->
                playlists = items.filter { !it.isPodcast && it.playlistId.isNotBlank() }
            }
            .onFailure { error ->
                loadError = error.message ?: "Couldn't load YouTube Music playlists."
            }
        isLoading = false
    }

    LaunchedEffect(isAccountConnected) {
        refreshPlaylists()
    }

    fun addToExistingPlaylist(playlist: OnlinePlaylist) {
        if (isWorking || safeVideoIds.isEmpty()) return
        scope.launch {
            isWorking = true
            val result = YouTubeMusic.addToPlaylist(playlist.playlistId, safeVideoIds)
            isWorking = false
            if (result.getOrNull() == true) {
                onPlaylistAdded(playlist.title)
            } else {
                com.dsann.music.ui.SnackbarController.showMessage(
                    result.exceptionOrNull()?.message
                        ?: "YouTube Music didn't confirm adding these tracks."
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = {
            Text(
                "YouTube Music playlists",
                fontWeight = FontWeight.Bold,
                style = DsmusicTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when {
                    !isAccountConnected -> {
                        Text(
                            "Connect your YouTube Music account in Settings → Account & Sync to add tracks to your online playlists.",
                            style = DsmusicTheme.typography.bodyMedium,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    safeVideoIds.isEmpty() -> {
                        Text(
                            "These tracks don't have YouTube Music video IDs, so they can't be added to an online playlist.",
                            style = DsmusicTheme.typography.bodyMedium,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    isLoading -> {
                        Text("Loading your YouTube Music playlists…", style = DsmusicTheme.typography.bodyMedium)
                    }
                    loadError != null -> {
                        Text(
                            loadError.orEmpty(),
                            style = DsmusicTheme.typography.bodyMedium,
                            color = DsmusicTheme.colorScheme.error
                        )
                        DsmusicTextButton(onClick = { scope.launch { refreshPlaylists() } }) {
                            Text("Retry")
                        }
                    }
                    playlists.isEmpty() -> {
                        Text(
                            "No YouTube Music playlists were found in this account.",
                            style = DsmusicTheme.typography.bodyMedium,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(playlists, key = { it.playlistId }) { playlist ->
                                DsmusicSurface(
                                    onClick = { addToExistingPlaylist(playlist) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = DsmusicTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.PlaylistPlay,
                                            contentDescription = null,
                                            tint = DsmusicTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                playlist.title,
                                                fontWeight = FontWeight.SemiBold,
                                                style = DsmusicTheme.typography.bodyLarge,
                                                color = DsmusicTheme.colorScheme.onSurface
                                            )
                                            playlist.songCount?.takeIf { it.isNotBlank() }?.let {
                                                Text(
                                                    it,
                                                    style = DsmusicTheme.typography.bodySmall,
                                                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (isWorking) {
                                            Text("Adding…", style = DsmusicTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isAccountConnected && safeVideoIds.isNotEmpty()) {
                    DsmusicOutlinedButton(
                        onClick = {
                            newPlaylistTitle = ""
                            showCreateDialog = true
                        },
                        enabled = !isWorking,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Create YouTube Music playlist")
                    }
                }
            }
        },
        confirmButton = {
            DsmusicTextButton(onClick = { if (!isWorking) onDismiss() }) {
                Text("Cancel")
            }
        }
    )

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { if (!isWorking) showCreateDialog = false },
            title = { Text("New YouTube Music playlist", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistTitle,
                    onValueChange = { newPlaylistTitle = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                DsmusicButton(
                    onClick = {
                        val title = newPlaylistTitle.trim()
                        if (title.isNotEmpty() && !isWorking) {
                            scope.launch {
                                isWorking = true
                                val result = YouTubeMusic.createPlaylist(
                                    title = title,
                                    videoIds = safeVideoIds
                                )
                                isWorking = false
                                result.onSuccess {
                                    onPlaylistAdded(title)
                                }.onFailure { error ->
                                    com.dsann.music.ui.SnackbarController.showMessage(
                                        error.message ?: "Couldn't create the YouTube Music playlist."
                                    )
                                }
                            }
                        }
                    },
                    enabled = newPlaylistTitle.isNotBlank() && !isWorking
                ) {
                    Text(if (isWorking) "Creating…" else "Create and add")
                }
            },
            dismissButton = {
                DsmusicTextButton(onClick = { if (!isWorking) showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LocalPlaylistPickerDialog(
    playlists: List<com.dsann.music.data.db.PlaylistEntity>,
    onCreateNew: () -> Unit,
    onPlaylistSelected: (com.dsann.music.data.db.PlaylistEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Add to Playlist",
                fontWeight = FontWeight.Bold,
                style = DsmusicTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
            ) {
                // Button to create new playlist
                DsmusicButton(
                    onClick = onCreateNew,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DsmusicTheme.colorScheme.primaryContainer, contentColor = DsmusicTheme.colorScheme.onPrimaryContainer)
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create New Playlist")
                }

                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No playlists yet",
                            style = DsmusicTheme.typography.bodyMedium,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(playlists) { playlist ->
                            DsmusicSurface(
                                onClick = { onPlaylistSelected(playlist) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = DsmusicTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.PlaylistPlay,
                                        null,
                                        tint = DsmusicTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Text(
                                        playlist.title,
                                        fontWeight = FontWeight.SemiBold,
                                        style = DsmusicTheme.typography.bodyLarge,
                                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            DsmusicTextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CreateLocalPlaylistDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "New Playlist",
                fontWeight = FontWeight.Bold,
                style = DsmusicTheme.typography.titleLarge
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            DsmusicButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            DsmusicTextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun HeaderSection(
    song: SongItem?,
    bitmap: android.graphics.Bitmap?,
    onEdit: () -> Unit,
    isLandscape: Boolean = false
) {
    if (!isLandscape) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DsmusicSurface(
                shape = DsmusicTheme.shapes.large,
                color = DsmusicTheme.colorScheme.surfaceVariant,
                showBorder = false
            ) {
                if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), null, Modifier.size(84.dp), contentScale = ContentScale.Crop)
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.default_album_art),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(84.dp)
                    )
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Text(
                    song?.title ?: "Unknown",
                    style = DsmusicTheme.typography.headlineSmall,
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song?.artist ?: "Unknown Artist",
                    style = DsmusicTheme.typography.bodyMedium,
                    fontFamily = GoogleSansFlex,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            DsmusicIconButton(
                onClick = onEdit,
                modifier = Modifier.background(DsmusicTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(Icons.Rounded.Edit, null)
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                DsmusicSurface(
                    shape = RoundedCornerShape(16.dp),
                    color = DsmusicTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp,
                    showBorder = false
                ) {
                    if (bitmap != null) {
                        Image(bitmap.asImageBitmap(), null, Modifier.size(112.dp), contentScale = ContentScale.Crop)
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.default_album_art),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(112.dp)
                        )
                    }
                }
                
                DsmusicIconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(32.dp)
                        .offset(x = 6.dp, y = 6.dp)
                        .background(DsmusicTheme.colorScheme.primaryContainer, CircleShape)
                ) {
                    Icon(
                        Icons.Rounded.Edit,
                        null,
                        tint = DsmusicTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                song?.title ?: "Unknown",
                style = DsmusicTheme.typography.titleMedium,
                fontFamily = GoogleSansFlex,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                song?.artist ?: "Unknown Artist",
                style = DsmusicTheme.typography.bodySmall,
                fontFamily = GoogleSansFlex,
                color = DsmusicTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@Composable
fun ActionButtonRow1(
    song: SongItem?,
    isFavorite: Boolean,
    onFavoriteChange: (Boolean) -> Unit,
    actionListener: SongActionsBottomSheet.ActionListener?,
    onDismiss: () -> Unit,
    showLikeAndShare: Boolean = true
) {
    var favorite by remember(song?.id, isFavorite) { mutableStateOf(isFavorite) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SongActionTile(
            modifier = Modifier.weight(if (showLikeAndShare) 1f else 3f),
            label = "Play",
            containerColor = DsmusicTheme.colorScheme.primaryContainer,
            contentColor = DsmusicTheme.colorScheme.onPrimaryContainer,
            onClick = {
                song?.let { actionListener?.onPlay(it) }
                onDismiss()
            }
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = DsmusicTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
        if (showLikeAndShare) SongActionTile(
            modifier = Modifier.weight(1f),
            label = if (favorite) "Liked" else "Like",
            containerColor = DsmusicTheme.colorScheme.secondaryContainer,
            contentColor = if (favorite) Color(0xFFE91E63) else DsmusicTheme.colorScheme.onSecondaryContainer,
            onClick = {
                favorite = !favorite
                onFavoriteChange(favorite)
                song?.let { actionListener?.onFavorite(it, favorite) }
            }
        ) {
            Icon(
                imageVector = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = null,
                tint = if (favorite) Color(0xFFE91E63) else DsmusicTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(23.dp)
            )
        }
        if (showLikeAndShare) SongActionTile(
            modifier = Modifier.weight(1f),
            label = "Share",
            containerColor = DsmusicTheme.colorScheme.tertiaryContainer,
            contentColor = DsmusicTheme.colorScheme.onTertiaryContainer,
            onClick = { song?.let { actionListener?.onShare(it) } }
        ) {
            Icon(
                Icons.Rounded.Share,
                contentDescription = null,
                tint = DsmusicTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(23.dp)
            )
        }
    }
}

@Composable
fun ActionButtonRow2(
    song: SongItem?,
    actionListener: SongActionsBottomSheet.ActionListener?,
    onDismiss: () -> Unit,
    showAddToQueue: Boolean = true,
    downloadedSongMode: Boolean = false,
    onDeleteDownloaded: () -> Unit = {},
    localSongMode: Boolean = false
) {
    val context = LocalContext.current
    val videoId = song?.videoId
    val isOnline = !localSongMode && !videoId.isNullOrBlank()
    val activeDownloadIds by com.dsann.music.util.SongDownloader.activeDownloadIds.collectAsStateWithLifecycle()
    val downloadedVideoIds by com.dsann.music.util.SongDownloader.downloadedVideoIds.collectAsStateWithLifecycle()
    val convertingKeys by com.dsann.music.util.OpusToMp3Converter.convertingKeys
        .collectAsStateWithLifecycle(emptySet())
    LaunchedEffect(localSongMode, videoId, song?.title, song?.artist) {
        if (!localSongMode) {
            song?.let { com.dsann.music.util.SongDownloader.refreshDownloadedStatus(context, it) }
        }
    }
    val isDownloading = videoId?.let { it in activeDownloadIds } == true
    val isDownloaded = videoId?.let { it in downloadedVideoIds } == true
    val isConvertingToMp3 = song?.let { target ->
        com.dsann.music.util.OpusToMp3Converter.conversionKeys(target).any { it in convertingKeys }
    } == true
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showAddToQueue) {
            SongActionTile(
                modifier = Modifier.weight(1f),
                label = "Queue",
                containerColor = DsmusicTheme.colorScheme.secondaryContainer,
                contentColor = DsmusicTheme.colorScheme.onSecondaryContainer,
                onClick = {
                    song?.let { actionListener?.onAddToQueue(it) }
                    onDismiss()
                }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_queue_music),
                    contentDescription = null,
                    tint = DsmusicTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(23.dp)
                )
            }
        }
        SongActionTile(
            modifier = Modifier.weight(1f),
            label = "Next",
            containerColor = DsmusicTheme.colorScheme.tertiaryContainer,
            contentColor = DsmusicTheme.colorScheme.onTertiaryContainer,
            onClick = {
                song?.let { actionListener?.onPlayNext(it) }
                onDismiss()
            }
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_next),
                contentDescription = null,
                tint = DsmusicTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(23.dp)
            )
        }
        if (!localSongMode) SongActionTile(
            modifier = Modifier.weight(1f),
            label = when {
                downloadedSongMode -> "Delete"
                isConvertingToMp3 -> "Converting to MP3…"
                !isOnline -> "Offline"
                isDownloading -> "Cancel"
                isDownloaded -> "Downloaded"
                else -> "Download"
            },
            containerColor = when {
                downloadedSongMode -> DsmusicTheme.colorScheme.errorContainer
                isConvertingToMp3 -> DsmusicTheme.colorScheme.tertiaryContainer
                !isOnline -> DsmusicTheme.colorScheme.surfaceVariant
                isDownloading -> DsmusicTheme.colorScheme.errorContainer
                isDownloaded -> DsmusicTheme.colorScheme.secondaryContainer
                else -> DsmusicTheme.colorScheme.primaryContainer
            },
            contentColor = when {
                downloadedSongMode -> DsmusicTheme.colorScheme.onErrorContainer
                isConvertingToMp3 -> DsmusicTheme.colorScheme.onTertiaryContainer
                !isOnline -> DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                isDownloading -> DsmusicTheme.colorScheme.onErrorContainer
                isDownloaded -> DsmusicTheme.colorScheme.onSecondaryContainer
                else -> DsmusicTheme.colorScheme.onPrimaryContainer
            },
            enabled = if (downloadedSongMode) !isConvertingToMp3 else
                !isConvertingToMp3 && isOnline && (isDownloading || !isDownloaded),
            onClick = {
                if (downloadedSongMode) {
                    onDeleteDownloaded()
                    onDismiss()
                } else {
                    song?.let {
                        when {
                            isDownloading -> com.dsann.music.util.SongDownloader.cancelDownload(context, it)
                            !isDownloaded -> com.dsann.music.util.SongDownloader.downloadSong(context, it)
                        }
                    }
                    // Keep the sheet open so this same control can turn into Cancel and then Downloaded.
                }
            }
        ) {
            Icon(
                imageVector = when {
                    downloadedSongMode -> Icons.Rounded.DeleteOutline
                    isConvertingToMp3 -> Icons.Rounded.Download
                    !isOnline -> Icons.Rounded.Check
                    isDownloading -> Icons.Rounded.Close
                    isDownloaded -> Icons.Rounded.Check
                    else -> Icons.Rounded.Download
                },
                contentDescription = when {
                    downloadedSongMode -> "Delete downloaded song"
                    isConvertingToMp3 -> "Converting to MP3"
                    isDownloading -> "Cancel download"
                    isDownloaded -> "Downloaded"
                    else -> "Download"
                },
                tint = when {
                    downloadedSongMode -> DsmusicTheme.colorScheme.onErrorContainer
                    isConvertingToMp3 -> DsmusicTheme.colorScheme.onTertiaryContainer
                    !isOnline -> DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    isDownloading -> DsmusicTheme.colorScheme.onErrorContainer
                    isDownloaded -> DsmusicTheme.colorScheme.onSecondaryContainer
                    else -> DsmusicTheme.colorScheme.onPrimaryContainer
                },
                modifier = Modifier.size(23.dp)
            )
        }
    }
}
@SuppressLint("UseOfNonLambdaOffsetOverload")
@Composable
fun ExpressiveTabSwitcher(
    selectedTab: BottomSheetTab,
    onTabSelected: (BottomSheetTab) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    compact: Boolean = false
) {
    BoxWithConstraints(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(DsmusicTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        // The landscape left pane can be substantially narrower than 320 dp.
        // Derive both the selected pill width and its offset from the measured
        // container so Options and Info never draw outside their own hit regions.
        val tabWidth = maxWidth / 2
        val offset by animateDpAsState(
            targetValue = if (selectedTab == BottomSheetTab.OPTIONS) 0.dp else tabWidth,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
            label = "TabOffset"
        )

        Box(
            modifier = Modifier
                .offset(x = offset)
                .width(tabWidth)
                .fillMaxHeight()
                .padding(4.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(DsmusicTheme.colorScheme.primary)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onTabSelected(BottomSheetTab.OPTIONS) },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = null,
                        tint = if (selectedTab == BottomSheetTab.OPTIONS) DsmusicTheme.colorScheme.onPrimary else DsmusicTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(if (compact) 14.dp else 18.dp)
                    )
                    Text(
                        text = "OPTIONS",
                        color = if (selectedTab == BottomSheetTab.OPTIONS) DsmusicTheme.colorScheme.onPrimary else DsmusicTheme.colorScheme.onSurfaceVariant,
                        fontSize = if (compact) 10.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSansFlex,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onTabSelected(BottomSheetTab.INFO) },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = if (selectedTab == BottomSheetTab.INFO) DsmusicTheme.colorScheme.onPrimary else DsmusicTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(if (compact) 14.dp else 18.dp)
                    )
                    Text(
                        text = "INFO",
                        color = if (selectedTab == BottomSheetTab.INFO) DsmusicTheme.colorScheme.onPrimary else DsmusicTheme.colorScheme.onSurfaceVariant,
                        fontSize = if (compact) 10.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSansFlex,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}

private data class SongInfoDetails(
    val duration: String,
    val album: String,
    val artist: String,
    val size: String,
    val bitrate: String,
    val format: String,
    val sampleRate: String,
    val location: String
)

@Composable
fun InfoContent(song: SongItem?) {
    val context = LocalContext.current
    val mainActivity = remember(context) { findMainActivity(context) }
    val info by produceState(
        initialValue = SongInfoDetails(
            duration = "Reading metadata…",
            album = "Reading metadata…",
            artist = song?.artist ?: "Reading metadata…",
            size = "Calculating file size…",
            bitrate = "Reading audio format…",
            format = "Detecting format…",
            sampleRate = "Reading audio format…",
            location = song?.path?.takeIf { it.isNotBlank() } ?: "Online source"
        ),
        song,
        context,
        mainActivity?.audioService
    ) {
        val liveFormat = song?.let {
            mainActivity?.audioService?.getCurrentAudioFormatDetails(it.id.toString())
        }
        value = withContext(Dispatchers.IO) {
            val onlineMetadata = song?.videoId?.takeIf { it.isNotBlank() }?.let {
                OnlineStreamMetadataReader.read(it)
            }
            readSongInfoDetails(context, song, liveFormat, onlineMetadata)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InfoRow("Duration", info.duration, Icons.Rounded.DateRange)
        InfoRow("Album", info.album, Icons.Rounded.Face)
        InfoRow("Artist", info.artist, Icons.Rounded.Person)
        InfoRow("Size", info.size, Icons.Rounded.Download)
        InfoRow("Bitrate", info.bitrate, Icons.Rounded.Info)
        InfoRow("Format", info.format, Icons.Rounded.Info)
        InfoRow("Sample rate", info.sampleRate, Icons.Rounded.Info)
        InfoRow(if (song?.videoId.isNullOrBlank()) "Location" else "Source", info.location, Icons.Rounded.Menu)
    }
}

private fun findMainActivity(context: Context): MainActivity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is MainActivity) return current
        current = current.baseContext
    }
    return current as? MainActivity
}

private fun readSongInfoDetails(
    context: Context,
    song: SongItem?,
    liveFormat: CurrentAudioFormatDetails?,
    onlineStreamMetadata: OnlineStreamMetadata? = null
): SongInfoDetails {
    if (song == null) {
        return SongInfoDetails(
            duration = "No track selected",
            album = "No track selected",
            artist = "No track selected",
            size = "No track selected",
            bitrate = "No track selected",
            format = "No track selected",
            sampleRate = "No track selected",
            location = "No track selected"
        )
    }

    val rawPath = song.path?.trim().orEmpty()
    val uriFromPath = rawPath.takeIf {
        it.startsWith("content://", ignoreCase = true) ||
            it.startsWith("file://", ignoreCase = true)
    }?.let { Uri.parse(it) }
    val sourceUri = uriFromPath ?: song.contentUri
    val file = rawPath.takeIf {
        it.isNotBlank() &&
            !it.startsWith("http://", ignoreCase = true) &&
            !it.startsWith("https://", ignoreCase = true) &&
            !it.startsWith("content://", ignoreCase = true) &&
            !it.startsWith("file://", ignoreCase = true)
    }?.let { File(it) }?.takeIf { it.isFile }
    val remote = !song.videoId.isNullOrBlank() ||
        rawPath.startsWith("http://", ignoreCase = true) ||
        rawPath.startsWith("https://", ignoreCase = true) ||
        sourceUri.scheme == "http" || sourceUri.scheme == "https"
    val localUri = !remote && (sourceUri.scheme == "content" || sourceUri.scheme == "file")

    var durationMs = song.duration
    var albumTag: String? = null
    var artistTag: String? = null
    var bitrateBps: Long? = null
    var sampleRateHz: Long? = null
    var mimeType: String? = null

    val retriever = MediaMetadataRetriever()
    try {
        when {
            file != null -> retriever.setDataSource(file.absolutePath)
            localUri -> retriever.setDataSource(context, sourceUri)
        }
        albumTag = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            ?.trim()?.takeIf { it.isNotEmpty() && !it.equals("<unknown>", ignoreCase = true) }
        artistTag = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?.trim()?.takeIf { it.isNotEmpty() && !it.equals("<unknown>", ignoreCase = true) }
        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()?.takeIf { it > 0L } ?: durationMs
        bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            ?.toLongOrNull()?.takeIf { it > 0L }
        sampleRateHz = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
            ?.toLongOrNull()?.takeIf { it > 0L }
        mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            ?.trim()?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        // Older decoders and some MediaStore providers omit optional tags.
    } finally {
        try {
            retriever.release()
        } catch (_: Exception) {
            // Preserve metadata already extracted.
        }
    }

    var sizeBytes = file?.length()?.takeIf { it > 0L } ?: 0L
    if (sizeBytes <= 0L && localUri) {
        try {
            context.contentResolver.openAssetFileDescriptor(sourceUri, "r")?.use { descriptor ->
                if (descriptor.length > 0L) sizeBytes = descriptor.length
            }
        } catch (_: Exception) {
            // Use OpenableColumns as a secondary fallback.
        }
        if (sizeBytes <= 0L) {
            try {
                context.contentResolver.query(
                    sourceUri,
                    arrayOf(OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (index >= 0 && !cursor.isNull(index)) {
                            sizeBytes = cursor.getLong(index).takeIf { it > 0L } ?: 0L
                        }
                    }
                }
            } catch (_: Exception) {
                // Some content providers intentionally hide file size.
            }
        }
    }

    if (durationMs <= 0L) {
        durationMs = onlineStreamMetadata?.durationMs?.takeIf { it > 0L }
            ?: liveFormat?.durationMs?.takeIf { it > 0L }
            ?: durationMs
    }

    var cachedStreamBytes = 0L
    if (remote) {
        try {
            val snapshot = AudioPlaybackService.getMediaCacheSnapshot(context)
            cachedStreamBytes = snapshot.second.sumOf { entry ->
                val metadata = AudioPlaybackService.getCachedMediaMetadata(context, entry.first)
                if (metadata?.songId == song.id.toString()) entry.second else 0L
            }
        } catch (_: Exception) {
            // The current track information remains valid if cache data isn't available.
        }
    }

    val effectiveBitrate = bitrateBps
        ?: liveFormat?.bitrateBps?.toLong()?.takeIf { it > 0L }
        ?: onlineStreamMetadata?.bitrateBps?.toLong()?.takeIf { it > 0L }
    val effectiveSampleRate = sampleRateHz ?: liveFormat?.sampleRateHz?.toLong()?.takeIf { it > 0L }
    val reportedOnlineStreamBytes = onlineStreamMetadata?.contentLengthBytes?.takeIf { it > 0L }
    val durationText = if (durationMs > 0L) {
        val seconds = durationMs / 1000L
        String.format(java.util.Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L)
    } else {
        "Duration not supplied by source"
    }
    val album = albumTag ?: if (remote) {
        "Online track; album metadata not supplied"
    } else {
        "No album tag embedded in this file"
    }
    val artist = artistTag ?: song.artist.trim().takeIf {
        it.isNotEmpty() &&
            !it.equals("Unknown Artist", ignoreCase = true) &&
            !it.equals("<unknown>", ignoreCase = true)
    } ?: "Artist metadata not supplied"

    val estimatedRemoteBytes = if (remote && effectiveBitrate != null && durationMs > 0L) {
        (effectiveBitrate * durationMs / 8000L).takeIf { it > 0L }
    } else null
    val sizeText = when {
        sizeBytes > 0L -> Formatter.formatFileSize(context, sizeBytes)
        remote && reportedOnlineStreamBytes != null ->
            Formatter.formatFileSize(context, reportedOnlineStreamBytes) + " (reported audio stream length)"
        remote && cachedStreamBytes > 0L && estimatedRemoteBytes != null ->
            Formatter.formatFileSize(context, cachedStreamBytes) + " cached of about " +
                Formatter.formatFileSize(context, estimatedRemoteBytes) + " estimated stream size"
        remote && cachedStreamBytes > 0L ->
            Formatter.formatFileSize(context, cachedStreamBytes) + " of stream data cached so far"
        remote && estimatedRemoteBytes != null ->
            Formatter.formatFileSize(context, estimatedRemoteBytes) + " estimated from bitrate and duration"
        remote -> "Online stream; no fixed local file size"
        else -> "Size is not exposed by this media provider"
    }
    val bitrateText = when {
        effectiveBitrate != null ->
            String.format(java.util.Locale.US, "%.0f kbps", effectiveBitrate / 1000.0) +
                when {
                    !remote -> ""
                    liveFormat?.bitrateBps != null -> " (current stream)"
                    else -> " (selected source stream)"
                }
        sizeBytes > 0L && durationMs > 0L ->
            String.format(java.util.Locale.US, "%.0f kbps estimated from file size", sizeBytes * 8.0 / durationMs)
        remote -> "Selected dynamically by the stream"
        else -> "Bitrate tag not embedded; no reliable estimate"
    }
    val format = file?.extension?.takeIf { it.isNotBlank() }?.uppercase(java.util.Locale.US)
        ?: mimeType?.substringAfter('/', "")?.takeIf { it.isNotBlank() }?.uppercase(java.util.Locale.US)
        ?: onlineStreamMetadata?.mimeType?.substringAfter('/', "")?.takeIf { it.isNotBlank() }
            ?.uppercase(java.util.Locale.US)
        ?: if (remote) "Online audio stream" else "Audio format not tagged"
    val sampleRateText = effectiveSampleRate?.let {
        if (it % 1000L == 0L) (it / 1000L).toString() + " kHz"
        else String.format(java.util.Locale.US, "%.1f kHz", it / 1000.0)
    } ?: if (remote) {
        "Reported by decoder during playback"
    } else {
        "Sample rate tag not included"
    }
    val location = when {
        file != null -> file.absolutePath
        localUri -> sourceUri.toString()
        remote -> {
            val host = runCatching { Uri.parse(rawPath).host }.getOrNull()
            if (!host.isNullOrBlank()) "Online stream (" + host + ")"
            else "YouTube Music stream • " + song.videoId
        }
        rawPath.isNotBlank() -> rawPath
        else -> "MediaStore audio item " + song.id
    }

    return SongInfoDetails(
        duration = durationText,
        album = album,
        artist = artist,
        size = sizeText,
        bitrate = bitrateText,
        format = format,
        sampleRate = sampleRateText,
        location = location
    )
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(DsmusicTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = DsmusicTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                color = DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = GoogleSansFlex
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = DsmusicTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                fontFamily = GoogleSansFlex
            )
        }
    }
}

@SuppressLint("DefaultLocale")
fun SongItem.getFormattedDuration(): String {
    val totalSeconds = duration / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
