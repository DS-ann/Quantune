package com.dsann.music.ui

import com.dsann.music.ui.theme.DsmusicIconButton
import com.dsann.music.ui.theme.DsmusicSurface
import com.dsann.music.ui.theme.DsmusicTextButton
import com.dsann.music.ui.player.rememberAppAnimationFrameTimeNanos
import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowRight
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import com.dsann.music.ui.theme.AlertDialog
import com.dsann.music.ui.theme.CircularWavyProgressIndicator
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.IconButton
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.Surface
import com.dsann.music.ui.theme.Text
import com.dsann.music.ui.theme.TextButton
import com.dsann.music.ui.theme.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsann.music.MainActivity
import com.dsann.music.R
import com.dsann.music.model.PlayerGestureAction
import com.dsann.music.model.SongItem
import com.dsann.music.ui.player.ArtworkPager
import com.dsann.music.ui.player.bestProgressContrastColor
import com.dsann.music.ui.player.DsmCoverShapes
import com.dsann.music.ui.player.FullPlayerScreen
import com.dsann.music.ui.player.PlayerUiState
import com.dsann.music.ui.theme.observeKey
import com.dsann.music.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

// ==========================================
// 1. STEP 1: CORE STATE MACHINE & INTERFACES
// ==========================================
enum class PlayerSheetState {
    COLLAPSED,
    EXPANDED
}

private enum class MiniDismissDragPhase {
    IDLE,
    TENSION,
    SNAPPING,
    FREE_DRAG
}

// ==========================================
// 2. STEP 2: DYNAMIC SHAPE & SQUIRCLE MORPHING
// ==========================================
/**
 * Dynamic clipping outline for the mini-to-full player frame. The player tree keeps
 * full-window measurement throughout the animation; only this graphics-layer outline
 * changes, avoiding per-frame remeasurement of controls, lyrics and artwork.
 */
private class PlayerSheetAnimatedFrameShape(
    private val progress: Float,
    private val miniWidthFraction: Float,
    private val miniHeightPx: Float,
    private val miniCornerRadiusPx: Float,
    private val miniCenterOffsetPx: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val fraction = progress.coerceIn(0f, 1f)
        val frameWidth = size.width *
            (miniWidthFraction + (1f - miniWidthFraction) * fraction)
        val frameHeight = miniHeightPx + (size.height - miniHeightPx) * fraction
        // The rail occupies space only at the collapsed endpoint. Move the mini frame
        // toward the content pane as it collapses; the expanded player remains full-width.
        val left = (size.width - frameWidth) / 2f +
            miniCenterOffsetPx * (1f - fraction)
        val radius = miniCornerRadiusPx * (1f - fraction)
        return Outline.Rounded(
            RoundRect(
                rect = Rect(left, 0f, left + frameWidth, frameHeight),
                topLeft = CornerRadius(radius, radius),
                topRight = CornerRadius(radius, radius),
                bottomRight = CornerRadius(radius, radius),
                bottomLeft = CornerRadius(radius, radius)
            )
        )
    }
}

class PlayerSheetDynamicShape(
    private val topRadiusProvider: () -> Dp,
    private val bottomRadiusProvider: () -> Dp,
    private val virtualHeightProvider: () -> Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val topRadius = topRadiusProvider().nonNegative()
        val bottomRadius = bottomRadiusProvider().nonNegative()

        // Fall back to standard high-performance rounded corner shape
        val topRadiusPx = with(density) { topRadius.toPx() }
        val bottomRadiusPx = with(density) { bottomRadius.toPx() }
        val virtualHeight = virtualHeightProvider()
        return Outline.Rounded(
            RoundRect(
                rect = Rect(0f, 0f, size.width, virtualHeight),
                topLeft = CornerRadius(topRadiusPx, topRadiusPx),
                topRight = CornerRadius(topRadiusPx, topRadiusPx),
                bottomRight = CornerRadius(bottomRadiusPx, bottomRadiusPx),
                bottomLeft = CornerRadius(bottomRadiusPx, bottomRadiusPx)
            )
        )
    }
}

private fun Dp.nonNegative(): Dp = takeIf { it.value.isFinite() && it.value > 0f } ?: 0.dp

// ==========================================
// 3. STEP 3: DRAW-PHASE & LAYOUT-PHASE PROVIDERS
// ==========================================
val MiniPlayerHeight = 72.dp // Reduced by 10% while preserving the existing layout

data class SheetVisualState(
    val currentBottomPadding: Dp,
    val playerContentAreaHeightPxProvider: () -> Float,
    val visualSheetTranslationYProvider: () -> Float,
    val overallSheetTopCornerRadiusProvider: () -> Dp,
    val playerContentActualBottomRadiusProvider: () -> Dp,
    val currentHorizontalPaddingStartPxProvider: () -> Float,
    val currentHorizontalPaddingEndPxProvider: () -> Float
)

@Composable
fun rememberSheetVisualState(
    showPlayerContentArea: Boolean,
    collapsedStateHorizontalPadding: Dp,
    predictiveBackCollapseProgress: Float,
    playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    containerHeight: Dp,
    currentSheetTranslationY: Animatable<Float, AnimationVector1D>,
    sheetCollapsedTargetYProvider: () -> Float,
    isNavBarHiddenProvider: () -> Boolean,
    navBarCornerRadiusDp: Dp,
    miniPlayerHeight: Dp = MiniPlayerHeight
): SheetVisualState {
    
    val currentBottomPadding by remember(showPlayerContentArea, collapsedStateHorizontalPadding, predictiveBackCollapseProgress) {
        derivedStateOf {
            if (predictiveBackCollapseProgress > 0f && showPlayerContentArea) {
                lerp(0.dp, collapsedStateHorizontalPadding, predictiveBackCollapseProgress)
            } else {
                0.dp
            }
        }
    }

    val density = LocalDensity.current
    val miniHeightPx = remember(miniPlayerHeight, density) { with(density) { miniPlayerHeight.toPx() } }
    val containerHeightPx = remember(containerHeight, density) { with(density) { containerHeight.toPx() } }
    
    // Dynamic height provider (Layout-phase)
    val playerContentAreaHeightPxProvider: () -> Float = remember(showPlayerContentArea, playerContentExpansionFraction, miniHeightPx, containerHeightPx) {
        {
            if (showPlayerContentArea) {
                androidx.compose.ui.util.lerp(miniHeightPx, containerHeightPx, playerContentExpansionFraction.value)
            } else {
                0f
            }
        }
    }

    val predictiveBackCollapseProgressState = rememberUpdatedState(predictiveBackCollapseProgress)
    val visualSheetTranslationYProvider: () -> Float = remember(currentSheetTranslationY, sheetCollapsedTargetYProvider) {
        {
            val progress = predictiveBackCollapseProgressState.value
            val collapsedY = sheetCollapsedTargetYProvider()
            currentSheetTranslationY.value * (1f - progress) + (collapsedY * progress)
        }
    }

    // Dynamic top corners logic (lerps from pill corner to 0.dp)
    val overallSheetTopCornerRadiusProvider: () -> Dp = remember(showPlayerContentArea, playerContentExpansionFraction, navBarCornerRadiusDp, isNavBarHiddenProvider, miniPlayerHeight) {
        {
            if (showPlayerContentArea) {
                val collapsedCornerTarget = miniPlayerHeight / 2f
                val fraction = playerContentExpansionFraction.value
                lerp(collapsedCornerTarget, 0.dp, fraction)
            } else {
                miniPlayerHeight / 2f
            }
        }
    }

    // Dynamic bottom corners logic (morphs down to 0.dp)
    val playerContentActualBottomRadiusProvider: () -> Dp = remember(showPlayerContentArea, playerContentExpansionFraction, isNavBarHiddenProvider, navBarCornerRadiusDp, miniPlayerHeight) {
        {
            val fraction = playerContentExpansionFraction.value
            val collapsedRadius = miniPlayerHeight / 2f
            
            // Morphs outward to 26.dp in first 20% drag to form curved floating card, then goes flat
            if (fraction < 0.2f) {
                lerp(collapsedRadius, 26.dp, (fraction / 0.2f).coerceIn(0f, 1f))
            } else {
                lerp(26.dp, 0.dp, ((fraction - 0.2f) / 0.8f).coerceIn(0f, 1f))
            }
        }
    }

    val collapsedStateHorizontalPaddingPx = remember(collapsedStateHorizontalPadding, density) {
        with(density) { collapsedStateHorizontalPadding.toPx() }
    }

    // Draw-phase padding providers
    val currentHorizontalPaddingStartPxProvider: () -> Float = remember(showPlayerContentArea, playerContentExpansionFraction, collapsedStateHorizontalPaddingPx) {
        {
            androidx.compose.ui.util.lerp(collapsedStateHorizontalPaddingPx, 0f, playerContentExpansionFraction.value)
        }
    }
    
    val currentHorizontalPaddingEndPxProvider: () -> Float = remember(showPlayerContentArea, playerContentExpansionFraction, collapsedStateHorizontalPaddingPx) {
        {
            androidx.compose.ui.util.lerp(collapsedStateHorizontalPaddingPx, 0f, playerContentExpansionFraction.value)
        }
    }

    return remember(
        currentBottomPadding,
        playerContentAreaHeightPxProvider,
        visualSheetTranslationYProvider,
        overallSheetTopCornerRadiusProvider,
        playerContentActualBottomRadiusProvider,
        currentHorizontalPaddingStartPxProvider,
        currentHorizontalPaddingEndPxProvider
    ) {
        SheetVisualState(
            currentBottomPadding = currentBottomPadding,
            playerContentAreaHeightPxProvider = playerContentAreaHeightPxProvider,
            visualSheetTranslationYProvider = visualSheetTranslationYProvider,
            overallSheetTopCornerRadiusProvider = overallSheetTopCornerRadiusProvider,
            playerContentActualBottomRadiusProvider = playerContentActualBottomRadiusProvider,
            currentHorizontalPaddingStartPxProvider = currentHorizontalPaddingStartPxProvider,
            currentHorizontalPaddingEndPxProvider = currentHorizontalPaddingEndPxProvider
        )
    }
}

