package com.dsann.music.model

/**
 * Actions assignable to the full-player cover and the mini player.
 * Kept deliberately small and serializable so gesture choices survive app restarts.
 */
enum class PlayerGestureAction(val label: String) {
    NEXT("Next song"),
    PREVIOUS("Previous song"),
    SEEK_BACK("Back 10 s"),
    SEEK_AHEAD("Ahead 10 s"),
    SEEK_SIDES("Seek by side"),
    PLAY_PAUSE("Play / pause"),
    LIKE("Like"),
    QUEUE("Up next"),
    LYRICS("Lyrics"),
    VIDEO("Video"),
    STOP("Stop"),
    OPEN("Open the player"),
    NONE("Nothing");

    companion object {
        fun choices(doubleTap: Boolean = false, mini: Boolean = false): List<PlayerGestureAction> =
            entries.filter { action ->
                when (action) {
                    SEEK_SIDES -> doubleTap
                    STOP, OPEN -> mini
                    LYRICS, VIDEO -> !mini
                    else -> true
                }
            }

        fun fromStored(value: String?, fallback: PlayerGestureAction): PlayerGestureAction =
            entries.firstOrNull { it.name == value } ?: fallback
    }
}

data class PlayerGestureSettings(
    val coverSwipeLeft: PlayerGestureAction = PlayerGestureAction.NEXT,
    val coverSwipeRight: PlayerGestureAction = PlayerGestureAction.PREVIOUS,
    val coverSwipeUp: PlayerGestureAction = PlayerGestureAction.QUEUE,
    val coverDoubleTap: PlayerGestureAction = PlayerGestureAction.SEEK_SIDES,
    val coverLongPress: PlayerGestureAction = PlayerGestureAction.NONE,
    val miniSwipeLeft: PlayerGestureAction = PlayerGestureAction.NEXT,
    val miniSwipeRight: PlayerGestureAction = PlayerGestureAction.PREVIOUS,
    val miniSwipeDown: PlayerGestureAction = PlayerGestureAction.STOP,
    val doubleTapSeekSeconds: Int = 10
) {
    companion object {
        const val KEY_PREFIX = "gesture_"

        fun fromPreferences(prefs: android.content.SharedPreferences): PlayerGestureSettings =
            PlayerGestureSettings(
                coverSwipeLeft = PlayerGestureAction.fromStored(prefs.getString("cover_swipe_left", null), PlayerGestureAction.NEXT),
                coverSwipeRight = PlayerGestureAction.fromStored(prefs.getString("cover_swipe_right", null), PlayerGestureAction.PREVIOUS),
                coverSwipeUp = PlayerGestureAction.fromStored(prefs.getString("cover_swipe_up", null), PlayerGestureAction.QUEUE),
                coverDoubleTap = PlayerGestureAction.fromStored(prefs.getString("cover_double_tap", null), PlayerGestureAction.SEEK_SIDES),
                coverLongPress = PlayerGestureAction.fromStored(prefs.getString("cover_long_press", null), PlayerGestureAction.NONE),
                miniSwipeLeft = PlayerGestureAction.fromStored(prefs.getString("mini_swipe_left", null), PlayerGestureAction.NEXT),
                miniSwipeRight = PlayerGestureAction.fromStored(prefs.getString("mini_swipe_right", null), PlayerGestureAction.PREVIOUS),
                miniSwipeDown = PlayerGestureAction.fromStored(prefs.getString("mini_swipe_down", null), PlayerGestureAction.STOP),
                doubleTapSeekSeconds = prefs.getInt("double_tap_seek_seconds", 10).coerceIn(3, 30)
            )
    }
}
