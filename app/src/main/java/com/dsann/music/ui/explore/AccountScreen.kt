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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.VpnKey
import com.dsann.music.ui.theme.AlertDialog
import com.dsann.music.ui.theme.Button
import com.dsann.music.ui.theme.ButtonDefaults
import com.dsann.music.ui.theme.Card
import com.dsann.music.ui.theme.CardDefaults
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.IconButton
import com.dsann.music.ui.theme.ListItem
import com.dsann.music.ui.theme.ListItemDefaults
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.Scaffold
import com.dsann.music.ui.theme.Switch
import com.dsann.music.ui.theme.Text
import com.dsann.music.ui.theme.TextButton
import com.dsann.music.ui.theme.TopAppBar
import com.dsann.music.ui.theme.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dsann.music.data.innertube.AccountManager
import com.dsann.music.data.innertube.OnlineSong
import com.dsann.music.data.innertube.UserProfile
import com.dsann.music.viewmodel.AccountViewModel

/**
 * Dedicated account portal presenting library details and core auth instrumentation 
 */

@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    onBack: () -> Unit,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit = { _, _, _ -> },
    onNavigateToSignIn: () -> Unit = {}
) {
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (AccountManager.isLoggedIn(context)) {
            viewModel.refreshAll()
        }
    }
    
    Scaffold(
        modifier = Modifier.statusBarsPadding().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Account Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    DsmusicIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(DsmusicTheme.colorScheme.surface)) {
            AccountSettingsSection(
                viewModel = viewModel,
                userProfile = userProfile,
                history = history,
                onSongClick = onSongClick,
                onClose = onBack,
                onNavigateToSignIn = onNavigateToSignIn
            )
        }
    }
}