// ==========================================
// 4. STEP 4: STACKING & CROSS-FADING
// ==========================================
@Composable
fun BoxScope.UnifiedPlayerMiniAndFullLayers(
    activity: MainActivity,
    viewModel: PlayerSharedViewModel,
    uiState: PlayerUiState,
    songList: List<SongItem>,
    baseAccentColor: Color,
    context: Context,
    playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    isSheetExpanded: Boolean,
    onNavigateToListenTogether: () -> Unit,
    onFullPlayerLayoutReady: () -> Unit,
    containerHeight: Dp,
    onCollapse: () -> Unit,
    onExpand: () -> Unit,
    dragModifier: Modifier,
    miniPlayerHeight: Dp,
    miniPlayerWidthFraction: Float,
    miniPlayerHorizontalOffset: Dp,
    miniPlayerBackgroundColor: Color,
    miniPlayerSurfaceAlpha: Float,
    miniPlayerShape: Shape,
    miniPlayerElevation: Dp,
    miniSwipeModifier: Modifier,
    isFullscreenVisualizerOpen: Boolean = false,
    onFullscreenVisualizerVisibilityChanged: (Boolean) -> Unit = {}
) {
    val currentSong = uiState.currentSong ?: return
    val isPlaying = uiState.isPlaying
    val fullPlayerStateHolder = rememberSaveableStateHolder()
    val isLyricsModeEnabled by viewModel.isLyricsModeEnabled.collectAsStateWithLifecycle()
    val isQueueExpanded by viewModel.isQueueExpanded.collectAsStateWithLifecycle()
    // Overlays such as Queue are only allowed once the sheet has actually settled open.
    val isFullPlayerVisible by remember(playerContentExpansionFraction) {
        derivedStateOf { playerContentExpansionFraction.value >= 0.995f }
    }
    // Mini-player gesture mappings can open Lyrics or Queue directly. Keep those states
    // functional even when the sheet has not crossed the normal full-player threshold.
    val shouldComposeFullPlayer by remember(
        playerContentExpansionFraction,
        isLyricsModeEnabled,
        isQueueExpanded,
        isSheetExpanded
    ) {
        derivedStateOf {
            // Mount the expensive full-player tree before the first animated frame.
            // This keeps lyrics, artwork and visualizers from being composed mid-transition.
            isSheetExpanded ||
                playerContentExpansionFraction.value > 0.015f ||
                isLyricsModeEnabled ||
                isQueueExpanded
        }
    }

    // The shared artwork layer now owns the cover from the collapsed dock through expansion.
    val showLegacyCompactArtwork = false

    val miniPlayerZIndex by remember {
        derivedStateOf { if (playerContentExpansionFraction.value < 0.5f) 1f else 0f }
    }
    val fullPlayerZIndex by remember {
        derivedStateOf { if (playerContentExpansionFraction.value >= 0.5f) 1f else 0f }
    }

    // Mini content shares the sheet's origin and moves with its interpolated frame.
    // Keep the gesture/hit-test layer full-width inside its collapsed frame. The visible
    // capsule is a separate inset child, so the left/right gutters stay genuinely transparent
    // instead of being painted by the capsule surface or its shadow.
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(x = miniPlayerHorizontalOffset)
            .fillMaxWidth(miniPlayerWidthFraction)
            .height(miniPlayerHeight)
            // The mini-player has one gesture recognizer for every swipe direction.
            // A second vertical recognizer here competed for the same pointer stream.
            .then(miniSwipeModifier)
            .graphicsLayer {
                alpha = (1f - playerContentExpansionFraction.value).coerceIn(0f, 1f)
            }
            .zIndex(miniPlayerZIndex)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .shadow(miniPlayerElevation, miniPlayerShape)
                .clip(miniPlayerShape)
                .background(
                    miniPlayerBackgroundColor.copy(alpha = miniPlayerSurfaceAlpha.coerceIn(0f, 1f)),
                    miniPlayerShape
                )
        ) {
            MiniPlayerContentInternal(
                viewModel = viewModel,
                currentSong = currentSong,
                isPlaying = isPlaying,
                accentColor = baseAccentColor,
                miniPlayerBackgroundColor = miniPlayerBackgroundColor,
                coverDerivedColor = Color(uiState.playerBackgroundColor),
                showLegacyCompactArtwork = showLegacyCompactArtwork,
                expansionFraction = playerContentExpansionFraction,
                onClick = {
                    // A tap on the mini player always opens the normal full player.
                    // Lyrics and Queue are explicit modes, never inherited from the last session.
                    viewModel.setLyricsModeEnabled(false)
                    viewModel.setQueueExpanded(false)
                    onExpand()
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // Mount the expensive full-player tree only after the sheet is actually open.
    // Its saveable state is retained while collapsed, without keeping lyrics, visualizers,
    // and hidden layout work alive behind the mini player.
    if (shouldComposeFullPlayer) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(containerHeight)
                .onGloballyPositioned { coordinates ->
                    if (coordinates.size.width > 0 && coordinates.size.height > 0) {
                        onFullPlayerLayoutReady()
                    }
                }
                .graphicsLayer {
                    // The full player follows the same sheet origin as the mini layer.
                    alpha = playerContentExpansionFraction.value.coerceIn(0f, 1f)
                }
                .zIndex(fullPlayerZIndex)
        ) {
            fullPlayerStateHolder.SaveableStateProvider("full-player") {
                FullPlayerScreen(
                    activity = activity,
                    viewModel = viewModel,
                    uiState = uiState,
                    songList = songList,
                    accentColor = baseAccentColor,
                    context = context,
                    onCollapse = onCollapse,
                    onNavigateToListenTogether = onNavigateToListenTogether,
                    dragModifier = dragModifier,
                    isPlayerVisible = isFullPlayerVisible,
                    onVisualizerVisibilityChanged = onFullscreenVisualizerVisibilityChanged,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

    }
}

// ==========================================
// 5. STEP 5: VERTICAL GESTURE HANDLER & MATH
// ==========================================
data class SheetVerticalDragFrame(
    val translationY: Float,
    val expansionFraction: Float
)

private data class SheetAnimationRequest(
    val targetExpanded: Boolean,
    val animationSpec: AnimationSpec<Float>?,
    val initialVelocity: Float
)

fun computeSheetVerticalDragFrame(
    accumulatedDragY: Float,
    expandedY: Float,
    collapsedY: Float,
    miniHeightPx: Float,
    initialFractionOnDragStart: Float,
    initialYOnDragStart: Float
): SheetVerticalDragFrame {
    // A drag that starts with the mini-player fully collapsed may open it upward,
    // but must never pull the capsule below its normal dock position.
    // Keep the small downward overscroll only for a drag that starts from the full player.
    val maxDragY = if (initialFractionOnDragStart <= 0.001f) {
        collapsedY
    } else {
        collapsedY + miniHeightPx * 0.2f
    }
    val newY = (initialYOnDragStart + accumulatedDragY)
        .coerceIn(expandedY, maxDragY)
    val denominator = (collapsedY - expandedY).coerceAtLeast(1f)
    val dragRatio = (initialYOnDragStart - newY) / denominator
    val newFraction = (initialFractionOnDragStart + dragRatio).coerceIn(0f, 1f)
    return SheetVerticalDragFrame(translationY = newY, expansionFraction = newFraction)
}

fun resolveVerticalSheetTargetState(
    currentSheetContentState: PlayerSheetState,
    accumulatedDragY: Float,
    minDragThresholdPx: Float,
    verticalVelocity: Float,
    velocityThreshold: Float,
    currentFraction: Float
): PlayerSheetState {
    return when {
        currentSheetContentState == PlayerSheetState.EXPANDED && accumulatedDragY <= 0f -> 
            PlayerSheetState.EXPANDED

        abs(accumulatedDragY) > minDragThresholdPx ->
            if (accumulatedDragY < 0f) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED

        abs(verticalVelocity) > velocityThreshold ->
            if (verticalVelocity < 0f) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED

        else ->
            if (currentFraction > 0.5f) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
    }
}

class SheetVerticalDragGestureHandler(
    private val scope: CoroutineScope,
    private val velocityTracker: VelocityTracker,
    private val densityProvider: () -> Density,
    private val playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    private val currentSheetTranslationY: Animatable<Float, AnimationVector1D>,
    private val expandedYProvider: () -> Float,
    private val collapsedYProvider: () -> Float,
    private val miniHeightPxProvider: () -> Float,
    private val currentSheetStateProvider: () -> PlayerSheetState,
    private val onAnimateSheet: suspend (targetExpanded: Boolean, animationSpec: AnimationSpec<Float>?, initialVelocity: Float) -> Unit,
    private val onExpandSheetState: () -> Unit,
    private val onCollapseSheetState: () -> Unit,
    private val onDragStateChange: (Boolean) -> Unit
) {
    private var initialFractionOnDragStart = 0f
    private var initialYOnDragStart = 0f
    private var accumulatedDragYSinceStart = 0f
    private var dragJob: Job? = null
    private var dragFrameChannel: Channel<SheetVerticalDragFrame>? = null
    private var isActivelyDragging = false

    fun onDragStart() {
        isActivelyDragging = true
        onDragStateChange(true)
        dragJob?.cancel()
        dragFrameChannel?.close()
        velocityTracker.resetTracking()
        initialFractionOnDragStart = playerContentExpansionFraction.value
        initialYOnDragStart = currentSheetTranslationY.value
        accumulatedDragYSinceStart = 0f

        // Keep one writer for both Animatables. A conflated stream drops stale pointer
        // samples instead of racing a cancelled coroutine against the next frame.
        val channel = Channel<SheetVerticalDragFrame>(Channel.CONFLATED)
        dragFrameChannel = channel
        dragJob = scope.launch {
            for (frame in channel) {
                currentSheetTranslationY.snapTo(frame.translationY)
                playerContentExpansionFraction.snapTo(frame.expansionFraction)
            }
        }
    }

    fun onVerticalDrag(uptimeMillis: Long, position: Offset, dragAmount: Float) {
        // Allow the finger to reverse direction mid-gesture. The stored sheet state is not
        // updated until drag end, so checking it here incorrectly discarded upward deltas.
        accumulatedDragYSinceStart += dragAmount
        val dragFrame = computeSheetVerticalDragFrame(
            accumulatedDragY = accumulatedDragYSinceStart,
            expandedY = expandedYProvider(),
            collapsedY = collapsedYProvider(),
            miniHeightPx = miniHeightPxProvider(),
            initialFractionOnDragStart = initialFractionOnDragStart,
            initialYOnDragStart = initialYOnDragStart
        )
        dragFrameChannel?.trySend(dragFrame)
        velocityTracker.addPosition(uptimeMillis, position)
    }

    fun onDragEnd() {
        if (!isActivelyDragging) return
        isActivelyDragging = false
        val channel = dragFrameChannel
        dragFrameChannel = null
        channel?.close()
        val pendingFrameJob = dragJob
        dragJob = null
        val verticalVelocity = velocityTracker.calculateVelocity().y
        val minDragThresholdPx = with(densityProvider()) { 5.dp.toPx() }

        scope.launch {
            // Finish the final conflated pointer frame before resolving the snap target.
            pendingFrameJob?.join()
            val currentFraction = playerContentExpansionFraction.value
            val targetState = resolveVerticalSheetTargetState(
                currentSheetContentState = currentSheetStateProvider(),
                accumulatedDragY = accumulatedDragYSinceStart,
                minDragThresholdPx = minDragThresholdPx,
                verticalVelocity = verticalVelocity,
                velocityThreshold = 150f,
                currentFraction = currentFraction
            )

            if (targetState == PlayerSheetState.EXPANDED) {
                onAnimateSheet(true, null, 0f)
                onExpandSheetState()
            } else {
                onAnimateSheet(false, null, verticalVelocity)
                onCollapseSheetState()
            }
            // Keep the layout's drag guard active until the final pointer sample is applied
            // and the snap request has been handed to the single animation owner.
            onDragStateChange(false)
        }
    }
}

fun Modifier.playerSheetVerticalDragGesture(
    enabled: Boolean,
    handler: SheetVerticalDragGestureHandler
): Modifier {
    if (!enabled) return this
    return this.pointerInput(true, handler) {
        detectVerticalDragGestures(
            onDragStart = { handler.onDragStart() },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                handler.onVerticalDrag(change.uptimeMillis, change.position, dragAmount)
            },
            onDragEnd = { handler.onDragEnd() },
            onDragCancel = { handler.onDragEnd() }
        )
    }
}

// ==========================================
// 6. STEP 6: HORIZONTAL SWIPE-TO-DISMISS TENSION
// ==========================================
class MiniPlayerDismissGestureHandler(
    private val scope: CoroutineScope,
    private val density: Density,
    private val offsetAnimatable: Animatable<Float, AnimationVector1D>,
    private val screenWidthPx: Float,
    private val onDismissQueue: () -> Unit
) {
    private var dragPhase = MiniDismissDragPhase.IDLE
    private var accumulatedDragX = 0f
    private var offsetJob: Job? = null

    fun onDragStart() {
        dragPhase = MiniDismissDragPhase.TENSION
        accumulatedDragX = 0f
        offsetJob?.cancel()
        offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            offsetAnimatable.stop()
        }
    }

    fun onHorizontalDrag(dragAmount: Float) {
        accumulatedDragX += dragAmount

        when (dragPhase) {
            MiniDismissDragPhase.TENSION -> {
                val snapThresholdPx = 100f * density.density
                
                // Pulls back with structural resistance under the 100dp threshold
                if (abs(accumulatedDragX) < snapThresholdPx) {
                    val maxTensionOffsetPx = 30f * density.density
                    val dragFraction = (abs(accumulatedDragX) / snapThresholdPx).coerceIn(0f, 1f)
                    val tensionOffset = androidx.compose.ui.util.lerp(0f, maxTensionOffsetPx, dragFraction)
                    offsetJob?.cancel()
                    offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        offsetAnimatable.snapTo(tensionOffset * accumulatedDragX.sign)
                    }
                } else {
                    dragPhase = MiniDismissDragPhase.SNAPPING
                }
            }

            MiniDismissDragPhase.SNAPPING -> {
                offsetJob?.cancel()
                offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    // Follow the finger directly; animating every drag delta adds latency.
                    offsetAnimatable.snapTo(accumulatedDragX)
                }
                dragPhase = MiniDismissDragPhase.FREE_DRAG
            }

            MiniDismissDragPhase.FREE_DRAG -> {
                offsetJob?.cancel()
                offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    // A drag is already a continuous input; snap instead of spawning
                    // another spring animation for every pointer event.
                    offsetAnimatable.snapTo(accumulatedDragX)
                }
            }
            else -> Unit
        }
    }

    fun onDragEnd() {
        dragPhase = MiniDismissDragPhase.IDLE
        offsetJob?.cancel()
        val dismissThreshold = screenWidthPx * 0.4f
        
        if (abs(accumulatedDragX) > dismissThreshold) {
            val targetDismissOffset = if (accumulatedDragX < 0) -screenWidthPx else screenWidthPx
            offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                offsetAnimatable.animateTo(
                    targetValue = targetDismissOffset,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                )
                onDismissQueue()
                offsetAnimatable.snapTo(0f)
            }
        } else {
            // Springs back dynamically to center
            offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                offsetAnimatable.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                )
            }
        }
    }
}

@Composable
fun rememberMiniPlayerDismissGestureHandler(
    scope: CoroutineScope,
    density: Density,
    offsetAnimatable: Animatable<Float, AnimationVector1D>,
    screenWidthPx: Float,
    onDismissQueue: () -> Unit
): MiniPlayerDismissGestureHandler {
    val onDismissQueueState = rememberUpdatedState(onDismissQueue)
    return remember(scope, density, offsetAnimatable, screenWidthPx) {
        MiniPlayerDismissGestureHandler(
            scope = scope,
            density = density,
            offsetAnimatable = offsetAnimatable,
            screenWidthPx = screenWidthPx,
            onDismissQueue = { onDismissQueueState.value() }
        )
    }
}

