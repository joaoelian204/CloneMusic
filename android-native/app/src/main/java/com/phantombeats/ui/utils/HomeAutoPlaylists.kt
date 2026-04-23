package com.phantombeats.ui.utils

import com.phantombeats.domain.model.Song
import kotlin.math.ln
import kotlin.math.max

data class AutoPlaylist(
    val id: String,
    val title: String,
    val subtitle: String,
    val songs: List<Song>
)

fun buildAutoPlaylists(
    pool: List<Song>,
    recentQueries: List<String>,
    maxPlaylists: Int = 6,
    tracksPerPlaylist: Int = 40,
    likedSongKeys: Set<String> = emptySet(),
    blockedSongKeys: Set<String> = emptySet()
): List<AutoPlaylist> {
    if (pool.isEmpty()) return emptyList()

    val songs = pool.distinctBy { it.id }
    val signals = extractPreferenceSignals(recentQueries)
    val specs = playlistCategorySpecs()
    val globalUsedSongKeys = mutableSetOf<String>()

    val generated = mutableListOf<AutoPlaylist>()

    specs.forEach { spec ->
        val demandBoost = categoryDemandBoost(spec, recentQueries)
        val ranked = songs
            .mapNotNull { song ->
                val baseScore = categorySongScore(song, spec, signals)
                if (baseScore <= 0.0) null else song to (baseScore + demandBoost)
            }
            .sortedByDescending { it.second }
            .distinctBy { it.first.id }

        val selectedSongs = selectCategorySongs(
            rankedSongs = ranked,
            limit = tracksPerPlaylist,
            enforceArtistDiversity = spec.enforceArtistDiversity,
            maxSongsPerArtist = spec.maxSongsPerArtist,
            maxSongsPerAlbum = spec.maxSongsPerAlbum,
            maxSongsPerDecade = spec.maxSongsPerDecade,
            maxSongsPerGenre = spec.maxSongsPerGenre,
            globallyUsedSongKeys = globalUsedSongKeys,
            blockedSongKeys = blockedSongKeys
        )

        if (selectedSongs.size >= 6) {
            selectedSongs.mapTo(globalUsedSongKeys) { songFeedbackKey(it) }
            generated.add(
                AutoPlaylist(
                    id = spec.id,
                    title = spec.title,
                    subtitle = spec.subtitle,
                    songs = selectedSongs
                )
            )
        }
    }

    val tokenBased = buildTokenDrivenPlaylist(songs, signals)
    if (tokenBased != null && generated.none { it.id == tokenBased.id }) {
        val tokenSongs = tokenBased.songs
            .filter { songFeedbackKey(it) !in globalUsedSongKeys && songFeedbackKey(it) !in blockedSongKeys }
        if (tokenSongs.size >= 6) {
            globalUsedSongKeys.addAll(tokenSongs.map { songFeedbackKey(it) })
            generated.add(0, tokenBased.copy(songs = tokenSongs))
        }
    }

    val likedBoostPlaylists = if (likedSongKeys.isNotEmpty()) {
        generated.map { playlist ->
            val boostedSongs = playlist.songs.sortedByDescending { song ->
                if (songFeedbackKey(song) in likedSongKeys) 1 else 0
            }
            playlist.copy(songs = boostedSongs)
        }
    } else {
        generated
    }

    if (likedBoostPlaylists.isNotEmpty()) {
        return likedBoostPlaylists.take(maxPlaylists)
    }

    val fallback = songs
        .filter { songFeedbackKey(it) !in blockedSongKeys }
        .sortedByDescending { if (it.isFavorite) 1 else 0 }
        .sortedByDescending { it.playCount }
        .take(tracksPerPlaylist)

    return if (fallback.size >= 6) {
        listOf(
            AutoPlaylist(
                id = "auto-favoritas",
                title = "Tus favoritas",
                subtitle = "Se actualiza con lo que escuchas",
                songs = fallback
            )
        )
    } else {
        emptyList()
    }
}

private data class CategorySpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val keywords: Set<String>,
    val artistWhitelist: Set<String>,
    val requiredGenres: Set<String> = emptySet(),
    val languageMode: LanguageMode = LanguageMode.ANY,
    val enforceArtistDiversity: Boolean = true,
    val maxSongsPerArtist: Int = 2,
    val maxSongsPerAlbum: Int = 3,
    val maxSongsPerDecade: Int = 6,
    val maxSongsPerGenre: Int = 8,
    val matcher: (String) -> Boolean
)

