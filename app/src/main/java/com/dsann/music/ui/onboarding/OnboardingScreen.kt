@file:Suppress("DEPRECATION")

package com.dsann.music.ui.onboarding
    
    import android.Manifest
    import android.annotation.SuppressLint
    import android.app.Activity
    import android.content.Context
    import android.content.Intent
    import android.content.pm.PackageManager
    import android.net.Uri
    import android.os.Build
    import android.provider.Settings
    import androidx.activity.compose.rememberLauncherForActivityResult
    import androidx.activity.result.contract.ActivityResultContracts
    import androidx.compose.animation.AnimatedContent
    import androidx.compose.animation.SizeTransform
    import androidx.compose.animation.core.FastOutSlowInEasing
    import androidx.compose.animation.core.Spring
    import androidx.compose.animation.core.animateFloat
    import androidx.compose.animation.core.animateFloatAsState
    import androidx.compose.animation.core.spring
    import androidx.compose.animation.core.tween
    import androidx.compose.animation.fadeIn
    import androidx.compose.animation.fadeOut
    import androidx.compose.animation.slideInVertically
    import androidx.compose.animation.slideOutVertically
    import androidx.compose.animation.togetherWith
    import androidx.compose.foundation.ExperimentalFoundationApi
    import androidx.compose.foundation.Image
    import androidx.compose.foundation.background
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.rememberScrollState
    import androidx.compose.foundation.verticalScroll
    import androidx.compose.foundation.layout.Arrangement
    import androidx.compose.foundation.layout.Box
    import androidx.compose.foundation.layout.Column
    import androidx.compose.foundation.layout.Row
    import androidx.compose.foundation.layout.Spacer
    import androidx.compose.foundation.layout.aspectRatio
    import androidx.compose.foundation.layout.fillMaxSize
    import androidx.compose.foundation.layout.fillMaxWidth
    import androidx.compose.foundation.layout.height
    import androidx.compose.foundation.layout.heightIn
    import androidx.compose.foundation.layout.navigationBarsPadding
    import androidx.compose.foundation.layout.padding
    import androidx.compose.foundation.layout.size
    import androidx.compose.foundation.layout.statusBarsPadding
    import androidx.compose.foundation.layout.width
    import androidx.compose.foundation.pager.HorizontalPager
    import androidx.compose.foundation.pager.PagerState
    import androidx.compose.foundation.pager.rememberPagerState
    import androidx.compose.foundation.shape.CircleShape
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.rounded.AccountCircle
    import androidx.compose.material.icons.rounded.ArrowBack
    import androidx.compose.material.icons.rounded.ArrowForward
    import androidx.compose.material.icons.rounded.Check
    import androidx.compose.material.icons.rounded.CheckCircle
    import androidx.compose.material.icons.rounded.DarkMode
    import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
    import androidx.compose.material.icons.rounded.Headphones
    import androidx.compose.material.icons.rounded.Explore
    import androidx.compose.material.icons.rounded.LibraryMusic
    import androidx.compose.material.icons.rounded.LightMode
    import androidx.compose.material.icons.rounded.Notifications
    import androidx.compose.material.icons.rounded.MusicNote
    import androidx.compose.material.icons.rounded.Palette
    import androidx.compose.material.icons.rounded.Search
    import androidx.compose.material.icons.rounded.Tune
    import androidx.compose.material.icons.rounded.Settings
    import com.dsann.music.ui.theme.Card
    import com.dsann.music.ui.theme.CardDefaults
        import com.dsann.music.ui.theme.FloatingActionButton
    import com.dsann.music.ui.theme.FloatingActionButtonDefaults
    import com.dsann.music.ui.theme.Icon
    import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.player.rememberAppAnimationFrameTimeNanos
    import com.dsann.music.ui.theme.Scaffold
    import com.dsann.music.ui.theme.Surface
    import com.dsann.music.ui.theme.Switch
    import com.dsann.music.ui.theme.Text
    import androidx.compose.runtime.Composable
    import androidx.compose.runtime.DisposableEffect
    import androidx.compose.runtime.LaunchedEffect
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.mutableStateOf
    import androidx.compose.runtime.remember
    import androidx.compose.runtime.rememberCoroutineScope
    import androidx.compose.runtime.saveable.rememberSaveable
    import androidx.compose.runtime.setValue
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.clip
    import androidx.compose.foundation.border
    import androidx.compose.ui.draw.rotate
    import androidx.compose.ui.draw.shadow
    import androidx.compose.ui.graphics.Brush
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.graphics.graphicsLayer
    import androidx.compose.ui.graphics.vector.ImageVector
    import androidx.compose.ui.layout.ContentScale
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.res.painterResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import androidx.core.content.ContextCompat
    import androidx.lifecycle.Lifecycle
    import androidx.lifecycle.LifecycleEventObserver
    import androidx.lifecycle.compose.LocalLifecycleOwner
    import com.dsann.music.R