fun Modifier.miniPlayerDismissHorizontalGesture(
    enabled: Boolean,
    handler: MiniPlayerDismissGestureHandler
): Modifier {
    if (!enabled) return this
    return this.pointerInput(true, handler) {
        detectHorizontalDragGestures(
            onDragStart = { handler.onDragStart() },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                handler.onHorizontalDrag(dragAmount)
            },
            onDragEnd = { handler.onDragEnd() }
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.VideoOverlayControls(
    qualityLabel: String?,
    qualityEnabled: Boolean,
    onOpenQuality: () -> Unit,
    onToggleFullscreen: () -> Unit,
    fullscreen: Boolean
) {
    // Measure the actual video viewport. The artwork preference is already applied
    // to the shared cover layer, so scaling controls from that preference made them
    // stay large when the player was resized into a short landscape window.
    BoxWithConstraints(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(4.dp)
    ) {
        val controlSize = (minOf(maxWidth, maxHeight) * 0.19f).coerceIn(24.dp, 40.dp)
        val iconSize = (controlSize * 0.43f).coerceIn(11.dp, 17.dp)
        Row(
            horizontalArrangement = Arrangement.spacedBy((controlSize * 0.14f).coerceIn(3.dp, 6.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DsmusicSurface(
                modifier = Modifier.size(controlSize),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.62f),
                contentColor = Color.White,
                showBorder = false,
                onClick = if (qualityEnabled) onOpenQuality else null
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = qualityLabel?.let { "Video quality, $it" } ?: "Video quality",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(iconSize)
                )
            }
            DsmusicSurface(
                modifier = Modifier.size(controlSize),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.62f),
                contentColor = Color.White,
                showBorder = false,
                onClick = onToggleFullscreen
            ) {
                Icon(
                    painter = painterResource(
                        if (fullscreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen
                    ),
                    contentDescription = if (fullscreen) "Exit fullscreen" else "Enter fullscreen",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(iconSize)
                )
            }
        }
    }
}

// ==========================================
// 7. STEP 7: THE MASTER ASSEMBLY CONTAINER
// ==========================================
@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerBottomSheetCompose(
    activity: MainActivity,
    viewModel: PlayerSharedViewModel,
    bottomNavigationVisible: Boolean = false,
    navigationRailInset: Dp = 0.dp,
    onNavigateToListenTogether: () -> Unit,
    onNavigateToSongRecognizer: () -> Unit,
    showSongRecognizerShortcut: Boolean = false,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val context = LocalContext.current
        var isFullscreenVisualizerOpen by remember { mutableStateOf(false) }
        val density = LocalDensity.current
        // The player can be hosted in a resized/freeform window. Use its measured bounds
        // instead of the physical display configuration for every responsive breakpoint.
        val isLandscapeWindow = maxWidth > maxHeight
        val useNavigationRail = maxWidth >= 600.dp
        val useWidePlayer = maxWidth >= 840.dp ||
            (useNavigationRail && isLandscapeWindow)
        val miniPlayerHeight = if (isLandscapeWindow) MiniPlayerHeight * 0.8f else MiniPlayerHeight
        val scope = rememberCoroutineScope()
        val playerPreferences = remember(context) {
            context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        }
        val playerBackgroundStyle by playerPreferences.observeKey("player_background_style", "Artwork")
        val playerLayout by playerPreferences.observeKey("player_layout", "Standard")
        val playerArtworkScale by playerPreferences.observeKey("player_artwork_scale", 1f)
        val playerArtworkShapeName by playerPreferences.observeKey("player_artwork_shape", "Rounded")
        val playerArtworkShape = remember(playerLayout, playerArtworkShapeName) {
            DsmCoverShapes.forPlayerLayout(playerLayout, playerArtworkShapeName)
        }
        val miniPlayerStyle by playerPreferences.observeKey("mini_player_style", "Card")
        
        // Playback state observers combined into stable UI model
        val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
        val isVideoMode by viewModel.isVideoMode.collectAsStateWithLifecycle()
        val videoPlaybackMetadata by viewModel.videoPlaybackMetadata.collectAsStateWithLifecycle()
        val audioService by viewModel.audioServiceState.collectAsStateWithLifecycle()
        val gestureSettings by viewModel.playerGestureSettings.collectAsStateWithLifecycle()
        var videoQualityOptions by remember {
            mutableStateOf<List<com.dsann.music.data.innertube.VideoStreamOption>>(emptyList())
        }
        var preferredVideoQualityHeight by remember(playerPreferences) {
            mutableStateOf(playerPreferences.getInt("video_quality_preferred_height", 0))
        }
        var selectedVideoQuality by remember(playerPreferences) {
            mutableStateOf(playerPreferences.getString("video_quality_preferred_label", null))
        }
        var videoQualityLoading by remember { mutableStateOf(false) }
        var showVideoQualityDialog by remember { mutableStateOf(false) }
        var isVideoFullscreen by remember { mutableStateOf(false) }

        LaunchedEffect(isVideoMode, currentSong?.id, currentSong?.videoId, audioService) {
            val targetSongId = currentSong?.id
            val videoId = currentSong?.videoId?.takeIf { it.isNotBlank() }
            // A new queue item gets its own quality/fullscreen state. Never leave the
            // previous video's controls or fullscreen surface attached to the next song.
            videoQualityOptions = emptyList()
            showVideoQualityDialog = false
            isVideoFullscreen = false

            if (!isVideoMode) {
                // Keep the preferred resolution when audio mode is active.
                videoQualityLoading = false
                selectedVideoQuality = playerPreferences.getString("video_quality_preferred_label", null)
            } else if (videoId == null) {
                // The active queue item is local or has no video ID: immediately return
                // to the already-loaded audio item and remove the video window.
                audioService?.setVideoMode(false)
                videoQualityLoading = false
            } else {
                // If the queue item changed while Video was active, the service rebinds
                // playback to this video's own stream. This is a no-op for the already
                // active video ID.
                audioService?.setVideoMode(true)
                videoQualityLoading = true
                try {
                    val options = com.dsann.music.data.innertube.NewPipeStreamExtractor
                        .getVideoStreamOptions(videoId)
                    if (
                        currentSong?.id != targetSongId ||
                        currentSong?.videoId != videoId ||
                        !isVideoMode
                    ) return@LaunchedEffect

                    videoQualityOptions = options
                    val dataSaverOption = com.dsann.music.data.innertube.NewPipeStreamExtractor
                        .dataSaverVideoOption(options)
                    val preferredOption = if (preferredVideoQualityHeight > 0) {
                        options.minWithOrNull(
                            compareBy<com.dsann.music.data.innertube.VideoStreamOption> {
                                kotlin.math.abs(it.height - preferredVideoQualityHeight)
                            }.thenBy {
                                if (it.height > preferredVideoQualityHeight) 1 else 0
                            }.thenBy {
                                if (it.usesSeparateAudio) 1 else 0
                            }
                        )
                    } else {
                        null
                    }
                    val selectedOption = preferredOption ?: dataSaverOption
                    selectedVideoQuality = selectedOption?.label
                        ?: playerPreferences.getString("video_quality_preferred_label", null)

                    if (selectedOption != null) {
                        // Apply the preferred quality only after the service has installed
                        // the matching track video. Its default option remains the safe fallback.
                        if (
                            preferredOption != null &&
                            preferredOption.url != dataSaverOption?.url
                        ) {
                            audioService?.setVideoStreamQuality(
                                streamUrl = preferredOption.url,
                                audioUrl = preferredOption.audioUrl,
                                videoMimeType = preferredOption.mimeType,
                                audioMimeType = preferredOption.audioMimeType
                            )
                        }
                        viewModel.setVideoPlaybackMetadata(
                            com.dsann.music.viewmodel.VideoPlaybackMetadata(
                                title = selectedOption.videoTitle?.takeIf { it.isNotBlank() }
                                    ?: currentSong?.title.orEmpty(),
                                uploader = selectedOption.videoUploader?.takeIf { it.isNotBlank() }
                                    ?: currentSong?.artist.orEmpty(),
                                durationMs = selectedOption.durationMs,
                                contentLengthBytes = selectedOption.contentLengthBytes,
                                bitrateBps = selectedOption.bitrateBps,
                                resolution = selectedOption.label,
                                mimeType = selectedOption.mimeType,
                                description = selectedOption.videoDescription,
                                viewCount = selectedOption.videoViewCount,
                                likeCount = selectedOption.videoLikeCount,
                                uploadDate = selectedOption.videoUploadDate,
                                thumbnailUrl = selectedOption.videoThumbnailUrl,
                                uploaderUrl = selectedOption.videoUploaderUrl
                            )
                        )
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    videoQualityOptions = emptyList()
                    selectedVideoQuality = playerPreferences.getString("video_quality_preferred_label", null)
                } finally {
                    videoQualityLoading = false
                }
            }
        }

        val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val duration by viewModel.duration.collectAsStateWithLifecycle()
        val currentSongIndex by viewModel.currentSongIndex.collectAsStateWithLifecycle()
        val miniPlayerBlendColor by viewModel.miniPlayerBlendColor.collectAsStateWithLifecycle()
        val isCurrentSongFavorite by viewModel.isCurrentSongFavoriteFlow.collectAsStateWithLifecycle()
        val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
        val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsStateWithLifecycle()
        val playerBackgroundColor by viewModel.playerBackgroundColor.collectAsStateWithLifecycle()
        val likesCount by viewModel.likesCountFlow.collectAsStateWithLifecycle()
        val isDark = DsmusicTheme.colorScheme.background.luminance() < 0.5f
        val themePlayerSurface = DsmusicTheme.colorScheme.surface
        val dynamicBgColor = remember(playerBackgroundColor, isDark, playerBackgroundStyle, themePlayerSurface) {
            if (playerBackgroundStyle == "Theme") {
                themePlayerSurface
            } else {
                val baseColor = Color(playerBackgroundColor)
                val hsl = FloatArray(3)
                androidx.core.graphics.ColorUtils.colorToHSL(baseColor.toArgb(), hsl)
                val isMonochrome = hsl[1] < 0.06f
                if (isDark) {
                    hsl[2] = 0.155f
                    hsl[1] = if (isMonochrome) 0f else hsl[1].coerceIn(0.32f, 0.54f)
                    Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
                } else {
                    hsl[2] = 0.835f
                    hsl[1] = if (isMonochrome) 0f else hsl[1].coerceIn(0.30f, 0.48f)
                    Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
                }
            }
        }
        // Dark mode mini-player lightness is reduced by 20% from its previous 0.49 value.
        // Light mode retains its existing bright artwork-tinted surface.
        val miniPlayerBackgroundColor = remember(dynamicBgColor, isDark) {
            if (isDark) {
                val hsl = FloatArray(3)
                androidx.core.graphics.ColorUtils.colorToHSL(dynamicBgColor.toArgb(), hsl)
                hsl[2] = 0.49f * 0.8f
                Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
            } else {
                val hsl = FloatArray(3)
                androidx.core.graphics.ColorUtils.colorToHSL(dynamicBgColor.toArgb(), hsl)
                hsl[2] = hsl[2].coerceAtLeast(0.88f)
                Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
            }
        }
        val isCurrentSongDisliked by viewModel.isCurrentSongDisliked.collectAsStateWithLifecycle()
        val isCurrentSongDownloaded by viewModel.isCurrentSongDownloaded.collectAsStateWithLifecycle()
        val currentSongDownloadProgress by viewModel.currentSongDownloadProgress.collectAsStateWithLifecycle()
        val isLyricsModeEnabled by viewModel.isLyricsModeEnabled.collectAsStateWithLifecycle()
        val showSignInDialog by viewModel.showSignInDialog.collectAsStateWithLifecycle()
        if (showSignInDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissSignInDialog() },
                title = {
                    Text(
                        text = "Sign in Required",
                        style = DsmusicTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "To like or dislike songs on YouTube Music, please connect your account in Settings. This enables full synchronization of liked tracks and personalization algorithms.",
                        style = DsmusicTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    DsmusicTextButton(
                        onClick = {
                            viewModel.dismissSignInDialog()
                            activity.navigateToSettings()
                        }
                    ) {
                        Text(text = "Go to Settings", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    DsmusicTextButton(onClick = { viewModel.dismissSignInDialog() }) {
                        Text(text = "Cancel")
                    }
                }
            )
        }
        val lyricsArtworkProgress by animateFloatAsState(
            targetValue = if (isLyricsModeEnabled) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
            label = "LyricsArtworkSharedElement"
        )
 
        // While video mode is active, show the extracted video's own title/uploader rather
        // than leaving the player header stuck on the audio-track metadata.
        val displaySong = remember(currentSong, isVideoMode, videoPlaybackMetadata) {
            if (isVideoMode && videoPlaybackMetadata != null && currentSong != null) {
                currentSong!!.copy(
                    title = videoPlaybackMetadata!!.title,
                    artist = videoPlaybackMetadata!!.uploader
                )
            } else currentSong
        }

        val uiState = remember(
            displaySong, isPlaying, isLoading, duration, currentSongIndex,
            miniPlayerBlendColor, isCurrentSongFavorite, isCurrentSongDisliked, repeatMode, isShuffleEnabled,
            playerBackgroundColor, likesCount, isCurrentSongDownloaded, currentSongDownloadProgress
        ) {
            PlayerUiState(
                currentSong = displaySong,
                isPlaying = isPlaying,
                isLoading = isLoading,
                duration = duration,
                currentSongIndex = currentSongIndex,
                miniPlayerBlendColor = miniPlayerBlendColor,
                isCurrentSongFavorite = isCurrentSongFavorite,
                isCurrentSongDisliked = isCurrentSongDisliked,
                repeatMode = repeatMode,
                isShuffleEnabled = isShuffleEnabled,
                playerBackgroundColor = playerBackgroundColor,
                likesCount = likesCount,
                isCurrentSongDownloaded = isCurrentSongDownloaded,
                currentSongDownloadProgress = currentSongDownloadProgress
            )
        }

        // List is separate from quick state changes
        val songList by viewModel.songList.collectAsStateWithLifecycle()
        val isQueueExpanded by viewModel.isQueueExpanded.collectAsStateWithLifecycle()

        // Sliding Layout States
        val containerHeight = with(density) { maxHeight.toPx().toDp() }
        val containerWidthDp = maxWidth
        val screenHeightPx = with(density) { containerHeight.toPx() }
        val systemNavigationBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
        // Scaffold draws its bottom bar over the full-screen body. Use its measured
        // height (including the system navigation inset) exactly once when positioning
        // the mini player; the fallback mirrors the same nav-size settings before first measure.
        val bottomNavVisible = bottomNavigationVisible
        val dynamicBottomNavHeightState = viewModel.bottomNavHeight.collectAsStateWithLifecycle()
        val navBarSize by playerPreferences.observeKey("nav_bar_size", "Standard")
        val configuredBottomNavHeight = when (navBarSize) {
            "Compact" -> 56.dp
            "Tall" -> 76.dp
            else -> 68.dp
        }
        val fallbackBottomNavHeightPx = with(density) {
            configuredBottomNavHeight.toPx() +
                systemNavigationBottomPx
        }
        // The dock is now an overlay and no longer reduces Scaffold's content height.
        // Keep a collapsed mini-player immediately above it; when the dock is hidden,
        // use the full window height instead.
        val measuredBottomNavHeightPx = dynamicBottomNavHeightState.value
            .takeIf { it > 0f } ?: fallbackBottomNavHeightPx
        val miniPlayerBottomInsetPx = when {
            useNavigationRail -> systemNavigationBottomPx
            bottomNavVisible -> measuredBottomNavHeightPx
            else -> 0f
        }
        val miniPlayerBottomGapPx = with(density) {
            if (useNavigationRail) 8.dp.toPx() else 4.dp.toPx()
        }
        val sheetCollapsedTargetYState = remember(
            screenHeightPx, density, useNavigationRail, miniPlayerHeight,
            miniPlayerBottomInsetPx, miniPlayerBottomGapPx
        ) {
            derivedStateOf {
                val miniPlayerHeightPx = with(density) { miniPlayerHeight.toPx() }
                (screenHeightPx - miniPlayerHeightPx - miniPlayerBottomInsetPx - miniPlayerBottomGapPx)
                    .coerceIn(0f, screenHeightPx)
            }
        }

        // Restore the expanded player from the Activity-scoped ViewModel after rotation or
        // a window-size change. The media service keeps playing; the sheet must keep its state.
        val restoredPlayerExpanded = viewModel.isPlayerExpanded.value ||
            viewModel.playerExpansionFraction.value >= 0.5f
        val restoredExpansionFraction = if (restoredPlayerExpanded) {
            1f
        } else {
            viewModel.playerExpansionFraction.value.coerceIn(0f, 1f)
        }

        // Animation States
        val playerContentExpansionFraction = remember(viewModel) {
            Animatable(restoredExpansionFraction)
        }

        // Mirror only the current draw-phase progress to the dock. This is a
        // snapshot-state read by graphicsLayer, not a full tab-tree collection.
        LaunchedEffect(playerContentExpansionFraction) {
            snapshotFlow { playerContentExpansionFraction.value }
                .collect { viewModel.setPlayerExpansionProgress(it) }
        }

        // Queue-related layering changes only after the player sheet has completed
        // its mini-to-full morph, preventing the artwork from jumping behind the drawer.
        val isQueuePresentationActive by remember(isQueueExpanded, playerContentExpansionFraction) {
            derivedStateOf {
                isQueueExpanded && playerContentExpansionFraction.value >= 0.995f
            }
        }
        // In landscape the queue occupies a separate side pane. Keep the
        // shared artwork visible in the player pane instead of fading it away.
        val hideArtworkForQueue = isQueuePresentationActive && !isLandscapeWindow
        val artworkAlpha by animateFloatAsState(
            targetValue = if (hideArtworkForQueue) 0f else 1f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
            label = "ArtworkAlpha"
        )
        // Delay the first opening animation until the expanded player tree has actually
        // completed layout. A single withFrameNanos wait could still start while the
        // heavyweight lyrics/artwork/controls subtree was measuring.
        var fullPlayerLayoutReady by remember(viewModel) { mutableStateOf(false) }

        // Observe the animation through a threshold, not as a raw Float in composition.
        // The old direct reads recomposed the player subtree on every animation frame.
        val isPlayerFullyExpanded by remember(playerContentExpansionFraction) {
            derivedStateOf { playerContentExpansionFraction.value > 0.95f }
        }
        val isPlaybackReady by viewModel.isPlaybackReady.collectAsStateWithLifecycle()

        var currentSheetContentState by remember(viewModel) {
            mutableStateOf(
                if (restoredPlayerExpanded) PlayerSheetState.EXPANDED
                else PlayerSheetState.COLLAPSED
            )
        }

        fun performPlayerGesture(action: PlayerGestureAction, touchedLeftSide: Boolean = false) {
            when (action) {
                PlayerGestureAction.NEXT -> viewModel.playNextSong()
                PlayerGestureAction.PREVIOUS -> viewModel.playPreviousSong()
                PlayerGestureAction.SEEK_BACK -> viewModel.seekTo((viewModel.currentPosition.value - 10_000).coerceAtLeast(0))
                PlayerGestureAction.SEEK_AHEAD -> viewModel.seekTo(viewModel.currentPosition.value + 10_000)
                PlayerGestureAction.SEEK_SIDES -> {
                    val delta = gestureSettings.doubleTapSeekSeconds * 1000
                    viewModel.seekTo(if (touchedLeftSide) {
                        (viewModel.currentPosition.value - delta).coerceAtLeast(0)
                    } else {
                        viewModel.currentPosition.value + delta
                    })
                }
                PlayerGestureAction.PLAY_PAUSE -> {
                    if (isPlaying) viewModel.pauseAudio() else viewModel.playAudio()
                }
                PlayerGestureAction.LIKE -> viewModel.toggleFavorite()
                PlayerGestureAction.QUEUE -> {
                    // A queue gesture from the mini player first expands the sheet;
                    // the drawer itself is revealed only after the shared frame settles.
                    currentSheetContentState = PlayerSheetState.EXPANDED
                    viewModel.setQueueExpanded(true)
                }
                PlayerGestureAction.LYRICS -> viewModel.setLyricsModeEnabled(true)
                PlayerGestureAction.VIDEO -> audioService?.setVideoMode(!isVideoMode)
                PlayerGestureAction.STOP -> viewModel.dismissPlayer()
                PlayerGestureAction.OPEN -> currentSheetContentState = PlayerSheetState.EXPANDED
                PlayerGestureAction.NONE -> Unit
            }
        }

        fun handleCoverTap() {
            when {
                // The queue owns taps while open; do not pass them through to player actions.
                isQueueExpanded -> Unit
                isLyricsModeEnabled -> viewModel.setLyricsModeEnabled(false)
                currentSheetContentState != PlayerSheetState.EXPANDED ||
                    playerContentExpansionFraction.value < 0.95f -> {
                    currentSheetContentState = PlayerSheetState.EXPANDED
                }
                playerContentExpansionFraction.value >= 0.98f && !isQueuePresentationActive -> {
                    viewModel.setLyricsModeEnabled(true)
                }
            }
        }

        // Keep one animation owner for gesture, tap and Back transitions. The old drag handler
        // and the state observer both animated the same Animatables, sometimes cancelling a
        // collapse before the mini player reached its bottom position.
        var pendingSheetAnimation by remember { mutableStateOf<SheetAnimationRequest?>(null) }
        var sheetAnimationRequestId by remember { mutableStateOf(0) }
        var isSheetAnimating by remember { mutableStateOf(false) }

        val currentSheetTranslationY = remember(viewModel) {
            Animatable(sheetCollapsedTargetYState.value * (1f - restoredExpansionFraction))
        }

        // Route every close gesture/button through the same animation owner. Incrementing
        // the request id also handles a stale "collapsed" state after a partial gesture,
        // while clearing nested modes prevents Lyrics/Queue from keeping the full tree mounted.
        fun collapsePlayerFully() {
            viewModel.setLyricsModeEnabled(false)
            viewModel.setQueueExpanded(false)
            pendingSheetAnimation = SheetAnimationRequest(
                targetExpanded = false,
                animationSpec = tween(
                    durationMillis = 220,
                    easing = LinearEasing
                ),
                initialVelocity = 0f
            )
            sheetAnimationRequestId += 1
            currentSheetContentState = PlayerSheetState.COLLAPSED
        }

        LaunchedEffect(viewModel) {
            viewModel.playerBackCollapseRequests.collect {
                // A second Back while the player is already closing is consumed by
                // the Activity-level handler but must not restart its animation.
                if (pendingSheetAnimation?.targetExpanded != false) {
                    if (
                        currentSheetContentState == PlayerSheetState.EXPANDED ||
                        playerContentExpansionFraction.value > 0.01f
                    ) {
                        collapsePlayerFully()
                    }
                }
            }
        }

        // A rotation changes the sheet's coordinate space. Recalculate its Y position using
        // the new height while retaining the previously saved expansion fraction.
        LaunchedEffect(screenHeightPx, containerWidthDp, useNavigationRail, miniPlayerBottomInsetPx) {
            // Local sheet state is authoritative. The ViewModel expanded flag is updated
            // asynchronously and can still be true when the close animation begins.
            if (pendingSheetAnimation != null ||
                currentSheetTranslationY.isRunning ||
                playerContentExpansionFraction.isRunning
            ) return@LaunchedEffect

            val expanded = currentSheetContentState == PlayerSheetState.EXPANDED
            val fraction = if (expanded) 1f else 0f
            playerContentExpansionFraction.snapTo(fraction)
            currentSheetTranslationY.snapTo(
                if (expanded) 0f else sheetCollapsedTargetYState.value.coerceIn(0f, screenHeightPx)
            )
        }
        val offsetAnimatable = remember { Animatable(0f) }

        val screenWidthPx = with(density) { maxWidth.toPx() }
        
        var isDragging by remember { mutableStateOf(false) }

        val showPlayerContentArea = currentSong != null
        // Keep the same shared artwork instance on API 23 and newer devices.
        val isLegacyCompactArtwork = false

        LaunchedEffect(currentSheetContentState) {
            viewModel.setPlayerExpanded(currentSheetContentState == PlayerSheetState.EXPANDED)
        }

        // Synchronize dock changes both during and after a player transition.
        // If the dock moves while the sheet is animating, apply the latest target
        // when the animation owner finishes instead of silently discarding the update.
        LaunchedEffect(screenHeightPx, miniPlayerBottomInsetPx) {
            snapshotFlow {
                Triple(
                    sheetCollapsedTargetYState.value,
                    isSheetAnimating,
                    currentSheetContentState == PlayerSheetState.COLLAPSED &&
                        pendingSheetAnimation == null && !isDragging
                )
            }.collect { (targetY, animationRunning, shouldSynchronize) ->
                if (shouldSynchronize &&
                    !animationRunning &&
                    !currentSheetTranslationY.isRunning &&
                    currentSheetTranslationY.value != screenHeightPx
                ) {
                    currentSheetTranslationY.snapTo(
                        targetY.coerceIn(0f, screenHeightPx)
                    )
                }
            }
        }

        val sheetBackProgress = 0f


        // Auto-expand/collapse sheet when currentSong is changed / ready
        LaunchedEffect(currentSong, isPlaybackReady) {
            if (currentSong != null && isPlaybackReady) {
                if (currentSheetTranslationY.value == screenHeightPx) {
                    currentSheetTranslationY.animateTo(
                        targetValue = sheetCollapsedTargetYState.value,
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                    )
                    currentSheetContentState = PlayerSheetState.COLLAPSED
                }
            }
        }

        // Dynamic Blend Colors from Artwork
        val baseAccentColor = remember(miniPlayerBlendColor, isDark) {
            val rawAccent = if (miniPlayerBlendColor != 0) Color(miniPlayerBlendColor) else Color(0xFF8338EC)
            val hsl = FloatArray(3)
            androidx.core.graphics.ColorUtils.colorToHSL(rawAccent.toArgb(), hsl)
            if (hsl[1] < 0.05f) {
                // Monochromatic / Grayscale
                if (isDark) Color.White else Color(0xFF1C1B1F)
            } else {
                rawAccent
            }
        }

        // Resolves sheet animations
        suspend fun animatePlayerSheet(
            targetExpanded: Boolean,
            animationSpec: AnimationSpec<Float>? = null,
            initialVelocity: Float = 0f
        ) {
            // Use the exact measured target. Recalculating dock height here gave the
            // animation a different destination while the bottom dock was appearing.
            val destY = if (targetExpanded) {
                0f
            } else {
                sheetCollapsedTargetYState.value.coerceIn(0f, screenHeightPx)
            }
            val destFraction = if (targetExpanded) 1f else 0f
            val resolvedAnimationSpec = animationSpec
                ?: tween(durationMillis = 360, easing = FastOutSlowInEasing)
            val collapsedDistance = sheetCollapsedTargetYState.value.coerceAtLeast(1f)
            val fractionInitialVelocity = (-initialVelocity / collapsedDistance)
                .takeIf { it.isFinite() } ?: 0f

            isSheetAnimating = true
            try {
                // Y position and expansion fraction run on the same animation clock.
                // This moves and grows the same clipped frame instead of snapping the
                // sheet to an endpoint and fading two unrelated surfaces.
                coroutineScope {
                    val translationJob = launch {
                        currentSheetTranslationY.animateTo(
                            targetValue = destY,
                            animationSpec = resolvedAnimationSpec,
                            initialVelocity = initialVelocity
                        )
                    }
                    try {
                        playerContentExpansionFraction.animateTo(
                            targetValue = destFraction,
                            animationSpec = resolvedAnimationSpec,
                            initialVelocity = fractionInitialVelocity
                        )
                        translationJob.join()
                    } finally {
                        translationJob.cancel()
                    }
                }
                // Persist only the settled state. Publishing every animation frame to the
                // Activity ViewModel creates unnecessary flow work and UI invalidations.
                viewModel.setPlayerExpansionFraction(destFraction)
            } finally {
                isSheetAnimating = false
            }
        }

        // This is the only place that animates the sheet. Taps and Back share one easing curve;
        // drag gestures can supply their velocity without starting a competing animation.
        LaunchedEffect(currentSheetContentState, sheetAnimationRequestId) {
            val targetExpanded = currentSheetContentState == PlayerSheetState.EXPANDED
            val request = pendingSheetAnimation
                ?.takeIf { it.targetExpanded == targetExpanded }
            pendingSheetAnimation = null

            // Let the expanded player finish its first real layout before animating. This
            // moves its one-time composition/measurement cost out of the animated frames.
            if (targetExpanded && !fullPlayerLayoutReady) {
                snapshotFlow { fullPlayerLayoutReady }.first { it }
            }
            if (request != null) {
                animatePlayerSheet(
                    targetExpanded = request.targetExpanded,
                    animationSpec = request.animationSpec,
                    initialVelocity = request.initialVelocity
                )
            } else {
                animatePlayerSheet(targetExpanded = targetExpanded)
            }
            if (!targetExpanded) fullPlayerLayoutReady = false
        }

        val sheetVisualState = rememberSheetVisualState(
            showPlayerContentArea = showPlayerContentArea,
            collapsedStateHorizontalPadding = 12.dp,
            predictiveBackCollapseProgress = sheetBackProgress,
            playerContentExpansionFraction = playerContentExpansionFraction,
            containerHeight = containerHeight,
            currentSheetTranslationY = currentSheetTranslationY,
            sheetCollapsedTargetYProvider = { sheetCollapsedTargetYState.value },
            isNavBarHiddenProvider = {
                if (useNavigationRail) playerContentExpansionFraction.value > 0.5f
                else !bottomNavVisible
            },
            navBarCornerRadiusDp = 32.dp,
            miniPlayerHeight = miniPlayerHeight
        )

        // The mini shape values are also used by the shared frame outline below.
        val miniPlayerShape: Shape = when (miniPlayerStyle) {
            "Flat" -> RoundedCornerShape(0.dp)
            "Compact" -> RoundedCornerShape(18.dp)
            "Pill" -> CircleShape
            else -> RoundedCornerShape(22.dp)
        }
        val miniPlayerElevation = if (miniPlayerStyle == "Card") 6.dp else 0.dp
        val miniPlayerCornerRadius = when (miniPlayerStyle) {
            "Flat" -> 0.dp
            "Compact" -> 18.dp
            "Pill" -> miniPlayerHeight / 2f
            else -> 22.dp
        }

        // Keep the player host full-width throughout the transition. In landscape,
        // reserve the rail only for the collapsed capsule and animate its center and
        // width into the content pane instead of remeasuring the host at the endpoint.
        val effectiveMiniRailInset = if (isLandscapeWindow) {
            navigationRailInset.coerceAtLeast(0.dp).coerceAtMost(containerWidthDp)
        } else {
            0.dp
        }
        val miniPlayerHorizontalOffset = effectiveMiniRailInset / 2f
        val miniPlayerWidthFraction = if (isLandscapeWindow) {
            val paneWidthFraction = (
                (containerWidthDp - effectiveMiniRailInset) / containerWidthDp.coerceAtLeast(1.dp)
            ).coerceIn(0f, 1f)
            0.5f * paneWidthFraction
        } else {
            1f
        }
        val miniPlayerHorizontalOffsetPx = with(density) { miniPlayerHorizontalOffset.toPx() }
        val miniFrameWidthFraction = remember(containerWidthDp, density, miniPlayerWidthFraction) {
            with(density) {
                val fullWidthPx = containerWidthDp.toPx().coerceAtLeast(1f)
                (
                    (fullWidthPx * miniPlayerWidthFraction - 48.dp.toPx())
                        .coerceAtLeast(0f) / fullWidthPx
                )
            }
        }
        val miniPlayerCornerRadiusPx = with(density) { miniPlayerCornerRadius.toPx() }

        val velocityTracker = remember { VelocityTracker() }
        
        val sheetVerticalDragGestureHandler = remember(density) {
            val miniHeightPx = with(density) { miniPlayerHeight.toPx() }
            SheetVerticalDragGestureHandler(
                scope = scope,
                velocityTracker = velocityTracker,
                densityProvider = { density },
                playerContentExpansionFraction = playerContentExpansionFraction,
                currentSheetTranslationY = currentSheetTranslationY,
                expandedYProvider = { 0f },
                collapsedYProvider = { sheetCollapsedTargetYState.value },
                miniHeightPxProvider = { miniHeightPx },
                currentSheetStateProvider = { currentSheetContentState },
                onAnimateSheet = { target, animationSpec, velocity ->
                    pendingSheetAnimation = SheetAnimationRequest(target, animationSpec, velocity)
                    sheetAnimationRequestId += 1
                },
                onExpandSheetState = { currentSheetContentState = PlayerSheetState.EXPANDED },
                onCollapseSheetState = { currentSheetContentState = PlayerSheetState.COLLAPSED },
                onDragStateChange = { dragging -> isDragging = dragging }
            )
        }

        val miniDismissGestureHandler = rememberMiniPlayerDismissGestureHandler(
            scope = scope,
            density = density,
            offsetAnimatable = offsetAnimatable,
            screenWidthPx = screenWidthPx,
            onDismissQueue = {
                scope.launch {
                    currentSheetTranslationY.snapTo(screenHeightPx)
                }
                viewModel.dismissPlayer()
            }
        )

        val miniSwipeGestureModifier = Modifier.pointerInput(
            gestureSettings, isLyricsModeEnabled, isQueueExpanded
        ) {
            var dragX = 0f
            var dragY = 0f
            detectDragGestures(
                onDragStart = {
                    dragX = 0f
                    dragY = 0f
                },
                onDrag = { change, amount ->
                    dragX += amount.x
                    dragY += amount.y
                    change.consume()
                },
                onDragEnd = {
                    if (isLyricsModeEnabled || isQueueExpanded) return@detectDragGestures
                    if (kotlin.math.max(kotlin.math.abs(dragX), kotlin.math.abs(dragY)) < 24.dp.toPx()) {
                        return@detectDragGestures
                    }
                    when {
                        kotlin.math.abs(dragX) >= kotlin.math.abs(dragY) && dragX < 0 ->
                            performPlayerGesture(gestureSettings.miniSwipeLeft)
                        kotlin.math.abs(dragX) >= kotlin.math.abs(dragY) && dragX > 0 ->
                            performPlayerGesture(gestureSettings.miniSwipeRight)
                        // Swipe up is the fixed mini-player expand gesture; it is no
                        // longer a configurable action in Controls & Gestures.
                        dragY < 0 -> performPlayerGesture(PlayerGestureAction.OPEN)
                        // Pull down always executes the selected miniSwipeDown action.
                        else -> performPlayerGesture(gestureSettings.miniSwipeDown)
                    }
                }
            )
        }

        // Landscape should reveal the tab content beside the collapsed capsule.
        // Do not mount the full-window opaque backdrop until the sheet has actually started
        // expanding; portrait keeps its existing fade behavior unchanged.
        val showLandscapePlayerBackdrop by remember(playerContentExpansionFraction) {
            derivedStateOf { playerContentExpansionFraction.value > 0.015f }
        }

        if (showPlayerContentArea) {
            DsmusicSurface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = playerContentExpansionFraction.value.coerceIn(0f, 1f)
                        translationY = sheetVisualState.visualSheetTranslationYProvider()
                        shape = PlayerSheetAnimatedFrameShape(
                            progress = progress,
                            miniWidthFraction = miniFrameWidthFraction,
                            miniHeightPx = with(density) { miniPlayerHeight.toPx() },
                            miniCornerRadiusPx = miniPlayerCornerRadiusPx,
                            miniCenterOffsetPx = miniPlayerHorizontalOffsetPx
                        )
                        clip = true
                    },
                // One surface morphs from the dock card into the fullscreen player.
                // The player tree stays full-window measured; only the graphics-layer
                // outline changes during animation.
                shape = androidx.compose.ui.graphics.RectangleShape,
                color = Color.Transparent,
                showBorder = false
            ) {
                // Keep the areas outside the mini-player capsule transparent while the capsule
                // itself retains its solid surface. Fade the full-player background on its own draw layer.
                // invalidate this entire composition tree.
                if (isLandscapeWindow) {
                    // Paint the backdrop with color alpha directly instead of a full-window
                    // alpha layer. At the collapsed endpoint this leaves both side gutters
                    // completely untouched, while still fading the backdrop in on expansion.
                    if (showLandscapePlayerBackdrop) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    dynamicBgColor.copy(
                                        alpha = playerContentExpansionFraction.value.coerceIn(0f, 1f)
                                    )
                                )
                        )
                    }
                } else {
                    // Preserve the existing portrait transition exactly.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = playerContentExpansionFraction.value.coerceIn(0f, 1f)
                            }
                            .background(dynamicBgColor)
                    )
                }
                Box(modifier = Modifier.fillMaxSize().padding(bottom = sheetVisualState.currentBottomPadding.coerceAtLeast(0.dp))) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (isQueuePresentationActive) 5f else 2f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = offsetAnimatable.value
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                }
                        ) {
                            val verticalDragModifier = Modifier.playerSheetVerticalDragGesture(
                                enabled = currentSheetContentState == PlayerSheetState.EXPANDED && !isLyricsModeEnabled && !isQueueExpanded,
                                handler = sheetVerticalDragGestureHandler
                            )

                            UnifiedPlayerMiniAndFullLayers(
                                activity = activity,
                                viewModel = viewModel,
                                uiState = uiState,
                                songList = songList,
                                baseAccentColor = baseAccentColor,
                                context = context,
                                playerContentExpansionFraction = playerContentExpansionFraction,
                                isSheetExpanded = currentSheetContentState == PlayerSheetState.EXPANDED,
                                onNavigateToListenTogether = onNavigateToListenTogether,
                                onFullPlayerLayoutReady = { fullPlayerLayoutReady = true },
                                containerHeight = containerHeight,
                                onCollapse = { collapsePlayerFully() },
                                onExpand = { currentSheetContentState = PlayerSheetState.EXPANDED },
                                dragModifier = verticalDragModifier,
                                miniPlayerHeight = miniPlayerHeight,
                                miniPlayerWidthFraction = miniPlayerWidthFraction,
                                miniPlayerHorizontalOffset = miniPlayerHorizontalOffset,
                                miniPlayerBackgroundColor = miniPlayerBackgroundColor,
                                miniPlayerSurfaceAlpha = 1f,
                                miniPlayerShape = miniPlayerShape,
                                miniPlayerElevation = miniPlayerElevation,
                                miniSwipeModifier = miniSwipeGestureModifier,
                                isFullscreenVisualizerOpen = isFullscreenVisualizerOpen,
                                onFullscreenVisualizerVisibilityChanged = {
                                    isFullscreenVisualizerOpen = it
                                }
                            )
                        }
                    }

                    // Integrated shared artwork layer. In lyrics mode the same ArtworkPager
                    // moves into the app bar as a compact thumbnail instead of being hidden.
                    val albumArtScale = when (playerLayout) {
                        "Cover" -> 0.98f
                        "Minimal" -> 0.68f
                        "Vinyl", "Cassette" -> 0.82f
                        else -> 0.9f
                    }
                    val artworkScaleFactor = playerArtworkScale.coerceIn(0.65f, 1f)
                    val portraitArtHeightFactor = when {
                        containerHeight < 680.dp -> 0.30f
                        containerHeight < 760.dp -> 0.34f
                        else -> 0.40f
                    }
                    val landscapeArtHeightFactor = when {
                        containerHeight < 520.dp -> 0.18f
                        containerHeight < 640.dp -> 0.22f
                        else -> 0.28f
                    }
                    val widePortraitArtHeightFactor =
                        if (containerHeight < 760.dp) 0.34f else 0.50f
                    // Reserve a stable artwork slot for the controls column. The selected
                    // artwork scale changes only the floating cover's rendered size.
                    val albumArtSlotSizeDp = if (isLandscapeWindow) {
                        minOf(containerHeight * landscapeArtHeightFactor, containerWidthDp * 0.30f) *
                            (albumArtScale / 0.9f)
                    } else if (useWidePlayer) {
                        minOf(containerHeight * widePortraitArtHeightFactor, containerWidthDp * 0.42f) *
                            (albumArtScale / 0.9f)
                    } else {
                        minOf(
                            containerWidthDp * albumArtScale,
                            containerHeight * portraitArtHeightFactor
                        )
                    }
                    val albumArtSizeDp = albumArtSlotSizeDp * artworkScaleFactor
                    val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()
                    // Keep the shared cover aligned with the player pane while the
                    // landscape queue animates into its dedicated 42% side panel.
                    val sharedArtworkQueueInset = androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (
                            isLandscapeWindow && useWidePlayer && isQueuePresentationActive
                        ) containerWidthDp * 0.42f else 0.dp,
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
                        label = "SharedArtworkQueueInset"
                    )

                    if (!isLegacyCompactArtwork && !isFullscreenVisualizerOpen) {
                    Box(
                        modifier = Modifier
                            .size(albumArtSizeDp)
                            .graphicsLayer {
                                val t = playerContentExpansionFraction.value
                                val lyricsT = lyricsArtworkProgress

                                val miniSizePx = 48.dp.toPx()
                                val fullSizePx = albumArtSizeDp.toPx()
                                // The selected mini-cover shapes sit slightly smaller inside
                                // the unchanged 48 dp slot. Slanted keeps its width and shrinks
                                // only vertically.
                                val miniShapeScaleX = miniArtworkWidthScale(playerArtworkShapeName)
                                val miniShapeScaleY = miniArtworkHeightScale(playerArtworkShapeName)
                                // One shared cover travels from its exact mini-player anchor
                                // to the full-player anchor. All motion stays in this draw layer.
                                val artTransitionProgress = t.coerceIn(0f, 1f)
                                val normalScaleX = androidx.compose.ui.util.lerp(
                                    miniSizePx * miniShapeScaleX / fullSizePx,
                                    1f,
                                    artTransitionProgress
                                )
                                val normalScaleY = androidx.compose.ui.util.lerp(
                                    miniSizePx * miniShapeScaleY / fullSizePx,
                                    1f,
                                    artTransitionProgress
                                )

                                // Match the shared cover to the actual capsule content origin:
                                // 24dp capsule inset + 10dp row inset + centered 48dp art slot.
                                // Landscape centers the half-width capsule in the full player pane.
                                val miniArtworkSlotWidth = if (isLandscapeWindow) 48.dp else 60.dp
                                val miniArtworkContentInset =
                                    24.dp + 10.dp + (miniArtworkSlotWidth - 48.dp) / 2f
                                // Keep the title's row position unchanged while moving the
                                // shared cover toward it by half of the actual visible gap.
                                // Include the selected shape's horizontal scale so Rounded and
                                // Square covers receive the same 50% gap reduction as other shapes.
                                val miniTitleStartInset = if (isLandscapeWindow) {
                                    24.dp + 10.dp + 8.8.dp + 48.dp + 1.2.dp
                                } else {
                                    24.dp + 10.dp + 2.dp + 60.dp + 2.dp
                                }
                                val miniArtworkContentInsetPx = with(density) {
                                    miniArtworkContentInset.toPx()
                                }
                                val miniTitleStartInsetPx = with(density) {
                                    miniTitleStartInset.toPx()
                                }
                                val miniVisibleArtworkRightPx =
                                    miniArtworkContentInsetPx + miniSizePx * (1f + miniShapeScaleX) / 2f
                                val miniArtworkGapReductionPx =
                                    (miniTitleStartInsetPx - miniVisibleArtworkRightPx)
                                        .coerceAtLeast(0f) / 2f
                                val xStartPx = with(density) {
                                    miniArtworkContentInset.toPx() + miniArtworkGapReductionPx +
                                        if (isLandscapeWindow) {
                                            (containerWidthDp * ((1f - miniPlayerWidthFraction) / 2f)).toPx() +
                                                miniPlayerHorizontalOffsetPx
                                        } else {
                                            0f
                                        }
                                }
                                val xEndPx = if (useWidePlayer) {
                                    with(density) {
                                        val queueInset = sharedArtworkQueueInset.value.coerceAtLeast(0.dp)
                                        val remainingPlayerWidth = (containerWidthDp - queueInset - 60.dp)
                                            .coerceAtLeast(0.dp)
                                        (
                                            20.dp + queueInset +
                                                remainingPlayerWidth * 0.54f / 2f -
                                                albumArtSizeDp / 2f
                                            ).toPx()
                                    }
                                } else {
                                    (with(density) { containerWidthDp.toPx() } - fullSizePx) / 2f
                                }

                                val miniPlayerArtworkStartInset = with(density) {
                                    ((miniPlayerHeight - 48.dp) / 2f).toPx()
                                }
                                val yStartPx = miniPlayerArtworkStartInset
                                val minTopOffsetDp = with(density) { statusBarTopPx.toDp() } +
                                    when {
                                        isLandscapeWindow && useWidePlayer -> 84.dp
                                        isLandscapeWindow -> 60.dp
                                        else -> 76.dp
                                    }
                                val topOffsetDp = if (useWidePlayer) {
                                    minTopOffsetDp
                                } else {
                                    ((containerHeight - albumArtSlotSizeDp) / 2f - 220.dp).coerceAtLeast(minTopOffsetDp)
                                }
                                // Keep the cover centered in the fixed slot while its visible
                                // size changes; the content below the slot remains stationary.
                                val yEndPx = with(density) {
                                    (topOffsetDp + (albumArtSlotSizeDp - albumArtSizeDp) / 2f).toPx()
                                }

                                // Keep the smaller mini artwork centered in its original
                                // 48 dp slot, removing the inset as it grows to full size.
                                val miniCenterInsetX = miniSizePx * (1f - miniShapeScaleX) / 2f
                                val miniCenterInsetY = miniSizePx * (1f - miniShapeScaleY) / 2f
                                val normalX = androidx.compose.ui.util.lerp(
                                    xStartPx + miniCenterInsetX * (1f - artTransitionProgress),
                                    xEndPx,
                                    artTransitionProgress
                                )
                                val normalY = androidx.compose.ui.util.lerp(
                                    sheetCollapsedTargetYState.value + yStartPx +
                                        miniCenterInsetY * (1f - artTransitionProgress),
                                    yEndPx,
                                    artTransitionProgress
                                )
                                val normalShadow = androidx.compose.ui.util.lerp(
                                    0f,
                                    16.dp.toPx(),
                                    artTransitionProgress
                                )

                                val appBarThumbSizePx = 44.dp.toPx()
                                val appBarScale = appBarThumbSizePx / fullSizePx
                                val appBarX = 22.dp.toPx()
                                val appBarY = statusBarTopPx + 18.dp.toPx()

                                val dismissOffsetPx = offsetAnimatable.value * (1f - t)

                                scaleX = androidx.compose.ui.util.lerp(normalScaleX, appBarScale, lyricsT)
                                scaleY = androidx.compose.ui.util.lerp(normalScaleY, appBarScale, lyricsT)
                                translationX = androidx.compose.ui.util.lerp(normalX, appBarX, lyricsT) + dismissOffsetPx
                                translationY = androidx.compose.ui.util.lerp(normalY, appBarY, lyricsT) -
                                    currentSheetTranslationY.value
                                transformOrigin = TransformOrigin(0f, 0f)

                                // Keep the selected artwork outline on the shared cover;
                                // a second forced rounded clip made custom shapes disagree
                                // with the visible mini-player progress.
                                shape = if (isVideoMode) androidx.compose.ui.graphics.RectangleShape else playerArtworkShape
                                clip = true
                                shadowElevation = androidx.compose.ui.util.lerp(normalShadow, 6.dp.toPx(), lyricsT)
                                alpha = artworkAlpha
                            }
                            // Put the click target inside the transformed layer. With clickable
                            // outside graphicsLayer, taps on the old 90%-screen layout bounds could
                            // open lyrics even when the visible cover was elsewhere/smaller.
                            .pointerInput(
                                gestureSettings,
                                currentSheetContentState,
                                isLyricsModeEnabled,
                                isQueueExpanded,
                                isVideoMode
                            ) {
                                detectTapGestures(
                                    onTap = {
                                        if (!isVideoMode || playerContentExpansionFraction.value <= 0.95f) {
                                            handleCoverTap()
                                        }
                                    },
                                    onDoubleTap = { position ->
                                        if (currentSheetContentState == PlayerSheetState.EXPANDED &&
                                            playerContentExpansionFraction.value >= 0.95f &&
                                            !isLyricsModeEnabled && !isQueueExpanded
                                        ) {
                                            performPlayerGesture(
                                                gestureSettings.coverDoubleTap,
                                                touchedLeftSide = position.x < size.width / 2f
                                            )
                                        }
                                    },
                                    onLongPress = {
                                        if (currentSheetContentState == PlayerSheetState.EXPANDED &&
                                            playerContentExpansionFraction.value >= 0.95f
                                        ) {
                                            performPlayerGesture(gestureSettings.coverLongPress)
                                        }
                                    }
                                )
                            }
                            .pointerInput(
                                gestureSettings,
                                currentSheetContentState,
                                isLyricsModeEnabled,
                                isQueueExpanded,
                                isVideoMode
                            ) {
                                var dragX = 0f
                                var dragY = 0f
                                detectDragGestures(
                                    onDragStart = {
                                        dragX = 0f
                                        dragY = 0f
                                    },
                                    onDrag = { change, amount ->
                                        dragX += amount.x
                                        dragY += amount.y
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        if (currentSheetContentState != PlayerSheetState.EXPANDED ||
                                            playerContentExpansionFraction.value < 0.95f ||
                                            isLyricsModeEnabled || isQueueExpanded || isVideoMode
                                        ) return@detectDragGestures

                                        val threshold = 34.dp.toPx()
                                        if (kotlin.math.max(kotlin.math.abs(dragX), kotlin.math.abs(dragY)) < threshold) return@detectDragGestures
                                        when {
                                            kotlin.math.abs(dragX) >= kotlin.math.abs(dragY) && dragX < 0 ->
                                                performPlayerGesture(gestureSettings.coverSwipeLeft)
                                            kotlin.math.abs(dragX) >= kotlin.math.abs(dragY) && dragX > 0 ->
                                                performPlayerGesture(gestureSettings.coverSwipeRight)
                                            dragY < 0 ->
                                                performPlayerGesture(gestureSettings.coverSwipeUp)
                                            else -> currentSheetContentState = PlayerSheetState.COLLAPSED
                                        }
                                    }
                                )
                            }
                            .zIndex(
                                if (isQueuePresentationActive) {
                                    // The queue is confined to the left dock in wide
                                    // landscape mode; keep the shifted artwork above the
                                    // player content so it is not hidden by the full-screen layer.
                                    if (isLandscapeWindow && useWidePlayer) 6f else 1f
                                } else if (isLyricsModeEnabled || lyricsArtworkProgress > 0f) {
                                    6f
                                } else {
                                    3f
                                }
                            )
                    ) {
                        if (
                            isVideoMode &&
                            audioService != null &&
                            isPlayerFullyExpanded &&
                            !isLyricsModeEnabled &&
                            !isQueuePresentationActive
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                if (!isVideoFullscreen) {
                                    BoxWithConstraints(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // The shared artwork container already has the preferred
                                        // artwork-scaled dimensions; use its measured bounds directly.
                                        val squareVideoSize = minOf(maxWidth, maxHeight)
                                        androidx.compose.ui.viewinterop.AndroidView(
                                        factory = { viewContext ->
                                            (android.view.LayoutInflater.from(viewContext)
                                                .inflate(com.dsann.music.R.layout.view_video_player, null)
                                                as androidx.media3.ui.PlayerView).apply {
                                                player = audioService?.getPlayerInstance()
                                                useController = true
                                                controllerShowTimeoutMs = 3000
                                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                                keepScreenOn = true
                                            }
                                        },
                                        update = { playerView ->
                                            playerView.player = audioService?.getPlayerInstance()
                                        },
                                        modifier = Modifier.size(squareVideoSize)
                                    )
                                    }
                                }
                                VideoOverlayControls(
                                    qualityLabel = selectedVideoQuality,
                                    qualityEnabled = videoQualityOptions.isNotEmpty() && !videoQualityLoading,
                                    onOpenQuality = { showVideoQualityDialog = true },
                                    onToggleFullscreen = { isVideoFullscreen = !isVideoFullscreen },
                                    fullscreen = false
                                )
                            }

                            if (isVideoFullscreen) {
                                Dialog(
                                    onDismissRequest = { isVideoFullscreen = false },
                                    properties = DialogProperties(usePlatformDefaultWidth = false)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black)
                                    ) {
                                        BoxWithConstraints(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val squareVideoSize = minOf(maxWidth, maxHeight)
                                        androidx.compose.ui.viewinterop.AndroidView(
                                            factory = { viewContext ->
                                                (android.view.LayoutInflater.from(viewContext)
                                                .inflate(com.dsann.music.R.layout.view_video_player, null)
                                                as androidx.media3.ui.PlayerView).apply {
                                                player = audioService?.getPlayerInstance()
                                                useController = true
                                                controllerShowTimeoutMs = 3000
                                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                                keepScreenOn = true
                                            }
                                            },
                                            update = { playerView ->
                                                playerView.player = audioService?.getPlayerInstance()
                                            },
                                            modifier = Modifier.size(squareVideoSize)
                                        )
                                    }
                                        VideoOverlayControls(
                                            qualityLabel = selectedVideoQuality,
                                            qualityEnabled = videoQualityOptions.isNotEmpty() && !videoQualityLoading,
                                            onOpenQuality = { showVideoQualityDialog = true },
                                            onToggleFullscreen = { isVideoFullscreen = false },
                                            fullscreen = true
                                        )
                                    }
                                }
                            }

                            if (showVideoQualityDialog) {
                                AlertDialog(
                                    onDismissRequest = { showVideoQualityDialog = false },
                                    title = { Text("Video quality") },
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (videoQualityLoading) {
                                                Text("Loading available qualities…")
                                            } else if (videoQualityOptions.isEmpty()) {
                                                Text("No alternative muxed video qualities are available for this track.")
                                            } else {
                                                videoQualityOptions.forEach { option ->
                                                    DsmusicTextButton(
                                                        onClick = {
                                                            audioService?.setVideoStreamQuality(
                                                                streamUrl = option.url,
                                                                audioUrl = option.audioUrl,
                                                                videoMimeType = option.mimeType,
                                                                audioMimeType = option.audioMimeType
                                                            )
                                                            selectedVideoQuality = option.label
                                                            preferredVideoQualityHeight = option.height
                                                            playerPreferences.edit()
                                                                .putInt("video_quality_preferred_height", option.height)
                                                                .putString("video_quality_preferred_label", option.label)
                                                                .apply()
                                                            viewModel.setVideoPlaybackMetadata(
                                                                com.dsann.music.viewmodel.VideoPlaybackMetadata(
                                                                    title = option.videoTitle?.takeIf { it.isNotBlank() }
                                                                        ?: videoPlaybackMetadata?.title
                                                                        ?: currentSong?.title.orEmpty(),
                                                                    uploader = option.videoUploader?.takeIf { it.isNotBlank() }
                                                                        ?: videoPlaybackMetadata?.uploader
                                                                        ?: currentSong?.artist.orEmpty(),
                                                                    durationMs = option.durationMs.takeIf { it > 0L }
                                                                        ?: videoPlaybackMetadata?.durationMs ?: 0L,
                                                                    contentLengthBytes = option.contentLengthBytes,
                                                                    bitrateBps = option.bitrateBps,
                                                                    resolution = option.label,
                                                                    mimeType = option.mimeType,
                                                                    description = option.videoDescription ?: videoPlaybackMetadata?.description,
                                                                    viewCount = option.videoViewCount ?: videoPlaybackMetadata?.viewCount,
                                                                    likeCount = option.videoLikeCount ?: videoPlaybackMetadata?.likeCount,
                                                                    uploadDate = option.videoUploadDate ?: videoPlaybackMetadata?.uploadDate,
                                                                    thumbnailUrl = option.videoThumbnailUrl ?: videoPlaybackMetadata?.thumbnailUrl,
                                                                    uploaderUrl = option.videoUploaderUrl ?: videoPlaybackMetadata?.uploaderUrl
                                                                )
                                                            )
                                                            showVideoQualityDialog = false
                                                        }
                                                    ) {
                                                        Text(
                                                            if (selectedVideoQuality == option.label) {
                                                                "✓  ${option.label}"
                                                            } else {
                                                                option.label
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        DsmusicTextButton(onClick = { showVideoQualityDialog = false }) {
                                            Text("Close")
                                        }
                                    }
                                )
                            }
                        } else {
                            ArtworkPager(
                                viewModel = viewModel,
                                currentSong = currentSong!!,
                                songList = songList,
                                currentSongIndex = uiState.currentSongIndex,
                                context = context,
                                userScrollEnabled = isPlayerFullyExpanded && !isLyricsModeEnabled && !isQueuePresentationActive,
                                modifier = Modifier.fillMaxSize()
                            )
                            MiniPlayerArtworkProgressOverlay(
                                viewModel = viewModel,
                                accentColor = baseAccentColor,
                                backgroundColor = dynamicBgColor,
                                coverDerivedColor = Color(playerBackgroundColor),
                                expansionFraction = playerContentExpansionFraction,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    }
                }
            }
        }

        // Keep the Search song-recognizer shortcut visible even when playback is empty.
        // Its bottom anchor always reserves the collapsed mini-player slot, so its position
        // stays identical before and after a song starts.
        if (showSongRecognizerShortcut &&
            playerContentExpansionFraction.value < 0.05f
        ) {
            val collapsedPlayerLift = with(density) {
                (screenHeightPx - sheetCollapsedTargetYState.value).toDp()
            }
            val recognizerBottomInset = (collapsedPlayerLift - 1.dp).coerceAtLeast(0.dp)
            DsmusicIconButton(
                onClick = onNavigateToSongRecognizer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = recognizerBottomInset)
                    .size(42.dp)
                    .shadow(8.dp, CircleShape)
                    .background(DsmusicTheme.colorScheme.primary, CircleShape)
                    .border(
                        width = 1.dp,
                        color = DsmusicTheme.colorScheme.onPrimary.copy(alpha = 0.24f),
                        shape = CircleShape
                    ),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "Recognize a song",
                    tint = DsmusicTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Register after the full player and every screen underneath it have composed.
        // The latest enabled Compose BackHandler wins, so the player must be last here.
        androidx.activity.compose.BackHandler(
            enabled = currentSheetContentState == PlayerSheetState.EXPANDED &&
                !isLyricsModeEnabled && !isQueueExpanded
        ) {
            collapsePlayerFully()
        }
    }
}


