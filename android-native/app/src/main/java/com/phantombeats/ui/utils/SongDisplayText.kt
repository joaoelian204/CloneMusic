package com.phantombeats.ui.utils

import com.phantombeats.domain.model.Song

data class SongDisplayText(
    val title: String,
    val subtitle: String
)

fun Song.toDisplayText(): SongDisplayText {
    val artistPart = artist.substringBefore("•").trim()
    var albumPart = artist.substringAfter("•", "").trim().ifBlank { null }
    
    // Clean up duration or views from the album part
    if (albumPart != null) {
        val parts = albumPart.split("•").map { it.trim() }
        // Filter out duration (e.g. "3:45") and views (e.g. "1.5M views")
        val cleanParts = parts.filter { chunk ->
            !Regex("^(?:\\d+:)?\\d+:\\d+$").matches(chunk) &&
            !Regex("(?i)^[0-9.,]+[kmblr]?\\s*(vistas|views|reproducciones|meses|years|años)$").matches(chunk)
        }
        albumPart = cleanParts.joinToString(" • ").trim().ifBlank { null }
    }
    
    val normalizedArtist = artistPart.normalizeForCompare()

    val rawTitle = title.trim().trimTitleDelimiters()
    val (titleWithoutAlbumSuffix, albumFromTitleSuffix) = rawTitle.extractAlbumSuffixFromTitle()
    val (titleWithoutCollaborators, collaboratorArtists) = titleWithoutAlbumSuffix.extractCollaboratorArtists()
    val (titleWithoutFeat, featuredArtists) = titleWithoutCollaborators.extractFeaturedArtists()
    val secondaryArtists = (collaboratorArtists + featuredArtists)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.normalizeForCompare() }

    var inferredArtist: String? = null
    val titleSplit = titleWithoutFeat.splitArtistAndTitle()
    val baseTitle = if (titleSplit != null) {
        val left = titleSplit.first
        val right = titleSplit.second
        val normalizedLeft = left.normalizeForCompare()
        val normalizedRight = right.normalizeForCompare()
        val leftLikelyArtist = left.isLikelyArtistName()
        val rightLikelyArtist = right.isLikelyArtistName()

        when {
            normalizedArtist.isNotBlank() && (
                normalizedArtist == normalizedLeft ||
                    normalizedArtist.contains(normalizedLeft) ||
                    normalizedLeft.contains(normalizedArtist)
                ) -> {
                if (rightLikelyArtist && !leftLikelyArtist) {
                    inferredArtist = right
                    left
                } else {
                    right
                }
            }

            normalizedArtist.isNotBlank() && (
                normalizedArtist == normalizedRight ||
                    normalizedArtist.contains(normalizedRight) ||
                    normalizedRight.contains(normalizedArtist)
                ) -> {
                if (leftLikelyArtist && !rightLikelyArtist) {
                    inferredArtist = left
                    right
                } else {
                    left
                }
            }

            artistPart.isBlank() || artistPart.isProviderLabel() -> {
                if (rightLikelyArtist && !leftLikelyArtist) {
                    inferredArtist = right
                    left
                } else {
                    inferredArtist = left
                    right
                }
            }

            else -> titleWithoutFeat
        }
    } else {
        titleWithoutFeat
    }

    val cleanTitle = baseTitle.cleanTitleNoise()
        .ifBlank { titleWithoutFeat.cleanTitleNoise() }
        .ifBlank { rawTitle }

    val displayArtist = when {
        artistPart.isNotBlank() && !artistPart.isProviderLabel() -> artistPart
        !inferredArtist.isNullOrBlank() -> inferredArtist.trim()
        else -> ""
    }

    val subtitleParts = buildList<String> {
        if (displayArtist.isNotBlank()) add(displayArtist)
        secondaryArtists.forEach { guest ->
            val guestNorm = guest.normalizeForCompare()
            if (guestNorm.isNotBlank() && this.none { it.normalizeForCompare() == guestNorm }) {
                add(guest)
            }
        }
        val effectiveAlbum = albumPart ?: albumFromTitleSuffix
        if (!effectiveAlbum.isNullOrBlank() && this.none { it.normalizeForCompare() == effectiveAlbum.normalizeForCompare() }) {
            add(effectiveAlbum)
        }
        if (!albumPart.isNullOrBlank() && !albumPart.equals(displayArtist, ignoreCase = true)) {
            val albumNorm = albumPart.normalizeForCompare()
            if (this.none { it.normalizeForCompare() == albumNorm }) {
                add(albumPart)
            }
        }
    }

    val subtitleText = if (subtitleParts.isNotEmpty()) {
        subtitleParts.joinToString(" • ")
    } else {
        "Artista desconocido"
    }

    return SongDisplayText(
        title = cleanTitle,
        subtitle = subtitleText
    )
}

