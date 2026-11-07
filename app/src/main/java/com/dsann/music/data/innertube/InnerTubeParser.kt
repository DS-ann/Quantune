package com.dsann.music.data.innertube

import kotlin.math.abs
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Parses InnerTube API JSON responses into our clean data models.
 * Handles the deeply nested, inconsistent response structures from YouTube Music.
 */
object InnerTubeParser {

    private const val TAG = "InnerTubeParser"

    // ========== Search & Explore ==========

    fun parseSectionPage(response: JsonObject): HomeSection? {
        val header = response.path(
            "contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicResponsiveHeaderRenderer"
        )?.asJsonObject
            ?: response.path(
                "contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicEditablePlaylistDetailHeaderRenderer.header.musicResponsiveHeaderRenderer"
            )?.asJsonObject
            ?: response.path(
                "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicResponsiveHeaderRenderer"
            )?.asJsonObject
            ?: response.path(
                "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicEditablePlaylistDetailHeaderRenderer.header.musicResponsiveHeaderRenderer"
            )?.asJsonObject

        var title = header?.path("title.runs.0.text")?.asString
            ?: header?.path("title.simpleText")?.asString
            ?: "Section"

        // Parse items
        var itemContents = response.path(
            "contents.twoColumnBrowseResultsRenderer.secondaryContents.sectionListRenderer.contents.0.musicPlaylistShelfRenderer.contents"
        )?.asJsonArray
        if (itemContents == null) {
            itemContents = response.path(
                "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicPlaylistShelfRenderer.contents"
            )?.asJsonArray
        }
        if (itemContents == null) {
            itemContents = response.path(
                "contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicShelfRenderer.contents"
            )?.asJsonArray
        }
        if (itemContents == null) {
            itemContents = response.path(
                "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicShelfRenderer.contents"
            )?.asJsonArray
        }
        if (itemContents == null) {
            itemContents = response.path(
                "contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.gridRenderer.items"
            )?.asJsonArray
        }
        if (itemContents == null) {
            itemContents = response.path(
                "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.gridRenderer.items"
            )?.asJsonArray
        }

        val items = mutableListOf<SearchItem>()
        itemContents?.forEach { content ->
            val obj = content.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val twoRowRenderer = obj.getAsJsonObject("musicTwoRowItemRenderer")
            val listRenderer = obj.getAsJsonObject("musicResponsiveListItemRenderer")
            
            if (twoRowRenderer != null) {
                parseTwoRowItem(twoRowRenderer)?.let { items.add(it) }
            } else if (listRenderer != null) {
                parseSearchItem(obj)?.let { items.add(it) }
            }
        }

        // Genre/mood browse pages can contain several shelves and do not always use
        // the single first-shelf layout handled above. Fall back to the same general
        // shelf parser used by Home/Explore, then combine unique browseable results.
        if (items.isEmpty()) {
            val fallbackShelves = parseHomePage(response).sections
            fallbackShelves.flatMap { it.items }.forEach { item ->
                if (items.none { searchItemKey(it) == searchItemKey(item) }) {
                    items.add(item)
                }
            }
            if (title == "Section") {
                title = fallbackShelves.firstOrNull()?.title?.takeIf { it.isNotBlank() } ?: title
            }
        }

        if (items.isEmpty() && header == null) return null

        return HomeSection(
            title = title,
            items = items
        )
    }

    fun parseSearchSuggestions(json: JsonObject): SearchSuggestions {
        val queries = findObjects(json, "searchSuggestionRenderer").mapNotNull { renderer ->
            (renderer.path("suggestion.runs")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.joinToString("") { run -> run.asJsonObject.get("text")?.asString.orEmpty() }
                ?: renderer.path("suggestion.simpleText")?.asString)
                ?.takeIf { it.isNotBlank() }
        }.distinct()

        val items = findObjects(json, "musicResponsiveListItemRenderer").mapNotNull { renderer ->
            parseSearchItem(JsonObject().apply { add("musicResponsiveListItemRenderer", renderer) })
        }.distinctBy { searchItemKey(it) }.take(4)

        return SearchSuggestions(queries, items)
    }

    fun parseLiveSearchResponse(json: JsonObject): SearchResult {
        val songs = findObjects(json, "videoRenderer").mapNotNull { renderer ->
            runCatching {
                val videoId = renderer.get("videoId")?.asString ?: return@runCatching null
                if (renderer.get("upcomingEventData") != null) return@runCatching null

                val badges = renderer.getAsJsonArray("badges")?.mapNotNull { badge ->
                    badge.asJsonObject.path("metadataBadgeRenderer.style")?.asString
                }.orEmpty()
                val overlays = renderer.getAsJsonArray("thumbnailOverlays")?.mapNotNull { overlay ->
                    overlay.asJsonObject.path("thumbnailOverlayTimeStatusRenderer.style")?.asString
                }.orEmpty()
                val watching = renderer.path("viewCountText.simpleText")?.asString
                    ?.contains("watching", ignoreCase = true) == true
                val isLive = badges.any { it.contains("LIVE_NOW") } || "LIVE" in overlays ||
                    (renderer.get("lengthText") == null && watching)
                if (!isLive) return@runCatching null

                val title = renderer.path("title.runs.0.text")?.asString
                    ?: renderer.path("title.simpleText")?.asString
                    ?: return@runCatching null
                val artist = renderer.path("ownerText.runs.0.text")?.asString
                    ?: renderer.path("longBylineText.runs.0.text")?.asString
                    ?: renderer.path("shortBylineText.runs.0.text")?.asString
                    ?: "Live"
                val thumbnail = getHighResThumbnailUrl(
                    renderer.path("thumbnail.thumbnails")?.asJsonArray?.lastOrNull()
                        ?.asJsonObject?.get("url")?.asString
                ) ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                OnlineSong(
                    videoId = videoId,
                    title = title,
                    artist = artist,
                    thumbnailUrl = thumbnail,
                    isVideo = true,
                    isLive = true
                )
            }.getOrNull()
        }.distinctBy { it.videoId }

        val continuation = findObjects(json, "continuationItemRenderer").firstOrNull()
            ?.path("continuationEndpoint.continuationCommand.token")?.asString
        return SearchResult(songs.map { SearchItem.Song(it) }, continuation)
    }

    fun parsePodcastEpisodes(json: JsonObject): List<OnlineSong> {
        return findObjects(json, "musicMultiRowListItemRenderer").mapNotNull { renderer ->
            runCatching {
                val watch = renderer.path("onTap.watchEndpoint")?.asJsonObject
                    ?: renderer.path("overlay.musicItemThumbnailOverlayRenderer.content.musicPlayButtonRenderer.playNavigationEndpoint.watchEndpoint")?.asJsonObject
                    ?: return@runCatching null
                val videoId = watch.get("videoId")?.asString ?: return@runCatching null
                val title = renderer.path("title.runs.0.text")?.asString
                    ?: renderer.path("title.simpleText")?.asString
                    ?: return@runCatching null
                val secondRuns = renderer.path("secondTitle.runs")?.asJsonArray
                val showRun = secondRuns?.firstOrNull { run ->
                    val browse = run.asJsonObject.path("navigationEndpoint.browseEndpoint")
                    browse?.path("browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType")?.asString ==
                        "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE"
                }
                val showName = showRun?.asJsonObject?.get("text")?.asString
                    ?: renderer.path("secondTitle.simpleText")?.asString
                    ?: "Podcast"
                val podcastId = showRun?.asJsonObject
                    ?.path("navigationEndpoint.browseEndpoint.browseId")?.asString
                    ?: renderer.path("onTap.watchEndpoint.playlistId")?.asString
                val thumbnailUrl = getHighResThumbnailUrl(
                    renderer.path("thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")?.asJsonArray
                        ?.lastOrNull()?.asJsonObject?.get("url")?.asString
                )
                val subtitleText = renderer.path("subtitle.runs")?.asJsonArray
                    ?.joinToString("") { it.asJsonObject.get("text")?.asString.orEmpty() }.orEmpty()
                val duration = Regex("(?:\\d+\\s*hr\\s*)?\\d+\\s*min|(?:\\d+:)?\\d+:\\d+")
                    .find(subtitleText)?.value

                OnlineSong(
                    videoId = videoId,
                    title = title,
                    artist = showName,
                    duration = duration,
                    durationMs = parseDuration(duration),
                    thumbnailUrl = thumbnailUrl,
                    isEpisode = true,
                    podcastId = podcastId
                )
            }.getOrNull()
        }.distinctBy { it.videoId }
    }

    private fun searchItemKey(item: SearchItem): String = when (item) {
        is SearchItem.Song -> "song:" + item.song.videoId
        is SearchItem.Album -> "album:" + item.album.browseId
        is SearchItem.Artist -> "artist:" + item.artist.browseId
        is SearchItem.Playlist -> "playlist:" + item.playlist.playlistId
    }