// ==========================================
// 8. MINI PLAYER ROW INTERNAL CONTENT
// ==========================================

/** Mini-only artwork sizing; the full-player cover remains at its selected size. */
private fun miniArtworkWidthScale(shapeName: String): Float =
    if (shapeName == "Rounded" || shapeName == "Square") 0.9f else 1f

private fun miniArtworkHeightScale(shapeName: String): Float =
    if (shapeName == "Rounded" || shapeName == "Square" || shapeName == "Slanted") 0.9f else 1f

@Composable
private fun MiniPlayerContentInternal(
    viewModel: PlayerSharedViewModel,
    currentSong: SongItem,
    isPlaying: Boolean,
    accentColor: Color,
    miniPlayerBackgroundColor: Color,
    coverDerivedColor: Color,
    showLegacyCompactArtwork: Boolean,
    expansionFraction: Animatable<Float, AnimationVector1D>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    val isDark = DsmusicTheme.colorScheme.background.luminance() < 0.5f
    val context = LocalContext.current
    val miniPlayerPreferences = remember(context) {
        context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
    }
    val controlShapeName by miniPlayerPreferences.observeKey("mini_player_control_shape", "Circle")
    val controlIconSet by miniPlayerPreferences.observeKey("mini_player_control_icons", "Classic")
    val artworkShapeName by miniPlayerPreferences.observeKey("player_artwork_shape", "Rounded")
    val artworkLayout by miniPlayerPreferences.observeKey("player_layout", "Standard")
    val embeddedCurrentArtwork by viewModel.currentSongArtwork.collectAsStateWithLifecycle()
    val miniArtworkShape = remember(artworkShapeName, artworkLayout) {
        DsmCoverShapes.forPlayerLayout(artworkLayout, artworkShapeName)
    }
    val savedCurrentArtwork = if (
        currentSong.isCachedTrack ||
        (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M &&
            currentSong.isDownloadedOffline)
    ) {
        embeddedCurrentArtwork?.takeIf { it.isNotEmpty() }
    } else {
        null
    }
    val miniArtworkModel: Any? = remember(
        currentSong.id, currentSong.thumbnailUrl, currentSong.videoId,
        currentSong.albumId, currentSong.path, currentSong.isCachedTrack,
        currentSong.isDownloadedOffline, savedCurrentArtwork
    ) {
        savedCurrentArtwork
            ?: currentSong.thumbnailUrl?.takeIf { it.isNotBlank() }
                ?.let { SongItem.enhanceThumbnailUrl(it) }
            ?: currentSong.videoId?.takeIf { it.isNotBlank() }
                ?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
            ?: if (
                currentSong.isDownloadedOffline &&
                android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M &&
                currentSong.albumId <= 0L
            ) null else currentSong.getAlbumArtUri()?.toString()
    }
    val miniArtworkRequest = remember(context, miniArtworkModel) {
        miniArtworkModel?.let { model ->
            ImageRequest.Builder(context)
                .data(model)
                .size(96, 96)
                .precision(Precision.INEXACT)
                .crossfade(false)
                .build()
        }
    }
    val controlShape: Shape = when (controlShapeName) {
        "Rounded" -> RoundedCornerShape(14.dp)
        "Pill" -> RoundedCornerShape(percent = 50)
        else -> CircleShape
    }
    // Light mode uses black icons on white buttons; dark mode uses white icons on black buttons.
    val playerButtonColor = if (isDark) Color.Black else Color.White
    val playerButtonContentColor = if (isDark) Color.White else Color.Black
    val miniControlBackgroundColor = playerButtonColor
    val controlBackground: Modifier = if (controlShapeName == "None") {
        Modifier
    } else {
        Modifier.background(miniControlBackgroundColor, controlShape)
    }
    val sideControlWidth = when (controlShapeName) {
        "Pill" -> 64.dp
        "Rounded" -> 52.dp
        else -> 48.dp
    }
    val centerControlWidth = when (controlShapeName) {
        "Pill" -> 68.dp
        "Rounded" -> 56.dp
        else -> 48.dp
    }
    // Scale every clickable action surface to two-thirds of its previous size.
    // Keep the icon glyphs and the 4 dp inter-button spacing unchanged.
    fun controlModifier(width: Dp): Modifier =
        Modifier.size(
            width = width * (2f / 3f),
            height = 48.dp * (2f / 3f)
        ).then(controlBackground)
    val compactLandscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val artworkSize = if (compactLandscape) 48.dp else 60.dp
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    // Keep the song title on the same color as the filled transport buttons.
    val contentColor = playerButtonContentColor
    val contentSecondary = contentColor.copy(alpha = 0.6f)
    Row(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
            .padding(
                start = 10.dp,
                end = 8.dp,
                top = if (compactLandscape) 4.dp else 10.dp,
                bottom = if (compactLandscape) 4.dp else 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The shared cover is positioned independently and stays fixed. Keep only
        // the row-leading space needed for a 10 dp visible cover-to-title gap.
        Spacer(modifier = Modifier.width(if (compactLandscape) 8.8.dp else 2.dp))

        // Keep the Android 6 mini cover at its actual display size. The full-size
        // artwork pager is mounted only once the player is meaningfully expanded.
        Box(
            modifier = Modifier.size(artworkSize),
            contentAlignment = Alignment.Center
        ) {
            if (showLegacyCompactArtwork) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = miniArtworkWidthScale(artworkShapeName)
                            scaleY = miniArtworkHeightScale(artworkShapeName)
                        }
                        .clip(miniArtworkShape)
                        .background(DsmusicTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_music_note),
                        contentDescription = null,
                        tint = DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.align(Alignment.Center).size(26.dp)
                    )
                    miniArtworkRequest?.let { request ->
                        AsyncImage(
                            model = request,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(miniArtworkShape)
                        )
                    }
                }
                MiniPlayerArtworkProgressOverlay(
                    viewModel = viewModel,
                    accentColor = accentColor,
                    backgroundColor = miniPlayerBackgroundColor,
                    coverDerivedColor = coverDerivedColor,
                    expansionFraction = expansionFraction,
                    centerOnArtworkEdge = true,
                    modifier = Modifier.size(48.dp).graphicsLayer {
                        scaleX = miniArtworkWidthScale(artworkShapeName)
                        scaleY = miniArtworkHeightScale(artworkShapeName)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.width(if (compactLandscape) 1.2.dp else 2.dp))

        // The weighted text slot must end before the transport controls. Fill the
        // allocated width so long titles are ellipsized instead of drawing beneath actions.
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Text(
                text = currentSong.title,
                style = DsmusicTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                ),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = currentSong.artist,
                style = DsmusicTheme.typography.bodySmall.copy(
                    color = contentSecondary
                ),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Keep a small separation between the ellipsized text and fixed-width controls.
        Spacer(modifier = Modifier.width(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DsmusicIconButton(
                onClick = { viewModel.playPreviousSong() },
                modifier = controlModifier(sideControlWidth)
            ) {
                if (controlIconSet == "Classic") {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_previous),
                        contentDescription = "Previous",
                        tint = playerButtonContentColor,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (controlIconSet == "Seek") {
                    MiniPlayerSeekGlyph(
                        isForward = false,
                        tint = playerButtonContentColor,
                        modifier = Modifier.size(26.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.ChevronLeft,
                        contentDescription = "Previous",
                        tint = playerButtonContentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            DsmusicIconButton(
                onClick = {
                    if (!isLoading) {
                        if (isPlaying) viewModel.pauseAudio() else viewModel.playAudio()
                    }
                },
                modifier = controlModifier(centerControlWidth)
            ) {
                if (isLoading) {
                    com.dsann.music.ui.theme.LoadingIndicator(
                        modifier = Modifier.size(20.dp),
                        color = playerButtonContentColor
                    )
                } else if (controlIconSet == "Classic") {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = "Play/Pause",
                        tint = playerButtonContentColor,
                        modifier = Modifier.size(20.dp)
                    )
                } else if (controlIconSet == "Seek") {
                    Box(
                        modifier = Modifier.size(26.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = playerButtonContentColor,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 1.8.dp.toPx()
                                )
                            )
                        }
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = playerButtonContentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = playerButtonContentColor,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }

            DsmusicIconButton(
                onClick = { viewModel.playNextSong() },
                modifier = controlModifier(sideControlWidth)
            ) {
                if (controlIconSet == "Classic") {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_next),
                        contentDescription = "Next",
                        tint = playerButtonContentColor,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (controlIconSet == "Seek") {
                    MiniPlayerSeekGlyph(
                        isForward = true,
                        tint = playerButtonContentColor,
                        modifier = Modifier.size(26.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Next",
                        tint = playerButtonContentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}


@Composable
private fun MiniPlayerSeekGlyph(
    isForward: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    // Double outlined chevrons distinguish fast-seek controls from Classic's
    // single skip triangle plus track bar. Keep this vector-drawn for API 23.
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        val halfHeight = size.height * 0.29f
        val halfWidth = size.width * 0.20f
        val strokeWidth = 2.4.dp.toPx()
        val cap = androidx.compose.ui.graphics.StrokeCap.Round
        val centers = listOf(size.width * 0.32f, size.width * 0.68f)

        centers.forEach { centerX ->
            val outerX = if (isForward) centerX - halfWidth else centerX + halfWidth
            val innerX = if (isForward) centerX + halfWidth else centerX - halfWidth
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(outerX, centerY - halfHeight),
                end = androidx.compose.ui.geometry.Offset(innerX, centerY),
                strokeWidth = strokeWidth,
                cap = cap
            )
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(innerX, centerY),
                end = androidx.compose.ui.geometry.Offset(outerX, centerY + halfHeight),
                strokeWidth = strokeWidth,
                cap = cap
            )
        }
    }
}


@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
private fun MiniPlayerArtworkProgressOverlay(
    viewModel: PlayerSharedViewModel,
    accentColor: Color,
    backgroundColor: Color,
    coverDerivedColor: Color,
    expansionFraction: Animatable<Float, AnimationVector1D>,
    centerOnArtworkEdge: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isMiniLayerVisible by remember(expansionFraction) {
        derivedStateOf { expansionFraction.value < 0.5f }
    }
    if (!isMiniLayerVisible) return

    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
    }
    val style by preferences.observeKey("mini_player_progress", "Ring")
    val scrubberColor by preferences.observeKey("scrubber_color", "Accent")
    val artworkShapeName by preferences.observeKey("player_artwork_shape", "Rounded")
    val artworkLayout by preferences.observeKey("player_layout", "Standard")
    val matchingCoverShape = remember(artworkShapeName, artworkLayout) {
        DsmCoverShapes.forPlayerLayout(artworkLayout, artworkShapeName)
    }
    if (style == "None") return

    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val audioService by viewModel.audioServiceState.collectAsStateWithLifecycle()
    val forceDisplayRefresh = android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M
    val frameTimeNanos = rememberAppAnimationFrameTimeNanos(
        active = isMiniLayerVisible && isPlaying,
        forceDisplayRefresh = forceDisplayRefresh
    )
    // The service snapshot may run at 20-60 FPS. Sample only the paused/fallback value
    // for composition; the visible progress arc reads Media3 directly in its Canvas draw.
    val sampledPositionFlow = remember(viewModel.currentPosition) {
        viewModel.currentPosition.sample(250L)
    }
    val observedPosition by sampledPositionFlow.collectAsStateWithLifecycle(
        initialValue = viewModel.currentPosition.value
    )
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val contrastProgressColor = bestProgressContrastColor(backgroundColor)
    val requestedProgressColor = when (scrubberColor) {
        "White" -> Color.White
        "Cover" -> coverDerivedColor
        else -> DsmusicTheme.colorScheme.primary
    }
    // Accent and White are explicit colour choices and must remain visible as
    // selected. For Cover only, retain the requested cover-derived hue when it has
    // enough contrast; otherwise use a contrasting neutral on the artwork-matched
    // mini-player surface so the progress indicator does not disappear.
    val coverProgressContrast = androidx.core.graphics.ColorUtils.calculateContrast(
        requestedProgressColor.toArgb(),
        backgroundColor.toArgb()
    )
    val visibleAccentColor = if (
        scrubberColor == "Cover" && coverProgressContrast < 3.0
    ) contrastProgressColor else requestedProgressColor
    val progressTrackColor = Color.Transparent

    // The frame timestamp is read inside Canvas so only the artwork-progress drawing
    // invalidates at display refresh, not the entire mini-player composition.
    androidx.compose.foundation.Canvas(
        modifier = modifier.graphicsLayer {
            alpha = (1f - expansionFraction.value * 2f).coerceIn(0f, 1f)
            // On the mini artwork only, expand the overlay by half a stroke on each side.
            // This centers the Matching cover line directly on the artwork boundary.
            val edgeScale = if (centerOnArtworkEdge && style == "Matching cover") 1.112f else 1f
            scaleX = edgeScale
            scaleY = edgeScale
        }
    ) {
        val side = size.minDimension
        if (side <= 0f) return@Canvas
        val frameNanos = frameTimeNanos.value
        // Sample Media3 directly on each delivered display frame. The ViewModel value is
        // used only when paused or when the playback service is not yet connected.
        val currentPosition = if (isPlaying && frameNanos > 0L) {
            audioService?.getCurrentPositionForLyrics() ?: observedPosition
        } else {
            observedPosition
        }
        val progress = if (duration > 0) {
            (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        when (style) {
            "Ring" -> {
                val strokeWidth = (side * 0.052f).coerceAtLeast(1f)
                val inset = strokeWidth / 2f + side * 0.012f
                val ringSize = (side - inset * 2f).coerceAtLeast(1f)
                val ringTopLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                val ringDimensions = androidx.compose.ui.geometry.Size(ringSize, ringSize)
                val ringStroke = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                drawArc(
                    color = progressTrackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = ringTopLeft,
                    size = ringDimensions,
                    style = ringStroke
                )
                if (progress > 0f) {
                    drawArc(
                        color = visibleAccentColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = ringTopLeft,
                        size = ringDimensions,
                        style = ringStroke
                    )
                }
            }
            "Line" -> {
                val startX = side * 0.08f
                val endX = size.width - startX
                val y = size.height * 0.91f
                val thickness = (side * 0.028f).coerceAtLeast(1f)
                drawLine(
                    color = progressTrackColor,
                    start = androidx.compose.ui.geometry.Offset(startX, y),
                    end = androidx.compose.ui.geometry.Offset(endX, y),
                    strokeWidth = thickness,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                if (progress > 0f) {
                    drawLine(
                        color = visibleAccentColor,
                        start = androidx.compose.ui.geometry.Offset(startX, y),
                        end = androidx.compose.ui.geometry.Offset(startX + (endX - startX) * progress, y),
                        strokeWidth = thickness,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
            "Matching cover" -> {
                // Trace the same outline used to clip the artwork. Inset the path by
                // half the stroke so the complete border remains visible inside its bounds.
                // Match the standard Line progress thickness. The mini-player's
                // overlay is expanded by half this width on both sides so the stroke
                // is split evenly outside and inside the cover's actual perimeter.
                val strokeWidth = (side * (if (centerOnArtworkEdge) 0.112f else 0.056f)).coerceAtLeast(1f)
                val inset = strokeWidth / 2f
                val outlineSize = Size(
                    (size.width - strokeWidth).coerceAtLeast(1f),
                    (size.height - strokeWidth).coerceAtLeast(1f)
                )
                val outline = matchingCoverShape.createOutline(
                    outlineSize,
                    layoutDirection,
                    this
                )
                val outlinePath = when (outline) {
                    is Outline.Rectangle -> androidx.compose.ui.graphics.Path().apply {
                        addRect(outline.rect)
                    }
                    is Outline.Rounded -> androidx.compose.ui.graphics.Path().apply {
                        addRoundRect(outline.roundRect)
                    }
                    is Outline.Generic -> outline.path
                }
                val coverBorderPath = androidx.compose.ui.graphics.Path().apply {
                    addPath(outlinePath, androidx.compose.ui.geometry.Offset(inset, inset))
                }
                val borderMeasure = android.graphics.PathMeasure(
                    coverBorderPath.asAndroidPath(),
                    false
                )
                val borderLength = borderMeasure.length
                if (borderLength > 0f) {
                    val borderStroke = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    drawPath(
                        path = coverBorderPath,
                        color = progressTrackColor,
                        style = borderStroke
                    )
                    if (progress > 0f) {
                        val completedPath = android.graphics.Path()
                        borderMeasure.getSegment(
                            0f,
                            (borderLength * progress).coerceIn(0f, borderLength),
                            completedPath,
                            true
                        )
                        drawPath(
                            path = completedPath.asComposePath(),
                            color = visibleAccentColor,
                            style = borderStroke
                        )
                    }
                }
            }
            "Fill" -> {
                val fillHeight = size.height * progress
                if (fillHeight > 0f) {
                    val top = size.height - fillHeight
                    val artworkOutline = matchingCoverShape.createOutline(
                        size,
                        layoutDirection,
                        this
                    )
                    val artworkClipPath = when (artworkOutline) {
                        is Outline.Rectangle -> androidx.compose.ui.graphics.Path().apply {
                            addRect(artworkOutline.rect)
                        }
                        is Outline.Rounded -> androidx.compose.ui.graphics.Path().apply {
                            addRoundRect(artworkOutline.roundRect)
                        }
                        is Outline.Generic -> artworkOutline.path
                    }

                    clipPath(artworkClipPath) {
                        drawRect(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    visibleAccentColor.copy(alpha = 0.36f),
                                    visibleAccentColor.copy(alpha = 0.78f)
                                ),
                                startY = top,
                                endY = size.height
                            ),
                            topLeft = androidx.compose.ui.geometry.Offset(0f, top),
                            size = androidx.compose.ui.geometry.Size(size.width, fillHeight)
                        )
                        drawLine(
                            color = visibleAccentColor,
                            start = androidx.compose.ui.geometry.Offset(0f, top),
                            end = androidx.compose.ui.geometry.Offset(size.width, top),
                            strokeWidth = (side * 0.018f).coerceAtLeast(1f)
                        )
                    }
                }
            }
        }
    }
}
