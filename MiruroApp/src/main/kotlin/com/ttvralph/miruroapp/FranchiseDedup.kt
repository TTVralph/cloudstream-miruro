package com.ttvralph.miruroapp

import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.AnimeType
import com.ttvralph.miruroapp.data.HomeRow
import java.util.Locale

internal fun List<HomeRow>.collapseHomeFranchises(): List<HomeRow> = mapNotNull { row ->
    row.copy(items = row.items.collapseSeasonEntries()).takeIf { it.items.isNotEmpty() }
}

internal fun List<AnimeItem>.collapseSeasonEntries(): List<AnimeItem> {
    val groups = mutableListOf<MutableList<AnimeItem>>()
    for (item in this) {
        if (item.type != AnimeType.TV) {
            groups += mutableListOf(item)
            continue
        }
        val group = groups.firstOrNull { existing ->
            existing.firstOrNull()?.let { candidate ->
                candidate.type == AnimeType.TV && sameAnimeFranchise(candidate.title, item.title)
            } == true
        }
        if (group == null) groups += mutableListOf(item) else group += item
    }
    return groups.map { group ->
        if (group.size == 1) return@map group.first()
        val representative = group.minWithOrNull(
            compareBy<AnimeItem> { franchiseDisplayTitle(it.title).length }
                .thenBy { it.year ?: Int.MAX_VALUE }
        ) ?: group.first()
        representative.copy(
            title = franchiseDisplayTitle(representative.title),
            posterUrl = representative.posterUrl ?: group.firstNotNullOfOrNull { it.posterUrl },
            bannerUrl = representative.bannerUrl ?: group.firstNotNullOfOrNull { it.bannerUrl },
            score = representative.score ?: group.mapNotNull { it.score }.maxOrNull()
        )
    }
}

private fun sameAnimeFranchise(first: String, second: String): Boolean {
    val firstMarkers = variantMarkers(first)
    val secondMarkers = variantMarkers(second)
    if (firstMarkers != secondMarkers && (firstMarkers.isNotEmpty() || secondMarkers.isNotEmpty())) return false
    val firstTokens = franchiseTokens(first)
    val secondTokens = franchiseTokens(second)
    if (firstTokens.isEmpty() || secondTokens.isEmpty()) return false
    if (firstTokens == secondTokens) return true
    val shorter = minOf(firstTokens.size, secondTokens.size)
    val overlap = firstTokens.intersect(secondTokens).size
    if (shorter == 1) return overlap == 1
    val firstPrefix = firstTokens.take(3)
    val secondPrefix = secondTokens.take(3)
    return (firstPrefix.size >= 2 && firstPrefix == secondPrefix) ||
        (overlap >= 2 && overlap.toDouble() / shorter >= 0.72)
}

private fun franchiseTokens(value: String): List<String> = franchiseDisplayTitle(value)
    .lowercase(Locale.ROOT)
    .replace(Regex("[^a-z0-9]+"), " ")
    .split(Regex("\\s+"))
    .filter { token ->
        token.isNotBlank() &&
            token !in setOf("the", "a", "an", "season", "part", "cour", "series", "final") &&
            token.toIntOrNull() == null &&
            !token.matches(Regex("\\d+(st|nd|rd|th)"))
    }

private fun franchiseDisplayTitle(value: String): String = value
    .replace(Regex("(?i)\\s*[-:–—]?\\s*(?:season|part|cour)\\s*\\d+.*$"), "")
    .replace(Regex("(?i)\\s*[-:–—]?\\s*\\d+(?:st|nd|rd|th)\\s+season.*$"), "")
    .replace(Regex("(?i)\\s*[-:–—]?\\s*final season.*$"), "")
    .trim()
    .ifBlank { value }

private fun variantMarkers(value: String): Set<String> {
    val lower = value.lowercase(Locale.ROOT)
    return setOf("junior high", "chibi", "gaiden", "side story", "spin-off", "spin off", "alternative")
        .filterTo(linkedSetOf()) { it in lower }
}