@Composable
fun AccountSettingsSection(
    viewModel: AccountViewModel,
    userProfile: UserProfile?,
    history: List<OnlineSong> = emptyList(),
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit = { _, _, _ -> },
    onClose: () -> Unit,
    onNavigateToSignIn: () -> Unit = {}
) {
    val context = LocalContext.current
    val isLoggedIn = remember(userProfile) { AccountManager.isLoggedIn(context) }
    val accountName = userProfile?.name ?: AccountManager.getAccountName(context)
    val token = remember { AccountManager.getAuthCookie(context) ?: "No persistent credentials detected" }
    val libraryOfflineError by viewModel.offlineError.collectAsStateWithLifecycle()
    val profileOfflineError by viewModel.profileOfflineError.collectAsStateWithLifecycle()
    val offlineError = libraryOfflineError || profileOfflineError

    var syncEnabled by remember { mutableStateOf(AccountManager.isSyncEnabled(context)) }
    var showTokenAlert by remember { mutableStateOf(false) }
    val explorePrefs = remember { context.getSharedPreferences("explore_prefs", 0) }
    var customProfileName by remember {
        mutableStateOf(explorePrefs.getString("custom_profile_name", "").orEmpty())
    }
    var profileNameDraft by remember { mutableStateOf(customProfileName) }
    var showProfileNameDialog by remember { mutableStateOf(false) }
    val greeting = remember {
        when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Good morning,"
            in 12..16 -> "Good afternoon,"
            else -> "Good evening,"
        }
    }
    val greetingName = customProfileName.trim().ifBlank {
        accountName.trim().ifBlank { "Listener" }
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    DsmusicTheme.colorScheme.primaryContainer,
                                    DsmusicTheme.colorScheme.secondaryContainer
                                )
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(DsmusicTheme.colorScheme.surface.copy(alpha = 0.72f))
                                    .border(
                                        2.dp,
                                        DsmusicTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                        androidx.compose.foundation.shape.CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoggedIn && !userProfile?.avatarUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = rememberExploreImageRequest(userProfile!!.avatarUrl, 96),
                                        contentDescription = "Profile picture",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        Icons.Rounded.PersonOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(38.dp),
                                        tint = DsmusicTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isLoggedIn) "YOUTUBE MUSIC ACCOUNT" else "LOCAL PROFILE",
                                    style = DsmusicTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = DsmusicTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = accountName,
                                    style = DsmusicTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = DsmusicTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isLoggedIn) {
                                    Text(
                                        text = userProfile?.email ?: "YouTube Music connected",
                                        style = DsmusicTheme.typography.bodySmall,
                                        color = DsmusicTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (isLoggedIn) {
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(DsmusicTheme.colorScheme.error)
                                        .clickable {
                                            viewModel.logout()
                                            onClose()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.PowerSettingsNew,
                                        contentDescription = "Log out",
                                        tint = DsmusicTheme.colorScheme.onError,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.width(10.dp))
                                Row(
                                    modifier = Modifier
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(DsmusicTheme.colorScheme.primaryContainer)
                                        .clickable(onClick = onNavigateToSignIn)
                                        .padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.Login,
                                        contentDescription = "Log in",
                                        tint = DsmusicTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        "Log in",
                                        style = DsmusicTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = DsmusicTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Your music space, your identity.",
                            style = DsmusicTheme.typography.bodyMedium,
                            color = DsmusicTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                        )
                    }
                }

                DsmusicCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DsmusicTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(DsmusicTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = DsmusicTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Explore greeting",
                                    style = DsmusicTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Choose the name Quantune uses to welcome you.",
                                    style = DsmusicTheme.typography.bodySmall,
                                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(DsmusicTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 16.dp, vertical = 15.dp)
                        ) {
                            Text(
                                greeting,
                                style = DsmusicTheme.typography.titleSmall,
                                color = DsmusicTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                greetingName,
                                style = DsmusicTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = DsmusicTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DsmusicOutlinedButton(
                                onClick = {
                                    profileNameDraft = customProfileName
                                    showProfileNameDialog = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (customProfileName.isBlank()) "Add profile name" else "Edit profile name")
                            }
                            if (customProfileName.isNotBlank()) {
                                DsmusicTextButton(
                                    onClick = {
                                        customProfileName = ""
                                        profileNameDraft = ""
                                        explorePrefs.edit().remove("custom_profile_name").apply()
                                    }
                                ) {
                                    Text("Reset")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (offlineError) {
            item {
                com.dsann.music.ui.theme.NoInternetState(
                    modifier = Modifier.fillMaxWidth(),
                    onRetry = { viewModel.refreshAll() },
                    compact = true,
                    message = "Your YouTube Music library couldn't be loaded. Check your connection and retry."
                )
            }
        }

        if (history.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "Recent Listening History", 
                        style = DsmusicTheme.typography.titleLarge, 
                        fontWeight = FontWeight.Bold, 
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        this.itemsIndexed(history) { index, song ->
                            Column(
                                modifier = Modifier
                                    .width(140.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSongClick(song, history, index) }
                            ) {
                                DsmusicCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = DsmusicTheme.colorScheme.surfaceVariant)
                                ) {
                                    if (!song.thumbnailUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = rememberExploreImageRequest(song.thumbnailUrl, 128),
                                            contentDescription = song.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(32.dp), tint = DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = song.title,
                                    style = DsmusicTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    style = DsmusicTheme.typography.bodySmall,
                                    color = DsmusicTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                DsmusicListItem(
                    onClick = {
                        val next = !syncEnabled
                        syncEnabled = next
                        viewModel.toggleSync(next)
                    },
                    leadingContent = { Icon(Icons.Rounded.CloudSync, null) },
                    trailingContent = {
                        DsmusicSwitchControl(checked = syncEnabled, onCheckedChange = null)
                    },
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2).shape,
                    colors = ListItemDefaults.colors(containerColor = DsmusicTheme.colorScheme.surfaceContainer),
                    content = {
                        Column {
                            Text("Sync Playlists", fontWeight = FontWeight.Medium, style = DsmusicTheme.typography.titleMedium)
                            Text("Auto-update library states with remote servers", style = DsmusicTheme.typography.bodyMedium, color = DsmusicTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )

                DsmusicListItem(
                    onClick = { showTokenAlert = true },
                    leadingContent = { Icon(Icons.Rounded.VpnKey, null) },
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2).shape,
                    colors = ListItemDefaults.colors(containerColor = DsmusicTheme.colorScheme.surfaceContainer),
                    content = {
                        Column {
                            Text("Diagnostics Tool", fontWeight = FontWeight.Medium, style = DsmusicTheme.typography.titleMedium)
                            Text("Inspect background security context", style = DsmusicTheme.typography.bodyMedium, color = DsmusicTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            
            if (!isLoggedIn) {
                DsmusicButton(
                    onClick = onNavigateToSignIn,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DsmusicTheme.colorScheme.primary,
                        contentColor = DsmusicTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Login, null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Sign In to YouTube Music", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showProfileNameDialog) {
        AlertDialog(
            onDismissRequest = { showProfileNameDialog = false },
            title = { Text("Choose your profile name") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "This name appears in your Explore greeting. It does not change your YouTube Music account.",
                        style = DsmusicTheme.typography.bodyMedium,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                    BasicTextField(
                        value = profileNameDraft,
                        onValueChange = { profileNameDraft = it.take(32) },
                        singleLine = true,
                        textStyle = DsmusicTheme.typography.bodyLarge.copy(
                            color = DsmusicTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DsmusicTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        decorationBox = { innerTextField ->
                            Box {
                                if (profileNameDraft.isBlank()) {
                                    Text(
                                        "Enter the name you want to see",
                                        style = DsmusicTheme.typography.bodyLarge,
                                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            },
            confirmButton = {
                DsmusicTextButton(
                    onClick = {
                        val cleanedName = profileNameDraft.trim()
                        customProfileName = cleanedName
                        if (cleanedName.isBlank()) {
                            explorePrefs.edit().remove("custom_profile_name").apply()
                        } else {
                            explorePrefs.edit().putString("custom_profile_name", cleanedName).apply()
                        }
                        showProfileNameDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                DsmusicTextButton(onClick = { showProfileNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showTokenAlert) {
        AlertDialog(
            onDismissRequest = { showTokenAlert = false },
            title = { Text("Authentication Signature") },
            text = { Text(token, fontSize = 11.sp, style = DsmusicTheme.typography.labelSmall) },
            confirmButton = {
                DsmusicTextButton(onClick = { showTokenAlert = false }) { Text("Done") }
            }
        )
    }
}