private enum class LanguageMode {
    ANY,
    ENGLISH_STRICT,
    SPANISH_STRICT
}

private data class AutoPlaylistSignals(
    val tokenWeights: Map<String, Double>
)

private fun playlistCategorySpecs(): List<CategorySpec> {
    val reggaetonOld = setOf(
        "reggaeton viejo", "old school", "daddy yankee", "don omar", "wisin", "yandel",
        "hector", "zion", "nicky jam", "arcangel", "de la ghetto", "ivy queen", "tito"
    )

    val rockLatino = setOf(
        "rock latino", "soda stereo", "mana", "caifanes", "hombres g", "los prisioneros",
        "fito paez", "charly garcia", "heroes del silencio", "juanes", "molotov", "enanitos"
    )

    val latinAmerica = setOf(
        "latino", "bachata", "salsa", "merengue", "cumbia", "urbano", "reggaeton",
        "feid", "karol g", "maluma", "bad bunny", "j balvin", "romeo santos", "shakira",
        "anuel", "myke towers", "rauw", "arcangel", "ozuna", "camilo", "sech", "yandel"
    )

    val english = setOf(
        "english", "ingles", "ed sheeran", "drake", "the weeknd", "dua lipa", "coldplay",
        "taylor swift", "ariana grande", "eminem", "post malone", "billie eilish"
    )

    return listOf(
        CategorySpec(
            id = "auto-reggaeton-viejo",
            title = "Reggaeton Viejo",
            subtitle = "Perreo clasico segun tu vibe",
            keywords = reggaetonOld,
            requiredGenres = setOf("urbano-latino"),
            languageMode = LanguageMode.SPANISH_STRICT,
            artistWhitelist = setOf(
                "daddy yankee", "don omar", "wisin", "yandel", "nicky jam", "ivy queen", "arcangel", "zion", "tito"
            ),
            maxSongsPerArtist = 3,
            maxSongsPerDecade = 8,
            maxSongsPerGenre = 14,
            matcher = { text -> text.contains("reggaeton") || reggaetonOld.any { text.contains(it) } }
        ),
        CategorySpec(
            id = "auto-rock-latino",
            title = "Rock en Espanol / Latino",
            subtitle = "Solo rock latino y en espanol",
            keywords = rockLatino,
            requiredGenres = setOf("rock"),
            languageMode = LanguageMode.SPANISH_STRICT,
            artistWhitelist = setOf(
                "soda stereo", "mana", "caifanes", "hombres g", "los prisioneros", "fito paez", "charly garcia", "heroes del silencio", "juanes", "molotov", "enanitos", "andres calamaro", "bunbury", "zoe", "la ley"
            ),
            maxSongsPerArtist = 3,
            maxSongsPerDecade = 8,
            maxSongsPerGenre = 14,
            matcher = { text -> text.contains("rock") || rockLatino.any { text.contains(it) } }
        ),
        CategorySpec(
            id = "auto-latinoamerica",
            title = "Latinoamerica",
            subtitle = "Solo canciones latinas",
            keywords = latinAmerica,
            requiredGenres = setOf("urbano-latino", "tropical", "other"),
            languageMode = LanguageMode.SPANISH_STRICT,
            artistWhitelist = setOf(
                "feid", "karol g", "maluma", "bad bunny", "j balvin", "romeo santos", "shakira", "camilo", "anuel", "ozuna", "manuel turizo", "myke towers", "rauw", "arcangel", "sech", "yandel"
            ),
            maxSongsPerArtist = 3,
            maxSongsPerDecade = 10,
            maxSongsPerGenre = 14,
            matcher = { text -> isLikelyLatinText(text) || latinAmerica.any { text.contains(it) } }
        ),
        CategorySpec(
            id = "auto-ingles",
            title = "Hits en Ingles",
            subtitle = "Lo mas cercano a lo que buscas",
            keywords = english,
            requiredGenres = setOf("pop", "hiphop", "rock", "other"),
            languageMode = LanguageMode.ENGLISH_STRICT,
            artistWhitelist = setOf(
                "ed sheeran", "drake", "the weeknd", "dua lipa", "coldplay", "taylor swift", "ariana grande", "eminem", "post malone", "billie eilish"
            ),
            maxSongsPerArtist = 3,
            maxSongsPerDecade = 8,
            maxSongsPerGenre = 12,
            matcher = { text -> isLikelyEnglishText(text) || english.any { text.contains(it) } }
        ),
        CategorySpec(
            id = "auto-rock-english",
            title = "Rock en Ingles",
            subtitle = "Rock internacional con artistas variados",
            keywords = setOf("rock", "indie", "alternative", "punk", "metal", "grunge"),
            requiredGenres = setOf("rock"),
            languageMode = LanguageMode.ENGLISH_STRICT,
            artistWhitelist = setOf("coldplay", "imagine dragons", "linkin park", "nirvana", "queen", "u2", "arctic monkeys", "foo fighters"),
            maxSongsPerArtist = 3,
            maxSongsPerDecade = 8,
            maxSongsPerGenre = 12,
            matcher = { text -> text.contains("rock") || text.contains("indie") || text.contains("alternative") }
        ),
        CategorySpec(
            id = "auto-urbano-latino",
            title = "Urbano Latino",
            subtitle = "Trap, reggaeton y urbano en espanol",
            keywords = setOf("urbano", "reggaeton", "trap", "dembow", "perreo", "latin trap"),
            requiredGenres = setOf("urbano-latino"),
            languageMode = LanguageMode.SPANISH_STRICT,
            artistWhitelist = setOf("bad bunny", "anuel", "myke towers", "rauw", "arcangel", "feid", "karol g", "j balvin", "ozuna", "yandel"),
            maxSongsPerArtist = 3,
            maxSongsPerDecade = 9,
            maxSongsPerGenre = 14,
            matcher = { text -> text.contains("urbano") || text.contains("reggaeton") || text.contains("trap") || text.contains("dembow") }
        )
    )
}

