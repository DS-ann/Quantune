package com.dsann.music.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.History
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.IconButton
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.Scaffold
import com.dsann.music.ui.theme.Text
import com.dsann.music.ui.theme.TopAppBar
import com.dsann.music.ui.theme.TopAppBarDefaults
import com.dsann.music.ui.theme.PullToRefreshBox
import com.dsann.music.ui.theme.PullToRefreshDefaults
import com.dsann.music.ui.theme.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dsann.music.data.innertube.OnlineSong
import com.dsann.music.data.innertube.SearchItem
import com.dsann.music.ui.explore.SearchResultItem

/**
 * A beautiful dedicated page showing Listening History as a list of songs,
 * strictly focused on the song playback rather than account settings.
 */

@Composable
fun HistoryScreen(
    history: List<OnlineSong>,
    currentOnlineSong: OnlineSong?,
    isLoadingStream: Boolean,
    isRefreshing: Boolean = false,
    isOfflineError: Boolean = false,
    onRefresh: () -> Unit = {},
    onBack: () -> Unit,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text("Listening History", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DsmusicTheme.colorScheme.background
                    ),
                    windowInsets = WindowInsets(0, 0, 0, 0)
                )
            }
        }
    ) { paddingValues ->
        val pullToRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(DsmusicTheme.colorScheme.background),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            if (history.isEmpty() && isOfflineError) {
                com.dsann.music.ui.theme.NoInternetState(
                    modifier = Modifier.fillMaxSize(),
                    onRetry = onRefresh
                )
            } else if (history.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = DsmusicTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No listening history yet",
                            style = DsmusicTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Songs you listen to from your account will appear here.",
                            style = DsmusicTheme.typography.bodyMedium,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
                ) {
                    itemsIndexed(
                        items = history,
                        key = { index, song -> "history-${song.videoId}-$index" }
                    ) { index, song ->
                        SearchResultItem(
                            item = SearchItem.Song(song),
                            isCurrentlyPlaying = song.videoId == currentOnlineSong?.videoId,
                            isLoading = isLoadingStream && song.videoId == currentOnlineSong?.videoId,
                            onSongMenuClick = onSongMenuClick,
                            onClick = { onSongClick(song, history, index) }
                        )
                    }
                }
            }
        }
    }
}