    fun findFirstObject(root: JsonElement?, key: String): JsonObject? =
        findObjects(root, key).firstOrNull()

    private fun findObjects(root: JsonElement?, key: String): List<JsonObject> {
        val found = ArrayList<JsonObject>()
        fun visit(node: JsonElement?) {
            when {
                node == null || node.isJsonNull -> Unit
                node.isJsonObject -> {
                    val obj = node.asJsonObject
                    val target = obj.get(key)
                    if (target != null && target.isJsonObject) found.add(target.asJsonObject)
                    obj.entrySet().forEach { visit(it.value) }
                }
                node.isJsonArray -> node.asJsonArray.forEach { visit(it) }
            }
        }
        visit(root)
        return found
    }

    fun parseSearchResponse(json: JsonObject): SearchResult {
        val items = mutableListOf<SearchItem>()
        var continuation: String? = null

        try {
            // Initial search response
            val tabs = json.path("contents.tabbedSearchResultsRenderer.tabs")?.asJsonArray
            val tab = tabs?.firstOrNull()?.asJsonObject
            var contents = tab?.path("tabRenderer.content.sectionListRenderer.contents")?.asJsonArray
            if (contents == null) {
                contents = json.path("contents.sectionListRenderer.contents")?.asJsonArray
            }

            contents?.forEach { section ->
                val obj = section.asJsonObject
                val shelf = obj.getAsJsonObject("musicShelfRenderer")
                    ?: obj.getAsJsonObject("musicCardShelfRenderer")
                    ?: obj.getAsJsonObject("itemSectionRenderer")
                    ?: obj.getAsJsonObject("gridRenderer")

                if (shelf != null) {
                    val shelfContents = shelf.getAsJsonArray("contents") ?: shelf.getAsJsonArray("items")
                    shelfContents?.forEach { item ->
                        val itemObj = item.asJsonObject
                        var parsed = parseSearchItem(itemObj)
                        if (parsed == null) {
                            val twoRowRenderer = itemObj.getAsJsonObject("musicTwoRowItemRenderer")
                            if (twoRowRenderer != null) {
                                parsed = parseTwoRowItem(twoRowRenderer)
                            }
                        }
                        parsed?.let { items.add(it) }
                    }
                    if (continuation == null) {
                        continuation = shelf.path("continuations.0.nextContinuationData.continuation")?.asString
                    }
                }
            }

            // Continuation response
            if (items.isEmpty()) {
                var contContents = json.path("continuationContents.musicShelfContinuation.contents")?.asJsonArray
                if (contContents == null) {
                    contContents = json.path("continuationContents.itemSectionContinuation.contents")?.asJsonArray
                }
                contContents?.forEach { item ->
                    val itemObj = item.asJsonObject
                    var parsed = parseSearchItem(itemObj)
                    if (parsed == null) {
                        val twoRowRenderer = itemObj.getAsJsonObject("musicTwoRowItemRenderer")
                        if (twoRowRenderer != null) {
                            parsed = parseTwoRowItem(twoRowRenderer)
                        }
                    }
                    parsed?.let { items.add(it) }
                }
                if (continuation == null) {
                    continuation = json.path("continuationContents.musicShelfContinuation.continuations.0.nextContinuationData.continuation")?.asString
                        ?: json.path("continuationContents.itemSectionContinuation.continuations.0.nextContinuationData.continuation")?.asString
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing search response", e)
        }

        return SearchResult(items, continuation)
    }

    /**
     * Parse YouTube Music's own unfiltered "Top result" card. This card often understands
     * abbreviations and misspellings that are absent from the typed category shelves.
     */
    fun parseTopSearchCard(json: JsonObject): SearchItem? {
        val card = findObjects(json, "musicCardShelfRenderer").firstOrNull() ?: return null
        return runCatching {
            val titleRuns = card.path("title.runs")?.asJsonArray ?: return@runCatching null
            val titleRun = titleRuns.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@runCatching null
            val title = card.path("title.runs.0.text")?.asString
                ?: card.path("title.simpleText")?.asString
                ?: return@runCatching null
            if (title.isBlank()) return@runCatching null

            val subtitleRuns = card.path("subtitle.runs")?.asJsonArray ?: com.google.gson.JsonArray()
            val detailTexts = subtitleRuns.mapNotNull { run ->
                run.takeIf { it.isJsonObject }?.asJsonObject?.get("text")?.asString
            }
            val subtitle = detailTexts.joinToString("")
            val thumbnailUrl = getHighResThumbnailUrl(
                card.path("thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
                    ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
            )
            val navigation = titleRun.getAsJsonObject("navigationEndpoint")
                ?: card.getAsJsonObject("onTap")
                ?: return@runCatching null
            val watch = navigation.getAsJsonObject("watchEndpoint")
            if (watch != null) {
                val videoId = watch.get("videoId")?.asString ?: return@runCatching null
                val videoType = watch.path(
                    "watchEndpointMusicSupportedConfigs.watchEndpointMusicConfig.musicVideoType"
                )?.asString
                if (videoType == "MUSIC_VIDEO_TYPE_PODCAST_EPISODE") return@runCatching null

                val artistRun = subtitleRuns.firstOrNull { run ->
                    val runObject = run.takeIf { it.isJsonObject }?.asJsonObject ?: return@firstOrNull false
                    val pageType = runObject.path(
                        "navigationEndpoint.browseEndpoint.browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType"
                    )?.asString
                    pageType == "MUSIC_PAGE_TYPE_ARTIST" || pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL"
                }?.asJsonObject
                val albumRun = subtitleRuns.firstOrNull { run ->
                    run.takeIf { it.isJsonObject }?.asJsonObject?.path(
                        "navigationEndpoint.browseEndpoint.browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType"
                    )?.asString == "MUSIC_PAGE_TYPE_ALBUM"
                }?.asJsonObject
                val artist = artistRun?.get("text")?.asString
                    ?: detailTexts.firstOrNull {
                        it.isNotBlank() && !it.trim().matches(Regex("(\\d+:)?\\d+:\\d+"))
                    }?.takeUnless {
                        it.equals("Song", true) || it.equals("Video", true) || it.equals("Single", true)
                    }
                    ?: getCleanArtist(subtitle)

                SearchItem.Song(
                    OnlineSong(
                        videoId = videoId,
                        title = title,
                        artist = artist,
                        artistId = artistRun?.path("navigationEndpoint.browseEndpoint.browseId")?.asString,
                        albumName = albumRun?.get("text")?.asString,
                        albumId = albumRun?.path("navigationEndpoint.browseEndpoint.browseId")?.asString,
                        duration = detailTexts.lastOrNull { it.trim().matches(Regex("(\\d+:)?\\d+:\\d+")) },
                        durationMs = parseDuration(detailTexts.lastOrNull { it.trim().matches(Regex("(\\d+:)?\\d+:\\d+")) }),
                        thumbnailUrl = thumbnailUrl,
                        isVideo = !videoType.isNullOrBlank() && videoType != "MUSIC_VIDEO_TYPE_ATV"
                    )
                )
            } else {
                val browse = navigation.getAsJsonObject("browseEndpoint") ?: return@runCatching null
                val browseId = browse.get("browseId")?.asString ?: return@runCatching null
                val pageType = browse.path(
                    "browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType"
                )?.asString
                val parts = subtitle.split(" • ").map { it.trim() }
                when (pageType) {
                    "MUSIC_PAGE_TYPE_ALBUM" -> SearchItem.Album(
                        OnlineAlbum(
                            browseId = browseId,
                            title = title,
                            artists = listOfNotNull(parts.drop(1).firstOrNull()?.takeIf { it.isNotBlank() }?.let(::OnlineArtistRef)),
                            year = parts.lastOrNull()?.toIntOrNull(),
                            thumbnailUrl = thumbnailUrl
                        )
                    )
                    "MUSIC_PAGE_TYPE_ARTIST", "MUSIC_PAGE_TYPE_USER_CHANNEL" -> SearchItem.Artist(
                        OnlineArtist(
                            browseId = browseId,
                            title = title,
                            thumbnailUrl = thumbnailUrl,
                            subscriberCount = subtitle.takeIf { it.isNotBlank() }
                        )
                    )
                    "MUSIC_PAGE_TYPE_PLAYLIST" -> SearchItem.Playlist(
                        OnlinePlaylist(
                            playlistId = browseId.removePrefix("VL"),
                            title = title,
                            thumbnailUrl = thumbnailUrl,
                            songCount = parts.lastOrNull()
                        )
                    )
                    else -> when {
                        browseId.startsWith("UC") -> SearchItem.Artist(
                            OnlineArtist(browseId, title, thumbnailUrl, subscriberCount = subtitle)
                        )
                        browseId.startsWith("VL") || browseId.startsWith("PL") -> SearchItem.Playlist(
                            OnlinePlaylist(browseId.removePrefix("VL"), title, thumbnailUrl = thumbnailUrl, songCount = parts.lastOrNull())
                        )
                        browseId.startsWith("MPREb") -> SearchItem.Album(
                            OnlineAlbum(
                                browseId = browseId,
                                title = title,
                                artists = listOfNotNull(parts.drop(1).firstOrNull()?.takeIf { it.isNotBlank() }?.let(::OnlineArtistRef)),
                                year = parts.lastOrNull()?.toIntOrNull(),
                                thumbnailUrl = thumbnailUrl
                            )
                        )
                        else -> null
                    }
                }
            }
        }.getOrNull()
    }

    private fun parseSearchItem(item: JsonObject): SearchItem? {
        val renderer = item.getAsJsonObject("musicResponsiveListItemRenderer") ?: return null

        return try {
            val flexColumns = renderer.getAsJsonArray("flexColumns")
            val title = flexColumns?.getOrNull(0)?.asJsonObject
                ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs.0.text")?.asString ?: return null

            // Determine type from overlay/navigation
            val overlay = renderer.path("overlay.musicItemThumbnailOverlayRenderer.content.musicPlayButtonRenderer.playNavigationEndpoint")?.asJsonObject

            val watchEndpoint = overlay?.getAsJsonObject("watchEndpoint")
                ?: renderer.path("navigationEndpoint.watchEndpoint")?.asJsonObject
                ?: flexColumns.getOrNull(0)?.asJsonObject
                    ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs.0.navigationEndpoint.watchEndpoint")?.asJsonObject
            val browseEndpoint = renderer.path("navigationEndpoint.browseEndpoint")?.asJsonObject
                ?: flexColumns.getOrNull(0)?.asJsonObject
                    ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs.0.navigationEndpoint.browseEndpoint")?.asJsonObject

            when {
                // Song (has watchEndpoint)
                watchEndpoint != null -> {
                    val videoId = watchEndpoint.get("videoId")?.asString ?: return null
                    val subtitleRuns = flexColumns.getOrNull(1)?.asJsonObject
                        ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs")?.asJsonArray
                    
                    val parsedArtistRefs = parseArtistRefs(subtitleRuns)
                    var parsedArtist = ""
                    var parsedArtistId: String? = null
                    var parsedAlbumName: String? = null
                    var parsedAlbumId: String? = null
                    var durationText = ""
                    
                    subtitleRuns?.forEach { run ->
                        val runObj = run.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                        val text = runObj.get("text")?.asString ?: ""
                        val endpoint = runObj.path("navigationEndpoint.browseEndpoint")?.asJsonObject
                        
                        if (endpoint != null) {
                            val bId = endpoint.get("browseId")?.asString
                            val pageType = endpoint.path("browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType")?.asString
                            
                            if (pageType == "MUSIC_PAGE_TYPE_ARTIST" || pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL") {
                                if (parsedArtistId == null) {
                                    parsedArtistId = bId
                                    parsedArtist = text
                                }
                            } else if (pageType == "MUSIC_PAGE_TYPE_ALBUM") {
                                parsedAlbumId = bId
                                parsedAlbumName = text
                            }
                        } else {
                            if (text.trim().matches(Regex("\\d+:\\d+"))) {
                                durationText = text.trim()
                            }
                        }
                    }
                    
                    if (parsedArtist.isBlank()) {
                        val subtitleText = subtitleRuns?.joinToString("") { run ->
                            run.takeIf { run.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
                        } ?: ""
                        parsedArtist = getCleanArtist(subtitleText)
                    }
                    if (durationText.isBlank()) {
                        val fallback = subtitleRuns?.joinToString("") { run ->
                            run.takeIf { run.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
                        }?.split(" • ")?.lastOrNull() ?: ""
                        if (fallback.trim().matches(Regex("(\\d+:)?\\d+:\\d+"))) {
                            durationText = fallback.trim()
                        }
                    }
                    
                    val thumbnail = parseThumbnail(renderer)
                    val musicVideoType = watchEndpoint.path(
                        "watchEndpointMusicSupportedConfigs.watchEndpointMusicConfig.musicVideoType"
                    )?.asString
                    val podcastRun = subtitleRuns?.firstOrNull { run ->
                        val pageType = run.asJsonObject.path(
                            "navigationEndpoint.browseEndpoint.browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType"
                        )?.asString
                        pageType == "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE"
                    }
                    val podcastId = podcastRun?.asJsonObject
                        ?.path("navigationEndpoint.browseEndpoint.browseId")?.asString

                    SearchItem.Song(OnlineSong(
                        videoId = videoId,
                        title = title,
                        artist = parsedArtistRefs.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name }
                            ?: parsedArtist.trim(),
                        artistId = parsedArtistRefs.firstOrNull()?.id ?: parsedArtistId,
                        artistRefs = parsedArtistRefs,
                        albumName = parsedAlbumName,
                        albumId = parsedAlbumId,
                        duration = durationText.trim().takeIf { it.isNotEmpty() },
                        durationMs = parseDuration(durationText.trim()),
                        thumbnailUrl = thumbnail,
                        isVideo = !musicVideoType.isNullOrBlank() && musicVideoType != "MUSIC_VIDEO_TYPE_ATV",
                        isEpisode = musicVideoType == "MUSIC_VIDEO_TYPE_PODCAST_EPISODE",
                        podcastId = podcastId
                    ))
                }
                // Album or Artist (has browseEndpoint)
                browseEndpoint != null -> {
                    val browseId = browseEndpoint.get("browseId")?.asString ?: return null
                    val pageType = browseEndpoint.path("browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType")?.asString

                    val subtitleRuns = flexColumns.getOrNull(1)?.asJsonObject
                        ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs")?.asJsonArray
                    val subtitle = subtitleRuns?.joinToString("") { run ->
                        run.takeIf { run.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
                    } ?: ""
                    val thumbnail = parseThumbnail(renderer)

                    when (pageType) {
                        "MUSIC_PAGE_TYPE_ALBUM" -> {
                            val artistName = subtitle.split(" • ").drop(1).firstOrNull() ?: ""
                            SearchItem.Album(OnlineAlbum(
                                browseId = browseId,
                                title = title,
                                artists = listOf(OnlineArtistRef(artistName)),
                                thumbnailUrl = thumbnail,
                                year = subtitle.split(" • ").lastOrNull()?.toIntOrNull()
                            ))
                        }
                        "MUSIC_PAGE_TYPE_ARTIST" -> {
                            SearchItem.Artist(OnlineArtist(
                                browseId = browseId,
                                title = title,
                                thumbnailUrl = thumbnail,
                                subscriberCount = subtitle.takeIf { it.isNotBlank() }
                            ))
                        }
                        "MUSIC_PAGE_TYPE_PLAYLIST" -> {
                            SearchItem.Playlist(OnlinePlaylist(
                                playlistId = browseId.removePrefix("VL"),
                                title = title,
                                thumbnailUrl = thumbnail,
                                songCount = subtitle.split(" • ").lastOrNull()
                            ))
                        }
                        "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE" -> {
                            SearchItem.Playlist(OnlinePlaylist(
                                playlistId = browseId,
                                title = title,
                                author = OnlineArtistRef(subtitle.split(" • ").drop(1).firstOrNull().orEmpty()),
                                thumbnailUrl = thumbnail,
                                isPodcast = true
                            ))
                        }
                        else -> {
                            // Try to infer from browseId prefix
                            when {
                                browseId.startsWith("UC") -> {
                                    SearchItem.Artist(OnlineArtist(
                                        browseId = browseId,
                                        title = title,
                                        thumbnailUrl = thumbnail,
                                        subscriberCount = subtitle
                                    ))
                                }
                                browseId.startsWith("VL") || browseId.startsWith("PL") -> {
                                    SearchItem.Playlist(OnlinePlaylist(
                                        playlistId = browseId.removePrefix("VL"),
                                        title = title,
                                        thumbnailUrl = thumbnail,
                                        songCount = subtitle.split(" • ").lastOrNull()
                                    ))
                                }
                                browseId.startsWith("MPREb") -> {
                                    val artistName = subtitle.split(" • ").drop(1).firstOrNull() ?: ""
                                    SearchItem.Album(OnlineAlbum(
                                        browseId = browseId,
                                        title = title,
                                        artists = listOf(OnlineArtistRef(artistName)),
                                        thumbnailUrl = thumbnail,
                                        year = subtitle.split(" • ").lastOrNull()?.toIntOrNull()
                                    ))
                                }
                                browseId.startsWith("MPSP") -> {
                                    SearchItem.Playlist(OnlinePlaylist(
                                        playlistId = browseId,
                                        title = title,
                                        author = OnlineArtistRef(subtitle.split(" • ").drop(1).firstOrNull().orEmpty()),
                                        thumbnailUrl = thumbnail,
                                        isPodcast = true
                                    ))
                                }
                                else -> null
                            }
                        }
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse search item: ${e.message}")
            null
        }
    }

    // ========== Player Parsing ==========

    fun parsePlayerResponse(
        json: JsonObject,
        preferredAudioFormatOverride: String? = null,
        targetBitrateKbpsOverride: Int? = null
    ): PlayerResult? {
        try {
            val playabilityStatus = json.getAsJsonObject("playabilityStatus")
            val status = playabilityStatus?.get("status")?.asString
            if (status != "OK") {
                Log.w(TAG, "Player status: $status — ${playabilityStatus?.get("reason")?.asString}")
                return null
            }

            val videoDetails = json.getAsJsonObject("videoDetails") ?: return null
            val streamingData = json.getAsJsonObject("streamingData") ?: return null

            val videoId = videoDetails.get("videoId")?.asString ?: return null
            val title = videoDetails.get("title")?.asString ?: "Unknown"
            val author = videoDetails.get("author")?.asString ?: "Unknown Artist"
            val durationMs = (videoDetails.get("lengthSeconds")?.asLong ?: 0) * 1000
            val thumbnail = videoDetails.path("thumbnail.thumbnails")?.asJsonArray
                ?.lastOrNull()?.asJsonObject?.get("url")?.asString

            val allFormats = mutableListOf<JsonElement>()
            streamingData.getAsJsonArray("adaptiveFormats")?.let { allFormats.addAll(it) }
            streamingData.getAsJsonArray("formats")?.let { allFormats.addAll(it) }

            val availableAudioStreams = mutableListOf<StreamData>()
            val targetBitrateBps = targetBitrateKbpsOverride
                ?.coerceAtLeast(1)
                ?.times(1000)
                ?: NewPipeStreamExtractor.getTargetBitrateBps()
            val preferredAudioFormat = preferredAudioFormatOverride
                ?.let(NewPipeStreamExtractor::normalizeAudioFormat)
                ?: NewPipeStreamExtractor.getPlaybackAudioFormat()

            allFormats.forEach { format ->
                val obj = format.asJsonObject
                val mimeType = obj.get("mimeType")?.asString ?: ""
                
                // Optimization step: Instant disqualification of non-audio payloads saves huge computation cycles
                if (!mimeType.startsWith("audio/")) {
                    return@forEach
                }

                var url = obj.get("url")?.asString
                val cipher = obj.get("signatureCipher")?.asString ?: obj.get("cipher")?.asString
                val bitrate = obj.get("bitrate")?.asInt ?: 0

                // Logic step: Decipher the raw signature if direct URL is missing
                if (url == null && cipher != null) {
                    try {
                        val params = parseQueryString(cipher)
                        val streamUrl = params["url"]
                        val signature = params["s"]
                        val sp = params["sp"] ?: "sig"
                        if (streamUrl != null && signature != null) {
                            val deobfuscatedSig = org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, signature)
                            val encodedSig = java.net.URLEncoder.encode(deobfuscatedSig, "UTF-8")
                            val separator = if (streamUrl.contains("?")) "&" else "?"
                            url = "$streamUrl$separator$sp=$encodedSig"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Signature decryption failed for $videoId", e)
                    }
                }

                // Logic step: Deobfuscate the 'n' throttling parameter on EVERY stream to unlock speeds
                if (url != null) {
                    try {
                        url = org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to deobfuscate throttling parameter", e)
                    }
                }

                if (!url.isNullOrBlank()) {
                    availableAudioStreams.add(
                        StreamData(
                            url = url,
                            mimeType = mimeType,
                            bitrate = bitrate,
                            contentLength = obj.get("contentLength")?.asLong,
                            audioQuality = obj.get("audioQuality")?.asString
                        )
                    )
                }
            }

            // Return matching formats only when one exists. If the requested format is absent,
            // retain the actual available formats so the caller can ask the listener to choose
            // rather than silently changing the requested container/codec.
            val matchingStreams = availableAudioStreams.filter {
                NewPipeStreamExtractor.matchesAudioFormat(
                    it.mimeType,
                    preferredFormat = preferredAudioFormat
                )
            }
            // Playback (no explicit override) is Opus-first and falls back only to M4A.
            // Download parsing passes an override and retains the actual source list so the
            // download chooser can offer only its supported formats.
            val playbackM4aFallback = if (
                preferredAudioFormatOverride == null &&
                preferredAudioFormat == "opus" &&
                matchingStreams.isEmpty()
            ) {
                availableAudioStreams.filter {
                    NewPipeStreamExtractor.matchesAudioFormat(it.mimeType, preferredFormat = "m4a")
                }
            } else {
                emptyList()
            }
            val formatCandidates = when {
                preferredAudioFormat == "auto" -> {
                    availableAudioStreams.filter { it.mimeType.contains("audio/mp4", ignoreCase = true) }
                        .ifEmpty { availableAudioStreams }
                }
                matchingStreams.isNotEmpty() -> matchingStreams
                preferredAudioFormatOverride == null && preferredAudioFormat == "opus" ->
                    playbackM4aFallback
                preferredAudioFormatOverride == null -> emptyList()
                else -> availableAudioStreams
            }
            val streams = formatCandidates.sortedWith(
                compareBy<StreamData> {
                    if (it.bitrate > 0) abs(it.bitrate - targetBitrateBps) else Int.MAX_VALUE
                }
            )

            Log.d(
                TAG,
                "Parsed player: $videoId — ${streams.size} candidate audio streams from ${allFormats.size} total formats; " +
                    "requested=$preferredAudioFormat, matches=${matchingStreams.size}"
            )

            val tracking = json.getAsJsonObject("playbackTracking")
            val playbackUrl = tracking?.getAsJsonObject("videostatsPlaybackUrl")?.get("baseUrl")?.asString
            val watchtimeUrl = tracking?.getAsJsonObject("videostatsWatchtimeUrl")?.get("baseUrl")?.asString

            return PlayerResult(
                videoId = videoId,
                title = title,
                artist = author,
                thumbnailUrl = thumbnail,
                durationMs = durationMs,
                streams = streams,
                playbackUrl = playbackUrl,
                watchtimeUrl = watchtimeUrl
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing player response", e)
            return null
        }
    }

    // ========== Browse / Home Feed Parsing ==========

    fun parseHomePage(json: JsonObject): HomePage {
        val sections = mutableListOf<HomeSection>()
        val seenSectionTitles = linkedSetOf<String>()

        fun addSection(section: JsonObject) {
            val parsed = parseHomeSection(section) ?: return
            val key = parsed.title.trim().lowercase()
            if (key.isNotEmpty() && seenSectionTitles.add(key)) {
                sections.add(parsed)
            }
        }

        fun addSectionContents(contents: JsonArray?) {
            contents?.forEach { element ->
                if (element.isJsonObject) addSection(element.asJsonObject)
            }
        }

        try {
            // YouTube Music serves multiple browse layouts. Walk every section-list renderer
            // instead of assuming all clients return singleColumnBrowseResultsRenderer.
            findObjects(json, "sectionListRenderer").forEach { renderer ->
                addSectionContents(renderer.getAsJsonArray("contents"))
            }
            findObjects(json, "sectionListContinuation").forEach { renderer ->
                addSectionContents(renderer.getAsJsonArray("contents"))
            }

            // Some Explore categories are represented as shelves outside a section-list
            // wrapper. Parse those supported shelf renderers as a fallback and deduplicate.
            val shelfTypes = listOf(
                "musicCarouselShelfRenderer",
                "musicShelfRenderer",
                "musicPlaylistShelfRenderer",
                "musicCardShelfRenderer",
                "gridRenderer"
            )
            shelfTypes.forEach { type ->
                findObjects(json, type).forEach { renderer ->
                    addSection(JsonObject().apply { add(type, renderer) })
                }
            }

            val continuationCandidates =
                findObjects(json, "sectionListRenderer").mapNotNull { renderer ->
                    renderer.path("continuations.0.nextContinuationData.continuation")?.asString
                } +
                findObjects(json, "sectionListContinuation").mapNotNull { renderer ->
                    renderer.path("continuations.0.nextContinuationData.continuation")?.asString
                } +
                findObjects(json, "musicCarouselShelfRenderer").mapNotNull { renderer ->
                    renderer.path("continuations.0.nextContinuationData.continuation")?.asString
                } +
                findObjects(json, "musicShelfRenderer").mapNotNull { renderer ->
                    renderer.path("continuations.0.nextContinuationData.continuation")?.asString
                } +
                findObjects(json, "musicPlaylistShelfRenderer").mapNotNull { renderer ->
                    renderer.path("continuations.0.nextContinuationData.continuation")?.asString
                } +
                findObjects(json, "gridRenderer").mapNotNull { renderer ->
                    renderer.path("continuations.0.nextContinuationData.continuation")?.asString
                } +
                findObjects(json, "continuationItemRenderer").mapNotNull { renderer ->
                    renderer.path("continuationEndpoint.continuationCommand.token")?.asString
                }
            val continuation = continuationCandidates.firstOrNull { it.isNotBlank() }

            // Keep actual browseEndpoint values with the category labels.
            val moods = mutableListOf<String>()
            val categories = mutableListOf<HomeCategory>()
            findFirstObject(json, "chipCloudRenderer")?.getAsJsonArray("chips")?.forEach { chip ->
                val wrapper = chip.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val renderer = wrapper.getAsJsonObject("chipCloudChipRenderer") ?: wrapper
                val title = renderer.path("text.runs.0.text")?.asString
                    ?: renderer.path("text.simpleText")?.asString
                if (title.isNullOrBlank()) return@forEach
                moods.add(title)
                val browse = renderer.path("navigationEndpoint.browseEndpoint")?.asJsonObject
                    ?: renderer.path("onSelectCommand.browseEndpoint")?.asJsonObject
                    ?: renderer.path("navigationEndpoint.commandExecutorCommand.commands.0.browseEndpoint")?.asJsonObject
                val browseId = browse?.get("browseId")?.asString?.takeIf { it.isNotBlank() }
                if (browseId != null) {
                    categories.add(
                        HomeCategory(title, browseId, browse.get("params")?.asString?.takeIf { it.isNotBlank() })
                    )
                }
            }
            return HomePage(
                sections = sections,
                continuation = continuation,
                moods = moods.distinct(),
                categories = categories.distinctBy { it.browseId + "|" + it.params.orEmpty() }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing home page", e)
        }

        return HomePage(sections)
    }

    /**
     * YouTube Music's Search discovery page uses musicNavigationButtonRenderer entries,
     * not the chipCloudRenderer used by the Home page. Keep the real browse destination
     * (including params) so every mood/genre opens its own catalogue page.
     */
    fun parseMoodsAndGenres(json: JsonObject): List<HomeCategory> {
        val categories = mutableListOf<HomeCategory>()

        fun add(title: String?, browse: JsonObject?) {
            val cleanTitle = title?.trim().orEmpty()
            val browseId = browse?.get("browseId")?.asString?.takeIf { it.isNotBlank() }
            if (cleanTitle.isNotEmpty() && browseId != null) {
                categories.add(
                    HomeCategory(
                        title = cleanTitle,
                        browseId = browseId,
                        params = browse.get("params")?.asString?.takeIf { it.isNotBlank() }
                    )
                )
            }
        }

        findObjects(json, "musicNavigationButtonRenderer").forEach { renderer ->
            val title = renderer.path("buttonText.runs")?.asJsonArray
                ?.joinToString("") { run ->
                    run.takeIf { it.isJsonObject }?.asJsonObject?.get("text")?.asString.orEmpty()
                }
                ?.takeIf { it.isNotBlank() }
                ?: renderer.path("buttonText.simpleText")?.asString
                ?: renderer.get("buttonText")?.takeIf { it.isJsonPrimitive }?.asString

            val browse = renderer.path("clickCommand.browseEndpoint")?.asJsonObject
                ?: renderer.path("clickCommand.commandExecutorCommand.commands.0.browseEndpoint")?.asJsonObject
                ?: renderer.path("navigationEndpoint.browseEndpoint")?.asJsonObject
            add(title, browse)
        }

        // Retain compatibility with older Web layouts where discovery destinations
        // are supplied as chipCloudChipRenderer entries instead.
        if (categories.isEmpty()) {
            findObjects(json, "chipCloudChipRenderer").forEach { renderer ->
                val title = renderer.path("text.runs.0.text")?.asString
                    ?: renderer.path("text.simpleText")?.asString
                val browse = renderer.path("navigationEndpoint.browseEndpoint")?.asJsonObject
                    ?: renderer.path("onSelectCommand.browseEndpoint")?.asJsonObject
                    ?: renderer.path("navigationEndpoint.commandExecutorCommand.commands.0.browseEndpoint")?.asJsonObject
                add(title, browse)
            }
        }

        return categories.distinctBy { it.browseId + "|" + it.params.orEmpty() }
    }

    private fun parseHomeSection(section: JsonObject): HomeSection? {
        val shelf = section.getAsJsonObject("musicCarouselShelfRenderer")
            ?: section.getAsJsonObject("musicShelfRenderer")
            ?: section.getAsJsonObject("musicPlaylistShelfRenderer")
            ?: section.getAsJsonObject("musicCardShelfRenderer")
            ?: section.getAsJsonObject("gridRenderer")
            ?: return null

        val titlePaths = listOf(
            "header.musicCarouselShelfBasicHeaderRenderer.title.runs.0.text",
            "header.musicCarouselShelfBasicHeaderRenderer.title.simpleText",
            "header.musicShelfHeaderRenderer.title.runs.0.text",
            "header.musicShelfHeaderRenderer.title.simpleText",
            "header.musicPlaylistShelfHeaderRenderer.title.runs.0.text",
            "header.musicPlaylistShelfHeaderRenderer.title.simpleText",
            "header.musicCardShelfHeaderBasicRenderer.title.runs.0.text",
            "header.musicCardShelfHeaderBasicRenderer.title.simpleText",
            "header.gridHeaderRenderer.title.runs.0.text",
            "header.gridHeaderRenderer.title.simpleText",
            "title.runs.0.text",
            "title.simpleText"
        )
        val title = titlePaths.asSequence()
            .mapNotNull { path -> shelf.path(path)?.asString?.takeIf { it.isNotBlank() } }
            .firstOrNull()
            ?: return null

        val contents = shelf.getAsJsonArray("contents") ?: shelf.getAsJsonArray("items")
        val items = mutableListOf<SearchItem>()
        contents?.forEach { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val twoRowRenderer = obj.getAsJsonObject("musicTwoRowItemRenderer")
            val listRenderer = obj.getAsJsonObject("musicResponsiveListItemRenderer")

            when {
                twoRowRenderer != null -> parseTwoRowItem(twoRowRenderer)?.let { items.add(it) }
                listRenderer != null -> parseSearchItem(obj)?.let { items.add(it) }
                obj.getAsJsonObject("musicMultiRowListItemRenderer") != null -> {
                    parsePodcastEpisodes(obj).forEach { items.add(SearchItem.Song(it)) }
                }
                else -> parseSearchItem(obj)?.let { items.add(it) }
            }
        }

        val uniqueItems = items.distinctBy { item ->
            when (item) {
                is SearchItem.Song -> "song:${item.song.videoId}"
                is SearchItem.Album -> "album:${item.album.browseId}"
                is SearchItem.Artist -> "artist:${item.artist.browseId}"
                is SearchItem.Playlist -> "playlist:${item.playlist.playlistId}"
            }
        }
        if (uniqueItems.isEmpty()) return null

        val browseEndpointPaths = listOf(
            "header.musicCarouselShelfBasicHeaderRenderer.moreContentButton.buttonRenderer.navigationEndpoint.browseEndpoint.browseId",
            "header.musicShelfHeaderRenderer.moreContentButton.buttonRenderer.navigationEndpoint.browseEndpoint.browseId",
            "header.musicPlaylistShelfHeaderRenderer.moreContentButton.buttonRenderer.navigationEndpoint.browseEndpoint.browseId"
        )
        val paramsPaths = listOf(
            "header.musicCarouselShelfBasicHeaderRenderer.moreContentButton.buttonRenderer.navigationEndpoint.browseEndpoint.params",
            "header.musicShelfHeaderRenderer.moreContentButton.buttonRenderer.navigationEndpoint.browseEndpoint.params",
            "header.musicPlaylistShelfHeaderRenderer.moreContentButton.buttonRenderer.navigationEndpoint.browseEndpoint.params"
        )

        return HomeSection(
            title = title,
            items = uniqueItems,
            browseEndpoint = browseEndpointPaths.asSequence()
                .mapNotNull { shelf.path(it)?.asString?.takeIf(String::isNotBlank) }
                .firstOrNull(),
            params = paramsPaths.asSequence()
                .mapNotNull { shelf.path(it)?.asString?.takeIf(String::isNotBlank) }
                .firstOrNull()
        )
    }

    private fun parseArtistRefs(runs: com.google.gson.JsonArray?): List<OnlineArtistRef> {
        if (runs == null) return emptyList()
        return runs.mapNotNull { run ->
            val item = run.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val pageType = item.path(
                "navigationEndpoint.browseEndpoint.browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType"
            )?.asString
            if (pageType != "MUSIC_PAGE_TYPE_ARTIST" && pageType != "MUSIC_PAGE_TYPE_USER_CHANNEL") {
                return@mapNotNull null
            }
            val name = item.get("text")?.asString?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null
            OnlineArtistRef(name, item.path("navigationEndpoint.browseEndpoint.browseId")?.asString)
        }.distinctBy { it.id?.takeIf(String::isNotBlank) ?: it.name.trim().lowercase() }
    }

    private fun parseTwoRowItem(renderer: JsonObject): SearchItem? {
        try {
            val title = renderer.path("title.runs.0.text")?.asString ?: return null
            val subtitle = renderer.path("subtitle.runs")?.asJsonArray
                ?.joinToString("") { run ->
                    run.takeIf { run.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
                } ?: ""
            val thumbnail = renderer.path("thumbnailRenderer.musicThumbnailRenderer.thumbnail.thumbnails")
                ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString

            val navEndpoint = renderer.path("navigationEndpoint")?.asJsonObject
            val browseEndpoint = navEndpoint?.getAsJsonObject("browseEndpoint")
            val watchEndpoint = navEndpoint?.getAsJsonObject("watchEndpoint")

            return when {
                watchEndpoint != null -> {
                    val videoId = watchEndpoint.get("videoId")?.asString ?: return null
                    val refs = parseArtistRefs(renderer.path("subtitle.runs")?.asJsonArray)
                    SearchItem.Song(OnlineSong(
                        videoId = videoId,
                        title = title,
                        artist = refs.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name } ?: getCleanArtist(subtitle),
                        artistId = refs.firstOrNull()?.id,
                        thumbnailUrl = thumbnail,
                        artistRefs = refs
                    ))
                }
                browseEndpoint != null -> {
                    val browseId = browseEndpoint.get("browseId")?.asString ?: return null
                    val pageType = browseEndpoint.path(
                        "browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType"
                    )?.asString

                    when (pageType) {
                        "MUSIC_PAGE_TYPE_ALBUM" -> {
                            SearchItem.Album(OnlineAlbum(
                                browseId = browseId,
                                title = title,
                                artists = listOf(OnlineArtistRef(subtitle.split(" • ").drop(1).firstOrNull() ?: "")),
                                thumbnailUrl = thumbnail,
                                year = subtitle.split(" • ").lastOrNull()?.toIntOrNull()
                            ))
                        }
                        "MUSIC_PAGE_TYPE_ARTIST" -> {
                            SearchItem.Artist(OnlineArtist(
                                browseId = browseId,
                                title = title,
                                thumbnailUrl = thumbnail,
                                subscriberCount = subtitle
                            ))
                        }
                        "MUSIC_PAGE_TYPE_PLAYLIST" -> {
                            SearchItem.Playlist(OnlinePlaylist(
                                playlistId = browseId.removePrefix("VL"),
                                title = title,
                                thumbnailUrl = thumbnail,
                                songCount = subtitle.split(" • ").lastOrNull()
                            ))
                        }
                        else -> {
                            // Try to infer from browseId prefix
                            when {
                                browseId.startsWith("UC") -> {
                                    SearchItem.Artist(OnlineArtist(
                                        browseId = browseId,
                                        title = title,
                                        thumbnailUrl = thumbnail,
                                        subscriberCount = subtitle
                                    ))
                                }
                                browseId.startsWith("VL") || browseId.startsWith("PL") -> {
                                    SearchItem.Playlist(OnlinePlaylist(
                                        playlistId = browseId.removePrefix("VL"),
                                        title = title,
                                        thumbnailUrl = thumbnail,
                                        songCount = subtitle.split(" • ").lastOrNull()
                                    ))
                                }
                                browseId.startsWith("MPREb") -> {
                                    SearchItem.Album(OnlineAlbum(
                                        browseId = browseId,
                                        title = title,
                                        artists = listOf(OnlineArtistRef(subtitle.split(" • ").drop(1).firstOrNull() ?: "")),
                                        thumbnailUrl = thumbnail,
                                        year = subtitle.split(" • ").lastOrNull()?.toIntOrNull()
                                    ))
                                }
                                else -> null
                            }
                        }
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse two-row item: ${e.message}")
            return null
        }
    }

    // ========== Album Page Parsing ==========

    fun parseAlbumPage(json: JsonObject, browseId: String): AlbumPage? {
        try {
            val header = json.path(
                "contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicResponsiveHeaderRenderer"
            )?.asJsonObject ?: return null

            val title = header.path("title.runs.0.text")?.asString ?: return null
            val artists = header.path("straplineTextOne.runs")?.asJsonArray
                ?.filterIndexed { i, _ -> i % 2 == 0 }
                ?.map { run ->
                    val obj = run.asJsonObject
                    OnlineArtistRef(
                        name = obj.get("text")?.asString ?: "",
                        id = obj.path("navigationEndpoint.browseEndpoint.browseId")?.asString
                    )
                } ?: emptyList()

            val year = header.path("subtitle.runs")?.asJsonArray
                ?.lastOrNull()?.asJsonObject?.get("text")?.asString?.toIntOrNull()

            val thumbnail = header.path("thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
                ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString

            val playlistId = json.path("microformat.microformatDataRenderer.urlCanonical")
                ?.asString?.substringAfterLast("=") ?: ""

            // Parse songs
            val songContents = json.path(
                "contents.twoColumnBrowseResultsRenderer.secondaryContents.sectionListRenderer.contents.0.musicShelfRenderer.contents"
            )?.asJsonArray ?: json.path(
                "contents.twoColumnBrowseResultsRenderer.secondaryContents.sectionListRenderer.contents.0.musicPlaylistShelfRenderer.contents"
            )?.asJsonArray

            val songs = songContents?.mapNotNull { item ->
                parseAlbumSong(item.asJsonObject, thumbnail)
            } ?: emptyList()

            return AlbumPage(
                album = OnlineAlbum(
                    browseId = browseId,
                    playlistId = playlistId,
                    title = title,
                    artists = artists,
                    year = year,
                    thumbnailUrl = thumbnail
                ),
                songs = songs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing album page", e)
            return null
        }
    }

    private fun parseAlbumSong(item: JsonObject, fallbackThumbnail: String?): OnlineSong? {
        val renderer = item.getAsJsonObject("musicResponsiveListItemRenderer") ?: return null
        try {
            val flexColumns = renderer.getAsJsonArray("flexColumns")
            val title = flexColumns?.getOrNull(0)?.asJsonObject
                ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs.0.text")?.asString ?: return null

            val videoId = renderer.path("overlay.musicItemThumbnailOverlayRenderer.content.musicPlayButtonRenderer.playNavigationEndpoint.watchEndpoint.videoId")?.asString
                ?: flexColumns.getOrNull(0)?.asJsonObject
                    ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs.0.navigationEndpoint.watchEndpoint.videoId")?.asString
                ?: return null

            val subtitleRuns = flexColumns.getOrNull(1)?.asJsonObject
                ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs")?.asJsonArray
            val artist = subtitleRuns?.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" } ?: ""

            val fixedColumns = renderer.getAsJsonArray("fixedColumns")
            val duration = fixedColumns?.getOrNull(0)?.asJsonObject
                ?.path("musicResponsiveListItemFixedColumnRenderer.text.runs.0.text")?.asString

            val thumbnail = parseThumbnail(renderer) ?: fallbackThumbnail

            return OnlineSong(
                videoId = videoId,
                title = title,
                artist = artist.trim(),
                duration = duration,
                durationMs = parseDuration(duration),
                thumbnailUrl = thumbnail
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse album song: ${e.message}")
            return null
        }
    }

    // ========== Account & Library Parsing ==========

    fun parseAccountProfile(json: JsonObject): UserProfile? {
        try {
            val header = json.path("actions.0.openPopupAction.popup.multiPageMenuRenderer.header.activeAccountHeaderRenderer")?.asJsonObject
                ?: return null

            val name = header.path("accountName.runs.0.text")?.asString 
                ?: header.path("accountName.simpleText")?.asString
                ?: "Unknown User"
            val handle = header.path("channelHandle.runs.0.text")?.asString
                ?: header.path("channelHandle.simpleText")?.asString
            val email = header.path("email.runs.0.text")?.asString
                ?: header.path("email.simpleText")?.asString
            val avatarUrl = header.path("accountPhoto.thumbnails")?.asJsonArray
                ?.lastOrNull()?.asJsonObject?.get("url")?.asString

            return UserProfile(
                name = name,
                handle = handle,
                email = email,
                avatarUrl = getHighResThumbnailUrl(avatarUrl)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing account profile", e)
            return null
        }
    }

    fun parseHistory(json: JsonObject): List<OnlineSong> {
        val songs = mutableListOf<OnlineSong>()
        try {
            val sections = json.path("contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents")?.asJsonArray
            sections?.forEach { section ->
                val shelf = section.asJsonObject.getAsJsonObject("musicShelfRenderer")
                shelf?.getAsJsonArray("contents")?.forEach { item ->
                    val searchItem = parseSearchItem(item.asJsonObject)
                    if (searchItem is SearchItem.Song) {
                        songs.add(searchItem.song)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing history", e)
        }
        return songs
    }

    /**
     * Parse lyrics from a browse response for a lyrics browseId (MPLYt...).
     * Returns the plain text lyrics string.
     */
    fun parseLyrics(json: JsonObject): String {
        try {
            // Primary path: musicDescriptionShelfRenderer
            val description = json.path("contents.sectionListRenderer.contents.0.musicDescriptionShelfRenderer.description.runs")?.asJsonArray
            if (description != null && description.size() > 0) {
                return description.joinToString("") { run ->
                    run.takeIf { it.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
                }
            }

            // Fallback: Try plain text description
            val plainText = json.path("contents.sectionListRenderer.contents.0.musicDescriptionShelfRenderer.description.simpleText")?.asString
            if (!plainText.isNullOrEmpty()) return plainText

            // Ultimate fallback: Search for any text content
            val sections = json.path("contents.sectionListRenderer.contents")?.asJsonArray
            sections?.forEach { section ->
                val renderer = section.asJsonObject
                val messageRenderer = renderer.getAsJsonObject("messageRenderer")
                if (messageRenderer != null) {
                    val text = messageRenderer.path("text.runs.0.text")?.asString
                        ?: messageRenderer.path("text.simpleText")?.asString
                    if (!text.isNullOrEmpty()) return text
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing lyrics", e)
        }
        throw IllegalStateException("No lyrics content found in response")
    }



    fun parseLibraryPlaylists(json: JsonObject): List<OnlinePlaylist> {
        val playlists = mutableListOf<OnlinePlaylist>()
        try {
            val items = json.path("contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.gridRenderer.items")?.asJsonArray
                ?: json.path("contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.gridRenderer.items")?.asJsonArray
                ?: json.path("contents.sectionListRenderer.contents.0.gridRenderer.items")?.asJsonArray
                ?: json.path("contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.musicGridRenderer.items")?.asJsonArray
                ?: json.path("contents.sectionListRenderer.contents.0.musicGridRenderer.items")?.asJsonArray
                ?: json.path("contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicShelfRenderer.contents")?.asJsonArray
                ?: json.path("contents.sectionListRenderer.contents.0.musicShelfRenderer.contents")?.asJsonArray

            if (items != null) {
                return parseLibraryPlaylistsList(items)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing library playlists", e)
        }
        return playlists
    }

    fun parseLibraryPlaylistsList(items: JsonArray): List<OnlinePlaylist> {
        val playlists = mutableListOf<OnlinePlaylist>()
        items.forEach { itemObj ->
            val item = itemObj.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            if (item.has("musicTwoRowItemRenderer")) {
                val twoRow = item.getAsJsonObject("musicTwoRowItemRenderer")
                try {
                    val title = twoRow.path("title.runs.0.text")?.asString ?: return@forEach
                    val thumbnail = twoRow.path("thumbnailRenderer.musicThumbnailRenderer.thumbnail.thumbnails")
                        ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                    val browseId = twoRow.path("navigationEndpoint.browseEndpoint.browseId")?.asString ?: return@forEach
                    val subtitle = twoRow.path("subtitle.runs")?.asJsonArray
                        ?.joinToString("") { run ->
                            run.takeIf { run.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
                        } ?: ""
                    playlists.add(OnlinePlaylist(
                        playlistId = browseId.removePrefix("VL"),
                        title = title,
                        thumbnailUrl = thumbnail,
                        songCount = subtitle.split(" • ").lastOrNull()
                    ))
                } catch (_: Exception) {}
            } else if (item.has("musicResponsiveListItemRenderer")) {
                val listItem = item.getAsJsonObject("musicResponsiveListItemRenderer")
                try {
                    val title = listItem.path("flexColumns.0.musicResponsiveListItemFlexColumnRenderer.text.runs.0.text")?.asString ?: return@forEach
                    val browseId = listItem.path("navigationEndpoint.browseEndpoint.browseId")?.asString
                        ?: listItem.path("flexColumns.0.musicResponsiveListItemFlexColumnRenderer.text.runs.0.navigationEndpoint.browseEndpoint.browseId")?.asString ?: return@forEach
                    val thumbnail = listItem.path("thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
                        ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                    playlists.add(OnlinePlaylist(
                        playlistId = browseId.removePrefix("VL"),
                        title = title,
                        thumbnailUrl = thumbnail,
                        songCount = ""
                    ))
                } catch (_: Exception) {}
            }
        }
        return playlists
    }

    fun parseLibraryArtists(json: JsonObject): List<OnlineArtist> {
        val artists = mutableListOf<OnlineArtist>()
        try {
            val items = json.path("contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.gridRenderer.items")?.asJsonArray
                ?: json.path("contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.gridRenderer.items")?.asJsonArray
                ?: json.path("contents.sectionListRenderer.contents.0.gridRenderer.items")?.asJsonArray
                ?: json.path("contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.musicGridRenderer.items")?.asJsonArray
                ?: json.path("contents.sectionListRenderer.contents.0.musicGridRenderer.items")?.asJsonArray
                ?: json.path("contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicShelfRenderer.contents")?.asJsonArray
                ?: json.path("contents.sectionListRenderer.contents.0.musicShelfRenderer.contents")?.asJsonArray

            if (items != null) {
                return parseLibraryArtistsList(items)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing library artists", e)
        }
        return artists
    }

    fun parseLibraryArtistsList(items: JsonArray): List<OnlineArtist> {
        val artists = mutableListOf<OnlineArtist>()
        items.forEach { itemObj ->
            val item = itemObj.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            if (item.has("musicTwoRowItemRenderer")) {
                val twoRow = item.getAsJsonObject("musicTwoRowItemRenderer")
                try {
                    val title = twoRow.path("title.runs.0.text")?.asString ?: return@forEach
                    val thumbnail = twoRow.path("thumbnailRenderer.musicThumbnailRenderer.thumbnail.thumbnails")
                        ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                    val browseId = twoRow.path("navigationEndpoint.browseEndpoint.browseId")?.asString ?: return@forEach
                    val subtitle = twoRow.path("subtitle.runs")?.asJsonArray
                        ?.joinToString("") { run ->
                            run.takeIf { run.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
                        } ?: ""
                    artists.add(OnlineArtist(
                        browseId = browseId,
                        title = title,
                        thumbnailUrl = thumbnail,
                        subscriberCount = subtitle
                    ))
                } catch (_: Exception) {}
            } else if (item.has("musicResponsiveListItemRenderer")) {
                val listItem = item.getAsJsonObject("musicResponsiveListItemRenderer")
                try {
                    val title = listItem.path("flexColumns.0.musicResponsiveListItemFlexColumnRenderer.text.runs.0.text")?.asString ?: return@forEach
                    val browseId = listItem.path("navigationEndpoint.browseEndpoint.browseId")?.asString
                        ?: listItem.path("flexColumns.0.musicResponsiveListItemFlexColumnRenderer.text.runs.0.navigationEndpoint.browseEndpoint.browseId")?.asString ?: return@forEach
                    val thumbnail = listItem.path("thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
                        ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                    artists.add(OnlineArtist(
                        browseId = browseId,
                        title = title,
                        thumbnailUrl = thumbnail,
                        subscriberCount = ""
                    ))
                } catch (_: Exception) {}
            }
        }
        return artists
    }

    fun parseLibraryAlbums(json: JsonObject): List<OnlineAlbum> {
        val albums = mutableListOf<OnlineAlbum>()
        try {
            val items = json.path("contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.gridRenderer.items")?.asJsonArray
                ?: json.path("contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.gridRenderer.items")?.asJsonArray
                ?: json.path("contents.sectionListRenderer.contents.0.gridRenderer.items")?.asJsonArray
                ?: json.path("contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.musicGridRenderer.items")?.asJsonArray
                ?: json.path("contents.sectionListRenderer.contents.0.musicGridRenderer.items")?.asJsonArray
                ?: json.path("contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicShelfRenderer.contents")?.asJsonArray
                ?: json.path("contents.sectionListRenderer.contents.0.musicShelfRenderer.contents")?.asJsonArray

            if (items != null) {
                return parseLibraryAlbumsList(items)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing library albums", e)
        }
        return albums
    }

    fun parseLibraryAlbumsList(items: JsonArray): List<OnlineAlbum> {
        val albums = mutableListOf<OnlineAlbum>()
        items.forEach { itemObj ->
            val item = itemObj.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            if (item.has("musicTwoRowItemRenderer")) {
                val twoRow = item.getAsJsonObject("musicTwoRowItemRenderer")
                try {
                    val title = twoRow.path("title.runs.0.text")?.asString ?: return@forEach
                    val thumbnail = twoRow.path("thumbnailRenderer.musicThumbnailRenderer.thumbnail.thumbnails")
                        ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                    val browseId = twoRow.path("navigationEndpoint.browseEndpoint.browseId")?.asString ?: return@forEach
                    val subtitle = twoRow.path("subtitle.runs")?.asJsonArray
                        ?.joinToString("") { run ->
                            run.takeIf { run.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
                        } ?: ""
                    albums.add(OnlineAlbum(
                        browseId = browseId,
                        title = title,
                        artists = listOf(OnlineArtistRef(subtitle.split(" • ").drop(1).firstOrNull() ?: subtitle.split(" • ").firstOrNull() ?: "")),
                        thumbnailUrl = thumbnail,
                        year = subtitle.split(" • ").lastOrNull()?.toIntOrNull()
                    ))
                } catch (_: Exception) {}
            } else if (item.has("musicResponsiveListItemRenderer")) {
                val listItem = item.getAsJsonObject("musicResponsiveListItemRenderer")
                try {
                    val title = listItem.path("flexColumns.0.musicResponsiveListItemFlexColumnRenderer.text.runs.0.text")?.asString ?: return@forEach
                    val browseId = listItem.path("navigationEndpoint.browseEndpoint.browseId")?.asString
                        ?: listItem.path("flexColumns.0.musicResponsiveListItemFlexColumnRenderer.text.runs.0.navigationEndpoint.browseEndpoint.browseId")?.asString ?: return@forEach
                    val thumbnail = listItem.path("thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
                        ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                    albums.add(OnlineAlbum(
                        browseId = browseId,
                        title = title,
                        artists = emptyList(),
                        thumbnailUrl = thumbnail,
                        year = null
                    ))
                } catch (_: Exception) {}
            }
        }
        return albums
    }

    fun extractLibraryContinuation(json: JsonObject): String? {
        val prefix = "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0."
        val prefix2 = "contents.sectionListRenderer.contents.0."
        
        return json.path(prefix + "gridRenderer.continuations.0.nextContinuationData.continuation")?.asString
            ?: json.path(prefix + "musicGridRenderer.continuations.0.nextContinuationData.continuation")?.asString
            ?: json.path(prefix + "musicShelfRenderer.continuations.0.nextContinuationData.continuation")?.asString
            ?: json.path(prefix2 + "gridRenderer.continuations.0.nextContinuationData.continuation")?.asString
            ?: json.path(prefix2 + "musicGridRenderer.continuations.0.nextContinuationData.continuation")?.asString
            ?: json.path(prefix2 + "musicShelfRenderer.continuations.0.nextContinuationData.continuation")?.asString
            ?: json.path("contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.gridRenderer.continuations.0.nextContinuationData.continuation")?.asString
            ?: json.path("contents.twoColumnBrowseResultsRenderer.secondaryContents.sectionListRenderer.contents.0.musicPlaylistShelfRenderer.continuations.0.nextContinuationData.continuation")?.asString
    }

    /**
     * Parse playlist shelf continuation pages into playable songs while preserving the next
     * token for bulk collection actions.
     */
    fun parsePlaylistContinuationSongs(json: JsonObject): Pair<List<OnlineSong>, String?> {
        val (items, nextToken) = extractContinuationItemsAndNextToken(json)
        val songs = items?.mapNotNull { element ->
            val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            (parseSearchItem(item) as? SearchItem.Song)?.song
        }.orEmpty()
        return songs to nextToken
    }

    fun extractContinuationItemsAndNextToken(json: JsonObject): Pair<JsonArray?, String?> {
        val gridItems = json.path("continuationContents.gridContinuation.items")?.asJsonArray
            ?: json.path("continuationContents.musicGridContinuation.items")?.asJsonArray
            ?: json.path("continuationContents.musicShelfContinuation.contents")?.asJsonArray
            ?: json.path("continuationContents.musicPlaylistShelfContinuation.contents")?.asJsonArray
            ?: json.path("continuationContents.musicPlaylistShelfContinuation.items")?.asJsonArray
            
        val nextToken = json.path("continuationContents.gridContinuation.continuations.0.nextContinuationData.continuation")?.asString
            ?: json.path("continuationContents.musicGridContinuation.continuations.0.nextContinuationData.continuation")?.asString
            ?: json.path("continuationContents.musicShelfContinuation.continuations.0.nextContinuationData.continuation")?.asString
            ?: json.path("continuationContents.musicPlaylistShelfContinuation.continuations.0.nextContinuationData.continuation")?.asString
            
        return Pair(gridItems, nextToken)
    }

    // ========== Utility ==========

    private fun parseThumbnail(renderer: JsonObject): String? {
        val url = renderer.path("thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
            ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
        return getHighResThumbnailUrl(url)
    }

    /**
     * Replaces YouTube's low-resolution url parameters with high-resolution parameters (1080p)
     */
    fun getHighResThumbnailUrl(url: String?): String? {
        if (url == null) return null

        var finalUrl = url
        if (finalUrl.startsWith("//")) {
            finalUrl = "https:$finalUrl"
        }

        val prefs = try {
            com.dsann.music.DsmusicApplication.instance.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
        } catch (e: Exception) { null }
        val dataSaver = prefs?.getBoolean("data_saver", false) ?: false

        val targetRes = if (dataSaver) "=w540-h540" else "=w1080-h1080"

        // YouTube music album covers usually have a pattern like: =w120-h120-l90-rj
        // We replace any =w... block with =w1080-h1080 or =w540-h540
        if (finalUrl.contains("=w") && finalUrl.contains("-h")) {
            return finalUrl.replace(Regex("=w\\d+-h\\d+"), targetRes)
        }
        
        // Account avatars usually have a pattern like: =s88-c-k-c0x00...
        // We replace any =s88 or =s\d+ with =s1080
        if (finalUrl.contains("=s")) {
            val targetSquareRes = if (dataSaver) "=s540" else "=s1080"
            return finalUrl.replace(Regex("=s\\d+"), targetSquareRes)
        }

        // For ytimg.com video thumbnails, maxresdefault.jpg often 404s if the video isn't 720p+.
        // hqdefault.jpg (480x360) is guaranteed to exist.
        val targetHqRes = "hqdefault.jpg"

        if (finalUrl.contains("sqdefault.jpg")) return finalUrl.replace("sqdefault.jpg", targetHqRes)
        if (finalUrl.contains("mqdefault.jpg")) return finalUrl.replace("mqdefault.jpg", targetHqRes)

        return finalUrl
    }

    /** Parse duration string like "3:45" to milliseconds */
    private fun parseDuration(duration: String?): Long {
        if (duration == null) return 0
        return try {
            val parts = duration.split(":")
            when (parts.size) {
                2 -> (parts[0].toLong() * 60 + parts[1].toLong()) * 1000
                3 -> (parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()) * 1000
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun getCleanArtist(subtitleText: String): String {
        val ignoredTypes = setOf("song", "video", "single", "ep", "album", "artist", "playlist")
        val parts = subtitleText.split(" • ").map { it.trim() }
        val cleanParts = parts.filter { it.lowercase() !in ignoredTypes && it.isNotEmpty() }
        return cleanParts.firstOrNull() ?: "Unknown Artist"
    }

    private fun parseQueryString(query: String): Map<String, String> {
        return try {
            query.split("&").mapNotNull { pairStr ->
                val pair = pairStr.split("=", limit = 2)
                if (pair.size == 2) {
                    val key = java.net.URLDecoder.decode(pair[0], "UTF-8")
                    val value = java.net.URLDecoder.decode(pair[1], "UTF-8")
                    key to value
                } else null
            }.toMap()
        } catch (_: Exception) { emptyMap() }
    }
}

// ========== JSON Navigation Extension ==========

// High-performance statically allocated cache to eliminate thousands of string splits and GC sweeps per parse cycle
private val jsonPathCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

fun JsonElement?.path(path: String): JsonElement? {
    if (this == null) return null
    var current: JsonElement? = this
    
    // Atomically fetch pre-digested path fragments (Instant retrieval, 0 garbage generated)
    val keys = jsonPathCache[path] ?: path.split(".").let { computed ->
        jsonPathCache.putIfAbsent(path, computed) ?: computed
    }
    
    for (key in keys) {
        if (current == null) return null
        current = when {
            current.isJsonObject -> current.asJsonObject.get(key)
            current.isJsonArray -> {
                val index = key.toIntOrNull()
                if (index != null) current.asJsonArray.getOrNull(index)
                else null
            }
            else -> null
        }
    }
    return current
}

/** Safe getOrNull for JsonArray */
fun JsonArray.getOrNull(index: Int): JsonElement? {
    return if (index in 0 until size()) get(index) else null
}
