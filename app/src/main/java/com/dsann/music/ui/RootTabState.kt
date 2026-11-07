package com.dsann.music.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner

/** True only for the visible root tab; cached tabs disable decorative frame animations. */
internal val LocalRootTabActive = staticCompositionLocalOf { true }

private class CachedRootTabLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    init {
        registry.currentState = Lifecycle.State.CREATED
    }

    override val lifecycle: Lifecycle
        get() = registry

    fun setActive(active: Boolean) {
        registry.currentState = if (active) Lifecycle.State.STARTED else Lifecycle.State.CREATED
    }

    fun dispose() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

/**
 * Keeps an already visited root tab composed to preserve its complete screen state.
 * Lifecycle-aware flows and BackHandlers are suspended while the tab is inactive.
 */
@Composable
internal fun CachedRootTabLifecycle(
    active: Boolean,
    content: @Composable () -> Unit
) {
    val owner = remember { CachedRootTabLifecycleOwner() }

    SideEffect { owner.setActive(active) }
    DisposableEffect(owner) {
        onDispose { owner.dispose() }
    }

    CompositionLocalProvider(
        LocalLifecycleOwner provides owner,
        LocalRootTabActive provides active,
        content = content
    )
}