private fun selectCategorySongs(
    rankedSongs: List<Pair<Song, Double>>,
    limit: Int,
    enforceArtistDiversity: Boolean,
    maxSongsPerArtist: Int,
    maxSongsPerAlbum: Int,
    maxSongsPerDecade: Int,
    maxSongsPerGenre: Int,
    globallyUsedSongKeys: Set<String>,
    blockedSongKeys: Set<String>
): List<Song> {
    val artistCap = maxSongsPerArtist.coerceAtLeast(1)

    if (!enforceArtistDiversity) {
        val raw = rankedSongs
            .map { it.first }
            .filter { song ->
                val key = songFeedbackKey(song)
                key !in blockedSongKeys && key !in globallyUsedSongKeys
            }
            .take(limit)

        return enforceFinalArtistBalance(
            selected = raw,
            rankedSongs = rankedSongs,
            maxSongsPerArtist = artistCap
        )
    }

    val selected = mutableListOf<Song>()
    val artistCounter = mutableMapOf<String, Int>()
    val albumCounter = mutableMapOf<String, Int>()
    val decadeCounter = mutableMapOf<String, Int>()
    val genreCounter = mutableMapOf<String, Int>()

    fun canSelect(song: Song): Boolean {
        val songKey = songFeedbackKey(song)
        if (songKey in blockedSongKeys || songKey in globallyUsedSongKeys) return false
        val artistKey = song.artist.toArtistBucketKey()
        val albumKey = song.toAlbumBucketKey()
        val decadeKey = inferDecadeBucket(song)
        val genreKey = inferGenreBucket(song)
        val artistCount = artistCounter[artistKey] ?: 0
        val albumCount = albumCounter[albumKey] ?: 0
        val decadeCount = decadeCounter[decadeKey] ?: 0
        val genreCount = genreCounter[genreKey] ?: 0
        val decadeLimit = if (decadeKey == "unknown") max(limit, maxSongsPerDecade) else maxSongsPerDecade

        if (artistCount >= artistCap) return false
        if (albumCount >= maxSongsPerAlbum) return false
        if (decadeCount >= decadeLimit) return false
        if (genreCount >= maxSongsPerGenre) return false

        val normalizedTitle = canonicalSongTitle(song.title)
        val artistTitleKey = "$artistKey::$normalizedTitle"
        val hasSameArtistAndTitle = selected.any {
            val currentArtist = it.artist.toArtistBucketKey()
            val currentTitle = canonicalSongTitle(it.title)
            "$currentArtist::$currentTitle" == artistTitleKey
        }
        return !hasSameArtistAndTitle
    }

    fun registerSelected(song: Song) {
        selected.add(song)
        val artistKey = song.artist.toArtistBucketKey()
        val albumKey = song.toAlbumBucketKey()
        val decadeKey = inferDecadeBucket(song)
        val genreKey = inferGenreBucket(song)
        artistCounter[artistKey] = (artistCounter[artistKey] ?: 0) + 1
        albumCounter[albumKey] = (albumCounter[albumKey] ?: 0) + 1
        decadeCounter[decadeKey] = (decadeCounter[decadeKey] ?: 0) + 1
        genreCounter[genreKey] = (genreCounter[genreKey] ?: 0) + 1
    }

    val byArtist = rankedSongs
        .groupBy { it.first.artist.toArtistBucketKey() }
        .mapValues { (_, songs) -> songs.toMutableList() }
        .toMutableMap()
    val artistOrder = byArtist
        .entries
        .sortedByDescending { entry -> entry.value.firstOrNull()?.second ?: 0.0 }
        .map { it.key }
        .toMutableList()

    while (selected.size < limit && artistOrder.isNotEmpty()) {
        val iterator = artistOrder.iterator()
        while (iterator.hasNext() && selected.size < limit) {
            val artist = iterator.next()
            val bucket = byArtist[artist]
            if (bucket == null) {
                iterator.remove()
                continue
            }

            val next = bucket.firstOrNull { (song, _) -> canSelect(song) }
            if (next == null) {
                val fallback = bucket.removeFirstOrNull()
                if (fallback == null || bucket.isEmpty()) {
                    iterator.remove()
                }
                continue
            }

            bucket.remove(next)
            registerSelected(next.first)

            if (bucket.isEmpty()) {
                iterator.remove()
            }
        }
    }

    if (selected.size >= limit) {
        return enforceFinalArtistBalance(
            selected = selected.take(limit),
            rankedSongs = rankedSongs,
            maxSongsPerArtist = artistCap
        )
    }

    rankedSongs.forEach { (song, _) ->
        if (selected.size >= limit) return@forEach
        if (canSelect(song)) {
            registerSelected(song)
        }
    }

    if (selected.size >= limit) {
        return enforceFinalArtistBalance(
            selected = selected.take(limit),
            rankedSongs = rankedSongs,
            maxSongsPerArtist = artistCap
        )
    }

    rankedSongs.forEach { (song, _) ->
        if (selected.size >= limit) return@forEach
        if (selected.any { it.id == song.id }) return@forEach
        val songKey = songFeedbackKey(song)
        if (songKey in blockedSongKeys || songKey in globallyUsedSongKeys) return@forEach
        selected.add(song)
    }

    return enforceFinalArtistBalance(
        selected = selected.take(limit),
        rankedSongs = rankedSongs,
        maxSongsPerArtist = artistCap
    )
}