private fun String.extractCollaboratorArtists(): Pair<String, List<String>> {
    val guests = mutableListOf<String>()
    var baseTitle = this

    val parentheticalRegex = Regex("(?i)\\s*[\\(\\[\\{]\\s*(?:con|with|w/|junto\\s+a)\\s+([^\\)\\]\\}]+)\\s*[\\)\\]\\}]")
    parentheticalRegex.findAll(baseTitle).forEach { match ->
        guests += match.groupValues.getOrNull(1).orEmpty().splitFeaturedGuests()
    }
    baseTitle = baseTitle.replace(parentheticalRegex, " ")

    val tailRegex = Regex("(?i)\\s*(?:-|\\|)\\s*(?:con|with|w/|junto\\s+a)\\s+(.+?)\\s*$")
    val tailMatch = tailRegex.find(baseTitle)
    if (tailMatch != null) {
        guests += tailMatch.groupValues.getOrNull(1).orEmpty().splitFeaturedGuests()
        baseTitle = baseTitle.substring(0, tailMatch.range.first)
    }

    val cleanGuests = guests
        .map { it.trimFeaturedDecorators() }
        .filter { it.isNotBlank() }
        .distinctBy { it.normalizeForCompare() }

    return baseTitle.trimTitleDelimiters() to cleanGuests
}

private fun String.normalizeForCompare(): String {
    return lowercase()
        .replace("&", "and")
        .replace(Regex("[^a-z0-9]+"), "")
}

private fun String.trimTitleDelimiters(): String {
    return trim().trim('-', '~', '|').trim()
}

private fun String.cleanTitleNoise(): String {
    val noiseTag = "(?:official(?:\\s+music)?\\s+video|official\\s+video|official\\s+audio|official\\s+animated\\s+video|animated\\s+video|lyric\\s+video|lyrics|video\\s+oficial|audio\\s+oficial|visualizer)"

    val withoutBracketNoise = replace(
        Regex("(?i)\\s*[\\(\\[\\{*_\\-]{0,4}\\s*${noiseTag}\\s*[\\)\\]\\}*_\\-]{0,4}"),
        " "
    )

    val withoutTailNoise = withoutBracketNoise
        .replace(
            Regex("(?i)\\s*[-:|*_\\-]{1,6}\\s*${noiseTag}\\s*[*_\\-]{0,6}\\s*$"),
            " "
        )

    return withoutTailNoise
        .replace(Regex("\\s+"), " ")
        .trimTitleDelimiters()
}

private fun String.extractFeaturedArtists(): Pair<String, List<String>> {
    val extractedGuests = mutableListOf<String>()
    var baseTitle = this

    val parentheticalFeatRegex = Regex("(?i)\\s*[\\(\\[\\{]\\s*(?:ft\\.?|feat\\.?|featuring)\\s+([^\\)\\]\\}]+)\\s*[\\)\\]\\}]")
    parentheticalFeatRegex.findAll(baseTitle).forEach { match ->
        extractedGuests += match.groupValues.getOrNull(1).orEmpty().splitFeaturedGuests()
    }
    baseTitle = baseTitle.replace(parentheticalFeatRegex, " ")

    val wrappedFeatRegex = Regex("(?i)\\s*[*_\\-]{1,4}\\s*(?:ft\\.?|feat\\.?|featuring)\\s+(.+?)\\s*[*_\\-]{1,4}")
    wrappedFeatRegex.findAll(baseTitle).forEach { match ->
        extractedGuests += match.groupValues.getOrNull(1).orEmpty().splitFeaturedGuests()
    }
    baseTitle = baseTitle.replace(wrappedFeatRegex, " ")

    val tailFeatMatch = Regex("(?i)\\s*[\\(\\[\\{*_-]*\\s*(?:ft\\.?|feat\\.?|featuring)\\s+(.+?)\\s*[\\)\\]\\}*_-]*\\s*$").find(baseTitle)
    if (tailFeatMatch != null) {
        extractedGuests += tailFeatMatch.groupValues.getOrNull(1).orEmpty().splitFeaturedGuests()
        baseTitle = baseTitle.substring(0, tailFeatMatch.range.first)
    }

    val guests = extractedGuests
        .map { it.trim().trimTitleDelimiters() }
        .filter { it.isNotBlank() }
        .distinctBy { it.normalizeForCompare() }

    return baseTitle.trimTitleDelimiters() to guests
}

