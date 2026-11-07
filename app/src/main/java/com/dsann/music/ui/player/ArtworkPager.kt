package com.dsann.music.ui.player

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.DsmusicTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.imageLoader
import coil.size.Precision
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.dsann.music.R
import com.dsann.music.model.SongItem
import com.dsann.music.viewmodel.PlayerSharedViewModel
import com.dsann.music.ui.theme.observeKey
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtworkPager(
    viewModel: PlayerSharedViewModel,
    currentSong: SongItem,
    songList: List<SongItem>,
    currentSongIndex: Int,
    context: Context,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true
) {
    val artworkShapeName by context
        .getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        .observeKey("player_artwork_shape", "Rounded")
    val playerLayout by context
        .getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        .observeKey("player_layout", "Standard")
    val embeddedCurrentArtwork by viewModel.currentSongArtwork.collectAsStateWithLifecycle()
    val artworkShape = remember(artworkShapeName, playerLayout) {
        DsmCoverShapes.forPlayerLayout(playerLayout, artworkShapeName)
    }

    // Decode covers close to their display size with the same ceiling on every Android
    // version. Full-resolution artwork beyond the display scale wastes memory bandwidth.
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    // A consistent decode ceiling avoids oversized bitmaps and repeated resampling
    // across all devices, without disabling artwork features based on a RAM heuristic.
    val artworkTargetSizePx = remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        density.density
    ) {
        (minOf(configuration.screenWidthDp, configuration.screenHeightDp) * density.density)
            .roundToInt()
            .coerceIn(256, 768)
    }

    // Keep adjacent covers warm on modern devices. On API 23, speculative decoding
    // competes with local audio-file reads; the selected page still loads its own cover.
    val neighborArtworkUrls = remember(songList, currentSongIndex) {
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M) {
            emptyList()
        } else {
            listOf(currentSongIndex - 1, currentSongIndex + 1)
                .mapNotNull { songList.getOrNull(it) }
                .distinctBy { it.id }
                .mapNotNull(::resolveArtworkUrl)
        }
    }
    LaunchedEffect(neighborArtworkUrls, artworkTargetSizePx) {
        val loader = context.imageLoader
        neighborArtworkUrls.forEach { url ->
            val request = ImageRequest.Builder(context)
                .data(url.toUri())
                .size(artworkTargetSizePx, artworkTargetSizePx)
                .precision(Precision.INEXACT)
                .crossfade(false)
                .build()
            loader.enqueue(request)
        }
    }

    val pagerState = rememberPagerState(
        initialPage = currentSongIndex.coerceAtLeast(0)
    ) {
        songList.size.coerceAtLeast(1)
    }

    // Sync Pager Page with VM when active song changes externally
    LaunchedEffect(currentSongIndex) {
        if (currentSongIndex >= 0 && currentSongIndex < pagerState.pageCount && pagerState.currentPage != currentSongIndex) {
            pagerState.animateScrollToPage(currentSongIndex)
        }
    }

    // Sync VM when swiped in Pager (only when settling/finished scrolling to avoid race conditions)
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && currentSongIndex >= 0 && pagerState.currentPage != currentSongIndex) {
            viewModel.playSongAtIndex(pagerState.currentPage)
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        HorizontalPager(
            state = pagerState,
            key = { page -> "${songList.getOrNull(page)?.id ?: currentSong.id}:$page" },
            userScrollEnabled = userScrollEnabled,
            // Neighbour covers are already prefetched into Coil; compose only the visible page.
            beyondViewportPageCount = 0,
            modifier = Modifier.fillMaxSize().clip(artworkShape)
        ) { page ->
            val song = if (page == currentSongIndex) currentSong else (songList.getOrNull(page) ?: currentSong)
            val videoId = song.videoId
            val artworkUrl = remember(song.id, song.thumbnailUrl, song.albumId, song.path, videoId) {
                resolveArtworkUrl(song)
            }
            // Cached tracks keep their cover in D.S Music's persistent artwork store.
            // Downloaded-file covers still use the ViewModel bytes on Android 6, where Coil
            // cannot reliably decode embedded artwork from the active file URI.
            val savedArtworkForPage = if (
                song.id == currentSong.id &&
                (song.isCachedTrack || (
                    android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M &&
                    song.isDownloadedOffline
                ))
            ) {
                embeddedCurrentArtwork?.takeIf { it.isNotEmpty() }
            } else {
                null
            }
            val artworkModel: Any? = savedArtworkForPage ?: artworkUrl?.toUri()
            val imageRequest = remember(context, artworkModel, artworkTargetSizePx) {
                artworkModel?.let {
                    ImageRequest.Builder(context)
                        .data(it)
                        .size(artworkTargetSizePx, artworkTargetSizePx)
                        .precision(Precision.INEXACT)
                        .crossfade(false)
                        .build()
                }
            }

            var isError by remember(artworkUrl, savedArtworkForPage) { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(artworkShape)
                    .drawWithContent {
                        drawContent()
                        val side = size.minDimension
                        when (playerLayout) {
                            "Vinyl" -> {
                                // Pressed-record grooves and a centre label.
                                val ringColor = androidx.compose.ui.graphics.Color.White
                                listOf(0.28f, 0.38f, 0.47f).forEach { fraction ->
                                    drawCircle(
                                        color = ringColor.copy(alpha = 0.16f),
                                        radius = side * fraction,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = (side * 0.004f).coerceAtLeast(1f)
                                        )
                                    )
                                }
                                drawCircle(
                                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
                                    radius = side * 0.105f
                                )
                                drawCircle(
                                    color = ringColor.copy(alpha = 0.55f),
                                    radius = side * 0.105f,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = (side * 0.006f).coerceAtLeast(1f)
                                    )
                                )
                                drawCircle(
                                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                                    radius = side * 0.012f
                                )
                            }
                            "Cassette" -> {
                                // Tape window, twin reels, and a shell outline.
                                val shell = androidx.compose.ui.graphics.Color.White
                                drawRoundRect(
                                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.36f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(side * 0.045f),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = (side * 0.018f).coerceAtLeast(1.5f)
                                    )
                                )
                                drawRoundRect(
                                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.34f),
                                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.16f),
                                    size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.19f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(side * 0.025f)
                                )
                                listOf(0.34f, 0.66f).forEach { x ->
                                    val reelCenter = androidx.compose.ui.geometry.Offset(size.width * x, size.height * 0.68f)
                                    drawCircle(
                                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
                                        radius = side * 0.115f,
                                        center = reelCenter
                                    )
                                    drawCircle(
                                        color = shell.copy(alpha = 0.64f),
                                        radius = side * 0.115f,
                                        center = reelCenter,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = (side * 0.012f).coerceAtLeast(1f)
                                        )
                                    )
                                    drawCircle(
                                        color = shell.copy(alpha = 0.52f),
                                        radius = side * 0.035f,
                                        center = reelCenter
                                    )
                                }
                                drawRoundRect(
                                    color = shell.copy(alpha = 0.54f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(side * 0.045f),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = (side * 0.006f).coerceAtLeast(1f)
                                    )
                                )
                            }
                            else -> Unit
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (imageRequest != null && !isError) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        onState = { state ->
                            if (state is AsyncImagePainter.State.Error) {
                                isError = true
                            }
                        },
                        modifier = Modifier.fillMaxSize().clip(artworkShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(artworkShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        DsmusicTheme.colorScheme.surfaceVariant,
                                        DsmusicTheme.colorScheme.surfaceContainerHighest
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_music_note),
                            contentDescription = null,
                            tint = DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(96.dp)
                        )
                    }
                }
            }
        }
    }
}


private fun resolveArtworkUrl(song: SongItem): String? {
    val thumbnail = song.thumbnailUrl?.takeIf { it.isNotBlank() }
    val videoId = song.videoId
    return when {
        thumbnail != null -> SongItem.enhanceThumbnailUrl(thumbnail)
        !videoId.isNullOrBlank() -> "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
        song.albumId > 0L -> song.getAlbumArtUri()?.toString()
        !song.path.isNullOrBlank() &&
            !song.path!!.startsWith("http://", ignoreCase = true) &&
            !song.path!!.startsWith("https://", ignoreCase = true) &&
            !song.path!!.contains("://") -> song.getAlbumArtUri()?.toString()
        else -> null
    }
}
