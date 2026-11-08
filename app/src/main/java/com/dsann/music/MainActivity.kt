@file:Suppress("DEPRECATION")

package com.dsann.music

import android.Manifest
import android.app.AlertDialog
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.LocalPageTransitionDurationMillis
import com.dsann.music.ui.theme.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dsann.music.model.SongItem
import com.dsann.music.service.AudioPlaybackService
import com.dsann.music.ui.CachedRootTabLifecycle
import com.dsann.music.ui.AudioFormatChoiceDialogHost
import com.dsann.music.ui.PlayerBottomSheetCompose
import com.dsann.music.ui.MiniPlayerHeight
import com.dsann.music.ui.SettingsScreenContent
import com.dsann.music.ui.SettingsViewModel
import com.dsann.music.ui.TagEditorScreenEntryPoint
import com.dsann.music.ui.explore.ExploreScreen
import com.dsann.music.ui.explore.GoogleSignInScreen
import com.dsann.music.ui.library.LibraryScreen
import com.dsann.music.ui.library.LocalMusicScreen
import com.dsann.music.ui.theme.DsmusicNavigationBar
import com.dsann.music.ui.theme.DsmusicNavigationDestination
import com.dsann.music.ui.theme.DsmusicAppTheme
import com.dsann.music.ui.theme.rememberDsmusicDarkTheme
import com.dsann.music.ui.onboarding.OnboardingScreen
import android.content.Context
import com.dsann.music.update.GitHubReleaseClient
import com.dsann.music.update.UpdateManager
import com.dsann.music.update.VersionUtils
import com.dsann.music.viewmodel.ExploreViewModel
import com.dsann.music.viewmodel.PlayerSharedViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val playerViewModel: PlayerSharedViewModel by viewModels()
    private val exploreViewModel: ExploreViewModel by viewModels()
    private val searchViewModel: ExploreViewModel by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(viewModelStore, defaultViewModelProviderFactory)
            .get("search", ExploreViewModel::class.java)
    }

    private var navController: NavController? = null
    private var previousDestination = "explore"
    private var isNavigating = false

    var audioService: AudioPlaybackService? = null
    var isServiceBound = false
    lateinit var updateManager: UpdateManager

    var isBottomNavVisible by mutableStateOf(true)
        private set

    fun hideBottomNavWithAnimation() {
        // Legacy nested-scroll callback: navigation is persistent now.
        isBottomNavVisible = true
    }

    fun showBottomNavWithAnimation() {
        isBottomNavVisible = true
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected")
            val binder = service as? AudioPlaybackService.LocalBinder
            audioService = binder?.getService()
            isServiceBound = true
            audioServiceStartupRequested = false
            audioService?.let {
                playerViewModel.audioService = it
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            isServiceBound = false
            audioServiceStartupRequested = false
            audioService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        setupSystemBars()

        // Keep the live ViewModel position during a configuration change. Only read the
        // persisted checkpoint when creating a fresh playback ViewModel after process death.
        if (savedInstanceState == null || playerViewModel.currentSong.value == null) {
            playerViewModel.loadLastPlaybackState()
        }

        // Media3 cache index loading can be slow on cold/large caches. Prepare it off
        // the UI thread before creating the playback service.
        prepareAndStartAudioService()
        // Permissions are now handled in Onboarding Screen

        setContent {
            val prefs = remember { getSharedPreferences("AppSettings", Context.MODE_PRIVATE) }
            val isDarkTheme = rememberDsmusicDarkTheme()
            // Theme changes are handled by Compose without recreating MainActivity.
            // Re-apply system-bar icon contrast whenever the selected theme changes.
            LaunchedEffect(isDarkTheme) {
                setupSystemBars()
            }

            val currentSong by playerViewModel.currentSong.collectAsStateWithLifecycle()
            val playerBackgroundColor by playerViewModel.playerBackgroundColor.collectAsStateWithLifecycle()
            val dynamicAlbumColor = if (currentSong != null) playerBackgroundColor else null

            DsmusicAppTheme(darkTheme = isDarkTheme, dynamicAlbumColor = dynamicAlbumColor) {
                // The platform splash is a solid colour. Follow it with a short branded
                // gradient layer so the opening transition matches the launcher artwork.
                var showStartupOverlay by remember { mutableStateOf(savedInstanceState == null) }
                val startupOverlayAlpha by animateFloatAsState(
                    targetValue = if (showStartupOverlay) 1f else 0f,
                    animationSpec = tween(durationMillis = 320),
                    label = "startupSplashAlpha"
                )
                LaunchedEffect(Unit) {
                    if (showStartupOverlay) {
                        delay(220)
                        showStartupOverlay = false
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                // The settings screen and the navigation chrome share the Activity-scoped
                // SettingsViewModel. Using the same StateFlows makes dock changes immediate
                // and prevents the dock from becoming stale while its root tab is cached.
                val navigationSettings: SettingsViewModel =
                    androidx.lifecycle.viewmodel.compose.viewModel(viewModelStoreOwner = this@MainActivity)
                val navLabelsMode by navigationSettings.navLabelsMode.collectAsStateWithLifecycle()
                val dynamicNavStyle by navigationSettings.dynamicNavStyle.collectAsStateWithLifecycle()
                val navBarStyle by navigationSettings.navBarStyle.collectAsStateWithLifecycle()
                val navBarSize by navigationSettings.navBarSize.collectAsStateWithLifecycle()
                val pageTransitionSpeed by navigationSettings.pageTransitionSpeed.collectAsStateWithLifecycle()
                val pageTransitionDurationMillis = (300f / pageTransitionSpeed.coerceIn(0.5f, 1.5f))
                    .toInt().coerceIn(200, 600)
                // Read synchronously before building NavHost. An observeKey(default=false)
                // subscription briefly routes returning users to Welcome before preferences load.
                val hasSeenOnboarding = remember(prefs) {
                    prefs.getBoolean("has_seen_onboarding_1_8", false)
                }

                val currentNavController = rememberNavController()
                val rootBackStackEntry by currentNavController.currentBackStackEntryAsState()
                val rootBackRoute = rootBackStackEntry?.destination?.route
                val rootTabRoutes = remember { setOf("explore", "search", "library", "local_music", "settings") }
                val rootTabOrder = remember { listOf("explore", "search", "library", "local_music", "settings") }
                val rootTabIndices = remember { rootTabOrder.withIndex().associate { it.value to it.index } }
                val rootTabStateHolder = rememberSaveableStateHolder()
                // Keep every visited root tab composed so returning to Library, Explore,
                // or Settings does not rebuild the whole screen. CachedRootTabLifecycle suspends
                // lifecycle-aware collectors and decorative work while a tab is inactive.
                val isLegacyAndroid6 = Build.VERSION.SDK_INT <= Build.VERSION_CODES.M
                // An alpha-zero Compose root still participates in measurement and layout.
                // On API 23 retain only the active root; SaveableStateHolder restores the
                // previous tab's saveable state without keeping its full UI tree alive.
                val maxRetainedRootTabs = if (isLegacyAndroid6) 1 else rootTabRoutes.size
                val visitedRootTabs = remember { mutableStateListOf<String>() }
                var lastRootTab by remember { mutableStateOf("explore") }
                LaunchedEffect(currentNavController) {
                    navController = currentNavController
                }

                val isPlayerExpanded by playerViewModel.isPlayerExpanded.collectAsStateWithLifecycle()
                val playerExpansionFraction by playerViewModel.playerExpansionFraction.collectAsStateWithLifecycle()
                val isPlayerBackActive = isPlayerExpanded || playerExpansionFraction > 0.01f

                var editingSong by remember { mutableStateOf<SongItem?>(null) }
                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                // Use a wide-window breakpoint: phones in landscape and tablets use a side rail.
                val useNavigationRail = configuration.screenWidthDp >= 600
                val isLandscapeLayout = configuration.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE

                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    // The portrait dock floats above the screen. Keep the host behind
                    // its outline transparent so no full-width slab appears around it.
                    containerColor = if (!useNavigationRail && !isLandscapeLayout) {
                        androidx.compose.ui.graphics.Color.Transparent
                    } else {
                        DsmusicTheme.colorScheme.background
                    },
                    bottomBarOverlay = true,
                    bottomBar = {
                        if (!useNavigationRail) {
                        val navBackStackEntry by currentNavController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination

                        val showBottomBar = currentDestination?.route in listOf("explore", "search", "library", "local_music", "settings")

                        val density = LocalDensity.current
                        val reportedBottomPadding = WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding()
                        // Respect the actual system inset. A fixed 24dp minimum left
                        // an artificial gap below the app dock when Android's navigation bar
                        // was hidden or the window reported no bottom inset.
                        val bottomPadding = reportedBottomPadding
                        val navigationBarsHeightPx = with(density) { bottomPadding.toPx() }
                        val configuredNavHeight = when (navBarSize) {
                            "Compact" -> 56.dp
                            "Tall" -> 76.dp
                            else -> 68.dp
                        }
                        val bottomNavHeight = if (dynamicNavStyle) 64.dp else configuredNavHeight
                        val bottomNavHeightPx = with(density) { bottomNavHeight.toPx() }
                        val totalSlideDistPx = bottomNavHeightPx + navigationBarsHeightPx

                        // The dock overlays the tab viewport. Hide it immediately while the
                        // full player is opening so the dock never paints over the player surface.
                        val baseTargetTranslation =
                            if (showBottomBar && !isPlayerBackActive) 0f else totalSlideDistPx

                        val animatedBaseTranslationState = animateFloatAsState(
                            targetValue = baseTargetTranslation,
                            animationSpec = if (isLegacyAndroid6) snap() else spring(
                                dampingRatio = 0.85f,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "BottomNavTranslationY"
                        )

                        val navBarHeight by animateDpAsState(
                            bottomNavHeight,
                            animationSpec = if (isLegacyAndroid6) snap() else spring(stiffness = Spring.StiffnessMediumLow),
                            label = "navBarHeight"
                        )
                        val configuredIconSize = when (navBarSize) {
                            "Compact" -> 22.dp
                            "Tall" -> 30.dp
                            else -> 26.dp
                        }
                        val navIconSize by animateDpAsState(
                            if (dynamicNavStyle) 30.dp else configuredIconSize,
                            animationSpec = if (isLegacyAndroid6) snap() else spring(stiffness = Spring.StiffnessMediumLow),
                            label = "navIconSize"
                        )
                        DsmusicNavigationBar(
                            destinations = listOf(
                                DsmusicNavigationDestination("explore", "Explore", R.drawable.ic_explore),
                                DsmusicNavigationDestination("search", "Search", R.drawable.ic_search),
                                DsmusicNavigationDestination("library", "Library", R.drawable.ic_library_music),
                                DsmusicNavigationDestination("local_music", "Local Music", R.drawable.ic_music_note),
                                DsmusicNavigationDestination("settings", "Settings", R.drawable.ic_settings)
                            ),
                            currentRoute = currentDestination?.route,
                            labelsMode = navLabelsMode,
                            iconSize = navIconSize,
                            bottomInset = bottomPadding,
                            barStyle = navBarStyle,
                            modifier = Modifier
                                .graphicsLayer {
                                    val baseTranslation = animatedBaseTranslationState.value
                                        .coerceIn(0f, totalSlideDistPx)
                                    // The dock overlays tab content, but is hidden as soon as the
                                    // full player starts opening so it cannot cover the player surface.
                                    this.translationY = baseTranslation
                                    this.alpha = if (
                                        !showBottomBar || isPlayerBackActive ||
                                        baseTranslation >= totalSlideDistPx - 1f
                                    ) 0f else 1f
                                }
                                .height(navBarHeight + bottomPadding)
                                .onGloballyPositioned { coordinates ->
                                    playerViewModel.setBottomNavHeight(coordinates.size.height.toFloat())
                                },
                            onNavigate = { route ->
                                if (currentDestination?.route == "search" && route != "search") {
                                    exploreViewModel.clearSearch()
                                }
                                if (currentDestination?.route != route) {
                                    currentNavController.navigate(route) {
                                        popUpTo(currentNavController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                        }
                    }
                ) { paddingValues ->
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalPageTransitionDurationMillis provides pageTransitionDurationMillis
                    ) {
                    val navBackStackEntry by currentNavController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    val currentRoute = currentDestination?.route
                    LaunchedEffect(currentRoute) {
                        if (currentRoute != null && currentRoute in rootTabRoutes) {
                            lastRootTab = currentRoute
                            if (!isLegacyAndroid6) {
                                visitedRootTabs.remove(currentRoute)
                                visitedRootTabs.add(currentRoute)
                                while (visitedRootTabs.size > maxRetainedRootTabs) {
                                    val leastRecentlyUsed = visitedRootTabs.firstOrNull { it != currentRoute } ?: break
                                    visitedRootTabs.remove(leastRecentlyUsed)
                                }
                            }
                            // API 23 retains only the outgoing tab during its fade-out.
                            // The incoming tab is composed only after that page is removed.
                        }
                    }
                    val activeCachedRootTab = when {
                        currentRoute != null && currentRoute in rootTabRoutes -> currentRoute
                        currentRoute == "tag_editor" || currentRoute == "google_signin" -> lastRootTab
                        else -> null
                    }

                    // Keep both pages mounted only for the short transition so their edges
                    // stay attached while the pair travels as one horizontal track.
                    var renderedRootTab by remember { mutableStateOf<String?>(null) }
                    var incomingRootTab by remember { mutableStateOf<String?>(null) }
                    var rootTabSlideDirection by remember { mutableStateOf(1f) }
                    val rootTabSlideProgress = remember { androidx.compose.animation.core.Animatable(1f) }
                    val rootTabOpacity = remember { androidx.compose.animation.core.Animatable(1f) }
                    LaunchedEffect(activeCachedRootTab) {
                        val targetRoute = activeCachedRootTab
                        if (targetRoute != null && targetRoute != renderedRootTab) {
                            val outgoingRoute = renderedRootTab
                            val outgoingIndex = outgoingRoute?.let { rootTabIndices[it] } ?: 0
                            val incomingIndex = rootTabIndices[targetRoute] ?: outgoingIndex
                            rootTabSlideDirection = if (incomingIndex >= outgoingIndex) 1f else -1f
                            if (outgoingRoute == null) {
                                if (isLegacyAndroid6) {
                                    visitedRootTabs.clear()
                                    visitedRootTabs.add(targetRoute)
                                }
                                renderedRootTab = targetRoute
                                incomingRootTab = null
                                rootTabSlideProgress.snapTo(1f)
                                rootTabOpacity.snapTo(1f)
                            } else {
                                if (isLegacyAndroid6 && targetRoute !in visitedRootTabs) {
                                    visitedRootTabs.add(targetRoute)
                                }
                                incomingRootTab = targetRoute
                                rootTabSlideProgress.snapTo(0f)
                                rootTabOpacity.snapTo(1f)
                                val firstHalf = (pageTransitionDurationMillis / 2).coerceAtLeast(1)
                                val secondHalf = (pageTransitionDurationMillis - firstHalf).coerceAtLeast(1)
                                kotlinx.coroutines.coroutineScope {
                                    launch {
                                        rootTabSlideProgress.animateTo(
                                            1f,
                                            androidx.compose.animation.core.tween(
                                                durationMillis = pageTransitionDurationMillis,
                                                easing = androidx.compose.animation.core.FastOutSlowInEasing
                                            )
                                        )
                                    }
                                    launch {
                                        rootTabOpacity.animateTo(
                                            0.5f,
                                            androidx.compose.animation.core.tween(
                                                durationMillis = firstHalf,
                                                easing = androidx.compose.animation.core.LinearEasing
                                            )
                                        )
                                        rootTabOpacity.animateTo(
                                            1f,
                                            androidx.compose.animation.core.tween(
                                                durationMillis = secondHalf,
                                                easing = androidx.compose.animation.core.LinearEasing
                                            )
                                        )
                                    }
                                }
                                renderedRootTab = targetRoute
                                incomingRootTab = null
                                rootTabSlideProgress.snapTo(1f)
                                rootTabOpacity.snapTo(1f)
                                if (isLegacyAndroid6) {
                                    visitedRootTabs.clear()
                                    visitedRootTabs.add(targetRoute)
                                }
                            }
                        }
                    }

                    LaunchedEffect(currentDestination?.route) {
                        exploreViewModel.setExplorePageActive(currentDestination?.route == "explore")
                        // Search has its own ViewModel and loads only the dedicated moods/genres
                        // catalogue. It must not trigger a second copy of the Explore home feed.
                        searchViewModel.setExplorePageActive(
                            active = currentDestination?.route == "search",
                            loadHomeOnActivate = false
                        )
                    }
                    // Keep the tab viewport and navigation dock at their normal dimensions while the player expands.
                    // PlayerBottomSheetCompose is drawn later in this Box and overlays both surfaces.
                    val showBottomBar = currentDestination?.route in listOf("explore", "search", "library", "local_music", "settings")
                    // Keep the rail persistent in wide layouts; scroll-driven bottom-bar hiding is phone-only.
                    val showNavigationRail = useNavigationRail && showBottomBar
                    val layoutDirection = LocalLayoutDirection.current
                    // Navigation-bar start inset also covers landscape side-mounted system bars.
                    val railSystemInset = WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateStartPadding(layoutDirection)
                    // Reuse dock size/style customisation for the side rail. A Docked rail
                    // attaches to the screen edge; Floating keeps a margin and rounded shell;
                    // Minimal removes the shell surface.
                    val railWidth = when (navBarSize) {
                        "Compact" -> 60.dp
                        "Tall" -> 76.dp
                        else -> 68.dp
                    }
                    val railOuterGap = if (navBarStyle == "Docked") 0.dp else 12.dp
                    val railStartPadding = railSystemInset + railOuterGap
                    val railInsetState = animateDpAsState(
                        targetValue = if (showNavigationRail) railSystemInset + railWidth + railOuterGap else 0.dp,
                        animationSpec = if (isLegacyAndroid6) snap() else spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
                        label = "NavigationRailInset"
                    )
                    // Springs can overshoot their zero target slightly. Never feed a
                    // negative animated inset into Compose's non-negative padding modifier.
                    val railInset = railInsetState.value.coerceAtLeast(0.dp)

                    // The full player covers the navigation rail after expansion. Use a
                    // single state change instead of measuring the entire retained NavHost
                    // on every animation frame; the sheet itself owns the smooth transition.

                    val systemBottomInset = WindowInsets.navigationBars.asPaddingValues()
                        .calculateBottomPadding()
                    val miniPlayerHeight = if (
                        configuration.screenWidthDp > configuration.screenHeightDp
                    ) MiniPlayerHeight * 0.8f else MiniPlayerHeight

                    val configuredNavHeight = when (navBarSize) {
                        "Compact" -> 56.dp
                        "Tall" -> 76.dp
                        else -> 68.dp
                    }
                    // Keep the page viewport above the visible navigation dock at all times.
                    // The custom Scaffold draws the dock as an overlay, so its height must be
                    // reserved explicitly instead of relying on Scaffold's content padding.
                    val bottomDockInset = when {
                        useNavigationRail -> systemBottomInset
                        currentRoute in rootTabRoutes ->
                            (if (dynamicNavStyle) 64.dp else configuredNavHeight) + systemBottomInset
                        else -> 0.dp
                    }
                    val finalPadding = bottomDockInset

                    // The mini-player is a second overlay above the navigation dock. Reserve
                    // only its own height and gap here; the navigation inset is already applied
                    // to every page through finalPadding and must not be counted twice.
                    val miniPlayerBottomGap = if (useNavigationRail) 8.dp else 4.dp
                    val miniPlayerScrollReserve = if (
                        currentSong != null &&
                            !isPlayerBackActive &&
                            currentRoute != null &&
                            currentRoute != "onboarding"
                    ) {
                        miniPlayerHeight + miniPlayerBottomGap
                    } else 0.dp

                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    start = railInset.coerceAtLeast(0.dp),
                                    bottom = (finalPadding + miniPlayerScrollReserve).coerceAtLeast(0.dp)
                                )
                                // Keep transparent page regions on the selected app palette;
                                // this ends above the floating dock and does not paint its gutter.
                                .background(DsmusicTheme.colorScheme.background)
                                .clipToBounds()
                        ) {
                            // Only the active tab and the recent cache stay composed. On Android 6,
                            // old roots are evicted to reduce repeated layout, draw and memory work;
                            // rememberSaveableStateHolder restores their saveable UI state on return.
                            visitedRootTabs.forEach { cachedRoute ->
                                val isOutgoingTab = cachedRoute == renderedRootTab
                                val isIncomingTab = cachedRoute == incomingRootTab
                                val tabIsActive = isOutgoingTab || isIncomingTab
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            alpha = if (tabIsActive) rootTabOpacity.value else 0f
                                            val progress = rootTabSlideProgress.value.coerceIn(0f, 1f)
                                            translationX = when {
                                                isOutgoingTab && incomingRootTab != null ->
                                                    -rootTabSlideDirection * progress * size.width
                                                isIncomingTab ->
                                                    rootTabSlideDirection * (1f - progress) * size.width
                                                else -> 0f
                                            }
                                        }
                                        .zIndex(if (isIncomingTab) 2f else if (isOutgoingTab) 1f else 0f)
                                        .pointerInput(tabIsActive) {
                                            if (!tabIsActive) {
                                                awaitPointerEventScope {
                                                    while (true) {
                                                        awaitPointerEvent().changes.forEach { it.consume() }
                                                    }
                                                }
                                            }
                                        }
                                ) {
                                    rootTabStateHolder.SaveableStateProvider(cachedRoute) {
                                        CachedRootTabLifecycle(active = tabIsActive) {
                                            when (cachedRoute) {
                                        "explore" -> ExploreScreen(
                                            viewModel = exploreViewModel,
                                            playerSharedViewModel = playerViewModel,
                                            onNavigateToLibrary = {
                                                currentNavController.navigate("library") {
                                                    popUpTo(currentNavController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        )
                                        "search" -> ExploreScreen(
                                            viewModel = searchViewModel,
                                            playerSharedViewModel = playerViewModel,
                                            isSearchDestination = true,
                                            onNavigateToLibrary = {
                                                currentNavController.navigate("library") {
                                                    popUpTo(currentNavController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                            onLeaveSearchDestination = {
                                                searchViewModel.clearSearch()
                                                currentNavController.navigate("explore") {
                                                    popUpTo(currentNavController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        )
                                        "library" -> LibraryScreen(
                                            viewModel = playerViewModel,
                                            onEditSong = { song ->
                                                editingSong = song
                                                currentNavController.navigate("tag_editor")
                                            },
                                            onNavigateToExplore = {
                                                currentNavController.navigate("explore") {
                                                    popUpTo(currentNavController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        )
                                                "local_music" -> LocalMusicScreen(viewModel = playerViewModel)
                                                "settings" -> SettingsScreenContent()
                                            }
                                        }
                                    }
                                }
                            }

                            NavHost(
                                navController = currentNavController,
                                startDestination = if (hasSeenOnboarding) "explore" else "onboarding",
                                modifier = Modifier.fillMaxSize(),
                                // Page pushes enter from the right; Back navigation enters from the left.
                                enterTransition = {
                                    val initialRoute = initialState.destination.route.orEmpty()
                                    val targetRoute = targetState.destination.route.orEmpty()
                                    val initialIndex = rootTabIndices[initialRoute]
                                    val targetIndex = rootTabIndices[targetRoute]
                                    val direction = when {
                                        initialIndex != null && targetIndex != null ->
                                            if (targetIndex >= initialIndex) 1 else -1
                                        initialRoute == "onboarding" -> 1
                                        initialIndex != null -> 1
                                        targetIndex != null -> -1
                                        else -> 1
                                    }
                                    androidx.compose.animation.slideInHorizontally(
                                        animationSpec = androidx.compose.animation.core.tween(
                                            durationMillis = pageTransitionDurationMillis,
                                            easing = androidx.compose.animation.core.FastOutSlowInEasing
                                        )
                                    ) { width -> direction * width } +
                                        androidx.compose.animation.fadeIn(
                                            initialAlpha = 0.5f,
                                            animationSpec = androidx.compose.animation.core.tween(
                                                durationMillis = pageTransitionDurationMillis,
                                                easing = androidx.compose.animation.core.FastOutSlowInEasing
                                            )
                                        )
                                },
                                exitTransition = {
                                    val initialRoute = initialState.destination.route.orEmpty()
                                    val targetRoute = targetState.destination.route.orEmpty()
                                    val initialIndex = rootTabIndices[initialRoute]
                                    val targetIndex = rootTabIndices[targetRoute]
                                    val direction = when {
                                        initialIndex != null && targetIndex != null ->
                                            if (targetIndex >= initialIndex) 1 else -1
                                        initialRoute == "onboarding" -> 1
                                        initialIndex != null -> 1
                                        targetIndex != null -> -1
                                        else -> 1
                                    }
                                    androidx.compose.animation.slideOutHorizontally(
                                        animationSpec = androidx.compose.animation.core.tween(
                                            durationMillis = pageTransitionDurationMillis,
                                            easing = androidx.compose.animation.core.FastOutSlowInEasing
                                        )
                                    ) { width -> -direction * width } +
                                        androidx.compose.animation.fadeOut(
                                            targetAlpha = 0.5f,
                                            animationSpec = androidx.compose.animation.core.tween(
                                                durationMillis = pageTransitionDurationMillis,
                                                easing = androidx.compose.animation.core.FastOutSlowInEasing
                                            )
                                        )
                                },
                                popEnterTransition = {
                                    val initialRoute = initialState.destination.route.orEmpty()
                                    val targetRoute = targetState.destination.route.orEmpty()
                                    val initialIndex = rootTabIndices[initialRoute]
                                    val targetIndex = rootTabIndices[targetRoute]
                                    val direction = when {
                                        initialIndex != null && targetIndex != null ->
                                            if (targetIndex >= initialIndex) 1 else -1
                                        initialRoute == "onboarding" -> 1
                                        initialIndex != null -> 1
                                        targetIndex != null -> -1
                                        else -> 1
                                    }
                                    androidx.compose.animation.slideInHorizontally(
                                        animationSpec = androidx.compose.animation.core.tween(
                                            durationMillis = pageTransitionDurationMillis,
                                            easing = androidx.compose.animation.core.FastOutSlowInEasing
                                        )
                                    ) { width -> direction * width } +
                                        androidx.compose.animation.fadeIn(
                                            initialAlpha = 0.5f,
                                            animationSpec = androidx.compose.animation.core.tween(
                                                durationMillis = pageTransitionDurationMillis,
                                                easing = androidx.compose.animation.core.FastOutSlowInEasing
                                            )
                                        )
                                },
                                popExitTransition = {
                                    val initialRoute = initialState.destination.route.orEmpty()
                                    val targetRoute = targetState.destination.route.orEmpty()
                                    val initialIndex = rootTabIndices[initialRoute]
                                    val targetIndex = rootTabIndices[targetRoute]
                                    val direction = when {
                                        initialIndex != null && targetIndex != null ->
                                            if (targetIndex >= initialIndex) 1 else -1
                                        initialRoute == "onboarding" -> 1
                                        initialIndex != null -> 1
                                        targetIndex != null -> -1
                                        else -> 1
                                    }
                                    androidx.compose.animation.slideOutHorizontally(
                                        animationSpec = androidx.compose.animation.core.tween(
                                            durationMillis = pageTransitionDurationMillis,
                                            easing = androidx.compose.animation.core.FastOutSlowInEasing
                                        )
                                    ) { width -> -direction * width } +
                                        androidx.compose.animation.fadeOut(
                                            targetAlpha = 0.5f,
                                            animationSpec = androidx.compose.animation.core.tween(
                                                durationMillis = pageTransitionDurationMillis,
                                                easing = androidx.compose.animation.core.FastOutSlowInEasing
                                            )
                                        )
                                },

                            ) {
                                composable("onboarding") {
                                    OnboardingScreen(
                                        onComplete = {
                                            currentNavController.navigate("explore") {
                                                popUpTo("onboarding") { inclusive = true }
                                            }
                                        }
                                    )
                                }
                                composable("explore") { }
                                composable("search") { }
                                composable("library") { }
                                composable("local_music") { }
                                composable("settings") { }
                                composable("tag_editor") {
                                    editingSong?.let { song ->
                                        TagEditorScreenEntryPoint(
                                            song = song,
                                            onNavigateUp = {
                                                currentNavController.navigateUp()
                                            }
                                        )
                                    }
                                }
                                composable("google_signin") {
                                    val accountViewModel: com.dsann.music.viewmodel.AccountViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                                    GoogleSignInScreen(
                                        accountViewModel = accountViewModel,
                                        onSignInSuccess = {
                                            currentNavController.navigate("explore") {
                                                popUpTo("explore") { inclusive = true }
                                            }
                                        },
                                        onNavigateUp = {
                                            currentNavController.navigateUp()
                                        }
                                    )
                                }
                            }
                        }

                        if (showNavigationRail) {
                            val configuredRailIconSize = when (navBarSize) {
                                "Compact" -> 22.dp
                                "Tall" -> 30.dp
                                else -> 26.dp
                            }
                            val railIconSize by animateDpAsState(
                                if (dynamicNavStyle) 32.dp else configuredRailIconSize,
                                animationSpec = if (isLegacyAndroid6) snap() else spring(stiffness = Spring.StiffnessMediumLow),
                                label = "RailIconSize"
                            )
                            val railShape = when (navBarStyle) {
                                "Docked" -> RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp)
                                "Minimal" -> RoundedCornerShape(0.dp)
                                else -> RoundedCornerShape(22.dp)
                            }
                            val railSurface = DsmusicTheme.colorScheme.surfaceContainer
                            val railBorderBase = if (railSurface.luminance() > 0.5f) {
                                androidx.compose.ui.graphics.Color.Black
                            } else {
                                androidx.compose.ui.graphics.Color.White
                            }
                            val railBorder = railBorderBase.copy(
                                alpha = if (navBarStyle == "Floating") 0.78f else 0.56f
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = railStartPadding)
                                    .width(railWidth)
                                    .then(
                                        if (navBarStyle == "Floating") {
                                            Modifier.shadow(14.dp, railShape, clip = false)
                                        } else Modifier
                                    )
                                    .clip(railShape)
                                    .background(
                                        if (navBarStyle == "Minimal") androidx.compose.ui.graphics.Color.Transparent
                                        else railSurface
                                    )
                                    .border(
                                        width = if (navBarStyle == "Floating") 0.9.dp else 0.75.dp,
                                        color = railBorder,
                                        shape = railShape
                                    )
                                    .padding(
                                        vertical = if (navBarSize == "Compact") 6.dp else 8.dp,
                                        horizontal = 6.dp
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                    val items = listOf(
                                        Triple("explore", "Explore", R.drawable.ic_explore),
                                        Triple("search", "Search", R.drawable.ic_search),
                                        Triple("library", "Library", R.drawable.ic_library_music),
                                        Triple("local_music", "Local Music", R.drawable.ic_music_note),
                                        Triple("settings", "Settings", R.drawable.ic_settings)
                                    )
                                    items.forEach { (route, label, iconResId) ->
                                        val selected = currentDestination?.route == route
                                        val tint = if (selected) DsmusicTheme.colorScheme.primary
                                            else DsmusicTheme.colorScheme.onSurfaceVariant
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(22.dp))
                                                .background(
                                                    if (selected) DsmusicTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    else androidx.compose.ui.graphics.Color.Transparent
                                                )
                                                .clickable {
                                                    if (!selected) {
                                                        currentNavController.navigate(route) {
                                                            popUpTo(currentNavController.graph.findStartDestination().id) {
                                                                saveState = true
                                                            }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    }
                                                }
                                                .padding(
                                                    vertical = when {
                                                        navLabelsMode == "Never" && navBarSize == "Compact" -> 8.dp
                                                        navLabelsMode == "Never" -> 12.dp
                                                        navBarSize == "Compact" -> 5.dp
                                                        navBarSize == "Tall" -> 10.dp
                                                        else -> 8.dp
                                                    }
                                                ),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Image(
                                                painter = painterResource(id = iconResId),
                                                contentDescription = label,
                                                colorFilter = ColorFilter.tint(tint),
                                                modifier = Modifier.size(railIconSize)
                                            )
                                            if (navLabelsMode == "Always" || (navLabelsMode == "Selected" && selected)) {
                                                BasicText(
                                                    text = if (label == "Local Music") "Local\nMusic" else label,
                                                    style = DsmusicTheme.typography.labelSmall.copy(
                                                        color = tint,
                                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    ),
                                                    maxLines = 2,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 2.dp)
                                                )
                                            }
                                        }
                                    }
                            }
                        }

                        PlayerBottomSheetCompose(
                            activity = this@MainActivity,
                            viewModel = playerViewModel,
                            bottomNavigationVisible = showBottomBar && !useNavigationRail,
                            navigationRailInset = if (useNavigationRail && isLandscapeLayout) railInset else 0.dp,
                            modifier = Modifier.layout { measurable, constraints ->
                                // Portrait retains its existing rail inset behavior. In landscape,
                                // keep the player host full-width and morph only the collapsed capsule
                                // into the rail-excluded pane to prevent a final width/position snap.
                                val startInsetPx = if (
                                    useNavigationRail && !isLandscapeLayout &&
                                    railInset > 0.dp && !isPlayerBackActive
                                ) railInset.roundToPx().coerceAtLeast(0) else 0
                                val childMaxWidth = (constraints.maxWidth - startInsetPx).coerceAtLeast(0)
                                val childMinWidth = (constraints.minWidth - startInsetPx)
                                    .coerceAtLeast(0)
                                    .coerceAtMost(childMaxWidth)
                                val placeable = measurable.measure(
                                    constraints.copy(
                                        minWidth = childMinWidth,
                                        maxWidth = childMaxWidth
                                    )
                                )
                                val width = constraints.constrainWidth(placeable.width + startInsetPx)
                                val height = constraints.constrainHeight(placeable.height)
                                layout(width, height) {
                                    placeable.placeRelative(startInsetPx, 0)
                                }
                            }.fillMaxSize()
                        )
                        AudioFormatChoiceDialogHost()
                    }
                    }
                }

                // Register above the complete Scaffold tree. This keeps player dismissal
                // higher priority than BackHandlers in every root tab and nested destination.
                androidx.activity.compose.BackHandler(
                    enabled = isPlayerBackActive
                ) {
                    playerViewModel.requestPlayerCollapseFromBack()
                }

                // Back from the Explore root exits the Activity instead of walking
                // through the previously visited bottom-navigation destinations.
                // Player collapse remains higher priority while the player is expanded.
                androidx.activity.compose.BackHandler(
                    enabled = rootBackRoute == "explore" && !isPlayerBackActive
                ) {
                    this@MainActivity.finish()
                }

                    if (startupOverlayAlpha > 0.001f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = startupOverlayAlpha }
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xB3474747),
                                            Color(0xB3242730),
                                            Color(0xB3000000)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_art),
                                contentDescription = "Quantune",
                                modifier = Modifier.size(280.dp)
                            )
                        }
                    }
                }
            }
        }

        handleIntent(intent)

        updateManager = UpdateManager(this)
        checkForUpdateOnLaunch()
    }

    fun navigateToSettings() {
        val controller = navController ?: return
        if (controller.currentDestination?.route != "settings") {
            controller.navigate("settings") {
                popUpTo(controller.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
            previousDestination = "settings"
        }
    }

    fun navigateToGoogleSignIn() {
        val controller = navController ?: return
        if (controller.currentDestination?.route != "google_signin") {
            controller.navigate("google_signin")
        }
    }

    fun showArtistPage(artistId: String?, artistName: String?) {
        val controller = navController ?: return
        val cameFromOutside = controller.currentDestination?.route != "explore"
        
        if (cameFromOutside) {
            isNavigating = true
            if (controller.currentDestination?.route == "library") {
                // Keep Library below the Explore detail page, so Back returns to Library.
                controller.navigate("explore") {
                    launchSingleTop = true
                }
            } else {
                controller.navigate("explore") {
                    popUpTo(controller.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            previousDestination = "explore"
            isNavigating = false
        }

        if (!artistId.isNullOrBlank()) {
            if (cameFromOutside) {
                exploreViewModel.cameFromLibrary = true
            }
            exploreViewModel.loadArtist(artistId, fallbackArtistName = artistName)
        } else if (!artistName.isNullOrBlank() && artistName != "Unknown Artist") {
            if (cameFromOutside) {
                exploreViewModel.cameFromLibrary = true
            }
            exploreViewModel.loadArtistByName(artistName)
        }
    }

    private fun checkForUpdateOnLaunch() {
        if (!shouldCheckForUpdate()) {
            Log.d(TAG, "Update check skipped (checked recently)")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = GitHubReleaseClient("DS-ann", "Quantune")
                val release = client.latestRelease ?: return@launch

                val currentVersion = BuildConfig.VERSION_NAME
                if (VersionUtils.isNewer(release.tagName, currentVersion)) {
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(release)
                    }
                } else {
                    Log.d(TAG, "App is up to date (current: $currentVersion, latest: ${release.tagName})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
            }
        }
    }

    private fun showUpdateDialog(release: GitHubReleaseClient.ReleaseInfo) {
        val message = StringBuilder("Version ${release.tagName} is now available!\n\n")
        release.changelog?.takeIf { it.isNotEmpty() }?.let { changelog ->
            val displayLog = if (changelog.length > 350) "${changelog.substring(0, 350)}..." else changelog
            message.append("What's New:\n").append(displayLog)
        }

        AlertDialog.Builder(this)
            .setTitle("Update Available")
            .setMessage(message.toString())
            .setCancelable(true)
            .setPositiveButton("Update Now") { _, _ ->
                val rootView = findViewById<View>(android.R.id.content)
                updateManager.checkForUpdate(rootView, BuildConfig.VERSION_NAME)
            }
            .setNegativeButton("Later") { dialog, _ -> dialog.dismiss() }
            .setNeutralButton("Don't Show Again") { dialog, _ ->
                disableUpdateCheck()
                dialog.dismiss()
            }
            .show()
    }

    private fun shouldCheckForUpdate(): Boolean {
        val prefs = getSharedPreferences("update_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("update_check_disabled", false)) return false

        val lastCheck = prefs.getLong("last_update_check", 0)
        val currentTime = System.currentTimeMillis()
        val oneDayMillis = 24 * 60 * 60 * 1000L

        return if (currentTime - lastCheck > oneDayMillis) {
            prefs.edit { putLong("last_update_check", currentTime) }
            true
        } else false
    }

    private fun disableUpdateCheck() {
        getSharedPreferences("update_prefs", MODE_PRIVATE).edit {
            putBoolean("update_check_disabled", true)
        }
        Log.d(TAG, "Auto update check disabled by user")
    }

    private var audioServiceStartupRequested = false

    private fun prepareAndStartAudioService() {
        if (audioServiceStartupRequested || isServiceBound) return
        audioServiceStartupRequested = true

        lifecycleScope.launch {
            val cacheReady = withContext(Dispatchers.IO) {
                try {
                    AudioPlaybackService.getCache(applicationContext)
                    true
                } catch (firstError: Exception) {
                    Log.e(TAG, "Media cache initialization failed; retrying after clearing the damaged cache", firstError)
                    try {
                        AudioPlaybackService.clearMediaCache(applicationContext)
                        AudioPlaybackService.getCache(applicationContext)
                        true
                    } catch (retryError: Exception) {
                        Log.e(TAG, "Playback service start postponed because the media cache could not be opened", retryError)
                        false
                    }
                }
            }

            if (cacheReady && !isFinishing && !isDestroyed) {
                startAudioService()
            } else {
                audioServiceStartupRequested = false
            }
        }
    }

    private fun startAudioService() {
        val serviceIntent = Intent(this, AudioPlaybackService::class.java)
        try {
            startService(serviceIntent)
        } catch (e: Exception) {
            Log.w(TAG, "startService failed, relying on bind: ${e.message}")
        }
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)
        Log.d(TAG, "Audio service started and bound")
    }

    private fun ensureServiceRunning() {
        val manager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager ?: return
        val isRunning = manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == AudioPlaybackService::class.java.name
        }
        if (!isRunning) prepareAndStartAudioService()
    }

    private fun checkAudioPermission() {
        val requiredPermissions = getRequiredRuntimePermissions()
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), AUDIO_PERMISSION_REQUEST)
        }
    }

    private fun getRequiredRuntimePermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.RECORD_AUDIO
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Keep the existing Compose/NavHost trees alive across rotation. LocalConfiguration
        // then recomposes the active layouts without closing open subsections or menus.
        setupSystemBars()
    }

    override fun onResume() {
        super.onResume()
        // A FOLLOW_SYSTEM change can recreate this Activity while the ViewModelStore is
        // retained. Refresh the theme-dependent Settings controls from the new resources.
        ViewModelProvider(this)[SettingsViewModel::class.java].refreshSystemTheme()
        setupSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupSystemBars()
    }

    fun setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        val selectedThemeMode = getSharedPreferences("AppSettings", MODE_PRIVATE)
            .getString("theme_mode", "system")
        val isDarkMode = when (selectedThemeMode) {
            "dark" -> true
            "light" -> false
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkMode
            isAppearanceLightNavigationBars = !isDarkMode
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PREVIOUS_DESTINATION, previousDestination)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController?.navigateUp() == true || super.onSupportNavigateUp()
    }

    override fun onStart() {
        super.onStart()
        ensureServiceRunning()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            try {
                unbindService(serviceConnection)
            } catch (_: Exception) {
                Log.d(TAG, "Service not bound, skipping unbind")
            }
            isServiceBound = false
        }
    }
    fun showSnackbar(message: String, duration: Int) {
        com.dsann.music.ui.SnackbarController.showMessage(message)
    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        super.onBackPressed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        if (Intent.ACTION_VIEW == intent.action || "android.intent.action.MUSIC_PLAYER" == intent.action) {
            intent.data?.let {
                playExternalUri(it)
                return
            }
        }

        if (navController != null && intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) {
            try {
                navController?.navigate("library")
            } catch (e: Exception) {
                Log.e(TAG, "Navigation request failed in handleIntent", e)
            }
        }
    }

    private fun playExternalUri(uri: Uri) {
        var displayName = "External Track"
        if ("content" == uri.scheme) {
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            cursor.getString(nameIndex)?.takeIf { it.isNotEmpty() }?.let {
                                displayName = it
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query metadata for external uri", e)
            }
        } else if ("file" == uri.scheme) {
            displayName = uri.lastPathSegment ?: displayName
        }

        if (displayName.contains(".")) {
            val dotIdx = displayName.lastIndexOf('.')
            if (dotIdx > 0) displayName = displayName.substring(0, dotIdx)
        }

        val externalId = -System.currentTimeMillis()
        val externalSong = SongItem(
            externalId,
            displayName,
            "External Source",
            -1L,
            uri.toString(),
            0L,
            System.currentTimeMillis() / 1000
        ).apply { contentUri = uri }

        val triggerPlayTask = Runnable {
            playerViewModel.playSong(externalSong)
            navController?.let { controller ->
                try {
                    controller.navigate("library")
                } catch (e: Exception) {
                    Log.e(TAG, "Navigation route to PlayerFragment failed in playExternalUri", e)
                }
            }
        }

        if (!isServiceBound) {
            lifecycleScope.launch {
                playerViewModel.audioServiceState.collect { service ->
                    if (service != null) {
                        triggerPlayTask.run()
                        this@launch.coroutineContext[kotlinx.coroutines.Job]?.cancel()
                    }
                }
            }
        } else {
            triggerPlayTask.run()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val AUDIO_PERMISSION_REQUEST = 100
        private const val KEY_PREVIOUS_DESTINATION = "key_previous_destination"
        const val EXTRA_OPEN_PLAYER = "open_player"
    }
}
