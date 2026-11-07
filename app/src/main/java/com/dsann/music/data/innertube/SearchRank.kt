package com.dsann.music.data.innertube

import java.text.Normalizer

/**
 * Relevance ranking for YouTube Music search.
 *
 * Search results are fetched per result type. YouTube's unfiltered "Top result" card is
 * treated as a strong candidate, but exact artists/songs/albums and non-cover versions
 * can outrank it. In particular, title substrings are not treated as identity matches.
 */
internal object SearchRank {
    private const val CARD_PRIOR = 0.95

    private val limits = mapOf(
        SearchFilter.SONGS to 6,
        SearchFilter.ALBUMS to 8,
        SearchFilter.ARTISTS to 6,
        SearchFilter.FEATURED_PLAYLISTS to 8,
        SearchFilter.PLAYLISTS to 8,
        SearchFilter.VIDEOS to 6
    )

    fun norm(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .replace("&", " and ")
        .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun core(title: String): String = norm(
        title.replace(Regex("\\s*[(\\[].*?[)\\]]"), "")
            .replace(Regex("\\s+-\\s+.*$"), "")
    )

    private val knockoff = Regex(
        "\\b(instrumental|karaoke|cover|tribute|sped up|slowed|nightcore|8d|lofi|lo fi|piano version|made famous|flip|remix|mashup|bootleg)\\b"
    )

    private fun knockoffMarks(title: String, query: String): List<String> {
        val tails = Regex("[(\\[]([^)\\]]*)[)\\]]|\\s-\\s(.*)$")
            .findAll(title)
            .joinToString(" ") { norm(it.value) }
        val asked = norm(query).split(" ").toSet()
        return knockoff.findAll(tails).map { it.value }
            .filter { marker -> marker.split(" ").any { it !in asked } }
            .toList()
    }

    private fun initialsOf(value: String): String? {
        val words = core(value).split(" ").filter { it.isNotEmpty() }
        if (words.size < 2) return null
        return words.joinToString("") { it.take(1) }
    }

    /** 0..1 relevance; every word and the title/artist relationship matter. */
    fun match(query: String, title: String, extra: String = ""): Double {
        val q = norm(query)
        if (q.isEmpty()) return 0.0
        val t = norm(title)
        if (t == q) return 1.0
        if (core(title) == q) return if (knockoffMarks(title, q).isNotEmpty()) 0.7 else 0.97

        val queryWords = q.split(" ")
        val combinedWords = norm("$title $extra").split(" ").toSet()

        // The query can contain both title and artist, in either result text's representation.
        if (queryWords.all { it in combinedWords } &&
            core(title).split(" ").all { it in queryWords }
        ) return 0.95

        if (initialsOf(title) == q) return 0.9
        if (t.startsWith(q)) return 0.75
        if (queryWords.all { word -> combinedWords.any { it.startsWith(word) } }) return 0.6
        if (extra.isNotBlank() && extra.split(" • ", ", ", " & ").any { initialsOf(it) == q }) return 0.55
        if (t.contains(q)) return 0.45
        return 0.2
    }

    private val audienceRegex = Regex("(\\d[\\d.,  ]*\\d|\\d)\\s*([\\p{L}\\p{M}.]*)")
    private val audienceUnits = linkedMapOf(
        "mrd" to 1e9, "bn" to 1e9, "億" to 1e8, "亿" to 1e8, "억" to 1e8,
        "crore" to 1e7, "क" to 1e7, "lakh" to 1e5, "लाख" to 1e5,
        "mil" to 1e3, "mio" to 1e6, "mln" to 1e6, "млн" to 1e6, "mn" to 1e6,
        "mi" to 1e6, "jt" to 1e6, "tr" to 1e6, "ล้าน" to 1e6, "مليون" to 1e6,
        "ألف" to 1e3, "萬" to 1e4, "万" to 1e4, "만" to 1e4,
        "тыс" to 1e3, "тис" to 1e3, "tys" to 1e3, "tsd" to 1e3,
        "rb" to 1e3, "k" to 1e3, "m" to 1e6, "b" to 1e9
    )

    private fun audience(subtitle: String): Long {
        val text = subtitle.substringAfter(" • ", subtitle)
        val match = audienceRegex.find(text) ?: return 0L
        val raw = match.groupValues[1]
        val number = Regex("[.,](\\d{1,2})$").find(raw)?.let { decimal ->
            (raw.dropLast(decimal.value.length).replace(Regex("[., ]"), "") + "." + decimal.groupValues[1]).toDoubleOrNull()
        } ?: raw.replace(Regex("[., ]"), "").toDoubleOrNull() ?: return 0L
        val unit = match.groupValues[2].lowercase().trimEnd('.')
        val multiplier = audienceUnits.entries.firstOrNull { (prefix, _) -> unit.startsWith(prefix) }?.value ?: 1.0
        return (number * multiplier).toLong()
    }

    private fun weight(item: SearchItem): Double = when (item) {
        is SearchItem.Artist -> if (audience(item.artist.subscriberCount.orEmpty()) >= 1_000_000L) 1.0 else 0.8
        is SearchItem.Album -> 0.93
        is SearchItem.Song -> if (item.song.isVideo) 0.72 else 0.86
        is SearchItem.Playlist -> 0.62
    }

    fun score(query: String, item: SearchItem, position: Int): Double {
        val extra = when (item) {
            is SearchItem.Song -> item.song.artist
            is SearchItem.Album -> item.album.artists.joinToString(" • ") { it.name }
            is SearchItem.Artist -> item.artist.subscriberCount.orEmpty()
            is SearchItem.Playlist -> item.playlist.author?.name.orEmpty()
        }
        return match(query, title(item), extra) * weight(item) - position * 0.08
    }

    private fun title(item: SearchItem): String = when (item) {
        is SearchItem.Song -> item.song.title
        is SearchItem.Album -> item.album.title
        is SearchItem.Artist -> item.artist.title
        is SearchItem.Playlist -> item.playlist.title
    }

    private fun key(item: SearchItem): String = when (item) {
        is SearchItem.Song -> "song:${item.song.videoId}"
        is SearchItem.Album -> "album:${item.album.browseId}"
        is SearchItem.Artist -> "artist:${item.artist.browseId}"
        is SearchItem.Playlist -> "playlist:${item.playlist.playlistId}"
    }

    private fun kind(item: SearchItem): SearchFilter = when (item) {
        is SearchItem.Song -> if (item.song.isVideo) SearchFilter.VIDEOS else SearchFilter.SONGS
        is SearchItem.Album -> SearchFilter.ALBUMS
        is SearchItem.Artist -> SearchFilter.ARTISTS
        is SearchItem.Playlist -> SearchFilter.PLAYLISTS
    }

    private fun trustedCard(query: String, item: SearchItem): Boolean =
        knockoffMarks(title(item), norm(query)).isEmpty()

    private fun wholeWords(query: String, title: String): Boolean {
        val titleWords = norm(title).split(" ").toSet()
        return norm(query).split(" ").all { it in titleWords }
    }

    fun rank(
        query: String,
        results: List<Pair<SearchFilter, List<SearchItem>>>,
        card: SearchItem? = null
    ): SearchResult {
        val live = results.firstOrNull { it.first == SearchFilter.LIVE }
            ?.second.orEmpty().distinctBy(::key).take(10)

        // Podcasts and episodes remain available as their own shelves, but do not beat a music
        // artist/song just because YouTube labels a show or episode with the same short title.
        val musicFilters = setOf(
            SearchFilter.SONGS, SearchFilter.ALBUMS, SearchFilter.ARTISTS,
            SearchFilter.FEATURED_PLAYLISTS, SearchFilter.PLAYLISTS, SearchFilter.VIDEOS
        )
        val musicResults = results.filter { it.first in musicFilters }

        val songs = musicResults.firstOrNull { it.first == SearchFilter.SONGS }
            ?.second.orEmpty().filterIsInstance<SearchItem.Song>()
        val cardAsSong = (card as? SearchItem.Song)?.takeIf { it.song.isVideo }?.let { video ->
            songs.firstOrNull { song ->
                core(song.song.title) == core(video.song.title) &&
                    song.song.artist.isNotBlank() &&
                    norm(song.song.artist) == norm(video.song.artist)
            }
        }
        val trusted = (cardAsSong ?: card)?.takeIf { trustedCard(query, it) }
        val cardKind = trusted?.let(::kind)

        val withCard = if (trusted == null) musicResults else {
            val placed = musicResults.map { (filter, items) ->
                if (filter == cardKind) filter to (listOf(trusted) + items.filterNot { key(it) == key(trusted) })
                else filter to items
            }
            if (placed.any { it.first == cardKind }) placed else placed + (cardKind!! to listOf(trusted))
        }

        val trimmed = withCard.mapNotNull { (filter, items) ->
            val distinct = items.distinctBy(::key).take(limits[filter] ?: 6)
            distinct.takeIf { it.isNotEmpty() }?.let { filter to it }
        }
        val scored = trimmed.map { (filter, items) ->
            Triple(
                filter,
                items,
                items.mapIndexed { index, item ->
                    if (trusted != null && key(item) == key(trusted)) {
                        maxOf(score(query, item, 0), CARD_PRIOR)
                    } else {
                        score(query, item, index)
                    }
                }
            )
        }

        var top = scored.flatMap { (_, items, scores) ->
            items.zip(scores).take(3)
        }.maxByOrNull { it.second }?.first

        // A video card should give way to its audio recording if both are the same track.
        // Title-track album promotion is only used when YouTube supplies an explicit album id.
        if (top is SearchItem.Song) {
            val song = top.song
            val albumId = song.albumId
            val namesAlbum = !song.albumName.isNullOrBlank() &&
                core(song.albumName) == core(song.title) &&
                core(song.albumName) == norm(query)
            if (namesAlbum && !albumId.isNullOrBlank()) {
                val album = trimmed.firstOrNull { it.first == SearchFilter.ALBUMS }
                    ?.second?.filterIsInstance<SearchItem.Album>()
                    ?.firstOrNull { it.album.browseId == albumId }
                if (album != null && album.album.artists.any { norm(it.name) == norm(song.artist) }) {
                    val songTop = top
                    top = album
                    // Put the title track first in the Songs section after promoting the album.
                    val updated = scored.map { (filter, items, scores) ->
                        if (filter == SearchFilter.SONGS) {
                            val moved = listOf(songTop) + items.filterNot { key(it) == key(songTop) }
                            Triple(filter, moved, moved.mapIndexed { i, item -> score(query, item, i) })
                        } else Triple(filter, items, scores)
                    }
                    return finish(query, updated, top, results, live)
                }
            }
        }
        return finish(query, scored, top, results, live)
    }

    private fun finish(
        query: String,
        scored: List<Triple<SearchFilter, List<SearchItem>, List<Double>>>,
        top: SearchItem?,
        allResults: List<Pair<SearchFilter, List<SearchItem>>>,
        live: List<SearchItem>
    ): SearchResult {
        val effectiveTop = top ?: live.firstOrNull()
        val topKey = effectiveTop?.let(::key)
        val topArtist = (effectiveTop as? SearchItem.Artist)?.artist?.title?.let(::norm)
        fun relatedToTopArtist(item: SearchItem): Boolean = topArtist != null && when (item) {
            is SearchItem.Song -> norm(item.song.artist) == topArtist
            is SearchItem.Album -> item.album.artists.any { norm(it.name) == topArtist }
            else -> false
        }

        val rankedSections = scored
            .sortedByDescending { (filter, items, scores) ->
                val best = items.zip(scores).filter { it.first != top }.maxOfOrNull { it.second } ?: 0.0
                val related = if (items.any(::relatedToTopArtist)) 0.8 else 0.0
                maxOf(best, related, if (filter == SearchFilter.SONGS) 0.5 else 0.0)
            }
            .map { (filter, items, _) -> filter to items.filterNot { key(it) == topKey } }
            .filter { it.second.isNotEmpty() }

        val extraSections = allResults
            .filter { it.first == SearchFilter.PODCASTS || it.first == SearchFilter.EPISODES }
            .mapNotNull { (filter, items) ->
                val remaining = items.distinctBy(::key).filterNot { key(it) == topKey }
                remaining.takeIf { it.isNotEmpty() }?.let { filter to it.take(6) }
            }

        val sections = rankedSections + extraSections + (live.filterNot { key(it) == topKey }
            .takeIf { it.isNotEmpty() }?.let { listOf(SearchFilter.LIVE to it) }.orEmpty())

        val visibleSections = sections.map { (filter, items) -> SearchResultSection(filter, items) }
        val allItems = visibleSections.flatMap { it.items }
        return SearchResult(
            items = allItems,
            continuation = null,
            topResult = effectiveTop,
            sections = visibleSections
        )
    }
}
