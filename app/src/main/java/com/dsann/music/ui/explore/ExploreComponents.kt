@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.dsann.music.ui.explore

import com.dsann.music.ui.rememberTaperedSongDividersEnabled
import com.dsann.music.ui.taperedSongDivider


import com.dsann.music.ui.theme.DsmusicCard
import com.dsann.music.ui.theme.DsmusicSurface
import com.dsann.music.ui.theme.DsmusicListItem
import com.dsann.music.ui.theme.DsmusicButton
import com.dsann.music.ui.theme.DsmusicOutlinedButton
import com.dsann.music.ui.theme.DsmusicTextButton
import com.dsann.music.ui.theme.DsmusicIconButton
import com.dsann.music.ui.theme.DsmusicFilterChip
import com.dsann.music.ui.theme.DsmusicSwitchControl
 
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import android.os.Build
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import com.dsann.music.ui.theme.BottomSheetDefaults
import com.dsann.music.ui.theme.ButtonDefaults
import com.dsann.music.ui.theme.HorizontalDivider
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.IconButton
import com.dsann.music.ui.theme.LoadingIndicator
import com.dsann.music.ui.theme.ListItemDefaults
import com.dsann.music.ui.theme.ListItemShapes
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.ModalBottomSheet
import com.dsann.music.ui.theme.rememberModalBottomSheetState
import com.dsann.music.ui.theme.rememberDsmusicModalSheetGeometry
import com.dsann.music.ui.theme.OutlinedButton
import com.dsann.music.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Precision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.dsann.music.data.innertube.HomeSection
import com.dsann.music.data.innertube.OnlineAlbum
import com.dsann.music.data.innertube.OnlineArtist
import com.dsann.music.data.innertube.OnlineArtistRef
import com.dsann.music.data.innertube.OnlinePlaylist
import com.dsann.music.data.innertube.OnlineSong
import com.dsann.music.data.innertube.SearchItem
import com.dsann.music.model.SongItem
import com.dsann.music.ui.OptionsContent
import com.dsann.music.ui.InfoContent
import com.dsann.music.ui.SongActionsBottomSheet
import com.dsann.music.util.FavoritesManager
import com.dsann.music.util.SongDownloader
import com.dsann.music.viewmodel.ExploreViewModel
import com.dsann.music.viewmodel.PlayerSharedViewModel
import com.dsann.music.ui.LocalRootTabActive
import com.dsann.music.ui.NowPlayingCoverIndicator
import com.dsann.music.ui.rememberNowPlayingCoverColor
import com.dsann.music.ui.player.rememberAppAnimationFrameTimeNanos

/**
 * Standard UI Shimmer Modifier to render placeholder skeleton states efficiently.
 */
@Composable
fun ShimmerModifier(
    width: Dp,
    height: Dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)
) {
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(shape)
            .shimmerEffect()
    )
}

@Composable
fun Modifier.shimmerEffect(): Modifier {
    // Cached/inactive tabs stay static. Visible shimmer samples the shared app frame clock.
    val staticShimmerColor = DsmusicTheme.colorScheme.surfaceContainerHigh
    // Android 6's software-accelerated drawing path struggles with continuously
    // invalidated gradient placeholders. Keep the skeleton static on API 23.
    if (!LocalRootTabActive.current || Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
        return this.background(staticShimmerColor)
    }

    var positionInRoot by remember { mutableStateOf(Offset.Zero) }
    val frameTimeNanos = rememberAppAnimationFrameTimeNanos(active = true)
    val shimmerLowColor = DsmusicTheme.colorScheme.surfaceContainerLow
    val shimmerColors = remember(shimmerLowColor, staticShimmerColor) {
        listOf(shimmerLowColor, staticShimmerColor, shimmerLowColor)
    }

    return this
        .onGloballyPositioned { positionInRoot = it.positionInRoot() }
        .drawWithCache {
            val origin = positionInRoot
            onDrawBehind {
                val frameNanos = frameTimeNanos.value
                val phase = if (frameNanos > 0L) {
                    (frameNanos % 2_000_000_000L).toFloat() / 2_000_000_000f
                } else 0f
                val translate = -1000f + 4000f * phase
                val start = Offset(translate - origin.x, translate - origin.y)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = shimmerColors,
                        start = start,
                        end = Offset(start.x + 600f, start.y + 600f)
                    )
                )
            }
        }
}

/**
 * Rhythmic audio level indicators used to communicate "now playing" context visually.
 */
@Composable
fun AnimatedEqualizerBars(
    modifier: Modifier = Modifier,
    color: Color = DsmusicTheme.colorScheme.primary,
    isPlaying: Boolean = false
) {
    // Do not leave an animation frame loop running for a selected-but-paused track.
    // Android 6 also uses a static indicator here to keep scrolling inexpensive.
    if (!isPlaying || !LocalRootTabActive.current || Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight(0.45f).background(color, RoundedCornerShape(1.dp)))
            Box(Modifier.width(3.dp).fillMaxHeight(0.62f).background(color, RoundedCornerShape(1.dp)))
            Box(Modifier.width(3.dp).fillMaxHeight(0.36f).background(color, RoundedCornerShape(1.dp)))
        }
        return
    }
    val frameTimeNanos = rememberAppAnimationFrameTimeNanos(active = true)
    Canvas(modifier = modifier) {
        val seconds = frameTimeNanos.value.coerceAtLeast(0L) / 1_000_000_000.0
        val barWidth = 3.dp.toPx()
        val gap = 2.dp.toPx()
        val corner = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
        val periods = doubleArrayOf(0.90, 0.70, 1.10)
        val base = floatArrayOf(0.50f, 0.65f, 0.40f)
        val amplitude = floatArrayOf(0.30f, 0.35f, 0.30f)
        repeat(3) { index ->
            val phase = (seconds % periods[index]) / periods[index] * (2.0 * Math.PI)
            val wave = ((1.0 - kotlin.math.cos(phase)) * 0.5).toFloat()
            val fraction = (base[index] - amplitude[index] + 2f * amplitude[index] * wave)
                .coerceIn(0.12f, 1f)
            val height = size.height * fraction
            val x = index * (barWidth + gap)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, size.height - height),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = corner
            )
        }
    }
}

/**
 * Main content skeleton loading structure for the explore home feed.
 */