private fun enforceFinalArtistBalance(
    selected: List<Song>,
    rankedSongs: List<Pair<Song, Double>>,
    maxSongsPerArtist: Int
): List<Song> {
    if (selected.isEmpty()) return selected

    val cap = maxSongsPerArtist.coerceAtLeast(1)
    val artistCounter = mutableMapOf<String, Int>()
    val kept = mutableListOf<Song>()

    selected.forEach { song ->
        val artistKey = song.artist.toArtistBucketKey()
        val count = artistCounter[artistKey] ?: 0
        if (count < cap) {
            kept += song
            artistCounter[artistKey] = count + 1
        }
    }

    if (kept.size == selected.size) return kept

    val targetSize = selected.size
    val usedIds = kept.mapTo(mutableSetOf()) { it.id }

    rankedSongs.forEach { (song, _) ->
        if (kept.size >= targetSize) return@forEach
        if (song.id in usedIds) return@forEach

        val artistKey = song.artist.toArtistBucketKey()
        val count = artistCounter[artistKey] ?: 0
        if (count >= cap) return@forEach

        kept += song
        usedIds += song.id
        artistCounter[artistKey] = count + 1
    }

    return kept
}

private fun categorySongScore(song: Song, spec: CategorySpec, signals: AutoPlaylistSignals): Double {
    val rawText = "${song.title} ${song.artist}".trim()
    val titleText = song.title.normalizeAutoText()
    val artistText = song.artist.normalizeAutoText()
    val text = "$titleText $artistText"
    val keywordHits = spec.keywords.count { text.contains(it) }
    val matchesCategory = spec.matcher(text)
    val whitelistedArtist = spec.artistWhitelist.any { artistText.contains(it) }
    val inferredGenre = inferGenreBucket(song)
    val matchesGenreRequirement = spec.requiredGenres.isEmpty() || inferredGenre in spec.requiredGenres
    val languagePass = when (spec.languageMode) {
        LanguageMode.ANY -> true
        LanguageMode.ENGLISH_STRICT -> isLikelyEnglishText(rawText) && !isLikelyLatinText(rawText)
        LanguageMode.SPANISH_STRICT -> isLikelySpanishText(rawText) && !isLikelyEnglishText(rawText)
    }
    val genreMatch = when (spec.id) {
        "auto-rock-latino" -> inferredGenre == "rock"
        "auto-rock-english" -> inferredGenre == "rock"
        "auto-reggaeton-viejo" -> inferredGenre == "urbano-latino"
        "auto-urbano-latino" -> inferredGenre == "urbano-latino"
        "auto-latinoamerica" -> inferredGenre == "urbano-latino" || inferredGenre == "tropical"
        "auto-ingles" -> inferredGenre == "pop" || inferredGenre == "hiphop"
        else -> false
    }

    if (!languagePass) {
        return 0.0
    }

    if (!matchesGenreRequirement && !whitelistedArtist) {
        return 0.0
    }

    val strictMatch = matchesCategory || whitelistedArtist || genreMatch || matchesGenreRequirement

    if (!strictMatch) return 0.0

    var score = 0.0
    score += keywordHits * 20.0
    if (strictMatch) score += 14.0
    if (whitelistedArtist) score += 12.0
    if (genreMatch) score += 10.0
    if (song.isFavorite) score += 14.0
    score += ln((song.playCount + 1).toDouble()) * 10.0

    signals.tokenWeights.forEach { (token, weight) ->
        if (text.contains(token)) {
            score += weight * 6.0
        }
    }

    return score
}

