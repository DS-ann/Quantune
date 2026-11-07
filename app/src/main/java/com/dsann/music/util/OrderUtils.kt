package com.dsann.music.util

/**
 * Shared, deterministic list-order operations used by the player action editor and playback queue.
 */
object OrderUtils {
    /**
     * Returns a reordered copy. Invalid indices and no-op moves return the original list unchanged.
     */
    fun <T> move(items: List<T>, fromIndex: Int, toIndex: Int): List<T> {
        if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) {
            return items
        }
        return items.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
    }

    /**
     * Keeps a selected item's index pointing at the same item after a list move.
     */
    fun moveIndex(currentIndex: Int, fromIndex: Int, toIndex: Int): Int {
        if (fromIndex == toIndex || currentIndex < 0) return currentIndex
        return when {
            currentIndex == fromIndex -> toIndex
            fromIndex < toIndex && currentIndex in (fromIndex + 1)..toIndex -> currentIndex - 1
            fromIndex > toIndex && currentIndex in toIndex until fromIndex -> currentIndex + 1
            else -> currentIndex
        }
    }
}