@Composable
fun HomeFeedSkeleton() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DsmusicTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                ShimmerModifier(width = 160.dp, height = 40.dp, shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(20.dp))
                // Chip row shimmer
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(4) {
                        ShimmerModifier(width = 72.dp, height = 32.dp, shape = CircleShape)
                    }
                }
            }
        }
        items(4) {
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    ShimmerModifier(width = 140.dp, height = 26.dp, shape = RoundedCornerShape(8.dp))
                    ShimmerModifier(width = 64.dp, height = 28.dp, shape = CircleShape)
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(4) {
                        Column(horizontalAlignment = Alignment.Start) {
                            ShimmerModifier(width = 170.dp, height = 170.dp, shape = RoundedCornerShape(8.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            ShimmerModifier(width = 120.dp, height = 16.dp, shape = RoundedCornerShape(6.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            ShimmerModifier(width = 80.dp, height = 12.dp, shape = RoundedCornerShape(6.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Contextual welcome banner dynamically responding to internal application time states.
 */
@Composable
fun WelcomeGreetingBanner(
    userName: String?,
    onRecapClick: (() -> Unit)? = null
) {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = remember(hour) {
        when (hour) { in 0..11 -> "Good Morning,"; in 12..16 -> "Good Afternoon,"; else -> "Good Evening," }
    }
    val iconResId = remember(hour) {
        when (hour) {
            in 0..11 -> com.dsann.music.R.drawable.ic_morning
            in 12..16 -> com.dsann.music.R.drawable.ic_afternoon
            else -> com.dsann.music.R.drawable.ic_evening
        }
    }
    val name = if (!userName.isNullOrBlank()) userName else "Listener"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = iconResId),
                    contentDescription = null,
                    tint = DsmusicTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = greeting,
                    style = DsmusicTheme.typography.titleMedium,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onRecapClick != null) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(DsmusicTheme.colorScheme.primaryContainer)
                        .clickable(onClick = onRecapClick)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier.size(22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = com.dsann.music.R.drawable.ic_refresh),
                            contentDescription = "Listening Recap",
                            tint = DsmusicTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = DsmusicTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = DsmusicTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = DsmusicTheme.typography.headlineLarge.copy(letterSpacing = (-1).sp),
            fontWeight = FontWeight.ExtraBold,
            color = DsmusicTheme.colorScheme.onSurface
        )
    }
}
// ===== Search Result Item =====

@Composable
fun SearchResultItem(
    item: SearchItem,
    isCurrentlyPlaying: Boolean,
    isLoading: Boolean,
    isPlaybackActive: Boolean = false,
    onSongMenuClick: ((OnlineSong) -> Unit)? = null,
    onTouchDown: (() -> Unit)? = null,
    onCollectionMenuClick: ((SearchItem) -> Unit)? = null,
    onClick: () -> Unit
) {
    val backgroundColor = if (isCurrentlyPlaying)
        DsmusicTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    else Color.Transparent

    val containerShape = RoundedCornerShape(8.dp)

    val thumbnailUrl = when (item) {
        is SearchItem.Song -> item.song.thumbnailUrl
        is SearchItem.Album -> item.album.thumbnailUrl
        is SearchItem.Artist -> item.artist.thumbnailUrl
        is SearchItem.Playlist -> item.playlist.thumbnailUrl
    }
    val isCircle = item is SearchItem.Artist
    val itemId = when (item) {
        is SearchItem.Song -> item.song.videoId
        is SearchItem.Album -> item.album.browseId
        is SearchItem.Artist -> item.artist.browseId
        is SearchItem.Playlist -> item.playlist.playlistId
    }
    val imageShape = if (isCircle) CircleShape else RoundedCornerShape(4.dp) // Match YT Music sharp rounded corner
    val taperedDividersEnabled = rememberTaperedSongDividersEnabled()
    val isSongItem = item is SearchItem.Song
    val isCurrentSong = isSongItem && isCurrentlyPlaying && !isLoading
    val playingCoverColor = rememberNowPlayingCoverColor(thumbnailUrl, isCurrentSong)
    // Most Explore/history/detail rows do not need press-time prefetch. Avoid installing
    // an always-listening pointer coroutine for those rows, especially on API 23.
    val touchDownModifier = if (onTouchDown != null) {
        Modifier.pointerInput(itemId) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                        onTouchDown?.invoke()
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp) // Perfect gap between items
            .clip(containerShape)
            .background(backgroundColor)
            .taperedSongDivider(
                enabled = taperedDividersEnabled && isSongItem,
                color = DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
            )
            .then(touchDownModifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    when (item) {
                        is SearchItem.Song -> onSongMenuClick?.invoke(item.song)
                        is SearchItem.Album, is SearchItem.Playlist -> onCollectionMenuClick?.invoke(item)
                        is SearchItem.Artist -> Unit
                    }
                }
            )
            .padding(horizontal = 8.dp, vertical = 6.dp), // Tighter inner layout padding
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail Image
        Box(modifier = Modifier.size(48.dp)) {
            AsyncImage(
                model = rememberExploreImageRequest(thumbnailUrl, 160),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(imageShape).sharedElementIfAvailable("image-$itemId"),
                contentScale = ContentScale.Crop
            )
            if (isCurrentSong) {
                NowPlayingCoverIndicator(
                    isPlaying = true,
                    isAnimating = isPlaybackActive,
                    coverColor = playingCoverColor,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            if (isLoading) {
                val loadingShimmerColor = DsmusicTheme.colorScheme.surfaceContainerHigh
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
                    // Static API 23 loading marker; avoid a continuously invalidated shimmer/spinner.
                    Box(
                        modifier = Modifier.fillMaxSize().clip(imageShape)
                            .background(loadingShimmerColor.copy(alpha = 0.6f))
                    )
                } else {
                    val loadingFrameTimeNanos = rememberAppAnimationFrameTimeNanos(
                        active = LocalRootTabActive.current
                    )
                    Box(
                        modifier = Modifier.fillMaxSize().clip(imageShape).drawBehind {
                            val frameNanos = loadingFrameTimeNanos.value
                            val phase = if (frameNanos > 0L) {
                                (frameNanos % 800_000_000L).toFloat() / 800_000_000f
                            } else 0.5f
                            val pulse = (1f - kotlin.math.cos(phase * (2f * Math.PI.toFloat()))) * 0.5f
                            drawRect(loadingShimmerColor.copy(alpha = 0.3f + pulse * 0.4f))
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator(modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Titles and Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when (item) {
                    is SearchItem.Song -> item.song.title
                    is SearchItem.Album -> item.album.title
                    is SearchItem.Artist -> item.artist.title
                    is SearchItem.Playlist -> item.playlist.title
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = DsmusicTheme.typography.bodyMedium.copy(
                    fontWeight = if (isCurrentlyPlaying) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (isCurrentlyPlaying) DsmusicTheme.colorScheme.primary else DsmusicTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = when (item) {
                    is SearchItem.Song -> when {
                        item.song.isLive -> "LIVE • ${item.song.artist}"
                        item.song.isEpisode -> "Episode • ${item.song.artist}"
                        item.song.isVideo -> "Video • ${item.song.artist}"
                        else -> "${item.song.artist}${item.song.duration?.let { " • $it" } ?: ""}"
                    }
                    is SearchItem.Album -> "Album • ${item.album.artists.joinToString { it.name }}${item.album.year?.let { " • $it" } ?: ""}"
                    is SearchItem.Artist -> "Artist${item.artist.subscriberCount?.let { " • $it" } ?: ""}"
                    is SearchItem.Playlist -> if (item.playlist.isPodcast) {
                        "Podcast${item.playlist.author?.name?.let { " • $it" } ?: ""}"
                    } else "Playlist${item.playlist.songCount?.let { " • $it songs" } ?: ""}"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = DsmusicTheme.typography.bodySmall,
                color = DsmusicTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Trailing Content
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (item) {
                is SearchItem.Song -> {
                    if (onSongMenuClick != null) {
                        DsmusicIconButton(
                            onClick = { onSongMenuClick(item.song) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More Options",
                                tint = DsmusicTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(36.dp))
                    }
                }
                is SearchItem.Album, is SearchItem.Playlist -> {
                    if (onCollectionMenuClick != null) {
                        DsmusicIconButton(
                            onClick = { onCollectionMenuClick(item) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More options",
                                tint = DsmusicTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = DsmusicTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    Spacer(modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}

/**
 * Large lead result used at the top of the Top results page. It keeps the same item actions
 * as the regular rows while making the best match visually distinct.
 */
@Composable
fun SearchTopResultCard(
    item: SearchItem,
    isCurrentlyPlaying: Boolean,
    isLoading: Boolean,
    isPlaybackActive: Boolean = false,
    onSongMenuClick: ((OnlineSong) -> Unit)? = null,
    onTouchDown: () -> Unit = {},
    onCollectionMenuClick: ((SearchItem) -> Unit)? = null,
    onClick: () -> Unit
) {
    val thumbnail = when (item) {
        is SearchItem.Song -> item.song.thumbnailUrl
        is SearchItem.Album -> item.album.thumbnailUrl
        is SearchItem.Artist -> item.artist.thumbnailUrl
        is SearchItem.Playlist -> item.playlist.thumbnailUrl
    }
    val title = when (item) {
        is SearchItem.Song -> item.song.title
        is SearchItem.Album -> item.album.title
        is SearchItem.Artist -> item.artist.title
        is SearchItem.Playlist -> item.playlist.title
    }
    val detail = when (item) {
        is SearchItem.Song -> when {
            item.song.isLive -> "LIVE • ${item.song.artist}"
            item.song.isEpisode -> "Episode • ${item.song.artist}"
            item.song.isVideo -> "Video • ${item.song.artist}"
            else -> "Song • ${item.song.artist}"
        }
        is SearchItem.Album -> "Album • " + item.album.artists.joinToString { it.name }
        is SearchItem.Artist -> "Artist" + (item.artist.subscriberCount?.let { " • $it" } ?: "")
        is SearchItem.Playlist -> (if (item.playlist.isPodcast) "Podcast" else "Playlist") +
            (item.playlist.author?.name?.let { " • $it" } ?: "")
    }
    val imageShape = if (item is SearchItem.Artist) CircleShape else RoundedCornerShape(14.dp)
    val isCurrentSong = item is SearchItem.Song && isCurrentlyPlaying && !isLoading
    val playingCoverColor = rememberNowPlayingCoverColor(thumbnail, isCurrentSong)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(DsmusicTheme.colorScheme.surfaceContainerLow)
            .pointerInput(item) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press) onTouchDown()
                    }
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    when (item) {
                        is SearchItem.Song -> onSongMenuClick?.invoke(item.song)
                        is SearchItem.Album, is SearchItem.Playlist -> onCollectionMenuClick?.invoke(item)
                        is SearchItem.Artist -> Unit
                    }
                }
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(92.dp)) {
            AsyncImage(
                model = rememberExploreImageRequest(thumbnail, 320),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(imageShape),
                contentScale = ContentScale.Crop
            )
            if (isCurrentSong) {
                NowPlayingCoverIndicator(
                    isPlaying = true,
                    isAnimating = isPlaybackActive,
                    coverColor = playingCoverColor,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "TOP RESULT",
                style = DsmusicTheme.typography.labelSmall,
                color = DsmusicTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                title,
                style = DsmusicTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrentlyPlaying) DsmusicTheme.colorScheme.primary else DsmusicTheme.colorScheme.onSurface
            )
            Text(
                detail,
                style = DsmusicTheme.typography.bodySmall,
                color = DsmusicTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isLoading) {
                Spacer(Modifier.height(6.dp))
                LoadingIndicator(Modifier.size(18.dp))
            }
        }
        when (item) {
            is SearchItem.Song -> if (onSongMenuClick != null) {
                DsmusicIconButton(onClick = { onSongMenuClick(item.song) }) {
                    Icon(Icons.Rounded.MoreVert, "More options")
                }
            } else {
                Icon(Icons.Rounded.PlayArrow, "Open result", tint = DsmusicTheme.colorScheme.primary)
            }
            is SearchItem.Album, is SearchItem.Playlist -> if (onCollectionMenuClick != null) {
                DsmusicIconButton(onClick = { onCollectionMenuClick(item) }) {
                    Icon(Icons.Rounded.MoreVert, "More options")
                }
            } else {
                Icon(Icons.Rounded.PlayArrow, "Open result", tint = DsmusicTheme.colorScheme.primary)
            }
            is SearchItem.Artist -> Icon(Icons.Rounded.PlayArrow, "Open result", tint = DsmusicTheme.colorScheme.primary)
        }
    }
}

/** Compact image cards for albums, artists, and playlists in a Top results shelf. */
@Composable
fun SearchBrowseCard(
    item: SearchItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val (thumbnail, title, subtitle, isArtist) = when (item) {
        is SearchItem.Song -> QuadSearchCard(
            item.song.thumbnailUrl, item.song.title,
            when {
                item.song.isLive -> "Live"
                item.song.isEpisode -> "Episode"
                item.song.isVideo -> "Video"
                else -> item.song.artist
            }, false
        )
        is SearchItem.Album -> QuadSearchCard(
            item.album.thumbnailUrl, item.album.title,
            "Album • " + item.album.artists.joinToString { it.name }, false
        )
        is SearchItem.Artist -> QuadSearchCard(
            item.artist.thumbnailUrl, item.artist.title, "Artist", true
        )
        is SearchItem.Playlist -> QuadSearchCard(
            item.playlist.thumbnailUrl, item.playlist.title,
            if (item.playlist.isPodcast) "Podcast" else "Playlist",
            false
        )
    }
    Column(
        modifier = Modifier.width(132.dp).clip(RoundedCornerShape(12.dp)).combinedClickable(
            onClick = onClick,
            onLongClick = { onLongClick?.invoke() }
        ).padding(4.dp)
    ) {
        Box(modifier = Modifier.size(124.dp)) {
            AsyncImage(
                model = rememberExploreImageRequest(thumbnail, 260),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(if (isArtist) CircleShape else RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            if (item is SearchItem.Song && onLongClick != null) {
                ExploreCoverActionButton(
                    onClick = onLongClick,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(title, style = DsmusicTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = DsmusicTheme.typography.bodySmall, color = DsmusicTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private data class QuadSearchCard(
    val thumbnail: String?,
    val title: String,
    val subtitle: String,
    val isArtist: Boolean
)

// ===== Bento Discovery Cells =====

@Composable
fun BentoCell(
    item: SearchItem,
    modifier: Modifier = Modifier,
    isFeature: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    currentPlayingVideoId: String? = null,
    isPlaybackActive: Boolean = false
) {
    val title = when (item) {
        is SearchItem.Song -> item.song.title
        is SearchItem.Album -> item.album.title
        is SearchItem.Artist -> item.artist.title
        is SearchItem.Playlist -> item.playlist.title
    }
    val subtitle = when (item) {
        is SearchItem.Song -> item.song.artist
        is SearchItem.Album -> item.album.artists.joinToString { it.name }
        is SearchItem.Artist -> "Artist"
        is SearchItem.Playlist -> item.playlist.author?.name ?: "Playlist"
    }
    val thumbnailUrl = when (item) {
        is SearchItem.Song -> item.song.thumbnailUrl
        is SearchItem.Album -> item.album.thumbnailUrl
        is SearchItem.Artist -> item.artist.thumbnailUrl
        is SearchItem.Playlist -> item.playlist.thumbnailUrl
    }

    val isCurrentSong = !currentPlayingVideoId.isNullOrBlank() &&
        item is SearchItem.Song && item.song.videoId == currentPlayingVideoId
    val playingCoverColor = rememberNowPlayingCoverColor(thumbnailUrl, isCurrentSong)

    Box(
        modifier = modifier.clip(RoundedCornerShape(24.dp)).combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = rememberExploreImageRequest(thumbnailUrl, 512),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = if (isFeature) 0.9f else 0.7f)),
                        startY = 50f
                    )
                )
            )
            if (isCurrentSong) {
                NowPlayingCoverIndicator(
                    isPlaying = true,
                    isAnimating = isPlaybackActive,
                    coverColor = playingCoverColor,
                    modifier = Modifier.align(Alignment.Center).size(38.dp)
                )
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(if (isFeature) 16.dp else 12.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                if (isFeature) {
                    Box(
                        modifier = Modifier.padding(bottom = 8.dp)
                            .clip(CircleShape)
                            .background(DsmusicTheme.colorScheme.primaryContainer.copy(alpha = 0.92f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Feature",
                            style = DsmusicTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = DsmusicTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text(
                    text = title,
                    style = if (isFeature) DsmusicTheme.typography.titleLarge else DsmusicTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = if (isFeature) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = DsmusicTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (item is SearchItem.Song) {
            ExploreCoverActionButton(
                onClick = onLongClick,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            )
        }
    }
}

/**
 * Keeps upcoming vertical feed sections warm as the outer Explore LazyColumn scrolls.
 * The first LazyColumn item is the greeting banner, so list index 1 maps to section 0.
 * Only a few small thumbnails are decoded ahead; the page itself remains live and interactive.
 */
@Composable
internal fun PrefetchExploreHomeSectionImages(
    sections: List<HomeSection>,
    state: LazyListState,
    sectionsAhead: Int = 2,
    imagesPerSection: Int = 3
) {
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    // On API 23, decoding off-screen images competes with list layout/drawing. Visible
    // Coil requests are already capped to 60 px, so let LazyRows load only what enters view.
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) return
    LaunchedEffect(state, sections, sectionsAhead, imagesPerSection, imageLoader) {
        snapshotFlow {
            state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        }.distinctUntilChanged().collectLatest { lastVisibleListIndex ->
            val nextSectionIndex = lastVisibleListIndex.coerceAtLeast(0)
            val urls = sections.asSequence()
                .drop(nextSectionIndex)
                .take(sectionsAhead)
                .flatMap { section ->
                    section.items.asSequence().map { it.exploreThumbnailUrl() }
                }
                .filter { it.isNotBlank() }
                .distinct()
                .take(sectionsAhead * imagesPerSection)
                .toList()

            urls.forEach { url ->
                val request = buildExploreImageRequest(context, url, targetSizePx = 256)
                    ?: return@forEach
                imageLoader.enqueue(request)
            }
        }
    }
}

// ===== Home Feed Section =====

private fun openExploreItemMenu(
    item: SearchItem,
    onSongMenuClick: (OnlineSong) -> Unit,
    onCollectionMenuClick: (SearchItem) -> Unit
) {
    when (item) {
        is SearchItem.Song -> onSongMenuClick(item.song)
        is SearchItem.Album, is SearchItem.Playlist -> onCollectionMenuClick(item)
        is SearchItem.Artist -> Unit
    }
}

@Composable
fun HomeSectionRow(
    section: HomeSection,
    currentPlayingVideoId: String?,
    isPlaybackActive: Boolean = false,
    onSongClick: (OnlineSong) -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit,
    onArtistClick: (OnlineArtist) -> Unit,
    onPlaylistClick: (OnlinePlaylist) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onSongTouchDown: (OnlineSong) -> Unit = {},
    onCollectionMenuClick: (SearchItem) -> Unit = {},
    onSectionClick: (browseId: String, params: String?, title: String) -> Unit = { _, _, _ -> }
) {
    val hasSongsOnly = remember(section.items) {
        section.items.isNotEmpty() && section.items.all { it is SearchItem.Song }
    }

    // Android 6 struggles with animating the measured height of nested lazy rows.
    // Keep the layout stable there; newer versions retain the section resize animation.
    val sectionResizeAnimation =
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) Modifier
        else Modifier.animateContentSize()

    Column(modifier = Modifier.padding(vertical = 4.dp).then(sectionResizeAnimation)) {
        // Universal Section Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = section.title,
                style = DsmusicTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = DsmusicTheme.colorScheme.onBackground
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasSongsOnly || section.title.contains("daily discover", ignoreCase = true)) {
                    DsmusicOutlinedButton(
                        onClick = {
                            val songs = section.items.filterIsInstance<SearchItem.Song>().map { it.song }
                            if (songs.isNotEmpty()) onSongClick(songs.first())
                        },
                        modifier = Modifier.height(32.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = DsmusicTheme.colorScheme.onBackground
                        )
                    ) {
                        Text("Play all", style = DsmusicTheme.typography.labelSmall)
                    }
                }
                
                if (section.browseEndpoint != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    DsmusicIconButton(
                        onClick = { onSectionClick(section.browseEndpoint, section.params, section.title) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "See All",
                            tint = DsmusicTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        when {
            // 0. BENTO GRID — High Fidelity Personal Mix
            section.title.contains("personalized mix", ignoreCase = true) && section.items.size >= 5 -> {
                val items = section.items
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(modifier = Modifier.height(260.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Dominant Feature
                        BentoCell(
                            currentPlayingVideoId = currentPlayingVideoId,
                            isPlaybackActive = isPlaybackActive,
                            item = items[0],
                            modifier = Modifier.weight(1.25f).fillMaxHeight(),
                            isFeature = true,
                            onClick = {
                                when (val it = items[0]) {
                                    is SearchItem.Song -> onSongClick(it.song)
                                    is SearchItem.Album -> onAlbumClick(it.album)
                                    is SearchItem.Artist -> onArtistClick(it.artist)
                                    is SearchItem.Playlist -> onPlaylistClick(it.playlist)
                                }
                            },
                            onLongClick = { openExploreItemMenu(items[0], onSongMenuClick, onCollectionMenuClick) }
                        )
                        // Column of stacked tiles
                        Column(modifier = Modifier.weight(0.75f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            BentoCell(currentPlayingVideoId = currentPlayingVideoId, isPlaybackActive = isPlaybackActive, item = items[1], modifier = Modifier.weight(1f).fillMaxWidth(), onClick = {
                                when (val it = items[1]) {
                                    is SearchItem.Song -> onSongClick(it.song)
                                    is SearchItem.Album -> onAlbumClick(it.album)
                                    is SearchItem.Artist -> onArtistClick(it.artist)
                                    is SearchItem.Playlist -> onPlaylistClick(it.playlist)
                                }
                            }, onLongClick = { openExploreItemMenu(items[1], onSongMenuClick, onCollectionMenuClick) })
                            BentoCell(currentPlayingVideoId = currentPlayingVideoId, isPlaybackActive = isPlaybackActive, item = items[2], modifier = Modifier.weight(1f).fillMaxWidth(), onClick = {
                                when (val it = items[2]) {
                                    is SearchItem.Song -> onSongClick(it.song)
                                    is SearchItem.Album -> onAlbumClick(it.album)
                                    is SearchItem.Artist -> onArtistClick(it.artist)
                                    is SearchItem.Playlist -> onPlaylistClick(it.playlist)
                                }
                            }, onLongClick = { openExploreItemMenu(items[2], onSongMenuClick, onCollectionMenuClick) })
                        }
                    }
                    Row(modifier = Modifier.height(130.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Lower row wide tiles
                        BentoCell(currentPlayingVideoId = currentPlayingVideoId, isPlaybackActive = isPlaybackActive, item = items[3], modifier = Modifier.weight(1f).fillMaxHeight(), onClick = {
                            when (val it = items[3]) {
                                is SearchItem.Song -> onSongClick(it.song)
                                is SearchItem.Album -> onAlbumClick(it.album)
                                is SearchItem.Artist -> onArtistClick(it.artist)
                                is SearchItem.Playlist -> onPlaylistClick(it.playlist)
                            }
                        }, onLongClick = { openExploreItemMenu(items[3], onSongMenuClick, onCollectionMenuClick) })
                        BentoCell(currentPlayingVideoId = currentPlayingVideoId, isPlaybackActive = isPlaybackActive, item = items[4], modifier = Modifier.weight(1f).fillMaxHeight(), onClick = {
                            when (val it = items[4]) {
                                is SearchItem.Song -> onSongClick(it.song)
                                is SearchItem.Album -> onAlbumClick(it.album)
                                is SearchItem.Artist -> onArtistClick(it.artist)
                                is SearchItem.Playlist -> onPlaylistClick(it.playlist)
                            }
                        }, onLongClick = { openExploreItemMenu(items[4], onSongMenuClick, onCollectionMenuClick) })
                    }
                    // The Bento layout uses five featured cards. Keep every remaining
                    // recommendation reachable instead of silently dropping the rest.
                    if (items.size > 5) {
                        val additionalItems = items.drop(5)
                        val additionalRowState = rememberLazyListState()
                        val additionalImageUrls = remember(additionalItems) {
                            additionalItems.map { it.exploreThumbnailUrl() }
                        }
                        PrefetchExploreRowImages(
                            additionalImageUrls,
                            additionalRowState,
                            targetSizePx = 256
                        )
                        LazyRow(
                            state = additionalRowState,
                            contentPadding = PaddingValues(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(
                                items = additionalItems,
                                key = { index, item ->
                                    val itemKey = when (item) {
                                        is SearchItem.Song -> "song-${item.song.videoId}"
                                        is SearchItem.Album -> "album-${item.album.browseId}"
                                        is SearchItem.Artist -> "artist-${item.artist.browseId}"
                                        is SearchItem.Playlist -> "playlist-${item.playlist.playlistId}"
                                    }
                                    "mix-extra-$index-$itemKey"
                                }
                            ) { _, item ->
                                HomeCarouselItem(
                                    item = item,
                                    isPlaying = !currentPlayingVideoId.isNullOrBlank() && item is SearchItem.Song &&
                                        item.song.videoId == currentPlayingVideoId,
                                    isAnimating = isPlaybackActive,
                                    onSongMenuClick = onSongMenuClick,
                                    onCollectionMenuClick = onCollectionMenuClick,
                                    onClick = {
                                        when (item) {
                                            is SearchItem.Song -> onSongClick(item.song)
                                            is SearchItem.Album -> onAlbumClick(item.album)
                                            is SearchItem.Artist -> onArtistClick(item.artist)
                                            is SearchItem.Playlist -> onPlaylistClick(item.playlist)
                                        }
                                    },
                                    modifier = Modifier.width(144.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 1. SPEED DIAL — 3×3 Squircle Grid
            section.title.contains("speed dial", ignoreCase = true) -> {
                val rows = section.items.take(9).chunked(3)
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rows.forEach { rowItems ->
                        // Keep the three-column grid, but make each Speed Dial cover
                        // 30% smaller in both dimensions and center the compact grid.
                        Row(modifier = Modifier.fillMaxWidth(0.7f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { item ->
                                val title = when (item) {
                                    is SearchItem.Song -> item.song.title
                                    is SearchItem.Album -> item.album.title
                                    is SearchItem.Artist -> item.artist.title
                                    is SearchItem.Playlist -> item.playlist.title
                                }
                                val thumbnailUrl = when (item) {
                                    is SearchItem.Song -> item.song.thumbnailUrl
                                    is SearchItem.Album -> item.album.thumbnailUrl
                                    is SearchItem.Artist -> item.artist.thumbnailUrl
                                    is SearchItem.Playlist -> item.playlist.thumbnailUrl
                                }
                                val isCurrentSong = !currentPlayingVideoId.isNullOrBlank() &&
                                    item is SearchItem.Song && item.song.videoId == currentPlayingVideoId
                                val playingCoverColor = rememberNowPlayingCoverColor(thumbnailUrl, isCurrentSong)
                                Box(
                                    modifier = Modifier.weight(1f).aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .combinedClickable(
                                            onClick = {
                                                when (item) {
                                                    is SearchItem.Song -> onSongClick(item.song)
                                                    is SearchItem.Album -> onAlbumClick(item.album)
                                                    is SearchItem.Artist -> onArtistClick(item.artist)
                                                    is SearchItem.Playlist -> onPlaylistClick(item.playlist)
                                                }
                                            },
                                            onLongClick = {
                                                openExploreItemMenu(item, onSongMenuClick, onCollectionMenuClick)
                                            }
                                        )
                                ) {
                                    AsyncImage(
                                        model = rememberExploreImageRequest(thumbnailUrl, 512),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    // Scrim overlay using theme token scrim is always dark by M3 spec
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    DsmusicTheme.colorScheme.scrim.copy(alpha = 0.72f)
                                                ),
                                                startY = 80f
                                            )
                                        )
                                    )
                                    if (isCurrentSong) {
                                        NowPlayingCoverIndicator(
                                            isPlaying = true,
                                            isAnimating = isPlaybackActive,
                                            coverColor = playingCoverColor,
                                            modifier = Modifier.align(Alignment.Center).size(38.dp)
                                        )
                                    }
                                    Text(
                                        text = title,
                                        color = DsmusicTheme.colorScheme.inverseOnSurface,
                                        style = DsmusicTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                                    )
                                }
                            }
                            if (rowItems.size < 3) repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            }

            // 2. YOUR DAILY DISCOVER â€” Large Immersive Cards
            section.title.contains("daily discover", ignoreCase = true) -> {
                val rowState = rememberLazyListState()
                val prefetchUrls = remember(section.items) {
                    section.items.map { it.exploreThumbnailUrl() }
                }
                PrefetchExploreRowImages(prefetchUrls, rowState, targetSizePx = 384)
                LazyRow(
                    state = rowState,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(
                        items = section.items,
                        key = { index, item ->
                            val baseKey = when (item) {
                                is SearchItem.Song -> "discover-song-${item.song.videoId}"
                                is SearchItem.Album -> "discover-album-${item.album.browseId}"
                                is SearchItem.Artist -> "discover-artist-${item.artist.browseId}"
                                is SearchItem.Playlist -> "discover-playlist-${item.playlist.playlistId}"
                            }
                            "$index-$baseKey"
                        }
                    ) { _, item ->
                        val title = when (item) {
                            is SearchItem.Song -> item.song.title
                            is SearchItem.Album -> item.album.title
                            is SearchItem.Artist -> item.artist.title
                            is SearchItem.Playlist -> item.playlist.title
                        }
                        val subtitle = when (item) {
                            is SearchItem.Song -> item.song.artist
                            is SearchItem.Album -> item.album.artists.joinToString { it.name }
                            is SearchItem.Artist -> item.artist.subscriberCount ?: "Artist"
                            is SearchItem.Playlist -> item.playlist.author?.name ?: "Playlist"
                        }
                        val thumbnailUrl = when (item) {
                            is SearchItem.Song -> item.song.thumbnailUrl
                            is SearchItem.Album -> item.album.thumbnailUrl
                            is SearchItem.Artist -> item.artist.thumbnailUrl
                            is SearchItem.Playlist -> item.playlist.thumbnailUrl
                        }
                        val promptText = when (item) {
                            is SearchItem.Song -> "Because you liked ${item.song.artist}"
                            else -> "Handpicked based on your interests"
                        }

                        Box(
                            modifier = Modifier.width(300.dp).height(300.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .combinedClickable(
                                    onClick = {
                                        when (item) {
                                            is SearchItem.Song -> onSongClick(item.song)
                                            is SearchItem.Album -> onAlbumClick(item.album)
                                            is SearchItem.Artist -> onArtistClick(item.artist)
                                            is SearchItem.Playlist -> onPlaylistClick(item.playlist)
                                        }
                                    },
                                    onLongClick = {
                                        openExploreItemMenu(item, onSongMenuClick, onCollectionMenuClick)
                                    }
                                )
                        ) {
                            AsyncImage(
                                model = rememberExploreImageRequest(thumbnailUrl, 512),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(
                                        0f to DsmusicTheme.colorScheme.scrim.copy(alpha = 0.5f),
                                        0.35f to Color.Transparent,
                                        1f to DsmusicTheme.colorScheme.scrim.copy(alpha = 0.88f)
                                    )
                                )
                            )
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = title,
                                        color = Color.White,
                                        style = DsmusicTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = subtitle,
                                        color = Color.White.copy(alpha = 0.8f),
                                        style = DsmusicTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = promptText,
                                    color = Color.White.copy(alpha = 0.65f),
                                    style = DsmusicTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 3. FROM THE COMMUNITY â€” Nested Card with Song Preview
            section.title.contains("community", ignoreCase = true) -> {
                val rowState = rememberLazyListState()
                val prefetchUrls = remember(section.items) {
                    section.items.map { it.exploreThumbnailUrl() }
                }
                PrefetchExploreRowImages(prefetchUrls, rowState, targetSizePx = 256)
                LazyRow(
                    state = rowState,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(
                        items = section.items,
                        key = { index, item ->
                            val baseKey = when (item) {
                                is SearchItem.Song -> "community-song-${item.song.videoId}"
                                is SearchItem.Album -> "community-album-${item.album.browseId}"
                                is SearchItem.Artist -> "community-artist-${item.artist.browseId}"
                                is SearchItem.Playlist -> "community-playlist-${item.playlist.playlistId}"
                            }
                            "$index-$baseKey"
                        }
                    ) { _, item ->
                        val title = when (item) {
                            is SearchItem.Song -> item.song.title
                            is SearchItem.Album -> item.album.title
                            is SearchItem.Artist -> item.artist.title
                            is SearchItem.Playlist -> item.playlist.title
                        }
                        val author = when (item) {
                            is SearchItem.Song -> item.song.artist
                            is SearchItem.Album -> item.album.artists.firstOrNull()?.name ?: ""
                            is SearchItem.Artist -> "Artist"
                            is SearchItem.Playlist -> item.playlist.author?.name ?: "Community Curator"
                        }
                        val songCount = when (item) {
                            is SearchItem.Playlist -> item.playlist.songCount?.let { "$it songs" } ?: ""
                            is SearchItem.Album -> item.album.let { "" }
                            else -> ""
                        }
                        val thumbnailUrl = when (item) {
                            is SearchItem.Song -> item.song.thumbnailUrl
                            is SearchItem.Album -> item.album.thumbnailUrl
                            is SearchItem.Artist -> item.artist.thumbnailUrl
                            is SearchItem.Playlist -> item.playlist.thumbnailUrl
                        }

                        Box(
                            modifier = Modifier.width(320.dp).padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(DsmusicTheme.colorScheme.surfaceContainerLow)
                                .combinedClickable(
                                    onClick = {
                                        when (item) {
                                            is SearchItem.Song -> onSongClick(item.song)
                                            is SearchItem.Album -> onAlbumClick(item.album)
                                            is SearchItem.Artist -> onArtistClick(item.artist)
                                            is SearchItem.Playlist -> onPlaylistClick(item.playlist)
                                        }
                                    },
                                    onLongClick = {
                                        openExploreItemMenu(item, onSongMenuClick, onCollectionMenuClick)
                                    }
                                )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = rememberExploreImageRequest(thumbnailUrl, 256),
                                        contentDescription = null,
                                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(title, style = DsmusicTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            color = DsmusicTheme.colorScheme.onSurface)
                                        Text(author, style = DsmusicTheme.typography.bodySmall,
                                            color = DsmusicTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (songCount.isNotBlank()) {
                                            Text(songCount, style = DsmusicTheme.typography.labelSmall,
                                                color = DsmusicTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(54.dp)
                                            .clip(CircleShape)
                                            .background(DsmusicTheme.colorScheme.primaryContainer)
                                            .clickable {
                                                when (item) {
                                                    is SearchItem.Song -> onSongClick(item.song)
                                                    is SearchItem.Playlist -> onPlaylistClick(item.playlist)
                                                    else -> {}
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.PlayArrow,
                                            contentDescription = "Play",
                                            tint = DsmusicTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. GENERAL FALLBACK â€” Song List or Carousel
            else -> {
                if (hasSongsOnly) {
                    // Playback-state changes should not re-filter and re-chunk an unchanged
                    // home section; API 23 list recompositions must stay allocation-light.
                    val songItems = remember(section.items) {
                        section.items.filterIsInstance<SearchItem.Song>()
                    }
                    val chunkedSongs = remember(songItems) { songItems.chunked(4) }
                    val rowState = rememberLazyListState()
                    val prefetchUrls: List<String> = remember(songItems) {
                        songItems.map { it.song.thumbnailUrl }
                            .filterNotNull()
                            .filter { it.isNotBlank() }
                    }
                    PrefetchExploreRowImages(
                        thumbnailUrls = prefetchUrls,
                        state = rowState,
                        targetSizePx = 192,
                        lookAheadCount = 4,
                        itemsPerListEntry = 4
                    )
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        // Two four-song columns should fit inside the current viewport.
                        // A fixed 320dp width pushed the second column beyond phone screens.
                        val songColumnWidth = ((maxWidth - 48.dp) / 2f).coerceAtLeast(120.dp)
                        LazyRow(
                            state = rowState,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(items = chunkedSongs, key = { idx, _ ->
                                "song-col-$idx-${section.title}"
                            }) { _, songChunk ->
                                Column(
                                    modifier = Modifier.width(songColumnWidth),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    songChunk.forEachIndexed { songIndex, item ->
                                        HomeSongListItem(
                                            song = item.song,
                                            isPlaying = !currentPlayingVideoId.isNullOrBlank() && item.song.videoId == currentPlayingVideoId,
                                            isAnimating = isPlaybackActive,
                                            showDivider = songIndex < songChunk.lastIndex,
                                            onTouchDown = { onSongTouchDown(item.song) },
                                            onClick = { onSongClick(item.song) },
                                            onMenuClick = { onSongMenuClick(item.song) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val rowState = rememberLazyListState()
                    val prefetchUrls = remember(section.items) {
                        section.items.map { it.exploreThumbnailUrl() }
                    }
                    PrefetchExploreRowImages(prefetchUrls, rowState, targetSizePx = 384)
                    LazyRow(
                        state = rowState,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            items = section.items,
                            key = { index, item ->
                                val baseKey = when (item) {
                                    is SearchItem.Song -> "carousel-song-${item.song.videoId}-${section.title}"
                                    is SearchItem.Album -> "carousel-album-${item.album.browseId}-${section.title}"
                                    is SearchItem.Artist -> "carousel-artist-${item.artist.browseId}-${section.title}"
                                    is SearchItem.Playlist -> "carousel-playlist-${item.playlist.playlistId}-${section.title}"
                                }
                                "$index-$baseKey"
                            }
                        ) { _, item ->
                            HomeCarouselItem(
                                item = item,
                                isPlaying = when (item) {
                                    is SearchItem.Song -> !currentPlayingVideoId.isNullOrBlank() && item.song.videoId == currentPlayingVideoId
                                    else -> false
                                },
                                isAnimating = isPlaybackActive,
                                onTouchDown = if (item is SearchItem.Song) {
                                    { onSongTouchDown(item.song) }
                                } else null,
                                onSongMenuClick = onSongMenuClick,
                                onCollectionMenuClick = onCollectionMenuClick,
                                onClick = {
                                    when (item) {
                                        is SearchItem.Song -> onSongClick(item.song)
                                        is SearchItem.Album -> onAlbumClick(item.album)
                                        is SearchItem.Artist -> onArtistClick(item.artist)
                                        is SearchItem.Playlist -> onPlaylistClick(item.playlist)
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

// ===== Carousel Item =====

@Composable
fun HomeCarouselItem(
    item: SearchItem,
    isPlaying: Boolean,
    onTouchDown: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(160.dp),
    onSongMenuClick: ((OnlineSong) -> Unit)? = null,
    onCollectionMenuClick: ((SearchItem) -> Unit)? = null,
    isAnimating: Boolean = isPlaying
) {
    val isCircle = item is SearchItem.Artist
    val imageShape = if (isCircle) CircleShape else RoundedCornerShape(8.dp)
    val itemId = when (item) {
        is SearchItem.Song -> item.song.videoId
        is SearchItem.Album -> item.album.browseId
        is SearchItem.Artist -> item.artist.browseId
        is SearchItem.Playlist -> item.playlist.playlistId
    }
    val thumbnailUrl = when (item) {
        is SearchItem.Song -> item.song.thumbnailUrl
        is SearchItem.Album -> item.album.thumbnailUrl
        is SearchItem.Artist -> item.artist.thumbnailUrl
        is SearchItem.Playlist -> item.playlist.thumbnailUrl
    }
    val playingCoverColor = rememberNowPlayingCoverColor(thumbnailUrl, isPlaying)
    val title = when (item) {
        is SearchItem.Song -> item.song.title
        is SearchItem.Album -> item.album.title
        is SearchItem.Artist -> item.artist.title
        is SearchItem.Playlist -> item.playlist.title
    }
    val subtitle = when (item) {
        is SearchItem.Song -> item.song.artist
        is SearchItem.Album -> item.album.artists.joinToString { it.name }
        is SearchItem.Artist -> item.artist.subscriberCount ?: ""
        is SearchItem.Playlist -> item.playlist.author?.name ?: ""
    }

    val touchDownModifier = if (onTouchDown != null) {
        Modifier.pointerInput(itemId) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                        onTouchDown?.invoke()
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .then(touchDownModifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    when (item) {
                        is SearchItem.Song -> onSongMenuClick?.invoke(item.song)
                        is SearchItem.Album, is SearchItem.Playlist -> onCollectionMenuClick?.invoke(item)
                        is SearchItem.Artist -> Unit
                    }
                }
            ),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            contentAlignment = Alignment.TopStart
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .sharedElementIfAvailable("image-$itemId")
                    .clip(imageShape)
                    .background(DsmusicTheme.colorScheme.surfaceContainerLow)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = rememberExploreImageRequest(thumbnailUrl, 384),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (isPlaying) {
                        NowPlayingCoverIndicator(
                            isPlaying = true,
                            isAnimating = isAnimating,
                            coverColor = playingCoverColor,
                            modifier = Modifier.align(Alignment.Center).size(36.dp)
                        )
                    }
                }
            }
            val onMenuClick: (() -> Unit)? = when (item) {
                is SearchItem.Song -> onSongMenuClick?.let { callback -> { callback(item.song) } }
                is SearchItem.Album, is SearchItem.Playlist, is SearchItem.Artist -> null
            }
            if (onMenuClick != null) {
                ExploreCoverActionButton(
                    onClick = onMenuClick,
                    modifier = Modifier.align(Alignment.TopEnd).padding(7.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = DsmusicTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = DsmusicTheme.colorScheme.onBackground,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = subtitle.ifBlank { " " },
            style = DsmusicTheme.typography.labelSmall,
            color = DsmusicTheme.colorScheme.onSurfaceVariant,
            minLines = 1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ===== Song List Item =====

@Composable
fun HomeSongListItem(
    song: OnlineSong,
    isPlaying: Boolean,
    showDivider: Boolean = true,
    onTouchDown: () -> Unit = {},
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    isAnimating: Boolean = isPlaying
) {
    val taperedDividersEnabled = rememberTaperedSongDividersEnabled()
    val playingCoverColor = rememberNowPlayingCoverColor(song.thumbnailUrl, isPlaying)
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(DsmusicTheme.colorScheme.background)
            .taperedSongDivider(
                enabled = taperedDividersEnabled && showDivider,
                color = DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                            onTouchDown()
                        }
                    }
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onMenuClick
            )
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            AsyncImage(
                model = rememberExploreImageRequest(song.thumbnailUrl, 192),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
            if (isPlaying) {
                NowPlayingCoverIndicator(
                    isPlaying = true,
                    isAnimating = isAnimating,
                    coverColor = playingCoverColor,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = DsmusicTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isPlaying) DsmusicTheme.colorScheme.primary else DsmusicTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.artist,
                style = DsmusicTheme.typography.bodySmall,
                color = DsmusicTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DsmusicIconButton(onClick = onMenuClick) {
            Icon(Icons.Rounded.MoreVert, "More", tint = DsmusicTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CompactMenuItem(
    text: String,
    icon: ImageVector,
    shapes: ListItemShapes = ListItemDefaults.shapes(),
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(DsmusicTheme.colorScheme.primary.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DsmusicTheme.colorScheme.primary,
                modifier = Modifier.size(21.dp)
            )
        }
        Text(
            text = text,
            style = DsmusicTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = DsmusicTheme.colorScheme.onSurface
        )
    }
}


@Composable
private fun ExploreCoverActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    DsmusicIconButton(
        onClick = onClick,
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(DsmusicTheme.colorScheme.scrim.copy(alpha = 0.58f)),
        shape = CircleShape
    ) {
        Icon(
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = "More options",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun ExploreActionSheetHeader(
    thumbnailUrl: String?,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    // Compact identity header shared by song, album and playlist action sheets.
    DsmusicSurface(
        modifier = modifier.fillMaxWidth(),
        shape = DsmusicTheme.shapes.large,
        color = DsmusicTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(68.dp)
                    .clip(DsmusicTheme.shapes.large)
                    .background(DsmusicTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = rememberExploreImageRequest(thumbnailUrl, 144),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = DsmusicTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = DsmusicTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = DsmusicTheme.typography.bodySmall,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            trailingContent?.invoke()
        }
    }
}

private data class ExploreTrackMenuItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val action: () -> Unit
)

/**
 * Local-file action sheet. It keeps local playback actions, omits online/social actions,
 * and provides a delete action for the selected source file.
 */
@Composable
fun LocalSongActionsBottomSheet(
    song: SongItem,
    localQueue: List<SongItem>,
    selectedSongIndex: Int,
    playerSharedViewModel: PlayerSharedViewModel,
    onDismissRequest: () -> Unit,
    onDeleteSong: (SongItem) -> Unit,
    modifier: Modifier = Modifier,
    showDeleteAction: Boolean = true
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val modalGeometry = rememberDsmusicModalSheetGeometry()
    var showInfo by remember(song.id) { mutableStateOf(false) }
    var hasDownloadedOpus by remember(song.id, song.path) { mutableStateOf(false) }
    val conversionScope = rememberCoroutineScope()
    val convertingKeys by com.dsann.music.util.OpusToMp3Converter.convertingKeys
        .collectAsStateWithLifecycle(emptySet())
    val isConvertingToMp3 = remember(song.id, song.path, convertingKeys) {
        com.dsann.music.util.OpusToMp3Converter.conversionKeys(song).any { it in convertingKeys }
    }

    LaunchedEffect(song.id, song.path) {
        hasDownloadedOpus = withContext(Dispatchers.IO) {
            SongDownloader.findDownloadedOpusUri(context, song) != null
        }
    }

    val actionListener = remember(song.id, playerSharedViewModel, localQueue, selectedSongIndex, onDeleteSong) {
        object : SongActionsBottomSheet.ActionListener {
            override fun onPlayNext(songItem: SongItem) {
                playerSharedViewModel.addToQueueNext(songItem)
            }

            override fun onAddToQueue(songItem: SongItem) {
                playerSharedViewModel.addToQueue(songItem)
            }

            override fun onDelete(songItem: SongItem) = onDeleteSong(songItem)
            override fun onEdit(songItem: SongItem) = Unit

            override fun onPlay(songItem: SongItem) {
                if (selectedSongIndex in localQueue.indices &&
                    localQueue[selectedSongIndex].id == songItem.id
                ) {
                    playerSharedViewModel.setSongList(localQueue)
                    playerSharedViewModel.playSongAtIndex(selectedSongIndex)
                } else {
                    playerSharedViewModel.playSong(songItem)
                }
            }

            override fun onFavorite(songItem: SongItem, isFav: Boolean) = Unit
            override fun onShare(songItem: SongItem) = Unit
        }
    }

    val convertToMp3: () -> Unit = {
        if (hasDownloadedOpus && !isConvertingToMp3) {
            conversionScope.launch {
                val result = withContext(Dispatchers.IO) {
                    com.dsann.music.util.OpusToMp3Converter.convert(
                        context.applicationContext,
                        song
                    )
                }
                if (result.success) {
                    hasDownloadedOpus = false
                    SongDownloader.refreshDownloadedStatus(context, song)
                }
                com.dsann.music.ui.SnackbarController.showMessage(
                    result.message,
                    iconResId = if (result.success) com.dsann.music.R.drawable.ic_downloaded else null
                )
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        sheetMaxWidth = if (isLandscape) modalGeometry.width else Dp.Unspecified,
        centeredLandscape = true,
        centerOffsetX = modalGeometry.centerOffsetX,
        containerColor = DsmusicTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp, start = 14.dp, end = 14.dp, top = 4.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExploreActionSheetHeader(
                thumbnailUrl = song.getAlbumArtUri()?.toString(),
                title = song.title,
                subtitle = song.artist,
                trailingContent = {
                    DsmusicIconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            )
            HorizontalDivider(color = DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            if (showInfo) {
                DsmusicTextButton(
                    onClick = { showInfo = false },
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
                    showAddToQueue = true,
                    onDismiss = onDismissRequest,
                    showDelete = false,
                    hasDownloadedOpus = hasDownloadedOpus,
                    isConvertingToMp3 = isConvertingToMp3,
                    onConvertToMp3 = convertToMp3,
                    showLikeAndShare = false,
                    showAddToPlaylist = false,
                    localSongMode = true,
                    showInfoAction = true,
                    onShowInfoAction = { showInfo = true },
                    showStandaloneDelete = showDeleteAction
                )
            }
        }
    }
}


/**
 * Queue row actions use the same Compose sheet shell as Explore and Downloaded songs.
 * Only queue-relevant actions are exposed; there is no duplicate add-to-queue, like,
 * share, download, playlist or delete action.
 */
@Composable
fun QueueSongActionsBottomSheet(
    song: SongItem,
    playerSharedViewModel: PlayerSharedViewModel,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val modalGeometry = rememberDsmusicModalSheetGeometry()
    var showInfo by remember(song.id) { mutableStateOf(false) }

    val actionListener = remember(song.id, playerSharedViewModel) {
        object : SongActionsBottomSheet.ActionListener {
            override fun onPlayNext(songItem: SongItem) {
                playerSharedViewModel.addToQueueNext(songItem)
            }

            override fun onAddToQueue(songItem: SongItem) = Unit

            override fun onDelete(songItem: SongItem) {
                playerSharedViewModel.removeFromQueue(songItem.id)
            }

            override fun onEdit(songItem: SongItem) = Unit

            override fun onPlay(songItem: SongItem) {
                playerSharedViewModel.playSong(songItem)
            }

            override fun onFavorite(songItem: SongItem, isFav: Boolean) = Unit
            override fun onShare(songItem: SongItem) = Unit
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        sheetMaxWidth = if (isLandscape) modalGeometry.width else Dp.Unspecified,
        centeredLandscape = true,
        centerOffsetX = modalGeometry.centerOffsetX,
        containerColor = DsmusicTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp, start = 14.dp, end = 14.dp, top = 4.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExploreActionSheetHeader(
                thumbnailUrl = song.getAlbumArtUri()?.toString(),
                title = song.title,
                subtitle = song.artist,
                trailingContent = {
                    DsmusicIconButton(onClick = onDismissRequest) {
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

            if (showInfo) {
                DsmusicTextButton(
                    onClick = { showInfo = false },
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
                    onDismiss = onDismissRequest,
                    showDelete = false,
                    showRemoveFromQueue = true,
                    hasDownloadedOpus = false,
                    isConvertingToMp3 = false,
                    showLikeAndShare = false,
                    showAddToPlaylist = false,
                    localSongMode = true,
                    showInfoAction = true,
                    onShowInfoAction = { showInfo = true }
                )
            }
        }
    }
}

@Composable
fun OnlineSongBottomSheet(
    song: OnlineSong,
    isPinned: Boolean,
    onDismissRequest: () -> Unit,
    playerSharedViewModel: PlayerSharedViewModel,
    exploreViewModel: ExploreViewModel,
    onPlaylistAddClick: (SongItem) -> Unit,
    onViewCreditsClick: (OnlineSong) -> Unit,
    modifier: Modifier = Modifier,
    onRemoveFromCache: (() -> Unit)? = null,
    onNavigateToExplore: (() -> Unit)? = null,
    downloadedSong: SongItem? = null,
    onDeleteDownloaded: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val favoritesManager = remember { FavoritesManager(context) }
    var isFavorite by remember(song.videoId, downloadedSong?.id) {
        mutableStateOf(favoritesManager.isFavorite(downloadedSong?.id ?: song.videoId.hashCode().toLong()))
    }
    var showDownloadedInfo by remember(song.videoId, downloadedSong?.id) { mutableStateOf(false) }

    // Legacy Gson records can contain null artistRefs (or null entries) despite
    // OnlineSong's Kotlin default. Normalize once before building actions/artist rows.
    val safeArtistRefs = remember(song.artistRefs) {
        song.artistRefs.orEmpty().mapNotNull { ref ->
            val name = ref?.name?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            OnlineArtistRef(name, ref.id?.takeIf { it.isNotBlank() })
        }
    }
    val onlineSongItem = remember(
        song.videoId,
        song.title,
        song.artist,
        song.durationMs,
        song.thumbnailUrl,
        song.albumName,
        song.artistId,
        safeArtistRefs,
        downloadedSong
    ) {
        downloadedSong ?: SongItem.createOnlineSong(
            song.videoId,
            song.title,
            song.artist,
            "https://music.youtube.com/watch?v=${song.videoId}",
            song.durationMs,
            song.thumbnailUrl,
            song.artistId,
            albumName = song.albumName,
            artistRefs = safeArtistRefs
        )
    }

    val conversionScope = rememberCoroutineScope()
    val convertingKeys by com.dsann.music.util.OpusToMp3Converter.convertingKeys
        .collectAsStateWithLifecycle(emptySet())
    val isConvertingToMp3 = com.dsann.music.util.OpusToMp3Converter
        .conversionKeys(onlineSongItem).any { it in convertingKeys }
    var hasDownloadedOpus by remember(song.videoId) { mutableStateOf(false) }

    LaunchedEffect(song.videoId, song.title, song.artist, song.albumName, isConvertingToMp3) {
        hasDownloadedOpus = withContext(Dispatchers.IO) {
            SongDownloader.findDownloadedOpusUri(context, onlineSongItem) != null
        }
    }

    val convertToMp3: () -> Unit = {
        if (hasDownloadedOpus && !isConvertingToMp3) {
            conversionScope.launch {
                val result = withContext(Dispatchers.IO) {
                    com.dsann.music.util.OpusToMp3Converter.convert(
                        context.applicationContext,
                        onlineSongItem
                    )
                }
                if (result.success) {
                    hasDownloadedOpus = false
                    SongDownloader.refreshDownloadedStatus(context, onlineSongItem)
                }
                com.dsann.music.ui.SnackbarController.showMessage(result.message)
            }
        }
    }
    val queueStyleListener = remember(song.videoId, downloadedSong?.id) {
        object : SongActionsBottomSheet.ActionListener {
            override fun onPlayNext(songItem: SongItem) {
                playerSharedViewModel.addToQueueNext(songItem)
                if (downloadedSong == null) exploreViewModel.addToQueueNext(song)
            }
            override fun onAddToQueue(songItem: SongItem) {
                playerSharedViewModel.addToQueue(songItem)
                if (downloadedSong == null) exploreViewModel.addToQueueLast(song)
            }
            override fun onDelete(songItem: SongItem) = Unit
            override fun onEdit(songItem: SongItem) = Unit
            override fun onPlay(songItem: SongItem) {
                if (downloadedSong != null) playerSharedViewModel.playSong(songItem)
                else exploreViewModel.playOnlineSongWithQueue(song, listOf(song), 0)
            }
            override fun onFavorite(songItem: SongItem, isFav: Boolean) {
                favoritesManager.setFavorite(songItem.id, isFav)
                com.dsann.music.ui.SnackbarController.showMessage(
                    if (isFav) "Added to library" else "Removed from library"
                )
            }
            override fun onShare(songItem: SongItem) {
                val shareIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    if (downloadedSong != null) {
                        type = "audio/*"
                        putExtra(android.content.Intent.EXTRA_STREAM, downloadedSong.contentUri)
                        putExtra(android.content.Intent.EXTRA_TEXT, "Listen to ${downloadedSong.title}")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } else {
                        putExtra(
                            android.content.Intent.EXTRA_TEXT,
                            "Check out ${song.title} by ${song.artist}: https://music.youtube.com/watch?v=${song.videoId}"
                        )
                        type = "text/plain"
                    }
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share via"))
            }
        }
    }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val modalGeometry = rememberDsmusicModalSheetGeometry()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        sheetMaxWidth = if (isLandscape) modalGeometry.width else Dp.Unspecified,
        centeredLandscape = true,
        centerOffsetX = modalGeometry.centerOffsetX,
        containerColor = DsmusicTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp, start = 14.dp, end = 14.dp, top = 4.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExploreActionSheetHeader(
                thumbnailUrl = song.thumbnailUrl,
                title = song.title,
                subtitle = song.artist,
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DsmusicIconButton(onClick = onDismissRequest) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            )

            HorizontalDivider(color = DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            if (showDownloadedInfo && downloadedSong != null) {
                DsmusicTextButton(
                    onClick = { showDownloadedInfo = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back to song actions")
                }
                InfoContent(downloadedSong)
            } else {
                OptionsContent(
                    song = onlineSongItem,
                    isFavorite = isFavorite,
                    onFavoriteChange = { isFavorite = it },
                    actionListener = queueStyleListener,
                    onAddToPlaylist = {
                        onPlaylistAddClick(onlineSongItem)
                        onDismissRequest()
                    },
                    showAddToQueue = true,
                    onDismiss = onDismissRequest,
                    showDelete = false,
                    hasDownloadedOpus = hasDownloadedOpus,
                    isConvertingToMp3 = isConvertingToMp3,
                    onConvertToMp3 = convertToMp3,
                    downloadedSongMode = downloadedSong != null,
                    onDeleteDownloaded = { onDeleteDownloaded?.invoke() }
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "MORE FOR THIS TRACK",
                    style = DsmusicTheme.typography.labelSmall.copy(
                        letterSpacing = 1.1.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(2.dp))

            val artistMenuItems = safeArtistRefs
                .distinctBy { it.id ?: it.name.trim().lowercase() }
                .map { artist ->
                    ExploreTrackMenuItem(
                        title = "Open ${artist.name}",
                        subtitle = "View artist page",
                        icon = Icons.Rounded.Person
                    ) {
                        if (!artist.id.isNullOrBlank()) {
                            exploreViewModel.loadArtist(artist.id)
                        } else {
                            exploreViewModel.loadArtistByName(artist.name)
                        }
                        onDismissRequest()
                    }
                }.ifEmpty {
                    listOf(
                        ExploreTrackMenuItem(
                            title = "Go to artist",
                            subtitle = "View artist page",
                            icon = Icons.Rounded.Person
                        ) {
                            if (!song.artistId.isNullOrBlank()) {
                                exploreViewModel.loadArtist(song.artistId)
                            } else {
                                exploreViewModel.loadArtistByName(song.artist)
                            }
                            onDismissRequest()
                        }
                    )
                }

            val albumMenuItems = if (
                !song.albumId.isNullOrBlank() || !song.albumName.isNullOrBlank()
            ) {
                listOf(
                    ExploreTrackMenuItem(
                        title = "Go to album",
                        subtitle = "Open album details",
                        icon = Icons.Rounded.Album
                    ) {
                        exploreViewModel.cameFromLibrary = onNavigateToExplore != null
                        if (!song.albumId.isNullOrBlank()) {
                            exploreViewModel.loadAlbum(song.albumId)
                        } else {
                            exploreViewModel.loadAlbumByName(song.albumName.orEmpty(), song.artist)
                        }
                        onDismissRequest()
                        onNavigateToExplore?.invoke()
                    }
                )
            } else {
                emptyList()
            }

            val hasOnlineTrack = downloadedSong == null || !downloadedSong.videoId.isNullOrBlank()
            val onlineOnlyItems = if (hasOnlineTrack) {
                listOf(
                    ExploreTrackMenuItem(
                        title = "Start mix",
                        subtitle = "Radio based on this track",
                        icon = Icons.Rounded.Radio
                    ) {
                        exploreViewModel.startRadio(song)
                        com.dsann.music.ui.SnackbarController.showMessage("Starting mix based on ${song.title}")
                        onDismissRequest()
                    }
                ) + listOf(
                    ExploreTrackMenuItem(
                        title = "View song credits",
                        subtitle = "Writers and contributors",
                        icon = Icons.Rounded.Groups
                    ) {
                        onViewCreditsClick(song)
                        onDismissRequest()
                    },
                    ExploreTrackMenuItem(
                        title = if (isPinned) "Unpin from Speed dial" else "Pin to Speed dial",
                        subtitle = if (isPinned) "Remove from quick access" else "Keep this track close",
                        icon = Icons.Rounded.PushPin
                    ) {
                        exploreViewModel.pinToSpeedDial(song)
                        val msg = if (isPinned) "Song removed from Speed Dial!" else "Song pinned to Speed Dial!"
                        com.dsann.music.ui.SnackbarController.showMessage(msg)
                        onDismissRequest()
                    },
                    ExploreTrackMenuItem(
                        title = "Not interested",
                        subtitle = "See fewer similar tracks",
                        icon = Icons.Rounded.Block
                    ) {
                        exploreViewModel.setNotInterested(song)
                        com.dsann.music.ui.SnackbarController.showMessage("We will suggest fewer songs like this")
                        onDismissRequest()
                    }
                )
            } else {
                emptyList()
            }
            val baseMenuItems = onlineOnlyItems + albumMenuItems + artistMenuItems
            val trackMenuItems = if (downloadedSong != null) {
                baseMenuItems + ExploreTrackMenuItem(
                    title = "Info",
                    subtitle = "Track details and downloaded file information",
                    icon = Icons.Rounded.Info
                ) {
                    showDownloadedInfo = true
                }
            } else {
                baseMenuItems
            }
            val menuItems = if (onRemoveFromCache != null) {
                trackMenuItems + ExploreTrackMenuItem(
                    title = "Remove from cache",
                    subtitle = "Delete the offline copy",
                    icon = Icons.Rounded.DeleteOutline
                ) {
                    onRemoveFromCache()
                    onDismissRequest()
                }
            } else {
                trackMenuItems
            }

            // Keep the compact icon/title/subtitle card structure identical to the
            // album sheet's MORE OPTIONS rows, but pair the cards in portrait as well.
            val menuColumns = 2
            val menuChunks = menuItems.chunked(menuColumns)

            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                menuChunks.forEach { chunk ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                    ) {
                        chunk.forEach { item ->
                            CollectionActionRow(
                                modifier = Modifier.weight(1f),
                                title = item.title,
                                subtitle = item.subtitle,
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        tint = DsmusicTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                enabled = true,
                                onClick = item.action
                            )
                        }
                        if (chunk.size < menuColumns) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            }
        }
    }
}



/**
 * Keep Explore and search artwork decode sizes consistent across Android versions.
 */
internal fun exploreImageRequestTargetPx(targetSizePx: Int): Int = targetSizePx

/**
 * Uses stable source + decode-size keys so an artwork tile can be restored from Coil's
 * memory/disk cache after its LazyRow item leaves composition. The size is part of the
 * memory key to prevent a small thumbnail from replacing a larger player/detail decode.
 */
internal fun buildExploreImageRequest(
    context: android.content.Context,
    data: Any?,
    targetSizePx: Int
): ImageRequest? {
    val cacheIdentity = data?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val effectiveTargetSizePx = exploreImageRequestTargetPx(targetSizePx)
    return ImageRequest.Builder(context)
        .data(data)
        .size(effectiveTargetSizePx, effectiveTargetSizePx)
        .memoryCacheKey("dsmusic-explore:$cacheIdentity@$effectiveTargetSizePx")
        .diskCacheKey("dsmusic-explore:$cacheIdentity")
        .precision(Precision.INEXACT)
        .crossfade(false)
        .build()
}

@Composable
internal fun rememberExploreImageRequest(data: Any?, targetSizePx: Int): ImageRequest? {
    val context = LocalContext.current
    val effectiveTargetSizePx = exploreImageRequestTargetPx(targetSizePx)
    return remember(context, data, effectiveTargetSizePx) {
        buildExploreImageRequest(context, data, effectiveTargetSizePx)
    }
}

private fun SearchItem.exploreThumbnailUrl(): String = (when (this) {
    is SearchItem.Song -> song.thumbnailUrl
    is SearchItem.Album -> album.thumbnailUrl
    is SearchItem.Artist -> artist.thumbnailUrl
    is SearchItem.Playlist -> playlist.thumbnailUrl
}).orEmpty()

/**
 * Decode the next off-screen cards before they enter the viewport. This gives horizontal
 * shelves screenshot-like continuity without rasterizing the entire feed into one huge bitmap.
 * collectLatest cancels obsolete prefetch work when the user rapidly swipes to another position.
 */
@Composable
private fun PrefetchExploreRowImages(
    thumbnailUrls: List<String>,
    state: LazyListState,
    targetSizePx: Int,
    lookAheadCount: Int = 2,
    itemsPerListEntry: Int = 1
) {
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    // Avoid background image decode bursts while an API 23 user is scrolling.
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) return
    LaunchedEffect(state, thumbnailUrls, targetSizePx, lookAheadCount, imageLoader) {
        snapshotFlow {
            state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.distinctUntilChanged().collectLatest { lastVisibleIndex ->
            val startIndex = (lastVisibleIndex + 1).coerceAtLeast(0) * itemsPerListEntry
            val upcomingUrls = thumbnailUrls.asSequence()
                .drop(startIndex)
                .filter { it.isNotBlank() }
                .distinct()
                .take(lookAheadCount)
                .toList()
            upcomingUrls.forEach { url ->
                val request = buildExploreImageRequest(context, url, targetSizePx)
                    ?: return@forEach
                imageLoader.execute(request)
            }
        }
    }
}