fun songFeedbackKey(song: Song): String {
    return "${canonicalSongTitle(song.title)}|${canonicalArtistName(song.artist)}"
}

private fun canonicalArtistName(value: String): String {
    return value.normalizeAutoText()
}

private fun canonicalSongTitle(value: String): String {
    return value
        .lowercase()
        .replace(Regex("\\((feat|ft|featuring)[^)]+\\)", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\[(feat|ft|featuring)[^]]+\\]", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("(?i)\\b(feat|ft|featuring)\\.?\\s+[^-()\\[]+"), " ")
        .replace(Regex("(?i)\\b(live|acoustic|remix|remaster(ed)?|version|radio edit|karaoke)\\b"), " ")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun inferDecadeBucket(song: Song): String {
    val text = "${song.title} ${song.artist}"
    val yearMatch = Regex("(19|20)\\d{2}").find(text)
    val year = yearMatch?.value?.toIntOrNull()
    if (year != null) {
        val decade = (year / 10) * 10
        return "${decade}s"
    }

    val artist = song.artist.normalizeAutoText()
    return when {
        artist.contains("hombres g") || artist.contains("soda stereo") || artist.contains("mana") -> "1980s"
        artist.contains("caifanes") || artist.contains("los prisioneros") -> "1990s"
        artist.contains("daddy yankee") || artist.contains("don omar") || artist.contains("wisin") -> "2000s"
        artist.contains("bad bunny") || artist.contains("karol g") || artist.contains("the weeknd") -> "2010s"
        else -> "unknown"
    }
}

private fun inferGenreBucket(song: Song): String {
    val text = "${song.title} ${song.artist}".normalizeAutoText()
    return when {
        listOf("reggaeton", "perreo", "urbano", "trap").any { text.contains(it) } -> "urbano-latino"
        listOf("rock", "guitar", "band", "hombres g", "soda stereo").any { text.contains(it) } -> "rock"
        listOf("bachata", "salsa", "merengue", "cumbia").any { text.contains(it) } -> "tropical"
        listOf("pop", "taylor swift", "dua lipa", "ariana grande").any { text.contains(it) } -> "pop"
        listOf("rap", "hip hop", "drake", "eminem").any { text.contains(it) } -> "hiphop"
        else -> "other"
    }
}

private fun categoryDemandBoost(spec: CategorySpec, recentQueries: List<String>): Double {
    val normalizedQueries = recentQueries.map { it.normalizeAutoText() }
    val demandHits = normalizedQueries.count { query ->
        spec.keywords.any { query.contains(it) }
    }
    return demandHits * 8.0
}

private fun buildTokenDrivenPlaylist(songs: List<Song>, signals: AutoPlaylistSignals): AutoPlaylist? {
    val topToken = signals.tokenWeights.maxByOrNull { it.value }?.key ?: return null
    if (topToken.length < 4) return null

    val ranked = songs
        .filter { (it.title + " " + it.artist).normalizeAutoText().contains(topToken) }
        .sortedByDescending { if (it.isFavorite) 1 else 0 }
        .sortedByDescending { it.playCount }
        .take(18)

    if (ranked.size < 6) return null

    val displayToken = topToken.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    return AutoPlaylist(
        id = "auto-token-$topToken",
        title = "Basado en $displayToken",
        subtitle = "Creada automaticamente con tus busquedas",
        songs = ranked
    )
}

private fun extractPreferenceSignals(queries: List<String>): AutoPlaylistSignals {
    val stopWords = setOf(
        "de", "del", "la", "el", "los", "las", "y", "and", "the",
        "cancion", "canciones", "song", "songs", "album", "albums", "musica", "music"
    )

    val cleaned = queries.map { it.normalizeAutoText() }.filter { it.isNotBlank() }
    if (cleaned.isEmpty()) return AutoPlaylistSignals(emptyMap())

    val total = cleaned.size.toDouble().coerceAtLeast(1.0)
    val weights = mutableMapOf<String, Double>()

    cleaned.forEachIndexed { index, query ->
        val recencyWeight = ((cleaned.size - index).toDouble() / total).coerceAtLeast(0.2)
        query.split(" ")
            .asSequence()
            .map { it.trim() }
            .filter { it.length >= 3 && it !in stopWords }
            .forEach { token ->
                weights[token] = (weights[token] ?: 0.0) + recencyWeight
            }
    }

    return AutoPlaylistSignals(tokenWeights = weights)
}

private fun String.toArtistBucketKey(): String {
    val normalized = normalizeAutoText()
    if (normalized.isBlank()) return "desconocido"

    return normalized
        .replace(Regex("\\b(official|topic|records?|music|vevo)\\b"), " ")
        .split(Regex("\\s*(,|&| x | feat | ft\\.? )\\s*"))
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        ?.replace(Regex("\\s+"), " ")
        ?: normalized
}

private fun Song.toAlbumBucketKey(): String {
    val cover = coverUrl.normalizeAutoText()
    if (cover.isNotBlank()) return cover

    val titleAnchor = title.normalizeAutoText()
        .split(" ")
        .take(3)
        .joinToString(" ")
        .trim()

    val artistAnchor = artist.toArtistBucketKey()
    return "$artistAnchor::$titleAnchor"
}

private fun isLikelyEnglishText(text: String): Boolean {
    val containsCjk = text.any { ch ->
        ch in '\u3040'..'\u30ff' || // Japones
            ch in '\u3400'..'\u4dbf' || // CJK extension
            ch in '\u4e00'..'\u9fff' || // Han
            ch in '\uac00'..'\ud7af' // Coreano
    }
    if (containsCjk) return false

    val normalized = " ${text.normalizeAutoText()} "
    val englishWords = listOf(" the ", " and ", " love ", " night ", " baby ", " with ", " my ", " you ", " heart ", " lonely ", " dream ")
    val spanishWords = listOf(" que ", " con ", " por ", " para ", " amor ", " noche ", " corazon ", " tu ", " mi ", " quiero ", " beso ")

    val englishHits = englishWords.count { normalized.contains(it) }
    val spanishHits = spanishWords.count { normalized.contains(it) }

    return englishHits >= 1 && englishHits > spanishHits
}

private fun isLikelyLatinText(text: String): Boolean {
    val normalized = text.normalizeAutoText()
    val latinWords = listOf(" que ", " con ", " por ", " para ", " amor ", " noche ", " corazon ", " perreo ", " bailable ")
    return latinWords.any { normalized.contains(it) }
}

private fun isLikelySpanishText(text: String): Boolean {
    val normalized = " ${text.normalizeAutoText()} "
    val spanishWords = listOf(
        " que ", " con ", " por ", " para ", " amor ", " noche ", " corazon ", " besos ",
        " latido ", " llorar ", " volver ", " querer ", " contigo ", " conmigo ", " urbano ", " reggaeton ", " bachata ", " salsa "
    )
    val englishWords = listOf(" the ", " and ", " love ", " night ", " with ", " my ", " you ", " lonely ")
    val spanishHits = spanishWords.count { normalized.contains(it) }
    val englishHits = englishWords.count { normalized.contains(it) }
    return spanishHits >= 1 && spanishHits >= englishHits
}

private fun String.normalizeAutoText(): String {
    return lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