import com.dsann.music.applyDsmusicNightMode
    import kotlinx.coroutines.launch
    import androidx.core.content.edit

@OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun OnboardingScreen(
        onComplete: () -> Unit
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val pagerState = rememberPagerState(pageCount = { 8 })
        val prefs = remember { context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE) }
        
        var isLoggedIn by remember { mutableStateOf(com.dsann.music.data.innertube.AccountManager.isLoggedIn(context)) }
        var userName by remember { mutableStateOf("Connected User") }
        var userProfileUrl by remember { mutableStateOf<String?>(null) }
        
        LaunchedEffect(isLoggedIn) {
            if (isLoggedIn) {
                val result = com.dsann.music.data.innertube.YouTubeMusic.accountProfile()
                userName = result.getOrNull()?.name ?: "Connected User"
                userProfileUrl = result.getOrNull()?.avatarUrl
            }
        }
        
        var themeMode by rememberSaveable { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }
        var hideNavLabels by rememberSaveable { mutableStateOf(prefs.getBoolean("hide_nav_labels", false)) }
    
        val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        val notifPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
        val activity = context as? Activity
        val lifecycleOwner = LocalLifecycleOwner.current

        val audioPermissionRequestedKey = "onboarding_audio_permission_requested"
        val notifPermissionRequestedKey = "onboarding_notification_permission_requested"

        var audioGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) }
        var notifGranted by remember { mutableStateOf(notifPermission?.let { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } ?: true) }
        var audioNeedsSettings by remember { mutableStateOf(false) }
        var notifNeedsSettings by remember { mutableStateOf(false) }

        fun permissionNeedsSettings(permission: String, requestedKey: String): Boolean {
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                return false
            }
            if (!prefs.getBoolean(requestedKey, false)) return false

            // Android stops showing the permission dialog after repeated denials or an
            // explicit "don't ask again" choice. In that state, the only valid retry is
            // through this app's system settings page.
            return activity?.shouldShowRequestPermissionRationale(permission) == false
        }

        fun refreshPermissionState() {
            audioGranted = ContextCompat.checkSelfPermission(
                context,
                audioPermission
            ) == PackageManager.PERMISSION_GRANTED
            notifGranted = notifPermission?.let {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            } ?: true
            audioNeedsSettings = permissionNeedsSettings(audioPermission, audioPermissionRequestedKey)
            notifNeedsSettings = notifPermission?.let {
                permissionNeedsSettings(it, notifPermissionRequestedKey)
            } ?: false
        }

        fun openAppPermissionSettings() {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) {
            // Do not trust the result map alone; re-read the platform permission state.
            refreshPermissionState()
        }

        fun requestMissingPermissions(requests: List<Pair<String, String>>) {
            val missing = requests.filter { (permission, _) ->
                ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isEmpty()) {
                refreshPermissionState()
                return
            }

            if (missing.any { (permission, key) -> permissionNeedsSettings(permission, key) }) {
                openAppPermissionSettings()
                return
            }

            // Record that a request was attempted before opening Android's dialog so the
            // next denial can be distinguished from the first request.
            prefs.edit {
                missing.forEach { (_, key) -> putBoolean(key, true) }
            }
            permissionLauncher.launch(missing.map { it.first }.toTypedArray())
        }

        LaunchedEffect(Unit) {
            refreshPermissionState()
        }

        DisposableEffect(lifecycleOwner, activity) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    // Settings changes are applied when the user returns to onboarding.
                    refreshPermissionState()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    
        Scaffold(
            bottomBar = {
                // Keep Continue tappable on the permissions page. If access is missing,
                // the click below launches the corresponding Android permission prompt.
                val isNextEnabled = true
                SetupBottomBar(
                    pagerState = pagerState,
                    isNextEnabled = isNextEnabled,
                    onNextClicked = {
                        if (pagerState.currentPage == 4) {
                            val missingPermissions = mutableListOf<Pair<String, String>>()
                            if (!audioGranted) {
                                missingPermissions.add(audioPermission to audioPermissionRequestedKey)
                            }
                            if (notifPermission != null && !notifGranted) {
                                missingPermissions.add(notifPermission to notifPermissionRequestedKey)
                            }

                            if (missingPermissions.isNotEmpty()) {
                                requestMissingPermissions(missingPermissions)
                            } else if (pagerState.currentPage < pagerState.pageCount - 1) {
                                scope.launch {
                                    pagerState.animateScrollToPage(
                                        page = pagerState.currentPage + 1,
                                        animationSpec = spring(
                                            dampingRatio = 0.8f,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                }
                            }
                        } else if (pagerState.currentPage < pagerState.pageCount - 1) {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page = pagerState.currentPage + 1,
                                    animationSpec = spring(
                                        dampingRatio = 0.8f,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                        }
                    },
                    onBackClicked = {
                        if (pagerState.currentPage > 0) {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page = pagerState.currentPage - 1,
                                    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
                                )
                            }
                        }
                    },
                    onFinishClicked = {
                        prefs.edit {
                            putBoolean("has_seen_onboarding_1_8", true)
                                .putString("theme_mode", themeMode)
                                .putBoolean("hide_nav_labels", hideNavLabels)
                        }
                        onComplete()
                    }
                )
            }
        ) { paddingValues ->
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(paddingValues)
            ) { page ->
                val pageOffsetProvider = {
                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                }
    
                Box(modifier = Modifier.fillMaxSize()) {
                    when (page) {
                        0 -> WelcomePage(pageOffsetProvider)
                        1 -> EcosystemPage(pageOffsetProvider)
                        2 -> FeatureListPage(pageOffsetProvider)
                        3 -> SignInPage(
                            pageOffsetProvider = pageOffsetProvider,
                            isLoggedIn = isLoggedIn,
                            userName = userName,
                            userProfileUrl = userProfileUrl,
                            onLoginSuccess = { isLoggedIn = true },
                            onGuestClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(4) 
                                }
                            }
                        )
                        4 -> PermissionsPage(
                            pageOffsetProvider = pageOffsetProvider,
                            audioGranted = audioGranted,
                            notifGranted = notifGranted,
                            audioNeedsSettings = audioNeedsSettings,
                            notifNeedsSettings = notifNeedsSettings,
                            onRequestAudio = {
                                requestMissingPermissions(listOf(audioPermission to audioPermissionRequestedKey))
                            },
                            onRequestNotif = {
                                notifPermission?.let {
                                    requestMissingPermissions(listOf(it to notifPermissionRequestedKey))
                                }
                            }
                        )
                        5 -> ThemeSelectionPage(
                            pageOffsetProvider = pageOffsetProvider,
                            themeMode = themeMode,
                            onThemeChanged = {
                                themeMode = it
                                prefs.edit { putString("theme_mode", it) }
                                applyDsmusicNightMode(it)
                            }
                        )
                        6 -> NavigationStylePage(
                            pageOffsetProvider = pageOffsetProvider,
                            hideNavLabels = hideNavLabels,
                            onHideNavLabelsChanged = {
                                hideNavLabels = it
                                prefs.edit { putBoolean("hide_nav_labels", it) }
                            }
                        )
                        7 -> FinishPage(
                            pageOffsetProvider = pageOffsetProvider,
                            isLoggedIn = isLoggedIn,
                            userProfileUrl = userProfileUrl
                        )
                    }
                }
            }
        }
    }
    
    @SuppressLint("ConfigurationScreenWidthHeight")
    @Composable
    fun ImmersivePageLayout(
        pageOffsetProvider: () -> Float,
        heroIcon: ImageVector,
        heroTag: String,
        pageNumber: Int,
        imageUrl: String? = null,
        content: @Composable () -> Unit
    ) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp
        val horizontalPadding = if (screenWidth < 360.dp) 20.dp else if (screenWidth > 480.dp) 32.dp else 26.dp
        val verticalPadding = if (screenWidth < 360.dp) 12.dp else 16.dp
        val heroHeight = (screenWidth * 0.76f).coerceIn(224.dp, 304.dp)
        val footerHeight = (configuration.screenHeightDp.dp * 0.18f).coerceIn(96.dp, 148.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = horizontalPadding, end = horizontalPadding, top = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(DsmusicTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "QUANTUNE",
                    style = DsmusicTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.8.sp,
                    color = DsmusicTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "YOUR FIRST SETUP",
                    style = DsmusicTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                    color = DsmusicTheme.colorScheme.primary
                )
            }

            OnboardingHeroArtwork(
                icon = heroIcon,
                tag = heroTag,
                pageNumber = pageNumber,
                imageUrl = imageUrl,
                pageOffsetProvider = pageOffsetProvider,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .padding(horizontal = horizontalPadding, vertical = 10.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.Start
            ) {
                content()
            }

            OnboardingFeatureDock(
                pageNumber = pageNumber,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(footerHeight)
                    .padding(horizontal = horizontalPadding, vertical = 4.dp)
            )
        }
    }

    @Composable
    private fun OnboardingFeatureDock(
        pageNumber: Int,
        modifier: Modifier = Modifier
    ) {
        val colors = DsmusicTheme.colorScheme
        val features = listOf(
            Icons.Rounded.Headphones to "STREAM",
            Icons.Rounded.LibraryMusic to "LOCAL",
            Icons.Rounded.GraphicEq to "DISCOVER"
        )
        val activeFeature = (pageNumber - 1).coerceAtLeast(0) % features.size
        val waveformHeights = listOf(8.dp, 14.dp, 21.dp, 12.dp, 17.dp)

        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MADE FOR EVERY WAY YOU LISTEN",
                    style = DsmusicTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    waveformHeights.forEachIndexed { index, barHeight ->
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(barHeight)
                                .clip(CircleShape)
                                .background(
                                    when (index % 3) {
                                        0 -> colors.primary
                                        1 -> colors.secondary
                                        else -> colors.tertiary
                                    }.copy(alpha = 0.78f)
                                )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(9.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                features.forEachIndexed { index, feature ->
                    val selected = index == activeFeature
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(15.dp),
                        color = if (selected) colors.primaryContainer.copy(alpha = 0.86f)
                            else colors.surfaceContainerHigh.copy(alpha = 0.76f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 9.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = feature.first,
                                contentDescription = null,
                                tint = if (selected) colors.onPrimaryContainer else colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = feature.second,
                                style = DsmusicTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                letterSpacing = 0.7.sp,
                                color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun OnboardingHeroArtwork(
        icon: ImageVector,
        tag: String,
        pageNumber: Int,
        imageUrl: String?,
        pageOffsetProvider: () -> Float,
        modifier: Modifier = Modifier
    ) {
        val colors = DsmusicTheme.colorScheme
        val heroShape = RoundedCornerShape(32.dp)
        val heroBrush = Brush.linearGradient(
            colors = listOf(
                colors.surfaceContainerHigh,
                colors.surfaceContainer,
                colors.primaryContainer.copy(alpha = 0.72f)
            )
        )
        val equalizerHeights = listOf(10.dp, 19.dp, 28.dp, 16.dp, 24.dp, 12.dp, 20.dp)

        Box(
            modifier = modifier
                .clip(heroShape)
                .background(heroBrush, heroShape)
                .border(1.dp, colors.outlineVariant.copy(alpha = 0.48f), heroShape)
                .graphicsLayer {
                    val offset = pageOffsetProvider()
                    translationX = offset * 18f
                    rotationZ = offset * -0.7f
                    alpha = 1f - kotlin.math.abs(offset * 0.7f)
                }
        ) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .align(Alignment.TopEnd)
                    .graphicsLayer { translationX = 82f; translationY = -92f }
                    .background(colors.primary.copy(alpha = 0.09f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(144.dp)
                    .align(Alignment.BottomStart)
                    .graphicsLayer { translationX = -55f; translationY = 52f }
                    .border(1.dp, colors.secondary.copy(alpha = 0.18f), CircleShape)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = colors.surface.copy(alpha = 0.78f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LISTEN YOUR WAY",
                                style = DsmusicTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.7.sp,
                                color = colors.onSurface
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${pageNumber.toString().padStart(2, '0')} / 08",
                        style = DsmusicTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = colors.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .border(1.dp, colors.primary.copy(alpha = 0.20f), CircleShape)
                            .background(colors.primary.copy(alpha = 0.055f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .border(1.dp, colors.secondary.copy(alpha = 0.22f), CircleShape)
                                .background(colors.surface.copy(alpha = 0.42f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(104.dp)
                                    .shadow(12.dp, RoundedCornerShape(34.dp)),
                                shape = RoundedCornerShape(34.dp),
                                color = colors.primaryContainer
                            ) {
                                // Surface content does not guarantee that this child Box
                                // expands to the tile bounds. Fill the tile before centring
                                // the icon so it cannot sit at the top-start corner.
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (imageUrl != null) {
                                        coil.compose.AsyncImage(
                                            model = imageUrl,
                                            contentDescription = "Connected account profile",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(34.dp))
                                        )
                                    } else {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = colors.onPrimaryContainer,
                                            modifier = Modifier.size(54.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 2.dp, bottom = 10.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = colors.surfaceContainerHighest.copy(alpha = 0.96f)
                    ) {
                        Text(
                            text = "QUANTUNE",
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            style = DsmusicTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp,
                            color = colors.onSurface
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 2.dp, top = 12.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = colors.primaryContainer.copy(alpha = 0.96f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.padding(9.dp).size(20.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MADE FOR YOUR MUSIC",
                            style = DsmusicTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp,
                            color = colors.primary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = tag,
                            style = DsmusicTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Row(
                        modifier = Modifier.height(32.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        equalizerHeights.forEachIndexed { index, barHeight ->
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(barHeight)
                                    .clip(CircleShape)
                                    .background(
                                        when (index % 3) {
                                            0 -> colors.primary
                                            1 -> colors.secondary
                                            else -> colors.tertiary
                                        }.copy(alpha = 0.88f)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun WelcomePage(pageOffsetProvider: () -> Float) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            heroIcon = Icons.Rounded.MusicNote,
            pageNumber = 1,
            heroTag = "A FRESH WAY TO LISTEN"
        ) {
            Text(
                text = "Welcome\nto Quantune.",
                style = DsmusicTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                fontSize = 36.sp,
                lineHeight = 42.sp,
                letterSpacing = (-0.5).sp,
                color = DsmusicTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * 30f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "A listening space shaped around you.",
                style = DsmusicTheme.typography.headlineSmall,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                lineHeight = 28.sp,
                color = DsmusicTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * 50f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
    
    @Composable
    fun EcosystemPage(pageOffsetProvider: () -> Float) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            heroIcon = Icons.Rounded.LibraryMusic,
            pageNumber = 2,
            heroTag = "STREAM AND PLAY LOCAL"
        ) {
            Text(
                text = "All your music.\nOne place.",
                style = DsmusicTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                fontSize = 36.sp,
                lineHeight = 42.sp,
                letterSpacing = (-0.5).sp,
                color = DsmusicTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * -30f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Move between streaming, your local library, playlists and saved music in one familiar player.",
                style = DsmusicTheme.typography.headlineSmall,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                lineHeight = 28.sp,
                color = DsmusicTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * -60f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
    
    @Composable
    fun FeatureListPage(pageOffsetProvider: () -> Float) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            heroIcon = Icons.Rounded.GraphicEq,
            pageNumber = 3,
            heroTag = "SEARCH · LYRICS · OFFLINE"
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Stream",
                    style = DsmusicTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    color = DsmusicTheme.colorScheme.secondary,
                    modifier = Modifier.graphicsLayer { 
                        val offset = pageOffsetProvider()
                        translationY = offset * 20f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    }
                )
                Text(
                    text = "Discover",
                    style = DsmusicTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    color = DsmusicTheme.colorScheme.tertiary,
                    modifier = Modifier.graphicsLayer { 
                        val offset = pageOffsetProvider()
                        translationY = offset * 40f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    }
                )
                Text(
                    text = "Sing Along.",
                    style = DsmusicTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    color = DsmusicTheme.colorScheme.onBackground,
                    modifier = Modifier.graphicsLayer { 
                        val offset = pageOffsetProvider()
                        translationY = offset * 60f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Karaoke lyrics, powerful search, and offline downloads all in one place.",
                style = DsmusicTheme.typography.bodyLarge,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = DsmusicTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * 80f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
    
    
    @Composable
    fun ThemeSelectionPage(
        pageOffsetProvider: () -> Float,
        themeMode: String,
        onThemeChanged: (String) -> Unit
    ) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            heroIcon = Icons.Rounded.Palette,
            pageNumber = 6,
            heroTag = "COLOURS THAT FEEL RIGHT"
        ) {
            Text(
                text = "Make it\nfeel like yours.",
                style = DsmusicTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                fontSize = 36.sp,
                lineHeight = 42.sp,
                letterSpacing = (-0.5).sp,
                color = DsmusicTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * -30f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
    
            Spacer(modifier = Modifier.height(32.dp))
    
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    Triple("system", "System Default", Icons.Rounded.Settings),
                    Triple("dark", "Dark Mode", Icons.Rounded.DarkMode),
                    Triple("light", "Light Mode", Icons.Rounded.LightMode)
                ).forEachIndexed { index, (mode, label, _) ->
                    Card(
                        onClick = { onThemeChanged(mode) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (themeMode == mode) DsmusicTheme.colorScheme.primaryContainer else DsmusicTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .graphicsLayer {
                                val offset = pageOffsetProvider()
                                translationY = offset * (50f + (index * 40f))
                                alpha = 1f - kotlin.math.abs(offset * 1.5f)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (themeMode == mode) DsmusicTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                if (themeMode == mode) {
                                    Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = DsmusicTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = label,
                                style = DsmusicTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (themeMode == mode) DsmusicTheme.colorScheme.onPrimaryContainer else DsmusicTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    @Composable
    fun NavigationStylePage(
        pageOffsetProvider: () -> Float,
        hideNavLabels: Boolean,
        onHideNavLabelsChanged: (Boolean) -> Unit
    ) {
        val navigationItems = listOf(
            "Explore" to Icons.Rounded.Explore,
            "Search" to Icons.Rounded.Search,
            "Library" to Icons.Rounded.LibraryMusic,
            "Local Music" to Icons.Rounded.MusicNote,
            "Settings" to Icons.Rounded.Settings
        )
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            heroIcon = Icons.Rounded.Tune,
            pageNumber = 7,
            heroTag = "A NAVIGATION STYLE YOU LIKE"
        ) {
            Text(
                text = "Navigation,\nyour way.",
                style = DsmusicTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                fontSize = 36.sp,
                lineHeight = 42.sp,
                letterSpacing = (-0.5).sp,
                color = DsmusicTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * -30f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
    
            Spacer(modifier = Modifier.height(28.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .graphicsLayer {
                        val offset = pageOffsetProvider()
                        translationY = offset * 25f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    },
                shape = RoundedCornerShape(24.dp),
                color = DsmusicTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(82.dp)
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navigationItems.forEachIndexed { index, item ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == 0) DsmusicTheme.colorScheme.primaryContainer
                                        else androidx.compose.ui.graphics.Color.Transparent
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.second,
                                    contentDescription = item.first,
                                    tint = if (index == 0) DsmusicTheme.colorScheme.onPrimaryContainer
                                        else DsmusicTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            if (!hideNavLabels) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = item.first,
                                    style = DsmusicTheme.typography.labelSmall,
                                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 9.sp,
                                    color = if (index == 0) DsmusicTheme.colorScheme.onSurface
                                        else DsmusicTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
    
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .graphicsLayer {
                        val offset = pageOffsetProvider()
                        translationY = offset * 50f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    },
                shape = RoundedCornerShape(24.dp),
                color = DsmusicTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hide Nav Labels",
                            style = DsmusicTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Remove text labels from the bottom navigation bar.",
                            style = DsmusicTheme.typography.bodyMedium,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(checked = hideNavLabels, onCheckedChange = onHideNavLabelsChanged)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    @Composable
    fun PermissionsPage(
        pageOffsetProvider: () -> Float,
        audioGranted: Boolean,
        notifGranted: Boolean,
        audioNeedsSettings: Boolean,
        notifNeedsSettings: Boolean,
        onRequestAudio: () -> Unit,
        onRequestNotif: () -> Unit
    ) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            heroIcon = Icons.Rounded.FolderOpen,
            pageNumber = 5,
            heroTag = "YOUR DEVICE, YOUR LIBRARY"
        ) {
            Text(
                text = "Bring your\nmusic library in.",
                style = DsmusicTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                fontSize = 36.sp,
                lineHeight = 42.sp,
                letterSpacing = (-0.5).sp,
                color = DsmusicTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * -30f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
    
            Spacer(modifier = Modifier.height(32.dp))
    
            // Audio Permission Card
            PermissionCard(
                title = "Music Library",
                description = "Access local audio files for offline playback.",
                icon = Icons.Rounded.LibraryMusic,
                isGranted = audioGranted,
                requiresSettings = audioNeedsSettings,
                pageOffsetProvider = pageOffsetProvider,
                offsetMultiplier = 200f,
                onRequest = onRequestAudio
            )
    
            Spacer(modifier = Modifier.height(12.dp))
    
            // Notification Permission Card
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionCard(
                    title = "Playback Notifications",
                    description = "Control music from your lock screen.",
                    icon = Icons.Rounded.Notifications,
                    isGranted = notifGranted,
                    requiresSettings = notifNeedsSettings,
                    pageOffsetProvider = pageOffsetProvider,
                    offsetMultiplier = 300f,
                    onRequest = onRequestNotif
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
    
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    @Composable
    fun PermissionCard(
        title: String,
        description: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        isGranted: Boolean,
        requiresSettings: Boolean = false,
        pageOffsetProvider: () -> Float,
        offsetMultiplier: Float,
        onRequest: () -> Unit
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 0.dp, vertical = 6.dp)
                .graphicsLayer {
                    val offset = pageOffsetProvider()
                    translationY = offset * offsetMultiplier * 0.2f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                },
            shape = RoundedCornerShape(20.dp),
            color = DsmusicTheme.colorScheme.surfaceContainerHigh,
            onClick = { if (!isGranted) onRequest() }
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isGranted) DsmusicTheme.colorScheme.primary else DsmusicTheme.colorScheme.surface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Rounded.Check else icon,
                        contentDescription = "Permission icon",
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                scaleX = if (isGranted) 1.1f else 1f
                                scaleY = if (isGranted) 1.1f else 1f
                                alpha = if (isGranted) 1f else 0.8f
                            },
                        tint = if (isGranted) DsmusicTheme.colorScheme.onPrimary else DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = DsmusicTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (requiresSettings) {
                            "Permission is blocked. Open app settings to enable access."
                        } else {
                            description
                        },
                        style = DsmusicTheme.typography.bodySmall,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (!isGranted) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (requiresSettings) "SETTINGS" else "ALLOW",
                            style = DsmusicTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = DsmusicTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Icon(
                            imageVector = Icons.Rounded.ArrowForward,
                            contentDescription = if (requiresSettings) {
                                "Open app settings for $title"
                            } else {
                                "Grant $title permission"
                            },
                            modifier = Modifier.size(20.dp),
                            tint = DsmusicTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    fun SignInPage(
        pageOffsetProvider: () -> Float,
        isLoggedIn: Boolean,
        userName: String,
        userProfileUrl: String?,
        onLoginSuccess: () -> Unit,
        onGuestClick: () -> Unit
    ) {
        var showWebView by remember { mutableStateOf(false) }

        if (showWebView) {
            val accountViewModel: com.dsann.music.viewmodel.AccountViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showWebView = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                com.dsann.music.ui.explore.GoogleSignInScreen(
                    accountViewModel = accountViewModel,
                    onSignInSuccess = { 
                        showWebView = false 
                        onLoginSuccess()
                    },
                    onNavigateUp = { showWebView = false },
                    isOnboarding = true
                )
            }
        }

        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            heroIcon = Icons.Rounded.AccountCircle,
            pageNumber = 4,
            heroTag = "SYNC YOUR MUSIC ACCOUNT",
            imageUrl = if (isLoggedIn) userProfileUrl else null
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                if (isLoggedIn) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Welcome,\n$userName",
                        style = DsmusicTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        fontSize = 36.sp,
                        lineHeight = 42.sp,
                        letterSpacing = (-0.5).sp,
                        color = DsmusicTheme.colorScheme.onBackground,
                        modifier = Modifier.graphicsLayer { 
                            val offset = pageOffsetProvider()
                            translationY = offset * -30f
                            alpha = 1f - kotlin.math.abs(offset * 1.5f)
                        }
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Surface(
                        color = DsmusicTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier.graphicsLayer {
                            val offset = pageOffsetProvider()
                            translationY = offset * -20f
                            alpha = 1f - kotlin.math.abs(offset * 1.5f)
                        }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = DsmusicTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Login Successful", style = DsmusicTheme.typography.labelMedium, color = DsmusicTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(120.dp))
                } else {
                    Text(
                        text = "Connect once.\nStay in sync.",
                        style = DsmusicTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        fontSize = 36.sp,
                        lineHeight = 42.sp,
                        letterSpacing = (-0.5).sp,
                        color = DsmusicTheme.colorScheme.onBackground,
                        modifier = Modifier.graphicsLayer { 
                            val offset = pageOffsetProvider()
                            translationY = offset * -30f
                            alpha = 1f - kotlin.math.abs(offset * 1.5f)
                        }
                    )
        
                    Spacer(modifier = Modifier.height(20.dp))
        
                    Text(
                        text = "Connect your account to sync playlists, liked songs, and preferences across all your devices.",
                        style = DsmusicTheme.typography.bodyLarge,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 24.sp,
                        modifier = Modifier.graphicsLayer {
                            val offset = pageOffsetProvider()
                            translationY = offset * -20f
                            alpha = 1f - kotlin.math.abs(offset * 1.5f)
                        }
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    com.dsann.music.ui.theme.Button(
                        onClick = { showWebView = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .graphicsLayer {
                                val offset = pageOffsetProvider()
                                translationY = offset * -10f
                                alpha = 1f - kotlin.math.abs(offset * 1.5f)
                            },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_youtube_music),
                            contentDescription = "YouTube Music",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Continue with YouTube Music",
                            style = DsmusicTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    com.dsann.music.ui.theme.OutlinedButton(
                        onClick = onGuestClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .graphicsLayer {
                                val offset = pageOffsetProvider()
                                translationY = offset * -5f
                                alpha = 1f - kotlin.math.abs(offset * 1.5f)
                            },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "Continue as Guest",
                            style = DsmusicTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DsmusicTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
    
    @Composable
    fun FinishPage(
        pageOffsetProvider: () -> Float,
        isLoggedIn: Boolean,
        userProfileUrl: String?
    ) {
        val pulseFrameTimeNanos = rememberAppAnimationFrameTimeNanos(active = true)
    
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            heroIcon = Icons.Rounded.Headphones,
            pageNumber = 8,
            heroTag = "READY WHEN YOU ARE",
            imageUrl = if (isLoggedIn) userProfileUrl else null
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = DsmusicTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.graphicsLayer {
                        val offset = pageOffsetProvider()
                        translationY = offset * -30f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .graphicsLayer {
                                    val frameNanos = pulseFrameTimeNanos.value
                                    val phase = if (frameNanos > 0L) {
                                        (frameNanos % 1_000_000_000L).toDouble() / 1_000_000_000.0
                                    } else 0.0
                                    val pulse = (1.0 - kotlin.math.cos(phase * 2.0 * Math.PI)) * 0.5
                                    alpha = 0.4f + 0.6f * pulse.toFloat()
                                }
                                .background(DsmusicTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Setup Complete",
                            style = DsmusicTheme.typography.labelMedium,
                            color = DsmusicTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
    
                Text(
                    text = "You're all set.\nPress play.",
                    style = DsmusicTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    fontSize = 36.sp,
                    lineHeight = 42.sp,
                    letterSpacing = (-0.5).sp,
                    color = DsmusicTheme.colorScheme.onBackground,
                    modifier = Modifier.graphicsLayer { 
                        val offset = pageOffsetProvider()
                        translationY = offset * -20f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    }
                )
    
                Spacer(modifier = Modifier.height(20.dp))
    
                Text(
                    text = "Your setup is complete. Explore your music, shape your listening space and pick your next track.",
                    style = DsmusicTheme.typography.bodyLarge,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 24.sp,
                    modifier = Modifier.graphicsLayer {
                        val offset = pageOffsetProvider()
                        translationY = offset * -10f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    }
                )
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
    
    @Composable
    fun SetupBottomBar(
        modifier: Modifier = Modifier,
        pagerState: PagerState,
        isNextEnabled: Boolean = true,
        onNextClicked: () -> Unit,
        onBackClicked: () -> Unit,
        onFinishClicked: () -> Unit
    ) {
        val colors = DsmusicTheme.colorScheme
        val isLastPage = pagerState.currentPage == pagerState.pageCount - 1
        val progressShape = RoundedCornerShape(50)
        val dockShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)

        Surface(
            modifier = modifier
                .fillMaxWidth()
                .shadow(elevation = 14.dp, shape = dockShape, clip = true)
                .border(1.dp, colors.outlineVariant.copy(alpha = 0.42f), dockShape),
            color = colors.surfaceContainer,
            shape = dockShape
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pagerState.pageCount) { index ->
                        val isReached = index <= pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .weight(if (index == pagerState.currentPage) 2f else 1f)
                                .height(4.dp)
                                .clip(progressShape)
                                .background(
                                    if (isReached) colors.primary
                                    else colors.outlineVariant.copy(alpha = 0.50f)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pagerState.currentPage == 0) {
                        Surface(
                            modifier = Modifier.size(46.dp),
                            shape = CircleShape,
                            color = colors.primaryContainer
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = colors.onPrimaryContainer,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "QUANTUNE",
                                style = DsmusicTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.3.sp,
                                color = colors.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "WELCOME EXPERIENCE",
                                style = DsmusicTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.7.sp,
                                color = colors.onSurfaceVariant
                            )
                        }
                    } else {
                        Surface(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onBackClicked),
                            shape = CircleShape,
                            color = colors.surfaceContainerHigh
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowBack,
                                    contentDescription = "Previous step",
                                    tint = colors.onSurface,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "STEP ${(pagerState.currentPage + 1).toString().padStart(2, '0')}",
                                style = DsmusicTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp,
                                color = colors.primary
                            )
                            Text(
                                text = "OF ${pagerState.pageCount.toString().padStart(2, '0')}",
                                style = DsmusicTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Surface(
                        modifier = Modifier
                            .width(if (isLastPage) 166.dp else 150.dp)
                            .height(52.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(enabled = isLastPage || isNextEnabled) {
                                if (isLastPage) onFinishClicked()
                                else if (isNextEnabled) onNextClicked()
                            },
                        shape = RoundedCornerShape(18.dp),
                        color = when {
                            isLastPage -> colors.primary
                            isNextEnabled -> colors.primaryContainer
                            else -> colors.primaryContainer.copy(alpha = 0.38f)
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (isLastPage) "Start listening" else "Continue",
                                style = DsmusicTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isLastPage) colors.onPrimary else colors.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = if (isLastPage) Icons.Rounded.Check else Icons.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = if (isLastPage) colors.onPrimary else colors.onPrimaryContainer,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }
    }