private fun String.extractAlbumSuffixFromTitle(): Pair<String, String?> {
    val split = splitArtistAndTitleByDelimiter(" | ") ?: return this to null
    val left = split.first
    val right = split.second

    if (!right.isLikelyAlbumLabel()) return this to null
    return left to right
}

private fun String.splitArtistAndTitleByDelimiter(delimiter: String): Pair<String, String>? {
    val idx = indexOf(delimiter)
    if (idx < 0) return null

    val left = substring(0, idx).trimTitleDelimiters()
    val right = substring(idx + delimiter.length).trimTitleDelimiters()
    return if (left.isNotBlank() && right.isNotBlank()) left to right else null
}

private fun String.isLikelyAlbumLabel(): Boolean {
    val value = trimTitleDelimiters()
    if (value.isBlank()) return false
    if (value.length > 40) return false

    val normalized = value.normalizeForCompare()
    if (normalized.length < 2) return false
    if (Regex("(?i)(official|video|lyrics|lyric|audio|visualizer|feat|ft)").containsMatchIn(value)) {
        return false
    }

    val tokenCount = value.split(Regex("\\s+")).count { it.isNotBlank() }
    return tokenCount <= 6
}

private fun String.splitFeaturedGuests(): List<String> {
    return split(Regex("(?i)\\s*(?:,|&|/|\\by\\b|\\band\\b)\\s*"))
        .map { it.trimFeaturedDecorators() }
        .filter { it.isNotBlank() }
}

private fun String.trimFeaturedDecorators(): String {
    return trim()
        .trim('*', '_', '-', '[', ']', '(', ')', '{', '}', '|')
        .trim()
}

private fun String.splitArtistAndTitle(): Pair<String, String>? {
    val delimiters = listOf(" - ", " ~ ", " | ")
    val indexAndDelimiter = delimiters
        .map { delimiter -> delimiter to indexOf(delimiter) }
        .filter { it.second >= 0 }
        .minByOrNull { it.second }
        ?: return null

    val delimiter = indexAndDelimiter.first
    val splitIndex = indexAndDelimiter.second
    val left = substring(0, splitIndex).trimTitleDelimiters()
    val right = substring(splitIndex + delimiter.length).trimTitleDelimiters()

    return if (left.isNotBlank() && right.isNotBlank()) left to right else null
}

private fun String.isLikelyArtistName(): Boolean {
    val value = trimTitleDelimiters()
    if (value.isBlank()) return false
    if (Regex("\\d").containsMatchIn(value)) return false

    val normalized = value.normalizeForCompare()
    if (normalized.length < 4 || normalized.length > 40) return false

    val tokens = value.split(Regex("\\s+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (tokens.isEmpty() || tokens.size > 5) return false

    val titleWords = setOf(
        "amor", "cancion", "song", "music", "video", "official", "lyrics", "lyric",
        "nuestro", "nuestra", "juramento", "corazon", "corazon", "vida", "noche", "dia",
        "verano", "invierno", "baila", "bailando", "mix", "remix", "version", "live"
    )

    val loweredTokens = tokens.map { it.lowercase() }
    if (loweredTokens.any { it in titleWords }) return false

    val connectors = setOf("de", "del", "la", "las", "los", "y", "and", "the")
    val nonConnectorCount = loweredTokens.count { it !in connectors }
    return nonConnectorCount in 1..4
}

private fun String.isProviderLabel(): Boolean {
    val normalized = normalizeForCompare()
    return normalized == "youtube" ||
        normalized == "youtubemusic" ||
        normalized == "ytmusic" ||
        normalized == "soundcloud" ||
        normalized == "spotify" ||
        normalized == "itunes"
}
