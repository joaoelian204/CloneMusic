package com.phantombeats.ui.utils

import com.phantombeats.domain.model.Song
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

data class HomeRecommendationResult(
    val mixSongs: List<Song>,
    val recommendedSongs: List<Song>
)

fun buildHomeRecommendations(
    pool: List<Song>,
    recentQueries: List<String>,
    mixSize: Int = 6,
    recommendedSize: Int = 15
): HomeRecommendationResult {
    if (pool.isEmpty()) {
        return HomeRecommendationResult(emptyList(), emptyList())
    }

    val signals = extractPreferenceSignals(recentQueries)
    val nowMs = System.currentTimeMillis()
    val randomSeed = nowMs xor pool.size.toLong() xor recentQueries.joinToString("|").hashCode().toLong()
    val random = Random(randomSeed)

    val ranked = pool
        .distinctBy { it.contentKey() }
        .map { song ->
            ScoredSong(
                song = song,
                score = recommendationScore(song = song, signals = signals, nowMs = nowMs)
            )
        }
        .sortedWith(
            compareByDescending<ScoredSong> { it.score }
                .thenByDescending { it.song.isFavorite }
                .thenByDescending { it.song.playCount }
                .thenBy { it.song.title.lowercase() }
        )

    val mixFocus = ranked.filter { songMatchesTokens(it.song, signals.tokens) }
    val mixCandidates = (mixFocus + ranked)
        .distinctBy { it.song.contentKey() }

    val mix = selectRandomizedBlend(
        ranked = mixCandidates,
        random = random,
        nowMs = nowMs,
        targetSize = mixSize,
        maxPerArtist = 3,
        avoidConsecutiveArtist = true
    )

    val mixKeys = mix.map { it.contentKey() }.toSet()
    val recommended = selectRandomizedBlend(
        ranked = ranked.filterNot { it.song.contentKey() in mixKeys },
        random = random,
        nowMs = nowMs,
        targetSize = recommendedSize,
        maxPerArtist = 3,
        avoidConsecutiveArtist = true
    )

    return HomeRecommendationResult(
        mixSongs = mix,
        recommendedSongs = recommended
    )
}

private data class ScoredSong(
    val song: Song,
    val score: Double
)

private data class BlendTargets(
    val favorites: Int,
    val fresh: Int,
    val rediscovery: Int
)

private fun selectRandomizedBlend(
    ranked: List<ScoredSong>,
    random: Random,
    nowMs: Long,
    targetSize: Int,
    maxPerArtist: Int,
    avoidConsecutiveArtist: Boolean
): List<Song> {
    if (ranked.isEmpty() || targetSize <= 0) {
        return emptyList()
    }

    val selected = ArrayList<Song>(targetSize)
    val targets = buildRandomTargets(targetSize, random)
    val selectedIds = HashSet<String>(targetSize * 2)
    val artistCounter = HashMap<String, Int>(16)
    var selectedFavorites = 0
    var selectedFresh = 0
    var selectedRediscovery = 0
    val maxPoolSize = minOf(220, ranked.size)
    val rankingPool = ranked.take(maxPoolSize)

    var lastArtist: String? = null

    while (selected.size < targetSize) {
        val deficits = categoryDeficits(
            selectedFavorites = selectedFavorites,
            selectedFresh = selectedFresh,
            selectedRediscovery = selectedRediscovery,
            targets = targets
        )

        val candidates = rankingPool
            .asSequence()
            .filterNot { selectedIds.contains(it.song.id) }
            .filter { scored ->
                val artist = scored.song.artistGroupKey()
                (artistCounter[artist] ?: 0) < maxPerArtist
            }
            .filter { scored ->
                if (!avoidConsecutiveArtist || lastArtist == null) return@filter true
                scored.song.artistGroupKey() != lastArtist || selected.size + 1 >= targetSize
            }
            .take(150)
            .toList()

        if (candidates.isEmpty()) {
            break
        }

        val picked = weightedRandomPick(candidates, random) { scored ->
            val song = scored.song
            val artist = song.artistGroupKey()
            val artistSeen = artistCounter[artist] ?: 0

            val baseWeight = max(scored.score, 0.01)
            val categoryBoost = categoryBoost(song, nowMs, deficits)
            val artistPenalty = when (artistSeen) {
                0 -> 1.0
                1 -> 0.72
                else -> 0.48
            }
            val noise = 0.92 + (random.nextDouble() * 0.22)

            baseWeight * categoryBoost * artistPenalty * noise
        } ?: break

        val pickedSong = picked.song
        val canonicalArtist = pickedSong.artistGroupKey()
        val isFresh = pickedSong.playCount == 0 || pickedSong.lastPlayed <= 0L
        val isRediscovery = isRediscoverySong(pickedSong, nowMs)

        selected.add(pickedSong)
        selectedIds.add(pickedSong.id)
        artistCounter[canonicalArtist] = (artistCounter[canonicalArtist] ?: 0) + 1
        if (pickedSong.isFavorite) selectedFavorites += 1
        if (isFresh) selectedFresh += 1
        if (isRediscovery) selectedRediscovery += 1
        lastArtist = canonicalArtist
    }

    return selected
}

