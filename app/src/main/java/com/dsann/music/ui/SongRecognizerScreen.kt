package com.dsann.music.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Mic
import com.dsann.music.data.features.RecognizedSong
import com.dsann.music.data.features.SongRecognitionService
import com.dsann.music.data.innertube.SearchFilter
import com.dsann.music.data.innertube.SearchItem
import com.dsann.music.data.innertube.YouTubeMusic
import com.dsann.music.model.SongItem
import java.util.Locale
import com.dsann.music.ui.theme.DsmusicButton
import com.dsann.music.ui.theme.DsmusicCard
import com.dsann.music.ui.theme.DsmusicIconButton
import com.dsann.music.ui.theme.DsmusicOutlinedButton
import com.dsann.music.ui.theme.DsmusicPageHeading
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SongRecognizerScreen(
    onBack: () -> Unit,
    onPlaySong: (SongItem) -> Unit,
    onPlayNextSong: (SongItem) -> Unit,
    onAddToQueue: (SongItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var recognitionBusy by remember { mutableStateOf(false) }
    var recognitionMessage by remember {
        mutableStateOf("Ready when you are. Play music nearby, then start listening.")
    }
    var recognizedSong by remember { mutableStateOf<RecognizedSong?>(null) }
    var playableSong by remember { mutableStateOf<SongItem?>(null) }
    var resolvingPlayableMatch by remember { mutableStateOf(false) }

    val startRecognition: () -> Unit = {
        if (!recognitionBusy) {
            recognitionBusy = true
            recognizedSong = null
            playableSong = null
            resolvingPlayableMatch = false
            recognitionMessage = "Listening for 12 seconds…"
            scope.launch {
                try {
                    val match = SongRecognitionService.recognize(context) { stage ->
                        recognitionMessage = stage
                    }
                    recognizedSong = match
                    resolvingPlayableMatch = true
                    recognitionMessage = "Track identified. Finding it in D.S Music…"
                    playableSong = try {
                        resolveRecognizedSongInDsmusic(match)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                    recognitionMessage = if (playableSong != null) {
                        "Track identified and matched in D.S Music."
                    } else {
                        "Track identified, but no playable D.S Music match was found."
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    recognitionMessage = error.message ?: "Recognition failed. Try again."
                } finally {
                    resolvingPlayableMatch = false
                    recognitionBusy = false
                }
            }
        }
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecognition()
        else recognitionMessage = "Microphone permission is required to identify nearby music."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsmusicTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DsmusicPageHeading(
                title = "Song Recognizer",
                subtitle = "Find the music playing around you.",
                kicker = "AUDIO MATCH",
                modifier = Modifier.weight(1f)
            )
            DsmusicIconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(42.dp)
                    .background(DsmusicTheme.colorScheme.surfaceContainerLow, CircleShape),
                shape = CircleShape
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = DsmusicTheme.colorScheme.onSurface
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            DsmusicTheme.colorScheme.primary.copy(alpha = 0.94f),
                            DsmusicTheme.colorScheme.secondary.copy(alpha = 0.88f),
                            DsmusicTheme.colorScheme.tertiary.copy(alpha = 0.82f)
                        )
                    )
                )
                .padding(vertical = 28.dp, horizontal = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(DsmusicTheme.colorScheme.onPrimary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = DsmusicTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Text(
                    if (recognitionBusy) "Listening to the room" else "Ready to recognize",
                    style = DsmusicTheme.typography.titleLarge,
                    color = DsmusicTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Keep the music audible while the short sample is captured.",
                    style = DsmusicTheme.typography.bodyMedium,
                    color = DsmusicTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
                )
                DsmusicButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) startRecognition()
                        else microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    enabled = !recognitionBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (recognitionBusy) "Recognizing…" else "Start recognition")
                }
            }
        }

        DsmusicCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Recognition status", style = DsmusicTheme.typography.titleMedium)
                Text(
                    recognitionMessage,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )
                if (recognitionBusy) {
                    Text(
                        "Capture · fingerprint · match",
                        style = DsmusicTheme.typography.labelSmall,
                        color = DsmusicTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        recognizedSong?.let { song ->
            val metadata = listOfNotNull(
                song.album?.takeIf { it.isNotBlank() }?.let { "Album · $it" },
                song.releaseDate?.takeIf { it.isNotBlank() }?.let { "Released · $it" },
                song.genre?.takeIf { it.isNotBlank() }
            ).joinToString("  ·  ")
            val identifiers = listOfNotNull(
                song.label?.takeIf { it.isNotBlank() }?.let { "Label · $it" },
                song.isrc?.takeIf { it.isNotBlank() }?.let { "ISRC · $it" }
            ).joinToString("    ")
            val appleUrl = song.appleMusicUrl?.takeIf { it.isNotBlank() }
            val spotifyUrl = song.spotifyUrl?.takeIf { it.isNotBlank() }
            val detailsUrl = (song.shazamUrl ?: song.songUrl)?.takeIf { it.isNotBlank() }
            val resolvedAppSong = playableSong

            DsmusicCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(23.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 23.dp, topEnd = 23.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        DsmusicTheme.colorScheme.primary.copy(alpha = 0.92f),
                                        DsmusicTheme.colorScheme.secondary.copy(alpha = 0.82f),
                                        DsmusicTheme.colorScheme.tertiary.copy(alpha = 0.70f)
                                    )
                                )
                            )
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "MATCH FOUND",
                                    style = DsmusicTheme.typography.labelSmall,
                                    color = DsmusicTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.1.sp
                                )
                                Spacer(Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(DsmusicTheme.colorScheme.onPrimary.copy(alpha = 0.16f))
                                        .padding(horizontal = 9.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        if (resolvingPlayableMatch) "MATCHING IN APP"
                                        else if (resolvedAppSong != null) "PLAYABLE"
                                        else "IDENTIFIED",
                                        style = DsmusicTheme.typography.labelSmall,
                                        color = DsmusicTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(112.dp)
                                        .clip(RoundedCornerShape(17.dp))
                                        .background(DsmusicTheme.colorScheme.onPrimary.copy(alpha = 0.13f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!song.coverArtUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = song.coverArtUrl,
                                            contentDescription = "Recognized song artwork",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            Icons.Rounded.Mic,
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp),
                                            tint = DsmusicTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        song.title.ifBlank { "Unknown title" },
                                        style = DsmusicTheme.typography.titleLarge,
                                        color = DsmusicTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 3
                                    )
                                    Text(
                                        song.artist.ifBlank { "Unknown artist" },
                                        style = DsmusicTheme.typography.bodyMedium,
                                        color = DsmusicTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
                                        maxLines = 2
                                    )
                                }
                            }
                            if (metadata.isNotBlank()) {
                                Text(
                                    metadata,
                                    style = DsmusicTheme.typography.labelSmall,
                                    color = DsmusicTheme.colorScheme.onPrimary.copy(alpha = 0.86f),
                                    maxLines = 2
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (resolvingPlayableMatch) {
                            Text(
                                "Finding a playable version in D.S Music…",
                                style = DsmusicTheme.typography.bodySmall,
                                color = DsmusicTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (resolvedAppSong != null) {
                            Text(
                                "READY TO PLAY IN D.S MUSIC",
                                style = DsmusicTheme.typography.labelSmall,
                                color = DsmusicTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.7.sp
                            )
                            DsmusicButton(
                                onClick = { onPlaySong(resolvedAppSong) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Play now")
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DsmusicOutlinedButton(
                                    onClick = { onPlayNextSong(resolvedAppSong) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Play next")
                                }
                                DsmusicOutlinedButton(
                                    onClick = { onAddToQueue(resolvedAppSong) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Add to queue")
                                }
                            }
                        } else {
                            Text(
                                "The track was identified, but no close playable match was found in D.S Music. External track links are still available.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DsmusicTheme.colorScheme.surfaceContainerLow)
                                    .padding(10.dp),
                                style = DsmusicTheme.typography.bodySmall,
                                color = DsmusicTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (appleUrl != null || spotifyUrl != null || detailsUrl != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                appleUrl?.let { url ->
                                    DsmusicOutlinedButton(
                                        onClick = { openRecognizerUrl(context, url) },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Apple Music") }
                                }
                                spotifyUrl?.let { url ->
                                    DsmusicOutlinedButton(
                                        onClick = { openRecognizerUrl(context, url) },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Spotify") }
                                }
                                detailsUrl?.let { url ->
                                    DsmusicOutlinedButton(
                                        onClick = { openRecognizerUrl(context, url) },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Details") }
                                }
                            }
                        }

                        if (identifiers.isNotBlank()) {
                            Text(
                                identifiers,
                                style = DsmusicTheme.typography.labelSmall,
                                color = DsmusicTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }

                        DsmusicOutlinedButton(
                            onClick = startRecognition,
                            enabled = !recognitionBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Recognize another song") }
                    }
                }
            }

            Text(
                recognitionMessage,
                style = DsmusicTheme.typography.labelSmall,
                color = DsmusicTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            "The recording is fingerprinted on this device. Only the compact fingerprint and basic request metadata are sent for matching; microphone audio and device location are not uploaded.",
            style = DsmusicTheme.typography.bodySmall,
            color = DsmusicTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(176.dp))
    }
}

private suspend fun resolveRecognizedSongInDsmusic(
    recognizedSong: RecognizedSong
): SongItem? {
    val query = listOf(recognizedSong.title, recognizedSong.artist)
        .filter { it.isNotBlank() }
        .joinToString(" ")
    if (query.isBlank()) return null

    val candidates = YouTubeMusic.search(query, SearchFilter.SONGS)
        .getOrNull()
        ?.items
        ?.filterIsInstance<SearchItem.Song>()
        ?.map { it.song }
        ?.filter { it.videoId.isNotBlank() }
        .orEmpty()
    if (candidates.isEmpty()) return null

    val exactVideoMatch = recognizedSong.youtubeVideoId
        ?.takeIf { it.isNotBlank() }
        ?.let { videoId -> candidates.firstOrNull { it.videoId == videoId } }

    fun tokens(value: String): Set<String> = value
        .lowercase(Locale.ROOT)
        .replace(Regex("\\([^)]*\\)|\\[[^]]*\\]"), " ")
        .replace(
            Regex("\\b(official|audio|video|lyrics?|visualizer|topic|hd|4k)\\b"),
            " "
        )
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length > 1 }
        .toSet()

    fun overlap(expected: Set<String>, actual: Set<String>): Float {
        if (expected.isEmpty()) return 0f
        return expected.count { it in actual }.toFloat() / expected.size.toFloat()
    }

    val expectedTitle = tokens(recognizedSong.title)
    val expectedArtist = if (
        recognizedSong.artist.isBlank() ||
        recognizedSong.artist.equals("Unknown artist", ignoreCase = true)
    ) emptySet() else tokens(recognizedSong.artist)

    val selected = exactVideoMatch ?: candidates
        .map { candidate ->
            val titleScore = overlap(expectedTitle, tokens(candidate.title))
            val artistScore = overlap(expectedArtist, tokens(candidate.artist))
            Triple(candidate, titleScore, artistScore)
        }
        .maxByOrNull { (_, titleScore, artistScore) ->
            titleScore * 0.78f + artistScore * 0.22f
        }
        ?.takeIf { (_, titleScore, artistScore) ->
            titleScore >= 0.85f && (expectedArtist.isEmpty() || artistScore >= 0.5f)
        }
        ?.first
        ?: return null

    return SongItem.createOnlineSong(
        selected.videoId,
        selected.title,
        selected.artist,
        "https://music.youtube.com/watch?v=${selected.videoId}",
        selected.durationMs,
        selected.thumbnailUrl,
        selected.artistId,
        selected.albumName,
        selected.artistRefs
    )
}

private fun openRecognizerUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
