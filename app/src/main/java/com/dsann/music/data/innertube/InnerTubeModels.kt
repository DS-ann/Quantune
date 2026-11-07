package com.dsann.music.data.innertube

/**
 * Data models for YouTube Music online content.
 * These are separate from the local SongItem to cleanly handle online vs offline songs.
 */

/** Represents an online song from YouTube Music */
data class OnlineSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val albumName: String? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val duration: String? = null,       // "3:45" formatted
    val durationMs: Long = 0,           // milliseconds
    val thumbnailUrl: String? = null,
    val isExplicit: Boolean = false,
    val isVideo: Boolean = false,
    val isEpisode: Boolean = false,
    val isLive: Boolean = false,
    val podcastId: String? = null,
    val artistRefs: List<OnlineArtistRef> = emptyList()
)

/** Represents an online album */
data class OnlineAlbum(
    val browseId: String,
    val playlistId: String? = null,
    val title: String,
    val artists: List<OnlineArtistRef>,
    val year: Int? = null,
    val thumbnailUrl: String? = null,
    val songCount: String? = null
)

/** Represents an online artist */
data class OnlineArtist(
    val browseId: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val subscriberCount: String? = null,
    val shuffleEndpoint: String? = null,
    val radioEndpoint: String? = null,
    val isSubscribed: Boolean = false
)

/** Lightweight artist reference (used in songs/albums) */
data class OnlineArtistRef(
    val name: String,
    val id: String? = null
) : java.io.Serializable

/** Represents an online playlist */
data class OnlinePlaylist(
    val playlistId: String,
    val title: String,
    val author: OnlineArtistRef? = null,
    val songCount: String? = null,
    val thumbnailUrl: String? = null,
    val isPodcast: Boolean = false
)

/** Union type for search results */
sealed class SearchItem {
    data class Song(val song: OnlineSong) : SearchItem()
    data class Album(val album: OnlineAlbum) : SearchItem()
    data class Artist(val artist: OnlineArtist) : SearchItem()
    data class Playlist(val playlist: OnlinePlaylist) : SearchItem()
}

/** Search result container */
data class SearchResultSection(
    val filter: SearchFilter,
    val items: List<SearchItem>
)

/** The combined result set shown by the Top results view. */
data class SearchResult(
    val items: List<SearchItem>,
    val continuation: String? = null,
    val topResult: SearchItem? = null,
    val sections: List<SearchResultSection> = emptyList()
)

data class SearchSuggestions(
    val queries: List<String>,
    val items: List<SearchItem>
)

/** Stream URL data extracted from player response. bitrate is always in bits per second. */
data class StreamData(
    val url: String,
    val mimeType: String,
    val bitrate: Int,
    val contentLength: Long?,
    val audioQuality: String?
)

/** Player response containing streaming data */
data class PlayerResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val streams: List<StreamData>,
    val playbackUrl: String? = null,
    val watchtimeUrl: String? = null,
    val likesCount: String? = null
)

/** Home feed section (carousel) */
data class HomeSection(
    val title: String,
    val items: List<SearchItem>,
    val browseEndpoint: String? = null,
    val params: String? = null
)

/** A YouTube Music Explore chip with its original browse destination. */
data class HomeCategory(
    val title: String,
    val browseId: String,
    val params: String? = null
)


/**
 * Decodes the persisted genre catalogue without relying on Gson's reflective generic metadata.
 * Both the current {"categories":[...]} wrapper and a legacy raw-array cache are accepted.
 */
object HomeCategoryCacheParser {
    fun parse(json: String): List<HomeCategory> {
        val root = com.google.gson.JsonParser().parse(json)
        val categoryArray = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> root.asJsonObject.get("categories")
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
            else -> null
        } ?: return emptyList()

        return categoryArray.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val category = element.asJsonObject

            fun stringValue(name: String): String? =
                category.get(name)
                    ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
                    ?.asString
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }

            val title = stringValue("title") ?: return@mapNotNull null
            val browseId = stringValue("browseId") ?: return@mapNotNull null
            HomeCategory(title, browseId, stringValue("params"))
        }.distinctBy { it.browseId + "|" + it.params.orEmpty() }
    }
}

/** Home page data */
data class HomePage(
    val sections: List<HomeSection>,
    val continuation: String? = null,
    val moods: List<String> = emptyList(),
    val categories: List<HomeCategory> = emptyList()
)

/** Album page data */
data class AlbumPage(
    val album: OnlineAlbum,
    val songs: List<OnlineSong>,
    val otherVersions: List<OnlineAlbum> = emptyList()
)

/** Artist page data */
data class ArtistPage(
    val artist: OnlineArtist,
    val sections: List<HomeSection>,
    val description: String? = null
)

/** Playlist page data */
data class PlaylistPage(
    val playlist: OnlinePlaylist,
    val songs: List<OnlineSong>,
    val continuation: String? = null,
    val description: String? = null,
    val isOwned: Boolean = false,
    val privacy: String? = null
)

/** Search filter types */
enum class SearchFilter(val label: String, val value: String?) {
    SONGS("Songs", "EgWKAQIIAWoMEA4QChADEAQQCRAF"),
    VIDEOS("Videos", "EgWKAQIQAWoMEA4QChADEAQQCRAF"),
    ALBUMS("Albums", "EgWKAQIYAWoMEA4QChADEAQQCRAF"),
    ARTISTS("Artists", "EgWKAQIgAWoMEA4QChADEAQQCRAF"),
    FEATURED_PLAYLISTS("Featured playlists", "EgeKAQQoADgBagwQDhAKEAMQBBAJEAU="),
    PLAYLISTS("Community playlists", "EgeKAQQoAEABagwQDhAKEAMQBBAJEAU="),
    PODCASTS("Podcasts", "EgWKAQJQAWoMEA4QChADEAQQCRAF"),
    EPISODES("Episodes", "EgWKAQJIAWoMEA4QChADEAQQCRAF"),
    LIVE("Live", null)
}

/** Represents a user profile */
data class UserProfile(
    val name: String,
    val handle: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null
)