private fun buildRandomTargets(targetSize: Int, random: Random): BlendTargets {
    val favorites = (targetSize * (0.20 + random.nextDouble() * 0.18)).toInt()
    val fresh = (targetSize * (0.28 + random.nextDouble() * 0.18)).toInt()
    val rediscovery = (targetSize * (0.20 + random.nextDouble() * 0.16)).toInt()
    return BlendTargets(favorites = favorites, fresh = fresh, rediscovery = rediscovery)
}

private data class Deficits(
    val favorite: Double,
    val fresh: Double,
    val rediscovery: Double
)

private fun categoryDeficits(
    selectedFavorites: Int,
    selectedFresh: Int,
    selectedRediscovery: Int,
    targets: BlendTargets
): Deficits {
    return Deficits(
        favorite = (targets.favorites - selectedFavorites).coerceAtLeast(0).toDouble(),
        fresh = (targets.fresh - selectedFresh).coerceAtLeast(0).toDouble(),
        rediscovery = (targets.rediscovery - selectedRediscovery).coerceAtLeast(0).toDouble()
    )
}

private fun categoryBoost(song: Song, nowMs: Long, deficits: Deficits): Double {
    var boost = 1.0

    if (song.isFavorite && deficits.favorite > 0.0) {
        boost += 0.42 + (deficits.favorite * 0.07)
    }

    val isFresh = song.playCount == 0 || song.lastPlayed <= 0L
    if (isFresh && deficits.fresh > 0.0) {
        boost += 0.55 + (deficits.fresh * 0.08)
    }

    if (isRediscoverySong(song, nowMs) && deficits.rediscovery > 0.0) {
        boost += 0.32 + (deficits.rediscovery * 0.06)
    }

    return boost
}

private fun isRediscoverySong(song: Song, nowMs: Long): Boolean {
    if (song.lastPlayed <= 0L) return false
    val sevenDaysMs = 7L * 24L * 60L * 60L * 1000L
    return nowMs - song.lastPlayed >= sevenDaysMs
}

private fun <T> weightedRandomPick(
    items: List<T>,
    random: Random,
    weightProvider: (T) -> Double
): T? {
    if (items.isEmpty()) return null

    var totalWeight = 0.0
    val weights = DoubleArray(items.size)
    items.forEachIndexed { index, item ->
        val weight = max(0.0, weightProvider(item))
        weights[index] = weight
        totalWeight += weight
    }

    if (totalWeight <= 0.0) {
        return items.random(random)
    }

    var cursor = random.nextDouble() * totalWeight
    for (index in items.indices) {
        cursor -= weights[index]
        if (cursor <= 0.0) {
            return items[index]
        }
    }

    return items.last()
}

private data class HomeRecSignals(
    val tokens: Set<String>,
    val tokenWeights: Map<String, Double>
)

private fun recommendationScore(song: Song, signals: HomeRecSignals, nowMs: Long): Double {
    var score = 1.0

    // Perfil 50/50: conserva gusto histórico, pero abre espacio real a descubrimiento.
    if (song.isFavorite) score += 36.0
    score += sqrt((song.playCount + 1).toDouble()) * 8.6
    score -= song.skipCount.toDouble() * 4.1

    // Penalización temporal anti-repetición + redescubrimiento de catálogo.
    score *= temporalFreshnessMultiplier(song.lastPlayed, nowMs)
    score += explorationBoost(song = song, nowMs = nowMs)

    if (signals.tokens.isNotEmpty()) {
        val haystack = "${song.title} ${song.artist}".normalizeRecText()
        val matchCount = signals.tokens.count { haystack.contains(it) }

        score += matchCount * 19.0
        val weightedBoost = signals.tokenWeights
            .filterKeys { token -> haystack.contains(token) }
            .values
            .sum()
        score += weightedBoost * 5.2

        if (matchCount > 0 && song.isFavorite) {
            score += 6.5
        }
    }

    return score.coerceAtLeast(0.0)
}

private fun temporalFreshnessMultiplier(lastPlayed: Long, nowMs: Long): Double {
    if (lastPlayed <= 0L || nowMs <= lastPlayed) return 1.0

    val elapsedMs = nowMs - lastPlayed
    val hourMs = 60L * 60L * 1000L
    val dayMs = 24L * hourMs

    return when {
        elapsedMs < 4L * hourMs -> 0.10
        elapsedMs < 12L * hourMs -> 0.24
        elapsedMs < dayMs -> 0.46
        elapsedMs < 3L * dayMs -> 0.74
        elapsedMs < 7L * dayMs -> 0.93
        else -> 1.06
    }
}

private fun explorationBoost(song: Song, nowMs: Long): Double {
    // Empuje controlado para canciones poco escuchadas / nuevas sin romper relevancia.
    val novelty = if (song.playCount == 0) 14.0 else (9.0 / (song.playCount + 1).toDouble())
    if (song.lastPlayed <= 0L) return novelty + 6.0

    val daysSinceLastPlay = ((nowMs - song.lastPlayed).coerceAtLeast(0L) / (24L * 60L * 60L * 1000L)).toDouble()
    return novelty + minOf(10.5, daysSinceLastPlay * 0.72)
}

private fun songMatchesTokens(song: Song, tokens: Set<String>): Boolean {
    if (tokens.isEmpty()) return false
    val haystack = "${song.title} ${song.artist}".normalizeRecText()
    return tokens.any { haystack.contains(it) }
}

private fun extractPreferenceSignals(queries: List<String>): HomeRecSignals {
    val stopWords = setOf(
        "de", "del", "la", "el", "los", "las", "y", "and", "the",
        "cancion", "canciones", "song", "songs", "album", "albums", "musica", "music"
    )

    val cleaned = queries.map { it.normalizeRecText() }.filter { it.isNotBlank() }
    if (cleaned.isEmpty()) {
        return HomeRecSignals(emptySet(), emptyMap())
    }

    val total = cleaned.size.toDouble().coerceAtLeast(1.0)
    val tokenWeights = mutableMapOf<String, Double>()

    cleaned.forEachIndexed { index, query ->
        val recencyWeight = ((cleaned.size - index).toDouble() / total).coerceAtLeast(0.2)
        query.split(" ")
            .asSequence()
            .map { it.trim() }
            .filter { it.length >= 3 }
            .filter { it !in stopWords }
            .forEach { token ->
                tokenWeights[token] = (tokenWeights[token] ?: 0.0) + recencyWeight
            }
    }

    return HomeRecSignals(tokenWeights.keys, tokenWeights)
}

private fun String.normalizeRecText(): String {
    return lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun Song.contentKey(): String {
    return "${canonicalRecSongTitle(title)}|${artistGroupKey()}"
}

private fun Song.artistGroupKey(): String {
    val normalizedArtist = artist
        .normalizeRecText()
        .replace(Regex("\\b(official|topic|records?|music|vevo)\\b"), " ")
        .split(Regex("\\s*(,|&| x | feat | ft\\.? )\\s*"))
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
        .replace(Regex("\\s+"), " ")
        .trim()

    return if (normalizedArtist.isBlank()) {
        "unknown-${title.normalizeRecText()}"
    } else {
        normalizedArtist
    }
}

private fun canonicalRecSongTitle(value: String): String {
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
